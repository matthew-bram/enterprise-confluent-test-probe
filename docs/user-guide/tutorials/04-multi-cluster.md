# Tutorial 4: Multi-Cluster Testing

**Level:** Advanced
**Duration:** 30 minutes
**Prerequisites:** [Tutorial 1: Your First Test](01-first-test.md)

---

## What You'll Learn

By the end of this tutorial, you will:

1. Configure multiple Kafka clusters in test-config.yaml
2. Write tests that span multiple clusters
3. Test event replication across datacenters
4. Validate cross-cluster event correlation
5. Apply real-world multi-region patterns

## Expected Outcome

You'll write tests that validate event flows across multiple Kafka clusters, simulating production multi-region architectures.

---

## Prerequisites

Before starting, ensure:

- Completed [Tutorial 1](01-first-test.md)
- Access to Test-Probe service
- **Multiple Kafka clusters configured in Test-Probe** (provided by your platform team)

---

## Use Case: Multi-Region Architecture

### The Challenge

Modern enterprises deploy Kafka across multiple regions for:

- **Disaster Recovery (DR):** Failover capability
- **Low Latency:** Process events close to users
- **Data Residency:** Comply with regional regulations
- **High Availability:** Active-active deployments

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Test-Probe Service                          │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
           ┌───────────────────┼───────────────────┐
           │                   │                   │
           ▼                   ▼                   ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  Kafka Cluster  │  │  Kafka Cluster  │  │  Kafka Cluster  │
│    US-EAST      │  │    EU-WEST      │  │    APAC         │
│  (Primary)      │◀─┤  (Replicated)   │  │  (Replicated)   │
└─────────────────┘  └─────────────────┘  └─────────────────┘
         │                   │                    │
         └───────────────────┼────────────────────┘
                             │
                    MirrorMaker 2 / Cluster Linking
```

---

## Step 1: Configure Multiple Clusters

### 1.1 Define Clusters in test-config.yaml

```yaml
# test-config.yaml
test-probe:
  # Define multiple Kafka clusters
  clusters:
    # Primary cluster (US East)
    us-east:
      name: "US East Primary"
      bootstrap-servers: "kafka-us-east.example.com:9092"
      schema-registry-url: "https://sr-us-east.example.com"

    # Secondary cluster (EU West)
    eu-west:
      name: "EU West Secondary"
      bootstrap-servers: "kafka-eu-west.example.com:9092"
      schema-registry-url: "https://sr-eu-west.example.com"

    # APAC cluster
    apac:
      name: "APAC Region"
      bootstrap-servers: "kafka-apac.example.com:9092"
      schema-registry-url: "https://sr-apac.example.com"

  # Default cluster for topics without explicit cluster
  default-cluster: us-east

  serialization:
    key-format: cloudevents
    value-format: json

  test:
    timeout-seconds: 120
```

### 1.2 Configure Topic-to-Cluster Mapping

```yaml
# test-config.yaml (continued)
test-probe:
  topics:
    # US East topics
    - name: orders-us
      cluster: us-east
      description: "Orders originating in US"

    - name: orders-replicated-us
      cluster: us-east
      description: "Orders replicated from other regions"

    # EU West topics
    - name: orders-eu
      cluster: eu-west
      description: "Orders originating in EU"

    - name: orders-replicated-eu
      cluster: eu-west
      description: "Orders replicated from US"

    # APAC topics
    - name: orders-apac
      cluster: apac
      description: "Orders originating in APAC"
```

---

## Step 2: Write Cross-Cluster Tests

### 2.1 Basic Cross-Cluster Event Flow

Create `features/cross-cluster.feature`:

```gherkin
Feature: Cross-Cluster Event Replication
  Validate events replicate correctly between Kafka clusters

  Background:
    Given cluster "us-east" is configured
    And cluster "eu-west" is configured

  Scenario: Order event replicates from US to EU
    # Produce to US cluster
    Given I have a topic "orders-us" on cluster "us-east"
    When I produce a JSON event to topic "orders-us":
      """
      {
        "orderId": "ORD-US-001",
        "customerId": "CUST-US-100",
        "region": "US-EAST",
        "amount": 299.99,
        "timestamp": 1733407800000
      }
      """
    Then I should receive an event from topic "orders-us" within 10 seconds

    # Verify replication to EU cluster
    Given I have a topic "orders-replicated-eu" on cluster "eu-west"
    Then I should receive an event from topic "orders-replicated-eu" within 30 seconds
    And the event should match JSONPath "$.orderId" with value "ORD-US-001"
    And the event should match JSONPath "$.region" with value "US-EAST"
```

### 2.2 Bi-Directional Replication

```gherkin
  Scenario: Events replicate in both directions
    # Order created in EU
    Given I have a topic "orders-eu" on cluster "eu-west"
    When I produce a JSON event to topic "orders-eu":
      """
      {
        "orderId": "ORD-EU-001",
        "customerId": "CUST-EU-200",
        "region": "EU-WEST",
        "amount": 149.99
      }
      """
    Then I should receive an event from topic "orders-eu" within 10 seconds

    # Verify appears in US
    Given I have a topic "orders-replicated-us" on cluster "us-east"
    Then I should receive an event from topic "orders-replicated-us" within 30 seconds
    And the event should match JSONPath "$.orderId" with value "ORD-EU-001"

    # Order created in US
    Given I have a topic "orders-us" on cluster "us-east"
    When I produce a JSON event to topic "orders-us":
      """
      {
        "orderId": "ORD-US-002",
        "customerId": "CUST-US-300",
        "region": "US-EAST",
        "amount": 199.99
      }
      """

    # Verify appears in EU
    Given I have a topic "orders-replicated-eu" on cluster "eu-west"
    Then I should receive an event from topic "orders-replicated-eu" within 30 seconds
    And the event should match JSONPath "$.orderId" with value "ORD-US-002"
```

---

## Step 3: Cross-Cluster Event Correlation

### 3.1 Track Events Across Clusters

Use correlation IDs to track events as they flow between clusters:

```gherkin
Feature: Cross-Cluster Event Correlation
  Track individual events across multiple clusters

  Scenario: Track order through multi-region processing
    # Generate unique correlation ID for this test
    Given I have correlation ID "test-corr-001"

    # Step 1: Create order in US
    Given I have a topic "orders-us" on cluster "us-east"
    When I produce a JSON event with correlation "test-corr-001" to topic "orders-us":
      """
      {
        "orderId": "ORD-TRACK-001",
        "customerId": "CUST-GLOBAL",
        "amount": 599.99,
        "status": "CREATED"
      }
      """
    Then I should receive an event with correlation "test-corr-001" from topic "orders-us" within 10 seconds

    # Step 2: Verify replication to EU
    Given I have a topic "orders-replicated-eu" on cluster "eu-west"
    Then I should receive an event with correlation "test-corr-001" from topic "orders-replicated-eu" within 30 seconds
    And the event should match JSONPath "$.status" with value "CREATED"

    # Step 3: EU processing updates status
    Given I have a topic "order-status-eu" on cluster "eu-west"
    When I produce a JSON event with correlation "test-corr-001" to topic "order-status-eu":
      """
      {
        "orderId": "ORD-TRACK-001",
        "status": "PROCESSING",
        "processedBy": "EU-FULFILLMENT"
      }
      """

    # Step 4: Verify status update replicates back to US
    Given I have a topic "order-status-replicated-us" on cluster "us-east"
    Then I should receive an event with correlation "test-corr-001" from topic "order-status-replicated-us" within 30 seconds
    And the event should match JSONPath "$.status" with value "PROCESSING"
    And the event should match JSONPath "$.processedBy" with value "EU-FULFILLMENT"
```

### 3.2 Validate Correlation ID Consistency

```gherkin
  Scenario: Correlation ID preserved across clusters
    Given I have correlation ID "preserve-test-001"

    # Produce with specific CloudEvent correlation ID
    When I produce a JSON event with correlation "preserve-test-001" to topic "orders-us" on cluster "us-east":
      """
      {"orderId": "ORD-PRESERVE-001", "amount": 99.99}
      """

    # Verify same correlation ID in replicated event
    Then I should receive an event from topic "orders-replicated-eu" on cluster "eu-west" within 30 seconds
    And the event key should match JSONPath "$.correlationid" with value "preserve-test-001"
```

---

## Step 4: Replication Timing Tests

### 4.1 Measure Replication Latency

```gherkin
Feature: Replication Latency Validation
  Ensure replication meets SLA requirements

  Scenario: Replication completes within SLA
    Given I have a topic "orders-us" on cluster "us-east"
    And I have a topic "orders-replicated-eu" on cluster "eu-west"

    When I produce a JSON event to topic "orders-us":
      """
      {
        "orderId": "ORD-SLA-001",
        "timestamp": 1733407800000
      }
      """

    # SLA: Replication should complete within 30 seconds
    Then I should receive an event from topic "orders-replicated-eu" within 30 seconds
    And the event should match JSONPath "$.orderId" with value "ORD-SLA-001"

  Scenario: High-priority replication within 10 seconds
    Given I have a topic "priority-orders-us" on cluster "us-east"
    And I have a topic "priority-orders-eu" on cluster "eu-west"

    When I produce a JSON event to topic "priority-orders-us":
      """
      {
        "orderId": "ORD-PRIORITY-001",
        "priority": "HIGH"
      }
      """

    # Priority topics should replicate faster
    Then I should receive an event from topic "priority-orders-eu" within 10 seconds
```

### 4.2 Batch Replication Validation

```gherkin
  Scenario: All events in batch replicate correctly
    Given I have a topic "orders-us" on cluster "us-east"

    When I produce the following JSON events to topic "orders-us":
      | orderId       | amount  |
      | ORD-BATCH-001 | 100.00  |
      | ORD-BATCH-002 | 200.00  |
      | ORD-BATCH-003 | 300.00  |
      | ORD-BATCH-004 | 400.00  |
      | ORD-BATCH-005 | 500.00  |

    # All 5 events should replicate
    Then I should receive 5 events from topic "orders-replicated-eu" on cluster "eu-west" within 60 seconds
```

---

## Step 5: Real-World Patterns

### 5.1 Active-Active Multi-Region

```yaml
# test-config.yaml for active-active
test-probe:
  clusters:
    region-a:
      bootstrap-servers: "kafka-a.example.com:9092"
    region-b:
      bootstrap-servers: "kafka-b.example.com:9092"

  topics:
    - name: global-orders
      cluster: region-a
    - name: global-orders
      cluster: region-b
```

```gherkin
Feature: Active-Active Multi-Region
  Both regions can accept orders simultaneously

  Scenario: Orders from both regions merge correctly
    # Order from Region A
    When I produce a JSON event to topic "global-orders" on cluster "region-a":
      """
      {"orderId": "ORD-A-001", "region": "A", "amount": 100.00}
      """

    # Order from Region B (same time)
    When I produce a JSON event to topic "global-orders" on cluster "region-b":
      """
      {"orderId": "ORD-B-001", "region": "B", "amount": 200.00}
      """

    # Both appear in Region A's merged topic
    Then I should receive 2 events from topic "global-orders-merged" on cluster "region-a" within 60 seconds

    # Both appear in Region B's merged topic
    Then I should receive 2 events from topic "global-orders-merged" on cluster "region-b" within 60 seconds
```

### 5.2 Disaster Recovery (DR) Testing

```gherkin
Feature: Disaster Recovery Validation
  Ensure DR cluster can take over when primary fails

  Scenario: DR cluster receives all replicated data
    # Produce batch to primary
    Given I have a topic "orders" on cluster "primary"
    When I produce the following JSON events to topic "orders":
      | orderId   | critical |
      | ORD-DR-01 | true     |
      | ORD-DR-02 | true     |
      | ORD-DR-03 | true     |

    # Verify all critical data replicated to DR
    Given I have a topic "orders" on cluster "dr"
    Then I should receive 3 events from topic "orders" on cluster "dr" within 60 seconds
    And all events should match JSONPath "$.critical" with value true

  Scenario: DR cluster has correct consumer offsets
    # This validates Cluster Linking / MirrorMaker offset sync
    Given I have consumer group "order-processor" on cluster "primary"
    And I have consumer group "order-processor" on cluster "dr"

    When I check consumer group offset on cluster "primary"
    And I check consumer group offset on cluster "dr"

    Then the DR offset should be within 100 messages of primary
```

### 5.3 Hybrid Cloud (On-Prem + Cloud)

```yaml
# test-config.yaml for hybrid
test-probe:
  clusters:
    onprem:
      name: "On-Premises Datacenter"
      bootstrap-servers: "kafka.internal.company.com:9092"

    cloud:
      name: "AWS Cloud"
      bootstrap-servers: "kafka.us-east-1.aws.company.com:9092"
```

```gherkin
Feature: Hybrid Cloud Event Flow
  Validate events flow between on-prem and cloud

  Scenario: Order created on-prem, fulfilled in cloud
    # Order enters system on-prem
    Given I have a topic "orders" on cluster "onprem"
    When I produce a JSON event to topic "orders":
      """
      {
        "orderId": "ORD-HYBRID-001",
        "source": "retail-store",
        "amount": 249.99
      }
      """

    # Order replicates to cloud for fulfillment
    Given I have a topic "orders-to-fulfill" on cluster "cloud"
    Then I should receive an event from topic "orders-to-fulfill" within 30 seconds
    And the event should match JSONPath "$.source" with value "retail-store"

    # Fulfillment status returns to on-prem
    Given I have a topic "fulfillment-status" on cluster "cloud"
    When I produce a JSON event to topic "fulfillment-status":
      """
      {
        "orderId": "ORD-HYBRID-001",
        "status": "SHIPPED",
        "carrier": "UPS"
      }
      """

    Given I have a topic "fulfillment-status-local" on cluster "onprem"
    Then I should receive an event from topic "fulfillment-status-local" within 30 seconds
    And the event should match JSONPath "$.status" with value "SHIPPED"
```

---

## Step 6: Submit and Run

### 6.1 Deploy Multi-Cluster Test

```bash
# Initialize test session
TEST_ID=$(curl -sf -X POST "${TEST_PROBE_URL}/api/v1/test/initialize" | jq -r '.["test-id"]')

# Upload test assets
aws s3 cp ./features/ "s3://${BUCKET}/tests/${TEST_ID}/features/" --recursive
aws s3 cp ./test-config.yaml "s3://${BUCKET}/tests/${TEST_ID}/test-config.yaml"

# Start test
curl -sf -X POST "${TEST_PROBE_URL}/api/v1/test/start" \
  -H "Content-Type: application/json" \
  -d "{
    \"test-id\": \"${TEST_ID}\",
    \"block-storage-path\": \"s3://${BUCKET}/tests/${TEST_ID}\",
    \"test-type\": \"integration\"
  }"

# Poll for completion (longer timeout for cross-cluster)
MAX_WAIT=300  # 5 minutes for replication tests
# ... polling loop ...
```

---

## Troubleshooting

### Issue: Cluster Not Accessible

**Symptom:** `Failed to connect to cluster: eu-west`

**Fix:**
- Verify cluster is configured in Test-Probe service
- Check network connectivity from Test-Probe to cluster
- Verify bootstrap servers are correct

### Issue: Replication Timeout

**Symptom:** `Timed out waiting for event on replicated topic`

**Fix:**
- Increase timeout for replication scenarios (30-60 seconds)
- Verify MirrorMaker/Cluster Linking is running
- Check replication lag metrics

### Issue: Event Missing on Replicated Topic

**Symptom:** Event appears in source but not destination

**Fix:**
- Check topic naming conventions (MirrorMaker may prefix topics)
- Verify replication configuration includes the topic
- Check for replication filters that may exclude events

---

## Summary

You've learned multi-cluster testing in Test-Probe:

- Configuring multiple Kafka clusters in test-config.yaml
- Writing tests that span clusters
- Tracking events with correlation IDs
- Validating replication timing and consistency
- Testing real-world patterns (active-active, DR, hybrid)

**Key Takeaways:**
- Use correlation IDs to track events across clusters
- Allow extra time for replication in assertions
- Test both directions in bi-directional setups
- Validate all data replicates in DR scenarios

---

## Next Steps

Continue your Test-Probe journey:

1. **[CI/CD Integration](../integration/ci-cd-pipelines.md)** - Automate multi-cluster tests
2. **[Troubleshooting Guide](../TROUBLESHOOTING.md)** - Common issues and solutions
3. **[Getting Started](../GETTING-STARTED.md)** - Review core concepts

---

**Document Version:** 2.0.0
**Last Updated:** 2025-12-05
