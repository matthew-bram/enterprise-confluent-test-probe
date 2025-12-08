#!/bin/bash
# test-integration.sh - Run integration tests with automatic environment management
#
# Usage:
#   ./scripts/test-integration.sh [-m <module>] [-h|--help]
#
# Examples:
#   ./scripts/test-integration.sh -m core
#   ./scripts/test-integration.sh -m interfaces
#   SKIP_K8S_SCALING=1 ./scripts/test-integration.sh -m core  # Skip K8s (CI mode)

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
export CLEANUP_DOCKER="${CLEANUP_DOCKER:-1}"  # Enable Docker cleanup by default

# Trap to ensure postflight runs even on failure
trap 'postflight_all' EXIT

# Show help
show_help() {
  cat << EOF
${COLOR_BOLD}${COLOR_CYAN}test-integration.sh${COLOR_RESET} - Run integration tests with environment management

${COLOR_BOLD}USAGE:${COLOR_RESET}
  ./scripts/test-integration.sh [-m MODULE] [-h|--help]

${COLOR_BOLD}OPTIONS:${COLOR_RESET}
  -m, --module MODULE    Module to test (common|core|services|interfaces)
  -h, --help             Show this help message

${COLOR_BOLD}EXAMPLES:${COLOR_RESET}
  ${COLOR_CYAN}# Test core module integration tests${COLOR_RESET}
  ./scripts/test-integration.sh -m core

  ${COLOR_CYAN}# Test services module integration tests${COLOR_RESET}
  ./scripts/test-integration.sh -m services

  ${COLOR_CYAN}# Interactive mode (prompts for module)${COLOR_RESET}
  ./scripts/test-integration.sh

  ${COLOR_CYAN}# Skip K8s scaling (CI mode)${COLOR_RESET}
  SKIP_K8S_SCALING=1 ./scripts/test-integration.sh -m core

${COLOR_BOLD}FEATURES:${COLOR_RESET}
  ✓ Runs ONLY @IntegrationTest tagged scenarios
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

# Main execution
main() {
  # Start overall timer
  start_timer

  # Parse -m flag or prompt for module
  if ! parse_module_arg "$@"; then
    MODULE_NAME=$(select_module_interactive) || exit 1
  fi

  log_header "Integration Tests: $MODULE_NAME"

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
    mvn install -pl "$deps" -Dmaven.test.skip=true -q $MAVEN_OPTS || {
      log_error "Failed to compile dependencies"
      exit 1
    }
  else
    log_info "No dependencies to compile"
  fi

  # Step 4: Run integration tests
  log_step_timed 4 5 "Running integration tests for $MODULE_NAME"

  # Build Maven command as array
  local mvn_args=(
    test
    -Pintegration-only
    -pl "$MODULE_NAME"
  )

  # For core, enable parallel execution (hard-coded 4 threads)
  if [[ "$MODULE_NAME" == "test-probe-core" ]]; then
    log_info "Enabling parallel execution (4 threads) for test-probe-core"
    mvn_args+=("-Dcucumber.parallel.enabled=true")
    mvn_args+=("-Dcucumber.parallel.threads=4")
  fi

  # Add MAVEN_OPTS if present
  if [[ -n "$MAVEN_OPTS" ]]; then
    mvn_args+=($MAVEN_OPTS)
  fi

  log_progress "Running integration tests (this may take 3-5 minutes)"

  # Use environment variable for Cucumber tag filter to avoid Maven's arg parsing issues
  # Maven bug MNG-2190: -D properties with spaces in values don't work reliably
  export CUCUMBER_FILTER_TAGS="@IntegrationTest and not @Ignore"

  if mvn "${mvn_args[@]}" 2>&1 | tee /tmp/integration-test-output.log; then
    TEST_EXIT_CODE=0
  else
    TEST_EXIT_CODE=$?
  fi

  # Step 5: Report results
  log_step_timed 5 5 "Test execution complete"

  if [[ $TEST_EXIT_CODE -eq 0 ]]; then
    # Parse test summary
    local test_count=$(grep "Tests run:" /tmp/integration-test-output.log | tail -1 | grep -o "Tests run: [0-9]*" | grep -o "[0-9]*" || echo "unknown")
    local elapsed=$(get_elapsed_time)

    log_success "Integration tests passed for $MODULE_NAME! ✓"
    log_info "  Tests executed: $test_count"
    log_info "  Total execution time: $elapsed"
    return 0
  else
    local elapsed=$(get_elapsed_time)

    log_error "Integration tests failed for $MODULE_NAME (exit code: $TEST_EXIT_CODE)"
    log_error "  Total execution time: $elapsed"

    # Show test failure summary
    echo ""
    log_error "Test Failure Summary:"

    if grep -q "Tests run:" /tmp/integration-test-output.log; then
      grep "Tests run:" /tmp/integration-test-output.log | tail -1 | sed 's/^/  /'
    fi

    if grep -E "<<< FAILURE|<<< ERROR" /tmp/integration-test-output.log > /dev/null; then
      log_error "Failed tests:"
      grep -E "<<< FAILURE|<<< ERROR" /tmp/integration-test-output.log | sed 's/^/  - /' | head -10

      local failure_count=$(grep -E "<<< FAILURE|<<< ERROR" /tmp/integration-test-output.log | wc -l | tr -d ' ')
      if [[ $failure_count -gt 10 ]]; then
        log_info "  ... and $((failure_count - 10)) more failures"
      fi
    fi

    echo ""
    log_info "For full details, see: /tmp/integration-test-output.log"
    log_info "Run with -X for stack traces:"
    log_info "  mvn test -X -Pintegration-only -pl $MODULE_NAME -Dcucumber.filter.tags=\"@IntegrationTest\""

    return $TEST_EXIT_CODE
  fi
}

# Run main function
main "$@"
