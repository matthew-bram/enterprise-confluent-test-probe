# Test Probe Build Scripts

**Purpose**: Developer-friendly wrapper scripts that bake in best practices for Maven builds, testing, and CI/CD.

---

## Philosophy

1. **Dependencies compile without tests**: When working on a module, dependent modules are compiled but tests are skipped (fast!)
2. **Environment management**: Automatic K8s scaling, Docker cleanup, preflight/postflight
3. **Best practices baked in**: No need to remember Maven flags, profiles, or options
4. **Big red buttons**: Simple commands for common tasks

---

## Quick Reference

### Testing

```bash
# Unit tests only (fast)
./scripts/test-unit.sh -m core

# Component tests only (includes K8s management)
./scripts/test-component.sh -m core

# All tests (unit + component)
./scripts/test-all.sh -m core

# All tests + coverage report
./scripts/test-coverage.sh -m core
```

### Building

```bash
# Compile only (no tests, fast)
./scripts/compile.sh -m services

# Full CI/CD build (all modules, all tests, fat jar)
./scripts/build-ci.sh
```

### Module Names

| Short Name | Full Module Name |
|-----------|-----------------|
| `common` | `test-probe-common` |
| `core` | `test-probe-core` |
| `services` | `test-probe-services` |
| `interfaces` | `test-probe-interfaces` |

### Utilities

```bash
# Manually scale K8s up/down
./scripts/k8s-scale.sh {up|down|status}
```

---

## Detailed Command Reference

### `test-unit.sh -m <module>`

**Purpose**: Run unit tests only for a module

**What it does**:
1. Compiles dependent modules (skips their tests)
2. Compiles target module (source + test code)
3. Runs unit tests only (ScalaTest)
4. Skips component tests (Cucumber)

**Example**:
```bash
./scripts/test-unit.sh -m services
```

**Under the hood**:
```bash
# 1. Compile dependencies (no tests)
mvn install -pl test-probe-common,test-probe-core -Dmaven.test.skip=true -q

# 2. Run unit tests only
mvn test -Punit-only -pl test-probe-services
```

**Environment**:
- No K8s management (unit tests don't need Testcontainers)
- No Docker cleanup (minimal containers)

**Expected output**:
```
[INFO] Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

### `test-component.sh -m <module>`

**Purpose**: Run component tests only for a module

**What it does**:
1. Validates environment (Docker, kubectl, Maven)
2. Scales down K8s Kafka/Schema Registry (preflight)
3. Compiles dependent modules (skips their tests)
4. Compiles target module (source + test code)
5. Runs component tests only (Cucumber BDD)
6. **Parallel execution for test-probe-core** (4 threads, hard-coded)
7. Cleans up Docker resources (postflight)
8. Restores K8s Kafka/Schema Registry (postflight)

**Example**:
```bash
./scripts/test-component.sh -m core
```

**Under the hood**:
```bash
# 1. Preflight (K8s scaling, validation)
# 2. Compile dependencies (no tests)
mvn install -pl test-probe-common -Dmaven.test.skip=true -q

# 3. Run component tests (parallel for core)
mvn test -Pcomponent-only -pl test-probe-core \
  -Dcucumber.parallel.enabled=true \
  -Dcucumber.parallel.threads=4

# 4. Postflight (Docker cleanup, K8s restore)
```

**Environment**:
- K8s scaled down before tests
- K8s restored after tests
- Docker cleanup enabled
- Parallel execution (core only, 4 threads)

**Expected output**:
```
[INFO] Tests run: 168, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
Time: ~159-173 seconds (parallel)
```

**Requirements**:
- Docker Desktop: 16GB RAM (for parallel execution)
- kubectl configured (for K8s scaling)

---

### `test-all.sh -m <module>`

**Purpose**: Run ALL tests (unit + component) for a module

**What it does**:
1. Validates environment (Docker, kubectl, Maven)
2. Scales down K8s (preflight)
3. Compiles dependent modules (skips their tests)
4. Compiles target module
5. Runs unit tests (ScalaTest)
6. Runs component tests (Cucumber)
7. **Parallel execution for test-probe-core** (4 threads, component tests only)
8. Cleans up Docker resources (postflight)
9. Restores K8s (postflight)

**Example**:
```bash
./scripts/test-all.sh -m core
```

**Under the hood**:
```bash
# 1. Preflight
# 2. Compile dependencies
mvn install -pl test-probe-common -Dmaven.test.skip=true -q

# 3. Run all tests
mvn test -pl test-probe-core \
  -Dcucumber.parallel.enabled=true \
  -Dcucumber.parallel.threads=4

# 4. Postflight
```

**Expected output**:
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S (ScalaTest)
[INFO] -------------------------------------------------------
[INFO] Tests run: 261, Failures: 0, Errors: 0, Skipped: 0

[INFO] -------------------------------------------------------
[INFO]  T E S T S (Cucumber)
[INFO] -------------------------------------------------------
[INFO] Tests run: 168, Failures: 0, Errors: 0, Skipped: 0

[INFO] BUILD SUCCESS
```

---

### `test-coverage.sh -m <module>`

**Purpose**: Run all tests and generate Scoverage coverage report

**What it does**:
1. Validates environment
2. Scales down K8s (preflight)
3. Compiles dependent modules (skips their tests)
4. Compiles target module
5. Runs ALL tests (unit + component)
6. **Parallel execution for test-probe-core** (4 threads, component tests)
7. Generates Scoverage XML + HTML report
8. Prints path to HTML report (`target/site/scoverage/index.html`)
9. Cleans up Docker resources (postflight)
10. Restores K8s (postflight)

**Example**:
```bash
./scripts/test-coverage.sh -m core
```

**Under the hood**:
```bash
# 1. Preflight
# 2. Compile dependencies
mvn install -pl test-probe-common -Dmaven.test.skip=true -q

# 3. Run tests + coverage
mvn clean test scoverage:report -pl test-probe-core \
  -Dcucumber.parallel.enabled=true \
  -Dcucumber.parallel.threads=4

# 4. Postflight
```

**Expected output**:
```
[INFO] Tests run: 429, Failures: 0, Errors: 0, Skipped: 0
[INFO] Scoverage report generated successfully
[INFO]
[SUCCESS] Coverage Report: /Users/.../test-probe-core/target/site/scoverage/index.html
[INFO]
[INFO] Statement Coverage: 72.5%
[INFO] Branch Coverage: 68.3%
[INFO]
[INFO] BUILD SUCCESS
```

**Coverage thresholds**:
- Overall: 70% minimum
- Actors: 85% minimum
- Business logic: 80% minimum

---

### `compile.sh -m <module>`

**Purpose**: Compile a module (no tests, fast)

**What it does**:
1. Compiles dependent modules (skips their tests)
2. Compiles target module (source code only, skips test code)
3. No test execution

**Example**:
```bash
./scripts/compile.sh -m services
```

**Under the hood**:
```bash
# Compile dependencies + target module (no tests)
mvn install -pl test-probe-common,test-probe-core,test-probe-services \
  -Dmaven.test.skip=true -q
```

**Expected output**:
```
[INFO] Building Test Probe - Services 0.0.1-SNAPSHOT
[INFO] BUILD SUCCESS
Time: ~15-20 seconds
```

**Use cases**:
- Quick syntax checking
- Verify code compiles before running tests
- IDE integration (compile before run)

---

### `build-ci.sh`

**Purpose**: Full CI/CD build (all modules, all tests, fat jar)

**What it does**:
1. Validates environment
2. Scales down K8s (preflight)
3. Compiles ALL modules
4. Runs ALL unit tests (all modules)
5. Runs ALL component tests (all modules)
6. **Parallel execution for test-probe-core** (4 threads)
7. Generates coverage reports (all modules)
8. Packages fat jar (parent pom)
9. Validates artifacts
10. Cleans up Docker resources (postflight)
11. Restores K8s (postflight)

**Example**:
```bash
./scripts/build-ci.sh
```

**Under the hood**:
```bash
# 1. Preflight
# 2. Clean build
mvn clean

# 3. Run all tests (all modules)
mvn verify \
  -Dcucumber.parallel.enabled=true \
  -Dcucumber.parallel.threads=4

# 4. Generate coverage reports
mvn scoverage:report

# 5. Package fat jar
mvn package -DskipTests

# 6. Postflight
```

**Expected output**:
```
[INFO] Reactor Summary:
[INFO]
[INFO] Test Probe - Common ................................ SUCCESS [ 1:23 min]
[INFO] Test Probe - Core .................................. SUCCESS [ 3:45 min]
[INFO] Test Probe - Services .............................. SUCCESS [ 2:15 min]
[INFO] Test Probe - Interfaces ............................ SUCCESS [ 1:30 min]
[INFO] Test Probe - Parent ................................ SUCCESS [ 0:05 min]
[INFO]
[INFO] BUILD SUCCESS
[INFO]
[SUCCESS] Fat JAR: target/test-probe-1.0.0-SNAPSHOT.jar
```

**Artifacts produced**:
- Fat JAR: `target/test-probe-1.0.0-SNAPSHOT.jar`
- Coverage reports: `*/target/site/scoverage/index.html`
- Test reports: `*/target/surefire-reports/`

**CI/CD Integration**:
```yaml
# GitHub Actions example
- name: Full CI/CD Build
  run: ./scripts/build-ci.sh
  env:
    SKIP_K8S_SCALING: 1  # No K8s in CI
```

---

### `k8s-scale.sh {up|down|status}`

**Purpose**: Manually scale Kubernetes resources

**Commands**:
```bash
# Scale down (before tests)
./scripts/k8s-scale.sh down

# Scale up (after tests)
./scripts/k8s-scale.sh up

# Check status
./scripts/k8s-scale.sh status
```

**What it does**:
- `down`: Scales Kafka StatefulSet + Schema Registry Deployment to 0 replicas
- `up`: Scales Kafka StatefulSet + Schema Registry Deployment to 1 replica
- `status`: Shows current pod status in kafka namespace

**Under the hood**:
```bash
# Down
kubectl scale statefulset kafka --replicas=0 -n kafka
kubectl scale deployment schema-registry --replicas=0 -n kafka

# Up
kubectl scale statefulset kafka --replicas=1 -n kafka
kubectl scale deployment schema-registry --replicas=1 -n kafka

# Status
kubectl get pods -n kafka
```

---

## Module Dependencies

Understanding module dependencies helps optimize build times:

```
test-probe-common (no dependencies)
  ↓
test-probe-core (depends on: common)
  ↓
test-probe-services (depends on: common, core)
  ↓
test-probe-interfaces (depends on: common, core, services)
```

**When you run a script on a module, it automatically**:
1. Compiles all dependencies (without running their tests)
2. Runs tests ONLY on the target module

**Example**:
```bash
./scripts/test-unit.sh -m services

# Compiles (no tests):
#   - test-probe-common
#   - test-probe-core
# Runs unit tests:
#   - test-probe-services ✓
```

---

## Environment Variables

### Script Behavior

| Variable | Default | Description |
|----------|---------|-------------|
| `SKIP_K8S_SCALING` | `0` | Set to `1` to skip K8s scaling (CI mode) |
| `CLEANUP_DOCKER` | `1` | Set to `0` to skip Docker cleanup (debugging) |
| `K8S_NAMESPACE` | `kafka` | Kubernetes namespace for Kafka/Schema Registry |
| `MAVEN_OPTS` | (empty) | Additional Maven options |

**Examples**:
```bash
# Skip K8s scaling (CI environment)
SKIP_K8S_SCALING=1 ./scripts/test-component.sh -m core

# Disable Docker cleanup (debugging)
CLEANUP_DOCKER=0 ./scripts/test-component.sh -m core

# Custom Maven options
MAVEN_OPTS="-Xmx4G" ./scripts/test-all.sh -m core
```

---

## Interactive Mode

If you run a script **without specifying a module**, you'll be prompted:

```bash
$ ./scripts/test-unit.sh

Available modules:
  1) test-probe-common
  2) test-probe-core
  3) test-probe-services
  4) test-probe-interfaces

Select module (1-4): 2

[INFO] Running unit tests for: test-probe-core
...
```

---

## Error Handling

Scripts validate the environment before execution:

**Validation checks**:
- ✅ Docker daemon is running
- ✅ Docker has sufficient RAM (8GB min, 16GB recommended)
- ✅ kubectl is installed (for component tests)
- ✅ Maven wrapper (mvnw) exists
- ✅ Module exists in project

**Example failure**:
```bash
$ ./scripts/test-component.sh test-probe-core

[ERROR] Docker daemon not running
[ERROR] Please start Docker Desktop and try again

$ ./scripts/test-component.sh invalid-module

[ERROR] Module 'invalid-module' not found
[INFO] Available modules: test-probe-common, test-probe-core, test-probe-services, test-probe-interfaces
```

---

## Performance Baselines

### test-probe-core

| Command | Time | Tests Run | Docker RAM |
|---------|------|-----------|-----------|
| `test-unit.sh` | ~30s | 261 unit tests | 2GB |
| `test-component.sh` | ~166s | 168 component tests | 10GB |
| `test-all.sh` | ~200s | 429 total tests | 10GB |
| `test-coverage.sh` | ~240s | 429 tests + report | 10GB |
| `compile.sh` | ~15s | 0 tests | 1GB |

### Full CI/CD Build

| Command | Time | Tests Run | Docker RAM |
|---------|------|-----------|-----------|
| `build-ci.sh` | ~8-10 min | All modules | 12GB |

---

## Troubleshooting

### Tests Timing Out

**Symptom**: `ContainerLaunchException: Timed out waiting for container port to open`

**Solutions**:
```bash
# 1. Check Docker RAM
docker system info | grep "Total Memory"
# Should be: 16GB for parallel, 8GB minimum for sequential

# 2. Scale down K8s manually
./scripts/k8s-scale.sh down

# 3. Restart Docker Desktop
```

---

### Tests Running Slow

**Symptom**: Tests take 2x longer than baseline

**Solutions**:
```bash
# 1. Clean up Docker resources
docker system prune -f --volumes

# 2. Check K8s status
./scripts/k8s-scale.sh status
# Should show: No resources found (scaled down)

# 3. Verify parallel execution (core only)
grep "cucumber.parallel.enabled" pom.xml
```

---

### Docker Cleanup Not Working

**Symptom**: Docker containers accumulate after tests

**Solutions**:
```bash
# 1. Verify CLEANUP_DOCKER is enabled
echo $CLEANUP_DOCKER
# Should be: 1 (or empty - defaults to 1)

# 2. Manually clean up
docker ps -a --filter "label=org.testcontainers" --format "{{.ID}}" | xargs docker rm -f
docker network prune -f
docker volume prune -f

# 3. Check postflight logs
# Postflight should show: "Docker cleanup complete"
```

---

## Best Practices

### For Developers

✅ **DO**:
- Use wrapper scripts instead of raw Maven commands
- Run `test-unit.sh` frequently (fast feedback)
- Run `test-component.sh` before commits
- Run `test-coverage.sh` to verify coverage thresholds
- Use `compile.sh` for quick syntax checks

❌ **DON'T**:
- Run raw Maven commands (misses preflight/postflight)
- Run component tests without wrapper (K8s conflicts)
- Disable Docker cleanup unless debugging
- Run tests with < 8GB Docker RAM

---

### For CI/CD

✅ **DO**:
- Use `build-ci.sh` for full pipeline builds
- Set `SKIP_K8S_SCALING=1` in CI
- Set `CLEANUP_DOCKER=1` in CI
- Monitor test execution time (alert on slowdowns)

❌ **DON'T**:
- Use parallel execution without 16GB+ RAM
- Skip cleanup (wastes runner resources)
- Run individual module tests (use full build)

---

## Script Library Structure

```
scripts/
├── README.md                    # This file
├── test-unit.sh                 # Unit tests only
├── test-component.sh            # Component tests only
├── test-all.sh                  # All tests (unit + component)
├── test-coverage.sh             # All tests + coverage report
├── compile.sh                   # Compile only (no tests)
├── build-ci.sh                  # Full CI/CD build
├── k8s-scale.sh                 # Kubernetes scaling helper
└── lib/
    ├── logging.sh               # Colored output, structured logging
    ├── validation.sh            # Environment pre-checks
    ├── preflight.sh             # K8s scaling, environment prep
    └── postflight.sh            # K8s restore, Docker cleanup
```

---

## See Also

- **Maven Commands Reference**: `maven-commands-reference.md` (raw Maven command documentation)
- **Testing Guide**: `.claude/guides/TESTING.md`
- **Build Guide**: `.claude/guides/BUILD.md`
- **Best Practices**: `docs/testing-practice/component-test-resource-management.md`
- **ADR-TESTING-004**: `docs/architecture/adr/ADR-TESTING-004-testcontainers-kubernetes-isolation.md`

---

**Maintained By**: Engineering Team
**Last Updated**: 2025-10-30
