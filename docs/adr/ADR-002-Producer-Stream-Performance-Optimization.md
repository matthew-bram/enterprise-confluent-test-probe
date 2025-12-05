# ADR-002: Producer Stream Performance Optimization

**Date**: 2025-10-16
**Status**: Accepted (Deferred Implementation)
**Deciders**: Engineering Team
**Related Issues**: Scala Ninja Review P0-2

**Update (2025-10-16)**: Migrated from Akka Streams to Pekko Streams as part of Akka → Pekko migration (see **ADR-000**). All stream APIs remain identical; this decision and its rationale are unaffected by the migration.

---

## Context

The `KafkaProducerStreamingActor` currently creates a new Akka Stream materialization for each `ProduceEvent` message using the `Source.single()` pattern:

```scala
case ProduceEvent(env, replyTo) =>
  Source.single(env)
    .mapAsync(1) { envelope =>
      Future {
        SchemaRegistryEncoder.encode(envelope, envelope.topic)
      }(blockingEc)
    }
    .map { encoded =>
      new ProducerRecord[String, Array[Byte]](topicDirective.topic, env.key.orNull, encoded)
    }
    .runWith(Producer.plainSink(producerSettings))
```

During Scala Ninja peer review, this was flagged as **P0 Critical** due to:
- Potential **10-100x performance degradation** vs persistent stream
- TCP connection thrashing to Kafka
- Limited throughput (~10 msg/s per topic)
- Resource churn from repeated stream materialization

The question: **Should we refactor to use a persistent stream with SourceQueue pattern now, or defer until we have real performance data?**

---

## Decision

**We will DEFER implementing persistent stream optimization until smoke testing against real Kafka infrastructure provides performance data.**

### Rationale

**Test-Probe has unique characteristics that may make the current design acceptable**:

#### JWT Token Lifetime

**Critical insight**: OAuth JWT tokens typically have **TTL > 1 hour**

- Initial TLS handshake and JWT minting: Expensive (first connection)
- Subsequent messages: Reuse TCP connection + valid JWT token
- Kafka client connection pooling: Modern Kafka producers maintain connection pools internally
- **Result**: "Per-message stream" likely reuses underlying Kafka producer connection

#### Test Execution Patterns

**Functional Tests** (~10 mins):
- 20-200 events total across all topics
- Sequential execution via Cucumber (PicoContainer controls concurrency)
- Pattern: One event at a time across N topics
- Throughput: ~1-3 events/minute (far below 10 msg/s threshold)

**Resilience Tests** (20-40 mins):
- 10-50 events total
- Even lower throughput requirements
- Pattern: State transition events, not burst scenarios

#### Concurrency Model

**Cucumber execution is controlled**:
- PicoContainer manages scenario concurrency
- Test teams configure parallel execution (default: sequential)
- **Reality**: Likely sequential event production across N topics
- **No burst scenario**: Not producing 100+ events in <1 second

#### Unknown Use Cases

**We don't know every team's testing patterns yet**:
- Some teams may have unique requirements
- Better to optimize with real data than assumptions
- Premature optimization is the root of all evil

---

## Alternatives Considered

### 1. Persistent Stream with SourceQueue (Scala Ninja Recommendation)

**Pros**:
- Best practice for high-throughput Kafka production
- Eliminates stream materialization overhead
- Single persistent Kafka connection per topic
- Enables backpressure and queue overflow handling

**Cons**:
- Adds complexity (queue lifecycle management)
- Requires PostStop cleanup of queue
- Queue overflow scenarios to handle
- May be over-engineering for low-volume test scenarios

**Implementation**:
```scala
// Persistent stream created in Behaviors.setup
val (queue, done) = Source
  .queue[(EventEnvelope, ActorRef[ProduceResult])](
    bufferSize = 1000,
    OverflowStrategy.backpressure
  )
  .mapAsync(1) { case (envelope, replyTo) =>
    Future {
      val encoded = SchemaRegistryEncoder.encode(envelope, envelope.topic)
      (encoded, envelope, replyTo)
    }(blockingEc)
  }
  .map { case (encoded, env, replyTo) =>
    (new ProducerRecord[String, Array[Byte]](topicDirective.topic, env.key.orNull, encoded), replyTo)
  }
  .via(Producer.flexiFlow(producerSettings))
  .map { result => result.passThrough ! ProducedAck }
  .toMat(Sink.ignore)(Keep.both)
  .run()

// Handle ProduceEvent by offering to queue
case ProduceEvent(env, replyTo) =>
  queue.offer((env, replyTo)).foreach {
    case QueueOfferResult.Enqueued => // Success
    case QueueOfferResult.Dropped => replyTo ! ProducedNack(new Exception("Queue full"))
    case _ => replyTo ! ProducedNack(new Exception("Stream closed"))
  }
```

**Complexity increase**: ~50 additional lines, queue lifecycle management, overflow handling

### 2. Benchmark Now, Optimize if Needed

**Pros**: Data-driven decision
**Cons**: Requires Kafka infrastructure setup before smoke testing

### 3. Current Design (Per-Message Stream)

**Pros**:
- Simple, straightforward
- No queue management
- Backpressure handled by Akka ask pattern
- May be "good enough" for test-probe use case

**Cons**:
- Stream materialization overhead
- Potential performance issue IF throughput requirements increase

---

## Decision: Defer Until Smoke Testing

**Approach**: Accept current design, measure during smoke testing, optimize if needed

**Trigger Criteria for Revisiting**:

1. **Performance Issues During Smoke Testing**:
   - Produce latency P99 > 500ms
   - Throughput < 10 events/second
   - Connection pool exhaustion errors in logs

2. **Real-World Test Patterns Exceed Assumptions**:
   - Teams report burst scenarios (>50 events/second)
   - Concurrent test execution causes contention
   - JWT token refresh issues observed

3. **Kafka Infrastructure Feedback**:
   - Kafka brokers report excessive connection churn
   - Network team flags TCP connection thrashing
   - OAuth token endpoint reports high request volume

4. **Test Execution Time Exceeds Acceptable Limits**:
   - 10-minute functional tests take >15 minutes due to producer overhead
   - Resilience tests timeout due to throughput bottleneck

---

## Monitoring and Validation

### Smoke Testing Metrics to Capture

**1. Producer Performance**:
```
probe_kafka_producer_latency_ms{topic, percentile}
  - P50, P95, P99 latency for ProduceEvent
  - Target: P99 < 500ms

probe_kafka_producer_throughput{topic}
  - Events per second per topic
  - Target: >10 events/second sustained

probe_kafka_producer_errors_total{topic, error_type}
  - Connection errors, timeout errors
  - Target: <1% error rate
```

**2. Kafka Infrastructure**:
```
kafka_producer_connection_creation_total{client_id}
  - Count of new TCP connections
  - Target: <10 connections per test

kafka_producer_network_io_total{client_id}
  - Network I/O bytes
  - Baseline: Compare against persistent stream implementation
```

**3. Test Execution Time**:
```
probe_test_execution_duration_seconds{test_type}
  - Total test execution time
  - Target: <10 minutes for functional, <40 minutes for resilience
```

### Smoke Testing Checklist

- [ ] Run functional test (200 events) against real Kafka cluster
- [ ] Run resilience test (50 events) against real Kafka cluster
- [ ] Run concurrent tests (5 parallel scenarios) to measure contention
- [ ] Capture all metrics listed above
- [ ] Review Kafka broker logs for connection warnings
- [ ] Interview test teams for performance feedback

---

## Implementation Plan (If Optimization Needed)

**IF smoke testing reveals performance issues**:

### Phase 1: Benchmarking (1 day)
1. Create benchmark test: Current design vs persistent stream
2. Measure latency, throughput, connection count
3. Quantify actual performance delta (e.g., "Current: 5 msg/s, Persistent: 50 msg/s")

### Phase 2: Implementation (2-3 days)
1. Refactor `KafkaProducerStreamingActor` to use persistent stream
2. Add queue lifecycle management (PostStop cleanup)
3. Add overflow handling with configurable buffer size
4. Update unit tests and BDD scenarios

### Phase 3: Validation (1 day)
1. Re-run smoke tests
2. Verify performance improvements
3. Ensure no regressions in functionality

**Estimated effort**: 4-5 days IF optimization is required

---

## Consequences

### Positive
- **Data-driven decision**: Optimize only if real performance issue exists
- **Simplicity maintained**: Current code is simpler, easier to understand
- **Faster delivery**: Don't block on optimization that may not be needed
- **Focus on smoke testing**: Priority is validating against real Kafka infrastructure

### Negative
- **Potential technical debt**: May need refactor later if performance issues arise
- **Unknown risk**: Don't know actual performance impact until smoke testing
- **Delayed optimization**: If optimization IS needed, adds 4-5 days to timeline

### Neutral
- **Reversible decision**: Can implement persistent stream later if needed
- **Low risk**: Test-probe is not production system, performance requirements are bounded

---

## Related Decisions

- **ADR-000**: Akka to Pekko migration (stream APIs unaffected)
- **ADR-001**: Consumer registry memory management (also deferred optimization)
- **ADR-003** (future): Kafka producer connection pooling strategy (if issues found during smoke testing)
- **ADR-004** (future): Test concurrency limits and throughput requirements

---

## References

- Scala Ninja Review: `working/ScalaNinjaReview-KafkaStreaming-2025-10-16.md` (P0-2)
- Actor Implementation: `test-probe-core/src/main/scala/io/distia/probe/core/actors/KafkaProducerStreamingActor.scala`
- Pekko Streams SourceQueue: https://pekko.apache.org/docs/pekko/current/stream/operators/Source/queue.html
- Kafka Producer Best Practices: https://kafka.apache.org/documentation/#producerconfigs

---

## Review and Revision

**Next Review**: After initial smoke testing against real Kafka infrastructure

**Trigger for Revision**:
- Smoke testing reveals P99 latency > 500ms
- Throughput < 10 events/second observed
- Connection pool exhaustion errors
- Test team feedback on performance issues
- Kafka infrastructure team reports connection thrashing

**Decision Log**:
- **2025-10-16**: Initial decision to defer optimization until smoke testing
- **TBD**: Smoke testing results - REVISIT based on metrics
