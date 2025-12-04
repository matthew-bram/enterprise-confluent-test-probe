#!/bin/bash
# preflight.sh - Preflight environment preparation for Testcontainers tests
#
# Usage:
#   source scripts/lib/preflight.sh
#   preflight_all

# Load dependencies
[[ -n "${COLOR_RESET:-}" ]] || source "$(dirname "${BASH_SOURCE[0]}")/logging.sh"

# Configuration
K8S_NAMESPACE="${K8S_NAMESPACE:-kafka}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-10}"
SKIP_K8S_SCALING="${SKIP_K8S_SCALING:-0}"

preflight_scale_k8s() {
  # Skip in CI or if explicitly disabled
  if [[ -n "${CI:-}" ]] || [[ -n "${GITHUB_ACTIONS:-}" ]] || [[ "$SKIP_K8S_SCALING" == "1" ]]; then
    log_info "[CI MODE] Skipping K8s scaling (not present in CI)"
    return 0
  fi

  log_info "Scaling down K8s Kafka in namespace: $K8S_NAMESPACE"

  # Scale Kafka StatefulSet to 0
  if kubectl get statefulset kafka -n "$K8S_NAMESPACE" > /dev/null 2>&1; then
    kubectl scale statefulset kafka --replicas=0 -n "$K8S_NAMESPACE" 2>&1 | sed 's/^/  /' || {
      log_warning "Failed to scale Kafka StatefulSet (may not exist)"
    }
  else
    log_debug "Kafka StatefulSet not found in $K8S_NAMESPACE (OK)"
  fi

  # Scale Schema Registry Deployment to 0
  if kubectl get deployment schema-registry -n "$K8S_NAMESPACE" > /dev/null 2>&1; then
    kubectl scale deployment schema-registry --replicas=0 -n "$K8S_NAMESPACE" 2>&1 | sed 's/^/  /' || {
      log_warning "Failed to scale Schema Registry Deployment (may not exist)"
    }
  else
    log_debug "Schema Registry Deployment not found in $K8S_NAMESPACE (OK)"
  fi

  log_success "K8s resources scaled down"
  return 0
}

preflight_wait() {
  # Skip in CI
  if [[ -n "${CI:-}" ]] || [[ -n "${GITHUB_ACTIONS:-}" ]] || [[ "$SKIP_K8S_SCALING" == "1" ]]; then
    return 0
  fi

  log_info "Waiting for K8s pods to terminate (timeout: ${WAIT_TIMEOUT}s)..."

  local elapsed=0
  while [[ $elapsed -lt $WAIT_TIMEOUT ]]; do
    local pod_count
    pod_count=$(kubectl get pods -n "$K8S_NAMESPACE" --no-headers 2>/dev/null | wc -l | tr -d ' ')

    if [[ "$pod_count" == "0" ]]; then
      log_success "All K8s pods terminated"
      return 0
    fi

    sleep 1
    ((elapsed++))
  done

  log_warning "Timeout waiting for pods to terminate (${pod_count} pods still running)"
  log_warning "Continuing anyway - tests may experience resource contention"
  return 0
}

preflight_verify_clean() {
  log_info "Verifying clean Docker environment..."

  local kafka_containers
  kafka_containers=$(docker ps --format "{{.Names}}" | grep -i "kafka\|schema" | wc -l | tr -d ' ')

  if [[ "$kafka_containers" -gt 0 ]]; then
    log_warning "Found $kafka_containers Kafka/Schema containers still running:"
    docker ps --format "table {{.Names}}\t{{.Status}}" | grep -i "kafka\|schema" | sed 's/^/  /'
    log_warning "Tests may experience port conflicts"
  else
    log_success "No conflicting Kafka/Schema containers found"
  fi

  return 0
}

preflight_all() {
  log_header "Preflight: Preparing Test Environment"

  preflight_scale_k8s
  preflight_wait
  preflight_verify_clean

  log_success "Preflight complete - environment ready for Testcontainers"
  return 0
}
