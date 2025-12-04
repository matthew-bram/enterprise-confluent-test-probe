# 07 Models Overview

**Last Updated:** 2025-11-26
**Status:** Active - Core domain models complete
**Component:** Domain Model Architecture
**Related Documents:**
- [07.1 Probe Testing Models](07.1-probe-testing-models.md)
- [07.2 Event Models](07.2-event-models.md)
- [Topic Directive Model](../../../api/topic-directive-model.md)

---

## Table of Contents

- [Overview](#overview)
- [Model Categories](#model-categories)
- [Immutability Principles](#immutability-principles)
- [Model Hierarchy](#model-hierarchy)
- [Serialization Strategies](#serialization-strategies)
- [Related Documents](#related-documents)

---

## Overview

The Test-Probe framework uses immutable case classes as the foundation of its domain model. Models are organized by responsibility and follow functional programming principles to ensure type safety, thread safety, and clear boundaries between concerns.

**Design Principles:**

1. **Immutability**: All models are immutable case classes (no mutable state)
2. **Type Safety**: Compile-time guarantees through Scala type system
3. **Thread Safety**: Immutable models safe for concurrent actor operations
4. **Explicit**: No implicit conversions, clear field names
5. **Documentation**: Comprehensive ScalaDoc for all public models

**Module Organization:**

| Module | Purpose | Examples |
|--------|---------|----------|
| `test-probe-common` | Shared models (contracts) | TopicDirective, BlockStorageDirective, KafkaSecurityDirective |
| `test-probe-core` | Core domain models | ActorCommands, TestExecutionResult, CloudEvent |
| `test-probe-interfaces` | REST API models | RestStartTestRequest, RestStartTestResponse |

---

## Model Categories

### 1. Probe Testing Models

**Purpose:** Domain models for test configuration and execution

**Key Models:**

```scala
// TopicDirective - Kafka topic configuration
case class TopicDirective(
  topic: String,
  role: String,
  clientPrincipal: String,
  eventFilters: List[EventFilter],
  metadata: Map[String, String] = Map.empty,
  bootstrapServers: Option[String] = None
)

// EventFilter - CloudEvent filtering criteria
case class EventFilter(
  eventType: String,      // CloudEvent type
  payloadVersion: String  // CloudEvent schema version
)

// BlockStorageDirective - Test data location
case class BlockStorageDirective(
  jimfsLocation: String,
  evidenceDir: String,
  topicDirectives: List[TopicDirective],
  bucket: String,
  userGluePackages: List[String] = List.empty,
  tags: List[String] = List.empty
)

// KafkaSecurityDirective - Kafka authentication
enum SecurityProtocol:
  case PLAINTEXT
  case SASL_SSL

case class KafkaSecurityDirective(
  topic: String,
  role: String,
  securityProtocol: SecurityProtocol,
  jaasConfig: String
)

// TestExecutionResult - Test outcome
case class TestExecutionResult(
  testId: UUID,
  status: String,
  scenariosPassed: Int,
  scenariosFailed: Int,
  startTime: Instant,
  endTime: Instant,
  evidencePath: String
)
```

**Reference:** [07.1 Probe Testing Models](07.1-probe-testing-models.md)

---

### 2. Event Models

**Purpose:** CloudEvents and Kafka message structures

**Key Models:**

```scala
// CloudEvent - CloudEvents 1.0 specification
case class CloudEvent(
  id: String,
  source: String,
  specversion: String = "1.0",
  `type`: String,
  time: String,
  subject: String,
  datacontenttype: String = "application/octet-stream",
  correlationid: String,
  payloadversion: String,
  time_epoch_micro_source: Long = 0L
)

// EventEnvelope - Kafka message wrapper
case class EventEnvelope(
  testId: UUID,
  topic: String,
  key: CloudEvent,
  value: Array[Byte],  // Serialized event payload
  headers: Map[String, String],
  offset: Long,
  partition: Int,
  timestamp: Instant
)
```

**Serialization:**
- CloudEvent: JSON (Confluent JSON Schema Serializer)
- Event Payload: JSON, Avro, or Protobuf (configurable via SerdesFactory)

**Reference:** [07.2 Event Models](07.2-event-models.md)

---

### 3. Actor Command Models

**Purpose:** Message protocols for actor communication

**Key Models:**

```scala
// GuardianActor commands
sealed trait GuardianActorProtocol
object GuardianActorProtocol {
  case class StartTest(
    testId: UUID,
    bucket: String,
    testType: Option[String],
    replyTo: ActorRef[Response]
  ) extends GuardianActorProtocol

  case class GetTestStatus(
    testId: UUID,
    replyTo: ActorRef[Response]
  ) extends GuardianActorProtocol
}

// QueueActor commands
sealed trait QueueActorProtocol
object QueueActorProtocol {
  case class EnqueueTest(
    testId: UUID,
    bucket: String,
    testType: Option[String]
  ) extends QueueActorProtocol

  case class DequeueTest() extends QueueActorProtocol
}

// TestExecutionActor commands
sealed trait TestExecutionActorProtocol
object TestExecutionActorProtocol {
  case class InitializeTest(
    testId: UUID,
    bucket: String,
    testType: Option[String]
  ) extends TestExecutionActorProtocol

  case class CancelTest() extends TestExecutionActorProtocol
}
```

**Pattern:**
- Sealed traits prevent external extension
- Case classes for exhaustive pattern matching
- `replyTo: ActorRef[Response]` for request-response
- Nested under companion object for namespace

---

### 4. REST API Models

**Purpose:** HTTP request/response DTOs (anti-corruption layer)

**Key Models:**

```scala
// REST request (kebab-case for JSON)
case class RestStartTestRequest(
  `test-id`: UUID,
  `block-storage-path`: String,
  `test-type`: Option[String]
)

// REST response (kebab-case for JSON)
case class RestStartTestResponse(
  `test-id`: UUID,
  accepted: Boolean,
  `test-type`: Option[String],
  message: String
)

// REST error response (RFC 7807-inspired)
case class RestErrorResponse(
  error: String,
  message: String,
  details: Option[String] = None,
  timestamp: Long
)
```

**Why Separate from Core Models?**
- HTTP/JSON concerns don't leak into core
- kebab-case (REST) vs camelCase (Scala) conversion at boundary
- Easy to add/change REST fields without affecting core

---

## Immutability Principles

### Why Immutability?

**1. Thread Safety**

```scala
// Immutable model safe in concurrent actor system
case class TopicDirective(topic: String, role: String, ...)

// Multiple actors can safely read same instance
actor1 ! directive  // Safe
actor2 ! directive  // Safe
actor3 ! directive  // Safe
```

**2. No Defensive Copying**

```scala
// Immutable - no copy needed
val directive = TopicDirective(...)
val stored = directive  // Cheap reference copy

// Mutable - MUST copy to prevent modification
class MutableDirective(var topic: String, ...)
val stored = mutableDirective.copy()  // Expensive deep copy
```

**3. Referential Transparency**

```scala
// Function always returns same output for same input
def buildSecurityDirective(topic: TopicDirective): KafkaSecurityDirective = {
  // topic never changes, safe to reason about
  KafkaSecurityDirective(topic.topic, topic.role, ...)
}
```

---

### Copy-on-Update Pattern

**Scala case classes provide `.copy()` method:**

```scala
val original = TopicDirective(
  topic = "orders.events",
  role = "PRODUCER",
  clientPrincipal = "order-service",
  eventFilters = List.empty,
  metadata = Map.empty
)

// Create modified copy (original unchanged)
val updated = original.copy(
  metadata = Map("test-run-id" -> "run-123")
)

// original.metadata == Map.empty  (unchanged)
// updated.metadata == Map("test-run-id" -> "run-123")
```

**Use Cases:**
- Actor state updates
- Builder pattern steps
- Adding metadata to directives

---

## Model Hierarchy

### Probe Configuration Models

```
BlockStorageDirective
  ├── jimfsLocation: String
  ├── evidenceDir: String
  ├── topicDirectives: List[TopicDirective]
  │   ├── topic: String
  │   ├── role: String
  │   ├── clientPrincipal: String
  │   ├── eventFilters: List[EventFilter]
  │   │   ├── eventType: String
  │   │   └── payloadVersion: String
  │   ├── metadata: Map[String, String]
  │   └── bootstrapServers: Option[String]
  ├── bucket: String
  ├── userGluePackages: List[String]
  └── tags: List[String]
```

---

### Security Models

```
KafkaSecurityDirective
  ├── topic: String
  ├── role: String
  ├── securityProtocol: SecurityProtocol
  │   ├── PLAINTEXT
  │   └── SASL_SSL
  └── jaasConfig: String

VaultCredentials (private[vault])
  ├── topic: String
  ├── role: String
  ├── clientId: String
  └── clientSecret: String
```

---

### Event Models

```
EventEnvelope
  ├── testId: UUID
  ├── topic: String
  ├── key: CloudEvent
  │   ├── id: String
  │   ├── source: String
  │   ├── specversion: String
  │   ├── type: String
  │   ├── time: String
  │   ├── subject: String
  │   ├── datacontenttype: String
  │   ├── correlationid: String
  │   ├── payloadversion: String
  │   └── time_epoch_micro_source: Long
  ├── value: Array[Byte]
  ├── headers: Map[String, String]
  ├── offset: Long
  ├── partition: Int
  └── timestamp: Instant
```

---

## Serialization Strategies

### 1. Actor Messages (Circe)

**Why Circe?**
- Type-safe JSON encoding/decoding
- Automatic derivation for case classes
- Better Scala 3 support than Spray JSON

**Usage:**
```scala
import io.circe.*
import io.circe.generic.semiauto.*

case class TopicDirective(topic: String, role: String, ...)
object TopicDirective {
  implicit val encoder: Encoder[TopicDirective] = deriveEncoder
  implicit val decoder: Decoder[TopicDirective] = deriveDecoder
}
```

---

### 2. REST API (Spray JSON)

**Why Spray JSON?**
- Pekko HTTP native integration
- Simple format definition
- Separation from core (no Circe conflict)

**Usage:**
```scala
import spray.json.*

case class RestStartTestRequest(
  `test-id`: UUID,
  `block-storage-path`: String,
  `test-type`: Option[String]
)

object RestStartTestRequest extends DefaultJsonProtocol {
  implicit val format: RootJsonFormat[RestStartTestRequest] =
    jsonFormat3(RestStartTestRequest.apply)
}
```

---

### 3. Kafka Messages (Confluent Serializers)

**Why Confluent?**
- Schema Registry integration
- Schema evolution support
- Industry-standard serialization

**Serialization Formats:**

| Format | Use Case | Schema Registry |
|--------|----------|----------------|
| **JSON Schema** | CloudEvent keys, simple events | Yes |
| **Avro** | Binary efficiency, backward compatibility | Yes |
| **Protobuf** | gRPC integration, cross-language | Yes |

**Configuration:**
```scala
val serdesFactory = SerdesFactory(
  schemaRegistryUrl = "http://localhost:8081",
  schemaFormat = "json"  // or "avro" or "protobuf"
)

val keySerializer = serdesFactory.createKeySerializer[CloudEvent]
val valueSerializer = serdesFactory.createValueSerializer[OrderEvent]
```

---

## Related Documents

**Model Details:**
- [07.1 Probe Testing Models](07.1-probe-testing-models.md)
- [07.2 Event Models](07.2-event-models.md)
- [Topic Directive Model](../../../api/topic-directive-model.md)

**Architecture:**
- [10.2 Serdes DSL Architecture](../10%20Kafka%20Streaming/10.2%20Serialization/10.2-serdes-dsl-architecture.md)
- [04.1 Service Layer Architecture](../04%20Adapters/04.1-service-layer-architecture.md)

**Implementation Files:**
- `test-probe-common/src/main/scala/io/distia/probe/common/models/`
- `test-probe-core/src/main/scala/io/distia/probe/core/models/`
- `test-probe-core/src/main/scala/io/distia/probe/core/pubsub/models/`

---

**Last Updated:** 2025-11-26
**Status:** Active - Core domain models complete
