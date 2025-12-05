# ADR 004: Factory Injection Pattern for Child Actors

**Date:** 2025-10-13
**Status:** Accepted
**Deciders:** Engineering Team
**Component:** TestExecutionActor

## Context

The TestExecutionActor spawns 5 child actors during the Loading state:
- BlockStorageActor (fetch test files from S3/GCS/Azure)
- VaultActor (fetch credentials)
- CucumberExecutionActor (run tests)
- KafkaProducerActor (publish test events)
- KafkaConsumerActor (consume test messages)

These actors are essential to test execution but create testing challenges:

**Testing Challenges:**

1. **Integration required:** Real child actors require real S3, Vault, Kafka, etc.
2. **Slow tests:** Integration setup adds 5-10 seconds per test
3. **Flaky tests:** External services introduce non-determinism
4. **Limited coverage:** Can't easily test error scenarios (S3 timeout, Vault auth failure)
5. **Expensive:** Integration tests cost more to run (infrastructure, time)

**Goal:** Enable comprehensive unit testing of TestExecutionActor FSM without requiring real child actors.

**Requirements:**

1. **Production simplicity:** Default behavior should spawn real actors
2. **Test flexibility:** Tests should provide mock actors (TestProbes)
3. **Type safety:** Mock actors must have correct message type
4. **No framework dependency:** Avoid heavy DI frameworks (Guice, Spring)
5. **Clear semantics:** Factory usage should be obvious and hard to misuse

**Design Space:**

How do we make child actor creation injectable while maintaining production simplicity and type safety?

## Decision

We will use **constructor-based factory injection** with optional factory parameters:

**Factory Type Definitions:**

```scala
type BlockStorageFactory = ActorContext[TestExecutionCommand] => ActorRef[BlockStorageCommand]
type VaultFactory = ActorContext[TestExecutionCommand] => ActorRef[VaultCommand]
type CucumberFactory = ActorContext[TestExecutionCommand] => ActorRef[CucumberExecutionCommand]
type KafkaProducerFactory = ActorContext[TestExecutionCommand] => ActorRef[KafkaProducerCommand]
type KafkaConsumerFactory = ActorContext[TestExecutionCommand] => ActorRef[KafkaConsumerCommand]
```

**Actor Constructor:**

```scala
def apply(
  testId: UUID,
  queueActor: ActorRef[QueueCommands.QueueCommand],
  serviceConfig: ServiceConfig,
  blockStorageFactory: Option[BlockStorageFactory] = None,
  vaultFactory: Option[VaultFactory] = None,
  cucumberFactory: Option[CucumberFactory] = None,
  producerFactory: Option[KafkaProducerFactory] = None,
  consumerFactory: Option[KafkaConsumerFactory] = None
): Behavior[TestExecutionCommand]
```

**Default Production Factories:**

```scala
private def defaultBlockStorageFactory(testId: UUID): BlockStorageFactory = { ctx =>
  ctx.spawn(
    Behaviors.supervise(BlockStorageActor(testId))
      .onFailure[Exception](SupervisorStrategy.restart),
    s"block-storage-$testId"
  )
}

// Similar for other actors...
```

**Factory Resolution:**

```scala
val resolvedBlockStorageFactory = blockStorageFactory.getOrElse(defaultBlockStorageFactory(testId))
val resolvedVaultFactory = vaultFactory.getOrElse(defaultVaultFactory(testId))
// ... etc
```

**Production Usage (no injection):**

```scala
TestExecutionActor(
  testId = UUID.randomUUID(),
  queueActor = queueActorRef,
  serviceConfig = config
  // All factories default to production implementations
)
```

**Test Usage (with mocks):**

```scala
val blockStorageProbe = testKit.createTestProbe[BlockStorageCommand]()
val vaultProbe = testKit.createTestProbe[VaultCommand]()
// ... create other probes

TestExecutionActor(
  testId = testId,
  queueActor = queueProbe.ref,
  serviceConfig = config,
  blockStorageFactory = Some(ctx => blockStorageProbe.ref),
  vaultFactory = Some(ctx => vaultProbe.ref),
  cucumberFactory = Some(ctx => cucumberProbe.ref),
  producerFactory = Some(ctx => producerProbe.ref),
  consumerFactory = Some(ctx => consumerProbe.ref)
)
```

## Consequences

### Positive

1. **Production simplicity:** Zero-argument factory usage spawns real actors
2. **Test flexibility:** Can inject TestProbes for unit testing
3. **Type safety:** Factory signatures enforce correct ActorRef types
4. **No framework dependency:** Pure Scala, no DI framework required
5. **Compile-time checking:** Wrong ActorRef type won't compile
6. **Clear intent:** `Option[Factory]` makes injection explicit
7. **Incremental testing:** Can mock some actors and use real others
8. **Standard pattern:** Follows Scala idioms (function types, Option, default params)

### Negative

1. **Constructor bloat:** 8 parameters (testId, queueActor, config, 5 factories)
   - **Mitigation:** Named parameters keep it readable
2. **Boilerplate:** Default factories are repetitive
   - **Mitigation:** Pattern is copy-paste, can be extracted to trait if needed
3. **Factory complexity:** Tests must understand factory pattern
   - **Mitigation:** Test fixtures provide helper methods

### Risks

1. **Forgotten factory:** Developer adds new child actor but forgets to add factory parameter
   - **Mitigation:** Code review checklist includes factory injection
   - **Mitigation:** BDD tests won't work without factory

2. **Production uses test factory:** Developer accidentally passes test factory to production
   - **Mitigation:** Production code never imports test classes (compile error)
   - **Mitigation:** `Option[Factory] = None` default ensures production factory used

3. **Factory leaks context:** Factory uses ActorContext outside of spawn operation
   - **Mitigation:** Type signature prevents this (factory is just context => ActorRef)
   - **Mitigation:** Code review checks factory usage

## Alternatives Considered

### 1. Cake Pattern Dependency Injection

```scala
trait BlockStorageComponent {
  def blockStorageFactory: BlockStorageFactory
}

trait ProductionBlockStorageComponent extends BlockStorageComponent {
  def blockStorageFactory = defaultBlockStorageFactory
}

trait TestBlockStorageComponent extends BlockStorageComponent {
  def blockStorageFactory = mockBlockStorageFactory
}
```

**Pros:**
- Idiomatic Scala DI
- No constructor parameters

**Cons:**
- Complex trait composition
- Harder to understand for new developers
- Overkill for single-purpose actors
- Makes testing more complex, not simpler

**Verdict:** Rejected due to excessive complexity

### 2. Guice/Spring Framework

```scala
class TestExecutionActor @Inject()(
  @Named("blockStorage") blockStorageFactory: BlockStorageFactory,
  @Named("vault") vaultFactory: VaultFactory,
  // ...
)
```

**Pros:**
- Industry-standard DI
- Centralized configuration

**Cons:**
- Heavy framework dependency
- Runtime configuration errors
- Slower compilation
- Harder to reason about
- Overkill for actor testing

**Verdict:** Rejected due to framework overhead

### 3. Companion Object Var (Test-Only)

```scala
object TestExecutionActor {
  var blockStorageFactory: Option[BlockStorageFactory] = None
  var vaultFactory: Option[VaultFactory] = None
  // ...
}
```

**Pros:**
- No constructor parameters
- Simple test setup

**Cons:**
- Mutable global state (bad practice)
- Thread safety issues
- Test isolation problems (one test affects another)
- Impossible to run tests in parallel
- Not obvious that it's for testing only

**Verdict:** Rejected due to global mutable state

### 4. Implicit Context Passing

```scala
case class TestContext(
  blockStorageFactory: BlockStorageFactory,
  vaultFactory: VaultFactory,
  // ...
)

def apply(testId: UUID, queueActor: ActorRef, config: ServiceConfig)
         (implicit ctx: TestContext): Behavior[TestExecutionCommand]
```

**Pros:**
- Separate test concerns from production parameters

**Cons:**
- Implicit parameters reduce clarity
- Production code must provide empty implicit
- Harder to understand what's being injected
- Scala 3 deprecates implicit parameters

**Verdict:** Rejected due to implicit complexity

### 5. Actor Props Builder Pattern

```scala
case class TestExecutionActorProps(
  testId: UUID,
  queueActor: ActorRef,
  config: ServiceConfig
) {
  def withBlockStorageFactory(f: BlockStorageFactory): TestExecutionActorProps = ???
  def withVaultFactory(f: VaultFactory): TestExecutionActorProps = ???
  def build(): Behavior[TestExecutionCommand] = ???
}
```

**Pros:**
- Fluent builder API
- Explicit construction

**Cons:**
- Additional class to maintain
- More boilerplate than needed
- Builder pattern overkill for constructor injection

**Verdict:** Rejected as over-engineering

## Implementation Notes

**Helper Function in ActorWorld (Test Fixture):**

```scala
def spawnTestExecutionActorWithMocks(testId: UUID, config: ServiceConfig): Unit = {
  // Create TestProbes
  blockStorageProbe = Some(testKit.createTestProbe[BlockStorageCommand]())
  vaultProbe = Some(testKit.createTestProbe[VaultCommand]())
  cucumberProbe = Some(testKit.createTestProbe[CucumberExecutionCommand]())
  producerProbe = Some(testKit.createTestProbe[KafkaProducerCommand]())
  consumerProbe = Some(testKit.createTestProbe[KafkaConsumerCommand]())

  // Spawn actor with factory injection
  testExecutionActor = Some(testKit.spawn(TestExecutionActor(
    testId,
    queueProbe.ref,
    config,
    blockStorageFactory = Some(ctx => blockStorageProbe.get.ref),
    vaultFactory = Some(ctx => vaultProbe.get.ref),
    cucumberFactory = Some(ctx => cucumberProbe.get.ref),
    producerFactory = Some(ctx => producerProbe.get.ref),
    consumerFactory = Some(ctx => consumerProbe.get.ref)
  )))
}
```

**BDD Step Definition:**

```scala
When("""a service sends {string} with testId {word} to TestExecutionActor with mocks""") {
  (messageType: String, testIdStr: String) =>
    val testId = UUID.fromString(testIdStr)
    world.testId = testId

    if (world.testExecutionActor.isEmpty) {
      val serviceConfig = TestExecutionActorFixtures.defaultServiceConfig
      world.spawnTestExecutionActorWithMocks(testId, serviceConfig)
    }

    messageType match {
      case "InInitializeTestRequest" =>
        world.sendMessage(InInitializeTestRequest(testId, world.serviceProbe.ref))
    }
}
```

**Supervision in Default Factories:**

```scala
private def defaultBlockStorageFactory(testId: UUID): BlockStorageFactory = { ctx =>
  ctx.spawn(
    Behaviors.supervise(BlockStorageActor(testId))
      .onFailure[Exception](SupervisorStrategy.restart),
    s"block-storage-$testId"
  )
}
```

Note: Supervision is part of the default factory, not the TestExecutionActor. This allows tests to use mock actors without supervision if desired.

## Related Decisions

- ADR 001: FSM Pattern for Test Execution (parent decision)
- ADR 002: Self-Message Continuation (factories used during TrnLoading)

## References

- Akka Testing: https://doc.akka.io/docs/akka/current/typed/testing.html
- Dependency Injection in Scala: https://di-in-scala.github.io/
- Blueprint: [05.1-test-execution-actor-fsm.md](../blueprint/05%20State%20Machine/05.1-test-execution-actor-fsm.md) (Section: Factory Injection Pattern)
- Implementation: `test-probe-core/src/main/scala/io/distia/probe/core/actors/TestExecutionActor.scala:38-129`
- Test Usage: `test-probe-core/src/test/scala/io/distia/probe/core/glue/world/ActorWorld.scala`

---

**Document History:**
- 2025-10-13: Initial ADR created
