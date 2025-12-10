#!/bin/bash
# test-coverage.sh - Run all tests and generate Scoverage coverage report
#
# Usage:
#   ./scripts/test-coverage.sh [-m <module>] [-h|--help]
#
# Examples:
#   ./scripts/test-coverage.sh -m core
#   ./scripts/test-coverage.sh -m services

set -euo pipefail

# Determine script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load libraries
source "$SCRIPT_DIR/lib/logging.sh"
source "$SCRIPT_DIR/lib/validation.sh"
source "$SCRIPT_DIR/lib/module-helper.sh"
source "$SCRIPT_DIR/lib/preflight.sh"
source "$SCRIPT_DIR/lib/postflight.sh"

# Configuration
MAVEN_OPTS="${MAVEN_OPTS:-}"
export CLEANUP_DOCKER="${CLEANUP_DOCKER:-1}"

# Coverage thresholds
THRESHOLD_OVERALL=70
THRESHOLD_ACTORS=85
THRESHOLD_BUSINESS_LOGIC=80

# Trap to ensure postflight runs even on failure
trap 'postflight_all' EXIT

# Show help
show_help() {
  cat << EOF
${COLOR_BOLD}${COLOR_CYAN}test-coverage.sh${COLOR_RESET} - Generate code coverage report

${COLOR_BOLD}USAGE:${COLOR_RESET}
  ./scripts/test-coverage.sh [-m MODULE] [-h|--help]

${COLOR_BOLD}OPTIONS:${COLOR_RESET}
  -m, --module MODULE    Module to analyze (common|core|services|external|interfaces)
  -h, --help             Show this help message

${COLOR_BOLD}EXAMPLES:${COLOR_RESET}
  ${COLOR_CYAN}# Generate coverage for core module${COLOR_RESET}
  ./scripts/test-coverage.sh -m core

  ${COLOR_CYAN}# Generate coverage for services module${COLOR_RESET}
  ./scripts/test-coverage.sh -m services

  ${COLOR_CYAN}# Interactive mode (prompts for module)${COLOR_RESET}
  ./scripts/test-coverage.sh

${COLOR_BOLD}FEATURES:${COLOR_RESET}
  ✓ Runs all tests (unit + component) with coverage instrumentation
  ✓ Generates HTML coverage report
  ✓ Checks coverage against thresholds (warnings only)
  ✓ Parallel execution for core module (4 threads)
  ✓ Automatic dependency compilation
  ✓ Execution timing for each step
  ✓ Disk space validation (15GB+ recommended)

${COLOR_BOLD}COVERAGE THRESHOLDS:${COLOR_RESET}
  Overall:         ${THRESHOLD_OVERALL}%+ (recommended)
  Actors:          ${THRESHOLD_ACTORS}%+ (recommended for core)
  Business Logic:  ${THRESHOLD_BUSINESS_LOGIC}%+ (recommended)

${COLOR_BOLD}NOTE:${COLOR_RESET}
  Coverage instrumentation makes tests run slower (~30% slower).
  This script WARNS if coverage is below thresholds but does not fail.
  Use build-ci.sh for strict enforcement.

${COLOR_BOLD}AVAILABLE MODULES:${COLOR_RESET}
  common      test-probe-common              (no dependencies)
  core        test-probe-core                (depends on: common)
  services    test-probe-services            (depends on: common, core)
  external    test-probe-external-services   (depends on: common, core)
  interfaces  test-probe-interfaces          (depends on: common, core, services)

For more information, see scripts/README.md
EOF
}

# Parse arguments (check for --help first)
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  show_help
  exit 0
fi

# Check coverage thresholds and warn (not fail)
check_coverage() {
  local module="$1"
  local coverage_xml="$PROJECT_ROOT/$module/target/scoverage.xml"

  if [[ ! -f "$coverage_xml" ]]; then
    log_warning "Coverage report not found: $coverage_xml"
    return 0  # Don't fail, just warn
  fi

  # Parse statement coverage rate (already in percentage format 0-100)
  local statement_pct=$(grep -o 'statement-rate="[0-9.]*"' "$coverage_xml" | grep -o '[0-9.]*' | head -1)
  statement_pct=${statement_pct%.*}  # Remove decimal part

  log_info ""
  log_info "Coverage Summary:"
  log_info "  Statement Coverage: ${statement_pct}%"

  # Warn if below overall threshold
  if (( statement_pct < THRESHOLD_OVERALL )); then
    log_warning "Statement coverage (${statement_pct}%) is below minimum threshold (${THRESHOLD_OVERALL}%)"
    log_info "    Recommended: Increase test coverage to meet minimum standards"
  else
    log_success "Statement coverage (${statement_pct}%) meets minimum threshold (${THRESHOLD_OVERALL}%)"
  fi

  # For actors, check if this module has actors and apply stricter threshold
  if [[ "$module" == *"core"* ]]; then
    if (( statement_pct < THRESHOLD_ACTORS )); then
      log_warning "Actor module coverage (${statement_pct}%) is below recommended threshold (${THRESHOLD_ACTORS}%)"
      log_info "    Recommended: Increase actor test coverage to ${THRESHOLD_ACTORS}%+"
    fi
  fi

  log_info ""
}

# Main execution
main() {
  # Start overall timer
  start_timer

  # Parse -m flag or prompt for module
  if ! parse_module_arg "$@"; then
    MODULE_NAME=$(select_module_interactive) || exit 1
  fi

  log_header "Coverage Report: $MODULE_NAME"

  # Step 1: Validation
  log_step_timed 1 5 "Validating environment"
  validate_all || exit 1
  validate_module "$MODULE_NAME" || exit 1
  validate_disk_space 15 || true  # 15GB minimum for coverage, warning only

  # Step 2: Preflight
  log_step_timed 2 5 "Preparing environment (scaling down K8s)"
  preflight_all

  # Step 3: Compile dependencies
  log_step_timed 3 5 "Compiling dependencies"
  cd "$PROJECT_ROOT"

  local deps=$(get_module_deps "$MODULE_NAME")
  if [[ -n "$deps" ]]; then
    log_info "Compiling dependencies: $deps"
    log_progress "Compiling $deps (this may take a moment)"
    mvn install -pl "$deps" -Dmaven.test.skip=true -q $MAVEN_OPTS || {
      log_error "Failed to compile dependencies"
      exit 1
    }
  else
    log_info "No dependencies to compile"
  fi

  # Step 4: Run tests + coverage
  log_step_timed 4 5 "Running tests and generating coverage report"

  # For core, enable parallel execution
  local parallel_opts=""
  if [[ "$MODULE_NAME" == "test-probe-core" ]]; then
    log_info "Enabling parallel execution (4 threads) for test-probe-core"
    parallel_opts="-Dcucumber.parallel.enabled=true -Dcucumber.parallel.threads=4"
  fi

  log_progress "Compiling with coverage instrumentation"
  log_progress "Running tests (coverage instrumentation makes this ~30% slower)"
  log_progress "Generating coverage report (this may take 3-5 minutes total)"

  # Clean + coverage report (scoverage:report runs tests in forked lifecycle)
  if mvn clean scoverage:report -pl "$MODULE_NAME" $parallel_opts $MAVEN_OPTS 2>&1 | tee /tmp/coverage-output.log; then
    TEST_EXIT_CODE=0
  else
    TEST_EXIT_CODE=$?
  fi

  # Step 5: Report results
  log_step_timed 5 5 "Coverage report complete"

  if [[ $TEST_EXIT_CODE -ne 0 ]]; then
    local elapsed=$(get_elapsed_time)

    log_error "Tests failed for $MODULE_NAME (exit code: $TEST_EXIT_CODE)"
    log_error "  Total execution time: $elapsed"

    # Show test failure summary
    echo ""
    log_error "Test Failure Summary:"

    if grep -q "Tests run:" /tmp/coverage-output.log; then
      grep "Tests run:" /tmp/coverage-output.log | tail -1 | sed 's/^/  /'
    fi

    if grep -E "<<< FAILURE|<<< ERROR" /tmp/coverage-output.log > /dev/null; then
      log_error "Failed tests:"
      grep -E "<<< FAILURE|<<< ERROR" /tmp/coverage-output.log | sed 's/^/  - /' | head -10

      local failure_count=$(grep -E "<<< FAILURE|<<< ERROR" /tmp/coverage-output.log | wc -l | tr -d ' ')
      if [[ $failure_count -gt 10 ]]; then
        log_info "  ... and $((failure_count - 10)) more failures"
      fi
    fi

    echo ""
    log_info "For full details, see: /tmp/coverage-output.log"

    return $TEST_EXIT_CODE
  fi

  # Check coverage (warnings only)
  check_coverage "$MODULE_NAME"

  # Print path to HTML report
  local html_report="$PROJECT_ROOT/$MODULE_NAME/target/site/scoverage/index.html"
  if [[ -f "$html_report" ]]; then
    log_success "Coverage report: $html_report"
  else
    log_warning "HTML report not found: $html_report"
  fi

  local elapsed=$(get_elapsed_time)
  log_success "All tests passed for $MODULE_NAME! ✓"
  log_info "  Total execution time: $elapsed"
  return 0
}

# Run main function
main "$@"
