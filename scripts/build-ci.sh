#!/bin/bash
# build-ci.sh - Full CI/CD build (all modules, all tests, fat jar)
#
# Usage:
#   ./scripts/build-ci.sh [-h|--help]
#
# Examples:
#   ./scripts/build-ci.sh

set -euo pipefail

# Determine script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load libraries
source "$SCRIPT_DIR/lib/logging.sh"
source "$SCRIPT_DIR/lib/validation.sh"
source "$SCRIPT_DIR/lib/preflight.sh"
source "$SCRIPT_DIR/lib/postflight.sh"

# Configuration
MAVEN_OPTS="${MAVEN_OPTS:-}"
export CLEANUP_DOCKER="${CLEANUP_DOCKER:-1}"

# Coverage thresholds (HARD REQUIREMENTS for CI)
THRESHOLD_OVERALL=70
THRESHOLD_ACTORS=85
THRESHOLD_BUSINESS_LOGIC=80

# Trap to ensure postflight runs even on failure
trap 'postflight_all' EXIT

# Show help
show_help() {
  cat << EOF
${COLOR_BOLD}${COLOR_CYAN}build-ci.sh${COLOR_RESET} - Full CI/CD build pipeline

${COLOR_BOLD}USAGE:${COLOR_RESET}
  ./scripts/build-ci.sh [-h|--help]

${COLOR_BOLD}EXAMPLES:${COLOR_RESET}
  ${COLOR_CYAN}# Run full CI/CD build${COLOR_RESET}
  ./scripts/build-ci.sh

  ${COLOR_CYAN}# Skip K8s scaling (CI environment)${COLOR_RESET}
  SKIP_K8S_SCALING=1 ./scripts/build-ci.sh

${COLOR_BOLD}FEATURES:${COLOR_RESET}
  ✓ Runs ALL tests (unit + component) on ALL modules
  ✓ Generates coverage reports with instrumentation
  ✓ Enforces STRICT coverage thresholds (build fails if below)
  ✓ Packages fat JAR artifact
  ✓ Validates artifacts
  ✓ Parallel execution (4 threads for core module)
  ✓ Automatic K8s scaling and Docker cleanup
  ✓ Professional coverage summary table
  ✓ Execution timing for each step
  ✓ Disk space validation (20GB+ required)

${COLOR_BOLD}COVERAGE THRESHOLDS (STRICT - Build Fails if Below):${COLOR_RESET}
  Overall:         ${THRESHOLD_OVERALL}%+ (required for common, interfaces)
  Actors:          ${THRESHOLD_ACTORS}%+ (required for core)
  Business Logic:  ${THRESHOLD_BUSINESS_LOGIC}%+ (required for services)

${COLOR_BOLD}MODULES TESTED:${COLOR_RESET}
  test-probe-common      (no dependencies)
  test-probe-core        (depends on: common)
  test-probe-services    (depends on: common, core)
  test-probe-interfaces  (depends on: common, core, services)

${COLOR_BOLD}NOTE:${COLOR_RESET}
  This is the STRICT CI/CD build. Coverage below thresholds FAILS the build.
  For development builds with warnings only, use test-coverage.sh instead.

  Coverage instrumentation makes tests run ~30% slower but ensures
  comprehensive code coverage measurement.

${COLOR_BOLD}ENVIRONMENT VARIABLES:${COLOR_RESET}
  SKIP_K8S_SCALING=1     Skip Kubernetes scaling (for CI environments)
  CLEANUP_DOCKER=0       Skip Docker cleanup after tests
  MAVEN_OPTS             Additional Maven options

For more information, see scripts/README.md
EOF
}

# Parse arguments (check for --help first)
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  show_help
  exit 0
fi

# Get coverage data for a module (returns coverage|threshold|pass via stdout)
get_coverage_data() {
  local module="$1"
  local coverage_xml="$PROJECT_ROOT/$module/target/scoverage.xml"

  if [[ ! -f "$coverage_xml" ]]; then
    echo "0|70|false"
    return 1
  fi

  # Parse statement coverage rate (already in percentage format 0-100)
  local statement_pct=$(grep -o 'statement-rate="[0-9.]*"' "$coverage_xml" | grep -o '[0-9.]*' | head -1)
  statement_pct=${statement_pct%.*}  # Remove decimal part

  # Determine required threshold
  local required_threshold=$THRESHOLD_OVERALL

  if [[ "$module" == *"core"* ]]; then
    required_threshold=$THRESHOLD_ACTORS
  elif [[ "$module" == *"services"* ]]; then
    required_threshold=$THRESHOLD_BUSINESS_LOGIC
  fi

  # Check if passed
  local passed="false"
  if (( statement_pct >= required_threshold )); then
    passed="true"
  fi

  echo "${statement_pct}|${required_threshold}|${passed}"

  if [[ "$passed" == "true" ]]; then
    return 0
  else
    return 1
  fi
}

# Main execution
main() {
  # Start overall timer
  start_timer

  log_header "CI/CD Full Build"

  # Step 1: Validation
  log_step_timed 1 6 "Validating environment"
  validate_all || exit 1
  validate_disk_space 20 || true  # 20GB minimum for full build, warning only

  # Step 2: Preflight
  log_step_timed 2 6 "Preparing environment (scaling down K8s)"
  preflight_all

  # Step 3: Clean build
  log_step_timed 3 6 "Cleaning previous build artifacts"
  cd "$PROJECT_ROOT"
  log_progress "Cleaning target directories"
  ./mvnw clean -q $MAVEN_OPTS

  # Step 4: Run tests with coverage instrumentation
  log_step_timed 4 6 "Running tests with coverage (all modules)"
  log_info "Modules: common, core, services, interfaces"
  log_info "Parallel execution: 4 threads (core module)"
  log_info "Note: Coverage instrumentation makes tests run ~30% slower"

  log_progress "Compiling with coverage instrumentation"
  log_progress "Running all tests (unit + component) - this may take 5-10 minutes"
  log_progress "Generating coverage reports"

  # scoverage:report will compile with instrumentation, run tests, and generate report
  if ./mvnw scoverage:report \
      -Dcucumber.parallel.enabled=true \
      -Dcucumber.parallel.threads=4 \
      $MAVEN_OPTS 2>&1 | tee /tmp/ci-build-output.log; then
    TEST_EXIT_CODE=0
  else
    TEST_EXIT_CODE=$?
  fi

  if [[ $TEST_EXIT_CODE -ne 0 ]]; then
    local elapsed=$(get_elapsed_time)

    log_error "Tests failed - aborting build (exit code: $TEST_EXIT_CODE)"
    log_error "  Total execution time: $elapsed"

    # Show test failure summary
    echo ""
    log_error "Test Failure Summary:"

    if grep -q "Tests run:" /tmp/ci-build-output.log; then
      grep "Tests run:" /tmp/ci-build-output.log | tail -1 | sed 's/^/  /'
    fi

    if grep -E "<<< FAILURE|<<< ERROR" /tmp/ci-build-output.log > /dev/null; then
      log_error "Failed tests:"
      grep -E "<<< FAILURE|<<< ERROR" /tmp/ci-build-output.log | sed 's/^/  - /' | head -10

      local failure_count=$(grep -E "<<< FAILURE|<<< ERROR" /tmp/ci-build-output.log | wc -l | tr -d ' ')
      if [[ $failure_count -gt 10 ]]; then
        log_info "  ... and $((failure_count - 10)) more failures"
      fi
    fi

    echo ""
    log_info "For full details, see: /tmp/ci-build-output.log"

    exit $TEST_EXIT_CODE
  fi

  log_success "All tests passed with coverage!"

  # Step 5: Check coverage thresholds
  log_step_timed 5 6 "Validating coverage thresholds (STRICT MODE)"

  echo ""
  log_info "Coverage Summary:"
  echo ""

  # Print beautiful coverage table
  print_coverage_table_header

  local coverage_failures=0

  # Check each module and display in table
  local data

  # test-probe-common
  data=$(get_coverage_data "test-probe-common")
  IFS='|' read -r coverage threshold passed <<< "$data"
  print_coverage_table_row "test-probe-common" "$coverage" "$threshold" "$passed"
  [[ "$passed" == "false" ]] && ((coverage_failures++))

  # test-probe-core
  data=$(get_coverage_data "test-probe-core")
  IFS='|' read -r coverage threshold passed <<< "$data"
  print_coverage_table_row "test-probe-core" "$coverage" "$threshold" "$passed"
  [[ "$passed" == "false" ]] && ((coverage_failures++))

  # test-probe-services
  data=$(get_coverage_data "test-probe-services")
  IFS='|' read -r coverage threshold passed <<< "$data"
  print_coverage_table_row "test-probe-services" "$coverage" "$threshold" "$passed"
  [[ "$passed" == "false" ]] && ((coverage_failures++))

  # test-probe-interfaces
  data=$(get_coverage_data "test-probe-interfaces")
  IFS='|' read -r coverage threshold passed <<< "$data"
  print_coverage_table_row "test-probe-interfaces" "$coverage" "$threshold" "$passed"
  [[ "$passed" == "false" ]] && ((coverage_failures++))

  print_coverage_table_footer

  echo ""

  if (( coverage_failures > 0 )); then
    log_error "Coverage check FAILED for $coverage_failures module(s)"
    log_error "Build aborted - coverage below minimum thresholds"
    exit 1
  fi

  log_success "All coverage thresholds met!"

  # Step 6: Package fat JAR
  log_step_timed 6 6 "Packaging artifacts"

  log_progress "Building fat JAR (this may take a moment)"

  if ./mvnw package -DskipTests -q $MAVEN_OPTS; then
    log_success "Packaging complete"
  else
    log_error "Packaging failed"
    exit 1
  fi

  # Validate fat JAR exists
  local fat_jar=$(find "$PROJECT_ROOT/target" -name "test-probe-*.jar" -type f | head -1)
  if [[ -f "$fat_jar" ]]; then
    local jar_size=$(du -h "$fat_jar" | cut -f1)
    log_success "Fat JAR created: $fat_jar ($jar_size)"
  else
    log_warning "Fat JAR not found in target/"
  fi

  # Final summary with execution time
  local elapsed=$(get_elapsed_time)

  echo ""
  log_info "=========================================="
  log_success "CI/CD BUILD SUCCESSFUL"
  log_info "=========================================="
  log_info "Tests: ALL PASSED"
  log_info "Coverage: ALL THRESHOLDS MET"
  log_info "Artifacts: READY"
  [[ -n "$fat_jar" ]] && log_info "Fat JAR: $fat_jar"
  log_info "Total execution time: $elapsed"
  log_info "=========================================="

  return 0
}

# Run main function
main "$@"
