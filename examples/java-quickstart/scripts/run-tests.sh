#!/bin/bash
# =============================================================================
# Test-Probe Java Quickstart - Run Tests
# =============================================================================
# This is the PRIMARY script for running tests locally.
# It starts Docker Compose services and runs Maven tests.
#
# Usage:
#   ./scripts/run-tests.sh          # Run all tests
#   ./scripts/run-tests.sh json     # Run only JSON format tests
#   ./scripts/run-tests.sh avro     # Run only Avro format tests
#   ./scripts/run-tests.sh protobuf # Run only Protobuf format tests

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DOCKER_DIR="$PROJECT_DIR/docker"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Test-Probe Java Quickstart ===${NC}"
echo ""

# Change to docker directory
cd "$DOCKER_DIR"

# Start infrastructure
echo -e "${YELLOW}Starting Kafka and Schema Registry...${NC}"
docker-compose up -d

# Wait for services to be healthy
echo -e "${YELLOW}Waiting for services to be healthy...${NC}"
timeout=120
elapsed=0
while [ $elapsed -lt $timeout ]; do
    if docker-compose exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; then
        if curl -s http://localhost:8081/subjects > /dev/null 2>&1; then
            echo -e "${GREEN}Services are healthy!${NC}"
            break
        fi
    fi
    sleep 5
    elapsed=$((elapsed + 5))
    echo "Waiting... ($elapsed/$timeout seconds)"
done

if [ $elapsed -ge $timeout ]; then
    echo -e "${RED}Timeout waiting for services to be healthy${NC}"
    docker-compose logs
    exit 1
fi

# Create topics
echo -e "${YELLOW}Creating Kafka topics...${NC}"
docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 \
    --create --if-not-exists --topic order-events-json --partitions 3 --replication-factor 1
docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 \
    --create --if-not-exists --topic inventory-events-avro --partitions 3 --replication-factor 1
docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 \
    --create --if-not-exists --topic payment-events-protobuf --partitions 3 --replication-factor 1

# Run tests
echo -e "${YELLOW}Running tests...${NC}"
cd "$PROJECT_DIR"

# Check for format filter
if [ -n "$1" ]; then
    echo "Filtering tests for format: $1"
    mvn test -Dcucumber.filter.tags="@$1" \
        -Dkafka.bootstrap-servers=localhost:9092 \
        -Dschema.registry.url=http://localhost:8081
else
    mvn test \
        -Dkafka.bootstrap-servers=localhost:9092 \
        -Dschema.registry.url=http://localhost:8081
fi

TEST_EXIT_CODE=$?

if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}=== All tests passed! ===${NC}"
else
    echo -e "${RED}=== Some tests failed! ===${NC}"
fi

echo ""
echo "Test reports available at: target/cucumber-reports/cucumber.html"
echo ""
echo "To stop services: ./scripts/cleanup.sh"

exit $TEST_EXIT_CODE
