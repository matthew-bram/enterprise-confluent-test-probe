# ADR-KAFKA-001: Multiple Bootstrap Servers per Topic Directive

**Status:** Accepted
**Date:** 2025-11-22
**Decision Makers:** Engineering Team
**Related:** ADR-STORAGE-008, [topic-directive-model.md](../../api/topic-directive-model.md)

---

## Context

The test-probe framework currently limits all Kafka connections within a test execution to a single bootstrap server defined in `reference.conf`. This becomes a significant limitation for teams testing multi-stretch cluster event fabrics where events need to be validated as they propagate across different geographical regions or infrastructure clusters (e.g., on-premise to cloud, Region1 to Region2).

### Current Limitation

```
reference.conf (single global bootstrap server)
    ↓
KafkaProducerActor / KafkaConsumerActor (pass to all topics)
    ↓
All Topics → Same Bootstrap Server
```

**Problem:** Cannot test cross-cluster event propagation within a single test execution.

### Requirements

1. **Per-Topic Configuration:** Individual topics should specify their own bootstrap servers
2. **Backward Compatible:** Existing tests using default bootstrap server must work without changes
3. **Fallback to Default:** Topics without explicit bootstrap server should use `reference.conf` default
4. **Validation:** Prevent configuration errors (invalid formats, duplicate topics)
5. **Multi-Cluster Testing:** Enable Testcontainers support for multi-cluster scenarios in unit tests

---

## Decision

We will add an optional `bootstrapServers: Option[String] = None` field to the `TopicDirective` model. When present, Kafka streaming actors will use the topic-specific bootstrap servers; when absent, they will fall back to the default from configuration.

### New TopicDirective Model

```scala
case class TopicDirective(
  topic: String,
  role: String,
  clientPrincipal: String,
  eventFilters: List[EventFilter],
  metadata: Map[String, String] = Map.empty,
  bootstrapServers: Option[String] = None  // NEW: Optional per-topic bootstrap servers
)
```

### New YAML Format

```yaml
topics:
  # Topic using default bootstrap server (reference.conf)
  - topic: "region1-order-events"
    role: "producer"
    clientPrincipal: "service-account-1"
    eventFilters: []
    metadata: {}

  # Topic with explicit bootstrap server (different cluster)
  - topic: "region2-order-events"
    role: "consumer"
    clientPrincipal: "service-account-2"
    eventFilters: []
    metadata: {}
    bootstrapServers: "kafka-region2.company.com:9092"
```

### Streaming Actor Implementation

Kafka streaming actors determine the effective bootstrap server:

```scala
val effectiveBootstrapServers = directive.bootstrapServers.getOrElse(defaultBootstrapServers)
val settings = ConsumerSettings(...).withBootstrapServers(effectiveBootstrapServers)
```

### New Validation

Two new validators ensure data integrity:

```scala
object TopicDirectiveValidator {
  /**
   * Validates that all topic names are unique within the directive list.
   * @return Right(()) if valid, Left(errorMessages) if duplicates found
   */
  def validateUniqueness(directives: List[TopicDirective]): Either[List[String], Unit]

  /**
   * Validates bootstrap server format (comma-separated host:port).
   * @return Right(()) if valid or None, Left(errorMessage) if invalid format
   */
  def validateBootstrapServersFormat(bootstrapServers: Option[String]): Either[String, Unit]
}
```

---

## Rationale

### Why Optional Field?

**Backward Compatibility:** Existing tests continue to work without modification. The default value `None` means "use the global configuration."

**Example - No Migration Needed:**
```yaml
# Old YAML (still works without bootstrapServers field)
topics:
  - topic: "orders.events"
    role: "PRODUCER"
    clientPrincipal: "service-account"
```

### Why Streaming Actors Decide?

**Encapsulation:** Supervisor actors (KafkaProducerActor/KafkaConsumerActor) pass the default bootstrap servers from config. The streaming actors privately determine which bootstrap server to use.

```
Config (default) ──→ Supervisor ──→ Streaming Actor
                                    ├─ Uses directive.bootstrapServers if present
                                    └─ Falls back to parameter if not
```

**Benefits:**
- Single responsibility: supervisors manage lifecycle, streaming actors manage connections
- Testability: Easy to test both paths (explicit vs default)
- Encapsulation: Implementation detail doesn't leak upward

### Why Fail-Fast Validation?

**Unique Topic Validation:** Topic names must be unique because:
1. A test might produce to "topic1" and consume from "topic1" with different bootstrap servers → undefined behavior
2. Duplicate topic names with different configurations → ambiguous routing

**Bootstrap Server Format Validation:** Catch configuration errors early with clear messages:
```
Invalid bootstrap servers "invalid-format":
  Expected format: "host:port" or "host1:port1,host2:port2"
  Got: "invalid-format"
```

### Why Multi-Cluster Registry in TestcontainersManager?

**Current Design (Single Container):**
```scala
private var kafkaContainerOpt: Option[KafkaContainer] = None  // SINGLETON
```

**New Design (Named Cluster Registry):**
```scala
private val clusters: mutable.Map[String, ClusterInfo] = mutable.Map.empty

def getOrCreateCluster(name: String): ClusterInfo = synchronized {
  clusters.getOrElseUpdate(name, createCluster(name))
}

// Backward compatible - delegates to default cluster
def getKafkaBootstrapServers: String = getOrCreateCluster("default").bootstrapServers
```

**Benefits:**
- ✅ Backward compatible (existing tests call `getKafkaBootstrapServers`)
- ✅ Lazy startup (clusters created on-demand)
- ✅ Multi-cluster support (named cluster registry)
- ✅ Automatic cleanup (JVM shutdown hook cleans all clusters)

---

## Alternatives Considered

### Alternative 1: Global Configuration Only (Status Quo)

**Approach:** Don't add per-topic bootstrap servers. Keep single global configuration.

**Pros:**
- Simpler model
- No new validation needed

**Cons:**
- ❌ Cannot test multi-cluster event propagation
- ❌ Major limitation for enterprise users
- ❌ Deferred to future release

**Decision:** Rejected - Feature explicitly requested by users

---

### Alternative 2: Mutable TopicDirective Reference

**Approach:** Allow topics to override bootstrap servers dynamically via setter:

```scala
class TopicDirective(
  var bootstrapServers: String = defaultFromConfig
)
```

**Pros:**
- Flexibility to change at runtime

**Cons:**
- ❌ Mutable state (anti-pattern in Scala/FP)
- ❌ Thread-safety concerns in Akka actors
- ❌ Harder to test (state changes)

**Decision:** Rejected - Use immutable `Option[String]` instead

---

### Alternative 3: Separate KafkaDirective Model

**Approach:** Create a separate model just for Kafka configuration:

```scala
case class KafkaDirective(
  topic: String,
  bootstrapServers: String,
  role: String,
  ...
)
```

**Pros:**
- Clear separation of concerns

**Cons:**
- ❌ Duplicates TopicDirective fields
- ❌ More mapping code needed
- ❌ Breaks existing BlockStorageDirective structure

**Decision:** Rejected - Add field directly to TopicDirective

---

### Alternative 4: Vault-Based Bootstrap Servers

**Approach:** Fetch bootstrap servers from HashiCorp Vault instead of YAML:

```scala
bootstrapServers = VaultService.fetch(s"kafka/${region}/bootstrap-servers")
```

**Pros:**
- Centralized secret management

**Cons:**
- ❌ Bootstrap servers are infrastructure endpoints, not secrets
- ❌ Unnecessary vault round-trip per test
- ❌ Complicates configuration pipeline

**Decision:** Rejected - Bootstrap servers are YAML-configured infrastructure, not secrets

---

## Implementation Details

### Phase 1: Model Changes (common module)

1. **Update TopicDirective:** Add `bootstrapServers: Option[String] = None` field
2. **Create TopicDirectiveValidator:** Uniqueness and format validation
3. **Add Exceptions:** `DuplicateTopicException`, `InvalidBootstrapServersException`

### Phase 2: Streaming Actor Updates (core module)

1. **Update KafkaConsumerStreamingActor:** Add fallback logic
2. **Update KafkaProducerStreamingActor:** Add fallback logic
3. **Update TopicDirectiveMapper:** Call validators after YAML parsing

### Phase 3: Test Infrastructure (core/test)

1. **Refactor TestcontainersManager:** Named cluster registry
2. **Update TopicDirectiveFixtures:** Add `bootstrapServers` parameter

### Phase 4: Integration & Validation

1. **Unit Tests:** 100% coverage on new code
2. **Component Tests:** Multi-cluster BDD scenarios
3. **Documentation:** Update architecture diagrams

---

## Consequences

### Positive

✅ **Multi-Cluster Support:** Tests can now validate event propagation across different Kafka clusters
✅ **Backward Compatible:** Existing tests work without any changes
✅ **Type Safe:** Compilation ensures correctness of bootstrap server references
✅ **Fail-Fast:** Validation catches configuration errors immediately with clear messages
✅ **Encapsulated:** Implementation detail (which bootstrap server to use) doesn't leak upward

### Negative

⚠️ **New Field:** TopicDirective grows by one field (minimal impact)
⚠️ **Validation Logic:** New code paths for uniqueness and format checking
⚠️ **Test Complexity:** Multi-cluster test scenarios need careful setup and teardown

### Mitigation

- Default value `None` ensures backward compatibility
- Comprehensive validation prevents invalid configurations
- Named cluster registry with JVM shutdown hook ensures cleanup

---

## Data Migration

**No migration needed.** The YAML format is additive:

```yaml
# Old YAML files (pre-ADR-KAFKA-001) continue to work
topics:
  - topic: "orders.events"
    role: "PRODUCER"
    clientPrincipal: "service-account"
    eventFilters: []
    metadata: {}

# bootstrapServers field is optional - defaults to None
```

---

## Validation and Testing

### Valid Configuration Examples

**Single Cluster (Backward Compatible):**
```yaml
topics:
  - topic: "orders.events"
    role: "CONSUMER"
    clientPrincipal: "test-consumer"
```
Result: Uses default bootstrap server from `reference.conf`

**Multi-Cluster (New Feature):**
```yaml
topics:
  - topic: "region1-events"
    role: "CONSUMER"
    clientPrincipal: "service-1"
    bootstrapServers: "kafka-region1.com:9092"

  - topic: "region2-events"
    role: "PRODUCER"
    clientPrincipal: "service-2"
    bootstrapServers: "kafka-region2.com:9092"
```
Result: Each topic connects to its specified Kafka cluster

**Mixed (Default + Explicit):**
```yaml
topics:
  - topic: "local-events"
    role: "CONSUMER"
    clientPrincipal: "local-service"

  - topic: "remote-events"
    role: "PRODUCER"
    clientPrincipal: "remote-service"
    bootstrapServers: "kafka-remote.com:9092"
```
Result: `local-events` uses default, `remote-events` uses explicit bootstrap server

### Invalid Configuration Examples

**Duplicate Topics (Same Name):**
```yaml
topics:
  - topic: "orders.events"
    role: "CONSUMER"
    bootstrapServers: "kafka-region1:9092"

  - topic: "orders.events"  # ERROR: Duplicate!
    role: "PRODUCER"
    bootstrapServers: "kafka-region2:9092"
```
Error: `DuplicateTopicException: Topics must be unique. Duplicates found: orders.events`

**Invalid Bootstrap Server Format:**
```yaml
topics:
  - topic: "events"
    role: "CONSUMER"
    clientPrincipal: "test"
    bootstrapServers: "invalid-format"  # ERROR: Not host:port
```
Error: `InvalidBootstrapServersException: Bootstrap servers "invalid-format" has invalid format. Expected: host:port or comma-separated list`

---

## Testing Strategy

### Unit Tests

1. **TopicDirectiveValidator.validateUniqueness()**
   - Happy path: All topics unique → Right(())
   - Error path: Duplicate topics → Lists all duplicates
   - Edge case: Empty list → Right(())

2. **TopicDirectiveValidator.validateBootstrapServersFormat()**
   - Happy path: "host:9092" → Right(())
   - Happy path: "host1:9092,host2:9092" → Right(())
   - Happy path: None → Right(())
   - Error path: "invalid" → Left(error message)
   - Error path: "host" (missing port) → Left(error message)

3. **Streaming Actor Fallback Logic**
   - Explicit bootstrap servers used when present
   - Default bootstrap servers used when None
   - Integration with ConsumerSettings/ProducerSettings

### Component Tests (BDD)

1. **Multi-Cluster Consumer Scenarios**
   - Given two topics with different bootstrap servers
   - When consuming from both
   - Then events come from correct clusters

2. **Multi-Cluster Producer Scenarios**
   - Given two topics with different bootstrap servers
   - When producing to both
   - Then events go to correct clusters

3. **Error Scenarios**
   - Duplicate topics rejected
   - Invalid bootstrap server format rejected

### Integration Tests

1. **Backward Compatibility**
   - Old YAML files parse correctly
   - Topics without bootstrapServers use default

2. **TestcontainersManager Multi-Cluster**
   - Multiple clusters created on-demand
   - All clusters cleaned up on JVM shutdown

---

## Follow-Up Work

**Future Enhancements:**
- CLI validation tool for YAML files
- Web UI for topic directive builder
- Schema registry per-cluster support (if needed)
- Multi-region failover strategies

**Documentation:**
- Add examples to team wiki
- Create migration guide (reference.conf → per-topic configuration)

---

## References

- **ADR-STORAGE-008:** Topic Directive YAML Format
- **Topic Directive Model API:** `docs/api/topic-directive-model.md`
- **Kafka Streaming Architecture:** `docs/architecture/blueprint/10 Kafka Streaming/10.1-kafka-streaming-architecture.md`
- **TopicDirective Source:** `test-probe-common/src/main/scala/com/company/probe/common/models/TopicDirective.scala`

---

## Review History

- **2025-11-22:** Initial ADR accepted (Feature Implementation Phase 6)
- **Phases Completed:**
  - Phase 0: Architecture documentation (this ADR)
  - Phase 0.5: Technical debt cleanup
  - Phase 1: Code implementation (TopicDirective, validators, streaming actors)
  - Phase 2: Peer review (visibility pattern fixes)
  - Phase 3: BDD specifications (31 scenarios)
  - Phase 4: Component tests (100% passing)
  - Phase 5: Unit tests (100% coverage)
  - Phase 6: As-built documentation (this ADR)

---

## Appendix: Complete Multi-Cluster Example

```yaml
topics:
  # Region 1 - On-Premise Kafka
  - topic: "region1.orders.submitted"
    role: "producer"
    clientPrincipal: "order-service-region1"
    eventFilters: []
    metadata:
      region: "on-premise"
      environment: "production"
    bootstrapServers: "kafka-on-prem.company.internal:9092"

  # Region 2 - Cloud (AWS) Kafka
  - topic: "region2.orders.submitted"
    role: "consumer"
    clientPrincipal: "order-aggregator"
    eventFilters:
      - key: "EventType"
        value: "OrderSubmitted"
      - key: "Region"
        value: "region2"
    metadata:
      region: "aws-us-east-1"
      environment: "production"
      team: "order-fulfillment"
    bootstrapServers: "kafka-aws.us-east-1.amazonaws.com:9092"

  # Local Default (uses reference.conf)
  - topic: "local.orders.completed"
    role: "consumer"
    clientPrincipal: "local-fulfillment-service"
    eventFilters:
      - key: "EventType"
        value: "OrderCompleted"
    metadata:
      environment: "integration-test"
```

**Test Scenario:**
1. Producer sends `OrderSubmitted` event to Region 1 cluster
2. Region 1 → Region 2 replication (external process)
3. Consumer reads replicated event from Region 2 cluster
4. Consumer reads completion event from local cluster (default)
5. Validates cross-cluster event propagation end-to-end

---
