#!/bin/bash
# validation.sh - Environment validation functions for test wrapper scripts
#
# Usage:
#   source scripts/lib/validation.sh
#   validate_all || exit 1

# Load logging if not already loaded
[[ -n "${COLOR_RESET:-}" ]] || source "$(dirname "${BASH_SOURCE[0]}")/logging.sh"

validate_docker() {
  log_info "Checking Docker daemon..."

  if ! docker info > /dev/null 2>&1; then
    log_error "Docker daemon not running"
    log_error "Please start Docker Desktop and try again"
    return 1
  fi

  log_success "Docker daemon is running"
  return 0
}

validate_kubectl() {
  # Only required if K8s scaling is needed
  if [[ "${SKIP_K8S_SCALING:-0}" == "1" ]]; then
    log_debug "Skipping kubectl validation (K8s scaling disabled)"
    return 0
  fi

  log_info "Checking kubectl availability..."

  if ! command -v kubectl > /dev/null 2>&1; then
    log_warning "kubectl not found - K8s scaling will be skipped"
    export SKIP_K8S_SCALING=1
    return 0
  fi

  log_success "kubectl is available"
  return 0
}

validate_resources() {
  log_info "Checking Docker resources..."

  local memory_gb
  memory_gb=$(docker system info 2>/dev/null | grep "Total Memory" | awk '{print $3}' | sed 's/GiB//')

  if [[ -z "$memory_gb" ]]; then
    log_warning "Could not determine Docker memory allocation"
    return 0
  fi

  log_info "Docker memory: ${memory_gb}GB"

  # Warn if less than 8GB
  if (( $(echo "$memory_gb < 8" | bc -l) )); then
    log_warning "Docker has ${memory_gb}GB RAM. Recommended: 12GB+ for K8s + Testcontainers"
    log_warning "Tests may experience timeouts with current allocation"
  else
    log_success "Docker resources look good"
  fi

  return 0
}

validate_maven() {
  log_info "Checking Maven wrapper..."

  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

  if [[ ! -f "$script_dir/mvnw" ]]; then
    log_error "Maven wrapper (mvnw) not found in $script_dir"
    return 1
  fi

  log_success "Maven wrapper found"
  return 0
}

validate_module() {
  local module="${1:-test-probe-core}"

  log_info "Checking module: $module..."

  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

  if [[ ! -d "$script_dir/$module" ]]; then
    log_error "Module not found: $module"
    log_error "Available modules:"
    find "$script_dir" -maxdepth 1 -type d -name "test-probe-*" -exec basename {} \; | sed 's/^/  - /'
    return 1
  fi

  log_success "Module exists: $module"
  return 0
}

validate_disk_space() {
  local min_gb=${1:-5}  # Default minimum 5GB

  log_info "Checking disk space..."

  # Get free space in GB
  local free_space_kb
  local free_space_gb

  if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    free_space_kb=$(df -k . | tail -1 | awk '{print $4}')
  else
    # Linux
    free_space_kb=$(df -k . | tail -1 | awk '{print $4}')
  fi

  free_space_gb=$(echo "scale=1; $free_space_kb / 1024 / 1024" | bc)

  log_info "Free disk space: ${free_space_gb}GB"

  # Check if below minimum
  if (( $(echo "$free_space_gb < $min_gb" | bc -l) )); then
    log_warning "Free space (${free_space_gb}GB) is below recommended minimum (${min_gb}GB)"
    log_warning "Coverage reports and build artifacts require significant disk space"
    log_warning "Consider freeing up disk space before proceeding"
    return 0  # Warning only, don't fail
  fi

  log_success "Disk space sufficient"
  return 0
}

validate_all() {
  log_header "Environment Validation"

  local failed=0

  validate_docker || ((failed++))
  validate_kubectl || ((failed++))
  validate_resources || true  # Non-critical
  validate_maven || ((failed++))

  if [[ $failed -gt 0 ]]; then
    log_error "Validation failed with $failed error(s)"
    return 1
  fi

  log_success "All validations passed"
  return 0
}
