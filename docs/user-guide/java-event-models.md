# Java Event Models Guide

**Version:** 1.0.0
**Last Updated:** 2025-11-26
**Audience:** Java developers creating event models for Test-Probe framework

---

## Overview

This guide shows how to create event models in Java for the three serialization formats supported by Test-Probe:

1. **JSON Events** - Jackson-annotated POJOs with JSON Schema
2. **Avro Events** - SpecificRecord implementations with Avro schema
3. **Protobuf Events** - DynamicMessage wrappers with Protobuf schema

All three formats integrate seamlessly with Confluent Schema Registry and the ProbeJavaDsl API.

---

## Table of Contents

1. [JSON Events (Jackson POJOs)](#json-events-jackson-pojos)
2. [Avro Events (SpecificRecord)](#avro-events-specificrecord)
3. [Protobuf Events (DynamicMessage)](#protobuf-events-dynamicmessage)
4. [Schema Registration](#schema-registration)
5. [Complete Examples](#complete-examples)

---

## JSON Events (Jackson POJOs)

### Overview

JSON events are plain Java objects with Jackson annotations. They are serialized using JSON Schema and Jackson's ObjectMapper with Scala 3 support.

**When to Use:**
- Simple event structures
- Human-readable format for debugging
- Existing Jackson infrastructure

**Trade-offs:**
- Larger message size vs Avro/Protobuf
- No schema evolution guarantees (JSON Schema is permissive)
- Slower serialization vs binary formats

---

### Basic JSON Event

```java
package com.yourcompany.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;

/**
 * Simple JSON event for testing.
 */
@JsonSchemaTitle("TestEvent")
@JsonTypeName("TestEvent")
public class TestEvent {

    private String eventType;
    private String eventVersion;
    private String eventTestId;
    private long timestamp;
    private String key;
    private String payload;

    // Default constructor (required for Jackson deserialization)
    public TestEvent() {}

    // Full constructor
    public TestEvent(String eventType, String eventVersion, String eventTestId,
                     long timestamp, String key, String payload) {
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.eventTestId = eventTestId;
        this.timestamp = timestamp;
        this.key = key;
        this.payload = payload;
    }

    // Getters and setters
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventVersion() { return eventVersion; }
    public void setEventVersion(String eventVersion) { this.eventVersion = eventVersion; }

    public String getEventTestId() { return eventTestId; }
    public void setEventTestId(String eventTestId) { this.eventTestId = eventTestId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    @Override
    public String toString() {
        return "TestEvent{" +
                "eventType='" + eventType + '\'' +
                ", eventVersion='" + eventVersion + '\'' +
                ", eventTestId='" + eventTestId + '\'' +
                ", timestamp=" + timestamp +
                ", key='" + key + '\'' +
                ", payload='" + payload + '\'' +
                '}';
    }
}
```

**Key Annotations:**
- `@JsonSchemaTitle("TestEvent")` - Schema subject name in Schema Registry
- `@JsonTypeName("TestEvent")` - Jackson type identifier

---

### Complex JSON Event

```java
package com.yourcompany.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;
import java.util.List;
import java.util.Map;

@JsonSchemaTitle("OrderCreatedEvent")
@JsonTypeName("OrderCreatedEvent")
public class OrderCreatedEvent {

    private String eventType;
    private String eventVersion;
    private String orderId;
    private String customerId;
    private double totalAmount;
    private String currency;
    private List<OrderLineItem> lineItems;
    private Map<String, String> metadata;
    private long timestamp;

    public OrderCreatedEvent() {}

    public OrderCreatedEvent(String eventType, String eventVersion, String orderId,
                             String customerId, double totalAmount, String currency,
                             List<OrderLineItem> lineItems, Map<String, String> metadata,
                             long timestamp) {
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.orderId = orderId;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.lineItems = lineItems;
        this.metadata = metadata;
        this.timestamp = timestamp;
    }

    // Getters and setters omitted for brevity

    public static class OrderLineItem {
        private String productId;
        private int quantity;
        private double price;

        public OrderLineItem() {}

        public OrderLineItem(String productId, int quantity, double price) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
        }

        // Getters and setters omitted for brevity
    }
}
```

**Usage:**

```java
OrderCreatedEvent event = new OrderCreatedEvent(
    "OrderCreated",
    "v1",
    "order-123",
    "cust-456",
    199.98,
    "USD",
    List.of(
        new OrderLineItem("prod-1", 2, 99.99),
        new OrderLineItem("prod-2", 1, 0.00) // Free item
    ),
    Map.of("promo-code", "SUMMER2025"),
    System.currentTimeMillis()
);

ProduceResult result = ProbeJavaDsl.produceEventBlocking(
    testId, "orders", key, event, headers, OrderCreatedEvent.class
);
```

---

## Avro Events (SpecificRecord)

### Overview

Avro events implement `org.apache.avro.specific.SpecificRecord` for binary serialization. They provide strong schema evolution guarantees and compact message size.

**When to Use:**
- High-throughput scenarios
- Strong schema evolution requirements
- Binary serialization for performance

**Trade-offs:**
- More boilerplate code vs JSON
- Requires schema definition
- Less human-readable

---

### Basic Avro Event

```java
package com.yourcompany.events;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;

/**
 * Avro SpecificRecord implementation for OrderEvent.
 * Implements SpecificRecord to enable Avro binary serialization.
 */
public class OrderEvent implements SpecificRecord {

    /**
     * Avro schema for OrderEvent.
     * Must match the schema registered in Schema Registry.
     */
    public static final Schema SCHEMA = new Schema.Parser().parse(
        "{" +
        "  \"type\": \"record\"," +
        "  \"name\": \"OrderEvent\"," +
        "  \"namespace\": \"com.yourcompany.events\"," +
        "  \"doc\": \"Order event for Avro integration testing\"," +
        "  \"fields\": [" +
        "    {\"name\": \"eventType\", \"type\": \"string\", \"default\": \"OrderEvent\"}," +
        "    {\"name\": \"orderId\", \"type\": \"string\"}," +
        "    {\"name\": \"customerId\", \"type\": \"string\"}," +
        "    {\"name\": \"amount\", \"type\": \"double\"}," +
        "    {\"name\": \"currency\", \"type\": [\"null\", \"string\"], \"default\": null}," +
        "    {\"name\": \"timestamp\", \"type\": [\"null\", \"long\"], \"default\": null}" +
        "  ]" +
        "}"
    );

    // Fields - order matches schema field indices (critical!)
    private String eventType;
    private String orderId;
    private String customerId;
    private Double amount;
    private String currency;
    private Long timestamp;

    /**
     * Default constructor for Avro deserialization.
     */
    public OrderEvent() {
        this.eventType = "OrderEvent";
    }

    /**
     * Full constructor.
     */
    public OrderEvent(String eventType, String orderId, String customerId,
                      Double amount, String currency, Long timestamp) {
        this.eventType = eventType;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = timestamp;
    }

    // ==========================================================================
    // SpecificRecord Implementation
    // ==========================================================================

    @Override
    public Schema getSchema() {
        return SCHEMA;
    }

    @Override
    public Object get(int field) {
        switch (field) {
            case 0: return eventType;
            case 1: return orderId;
            case 2: return customerId;
            case 3: return amount;
            case 4: return currency;
            case 5: return timestamp;
            default: throw new IndexOutOfBoundsException("Invalid field index: " + field);
        }
    }

    @Override
    public void put(int field, Object value) {
        switch (field) {
            // Avro deserializes strings as Utf8 objects - must convert to String
            case 0: eventType = value != null ? value.toString() : null; break;
            case 1: orderId = value != null ? value.toString() : null; break;
            case 2: customerId = value != null ? value.toString() : null; break;
            case 3: amount = (Double) value; break;
            case 4: currency = value != null ? value.toString() : null; break;
            case 5: timestamp = (Long) value; break;
            default: throw new IndexOutOfBoundsException("Invalid field index: " + field);
        }
    }

    // ==========================================================================
    // Getters and Setters
    // ==========================================================================

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    // ==========================================================================
    // Builder Pattern (Fluent API)
    // ==========================================================================

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String eventType = "OrderEvent";
        private String orderId = "";
        private String customerId = "";
        private Double amount = 0.0;
        private String currency = null;
        private Long timestamp = null;

        public Builder setEventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder setOrderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder setCustomerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder setAmount(Double amount) {
            this.amount = amount;
            return this;
        }

        public Builder setCurrency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public OrderEvent build() {
            return new OrderEvent(eventType, orderId, customerId, amount, currency, timestamp);
        }
    }

    @Override
    public String toString() {
        return "OrderEvent{" +
                "eventType='" + eventType + '\'' +
                ", orderId='" + orderId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
```

**Important:** Field indices in `get()` and `put()` must match Avro schema field order.

---

### Avro Builder Usage

```java
OrderEvent order = OrderEvent.newBuilder()
    .setEventType("OrderEvent")
    .setOrderId("order-123")
    .setCustomerId("cust-456")
    .setAmount(99.99)
    .setCurrency("USD")
    .setTimestamp(System.currentTimeMillis())
    .build();

ProduceResult result = ProbeJavaDsl.produceEventBlocking(
    testId, "orders", key, order, headers, OrderEvent.class
);
```

---

## Protobuf Events (DynamicMessage)

### Overview

Protobuf events use `com.google.protobuf.DynamicMessage` for runtime schema parsing. This avoids code generation while maintaining binary serialization benefits.

**When to Use:**
- Binary serialization without code generation
- Polyglot environments (Protobuf is language-agnostic)
- Backward/forward compatibility requirements

**Trade-offs:**
- More verbose than Avro (no builder auto-generation)
- Requires wrapper class for business logic
- Field access by number (less type-safe)

---

### Basic Protobuf Event

```java
package com.yourcompany.events;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;

/**
 * Protobuf DynamicMessage wrapper for PaymentEvent.
 * Uses DynamicMessage for Schema Registry compatibility (no generated classes).
 */
public class PaymentEvent {

    /**
     * Protobuf schema string matching payment-event.proto.
     */
    public static final String SCHEMA_STRING =
        "syntax = \"proto3\";\n" +
        "package com.yourcompany.events;\n" +
        "option java_package = \"com.yourcompany.events\";\n" +
        "option java_outer_classname = \"PaymentEventProtos\";\n" +
        "option java_multiple_files = true;\n" +
        "message PaymentEventProto {\n" +
        "  string event_type = 1;\n" +
        "  string payment_id = 2;\n" +
        "  string order_id = 3;\n" +
        "  double amount = 4;\n" +
        "  string currency = 5;\n" +
        "  string payment_method = 6;\n" +
        "  int64 timestamp = 7;\n" +
        "}";

    /**
     * Protobuf schema for serialization/deserialization.
     */
    public static final ProtobufSchema SCHEMA = new ProtobufSchema(SCHEMA_STRING);

    // Fields - order matches proto field numbers
    private String eventType;
    private String paymentId;
    private String orderId;
    private Double amount;
    private String currency;
    private String paymentMethod;
    private Long timestamp;

    public PaymentEvent() {
        this.eventType = "PaymentEvent";
    }

    public PaymentEvent(String eventType, String paymentId, String orderId,
                        Double amount, String currency, String paymentMethod, Long timestamp) {
        this.eventType = eventType;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.timestamp = timestamp;
    }

    // ==========================================================================
    // DynamicMessage Conversion (Protobuf Serialization)
    // ==========================================================================

    /**
     * Convert to DynamicMessage for Confluent serialization.
     * Field numbers must match payment-event.proto.
     */
    public DynamicMessage toDynamicMessage(ProtobufSchema schema) {
        Descriptor descriptor = schema.toDescriptor();
        return DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByNumber(1), eventType != null ? eventType : "")
            .setField(descriptor.findFieldByNumber(2), paymentId != null ? paymentId : "")
            .setField(descriptor.findFieldByNumber(3), orderId != null ? orderId : "")
            .setField(descriptor.findFieldByNumber(4), amount != null ? amount : 0.0)
            .setField(descriptor.findFieldByNumber(5), currency != null ? currency : "")
            .setField(descriptor.findFieldByNumber(6), paymentMethod != null ? paymentMethod : "")
            .setField(descriptor.findFieldByNumber(7), timestamp != null ? timestamp : 0L)
            .build();
    }

    /**
     * Create from DynamicMessage (for deserialization).
     * Protobuf returns Java native types (String, Double, Long) - no conversion needed.
     */
    public static PaymentEvent fromDynamicMessage(DynamicMessage msg) {
        Descriptor descriptor = msg.getDescriptorForType();
        return new PaymentEvent(
            (String) msg.getField(descriptor.findFieldByNumber(1)),
            (String) msg.getField(descriptor.findFieldByNumber(2)),
            (String) msg.getField(descriptor.findFieldByNumber(3)),
            ((Number) msg.getField(descriptor.findFieldByNumber(4))).doubleValue(),
            (String) msg.getField(descriptor.findFieldByNumber(5)),
            (String) msg.getField(descriptor.findFieldByNumber(6)),
            ((Number) msg.getField(descriptor.findFieldByNumber(7))).longValue()
        );
    }

    // ==========================================================================
    // Getters and Setters
    // ==========================================================================

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    // ==========================================================================
    // Builder Pattern
    // ==========================================================================

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String eventType = "PaymentEvent";
        private String paymentId = "";
        private String orderId = "";
        private Double amount = 0.0;
        private String currency = null;
        private String paymentMethod = null;
        private Long timestamp = null;

        public Builder setEventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder setPaymentId(String paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder setOrderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder setAmount(Double amount) {
            this.amount = amount;
            return this;
        }

        public Builder setCurrency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder setPaymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        public Builder setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public PaymentEvent build() {
            return new PaymentEvent(eventType, paymentId, orderId, amount, currency, paymentMethod, timestamp);
        }
    }

    @Override
    public String toString() {
        return "PaymentEvent{" +
                "eventType='" + eventType + '\'' +
                ", paymentId='" + paymentId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
```

---

### Protobuf Usage

```java
// Create wrapper
PaymentEvent payment = PaymentEvent.newBuilder()
    .setEventType("PaymentEvent")
    .setPaymentId("pay-123")
    .setOrderId("order-456")
    .setAmount(149.99)
    .setCurrency("USD")
    .setPaymentMethod("CREDIT_CARD")
    .setTimestamp(System.currentTimeMillis())
    .build();

// Convert to DynamicMessage for Kafka serialization
DynamicMessage dynamicMessage = payment.toDynamicMessage(PaymentEvent.SCHEMA);

// Produce (note: DynamicMessage.class, not PaymentEvent.class)
ProduceResult result = ProbeJavaDsl.produceEventBlocking(
    testId,
    "payments",
    key,
    dynamicMessage,
    headers,
    DynamicMessage.class  // IMPORTANT: Use DynamicMessage.class
);

// Consume
ConsumedResult consumed = ProbeJavaDsl.fetchConsumedEventBlocking(
    testId,
    "payments",
    correlationId,
    DynamicMessage.class  // IMPORTANT: Use DynamicMessage.class
);

if (consumed instanceof ConsumedSuccess) {
    ConsumedSuccess success = (ConsumedSuccess) consumed;
    DynamicMessage consumedMsg = (DynamicMessage) success.value();

    // Convert back to wrapper
    PaymentEvent received = PaymentEvent.fromDynamicMessage(consumedMsg);
    assertEquals("pay-123", received.getPaymentId());
}
```

**Critical:** Always use `DynamicMessage.class` as the type parameter, not `PaymentEvent.class`.

---

## Schema Registration

### Schema Subject Naming

The framework uses **TopicRecordNameStrategy** for schema subjects:

```
Subject = {topic}-{className}
```

**Examples:**

| Topic | Class | Subject |
|-------|-------|---------|
| `orders` | `OrderEvent` | `orders-OrderEvent` |
| `payments` | `PaymentEvent` | `payments-PaymentEvent` |
| `test-events` | `TestEvent` | `test-events-TestEvent` |

---

### Automatic Schema Registration

Schemas are automatically registered when producing events:

```java
// First produce registers schema in Schema Registry
ProduceResult result = ProbeJavaDsl.produceEventBlocking(
    testId, "orders", key, orderEvent, headers, OrderEvent.class
);

// Schema registered as: "orders-OrderEvent"
```

**Configuration:**

```properties
# Schema Registry URL (required)
SCHEMA_REGISTRY_URL=http://localhost:8081

# Auto-registration (enabled by default in test mode)
auto.register.schemas=true
```

---

### Manual Schema Registration (Optional)

For production environments, pre-register schemas:

```bash
# JSON Schema
curl -X POST http://localhost:8081/subjects/orders-OrderEvent/versions \
  -H "Content-Type: application/json" \
  -d @order-event-schema.json

# Avro Schema
curl -X POST http://localhost:8081/subjects/orders-OrderEvent/versions \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{"schemaType": "AVRO", "schema": "{...}"}'

# Protobuf Schema
curl -X POST http://localhost:8081/subjects/payments-PaymentEvent/versions \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{"schemaType": "PROTOBUF", "schema": "syntax = \"proto3\";..."}'
```

---

## Complete Examples

### Example 1: JSON Order Event

```java
package com.yourcompany.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;

@JsonSchemaTitle("OrderCreatedEvent")
@JsonTypeName("OrderCreatedEvent")
public class OrderCreatedEvent {
    private String eventType;
    private String orderId;
    private String customerId;
    private double amount;
    private String currency;
    private long timestamp;

    public OrderCreatedEvent() {}

    public OrderCreatedEvent(String eventType, String orderId, String customerId,
                             double amount, String currency, long timestamp) {
        this.eventType = eventType;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = timestamp;
    }

    // Getters and setters omitted for brevity
}
```

**Usage:**

```java
OrderCreatedEvent event = new OrderCreatedEvent(
    "OrderCreated", "order-123", "cust-456", 99.99, "USD", System.currentTimeMillis()
);

ProduceResult result = ProbeJavaDsl.produceEventBlocking(
    testId, "orders", key, event, Map.of(), OrderCreatedEvent.class
);
```

---

### Example 2: Avro Inventory Event

```java
package com.yourcompany.events;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;

public class InventoryEvent implements SpecificRecord {

    public static final Schema SCHEMA = new Schema.Parser().parse(
        "{" +
        "  \"type\": \"record\"," +
        "  \"name\": \"InventoryEvent\"," +
        "  \"namespace\": \"com.yourcompany.events\"," +
        "  \"fields\": [" +
        "    {\"name\": \"eventType\", \"type\": \"string\"}," +
        "    {\"name\": \"inventoryId\", \"type\": \"string\"}," +
        "    {\"name\": \"productId\", \"type\": \"string\"}," +
        "    {\"name\": \"quantity\", \"type\": \"int\"}," +
        "    {\"name\": \"warehouse\", \"type\": \"string\"}," +
        "    {\"name\": \"timestamp\", \"type\": \"long\"}" +
        "  ]" +
        "}"
    );

    private String eventType;
    private String inventoryId;
    private String productId;
    private Integer quantity;
    private String warehouse;
    private Long timestamp;

    public InventoryEvent() {}

    // SpecificRecord implementation, getters, setters, builder omitted for brevity

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String eventType = "InventoryEvent";
        private String inventoryId;
        private String productId;
        private Integer quantity;
        private String warehouse;
        private Long timestamp;

        public Builder setEventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder setInventoryId(String inventoryId) {
            this.inventoryId = inventoryId;
            return this;
        }

        public Builder setProductId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder setQuantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder setWarehouse(String warehouse) {
            this.warehouse = warehouse;
            return this;
        }

        public Builder setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public InventoryEvent build() {
            InventoryEvent event = new InventoryEvent();
            event.setEventType(eventType);
            event.setInventoryId(inventoryId);
            event.setProductId(productId);
            event.setQuantity(quantity);
            event.setWarehouse(warehouse);
            event.setTimestamp(timestamp);
            return event;
        }
    }
}
```

**Usage:**

```java
InventoryEvent event = InventoryEvent.newBuilder()
    .setInventoryId("inv-123")
    .setProductId("prod-456")
    .setQuantity(100)
    .setWarehouse("warehouse-1")
    .setTimestamp(System.currentTimeMillis())
    .build();

ProduceResult result = ProbeJavaDsl.produceEventBlocking(
    testId, "inventory", key, event, Map.of(), InventoryEvent.class
);
```

---

### Example 3: Protobuf Metrics Event

```java
package com.yourcompany.events;

import com.google.protobuf.DynamicMessage;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;

public class MetricsEvent {

    public static final String SCHEMA_STRING =
        "syntax = \"proto3\";\n" +
        "package com.yourcompany.events;\n" +
        "message MetricsEventProto {\n" +
        "  string event_type = 1;\n" +
        "  string metric_id = 2;\n" +
        "  string metric_name = 3;\n" +
        "  double metric_value = 4;\n" +
        "  string unit = 5;\n" +
        "  int64 timestamp = 6;\n" +
        "}";

    public static final ProtobufSchema SCHEMA = new ProtobufSchema(SCHEMA_STRING);

    private String eventType;
    private String metricId;
    private String metricName;
    private Double metricValue;
    private String unit;
    private Long timestamp;

    public MetricsEvent() {}

    // toDynamicMessage, fromDynamicMessage, getters, setters, builder omitted for brevity

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String eventType = "MetricsEvent";
        private String metricId;
        private String metricName;
        private Double metricValue;
        private String unit;
        private Long timestamp;

        public Builder setEventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder setMetricId(String metricId) {
            this.metricId = metricId;
            return this;
        }

        public Builder setMetricName(String metricName) {
            this.metricName = metricName;
            return this;
        }

        public Builder setMetricValue(Double metricValue) {
            this.metricValue = metricValue;
            return this;
        }

        public Builder setUnit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public MetricsEvent build() {
            return new MetricsEvent(eventType, metricId, metricName, metricValue, unit, timestamp);
        }
    }
}
```

**Usage:**

```java
MetricsEvent event = MetricsEvent.newBuilder()
    .setMetricId("metric-123")
    .setMetricName("response_time")
    .setMetricValue(42.5)
    .setUnit("ms")
    .setTimestamp(System.currentTimeMillis())
    .build();

DynamicMessage dynamicMessage = event.toDynamicMessage(MetricsEvent.SCHEMA);

ProduceResult result = ProbeJavaDsl.produceEventBlocking(
    testId, "metrics", key, dynamicMessage, Map.of(), DynamicMessage.class
);
```

---

## Related Documentation

- **Java DSL API Reference:** [docs/api/probe-java-dsl-api.md](../api/probe-java-dsl-api.md)
- **Java Step Definitions Guide:** [docs/user-guide/java-step-definitions-guide.md](./java-step-definitions-guide.md)
- **Module README:** [test-probe-java-api/README.md](../../test-probe-java-api/README.md)

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-26 | Initial release with JSON, Avro, Protobuf examples |
