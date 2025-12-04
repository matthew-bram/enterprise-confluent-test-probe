# ADR-CUCUMBER-002: Parallel Test Execution Architectural Constraints

**Status**: Accepted
**Date**: 2025-11-26
**Decision Maker**: Engineering Team
**Context**: Validation of Phase 4 parallel execution design revealed architectural constraints

---

## Context and Problem Statement

During implementation of the "Bring Back Integration Tests" feature (Phase 4), we evaluated enabling parallel test execution to reduce CI/CD pipeline times. The original Phase 4 plan proposed:

1. Update `CucumberContext` with multi-strategy registry (ThreadLocal + ConcurrentHashMap)
2. Add `parallelThreads` to `CucumberConfiguration`
3. Enable Cucumber's `--threads` option for parallel scenario execution

**Investigation Findings:**

Upon analysis, we discovered that the Test Probe architecture uses a **singleton ActorSystem** pattern (`DefaultActorSystem` in production) which creates fundamental constraints:

| Component | Pattern | Parallel Test Impact |
|-----------|---------|---------------------|
| `DefaultActorSystem` | Singleton per JVM | All tests share same GuardianActor, QueueActor |
| `ProbeScalaDsl.registerSystem()` | Global `@volatile var` | Last test to register wins, others get wrong ActorSystem |
| `CucumberContext` | ThreadLocal isolation | Works for testId/evidencePath, NOT for ActorSystem state |
| `TestExecutionListenerRegistry` | ConcurrentHashMap | Safe, but results could be interleaved |

**The Core Problem:**

```
Test A creates IntegrationWorld → calls ProbeScalaDsl.registerSystem(systemA)
Test B creates IntegrationWorld → calls ProbeScalaDsl.registerSystem(systemB)
                                                      ↓
                                Test A's concurrent operations may use systemB!
```

---

## Decision

**Sequential test execution is by design, not a limitation to fix.**

Integration tests will continue to run sequentially. Phase 4 of the implementation plan is marked as **Deferred** with this ADR as the rationale.

### What This Means

1. **No `--threads` option** added to CucumberConfiguration
2. **No multi-strategy CucumberContext** needed (current ThreadLocal is sufficient)
3. **Integration tests share one ActorSystem** per test run (mirrors production)
4. **CI/CD pipelines** cannot parallelize at the test level (but can parallelize modules)

---

## Rationale

### 1. Production Architecture Parity

In production, Test Probe uses `DefaultActorSystem` - a singleton created by the `BuilderContext`. This means:

- One `GuardianActor` per application instance
- One `QueueActor` managing all test executions
- Tests are queued and processed sequentially by design

**Running integration tests in parallel would NOT test the actual production behavior.**

### 2. Global State in ProbeScalaDsl

```scala
// ProbeScalaDsl.scala
@volatile private var systemOpt: Option[ActorSystem[_]] = None

def registerSystem(system: ActorSystem[_]): Unit =
  systemOpt = Some(system)  // Last writer wins!
```

Multiple parallel tests would overwrite each other's ActorSystem references, causing:
- Wrong actor references in step definitions
- Interleaved message handling
- Non-deterministic test failures

### 3. Testcontainers Already Works Well

| Component | Status | Notes |
|-----------|--------|-------|
| `TestcontainersManager` | Thread-safe | Synchronized singleton, shared containers |
| Schema registration | Idempotent | Schema Registry handles duplicate registrations |
| Verbose topic naming | Working | `test-events-json`, `order-events-avro` avoids latest-version contention |
| `cucumber-blocking-dispatcher` | Optimized | 4-thread pool for concurrent (not parallel) test execution |

**The current architecture is already optimized for the singleton ActorSystem pattern.**

### 4. Simplicity Over Complexity

Adding parallel test support would require:
- Per-test ActorSystem instances (breaks production parity)
- Thread-safe DSL registration (complex locking)
- Isolated Testcontainers per test (3x+ resource usage)
- Topic sharding per test (complex naming conventions)

**The complexity cost outweighs the benefit for integration tests.**

---

## Consequences

### Positive

- **Production parity**: Tests mirror actual singleton ActorSystem behavior
- **Simplicity**: No complex thread-safe DSL registration needed
- **Reliability**: No race conditions in global state access
- **Resource efficiency**: Shared Testcontainers, one Schema Registry instance
- **Predictable execution**: Sequential tests are deterministic

### Negative

- **CI/CD time**: Integration tests run sequentially (~3-5 minutes for full suite)
- **No test-level parallelism**: Cannot speed up individual test runs
- **Developer wait time**: Must wait for full test suite on local changes

### Neutral

- **Module-level parallelism**: Maven can parallelize across modules (`-T 4`)
- **Unit test parallelism**: Unit tests (no ActorSystem dependency) can still run in parallel
- **Component tests**: Continue to use Testcontainers isolation (sequential within module)

---

## Alternatives Considered

### Alternative 1: JVM Forking via Maven Surefire

**Approach**: Use `forkCount` to run tests in separate JVMs.

**Pros**:
- True isolation - each JVM has its own ActorSystem
- No code changes needed

**Cons**:
- Each JVM needs its own Testcontainers → 3x-5x resource usage
- JVM startup overhead (~10-15 seconds per fork)
- Testcontainers cold start per fork (~30-60 seconds)
- Total time likely INCREASES due to container startup

**Decision**: Rejected. Container overhead negates parallelism benefits.

---

### Alternative 2: Per-Test ActorSystem (Break Singleton Pattern)

**Approach**: Each test creates its own ActorSystem with unique name.

**Pros**:
- True isolation between tests
- Parallel execution possible

**Cons**:
- Breaks production parity (production uses singleton)
- `ProbeScalaDsl.registerSystem()` still global - need thread-local version
- Actor name conflicts possible
- Test resource cleanup more complex

**Decision**: Rejected. Breaks fundamental architectural assumption.

---

### Alternative 3: Topic Sharding (Per-Test Topics)

**Approach**: Each test uses unique topics (`test-events-json-testA`, `test-events-json-testB`).

**Pros**:
- Single ActorSystem, but isolated message streams
- Parallel tests don't interfere with each other's Kafka messages

**Cons**:
- Complex topic naming conventions
- Schema registration per unique topic
- QueueActor still shared - concurrent test submissions may interleave
- Doesn't solve ProbeScalaDsl global state issue

**Decision**: Viable future option if parallelism becomes critical. Documented in Future Options.

---

### Alternative 4: Cucumber --threads for Parallel Scenarios

**Approach**: Use Cucumber's built-in `--threads` option.

**Pros**:
- No architectural changes
- Scenarios within one test run in parallel

**Cons**:
- Scenarios share ActorSystem state
- Step definitions must be thread-safe
- CucumberContext ThreadLocal isolation insufficient for shared actor state
- Doesn't address multiple IntegrationWorld instances

**Decision**: Rejected. Doesn't solve the core ActorSystem state sharing issue.

---

## Future Options

If parallel test execution becomes a critical requirement:

### Option A: Topic Sharding + Thread-Local DSL

1. Create `ThreadLocal` version of `ProbeScalaDsl.registerSystem()`
2. Each test uses unique topic names with testId suffix
3. Scenarios that share the same IntegrationWorld run sequentially
4. Different IntegrationWorld instances can run in parallel

**Estimated effort**: 2-3 days
**Trade-off**: Complexity vs. ~50% CI time reduction

### Option B: Testcontainers Reuse Mode

1. Enable Testcontainers reusable containers feature
2. JVM forking with container reuse
3. Reduces container startup overhead

**Estimated effort**: 1 day
**Trade-off**: Local developer experience vs. CI cold start

### Option C: Test Suite Sharding (CI Level)

1. Split test suite into independent shards
2. Run shards on separate CI runners
3. Aggregate results

**Estimated effort**: 1 day (CI configuration)
**Trade-off**: CI cost vs. execution time

---

## References

- `working/bringBackIntegrationTests/IMPLEMENTATION-PLAN.md` - Phase 4 original design
- `test-probe-core/src/main/scala/io/distia/probe/core/pubsub/ProbeScalaDsl.scala` - Global state
- `test-probe-core/src/test/scala/io/distia/probe/core/testutil/TestcontainersManager.scala` - Singleton pattern
- `test-probe-core/src/main/scala/io/distia/probe/core/services/cucumber/CucumberContext.scala` - ThreadLocal isolation
- `ADR-TESTING-004-testcontainers-kubernetes-isolation.md` - Related Testcontainers decision

---

## Approval

**Approved by**: Engineering Team
**Date**: 2025-11-26
**Review**: Accepted - Sequential test execution is architecturally appropriate
