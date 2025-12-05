# Java Step Definitions Guide

**Version:** 1.0.0
**Last Updated:** 2025-11-26
**Audience:** Java developers writing Cucumber step definitions for Test-Probe

---

## Overview

This guide shows how to write Cucumber step definitions in Java for testing event-driven architectures with the Test-Probe framework. It covers package structure, ProbeJavaDsl usage, CloudEvent construction, assertions, and best practices.

---

## Table of Contents

1. [Package Structure](#package-structure)
2. [Step Class Pattern](#step-class-pattern)
3. [Using ProbeJavaDsl](#using-probejavaadsl)
4. [CloudEvent Construction](#cloudevent-construction)
5. [Event Payload Patterns](#event-payload-patterns)
6. [Assertions](#assertions)
7. [Best Practices](#best-practices)
8. [Complete Examples](#complete-examples)

---

## Package Structure

### Recommended Layout

```
src/test/java/
└── com/yourcompany/tests/
    ├── steps/
    │   ├── OrderSteps.java           # Order-related steps
    │   ├── PaymentSteps.java         # Payment-related steps
    │   └── CommonSteps.java          # Shared setup/teardown
    ├── models/
    │   ├── OrderEvent.java           # Avro/JSON event models
    │   ├── PaymentEvent.java         # Protobuf event models
    │   └── TestEvent.java            # Generic test events
    ├── fixtures/
    │   ├── CloudEventFactory.java    # CloudEvent construction
    │   └── TestDataFactory.java      # Test data builders
    └── World.java                    # Cucumber world (shared state)
```

### Glue Package Configuration

```java
@CucumberOptions(
    features = "src/test/resources/features",
    glue = {
        "io.distia.probe.core.glue",      // Framework glue (REQUIRED)
        "com.yourcompany.tests.steps"       // Your step definitions
    },
    plugin = {"pretty", "json:target/cucumber-report.json"}
)
public class RunCucumberTest {}
```

**Important:** Always include `io.distia.probe.core.glue` for framework initialization steps.

---

## Step Class Pattern

### Basic Step Definition Class

```java
package com.yourcompany.tests.steps;

import io.distia.probe.javaapi.ProbeJavaDsl;
import io.distia.probe.core.pubsub.models.*;
import io.distia.probe.core.services.cucumber.CucumberContext;
import io.cucumber.java.en.*;
import java.util.*;
import static org.junit.Assert.*;

public class OrderSteps {

    // Shared state (Cucumber World pattern)
    private UUID testId;
    private String correlationId;
    private CloudEvent cloudEventKey;
    private OrderEvent orderPayload;

    // Constructor injection for shared state (optional)
    private final World world;

    public OrderSteps(World world) {
        this.world = world;
    }

    @Given("I have a test ID from the Cucumber context")
    public void iHaveTestId() {
        this.testId = CucumberContext.getTestId();
        assertNotNull("Test ID should be available", testId);
    }

    @Given("I create an order event with ID {string}")
    public void iCreateOrderEvent(String orderId) {
        // Implementation details below
    }

    @When("I produce the order event to topic {string}")
    public void iProduceOrderEvent(String topic) {
        // Implementation details below
    }

    @Then("I should receive an order processed event")
    public void iShouldReceiveOrderProcessed() {
        // Implementation details below
    }
}
```

### World Pattern (Shared State)

```java
package com.yourcompany.tests;

import java.util.*;

/**
 * Cucumber World - shared state across step definitions.
 * One instance per scenario execution.
 */
public class World {
    private UUID testId;
    private String correlationId;
    private Map<String, String> correlationIds = new HashMap<>();
    private Map<String, Object> payloads = new HashMap<>();

    public UUID getTestId() {
        return testId;
    }

    public void setTestId(UUID testId) {
        this.testId = testId;
    }

    public void storeCorrelationId(String eventTestId, String correlationId) {
        this.correlationIds.put(eventTestId, correlationId);
    }

    public String getCorrelationId(String eventTestId) {
        return this.correlationIds.get(eventTestId);
    }

    public void storePayload(String eventTestId, Object payload) {
        this.payloads.put(eventTestId, payload);
    }

    public <T> T getPayload(String eventTestId, Class<T> clazz) {
        return clazz.cast(payloads.get(eventTestId));
    }
}
```

---

## Using ProbeJavaDsl

### Initialization (Background Step)

```java
@Given("I have a test ID from the Cucumber context")
public void iHaveTestId() {
    this.testId = CucumberContext.getTestId();
    assertNotNull("Test ID should be available from CucumberContext", testId);
    System.out.println("Test ID: " + testId);
}
```

**Important:** `CucumberContext.getTestId()` is populated by the framework's `@Before` hook. Always retrieve it in a `@Given` step.

---

### Producing Events (Blocking)

```java
@When("I produce an {string} event to topic {string}")
public void iProduceEvent(String eventType, String topic) {
    // Create CloudEvent key
    CloudEvent key = CloudEventFactory.create(eventType, correlationId, "v1");

    // Create payload
    OrderEvent order = OrderEvent.newBuilder()
        .setOrderId("order-123")
        .setCustomerId("cust-456")
        .setAmount(99.99)
        .setCurrency("USD")
        .setTimestamp(System.currentTimeMillis())
        .build();

    // Produce event (blocking)
    ProduceResult result = ProbeJavaDsl.produceEventBlocking(
        testId,
        topic,
        key,
        order,
        Map.of("trace-id", "xyz-789"),
        OrderEvent.class
    );

    // Verify success
    assertTrue("Event should produce successfully",
        result instanceof ProducingSuccess);

    System.out.println("✅ Produced " + eventType + " to " + topic);
}
```

---

### Producing Events (Async)

```java
@When("I produce an {string} event to topic {string}")
public void iProduceEventAsync(String eventType, String topic) {
    CloudEvent key = CloudEventFactory.create(eventType, correlationId, "v1");
    OrderEvent order = createOrderEvent();

    // Produce asynchronously
    CompletionStage<ProduceResult> future = ProbeJavaDsl.produceEvent(
        testId,
        topic,
        key,
        order,
        Map.of(),
        OrderEvent.class
    );

    // Wait for completion
    future.thenAccept(result -> {
        assertTrue("Event should produce successfully",
            result instanceof ProducingSuccess);
    }).exceptionally(throwable -> {
        fail("Failed to produce event: " + throwable.getMessage());
        return null;
    }).toCompletableFuture().join();
}
```

**When to Use:**
- **Blocking:** Simpler code, acceptable for most Cucumber scenarios
- **Async:** Better performance when producing multiple events in parallel

---

### Consuming Events (Blocking with Retry)

```java
@Then("I should receive an {string} event on topic {string}")
public void iShouldReceiveEvent(String eventType, String topic) {
    // Retry loop for eventual consistency
    int maxAttempts = 10;
    int attemptDelay = 1000; // 1 second
    ConsumedResult result = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        System.out.println("Fetch attempt " + attempt + "/" + maxAttempts);

        try {
            result = ProbeJavaDsl.fetchConsumedEventBlocking(
                testId,
                topic,
                correlationId,
                OrderProcessedEvent.class
            );
            System.out.println("✅ Event consumed on attempt " + attempt);
            break; // Success

        } catch (ConsumerNotAvailableException e) {
            if (attempt < maxAttempts) {
                System.out.println("Event not ready, waiting " + attemptDelay + "ms...");
                Thread.sleep(attemptDelay);
            } else {
                fail("Event not consumed after " + maxAttempts + " attempts: " + e.getMessage());
            }
        }
    }

    // Verify result
    assertNotNull("Event should be consumed", result);
    assertTrue("Expected ConsumedSuccess", result instanceof ConsumedSuccess);

    // Extract event data
    ConsumedSuccess success = (ConsumedSuccess) result;
    CloudEvent key = (CloudEvent) success.key();
    OrderProcessedEvent event = (OrderProcessedEvent) success.value();
    Map<String, String> headers = success.headers();

    // Assertions
    assertEquals("Event type should match", eventType, key.type());
    assertEquals("Order ID should match", "order-123", event.getOrderId());

    System.out.println("✅ Verified " + eventType + " event");
}
```

**Why Retry?**
- Kafka consumers have eventual consistency
- Event may not be consumed immediately
- 10 attempts × 1 second = 10 seconds total timeout

---

### Consuming Events (Async)

```java
@Then("I should receive an {string} event on topic {string}")
public void iShouldReceiveEventAsync(String eventType, String topic) {
    CompletionStage<ConsumedResult> future = ProbeJavaDsl.fetchConsumedEvent(
        testId,
        topic,
        correlationId,
        OrderProcessedEvent.class
    );

    future.thenAccept(result -> {
        assertTrue("Expected ConsumedSuccess", result instanceof ConsumedSuccess);
        ConsumedSuccess success = (ConsumedSuccess) result;
        OrderProcessedEvent event = (OrderProcessedEvent) success.value();
        assertEquals("order-123", event.getOrderId());
    }).exceptionally(throwable -> {
        fail("Failed to consume event: " + throwable.getMessage());
        return null;
    }).toCompletableFuture().join();
}
```

---

## CloudEvent Construction

### Factory Pattern (Recommended)

```java
package com.yourcompany.tests.fixtures;

import io.distia.probe.core.pubsub.models.CloudEvent;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class CloudEventFactory {

    public static CloudEvent create(
            String type,
            String correlationId,
            String payloadVersion) {
        return new CloudEvent(
            UUID.randomUUID().toString(),               // id
            "test-probe",                               // source
            "1.0",                                      // specversion
            type,                                       // type
            DateTimeFormatter.ISO_INSTANT.format(Instant.now()), // time
            correlationId,                              // subject
            "application/octet-stream",                 // datacontenttype
            correlationId,                              // correlationid
            payloadVersion,                             // payloadversion
            0L                                          // time_epoch_micro_source
        );
    }

    public static CloudEvent createWithSubject(
            String type,
            String subject,
            String correlationId,
            String payloadVersion) {
        return new CloudEvent(
            UUID.randomUUID().toString(),
            "test-probe",
            "1.0",
            type,
            DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            subject,                                    // Custom subject (e.g., order-123)
            "application/octet-stream",
            correlationId,
            payloadVersion,
            0L
        );
    }

    /**
     * Generate deterministic correlation ID from eventTestId.
     * Enables reproducible tests across runs.
     */
    public static String getCorrelationIdForEventTestId(String eventTestId) {
        return UUID.nameUUIDFromBytes(eventTestId.getBytes()).toString();
    }

    public static CloudEvent createForEventTestId(
            String eventTestId,
            String type,
            String payloadVersion) {
        String correlationId = getCorrelationIdForEventTestId(eventTestId);
        return create(type, correlationId, payloadVersion);
    }
}
```

### Usage in Steps

```java
@Given("I create a CloudEvent key with eventTestId {string}")
public void iCreateCloudEventKey(String eventTestId) {
    // Deterministic correlation ID from eventTestId
    this.correlationId = CloudEventFactory.getCorrelationIdForEventTestId(eventTestId);

    // Create CloudEvent (will be updated with correct type/version later)
    this.cloudEventKey = CloudEventFactory.createForEventTestId(
        eventTestId,
        "OrderCreated",  // Default type
        "v1"             // Default version
    );

    System.out.println("Created CloudEvent with correlationId: " + correlationId);
}
```

---

## Event Payload Patterns

### JSON Events (Jackson POJOs)

```java
@And("I create a {word} payload with version {string}")
public void iCreatePayload(String eventType, String version) {
    switch (eventType) {
        case "OrderCreated":
            OrderCreatedEvent order = new OrderCreatedEvent(
                eventType,
                version,
                "order-123",
                "cust-456",
                99.99,
                "USD",
                System.currentTimeMillis()
            );
            this.orderPayload = order;
            break;

        // Other event types...
        default:
            throw new IllegalArgumentException("Unknown event type: " + eventType);
    }
}
```

### Avro Events (Builder Pattern)

```java
@And("I create an Avro {word} event")
public void iCreateAvroEvent(String eventType) {
    switch (eventType) {
        case "OrderEvent":
            OrderEvent order = OrderEvent.newBuilder()
                .setEventType(eventType)
                .setOrderId("order-123")
                .setCustomerId("cust-456")
                .setAmount(99.99)
                .setCurrency("USD")
                .setTimestamp(System.currentTimeMillis())
                .build();
            this.orderPayload = order;
            break;

        // Other Avro event types...
        default:
            throw new IllegalArgumentException("Unknown Avro event: " + eventType);
    }
}
```

### Protobuf Events (DynamicMessage)

```java
@And("I create a Protobuf {word} event")
public void iCreateProtobufEvent(String eventType) {
    switch (eventType) {
        case "PaymentEvent":
            // Create wrapper
            PaymentEvent payment = PaymentEvent.newBuilder()
                .setEventType(eventType)
                .setPaymentId("pay-123")
                .setOrderId("order-456")
                .setAmount(149.99)
                .setCurrency("USD")
                .setPaymentMethod("CREDIT_CARD")
                .setTimestamp(System.currentTimeMillis())
                .build();

            // Convert to DynamicMessage for serialization
            DynamicMessage dynamicMessage = payment.toDynamicMessage(PaymentEvent.SCHEMA);
            this.paymentPayload = dynamicMessage;
            break;

        // Other Protobuf event types...
        default:
            throw new IllegalArgumentException("Unknown Protobuf event: " + eventType);
    }
}
```

**Important:** ProbeJavaDsl expects `DynamicMessage.class` for Protobuf events, not the wrapper class.

---

## Assertions

### JUnit 5 Assertions

```java
import static org.junit.jupiter.api.Assertions.*;

@Then("the consumed event should match the produced event")
public void theConsumedEventShouldMatch() {
    ConsumedResult result = fetchConsumedEventWithRetry(
        testId, topic, correlationId, OrderEvent.class
    );

    assertTrue(result instanceof ConsumedSuccess, "Should consume successfully");

    ConsumedSuccess success = (ConsumedSuccess) result;
    CloudEvent key = (CloudEvent) success.key();
    OrderEvent value = (OrderEvent) success.value();

    // CloudEvent assertions
    assertEquals("OrderCreated", key.type(), "Event type should match");
    assertEquals("v1", key.payloadversion(), "Payload version should match");
    assertEquals(correlationId, key.correlationid(), "Correlation ID should match");

    // Payload assertions
    assertEquals("order-123", value.getOrderId(), "Order ID should match");
    assertEquals("cust-456", value.getCustomerId(), "Customer ID should match");
    assertEquals(99.99, value.getAmount(), 0.01, "Amount should match");
    assertEquals("USD", value.getCurrency(), "Currency should match");

    System.out.println("✅ All assertions passed");
}
```

### Custom Assertion Helpers

```java
public class EventAssertions {

    public static void assertCloudEventEquals(
            CloudEvent expected, CloudEvent actual, String message) {
        assertEquals(expected.type(), actual.type(), message + " - type");
        assertEquals(expected.correlationid(), actual.correlationid(), message + " - correlationId");
        assertEquals(expected.payloadversion(), actual.payloadversion(), message + " - version");
    }

    public static void assertOrderEventEquals(
            OrderEvent expected, OrderEvent actual, String message) {
        assertEquals(expected.getOrderId(), actual.getOrderId(), message + " - orderId");
        assertEquals(expected.getCustomerId(), actual.getCustomerId(), message + " - customerId");
        assertEquals(expected.getAmount(), actual.getAmount(), message + " - amount");
        assertEquals(expected.getCurrency(), actual.getCurrency(), message + " - currency");
    }
}
```

**Usage:**

```java
EventAssertions.assertOrderEventEquals(producedOrder, consumedOrder, "Order events");
```

---

## Best Practices

### 1. Always Retrieve Test ID from Context

```java
// ✅ Good - Uses framework-provided test ID
@Given("I have a test ID from the Cucumber context")
public void iHaveTestId() {
    this.testId = CucumberContext.getTestId();
    assertNotNull("Test ID should be available", testId);
}

// ❌ Bad - Generates own test ID (actors won't be registered)
@Given("I have a test ID")
public void iHaveTestId() {
    this.testId = UUID.randomUUID(); // WRONG
}
```

---

### 2. Use Deterministic Correlation IDs

```java
// ✅ Good - Deterministic from eventTestId (reproducible)
String correlationId = CloudEventFactory.getCorrelationIdForEventTestId("test-001");

// ❌ Bad - Random UUID (can't match produce/consume)
String correlationId = UUID.randomUUID().toString();
```

---

### 3. Implement Retry Logic for Consumers

```java
// ✅ Good - Handles eventual consistency
public <T> ConsumedResult fetchWithRetry(
        UUID testId, String topic, String correlationId, Class<T> valueClass) {
    int maxAttempts = 10;
    for (int i = 1; i <= maxAttempts; i++) {
        try {
            return ProbeJavaDsl.fetchConsumedEventBlocking(
                testId, topic, correlationId, valueClass
            );
        } catch (ConsumerNotAvailableException e) {
            if (i < maxAttempts) Thread.sleep(1000);
            else throw new AssertionError("Event not consumed after 10 attempts");
        }
    }
    throw new AssertionError("Unreachable");
}

// ❌ Bad - No retry, will fail on eventual consistency
ConsumedResult result = ProbeJavaDsl.fetchConsumedEventBlocking(...);
```

---

### 4. Use Type-Safe Event Construction

```java
// ✅ Good - Builder pattern with type safety
OrderEvent order = OrderEvent.newBuilder()
    .setOrderId("order-123")
    .setAmount(99.99)
    .build();

// ❌ Bad - Magic strings, error-prone
OrderEvent order = new OrderEvent();
order.setOrderId("order-123");
order.setAmount(99.99);
// Oops, forgot to set customerId - NPE later
```

---

### 5. Verify Result Types Before Casting

```java
// ✅ Good - Type check before cast
ConsumedResult result = ProbeJavaDsl.fetchConsumedEventBlocking(...);
if (result instanceof ConsumedSuccess) {
    ConsumedSuccess success = (ConsumedSuccess) result;
    OrderEvent event = (OrderEvent) success.value();
} else {
    fail("Expected ConsumedSuccess but got: " + result.getClass().getName());
}

// ❌ Bad - Blind cast, ClassCastException on failure
ConsumedSuccess success = (ConsumedSuccess) result; // May throw
```

---

### 6. Use Logging for Debugging

```java
@When("I produce the event to topic {string}")
public void iProduceEvent(String topic) {
    System.out.println("[STEP] Producing to topic: " + topic);
    System.out.println("[STEP] Key: correlationId=" + cloudEventKey.correlationid());
    System.out.println("[STEP] Value: " + orderPayload);

    ProduceResult result = ProbeJavaDsl.produceEventBlocking(
        testId, topic, cloudEventKey, orderPayload, Map.of(), OrderEvent.class
    );

    System.out.println("[STEP] ✅ Produced successfully!");
    assertTrue(result instanceof ProducingSuccess);
}
```

**Why:** Cucumber reports show step output, making it easier to debug failures.

---

## Complete Examples

### Example 1: Simple JSON Event Roundtrip

```java
package com.yourcompany.tests.steps;

import io.distia.probe.javaapi.ProbeJavaDsl;
import io.distia.probe.core.pubsub.models.*;
import io.distia.probe.core.services.cucumber.CucumberContext;
import com.yourcompany.tests.fixtures.CloudEventFactory;
import com.yourcompany.tests.models.TestEvent;
import io.cucumber.java.en.*;
import java.util.*;
import static org.junit.Assert.*;

public class SimpleJsonSteps {

    private UUID testId;
    private String correlationId;
    private CloudEvent cloudEventKey;
    private TestEvent testPayload;

    @Given("I have a test ID from the Cucumber context")
    public void iHaveTestId() {
        this.testId = CucumberContext.getTestId();
        assertNotNull("Test ID should be available", testId);
    }

    @Given("I create a test event with ID {string}")
    public void iCreateTestEvent(String eventTestId) {
        // Deterministic correlation ID
        this.correlationId = CloudEventFactory.getCorrelationIdForEventTestId(eventTestId);

        // Create CloudEvent key
        this.cloudEventKey = CloudEventFactory.create(
            "TestEvent",
            correlationId,
            "v1"
        );

        // Create JSON payload
        this.testPayload = new TestEvent(
            "TestEvent",
            "v1",
            eventTestId,
            System.currentTimeMillis(),
            "test-key-" + eventTestId,
            "Test payload for " + eventTestId
        );
    }

    @When("I produce the test event to topic {string}")
    public void iProduceTestEvent(String topic) {
        ProduceResult result = ProbeJavaDsl.produceEventBlocking(
            testId,
            topic,
            cloudEventKey,
            testPayload,
            Map.of("trace-id", "xyz-789"),
            TestEvent.class
        );

        assertTrue("Event should produce successfully",
            result instanceof ProducingSuccess);
    }

    @Then("I should receive the test event on topic {string}")
    public void iShouldReceiveTestEvent(String topic) {
        // Retry for eventual consistency
        ConsumedResult result = null;
        for (int i = 1; i <= 10; i++) {
            try {
                result = ProbeJavaDsl.fetchConsumedEventBlocking(
                    testId, topic, correlationId, TestEvent.class
                );
                break;
            } catch (ConsumerNotAvailableException e) {
                if (i < 10) Thread.sleep(1000);
                else fail("Event not consumed after 10 attempts");
            }
        }

        // Verify
        assertTrue(result instanceof ConsumedSuccess);
        ConsumedSuccess success = (ConsumedSuccess) result;
        TestEvent consumed = (TestEvent) success.value();

        assertEquals(testPayload.getEventTestId(), consumed.getEventTestId());
        assertEquals(testPayload.getPayload(), consumed.getPayload());
    }
}
```

**Feature File:**

```gherkin
Feature: Simple JSON event roundtrip

  Scenario: Produce and consume JSON event
    Given I have a test ID from the Cucumber context
    And I create a test event with ID "test-001"
    When I produce the test event to topic "test-events"
    Then I should receive the test event on topic "test-events"
```

---

### Example 2: Avro Order Processing

```java
package com.yourcompany.tests.steps;

import io.distia.probe.javaapi.ProbeJavaDsl;
import io.distia.probe.core.pubsub.models.*;
import io.distia.probe.core.services.cucumber.CucumberContext;
import com.yourcompany.tests.fixtures.CloudEventFactory;
import com.yourcompany.tests.models.OrderEvent;
import io.cucumber.java.en.*;
import java.util.*;
import static org.junit.Assert.*;

public class OrderProcessingSteps {

    private UUID testId;
    private String correlationId;
    private OrderEvent orderEvent;

    @Given("I have a test ID from the Cucumber context")
    public void iHaveTestId() {
        this.testId = CucumberContext.getTestId();
    }

    @Given("I create an order for customer {string} with amount {double}")
    public void iCreateOrder(String customerId, double amount) {
        this.correlationId = UUID.randomUUID().toString();

        this.orderEvent = OrderEvent.newBuilder()
            .setEventType("OrderCreated")
            .setOrderId("order-" + System.currentTimeMillis())
            .setCustomerId(customerId)
            .setAmount(amount)
            .setCurrency("USD")
            .setTimestamp(System.currentTimeMillis())
            .build();
    }

    @When("I submit the order to topic {string}")
    public void iSubmitOrder(String topic) {
        CloudEvent key = CloudEventFactory.create(
            "OrderCreated",
            correlationId,
            "v1"
        );

        ProduceResult result = ProbeJavaDsl.produceEventBlocking(
            testId, topic, key, orderEvent, Map.of(), OrderEvent.class
        );

        assertTrue(result instanceof ProducingSuccess);
        System.out.println("✅ Order submitted: " + orderEvent.getOrderId());
    }

    @Then("the order should be processed on topic {string}")
    public void theOrderShouldBeProcessed(String topic) {
        ConsumedResult result = fetchWithRetry(
            testId, topic, correlationId, OrderEvent.class
        );

        assertTrue(result instanceof ConsumedSuccess);
        ConsumedSuccess success = (ConsumedSuccess) result;
        OrderEvent processed = (OrderEvent) success.value();

        assertEquals(orderEvent.getOrderId(), processed.getOrderId());
        assertEquals(orderEvent.getCustomerId(), processed.getCustomerId());
        System.out.println("✅ Order processed: " + processed.getOrderId());
    }

    private <T> ConsumedResult fetchWithRetry(
            UUID testId, String topic, String correlationId, Class<T> valueClass) {
        for (int i = 1; i <= 10; i++) {
            try {
                return ProbeJavaDsl.fetchConsumedEventBlocking(
                    testId, topic, correlationId, valueClass
                );
            } catch (ConsumerNotAvailableException e) {
                if (i < 10) Thread.sleep(1000);
                else throw new AssertionError("Event not consumed after 10 attempts");
            }
        }
        throw new AssertionError("Unreachable");
    }
}
```

**Feature File:**

```gherkin
Feature: Order processing

  Scenario: Process customer order
    Given I have a test ID from the Cucumber context
    And I create an order for customer "cust-456" with amount 99.99
    When I submit the order to topic "orders"
    Then the order should be processed on topic "orders-processed"
```

---

## Related Documentation

- **Java DSL API Reference:** [docs/api/probe-java-dsl-api.md](../api/probe-java-dsl-api.md)
- **Java Event Models Guide:** [docs/user-guide/java-event-models.md](./java-event-models.md)
- **Module README:** [test-probe-java-api/README.md](../../test-probe-java-api/README.md)

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-26 | Initial release |
