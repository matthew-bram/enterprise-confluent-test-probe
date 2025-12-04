# ADR-001: Consumer Registry Memory Management

**Date**: 2025-10-16
**Status**: Accepted
**Deciders**: Engineering Team
**Related Issues**: Scala Ninja Review P0-1

---

## Context

The `KafkaConsumerStreamingActor` maintains an in-memory registry (`mutable.Map[UUID, EventEnvelope]`) to store consumed events for Cucumber test access via `ProbeScalaDsl.fetchConsumedEvent()`.

During Scala Ninja peer review, this was flagged as **P0 Critical** due to:
- No eviction policy
- Registry grows unbounded during test execution
- Potential memory leak in long-running tests

The question: **Should we implement cache eviction (LRU, TTL, etc.) or event deletion after retrieval?**

---

## Decision

**We will NOT implement cache eviction or automatic event deletion at this time.**

### Rationale

**Test-Probe is NOT a production system** - it's a bounded test execution framework with the following characteristics:

#### Test Execution Patterns

**Functional Tests** (~10 mins):
- 20-200 events per topic
- Typical event size: ~2KB (Avro + metadata)
- Memory footprint: 40KB - 400KB per topic
- Multiple topics: 5-10 typical
- Total memory: ~2MB - 4MB per test

**Resilience Tests** (20-40 mins):
- 10-50 events per topic
- Smaller event volume (state transitions)
- Memory footprint: 20KB - 100KB per topic
- Total memory: ~100KB - 1MB per test

#### Registry Lifecycle

- **Bounded by test timeout**: Maximum 40 minutes
- **Actor lifecycle**: Registry exists only while `KafkaConsumerStreamingActor` is alive
- **Cleanup**: Actor stops when test completes, registry is garbage collected
- **Typical tests**: Most complete in &lt;10 minutes

#### Memory Analysis

**Worst case scenario** (40-min resilience test):
- 200 events × 2KB = 400KB per topic
- 10 topics = 4MB total
- Acceptable memory overhead for JVM test process

**Production deployment** would have different constraints, but Test-Probe is designed for:
- Short-lived test execution
- Bounded event consumption
- Ephemeral actor lifecycle

---

## Alternatives Considered

### 1. LRU Cache with Size Limit
**Pros**: Bounds memory usage predictably
**Cons**:
- Adds complexity (eviction logic, cache library dependency)
- May evict events still needed by Cucumber scenarios
- Overkill for bounded test scenarios

### 2. Time-To-Live (TTL) Eviction
**Pros**: Auto-cleanup of old events
**Cons**:
- Requires background cleanup task
- TTL tuning difficult (varies by test type)
- Events may be evicted before Cucumber accesses them

### 3. Delete After Retrieval
**Pros**: Natural cleanup, simple implementation
**Cons**:
- Breaks scenarios that fetch same event multiple times
- Cucumber steps often verify same event from different angles
- Would require test rewrite or event caching in test context

---

## Mitigation Strategies (If Memory Becomes an Issue)

### Short-Term (No Code Changes)

**1. Increase JVM Heap Size**
```bash
# Current default
-Xmx2G

# Increase if needed
-Xmx4G -Xmx8G
```

**2. Monitor Memory Usage**
- Add JMX metrics for registry size
- Log warning if registry exceeds threshold (e.g., 1000 events)
- Alert teams if memory pressure detected

### Medium-Term (Minimal Code Changes)

**3. Delete After Retrieval (Optional)**
```scala
// In ProbeScalaDsl.fetchConsumedEventBlocking()
consumer.ask[ConsumedResult](replyTo => FetchConsumedEvent(eventTestId, replyTo, deleteAfterFetch = true))

// In KafkaConsumerStreamingActor
case FetchConsumedEvent(eventTestId, replyTo, deleteAfterFetch) =>
  registry.get(eventTestId) match {
    case Some(event) =>
      if (deleteAfterFetch) registry.remove(eventTestId)
      replyTo ! ConsumedAck(event)
    case None => replyTo ! ConsumedNack()
  }
```

**Pros**: Simple, no external dependencies
**Cons**: Requires Cucumber step refactoring to avoid multi-fetch scenarios

### Long-Term (If Test-Probe Becomes Production System)

**4. Redis/External Cache**
- Move registry to Redis with TTL
- Supports distributed test execution
- Automatic eviction via Redis TTL

**5. Event Streaming Pattern**
- Stream events to storage (S3/DB) instead of in-memory
- Query storage for event retrieval
- Unbounded event history with minimal memory

---

## Consequences

### Positive
- **Simplicity**: No additional code complexity or dependencies
- **Performance**: No eviction overhead, direct map lookup
- **Predictability**: Registry behavior is straightforward
- **Testability**: Easy to reason about and test

### Negative
- **Memory growth**: Registry grows linearly with consumed events (bounded by test timeout)
- **Monitoring gap**: No automatic alerts for large registries
- **Future refactor**: May need eviction if test patterns change

### Neutral
- **Memory budget**: Test teams must be aware of memory constraints
- **Documentation**: Need to document expected memory footprint

---

## Compliance and Testing

**Does NOT violate**:
- Test-Probe design principles (simplicity, reliability)
- Memory safety (bounded by test timeout)
- Actor lifecycle (registry cleaned up on actor stop)

**Monitoring**:
- Add log statement on registry size in `FetchConsumedEvent` handler
- Document expected memory footprint in operational runbook

---

## Related Decisions

- **ADR-002** (future): Event storage strategy if Test-Probe expands to long-running scenarios
- **ADR-003** (future): Distributed test execution requiring shared event registry

---

## References

- Scala Ninja Review: `working/ScalaNinjaReview-KafkaStreaming-2025-10-16.md` (P0-1)
- Actor Implementation: `test-probe-core/src/main/scala/com/company/probe/core/actors/KafkaConsumerStreamingActor.scala`
- DSL Implementation: `test-probe-core/src/main/scala/com/company/probe/core/pubsub/ProbeScalaDsl.scala`

---

## Review and Revision

**Next Review**: When test patterns exceed 40-minute execution or event counts exceed 500 per topic

**Trigger for Revision**:
- Memory pressure in production test environments
- Test execution times extend beyond 1 hour
- Distributed test execution requirements
- Team feedback on memory issues
