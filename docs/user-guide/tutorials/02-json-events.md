# Tutorial 2: Working with JSON Events

**Level:** Intermediate
**Duration:** 30 minutes
**Prerequisites:** [Tutorial 1: Your First Test](01-first-test.md)

---

## What You'll Learn

By the end of this tutorial, you will:

1. Use advanced JSONPath assertions in Gherkin
2. Match events with complex nested structures
3. Use pattern matching for dynamic values
4. Handle multiple events in a single scenario
5. Validate event ordering and counts

## Expected Outcome

You'll write feature files that validate complex JSON event structures, including nested objects, arrays, and dynamic field matching.

---

## Prerequisites

Before starting, ensure you have:

- Completed [Tutorial 1](01-first-test.md)
- Access to Test-Probe service
- S3 bucket for test assets

---

## Step 1: JSONPath Basics

### 1.1 Understanding JSONPath Syntax

JSONPath is used to query JSON documents. Test-Probe supports standard JSONPath expressions:

| Expression | Description | Example |
|------------|-------------|---------|
| `$` | Root object | `$` |
| `.field` | Child field | `$.orderId` |
| `[n]` | Array index | `$.items[0]` |
| `[*]` | All array elements | `$.items[*]` |
| `..field` | Recursive descent | `$..productId` |

### 1.2 Basic Assertions

Create `features/json-basics.feature`:

```gherkin
Feature: JSON Event Basics
  Validate JSON event structure and field values

  Scenario: Validate simple fields
    Given I have a topic "orders"
    When I produce a JSON event to topic "orders":
      """
      {
        "orderId": "ORD-12345",
        "customerId": "CUST-789",
        "status": "PENDING",
        "amount": 199.99,
        "currency": "USD"
      }
      """
    Then I should receive an event from topic "orders" within 10 seconds
    And the event should match JSONPath "$.orderId" with value "ORD-12345"
    And the event should match JSONPath "$.status" with value "PENDING"
    And the event should match JSONPath "$.amount" with value 199.99
```

---

## Step 2: Nested Object Validation

### 2.1 Accessing Nested Fields

```gherkin
Feature: Nested JSON Validation
  Validate events with nested object structures

  Scenario: Validate customer address in order
    Given I have a topic "orders"
    When I produce a JSON event to topic "orders":
      """
      {
        "orderId": "ORD-67890",
        "customer": {
          "id": "CUST-123",
          "name": "John Doe",
          "email": "john.doe@example.com",
          "address": {
            "street": "123 Main St",
            "city": "New York",
            "state": "NY",
            "zip": "10001",
            "country": "USA"
          }
        },
        "total": 299.99
      }
      """
    Then I should receive an event from topic "orders" within 10 seconds
    # Access nested fields with dot notation
    And the event should match JSONPath "$.customer.name" with value "John Doe"
    And the event should match JSONPath "$.customer.email" with value "john.doe@example.com"
    # Deep nesting
    And the event should match JSONPath "$.customer.address.city" with value "New York"
    And the event should match JSONPath "$.customer.address.zip" with value "10001"
```

### 2.2 Validating Object Existence

```gherkin
  Scenario: Verify nested object exists
    Given I have a topic "orders"
    When I produce a JSON event to topic "orders":
      """
      {
        "orderId": "ORD-11111",
        "payment": {
          "method": "CREDIT_CARD",
          "last4": "4242"
        }
      }
      """
    Then I should receive an event from topic "orders" within 10 seconds
    # Verify object exists (not null)
    And the event should have JSONPath "$.payment"
    And the event should match JSONPath "$.payment.method" with value "CREDIT_CARD"
```

---

## Step 3: Array Validation

### 3.1 Accessing Array Elements

```gherkin
Feature: Array Validation
  Validate events with array structures

  Scenario: Validate order line items
    Given I have a topic "orders"
    When I produce a JSON event to topic "orders":
      """
      {
        "orderId": "ORD-ARRAY-001",
        "items": [
          {
            "productId": "PROD-A",
            "name": "Widget",
            "quantity": 2,
            "price": 49.99
          },
          {
            "productId": "PROD-B",
            "name": "Gadget",
            "quantity": 1,
            "price": 199.99
          },
          {
            "productId": "PROD-C",
            "name": "Gizmo",
            "quantity": 3,
            "price": 29.99
          }
        ]
      }
      """
    Then I should receive an event from topic "orders" within 10 seconds
    # Access by index (0-based)
    And the event should match JSONPath "$.items[0].productId" with value "PROD-A"
    And the event should match JSONPath "$.items[1].name" with value "Gadget"
    And the event should match JSONPath "$.items[2].quantity" with value 3
```

### 3.2 Array Length Validation

```gherkin
  Scenario: Validate array length
    Given I have a topic "orders"
    When I produce a JSON event to topic "orders":
      """
      {
        "orderId": "ORD-ARRAY-002",
        "items": [
          {"productId": "A"},
          {"productId": "B"},
          {"productId": "C"}
        ]
      }
      """
    Then I should receive an event from topic "orders" within 10 seconds
    And the event should match JSONPath "$.items.length()" with value 3
```

### 3.3 Wildcard Array Queries

```gherkin
  Scenario: Find value in any array element
    Given I have a topic "orders"
    When I produce a JSON event to topic "orders":
      """
      {
        "orderId": "ORD-ARRAY-003",
        "items": [
          {"productId": "PROD-X", "status": "SHIPPED"},
          {"productId": "PROD-Y", "status": "PENDING"},
          {"productId": "PROD-Z", "status": "SHIPPED"}
        ]
      }
      """
    Then I should receive an event from topic "orders" within 10 seconds
    # Check if ANY item has status PENDING
    And the event should contain JSONPath "$.items[*].status" with value "PENDING"
```

---

## Step 4: Pattern Matching

### 4.1 Regex Patterns for Dynamic Values

```gherkin
Feature: Pattern Matching
  Use regex patterns to match dynamic values

  Scenario: Match UUID format
    Given I have a topic "events"
    When I produce a JSON event to topic "events":
      """
      {
        "eventId": "550e8400-e29b-41d4-a716-446655440000",
        "timestamp": "2025-12-05T14:30:00Z",
        "source": "order-service"
      }
      """
    Then I should receive an event from topic "events" within 10 seconds
    # Match UUID pattern
    And the event should match JSONPath "$.eventId" with pattern "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    # Match ISO timestamp pattern
    And the event should match JSONPath "$.timestamp" with pattern "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$"
```

### 4.2 Partial String Matching

```gherkin
  Scenario: Match partial strings
    Given I have a topic "logs"
    When I produce a JSON event to topic "logs":
      """
      {
        "message": "Order ORD-12345 processed successfully",
        "level": "INFO",
        "service": "payment-service-prod-us-east-1"
      }
      """
    Then I should receive an event from topic "logs" within 10 seconds
    # Contains substring
    And the event should match JSONPath "$.message" containing "processed successfully"
    # Starts with
    And the event should match JSONPath "$.service" with pattern "^payment-service-.*"
    # Ends with
    And the event should match JSONPath "$.service" with pattern ".*-us-east-1$"
```

### 4.3 Numeric Range Matching

```gherkin
  Scenario: Validate numeric ranges
    Given I have a topic "metrics"
    When I produce a JSON event to topic "metrics":
      """
      {
        "metricName": "response_time_ms",
        "value": 145,
        "percentile": 99.5
      }
      """
    Then I should receive an event from topic "metrics" within 10 seconds
    # Greater than
    And the event should match JSONPath "$.value" greater than 100
    # Less than
    And the event should match JSONPath "$.value" less than 200
    # Between (using both)
    And the event should match JSONPath "$.percentile" greater than 99.0
    And the event should match JSONPath "$.percentile" less than 100.0
```

---

## Step 5: Multiple Events

### 5.1 Producing Multiple Events

```gherkin
Feature: Multiple Event Handling
  Test scenarios with multiple events

  Scenario: Produce and consume multiple orders
    Given I have a topic "orders"
    When I produce the following JSON events to topic "orders":
      | orderId     | status    | amount |
      | ORD-001     | PENDING   | 99.99  |
      | ORD-002     | CONFIRMED | 149.99 |
      | ORD-003     | SHIPPED   | 249.99 |
    Then I should receive 3 events from topic "orders" within 30 seconds
```

### 5.2 Validating Event Order

```gherkin
  Scenario: Validate events arrive in order
    Given I have a topic "order-status"
    When I produce the following JSON events to topic "order-status":
      | orderId | status    | sequence |
      | ORD-X   | CREATED   | 1        |
      | ORD-X   | CONFIRMED | 2        |
      | ORD-X   | SHIPPED   | 3        |
      | ORD-X   | DELIVERED | 4        |
    Then I should receive 4 events from topic "order-status" within 30 seconds
    And the events should be ordered by JSONPath "$.sequence" ascending
```

### 5.3 Filtering Events

```gherkin
  Scenario: Filter events by field value
    Given I have a topic "mixed-events"
    When I produce the following JSON events to topic "mixed-events":
      | eventType      | orderId |
      | OrderCreated   | ORD-A   |
      | OrderUpdated   | ORD-A   |
      | OrderCreated   | ORD-B   |
      | OrderShipped   | ORD-A   |
      | OrderCreated   | ORD-C   |
    Then I should receive 5 events from topic "mixed-events" within 30 seconds
    # Filter to only OrderCreated events
    And 3 events should match JSONPath "$.eventType" with value "OrderCreated"
    # Filter to only ORD-A events
    And 3 events should match JSONPath "$.orderId" with value "ORD-A"
```

---

## Step 6: CloudEvents Envelope

### 6.1 Understanding the Key-Value Structure

Test-Probe uses CloudEvents for message keys:

```
Kafka Message:
┌────────────────────────────────────────────────┐
│ KEY (CloudEvents envelope):                    │
│   {                                            │
│     "id": "uuid",                              │
│     "type": "OrderCreatedEvent",               │
│     "source": "test-probe",                    │
│     "correlationid": "correlation-uuid",       │
│     "specversion": "1.0"                       │
│   }                                            │
├────────────────────────────────────────────────┤
│ VALUE (Your JSON payload):                     │
│   {                                            │
│     "orderId": "ORD-123",                      │
│     "amount": 99.99                            │
│   }                                            │
└────────────────────────────────────────────────┘
```

### 6.2 Matching on CloudEvent Fields

```gherkin
Feature: CloudEvents Integration
  Work with CloudEvents envelope structure

  Scenario: Match events by CloudEvent type
    Given I have a topic "events"
    When I produce a JSON event with type "OrderCreatedEvent" to topic "events":
      """
      {
        "orderId": "ORD-CE-001",
        "amount": 199.99
      }
      """
    Then I should receive an event from topic "events" within 10 seconds
    # Match on CloudEvent key fields
    And the event key should match JSONPath "$.type" with value "OrderCreatedEvent"
    And the event key should have JSONPath "$.correlationid"
    # Match on value fields
    And the event value should match JSONPath "$.orderId" with value "ORD-CE-001"
```

---

## Step 7: Complete Example

### 7.1 Full Feature File

Create `features/order-processing.feature`:

```gherkin
Feature: Order Processing Validation
  Comprehensive validation of order event structures

  Background:
    Given I have a topic "orders"

  Scenario: Complete order with all fields
    When I produce a JSON event with type "OrderCreatedEvent" to topic "orders":
      """
      {
        "eventType": "OrderCreatedEvent",
        "eventVersion": "v1",
        "orderId": "ORD-FULL-001",
        "customer": {
          "id": "CUST-500",
          "name": "Alice Smith",
          "email": "alice@example.com",
          "tier": "GOLD"
        },
        "items": [
          {
            "productId": "PROD-100",
            "name": "Premium Widget",
            "quantity": 2,
            "unitPrice": 49.99,
            "totalPrice": 99.98
          },
          {
            "productId": "PROD-200",
            "name": "Standard Gadget",
            "quantity": 1,
            "unitPrice": 29.99,
            "totalPrice": 29.99
          }
        ],
        "totals": {
          "subtotal": 129.97,
          "tax": 11.70,
          "shipping": 5.99,
          "total": 147.66
        },
        "payment": {
          "method": "CREDIT_CARD",
          "status": "AUTHORIZED",
          "transactionId": "TXN-789456"
        },
        "metadata": {
          "source": "web",
          "userAgent": "Mozilla/5.0",
          "ipAddress": "192.168.1.100"
        },
        "timestamps": {
          "created": "2025-12-05T10:30:00Z",
          "updated": "2025-12-05T10:30:00Z"
        }
      }
      """
    Then I should receive an event from topic "orders" within 15 seconds

    # Basic field validation
    And the event should match JSONPath "$.eventType" with value "OrderCreatedEvent"
    And the event should match JSONPath "$.orderId" with value "ORD-FULL-001"

    # Customer validation
    And the event should match JSONPath "$.customer.name" with value "Alice Smith"
    And the event should match JSONPath "$.customer.tier" with value "GOLD"
    And the event should match JSONPath "$.customer.email" with pattern "^[^@]+@[^@]+\\.[^@]+$"

    # Items validation
    And the event should match JSONPath "$.items.length()" with value 2
    And the event should match JSONPath "$.items[0].productId" with value "PROD-100"
    And the event should match JSONPath "$.items[0].quantity" with value 2
    And the event should match JSONPath "$.items[1].name" with value "Standard Gadget"

    # Totals validation (numeric)
    And the event should match JSONPath "$.totals.subtotal" with value 129.97
    And the event should match JSONPath "$.totals.total" greater than 100.0
    And the event should match JSONPath "$.totals.total" less than 200.0

    # Payment validation
    And the event should match JSONPath "$.payment.status" with value "AUTHORIZED"
    And the event should match JSONPath "$.payment.transactionId" with pattern "^TXN-\\d+$"

    # Timestamp validation (ISO format)
    And the event should match JSONPath "$.timestamps.created" with pattern "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$"
```

### 7.2 Test Configuration

Create `test-config.yaml`:

```yaml
test-probe:
  kafka:
    cluster: default

  test:
    timeout-seconds: 60

  serialization:
    key-format: cloudevents
    value-format: json

  assertions:
    # Default timeout for event assertions
    default-timeout-seconds: 15
    # Polling interval when waiting for events
    poll-interval-ms: 500
```

---

## Step 8: Submit and Run

### 8.1 Deploy and Execute

```bash
# Initialize test session
TEST_ID=$(curl -sf -X POST "${TEST_PROBE_URL}/api/v1/test/initialize" | jq -r '.["test-id"]')

# Upload test assets
aws s3 cp ./features/ "s3://${BUCKET}/tests/${TEST_ID}/features/" --recursive
aws s3 cp ./test-config.yaml "s3://${BUCKET}/tests/${TEST_ID}/test-config.yaml"

# Start test
curl -sf -X POST "${TEST_PROBE_URL}/api/v1/test/start" \
  -H "Content-Type: application/json" \
  -d "{\"test-id\": \"${TEST_ID}\", \"block-storage-path\": \"s3://${BUCKET}/tests/${TEST_ID}\"}"

# Poll for completion
while true; do
  STATUS=$(curl -sf "${TEST_PROBE_URL}/api/v1/test/${TEST_ID}/status" | jq -r '.state')
  echo "Status: $STATUS"
  [ "$STATUS" = "Completed" ] && break
  [ "$STATUS" = "Failed" ] && exit 1
  sleep 5
done
```

---

## Troubleshooting

### Issue: JSONPath Not Found

**Symptom:** `JSONPath $.field not found in event`

**Fix:**
- Check field name spelling (case-sensitive)
- Verify JSON structure matches your expectation
- Use the evidence report to see actual event payload

### Issue: Pattern Match Failed

**Symptom:** `Pattern '^[0-9]+$' did not match value 'abc123'`

**Fix:**
- Test your regex pattern with sample values
- Escape backslashes in Gherkin (`\\d` instead of `\d`)
- Remember patterns are anchored with `^` and `$`

### Issue: Array Index Out of Bounds

**Symptom:** `Array index 5 out of bounds for array of length 3`

**Fix:**
- Check array length before accessing by index
- Use `$.items.length()` to verify array size
- Consider using wildcard `[*]` for flexible matching

---

## Summary

You've learned advanced JSON event validation in Test-Probe:

- JSONPath expressions for field access
- Nested object and array validation
- Pattern matching with regex
- Numeric range assertions
- Multiple event scenarios
- CloudEvents envelope structure

---

## Next Steps

Continue your Test-Probe journey:

1. **[Tutorial 3: Avro & Protobuf](03-avro-protobuf.md)** - Schema-based serialization
2. **[Tutorial 4: Multi-Cluster Testing](04-multi-cluster.md)** - Cross-datacenter validation
3. **[CI/CD Integration](../integration/ci-cd-pipelines.md)** - Automate your tests

---

**Document Version:** 2.0.0
**Last Updated:** 2025-12-05
