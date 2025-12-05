# Test-Probe Java Quickstart

A minimal example demonstrating the Test-Probe dependency.

## Prerequisites

- **Java 21** (OpenJDK or Eclipse Temurin)
- **Maven 3.9+**

## Quick Start

```bash
# 1. Build and install test-probe to local Maven repo (from test-probe root)
cd /path/to/test-probe
mvn clean install -DskipTests

# 2. Run the example
cd examples/java-quickstart
mvn compile exec:java
```

Expected output:
```
========================================
  Test-Probe Java Quickstart
========================================

Test-Probe dependency loaded successfully!

Available APIs:
  - ProbeJavaDsl: Java DSL for produce/consume
  - CloudEvent: Kafka message key model
  - ProduceResult/ConsumedResult: Operation results

[OK] ProbeJavaDsl available
[OK] CloudEvent available
[OK] ProduceResult available
[OK] ConsumedResult available
[OK] Pekko ActorSystem available
[OK] Cucumber available

See test-probe-core integration tests for full examples.
========================================
```

## Usage

Add the test-probe dependency to your project:

```xml
<dependency>
    <groupId>io.distia.probe</groupId>
    <artifactId>test-probe</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Note

This is a **minimal example** that verifies the test-probe dependency resolves correctly.

For full integration testing examples with:
- Kafka produce/consume workflows
- JSON, Avro, and Protobuf serialization
- BDD step definitions
- Docker Compose infrastructure

See the **integration tests** in `test-probe-core/src/test/`:
- `IntegrationProduceConsumeSteps.java` - Step definitions
- `resources/features/integration/` - Feature files

## API Reference

The main Java API is `ProbeJavaDsl`:

```java
import io.distia.probe.javaapi.ProbeJavaDsl;
import io.distia.probe.core.pubsub.models.CloudEvent;

// Register actor system (once at startup)
ProbeJavaDsl.registerSystem(actorSystem);

// Produce event
ProduceResult result = ProbeJavaDsl.produceEventBlocking(
    testId, "topic-name", cloudEventKey, payload, headers, PayloadClass.class
);

// Consume event
ConsumedResult consumed = ProbeJavaDsl.fetchConsumedEventBlocking(
    testId, "topic-name", correlationId, PayloadClass.class
);
```

---

**Last Updated**: 2025-12-01
