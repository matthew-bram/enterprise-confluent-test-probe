# ADR 003: Poison Pill Timer Pattern for Actor Cleanup

**Date:** 2025-10-13
**Status:** Accepted
**Deciders:** Engineering Team
**Component:** TestExecutionActor

## Context

The TestExecutionActor manages test lifecycle for a single test execution. Each actor is ephemeral and should terminate after completing its work. However, several scenarios can leave actors orphaned:

**Orphan Scenarios:**

1. **Stuck in Setup:** Test initialized but never started (QueueActor crashed, client disconnected)
2. **Stuck in Loading:** Children never respond with `ChildGoodToGo` (network partition, child crash)
3. **Stuck in Completed:** Test finished but cleanup never triggered (QueueActor busy, system overload)
4. **Stuck in Exception:** Error occurred but cleanup never triggered (similar to Completed)

Without cleanup, orphaned actors accumulate and consume memory indefinitely. In production, this leads to:
- Memory leaks (actor state retained in heap)
- Actor system bloat (thousands of orphaned actors)
- Degraded performance (mailbox processing overhead)
- Eventually: OutOfMemoryError and system crash

**Design Constraints:**

1. **No external cleanup:** Parent QueueActor may be unavailable or crashed
2. **No global timeout:** Different states have different valid durations
3. **No mandatory user action:** Cleanup must be automatic
4. **Preserve test results:** Completed tests should not timeout prematurely

**User Experience Considerations:**

- **Loaded state:** Tests may sit in queue for hours (large queue, low concurrency)
- **Testing state:** Tests may run for minutes to hours (performance tests, integration tests)
- Timing out legitimate work would frustrate users and lose test results

## Decision

We will implement the **Poison Pill Timer Pattern** with state-specific timeouts:

**Implementation:**

1. **Per-state timers:** Each state schedules its own timeout
2. **Named timer:** Single timer named "poison-pill" (canceled/replaced on state change)
3. **Timeout message:** `TrnPoisonPill` sent to self when timer expires
4. **Unconditional shutdown:** `TrnPoisonPill` always transitions to ShuttingDown state
5. **Selective states:** Only states that should timeout get timers

**State Timeout Configuration:**

| State | Timer | Default Timeout | Rationale |
|-------|-------|-----------------|-----------|
| Setup | Yes | 60 seconds | Should start quickly or be canceled |
| Loading | Yes | 60 seconds | Initialization should complete quickly |
| Loaded | **No** | N/A | May wait in queue indefinitely (bad UX) |
| Testing | **No** | N/A | Test duration is user-controlled (bad UX) |
| Completed | Yes | 60 seconds | Cleanup should be fast |
| Exception | Yes | 60 seconds | Cleanup should be fast |
| ShuttingDown | No | N/A | Already terminating |

**Timer Lifecycle:**

```scala
// State entry: Cancel previous timer, start new timer
case TrnSetup =>
  timers.cancel("poison-pill")  // Cancel any previous timer
  timers.startSingleTimer("poison-pill", TrnPoisonPill, setupTimeout)
  // ... state entry logic
  Behaviors.same

// Timer expiry: Transition to shutdown
case TrnPoisonPill =>
  context.log.info(s"Poison pill timer expired in Setup state")
  context.self ! TrnShutdown
  shuttingDownBehavior(...)
```

**Configuration:**

```hocon
service {
  timeouts {
    setupStateTimeout = 60 seconds
    loadingStateTimeout = 60 seconds
    completedStateTimeout = 60 seconds
    exceptionStateTimeout = 60 seconds
  }
}
```

## Consequences

### Positive

1. **Guaranteed cleanup:** All actors eventually terminate (no memory leaks)
2. **State-specific semantics:** Each state has appropriate timeout
3. **User-friendly:** Long-running operations (queue wait, test execution) don't timeout
4. **Configurable:** Production vs test environments can use different timeouts
5. **Simple implementation:** Single named timer per actor (no complex timer management)
6. **Testable:** Timer behavior can be verified with short test timeouts
7. **Fail-safe:** Even if parent crashes, actor cleans itself up
8. **Observable:** Timer expiry logged clearly

### Negative

1. **Timer management overhead:** Each state transition must cancel/reschedule timer
2. **Configuration complexity:** Need to tune timeouts per environment
3. **Potential data loss:** Test results lost if Completed state times out before QueueActor retrieves them
   - **Mitigation:** 60-second timeout should be sufficient for QueueActor to respond
4. **False positives:** Slow systems might timeout legitimate operations
   - **Mitigation:** Timeouts are configurable per environment

### Risks

1. **Loaded/Testing states timeout accidentally:** Developer adds timer where it shouldn't exist
   - **Mitigation:** Code review verifies no timer in Loaded/Testing states
   - **Mitigation:** BDD tests verify long waits don't timeout

2. **Timer not canceled:** State transition forgets to cancel previous timer
   - **Mitigation:** Pattern always cancels before starting new timer
   - **Mitigation:** Using named timer ensures only one active at a time

3. **Timeout too short:** Legitimate operations timeout prematurely
   - **Mitigation:** 60-second default is conservative
   - **Mitigation:** Configurable per environment

4. **Timeout too long:** Orphaned actors live too long
   - **Mitigation:** 60 seconds is reasonable balance
   - **Mitigation:** Can be reduced in testing environments

## Alternatives Considered

### 1. Global Actor Timeout

```scala
// Single timeout for entire actor lifecycle
val globalTimeout = 5.minutes
timers.startSingleTimer("global-timeout", TrnPoisonPill, globalTimeout)
```

**Pros:**
- Simple: One timer for entire actor
- No per-state management

**Cons:**
- Doesn't account for state-specific needs
- May timeout long-running tests
- May not timeout stuck states quickly enough
- Not configurable per state

**Verdict:** Rejected due to lack of state-specific semantics

### 2. External Watchdog Actor

```scala
// Separate actor monitors TestExecutionActor lifecycle
class WatchdogActor extends Actor {
  scheduleOnce(timeout, testExecutionActor, TrnPoisonPill)
}
```

**Pros:**
- Separation of concerns
- Centralized timeout management

**Cons:**
- Additional actor overhead
- Complexity of watchdog lifecycle
- Watchdog itself could fail
- Harder to test
- Doesn't scale (one watchdog per test actor)

**Verdict:** Rejected due to unnecessary complexity

### 3. Parent-Managed Timeout

```scala
// QueueActor sends timeout message to children
queueActor ! ScheduleTimeout(testExecutionActor, 60.seconds)
```

**Pros:**
- Centralized in parent

**Cons:**
- Requires parent to be alive
- Parent may be overloaded or crashed (why orphans exist)
- Parent must track all children timeouts
- Doesn't handle parent failures

**Verdict:** Rejected because it doesn't solve the orphan problem

### 4. No Timeout (Manual Cleanup)

**Pros:**
- No timer overhead
- No false positives

**Cons:**
- Orphaned actors never cleaned up
- Memory leaks
- Eventually system crash
- Requires external monitoring

**Verdict:** Rejected due to unacceptable risk of memory leaks

### 5. Actor System DeathWatch

```scala
// QueueActor watches children, stops them on parent termination
context.watch(testExecutionActor)
```

**Pros:**
- Built-in Akka feature
- Parent termination cleans children

**Cons:**
- Only works if parent terminates
- Doesn't handle orphans (parent alive, child stuck)
- Doesn't handle state-specific timeouts
- Already used in conjunction with poison pill

**Verdict:** Insufficient alone, used as complement

## Implementation Notes

**Timer Management Pattern:**

```scala
// State entry: Cancel and reschedule
timers.cancel("poison-pill")
timers.startSingleTimer("poison-pill", TrnPoisonPill, timeout)

// State without timer: Cancel only
timers.cancel("poison-pill")
// NO new timer scheduled

// Timer expiry: Always shutdown
case TrnPoisonPill =>
  context.log.info(s"Timer expired in $currentState state")
  context.self ! TrnShutdown
  shuttingDownBehavior(...)
```

**Testing Pattern:**

```scala
// Short timer config for tests
val testConfig = ServiceConfig(
  setupStateTimeout = 2.seconds,
  loadingStateTimeout = 2.seconds,
  completedStateTimeout = 2.seconds,
  exceptionStateTimeout = 2.seconds
)

// Wait for timer expiry
Thread.sleep(3000)  // 3s > 2s timer

// Verify shutdown occurred
world.expectQueueMessage[TestStopping]()
world.expectActorTerminated()
```

**BDD Scenarios:**

All timer expiry scenarios test:
1. Actor spawned with short timers (2 seconds)
2. Actor transitions to target state
3. Wait 3 seconds (50% safety margin)
4. Verify `TestStopping` sent to QueueActor
5. Verify actor terminated

**Files:**
- Feature: `test-probe-core/src/test/resources/features/component/actor-lifecycle/test-execution-actor-fsm.feature`
- Tests tagged with `@TimerExpiry`

## Related Decisions

- ADR 001: FSM Pattern for Test Execution (parent decision)
- ADR 002: Self-Message Continuation (TrnPoisonPill is a self-message)

## References

- Akka Timers: https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html#scheduling-messages-to-self
- Poison Pill Pattern: https://www.enterpriseintegrationpatterns.com/patterns/messaging/ControlBus.html
- Blueprint: [05.1-test-execution-actor-fsm.md](../blueprint/05%20State%20Machine/05.1-test-execution-actor-fsm.md) (Section: Poison Pill Timer Pattern)
- Requirements: `working/TestExecutionActorFSMRequirements.md` (Section 9: Timers)

---

**Document History:**
- 2025-10-13: Initial ADR created
