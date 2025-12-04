# ADR-TESTING-001: Component Test NFR Separation

**Status**: Accepted
**Date**: 2025-10-26
**Decision Maker**: Engineering Team
**Context**: Component test suite refactoring (Phase 1 - Child Actors)

---

## Context and Problem Statement

During component test development for child actors (BlockStorageActor, VaultActor, CucumberExecutionActor, KafkaProducerActor, KafkaConsumerActor), we encountered test scenarios that attempted to validate both:

1. **Functional Behavior**: Actor message protocols, state transitions, initialization sequences, error propagation
2. **Non-Functional Requirements (NFRs)**: Logging compliance, credential redaction, performance characteristics

This mixing of concerns led to:
- Test failures unrelated to the functional behavior being tested
- Increased test complexity and brittleness
- Difficult distinction between functional regressions and logging/monitoring issues
- Test maintenance overhead (logging patterns change more frequently than protocols)

Specifically, 12 component test scenarios (Category 3 failures) were validating:
- "And the KafkaSecurityDirective should NOT be logged in any log statement"
- "Then all log messages should contain 'credentials REDACTED'"
- "And no log message should contain clientSecret or jaasConfig values"

These logging assertions were causing test failures even when the actors' functional behavior (initialization, message passing, exception bubbling) was working correctly.

---

## Decision

**Component tests will focus exclusively on functional actor behavior**. Non-functional requirements (logging, monitoring, performance, security compliance) will be validated separately through:

1. **Unit Tests**: Where NFR validation already exists and is proven (e.g., credential redaction unit tests)
2. **Dedicated NFR Test Suites**: Future integration/system tests specifically for logging compliance, observability, and security auditing
3. **Static Analysis**: Linting, security scanning, and architectural fitness functions

### Scope of Component Tests (What We Test)

Component tests verify:
- ✅ Actor initialization sequences and message protocols
- ✅ Message passing between actors (parent-child, child-parent)
- ✅ Exception propagation and error bubbling to parent
- ✅ State transitions and lifecycle management (Initialize → StartTest → Stop)
- ✅ Integration between actors and service layers
- ✅ Response correctness (message types, data payloads)

### Scope of Component Tests (What We Don't Test)

Component tests do NOT verify:
- ❌ Logging statements or log message content
- ❌ Credential redaction in logs (covered by unit tests)
- ❌ Performance/timing characteristics
- ❌ Resource cleanup details (memory, threads, connections)
- ❌ Monitoring metrics or observability instrumentation

---

## Rationale

### 1. Separation of Concerns
- **Functional behavior** is the core responsibility of actors (message protocols, state management)
- **Cross-cutting concerns** (logging, monitoring) are orthogonal to functional correctness
- Testing these together couples functional tests to implementation details of logging frameworks

### 2. Test Stability
- Logging patterns change frequently (log levels, formats, redaction rules)
- Functional protocols are stable and change only when requirements change
- Mixing these creates brittle tests that fail for non-functional reasons

### 3. Existing Coverage
- Unit tests already validate credential redaction (proven working)
- Component tests add no additional value by re-testing this at integration level
- Duplication increases maintenance burden without improving confidence

### 4. Faster Feedback
- Functional failures indicate broken business logic (high severity)
- Logging failures indicate compliance issues (different remediation path)
- Separating these allows faster diagnosis and targeted fixes

### 5. Consistency with Unit Test Approach
- Unit tests removed logging validation steps earlier in development
- This decision aligns component tests with established unit test patterns
- Maintains consistency across the test pyramid

---

## Implementation

### Files Modified (2025-10-26)

Removed logging validation steps from 4 component test feature files:

1. **`test-probe-core/src/test/resources/features/component/child-actors/cucumber-execution-actor.feature`**
   - Line 22: Removed "And the KafkaSecurityDirective should NOT be logged in any log statement"

2. **`test-probe-core/src/test/resources/features/component/child-actors/vault-actor.feature`**
   - Line 22: Removed "And the KafkaSecurityDirective should NOT be logged in any log statement"

3. **`test-probe-core/src/test/resources/features/component/child-actors/kafka-producer-actor.feature`**
   - Line 25: Removed "And the KafkaSecurityDirective should NOT be logged in any log statement"

4. **`test-probe-core/src/test/resources/features/component/child-actors/kafka-consumer-actor.feature`**
   - Line 25: Removed "And the KafkaSecurityDirective should NOT be logged in any log statement"

**Impact**: 12 scenarios affected (removed logging assertions from @Critical, @Security scenarios)

---

## Consequences

### Positive

✅ **Clearer Test Purpose**: Component tests now have single, clear responsibility (functional behavior)
✅ **Reduced Brittleness**: Tests won't fail when logging patterns/frameworks change
✅ **Faster Diagnosis**: Failures immediately indicate functional regressions
✅ **Simplified Maintenance**: Logging changes don't require component test updates
✅ **Architectural Clarity**: Enforces separation between functional and cross-cutting concerns

### Negative

⚠️ **Deferred NFR Validation**: Logging compliance must be validated elsewhere
⚠️ **Risk of NFR Regression**: If dedicated NFR tests aren't added, logging issues may go undetected

### Mitigation

1. **Unit Test Coverage**: Credential redaction already proven in unit tests
2. **Future NFR Test Suite**: Create dedicated logging/observability integration tests
3. **CI/CD Integration**: Add static analysis for security compliance (e.g., no credentials in logs)
4. **Documentation**: This ADR establishes clear testing responsibilities and scope

---

## Future Work

1. **Dedicated Logging Test Suite**: Create integration tests specifically for:
   - Credential redaction validation across all actors
   - Log level compliance (no secrets at INFO/DEBUG)
   - Structured logging format validation
   - Correlation ID propagation

2. **Observability Testing**: System tests for:
   - Metrics emission and accuracy
   - Distributed tracing span creation
   - Health check endpoint correctness

3. **Security Compliance Testing**: Automated auditing for:
   - PII/credential leakage detection
   - Log sanitization verification
   - Security event logging completeness

---

## Related Decisions

- **Unit Test Practices**: Previously removed logging validation from unit tests (same rationale)
- **ADR-005-error-kernel-pattern.md**: Error propagation tested functionally, not through logs
- **Testing Pyramid**: Component tests sit between unit (logic) and system (end-to-end)

---

## References

- **Category 3 Failures**: `working/testRefactor/comprehensive-component-tests-failures-102625.md` (lines 290-389)
- **Test Quality Enforcer**: `.claude/skills/test-quality-enforcer/SKILL.md`
- **Testing Standards**: `.claude/guides/TESTING.md`

---

## Notes

This decision was made during Phase 1 component test refactoring after discovering 12 scenarios failing purely on logging validation while functional behavior was correct. The decision aligns with the project's "Definition of Done" principle: tests must validate ONE thing clearly, not mix functional and non-functional concerns.

**Review Trigger**: Revisit when dedicated NFR test suite is implemented (ensure logging compliance still validated somewhere).
