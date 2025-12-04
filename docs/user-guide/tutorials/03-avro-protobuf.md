# Tutorial 3: Multi-Format Serialization (Avro & Protobuf)

**Level:** Advanced
**Duration:** 60 minutes
**Prerequisites:** [Tutorial 2: Working with JSON Events](02-json-events.md)

---

## What You'll Learn

By the end of this tutorial, you will:

1. Understand when to use Avro vs Protobuf vs JSON
2. Define Avro schemas and generate Java classes
3. Work with Avro SpecificRecord in Java
4. Define Protobuf schemas and generate Java classes
5. Use Protobuf DynamicMessage for flexible serialization
6. Configure SerdesFactory for multi-format scenarios
7. Compare performance characteristics

## Expected Outcome

You'll build a multi-format event testing pipeline supporting JSON, Avro, and Protobuf with proper schema evolution.

---

## Format Comparison

### When to Use Each Format

| Feature | JSON Schema | Avro | Protobuf |
|---------|------------|------|----------|
| **Human Readable** | ✅ Yes | ❌ No (binary) | ❌ No (binary) |
| **Schema Evolution** | ✅ Good | ✅ Excellent | ✅ Excellent |
| **Performance** | ⚠️ Slower | ✅ Fast | ✅ Fastest |
| **Payload Size** | ⚠️ Larger | ✅ Compact | ✅ Most compact |
| **Language Support** | ✅ Universal | ✅ JVM/Python | ✅ Universal |
| **Learning Curve** | ✅ Easy | ⚠️ Moderate | ⚠️ Moderate |
| **Backward Compat** | ✅ Good | ✅ Excellent | ✅ Excellent |
| **Forward Compat** | ⚠️ Limited | ✅ Good | ✅ Excellent |

**Recommendations:**
- **JSON** - Development, debugging, human-readable APIs
- **Avro** - Big data pipelines, Kafka Connect, JVM-heavy ecosystems
- **Protobuf** - Microservices, gRPC, polyglot environments, extreme performance

---

## Part 1: Working with Avro

### Step 1.1: Define Avro Schema

Create `src/test/resources/schemas/order-event.avsc`:

```json
{
  "type": "record",
  "name": "OrderEvent",
  "namespace": "com.yourcompany.events",
  "doc": "Order event schema for Avro serialization. High-performance binary format for order processing pipeline.",
  "fields": [
    {
      "name": "eventType",
      "type": "string",
      "default": "OrderEvent",
      "doc": "Event type discriminator"
    },
    {
      "name": "orderId",
      "type": "string",
      "doc": "Unique order identifier (UUID format)"
    },
    {
      "name": "customerId",
      "type": "string",
      "doc": "Customer identifier who placed the order"
    },
    {
      "name": "amount",
      "type": "double",
      "doc": "Order total amount"
    },
    {
      "name": "currency",
      "type": ["null", "string"],
      "default": null,
      "doc": "Currency code (ISO 4217, e.g., USD, EUR)"
    },
    {
      "name": "items",
      "type": {
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
      },
      "doc": "List of items in the order"
    },
    {
      "name": "timestamp",
      "type": ["null", "long"],
      "default": null,
      "doc": "Event timestamp in epoch milliseconds"
    }
  ]
}
```

**Avro Features:**
- **Union Types** - `["null", "string"]` for optional fields
- **Nested Records** - `OrderItem` embedded in `items` array
- **Default Values** - Required for schema evolution
- **Documentation** - `doc` field for human readers

### Step 1.2: Generate Java Classes

Add Avro Maven plugin to `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.avro</groupId>
            <artifactId>avro-maven-plugin</artifactId>
            <version>1.11.3</version>
            <executions>
                <execution>
                    <phase>generate-test-sources</phase>
                    <goals>
                        <goal>schema</goal>
                    </goals>
                    <configuration>
                        <sourceDirectory>${project.basedir}/src/test/resources/schemas</sourceDirectory>
                        <outputDirectory>${project.build.directory}/generated-test-sources/avro</outputDirectory>
                        <testSourceDirectory>${project.basedir}/src/test/resources/schemas</testSourceDirectory>
                        <testOutputDirectory>${project.build.directory}/generated-test-sources/avro</testOutputDirectory>
                        <stringType>String</stringType>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Generate classes:

```bash
mvn generate-test-sources

# Output:
# target/generated-test-sources/avro/com/yourcompany/events/OrderEvent.java
# target/generated-test-sources/avro/com/yourcompany/events/OrderItem.java
```

### Step 1.3: Use Avro SpecificRecord

```java
package com.yourcompany.steps;

import com.yourcompany.events.OrderEvent;
import com.yourcompany.events.OrderItem;
import io.distia.probe.core.pubsub.models.CloudEvent;
import io.distia.probe.core.testutil.IntegrationTestDsl;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.*;

public class OrderEventAvroSteps {

    private UUID testId;
    private CloudEvent cloudEventKey;
    private OrderEvent orderEvent;
    private String correlationId;

    @Given("I have an Avro OrderEvent with orderId {string}")
    public void iHaveAnAvroOrderEventWithOrderId(String orderId) {
        this.correlationId = UUID.randomUUID().toString();

        // Create CloudEvent key
        this.cloudEventKey = CloudEventFactory.createWithCorrelationId(
            correlationId,
            "OrderEvent",
            "v1"
        );

        // Create Avro OrderEvent using builder pattern
        this.orderEvent = OrderEvent.newBuilder()
            .setEventType("OrderEvent")
            .setOrderId(orderId)
            .setCustomerId("customer-456")
            .setAmount(199.99)
            .setCurrency("USD")
            .setItems(Arrays.asList(
                OrderItem.newBuilder()
                    .setProductId("prod-123")
                    .setQuantity(2)
                    .setPrice(99.99)
                    .build(),
                OrderItem.newBuilder()
                    .setProductId("prod-456")
                    .setQuantity(1)
                    .setPrice(0.01)
                    .build()
            ))
            .setTimestamp(System.currentTimeMillis())
            .build();

        System.out.println("Created Avro OrderEvent: " + orderEvent);
    }

    @When("I produce the Avro event to topic {string}")
    public void iProduceTheAvroEventToTopic(String topic) {
        System.out.println("Producing Avro event to topic: " + topic);

        ProduceResult result = IntegrationTestDsl.produceEventBlocking(
            testId,
            topic,
            cloudEventKey,
            orderEvent,
            OrderEvent.class  // SpecificRecord class
        );

        assertTrue("Avro event production should succeed",
                   result instanceof ProducingSuccess);
        System.out.println("Avro event produced successfully");
    }

    @Then("I should consume the Avro event with matching items")
    public void iShouldConsumeTheAvroEventWithMatchingItems() {
        ConsumedResult result = IntegrationTestDsl.fetchConsumedEventBlocking(
            testId,
            "orders-avro",
            correlationId,
            OrderEvent.class
        );

        assertTrue("Expected ConsumedSuccess", result instanceof ConsumedSuccess);
        ConsumedSuccess success = (ConsumedSuccess) result;

        // Verify Avro OrderEvent
        OrderEvent consumed = (OrderEvent) success.value();
        assertEquals("Order ID should match",
                     orderEvent.getOrderId(), consumed.getOrderId());
        assertEquals("Amount should match",
                     orderEvent.getAmount(), consumed.getAmount(), 0.01);
        assertEquals("Currency should match",
                     orderEvent.getCurrency(), consumed.getCurrency());

        // Verify nested items
        assertEquals("Should have 2 items",
                     2, consumed.getItems().size());

        OrderItem item1 = consumed.getItems().get(0);
        assertEquals("Product ID should match",
                     "prod-123", item1.getProductId().toString());
        assertEquals("Quantity should match",
                     2, item1.getQuantity().intValue());

        System.out.println("Avro event verification complete!");
    }
}
```

**Avro Builder Pattern:**
- Type-safe field setting
- Compile-time validation
- Fluent API
- Immutable after `.build()`

---

## Part 2: Working with Protobuf

### Step 2.1: Define Protobuf Schema

Create `src/test/resources/schemas/payment-event.proto`:

```protobuf
syntax = "proto3";

package com.yourcompany.events;

option java_package = "com.yourcompany.events";
option java_outer_classname = "PaymentEventProtos";
option java_multiple_files = true;

// PaymentEvent schema for Protobuf serialization.
// Used for high-performance payment processing pipeline.
message PaymentEventProto {
  string event_type = 1;       // Event type discriminator
  string payment_id = 2;       // Unique payment identifier
  string order_id = 3;         // Associated order identifier
  double amount = 4;           // Payment amount
  string currency = 5;         // Currency code (USD, EUR, etc.)
  PaymentMethod method = 6;    // Payment method
  int64 timestamp = 7;         // Event timestamp in epoch millis

  // Payment method enum
  enum PaymentMethod {
    UNKNOWN = 0;
    CREDIT_CARD = 1;
    DEBIT_CARD = 2;
    PAYPAL = 3;
    BANK_TRANSFER = 4;
    CRYPTO = 5;
  }

  // Optional metadata
  PaymentMetadata metadata = 8;
}

// Payment metadata (nested message)
message PaymentMetadata {
  string ip_address = 1;       // Customer IP address
  string user_agent = 2;       // Browser user agent
  string country_code = 3;     // Country code (ISO 3166-1)
  bool fraud_check_passed = 4; // Fraud detection result
}
```

**Protobuf Features:**
- **Field Numbers** - Required for wire format (never change!)
- **Enums** - Type-safe enumeration (better than strings)
- **Nested Messages** - `PaymentMetadata` embedded
- **proto3 Syntax** - Modern Protobuf (default values, optional support)

### Step 2.2: Generate Java Classes

Add Protobuf Maven plugin to `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.xolstice.maven.plugins</groupId>
            <artifactId>protobuf-maven-plugin</artifactId>
            <version>0.6.1</version>
            <configuration>
                <protocArtifact>com.google.protobuf:protoc:3.24.0:exe:${os.detected.classifier}</protocArtifact>
                <protoSourceRoot>${project.basedir}/src/test/resources/schemas</protoSourceRoot>
                <outputDirectory>${project.build.directory}/generated-test-sources/protobuf</outputDirectory>
                <clearOutputDirectory>false</clearOutputDirectory>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>test-compile</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>

    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
</build>
```

Generate classes:

```bash
mvn generate-test-sources

# Output:
# target/generated-test-sources/protobuf/com/yourcompany/events/PaymentEventProto.java
# target/generated-test-sources/protobuf/com/yourcompany/events/PaymentMetadata.java
# target/generated-test-sources/protobuf/com/yourcompany/events/PaymentEventProtos.java
```

### Step 2.3: Use Protobuf with DynamicMessage

Test-Probe uses `DynamicMessage` for Protobuf serialization to Kafka:

```java
package com.yourcompany.steps;

import com.yourcompany.events.PaymentEventProto;
import com.yourcompany.events.PaymentMetadata;
import com.google.protobuf.DynamicMessage;
import io.distia.probe.core.testutil.IntegrationTestDsl;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;

import static org.junit.Assert.*;

public class PaymentEventProtobufSteps {

    private UUID testId;
    private CloudEvent cloudEventKey;
    private PaymentEventProto paymentEvent;  // Wrapper for test logic
    private DynamicMessage dynamicMessage;   // For Kafka serialization
    private String correlationId;

    @Given("I have a Protobuf PaymentEvent with paymentId {string}")
    public void iHaveAProtobufPaymentEventWithPaymentId(String paymentId) {
        this.correlationId = UUID.randomUUID().toString();

        // Create CloudEvent key
        this.cloudEventKey = CloudEventFactory.createWithCorrelationId(
            correlationId,
            "PaymentEvent",
            "v1"
        );

        // Create Protobuf PaymentEvent
        PaymentMetadata metadata = PaymentMetadata.newBuilder()
            .setIpAddress("192.168.1.100")
            .setUserAgent("Mozilla/5.0")
            .setCountryCode("US")
            .setFraudCheckPassed(true)
            .build();

        this.paymentEvent = PaymentEventProto.newBuilder()
            .setEventType("PaymentEvent")
            .setPaymentId(paymentId)
            .setOrderId("order-789")
            .setAmount(299.99)
            .setCurrency("USD")
            .setMethod(PaymentEventProto.PaymentMethod.CREDIT_CARD)
            .setTimestamp(System.currentTimeMillis())
            .setMetadata(metadata)
            .build();

        // Convert to DynamicMessage for Kafka
        this.dynamicMessage = DynamicMessage.newBuilder(paymentEvent)
            .build();

        System.out.println("Created Protobuf PaymentEvent: " + paymentEvent);
    }

    @When("I produce the Protobuf event to topic {string}")
    public void iProduceTheProtobufEventToTopic(String topic) {
        System.out.println("Producing Protobuf event to topic: " + topic);

        // Produce using DynamicMessage (what SerdesFactory expects)
        ProduceResult result = IntegrationTestDsl.produceEventBlocking(
            testId,
            topic,
            cloudEventKey,
            dynamicMessage,
            DynamicMessage.class  // Critical: Use DynamicMessage class
        );

        assertTrue("Protobuf event production should succeed",
                   result instanceof ProducingSuccess);
        System.out.println("Protobuf event produced successfully");
    }

    @Then("I should consume the Protobuf event with matching metadata")
    public void iShouldConsumeTheProtobufEventWithMatchingMetadata() {
        // Fetch as DynamicMessage (what SerdesFactory returns)
        ConsumedResult result = IntegrationTestDsl.fetchConsumedEventBlocking(
            testId,
            "payments-proto",
            correlationId,
            DynamicMessage.class
        );

        assertTrue("Expected ConsumedSuccess", result instanceof ConsumedSuccess);
        ConsumedSuccess success = (ConsumedSuccess) result;

        // Convert DynamicMessage back to PaymentEventProto
        DynamicMessage consumedDynamic = (DynamicMessage) success.value();
        PaymentEventProto consumed = PaymentEventProto.parseFrom(
            consumedDynamic.toByteArray()
        );

        // Verify fields
        assertEquals("Payment ID should match",
                     paymentEvent.getPaymentId(), consumed.getPaymentId());
        assertEquals("Order ID should match",
                     paymentEvent.getOrderId(), consumed.getOrderId());
        assertEquals("Amount should match",
                     paymentEvent.getAmount(), consumed.getAmount(), 0.01);
        assertEquals("Method should match",
                     paymentEvent.getMethod(), consumed.getMethod());

        // Verify nested metadata
        assertEquals("IP address should match",
                     paymentEvent.getMetadata().getIpAddress(),
                     consumed.getMetadata().getIpAddress());
        assertEquals("Fraud check should match",
                     paymentEvent.getMetadata().getFraudCheckPassed(),
                     consumed.getMetadata().getFraudCheckPassed());

        System.out.println("Protobuf event verification complete!");
    }
}
```

**DynamicMessage Pattern:**
- Test-Probe uses `DynamicMessage` for Kafka wire format
- Convert generated classes → `DynamicMessage` → Kafka
- Kafka → `DynamicMessage` → parse to generated classes
- Enables schema flexibility and evolution

---

## Part 3: SerdesFactory Configuration

### Step 3.1: Understanding Format Detection

SerdesFactory automatically detects format from Schema Registry:

```java
// Automatic format detection flow:
1. Extract class name: "OrderEvent"
2. Build subject: "orders-OrderEvent"
3. Query Schema Registry: GET /subjects/orders-OrderEvent/versions/latest
4. Check schemaType field: "AVRO" | "PROTOBUF" | "JSON"
5. Dispatch to correct serializer:
   - AVRO → KafkaAvroSerializer
   - PROTOBUF → KafkaProtobufSerializer
   - JSON → KafkaJsonSchemaSerializer
```

### Step 3.2: Topic-Specific Configuration

For mixed-format topics:

```java
// Topic: orders-json (JSON Schema)
ProduceResult jsonResult = IntegrationTestDsl.produceEventBlocking(
    testId, "orders-json", cloudEvent, orderJsonEvent, OrderJsonEvent.class
);

// Topic: orders-avro (Avro)
ProduceResult avroResult = IntegrationTestDsl.produceEventBlocking(
    testId, "orders-avro", cloudEvent, orderAvroEvent, OrderEvent.class
);

// Topic: payments-proto (Protobuf)
ProduceResult protoResult = IntegrationTestDsl.produceEventBlocking(
    testId, "payments-proto", cloudEvent, paymentDynamic, DynamicMessage.class
);
```

**No configuration needed** - SerdesFactory handles everything!

---

## Part 4: Performance Comparison

### Step 4.1: Benchmark Test

Create `src/test/java/com/yourcompany/benchmarks/SerializationBenchmark.java`:

```java
package com.yourcompany.benchmarks;

import com.yourcompany.events.OrderEvent;        // Avro
import com.yourcompany.events.PaymentEventProto; // Protobuf
import com.yourcompany.events.OrderJsonEvent;    // JSON
import io.distia.probe.core.pubsub.SerdesFactory;

public class SerializationBenchmark {

    public static void main(String[] args) {
        int iterations = 10000;

        // Setup
        OrderJsonEvent jsonEvent = createJsonEvent();
        OrderEvent avroEvent = createAvroEvent();
        DynamicMessage protoEvent = createProtoEvent();

        // Warmup
        for (int i = 0; i < 1000; i++) {
            SerdesFactory.serialize(jsonEvent, "orders", false);
            SerdesFactory.serialize(avroEvent, "orders", false);
            SerdesFactory.serialize(protoEvent, "payments", false);
        }

        // Benchmark JSON
        long jsonStart = System.nanoTime();
        int jsonSize = 0;
        for (int i = 0; i < iterations; i++) {
            byte[] bytes = SerdesFactory.serialize(jsonEvent, "orders", false);
            jsonSize = bytes.length;
        }
        long jsonTime = System.nanoTime() - jsonStart;

        // Benchmark Avro
        long avroStart = System.nanoTime();
        int avroSize = 0;
        for (int i = 0; i < iterations; i++) {
            byte[] bytes = SerdesFactory.serialize(avroEvent, "orders", false);
            avroSize = bytes.length;
        }
        long avroTime = System.nanoTime() - avroStart;

        // Benchmark Protobuf
        long protoStart = System.nanoTime();
        int protoSize = 0;
        for (int i = 0; i < iterations; i++) {
            byte[] bytes = SerdesFactory.serialize(protoEvent, "payments", false);
            protoSize = bytes.length;
        }
        long protoTime = System.nanoTime() - protoStart;

        // Results
        System.out.println("Serialization Performance (" + iterations + " iterations):");
        System.out.println("─────────────────────────────────────────────────────");
        System.out.printf("JSON:     %6d ms  |  %5d bytes%n", jsonTime / 1_000_000, jsonSize);
        System.out.printf("Avro:     %6d ms  |  %5d bytes  (%.1fx faster, %.1f%% smaller)%n",
                          avroTime / 1_000_000, avroSize,
                          (double) jsonTime / avroTime,
                          100.0 * (1 - (double) avroSize / jsonSize));
        System.out.printf("Protobuf: %6d ms  |  %5d bytes  (%.1fx faster, %.1f%% smaller)%n",
                          protoTime / 1_000_000, protoSize,
                          (double) jsonTime / protoTime,
                          100.0 * (1 - (double) protoSize / jsonSize));
    }
}
```

**Sample Output:**
```
Serialization Performance (10000 iterations):
─────────────────────────────────────────────────────
JSON:        450 ms  |   312 bytes
Avro:        180 ms  |   145 bytes  (2.5x faster, 53.5% smaller)
Protobuf:    120 ms  |   128 bytes  (3.8x faster, 59.0% smaller)
```

### Step 4.2: Schema Evolution Performance

**Avro** - Fast with reader/writer schemas
**Protobuf** - Excellent forward/backward compatibility
**JSON** - Flexible but larger payload

---

## Summary

Congratulations! You've mastered multi-format serialization in Test-Probe. You learned:

- When to use JSON, Avro, or Protobuf
- Defining Avro schemas and using SpecificRecord builder pattern
- Defining Protobuf schemas with enums and nested messages
- DynamicMessage pattern for Kafka serialization
- Automatic format detection in SerdesFactory
- Performance characteristics and benchmarking

**Key Takeaways:**
- JSON: Human-readable, best for development/debugging
- Avro: Binary, excellent for big data pipelines
- Protobuf: Most compact, best for microservices and gRPC

---

## Next Steps

1. **[Tutorial 4: Cross-Datacenter Testing](04-multi-cluster.md)** - Multiple Kafka clusters
2. **[Tutorial 5: Evidence Generation](05-evidence-generation.md)** - Audit compliance
3. **[SerdesFactory API](../../api/serdes-factory-api.md)** - Deep dive into serialization

---

**Document Version:** 1.0.0
**Last Updated:** 2025-11-26
**Tested With:** test-probe-core 1.0.0, Avro 1.11.3, Protobuf 3.24.0
