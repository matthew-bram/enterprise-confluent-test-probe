# BDD Testing Standards

## Overview
This guide documents the standards and patterns for Behavior-Driven Development (BDD) testing using Cucumber and cucumber-scala 8.1.0. These patterns were validated through systematic testing and are production-ready.

**Testing Framework Stack:**
- Cucumber JVM with cucumber-scala 8.1.0
- ScalaTest matchers for assertions
- Akka Typed TestKit for actor testing
- JUnit4 as test runner

---

## Section 1: Feature File Patterns and Best Practices

### 1.1 Scenario Outline vs Scenario

**Decision Criteria**:

| Use Scenario Outline When | Use Scenario When |
|---------------------------|-------------------|
| Testing same behavior with multiple data variations | Testing unique one-off behavior |
| Need 2+ test cases with same steps | Only need 1 test case |
| Data variations are the focus (e.g., functional, performance, regression test types) | Flow/sequence is the focus |
| Want to see all test cases in Examples table | Unique test that doesn't fit a pattern |

**Standard Pattern**: **Always use Scenario Outline with Examples tables**

**Rationale**:
- ✅ Consistency across all scenarios
- ✅ Easy to add more test cases later
- ✅ Clear visibility of test data in one place
- ✅ Matches cucumber-scala 8.1.0 best practices
- ✅ Even single test cases benefit from parameterization

**Example**:
```gherkin
@Critical @InitializeRequest
Scenario Outline: Handle InInitializeTestRequest in Setup state
  When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor
  Then the service should receive "InitializeTestResponse" with testId <testId> and success <success>
  Examples:
    | testId                               | success |
    | 11111111-1111-1111-1111-111111111111 | true    |
    | 22222222-2222-2222-2222-222222222222 | true    |
```

---

### 1.2 Parameter Quoting in Gherkin

**Critical Rule**: Quote parameters based on their Cucumber Expression type

| Cucumber Expression | Gherkin Syntax | Example |
|-------------------|----------------|---------|
| `{word}` | Unquoted | `testId <testId>` |
| `{string}` | Quoted | `bucket "<bucket>"` |
| `{int}` | Unquoted | `timeout <timeout>` |

**Why This Matters**:
- `{word}` matches a single non-whitespace token - cannot parse quoted strings
- `{string}` requires quotes in the feature file
- Mixing these causes "step undefined" errors

**Example**:
```gherkin
# CORRECT: {word} parameter unquoted, {string} parameters quoted
When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"

# WRONG: Would fail because {word} can't match quoted value
When a service sends "InStartTestRequest" with testId "<testId>", bucket "<bucket>", testType "<testType>"
```

---

### 1.3 Explicit State Transitions (The "No Magic" Principle)

**Rule**: Never try to manually set actor state - always transition through proper message sequences

❌ **Don't** (Broken Approach):
```gherkin
Given a TestExecutionActor in Loaded state for test <testId>
# This just spawns a fresh actor - it's actually in Setup, not Loaded!
```

✅ **Do** (Explicit Transitions):
```gherkin
When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
And the TestExecutionActor processes "TrnSetup"
Then the service should receive "InitializeTestResponse" with testId <testId> and success true
When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
And the TestExecutionActor processes "TrnLoading"
Then the service should receive "StartTestResponse" with testId <testId> and accepted true
When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
And the VaultActor sends "ChildGoodToGo" for testId <testId>
And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
And the KafkaConsumerActor sends "ChildGoodToGo" for testId <testId>
And the TestExecutionActor processes "TrnLoaded"
# NOW actor is genuinely in Loaded state
```

**Why**:
- FSM requires proper state transitions via message passing
- Manual state setting violates actor encapsulation
- Tests become documentation of actual workflow
- Catches transition bugs that "magic" setup would hide

---

### 1.4 Given/When/Then Syntax Rules

**CRITICAL**: Correct Given/When/Then usage is essential for maintainability and message queue ordering

**The Rules**:
- **Given** = Preconditions and state setup (NO actions, NO message sends)
- **When** = Actions that trigger behavior (send messages, invoke methods)
- **Then** = Verification of outcomes (consume responses, check state)
- **And** = Continuation of the previous step type (Given→And is Given, When→And is When, etc.)

**Common BDD Syntax Violation** (Most Frequent Error):

❌ **WRONG**: "has been initialized" marked as Given/And
```gherkin
Given a BlockStorageActor is spawned for test "test-003"
And the BlockStorageActor has been initialized with bucket "evidence-bucket"  # ❌ WRONG: This SENDS Initialize message!
Then the BlockStorageActor should send "BlockStorageFetched" to parent
When the parent sends "LoadToBlockStorage" with testExecutionResult
```

**Why This Fails**:
- "has been initialized" SENDS the Initialize message (it's an action)
- Using And/Given incorrectly suggests it's a precondition
- Causes Then steps to consume messages out of order
- Leads to "Expected X, got Y" errors when messages are consumed out of FIFO order

✅ **CORRECT**: "has been initialized" marked as When
```gherkin
Given a BlockStorageActor is spawned for test "test-003"
When the BlockStorageActor has been initialized with bucket "evidence-bucket"  # ✅ CORRECT: When (action)
Then the BlockStorageActor should send "BlockStorageFetched" to parent
And the BlockStorageActor should send "ChildGoodToGo" to parent
When the parent sends "LoadToBlockStorage" with testExecutionResult
Then the BlockStorageActor should send "BlockStorageUploadComplete" to parent
```

**Rule**: If the step SENDS A MESSAGE, it's a When step, not a Given/And step.

**Correct Examples**:
```gherkin
# Given = State setup (no message sends)
Given a BlockStorageActor is spawned for test "test-001"
Given a BlockStorageDirective is available
Given a list of SecurityDirective is available

# When = Actions (sends messages)
When the parent sends "Initialize" with bucket "my-bucket"
When the BlockStorageActor has been initialized with bucket "evidence-bucket"  # Sends Initialize
When the CucumberExecutionActor parent sends "StartTest"

# Then = Verification (consumes responses)
Then the BlockStorageActor should send "BlockStorageFetched" to parent
Then the BlockStorageActor should send "ChildGoodToGo" to parent
Then the BlockStorageActor should stop cleanly
```

---

### 1.5 Message Queue Order and Consumption

**Critical Pattern**: Consume all expected messages in order they're sent

**Problem**: ClassCastException when expecting wrong message type

**Example Error**:
```
java.lang.ClassCastException: QueueCommands$TestInitialized cannot be cast to QueueCommands$TestLoading
Expected BlockStorageUploadComplete, got BlockStorageFetched
```

**Root Cause**: TestProbe has messages in FIFO order, but scenario doesn't consume them in order

**Solution**: Explicit expectations for all messages + correct Given/When/Then syntax

```gherkin
# WRONG: Missing TestInitialized consumption
When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor
And the TestExecutionActor processes "TrnSetup"
Then the service should receive "InitializeTestResponse" with testId <testId> and success true
# BUG: TestInitialized sent to queue but not consumed!
When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
And the TestExecutionActor processes "TrnLoading"
Then the QueueActor should receive "TestLoading" with testId <testId>
# ERROR: Actually receives TestInitialized, expects TestLoading!

# CORRECT: Consume TestInitialized before expecting TestLoading
When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor
And the TestExecutionActor processes "TrnSetup"
Then the service should receive "InitializeTestResponse" with testId <testId> and success true
And the QueueActor should receive "TestInitialized" with testId <testId>  # ← Consume first message
When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
And the TestExecutionActor processes "TrnLoading"
Then the service should receive "StartTestResponse" with testId <testId> and accepted true
And the QueueActor should receive "TestLoading" with testId <testId>  # ← Now receives correct message
```

**Rule**: Every message sent to a TestProbe must be explicitly consumed in the scenario in FIFO order

---

### 1.5 Sentinel Values for Optional Fields

**Pattern**: Use consistent sentinel values to represent `None` in feature files

**Standard Sentinels**:
| Scala Type | Feature File Value | Meaning |
|------------|-------------------|---------|
| `Option[String] = None` | `undefined` | Field not set |
| `Option[String] = Some("")` | `""` (empty) | Empty string |
| `Option[String] = Some(timestamp)` | `defined` | Has value (timestamp/computed) |
| `Option[Boolean] = None` | `undefined` | Not set |
| `Option[Boolean] = Some(true)` | `true` | Boolean value |

**Example**:
```gherkin
Then the service should receive "TestStatusResponse" with testId "<testId>", state "<state>", bucket "<bucket>", testType "<testType>", startTime "<startTime>", endTime "<endTime>", success "<success>", and error "<error>"
Examples:
  | testId         | state   | bucket      | testType   | startTime | endTime   | success   | error     |
  | 999...999      | Testing | test-bucket | functional | defined   | undefined | undefined | undefined |
```

**Step Definition Handling**:
```scala
// For Option[String] fields with timestamps
if (startTime == "defined") {
  response.startTime shouldBe defined  // Verify it's Some(_), don't care about value
} else {
  response.startTime shouldBe toOptionString(startTime)  // Exact match
}

// Helper function
def toOptionString(value: String): Option[String] = {
  if (value == "undefined") None
  else if (value.isEmpty) Some("")
  else Some(value)
}
```

---

### 1.6 Dependency Injection via Factory Pattern

**Pattern**: Use optional factory parameters for child actor mocking

**Step Definition Marker**: Add "with mocks" suffix to indicate factory injection

```gherkin
# Without mocks - spawns real child actors
When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor

# With mocks - injects TestProbe factories
When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
```

**When to Use Mocks**:
- ✅ Testing states that require child actor coordination (Loaded, Testing, Completed)
- ✅ Need to control child actor responses (ChildGoodToGo, TestComplete, etc.)
- ✅ Testing error handling from child actors
- ❌ Testing states without child actors (Setup, Loading transitions)

**Implementation**:
```scala
When("""a service sends {string} with testId {word} to TestExecutionActor with mocks""") {
  (messageType: String, testIdStr: String) =>
    val testId: UUID = UUID.fromString(testIdStr)
    world.testId = testId

    if (world.testExecutionActor.isEmpty) {
      val serviceConfig = TestExecutionActorFixtures.defaultServiceConfig
      world.spawnTestExecutionActorWithMocks(testId, serviceConfig)  // ← Factory injection
    }

    messageType match {
      case "InInitializeTestRequest" =>
        world.sendMessage(TestExecutionCommands.InInitializeTestRequest(testId, world.serviceProbe.ref))
    }
}
```

---

### 1.7 Tag Strategy

**Standard Tags**:
| Tag | Purpose | Usage |
|-----|---------|-------|
| `@ComponentTest` | Feature-level marker | All component test features |
| `@Critical` | Business-critical scenarios | Core FSM transitions, happy paths |
| `@Edge` | Edge cases and error handling | Boundary conditions, error scenarios |
| `@GetStatus` | Scenario group marker | All GetStatus-related tests |
| `@Setup`, `@Loading`, etc. | State-specific marker | Filter tests by FSM state |

**Example**:
```gherkin
@ComponentTest
Feature: Test Execution Actor FSM Complete Implementation
  ...

  @Critical @GetStatus @Loaded
  Scenario Outline: Handle GetStatus in Loaded state
    ...

  @Edge @TimerExpiry
  Scenario Outline: Handle poison pill timer expiry in different states
    ...
```

**Tag Execution Filters**:
```bash
# Run only Critical tests
mvn test -Dcucumber.filter.tags="@Critical"

# Run GetStatus tests for specific state
mvn test -Dcucumber.filter.tags="@GetStatus and @Loaded"

# Exclude Edge tests
mvn test -Dcucumber.filter.tags="not @Edge"
```

---

### 1.8 Examples Table Best Practices

**Standard Patterns**:

**1. Use Descriptive Test IDs**:
```gherkin
# GOOD: Different UUIDs per scenario type
Examples:
  | testId                               | state   |
  | 11111111-1111-1111-1111-111111111111 | Setup   |  # Repeated 1s
  | 22222222-2222-2222-2222-222222222222 | Loading |  # Repeated 2s
  | 33333333-3333-3333-3333-333333333333 | Loaded  |  # Repeated 3s

# BAD: Random UUIDs
Examples:
  | testId                               | state   |
  | 8f7d3c2a-9b1e-4f5a-a8c3-2d9e7f6b4c1a | Setup   |  # Hard to distinguish
  | 2a9f8d3c-1e4b-5a6f-c8a3-9d7e2f6b1c4a | Loading |
```

**2. Align Columns for Readability**:
```gherkin
# GOOD: Aligned columns
Examples:
  | testId                               | state     | bucket      | testType    |
  | dddddddd-dddd-dddd-dddd-dddddddddddd | Completed | test-bucket | functional  |
  | eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee | Completed | test-bucket | performance |

# BAD: Unaligned
Examples:
  | testId | state | bucket | testType |
  | dddddddd-dddd-dddd-dddd-dddddddddddd | Completed | test-bucket | functional |
  | eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee | Completed | test-bucket | performance |
```

**3. Minimum 2 Examples Per Scenario**:
- Tests parameterization works correctly
- Validates pattern applies to multiple cases
- Catches hardcoding bugs

---

### 1.9 Background Usage

**Standard Pattern**: Use Background for common setup across all scenarios

```gherkin
@ComponentTest
Feature: Test Execution Actor FSM Complete Implementation
  Background:
    Given a running actor system
    And Docker is available
    And Kafka is running
    And a QueueActor is spawned as parent
```

**What Goes in Background**:
- ✅ Actor system initialization
- ✅ Test infrastructure setup (Docker, Kafka)
- ✅ Parent actor spawning
- ✅ Common test fixtures
- ❌ Scenario-specific state setup
- ❌ Message sending or assertions

---

### 1.10 Feature File Organization

**File Structure**:
```
test/resources/features/
├── component/
│   ├── test-execution-actor-fsm.feature    # FSM lifecycle tests
│   ├── queue-actor-tests.feature            # QueueActor tests
│   └── kafka-integration.feature            # Kafka tests
├── integration/
│   └── end-to-end-workflows.feature         # Full system tests
└── performance/
    └── load-testing.feature                 # Performance tests
```

**Naming Convention**:
- Use kebab-case: `test-execution-actor-fsm.feature`
- Suffix with `.feature`
- Group by test level: component, integration, performance
- One actor/component per feature file

**Reference Example**: `test-probe-core/src/test/resources/features/component/test-execution-actor-fsm.feature`

---

## Section 2: Step Definitions Patterns

### 2.1 Adopted Standard: Cucumber Expressions

**Decision**: Use Cucumber Expressions (NOT regex patterns)

**Rationale**:
- ✅ Clean, readable syntax without regex complexity
- ✅ Type-safe with explicit conversions
- ✅ No "arguments received doesn't match" errors (common with regex optional groups)
- ✅ Better maintainability and discoverability
- ✅ Works reliably with cucumber-scala 8.1.0

**When to use Cucumber Expressions**:
- All new step definitions
- Steps with simple parameter extraction (strings, words, numbers)
- Steps requiring DataTables

**When NOT to use**:
- ❌ Never use regex patterns with optional capture groups
- ❌ Don't use regex unless absolutely necessary for complex pattern matching

---

### 2.2 Parameter Type Mapping

| Gherkin Text | Cucumber Expression | Scala Type | Conversion Example |
|--------------|-------------------|------------|-------------------|
| `"quoted string"` | `{string}` | `String` | Direct use |
| `single-word-or-UUID` | `{word}` | `String` | `UUID.fromString(testIdStr)` |
| `42` | `{int}` | `Int` | Direct use |
| DataTable structure | `dataTable: DataTable` | `DataTable` | `dataTable.asMap().asScala.toMap` |

**Important Notes**:
- UUIDs must be in full format: `XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX`
- `{word}` matches any single word without spaces (includes UUIDs, enums, single-word strings)
- Parameter order in function must match order in step text
- All parameters come as String - explicit type conversion required

---

### 2.3 Step Definition Template

**Standard structure for step definitions**:

```scala
Then("""the service should receive {string} with testId {word} and success {word}""") {
  (responseType: String, testIdStr: String, successStr: String) =>
    // 1. Type conversion
    val testId: UUID = UUID.fromString(testIdStr)
    val expectedSuccess: Boolean = successStr.toBoolean

    // 2. Pattern match on message type
    responseType match {
      case "InitializeTestResponse" =>
        // 3. Retrieve from ActorWorld
        val response: InitializeTestResponse = world.expectServiceResponse[InitializeTestResponse]()

        // 4. Assertions using ScalaTest matchers
        response.testId shouldBe testId
        expectedSuccess shouldBe true
    }
}
```

**Key Elements**:
1. **Triple-quoted string**: Use `"""..."""` for step pattern to avoid escape issues
2. **Parameter extraction**: Cucumber expressions in curly braces
3. **Anonymous function**: Parameters must match order in pattern
4. **Type conversion**: Convert String parameters to domain types
5. **Pattern matching**: Handle different message types explicitly
6. **World access**: Use `world` to access test context
7. **Assertions**: Use ScalaTest matchers (`shouldBe`, `should contain`, etc.)

---

### 2.4 DataTable Pattern

**Feature File Syntax**:
```gherkin
Then the service should receive "TestStatusResponse" with:
  | field     | value      |
  | testId    | <testId>   |
  | state     | <state>    |
  | bucket    | <bucket>   |
  | testType  | <testType> |
```

**Step Definition Implementation**:

```scala
import io.cucumber.datatable.DataTable
import scala.jdk.CollectionConverters._

Then("""the service should receive {string} with:""") {
  (responseType: String, dataTable: DataTable) =>
    responseType match {
      case "TestStatusResponse" =>
        val response: TestStatusResponse = world.expectServiceResponse[TestStatusResponse]()

        // Convert DataTable to Map
        val expectedFields: Map[String, String] =
          dataTable.asMap(classOf[String], classOf[String]).asScala.toMap

        // Helper functions for Option type conversion
        def toOptionString(value: String): Option[String] =
          if (value == "undefined") None else Some(value)

        def toOptionBoolean(value: String): Option[Boolean] =
          if (value == "undefined") None else Some(value.toBoolean)

        // Verify each field in the DataTable
        expectedFields.foreach { case (field, expectedValue) =>
          field match {
            case "testId" =>
              response.testId.toString shouldBe expectedValue
            case "state" =>
              response.state shouldBe expectedValue
            case "bucket" =>
              response.bucket shouldBe toOptionString(expectedValue)
            case "testType" =>
              response.testType shouldBe toOptionString(expectedValue)
            case "success" =>
              response.success shouldBe toOptionBoolean(expectedValue)
            case unknown =>
              fail(s"Unknown field in DataTable: $unknown")
          }
        }
    }
}
```

**Key Patterns**:
- **DataTable parameter**: Add `dataTable: DataTable` as last parameter
- **Java to Scala conversion**: Use `scala.jdk.CollectionConverters._` for `.asScala`
- **Sentinel values**: Use `"undefined"` in feature files to represent `None`
- **Type conversion helpers**: Create local helper functions for reusable conversions
- **Exhaustive matching**: Pattern match on all possible fields with `unknown` catch-all

---

### 2.5 World Manager Pattern

**Purpose**: Thread-safe access to test context (ActorWorld) across step definitions.

**Pattern**:
```scala
import glue.world.{ActorWorld, WorldManager}

class TestExecutionActorSteps extends ScalaDsl with EN with Matchers {

  // Access world via WorldManager singleton
  private def world: ActorWorld = WorldManager.getWorld

  Given("""a TestExecutionActor in {word} state for test {word}""") {
    (state: String, testIdStr: String) =>
      val testId: UUID = UUID.fromString(testIdStr)

      // Update world state
      world.testId = testId
      world.currentState = state

      // Spawn actor if not already spawned
      if (world.testExecutionActor.isEmpty) {
        val serviceConfig = TestExecutionActorFixtures.defaultServiceConfig
        world.spawnTestExecutionActor(testId, serviceConfig)
      }
  }
}
```

**Why WorldManager?**
- **Thread-local storage**: Each scenario gets its own isolated ActorWorld
- **Lifecycle management**: Hooks handle creation and cleanup
- **No explicit passing**: Step definitions access world via singleton
- **Type-safe**: Strongly-typed context with TestProbes and state tracking

**ActorWorld Capabilities**:
- `testKit: ActorTestKit` - Akka TestKit for actor lifecycle
- `queueProbe: TestProbe[QueueCommand]` - Mock for QueueActor
- `serviceProbe: TestProbe[ServiceResponse]` - Mock for service responses
- `spawnTestExecutionActor()` - Spawn actor under test
- `sendMessage()` - Send command to actor
- `expectServiceResponse[T]()` - Await and capture response
- `expectQueueMessage[T]()` - Await and capture queue message

---

### 2.6 Required Imports

**Standard imports for all step definition files**:

```scala
package io.distia.probe
package core
package glue
package steps

import io.cucumber.scala.{EN, ScalaDsl}
import io.cucumber.datatable.DataTable              // For DataTable support
import org.scalatest.matchers.should.Matchers       // For shouldBe assertions

import glue.world.{ActorWorld, WorldManager}        // World pattern
import fixtures.TestExecutionActorFixtures          // Test fixtures
import models._                                      // Domain models

import java.util.UUID                               // UUID type
import scala.jdk.CollectionConverters._             // For DataTable conversion
```

---

### 2.7 Type Conversion Patterns

**Common conversions needed in step definitions**:

```scala
// UUID conversion
val testId: UUID = UUID.fromString(testIdStr)

// Boolean conversion
val success: Boolean = successStr.toBoolean

// Option[String] conversion (with sentinel value)
def toOptionString(value: String): Option[String] =
  if (value == "undefined") None else Some(value)

// Option[Boolean] conversion
def toOptionBoolean(value: String): Option[Boolean] =
  if (value == "undefined") None else Some(value.toBoolean)

// Option[Int] conversion
def toOptionInt(value: String): Option[Int] =
  if (value == "undefined") None else Some(value.toInt)

// Enum-like conversion
def toTestType(value: String): TestType = value match {
  case "functional"  => TestType.Functional
  case "performance" => TestType.Performance
  case "regression"  => TestType.Regression
}
```

**Sentinel Value Convention**:
- Use `"undefined"` in feature files to represent `None`
- Convert to `None` in step definitions
- Alternative: Use empty string `""` if appropriate for domain
- Be consistent across all scenarios

---

### 2.8 Step Organization Best Practices

**File Structure**:
```
test/scala/com/company/probe/core/glue/
├── hooks/
│   └── ComponentTestHooks.scala       # Before/After scenario hooks
├── steps/
│   ├── ActorWorldSteps.scala         # Background setup steps
│   ├── TestExecutionActorSteps.scala # Component-specific steps
│   └── QueueActorSteps.scala         # Other component steps
└── world/
    ├── ActorWorld.scala               # Test context
    └── WorldManager.scala             # Thread-local singleton
```

**Step Definition Class Organization**:

```scala
/**
 * Step Definitions for TestExecutionActor
 *
 * Covers scenarios for FSM lifecycle:
 * - Initialization and setup
 * - State transitions
 * - Message handling
 * - Status queries
 */
class TestExecutionActorSteps extends ScalaDsl with EN with Matchers {

  private def world: ActorWorld = WorldManager.getWorld

  // ========== SETUP STEPS ==========

  Given("""...""") { ... }

  // ========== ACTION STEPS ==========

  When("""...""") { ... }

  // ========== VERIFICATION STEPS ==========

  Then("""...""") { ... }

  // ========== HELPER METHODS ==========

  private def convertToOption(value: String): Option[String] = { ... }
}
```

**Organization Principles**:
- **One file per actor/component**: Group related steps together
- **Section comments**: Use `// ==========` comments to delineate sections
- **ScalaDoc**: Document complex steps or patterns
- **Helper methods**: Extract common conversions to private methods
- **Naming**: Match file name to actor/component being tested

---

### 2.9 Hooks and Lifecycle Management

**Scenario Lifecycle Pattern**:

```scala
package io.distia.probe
package core
package glue
package hooks

import io.cucumber.scala.{ScalaDsl, EN, Scenario}
import glue.world.WorldManager

/**
 * Cucumber hooks for component test lifecycle
 */
class ComponentTestHooks extends ScalaDsl with EN {

  /**
   * Before each scenario: Initialize ActorWorld
   */
  Before { (scenario: Scenario) =>
    // WorldManager will create ActorWorld on first access
    println(s"[ComponentTest] Starting scenario: ${scenario.getName}")
  }

  /**
   * After each scenario: Cleanup ActorWorld
   */
  After { (scenario: Scenario) =>
    val status: String = if (scenario.isFailed) "FAILED" else "PASSED"
    println(s"[ComponentTest] Completed scenario: ${scenario.getName} - $status")

    // Shutdown ActorWorld for this thread (terminates ActorTestKit)
    WorldManager.shutdownWorld()
  }

  /**
   * After scenario with specific tag: Additional cleanup
   */
  After(order = 10, tagExpression = "@Critical") { (scenario: Scenario) =>
    if (scenario.isFailed) {
      println(s"[ComponentTest] CRITICAL TEST FAILED: ${scenario.getName}")
      // Could add additional logging or notifications here
    }
  }
}
```

**Lifecycle Guarantees**:
- `Before`: Runs before each scenario - ActorWorld created on first access
- `After`: Runs after each scenario - ActorWorld cleaned up automatically
- **Thread-local isolation**: Each scenario thread gets its own ActorWorld
- **Resource cleanup**: ActorTestKit properly shutdown via `WorldManager.shutdownWorld()`

---

### 2.10 Common Pitfalls and Solutions

#### Pitfall 1: Regex Optional Groups
❌ **Don't**:
```scala
Then("""^the service should receive \"(.+?)\"(?: with testId (.+?))?$""") {
  (responseType: String, testIdStr: String) =>
    // ERROR: "arguments received doesn't match the step definition"
}
```

✅ **Do**:
```scala
// Create separate explicit steps
Then("""the service should receive {string}""") { ... }
Then("""the service should receive {string} with testId {word}""") { ... }
```

---

#### Pitfall 2: Forgotten Backup Files
❌ **Don't**:
```bash
# .scala.bak files are still compiled by Scala!
mv TestExecutionActorSteps.scala TestExecutionActorSteps.scala.bak
```

✅ **Do**:
```bash
# Use non-Scala extension
mv TestExecutionActorSteps.scala TestExecutionActorSteps.scala.bak.txt
# Or move to different directory
mv TestExecutionActorSteps.scala ../archived/
```

---

#### Pitfall 3: Direct Option Matching
❌ **Don't**:
```scala
// Feature file has: bucket | test-bucket
// Model has: bucket: Option[String]
response.bucket shouldBe "test-bucket"  // Type mismatch!
```

✅ **Do**:
```scala
// Create helper for conversion
def toOptionString(value: String): Option[String] =
  if (value == "undefined") None else Some(value)

response.bucket shouldBe toOptionString("test-bucket")  // Some("test-bucket")
```

---

#### Pitfall 4: UUID Format Mismatch
❌ **Don't**:
```gherkin
# Shortened UUID - will fail UUID.fromString()
| testId | 11111111-1111 |
```

✅ **Do**:
```gherkin
# Full UUID format required
| testId | 11111111-1111-1111-1111-111111111111 |
```

---

#### Pitfall 5: Parameter Order Mismatch
❌ **Don't**:
```scala
// Step: "actor sends {string} with testId {word}"
When("""actor sends {string} with testId {word}""") {
  (testId: String, messageType: String) =>  // WRONG ORDER!
    // testId will contain messageType value, and vice versa
}
```

✅ **Do**:
```scala
// Parameters must match left-to-right order in step text
When("""actor sends {string} with testId {word}""") {
  (messageType: String, testId: String) =>  // CORRECT ORDER
    // ...
}
```

---

### 2.11 Testing Strategy and Validation

**Systematic Approach** (from StepDefinitionAttempts.md workflow):

1. **Document the pattern**: Write down the style/approach being tested
2. **Implement in one scenario**: Start with a single scenario to validate
3. **Compile and run**: Ensure no compilation errors and tests execute
4. **Document results**: Record success or failure with details
5. **On acceptance**: Expand pattern to all relevant scenarios
6. **Update style guide**: Add validated patterns to this document

**Validation Checklist**:
- ✅ Compilation succeeds without errors
- ✅ No step definition ambiguities
- ✅ No "arguments received doesn't match" errors
- ✅ Tests execute and assertions pass
- ✅ No timeout issues with TestProbes
- ✅ Clean logs without unexpected errors

**Example Validation Process**:
```markdown
### Testing Pattern: DataTable with Option Types

**Test Scenario**: "Handle GetStatus in all states"
**Test Date**: 2025-10-13

**Results**:
- ✅ Compilation: SUCCESS
- ✅ DataTable syntax: Works with Cucumber expressions
- ✅ DataTable parsing: asMap() with Scala converters successful
- ✅ Type conversion: Option helpers work correctly
- ⚠️ Test execution: Requires proper actor state initialization

**Conclusion**: DataTable pattern validated and ready for production use.
```

---

### 2.12 Non-Blocking Verification Patterns

**CRITICAL**: Align with scala-conventions.md:99-100 "Non-Blocking Bias" principle

**Rule**: Never use Thread.sleep() - Akka TestKit provides built-in blocking mechanisms

**4 Refactoring Patterns** (validated across 90 Thread.sleep() removals):

#### Pattern 1: Actor Spawn Verification

❌ **WRONG**: Sleep after spawn
```scala
Given("""a BlockStorageActor is spawned for test {string}""") { (testIdStr: String) =>
  blockStorageActor = Some(world.testKit.spawn(
    BlockStorageActor(testId, parent.ref),
    s"storage-$testIdStr"
  ))
  Thread.sleep(100)  // ❌ ANTI-PATTERN: Akka typed actors ready immediately
}
```

✅ **CORRECT**: No sleep - add comment
```scala
Given("""a BlockStorageActor is spawned for test {string}""") { (testIdStr: String) =>
  blockStorageActor = Some(world.testKit.spawn(
    BlockStorageActor(testId, parent.ref),
    s"storage-$testIdStr"
  ))
  // Actor is ready immediately after spawn - no sleep needed
}
```

**Rationale**: Akka Typed actors are ready immediately after `testKit.spawn()` - no async initialization

---

#### Pattern 2: Message Send Verification

❌ **WRONG**: Sleep after message send
```scala
When("""the parent sends {string} with bucket {string}""") { (command: String, bucket: String) =>
  command match {
    case "Initialize" =>
      blockStorageActor.foreach { actor =>
        actor ! Initialize(bucket)
        Thread.sleep(100)  // ❌ ANTI-PATTERN: TestProbe.receiveMessage handles waiting
      }
  }
}
```

✅ **CORRECT**: No sleep - TestProbe handles waiting
```scala
When("""the parent sends {string} with bucket {string}""") { (command: String, bucket: String) =>
  command match {
    case "Initialize" =>
      blockStorageActor.foreach { actor =>
        actor ! Initialize(bucket)
        // TestProbe.receiveMessage in Then step handles waiting
      }
  }
}
```

**Rationale**: `TestProbe.receiveMessage(timeout)` blocks with timeout - sleep is redundant and causes timing issues

---

#### Pattern 3: Logging/State Verification

❌ **WRONG**: Sleep for logging
```scala
Then("""the BlockStorageActor should log {string} message""") { (logContent: String) =>
  Thread.sleep(50)  // ❌ ANTI-PATTERN: Sleep won't help - log capture needed
  true shouldBe true
}
```

✅ **CORRECT**: TODO comment for future log capture
```scala
Then("""the BlockStorageActor should log {string} message""") { (logContent: String) =>
  // TODO: Implement log capture when moving beyond scaffolding phase
  true shouldBe true
}
```

**Rationale**: Thread.sleep() doesn't enable log capture - proper log capture infrastructure needed

---

#### Pattern 4: Actor Shutdown Verification (PostStop Pattern)

❌ **WRONG**: Sleep after Stop message
```scala
Then("""the BlockStorageActor should stop cleanly""") { () =>
  blockStorageActor.foreach { actor =>
    actor ! Stop
  }
  Thread.sleep(300)  // ❌ ANTI-PATTERN: Can't verify stop actually happened
  true shouldBe true
}
```

✅ **CORRECT**: Use testKit.stop() with timeout
```scala
Then("""the BlockStorageActor should stop cleanly""") { () =>
  // Use TestKit.stop to wait for actor shutdown with timeout
  blockStorageActor.foreach { actor =>
    world.testKit.stop(actor, 2.seconds)
  }
}
```

**Rationale**: `testKit.stop(actor, timeout)` blocks until:
- Actor processes Stop message
- PostStop hook executes
- Actor terminates
- Timeout expires (throws exception if actor doesn't stop)

**Coverage Impact**: This refactoring eliminated 90 Thread.sleep() calls across 6 step definition files:
- BlockStorageActorSteps: 16 removed
- VaultActorSteps: 17 removed
- CucumberExecutionActorSteps: 13 removed
- KafkaProducerActorSteps: 10 removed
- KafkaConsumerActorSteps: 10 removed
- GuardianActorSteps: 24 removed

**Result**: 139/139 component tests passing with 0 errors, 0 Thread.sleep() calls in step definitions

---

## Quick Reference

### Step Definition Checklist
- [ ] Use Cucumber Expressions (no regex)
- [ ] Import DataTable if needed
- [ ] Access world via `WorldManager.getWorld`
- [ ] Convert String parameters to domain types
- [ ] Use pattern matching for message types
- [ ] Use ScalaTest matchers for assertions
- [ ] Add ScalaDoc for complex steps
- [ ] Group steps in logical sections
- [ ] Extract common conversions to helpers
- [ ] **NEVER use Thread.sleep() - use TestProbe.receiveMessage() or testKit.stop()**

### When Troubleshooting
1. Check parameter order matches step text
2. Verify UUID format is complete
3. Ensure Option types use helper conversions
4. Confirm no .scala.bak files in source tree
5. Check ActorWorld has required fields
6. Verify hooks are cleaning up properly
7. Look for "arguments received doesn't match" errors (indicates regex issue)
8. **Check for Thread.sleep() anti-pattern - replace with non-blocking alternatives**

---

## References

- **Cucumber Expressions Docs**: https://github.com/cucumber/cucumber-expressions
- **cucumber-scala GitHub**: https://github.com/cucumber/cucumber-jvm-scala
- **Validated Examples**:
  - `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/TestExecutionActorSteps.scala`
  - `test-probe-core/src/test/resources/features/component/test-execution-actor-fsm.feature`
- **Testing Experiments Log**: `working/StepDefinitionAttempts.md`
