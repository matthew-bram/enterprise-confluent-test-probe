#!/bin/bash
# =============================================================================
# Test-Probe Java Quickstart - Application Runner
# =============================================================================
# Starts the Test-Probe framework as a standalone application.
# This script handles:
#   1. Starting Kafka and Schema Registry via Docker Compose
#   2. Waiting for services to be healthy
#   3. Building the application
#   4. Running the main application
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Test-Probe Java Quickstart           ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# -----------------------------------------------------------------------------
# Step 1: Start Docker Compose Services
# -----------------------------------------------------------------------------
echo -e "${YELLOW}[1/4] Starting Kafka and Schema Registry...${NC}"
cd "${PROJECT_DIR}/docker"

if ! docker-compose ps | grep -q "Up"; then
    docker-compose up -d
    echo "Waiting for services to start..."
    sleep 5
else
    echo "Services already running."
fi

# -----------------------------------------------------------------------------
# Step 2: Wait for Kafka to be healthy
# -----------------------------------------------------------------------------
echo -e "${YELLOW}[2/4] Waiting for Kafka to be ready...${NC}"
MAX_WAIT=60
WAIT_COUNT=0
until docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list > /dev/null 2>&1; do
    WAIT_COUNT=$((WAIT_COUNT + 1))
    if [ $WAIT_COUNT -ge $MAX_WAIT ]; then
        echo -e "${RED}Error: Kafka did not become ready in ${MAX_WAIT} seconds${NC}"
        exit 1
    fi
    echo "  Waiting for Kafka... (${WAIT_COUNT}s)"
    sleep 1
done
echo -e "${GREEN}  Kafka is ready!${NC}"

# -----------------------------------------------------------------------------
# Step 3: Wait for Schema Registry to be healthy
# -----------------------------------------------------------------------------
echo -e "${YELLOW}[3/4] Waiting for Schema Registry to be ready...${NC}"
WAIT_COUNT=0
until curl -s http://localhost:8081/ > /dev/null 2>&1; do
    WAIT_COUNT=$((WAIT_COUNT + 1))
    if [ $WAIT_COUNT -ge $MAX_WAIT ]; then
        echo -e "${RED}Error: Schema Registry did not become ready in ${MAX_WAIT} seconds${NC}"
        exit 1
    fi
    echo "  Waiting for Schema Registry... (${WAIT_COUNT}s)"
    sleep 1
done
echo -e "${GREEN}  Schema Registry is ready!${NC}"

# -----------------------------------------------------------------------------
# Step 4: Build and Run the Application
# -----------------------------------------------------------------------------
echo -e "${YELLOW}[4/4] Building and running the application...${NC}"
cd "${PROJECT_DIR}"

# Build the application
mvn clean compile -q

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Starting Test-Probe Application      ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "REST API will be available at: http://localhost:8080"
echo "Press Ctrl+C to stop the application"
echo ""

# Run the application
mvn exec:java

# Note: The script will block here until the user presses Ctrl+C
