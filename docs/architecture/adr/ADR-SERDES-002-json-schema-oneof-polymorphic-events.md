# ADR-SERDES-002: JSON Schema OneOf for Polymorphic Event Types

**Status:** Accepted
**Date:** 2025-01-21
**Decision Makers:** Architecture Team, Test Probe Core Team
**Consulted:** Kafka SME, Platform Engineering
**Informed:** Development Teams, QA, DevOps

---

## Context

### Problem Statement

The test probe needs to support multiple event types on a single Kafka topic to enable **event democratization**—allowing diverse services to produce different event types to a shared topic while maintaining schema validation, independent evolution, and type safety.

**Current State:**
- Single event type per topic (TestEventPayload)
- TopicRecordNameStrategy with JSON Schema
- CloudEvent keys for metadata and partitioning
- One-to-one mapping: topic → event type → schema

**Limitations:**
1. **Topic Proliferation:** Each new event type requires a new topic
2. **Consumer Complexity:** Consumers must subscribe to N topics for N event types
3. **Operational Overhead:** Topic management, ACLs, monitoring multiply with event types
4. **Partitioning Challenges:** Related events across topics cannot maintain order
5. **Schema Governance:** Fragmented schemas across many topics

### Business Drivers

**Event Democratization Goals:**
1. **Single Topic per Domain:** All order events → `orders` topic, all payment events → `payments` topic
2. **Independent Service Evolution:** Services add new event types without coordinating topic creation
3. **Consumer Flexibility:** Consumers subscribe once, filter by event type
4. **Operational Simplicity:** Manage 5 domain topics instead of 50+ event-specific topics
5. **Cost Efficiency:** Fewer topics → lower cluster resource usage

**Example Scenario:**
```
Current (Topic per Event):
- orders-created (OrderCreated)
- orders-updated (OrderUpdated)
- orders-cancelled (OrderCancelled)
- orders-shipped (OrderShipped)
→ 4 topics, 4 ACL configs, 4 monitoring dashboards

Target (Polymorphic Topic):
- orders (OrderCreated | OrderUpdated | OrderCancelled | OrderShipped)
→ 1 topic, 1 ACL config, 1 monitoring dashboard
```

### Technical Requirements

1. **Type Safety:** Consumers must deserialize to correct concrete types
2. **Schema Validation:** Broker/producer validates messages against registered schemas
3. **Independent Evolution:** Each event type evolves independently (BACKWARD/FORWARD compatibility)
4. **Discoverability:** New event types discoverable via Schema Registry
5. **Performance:** No significant serialization/deserialization overhead
6. **Tooling Support:** Compatible with Confluent Platform, Schema Registry, Control Center

---

## Decision

### Use JSON Schema OneOf with TopicRecordNameStrategy

We will use **JSON Schema `oneOf`** to define polymorphic event types on shared topics, combined with **TopicRecordNameStrategy** for subject naming.

### Architecture

#### 1. Schema Structure

**Parent Schema (Discriminator):**
```json
{
  "$schema": "http://json-schema.org/draft-2020-12/schema#",
  "title": "OrderEvent",
  "oneOf": [
    {"$ref": "OrderCreated.json"},
    {"$ref": "OrderUpdated.json"},
    {"$ref": "OrderCancelled.json"},
    {"$ref": "OrderShipped.json"}
  ],
  "discriminator": {
    "propertyName": "eventType",
    "mapping": {
      "OrderCreated": "OrderCreated.json",
      "OrderUpdated": "OrderUpdated.json",
      "OrderCancelled": "OrderCancelled.json",
      "OrderShipped": "OrderShipped.json"
    }
  }
}
```

**Concrete Event Schema (OrderCreated.json):**
```json
{
  "$schema": "http://json-schema.org/draft-2020-12/schema#",
  "title": "OrderCreated",
  "type": "object",
  "properties": {
    "eventType": {"type": "string", "const": "OrderCreated"},
    "orderId": {"type": "string"},
    "customerId": {"type": "string"},
    "amount": {"type": "number"},
    "currency": {"type": "string"}
  },
  "required": ["eventType", "orderId", "customerId", "amount"]
}
```

#### 2. Subject Naming (TopicRecordNameStrategy)

**Schema Registry Subjects:**
```
orders-OrderCreated     (value schema)
orders-OrderUpdated     (value schema)
orders-OrderCancelled   (value schema)
orders-OrderShipped     (value schema)
orders-CloudEvent       (key schema - shared by all events)
```

**Pattern:** `{topic}-{RecordName}`
- No `-key`/`-value` suffix (TopicRecordNameStrategy behavior)
- Each concrete type registers independently
- Parent schema not registered (discriminator only)

#### 3. Producer Configuration

```scala
val producerProps = Map(
  ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG -> classOf[KafkaJsonSchemaSerializer[CloudEvent]],
  ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG -> classOf[KafkaJsonSchemaSerializer[OrderEvent]],
  "schema.registry.url" -> schemaRegistryUrl,
  "auto.register.schemas" -> "false",  // Manual registration required
  "use.latest.version" -> "true",
  "json.fail.invalid.schema" -> "true",
  "key.subject.name.strategy" -> "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy",
  "value.subject.name.strategy" -> "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy"
)
```

#### 4. Consumer Configuration

**Option A: Deserialize to Base Type**
```scala
val consumerProps = Map(
  ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> classOf[KafkaJsonSchemaDeserializer[OrderEvent]],
  "json.value.type" -> classOf[OrderEvent].getName
)

// Consumer handles all types via pattern matching
record.value() match {
  case created: OrderCreated => handleOrderCreated(created)
  case updated: OrderUpdated => handleOrderUpdated(updated)
  case cancelled: OrderCancelled => handleOrderCancelled(cancelled)
  case shipped: OrderShipped => handleOrderShipped(shipped)
}
```

**Option B: Filter by Event Type (Recommended)**
```scala
// Consumer subscribes to topic with filtering
val consumerProps = Map(
  ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> classOf[KafkaJsonSchemaDeserializer[OrderCreated]],
  "json.value.type" -> classOf[OrderCreated].getName
)

consumer.subscribe(List("orders"))
// Filter in application logic or use Kafka Streams filtering
records.filter(_.value().eventType == "OrderCreated")
```

---

## Consequences

### Positive Consequences

#### 1. Event Democratization

**Single Topic per Domain:**
- Orders: `orders` topic (all order events)
- Payments: `payments` topic (all payment events)
- Inventory: `inventory` topic (all inventory events)

**Benefits:**
- Fewer topics → simpler operations
- Logical grouping by business domain
- Easier discovery (one topic to explore)
- Cost savings (fewer partitions, lower resource usage)

#### 2. Independent Schema Evolution

Each event type evolves independently:
```bash
# Add new field to OrderCreated
curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data @OrderCreated-v2.json \
  "http://localhost:8081/subjects/orders-OrderCreated/versions"

# Does NOT impact OrderUpdated, OrderCancelled, etc.
```

**Compatibility Modes (Per Event Type):**
- `orders-OrderCreated`: BACKWARD (consumers can read old and new)
- `orders-OrderUpdated`: FULL (both directions)
- `orders-OrderCancelled`: FORWARD (producers can write new, consumers catch up)

#### 3. Simplified Consumer Logic

**Before (Multiple Topics):**
```scala
consumer.subscribe(List(
  "orders-created",
  "orders-updated",
  "orders-cancelled",
  "orders-shipped"
))

// Complex topic → handler mapping
records.foreach { record =>
  record.topic() match {
    case "orders-created" => handleOrderCreated(deserialize[OrderCreated](record))
    case "orders-updated" => handleOrderUpdated(deserialize[OrderUpdated](record))
    case "orders-cancelled" => handleOrderCancelled(deserialize[OrderCancelled](record))
    case "orders-shipped" => handleOrderShipped(deserialize[OrderShipped](record))
  }
}
```

**After (Single Topic with Polymorphism):**
```scala
consumer.subscribe(List("orders"))

// Type-based dispatching
records.foreach { record =>
  record.value() match {
    case created: OrderCreated => handleOrderCreated(created)
    case updated: OrderUpdated => handleOrderUpdated(updated)
    case cancelled: OrderCancelled => handleOrderCancelled(cancelled)
    case shipped: OrderShipped => handleOrderShipped(shipped)
  }
}
```

#### 4. Partitioning and Ordering

Related events maintain order:
```scala
// All events for order "order-123" go to same partition
val key = CloudEvent(
  id = UUID.randomUUID(),
  source = "order-service",
  type = "OrderCreated",
  correlationid = "order-123"  // Partition key
)

// Guaranteed order:
// 1. OrderCreated (partition 5)
// 2. OrderUpdated (partition 5)
// 3. OrderShipped (partition 5)
```

#### 5. Schema Discovery

All event types visible in Schema Registry:
```bash
# List all order event schemas
curl http://localhost:8081/subjects | grep "orders-"

# Output:
# orders-CloudEvent       (key)
# orders-OrderCreated     (value)
# orders-OrderUpdated     (value)
# orders-OrderCancelled   (value)
# orders-OrderShipped     (value)
```

### Negative Consequences

#### 1. Broker-Level Validation Limitations

**Challenge:** Confluent Server validates per-message but doesn't enforce oneOf at broker level.

**Example:**
```scala
// Producer can send ANY registered schema for the topic
// Broker validates: "Is this schema registered for 'orders' topic?"
// Broker does NOT validate: "Is this schema part of the oneOf union?"

// Valid (OrderCreated is registered)
producer.send(new ProducerRecord("orders", cloudEvent, orderCreated))

// Also valid (OrderUpdated is registered)
producer.send(new ProducerRecord("orders", cloudEvent, orderUpdated))

// PROBLEM: No enforcement that both are part of same oneOf family
```

**Mitigation:**
- Manual governance via Schema Registry ACLs
- CI/CD validation: "All order schemas must reference parent OrderEvent"
- Monitoring: Alert on unexpected schema registrations

#### 2. Consumer Complexity (Polymorphic Deserialization)

**Challenge:** Consumers must handle multiple types.

**Option A: Deserialize to base type + pattern match**
```scala
// Requires sealed trait hierarchy
sealed trait OrderEvent
case class OrderCreated(...) extends OrderEvent
case class OrderUpdated(...) extends OrderEvent

// Pattern matching
record.value() match {
  case created: OrderCreated => // handle
  case updated: OrderUpdated => // handle
}
```

**Option B: Filter before deserialization (recommended)**
```scala
// Consumer only cares about OrderCreated
val deserializer = new KafkaJsonSchemaDeserializer[OrderCreated]

consumer.subscribe(List("orders"))
records
  .filter(r => extractEventType(r) == "OrderCreated")
  .map(r => deserializer.deserialize("orders", r.value()))
```

**Trade-offs:**
- Option A: More flexible, more complex
- Option B: Simpler, requires filtering

#### 3. Schema Registry Performance

**Challenge:** More schemas → more registry lookups.

**Before (1 topic = 1 schema):**
- 1 schema lookup per topic
- Cache hit ratio: 99%+

**After (1 topic = N schemas):**
- N schema lookups per topic
- Cache hit ratio: Still 99%+ (due to schema ID caching)
- Slightly higher registry load

**Mitigation:**
- Schema Registry caching (`latest.cache.ttl=60`)
- Schema ID embedding in messages (Confluent wire format)
- Monitoring: Schema Registry latency metrics

#### 4. Operational Complexity

**New Operational Concerns:**

**Schema Governance:**
- More schemas to review and approve
- Compatibility enforcement per event type
- Breaking change detection across union members

**Monitoring:**
- Track event type distribution (5% OrderCreated, 80% OrderUpdated, etc.)
- Alert on skewed distribution (possible producer misconfiguration)
- Schema version mismatch detection

**Troubleshooting:**
- "Which event type failed validation?"
- "Why did consumer X deserialize to wrong type?"
- "Is event type Y part of the union?"

**Mitigation:**
- Automated schema governance (CI/CD)
- Enhanced monitoring (event type metrics)
- Documentation and runbooks

#### 5. Migration Complexity

**Challenge:** Migrating existing single-type topics to polymorphic topics.

**Migration Path:**
1. **Dual Write Phase:**
   - Producer writes to BOTH old topic and new polymorphic topic
   - Consumers read from old topic
   - Validate data consistency

2. **Consumer Migration:**
   - Migrate consumers one-by-one to new topic
   - Monitor lag and error rates
   - Rollback capability

3. **Deprecation Phase:**
   - Stop writing to old topic
   - Archive old topic data
   - Decommission old topic

**Complexity:**
- 6-12 week migration timeline
- Coordination across teams
- Rollback complexity

---

## Implementation Strategy

### Phase 1: Foundation (Week 1-2)

**Objectives:**
- Implement oneOf support in test probe
- Create test fixtures for polymorphic events
- Document patterns and best practices

**Deliverables:**
1. Feature file: `test-probe-core/src/test/resources/features/component/streaming/polymorphic-events.feature`
2. Step definitions: `PolymorphicEventSteps.scala`
3. Fixtures: Updated SerdesFixtures with oneOf support
4. Documentation: This ADR + implementation guide

### Phase 2: Pilot (Week 3-4)

**Objectives:**
- Deploy to development environment
- Test with 2-3 event types
- Gather feedback

**Success Criteria:**
- 100% test pass rate
- Schema validation working correctly
- Consumer deserialization successful
- Performance benchmarks met (< 2ms serialization)

### Phase 3: Production Rollout (Week 5-8)

**Objectives:**
- Gradual rollout to production
- Monitor key metrics
- Iterate based on learnings

**Rollout Strategy:**
1. Low-volume topics first (< 100 msg/sec)
2. Medium-volume topics (100-1000 msg/sec)
3. High-volume topics (1000+ msg/sec)

### Phase 4: Optimization (Week 9-12)

**Objectives:**
- Performance tuning
- Operational improvements
- Documentation updates

---

## Alternatives Considered

### Alternative 1: Topic Per Event Type (Status Quo)

**Pros:**
- Simple schema management (1 topic = 1 schema)
- Clear separation of concerns
- Easy to understand

**Cons:**
- Topic proliferation (100+ topics for large systems)
- Consumer complexity (subscribe to many topics)
- Operational overhead (ACLs, monitoring, backups)
- Cost inefficiency

**Decision:** Rejected due to scalability and operational concerns.

### Alternative 2: Avro Union Types

**Pros:**
- Native Avro support for unions
- Compact binary format
- Strong tooling support

**Cons:**
- Loss of JSON readability (binary format)
- Schema evolution more complex (Avro compatibility rules)
- Requires Avro-specific tooling
- Migration cost (already using JSON Schema)

**Decision:** Rejected to maintain JSON ecosystem and readability.

### Alternative 3: Protobuf OneOf

**Pros:**
- Native oneOf support
- Efficient binary format
- Strong typing

**Cons:**
- Loss of JSON readability
- Schema evolution limitations (field numbers)
- Migration cost
- Less flexible than JSON Schema

**Decision:** Rejected to maintain JSON ecosystem.

### Alternative 4: Single Polymorphic Schema (No OneOf)

**Example:**
```json
{
  "type": "object",
  "properties": {
    "eventType": {"type": "string"},
    "payload": {"type": "object"}  // Unstructured
  }
}
```

**Pros:**
- Single schema to manage
- Maximum flexibility

**Cons:**
- No type safety (payload is unstructured)
- No schema validation for payloads
- Loss of schema evolution benefits
- Consumer deserialization complexity

**Decision:** Rejected due to loss of type safety and validation.

---

## References

### Confluent Documentation

1. **JSON Schema Serializer**
   - https://docs.confluent.io/platform/current/schema-registry/serdes-develop/serdes-json.html

2. **Subject Naming Strategies**
   - https://docs.confluent.io/platform/current/schema-registry/serdes-develop/index.html#subject-name-strategy

3. **Schema Evolution and Compatibility**
   - https://docs.confluent.io/platform/current/schema-registry/avro.html#compatibility-types

### JSON Schema Specification

1. **OneOf Keyword**
   - https://json-schema.org/understanding-json-schema/reference/combining.html#oneof

2. **Discriminator**
   - https://swagger.io/docs/specification/data-models/inheritance-and-polymorphism/

### Internal Documentation

1. **ACL Configuration Guide**
   - `docs/kafka/CONFLUENT-ACL-CONFIGURATION-GUIDE.md`

2. **Serdes Strategy ADR**
   - `docs/architecture/adr/ADR-SERDES-001-confluent-json-schema-serialization-strategy.md`

3. **Topic Naming Standards**
   - `docs/standards/TOPIC-NAMING-CONVENTIONS.md`

---

## Monitoring and Success Metrics

### Key Metrics

**Performance:**
- Serialization latency: < 2ms p99
- Deserialization latency: < 2ms p99
- Schema Registry lookup latency: < 10ms p99
- Cache hit ratio: > 99%

**Correctness:**
- Schema validation pass rate: 100%
- Deserialization error rate: < 0.01%
- Wrong type deserialization: 0

**Operational:**
- Number of schemas per topic: Track growth
- Schema registration rate: Alert on spikes
- ACL violations: 0 per week

### Alerts

1. **Schema validation failures** > 10/min → Page on-call
2. **Deserialization errors** > 1% → Investigate
3. **Unexpected schema registration** → Page security team
4. **Schema Registry latency** > 50ms p99 → Scale registry

---

## Revision History

| Version | Date       | Author              | Changes                           |
|---------|------------|---------------------|-----------------------------------|
| 1.0     | 2025-01-21 | Test Probe Team     | Initial version                   |

---

**Approval:**
- [x] Architecture Review Board
- [x] Security Team (ACL governance)
- [x] Platform Engineering (operational impact)
- [x] Development Teams (API impact)

**Status:** Accepted and implementation in progress (Phase 1).
