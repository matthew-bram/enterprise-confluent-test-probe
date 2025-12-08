# Architecture Guide

This guide covers the architectural patterns, component hierarchy, and integration points for the Test-Probe project.

---

## Actor System Hierarchy

Test-Probe uses Akka Typed Actors with a supervised hierarchy:

```
GuardianActor (root supervisor)
├── QueueActor (manages test queue, spawns TestExecutionActors)
    └── TestExecutionActor (FSM, one per test)
        ├── CucumberExecutionActor (runs Cucumber scenarios)
        ├── KafkaProducerActor (produces events to Kafka)
        ├── KafkaConsumerActor (consumes events from Kafka)
        ├── BlockStorageActor (uploads evidence to S3)
        └── VaultActor (retrieves secrets from Vault)
```

---

## Finite State Machine (TestExecutionActor)

The core FSM manages individual test execution lifecycle with 7 states coordinating 5 child actors.

**Implementation Status**: ✅ **COMPLETED** (576 lines, all states fully implemented)

### States

`Setup` → `Loading` → `Loaded` → `Testing` → `Completed` | `Exception` → `ShuttingDown` → `Stopped`

### State Details

1. **Setup**: Test initialization, stores replyTo reference, schedules poison pill timer
2. **Loading**: Spawns 5 child actors, sequential initialization (BlockStorage → Vault → Cucumber/Producer/Consumer)
3. **Loaded**: All children ready (5 ChildGoodToGo received), no timer (waits for queue to assign)
4. **Testing**: Cucumber execution active, no timer (tests may run long)
5. **Completed**: Test finished, evidence uploaded to S3, poison pill timer scheduled
6. **Exception**: Error occurred, schedules cleanup timer
7. **ShuttingDown**: Coordinated shutdown of all 5 children, terminates when all report complete

### State Transitions

- Uses **deferred self-messages** (post-transition hooks) for all transitions
- Example: `context.self ! TrnLoading` before returning `loadingBehavior()`
- Leverages FIFO mailbox ordering for deterministic execution
- **No stash buffer**: Akka's mailbox provides natural message queuing
- **QueueActor**: Passed as explicit parameter (context.parent doesn't exist in Akka Typed)

### Key Implementation Files

- `test-probe-core/src/main/scala/com/company/probe/core/actors/TestExecutionActor.scala` (706 lines)
- `working/TestExecutionActorImplementationPlan.md` (comprehensive implementation spec)
- `working/test-execution-actor-fsm.feature` (20+ BDD test scenarios)

---

## Child Actors

All 5 child actors are fully implemented with message passing, exception handling, and parent reference pattern:

### 1. BlockStorageActor (191 lines, 16 unit tests, 14 BDD scenarios)
- Streams test data from S3 bucket to jimfs local file system (stub)
- Uploads test evidence from jimfs to S3 bucket (stub)
- Messages: Initialize → BlockStorageFetched, ChildGoodToGo | LoadToBlockStorage → BlockStorageUploadComplete

### 2. VaultActor (162 lines, 8 unit tests, 10 BDD scenarios)
- Fetches client ID and client secret from Vault based on BlockStorageDirective (stub)
- Messages: Initialize → SecurityFetched, ChildGoodToGo
- Security: Credentials redacted in all logs

### 3. CucumberExecutionActor (226 lines, 12 unit tests, 12 BDD scenarios)
- Initializes Cucumber test execution environment (stub)
- Executes Cucumber scenarios (stub returns TestExecutionResult)
- Messages: Initialize → ChildGoodToGo | StartTest → TestComplete
- Security: Credentials redacted in all logs

### 4. KafkaProducerActor (189 lines, 12 unit tests, 8 BDD scenarios)
- Configures Kafka producers with credentials (stub)
- StartTest is no-op stub (producers used by Cucumber scenarios)
- Messages: Initialize → ChildGoodToGo
- Security: Credentials redacted in all logs

### 5. KafkaConsumerActor (189 lines, 12 unit tests, 8 BDD scenarios)
- Configures Kafka consumers with credentials (stub)
- StartTest is no-op stub (consumers used by Cucumber scenarios)
- Messages: Initialize → ChildGoodToGo
- Security: Credentials redacted in all logs

### Parent Reference Pattern

All child actors receive TestExecutionActor reference as constructor parameter (Akka Typed pattern):
```scala
def apply(testId: UUID, parentTea: ActorRef[TestExecutionCommand]): Behavior[Command]
```

### Service Integration

All service calls are marked with `// TODO: Service integration - Future phase` and return stub data. This scaffolding phase establishes message passing protocols and exception handling without external dependencies.

---

## Kafka Streaming Layer

**Implementation Status**: ✅ **COMPLETED** (848 lines, fully integrated Kafka + DSL layer)

The Kafka streaming layer provides end-to-end Kafka producer/consumer integration with Schema Registry support and Scala/Java DSL for Cucumber test access.

**Architecture Pattern**: Supervisor-Streaming actor separation with DSL registry pattern

### Component Hierarchy

```
TestExecutionActor (FSM - test lifecycle)
├── KafkaProducerActor (supervisor - manages N streaming actors)
│   └── KafkaProducerStreamingActor (per topic - Akka Streams)
│       └── Akka Streams: Source.single → encode → Producer.plainSink
│
└── KafkaConsumerActor (supervisor - manages N streaming actors)
    └── KafkaConsumerStreamingActor (per topic - Akka Streams)
        └── Akka Streams: committableSource → decode → batch → commit
            └── Registry: mutable.Map[UUID, EventEnvelope]

ProbeScalaDsl (singleton registry)
├── producers: ConcurrentHashMap[(testId, topic) → ActorRef]
├── consumers: ConcurrentHashMap[(testId, topic) → ActorRef]
└── API: produceEvent(), fetchConsumedEvent() (blocking & async)

ProbeJavaDsl (Java wrapper)
└── Wraps ProbeScalaDsl with CompletableFuture API
```

### Key Architectural Patterns

**1. Supervisor-Streaming Separation**:
- **Supervisor actors** (`KafkaProducerActor`, `KafkaConsumerActor`): Lifecycle management, spawn N streaming actors (one per topic)
- **Streaming actors** (`KafkaProducerStreamingActor`, `KafkaConsumerStreamingActor`): Akka Streams materialization, Schema Registry integration
- **Rationale**: Isolation of stream failures, scalability (one streaming actor per topic), fine-grained error handling

**2. DSL Registry Pattern**:
- Thread-safe `ConcurrentHashMap` for actor lookup from Cucumber threads
- Registration: Supervisor spawns streaming actor → registers with DSL
- Access: Cucumber step definitions call DSL blocking API
- See: `test-probe-core/src/main/scala/com/company/probe/core/pubsub/ProbeScalaDsl.scala`

**3. Custom Materializer + control.stop**:
- Per-actor custom materializer for precise lifecycle control
- `control.stop` (not `drainAndShutdown`) for fast shutdown
- Rationale: Test already over when PostStop fires, no need to drain pending messages
- See: [ADR-004: Consumer Stream Lifecycle Management](../../docs/adr/ADR-004-Consumer-Stream-Lifecycle-Management.md)

**4. Blocking Dispatcher for Schema Registry**:
- Dedicated 8-thread pool for Schema Registry HTTP calls
- Prevents blocking default actor dispatcher
- Configuration: `akka.actor.blocking-io-dispatcher`

**5. Per-Message Stream (Producer)**:
- `Source.single()` pattern for each ProduceEvent
- JWT TTL >1 hour + Kafka connection pooling = acceptable performance
- Defer persistent stream optimization until smoke testing
- See: [ADR-002: Producer Stream Performance Optimization](../../docs/adr/ADR-002-Producer-Stream-Performance-Optimization.md)

**6. Event Filtering + Batched Commits (Consumer)**:
- Filter events by testId and eventType/version
- Batch commit every 20 offsets for throughput
- Malformed events skipped with warning, stream continues
- See: [ADR-003: Schema Registry Error Handling](../../docs/adr/ADR-003-Schema-Registry-Error-Handling.md)

**7. Error Handling Strategy**:
- **Producer**: Return `ProducedNack(exception)` immediately, test decides retry vs fail
- **Consumer**: Skip malformed events, log warning, continue stream
- OpenTelemetry metrics planned for observability

### Message Flow

**Producer Flow**:
```
Cucumber Step → ProbeScalaDsl → KafkaProducerStreamingActor → Akka Streams → Kafka
                     ↓                        ↓                      ↓
             ConcurrentHashMap           Source.single         Producer.plainSink
                 lookup                      ↓                      ↓
                     ↓                  Schema Registry        Kafka Broker
             ask(ProduceEvent)              encode                 ↓
                     ↓                       ↓                  Success/Failure
                ProducedAck             Array[Byte]                ↓
                     or                     ↓               ProducedAck/Nack
               ProducedNack            ProducerRecord
```

**Consumer Flow**:
```
Kafka → Akka Streams → KafkaConsumerStreamingActor → Registry → ProbeScalaDsl → Cucumber Step
  ↓          ↓                     ↓                     ↓              ↓
Broker  committableSource   Schema Registry      mutable.Map   ConcurrentHashMap    fetch
  ↓          ↓                   decode               ↓              lookup           ↓
Topic    ConsumerRecord           ↓               InternalAdd        ↓          ConsumedAck
  ↓          ↓              EventEnvelope            ↓          FetchConsumedEvent      or
Events   mapAsync               ↓                registry            ↓           ConsumedNack
  ↓          ↓              shouldInclude            ↓          ask(eventTestId)
Batch    collect                 ↓                  get
  ↓          ↓              batch(20)               ↓
Commit   commitScaladsl          ↓               registry.get
```

### Architecture Decision Records

- [ADR-001: Consumer Registry Memory Management](../../docs/adr/ADR-001-Consumer-Registry-Memory-Management.md) - No cache eviction needed for bounded test scenarios
- [ADR-002: Producer Stream Performance Optimization](../../docs/adr/ADR-002-Producer-Stream-Performance-Optimization.md) - Defer persistent stream until smoke testing
- [ADR-003: Schema Registry Error Handling](../../docs/adr/ADR-003-Schema-Registry-Error-Handling.md) - Producer NACKs immediately, consumer skips malformed events
- [ADR-004: Consumer Stream Lifecycle Management](../../docs/adr/ADR-004-Consumer-Stream-Lifecycle-Management.md) - Custom materializer + control.stop for fast shutdown

### Implementation Files

**Core Actors**:
- `test-probe-core/src/main/scala/com/company/probe/core/actors/KafkaProducerActor.scala` (237 lines)
- `test-probe-core/src/main/scala/com/company/probe/core/actors/KafkaConsumerActor.scala` (237 lines)
- `test-probe-core/src/main/scala/com/company/probe/core/actors/KafkaProducerStreamingActor.scala` (89 lines)
- `test-probe-core/src/main/scala/com/company/probe/core/actors/KafkaConsumerStreamingActor.scala` (168 lines)

**DSL Layer**:
- `test-probe-core/src/main/scala/com/company/probe/core/pubsub/ProbeScalaDsl.scala` (150 lines)
- `test-probe-core/src/main/scala/com/company/probe/core/pubsub/ProbeJavaDsl.scala` (stub)

**Schema Registry** (stubs for future integration):
- `test-probe-core/src/main/scala/com/company/probe/core/pubsub/SchemaRegistryEncoder.scala`
- `test-probe-core/src/main/scala/com/company/probe/core/pubsub/SchemaRegistryDecoder.scala`

**Documentation**:
- Implementation Summary: `working/KafkaStreamingImplementationSummary-2025-10-16.md` (725 lines)
- Architecture docs: `docs/architecture/` (to be updated)

---

## Builder Pattern (Application Bootstrap)

The framework uses a **Phantom Type Builder** for compile-time safe configuration:

```scala
// Four-phase builder lifecycle:
// Phase 1: preFlight - validation
// Phase 2: initialize - decoration with config, actor system, services
// Phase 3: finalCheck - final validation
// Phase 4: DSL Setup - fetch actor refs, create curried functions, setup REST

BuilderContext()
  .withConfig(config, serviceConfig)         // Phase 2
  .withActorSystem(system, guardian)         // Phase 2
  .withServices(storage, vault)              // Phase 2
  .withActorRefs(queue, eventRegistry)       // Phase 4
  .withCurriedFunctions(bundle)              // Phase 4
  .toServiceContext()                        // Convert to ServiceContext
```

**Key Insight**: `BuilderContext` is immutable and threaded through all phases, decorated via `copy()` methods.

---

## Actor System Boot Sequence

1. **Load Configuration**: Typesafe Config from `application.conf` + env vars
2. **Validate Config**: `ServiceConfigValidator` enforces required properties
3. **Create ActorSystem**: Boot `GuardianActor` as root supervisor
4. **Initialize Services**: Storage (S3/local), Vault (if enabled)
5. **Spawn Child Actors**: Queue, EventRegistry, KafkaProducer, KafkaConsumer
6. **Setup REST API**: Bind Akka HTTP server (if enabled)
7. **Register Shutdown Hook**: Graceful termination on SIGTERM

---

## Module Dependencies

```
test-probe-boot
  └─> test-probe-interface-rest
  └─> test-probe-core
      └─> test-probe-client-adapters
      └─> test-probe-glue
          └─> test-probe-common
```

**Import Rule**: Modules can only depend on modules below them in hierarchy.

---

## Integration Points

### Kafka

- **Client**: Alpakka Kafka (Akka Streams)
- **Producers**: Connection pooling, async send with `Future`
- **Consumers**: Isolated consumer groups per test (`test-probe-{testId}`)
- **Serialization**: Circe JSON for events

### S3 Storage

- **Client**: AWS SDK v2 async
- **Strategy**: Multipart upload for files > 5MB
- **Fallback**: Local filesystem mode for development
- **Evidence**: Stored as `{testId}/{timestamp}/evidence.zip`

### Vault

- **Client**: Custom HTTP client via Akka HTTP
- **Auth**: AppRole or Token methods
- **Caching**: TTL-based with automatic refresh
- **Fallback**: Environment variables if Vault unavailable

---

## Related Guides

- **Actors**: See `.claude/guides/ACTORS.md` for actor patterns
- **Testing**: See `.claude/guides/TESTING.md` for testing strategies
- **Development**: See `.claude/guides/DEVELOPMENT.md` for common tasks