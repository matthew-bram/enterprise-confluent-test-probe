# ADR-STORAGE-004: Option Resolution Strategy

**Status**: Accepted
**Date**: 2025-10-20
**Component**: BlockStorageActor Option Handling
**Related Documents**:
- [05.3 Child Actors Integration](../blueprint/05%20State%20Machine/05.3-child-actors-integration.md)
- [ADR-STORAGE-001: Block Storage Abstraction](./ADR-STORAGE-001-block-storage-abstraction.md)

---

## Context

Bucket parameter is `String` in REST API but `Option[String]` in TestExecutionActor FSM state. The bucket flows through multiple layers:
1. REST API: `StartTestRequest(bucket: String)` - user provides bucket name
2. FSM State: `SetupData(bucket: None)` → `LoadingData(bucket: Some("prod-tests"))`
3. BlockStorageActor: `Initialize(bucket: Option[String])`
4. Service Layer: `fetchFromBlockStorage(testId: UUID, bucket: String)`

The question is: where should `Option[String]` be resolved to `String`?

## Decision

**Resolve Option types at actor boundary before calling services**:

```scala
// Actor receives Option[String] from FSM state
case Initialize(bucket: Option[String]) =>
  handleInitialize(testId, bucket, ...)

// Handler resolves to concrete type before calling service
def handleInitialize(..., bucket: Option[String], ...): Behavior = {
  val resolvedBucket: String = bucket.getOrElse("default-bucket")
  val future = fetchFromBlockStorage(testId, resolvedBucket)  // Service takes String
  // ...
}

// Service function signature uses concrete type
trait ProbeStorageService {
  def fetchFromBlockStorage(testId: UUID, bucket: String): Future[BlockStorageDirective]
}
```

**Resolution Pattern**:
- **Actor Layer**: Accepts `Option[String]` (matches FSM state)
- **Actor Boundary**: Resolves `Option[String]` → `String` with sensible default
- **Service Layer**: Uses concrete types only (`String`, not `Option[String]`)

## Consequences

### Positive

✅ **Service layer deals with concrete types only**
- Service implementations don't handle Option semantics
- Simpler service code (no None checks, no default handling)
- Service functions are pure business logic

✅ **Option handling is an actor/FSM concern**
- FSM uses Option to represent state progression (None in Setup, Some after StartTest)
- Actor layer understands FSM semantics
- Service layer doesn't need to know about FSM lifecycle

✅ **Clear separation of concerns**
- FSM: State lifecycle management (Setup → Loading → Loaded)
- Actor: FSM orchestration and Option resolution
- Service: Pure business logic with concrete inputs

✅ **Type-safe defaults**
- `bucket.getOrElse("default-bucket")` provides sensible default
- Default is documented in code (explicit, not hidden in config)
- Test failures clearly show which bucket was used

### Negative

❌ **Requires resolution logic in actor**
- Actor must handle Option unwrapping
- More code in actor (though simple: `getOrElse`)

❌ **Need sensible defaults for None cases**
- Must document what happens when bucket is None
- Default must be reasonable for all environments (dev, staging, prod)

### Mitigation

- Default bucket name documented in code comments
- Future enhancement: Configuration-driven defaults (`probe.storage.default-bucket`)
- Clear error messages if default bucket doesn't exist

## Alternatives Considered

### Alternative 1: Service Layer Handles Option

```scala
// REJECTED
trait ProbeStorageService {
  def fetchFromBlockStorage(testId: UUID, bucket: Option[String]): Future[BlockStorageDirective]
}

class LocalBlockStorageService extends ProbeStorageService {
  def fetchFromBlockStorage(testId: UUID, bucket: Option[String]): Future[BlockStorageDirective] = {
    val resolvedBucket = bucket.getOrElse("default-bucket")  // Resolution in service
    // ...
  }
}
```

**Problems**:
- Service layer knows about FSM lifecycle (Option = state progression artifact)
- Every service implementation must handle None case
- Duplicated resolution logic across 4 service implementations
- Service layer polluted with FSM concerns

**Why Rejected**: Violates separation of concerns, logic duplication

### Alternative 2: FSM Resolves Before Sending to Actor

```scala
// REJECTED - FSM resolves Option before sending Initialize
case Event(StartTest(bucket, ...), data: SetupData) =>
  val resolvedBucket = Some(bucket.getOrElse("default-bucket"))
  blockStorageActor ! Initialize(resolvedBucket)  // Always Some
  // ...
```

**Problems**:
- FSM knows about default bucket logic (FSM shouldn't know service defaults)
- Default handling is FSM concern (should be actor/service concern)
- FSM state still uses Option (LoadingData.bucket: Option[String]) but always Some

**Why Rejected**: FSM shouldn't know about service defaults, inconsistent state

### Alternative 3: Configuration-Driven Default

```scala
// REJECTED (for initial implementation, but good future enhancement)
def handleInitialize(..., bucket: Option[String], ...): Behavior = {
  val resolvedBucket = bucket.getOrElse(config.getString("probe.storage.default-bucket"))
  // ...
}
```

**Problems**:
- Adds configuration dependency to actor
- Actor must have access to config (requires passing config through constructor)
- More complex for initial implementation

**Why Rejected for Now**: Over-engineered for initial implementation
**Future Enhancement**: Add configuration support after initial spike validation

## Implementation Pattern

**Pattern: Option Resolution at Actor Boundary**

**Step 1: FSM State (Option[String])**
```scala
case class SetupData(
  testId: UUID,
  bucket: None.type  // No bucket yet
)

case class LoadingData(
  testId: UUID,
  bucket: Option[String]  // Some("prod-tests") after StartTest
)
```

**Step 2: Actor Command (Option[String])**
```scala
case class Initialize(bucket: Option[String]) extends BlockStorageCommand
```

**Step 3: Actor Handler (Resolve to String)**
```scala
def handleInitialize(
  testId: UUID,
  bucket: Option[String],  // Option from FSM state
  parentTea: ActorRef[TestExecutionCommand],
  fetchFromBlockStorage: (UUID, String) => Future[BlockStorageDirective],  // Service takes String
  loadToBlockStorage: (String, TestExecutionResult) => Future[Unit],
  context: ActorContext[BlockStorageCommand]
): Behavior[BlockStorageCommand] = {
  // RESOLVE: Unwrap Option[String] to String (service layer uses concrete types)
  val resolvedBucket: String = bucket.getOrElse("default-bucket")

  context.log.info(s"Initializing BlockStorageActor for test $testId with bucket $resolvedBucket")

  import context.executionContext
  val fetchFuture: Future[BlockStorageDirective] = fetchFromBlockStorage(testId, resolvedBucket)

  // ...
}
```

**Step 4: Service Function (Concrete String)**
```scala
trait ProbeStorageService {
  def fetchFromBlockStorage(testId: UUID, bucket: String): Future[BlockStorageDirective]
  //                                            ^^^^^^ Concrete type, not Option
}
```

## Why Option[String] in FSM State?

**Option[String] represents state progression**:
- **Setup State**: `bucket = None` (test not started yet, no bucket provided)
- **Loading State**: `bucket = Some("prod-tests")` (StartTest received with bucket)

**FSM State Transition**:
```
Setup(bucket=None) --[StartTest(bucket="prod-tests")]--> Loading(bucket=Some("prod-tests"))
```

**Option is an FSM Lifecycle Artifact**:
- Bucket is None until StartTest command received
- Bucket is Some after StartTest command provides value
- This is FSM state evolution, not a service layer concern

## Sensible Defaults

**Default Bucket**: `"default-bucket"`

**Rationale**:
- Generic name works across all environments
- Clear indication that default was used (appears in logs)
- Can be overridden via StartTest command

**Future Enhancement**:
```hocon
probe.storage {
  default-bucket = "dev-test-bucket"       # Development
  default-bucket = "staging-test-bucket"   # Staging
  default-bucket = "prod-test-bucket"      # Production
}
```

## References

- Scala Option: https://www.scala-lang.org/api/current/scala/Option.html
- Akka Typed State Management: https://doc.akka.io/docs/akka/current/typed/fsm.html

---

## Document History

- 2025-10-20: Initial ADR created documenting Option resolution strategy decision
