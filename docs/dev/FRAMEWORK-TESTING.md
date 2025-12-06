# Framework Testing Guide

> **This guide is for contributors developing the Test-Probe framework itself.** End users of Test-Probe do not need Docker or Testcontainers - they connect to real Kafka clusters. See the [Getting Started Guide](../user-guide/GETTING-STARTED.md) for user setup.

## Overview

Test-Probe uses [Testcontainers](https://www.testcontainers.org/) internally to spin up real Kafka instances for testing the framework's own code. This ensures the framework works correctly before users deploy it against their production clusters.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Docker Setup](#docker-setup)
- [Running Framework Tests](#running-framework-tests)
- [Environment Isolation](#environment-isolation)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

| Requirement | Version | Purpose |
|-------------|---------|---------|
| Java | 21+ | Runtime and compilation |
| Maven | 3.9+ | Build tool |
| Docker | Desktop 4.x+ | Required for component tests |

## Docker Setup

### Docker Desktop (Recommended)

1. **Install Docker Desktop** from [docker.com](https://www.docker.com/products/docker-desktop/)

2. **Configure resources**:
   ```
   Docker Desktop → Preferences → Resources
   - Memory: 8GB minimum, 16GB recommended
   - CPUs: 4+ cores
   - Disk: 20GB+ free space
   ```

3. **Verify Docker is running**:
   ```bash
   docker info
   ```

### Alternative Docker Runtimes

**Colima (macOS/Linux)**:
```bash
# Install
brew install colima

# Start with recommended resources
colima start --cpu 4 --memory 8 --disk 60

# Verify
colima status
```

**Rancher Desktop**:
- Download from [rancherdesktop.io](https://rancherdesktop.io/)
- Configure resources similar to Docker Desktop

---

## Running Framework Tests

### Test Commands

```bash
# Unit tests only (no Docker required, ~30s)
./scripts/test-unit.sh

# Unit tests for specific module
./scripts/test-unit.sh -m core

# Component tests (requires Docker, ~3min)
./scripts/test-component.sh

# All tests (unit + component, ~5min)
./scripts/test-all.sh

# Coverage report
./scripts/test-coverage.sh
```

### Maven Commands

```bash
# Unit tests only
./mvnw test -Punit-only

# Component tests only
./mvnw test -Pcomponent-only

# All tests with coverage
./mvnw test scoverage:report -Pcoverage
```

### Performance Baselines

| Test Type | Target Time | Notes |
|-----------|-------------|-------|
| Unit tests | ~30s | No Docker required |
| Component tests | ~3min | Includes container startup |
| All tests | ~5min | Full test suite |

---

## Environment Isolation

### Kubernetes Conflict

If you run Kubernetes locally (Docker Desktop K8s, minikube, etc.), you may experience resource conflicts with Testcontainers:

- **Port conflicts**: K8s services may bind to ports Testcontainers needs
- **Resource starvation**: K8s consumes Docker resources
- **Container slot exhaustion**: Too many containers running

### Solution: K8s Scaling Script

See [ADR-TESTING-004](../architecture/adr/ADR-TESTING-004-testcontainers-kubernetes-isolation.md) for the full decision record.

```bash
# Scale down K8s before running component tests
./scripts/k8s-scale.sh down

# Run tests
./scripts/test-component.sh

# Scale K8s back up when done
./scripts/k8s-scale.sh up
```

---

## Troubleshooting

### Issue: "Cannot connect to Docker daemon"

**Error**:
```
org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy
Cannot connect to the Docker daemon at unix:///var/run/docker.sock
```

**Solutions**:

<details>
<summary><strong>macOS with Docker Desktop</strong></summary>

1. **Check Docker Desktop is running**:
   - Look for Docker whale icon in menu bar
   - If not running: Launch Docker Desktop from Applications

2. **Verify Docker is accessible**:
   ```bash
   docker ps
   ```

3. **Restart Docker Desktop**:
   - Click Docker icon → Quit Docker Desktop
   - Relaunch Docker Desktop
   - Wait for "Docker Desktop is running" message
</details>

<details>
<summary><strong>macOS with Colima</strong></summary>

1. **Start Colima**:
   ```bash
   colima start
   ```

2. **Check status**:
   ```bash
   colima status
   ```

3. **Restart if needed**:
   ```bash
   colima restart
   ```
</details>

<details>
<summary><strong>Linux</strong></summary>

1. **Check Docker service**:
   ```bash
   sudo systemctl status docker
   ```

2. **Start Docker service**:
   ```bash
   sudo systemctl start docker
   ```

3. **Add user to docker group** (avoid sudo):
   ```bash
   sudo usermod -aG docker $USER
   newgrp docker
   ```
</details>

### Issue: "Testcontainers timing out"

**Error**:
```
org.testcontainers.containers.ContainerLaunchException: Timed out waiting for container port to open
```

**Solutions**:

1. **Increase Docker RAM allocation**:
   ```
   Docker Desktop → Preferences → Resources
   - RAM: 8GB minimum, 16GB recommended
   ```

2. **Scale down local Kubernetes**:
   ```bash
   ./scripts/k8s-scale.sh down
   ```

3. **Pre-pull Docker images**:
   ```bash
   docker pull confluentinc/cp-kafka:7.6.0
   docker pull confluentinc/cp-schema-registry:7.6.0
   ```

4. **Restart Docker**:
   ```bash
   # Docker Desktop: Click Docker icon → Restart
   # Colima:
   colima restart
   ```

### Issue: "Port already in use"

**Error**:
```
Bind for 0.0.0.0:9092 failed: port is already allocated
```

**Solutions**:

1. **Find process using port**:
   ```bash
   lsof -i :9092
   ```

2. **Stop conflicting process**:
   ```bash
   ./scripts/k8s-scale.sh down  # If K8s is running Kafka
   kill -9 <PID>
   ```

3. **Clean up orphaned containers**:
   ```bash
   docker ps -a --filter "label=org.testcontainers" --format "{{.ID}}" | xargs docker rm -f
   ```

### Issue: "Out of disk space"

**Error**:
```
no space left on device
```

**Solutions**:

1. **Check Docker disk usage**:
   ```bash
   docker system df
   ```

2. **Clean up unused resources**:
   ```bash
   # Remove stopped containers, unused networks, dangling images
   docker system prune -f

   # Remove ALL unused images (more aggressive)
   docker system prune -a -f
   ```

3. **Increase Docker disk allocation**:
   ```
   Docker Desktop → Preferences → Resources → Disk image size
   - Increase to 60GB+ for development
   ```

### Issue: Tests running slowly

**Solutions**:

1. **Check Docker resources**:
   ```bash
   docker system info | grep "Total Memory"
   # Should be: 8GB+ (16GB recommended)
   ```

2. **Scale down Kubernetes**:
   ```bash
   ./scripts/k8s-scale.sh down
   ```

3. **Pre-pull images**:
   ```bash
   docker pull confluentinc/cp-kafka:7.6.0
   docker pull confluentinc/cp-schema-registry:7.6.0
   ```

---

## Debug Logging

Enable verbose logging for troubleshooting:

```xml
<!-- logback-test.xml -->
<logger name="org.testcontainers" level="DEBUG"/>
<logger name="com.github.dockerjava" level="DEBUG"/>
```

---

## Related Documentation

- [CONTRIBUTING.md](../../CONTRIBUTING.md) - Contribution workflow
- [ADR-TESTING-004](../architecture/adr/ADR-TESTING-004-testcontainers-kubernetes-isolation.md) - K8s isolation decision
- [Scripts README](../../scripts/README.md) - Build script reference
