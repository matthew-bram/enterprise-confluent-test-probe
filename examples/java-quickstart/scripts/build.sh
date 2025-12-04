#!/bin/bash
# =============================================================================
# Test-Probe Java Quickstart - Build Script
# =============================================================================
# Builds the Maven project and Docker image.
#
# Usage:
#   ./scripts/build.sh              # Build project + Docker image
#   ./scripts/build.sh --no-docker  # Build project only
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Building Test-Probe Java Quickstart ===${NC}"
cd "$PROJECT_DIR"

# Build Maven project
echo -e "${YELLOW}Building Maven project...${NC}"
mvn clean compile -q

if [ "$1" != "--no-docker" ]; then
    # Build Docker image
    echo ""
    echo -e "${YELLOW}Building Docker image...${NC}"
    docker build -t test-probe/java-quickstart:latest -f docker/Dockerfile .
    echo ""
    echo -e "${GREEN}Docker image built: test-probe/java-quickstart:latest${NC}"
fi

echo ""
echo -e "${GREEN}=== Build complete! ===${NC}"
echo ""
echo "Run the application:"
echo "  mvn exec:java"
echo ""
echo "Or with Docker:"
echo "  docker run -p 8080:8080 test-probe/java-quickstart:latest"
