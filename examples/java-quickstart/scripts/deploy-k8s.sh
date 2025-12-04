#!/bin/bash
# =============================================================================
# Test-Probe Java Quickstart - Kubernetes Deployment
# =============================================================================
# SECONDARY deployment option - use Docker Compose for local development.
#
# Prerequisites:
#   - Docker Desktop with Kubernetes enabled, OR
#   - minikube, kind, or other local K8s cluster
#   - kubectl configured
#   - helm 3.x installed
#
# Usage:
#   ./scripts/deploy-k8s.sh          # Deploy to local K8s
#   ./scripts/deploy-k8s.sh --clean  # Clean up before deploying

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
HELM_DIR="$PROJECT_DIR/kubernetes/helm"
LOCAL_KAFKA_DIR="$(dirname "$(dirname "$PROJECT_DIR")")/localKafka"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== Test-Probe Java Quickstart - Kubernetes Deployment ===${NC}"
echo ""

# Check prerequisites
echo "Checking prerequisites..."
command -v kubectl >/dev/null 2>&1 || { echo -e "${RED}kubectl not found${NC}"; exit 1; }
command -v helm >/dev/null 2>&1 || { echo -e "${RED}helm not found${NC}"; exit 1; }
command -v docker >/dev/null 2>&1 || { echo -e "${RED}docker not found${NC}"; exit 1; }

# Check K8s connection
kubectl cluster-info >/dev/null 2>&1 || { echo -e "${RED}Cannot connect to Kubernetes${NC}"; exit 1; }
echo -e "${GREEN}Prerequisites OK${NC}"
echo ""

# Clean up if requested
if [ "$1" == "--clean" ]; then
    echo -e "${YELLOW}Cleaning up existing deployments...${NC}"
    helm uninstall quickstart -n kafka 2>/dev/null || true
    helm uninstall kafka -n kafka 2>/dev/null || true
fi

# Build Docker image
echo -e "${YELLOW}Building Docker image...${NC}"
cd "$PROJECT_DIR"
./scripts/build.sh

# Deploy Kafka (using existing localKafka chart)
if [ -d "$LOCAL_KAFKA_DIR" ]; then
    echo -e "${YELLOW}Deploying Kafka...${NC}"
    helm upgrade --install kafka "$LOCAL_KAFKA_DIR" \
        --namespace kafka \
        --create-namespace \
        --wait \
        --timeout 5m
else
    echo -e "${YELLOW}localKafka chart not found, assuming Kafka is already deployed${NC}"
fi

# Wait for Kafka to be ready
echo -e "${YELLOW}Waiting for Kafka to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=kafka -n kafka --timeout=120s 2>/dev/null || true

# Deploy quickstart application
echo -e "${YELLOW}Deploying Java Quickstart...${NC}"
helm upgrade --install quickstart "$HELM_DIR/java-quickstart" \
    --namespace kafka \
    --wait \
    --timeout 3m

# Setup port forwarding
echo -e "${YELLOW}Setting up port forwarding...${NC}"
kubectl port-forward svc/kafka 9092:9092 -n kafka >/dev/null 2>&1 &
kubectl port-forward svc/schema-registry 8081:8081 -n kafka >/dev/null 2>&1 &

echo ""
echo -e "${GREEN}=== Deployment complete! ===${NC}"
echo ""
echo "Services available:"
echo "  - Kafka: localhost:9092"
echo "  - Schema Registry: http://localhost:8081"
echo ""
echo "Run tests with: ./scripts/run-tests.sh"
echo "Clean up with: ./scripts/cleanup-k8s.sh"
