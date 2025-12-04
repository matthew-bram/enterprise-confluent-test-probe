# ADR-STORAGE-003: jimfsLocation State Management

**Status**: Accepted
**Date**: 2025-10-20
**Component**: BlockStorageActor State Management
**Related Documents**:
- [05.3 Child Actors Integration](../blueprint/05%20State%20Machine/05.3-child-actors-integration.md)
- [ADR-STORAGE-001: Block Storage Abstraction](./ADR-STORAGE-001-block-storage-abstraction.md)

---

## Context

Load operation needs `jimfsLocation` from fetch operation. The fetch operation returns `BlockStorageDirective` containing `jimfsLocation` and `topicDirectives`. Load operation must read evidence files from jimfs at this exact location. The question is: where should `BlockStorageDirective` be stored between fetch and load?

Options:
1. Store in external state (Redis, database)
2. Store in BlockStorageActor state
3. Re-derive jimfsLocation from testId in load phase
4. Pass jimfsLocation through FSM state transitions

## Decision

**Store BlockStorageDirective in actor state after fetch**:

```scala
def activeBehavior(
  testId: UUID,
  initialized: Boolean,
  blockStorageDirective: Option[BlockStorageDirective],  // Stored state
  parentTea: ActorRef[TestExecutionCommand],
  fetchFromBlockStorage: (UUID, String) => Future[BlockStorageDirective],
  loadToBlockStorage: (String, TestExecutionResult) => Future[Unit],
  context: ActorContext[BlockStorageCommand]
): Behavior[BlockStorageCommand]

// After fetch completes
activeBehavior(testId, initialized = true, Some(directive), ...)

// During load
val jimfsLocation = blockStorageDirective.getOrElse {
  throw IllegalStateException("fetch must complete before load")
}.jimfsLocation
```

## Consequences

### Positive

✅ **Natural temporal coupling (fetch → load dependency)**
- Actor state naturally represents lifecycle coupling
- Fetch must complete before load (validated by Option)
- Clear state progression: None → Some(directive) → used in load

✅ **No external state management needed**
- No Redis, database, or distributed cache required
- Simpler architecture (fewer moving parts)
- Lower operational complexity

✅ **Validates fetch completed before load**
- `Option[BlockStorageDirective]` type enforces validation
- Throws `IllegalStateException` if directive is None during load
- Fail-fast behavior prevents silent errors

✅ **Actor state is Akka/Pekko's strength**
- Actors are designed to manage state
- State transitions map to behavior changes
- No concurrency issues (actor message loop serializes access)

### Negative

❌ **Actor must maintain state across operations**
- More complex than stateless actor
- State must be passed through all behavior transitions

❌ **State lost on actor restart**
- If actor crashes and restarts, BlockStorageDirective is lost
- Parent must retry fetch operation

### Mitigation

- **Acceptable**: State loss on restart is acceptable
  - Parent (TestExecutionActor) can retry fetch operation
  - Test will be marked as failed and retried
  - Evidence from failed test is not critical (test re-runs)
- **Supervision**: BlockStorageActor has restart supervision strategy
  - Parent receives exception notification
  - Parent can decide to retry or fail test

## Alternatives Considered

### Alternative 1: External State (Redis, Database)

```scala
// REJECTED
def handleLoadToBlockStorage(...): Behavior = {
  val directive = redis.get(s"test-$testId-directive")
  val jimfsLocation = directive.jimfsLocation
  // ...
}
```

**Problems**:
- Adds external dependency (Redis, database)
- Network call overhead (latency)
- Requires serialization/deserialization
- More points of failure (Redis down, network issues)
- Operational complexity (Redis cluster management)

**Why Rejected**: Over-engineered, unnecessary external dependency, operational overhead

### Alternative 2: Re-derive jimfsLocation from testId

```scala
// REJECTED
def handleLoadToBlockStorage(...): Behavior = {
  val jimfsLocation = s"/jimfs/test-$testId"  // Re-derive
  // ...
}
```

**Problems**:
- Assumes jimfsLocation derivation is constant (may not be true in future)
- Duplicates logic (same derivation in fetch and load)
- Loses topicDirectives (only jimfsLocation re-derived, not full directive)
- Fragile to changes in jimfsLocation naming convention

**Why Rejected**: Logic duplication, fragile assumption, loses data

### Alternative 3: Pass through FSM State

```scala
// REJECTED - Pass BlockStorageDirective through FSM state transitions

case class LoadingData(
  testId: UUID,
  bucket: Option[String],
  blockStorageDirective: Option[BlockStorageDirective],  // Add to FSM state
  ...
)
```

**Problems**:
- FSM state already has many fields (testId, bucket, actors, etc.)
- BlockStorageDirective is BlockStorageActor's concern, not FSM's
- Violates separation of concerns (FSM shouldn't know about actor-specific data)
- Increases FSM state complexity

**Why Rejected**: Violates separation of concerns, FSM state bloat

## Implementation Pattern

**Pattern: Actor State Storage with Option Validation**

**Step 1: State Initialization (None)**
```scala
Behaviors.setup { context =>
  activeBehavior(testId, initialized = false, None, parentTea, fetchFunc, loadFunc, context)
}
```

**Step 2: Fetch Completes (Some(directive))**
```scala
def handleFetchResults(...): Behavior = {
  results.results match {
    case Right(directive) =>
      parentTea ! BlockStorageFetched(testId, directive)
      parentTea ! ChildGoodToGo(testId, context.self)

      // CRITICAL: Store directive in state
      activeBehavior(testId, initialized = true, Some(directive), parentTea, fetchFunc, loadFunc, context)

    case Left(ex) =>
      throw BlockStorageException(...)
  }
}
```

**Step 3: Load Uses Stored Directive**
```scala
def handleLoadToBlockStorage(
  testId: UUID,
  testExecutionResult: TestExecutionResult,
  blockStorageDirective: Option[BlockStorageDirective],  // From actor state
  ...
): Behavior = {
  // Extract jimfsLocation with validation
  val jimfsLocation = blockStorageDirective match {
    case Some(directive) => directive.jimfsLocation
    case None =>
      throw new IllegalStateException("BlockStorageDirective not available - fetch must complete before load")
  }

  val loadFuture = loadToBlockStorage(jimfsLocation, testExecutionResult)
  // ...
}
```

**Step 4: State Retained After Load**
```scala
def handleLoadResults(...): Behavior = {
  results.results match {
    case Right(_) =>
      parentTea ! BlockStorageUploadComplete(testId)

      // Retain stored directive (may be needed for retry)
      activeBehavior(testId, initialized = true, blockStorageDirective, parentTea, fetchFunc, loadFunc, context)

    case Left(ex) =>
      throw BlockStorageException(...)
  }
}
```

## State Lifecycle

```
1. Actor Created        → blockStorageDirective = None
2. Initialize Received  → blockStorageDirective = None (fetch in progress)
3. FetchResults Success → blockStorageDirective = Some(directive)
4. LoadToBlockStorage   → blockStorageDirective = Some(directive) (used)
5. LoadResults Success  → blockStorageDirective = Some(directive) (retained)
6. Stop                 → Actor terminated, state discarded
```

## References

- Akka Typed Actor State: https://doc.akka.io/docs/akka/current/typed/from-classic.html#managing-state
- Functional State Management: https://www.scala-lang.org/api/current/scala/Option.html

---

## Document History

- 2025-10-20: Initial ADR created documenting jimfsLocation state management decision
