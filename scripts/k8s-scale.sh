#!/bin/bash
# k8s-scale.sh - Helper to manually scale K8s Kafka up/down
#
# Usage:
#   ./scripts/k8s-scale.sh {up|down|status}
#
# Examples:
#   ./scripts/k8s-scale.sh down    # Scale to 0 replicas
#   ./scripts/k8s-scale.sh up      # Scale to 1 replica
#   ./scripts/k8s-scale.sh status  # Show current status

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/logging.sh"

K8S_NAMESPACE="${K8S_NAMESPACE:-kafka}"
ACTION="${1:-status}"

scale_down() {
  log_info "Scaling down K8s Kafka in namespace: $K8S_NAMESPACE"

  kubectl scale statefulset kafka --replicas=0 -n "$K8S_NAMESPACE" 2>&1 || {
    log_warning "Failed to scale Kafka StatefulSet (may not exist)"
  }

  kubectl scale deployment schema-registry --replicas=0 -n "$K8S_NAMESPACE" 2>&1 || {
    log_warning "Failed to scale Schema Registry Deployment (may not exist)"
  }

  log_success "K8s resources scaled down"
}

scale_up() {
  log_info "Scaling up K8s Kafka in namespace: $K8S_NAMESPACE"

  kubectl scale statefulset kafka --replicas=1 -n "$K8S_NAMESPACE" 2>&1 || {
    log_warning "Failed to scale Kafka StatefulSet (may not exist)"
  }

  kubectl scale deployment schema-registry --replicas=1 -n "$K8S_NAMESPACE" 2>&1 || {
    log_warning "Failed to scale Schema Registry Deployment (may not exist)"
  }

  log_success "K8s resources scaled up"
}

show_status() {
  log_info "K8s pod status in namespace: $K8S_NAMESPACE"
  kubectl get pods -n "$K8S_NAMESPACE" || {
    log_warning "No pods found or namespace doesn't exist"
  }
}

case "$ACTION" in
  down)
    scale_down
    ;;
  up)
    scale_up
    ;;
  status)
    show_status
    ;;
  *)
    log_error "Usage: $0 {up|down|status}"
    exit 1
    ;;
esac
