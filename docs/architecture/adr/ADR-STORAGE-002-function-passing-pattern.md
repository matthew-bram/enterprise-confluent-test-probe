# ADR-STORAGE-002: Function Passing vs Service Objects

**Status**: Accepted
**Date**: 2025-10-20
**Component**: Service Layer Integration Pattern
**Related Documents**:
- [04.1 Service Layer Architecture](../blueprint/04%20Adapters/04.1-service-layer-architecture.md)
- [ADR-STORAGE-001: Block Storage Abstraction](./ADR-STORAGE-001-block-storage-abstraction.md)

---

## Context

Actors need to call storage service methods. We must decide how to pass service layer functionality to the actor layer. Two primary options:
1. Pass service object instances to actors
2. Pass curried functions through ServiceFunctionsContext

Key concerns:
- **Signature Churn**: Adding new service methods shouldn't require changing all actor constructors
- **Testability**: Actors should be easy to test with stubbed service calls
- **Coupling**: Actors shouldn't depend on service module lifecycle
- **Type Safety**: Compiler should enforce correct usage

## Decision

**Pass curried functions through ServiceFunctionsContext**:

```scala
// Function Bundle (No Signature Churn)
case class StorageServiceFunctions(
  fetchFromBlockStorage: (UUID, String) => Future[BlockStorageDirective],
  loadToBlockStorage: (String, TestExecutionResult) => Future[Unit]
)

object StorageServiceFunctions {
  def fromService(service: ProbeStorageService): StorageServiceFunctions =
    StorageServiceFunctions(
      fetchFromBlockStorage = service.fetchFromBlockStorage,
      loadToBlockStorage = service.loadToBlockStorage
    )
}

// Context Bundling (All Services Together)
case class ServiceFunctionsContext(
  vault: VaultServiceFunctions,
  storage: StorageServiceFunctions
)

// Actor Consumption
def apply(
  testId: UUID,
  parentTea: ActorRef[TestExecutionCommand],
  fetchFromBlockStorage: (UUID, String) => Future[BlockStorageDirective],  // Curried function
  loadToBlockStorage: (String, TestExecutionResult) => Future[Unit]  // Curried function
): Behavior[BlockStorageCommand]
```

**Extraction Flow**:
1. DefaultActorSystem.initialize() extracts functions from service modules
2. Functions bundled into ServiceFunctionsContext
3. Context passed through actor hierarchy: GuardianActor → QueueActor → TestExecutionActor
4. TestExecutionActor extracts specific functions and passes to child actor factories
5. BlockStorageActor receives curried functions (no service object dependency)

## Consequences

### Positive

✅ **Actors don't depend on service implementations**
- Actors receive function references, not service objects
- Service module can be swapped without touching actor code
- Clear interface contract (function signature)

✅ **Adding new service methods doesn't change actor signatures**
- New methods added to service trait
- New functions added to function bundle (StorageServiceFunctions)
- ServiceFunctionsContext unchanged (existing field updated)
- Actor constructors unchanged

✅ **Functions are testable in isolation**
- Service functions can be tested independently
- Actors can be tested with stubbed functions: `(_, _) => Future.successful(...)`
- No need to mock entire service objects

✅ **Follows functional programming principles**
- Functions as first-class values
- Composition over inheritance
- Immutable data flow
- Clear separation of concerns

✅ **Type Safety**
- Compiler enforces correct function signatures
- No runtime type errors from service misuse
- Phantom type builder ensures all services present

### Negative

❌ **Slight learning curve for developers unfamiliar with currying**
- Requires understanding functional programming concepts
- More indirection than direct object method calls

❌ **More indirection (service → functions → context → actors)**
- Extra layer of abstraction
- More files to navigate during debugging

### Mitigation

- Comprehensive documentation in `04.1-service-layer-architecture.md`
- ServiceFunctionsTestHelper provides reusable stubs
- Currying flow diagram clarifies extraction process

## Alternatives Considered

### Alternative 1: Pass Service Object Instances

```scala
// REJECTED
def apply(
  testId: UUID,
  parentTea: ActorRef[TestExecutionCommand],
  storageService: ProbeStorageService  // Direct dependency
): Behavior[BlockStorageCommand]
```

**Problems**:
- Actor depends on service module lifecycle
- Adding new service methods changes actor signature (new parameter)
- Harder to test (need to mock entire service object)
- Couples actor layer to service layer implementation
- Service object may have stateful dependencies (ExecutionContext, FileSystem, etc.)

**Why Rejected**: Tight coupling, signature churn, reduced testability

### Alternative 2: Dependency Injection Framework (Guice, Spring)

```scala
// REJECTED
class BlockStorageActor @Inject()(storageService: ProbeStorageService, ...)
```

**Problems**:
- Adds framework dependency (Guice ~1MB, Spring ~5MB)
- Runtime dependency resolution (no compile-time guarantees)
- Incompatible with Akka Typed spawn semantics (actors created via Behaviors.setup, not DI container)
- Over-engineered for our needs

**Why Rejected**: Framework overhead, runtime resolution, incompatible with Akka Typed

### Alternative 3: Actor Context-Based Lookup (Service Registry)

```scala
// REJECTED
trait ServiceRegistry {
  def getStorageService: ProbeStorageService
}

// In actor
val storageService = context.system.extension[ServiceRegistry].getStorageService
```

**Problems**:
- Runtime lookup (no compile-time guarantees)
- Hidden dependency (not visible in actor signature)
- Global mutable state (registry)
- Harder to test (need to set up registry with stubs)

**Why Rejected**: Hidden dependencies, runtime lookup, global state

## Implementation Pattern

**Pattern: Service Module → Curried Functions → Actor Consumption**

**Step 1: Service Trait (Contract)**
```scala
trait ProbeStorageService extends Feature with BuilderModule {
  def fetchFromBlockStorage(testId: UUID, bucket: String): Future[BlockStorageDirective]
  def loadToBlockStorage(jimfsLocation: String, testResult: TestExecutionResult): Future[Unit]
}
```

**Step 2: Service Implementation**
```scala
class LocalBlockStorageService extends ProbeStorageService {
  override def fetchFromBlockStorage(testId: UUID, bucket: String): Future[BlockStorageDirective] = {...}
  override def loadToBlockStorage(jimfsLocation: String, testResult: TestExecutionResult): Future[Unit] = {...}
}
```

**Step 3: Function Bundle**
```scala
case class StorageServiceFunctions(
  fetchFromBlockStorage: (UUID, String) => Future[BlockStorageDirective],
  loadToBlockStorage: (String, TestExecutionResult) => Future[Unit]
)

object StorageServiceFunctions {
  def fromService(service: ProbeStorageService): StorageServiceFunctions =
    StorageServiceFunctions(
      fetchFromBlockStorage = service.fetchFromBlockStorage,
      loadToBlockStorage = service.loadToBlockStorage
    )
}
```

**Step 4: Context Bundling**
```scala
case class ServiceFunctionsContext(
  vault: VaultServiceFunctions,
  storage: StorageServiceFunctions
)
```

**Step 5: Extraction and Propagation**
```scala
// In DefaultActorSystem.initialize()
val storageServiceFunctions = StorageServiceFunctions.fromService(storageModule)
val serviceFunctionsContext = ServiceFunctionsContext(vaultFunctions, storageServiceFunctions)

// Pass through actor hierarchy
GuardianActor(serviceFunctionsContext) → QueueActor → TestExecutionActor → BlockStorageActor
```

**Step 6: Actor Consumption**
```scala
def apply(
  testId: UUID,
  parentTea: ActorRef[TestExecutionCommand],
  fetchFromBlockStorage: (UUID, String) => Future[BlockStorageDirective],
  loadToBlockStorage: (String, TestExecutionResult) => Future[Unit]
): Behavior[BlockStorageCommand] = {
  Behaviors.setup { context =>
    import context.executionContext
    val fetchFuture = fetchFromBlockStorage(testId, "my-bucket")  // Just call the function
    // ...
  }
}
```

## References

- Functional Programming in Scala: https://www.manning.com/books/functional-programming-in-scala
- Akka Typed Best Practices: https://doc.akka.io/docs/akka/current/typed/style-guide.html
- Dependency Injection vs Functional Composition: https://www.infoq.com/articles/dependency-injection-functional-composition/

---

## Document History

- 2025-10-20: Initial ADR created documenting function passing pattern decision
