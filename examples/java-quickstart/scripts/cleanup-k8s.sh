#!/bin/bash
# =============================================================================
# Test-Probe Java Quickstart - Kubernetes Cleanup
# =============================================================================
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== Cleaning up Kubernetes deployments ===${NC}"

# Kill port-forward processes
echo -e "${YELLOW}Stopping port-forward processes...${NC}"
pkill -f "kubectl port-forward" 2>/dev/null || true

# Uninstall Helm releases
echo -e "${YELLOW}Uninstalling quickstart...${NC}"
helm uninstall quickstart -n kafka 2>/dev/null || true

echo ""
read -p "Also remove Kafka? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Uninstalling Kafka...${NC}"
    helm uninstall kafka -n kafka 2>/dev/null || true
fi

read -p "Delete kafka namespace? (y/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Deleting kafka namespace...${NC}"
    kubectl delete namespace kafka 2>/dev/null || true
fi

echo ""
echo -e "${GREEN}=== Cleanup complete! ===${NC}"
