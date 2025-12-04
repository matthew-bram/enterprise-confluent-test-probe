# ADR-STORAGE-008: Topic Directive YAML Format

**Status:** Accepted
**Date:** 2025-10-27 (Updated: 2025-11-22)
**Decision Makers:** Engineering Team
**Related:** ADR-STORAGE-005, [topic-directive-model.md](../../api/topic-directive-model.md), [ADR-KAFKA-001](ADR-KAFKA-001-multiple-bootstrap-servers.md)

---

## Context

Test-Probe needs a human-readable, version-controllable format to define Kafka topic configurations for test executions. This file must be stored in cloud storage alongside Cucumber feature files and parsed during test initialization.

### Requirements

1. **Human Readable:** Easy for teams to author and review
2. **Version Controllable:** Can be committed to git, diff-friendly
3. **Structured:** Maps cleanly to `TopicDirective` Scala model
4. **Flexible:** Support multiple topics per test with different configurations
5. **Metadata Support:** Allow custom key-value pairs for Rosetta vault templating

### Existing Model

```scala
case class TopicDirective(
  topic: String,                              // Kafka topic name
  role: String,                               // "PRODUCER" or "CONSUMER"
  clientPrincipal: String,                    // Authentication principal
  eventFilters: List[(String, String)],       // Event filters for consumption
  metadata: Map[String, String] = Map.empty,  // Custom metadata for Rosetta
  bootstrapServers: Option[String] = None     // Optional: Custom Kafka cluster (v2.0)
)
```

**Bootstrap Servers (v2.0 - Added 2025-11-22):**
- `None` (default): Use bootstrap server from `reference.conf`
- `Some("host:port")`: Connect to specified Kafka cluster
- `Some("host1:port1,host2:port2")`: Connect to multiple brokers

This enables multi-cluster testing where different topics connect to different Kafka instances.
See [ADR-KAFKA-001](ADR-KAFKA-001-multiple-bootstrap-servers.md) for full details.

**Event Filters:**
- Used by KafkaConsumerStreamingActor to filter consumed events
- Tuple format: `(key, value)` for matching event properties
- Example: `("EventType", "OrderCreated")`, `("EventVersion", "v2.0")`

**Metadata:**
- Used by Rosetta RequestBodyBuilder for vault request templating
- Variable pattern: `{{'metadataKey'}}` references metadata Map
- Example: `{{'blood-type'}}` → `"O-negative"` from metadata

---

## Decision

We will use **YAML format** with a `topics` top-level key containing a list of topic directive objects.

### Canonical Format

```yaml
topics:
  - topic: "orders.events"
    role: "PRODUCER"
    clientPrincipal: "service-account-123"
    eventFilters:
      - key: "EventType"
        value: "OrderCreated"
      - key: "EventVersion"
        value: "v2.0"
    metadata:
      blood-type: "O-negative"
      test-run-id: "run-2025-123"
      environment: "staging"

  - topic: "payments.events"
    role: "CONSUMER"
    clientPrincipal: "consumer-account-456"
    eventFilters: []
    metadata: {}
```

### File Naming

- Default: `test-config.yaml`
- Configurable via: `test-probe.services.block-storage.topic-directive-file-name`
- Location: Root of test directory in JIMFS (e.g., `/testId/test-config.yaml`)

---

## Rationale

### Why YAML?

**Benefit 1: Human Readable**
```yaml
# YAML
topics:
  - topic: "orders.events"
    role: "PRODUCER"
```

vs

```json
{
  "topics": [
    {
      "topic": "orders.events",
      "role": "PRODUCER"
    }
  ]
}
```

YAML is cleaner for human authoring (no quotes, brackets, commas).

**Benefit 2: Comments Support**
```yaml
topics:
  - topic: "orders.events"
    role: "PRODUCER"
    eventFilters:
      - key: "EventType"
        value: "OrderCreated"
```

JSON doesn't support comments (common complaint).

**Benefit 3: Multi-line Strings**
```yaml
metadata:
  description: |
    This is a multi-line
    description of the test
```

More readable for longer text values.

**Benefit 4: Less Verbose**
- No closing brackets/braces
- No trailing commas
- Less punctuation

### Why Not JSON?

**JSON Drawbacks:**
- No comments
- More punctuation (commas, quotes, brackets)
- No multi-line strings (without escaping)
- Less human-friendly

**JSON Benefits (not compelling here):**
- Strict syntax (but YAML validation tools exist)
- Native JavaScript parsing (not relevant for backend)

**Decision:** YAML's human-readability wins for team-authored configs.

---

### Why `topics` Top-Level Key?

**Explicit Structure:**
```yaml
topics:
  - topic: "..."
```

Makes it clear this file defines multiple topic directives.

**Future Extensibility:**
```yaml
topics:
  - topic: "..."

test-metadata:
  owner: "team-a"
  environment: "staging"
```

Can add other top-level keys without breaking parser.

**Alternative (Rejected):**
```yaml
- topic: "orders.events"
  role: "PRODUCER"
```

Starts with array (ambiguous, harder to extend).

---

### Why Object-per-Filter Format?

**Event Filters Design:**
```yaml
eventFilters:
  - key: "EventType"
    value: "OrderCreated"
  - key: "EventVersion"
    value: "v2.0"
```

**Alternative (Rejected - Inline):**
```yaml
eventFilters:
  - ["EventType", "OrderCreated"]
  - ["EventVersion", "v2.0"]
```

**Reasoning:**
- Object format is self-documenting (`key:` and `value:` labels)
- Easier to understand for non-developers
- Aligns with Scala model: `List[(String, String)]`
- More consistent with rest of YAML structure

---

## Implementation Details

### Parser: Circe YAML

**Library:** `circe-yaml_3` (version 0.15.0)

**Decoder Structure:**
```scala
import io.circe.yaml.parser
import io.circe.{Decoder, DecodingFailure}
import io.circe.generic.semiauto._

implicit val eventFilterDecoder: Decoder[(String, String)] = Decoder.instance { cursor =>
  for {
    key <- cursor.get[String]("key")
    value <- cursor.get[String]("value")
  } yield (key, value)
}

implicit val topicDirectiveDecoder: Decoder[TopicDirective] = deriveDecoder[TopicDirective]

case class TopicsWrapper(topics: List[TopicDirective])
implicit val topicsWrapperDecoder: Decoder[TopicsWrapper] = deriveDecoder[TopicsWrapper]
```

**Parsing:**
```scala
def parse(yamlContent: String): List[TopicDirective] = parser.parse(yamlContent) match {
  case Right(json) => json.as[TopicsWrapper] match {
    case Right(wrapper) => wrapper.topics
    case Left(error) => throw InvalidTopicDirectiveFormatException(...)
  }
  case Left(error) => throw InvalidTopicDirectiveFormatException(...)
}
```

### Validation Rules

**Required Fields:**
- `topic`: Non-empty string
- `role`: Must be "PRODUCER" or "CONSUMER"
- `clientPrincipal`: Non-empty string

**Optional Fields:**
- `eventFilters`: Defaults to empty list `[]`
- `metadata`: Defaults to empty map `{}`
- `bootstrapServers`: Defaults to `None` (uses reference.conf default) - v2.0

**Validation:**
```scala
require(topic.nonEmpty, "Topic name cannot be empty")
require(
  role == "PRODUCER" || role == "CONSUMER",
  s"Role must be PRODUCER or CONSUMER, got: $role"
)
require(clientPrincipal.nonEmpty, "Client principal cannot be empty")
```

**Multi-Cluster Validation (v2.0):**

`TopicDirectiveValidator` performs additional validation during YAML parsing:

```scala
// Topic Uniqueness - each topic name must be unique
TopicDirectiveValidator.validateUniqueness(directives) match {
  case Left(errors) => throw DuplicateTopicException(errors.mkString("; "))
  case Right(()) => // valid
}

// Bootstrap Server Format - must be valid host:port
TopicDirectiveValidator.validateBootstrapServersFormat(bootstrapServers) match {
  case Left(error) => throw InvalidBootstrapServersException(error)
  case Right(()) => // valid
}
```

**Valid Bootstrap Server Formats:**
- `"localhost:9092"` - Single broker
- `"kafka.company.com:9092"` - FQDN with port
- `"kafka1:9092,kafka2:9092,kafka3:9092"` - Multiple brokers
- `None` or omitted - Uses default from reference.conf

**Invalid Bootstrap Server Formats:**
- `"localhost"` - Missing port
- `"localhost:0"` - Port 0 not allowed
- `"localhost:99999"` - Port > 65535
- `"-invalid:9092"` - Hostname cannot start with dash
- `""` - Empty string not allowed

### Error Messages

**Parse Error:**
```
Failed to parse topic directive file at /testId/test-config.yaml:
  Invalid YAML syntax at line 5: Expected ':' after key
```

**Decode Error:**
```
Failed to decode topic directives from /testId/test-config.yaml:
  Missing required field 'role' for topic 'orders.events'
```

**Validation Error:**
```
Invalid topic directive for topic 'orders.events':
  Role must be PRODUCER or CONSUMER, got: UNKNOWN
```

---

## Alternatives Considered

### Alternative 1: JSON Format

**Example:**
```json
{
  "topics": [
    {
      "topic": "orders.events",
      "role": "PRODUCER",
      "clientPrincipal": "service-account-123",
      "eventFilters": [
        {"key": "EventType", "value": "OrderCreated"}
      ],
      "metadata": {
        "blood-type": "O-negative"
      }
    }
  ]
}
```

**Pros:**
- Strict syntax (harder to make mistakes)
- Native parsing in JavaScript

**Cons:**
- More verbose
- No comments
- Less human-readable

**Decision:** Rejected in favor of YAML

---

### Alternative 2: HOCON Format

**Example:**
```hocon
topics = [
  {
    topic = "orders.events"
    role = "PRODUCER"
    clientPrincipal = "service-account-123"
    eventFilters = [
      {key = "EventType", value = "OrderCreated"}
    ]
    metadata {
      blood-type = "O-negative"
    }
  }
]
```

**Pros:**
- Used elsewhere in project (application.conf)
- Supports substitution and includes

**Cons:**
- Less common than YAML (team familiarity)
- More complex syntax (inheritance, substitution)
- Overkill for simple list of configs

**Decision:** Rejected - YAML is simpler for this use case

---

### Alternative 3: Properties File

**Example:**
```properties
topics.0.topic=orders.events
topics.0.role=PRODUCER
topics.0.clientPrincipal=service-account-123
topics.0.eventFilters.0.key=EventType
topics.0.eventFilters.0.value=OrderCreated
```

**Pros:**
- Simple key-value format
- No complex parsing

**Cons:**
- ❌ Extremely verbose for nested structures
- ❌ Hard to read/maintain
- ❌ No type safety (everything is string)

**Decision:** Rejected - too primitive for structured data

---

## Usage Examples

### Example 1: Producer Only

```yaml
topics:
  - topic: "orders.events"
    role: "PRODUCER"
    clientPrincipal: "order-service-producer"
    eventFilters: []
    metadata:
      service: "order-service"
      version: "1.0"
```

**Use Case:** Test producing events to a topic, no consumption.

---

### Example 2: Consumer with Event Filters

```yaml
topics:
  - topic: "orders.events"
    role: "CONSUMER"
    clientPrincipal: "test-consumer"
    eventFilters:
      - key: "EventType"
        value: "OrderCreated"
      - key: "EventVersion"
        value: "v2.0"
      - key: "Region"
        value: "us-west-2"
    metadata:
      test-scenario: "order-creation"
```

**Use Case:** Consume only specific event types/versions from topic.

---

### Example 3: Multiple Topics (Producer + Consumer)

```yaml
topics:
  - topic: "orders.events"
    role: "PRODUCER"
    clientPrincipal: "order-service"
    eventFilters: []
    metadata: {}

  - topic: "payments.events"
    role: "CONSUMER"
    clientPrincipal: "payment-listener"
    eventFilters:
      - key: "EventType"
        value: "PaymentProcessed"
    metadata:
      correlation-id: "order-payment-flow"
```

**Use Case:** Produce to one topic, consume from another (typical event-driven flow).

---

### Example 5: Multi-Cluster Testing (v2.0)

```yaml
topics:
  # Topic on default Region 1 cluster (uses reference.conf bootstrap server)
  - topic: "orders.events"
    role: "CONSUMER"
    clientPrincipal: "order-service"
    eventFilters:
      - key: "EventType"
        value: "OrderCreated"
    metadata: {}

  # Topic on Region 2 cluster (custom bootstrap server)
  - topic: "region2-payments"
    role: "PRODUCER"
    clientPrincipal: "payment-service"
    eventFilters: []
    metadata: {}
    bootstrapServers: "kafka-region2.company.com:9092"

  # Topic on Region 3 cluster with multiple brokers
  - topic: "region3-shipments"
    role: "CONSUMER"
    clientPrincipal: "shipment-listener"
    eventFilters:
      - key: "EventType"
        value: "ShipmentDispatched"
    metadata:
      region: "region3"
    bootstrapServers: "kafka-r3-1:9092,kafka-r3-2:9092,kafka-r3-3:9092"
```

**Use Case:** Test event propagation across multiple geographical regions or data centers.
Each topic connects to its specified Kafka cluster, enabling multi-stretch fabric testing.

**Validation Rules (v2.0):**
- **Topic Uniqueness:** Each topic name must appear only once across all directives
- **Bootstrap Format:** Must be valid `host:port` or comma-separated `host1:port1,host2:port2`

See [ADR-KAFKA-001](ADR-KAFKA-001-multiple-bootstrap-servers.md) for complete multi-cluster documentation.

---

### Example 4: Metadata for Rosetta Vault Templating

```yaml
topics:
  - topic: "healthcare.events"
    role: "CONSUMER"
    clientPrincipal: "healthcare-app"
    eventFilters:
      - key: "EventType"
        value: "PatientRecordUpdated"
    metadata:
      blood-type: "O-negative"
      facility-id: "FAC-123"
      security-clearance: "LEVEL-3"
      test-run-id: "run-2025-10-27-001"
```

**Use Case:** Metadata keys used in Rosetta vault request template:
```yaml
# vault-mapping.yaml request-template
request:
  authentication:
    principal: "{{clientPrincipal}}"
    facility: "{{'facility-id'}}"
    security-level: "{{'security-clearance'}}"
  patient:
    blood-type: "{{'blood-type'}}"
```

---

## Consequences

### Positive

✅ **Human Readable:** Teams can author/review configs easily
✅ **Git Friendly:** Diffs are meaningful, merge conflicts rare
✅ **Comments Support:** Can document intent inline
✅ **Flexible:** Easy to add optional fields
✅ **Proven Format:** YAML widely used for configuration

### Negative

⚠️ **YAML Pitfalls:** Indentation-sensitive (can cause errors)
⚠️ **Parser Dependency:** Requires circe-yaml library
⚠️ **Type Coercion:** YAML auto-converts types (e.g., `yes` → boolean)

### Mitigation

- Provide clear examples and templates
- Validate YAML syntax during fetchFromBlockStorage
- Use explicit quotes for string values
- Lint YAML files in CI/CD pipeline

---

## Validation and Testing

### Valid YAML Examples

**Minimal:**
```yaml
topics:
  - topic: "test.events"
    role: "PRODUCER"
    clientPrincipal: "test-principal"
    eventFilters: []
    metadata: {}
```

**Empty Lists/Maps:**
```yaml
topics:
  - topic: "test.events"
    role: "CONSUMER"
    clientPrincipal: "test"
    eventFilters: []  # or omit entirely
    metadata: {}      # or omit entirely
```

### Invalid YAML Examples

**Missing Required Field:**
```yaml
topics:
  - topic: "test.events"
    role: "PRODUCER"
```
Error: Missing required field 'clientPrincipal'

**Invalid Role:**
```yaml
topics:
  - topic: "test.events"
    role: "PUBLISHER"
    clientPrincipal: "test"
```
Error: Role must be PRODUCER or CONSUMER

**Malformed Event Filter:**
```yaml
topics:
  - topic: "test.events"
    role: "CONSUMER"
    clientPrincipal: "test"
    eventFilters:
      - key: "EventType"
```
Error: Missing 'value' field in event filter

---

## Follow-Up Work

- **YAML Linter:** Add CI/CD step to validate YAML syntax
- **Schema Validation:** Create JSON Schema for topic directive format
- **Templates:** Provide templates for common use cases
- **Documentation:** Add examples to team wiki/docs

---

## References

- **Circe YAML:** https://circe.github.io/circe-yaml/
- **YAML Spec:** https://yaml.org/spec/1.2.2/
- **YAML Best Practices:** https://docs.ansible.com/ansible/latest/reference_appendices/YAMLSyntax.html
- **TopicDirective Model:** `test-probe-common/src/main/scala/com/company/probe/common/models/TopicDirective.scala`
- **Rosetta Vault Mapping:** `docs/api/rosetta-vault-mapping-api.md`

---

## Review History

- **2025-10-27:** Initial ADR created (Paired Programming Session)
- **2025-11-22:** v2.0 - Added `bootstrapServers` field documentation for multi-cluster support (Multiple Bootstrap Servers feature)

---

## Appendix: Complete Example

### Single-Cluster Configuration (v1.0 Compatible)

```yaml
topics:
  - topic: "mf-team-1-topic"
    role: "CONSUMER"
    clientPrincipal: "mf-team-1-service-account"
    eventFilters:
      - key: "EventType"
        value: "MortgageApplicationSubmitted"
      - key: "EventVersion"
        value: "v3.0"
      - key: "Region"
        value: "north-america"
    metadata:
      blood-type: "O-negative"
      test-run-id: "run-2025-10-27-12345"
      correlation-id: "corr-abc-123-xyz"
      environment: "staging"
      team-name: "MF Team 1"
      security-clearance: "LEVEL-2"

  - topic: "notification.events"
    role: "PRODUCER"
    clientPrincipal: "notification-service"
    eventFilters: []
    metadata:
      service: "notification-service"
      version: "2.1.0"
```

### Multi-Cluster Configuration (v2.0)

```yaml
topics:
  # On-premise Region 1 cluster (default from reference.conf)
  - topic: "mortgage-applications"
    role: "CONSUMER"
    clientPrincipal: "mf-team-1-service-account"
    eventFilters:
      - key: "EventType"
        value: "MortgageApplicationSubmitted"
    metadata:
      team-name: "MF Team 1"
      environment: "on-prem-r1"

  # Cloud Region 2 cluster (AWS us-east-1)
  - topic: "cloud-mortgage-events"
    role: "PRODUCER"
    clientPrincipal: "cloud-mortgage-service"
    eventFilters: []
    metadata:
      environment: "aws-us-east-1"
    bootstrapServers: "kafka-cloud-r2.company.com:9092"

  # Disaster Recovery cluster (multi-broker)
  - topic: "dr-mortgage-backup"
    role: "CONSUMER"
    clientPrincipal: "dr-backup-service"
    eventFilters:
      - key: "EventType"
        value: "MortgageApplicationSubmitted"
    metadata:
      environment: "dr-site"
      replication-lag-tolerance: "5000ms"
    bootstrapServers: "kafka-dr-1:9092,kafka-dr-2:9092,kafka-dr-3:9092"
```

**Use Case:** Test mortgage application flow across:
1. On-premise Region 1 (event source)
2. Cloud Region 2 (event processing)
3. DR site (backup verification)

**Resulting Scala Model (Single-Cluster):**
```scala
List(
  TopicDirective(
    topic = "mf-team-1-topic",
    role = "CONSUMER",
    clientPrincipal = "mf-team-1-service-account",
    eventFilters = List(
      ("EventType", "MortgageApplicationSubmitted"),
      ("EventVersion", "v3.0"),
      ("Region", "north-america")
    ),
    metadata = Map(
      "blood-type" -> "O-negative",
      "test-run-id" -> "run-2025-10-27-12345",
      "correlation-id" -> "corr-abc-123-xyz",
      "environment" -> "staging",
      "team-name" -> "MF Team 1",
      "security-clearance" -> "LEVEL-2"
    ),
    bootstrapServers = None  // Uses default from reference.conf
  ),
  TopicDirective(
    topic = "notification.events",
    role = "PRODUCER",
    clientPrincipal = "notification-service",
    eventFilters = List.empty,
    metadata = Map(
      "service" -> "notification-service",
      "version" -> "2.1.0"
    ),
    bootstrapServers = None  // Uses default from reference.conf
  )
)
```

**Resulting Scala Model (Multi-Cluster v2.0):**
```scala
List(
  TopicDirective(
    topic = "mortgage-applications",
    role = "CONSUMER",
    clientPrincipal = "mf-team-1-service-account",
    eventFilters = List(("EventType", "MortgageApplicationSubmitted")),
    metadata = Map("team-name" -> "MF Team 1", "environment" -> "on-prem-r1"),
    bootstrapServers = None  // Uses default (on-prem Region 1)
  ),
  TopicDirective(
    topic = "cloud-mortgage-events",
    role = "PRODUCER",
    clientPrincipal = "cloud-mortgage-service",
    eventFilters = List.empty,
    metadata = Map("environment" -> "aws-us-east-1"),
    bootstrapServers = Some("kafka-cloud-r2.company.com:9092")  // Cloud Region 2
  ),
  TopicDirective(
    topic = "dr-mortgage-backup",
    role = "CONSUMER",
    clientPrincipal = "dr-backup-service",
    eventFilters = List(("EventType", "MortgageApplicationSubmitted")),
    metadata = Map("environment" -> "dr-site", "replication-lag-tolerance" -> "5000ms"),
    bootstrapServers = Some("kafka-dr-1:9092,kafka-dr-2:9092,kafka-dr-3:9092")  // DR cluster
  )
)
```
