# ADR 001: Finite State Machine Pattern for Test Execution

**Date:** 2025-10-13
**Status:** Accepted
**Deciders:** Engineering Team
**Component:** TestExecutionActor

## Context

The TestExecutionActor needs to manage complex test lifecycle orchestration involving:
- Multiple asynchronous initialization steps (BlockStorage, Vault, Cucumber, Kafka)
- Sequential dependencies (must fetch files before credentials, credentials before test execution)
- Error handling and recovery across different lifecycle stages
- Cancellation support at specific stages
- Timeout-based cleanup to prevent orphaned actors
- Clear reporting of lifecycle state to parent QueueActor

Initial implementation attempts using ad-hoc state management led to:
- Race conditions between initialization steps
- Unclear error handling boundaries
- Difficulty testing state transitions
- Inconsistent behavior under concurrent message arrival
- Hard-to-debug issues with message ordering

We needed a structured approach to model the test lifecycle that would:
1. Make all valid states and transitions explicit
2. Prevent invalid transitions at compile time where possible
3. Provide clear error handling semantics
4. Enable comprehensive testing of all state paths
5. Maintain Akka Typed idioms and best practices

## Decision

We will use the **Akka Typed Finite State Machine (FSM) pattern** to model TestExecutionActor behavior.

**Specific implementation choices:**

1. **7 explicit states:** Setup, Loading, Loaded, Testing, Completed, Exception, ShuttingDown
2. **Behavior-based FSM:** Each state is a separate behavior function
3. **Self-message continuation:** All state transitions triggered by `Trn*` self-messages
4. **Message-driven:** No futures, no blocking, no asks - only tell/ask patterns
5. **Pure transitions:** State transition logic separated from side effects
6. **Supervision-based error handling:** Exceptions caught by supervision, translated to `TrnException` messages

**State modeling:**
```scala
// Each state is a function returning Behavior[TestExecutionCommand]
private def setupBehavior(...): Behavior[TestExecutionCommand]
private def loadingBehavior(...): Behavior[TestExecutionCommand]
private def loadedBehavior(...): Behavior[TestExecutionCommand]
private def testingBehavior(...): Behavior[TestExecutionCommand]
private def completedBehavior(...): Behavior[TestExecutionCommand]
private def exceptionBehavior(...): Behavior[TestExecutionCommand]
private def shuttingDownBehavior(...): Behavior[TestExecutionCommand]
```

**Transition pattern:**
```scala
case InStartTestRequest(testId, bucket, testType, replyTo) =>
  replyTo ! StartTestResponse(testId, accepted = true)
  context.self ! TrnLoading  // Defer to self-message
  loadingBehavior(...)        // Transition to next state

case TrnLoading =>
  // Execute all side effects for Loading state entry
  spawnChildren()
  initializeChildren()
  queueActor ! TestLoading
  Behaviors.same
```

## Consequences

### Positive

1. **Explicit state model:** All 7 states are clearly defined with entry/exit actions
2. **Deterministic transitions:** State transition table documents all valid transitions
3. **Compile-time safety:** Type system prevents many invalid transitions
4. **Testability:** Each state behavior can be tested independently
5. **Message ordering:** Self-message continuation guarantees correct ordering
6. **Error isolation:** Exceptions in one state don't corrupt other states
7. **Documentation:** State diagram provides clear visual model
8. **Maintainability:** Adding new states or transitions is straightforward
9. **Debugging:** Current state always explicit, logged clearly
10. **Akka idiomatic:** Follows Akka Typed best practices and patterns

### Negative

1. **Boilerplate:** Each state requires separate function and message handling
2. **Code volume:** ~688 lines for TestExecutionActor (vs ~200-300 for simpler approaches)
3. **Learning curve:** Developers must understand FSM pattern and self-message continuation
4. **State data threading:** TestData must be passed through all state functions
5. **Timer management:** Each state responsible for canceling/scheduling timers

### Risks

1. **Over-engineering:** FSM may be overkill for simpler actors
   - **Mitigation:** Use FSM only for complex lifecycle actors (TEA, QueueActor)

2. **State explosion:** Adding features could lead to too many states
   - **Mitigation:** Keep states coarse-grained, use TestData for fine-grained state

3. **Message queue buildup:** Self-messages could queue up if processing is slow
   - **Mitigation:** Keep state transitions lightweight, no blocking operations

## Alternatives Considered

### 1. Akka Classic FSM

**Pros:**
- Mature, well-documented pattern
- Built-in state timeout handling
- `when(State)(handler)` syntax

**Cons:**
- Uses untyped actors (ClassicActorContext)
- Deprecated in favor of Akka Typed
- Less type safety
- Project already committed to Akka Typed

**Verdict:** Rejected due to deprecation and lack of type safety

### 2. Simple Mutable State Variable

```scala
var currentState: String = "Setup"
var testData: TestData = ...

case InStartTestRequest(...) =>
  if (currentState == "Setup") {
    currentState = "Loading"
    // ... side effects
  }
```

**Pros:**
- Simple, minimal code
- No need for separate behavior functions

**Cons:**
- Mutable state in actor (anti-pattern in Akka)
- No compile-time transition checking
- Hard to test state transitions
- Race conditions with concurrent messages
- Unclear state boundaries

**Verdict:** Rejected due to poor testability and lack of safety

### 3. State Pattern (OOP)

```scala
trait State {
  def handle(msg: TestExecutionCommand): State
}

class SetupState extends State { ... }
class LoadingState extends State { ... }
```

**Pros:**
- Explicit state classes
- Polymorphic message handling

**Cons:**
- Not idiomatic in Akka Typed
- Requires mutable state reference
- More boilerplate than behavior-based FSM
- Harder to integrate with Akka lifecycle

**Verdict:** Rejected due to poor fit with Akka Typed

### 4. Akka Typed FSM with `EventSourcedBehavior`

**Pros:**
- Event sourcing provides audit trail
- Can replay state from events
- Persistent state across restarts

**Cons:**
- Adds complexity of event persistence
- Requires event store (Cassandra, PostgreSQL, etc.)
- Overkill for ephemeral test execution actor
- Performance overhead of persistence

**Verdict:** Rejected due to unnecessary complexity for non-persistent actor

## Implementation Notes

**File:** `test-probe-core/src/main/scala/com/company/probe/core/actors/TestExecutionActor.scala`

**Key patterns:**
1. Each state behavior is a pure function of (state, message) → next state
2. Side effects occur in response to `Trn*` messages, not external messages
3. Timer management in each state (cancel on exit, schedule on entry)
4. GetStatus handled in all states (always returns current state)
5. Supervision catches exceptions, translates to `TrnException` self-message

**Testing:**
- BDD scenarios: 471 lines covering all state transitions
- Feature file: `test-probe-core/src/test/resources/features/component/actor-lifecycle/test-execution-actor-fsm.feature`
- Step definitions: `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/TestExecutionActorSteps.scala`

## Related Decisions

- ADR 002: Self-Message Continuation for State Transitions
- ADR 003: Poison Pill Timer Pattern
- ADR 004: Factory Injection for Child Actors
- ADR 005: Error Kernel Pattern for Exception Handling

## References

- Akka Typed FSM: https://doc.akka.io/docs/akka/current/typed/fsm.html
- Blueprint: [05.1-test-execution-actor-fsm.md](../blueprint/05%20State%20Machine/05.1-test-execution-actor-fsm.md)
- Requirements: `working/TestExecutionActorFSMRequirements.md`

---

**Document History:**
- 2025-10-13: Initial ADR created
