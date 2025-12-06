# Tutorial 3: Avro and Protobuf Serialization

**Level:** Advanced
**Duration:** 30 minutes
**Prerequisites:** [Tutorial 2: Working with JSON Events](02-json-events.md)

---

## What You'll Learn

By the end of this tutorial, you will:

1. Understand when to use Avro vs Protobuf vs JSON
2. Configure serialization formats in test-config.yaml
3. Write Gherkin tests for Avro-serialized events
4. Write Gherkin tests for Protobuf-serialized events
5. Handle schema evolution scenarios

## Expected Outcome

You'll write tests that validate events using binary serialization formats (Avro and Protobuf) commonly used in production Kafka deployments.

---

## Prerequisites

Before starting, ensure:

- Completed [Tutorial 1](01-first-test.md) and [Tutorial 2](02-json-events.md)
- Access to Test-Probe service
- **Schemas preloaded in Schema Registry** (see [Assumptions](#schema-registry-assumptions))

---

## When to Use Each Format

| Format | Best For | Payload Size | Performance |
|--------|----------|--------------|-------------|
| **JSON** | Development, debugging, human-readable events | Larger | Slower |
| **Avro** | Big data pipelines, Kafka Connect, JVM ecosystems | Compact | Fast |
| **Protobuf** | Microservices, gRPC, polyglot environments | Most compact | Fastest |

**Recommendations:**
- **Development/Testing:** JSON (human-readable)
- **Data Pipelines:** Avro (Kafka ecosystem standard)
- **High-Performance Services:** Protobuf (smallest payload, fastest)

---

## Schema Registry Assumptions

Test-Probe assumes schemas are **preloaded** in Schema Registry before test execution. This aligns with enterprise practices where schema lifecycle is managed separately from testing.

```
Schema Lifecycle (External to Test-Probe):
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Schema Design  │───▶│  CI/CD Deploy   │───▶│ Schema Registry │
│  (Avro/Proto)   │    │  (schema-registry)   │    │  (Schemas Live) │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                       │
                                                       ▼
                                              ┌─────────────────┐
                                              │   Test-Probe    │
                                              │  (Uses Schemas) │
                                              └─────────────────┘
```

If you need to verify schema existence, use the Schema Registry REST API:

```bash
# List all subjects
curl https://schema-registry.example.com/subjects

# Get specific schema
curl https://schema-registry.example.com/subjects/orders-value/versions/latest
```

---

## Part 1: Avro Serialization

### Step 1.1: Configure Avro Format

Create `test-config.yaml`:

```yaml
test-probe:
  kafka:
    cluster: default

  serialization:
    key-format: cloudevents   # CloudEvents key (always)
    value-format: avro        # Avro for event values

  schema-registry:
    # Schema Registry is configured at the service level
    # Schemas must be pre-registered before running tests
    auto-register: false

  test:
    timeout-seconds: 60
```

### Step 1.2: Write Avro Event Tests

Create `features/avro-orders.feature`:

```gherkin
Feature: Avro Order Events
  Test order processing with Avro serialization

  Background:
    Given I have a topic "orders-avro"
    And the topic uses Avro serialization

  Scenario: Produce and consume Avro order event
    When I produce an Avro event with schema "OrderEvent" to topic "orders-avro":
      """
      {
        "orderId": "ORD-AVRO-001",
        "customerId": "CUST-100",
        "amount": 199.99,
        "currency": "USD",
        "items": [
          {
            "productId": "PROD-A",
            "quantity": 2,
            "price": 99.99
          }
        ],
        "timestamp": 1733407800000
      }
      """
    Then I should receive an Avro event from topic "orders-avro" within 15 seconds
    And the event should match JSONPath "$.orderId" with value "ORD-AVRO-001"
    And the event should match JSONPath "$.amount" with value 199.99
    And the event should match JSONPath "$.items[0].productId" with value "PROD-A"
```

### Step 1.3: Avro Schema Reference

The test above assumes this schema exists in Schema Registry:

```json
{
  "type": "record",
  "name": "OrderEvent",
  "namespace": "com.example.events",
  "fields": [
    {"name": "orderId", "type": "string"},
    {"name": "customerId", "type": "string"},
    {"name": "amount", "type": "double"},
    {"name": "currency", "type": ["null", "string"], "default": null},
    {"name": "items", "type": {
      "type": "array",
      "items": {
        "type": "record",
        "name": "OrderItem",
        "fields": [
          {"name": "productId", "type": "string"},
          {"name": "quantity", "type": "int"},
          {"name": "price", "type": "double"}
        ]
      }
    }},
    {"name": "timestamp", "type": ["null", "long"], "default": null}
  ]
}
```

### Step 1.4: Advanced Avro Scenarios

```gherkin
  Scenario: Avro event with optional fields
    When I produce an Avro event with schema "OrderEvent" to topic "orders-avro":
      """
      {
        "orderId": "ORD-AVRO-002",
        "customerId": "CUST-200",
        "amount": 49.99,
        "currency": null,
        "items": [],
        "timestamp": null
      }
      """
    Then I should receive an Avro event from topic "orders-avro" within 15 seconds
    And the event should match JSONPath "$.orderId" with value "ORD-AVRO-002"
    # Optional fields can be null
    And the event should not have JSONPath "$.currency"
    And the event should match JSONPath "$.items.length()" with value 0

  Scenario: Multiple Avro events
    When I produce the following Avro events with schema "OrderEvent" to topic "orders-avro":
      | orderId       | customerId | amount  |
      | ORD-BATCH-001 | CUST-A     | 100.00  |
      | ORD-BATCH-002 | CUST-B     | 200.00  |
      | ORD-BATCH-003 | CUST-C     | 300.00  |
    Then I should receive 3 Avro events from topic "orders-avro" within 30 seconds
```

---

## Part 2: Protobuf Serialization

### Step 2.1: Configure Protobuf Format

Create `test-config.yaml`:

```yaml
test-probe:
  kafka:
    cluster: default

  serialization:
    key-format: cloudevents   # CloudEvents key (always)
    value-format: protobuf    # Protobuf for event values

  schema-registry:
    auto-register: false

  test:
    timeout-seconds: 60
```

### Step 2.2: Write Protobuf Event Tests

Create `features/protobuf-payments.feature`:

```gherkin
Feature: Protobuf Payment Events
  Test payment processing with Protobuf serialization

  Background:
    Given I have a topic "payments-proto"
    And the topic uses Protobuf serialization

  Scenario: Produce and consume Protobuf payment event
    When I produce a Protobuf event with schema "PaymentEvent" to topic "payments-proto":
      """
      {
        "paymentId": "PAY-PROTO-001",
        "orderId": "ORD-789",
        "amount": 299.99,
        "currency": "USD",
        "method": "CREDIT_CARD",
        "status": "AUTHORIZED",
        "metadata": {
          "ipAddress": "192.168.1.100",
          "userAgent": "Mozilla/5.0",
          "countryCode": "US"
        }
      }
      """
    Then I should receive a Protobuf event from topic "payments-proto" within 15 seconds
    And the event should match JSONPath "$.paymentId" with value "PAY-PROTO-001"
    And the event should match JSONPath "$.status" with value "AUTHORIZED"
    And the event should match JSONPath "$.metadata.countryCode" with value "US"
```

### Step 2.3: Protobuf Schema Reference

The test above assumes this schema exists in Schema Registry:

```protobuf
syntax = "proto3";

package com.example.events;

message PaymentEvent {
  string payment_id = 1;
  string order_id = 2;
  double amount = 3;
  string currency = 4;
  PaymentMethod method = 5;
  string status = 6;
  PaymentMetadata metadata = 7;

  enum PaymentMethod {
    UNKNOWN = 0;
    CREDIT_CARD = 1;
    DEBIT_CARD = 2;
    PAYPAL = 3;
    BANK_TRANSFER = 4;
  }

  message PaymentMetadata {
    string ip_address = 1;
    string user_agent = 2;
    string country_code = 3;
  }
}
```

### Step 2.4: Protobuf Enum Handling

```gherkin
  Scenario: Validate Protobuf enum values
    When I produce a Protobuf event with schema "PaymentEvent" to topic "payments-proto":
      """
      {
        "paymentId": "PAY-PROTO-002",
        "orderId": "ORD-456",
        "amount": 150.00,
        "currency": "EUR",
        "method": "PAYPAL",
        "status": "COMPLETED"
      }
      """
    Then I should receive a Protobuf event from topic "payments-proto" within 15 seconds
    # Enum values are returned as strings
    And the event should match JSONPath "$.method" with value "PAYPAL"

  Scenario: Protobuf with default values
    When I produce a Protobuf event with schema "PaymentEvent" to topic "payments-proto":
      """
      {
        "paymentId": "PAY-PROTO-003",
        "orderId": "ORD-999",
        "amount": 0.0,
        "currency": "",
        "method": "UNKNOWN",
        "status": "PENDING"
      }
      """
    Then I should receive a Protobuf event from topic "payments-proto" within 15 seconds
    # Proto3 default values (0, empty string, first enum)
    And the event should match JSONPath "$.amount" with value 0.0
    And the event should match JSONPath "$.method" with value "UNKNOWN"
```

---

## Part 3: Mixed Format Testing

### Step 3.1: Per-Topic Serialization

You can test systems that use different serialization formats per topic:

```yaml
# test-config.yaml
test-probe:
  kafka:
    cluster: default

  topics:
    # JSON topic
    - name: orders-json
      serialization:
        value-format: json

    # Avro topic
    - name: orders-avro
      serialization:
        value-format: avro
        schema: OrderEvent

    # Protobuf topic
    - name: payments-proto
      serialization:
        value-format: protobuf
        schema: PaymentEvent
```

### Step 3.2: Cross-Format Flow Test

```gherkin
Feature: Cross-Format Event Flow
  Test event flow across different serialization formats

  Scenario: Order creates payment across formats
    # Step 1: Create order (JSON)
    Given I have a topic "orders-json"
    When I produce a JSON event to topic "orders-json":
      """
      {
        "orderId": "ORD-CROSS-001",
        "customerId": "CUST-500",
        "amount": 399.99
      }
      """
    Then I should receive an event from topic "orders-json" within 10 seconds

    # Step 2: Process order -> Create payment (Avro)
    Given I have a topic "order-processing-avro"
    When I produce an Avro event with schema "OrderProcessedEvent" to topic "order-processing-avro":
      """
      {
        "orderId": "ORD-CROSS-001",
        "status": "PROCESSING",
        "paymentRequired": true
      }
      """
    Then I should receive an Avro event from topic "order-processing-avro" within 10 seconds

    # Step 3: Payment created (Protobuf)
    Given I have a topic "payments-proto"
    When I produce a Protobuf event with schema "PaymentEvent" to topic "payments-proto":
      """
      {
        "paymentId": "PAY-CROSS-001",
        "orderId": "ORD-CROSS-001",
        "amount": 399.99,
        "currency": "USD",
        "method": "CREDIT_CARD",
        "status": "PENDING"
      }
      """
    Then I should receive a Protobuf event from topic "payments-proto" within 10 seconds
    And the event should match JSONPath "$.orderId" with value "ORD-CROSS-001"
```

---

## Part 4: Schema Evolution Testing

### Step 4.1: Backward Compatible Changes

Test that consumers can handle schema evolution:

```gherkin
Feature: Schema Evolution
  Validate backward compatibility of schema changes

  Scenario: Consumer handles new optional field (backward compatible)
    # Producer uses v2 schema with new 'loyaltyTier' field
    When I produce an Avro event with schema "CustomerEvent-v2" to topic "customers-avro":
      """
      {
        "customerId": "CUST-EVO-001",
        "name": "Alice Smith",
        "email": "alice@example.com",
        "loyaltyTier": "GOLD"
      }
      """
    Then I should receive an Avro event from topic "customers-avro" within 15 seconds
    # Existing fields work
    And the event should match JSONPath "$.customerId" with value "CUST-EVO-001"
    And the event should match JSONPath "$.name" with value "Alice Smith"
    # New field is present
    And the event should match JSONPath "$.loyaltyTier" with value "GOLD"

  Scenario: New consumer reads old event (forward compatible)
    # Producer uses v1 schema (no 'loyaltyTier' field)
    When I produce an Avro event with schema "CustomerEvent-v1" to topic "customers-avro":
      """
      {
        "customerId": "CUST-EVO-002",
        "name": "Bob Jones",
        "email": "bob@example.com"
      }
      """
    Then I should receive an Avro event from topic "customers-avro" within 15 seconds
    # Existing fields work
    And the event should match JSONPath "$.customerId" with value "CUST-EVO-002"
    # New field is absent (or has default value)
    And the event should not have JSONPath "$.loyaltyTier"
```

### Step 4.2: Schema Version Assertions

```gherkin
  Scenario: Verify schema version in event
    When I produce an Avro event with schema "OrderEvent" version 2 to topic "orders-avro":
      """
      {
        "orderId": "ORD-VER-001",
        "customerId": "CUST-VER",
        "amount": 99.99
      }
      """
    Then I should receive an Avro event from topic "orders-avro" within 15 seconds
    # CloudEvent key contains schema info
    And the event key should match JSONPath "$.payloadversion" with value "v2"
```

---

## Part 5: Performance Comparison

### Format Comparison Table

| Metric | JSON | Avro | Protobuf |
|--------|------|------|----------|
| **Typical Payload** | 500 bytes | 200 bytes | 180 bytes |
| **Serialization** | ~1ms | ~0.3ms | ~0.2ms |
| **Human Readable** | Yes | No | No |
| **Schema Required** | Optional | Yes | Yes |

### Choosing the Right Format

```gherkin
Feature: Format Selection Guide
  # Use this as a reference for choosing serialization format

  # JSON: Best for...
  # - Development and debugging
  # - APIs consumed by web frontends
  # - Events that need to be human-readable
  # - Low-volume topics

  # Avro: Best for...
  # - Kafka Connect integrations
  # - Data lake ingestion (Spark, Flink)
  # - JVM-based microservices
  # - Topics with high schema evolution

  # Protobuf: Best for...
  # - gRPC service communication
  # - Polyglot environments (Go, Rust, Python)
  # - Maximum performance requirements
  # - Mobile app backends
```

---

## Troubleshooting

### Issue: Schema Not Found

**Symptom:** `Schema not found: OrderEvent`

**Fix:**
- Verify schema is registered in Schema Registry
- Check subject naming: `{topic}-value` (default convention)
- Use Schema Registry API to list subjects:
  ```bash
  curl https://schema-registry.example.com/subjects
  ```

### Issue: Schema Compatibility Error

**Symptom:** `Schema being registered is incompatible with an earlier schema`

**Fix:**
- Check Schema Registry compatibility mode
- Ensure new schema is backward compatible
- Review [Confluent Schema Evolution docs](https://docs.confluent.io/platform/current/schema-registry/avro.html#schema-evolution)

### Issue: Deserialization Failed

**Symptom:** `Failed to deserialize Avro record`

**Fix:**
- Verify event was produced with correct schema
- Check schema ID in message header
- Ensure Schema Registry is accessible

---

## Summary

You've learned schema-based serialization in Test-Probe:

- Configuring Avro and Protobuf in test-config.yaml
- Writing Gherkin tests for binary-serialized events
- Testing across multiple serialization formats
- Validating schema evolution scenarios

**Key Takeaways:**
- Schemas must be preloaded in Schema Registry
- JSON for readability, Avro/Protobuf for performance
- Test schema evolution with version-specific scenarios

---

## Next Steps

Continue your Test-Probe journey:

1. **[Tutorial 4: Multi-Cluster Testing](04-multi-cluster.md)** - Cross-datacenter validation
2. **[CI/CD Integration](../integration/ci-cd-pipelines.md)** - Automate your tests
3. **[Troubleshooting Guide](../TROUBLESHOOTING.md)** - Common issues and solutions

---

**Document Version:** 2.0.0
**Last Updated:** 2025-12-05
