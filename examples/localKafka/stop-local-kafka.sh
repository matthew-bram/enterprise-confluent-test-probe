#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

NAMESPACE="kafka"
RELEASE_NAME="local-kafka"

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}Stopping Local Kafka${NC}"
echo -e "${YELLOW}========================================${NC}"

# Kill port-forward processes
echo -e "${GREEN}Stopping port-forward processes...${NC}"
pkill -f "port-forward.*kafka.*9092" 2>/dev/null && echo "  ✓ Stopped Kafka port-forward (9092)" || echo "  - No Kafka port-forward found"
pkill -f "port-forward.*schema-registry.*8081" 2>/dev/null && echo "  ✓ Stopped Schema Registry port-forward (8081)" || echo "  - No Schema Registry port-forward found"

# Ask if user wants to uninstall
read -p "$(echo -e ${YELLOW}Do you want to uninstall Kafka? [y/N]: ${NC})" -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${GREEN}Uninstalling Helm release...${NC}"
    helm uninstall ${RELEASE_NAME} -n ${NAMESPACE} 2>/dev/null && echo "  ✓ Uninstalled ${RELEASE_NAME}" || echo "  - Release not found"

    read -p "$(echo -e ${YELLOW}Delete namespace and all resources (including PVCs)? [y/N]: ${NC})" -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${GREEN}Deleting namespace ${NAMESPACE}...${NC}"
        kubectl delete namespace ${NAMESPACE} 2>/dev/null && echo "  ✓ Deleted namespace ${NAMESPACE}" || echo "  - Namespace not found"
    fi
fi

echo -e "\n${GREEN}Cleanup complete!${NC}\n"
