# Test-Probe Frequently Asked Questions (FAQ)

Common questions and answers about using Test-Probe for event-driven architecture testing.

## Table of Contents

- [General Questions](#general-questions)
- [Installation and Setup](#installation-and-setup)
- [Kafka Questions](#kafka-questions)
- [Serialization](#serialization)
- [Testing Strategies](#testing-strategies)
- [Performance](#performance)
- [CI/CD Integration](#cicd-integration)

---

## General Questions

### What is Test-Probe?

Test-Probe is an enterprise-grade testing framework for Apache Kafka and event-driven architectures. It enables teams to write BDD-style tests using Gherkin (Given/When/Then syntax) to validate event flows across distributed systems.

### Why should I use Test-Probe instead of writing custom test code?

**Test-Probe provides:**
- Connect to your existing Kafka clusters (no infrastructure setup)
- Built-in step definitions for common Kafka operations
- High-performance actor-based execution (concurrent tests)
- Automatic test evidence generation for compliance
- Multi-format serialization (JSON, Avro, Protobuf) out-of-the-box
- Multi-cluster support for complex topologies
- Multi-cloud vault integration (AWS, Azure, GCP)

**Without Test-Probe, you'd need to:**
- Write boilerplate producer/consumer code for each test
- Implement JSONPath matching and event verification
- Handle async event polling and timeouts
- Generate test reports manually
- Build your own credential management

### What technologies does Test-Probe use?

- **Apache Pekko Typed Actors**: High-performance concurrent execution
- **Cucumber/Gherkin**: Business-readable test specifications
- **Apache Kafka**: Native Kafka client integration
- **Confluent Schema Registry**: Avro and Protobuf schema management
- **Multi-cloud Vaults**: AWS Secrets Manager, Azure Key Vault, GCP Secret Manager
- **Scala 3.3.6**: Type-safe implementation

### Is Test-Probe production-ready?

Test-Probe is currently in **active development** (version 0.0.1-SNAPSHOT). The core modules are complete with 1,272 tests (100% passing), including 15 end-to-end integration tests. It's ready for evaluation and pilot projects but not recommended for production use until version 1.0.

### What languages can I use with Test-Probe?

Test-Probe supports both **Java** and **Scala**:
- Write feature files in Gherkin (language-agnostic)
- Use built-in step definitions (no coding required)
- Extend with custom step definitions in Java or Scala
- Run tests from JUnit (Java) or ScalaTest (Scala)

---

## Installation and Setup

### What are the system requirements?

**Required:**
- Java JDK 21+ (OpenJDK, Oracle JDK, or Amazon Corretto)
- Maven 3.9+
- Access to a Kafka cluster (Confluent Platform, AWS MSK, Azure Event Hubs, etc.)
- Schema Registry access (Confluent Schema Registry)

**Recommended:**
- Vault access for secure credential management (AWS Secrets Manager, Azure Key Vault, or GCP Secret Manager)
- Evidence storage configured (S3, Azure Blob, GCS, or local filesystem)

### Do I need Docker to use Test-Probe?

**No**, Docker is not required to use Test-Probe. Users connect directly to their real Kafka clusters.

> **Note for contributors**: If you're contributing to Test-Probe framework development, Docker is required for running the framework's internal component tests. See [CONTRIBUTING.md](../../CONTRIBUTING.md) for development setup.

### Do I need Kubernetes to use Test-Probe?

**No**, Kubernetes is not required. Test-Probe connects directly to your Kafka clusters via bootstrap servers.

Kubernetes is only relevant if your Kafka cluster happens to be deployed on Kubernetes, but Test-Probe doesn't need to know about your cluster's deployment method.

### How do I add Test-Probe to my existing Maven project?

Add these dependencies to your `pom.xml`:

```xml
<dependency>
  <groupId>io.distia.probe</groupId>
  <artifactId>test-probe-core</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <scope>test</scope>
</dependency>

<dependency>
  <groupId>io.distia.probe</groupId>
  <artifactId>test-probe-glue</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

See [Getting Started - Installation](GETTING-STARTED.md#installation) for complete setup.

---

## Kafka Questions

### What versions of Kafka does Test-Probe support?

Test-Probe uses **Apache Kafka 3.x** client libraries and is tested against:
- Kafka 3.6.x
- Kafka 3.7.x (via Confluent Platform 7.6.0)

It should work with any Kafka 3.x cluster.

### How do I connect to my Kafka cluster?

Test-Probe is designed to connect to your real Kafka clusters. Configure your connection in `application.conf`:

```hocon
test-probe {
  kafka {
    bootstrap-servers = "my-kafka-cluster:9092"
    schema-registry-url = "https://schema-registry:8081"
  }
}
```

Or override via environment variables:
```bash
export KAFKA_BOOTSTRAP_SERVERS="my-kafka-cluster:9092"
```

See [Getting Started - Connecting to Your Kafka Cluster](GETTING-STARTED.md#connecting-to-your-kafka-cluster) for authentication examples.

### How do I test multiple Kafka clusters?

Test-Probe supports multi-cluster testing with per-topic bootstrap server configuration:

```gherkin
Feature: Multi-Cluster Testing
  Scenario: Route events between US and EU clusters
    # Produce to US cluster
    Given I have a topic "events-us" with bootstrap servers "kafka-us:9092"
    When I produce event {"region": "US"} to topic "events-us"

    # Consume from EU cluster
    Given I have a topic "events-eu" with bootstrap servers "kafka-eu:9092"
    Then I should receive an event from topic "events-eu" within 10 seconds
```

See [Getting Started - Multiple Kafka Clusters](GETTING-STARTED.md#multiple-kafka-clusters).

### How do I test Kafka Streams applications?

Test-Probe tests Kafka at the producer/consumer level. For Kafka Streams:

1. **External black-box testing** (recommended):
   - Produce events to input topics
   - Consume events from output topics
   - Verify transformations via output events

2. **Topology testing** (use Kafka Streams Test Utils):
   - Test stream topology separately
   - Use Test-Probe for end-to-end integration tests

### Do I need to create topics manually?

No, Test-Probe automatically creates topics when you use:

```gherkin
Given I have a topic "my-topic"
```

Topics are created with default settings (1 partition, replication factor 1). For custom configuration:

```gherkin
Given I have a topic "my-topic" with 3 partitions
```

### How do I clean up topics between tests?

When testing against real clusters, implement cleanup in `@After` hooks:

```java
@After
public void cleanup() {
    // Delete test topics
    adminClient.deleteTopics(Arrays.asList("test-topic-1", "test-topic-2"));
}
```

**Best practices for integration environments:**
- Use unique topic names per test run (e.g., `test-orders-{timestamp}`)
- Use dedicated test namespaces/prefixes (e.g., `sit-test-*`)
- Configure topic retention for automatic cleanup

---

## Serialization

### What serialization formats does Test-Probe support?

Test-Probe supports three serialization formats:

1. **JSON** (default): Simple key-value JSON serialization
2. **Apache Avro**: Schema-based binary serialization
3. **Protocol Buffers**: Google's binary serialization format

All formats are tested in the integration test suite.

### How do I use Avro serialization?

1. Register schemas in your feature file:
```gherkin
Background:
  Given CloudEvent Avro key schema is registered for topic "events-avro"
  And MyEvent Avro value schema is registered for topic "events-avro"
```

2. Produce Avro events:
```gherkin
When I produce Avro event {"field1": "value1"} to topic "events-avro"
```

3. Consume Avro events:
```gherkin
Then I should receive an Avro event from topic "events-avro" within 5 seconds
```

Schemas are automatically registered with Confluent Schema Registry during test setup.

### Where do I define Avro/Protobuf schemas?

Schemas are defined in:
- **Avro**: `src/main/avro/*.avsc`
- **Protobuf**: `src/main/proto/*.proto`

Test-Probe uses SerdesFactory to load and register schemas automatically.

### Can I use custom serializers?

Yes, Test-Probe supports custom serializers via configuration. However, the built-in serializers (JSON, Avro, Protobuf) cover most use cases.

### How do I test schema evolution?

1. Register version 1 schema:
```gherkin
Given OrderEvent Avro schema version 1 is registered for topic "orders"
```

2. Produce events with version 1:
```gherkin
When I produce Avro event {"orderId": "123", "amount": 99.99} to topic "orders"
```

3. Register version 2 schema (with new field):
```gherkin
And OrderEvent Avro schema version 2 is registered for topic "orders"
```

4. Produce events with version 2:
```gherkin
When I produce Avro event {"orderId": "124", "amount": 199.99, "currency": "USD"} to topic "orders"
```

5. Verify consumer handles both versions:
```gherkin
Then I should receive 2 events from topic "orders" within 10 seconds
```

---

## Testing Strategies

### Should I write unit tests or integration tests?

**Both!** Test-Probe fits into the testing pyramid:

1. **Unit Tests** (70%): Fast, isolated tests of business logic
   - Test individual components without Kafka
   - Use mocks for external dependencies
   - Run in milliseconds

2. **Integration Tests** (20%): Test against real Kafka clusters
   - Test Kafka producer/consumer logic
   - Verify serialization/deserialization
   - Validate event flows in SIT/UAT environments
   - Run in seconds to minutes

3. **End-to-End Tests** (10%): Full system validation
   - Test complete event flows across services
   - Verify cross-cell/cross-service contracts
   - Run in CD pipelines

Test-Probe is primarily for **integration and end-to-end tests** against real clusters.

### How long do tests take to run?

**Typical execution times**:
- Per test scenario: 10-60 seconds (depends on event flow complexity)
- Full test suite: Varies by environment and network latency

**Performance tips**:
- Run unit tests frequently (fast feedback)
- Run integration tests before commits
- Run full test suite in CI/CD pipelines
- Consider network latency to your Kafka cluster

### Can I run tests in parallel?

Yes, Test-Probe supports parallel test execution:

```xml
<!-- pom.xml -->
<configuration>
  <parallel>scenarios</parallel>
  <threadCount>4</threadCount>
</configuration>
```

**Considerations for parallel execution**:
- Ensure your Kafka cluster can handle concurrent connections
- Use unique consumer group IDs per test thread
- Consider topic partitioning for parallel consumption

### How do I test asynchronous event flows?

Use timeouts in your assertions:

```gherkin
# Wait up to 30 seconds for event
Then I should receive an event from topic "async-results" within 30 seconds

# Poll multiple times
Then I should receive exactly 5 events from topic "batch-results" within 60 seconds
```

Test-Probe polls Kafka continuously until the condition is met or timeout expires.

### How do I test event ordering?

Verify event order by consuming multiple events and checking sequence:

```gherkin
Scenario: Verify event ordering
  When I produce the following events to topic "ordered-events":
    | eventId | sequence |
    | evt-001 | 1        |
    | evt-002 | 2        |
    | evt-003 | 3        |

  Then I should receive 3 events from topic "ordered-events" within 10 seconds
  And the events should be in order by JSONPath "$.sequence"
```

**Note**: Kafka only guarantees ordering within a single partition. Use the same key for events that must be ordered.

---

## Performance

### Why are my tests slow?

Common causes:

1. **Network latency**: Distance to your Kafka cluster
   - Solution: Use a cluster in the same region as your CI/CD runners

2. **Long timeouts**: Tests waiting for events that never arrive
   - Solution: Reduce timeouts or fix event production logic

3. **Sequential execution**: Running tests one at a time
   - Solution: Enable parallel execution

4. **Cluster throttling**: Kafka cluster rate limiting
   - Solution: Check cluster quotas and adjust test concurrency

See [Troubleshooting](TROUBLESHOOTING.md) for more solutions.

### How can I speed up tests?

**Development workflow**:
```bash
# Fast: Unit tests only (~30 seconds)
./scripts/test-unit.sh -m core

# Integration tests: Against your cluster
mvn test -Pintegration
```

**CI/CD optimizations**:
- Use a Kafka cluster close to your CI/CD runners
- Run tests in parallel
- Use faster CI runners (more CPU/RAM)
- Reuse Kafka connections across tests

### How many concurrent tests can I run?

This depends on your Kafka cluster capacity:
- Check your cluster's connection limits
- Monitor consumer group count
- Consider partition count per topic

Test-Probe's actor-based architecture supports high concurrency, limited mainly by your cluster's capacity.

---

## CI/CD Integration

### How do I integrate Test-Probe with GitHub Actions?

```yaml
name: Test
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run Test-Probe tests
        run: ./scripts/build-ci.sh

      - name: Upload test results
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: target/cucumber-reports/
```

### How do I integrate with GitLab CI?

```yaml
test:
  stage: test
  image: maven:3.9-eclipse-temurin-21
  variables:
    KAFKA_BOOTSTRAP_SERVERS: $CI_KAFKA_BOOTSTRAP_SERVERS
    SCHEMA_REGISTRY_URL: $CI_SCHEMA_REGISTRY_URL
    VAULT_PROVIDER: aws
    VAULT_SECRET_ID: kafka-credentials
  script:
    - mvn test -Pintegration
  artifacts:
    reports:
      junit: '**/target/surefire-reports/TEST-*.xml'
```

Configure `CI_KAFKA_BOOTSTRAP_SERVERS` and `CI_SCHEMA_REGISTRY_URL` as CI/CD variables pointing to your integration environment.

### How do I publish test reports?

Test-Probe generates standard Cucumber reports:

**JSON report** (for CI/CD):
```
target/cucumber-reports/cucumber.json
```

**HTML report** (for humans):
```
target/cucumber-reports/cucumber.html
```

**JUnit XML** (for test aggregators):
```
target/surefire-reports/TEST-*.xml
```

Use your CI/CD platform's test report integration to visualize results.

### Should I commit test evidence to Git?

**No**, evidence files should be excluded from Git:

```gitignore
# .gitignore
target/
target/cucumber-reports/
*.log
```

Instead:
- Upload evidence as CI/CD artifacts
- Store long-term evidence in S3 (production feature, coming soon)

---

## Still Have Questions?

- **Check**: [Troubleshooting Guide](TROUBLESHOOTING.md) for common issues
- **Read**: [Getting Started Guide](GETTING-STARTED.md) for detailed tutorials
- **Browse**: [Architecture Documentation](../architecture/) for system design
- **Ask**: Open a GitHub Discussion or Issue

**Need help right now?** Check the troubleshooting guide or open an issue with:
- Test-Probe version
- Feature file (Gherkin)
- Error message and stack trace
- Kafka cluster type and version
