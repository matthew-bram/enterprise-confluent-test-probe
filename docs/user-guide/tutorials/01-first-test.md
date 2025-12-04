# Tutorial 1: Your First Test

**Level:** Basic
**Duration:** 30 minutes
**Prerequisites:** Java 11+, Maven 3.8+, Docker Desktop

---

## What You'll Learn

By the end of this tutorial, you will:

1. Set up a local Kafka environment using Testcontainers
2. Create a simple Gherkin feature file
3. Write Java step definitions using Test-Probe
4. Produce and consume a JSON event
5. Run your test and verify results

## Expected Outcome

You'll have a working end-to-end test that produces a JSON event to Kafka, consumes it back, and validates the payload matches.

---

## Prerequisites Check

Before starting, verify you have the required tools:

```bash
# Check Java version (11+ required)
java -version

# Check Maven version (3.8+ required)
mvn --version

# Check Docker is running
docker ps

# Verify you can pull Testcontainers images
docker pull confluentinc/cp-kafka:7.5.0
```

---

## Step 1: Project Setup

### 1.1 Add Test-Probe Dependency

Add the Test-Probe framework to your `pom.xml`:

```xml
<dependencies>
    <!-- Test-Probe Core -->
    <dependency>
        <groupId>io.distia.probe</groupId>
        <artifactId>test-probe-core</artifactId>
        <version>1.0.0</version>
        <scope>test</scope>
    </dependency>

    <!-- Cucumber for BDD -->
    <dependency>
        <groupId>io.cucumber</groupId>
        <artifactId>cucumber-java</artifactId>
        <version>7.14.0</version>
        <scope>test</scope>
    </dependency>

    <!-- JUnit 4 (Cucumber compatible) -->
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <version>4.13.2</version>
        <scope>test</scope>
    </dependency>

    <!-- Jackson for JSON serialization -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.15.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 1.2 Configure Testcontainers

Test-Probe uses Testcontainers to manage Kafka infrastructure. No additional configuration needed - it's automatic!

---

## Step 2: Create Your Event Model

### 2.1 Define the TestEvent POJO

Create `src/test/java/com/yourcompany/events/OrderCreatedEvent.java`:

```java
package com.yourcompany.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;

@JsonSchemaTitle("OrderCreatedEvent")
@JsonTypeName("OrderCreatedEvent")
public class OrderCreatedEvent {

    private String eventType;
    private String eventVersion;
    private String orderId;
    private String customerId;
    private double amount;
    private long timestamp;

    // Default constructor (required for Jackson)
    public OrderCreatedEvent() {}

    // Full constructor
    public OrderCreatedEvent(String eventType, String eventVersion,
                            String orderId, String customerId,
                            double amount, long timestamp) {
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventVersion() { return eventVersion; }
    public void setEventVersion(String eventVersion) { this.eventVersion = eventVersion; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "OrderCreatedEvent{" +
                "eventType='" + eventType + '\'' +
                ", eventVersion='" + eventVersion + '\'' +
                ", orderId='" + orderId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", amount=" + amount +
                ", timestamp=" + timestamp +
                '}';
    }
}
```

**Key Points:**
- `@JsonSchemaTitle` - Required for Schema Registry registration
- `@JsonTypeName` - Identifies the event type in polymorphic scenarios
- Default constructor - Required for Jackson deserialization
- Getters/setters - Standard JavaBean pattern

---

## Step 3: Write Your First Feature File

### 3.1 Create the Gherkin Specification

Create `src/test/resources/features/FirstTest.feature`:

```gherkin
Feature: First Order Event Test
  As a developer learning Test-Probe
  I want to produce and consume an OrderCreated event
  So that I can validate my Kafka integration works

  Background: Test Environment
    Given the test environment is initialized
    And Kafka and Schema Registry are running

  Scenario: Produce and consume OrderCreated event
    Given I have an OrderCreated event with orderId "order-123"
    When I produce the event to topic "orders-created"
    Then I should be able to consume the event from topic "orders-created"
    And the consumed event should match the produced event
```

**Gherkin Best Practices:**
- Use business language (not technical jargon)
- Each scenario should be independent
- Background steps set up common preconditions

---

## Step 4: Write Java Step Definitions

### 4.1 Create Step Definition Class

Create `src/test/java/com/yourcompany/steps/OrderCreatedSteps.java`:

```java
package com.yourcompany.steps;

import io.distia.probe.core.pubsub.models.CloudEvent;
import io.distia.probe.core.pubsub.models.ConsumedResult;
import io.distia.probe.core.pubsub.models.ConsumedSuccess;
import io.distia.probe.core.pubsub.models.ProduceResult;
import io.distia.probe.core.pubsub.models.ProducingSuccess;
import io.distia.probe.core.services.cucumber.CucumberContext;
import io.distia.probe.core.testutil.CloudEventFactory;
import io.distia.probe.core.testutil.IntegrationTestDsl;
import com.yourcompany.events.OrderCreatedEvent;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.And;

import java.util.UUID;

import static org.junit.Assert.*;

public class OrderCreatedSteps {

    // Test context - holds state between steps
    private UUID testId;
    private CloudEvent cloudEventKey;
    private OrderCreatedEvent orderEvent;
    private String currentTopic;
    private String correlationId;
    private ConsumedSuccess consumedResult;

    @Given("the test environment is initialized")
    public void theTestEnvironmentIsInitialized() {
        // Get test ID from Cucumber context (injected by framework)
        this.testId = CucumberContext.getTestId();
        assertNotNull("Test ID should be available", testId);
        System.out.println("Test initialized with ID: " + testId);
    }

    @And("Kafka and Schema Registry are running")
    public void kafkaAndSchemaRegistryAreRunning() {
        // Testcontainers handles this automatically
        // Schema registration happens automatically on first produce
        System.out.println("Kafka infrastructure ready");
    }

    @Given("I have an OrderCreated event with orderId {string}")
    public void iHaveAnOrderCreatedEventWithOrderId(String orderId) {
        // Create correlation ID for event lookup
        this.correlationId = UUID.randomUUID().toString();

        // Create CloudEvent key (standard envelope)
        this.cloudEventKey = CloudEventFactory.createWithCorrelationId(
            correlationId,
            "OrderCreatedEvent",  // Event type
            "v1"                  // Event version
        );

        // Create OrderCreated payload
        this.orderEvent = new OrderCreatedEvent(
            "OrderCreatedEvent",
            "v1",
            orderId,
            "customer-456",
            99.99,
            System.currentTimeMillis()
        );

        System.out.println("Created OrderCreated event: " + orderEvent);
    }

    @When("I produce the event to topic {string}")
    public void iProduceTheEventToTopic(String topic) {
        this.currentTopic = topic;

        System.out.println("Producing event to topic: " + topic);
        System.out.println("CloudEvent key correlationId: " + correlationId);

        // Produce event using Test-Probe DSL
        ProduceResult result = IntegrationTestDsl.produceEventBlocking(
            testId,
            topic,
            cloudEventKey,
            orderEvent,
            OrderCreatedEvent.class
        );

        // Verify production succeeded
        assertTrue("Event production should succeed",
                   result instanceof ProducingSuccess);

        System.out.println("Event produced successfully");
    }

    @Then("I should be able to consume the event from topic {string}")
    public void iShouldBeAbleToConsumeTheEventFromTopic(String topic) {
        System.out.println("Consuming event from topic: " + topic);

        // Retry loop for eventual consistency
        int maxAttempts = 10;
        ConsumedResult result = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                result = IntegrationTestDsl.fetchConsumedEventBlocking(
                    testId,
                    topic,
                    correlationId,
                    OrderCreatedEvent.class
                );

                // Success - break out of retry loop
                System.out.println("Event consumed on attempt " + attempt);
                break;

            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    System.out.println("Event not ready, retrying in 1s...");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        fail("Interrupted while waiting for event");
                    }
                } else {
                    fail("Event consumption failed after " + maxAttempts +
                         " attempts: " + e.getMessage());
                }
            }
        }

        // Verify consumption succeeded
        assertNotNull("Event should be consumed", result);
        assertTrue("Expected ConsumedSuccess", result instanceof ConsumedSuccess);

        this.consumedResult = (ConsumedSuccess) result;
        System.out.println("Event consumed successfully");
    }

    @And("the consumed event should match the produced event")
    public void theConsumedEventShouldMatchTheProducedEvent() {
        assertNotNull("Consumed result should not be null", consumedResult);

        // Verify CloudEvent key
        CloudEvent consumedKey = (CloudEvent) consumedResult.key();
        assertEquals("Event type should match",
                     cloudEventKey.type(), consumedKey.type());
        assertEquals("Correlation ID should match",
                     cloudEventKey.correlationid(), consumedKey.correlationid());

        // Verify OrderCreated payload
        OrderCreatedEvent consumed = (OrderCreatedEvent) consumedResult.value();
        assertEquals("Event type should match",
                     orderEvent.getEventType(), consumed.getEventType());
        assertEquals("Event version should match",
                     orderEvent.getEventVersion(), consumed.getEventVersion());
        assertEquals("Order ID should match",
                     orderEvent.getOrderId(), consumed.getOrderId());
        assertEquals("Customer ID should match",
                     orderEvent.getCustomerId(), consumed.getCustomerId());
        assertEquals("Amount should match",
                     orderEvent.getAmount(), consumed.getAmount(), 0.01);

        System.out.println("Event verification complete - all fields match!");
    }
}
```

**Key Concepts:**
- **CloudEvent** - Standard envelope for all Kafka keys (contains correlationId)
- **IntegrationTestDsl** - Java-friendly wrapper around ProbeScalaDsl
- **CucumberContext** - Framework-injected test ID
- **Retry Logic** - Events are consumed asynchronously, retry with backoff
- **Type Safety** - Pass `OrderCreatedEvent.class` for serialization

---

## Step 5: Run Your Test

### 5.1 Execute with Maven

```bash
# Run the specific feature file
mvn test -Dtest=CucumberRunner -Dcucumber.filter.tags="@FirstTest"

# Or run all tests
mvn test
```

### 5.2 Expected Output

```
[INFO] Running com.yourcompany.CucumberRunner
Test initialized with ID: 550e8400-e29b-41d4-a716-446655440000
Kafka infrastructure ready
Created OrderCreated event: OrderCreatedEvent{eventType='OrderCreatedEvent', ...}
Producing event to topic: orders-created
CloudEvent key correlationId: 7c9e6679-7425-40de-944b-e07fc1f90ae7
Event produced successfully
Consuming event from topic: orders-created
Event consumed on attempt 1
Event verification complete - all fields match!

1 Scenarios (1 passed)
4 Steps (4 passed)
0m5.234s

[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## Troubleshooting Common Issues

### Issue 1: Docker Not Running

**Symptom:**
```
org.testcontainers.containers.ContainerLaunchException:
  Could not create/start container
```

**Fix:**
```bash
# Start Docker Desktop
# Verify Docker is running
docker ps
```

---

### Issue 2: Schema Registration Fails

**Symptom:**
```
io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException:
  Subject not found
```

**Fix:**
- Ensure `@JsonSchemaTitle` annotation is present on your event class
- Verify Schema Registry Testcontainer is running
- Check Schema Registry logs: `docker logs <container-id>`

---

### Issue 3: Event Not Consumed (Timeout)

**Symptom:**
```
Event consumption failed after 10 attempts: ConsumerNotAvailableException
```

**Fix:**
- Increase retry attempts (change `maxAttempts` to 20)
- Increase retry delay (change `Thread.sleep(1000)` to `Thread.sleep(2000)`)
- Check Kafka topic exists: View Kafka logs or use Kafka tools

---

### Issue 4: Jackson Deserialization Error

**Symptom:**
```
com.fasterxml.jackson.databind.exc.InvalidDefinitionException:
  Cannot construct instance of OrderCreatedEvent (no Creators, like default constructor, exist)
```

**Fix:**
- Add default no-args constructor to your event class
- Ensure all fields have getters and setters

---

### Issue 5: Port Conflicts

**Symptom:**
```
BindException: Address already in use
```

**Fix:**
```bash
# Find processes using common ports
lsof -i :9092  # Kafka
lsof -i :8081  # Schema Registry

# Kill conflicting processes or stop local Kafka
```

---

## Summary

Congratulations! You've completed your first Test-Probe test. You learned how to:

- Set up Testcontainers for local Kafka infrastructure
- Create a JSON event model with Jackson annotations
- Write Gherkin specifications for event-driven tests
- Use IntegrationTestDsl to produce and consume events
- Handle eventual consistency with retry logic
- Verify CloudEvent keys and event payloads

---

## Next Steps

Ready to level up? Try these follow-on tutorials:

1. **[Tutorial 2: Working with JSON Events](02-json-events.md)** - Learn JSON Schema validation, schema evolution, and advanced patterns
2. **[Tutorial 3: Multi-Format Serialization](03-avro-protobuf.md)** - Add Avro and Protobuf support for high-performance scenarios
3. **[Tutorial 4: Cross-Datacenter Testing](04-multi-cluster.md)** - Test event propagation across multiple Kafka clusters

---

## Additional Resources

- **[ProbeScalaDsl API Reference](../../api/probe-scala-dsl-api.md)** - Complete API documentation
- **[SerdesFactory API Reference](../../api/serdes-factory-api.md)** - Serialization details
- **[CloudEvent Specification](https://cloudevents.io/)** - Industry standard event envelope

---

**Document Version:** 1.0.0
**Last Updated:** 2025-11-26
**Tested With:** test-probe-core 1.0.0, Java 11, Maven 3.8.6
