#!/bin/bash
# =============================================================================
# Test-Probe Java Quickstart - Cleanup Script
# =============================================================================
# Stops Docker Compose services and cleans up resources.
#
# Usage:
#   ./scripts/cleanup.sh          # Stop services, keep data
#   ./scripts/cleanup.sh --full   # Stop services + remove volumes

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DOCKER_DIR="$PROJECT_DIR/docker"

echo "=== Cleaning up Test-Probe Java Quickstart ==="

cd "$DOCKER_DIR"

if [ "$1" == "--full" ]; then
    echo "Stopping services and removing volumes..."
    docker-compose down -v
else
    echo "Stopping services (keeping volumes)..."
    docker-compose down
fi

echo ""
echo "=== Cleanup complete! ==="
echo ""
echo "To restart: ./scripts/run-tests.sh"
