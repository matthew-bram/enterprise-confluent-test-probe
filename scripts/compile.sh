#!/bin/bash
# compile.sh - Compile a module (no tests, fast)
#
# Usage:
#   ./scripts/compile.sh [-m <module>] [-h|--help]
#
# Examples:
#   ./scripts/compile.sh -m core
#   ./scripts/compile.sh -m services
#   ./scripts/compile.sh  (interactive mode)

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
${COLOR_BOLD}${COLOR_CYAN}compile.sh${COLOR_RESET} - Fast compilation (no tests)

${COLOR_BOLD}USAGE:${COLOR_RESET}
  ./scripts/compile.sh [-m MODULE] [-h|--help]

${COLOR_BOLD}OPTIONS:${COLOR_RESET}
  -m, --module MODULE    Module to compile (common|core|services|external|interfaces)
  -h, --help             Show this help message

${COLOR_BOLD}EXAMPLES:${COLOR_RESET}
  ${COLOR_CYAN}# Compile core module${COLOR_RESET}
  ./scripts/compile.sh -m core

  ${COLOR_CYAN}# Compile services module${COLOR_RESET}
  ./scripts/compile.sh -m services

  ${COLOR_CYAN}# Interactive mode (prompts for module)${COLOR_RESET}
  ./scripts/compile.sh

${COLOR_BOLD}FEATURES:${COLOR_RESET}
  ✓ Fast compilation (skips all tests)
  ✓ Automatic dependency compilation
  ✓ Execution timing for each step
  ✓ Progress indicators

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

  log_header "Compile: $MODULE_NAME"

  # Step 1: Validation
  log_step_timed 1 2 "Validating environment"
  validate_maven || exit 1
  validate_module "$MODULE_NAME" || exit 1

  # Step 2: Compile
  log_step_timed 2 2 "Compiling $MODULE_NAME (with dependencies, no tests)"
  cd "$PROJECT_ROOT"

  # Get dependencies
  local deps=$(get_module_deps "$MODULE_NAME")

  # Build module list: dependencies + target module
  local modules="$MODULE_NAME"
  if [[ -n "$deps" ]]; then
    modules="$deps,$MODULE_NAME"
    log_info "Compiling: $modules"
  else
    log_info "Compiling: $MODULE_NAME (no dependencies)"
  fi

  # Compile (skip all tests)
  log_progress "Compiling modules (this may take a moment)"
  if mvn install -pl "$modules" -Dmaven.test.skip=true -q $MAVEN_OPTS; then
    local elapsed=$(get_elapsed_time)
    log_success "Compilation successful for $MODULE_NAME! ✓"
    log_info "  Total execution time: $elapsed"
    return 0
  else
    local exit_code=$?
    local elapsed=$(get_elapsed_time)
    log_error "Compilation failed for $MODULE_NAME (exit code: $exit_code)"
    log_error "  Total execution time: $elapsed"
    log_info "Run with -X for detailed error messages:"
    log_info "  mvn install -pl $modules -Dmaven.test.skip=true -X"
    return $exit_code
  fi
}

# Run main function
main "$@"
