# Build and Test Commands Guide

This guide covers all build and test commands for the Test-Probe project.

---

## Recommended: Enhanced Build Scripts (Production-Grade!)

The project provides **production-grade build scripts** with professional UX features. All scripts enhanced with timing, progress indicators, help documentation, and failure summaries.

```bash
# Compile module (fast, no tests)
./scripts/compile.sh -m core

# Run unit tests (fast, ~5-10s)
./scripts/test-unit.sh -m core

# Run component tests (requires Docker, ~2-3min with 4-thread parallel)
./scripts/test-component.sh -m core

# Run all tests (unit + component)
./scripts/test-all.sh -m core

# Run tests with coverage report (warnings only)
./scripts/test-coverage.sh -m core

# Full CI/CD build (strict coverage enforcement, fat JAR, coverage table)
./scripts/build-ci.sh
```

**Available modules**: `common`, `core`, `services`, `interfaces`

**Interactive mode**: Omit `-m` flag to get an interactive prompt:
```bash
./scripts/test-unit.sh
# Prompts: Select module (1-4):
```

**ALL scripts have --help**:
```bash
./scripts/test-unit.sh --help      # Beautiful formatted help
./scripts/build-ci.sh --help       # Shows features, examples, thresholds
```

---

### ✨ What's New - 2025-10-30 Enhancement Suite

**All 6 scripts now include:**

1. **Help Documentation** (`--help` flag)
   - Beautiful formatted help with examples
   - Feature lists and usage patterns
   - Module dependencies and requirements

2. **Execution Timing**
   - Per-step timing (e.g., "Compiling dependencies (45s)")
   - Total elapsed time at end
   - Helps identify bottlenecks

3. **Progress Indicators**
   - Visual feedback for long operations (⏳)
   - "Compiling...", "Running tests...", "Generating coverage..."
   - No more silent waiting

4. **Disk Space Validation**
   - Checks free disk space before operations
   - Warnings if below recommended (10-20GB depending on script)
   - Prevents out-of-disk failures mid-run

5. **Test Failure Summaries**
   - Shows which tests failed (no log hunting)
   - Lists first 10 failures + count
   - Suggests debug commands

6. **Coverage Table** (build-ci.sh only - THE SHOWPIECE!)
   ```
   Coverage Summary:
   ┌─────────────────────────┬──────────┬───────────┬──────────┐
   │ Module                  │ Coverage │ Threshold │ Status   │
   ├─────────────────────────┼──────────┼───────────┼──────────┤
   │ test-probe-common       │   78%    │   70%     │ ✓ PASS   │
   │ test-probe-core         │   86%    │   85%     │ ✓ PASS   │
   │ test-probe-services     │   82%    │   80%     │ ✓ PASS   │
   │ test-probe-interfaces   │   75%    │   70%     │ ✓ PASS   │
   └─────────────────────────┴──────────┴───────────┴──────────┘
   ```

**Example Output** (test-component.sh):
```bash
$ ./scripts/test-component.sh -m core

[=== Component Tests: test-probe-core ===]

[STEP 1/5] Validating environment (2s)
[INFO] Free disk space: 45.2GB
[SUCCESS] Disk space sufficient

[STEP 2/5] Preparing environment (scaling down K8s) (3s)
[STEP 3/5] Compiling dependencies (45s)
[PROGRESS] Compiling test-probe-common ⏳

[STEP 4/5] Running component tests for test-probe-core (2m 34s)
[INFO] Enabling parallel execution (4 threads) for test-probe-core
[PROGRESS] Running component tests (this may take 2-3 minutes) ⏳

[STEP 5/5] Test execution complete (1s)
[SUCCESS] Component tests passed for test-probe-core! ✓
[INFO]   Tests executed: 168
[INFO]   Total execution time: 3m 25s
```

---

### Key Features

- **Automatic dependency compilation** (no dependency tests run)
- **K8s scaling and Docker cleanup** (component/coverage/CI scripts)
- **Parallel execution for core** (hard-coded, 4 threads - 61% faster!)
- **Coverage warnings** (dev scripts) vs **strict failures** (CI script)
- **Professional UX** (timing, progress, summaries)

**Full Documentation**: `scripts/README.md`

---

## Direct Maven Commands

For advanced use cases or when you need more control:

### Quick Start

```bash
# Compile all modules (no tests)
mvn compile

# Compile specific module with dependencies
mvn compile -pl test-probe-core -am

# Run all tests
mvn test

# Run tests for specific module with dependencies
mvn test -pl test-probe-core -am
```

---

## Test Profiles

The project uses a 3-tier testing pyramid (70% unit, 20% component, 10% SIT):

```bash
# Unit tests only (fast, no Docker, ~5-10s)
mvn test -Punit-only -pl test-probe-core -am

# Component tests only (requires Docker/Testcontainers, ~4-5min)
mvn test -Pcomponent-only -pl test-probe-core -am

# All tests (unit + component)
mvn test -pl test-probe-core -am

# All unit tests across all modules (446 tests)
mvn test -Punit-only

# All component tests across all modules
mvn test -Pcomponent-only
```

---

## Code Coverage

```bash
# Run tests with coverage report - single module
mvn clean test scoverage:report -pl test-probe-core -am

# Coverage for unit tests only
mvn clean test scoverage:report -Punit-only -pl test-probe-core -am

# Coverage for component tests only
mvn clean test scoverage:report -Pcomponent-only -pl test-probe-core -am

# Aggregate coverage across all modules
mvn clean test scoverage:report -Pcoverage-aggregate

# Enforce 70% minimum coverage (fails if below)
mvn -Pcoverage clean test

# Report location: target/site/scoverage/index.html
```

---

## Specific Test Execution

```bash
# Run single test spec
mvn test -Dtest=QueueActorSpec -pl test-probe-core

# Run multiple test specs
mvn test -Dtest=QueueActorSpec,TestExecutionActorSpec -pl test-probe-core

# Run tests matching pattern
mvn test -Dtest="*ActorSpec" -pl test-probe-core

# Debug mode with full logging
mvn test -Dtest=QueueActorSpec -X -pl test-probe-core
```

---

## Clean and Package

```bash
# Clean build artifacts
mvn clean

# Build JAR without tests
mvn package -DskipTests

# Clean build with tests
mvn clean package
```

---

## Fast Feedback Loop

**Recommended (using build scripts)**:
```bash
# 1. Quick compilation check
./scripts/compile.sh -m core

# 2. Run unit tests (fast, ~5-10s)
./scripts/test-unit.sh -m core

# 3. Run specific test (use Maven directly)
mvn test -Punit-only -Dtest=QueueActorSpec -pl test-probe-core -am
```

**Alternative (direct Maven commands)**:
```bash
# 1. Quick compilation check (always compile dependencies with -am)
mvn compile -DskipTests -pl test-probe-core -am

# 2. Run unit tests (fast, ~5-10s)
mvn test -Punit-only -pl test-probe-core -am

# 3. Run specific test during development
mvn test -Punit-only -Dtest=QueueActorSpec -pl test-probe-core -am
```

---

## Pre-Commit Checklist

**Recommended (using build scripts)**:
```bash
# Quick validation for your module
./scripts/test-unit.sh -m core        # Fast unit tests
./scripts/test-component.sh -m core   # Component tests (Docker required)

# Before commit: Run coverage check
./scripts/test-coverage.sh -m core    # Warns if coverage below threshold
```

**Alternative (direct Maven commands)**:
```bash
# 1. Clean build
mvn clean compile -DskipTests

# 2. Unit tests
mvn test -Punit-only

# 3. Component tests (if Docker available)
mvn test -Pcomponent-only

# 4. Coverage check
mvn -Pcoverage clean test
```

---

## Debugging Test Failures

```bash
# Run with debug output
mvn test -Dtest=FailingSpec -X

# Run with stack traces
mvn test -Dtest=FailingSpec -e

# Save output to file
mvn test -Dtest=FailingSpec 2>&1 | tee test.log
```

---

## Reference

See `maven-commands-reference.md` in the project root for complete command documentation.