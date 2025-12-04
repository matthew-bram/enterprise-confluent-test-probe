# test-probe-java-api

Java API module for Test-Probe framework - provides Java-friendly wrappers around the Scala DSL for producing and consuming Kafka events.

## What This Module Provides

`ProbeJavaDsl` - A CompletionStage-based async API for Java developers writing Cucumber step definitions:

- **Async Methods:** `produceEvent()`, `fetchConsumedEvent()` returning `CompletionStage<T>`
- **Blocking Methods:** `produceEventBlocking()`, `fetchConsumedEventBlocking()` for simpler test code
- **Automatic Type Conversions:** Java Map → Scala Map, Java Class → Scala ClassTag, Scala Future → CompletionStage
- **Exception Mapping:** All Scala exceptions mapped to Java exceptions

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>io.distia.probe</groupId>
    <artifactId>test-probe-java-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 5-Minute Example

```java
import io.distia.probe.javaapi.ProbeJavaDsl;
import io.distia.probe.core.pubsub.models.*;
import java.util.Map;
import java.util.UUID;

// 1. Initialize (once)
ProbeJavaDsl.registerSystem(actorSystem);

// 2. Produce event
CloudEvent key = new CloudEvent(
    UUID.randomUUID().toString(), // id
    "test-probe",                 // source
    "1.0",                        // specversion
    "OrderCreated",               // type
    ISO8601.now(),                // time
    "order-123",                  // subject
    "application/octet-stream",   // datacontenttype
    "corr-456",                   // correlationid
    "v1",                         // payloadversion
    0L                            // time_epoch_micro_source
);

OrderEvent order = OrderEvent.newBuilder()
    .setOrderId("order-123")
    .setCustomerId("cust-456")
    .setAmount(99.99)
    .build();

ProduceResult result = ProbeJavaDsl.produceEventBlocking(
    testId,
    "orders",
    key,
    order,
    Map.of("trace-id", "xyz"),
    OrderEvent.class
);

// 3. Consume event
ConsumedResult consumed = ProbeJavaDsl.fetchConsumedEventBlocking(
    testId,
    "orders-processed",
    "corr-456",
    OrderProcessedEvent.class
);

if (consumed instanceof ConsumedSuccess) {
    ConsumedSuccess success = (ConsumedSuccess) consumed;
    OrderProcessedEvent event = (OrderProcessedEvent) success.value();
    System.out.println("Order processed: " + event.getOrderId());
}

// 4. Cleanup
ProbeJavaDsl.clearSystem();
```

## Documentation

**Comprehensive API Reference:** [docs/api/probe-java-dsl-api.md](../docs/api/probe-java-dsl-api.md)

Covers:
- All API methods with parameters and return types
- Type conversions (Map, ClassTag, Future)
- Exception handling with examples
- CloudEvent construction patterns
- Thread safety guarantees
- Best practices for Cucumber steps

**User Guides:**
- [Java Step Definitions Guide](../docs/user-guide/java-step-definitions-guide.md) - Writing Cucumber steps in Java
- [Java Event Models Guide](../docs/user-guide/java-event-models.md) - Creating JSON, Avro, Protobuf events

## Module Structure

```
test-probe-java-api/
├── src/main/java/io/distia/probe/javaapi/
│   └── ProbeJavaDsl.java        # Main API class
├── pom.xml                       # Maven configuration
└── README.md                     # This file
```

## Dependencies

This module depends on:
- `test-probe-core` - For ProbeScalaDsl, CloudEvent, result types
- `pekko-actor-typed` - For ActorSystem
- `scala-library` - For Scala interop (Future, ClassTag, Map)

## Design Philosophy

**Thin Wrapper, Zero Logic:**
- All business logic in `ProbeScalaDsl` (Scala)
- Java API only handles type conversions and async bridging
- No duplicate code between Java and Scala APIs

**Java Idioms:**
- `CompletionStage<T>` instead of Scala `Future[T]`
- `Map<K,V>` instead of Scala `Map[K,V]`
- `Class<T>` instead of Scala `ClassTag[T]`
- Builder patterns for event construction

**Test-Friendly:**
- Blocking variants for simple step definitions
- Clear exception messages
- Type-safe generics

## Examples in the Wild

See `IntegrationProduceConsumeSteps.java` in test-probe-core for real-world examples:
- JSON events (TestEvent, UserEvent, ProductEvent)
- Avro events (OrderEvent, InventoryEvent, ShipmentEvent)
- Protobuf events (PaymentEvent, MetricsEvent)

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-26 | Initial release |
