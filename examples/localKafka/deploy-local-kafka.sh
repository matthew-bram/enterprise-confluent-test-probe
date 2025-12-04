#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

NAMESPACE="kafka"
RELEASE_NAME="local-kafka"
CHART_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Deploying Local Kafka with KRaft${NC}"
echo -e "${GREEN}========================================${NC}"

# Function to cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Cleaning up port-forward processes...${NC}"
    pkill -f "port-forward.*kafka.*9092" 2>/dev/null || true
    pkill -f "port-forward.*schema-registry.*8081" 2>/dev/null || true
    exit 0
}

trap cleanup SIGINT SIGTERM

# Check if helm is installed
if ! command -v helm &> /dev/null; then
    echo -e "${RED}Error: helm is not installed${NC}"
    exit 1
fi

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}Error: kubectl is not installed${NC}"
    exit 1
fi

# Check if existing deployment exists
if helm list -n ${NAMESPACE} 2>/dev/null | grep -q ${RELEASE_NAME}; then
    RELEASE_STATUS=$(helm list -n ${NAMESPACE} -o json | jq -r ".[] | select(.name==\"${RELEASE_NAME}\") | .status")
    echo -e "${YELLOW}Existing deployment found (status: ${RELEASE_STATUS}). Uninstalling...${NC}"
    helm uninstall ${RELEASE_NAME} -n ${NAMESPACE}

    # Wait for pods to be deleted
    echo "Waiting for pods to be deleted..."
    kubectl wait --for=delete pod/kafka-0 -n ${NAMESPACE} --timeout=30s 2>/dev/null || true
    kubectl wait --for=delete pod -l app=schema-registry -n ${NAMESPACE} --timeout=30s 2>/dev/null || true
    sleep 5
fi

# Install Kafka using Helm
echo -e "${GREEN}Installing Kafka with Helm...${NC}"
helm install ${RELEASE_NAME} ${CHART_DIR} \
    -n ${NAMESPACE} \
    --create-namespace \
    --wait \
    --timeout 5m

# Wait for Kafka pod to be ready
echo -e "${GREEN}Waiting for Kafka pod to be ready...${NC}"
kubectl wait --for=condition=ready pod/kafka-0 -n ${NAMESPACE} --timeout=120s

# Wait for Schema Registry to be ready
echo -e "${GREEN}Waiting for Schema Registry to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=schema-registry -n ${NAMESPACE} --timeout=120s

# Kill any existing port-forward processes
pkill -f "port-forward.*kafka.*9092" 2>/dev/null || true
pkill -f "port-forward.*schema-registry.*8081" 2>/dev/null || true

# Start port-forwarding for Kafka
echo -e "${GREEN}Setting up port-forward for Kafka (localhost:9092)...${NC}"
kubectl port-forward -n ${NAMESPACE} svc/kafka 9092:9092 > /dev/null 2>&1 &
KAFKA_PF_PID=$!

# Start port-forwarding for Schema Registry
echo -e "${GREEN}Setting up port-forward for Schema Registry (localhost:8081)...${NC}"
kubectl port-forward -n ${NAMESPACE} svc/schema-registry 8081:8081 > /dev/null 2>&1 &
SCHEMA_PF_PID=$!

# Wait a moment for port-forwards to establish
sleep 2

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}Deployment Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\n${GREEN}Kafka is now accessible at:${NC}"
echo -e "  Kafka Broker:      ${YELLOW}localhost:9092${NC}"
echo -e "  Schema Registry:   ${YELLOW}http://localhost:8081${NC}"
echo -e "\n${GREEN}Port-forward PIDs:${NC}"
echo -e "  Kafka:             ${KAFKA_PF_PID}"
echo -e "  Schema Registry:   ${SCHEMA_PF_PID}"
echo -e "\n${YELLOW}Press Ctrl+C to stop port-forwarding and exit${NC}"
echo -e "${YELLOW}Or run './stop-local-kafka.sh' from another terminal to cleanup${NC}\n"

# Keep the script running
wait
