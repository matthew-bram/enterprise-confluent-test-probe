# Getting Started with Test-Probe

Welcome to Test-Probe! This guide will walk you through everything you need to know to start testing your event-driven architecture with Kafka.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Installation](#installation)
3. [Building the Framework](#building-the-framework)
4. [Your First Test (5 minutes)](#your-first-test-5-minutes)
5. [Understanding the Framework](#understanding-the-framework)
6. [Writing Tests](#writing-tests)
7. [Advanced Topics](#advanced-topics)

---

## Prerequisites

Before you begin, ensure you have the following installed:

### Required Software

| Software | Version | Purpose |
|----------|---------|---------|
| **Java JDK** | 21+ | Runtime environment (OpenJDK, Oracle JDK, or Amazon Corretto) |
| **Maven** | 3.9+ | Build tool (or use included wrapper `./mvnw`) |
| **Docker** | 4.x+ | Container runtime for Testcontainers |

### Docker Setup

Test-Probe uses Testcontainers to spin up real Kafka instances during testing. You need Docker installed and running:

**Option 1: Docker Desktop** (Easiest)
```bash
# Download from: https://www.docker.com/products/docker-desktop
# After installation, verify:
docker --version
docker ps
```

**Option 2: Colima** (Free alternative for macOS)
```bash
brew install colima
colima start
docker --version
```

**Option 3: Rancher Desktop** (Free, cross-platform)
```bash
# Download from: https://rancherdesktop.io/
# Configure: Preferences → Container Runtime → dockerd (moby)
docker --version
```

**Docker Resource Requirements**:
- **Minimum**: 8GB RAM allocated to Docker
- **Recommended**: 16GB RAM for parallel test execution
- **Disk Space**: 20GB free space recommended

For detailed Docker setup instructions, see [Kafka Environment Setup](../../docs/testing-practice/kafka-env-setup.md).

### Verify Prerequisites

```bash
# Check Java version (should be 21+)
java -version

# Check Maven version (should be 3.9+)
mvn -version

# Check Docker is running
docker ps

# Expected: Empty list or running containers (no errors)
```

---

## Installation

### Option 1: Add to Existing Maven Project

Add Test-Probe dependencies to your `pom.xml`:

<details>
<summary><strong>Java Project</strong> (Click to expand)</summary>

```xml
<project>
  <properties>
    <test-probe.version>0.0.1-SNAPSHOT</test-probe.version>
    <scala.version>3.3.6</scala.version>
    <scala.binary.version>3</scala.binary.version>
  </properties>

  <dependencies>
    <!-- Test-Probe Core Framework -->
    <dependency>
      <groupId>io.distia.probe</groupId>
      <artifactId>test-probe-core</artifactId>
      <version>${test-probe.version}</version>
      <scope>test</scope>
    </dependency>

    <!-- Test-Probe Cucumber Integration -->
    <dependency>
      <groupId>io.distia.probe</groupId>
      <artifactId>test-probe-glue</artifactId>
      <version>${test-probe.version}</version>
      <scope>test</scope>
    </dependency>

    <!-- JUnit 5 (optional, for Java tests) -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.1</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <!-- Cucumber Maven Plugin -->
      <plugin>
        <groupId>io.cucumber</groupId>
        <artifactId>cucumber-maven-plugin</artifactId>
        <version>7.14.1</version>
        <executions>
          <execution>
            <id>cucumber-tests</id>
            <phase>test</phase>
            <goals>
              <goal>verify</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <features>src/test/resources/features</features>
          <glue>io.distia.probe.glue</glue>
          <plugins>
            <plugin>json:target/cucumber-reports/cucumber.json</plugin>
            <plugin>html:target/cucumber-reports/cucumber.html</plugin>
          </plugins>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```
</details>

<details>
<summary><strong>Scala Project</strong> (Click to expand)</summary>

```xml
<project>
  <properties>
    <test-probe.version>0.0.1-SNAPSHOT</test-probe.version>
    <scala.version>3.3.6</scala.version>
    <scala.binary.version>3</scala.binary.version>
  </properties>

  <dependencies>
    <!-- Test-Probe Core Framework -->
    <dependency>
      <groupId>io.distia.probe</groupId>
      <artifactId>test-probe-core</artifactId>
      <version>${test-probe.version}</version>
      <scope>test</scope>
    </dependency>

    <!-- Test-Probe Cucumber Integration -->
    <dependency>
      <groupId>io.distia.probe</groupId>
      <artifactId>test-probe-glue</artifactId>
      <version>${test-probe.version}</version>
      <scope>test</scope>
    </dependency>

    <!-- ScalaTest (optional, for Scala tests) -->
    <dependency>
      <groupId>org.scalatest</groupId>
      <artifactId>scalatest_${scala.binary.version}</artifactId>
      <version>3.2.17</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <!-- Scala Maven Plugin -->
      <plugin>
        <groupId>net.alchim31.maven</groupId>
        <artifactId>scala-maven-plugin</artifactId>
        <version>4.8.1</version>
        <executions>
          <execution>
            <goals>
              <goal>compile</goal>
              <goal>testCompile</goal>
            </goals>
          </execution>
        </executions>
      </plugin>

      <!-- Cucumber Maven Plugin -->
      <plugin>
        <groupId>io.cucumber</groupId>
        <artifactId>cucumber-maven-plugin</artifactId>
        <version>7.14.1</version>
        <executions>
          <execution>
            <id>cucumber-tests</id>
            <phase>test</phase>
            <goals>
              <goal>verify</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <features>src/test/resources/features</features>
          <glue>io.distia.probe.glue</glue>
          <plugins>
            <plugin>json:target/cucumber-reports/cucumber.json</plugin>
            <plugin>html:target/cucumber-reports/cucumber.html</plugin>
          </plugins>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```
</details>

### Option 2: Clone Test-Probe Repository

```bash
# Clone the repository
git clone https://github.com/your-org/test-probe.git
cd test-probe

# Build from source
./mvnw clean install -DskipTests

# Run tests to verify
./scripts/test-unit.sh -m core
```

---

## Building the Framework

Before writing tests, you need to bootstrap the Test-Probe framework using the **ServiceDsl builder**. This type-safe builder ensures all required modules are configured at compile time.

### Quick Bootstrap Example

Here's the minimal code to start Test-Probe with default modules:

```scala
import io.distia.probe.core.ServiceDsl
import io.distia.probe.core.builder.modules._
import io.distia.probe.core.builder.ServiceContext

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

// Build the framework with default modules
val serviceContextFuture: Future[ServiceContext] = ServiceDsl()
  .withConfig(DefaultConfig())                        // Configuration
  .withActorSystem(DefaultActorSystem())              // Actor system
  .withInterface(DefaultRestInterface())              // REST API
  .withStorageService(LocalBlockStorageService())     // Local storage
  .withVaultServiceModule(LocalVaultService())        // Local vault (no secrets)
  .build()

// Wait for initialization (typically 5-10 seconds)
val serviceContext = Await.result(serviceContextFuture, 30.seconds)

println(s"✓ Test-Probe started on http://localhost:8080")
```

### What Just Happened?

The builder created:
- **ActorSystem** with GuardianActor (Error Kernel pattern)
- **QueueActor** for test execution management
- **REST API** on port 8080 for test submission
- **Local storage** in `./test-probe-storage/`
- **Local vault** (no encryption, dev only)

### Compile-Time Safety

The builder uses **phantom types** to guarantee all required modules are present:

```scala
// ❌ COMPILE ERROR - Missing VaultService
ServiceDsl()
  .withConfig(DefaultConfig())
  .withActorSystem(DefaultActorSystem())
  .withInterface(DefaultRestInterface())
  .withStorageService(LocalBlockStorageService())
  // .withVaultServiceModule(LocalVaultService())  // <-- MISSING!
  .build()  // Won't compile!
```

The compiler will show:
```
error: Cannot prove that Contains[Features, ProbeVaultService]
.build()
^
```

**This is a FEATURE, not a bug** - impossible states are unrepresentable at runtime!

### Module Order Independence

Add modules in **any order** - the framework executes them in the correct dependency order:

```scala
// Both are equivalent:

// Option 1
ServiceDsl()
  .withConfig(...)
  .withActorSystem(...)
  .withInterface(...)
  .withStorageService(...)
  .withVaultServiceModule(...)
  .build()

// Option 2 (different order, same result)
ServiceDsl()
  .withVaultServiceModule(...)
  .withStorageService(...)
  .withInterface(...)
  .withActorSystem(...)
  .withConfig(...)
  .build()
```

### Production Configuration

For production deployments, use cloud provider modules:

```scala
// AWS Production
val serviceContext = ServiceDsl()
  .withConfig(DefaultConfig())
  .withActorSystem(DefaultActorSystem())
  .withInterface(DefaultRestInterface())
  .withStorageService(AwsBlockStorageService())      // S3 storage
  .withVaultServiceModule(AwsVaultService())         // Secrets Manager
  .build()
```

### Next Steps

For complete builder documentation including:
- All module implementations (AWS, Azure, GCP)
- Custom module creation
- Three-phase lifecycle (preFlight → initialize → finalCheck)
- Production examples with Kubernetes
- Mock modules for testing

See **[Builder Pattern Guide](BUILDER-PATTERN.md)** (comprehensive 1000-line reference).

---

## Your First Test (5 minutes)

Let's create a simple test that produces and consumes a Kafka event.

### Step 1: Create Feature File

Create a new file: `src/test/resources/features/my-first-test.feature`

```gherkin
Feature: My First Kafka Test
  As a developer
  I want to test basic Kafka event production and consumption
  So that I can verify my event-driven system works

  Scenario: Produce and consume a simple JSON event
    Given I have a topic "test-events"
    When I produce event {"eventId": "evt-001", "message": "Hello Kafka!"} to topic "test-events"
    Then I should receive an event from topic "test-events" within 10 seconds
    And the event should match JSONPath "$.message" with value "Hello Kafka!"
```

### Step 2: Run the Test

<details>
<summary><strong>Java</strong></summary>

```java
// src/test/java/com/example/MyFirstTest.java
package com.example;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "io.distia.probe.glue")
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber-reports/cucumber.json")
public class MyFirstTest {
}
```

Run with Maven:
```bash
mvn test
```
</details>

<details>
<summary><strong>Scala</strong></summary>

```scala
// src/test/scala/com/example/MyFirstTest.scala
package com.example

import io.cucumber.junit.{Cucumber, CucumberOptions}
import org.junit.runner.RunWith

@RunWith(classOf[Cucumber])
@CucumberOptions(
  features = Array("classpath:features"),
  glue = Array("io.distia.probe.glue"),
  plugin = Array("pretty", "json:target/cucumber-reports/cucumber.json")
)
class MyFirstTest
```

Run with Maven:
```bash
mvn test
```
</details>

### Step 3: View Results

After running the test, view the results:

```bash
# Console output shows:
# ✓ Scenario: Produce and consume a simple JSON event - PASSED
# ✓ 4 steps passed

# HTML report:
open target/cucumber-reports/cucumber.html

# JSON report (for CI/CD):
cat target/cucumber-reports/cucumber.json
```

**Congratulations!** You've just run your first Test-Probe test with a real Kafka instance.

---

## Understanding the Framework

### Architecture Overview

Test-Probe consists of several layers working together:

```
┌───────────────────────────────────────────┐
│  Your Gherkin Feature Files              │
│  (Business-readable test scenarios)      │
└───────────────┬───────────────────────────┘
                │
┌───────────────▼───────────────────────────┐
│  Step Definitions (Glue Code)            │
│  - Built-in steps (Kafka, JSON, etc.)    │
│  - Custom steps (your domain logic)      │
└───────────────┬───────────────────────────┘
                │
┌───────────────▼───────────────────────────┐
│  Test-Probe Actor System                 │
│  - TestExecutionActor (FSM)               │
│  - KafkaProducerActor                     │
│  - KafkaConsumerActor                     │
│  - CucumberExecutionActor                 │
└───────────────┬───────────────────────────┘
                │
┌───────────────▼───────────────────────────┐
│  Infrastructure (via Testcontainers)     │
│  - Apache Kafka                           │
│  - Confluent Schema Registry              │
│  - (Optional) Vault for secrets           │
└───────────────────────────────────────────┘
```

### Key Concepts

#### 1. Feature Files (Gherkin)
Business-readable test specifications using Given/When/Then syntax.

```gherkin
Feature: Order Processing
  Scenario: Process valid order
    Given I have a topic "orders"
    When I produce event {"orderId": "123", "amount": 99.99} to topic "orders"
    Then I should receive an event from topic "confirmations" within 5 seconds
```

#### 2. Step Definitions
Code that implements each Gherkin step. Test-Probe provides built-in steps for common Kafka operations.

#### 3. Actors
High-performance concurrent components that handle test execution, Kafka operations, and evidence collection.

#### 4. Testcontainers
Automatically spins up Docker containers with Kafka and Schema Registry for each test run.

---

## Writing Tests

### Basic Test Structure

Every Test-Probe test follows this pattern:

```gherkin
Feature: <Feature Name>
  <Feature description>

  Background: <Common setup>
    Given <preconditions that apply to all scenarios>

  Scenario: <Scenario Name>
    Given <initial state>
    When <action>
    Then <expected outcome>
    And <additional verification>
```

### Built-in Step Definitions

Test-Probe includes step definitions for common Kafka testing patterns:

#### Topic Management

```gherkin
Given I have a topic "my-topic"
Given I have a topic "my-topic" with 3 partitions
Given I have topics "topic-1" and "topic-2"
```

#### Event Production

```gherkin
# Simple JSON event
When I produce event {"key": "value"} to topic "my-topic"

# Event with headers
When I produce event {"data": "test"} to topic "my-topic" with headers {"correlation-id": "123"}

# Multiple events
When I produce the following events to topic "my-topic":
  | eventId | message |
  | evt-001 | Hello   |
  | evt-002 | World   |
```

#### Event Consumption

```gherkin
# Consume and verify existence
Then I should receive an event from topic "my-topic" within 10 seconds

# Consume with JSONPath matching
Then I should receive an event matching JSONPath "$.status" with value "completed"

# Consume exactly N events
Then I should receive exactly 5 events from topic "my-topic" within 30 seconds

# Verify event count
Then topic "my-topic" should have at least 10 events
```

#### JSONPath Assertions

```gherkin
# Exact match
And the event should match JSONPath "$.orderId" with value "123"

# Pattern matching
And the event should match JSONPath "$.email" with pattern ".*@example.com"

# Existence check
And the event should have JSONPath "$.metadata.timestamp"

# Nested paths
And the event should match JSONPath "$.customer.address.zipCode" with value "12345"
```

### Example: Complete E-Commerce Test

<details>
<summary><strong>Click to expand complete example</strong></summary>

```gherkin
Feature: E-Commerce Order Processing
  Test the complete order fulfillment flow from order placement to shipping

  Background: Set up topics and infrastructure
    Given I have a topic "orders"
    And I have a topic "inventory"
    And I have a topic "shipping"
    And I have a topic "notifications"

  Scenario: Successful order fulfillment
    # Step 1: Customer places order
    When I produce event to topic "orders":
      """json
      {
        "orderId": "ORD-12345",
        "customerId": "CUST-001",
        "items": [
          {"sku": "WIDGET-001", "quantity": 2, "price": 29.99}
        ],
        "totalAmount": 59.98,
        "timestamp": "2025-01-15T10:30:00Z"
      }
      """

    # Step 2: Inventory system reserves items
    Then I should receive an event from topic "inventory" within 5 seconds
    And the event should match JSONPath "$.orderId" with value "ORD-12345"
    And the event should match JSONPath "$.status" with value "RESERVED"

    # Step 3: Shipping system creates label
    Then I should receive an event from topic "shipping" within 5 seconds
    And the event should match JSONPath "$.orderId" with value "ORD-12345"
    And the event should match JSONPath "$.trackingNumber" matching pattern "^TRACK-.*"

    # Step 4: Customer receives notification
    Then I should receive an event from topic "notifications" within 5 seconds
    And the event should match JSONPath "$.customerId" with value "CUST-001"
    And the event should match JSONPath "$.type" with value "ORDER_CONFIRMED"

  Scenario: Order fails due to insufficient inventory
    # Step 1: Customer places order for out-of-stock item
    When I produce event to topic "orders":
      """json
      {
        "orderId": "ORD-12346",
        "customerId": "CUST-002",
        "items": [
          {"sku": "OUT-OF-STOCK", "quantity": 1, "price": 99.99}
        ],
        "totalAmount": 99.99,
        "timestamp": "2025-01-15T10:31:00Z"
      }
      """

    # Step 2: Inventory system rejects order
    Then I should receive an event from topic "inventory" within 5 seconds
    And the event should match JSONPath "$.orderId" with value "ORD-12346"
    And the event should match JSONPath "$.status" with value "INSUFFICIENT_STOCK"

    # Step 3: Customer receives rejection notification
    Then I should receive an event from topic "notifications" within 5 seconds
    And the event should match JSONPath "$.customerId" with value "CUST-002"
    And the event should match JSONPath "$.type" with value "ORDER_REJECTED"

    # Step 4: No shipping event should be created
    And topic "shipping" should have 0 events with JSONPath "$.orderId" matching "ORD-12346"
```
</details>

---

## Advanced Topics

### Multiple Kafka Clusters

Test-Probe supports testing across multiple Kafka clusters with per-topic bootstrap server configuration:

```gherkin
Feature: Multi-Cluster Event Routing
  Scenario: Route events between clusters
    # Cluster 1: US datacenter
    Given I have a topic "orders-us" with bootstrap servers "kafka-us:9092"

    # Cluster 2: EU datacenter
    And I have a topic "orders-eu" with bootstrap servers "kafka-eu:9092"

    # Produce to US cluster
    When I produce event {"region": "US", "orderId": "123"} to topic "orders-us"

    # Consume from EU cluster (after replication)
    Then I should receive an event from topic "orders-eu" within 10 seconds
    And the event should match JSONPath "$.orderId" with value "123"
```

### Avro Serialization

Test-Probe supports Apache Avro with Schema Registry:

```gherkin
Feature: Avro Event Testing
  Background: Register schemas
    Given CloudEvent Avro key schema is registered for topic "order-events-avro"
    And OrderEvent Avro value schema is registered for topic "order-events-avro"

  Scenario: Produce and consume Avro events
    When I produce Avro event to topic "order-events-avro":
      """json
      {
        "orderId": "123",
        "customerId": "CUST-001",
        "amount": 99.99,
        "status": "PENDING"
      }
      """
    Then I should receive an Avro event from topic "order-events-avro" within 5 seconds
    And the event should match JSONPath "$.orderId" with value "123"
```

**Schema Registration**: Avro schemas are automatically registered with Confluent Schema Registry during test setup.

### Protocol Buffers

Test-Probe supports Protocol Buffers (Protobuf):

```gherkin
Feature: Protobuf Event Testing
  Background: Register schemas
    Given CloudEvent Protobuf key schema is registered for topic "payment-events-proto"
    And PaymentEvent Protobuf value schema is registered for topic "payment-events-proto"

  Scenario: Produce and consume Protobuf events
    When I produce Protobuf event to topic "payment-events-proto":
      """json
      {
        "paymentId": "PAY-001",
        "amount": 149.99,
        "currency": "USD",
        "status": "AUTHORIZED"
      }
      """
    Then I should receive a Protobuf event from topic "payment-events-proto" within 5 seconds
    And the event should match JSONPath "$.paymentId" with value "PAY-001"
```

### Custom Step Definitions

Extend Test-Probe with your own domain-specific steps:

<details>
<summary><strong>Java Example</strong></summary>

```java
package com.example.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.distia.probe.glue.TestContext;

public class CustomSteps {
    private final TestContext testContext;

    public CustomSteps(TestContext testContext) {
        this.testContext = testContext;
    }

    @Given("I have authenticated as user {string}")
    public void authenticateUser(String username) {
        // Your authentication logic
        testContext.put("username", username);
    }

    @When("I create an order with {int} items")
    public void createOrder(int itemCount) {
        // Your order creation logic
        String orderId = generateOrderId();
        String orderJson = buildOrderJson(itemCount, orderId);

        // Use Test-Probe's Kafka producer
        testContext.produceEvent("orders", orderJson);
        testContext.put("orderId", orderId);
    }

    @Then("the order should be in {string} status")
    public void verifyOrderStatus(String expectedStatus) {
        // Your verification logic
        String orderId = testContext.get("orderId");
        // Use Test-Probe's Kafka consumer
        String event = testContext.consumeEvent("order-status",
            "$.orderId", orderId, 10);

        // Assert status matches
        assertJsonPath(event, "$.status", expectedStatus);
    }
}
```
</details>

<details>
<summary><strong>Scala Example</strong></summary>

```scala
package com.example.steps

import io.cucumber.scala.{ScalaDsl, EN}
import io.distia.probe.glue.TestContext

class CustomSteps extends ScalaDsl with EN {
  private var testContext: TestContext = _

  Given("""I have authenticated as user {string}"""") { (username: String) =>
    // Your authentication logic
    testContext.put("username", username)
  }

  When("""I create an order with {int} items""") { (itemCount: Int) =>
    // Your order creation logic
    val orderId = generateOrderId()
    val orderJson = buildOrderJson(itemCount, orderId)

    // Use Test-Probe's Kafka producer
    testContext.produceEvent("orders", orderJson)
    testContext.put("orderId", orderId)
  }

  Then("""the order should be in {string} status""") { (expectedStatus: String) =>
    // Your verification logic
    val orderId = testContext.get("orderId")

    // Use Test-Probe's Kafka consumer
    val event = testContext.consumeEvent("order-status",
      "$.orderId", orderId, 10)

    // Assert status matches
    assertJsonPath(event, "$.status", expectedStatus)
  }
}
```
</details>

### Test Evidence and Reporting

Test-Probe automatically generates comprehensive test evidence:

**Evidence Artifacts**:
- Cucumber JSON reports (`target/cucumber-reports/cucumber.json`)
- HTML reports (`target/cucumber-reports/cucumber.html`)
- Event logs (all produced/consumed events)
- Execution metadata (timestamps, state transitions)

**CI/CD Integration**:
```yaml
# GitHub Actions example
- name: Run Test-Probe tests
  run: mvn test

- name: Publish test results
  uses: EnricoMi/publish-unit-test-result-action@v2
  with:
    files: 'target/cucumber-reports/cucumber.json'

- name: Upload evidence artifacts
  uses: actions/upload-artifact@v3
  with:
    name: test-evidence
    path: target/cucumber-reports/
```

---

## Next Steps

Now that you understand the basics, explore these topics:

1. **[FAQ](FAQ.md)** - Common questions about Test-Probe
2. **[Troubleshooting](TROUBLESHOOTING.md)** - Solutions to common issues
3. **[Product Requirements](../product/PRODUCT-REQUIREMENTS-DOCUMENT.md)** - Complete feature documentation
4. **[Architecture Docs](../architecture/)** - Deep dive into system design

### Example Projects

Browse complete examples in the `examples/` directory:
- `examples/java/` - Java integration examples
- `examples/scala/` - Scala integration examples
- `test-probe-core/src/test/resources/features/integration/` - Real integration tests

### Community Resources

- **GitHub Discussions**: Ask questions and share knowledge
- **Issues**: Report bugs or request features
- **Contributing**: Help improve Test-Probe ([Contributing Guide](../../CONTRIBUTING.md))

---

**Need Help?** See the [Troubleshooting Guide](TROUBLESHOOTING.md) or open a GitHub issue.
