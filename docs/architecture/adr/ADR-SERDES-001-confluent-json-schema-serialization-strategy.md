# ADR-SERDES-001: Confluent JSON Schema Serialization Strategy

**Status:** Accepted

**Date:** 2025-11-19

**Context:** Serdes implementation for CloudEvent-based Kafka messaging

**Deciders:** Development Team

---

## Context and Problem Statement

Test-Probe uses **CloudEvent as the message key** (for partitioning by correlationid) and **event payloads as values** (for business data). We needed a serialization strategy that:

1. ✅ Supports JSON Schema with Schema Registry validation
2. ✅ Handles polymorphic deserialization (multiple event types per topic)
3. ✅ Enables independent schema evolution
4. ✅ Works with Scala 3.3.6 case classes and ClassTag generics
5. ✅ Provides deterministic serialization for consistent partitioning
6. ✅ Integrates with Confluent Platform 8.1.0

### The Critical Issue (5-Day Investigation)

**ClassCastException:**
```
class java.util.LinkedHashMap cannot be cast to class [B
(java.util.LinkedHashMap and [B are in module java.base of loader 'bootstrap')
```

**Root Cause:** Fundamental misunderstanding of `KafkaJsonSchemaDeserializer<T>` type parameter.

**Broken Implementation:**
```scala
// ❌ WRONG: Type parameter Array[Byte] means "deserialize TO Array[Byte]"
private val des = new KafkaJsonSchemaDeserializer[Array[Byte]](schemaRegistryClient)
val deserializedBytes = des.deserialize(topic, bytes)  // Returns LinkedHashMap!
val jsonString = new String(deserializedBytes, "UTF-8")  // ClassCastException!
```

**Key Learning:** The type parameter `T` in `KafkaJsonSchemaDeserializer<T>` is the **output type** after deserialization, NOT an intermediate format. When configured without explicit type configuration, it defaults to Java's generic JSON representation (`LinkedHashMap`), which cannot be cast to `Array[Byte]`.

---

## Decision Drivers

1. **CloudEvent as Key Requirement** - Full CloudEvent structure needed for partitioning
2. **oneOf Union Schema Pattern** - Confluent's production recommendation for multi-event topics
3. **Type Safety** - Scala ClassTag-based generics without concrete class passing
4. **Schema Evolution** - Independent evolution of individual event types
5. **Deterministic Serialization** - Same CloudEvent → same bytes → same partition
6. **Performance** - Minimize serialization overhead and Schema Registry calls

---

## Considered Options

### Option 1: Direct Type Deserialization
```scala
val des = new KafkaJsonSchemaDeserializer[CloudEvent](schemaRegistryClient)
return des.deserialize(topic, bytes)  // Returns CloudEvent directly
```

**Pros:**
- Simplest approach
- One-step deserialization
- Fastest performance

**Cons:**
- ❌ Cannot handle polymorphic deserialization (oneOf)
- ❌ Requires separate deserializer per type
- ❌ Doesn't support discriminator-based type routing

### Option 2: JsonNode + Discriminator (SELECTED)
```scala
val des = new KafkaJsonSchemaDeserializer[JsonNode](schemaRegistryClient)
val node = des.deserialize(topic, bytes)  // Returns JsonNode
val typed = mapper.treeToValue(node, targetClass)  // Convert to T
```

**Pros:**
- ✅ Supports polymorphic deserialization
- ✅ Works with oneOf union schemas
- ✅ Enables discriminator-based type routing
- ✅ Single deserializer for all types
- ✅ Schema validation at deserialization time

**Cons:**
- Slight performance overhead (two-step process)
- Requires ObjectMapper configuration

### Option 3: Byte Array + Manual JSON Parsing
```scala
val des = new KafkaJsonSchemaDeserializer[Array[Byte]](schemaRegistryClient)
// This doesn't work - see "Critical Issue" above!
```

**Verdict:** ❌ **Fundamentally broken** - this was the source of the 5-day bug.

---

## Decision Outcome

**Chosen Option:** **Option 2 - JsonNode + Discriminator Pattern**

This is **Confluent's recommended approach** for handling multiple event types in the same topic (oneOf union schema pattern).

---

## Implementation Details

### Serialization Configuration

#### Key Serialization (CloudEvent)
```scala
// ScalaConfluentJsonSerializer configuration
Map(
  "schema.registry.url" -> extractSchemaRegistryUrl,
  "auto.register.schemas" -> "false",        // Use pre-registered CloudEvent schema
  "use.latest.version" -> "true",            // Pin to schema version
  "normalize.schemas" -> "true",             // CRITICAL: Deterministic serialization
  "json.fail.invalid.schema" -> "true",      // Fail fast on schema violations
  "json.schema.spec.version" -> "draft_2020_12",
  "value.subject.name.strategy" -> classOf[TopicNameStrategy].getName,
  "latest.cache.size" -> "5000",             // Cache schemas for performance
  "latest.cache.ttl.sec" -> "3600"
)
```

**Key Settings Explained:**

1. **`normalize.schemas=true`** (CRITICAL for keys)
   - Ensures deterministic JSON serialization
   - Same CloudEvent → same byte representation → same Kafka partition
   - Without this: field ordering may vary, breaking partitioning

2. **`auto.register.schemas=false`**
   - Use pre-registered schemas from Schema Registry
   - Prevents accidental schema overwrites
   - Production best practice

3. **`use.latest.version=true`**
   - Use latest schema version for serialization
   - Ensures consistency across producers

4. **`value.subject.name.strategy=TopicNameStrategy`**
   - Subject naming: `{topic}-key` or `{topic}-value`
   - Default strategy, works with Confluent Control Center UI
   - Required for oneOf union schema pattern

#### Value Serialization (Event Payloads)
```scala
// Same configuration as key, but with isKey=false
// This allows oneOf union schema validation at produce time
```

### Deserialization Configuration

#### JsonNode Pattern (Both Key and Value)
```scala
// ScalaConfluentJsonDeserializer configuration
val des = new KafkaJsonSchemaDeserializer[JsonNode](schemaRegistryClient)

des.configure(Map(
  "schema.registry.url" -> schemaRegistryUrl,
  KafkaJsonSchemaDeserializerConfig.JSON_VALUE_TYPE -> classOf[JsonNode].getName,  // CRITICAL!
  "json.fail.unknown.properties" -> "false",  // Flexible for schema evolution
  "value.subject.name.strategy" -> classOf[TopicNameStrategy].getName,
  "latest.cache.size" -> "5000",
  "latest.cache.ttl.sec" -> "3600"
).asJava, isKey)  // IMPORTANT: Use isKey parameter, not hardcoded false!
```

**Key Settings Explained:**

1. **`JSON_VALUE_TYPE=JsonNode`** (CRITICAL)
   - Tells deserializer to return Jackson JsonNode
   - Enables two-step deserialization: bytes → JsonNode → typed object
   - Required for polymorphic deserialization

2. **`json.fail.unknown.properties=false`**
   - Allows schema evolution (adding optional fields)
   - Old consumers can still deserialize new messages
   - Production best practice

3. **`isKey` parameter must be passed through!**
   - Originally hardcoded to `false` (5-day bug source)
   - Must match serialization: `isKey=true` for keys, `false` for values
   - Determines subject lookup: `{topic}-key` vs `{topic}-value`

### Two-Step Deserialization Process

```scala
def deserialize(topic: String, bytes: Array[Byte], isKey: Boolean = false): T = Try {
  // Step 1: Confluent deserializer (bytes → JsonNode)
  // - Extracts schema ID from bytes
  // - Fetches schema from Schema Registry
  // - Validates JSON against schema
  // - Returns JsonNode (Jackson tree model)
  val node: JsonNode = des.deserialize(topic, bytes)

  // Step 2: Jackson conversion (JsonNode → T)
  // - Uses ClassTag to determine target type
  // - Converts to Scala case class
  // - Handles Option, Seq, etc.
  val targetClass = summon[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
  val typed: T = mapper.treeToValue(node, targetClass)

  typed
} match {
  case Success(t) => t
  case Failure(exception) =>
    println(s"✗ Deserialization failed: ${exception.getMessage}")
    throw exception
}
```

**Why Two Steps?**

1. **Schema Validation**: Confluent deserializer validates against Schema Registry
2. **Type Safety**: Jackson uses ClassTag for compile-time type checking
3. **Polymorphism**: Enables discriminator-based type routing for oneOf
4. **Flexibility**: Can inspect JsonNode before conversion (useful for debugging)

---

## Integration with CloudEvent Architecture

### Message Structure

**Kafka Message:**
- **Key**: CloudEvent (JSON Schema) - Full CloudEvent metadata for partitioning
- **Value**: Event payload (JSON Schema) - Business event data

**Schema Registry Subjects:**
- `{topic}-key`: CloudEvent schema (single schema for all topics)
- `{topic}-value`: oneOf union schema (OrderCreated | OrderUpdated | OrderCancelled)

### Partitioning Strategy

**Partition by CloudEvent.correlationid:**
1. CloudEvent serialized with `normalize.schemas=true`
2. Same correlationid → same CloudEvent bytes → same Kafka partition
3. Ensures ordered processing per correlation ID

### oneOf Union Schema Pattern

**Producer Flow:**
```
1. Create CloudEvent + Payload
2. Serialize key: CloudEvent → bytes (validate against CloudEvent schema)
3. Serialize value: Payload → bytes (validate against oneOf union schema)
4. Produce to Kafka with partitioning by correlationid
```

**Consumer Flow:**
```
1. Consume from Kafka
2. Deserialize key: bytes → JsonNode → CloudEvent
3. Deserialize value: bytes → JsonNode → check discriminator → typed payload
4. Apply EventFilter predicate (if configured)
5. Process typed event
```

---

## Consequences

### Positive

1. ✅ **Type Safety** - ClassTag-based generics ensure compile-time type checking
2. ✅ **Schema Validation** - Schema Registry validates all messages at produce/consume time
3. ✅ **Deterministic Partitioning** - `normalize.schemas=true` ensures consistent hashing
4. ✅ **Polymorphic Deserialization** - Supports oneOf union schema with discriminator
5. ✅ **Schema Evolution** - Independent evolution of CloudEvent and event types
6. ✅ **Performance** - Schema caching (5000 entries, 1-hour TTL) minimizes Registry calls
7. ✅ **Debuggability** - JsonNode intermediate format enables inspection
8. ✅ **Confluent UI Integration** - TopicNameStrategy works with Control Center

### Negative

1. ⚠️ **Two-Step Deserialization** - Slight performance overhead vs direct deserialization
2. ⚠️ **Configuration Complexity** - Many settings must be correct for proper operation
3. ⚠️ **Error Messages** - ClassCastException if misconfigured (as we discovered!)

### Risks and Mitigations

**Risk 1: Schema Registry Unavailability**
- **Mitigation**: Schema caching (1-hour TTL) allows operation during brief outages
- **Monitoring**: Alert on Schema Registry latency/failures

**Risk 2: Schema Evolution Breaks Compatibility**
- **Mitigation**: `BACKWARD_TRANSITIVE` compatibility mode enforced
- **Testing**: Schema compatibility tests in CI/CD

**Risk 3: Performance Degradation**
- **Mitigation**: Cache tuning (5000 entries, 1-hour TTL)
- **Monitoring**: Track serialization/deserialization latency

---

## Key Learnings

### 1. The ClassCastException Root Cause

**Mistake:**
```scala
val des = new KafkaJsonSchemaDeserializer[Array[Byte]](schemaRegistryClient)
```

**Understanding:**
- Type parameter `T` is the **OUTPUT type** after deserialization
- `Array[Byte]` means "deserialize TO byte array"
- But Confluent deserializer returns Java objects (LinkedHashMap for generic JSON)
- Result: `LinkedHashMap` cannot be cast to `Array[Byte]` → ClassCastException

**Lesson:** Always understand library type parameters before using them!

### 2. The isKey Parameter Issue

**Mistake:**
```scala
des.configure(config.asJava, false)  // Hardcoded false!
```

**Impact:**
- Key deserialization looks up wrong subject: `{topic}-value` instead of `{topic}-key`
- Schema not found error or wrong schema applied

**Fix:**
```scala
des.configure(config.asJava, isKey)  // Pass through parameter!
```

**Lesson:** Never hardcode boolean flags that should be parameterized!

### 3. normalize.schemas is Critical for Keys

**Without `normalize.schemas=true`:**
- Field ordering may vary between serializations
- Same CloudEvent → different byte representations
- Different partitions per message → breaks ordering guarantees

**With `normalize.schemas=true`:**
- Consistent field ordering
- Same CloudEvent → same bytes
- Same partition → ordering preserved

**Lesson:** Deterministic serialization is critical for Kafka partitioning!

---

## Testing Strategy

### Unit Tests
```scala
"ScalaConfluentJsonSerializer" should {
  "serialize CloudEvent with deterministic output" in {
    val ce = CloudEvent(...)
    val bytes1 = serializer.serialize("topic", ce, isKey = true)
    val bytes2 = serializer.serialize("topic", ce, isKey = true)
    bytes1 shouldEqual bytes2  // Same input → same output
  }
}

"ScalaConfluentJsonDeserializer" should {
  "deserialize to JsonNode then convert to CloudEvent" in {
    val ce = CloudEvent(...)
    val bytes = serializer.serialize("topic", ce, isKey = true)
    val deserialized = deserializer.deserialize[CloudEvent]("topic", bytes, isKey = true)
    deserialized shouldEqual ce
  }

  "handle isKey parameter correctly" in {
    // Test key vs value subject lookup
    val keyBytes = serializer.serialize("topic", cloudEvent, isKey = true)
    val valueBytes = serializer.serialize("topic", payload, isKey = false)

    deserializer.deserialize[CloudEvent]("topic", keyBytes, isKey = true) shouldEqual cloudEvent
    deserializer.deserialize[Payload]("topic", valueBytes, isKey = false) shouldEqual payload
  }
}
```

### Integration Tests
```scala
"SerdesFactory with Schema Registry" should {
  "round-trip CloudEvent through serialization" in {
    withRunningSchemaRegistry { url =>
      SerdesFactory.setClient(client, url)

      val ce = CloudEvent(...)
      val bytes = SerdesFactory.serializeJsonSchema(ce, "topic", isKey = true)
      val deserialized = SerdesFactory.deserializeJsonSchema[CloudEvent](bytes, "topic", isKey = true)

      deserialized shouldEqual ce
    }
  }
}
```

---

## Configuration Reference

### Producer Configuration (Pekko Streams)
```scala
ProducerSettings(system, ByteArraySerializer(), ByteArraySerializer())
  .withBootstrapServers(bootstrapServers)
  .withProperty("enable.idempotence", "true")
  .withProperty("acks", "all")
  .withProperty("compression.type", "snappy")

// Serialization handled by SerdesFactory
val keyBytes = SerdesFactory.serializeJsonSchema[CloudEvent](ce, topic, isKey = true)
val valueBytes = SerdesFactory.serializeJsonSchema[Payload](payload, topic, isKey = false)
```

### Consumer Configuration (Pekko Streams)
```scala
ConsumerSettings(system, ByteArrayDeserializer(), ByteArrayDeserializer())
  .withBootstrapServers(bootstrapServers)
  .withGroupId(groupId)
  .withProperty("auto.offset.reset", "earliest")
  .withProperty("enable.auto.commit", "false")
  .withProperty("isolation.level", "read_committed")

// Deserialization handled by SerdesFactory
val ce = SerdesFactory.deserializeJsonSchema[CloudEvent](record.key(), record.topic(), isKey = true)
val payload = SerdesFactory.deserializeJsonSchema[Payload](record.value(), record.topic(), isKey = false)
```

---

## Related ADRs

- **ADR-KAFKA-001**: Kafka Streaming Architecture with Pekko Connectors
- **ADR-SCHEMA-REGISTRY-001**: Schema Registry Integration Strategy
- **ADR-CLOUDEVENTS-001**: CloudEvent as Message Key for Partitioning
- **ADR-ONEOF-001**: oneOf Union Schema Pattern for Multi-Event Topics (planned)

---

## References

1. **Confluent Documentation**
   - [JSON Schema Serializer](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/serdes-json.html)
   - [Multiple Event Types in Same Topic](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/serdes-json.html#multiple-event-types-in-the-same-topic)

2. **Internal Research**
   - `CONFLUENT-MULTIPLE-EVENT-TYPES-EXAMPLES.md`
   - `CONFLUENT-ONEOF-UNION-SCHEMA-IMPLEMENTATION-GUIDE.md`
   - `CONFLUENT-KEY-SERDES-BEST-PRACTICES.md`

3. **Code References**
   - `SerdesFactory.scala` - Generic serialization/deserialization factory
   - `ScalaConfluentJsonSerializer.scala` - JSON Schema serializer wrapper
   - `ScalaConfluentJsonDeserializer.scala` - JSON Schema deserializer wrapper
   - `PubSubModels.scala` - CloudEvent and message models

---

## Appendix: Debugging Tips

### Common Errors and Solutions

**Error 1: ClassCastException - LinkedHashMap to Array[Byte]**
```
Solution: Change KafkaJsonSchemaDeserializer[Array[Byte]] to [JsonNode]
```

**Error 2: Schema not found for subject**
```
Check: isKey parameter passed correctly (not hardcoded to false)
Check: Schema registered in Schema Registry
Check: Subject naming strategy matches producer
```

**Error 3: Different partitions for same correlationid**
```
Solution: Ensure normalize.schemas=true for key serialization
```

**Error 4: Unknown properties during deserialization**
```
Solution: Set json.fail.unknown.properties=false
Reason: Schema evolution added new fields
```

### Diagnostic Logging

Enable debug logging to see serialization/deserialization flow:
```scala
def deserialize(...): T = Try {
  val node: JsonNode = des.deserialize(topic, bytes)
  println(s"✓ Deserialized to JsonNode: ${node.toPrettyString}")

  val typed: T = mapper.treeToValue(node, targetClass)
  println(s"✓ Converted to ${targetClass.getSimpleName}: $typed")

  typed
}
```

---

**Last Updated:** 2025-11-19

**Authors:** Development Team

**Review Status:** Accepted after 5-day investigation and resolution
