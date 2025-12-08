# Testing Standards Guide

This guide covers all testing practices, standards, and requirements for the Test-Probe project.

---

## 🚨 CRITICAL: Pre-Test Checklist 🚨

**BEFORE running ANY test**, you MUST follow this checklist to avoid false positives and stale data issues.

### 1. Clear /tmp Directory (MOST CRITICAL)

**PROBLEM**: Tests often write logs and output to `/tmp`. If you don't clear these before each test run, you may be reading STALE DATA from previous runs, leading to false positive results.

**ALWAYS do this before EVERY test run**:
```bash
# Clear test logs and output files
rm -f /tmp/*.log
rm -f /tmp/*.txt
rm -f /tmp/*test*.log
rm -f /tmp/*test*.txt

# Or clear everything (safer)
rm -rf /tmp/*
```

**Common Mistake**:
```bash
# Run tests
mvn test -Pcomponent-only

# See log output that looks successful
cat /tmp/test-run.log  # ❌ THIS MIGHT BE FROM YESTERDAY!

# Tests appear to pass, but you're reading OLD DATA
```

**Correct Approach**:
```bash
# 1. FIRST: Clear /tmp
rm -f /tmp/*.log /tmp/*.txt

# 2. THEN: Run tests
mvn test -Pcomponent-only 2>&1 | tee /tmp/fresh-test-run.log

# 3. VERIFY: Check the NEW output
cat /tmp/fresh-test-run.log  # ✅ This is definitely from THIS run
```

**When to Clear /tmp**:
- ✅ Before EVERY test run (component, unit, full suite)
- ✅ After making code changes
- ✅ When switching branches
- ✅ When test output seems suspicious or inconsistent
- ✅ When marking a task as "complete"

### 2. Verify ALL Tests Pass (Zero Tolerance)

**CRITICAL**: A test run is NOT successful unless ALL of these are true:

```bash
# Expected output for SUCCESS:
[INFO] Tests run: X, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Common Violations**:

❌ **WRONG - Errors present but ignored**:
```bash
[ERROR] Tests run: 139, Failures: 0, Errors: 47, Skipped: 0
# Engineer says: "Tests pass!"  ❌ NO THEY DON'T
```

❌ **WRONG - Build failure ignored**:
```bash
[INFO] Tests run: 101, Failures: 0, Errors: 0, Skipped: 0
[ERROR] BUILD FAILURE
# Engineer says: "All tests passed!"  ❌ BUILD FAILED
```

❌ **WRONG - Undefined steps ignored**:
```bash
io.cucumber.junit.UndefinedStepException: The step "the parent sends Initialize" is undefined
# Engineer says: "Feature file is complete!"  ❌ STEP NOT IMPLEMENTED
```

**Verification Checklist**:
- [ ] `Failures: 0` (must be zero)
- [ ] `Errors: 0` (must be zero)
- [ ] `Skipped: 0` (must be zero, unless intentional)
- [ ] `BUILD SUCCESS` (must see this message)
- [ ] No `UndefinedStepException` messages
- [ ] No compilation errors
- [ ] `/tmp` was cleared before this run

### 3. Step Definition Management

**PROBLEM**: Duplicate step definitions cause Cucumber to fail with `AmbiguousStepDefinitionsException`. Without an index, it's easy to duplicate patterns across different step files.

**Best Practices**:

**Use Actor-Specific Patterns** (PREFERRED):
```scala
// SharedSteps.scala - Generic operations
When("""the test world is initialized""")
When("""I wait {int} seconds""")

// QueueActorSteps.scala - Queue-specific
When("""the QueueActor receives {string}""")
Then("""the QueueActor should respond with {string}""")

// TestExecutionActorSteps.scala - TEA-specific
When("""the TestExecutionActor receives {string}""")
Then("""the TestExecutionActor should be in {string} state""")
```

**Avoid Generic Patterns** (causes duplication):
```scala
// ❌ BAD - Too generic, will duplicate across files
When("""the actor receives {string}""")      // Which actor?
Then("""the actor should respond {string}""") // Which actor?
```

**Before Adding a New Step**:
1. Search existing step definitions: `grep -r "When(" test-probe-core/src/test/scala/`
2. Check if a similar pattern already exists
3. If similar step exists, reuse it or make pattern more specific
4. Document the step in the appropriate steps file header

**Quick Step Index** (search these files):
- `SharedSteps.scala` - Test setup, waiting, common operations
- `ActorWorld.scala` - Actor system setup, world initialization
- `QueueActorSteps.scala` - Queue operations, test submission
- `TestExecutionActorSteps.scala` - FSM states, transitions
- `KafkaProducerStreamingSteps.scala` - Producer operations
- `KafkaConsumerStreamingSteps.scala` - Consumer operations
- `ProbeScalaDslSteps.scala` - DSL API operations

### 4. Fresh Test Run Verification

**Before marking ANY task as complete**:

**Recommended (using build scripts)**:
```bash
# 1. Clear /tmp (ALWAYS FIRST!)
rm -f /tmp/*.log /tmp/*.txt

# 2. Run unit tests for your module
./scripts/test-unit.sh -m core

# 3. Run component tests (if applicable)
./scripts/test-component.sh -m core

# 4. Check coverage
./scripts/test-coverage.sh -m core

# 5. Verify output (look at FRESH logs)
# Expected: Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS
```

**Alternative (direct Maven commands)**:
```bash
# 1. Clear /tmp
rm -f /tmp/*.log /tmp/*.txt

# 2. Run FULL test suite
mvn clean test -pl test-probe-core

# 3. Verify output (look at FRESH logs)
# Expected: Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS

# 4. If component tests involved, run those too
mvn test -Pcomponent-only -pl test-probe-core

# 5. Check coverage (if applicable)
mvn -Pcoverage clean test
```

**If ANY of these fail**: Work is NOT done. Fix the issue and repeat from step 1.

---

## Testing Pyramid

```
SIT (10%): Live systems, Gherkin features, compliance evidence
  ↑
Component (20%): Virtualized dependencies (Testcontainers), actor protocols
  ↑
Unit (70%): Pure functions, mocked I/O, fast feedback
```

---

## Parallel Test Execution (Component Tests)

**Optimization Achievement**: 61% faster component tests through K8s isolation + parallel execution

### Quick Start

**Using Build Scripts** (Recommended):
```bash
# Sequential (stable, 8GB+ Docker RAM)
./scripts/test-component.sh -m core

# Parallel 4-thread (faster, 16GB Docker RAM required)
./scripts/test-component.sh -m core \
  -Dcucumber.parallel.enabled=true \
  -Dcucumber.parallel.threads=4
```

**Direct Maven**:
```bash
# Sequential (default, 186s for core)
mvn test -Pcomponent-only -pl test-probe-core

# Parallel 4-thread (fastest, 159s for core - 15% faster)
mvn test -Pcomponent-only -pl test-probe-core \
  -Dcucumber.parallel.enabled=true \
  -Dcucumber.parallel.threads=4
```

### Performance Results

| Configuration | Time | Pass Rate | Docker RAM | When to Use |
|--------------|------|-----------|-----------|-------------|
| **Sequential** | 186s (3m 6s) | 100% | ~4GB | CI/CD, limited RAM |
| **Parallel 2** | 204s (3m 24s) | 100% | ~6GB | ❌ NOT RECOMMENDED (slower!) |
| **Parallel 4** | 159s (2m 39s) | 100% | ~10GB | ✅ Local dev (16GB Docker) |

**Key Insight**: Parallelism=2 is **slower** than sequential due to container startup overhead. Only parallelism=4+ shows improvement.

### Prerequisites

**For Parallel Execution**:
- Docker Desktop with **16GB RAM allocated**
- 4+ CPU cores
- K8s scaled down (handled automatically by build scripts)

**For Sequential**:
- Docker Desktop with **8GB RAM** (minimum)
- 2+ CPU cores

### Configuration

**Default** (pom.xml):
```xml
<cucumber.parallel.enabled>false</cucumber.parallel.enabled>  <!-- Sequential -->
<cucumber.parallel.threads>1</cucumber.parallel.threads>
```

**Override at Runtime**:
```bash
# Enable parallel with 4 threads
mvn test -Dcucumber.parallel.enabled=true -Dcucumber.parallel.threads=4
```

### Resource Management

**Automatic Cleanup** (Build Scripts):
- K8s scaled down before tests (preflight)
- Docker containers/networks cleaned after tests (postflight)
- K8s restored after tests
- Set `CLEANUP_DOCKER=0` to disable cleanup (for debugging)

**Manual Cleanup**:
```bash
# Stop Testcontainers
docker ps -a --filter "label=org.testcontainers" | xargs docker rm -f

# Remove networks
docker network ls --filter "name=testcontainers" | xargs docker network rm

# Prune volumes
docker system prune -f --volumes
```

### Troubleshooting

**Tests fail with "port already in use"**:
- K8s Kafka/Schema Registry running → Scale down K8s or use build scripts
- Previous Testcontainers not cleaned → Run manual cleanup above

**Slower with parallelism=2**:
- Expected! Container startup overhead dominates with only 2 threads
- Use sequential or jump to parallelism=4

**Docker RAM exhausted**:
- Reduce parallelism or increase Docker RAM allocation
- Sequential mode uses ~4GB, parallel=4 uses ~10GB

**Flaky tests / random failures**:
- Ensure K8s is scaled down (Docker resources conflict)
- Use build scripts for automatic environment isolation

### References

- **ADR-TESTING-004**: Testcontainers/Kubernetes Isolation decision
- **Component Test Resource Management**: `docs/testing-practice/component-test-resource-management.md`
- **Optimization Analysis**: `working/componentTestOptimization/FINAL-OPTIMIZATION-SUMMARY-2025-10-30.md`

---

## @Pending Tests - CRITICAL UNDERSTANDING

**IMPORTANT**: Tests tagged with `@Pending` are NOT passing tests - they are SKIPPED and do NOT count toward coverage or completion.

### What @Pending means

- BDD feature file exists but step definitions are NOT implemented
- Scenarios are excluded from test execution: `tags = "@ComponentTest and not @Pending"`
- Build will show "X tests passed" but this EXCLUDES all @Pending scenarios
- Coverage reports do NOT include code paths for @Pending scenarios

### Example

```gherkin
@ComponentTest @Pending
Feature: Queue Actor - FIFO Test Management
  # This feature has 28 scenarios
  # BUT: All 28 scenarios are SKIPPED during test execution
  # RESULT: Build shows 101/101 tests passed, but 28 scenarios are NOT tested
```

### Understanding Test Output

When test output shows: "Tests run: 101, Failures: 0, Errors: 0, Skipped: 0"
- This means 101 IMPLEMENTED tests passed
- Does NOT include scenarios tagged with @Pending
- Check `working/` directory for implementation plans for @Pending features

### Completion Rule

A feature is NOT complete until:
1. ✅ Feature file created (.feature)
2. ✅ Step definitions implemented (Steps.scala)
3. ✅ @Pending tag removed from feature
4. ✅ All scenarios passing (no errors, no skipped)
5. ✅ Implementation plan closed in `working/`

### Current @Pending Features

- `queue-actor.feature` - 28 scenarios pending step definitions (see `working/QueueActorImplementationPlan.md`)

---

## Test Structure

Use ScalaTest with `AnyWordSpec` and `Matchers`:

```scala
class ComponentSpec extends AnyWordSpec with Matchers {
  "ComponentName" should {
    "behavior description" in {
      // Given
      val input: Input = createTestInput()

      // When
      val result: Result = component.process(input)

      // Then
      result shouldBe expectedResult
    }
  }
}
```

---

## Test Naming

- **Files**: `ComponentNameSpec.scala`
- **Classes**: `ComponentNameSpec`
- **Describe blocks**: Component/class name
- **Test descriptions**: "should [expected behavior]"

---

## Coverage Requirements

- **Minimum**: 70%+ line coverage (enforced by Scoverage)
- **Target for Actors/FSMs**: 85%+ line coverage
- **Focus**: Critical business logic, error conditions, edge cases, all helper methods
- **Skip**: Trivial getters/setters without logic
- **Pattern**: Use visibility pattern (`private[core]` + public methods) to enable comprehensive testing

---

## Test Directory Structure

```
src/test/
├── scala/com/company/probe/
│   ├── unit/              # Layer 1: Pure unit tests (70%)
│   └── component/         # Layer 2: Component tests (20%)
└── resources/
    ├── unit/fixtures/     # Test data for unit tests
    ├── component/features/# Gherkin features for component tests
    └── sit/features/      # SIT features (executed via REST API)
```

**Rule**: Test file location determines test type and execution phase.

---

## Testing Actors

```scala
class ActorSpec extends AnyWordSpec with Matchers {
  implicit val testKit: ActorTestKit = ActorTestKit()

  "ActorName" should {
    "respond correctly" in {
      val probe: TestProbe[Response] = testKit.createTestProbe[Response]()
      val actor: ActorRef[Command] = testKit.spawn(ActorName(deps))

      actor ! Command(data, probe.ref)
      probe.expectMessage(ExpectedResponse)
    }
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()
}
```

---

## Adding Cucumber Step Definitions

1. Create step definition class in `test-probe-glue/src/main/scala/.../glue/steps/`
2. Extend base traits: `ScalaDsl`, `EN` (Cucumber)
3. Access `TestContext` for framework services (actors, storage, etc.)
4. Create feature file in `src/test/resources/component/features/` or `sit/features/`
5. Add test runner in `src/test/scala/.../` (JUnit-based for Cucumber)

---

## Related Guides

- **Build Commands**: See `.claude/guides/BUILD.md` for test execution commands
- **Style Standards**: See `.claude/styles/testing-standards.md` for detailed testing patterns
- **BDD Standards**: See `.claude/styles/bdd-testing-standards.md` for Cucumber guidelines
- **Visibility Pattern**: See CLAUDE.md for testability patterns