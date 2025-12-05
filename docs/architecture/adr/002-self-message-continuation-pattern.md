# ADR 002: Self-Message Continuation for State Transitions

**Date:** 2025-10-13
**Status:** Accepted
**Deciders:** Engineering Team
**Component:** TestExecutionActor, QueueActor

## Context

In Akka FSM implementations, state transitions must handle two concerns:
1. **Pre-transition logic:** Acknowledging the triggering message, updating state
2. **Post-transition logic:** Side effects that should occur after entering new state

Mixing these concerns in a single message handler creates several problems:

**Problem 1: Message Ordering Ambiguity**
```scala
// BAD: Side effects mixed with transition
case InStartTestRequest(testId, bucket, testType, replyTo) =>
  replyTo ! StartTestResponse(testId, accepted = true)
  spawnChildren()  // When does this execute relative to next message?
  initializeChildren()  // What if another message arrives during init?
  queueActor ! TestLoading
  loadingBehavior(...)
```

If another message arrives while `spawnChildren()` is executing, should it be handled by Setup behavior or Loading behavior? The answer is unclear.

**Problem 2: Non-Deterministic Testing**
```scala
// Test sends message
testActor ! InStartTestRequest(...)
// When can we assert children are spawned?
// Immediately? After 100ms? After receiving LoadingResponse?
```

Without explicit sequencing, tests become flaky and timing-dependent.

**Problem 3: State Contamination**
```scala
case InStartTestRequest(...) =>
  val children = spawnChildren()  // Exception here!
  loadingBehavior(...)  // State already transitioned, but children not spawned
```

If side effects fail mid-execution, the actor may be in an inconsistent state.

## Decision

We will use the **Self-Message Continuation Pattern** for all state transitions:

1. **External message handler:** Only acknowledges the message and sends a transition self-message
2. **Transition message handler:** Executes all side effects for state entry
3. **Naming convention:** All transition messages prefixed with `Trn*` (e.g., `TrnLoading`, `TrnTesting`)

**Pattern Template:**
```scala
// Step 1: External message triggers transition
case ExternalMessage(...) =>
  // Acknowledge immediately (if needed)
  replyTo ! AckResponse(...)

  // Defer all side effects to self-message
  context.self ! TrnNextState

  // Transition to next behavior
  nextStateBehavior(...)

// Step 2: Self-message executes side effects
case TrnNextState =>
  // All side effects occur here
  spawnChildren()
  initializeChildren()
  notifyParent()

  // Stay in current behavior (already transitioned)
  Behaviors.same
```

**Concrete Example:**
```scala
// Setup state: Handle start request
case InStartTestRequest(testId, bucket, testType, replyTo) =>
  context.log.info(s"Handling InStartTestRequest for test $testId")
  val updatedData = data.copy(bucket = Some(bucket), testType = testType)
  context.self ! TrnLoading  // Defer to self-message
  loadingBehavior(testId, queueActor, timers, updatedData, ...)

// Loading state: Execute loading logic
case TrnLoading =>
  context.log.info(s"Processing TrnLoading for test $testId")
  timers.cancel("poison-pill")
  timers.startSingleTimer("poison-pill", TrnPoisonPill, timeout)

  val (blockStorage, vault, cucumber, producer, consumer) = spawnChildren(...)
  blockStorage ! Initialize(data.bucket)

  data.replyTo ! StartTestResponse(testId, accepted = true)
  queueActor ! QueueCommands.TestLoading(testId)

  Behaviors.same
```

## Consequences

### Positive

1. **Message ordering guaranteed:** Side effects always execute after state transition
2. **Deterministic testing:** Tests can reliably observe state before and after transition
3. **Failure isolation:** If side effects fail, supervision can retry without repeating transition
4. **Clear separation:** Transition logic cleanly separated from entry logic
5. **Explicit sequencing:** Self-message appears in mailbox, making order visible
6. **Actor single-threading:** Akka guarantees self-message processed after current message
7. **Debuggability:** Transition messages appear in logs, making execution flow clear
8. **Testability:** Can inject `Trn*` messages to test specific state entry scenarios

### Negative

1. **Extra messages:** Each state transition requires two messages (external + `Trn*`)
2. **Code verbosity:** Pattern adds ~5-10 lines per transition
3. **Learning curve:** Developers must understand why pattern is necessary
4. **Indirection:** External message handler doesn't show what side effects occur

### Risks

1. **Forgotten self-message:** Developer forgets to send `Trn*` message
   - **Mitigation:** BDD tests verify all transitions complete
   - **Mitigation:** Code review checklist includes pattern verification

2. **Wrong behavior transition:** State transitions before `Trn*` message sent
   - **Mitigation:** State transition always happens before `context.self ! Trn*`
   - **Mitigation:** Pattern is consistent across all states

3. **Self-message queue buildup:** Too many deferred messages could fill mailbox
   - **Mitigation:** Each transition only sends one self-message
   - **Mitigation:** No recursive self-message patterns

## Alternatives Considered

### 1. Immediate Side Effects

```scala
case InStartTestRequest(...) =>
  // Execute side effects immediately
  spawnChildren()
  initializeChildren()
  queueActor ! TestLoading
  loadingBehavior(...)
```

**Pros:**
- Simple, no extra messages
- Immediate execution

**Cons:**
- Unclear message ordering
- Non-deterministic testing
- State contamination on failure

**Verdict:** Rejected due to testing and ordering issues

### 2. Become-Then-Send Pattern

```scala
case InStartTestRequest(...) =>
  loadingBehavior(...)  // Transition first

case Other =>
  // New state behavior
  spawnChildren()  // Execute on any message
  ...
```

**Pros:**
- No extra messages

**Cons:**
- Side effects trigger on wrong message
- Cannot control when side effects occur
- Difficult to distinguish entry logic from message handling

**Verdict:** Rejected due to unclear semantics

### 3. Future-Based Continuation

```scala
case InStartTestRequest(...) =>
  Future {
    spawnChildren()
    initializeChildren()
  }.map { _ =>
    context.self ! TrnLoaded
  }
  loadingBehavior(...)
```

**Pros:**
- Asynchronous side effects

**Cons:**
- Breaks actor single-threading guarantee
- Future context is outside actor context
- Cannot use `context.spawn` from Future
- Harder to test and reason about

**Verdict:** Rejected due to breaking actor model

### 4. Stash-Based Deferral

```scala
// Stash all messages until side effects complete
case InStartTestRequest(...) =>
  stash.stash(message)
  executeSideEffects()
  stash.unstashAll()
  loadingBehavior(...)
```

**Pros:**
- Can process side effects before other messages

**Cons:**
- Requires StashBuffer (complexity)
- Unclear when to unstash
- Can hide bugs (messages processed out of order)
- Project explicitly avoids stashing (queue enforces order)

**Verdict:** Rejected due to project decision against stashing

## Implementation Notes

**Applies to:**
- TestExecutionActor (7 states with transitions)
- QueueActor (planned, similar lifecycle)
- Any actor with complex FSM behavior

**Transition Messages:**
```scala
sealed trait TestExecutionCommand
case object TrnSetup extends TestExecutionCommand
case object TrnLoading extends TestExecutionCommand
case object TrnLoaded extends TestExecutionCommand
case object TrnTesting extends TestExecutionCommand
case object TrnComplete extends TestExecutionCommand
case class TrnException(exception: ProbeExceptions) extends TestExecutionCommand
case object TrnPoisonPill extends TestExecutionCommand
case object TrnShutdown extends TestExecutionCommand
```

**Naming Convention:**
- External messages: Business domain terms (`InStartTestRequest`, `StartTesting`)
- Internal messages: `Trn*` prefix for transitions (`TrnLoading`, `TrnComplete`)

**Testing Pattern:**
```scala
When("a service sends InStartTestRequest") {
  world.sendMessage(InStartTestRequest(...))
}

And("the TestExecutionActor processes TrnLoading") {
  Thread.sleep(100)  // Allow self-message to process
}

Then("children should be spawned") {
  world.expectChildMessage(...)
}
```

## Related Decisions

- ADR 001: FSM Pattern for Test Execution (parent decision)
- ADR 005: Error Kernel Pattern for Exception Handling (uses `TrnException`)

## References

- Akka Typed FSM: https://doc.akka.io/docs/akka/current/typed/fsm.html
- Actor Model: https://en.wikipedia.org/wiki/Actor_model
- Blueprint: [05.1-test-execution-actor-fsm.md](../blueprint/05%20State%20Machine/05.1-test-execution-actor-fsm.md) (Section: Self-Message Continuation)
- Requirements: `working/TestExecutionActorFSMRequirements.md` (Section 10: Implementation Notes)

---

**Document History:**
- 2025-10-13: Initial ADR created
