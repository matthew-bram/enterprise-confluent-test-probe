# ADR-003: Schema Registry Error Handling Strategy

**Date**: 2025-10-16
**Status**: Accepted (Immediate Implementation)
**Deciders**: Engineering Team
**Related Issues**: Scala Ninja Review P0-3

---

## Context

The Kafka streaming actors (`KafkaProducerStreamingActor` and `KafkaConsumerStreamingActor`) make HTTP calls to Schema Registry for Avro encoding/decoding. These calls can fail due to:

1. **Operational failures**: Network timeouts, SR service down, temporary outages
2. **Configuration failures**: Schema not found (404), authentication errors (401)
3. **Data failures**: Malformed events, incompatible schema versions

During Scala Ninja peer review, this was flagged as **P0 Critical** due to:
- Schema Registry failures can kill the entire stream
- No retry mechanism for transient errors
- No graceful degradation
- Poor observability (no metrics on SR failures)

The question: **How should we handle Schema Registry errors in producer vs consumer, and who should decide retry strategy?**

---

## Decision

**Producer and Consumer have different error handling strategies based on their contract with the test framework.**

### Producer Strategy: Immediate NACK with Exception

**Rationale**: Producer errors indicate the test cannot proceed as designed. The **test/caller should decide** whether to retry (operational error) or fail (configuration error).

**Behavior**:
- Encode failure → Return `ProducedNack(exception)` immediately
- Stream continues processing (error isolated to that message)
- Actor never sees the failure (handled in stream recovery)
- Test receives exception and can:
  - **Retry** if operational (SR down, network timeout)
  - **Fail scenario** if configuration (schema not found, serialization error)

**Never retry on behalf of the test** - the test knows its own context and timeout constraints.

### Consumer Strategy: Skip Event with Warning

**Rationale**: Consumer errors are non-fatal - a malformed event should not crash the entire test. The test will notice the missing event when it queries the registry.

**Behavior**:
- Decode failure → Log warning, skip event, continue stream
- Event never appears in registry (predictable behavior)
- Test polling for event will get `ConsumedNack()` (event not found)
- Actor never sees the failure (handled in stream recovery)

**Already implemented** in FIX #4 ✅

---

## Error Type Classification

### Operational Errors (Transient - Test Can Retry)

- **Network timeout**: `java.net.SocketTimeoutException`
- **SR service unavailable**: `java.io.IOException`, HTTP 503
- **Connection refused**: `java.net.ConnectException`
- **Temporary overload**: HTTP 429 (rate limit)

**Test behavior**: Retry with backoff (test framework controls retry logic)

### Configuration Errors (Permanent - Test Should Fail)

- **Schema not found**: HTTP 404
- **Authentication failure**: HTTP 401, 403
- **Incompatible schema**: `org.apache.avro.AvroTypeException`
- **Serialization error**: `IllegalArgumentException` from encoder

**Test behavior**: Fail scenario immediately, log diagnostic information

---

## Implementation

### Producer Error Handling

```scala
// KafkaProducerStreamingActor.scala
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
    .onComplete {
      case Success(_) =>
        ctx.log.info(s"Produced event ${env.eventTestId} to ${topicDirective.topic}")
        replyTo ! ProducedAck
      case Failure(ex) =>
        // Schema Registry error, network error, or Kafka error
        ctx.log.error(s"Producer failed for event ${env.eventTestId}: ${ex.getMessage}", ex)

        // TODO: Add OpenTelemetry metric
        // meter.counter("probe_kafka_producer_errors_total")
        //   .add(1, Attributes.of(
        //     AttributeKey.stringKey("topic") -> topicDirective.topic,
        //     AttributeKey.stringKey("error_type") -> classifyError(ex)
        //   ))

        replyTo ! ProducedNack(ex)  // Return exception to caller
    }
  Behaviors.same
```

**Key points**:
- `onComplete` catches ALL failures (SR, network, Kafka)
- Exception passed to caller via `ProducedNack(ex)`
- Actor continues processing (stream isolated per message with `Source.single`)
- OpenTelemetry metric tracks error by type

### Consumer Error Handling (Already Implemented)

```scala
// KafkaConsumerStreamingActor.scala
.mapAsync(1) { msg =>
  Future {
    Try(SchemaRegistryDecoder.decode(msg.record.value())) match {
      case Success(env) => Some((env, msg.committableOffset))
      case Failure(ex) =>
        ctx.log.warn(s"Schema decode failed for message on topic ${directive.topic}, skipping: ${ex.getMessage}")

        // TODO: Add OpenTelemetry metric
        // meter.counter("probe_kafka_consumer_decode_errors_total")
        //   .add(1, Attributes.of(
        //     AttributeKey.stringKey("topic") -> directive.topic,
        //     AttributeKey.stringKey("error_type") -> classifyError(ex)
        //   ))

        None
    }
  }(blockingEc)
}
.collect { case Some(tuple) => tuple }  // Filter out decode failures
```

**Key points**:
- `Try` wrapper prevents stream termination
- Event skipped, never added to registry
- OpenTelemetry metric tracks decode failures
- Actor never sees the error

---

## Observability: OpenTelemetry Metrics

### Producer Metrics

```
probe_kafka_producer_errors_total{topic, error_type, testId}
  - Counter for all producer errors
  - error_type: "schema_not_found", "network_timeout", "serialization_error", etc.

probe_kafka_producer_encode_duration_ms{topic, percentile}
  - Histogram for Schema Registry encode latency
  - P50, P95, P99 percentiles
```

### Consumer Metrics

```
probe_kafka_consumer_decode_errors_total{topic, error_type, testId}
  - Counter for all decode failures
  - error_type: "schema_not_found", "malformed_avro", "network_timeout", etc.

probe_kafka_consumer_decode_duration_ms{topic, percentile}
  - Histogram for Schema Registry decode latency
  - P50, P95, P99 percentiles
```

### Error Classification

```scala
def classifyError(ex: Throwable): String = ex match {
  case _: SocketTimeoutException => "network_timeout"
  case _: ConnectException => "connection_refused"
  case _: IOException => "network_io"
  case e: Exception if e.getMessage.contains("404") => "schema_not_found"
  case e: Exception if e.getMessage.contains("401") => "auth_failure"
  case _: AvroTypeException => "incompatible_schema"
  case _: IllegalArgumentException => "serialization_error"
  case _ => "unknown"
}
```

---

## Test Behavior Examples

### Scenario 1: Schema Registry Down (Operational Error)

```gherkin
Scenario: Producer retries on Schema Registry outage
  Given a KafkaProducerStreamingActor for topic "orders"
  And Schema Registry is temporarily unavailable
  When a ProduceEvent command is sent
  Then the producer should return ProducedNack with IOException
  And the test should retry the produce operation
  And after Schema Registry recovers, the retry should succeed
```

**Test implementation**:
```scala
// Cucumber step definition
When("""a ProduceEvent command is sent""") { () =>
  val result = Await.result(ProbeScalaDsl.produceEvent(testId, topic, event), 5.seconds)
  result match {
    case ProducingFailed(ex: IOException) =>
      // Operational error - retry after backoff
      Thread.sleep(1000)
      val retryResult = Await.result(ProbeScalaDsl.produceEvent(testId, topic, event), 5.seconds)
      retryResult shouldBe ProducingSuccess()
    case ProducingSuccess() => // Success on first attempt
    case ProducingFailed(ex) => fail(s"Unexpected error: $ex")
  }
}
```

### Scenario 2: Schema Not Found (Configuration Error)

```gherkin
Scenario: Producer fails immediately on schema not found
  Given a KafkaProducerStreamingActor for topic "unknown-topic"
  And Schema Registry has no schema for "unknown-topic"
  When a ProduceEvent command is sent
  Then the producer should return ProducedNack with 404 error
  And the test scenario should fail with diagnostic message
  And the error should be logged with schema details
```

**Test implementation**:
```scala
When("""a ProduceEvent command is sent""") { () =>
  val result = Await.result(ProbeScalaDsl.produceEvent(testId, topic, event), 5.seconds)
  result match {
    case ProducingFailed(ex) if ex.getMessage.contains("404") =>
      // Configuration error - fail scenario immediately
      fail(s"Schema not found for topic $topic. Check Schema Registry configuration.")
    case ProducingSuccess() => fail("Expected schema not found error")
    case ProducingFailed(ex) => fail(s"Unexpected error: $ex")
  }
}
```

### Scenario 3: Consumer Skips Malformed Event

```gherkin
Scenario: Consumer skips malformed event and continues stream
  Given a KafkaConsumerStreamingActor for topic "orders"
  And the topic contains 10 valid events and 1 malformed event
  When the consumer processes all events
  Then the consumer should log warning for malformed event
  And the consumer registry should contain exactly 10 events
  And the consumer stream should continue processing
```

---

## Alternatives Considered

### 1. Automatic Retry in Producer Stream

**Rejected**: Test has better context for retry strategy (timeout constraints, business logic).

**Scala Ninja suggested**:
```scala
.mapAsync(1) { envelope =>
  Retry(maxAttempts = 3, minBackoff = 100.millis, maxBackoff = 5.seconds) { () =>
    Future(SchemaRegistryEncoder.encode(envelope, envelope.topic))(blockingEc)
  }
}
```

**Problems**:
- Test timeout might be exceeded during retries
- Test doesn't know retries are happening (opaque)
- Configuration errors get retried needlessly
- No way for test to distinguish operational vs configuration errors

### 2. Circuit Breaker for Schema Registry

**Deferred**: Good idea for production systems, but adds complexity for test-probe.

**Would provide**:
- Fail-fast when SR is persistently down
- Prevent resource exhaustion from repeated SR calls

**Downsides**:
- Adds state management
- Circuit state shared across tests (or not?)
- May need external circuit breaker service

**Decision**: Revisit during smoke testing if SR reliability is an issue.

### 3. Fail Stream on First Error

**Rejected**: Too brittle for test scenarios.

**Would cause**:
- Consumer: One malformed event kills entire test
- Producer: First encode failure terminates stream (all subsequent produces fail)

---

## Consequences

### Positive
- **Clear responsibility**: Test decides retry vs fail, not framework
- **Predictable behavior**: Producer NACKs, consumer skips
- **Better observability**: OpenTelemetry metrics for all SR failures
- **Fail-fast for configuration**: Schema errors surface immediately
- **Graceful degradation**: Consumer continues despite malformed events

### Negative
- **Test complexity**: Tests must handle `ProducedNack` and retry logic
- **No automatic retry**: Framework doesn't retry transient errors
- **Observability gap**: OpenTelemetry instrumentation not yet implemented

### Neutral
- **Stream isolation**: Errors don't kill streams (good for resilience)
- **Actor isolation**: Actors never see errors (good for supervision)

---

## Future Enhancements

### Phase 1: OpenTelemetry Instrumentation (Next Sprint)
- Implement error classification
- Add metrics for producer/consumer SR failures
- Dashboard for SR health monitoring

### Phase 2: Circuit Breaker (If Needed)
- Add circuit breaker for Schema Registry client
- Configurable failure threshold and timeout
- Shared vs per-test circuit state

### Phase 3: Retry Helpers (Nice to Have)
- Provide utility functions for common retry patterns
- Example: `ProbeRetry.withExponentialBackoff(maxAttempts, maxDuration)`

---

## Acceptance Criteria

### Implementation
- [x] Producer returns `ProducedNack(ex)` on encode failure
- [x] Consumer skips events on decode failure (already implemented)
- [ ] Add OpenTelemetry metrics for producer errors
- [ ] Add OpenTelemetry metrics for consumer decode errors
- [ ] Error classification helper: `classifyError(ex): String`

### Testing
- [ ] Unit test: Producer encode fails → returns `ProducedNack`
- [ ] Unit test: Consumer decode fails → event skipped, registry empty
- [ ] Component test: SR down → producer NACKs with IOException
- [ ] Component test: Schema not found → producer NACKs with 404 error
- [ ] BDD scenario: Test retries on operational error, succeeds
- [ ] BDD scenario: Test fails immediately on configuration error

### Documentation
- [x] ADR-003 created
- [ ] Update CLAUDE.md with error handling pattern
- [ ] Runbook: Troubleshooting SR errors
- [ ] API docs: Error types and test retry strategies

---

## Related Decisions

- **ADR-001**: Consumer registry memory management
- **ADR-002**: Producer stream performance optimization
- **ADR-004** (future): Circuit breaker strategy for external services

---

## References

- Scala Ninja Review: `working/ScalaNinjaReview-KafkaStreaming-2025-10-16.md` (P0-3)
- Actor Implementation: `test-probe-core/src/main/scala/com/company/probe/core/actors/KafkaProducerStreamingActor.scala`
- Error Models: `test-probe-core/src/main/scala/com/company/probe/core/pubsub/models/PubSubModels.scala`

---

## Review and Revision

**Next Review**: After OpenTelemetry instrumentation and smoke testing

**Trigger for Revision**:
- SR failures exceed 5% of test executions
- Test teams report retry logic is too complex
- Circuit breaker needed for SR reliability

**Decision Log**:
- **2025-10-16**: Initial decision - immediate NACK for producer, skip for consumer
