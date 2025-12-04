# ADR-004: Consumer Stream Lifecycle Management

**Date**: 2025-10-16
**Status**: Accepted
**Deciders**: Engineering Team
**Related Issues**: Scala Ninja Review P0-4

**Update (2025-10-16)**: Migrated from Akka Streams to Pekko Streams as part of Akka → Pekko migration (see **ADR-000**). Stream lifecycle APIs remain identical; this decision and its rationale are unaffected by the migration.

---

## Context

The `KafkaConsumerStreamingActor` manages an Akka Streams consumer that must be properly shut down during actor PostStop to ensure Kafka offset commits complete before the stream terminates.

During Scala Ninja peer review, the use of `control.drainAndShutdown(done)` without awaiting the Future was flagged as **P0 Critical** due to potential offset loss.

The question: **How should we manage stream shutdown - SystemMaterializer with drainAndShutdown, or custom materializer with control.stop?**

---

## Decision

**We will use a CUSTOM MATERIALIZER with `control.stop` followed by explicit materializer shutdown.**

### Pattern

```scala
// Setup: Custom materializer scoped to this actor
implicit val mat: Materializer = Materializer(ctx)

// PostStop: Stop control, then shutdown materializer
.receiveSignal {
  case (_, PostStop) =>
    ctx.log.info(s"PostStop — shutting down Kafka stream for test $testId, topic ${directive.topic}")
    ProbeScalaDsl.unRegisterConsumerActor(testId, directive.topic)

    // TODO: DO NOT CHANGE without reading ADR-004
    // control.stop stops accepting new messages, DOES NOT drain pending offsets
    // This is intentional: test is over, no need to commit pending offsets
    // If test termination was due to exception, offsets are already invalid
    control.stop.map(_ => mat.shutdown).recover {
      case e: Throwable =>
        ctx.log.error(s"Kafka or materializer was not shutdown. Investigate $e with message ${e.getMessage}")
    }
    Behaviors.same
}
```

---

## Rationale

### Test-Probe Specific Context

**Test lifecycle**:
1. Test starts → Consumer stream begins
2. Test executes → Events consumed and committed (batched every 20 messages)
3. Test completes → Actor receives stop signal
4. PostStop fires → Stream shutdown initiated

**Critical insight**: By the time PostStop fires, the test is **already over**:
- Test result has been determined (pass/fail)
- Evidence uploaded to S3
- Cucumber execution complete

**Offset commit strategy**:
- Offsets are committed **during test execution** (batch every 20 messages)
- Final batch commit happens when test completes normally
- If PostStop fires due to **exception**, offsets are already invalid (test will re-run)

### Why NOT drainAndShutdown

**Scala Ninja recommended**:
```scala
control.drainAndShutdown(done).onComplete { ... }  // or Await.result
```

**Problems for test-probe**:
1. **Drains pending messages**: Continues processing Kafka messages after test is done
2. **Commits final offsets**: May commit offsets for events the test didn't validate
3. **Blocks PostStop**: If using Await.result, delays actor termination
4. **Unnecessary work**: Test result won't change, evidence already uploaded

**Specific scenario**:
- Test consumes 100 events
- Test validation completes after event 95
- Events 96-100 still in Kafka buffer
- PostStop fires (test done)
- **drainAndShutdown**: Processes 96-100, commits offsets (WHY?)
- **control.stop**: Stops immediately, discards 96-100 (CORRECT)

### Why Custom Materializer

**SystemMaterializer** (Scala Ninja recommended):
- Managed by ActorSystem
- Shared across all actors
- No manual shutdown needed
- **Problem**: We cannot control shutdown timing

**Custom Materializer** (our pattern):
- Scoped to this actor
- Explicit lifecycle management
- Shutdown controlled by actor PostStop
- **Benefit**: Precise control over stream termination

**Use case**: If test fails due to broader system exception:
- Control.stop prevents further Kafka consumption
- mat.shutdown releases resources immediately
- Error logged if shutdown fails (diagnostic)
- Actor terminates cleanly

---

## Trade-offs

### Offset Loss Scenario

**Concern**: What if final batch of offsets isn't committed?

**Analysis**:
- Offsets committed every 20 messages during test execution
- Last 0-19 events may not be committed at PostStop
- **Impact**: Next test run re-consumes last 0-19 events

**Why this is acceptable**:
1. **Test isolation**: Each test has unique testId in consumer group
2. **Idempotent validation**: Cucumber scenarios validate expected events by testId
3. **Re-consumption safe**: Events with testId will be filtered correctly
4. **Bounded impact**: Maximum 19 duplicate events (batch size)

**When this matters**:
- Long-running resilience tests (>40 mins)
- Test crashes mid-execution
- **Result**: Next run re-processes last batch (acceptable)

**When this doesn't matter**:
- Test completes normally (all offsets committed during execution)
- Test fails due to validation error (will re-run anyway)
- **Result**: No duplicate processing

---

## Alternatives Considered

### 1. SystemMaterializer + drainAndShutdown + Await

**Recommended by Scala Ninja**

```scala
implicit val mat: Materializer = SystemMaterializer(ctx.system).materializer

.receiveSignal {
  case (_, PostStop) =>
    ProbeScalaDsl.unRegisterConsumerActor(testId, directive.topic)
    try {
      Await.result(control.drainAndShutdown(done), 10.seconds)
      ctx.log.info("Kafka stream shutdown complete")
    } catch {
      case _: TimeoutException => ctx.log.error("Stream shutdown timed out")
    }
    Behaviors.same
}
```

**Rejected because**:
- Blocks PostStop for up to 10 seconds (delays actor termination)
- Drains pending messages test doesn't need
- Commits offsets for events test didn't validate
- Adds complexity with Await and timeout handling

### 2. Fire-and-Forget drainAndShutdown

**Original implementation**

```scala
control.drainAndShutdown(done).recover {
  case e: Throwable => ctx.log.warn(s"Stream shutdown failed: ${e.getMessage}")
}
```

**Rejected because**:
- Future may not complete before actor terminates
- Offsets may not be committed
- Race condition between shutdown and termination

### 3. Custom Materializer + drainAndShutdown (No Await)

**Hybrid approach**

```scala
control.drainAndShutdown(done).map(_ => mat.shutdown).recover { ... }
```

**Rejected because**:
- Still drains pending messages unnecessarily
- Still has race condition (no await)
- Doesn't solve core issue

---

## Consequences

### Positive
- **Fast shutdown**: No waiting for drain, immediate termination
- **Resource control**: Explicit materializer lifecycle management
- **Predictable**: control.stop is deterministic, no race conditions
- **Diagnostic**: Error logged if shutdown fails

### Negative
- **Last batch uncommitted**: 0-19 events may be re-consumed on next test run
- **Manual management**: Custom materializer requires explicit shutdown
- **Non-standard**: Most Akka apps use SystemMaterializer

### Neutral
- **Test re-run safe**: Duplicate event processing doesn't affect test results
- **Acceptable trade-off**: Speed vs completeness (we choose speed)

---

## Future Enhancements

### If Offset Completeness Becomes Critical

**Scenario**: Teams report issues with duplicate event processing on test re-runs

**Option 1**: Await with short timeout
```scala
Try(Await.result(control.drainAndShutdown(done), 2.seconds)) match {
  case Success(_) => ctx.log.info("Offsets committed")
  case Failure(_) => ctx.log.warn("Shutdown timed out, continuing")
}
mat.shutdown()
```

**Option 2**: Commit offsets synchronously before PostStop
```scala
// In test completion handler (before actor stop)
commitFlow.commitScaladsl().onComplete { _ =>
  context.self ! Stop  // Now safe to stop
}
```

---

## Acceptance Criteria

### Implementation
- [x] Use `Materializer(ctx)` instead of SystemMaterializer
- [x] Use `control.stop` instead of `drainAndShutdown`
- [x] Add TODO comment explaining decision (reference ADR-004)
- [x] Log error if shutdown fails

### Documentation
- [x] ADR-004 created
- [x] Rationale for control.stop vs drainAndShutdown documented
- [ ] Update CLAUDE.md with PostStop pattern guidance

---

## Related Decisions

- **ADR-000**: Akka to Pekko migration (stream lifecycle APIs unaffected)
- **ADR-001**: Consumer registry memory management
- **ADR-002**: Producer stream performance optimization
- **ADR-003**: Schema Registry error handling

---

## References

- Scala Ninja Review: `working/ScalaNinjaReview-KafkaStreaming-2025-10-16.md` (P0-4)
- Actor Implementation: `test-probe-core/src/main/scala/io/distia/probe/core/actors/KafkaConsumerStreamingActor.scala`
- Pekko Streams Lifecycle: https://pekko.apache.org/docs/pekko/current/stream/stream-quickstart.html

---

## Review and Revision

**Next Review**: If teams report duplicate event processing issues

**Trigger for Revision**:
- Test re-runs frequently hit duplicate events
- Offset loss causes test flakiness
- Teams request guaranteed offset commit before shutdown

**Decision Log**:
- **2025-10-16**: Initial decision - control.stop + custom materializer for fast shutdown
