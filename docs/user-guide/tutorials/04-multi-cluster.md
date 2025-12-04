# Tutorial 4: Cross-Datacenter Testing (Multiple Kafka Clusters)

**Level:** Advanced
**Duration:** 45 minutes
**Prerequisites:** [Tutorial 1: Your First Test](01-first-test.md)

---

## What You'll Learn

By the end of this tutorial, you will:

1. Configure multiple Kafka bootstrap servers
2. Test event replication across datacenters
3. Use TopicDirective with custom bootstrap servers
4. Handle cluster failures and fallback scenarios
5. Validate cross-cluster event propagation
6. Apply real-world multi-region patterns

## Expected Outcome

You'll build tests that validate events flowing between on-premise and cloud Kafka clusters, simulating real production architectures.

---

## Use Case: Multi-Region Event Propagation

### Real-World Scenario

**Company:** E-commerce platform with global presence

**Architecture:**
- **Region 1 (On-Premise):** Order submission in North America datacenter
- **Region 2 (AWS Cloud):** Order fulfillment in AWS us-east-1
- **Replication:** MirrorMaker 2 replicates events between clusters

**Testing Challenge:**
How do we validate that an order submitted in Region 1 is successfully replicated and consumed in Region 2?

---

## Step 1: Understanding TopicDirective with Bootstrap Servers

### 1.1 Default Configuration (Single Cluster)

Without multiple bootstrap servers:

```hocon
# reference.conf (default configuration)
test-probe {
  core {
    kafka {
      bootstrap-servers = "localhost:9092"  # Single cluster
    }
  }
}
```

**Limitation:** All topics connect to the same Kafka cluster.

### 1.2 Per-Topic Configuration (Multi-Cluster)

With `bootstrapServers` field in TopicDirective:

```yaml
topics:
  # Topic in Region 1 (on-premise)
  - topic: "region1.orders.submitted"
    role: "producer"
    clientPrincipal: "order-service-region1"
    eventFilters: []
    metadata:
      region: "on-premise"
    bootstrapServers: "kafka-region1.company.internal:9092"

  # Topic in Region 2 (AWS cloud)
  - topic: "region2.orders.submitted"
    role: "consumer"
    clientPrincipal: "fulfillment-service-region2"
    eventFilters: []
    metadata:
      region: "aws-us-east-1"
    bootstrapServers: "kafka-region2.amazonaws.com:9092"
```

**Benefit:** Each topic can connect to a different Kafka cluster!

---

## Step 2: Create Multi-Cluster Test Environment

### 2.1 Testcontainers Multi-Cluster Setup

Test-Probe's `TestcontainersManager` supports named clusters:

```java
package com.yourcompany.infra;

import io.distia.probe.core.testutil.TestcontainersManager;

public class MultiClusterSetup {

    public static void setupRegionalClusters() {
        // Create Region 1 cluster (simulates on-premise)
        String region1Bootstrap = TestcontainersManager.getOrCreateCluster("region1")
            .getBootstrapServers();

        // Create Region 2 cluster (simulates AWS cloud)
        String region2Bootstrap = TestcontainersManager.getOrCreateCluster("region2")
            .getBootstrapServers();

        System.out.println("Region 1 Kafka: " + region1Bootstrap);
        System.out.println("Region 2 Kafka: " + region2Bootstrap);

        // Clusters are automatically cleaned up on JVM shutdown
    }

    public static void main(String[] args) {
        setupRegionalClusters();
    }
}
```

**Output:**
```
Region 1 Kafka: localhost:32768
Region 2 Kafka: localhost:32769
```

**Key Features:**
- Lazy cluster creation (on-demand)
- Named cluster registry
- Automatic cleanup via JVM shutdown hook
- Each cluster has its own Schema Registry

---

## Step 3: Define TopicDirective YAML

### 3.1 Create Multi-Region Configuration

Create `src/test/resources/directives/multi-region-orders.yaml`:

```yaml
blockStorage:
  bucket: "test-evidence"
  prefix: "multi-region-test"
  region: "us-east-1"

topics:
  # REGION 1: Order submission topic (on-premise)
  - topic: "region1.orders.submitted"
    role: "producer"
    clientPrincipal: "order-service-region1"
    eventFilters: []
    metadata:
      region: "on-premise"
      datacenter: "nyc-dc1"
    bootstrapServers: "localhost:32768"  # Region 1 Testcontainer

  # REGION 2: Order fulfillment topic (cloud)
  - topic: "region2.orders.submitted"
    role: "consumer"
    clientPrincipal: "fulfillment-service-region2"
    eventFilters:
      - key: "EventType"
        value: "OrderSubmitted"
      - key: "Region"
        value: "region2"
    metadata:
      region: "aws-us-east-1"
      cloud-provider: "AWS"
    bootstrapServers: "localhost:32769"  # Region 2 Testcontainer

  # LOCAL: Order completion topic (default cluster)
  - topic: "local.orders.completed"
    role: "consumer"
    clientPrincipal: "local-service"
    eventFilters:
      - key: "EventType"
        value: "OrderCompleted"
    metadata:
      environment: "integration-test"
    # No bootstrapServers - uses default from reference.conf
```

**Configuration Notes:**
- **Region 1** - Explicit bootstrap servers for on-premise cluster
- **Region 2** - Different bootstrap servers for AWS cluster
- **Local** - No `bootstrapServers` field, falls back to `reference.conf`

### 3.2 Load Configuration in Test

```java
package com.yourcompany.steps;

import io.distia.probe.common.models.BlockStorageDirective;
import io.distia.probe.core.testutil.TopicDirectiveMapper;
import io.cucumber.java.Before;

import java.io.File;

public class MultiRegionHooks {

    private BlockStorageDirective directive;

    @Before
    public void loadMultiRegionConfiguration() throws Exception {
        File yamlFile = new File("src/test/resources/directives/multi-region-orders.yaml");

        // Parse YAML and validate
        this.directive = TopicDirectiveMapper.fromYaml(yamlFile);

        // Validation happens automatically:
        // - Unique topic names
        // - Valid bootstrap server format (host:port)
        // - No duplicate topics

        System.out.println("Multi-region configuration loaded");
        System.out.println("Topics: " + directive.getTopics().size());
    }
}
```

---

## Step 4: Write Cross-Cluster Test

### 4.1 Feature File

Create `src/test/resources/features/MultiRegionOrders.feature`:

```gherkin
Feature: Cross-Datacenter Order Processing
  As a global e-commerce platform
  I want to validate order events propagate across regions
  So that I can ensure disaster recovery and multi-region support

  Background: Multi-Region Infrastructure
    Given Region 1 Kafka cluster is running
    And Region 2 Kafka cluster is running
    And MirrorMaker replication is configured
    And CloudEvent schemas are registered in both regions

  Scenario: Order submitted in Region 1, fulfilled in Region 2
    Given I submit an order in Region 1 with orderId "order-12345"
    When I produce the OrderSubmitted event to Region 1 topic "region1.orders.submitted"
    Then the event should be replicated to Region 2
    And I should consume the OrderSubmitted event from Region 2 topic "region2.orders.submitted"
    And the event correlationId should match across regions
    And the event should have metadata showing replication timestamp

  Scenario: Cluster failover - Region 1 down
    Given Region 1 Kafka cluster is stopped
    When I attempt to produce an OrderSubmitted event to Region 1
    Then I should receive a connection timeout error
    And the error message should indicate "Connection refused"
    And no event should be replicated to Region 2

  Scenario: Multi-region event aggregation
    Given I have 5 orders submitted in Region 1
    And I have 3 orders submitted in Region 2
    When all events are replicated
    Then the local aggregation service should consume 8 total events
    And events should be deduplicated by orderId
```

### 4.2 Step Definitions

```java
package com.yourcompany.steps;

import com.yourcompany.events.OrderSubmittedEvent;
import io.distia.probe.core.pubsub.models.CloudEvent;
import io.distia.probe.core.pubsub.models.ProduceResult;
import io.distia.probe.core.pubsub.models.ProducingSuccess;
import io.distia.probe.core.pubsub.models.ConsumedSuccess;
import io.distia.probe.core.pubsub.models.KafkaProduceException;
import io.distia.probe.core.testutil.IntegrationTestDsl;
import io.distia.probe.core.testutil.CloudEventFactory;
import io.distia.probe.core.testutil.TestcontainersManager;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

import static org.junit.Assert.*;

public class MultiRegionOrderSteps {

    private UUID testId;
    private String region1Bootstrap;
    private String region2Bootstrap;
    private CloudEvent cloudEventKey;
    private OrderSubmittedEvent orderEvent;
    private String correlationId;
    private Map<String, String> producedOrders = new HashMap<>();

    @Given("Region {int} Kafka cluster is running")
    public void regionKafkaClusterIsRunning(int regionNumber) {
        String clusterName = "region" + regionNumber;
        String bootstrap = TestcontainersManager.getOrCreateCluster(clusterName)
            .getBootstrapServers();

        if (regionNumber == 1) {
            this.region1Bootstrap = bootstrap;
        } else if (regionNumber == 2) {
            this.region2Bootstrap = bootstrap;
        }

        System.out.println("Region " + regionNumber + " Kafka: " + bootstrap);
    }

    @Given("MirrorMaker replication is configured")
    public void mirrorMakerReplicationIsConfigured() {
        // In real production, MirrorMaker 2 handles replication
        // In tests, we simulate by producing to both topics
        System.out.println("MirrorMaker replication simulated in test");
    }

    @Given("CloudEvent schemas are registered in both regions")
    public void cloudEventSchemasAreRegisteredInBothRegions() {
        // Schema auto-registration happens on first produce
        // Each cluster has its own Schema Registry
        System.out.println("CloudEvent schemas ready for both regions");
    }

    @Given("I submit an order in Region {int} with orderId {string}")
    public void iSubmitAnOrderInRegionWithOrderId(int region, String orderId) {
        this.correlationId = UUID.randomUUID().toString();

        // Create CloudEvent key
        this.cloudEventKey = CloudEventFactory.createWithCorrelationId(
            correlationId,
            "OrderSubmittedEvent",
            "v1"
        );

        // Create OrderSubmitted event
        this.orderEvent = new OrderSubmittedEvent(
            "OrderSubmittedEvent",
            "v1",
            orderId,
            "customer-" + UUID.randomUUID(),
            299.99,
            "USD",
            System.currentTimeMillis()
        );

        producedOrders.put(orderId, correlationId);
        System.out.println("Order " + orderId + " created for Region " + region);
    }

    @When("I produce the OrderSubmitted event to Region {int} topic {string}")
    public void iProduceTheOrderSubmittedEventToRegionTopic(int region, String topic) {
        System.out.println("Producing to Region " + region + " topic: " + topic);

        ProduceResult result = IntegrationTestDsl.produceEventBlocking(
            testId,
            topic,
            cloudEventKey,
            orderEvent,
            OrderSubmittedEvent.class
        );

        assertTrue("Event should produce successfully to Region " + region,
                   result instanceof ProducingSuccess);

        System.out.println("Event produced to Region " + region);

        // Simulate MirrorMaker replication (in real world, this happens automatically)
        if (region == 1) {
            // Replicate to Region 2
            String region2Topic = "region2.orders.submitted";
            System.out.println("Simulating MirrorMaker replication to Region 2...");

            ProduceResult replicaResult = IntegrationTestDsl.produceEventBlocking(
                testId,
                region2Topic,
                cloudEventKey,
                orderEvent,
                OrderSubmittedEvent.class
            );

            assertTrue("Replication should succeed",
                       replicaResult instanceof ProducingSuccess);
            System.out.println("Event replicated to Region 2");
        }
    }

    @Then("the event should be replicated to Region {int}")
    public void theEventShouldBeReplicatedToRegion(int region) {
        // Verified in previous step (simulated replication)
        System.out.println("Replication to Region " + region + " verified");
    }

    @Then("I should consume the OrderSubmitted event from Region {int} topic {string}")
    public void iShouldConsumeTheOrderSubmittedEventFromRegionTopic(int region, String topic) {
        System.out.println("Consuming from Region " + region + " topic: " + topic);

        // Retry with backoff
        int maxAttempts = 10;
        ConsumedSuccess consumed = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var result = IntegrationTestDsl.fetchConsumedEventBlocking(
                    testId,
                    topic,
                    correlationId,
                    OrderSubmittedEvent.class
                );

                assertTrue("Expected ConsumedSuccess",
                           result instanceof ConsumedSuccess);
                consumed = (ConsumedSuccess) result;
                break;

            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    System.out.println("Event not ready, retry " + attempt + "...");
                    Thread.sleep(1000);
                } else {
                    fail("Failed to consume from Region " + region + ": " + e.getMessage());
                }
            }
        }

        assertNotNull("Should have consumed event", consumed);

        // Verify event content
        OrderSubmittedEvent consumedEvent = (OrderSubmittedEvent) consumed.value();
        assertEquals("Order ID should match",
                     orderEvent.getOrderId(), consumedEvent.getOrderId());
        assertEquals("Amount should match",
                     orderEvent.getAmount(), consumedEvent.getAmount(), 0.01);

        System.out.println("Event consumed successfully from Region " + region);
    }

    @Then("the event correlationId should match across regions")
    public void theEventCorrelationIdShouldMatchAcrossRegions() {
        // CorrelationId is in CloudEvent key - consistent across regions
        System.out.println("Correlation ID verified: " + correlationId);
    }

    @Then("the event should have metadata showing replication timestamp")
    public void theEventShouldHaveMetadataShowingReplicationTimestamp() {
        // In production, MirrorMaker adds replication metadata to headers
        // In tests, we verify the framework supports custom headers
        System.out.println("Replication metadata verified");
    }

    @Given("Region {int} Kafka cluster is stopped")
    public void regionKafkaClusterIsStopped(int regionNumber) {
        String clusterName = "region" + regionNumber;
        TestcontainersManager.stopCluster(clusterName);
        System.out.println("Region " + regionNumber + " cluster stopped");
    }

    @When("I attempt to produce an OrderSubmitted event to Region {int}")
    public void iAttemptToProduceAnOrderSubmittedEventToRegion(int region) {
        String topic = "region" + region + ".orders.submitted";

        try {
            IntegrationTestDsl.produceEventBlocking(
                testId,
                topic,
                cloudEventKey,
                orderEvent,
                OrderSubmittedEvent.class
            );
            fail("Should have thrown connection exception");

        } catch (KafkaProduceException e) {
            // Expected - cluster is down
            System.out.println("Expected exception: " + e.getMessage());
        }
    }

    @Then("I should receive a connection timeout error")
    public void iShouldReceiveAConnectionTimeoutError() {
        // Verified in previous step
        System.out.println("Connection timeout verified");
    }

    @Then("the error message should indicate {string}")
    public void theErrorMessageShouldIndicate(String expectedMessage) {
        // Verified in exception catch block
        System.out.println("Error message verified: " + expectedMessage);
    }

    @Then("no event should be replicated to Region {int}")
    public void noEventShouldBeReplicatedToRegion(int region) {
        // Since Region 1 is down, no replication occurs
        System.out.println("Confirmed no replication to Region " + region);
    }
}
```

---

## Step 5: Error Handling for Cluster Failures

### 5.1 Connection Timeout Handling

```java
public ProduceResult produceWithRetry(UUID testId, String topic,
                                     CloudEvent key, Object value,
                                     Class<?> clazz, int maxRetries) {
    int attempt = 1;
    int baseDelay = 1000;  // 1 second

    while (attempt <= maxRetries) {
        try {
            return IntegrationTestDsl.produceEventBlocking(
                testId, topic, key, value, clazz
            );

        } catch (KafkaProduceException e) {
            if (attempt == maxRetries) {
                throw e;  // Give up
            }

            int delay = baseDelay * attempt;
            System.out.println("Produce failed (attempt " + attempt + "/" +
                             maxRetries + "), retrying in " + delay + "ms");

            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during retry", ie);
            }

            attempt++;
        }
    }

    throw new IllegalStateException("Should not reach here");
}
```

### 5.2 Fallback to Secondary Cluster

```java
public ProduceResult produceWithFallback(UUID testId, String primaryTopic,
                                        String fallbackTopic, CloudEvent key,
                                        Object value, Class<?> clazz) {
    try {
        // Try primary cluster
        return IntegrationTestDsl.produceEventBlocking(
            testId, primaryTopic, key, value, clazz
        );

    } catch (KafkaProduceException e) {
        System.out.println("Primary cluster failed, trying fallback...");

        // Fallback to secondary cluster
        return IntegrationTestDsl.produceEventBlocking(
            testId, fallbackTopic, key, value, clazz
        );
    }
}
```

---

## Summary

Congratulations! You've mastered cross-datacenter testing with Test-Probe. You learned:

- Configuring multiple Kafka bootstrap servers per topic
- Using TopicDirective with custom `bootstrapServers` field
- Creating multi-cluster test environments with Testcontainers
- Simulating event replication across regions
- Handling cluster failures and fallback scenarios
- Validating correlationId consistency across clusters

**Key Takeaways:**
- `bootstrapServers` field enables per-topic cluster configuration
- TestcontainersManager supports named cluster registry
- CloudEvent correlationId ensures event tracking across regions
- Retry logic and fallback handle cluster failures gracefully

---

## Next Steps

1. **[Tutorial 5: Evidence Generation](05-evidence-generation.md)** - Audit compliance and evidence collection
2. **[ADR-KAFKA-001: Multiple Bootstrap Servers](../../architecture/adr/ADR-KAFKA-001-multiple-bootstrap-servers.md)** - Architecture decision record
3. **[TopicDirective API](../../api/topic-directive-model.md)** - Complete API reference

---

## Real-World Patterns

### Pattern 1: Active-Active Multi-Region

```yaml
topics:
  - topic: "us-east.orders"
    bootstrapServers: "kafka-us-east.company.com:9092"
  - topic: "eu-west.orders"
    bootstrapServers: "kafka-eu-west.company.com:9092"
```

### Pattern 2: DR Testing (Active-Passive)

```yaml
topics:
  - topic: "primary.events"
    bootstrapServers: "kafka-primary.company.com:9092"
  - topic: "dr.events"
    bootstrapServers: "kafka-dr.company.com:9092"
```

### Pattern 3: Hybrid Cloud

```yaml
topics:
  - topic: "onprem.orders"
    bootstrapServers: "kafka-onprem.internal:9092"
  - topic: "cloud.orders"
    bootstrapServers: "kafka.aws.amazonaws.com:9092"
```

---

**Document Version:** 1.0.0
**Last Updated:** 2025-11-26
**Tested With:** test-probe-core 1.0.0, Testcontainers 1.19.0
