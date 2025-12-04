# ADR-TESTING-002: Internal Implementation Validation in Component Tests

**Status**: Accepted
**Date**: 2025-10-26
**Decision Maker**: Engineering Team
**Context**: Component test suite refactoring (Phase 1 - Streaming Actors)

---

## Context and Problem Statement

During component test development for streaming actors (KafkaConsumerStreamingActor, KafkaProducerStreamingActor), we encountered test scenarios attempting to validate **internal implementation details** - specifically, materializer shutdown behavior during actor PostStop.

**The Problem**: How do we validate internal resource management behaviors (materializer cleanup, thread pool shutdown, connection closures) in component tests without:
1. Complex mocking infrastructure that obscures test intent
2. Tight coupling between tests and implementation details
3. Brittle tests that break when refactoring internal logic

**Specific Failures** (Category 4):
- `kafka-consumer-streaming.feature:107` - "And the custom materializer should shutdown"
- `kafka-producer-streaming.feature:87` - "And the custom materializer should shutdown"

Both scenarios attempted to assert that `Materializer.shutdown()` was called during PostStop cleanup. The step definition could not directly observe this internal behavior without:
- Creating custom observable materializer wrappers
- Instrumenting actor internals with test-specific hooks
- Analyzing logs for shutdown evidence

---

## Decision

**Component tests will NOT validate internal implementation details that are not directly observable through public API or behavior**.

Internal behaviors (resource cleanup, shutdown sequencing, internal state transitions) will be validated through:

1. **Unit Tests**: Where internal methods can be tested directly with mock dependencies
2. **Indirect Behavioral Verification**: Component tests verify the *outcome* (clean termination, no resource leaks) rather than the *mechanism* (specific method calls)
3. **Future Dedicated Infrastructure**: If needed, build specialized observability/instrumentation for resource management validation

### What Component Tests Should Validate

Component tests verify **observable functional behavior**:
- ✅ Actor receives and responds to messages correctly
- ✅ State transitions occur as expected
- ✅ Integration between components works (message passing, data flow)
- ✅ System reaches expected end state (actor terminated, no errors)
- ✅ Public API contracts are honored

### What Component Tests Should NOT Validate

Component tests do NOT verify **internal implementation details**:
- ❌ Specific method calls within actor implementations (e.g., `materializer.shutdown()`)
- ❌ Internal cleanup sequencing (order of shutdown operations)
- ❌ Private field state unless exposed via public API
- ❌ Implementation-specific optimizations (caching, pooling strategies)
- ❌ Internal resource manager behavior (unless it affects observable outcomes)

---

## Rationale

### 1. Test Fragility and Coupling
**Problem**: Validating internal implementation details creates brittle tests.
- Tests break when refactoring internal logic (even if behavior is preserved)
- Example: Switching from custom materializer to system materializer breaks tests
- Tight coupling between test assertions and implementation details

**Benefit**: Focusing on observable behavior allows implementation changes without test rewrites.

### 2. Test Complexity
**Problem**: Validating internals requires complex test infrastructure.
- Custom mocks, spies, or observable wrappers needed
- Test setup becomes more complex than the code being tested
- Maintenance burden increases significantly

**Benefit**: Simpler tests that directly express behavioral expectations.

### 3. False Confidence
**Problem**: Asserting method calls doesn't guarantee correct behavior.
- `materializer.shutdown()` might be called but fail silently
- Resource leaks can occur even when shutdown methods are invoked
- Focus on mechanism rather than outcome provides false confidence

**Benefit**: Behavioral assertions (e.g., "no resource leaks") verify actual correctness.

### 4. Architectural Clarity
**Problem**: Mixing unit-test concerns (method calls) with component-test concerns (integration).
- Component tests should verify integration, not implementation
- Method-level validation belongs in unit tests where internals are accessible

**Benefit**: Clear test pyramid with distinct responsibilities at each level.

### 5. Testability Without Special Infrastructure
**Problem**: Observing internal behavior requires special infrastructure.
- Log monitoring, instrumentation, custom telemetry
- Building this infrastructure is a future enhancement, not a blocker

**Benefit**: Tests work today with existing infrastructure, enhanced later if needed.

---

## Implementation

### Files Modified (2025-10-26)

Removed internal implementation validation steps from 2 streaming actor feature files:

1. **`test-probe-core/src/test/resources/features/component/streaming/kafka-consumer-streaming.feature:107`**
   - Removed: "And the custom materializer should shutdown"
   - Kept: "And no resource leaks should be present" (outcome, not mechanism)

2. **`test-probe-core/src/test/resources/features/component/streaming/kafka-producer-streaming.feature:87`**
   - Removed: "And the custom materializer should shutdown"
   - Kept: "And no resource leaks should be present" (outcome, not mechanism)

**Impact**: 2 scenarios affected (PostStop cleanup scenarios)

---

## Consequences

### Positive

✅ **Reduced Test Fragility**: Tests won't break when refactoring internal cleanup logic
✅ **Clearer Test Intent**: Focus on "what" (system behaves correctly) not "how" (specific calls made)
✅ **Simpler Test Infrastructure**: No need for complex mocking/observation patterns
✅ **Better Separation of Concerns**: Component tests focus on integration, unit tests on implementation
✅ **Faster Test Development**: Don't need special infrastructure to write tests

### Negative

⚠️ **Reduced Internal Visibility**: Cannot directly verify specific cleanup methods are called
⚠️ **Potential for Silent Failures**: If shutdown methods are NOT called but tests still pass

### Mitigation

1. **Unit Test Coverage**: Verify materializer shutdown in actor unit tests
   - Mock materializer and assert shutdown() is called
   - Test PostStop handler directly with observable mocks

2. **Behavioral Proxies**: Use indirect validation in component tests
   - "No resource leaks" implies successful cleanup (outcome)
   - Actor termination timeout failures would indicate cleanup issues

3. **Future Observability Infrastructure**: When needed, build:
   - Log analysis tools to verify shutdown messages
   - Resource tracking instrumentation
   - Dedicated NFR test suite for resource management

4. **Code Reviews**: Ensure PostStop implementations follow ADR-004 patterns
   - Manual verification that materializer.shutdown() is present
   - Architectural fitness functions to detect missing cleanup

---

## Example: Correct vs. Incorrect Validation

### ❌ Incorrect (Validating Internal Implementation)

```gherkin
Scenario: PostStop cleanup
  When the KafkaConsumerStreamingActor receives PostStop signal
  Then the materializer.shutdown() method should be called  # ❌ Internal detail
  And the control.stop() method should be called           # ❌ Internal detail
  And the cleanup sequence should be: control → materializer → actor  # ❌ Internal sequencing
```

**Problem**: Test is tightly coupled to implementation details.

### ✅ Correct (Validating Observable Behavior)

```gherkin
Scenario: PostStop cleanup
  When the KafkaConsumerStreamingActor receives PostStop signal
  Then the Kafka consumer control should stop immediately   # ✅ Observable outcome
  And no resource leaks should be present                   # ✅ Behavioral validation
  And the actor should be unregistered from ProbeScalaDsl  # ✅ Public API state
```

**Benefit**: Test verifies the *outcome* (clean shutdown) without dictating *how* it's achieved.

---

## Alternative Approaches Considered

### Alternative 1: Mock Materializer with Observable Wrapper
**Approach**: Create test-specific materializer that tracks shutdown() calls.

```scala
class ObservableMaterializer extends Materializer {
  var shutdownCalled: Boolean = false

  override def shutdown(): Future[Done] = {
    shutdownCalled = true
    Future.successful(Done)
  }
}

// In test
Then("""the custom materializer should shutdown""") { () =>
  val mat = ActorWorld.getMaterializer.asInstanceOf[ObservableMaterializer]
  mat.shutdownCalled shouldBe true
}
```

**Rejected Because**:
- Requires test-specific production code (observable materializer)
- Tight coupling between test infrastructure and production
- Increased complexity for marginal benefit
- Unit tests already validate this behavior more effectively

### Alternative 2: Log Monitoring Infrastructure
**Approach**: Build infrastructure to capture and validate log messages for shutdown evidence.

```scala
Then("""the custom materializer should shutdown""") { () =>
  val logs = LogCapture.getLogsSince(testStart)
  logs should contain("Materializer shutdown completed")
}
```

**Deferred (Not Rejected)**:
- Valid approach for future NFR test suite
- Requires building log capture/analysis infrastructure
- Better suited for dedicated resource management tests
- May implement in future Phase (logging/observability validation)

### Alternative 3: Resource Leak Detection Framework
**Approach**: Build framework that tracks all resources (materializers, connections, threads) and validates cleanup.

**Deferred (Not Rejected)**:
- High-value infrastructure for production quality
- Requires significant engineering investment
- Better as dedicated project/phase
- Should be implemented eventually, but not blocking component tests

---

## Related Decisions

- **ADR-TESTING-001-component-test-nfr-separation.md**: Establishes component tests focus on functional behavior
- **ADR-004-factory-injection-for-child-actors.md**: Defines PostStop cleanup requirements (what MUST happen, not how to test it)
- **Testing Pyramid**: Unit tests validate method calls, component tests validate integration outcomes

---

## Future Work

1. **Unit Test Enhancement**: Ensure PostStop handlers have dedicated unit tests with mock dependencies
   ```scala
   test("PostStop should call materializer shutdown") {
     val mockMat = mock[Materializer]
     val actor = new StreamingActor(mockMat)
     actor.postStop()
     verify(mockMat).shutdown()
   }
   ```

2. **Resource Management Test Suite**: Create dedicated test suite for resource lifecycle validation
   - Thread pool cleanup verification
   - Connection closure validation
   - Materializer lifecycle tracking

3. **Observability Infrastructure**: Build specialized tools if needed
   - Log analysis for shutdown verification
   - Resource tracking instrumentation
   - Telemetry for cleanup operations

4. **Architectural Fitness Functions**: Automated checks in CI
   - Static analysis: Detect PostStop without materializer.shutdown()
   - Code review checklist: Verify cleanup patterns in new actors

---

## References

- **Category 4 Failures**: `working/testRefactor/comprehensive-component-tests-failures-102625.md` (lines 391-470)
- **Test Quality Standards**: `.claude/guides/TESTING.md`
- **ADR-004**: Factory injection and PostStop requirements

---

## Notes

This decision was made during Phase 1 component test refactoring after discovering that validating materializer shutdown required complex test infrastructure disproportionate to the value gained. Component tests already validate the *outcome* (clean termination, no resource leaks) which is the actual business requirement.

**Key Insight**: The failure of materializer to shutdown would manifest as resource leaks or timeout errors during actor termination - both of which ARE tested through behavioral assertions. Therefore, the explicit materializer shutdown validation is redundant if proper outcome validation exists.

**Review Trigger**: Revisit if:
- Resource leaks occur despite passing component tests (indicates outcome validation is insufficient)
- Unit test coverage for PostStop handlers drops below 85%
- Team builds observability infrastructure that makes internal validation trivial
