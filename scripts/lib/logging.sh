#!/bin/bash
# logging.sh - Colored output and logging functions for test wrapper scripts
#
# Usage:
#   source scripts/lib/logging.sh
#   log_info "Starting tests..."
#   log_success "Tests passed!"

# Color codes
readonly COLOR_RESET='\033[0m'
readonly COLOR_RED='\033[0;31m'
readonly COLOR_GREEN='\033[0;32m'
readonly COLOR_YELLOW='\033[0;33m'
readonly COLOR_BLUE='\033[0;34m'
readonly COLOR_CYAN='\033[0;36m'
readonly COLOR_BOLD='\033[1m'

# Logging functions

log_info() {
  echo -e "${COLOR_BLUE}[INFO]${COLOR_RESET} $*"
}

log_success() {
  echo -e "${COLOR_GREEN}[SUCCESS]${COLOR_RESET} $*"
}

log_warning() {
  echo -e "${COLOR_YELLOW}[WARNING]${COLOR_RESET} $*"
}

log_error() {
  echo -e "${COLOR_RED}[ERROR]${COLOR_RESET} $*" >&2
}

log_step() {
  local step=$1
  local total=$2
  shift 2
  echo -e "${COLOR_CYAN}[STEP $step/$total]${COLOR_RESET} $*"
}

log_header() {
  echo -e "${COLOR_BOLD}${COLOR_CYAN}=== $* ===${COLOR_RESET}"
}

log_debug() {
  if [[ "${DEBUG:-0}" == "1" ]]; then
    echo -e "${COLOR_CYAN}[DEBUG]${COLOR_RESET} $*"
  fi
}

# Timing functions

# Start a timer (stores start time in global variable)
start_timer() {
  TIMER_START=$(date +%s)
}

# Get elapsed time since start_timer was called
# Returns: formatted duration (e.g., "2m 34s", "45s", "3m 2s")
get_elapsed_time() {
  local end=$(date +%s)
  local elapsed=$((end - TIMER_START))

  if (( elapsed < 60 )); then
    echo "${elapsed}s"
  else
    local minutes=$((elapsed / 60))
    local seconds=$((elapsed % 60))
    echo "${minutes}m ${seconds}s"
  fi
}

# Log step with timing
# Args: $1=step, $2=total, $3+=message
log_step_timed() {
  local step=$1
  local total=$2
  shift 2

  local duration=""
  if [[ -n "${STEP_TIMER_START:-}" ]]; then
    local end=$(date +%s)
    local elapsed=$((end - STEP_TIMER_START))
    if (( elapsed < 60 )); then
      duration=" (${elapsed}s)"
    else
      local minutes=$((elapsed / 60))
      local seconds=$((elapsed % 60))
      duration=" (${minutes}m ${seconds}s)"
    fi
  fi

  echo -e "${COLOR_CYAN}[STEP $step/$total]${COLOR_RESET} $*${duration}"
  STEP_TIMER_START=$(date +%s)
}

# Coverage table functions

# Print coverage table header
print_coverage_table_header() {
  echo ""
  echo "Coverage Summary:"
  echo "┌─────────────────────────┬──────────┬───────────┬──────────┐"
  echo "│ Module                  │ Coverage │ Threshold │ Status   │"
  echo "├─────────────────────────┼──────────┼───────────┼──────────┤"
}

# Print coverage table row
# Args: $1=module name, $2=coverage %, $3=threshold %, $4=pass/fail
print_coverage_table_row() {
  local module=$1
  local coverage=$2
  local threshold=$3
  local passed=$4

  # Pad module name to 23 chars
  local padded_module=$(printf "%-23s" "$module")

  # Format coverage and threshold with right alignment (6 chars)
  local padded_coverage=$(printf "%5s%%" "$coverage")
  local padded_threshold=$(printf "%5s%%" "$threshold")

  # Status with color
  local status
  if [[ "$passed" == "true" ]]; then
    status="${COLOR_GREEN}✓ PASS${COLOR_RESET}  "
  else
    status="${COLOR_RED}✗ FAIL${COLOR_RESET}  "
  fi

  echo -e "│ $padded_module │ $padded_coverage │ $padded_threshold │ $status │"
}

# Print coverage table footer
print_coverage_table_footer() {
  echo "└─────────────────────────┴──────────┴───────────┴──────────┘"
  echo ""
}

# Progress indicator
log_progress() {
  echo -e "${COLOR_CYAN}[PROGRESS]${COLOR_RESET} $* ⏳"
}
