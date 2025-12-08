# Testing Quick Reference - Test-Probe Project

**Ultra-condensed. If in doubt, read `.claude/guides/TESTING.md`**

---

## Definition of Done

```bash
[INFO] Tests run: X, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Zero tolerance**: Any failures or errors = NOT DONE

---

## Test Commands (Maven)

### Fast Feedback (Use First)
```bash
# Unit tests only (FAST - 1-2 min)
mvn test -Punit-only -pl test-probe-core

# Single test class
mvn test -Dtest=MyActorSpec -pl test-probe-core

# Clean and test
mvn clean test -Punit-only -pl test-probe-core -q
```

### Component Tests
```bash
# Component tests only (slower - 3-5 min)
mvn test -Pcomponent-only -pl test-probe-core

# Both unit + component (full suite)
mvn test -pl test-probe-core
```

### Coverage
```bash
# Generate coverage report
mvn scoverage:report -pl test-probe-core -q

# Report location
# test-probe-core/target/site/scoverage/index.html
```

### Before Testing (CRITICAL)
```bash
# Clear stale data (prevents false positives)
rm -f /tmp/*.log /tmp/*.txt
```

---

## Coverage Requirements

| Component Type | Minimum | Target |
|----------------|---------|--------|
| Actors/FSMs | 85% | 90%+ |
| Business Logic | 80% | 85%+ |
| Services | 75% | 80%+ |
| Utilities | 70% | 75%+ |

**If below minimum**: Add unit tests for helper methods (check visibility pattern)

---

## Test Structure

### Unit Test (ScalaTest)
```scala
class MyActorSpec extends AnyWordSpec with Matchers:
  "MyActor" should:
    "handle command successfully" in:
      val testKit = ActorTestKit()
      val probe = testKit.createTestProbe[Response]()
      val actor = testKit.spawn(MyActor())

      actor ! Command.Start(probe.ref)

      probe.expectMessage(Response.Started)
      testKit.shutdownTestKit()
```

### Component Test (BDD-style)
```scala
class MyActorComponentSpec extends AnyWordSpec:
  "MyActor integration" should:
    "process full workflow" in:
      Given("actor is initialized")
      // setup

      When("receiving command sequence")
      // execute

      Then("should transition through states correctly")
      // assert
```

### BDD Feature File (Cucumber)
```gherkin
Feature: Test Execution

  Scenario: Successful test execution
    Given a test is queued
    When the test execution starts
    Then the test should complete successfully
```

---

## Test Profiles

| Profile | Tests Run | Speed | Use Case |
|---------|-----------|-------|----------|
| `unit-only` | Unit only | Fast (1-2 min) | Module development |
| `component-only` | Component only | Medium (3-5 min) | Integration checks |
| *(default)* | Both | Slow (5-10 min) | Full regression |

---

## Common Test Patterns

### Actor Testing
```scala
// Use TestKit for actor testing
val testKit = ActorTestKit()
val probe = testKit.createTestProbe[Response]()
val actor = testKit.spawn(MyActor())

// Test message handling
actor ! SomeCommand(probe.ref)
probe.expectMessage(ExpectedResponse)

// Always shutdown
testKit.shutdownTestKit()
```

### Async Testing
```scala
"async operation" in:
  val future = asyncOperation()
  future.futureValue shouldBe expectedResult
```

### Exception Testing
```scala
"invalid input" in:
  Try(dangerousOperation()) match
    case Failure(ex: IllegalArgumentException) => succeed
    case _ => fail("Expected IllegalArgumentException")
```

---

## Test Organization

```
src/test/scala/com/company/probe/
├── core/
│   ├── actors/
│   │   ├── MyActorSpec.scala          # Unit tests
│   │   └── MyActorComponentSpec.scala # Component tests
│   └── services/
│       └── MyServiceSpec.scala
└── features/
    └── my-feature.feature              # BDD scenarios
```

---

## @Pending Tests

```scala
"unimplemented feature" taggedAs Pending in:
  // Test code here - will be skipped but tracked
```

**Purpose**: Track future test cases without failing the build

---

## Test Fixtures

### Shared Setup
```scala
trait TestFixtures:
  val testConfig: Config = ConfigFactory.parseString("""
    test.timeout = 5s
  """)

  def createTestActor()(using testKit: ActorTestKit): ActorRef[Command] =
    testKit.spawn(MyActor())

class MySpec extends AnyWordSpec with TestFixtures:
  // Use fixtures
```

---

## Debugging Tests

### View Surefire Reports
```bash
# XML reports
ls -la test-probe-core/target/surefire-reports/

# View specific test output
cat test-probe-core/target/surefire-reports/TEST-*.xml
```

### Check Compilation
```bash
# Test compilation only (no execution)
mvn test-compile -pl test-probe-core -q
```

### Verbose Output
```bash
# Remove -q flag for full output
mvn test -pl test-probe-core
```

---

## Two-Phase Testing Strategy

### Phase 1: Focus (Module Testing)
```bash
# Fast feedback on specific module
mvn test -Punit-only -pl test-probe-core
```
**Goal**: Rapid iteration during development (1-2 min)

### Phase 2: Stable/Regression (Full Suite)
```bash
# Complete test suite
mvn test -pl test-probe-core
```
**Goal**: Pre-commit validation (5-10 min)

---

## Common Issues

| Issue | Solution |
|-------|----------|
| Stale test data | `rm -f /tmp/*.log /tmp/*.txt` |
| Tests pass locally, fail in suite | Component test interaction - run full suite |
| Low coverage | Check visibility pattern, add unit tests |
| Timeout errors | Increase `TestDuration` in test config |
| Tests won't compile | `mvn clean test-compile -pl test-probe-core` |

---

## Test Quality Checklist

- [ ] All tests pass (Failures: 0, Errors: 0)
- [ ] Coverage ≥ threshold for component type
- [ ] No `@Ignore` tests (use `@Pending` instead)
- [ ] Cleared `/tmp` before testing
- [ ] Ran unit tests first (fast feedback)
- [ ] Ran full regression (pre-commit)
- [ ] No flaky tests (deterministic)

---

## When in Doubt

📖 Full guide: `.claude/guides/TESTING.md`
📖 Test standards: `.claude/styles/testing-standards.md`
📖 BDD standards: `.claude/styles/bdd-testing-standards.md`

**Load context**: `/context-testing`
**Enforce quality**: Skill `test-quality-enforcer` auto-loads on test work