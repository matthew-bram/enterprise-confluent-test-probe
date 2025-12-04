# Tutorial 2: Working with JSON Events

**Level:** Intermediate
**Duration:** 45 minutes
**Prerequisites:** [Tutorial 1: Your First Test](01-first-test.md)

---

## What You'll Learn

By the end of this tutorial, you will:

1. Understand JSON Schema basics and validation
2. Work with CloudEvent envelope structure
3. Register schemas in Confluent Schema Registry
4. Handle schema evolution scenarios
5. Implement proper error handling and validation
6. Use advanced Jackson annotations for JSON Schema generation

## Expected Outcome

You'll build a robust JSON event testing pipeline with schema validation, proper error handling, and support for schema evolution.

---

## JSON Schema Fundamentals

### What is JSON Schema?

JSON Schema is a vocabulary for validating JSON documents. Test-Probe uses **Confluent JSON Schema** which extends standard JSON Schema with schema registry integration.

**Benefits:**
- **Validation** - Ensure events match expected structure
- **Documentation** - Self-documenting event contracts
- **Evolution** - Manage schema changes over time
- **Compatibility** - Enforce backward/forward compatibility

---

## Step 1: Create a Rich Event Model

### 1.1 Define CustomerRegisteredEvent

Create `src/test/java/com/yourcompany/events/CustomerRegisteredEvent.java`:

```java
package com.yourcompany.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaDescription;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@JsonSchemaTitle("CustomerRegisteredEvent")
@JsonTypeName("CustomerRegisteredEvent")
@JsonSchemaDescription("Event emitted when a new customer completes registration")
public class CustomerRegisteredEvent {

    @NotNull
    @JsonProperty(required = true)
    @JsonSchemaDescription("Event type discriminator")
    private String eventType;

    @NotNull
    @JsonProperty(required = true)
    @JsonSchemaDescription("Schema version (semantic versioning)")
    private String eventVersion;

    @NotNull
    @Size(min = 1, max = 100)
    @JsonProperty(required = true)
    @JsonSchemaDescription("Unique customer identifier")
    private String customerId;

    @NotNull
    @Email
    @JsonProperty(required = true)
    @JsonSchemaDescription("Customer email address")
    private String email;

    @NotNull
    @Size(min = 2, max = 100)
    @JsonProperty(required = true)
    @JsonSchemaDescription("Customer full name")
    private String fullName;

    @JsonProperty
    @JsonSchemaDescription("Customer phone number (optional)")
    private String phoneNumber;

    @JsonProperty
    @JsonSchemaDescription("Registration source (WEB, MOBILE, API)")
    private String registrationSource;

    @NotNull
    @JsonProperty(required = true)
    @JsonSchemaDescription("Event timestamp in epoch milliseconds")
    private long timestamp;

    // Default constructor (required for Jackson)
    public CustomerRegisteredEvent() {}

    // Full constructor
    public CustomerRegisteredEvent(String eventType, String eventVersion,
                                  String customerId, String email,
                                  String fullName, String phoneNumber,
                                  String registrationSource, long timestamp) {
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.customerId = customerId;
        this.email = email;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.registrationSource = registrationSource;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventVersion() { return eventVersion; }
    public void setEventVersion(String eventVersion) { this.eventVersion = eventVersion; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getRegistrationSource() { return registrationSource; }
    public void setRegistrationSource(String registrationSource) {
        this.registrationSource = registrationSource;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "CustomerRegisteredEvent{" +
                "eventType='" + eventType + '\'' +
                ", eventVersion='" + eventVersion + '\'' +
                ", customerId='" + customerId + '\'' +
                ", email='" + email + '\'' +
                ", fullName='" + fullName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", registrationSource='" + registrationSource + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
```

**Advanced Annotations:**
- `@JsonSchemaDescription` - Adds human-readable documentation to schema
- `@JsonProperty(required = true)` - Makes field mandatory in JSON Schema
- `@NotNull` - JSR-303 validation (runtime validation)
- `@Email` - Email format validation
- `@Size` - String length constraints

---

## Step 2: Understanding CloudEvent Structure

### 2.1 CloudEvent Key Fields

Every Kafka message in Test-Probe has a **CloudEvent key**:

```java
CloudEvent {
  id: "550e8400-e29b-41d4-a716-446655440000"      // Unique event ID
  source: "test-probe"                            // Event source system
  specversion: "1.0"                              // CloudEvents spec version
  type: "CustomerRegisteredEvent"                 // Event type
  time: "2025-11-26T10:30:00Z"                   // ISO-8601 timestamp
  subject: "customer-12345"                       // Event subject (entity ID)
  datacontenttype: "application/octet-stream"     // Kafka binary format
  correlationid: "7c9e6679-7425-40de-944b"       // Correlation ID (for fetch)
  payloadversion: "v1"                            // Payload schema version
  time_epoch_micro_source: 1732618200000000      // Epoch microseconds
}
```

### 2.2 Key vs Value in Kafka Messages

```
Kafka Message Structure:
┌──────────────────────────────────────────────────────────────┐
│ KEY: CloudEvent (envelope)                                   │
│   - correlationId: "abc-123"                                 │
│   - type: "CustomerRegisteredEvent"                          │
│   - payloadversion: "v1"                                     │
├──────────────────────────────────────────────────────────────┤
│ VALUE: CustomerRegisteredEvent (business payload)            │
│   - customerId: "customer-12345"                             │
│   - email: "john.doe@example.com"                            │
│   - fullName: "John Doe"                                     │
│   ...                                                        │
└──────────────────────────────────────────────────────────────┘
```

**Why Two Schemas?**
- **Key (CloudEvent)** - Routing, correlation, metadata
- **Value (Payload)** - Business data, domain-specific fields

---

## Step 3: Schema Registration

### 3.1 Automatic Schema Registration (Development)

Test-Probe automatically registers schemas on first produce:

```java
// When you call produceEventBlocking:
ProduceResult result = IntegrationTestDsl.produceEventBlocking(
    testId,
    "customers-registered",
    cloudEventKey,
    customerEvent,
    CustomerRegisteredEvent.class  // Schema auto-registered
);

// Schema Registry now has two subjects:
// 1. customers-registered-CloudEvent (key schema)
// 2. customers-registered-CustomerRegisteredEvent (value schema)
```

**Subject Naming Convention:**
```
{topic}-{className}

Topic: customers-registered
Class: CustomerRegisteredEvent
Subject: customers-registered-CustomerRegisteredEvent
```

### 3.2 Manual Schema Registration (Production)

For production environments, pre-register schemas:

**Step 1: Generate JSON Schema**

```java
// Use Test-Probe schema generator utility
import io.distia.probe.core.pubsub.SchemaGenerator;

String schema = SchemaGenerator.generateJsonSchema(CustomerRegisteredEvent.class);
System.out.println(schema);
```

**Output:**
```json
{
  "$schema": "http://json-schema.org/draft-2020-12/schema#",
  "title": "CustomerRegisteredEvent",
  "description": "Event emitted when a new customer completes registration",
  "type": "object",
  "properties": {
    "eventType": {
      "type": "string",
      "description": "Event type discriminator"
    },
    "eventVersion": {
      "type": "string",
      "description": "Schema version (semantic versioning)"
    },
    "customerId": {
      "type": "string",
      "minLength": 1,
      "maxLength": 100,
      "description": "Unique customer identifier"
    },
    "email": {
      "type": "string",
      "format": "email",
      "description": "Customer email address"
    },
    "fullName": {
      "type": "string",
      "minLength": 2,
      "maxLength": 100,
      "description": "Customer full name"
    },
    "phoneNumber": {
      "type": "string",
      "description": "Customer phone number (optional)"
    },
    "registrationSource": {
      "type": "string",
      "description": "Registration source (WEB, MOBILE, API)"
    },
    "timestamp": {
      "type": "integer",
      "description": "Event timestamp in epoch milliseconds"
    }
  },
  "required": ["eventType", "eventVersion", "customerId", "email", "fullName", "timestamp"]
}
```

**Step 2: Register via REST API**

```bash
# Register schema in Schema Registry
curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data '{
    "schema": "{\"$schema\": \"http://json-schema.org/draft-2020-12/schema#\", ...}",
    "schemaType": "JSON"
  }' \
  "http://localhost:8081/subjects/customers-registered-CustomerRegisteredEvent/versions"
```

---

## Step 4: Schema Evolution

### 4.1 Backward Compatible Change (Add Optional Field)

**Version 1 (Original):**
```java
public class CustomerRegisteredEvent {
    private String customerId;
    private String email;
    private String fullName;
    // ...
}
```

**Version 2 (Add Optional Field):**
```java
public class CustomerRegisteredEvent {
    private String customerId;
    private String email;
    private String fullName;

    @JsonProperty  // Optional field (no "required = true")
    @JsonSchemaDescription("Customer loyalty tier (BRONZE, SILVER, GOLD)")
    private String loyaltyTier;  // NEW FIELD - backward compatible

    // ...
}
```

**Why Backward Compatible?**
- Old consumers can still read new events (ignore `loyaltyTier`)
- New consumers can handle old events (`loyaltyTier` will be null)

### 4.2 Breaking Change (Remove Required Field)

**Version 1:**
```java
@NotNull
@JsonProperty(required = true)
private String email;
```

**Version 2 (BREAKING):**
```java
// Email field removed - BREAKS old consumers expecting this field!
```

**How to Handle:**
1. Use a new event type: `CustomerRegisteredEventV2`
2. Or bump `payloadversion` to `"v2"` and handle both versions
3. Deploy consumer updates BEFORE producer updates

### 4.3 Schema Compatibility Modes

Configure in Schema Registry:

```bash
# Set compatibility mode for subject
curl -X PUT -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data '{"compatibility": "BACKWARD"}' \
  "http://localhost:8081/config/customers-registered-CustomerRegisteredEvent"
```

**Compatibility Modes:**
- `BACKWARD` - New schema can read old data (add optional fields)
- `FORWARD` - Old schema can read new data (remove optional fields)
- `FULL` - Both backward and forward compatible
- `NONE` - No compatibility checks (use with caution!)

---

## Step 5: Advanced Error Handling

### 5.1 Validation Errors

```java
package com.yourcompany.steps;

import io.distia.probe.core.pubsub.models.KafkaProduceException;
import io.distia.probe.core.pubsub.models.ConsumerNotAvailableException;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;

import static org.junit.Assert.*;

public class CustomerRegisteredSteps {

    @When("I produce an invalid CustomerRegistered event")
    public void iProduceAnInvalidCustomerRegisteredEvent() {
        // Create event with missing required field
        CustomerRegisteredEvent invalidEvent = new CustomerRegisteredEvent();
        invalidEvent.setEventType("CustomerRegisteredEvent");
        // Missing customerId, email, fullName - should fail validation

        try {
            IntegrationTestDsl.produceEventBlocking(
                testId,
                "customers-registered",
                cloudEventKey,
                invalidEvent,
                CustomerRegisteredEvent.class
            );
            fail("Should have thrown validation exception");

        } catch (KafkaProduceException e) {
            // Expected - schema validation failed
            assertTrue("Should mention validation",
                       e.getMessage().contains("validation"));
            System.out.println("Validation error caught: " + e.getMessage());
        }
    }

    @Then("I should receive a clear validation error message")
    public void iShouldReceiveAClearValidationErrorMessage() {
        // Verified in previous step's catch block
        System.out.println("Validation error handling complete");
    }
}
```

### 5.2 Schema Not Found Errors

```java
@When("I consume from a topic with no registered schema")
public void iConsumeFromATopicWithNoRegisteredSchema() {
    try {
        IntegrationTestDsl.fetchConsumedEventBlocking(
            testId,
            "nonexistent-topic",
            correlationId,
            CustomerRegisteredEvent.class
        );
        fail("Should have thrown SchemaNotFoundException");

    } catch (Exception e) {
        assertTrue("Should be consumer not available",
                   e instanceof ConsumerNotAvailableException);
        System.out.println("Expected error: " + e.getMessage());
    }
}
```

### 5.3 Retry with Exponential Backoff

```java
public ConsumedResult fetchWithBackoff(UUID testId, String topic,
                                      String correlationId, int maxRetries) {
    int baseDelay = 500;  // Start with 500ms

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            return IntegrationTestDsl.fetchConsumedEventBlocking(
                testId, topic, correlationId, CustomerRegisteredEvent.class
            );

        } catch (ConsumerNotAvailableException e) {
            if (attempt == maxRetries) {
                throw e;  // Give up after max retries
            }

            // Exponential backoff: 500ms, 1s, 2s, 4s, 8s...
            int delay = baseDelay * (1 << (attempt - 1));
            System.out.println("Retry " + attempt + "/" + maxRetries +
                             " in " + delay + "ms");

            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during backoff", ie);
            }
        }
    }

    throw new IllegalStateException("Should not reach here");
}
```

---

## Step 6: Complete Feature File Example

Create `src/test/resources/features/CustomerRegistration.feature`:

```gherkin
Feature: Customer Registration Event Flow
  As a customer service platform
  I want to emit CustomerRegistered events to Kafka
  So that downstream systems can onboard new customers

  Background: Schema Registry
    Given the Schema Registry is running
    And CloudEvent schema is registered
    And CustomerRegisteredEvent v1 schema is registered

  Scenario: Successful customer registration
    Given I have a CustomerRegistered event with:
      | customerId         | cust-12345              |
      | email              | john.doe@example.com    |
      | fullName           | John Doe                |
      | phoneNumber        | +1-555-1234             |
      | registrationSource | WEB                     |
    When I produce the event to topic "customers-registered"
    Then the event should be produced successfully
    And I should be able to consume the event
    And the consumed event should match all fields

  Scenario: Registration with minimal fields (optional fields omitted)
    Given I have a CustomerRegistered event with:
      | customerId         | cust-67890              |
      | email              | jane.smith@example.com  |
      | fullName           | Jane Smith              |
    When I produce the event to topic "customers-registered"
    Then the event should be produced successfully
    And the consumed event should have null phoneNumber
    And the consumed event should have null registrationSource

  Scenario: Invalid email format
    Given I have a CustomerRegistered event with invalid email "not-an-email"
    When I attempt to produce the event to topic "customers-registered"
    Then I should receive a JSON Schema validation error
    And the error message should mention "email format"

  Scenario: Schema evolution - v2 with loyalty tier
    Given CustomerRegisteredEvent v2 schema is registered
    And I have a CustomerRegistered v2 event with loyaltyTier "GOLD"
    When I produce the v2 event to topic "customers-registered"
    Then a v1 consumer should still be able to read the event
    And the v1 consumer should ignore the loyaltyTier field
```

---

## Summary

Congratulations! You've mastered JSON events in Test-Probe. You learned:

- JSON Schema fundamentals and validation
- CloudEvent envelope structure (key vs value)
- Automatic and manual schema registration
- Schema evolution strategies (backward/forward compatibility)
- Advanced error handling with retries and backoff
- Jackson annotations for rich schema documentation

---

## Next Steps

Continue your Test-Probe journey:

1. **[Tutorial 3: Multi-Format Serialization](03-avro-protobuf.md)** - Learn Avro and Protobuf for high-performance scenarios
2. **[Tutorial 4: Cross-Datacenter Testing](04-multi-cluster.md)** - Test event propagation across Kafka clusters
3. **[Tutorial 5: Evidence Generation](05-evidence-generation.md)** - Audit compliance and evidence collection

---

## Additional Resources

- **[SerdesFactory API](../../api/serdes-factory-api.md)** - Serialization internals
- **[JSON Schema Specification](https://json-schema.org/)** - JSON Schema standard
- **[Confluent Schema Registry Docs](https://docs.confluent.io/platform/current/schema-registry/)** - Schema Registry guide
- **[CloudEvents Spec](https://cloudevents.io/)** - CloudEvent standard

---

**Document Version:** 1.0.0
**Last Updated:** 2025-11-26
**Tested With:** test-probe-core 1.0.0, Java 11
