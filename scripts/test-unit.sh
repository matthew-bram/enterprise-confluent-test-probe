#!/bin/bash
# test-unit.sh - Run unit tests (no environment preparation needed)
#
# Usage:
#   ./scripts/test-unit.sh [-m <module>] [-h|--help]
#
# Examples:
#   ./scripts/test-unit.sh -m core
#   ./scripts/test-unit.sh -m interfaces
#   ./scripts/test-unit.sh  (interactive mode)

set -euo pipefail

# Determine script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load libraries
source "$SCRIPT_DIR/lib/logging.sh"
source "$SCRIPT_DIR/lib/validation.sh"
source "$SCRIPT_DIR/lib/module-helper.sh"

# Configuration
MAVEN_OPTS="${MAVEN_OPTS:-}"

# Show help
show_help() {
  cat << EOF
${COLOR_BOLD}${COLOR_CYAN}test-unit.sh${COLOR_RESET} - Run unit tests for a module

${COLOR_BOLD}USAGE:${COLOR_RESET}
  ./scripts/test-unit.sh [-m MODULE] [-h|--help]

${COLOR_BOLD}OPTIONS:${COLOR_RESET}
  -m, --module MODULE    Module to test (common|core|services|external|interfaces)
  -h, --help             Show this help message

${COLOR_BOLD}EXAMPLES:${COLOR_RESET}
  ${COLOR_CYAN}# Test core module${COLOR_RESET}
  ./scripts/test-unit.sh -m core

  ${COLOR_CYAN}# Test services module${COLOR_RESET}
  ./scripts/test-unit.sh -m services

  ${COLOR_CYAN}# Interactive mode (prompts for module)${COLOR_RESET}
  ./scripts/test-unit.sh

${COLOR_BOLD}FEATURES:${COLOR_RESET}
  ✓ Fast unit tests (~5-10s for most modules)
  ✓ Automatic dependency compilation (no dependency tests)
  ✓ Execution timing for each step
  ✓ Test failure summaries

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

# Main execution
main() {
  # Start overall timer
  start_timer

  # Parse -m flag or prompt for module
  if ! parse_module_arg "$@"; then
    MODULE_NAME=$(select_module_interactive) || exit 1
  fi

  log_header "Unit Tests: $MODULE_NAME"

  # Step 1: Validation
  log_step_timed 1 3 "Validating environment"
  validate_docker || exit 1
  validate_maven || exit 1
  validate_module "$MODULE_NAME" || exit 1

  # Step 2: Compile dependencies (no tests)
  log_step_timed 2 3 "Compiling dependencies"
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

  # Step 3: Run unit tests
  log_step_timed 3 3 "Running unit tests for $MODULE_NAME"
  log_progress "Executing unit tests"

  if mvn test -Punit-only -pl "$MODULE_NAME" $MAVEN_OPTS 2>&1 | tee /tmp/unit-test-output.log; then
    # Parse test summary
    local test_count=$(grep "Tests run:" /tmp/unit-test-output.log | tail -1 | grep -o "Tests run: [0-9]*" | grep -o "[0-9]*")
    local elapsed=$(get_elapsed_time)

    log_success "Unit tests passed for $MODULE_NAME! ✓"
    log_info "  Tests executed: $test_count"
    log_info "  Total execution time: $elapsed"
    return 0
  else
    local exit_code=$?
    local elapsed=$(get_elapsed_time)

    log_error "Unit tests failed for $MODULE_NAME (exit code: $exit_code)"
    log_error "  Total execution time: $elapsed"

    # Show test failure summary
    echo ""
    log_error "Test Failure Summary:"

    if grep -q "Tests run:" /tmp/unit-test-output.log; then
      grep "Tests run:" /tmp/unit-test-output.log | tail -1 | sed 's/^/  /'
    fi

    if grep -E "<<< FAILURE|<<< ERROR" /tmp/unit-test-output.log > /dev/null; then
      log_error "Failed tests:"
      grep -E "<<< FAILURE|<<< ERROR" /tmp/unit-test-output.log | sed 's/^/  - /' | head -10

      local failure_count=$(grep -E "<<< FAILURE|<<< ERROR" /tmp/unit-test-output.log | wc -l | tr -d ' ')
      if [[ $failure_count -gt 10 ]]; then
        log_info "  ... and $((failure_count - 10)) more failures"
      fi
    fi

    echo ""
    log_info "For full details, see: /tmp/unit-test-output.log"
    log_info "Run with -X for stack traces:"
    log_info "  mvn test -X -Punit-only -pl $MODULE_NAME"

    return $exit_code
  fi
}

# Run main function
main "$@"
