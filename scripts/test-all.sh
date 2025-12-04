#!/bin/bash
# test-all.sh - Run ALL tests (unit + component) for a module
#
# Usage:
#   ./scripts/test-all.sh [-m <module>] [-h|--help]
#
# Examples:
#   ./scripts/test-all.sh -m core
#   ./scripts/test-all.sh -m services

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

# Trap to ensure postflight runs even on failure
trap 'postflight_all' EXIT

# Show help
show_help() {
  cat << EOF
${COLOR_BOLD}${COLOR_CYAN}test-all.sh${COLOR_RESET} - Run all tests (unit + component)

${COLOR_BOLD}USAGE:${COLOR_RESET}
  ./scripts/test-all.sh [-m MODULE] [-h|--help]

${COLOR_BOLD}OPTIONS:${COLOR_RESET}
  -m, --module MODULE    Module to test (common|core|services|interfaces)
  -h, --help             Show this help message

${COLOR_BOLD}EXAMPLES:${COLOR_RESET}
  ${COLOR_CYAN}# Run all tests for core module${COLOR_RESET}
  ./scripts/test-all.sh -m core

  ${COLOR_CYAN}# Run all tests for services module${COLOR_RESET}
  ./scripts/test-all.sh -m services

  ${COLOR_CYAN}# Interactive mode (prompts for module)${COLOR_RESET}
  ./scripts/test-all.sh

${COLOR_BOLD}FEATURES:${COLOR_RESET}
  ✓ Runs both unit and component tests
  ✓ Automatic K8s scaling (down before tests, restored after)
  ✓ Docker cleanup after tests
  ✓ Parallel execution for core module (4 threads)
  ✓ Automatic dependency compilation (no dependency tests)
  ✓ Execution timing for each step
  ✓ Disk space validation
  ✓ Test failure summaries

${COLOR_BOLD}AVAILABLE MODULES:${COLOR_RESET}
  common      test-probe-common      (no dependencies)
  core        test-probe-core        (depends on: common)
  services    test-probe-services    (depends on: common, core)
  interfaces  test-probe-interfaces  (depends on: common, core, services)

For more information, see scripts/README.md
EOF
}

# Parse arguments (check for --help first)
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  show_help
  exit 0
fi

# Main execution
main() {
  # Start overall timer
  start_timer

  # Parse -m flag or prompt for module
  if ! parse_module_arg "$@"; then
    MODULE_NAME=$(select_module_interactive) || exit 1
  fi

  log_header "All Tests: $MODULE_NAME"

  # Step 1: Validation
  log_step_timed 1 5 "Validating environment"
  validate_all || exit 1
  validate_module "$MODULE_NAME" || exit 1
  validate_disk_space 10 || true  # 10GB minimum, warning only

  # Step 2: Preflight
  log_step_timed 2 5 "Preparing environment (scaling down K8s)"
  preflight_all

  # Step 3: Compile dependencies (no tests)
  log_step_timed 3 5 "Compiling dependencies"
  cd "$PROJECT_ROOT"

  local deps=$(get_module_deps "$MODULE_NAME")
  if [[ -n "$deps" ]]; then
    log_info "Compiling dependencies: $deps"
    log_progress "Compiling $deps (this may take a moment)"
    ./mvnw install -pl "$deps" -Dmaven.test.skip=true -q $MAVEN_OPTS || {
      log_error "Failed to compile dependencies"
      exit 1
    }
  else
    log_info "No dependencies to compile"
  fi

  # Step 4: Run all tests (unit + component)
  log_step_timed 4 5 "Running all tests for $MODULE_NAME"

  # For core, enable parallel execution (hard-coded 4 threads)
  local parallel_opts=""
  if [[ "$MODULE_NAME" == "test-probe-core" ]]; then
    log_info "Enabling parallel execution (4 threads) for test-probe-core"
    parallel_opts="-Dcucumber.parallel.enabled=true -Dcucumber.parallel.threads=4"
  fi

  log_progress "Running unit and component tests (this may take 3-5 minutes)"

  if ./mvnw test -pl "$MODULE_NAME" $parallel_opts $MAVEN_OPTS 2>&1 | tee /tmp/all-test-output.log; then
    TEST_EXIT_CODE=0
  else
    TEST_EXIT_CODE=$?
  fi

  # Step 5: Report results
  log_step_timed 5 5 "Test execution complete"

  if [[ $TEST_EXIT_CODE -eq 0 ]]; then
    # Parse test summary
    local test_count=$(grep "Tests run:" /tmp/all-test-output.log | tail -1 | grep -o "Tests run: [0-9]*" | grep -o "[0-9]*" || echo "unknown")
    local elapsed=$(get_elapsed_time)

    log_success "All tests passed for $MODULE_NAME! ✓"
    log_info "  Tests executed: $test_count"
    log_info "  Total execution time: $elapsed"
    return 0
  else
    local elapsed=$(get_elapsed_time)

    log_error "Tests failed for $MODULE_NAME (exit code: $TEST_EXIT_CODE)"
    log_error "  Total execution time: $elapsed"

    # Show test failure summary
    echo ""
    log_error "Test Failure Summary:"

    if grep -q "Tests run:" /tmp/all-test-output.log; then
      grep "Tests run:" /tmp/all-test-output.log | tail -1 | sed 's/^/  /'
    fi

    if grep -E "<<< FAILURE|<<< ERROR" /tmp/all-test-output.log > /dev/null; then
      log_error "Failed tests:"
      grep -E "<<< FAILURE|<<< ERROR" /tmp/all-test-output.log | sed 's/^/  - /' | head -10

      local failure_count=$(grep -E "<<< FAILURE|<<< ERROR" /tmp/all-test-output.log | wc -l | tr -d ' ')
      if [[ $failure_count -gt 10 ]]; then
        log_info "  ... and $((failure_count - 10)) more failures"
      fi
    fi

    echo ""
    log_info "For full details, see: /tmp/all-test-output.log"
    log_info "Run with -X for stack traces:"
    log_info "  ./mvnw test -X -pl $MODULE_NAME"

    return $TEST_EXIT_CODE
  fi
}

# Run main function
main "$@"
