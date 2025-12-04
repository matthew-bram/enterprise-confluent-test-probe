# Test Architecture Contract

**🚨 MANDATORY READING - DO NOT SKIP 🚨**

This document is THE CONTRACT for all test code in `test-probe-core`.
**Every engineer MUST read this WORD FOR WORD before writing ANY test code.**

Violations of these patterns **WILL** result in:
- ❌ Code review rejection
- ❌ CI/CD pipeline failure
- ❌ Mandatory refactoring
- ❌ Lost development time

**"I didn't read the README"** is NOT an acceptable excuse.

---

## Why This Contract Exists

This test suite was **completely rebuilt** after discovering **17 critical architectural smells** that made the original 9,000+ lines of test code unmaintainable:

1. ❌ **God Object** (870-line ActorWorld with 7 responsibilities)
2. ❌ **300% Code Duplication** (same test message creation copy-pasted 15+ times)
3. ❌ **Hardcoded Values** (249 lines of hardcoded directives)
4. ❌ **No Abstraction** (direct coupling to EventEnvelope - BROKEN by CloudEvents migration)
5. ❌ **15 Fixture Files** (3,225 lines with massive duplication)
6. ❌ **Inverted Test Pyramid** (too many integration tests, not enough unit tests)
7. ❌ **... and 10 more critical smells**

**Total Waste**: ~9,000 lines of test code had to be archived and rebuilt from scratch.

**This MUST NOT happen again.**

---

## Test Architecture Overview

The test architecture follows a **layered design** with **Single Responsibility Principle**:

```
test-probe-core/src/test/scala/com/company/probe/core/
├── testutil/              # Infrastructure Layer (Testcontainers, Schema Registry)
├── fixtures/              # Reusable test fixtures (ActorTestingFixtures, ConfigurationFixtures)
├── glue/world/            # BDD World objects (ActorWorld, StreamingWorld)
└── [package]/             # Spec files organized by production code package
```

### Layer Responsibilities

| Layer | Purpose | Examples | Lines |
|-------|---------|----------|-------|
| **Infrastructure** | Testcontainers, Schema Registry | TestcontainersManager, SchemaRegistrationService | ~500 |
| **Abstraction** | Test data builders, message abstractions | TestMessageBuilder, TopicDirectiveBuilder | ~750 |
| **Fixtures** | Reusable test utilities | ActorTestingFixtures, ConfigurationFixtures | ~600 |
| **World** | BDD state containers | ActorWorld, WorldBase | ~500 |
| **Specs** | Unit, component, integration tests | GuardianActorSpec, TestExecutionActorSpec | varies |

**Total Foundation**: ~2,350 lines (vs old 9,000+ lines - **74% reduction**)

---

## 10 MANDATORY PATTERNS ✅

### 1. Use Builders for Test Data ✅

**Rule**: NEVER hardcode test messages or directives inline.

**✅ CORRECT:**
```scala
val message = TestMessageBuilder
  .aTestEvent()
  .withEventType("OrderPlaced")
  .withPayload("""{"orderId": "123"}""")
  .build()
```

**❌ WRONG:**
```scala
val message = CloudEvent(
  id = UUID.randomUUID().toString,
  source = "test-source",
  specversion = "1.0",
  type = "OrderPlaced",
  datacontenttype = Some("application/json"),
  // ... 10 more fields hardcoded
)
```

**Why**: Eliminates 300% code duplication. Changes require one edit, not 15.

---

### 2. Code to Interfaces, Not Concrete Types ✅

**Rule**: Use abstraction traits (KafkaMessage), not concrete types (CloudEvent).

**✅ CORRECT:**
```scala
def processMessage(message: KafkaMessage): Unit = {
  val topic = message.topic
  val eventType = message.eventType
  // ...
}
```

**❌ WRONG:**
```scala
def processMessage(message: CloudEvent): Unit = {
  // Direct coupling to CloudEvent
  // Breaks when message format changes
}
```

**Why**: CloudEvents migration **broke 9,000+ lines** because tests were coupled to EventEnvelope.

---

### 3. Fixtures via Trait Composition, Not Inheritance ✅

**Rule**: Mix in fixtures as needed, don't create deep inheritance hierarchies.

**✅ CORRECT:**
```scala
class MyActorSpec extends AnyWordSpec
  with ActorTestingFixtures
  with ConfigurationFixtures
  with TestHarnessFixtures {
  // ...
}
```

**❌ WRONG:**
```scala
abstract class BaseActorSpec extends ActorSpecBase
abstract class ChildActorSpec extends BaseActorSpec
class MyActorSpec extends ChildActorSpec
// 4-level inheritance hierarchy
```

**Why**: Composition is flexible, inheritance is brittle and hard to refactor.

---

### 4. Single Responsibility Per Component ✅

**Rule**: Every class, trait, or object has ONE job.

**✅ CORRECT:**
```scala
// TestcontainersManager: Manages Docker containers ONLY
object TestcontainersManager {
  def start(): Try[Unit]
  def stop(): Unit
  def getKafkaBootstrapServers: String
}

// SchemaRegistrationService: Registers schemas ONLY
class SchemaRegistrationService(schemaRegistryUrl: String) {
  def registerSchema(topic: String): Try[Int]
}
```

**❌ WRONG:**
```scala
// ActorWorld (old): 870 lines with 7 responsibilities
class ActorWorld {
  def startTestcontainers(): Unit        // Responsibility 1
  def registerSchema(topic: String): Int // Responsibility 2
  def spawnActor(): ActorRef             // Responsibility 3
  def createProbes(): Unit               // Responsibility 4
  def expectMessage(): ServiceResponse   // Responsibility 5
  // ... 2 more responsibilities
}
```

**Why**: God Objects are unmaintainable. 870-line ActorWorld reduced to 306 lines.

---

### 5. DRY (Don't Repeat Yourself) ✅

**Rule**: Eliminate duplication through abstraction.

**✅ CORRECT:**
```scala
// Object Mother pattern
object TopicDirectiveBuilder {
  def allProducers(): List[TopicDirective] = producers(TestTopics: _*)
  def allConsumers(): List[TopicDirective] = consumers(TestTopics: _*)
}

// Usage (1 line):
val directives = TopicDirectiveBuilder.allProducers()
```

**❌ WRONG:**
```scala
// Hardcoded in every test file (15 lines x 15 files = 249 lines)
val directives = List(
  TopicDirective("test-events", "producer"),
  TopicDirective("order-events", "producer"),
  TopicDirective("payment-events", "producer"),
  TopicDirective("user-events", "producer")
)
```

**Why**: 249 lines of duplication eliminated. One change propagates everywhere.

---

### 6. Immutability ✅

**Rule**: All test data builders and fixtures must be immutable.

**✅ CORRECT:**
```scala
final case class TestMessageBuilder private (
  topic: String = "test-events",
  eventType: String = "TestEvent",
  // ...
) {
  def withTopic(t: String): TestMessageBuilder = copy(topic = t)
  def withEventType(et: String): TestMessageBuilder = copy(eventType = et)
}
```

**❌ WRONG:**
```scala
class TestMessageBuilder {
  var topic: String = "test-events"
  var eventType: String = "TestEvent"

  def setTopic(t: String): Unit = { topic = t }
}
```

**Why**: Mutable state causes flaky tests and race conditions.

---

### 7. Fail Fast with Clear Errors ✅

**Rule**: Use Try/Either with descriptive error messages.

**✅ CORRECT:**
```scala
def registerSchema(topic: String): Try[Int] = Try {
  val schemaType = getSchemaType(topic)
  val schemaSource = Source.fromResource(s"schemas/cloud-event.$extension")

  if (!schemaSource.hasNext) {
    throw new IllegalStateException(
      s"Schema file not found: schemas/cloud-event.$extension"
    )
  }
  // ...
} recoverWith {
  case ex: IOException =>
    Failure(new RuntimeException(
      s"Failed to register schema for topic '$topic': ${ex.getMessage}",
      ex
    ))
}
```

**❌ WRONG:**
```scala
def registerSchema(topic: String): Option[Int] = {
  try {
    // ... magic happens
    Some(schemaId)
  } catch {
    case _: Exception => None  // WHAT WENT WRONG?!
  }
}
```

**Why**: Silent failures waste hours debugging. Explicit errors save time.

---

### 8. Small, Focused Specs ✅

**Rule**: Each spec tests ONE component, with clear test names.

**✅ CORRECT:**
```scala
class TestMessageBuilderSpec extends AnyWordSpec with Matchers {
  "TestMessageBuilder" should {
    "create message with default values" in { /* ... */ }
    "override topic" in { /* ... */ }
    "override event type" in { /* ... */ }
  }
}
```

**❌ WRONG:**
```scala
class MegaIntegrationSpec extends AnyWordSpec {
  "entire system" should {
    "do everything" in {
      // 500 lines testing actors, Kafka, S3, Vault, Cucumber
      // Fails? Good luck finding the cause!
    }
  }
}
```

**Why**: Small specs = fast feedback, easy debugging, clear failures.

---

### 9. Test Pyramid: 70% Unit, 20% Component, 10% Integration ✅

**Rule**: Majority of tests should be fast unit tests.

**✅ CORRECT:**
```
Unit Tests (70%):        TestMessageBuilderSpec, TopicDirectiveBuilderSpec, etc.
Component Tests (20%):   ActorWorldSpec (no Testcontainers), fixture specs
Integration Tests (10%): Full E2E with Testcontainers
```

**❌ WRONG:**
```
Unit Tests (10%):        Barely any
Component Tests (20%):   Some
Integration Tests (70%): Slow, flaky, hard to debug
```

**Why**: Fast tests = fast feedback. Slow tests = wasted developer time.

---

### 10. Documentation at Every Level ✅

**Rule**: Every class, trait, method has purpose documentation.

**✅ CORRECT:**
```scala
/**
 * Manages Testcontainers infrastructure for integration tests.
 *
 * Provides:
 * - Kafka container (Confluent Platform 7.5.0)
 * - Schema Registry container
 * - Docker network for inter-container communication
 *
 * Usage:
 * {{{
 *   TestcontainersManager.start()
 *   val bootstrapServers = TestcontainersManager.getKafkaBootstrapServers
 *   // ... run tests
 *   TestcontainersManager.stop()
 * }}}
 *
 * Thread Safety: NOT thread-safe. Call start() once before tests.
 */
private[core] object TestcontainersManager {
  // ...
}
```

**❌ WRONG:**
```scala
object TM {
  def s(): Unit = { /* ... */ }
  def g(): String = { /* ... */ }
}
```

**Why**: Code without docs is unmaintainable. Future you will thank present you.

---

## 10 ANTI-PATTERNS ❌

### 1. ❌ God Objects

**Never** create classes with multiple responsibilities.

**Example**: Old ActorWorld (870 lines, 7 responsibilities) → New ActorWorld (306 lines, 1 responsibility)

---

### 2. ❌ Hardcoded Values

**Never** hardcode test data inline. Use builders.

**Bad**: 249 lines of hardcoded TopicDirective lists across files
**Good**: `TopicDirectiveBuilder.allProducers()` (1 line)

---

### 3. ❌ Coupling to Concrete Types

**Never** couple to specific message types (EventEnvelope, CloudEvent).

**Bad**: `def process(msg: EventEnvelope)` - broke 9,000+ lines when EventEnvelope removed
**Good**: `def process(msg: KafkaMessage)` - resilient to format changes

---

### 4. ❌ Deep Inheritance Hierarchies

**Never** create 3+ level inheritance chains.

**Bad**: BaseSpec → ActorBaseSpec → ChildActorSpec → MySpec
**Good**: Trait composition with `with ActorTestingFixtures with ConfigurationFixtures`

---

### 5. ❌ Mutable State in Tests

**Never** use `var` in test builders or fixtures.

**Bad**: `var topic: String = "test-events"` - causes flaky tests
**Good**: `final case class Builder(topic: String)` with immutable copy

---

### 6. ❌ Silent Failures

**Never** swallow exceptions or return Option without error context.

**Bad**: `try { ... } catch { case _: Exception => None }`
**Good**: `Try { ... } recoverWith { case ex => Failure(new RuntimeException(..., ex)) }`

---

### 7. ❌ Mega Integration Tests

**Never** test everything in one giant test.

**Bad**: 500-line test covering actors + Kafka + S3 + Vault + Cucumber
**Good**: 20 focused unit tests, 5 component tests, 1 integration test

---

### 8. ❌ Code Duplication

**Never** copy-paste test code. Extract to builder/fixture.

**Bad**: Same test message creation repeated 15 times
**Good**: `TestMessageBuilder.aTestEvent().build()` (reused everywhere)

---

### 9. ❌ Unclear Test Names

**Never** use vague test names like "test1" or "it works".

**Bad**: `"test" in { /* ... */ }`
**Good**: `"should create message with default values" in { /* ... */ }`

---

### 10. ❌ Missing Documentation

**Never** write code without documentation.

**Bad**: `object X { def f(): Unit }`
**Good**: Scaladoc with purpose, usage, examples, thread safety

---

## Code Review Checklist

Before submitting ANY test code for review, verify:

- [ ] **Builders used** for all test data (no hardcoded messages/directives)
- [ ] **Abstraction traits** used (not concrete types like CloudEvent)
- [ ] **Fixtures via trait composition** (not inheritance)
- [ ] **Single Responsibility** (each component has ONE job)
- [ ] **DRY** (zero duplication - extract to builder/fixture)
- [ ] **Immutable** (all builders/fixtures use `final case class` + `copy`)
- [ ] **Fail fast** (Try/Either with descriptive errors)
- [ ] **Small specs** (focused on one component, clear test names)
- [ ] **Test pyramid** (70% unit, 20% component, 10% integration)
- [ ] **Documented** (Scaladoc on every class, trait, method)

**If ANY checkbox is unchecked, code review will be REJECTED.**

---

## Enforcement Policy

**Violations of this contract:**

1. **First violation**: Code review comment + mandatory fix
2. **Second violation**: Code review rejection + re-education session
3. **Third violation**: Escalation to tech lead

**This is non-negotiable.**

---

## References

- **Scala Style Guide**: `.claude/styles/scala-conventions.md`
- **Testing Standards**: `.claude/styles/testing-standards.md`
- **Akka Patterns**: `.claude/styles/akka-patterns.md`
- **BDD Testing**: `.claude/styles/bdd-testing-standards.md`
- **Refactoring Analysis**: `working/testArchitectureRefactor/COMPREHENSIVE-TEST-ARCHITECTURE-ANALYSIS.md`

---

## Quick Reference

**Need to:**
- Create test messages? → `TestMessageBuilder.aTestEvent().build()`
- Create directives? → `TopicDirectiveBuilder.allProducers()`
- Test actors? → `with ActorTestingFixtures`
- Get test config? → `ConfigurationFixtures.defaultCoreConfig`
- Start Testcontainers? → `world.startInfrastructure()` (from WorldBase)
- Create probes? → `with ActorSpawningFixtures`

---

**Last Updated**: 2025-11-09
**Architecture Version**: 2.0 (post-rebuild)
**Tests Passing**: 112/112 (100%)

---

**🚨 READ THIS CONTRACT BEFORE WRITING ANY TEST CODE 🚨**

**"I didn't know" is NOT an excuse. You have been warned.**
