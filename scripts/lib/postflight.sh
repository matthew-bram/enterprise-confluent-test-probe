#!/bin/bash
# postflight.sh - Postflight environment restoration after tests
#
# Usage:
#   source scripts/lib/postflight.sh
#   trap postflight_all EXIT

# Load dependencies
[[ -n "${COLOR_RESET:-}" ]] || source "$(dirname "${BASH_SOURCE[0]}")/logging.sh"

# Configuration
K8S_NAMESPACE="${K8S_NAMESPACE:-kafka}"
SKIP_K8S_RESTORE="${SKIP_K8S_RESTORE:-0}"
CLEANUP_DOCKER="${CLEANUP_DOCKER:-0}"

postflight_scale_k8s() {
  # Skip in CI or if explicitly disabled
  if [[ -n "${CI:-}" ]] || [[ -n "${GITHUB_ACTIONS:-}" ]] || [[ "$SKIP_K8S_RESTORE" == "1" ]]; then
    log_info "[CI MODE] Skipping K8s restore (not present in CI)"
    return 0
  fi

  # Only restore if we scaled down (i.e., SKIP_K8S_SCALING was not set)
  if [[ "${SKIP_K8S_SCALING:-0}" == "1" ]]; then
    log_info "Skipping K8s restore (was not scaled down)"
    return 0
  fi

  log_info "Restoring K8s Kafka in namespace: $K8S_NAMESPACE"

  # Scale Kafka StatefulSet back to 1
  if kubectl get statefulset kafka -n "$K8S_NAMESPACE" > /dev/null 2>&1; then
    kubectl scale statefulset kafka --replicas=1 -n "$K8S_NAMESPACE" 2>&1 | sed 's/^/  /' || {
      log_warning "Failed to restore Kafka StatefulSet"
    }
  fi

  # Scale Schema Registry Deployment back to 1
  if kubectl get deployment schema-registry -n "$K8S_NAMESPACE" > /dev/null 2>&1; then
    kubectl scale deployment schema-registry --replicas=1 -n "$K8S_NAMESPACE" 2>&1 | sed 's/^/  /' || {
      log_warning "Failed to restore Schema Registry Deployment"
    }
  fi

  log_success "K8s resources restored"
  return 0
}

postflight_cleanup() {
  if [[ "$CLEANUP_DOCKER" != "1" ]]; then
    log_debug "Skipping Docker cleanup (CLEANUP_DOCKER not set)"
    return 0
  fi

  log_info "Cleaning up Docker test artifacts..."

  # 1. Stop and remove Testcontainers containers
  local test_containers
  test_containers=$(docker ps -a --filter "label=org.testcontainers" --format "{{.ID}}" 2>/dev/null)

  if [[ -n "$test_containers" ]]; then
    echo "$test_containers" | xargs docker rm -f 2>/dev/null || true
    log_success "Removed Testcontainers containers"
  fi

  # 2. Remove Testcontainers networks
  local test_networks
  test_networks=$(docker network ls --filter "name=testcontainers" --format "{{.Name}}" 2>/dev/null)

  if [[ -n "$test_networks" ]]; then
    echo "$test_networks" | while read -r network; do
      docker network rm "$network" 2>/dev/null || true
    done
    log_success "Removed Testcontainers networks"
  fi

  # 3. Prune orphaned Docker resources (safe - only removes unused)
  log_info "Pruning orphaned Docker resources..."
  docker system prune -f --volumes 2>&1 | grep -v "^Total" | sed 's/^/  /' || true

  # 4. Report resource reclamation
  log_success "Docker cleanup complete - resources freed for other use"

  return 0
}

postflight_all() {
  log_header "Postflight: Restoring Environment"

  postflight_scale_k8s
  postflight_cleanup

  log_success "Postflight complete - environment restored"
  return 0
}
