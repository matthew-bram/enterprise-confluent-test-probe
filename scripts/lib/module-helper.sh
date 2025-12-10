#!/bin/bash
# module-helper.sh - Module name resolution and interactive selection
# Compatible with bash 3.x (macOS default)

# Load dependencies
[[ -n "${COLOR_RESET:-}" ]] || source "$(dirname "${BASH_SOURCE[0]}")/logging.sh"

# Resolve short module name to full Maven artifact name
# Args: $1 = short name (e.g., "core")
# Returns: Full name (e.g., "test-probe-core") or exits on error
resolve_module_name() {
  local short_name="$1"

  if [[ -z "$short_name" ]]; then
    log_error "Module name is required"
    return 1
  fi

  # Check if it's already a full name
  case "$short_name" in
    test-probe-common|test-probe-core|test-probe-services|test-probe-external-services|test-probe-interfaces)
      echo "$short_name"
      return 0
      ;;
  esac

  # Resolve short name to full name
  case "$short_name" in
    common)
      echo "test-probe-common"
      return 0
      ;;
    core)
      echo "test-probe-core"
      return 0
      ;;
    services)
      echo "test-probe-services"
      return 0
      ;;
    external)
      echo "test-probe-external-services"
      return 0
      ;;
    interfaces)
      echo "test-probe-interfaces"
      return 0
      ;;
    *)
      log_error "Unknown module: '$short_name'"
      log_info "Available modules: common, core, services, external, interfaces"
      return 1
      ;;
  esac
}

# Get module dependencies (comma-separated Maven -pl format)
# Args: $1 = full module name
# Returns: Comma-separated dependency list
get_module_deps() {
  local module="$1"

  case "$module" in
    test-probe-common)
      echo ""  # No dependencies
      ;;
    test-probe-core)
      echo "test-probe-common"
      ;;
    test-probe-services)
      echo "test-probe-common,test-probe-core"
      ;;
    test-probe-external-services)
      echo "test-probe-common,test-probe-core"
      ;;
    test-probe-interfaces)
      echo "test-probe-common,test-probe-core,test-probe-services"
      ;;
    *)
      echo ""
      ;;
  esac
}

# Interactive module selection
# Returns: Selected full module name
select_module_interactive() {
  log_info "Available modules:"
  echo ""
  echo "  1) common      (test-probe-common)"
  echo "  2) core        (test-probe-core)"
  echo "  3) services    (test-probe-services)"
  echo "  4) external    (test-probe-external-services)"
  echo "  5) interfaces  (test-probe-interfaces)"
  echo ""

  read -p "Select module (1-5): " selection

  case "$selection" in
    1) echo "test-probe-common" ;;
    2) echo "test-probe-core" ;;
    3) echo "test-probe-services" ;;
    4) echo "test-probe-external-services" ;;
    5) echo "test-probe-interfaces" ;;
    *)
      log_error "Invalid selection: $selection"
      return 1
      ;;
  esac
}

# Validate module exists in project
# Args: $1 = full module name
# Returns: 0 if exists, 1 otherwise
validate_module() {
  local module="$1"
  local module_dir="$PROJECT_ROOT/$module"

  if [[ ! -d "$module_dir" ]]; then
    log_error "Module directory not found: $module_dir"
    return 1
  fi

  if [[ ! -f "$module_dir/pom.xml" ]]; then
    log_error "Module pom.xml not found: $module_dir/pom.xml"
    return 1
  fi

  return 0
}

# Parse command line arguments for -m flag
# Args: All script arguments ("$@")
# Sets: MODULE_NAME (global variable)
# Returns: 0 if found, 1 if not provided (interactive mode needed)
parse_module_arg() {
  while [[ $# -gt 0 ]]; do
    case $1 in
      -m|--module)
        if [[ -z "${2:-}" ]]; then
          log_error "Module name required after -m flag"
          exit 1
        fi
        MODULE_NAME=$(resolve_module_name "$2") || exit 1
        return 0
        ;;
      *)
        shift
        ;;
    esac
    [[ $# -gt 0 ]] && shift
  done

  # No -m flag found, need interactive mode
  return 1
}
