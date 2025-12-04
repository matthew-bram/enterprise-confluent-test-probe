package io.distia.probe
package core
package services
package cucumber

import io.cucumber.plugin.event.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

/**
 * Comprehensive tests for TestExecutionEventListener.
 *
 * Tests all event handlers, status types, and result aggregation logic:
 * - TestRunStarted event handling (start time recording)
 * - TestRunFinished event handling (end time recording)
 * - TestCaseFinished event handling (scenario results)
 * - TestStepFinished event handling (step results)
 * - Result aggregation (passed/failed/skipped counts)
 * - Duration calculation
 * - Error message generation
 * - Edge cases (no scenarios, mixed results, etc.)
 *
 * Test Strategy: Unit tests with mock Cucumber events
 */
class TestExecutionEventListenerSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach:

  private val testId: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")

  override def beforeEach(): Unit =
    // Register test ID before each test (required by listener constructor)
    TestExecutionListenerRegistry.registerTestId(testId)

  override def afterEach(): Unit =
    // Clean up registry after each test
    try { TestExecutionListenerRegistry.unregister(testId) }
    catch { case _: Exception => () } // Ignore if not registered

  // Helper to create a mock TestRunStarted event
  private def createTestRunStartedEvent(instant: Instant): TestRunStarted =
    new TestRunStarted(instant)

  // Helper to create a mock TestRunFinished event
  private def createTestRunFinishedEvent(instant: Instant): TestRunFinished =
    new TestRunFinished(instant)

  // Helper to create a mock Result with specified status
  private def createResult(status: Status): Result =
    new Result(status, java.time.Duration.ZERO, null)

  // Helper to create a mock Result with specified status and error
  private def createResultWithError(status: Status, error: Throwable): Result =
    new Result(status, java.time.Duration.ZERO, error)

  "TestExecutionEventListener constructor" should:

    "register itself with TestExecutionListenerRegistry" in:
      val listener = new TestExecutionEventListener()

      val retrievedListener = TestExecutionListenerRegistry.getListener(testId)
      retrievedListener shouldBe listener

    "retrieve testId from ThreadLocal registry" in:
      val listener = new TestExecutionEventListener()
      val result = listener.getResult

      result.testId shouldBe testId

  "TestExecutionEventListener.handleTestRunStarted" should:

    "record start time from event" in:
      val listener = new TestExecutionEventListener()
      val startTime = Instant.parse("2025-01-15T10:00:00Z")

      listener.handleTestRunStarted(createTestRunStartedEvent(startTime))

      // Verify via getResult - duration should be 0 since no end time yet
      val result = listener.getResult
      result.durationMillis shouldBe 0L

    "overwrite previous start time if called multiple times" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestRunStarted(createTestRunStartedEvent(Instant.parse("2025-01-15T10:00:00Z")))
      listener.handleTestRunStarted(createTestRunStartedEvent(Instant.parse("2025-01-15T11:00:00Z")))
      listener.handleTestRunFinished(createTestRunFinishedEvent(Instant.parse("2025-01-15T11:00:30Z")))

      val result = listener.getResult
      // Duration should be 30 seconds from second start time
      result.durationMillis shouldBe 30000L

  "TestExecutionEventListener.handleTestRunFinished" should:

    "record end time from event" in:
      val listener = new TestExecutionEventListener()
      val startTime = Instant.parse("2025-01-15T10:00:00Z")
      val endTime = Instant.parse("2025-01-15T10:05:30Z")

      listener.handleTestRunStarted(createTestRunStartedEvent(startTime))
      listener.handleTestRunFinished(createTestRunFinishedEvent(endTime))

      val result = listener.getResult
      // 5 minutes 30 seconds = 330 seconds = 330000 milliseconds
      result.durationMillis shouldBe 330000L

  "TestExecutionEventListener.handleTestCaseFinished" should:

    "increment scenariosPassed when scenario passes" in:
      val listener = new TestExecutionEventListener()
      val event = createMockTestCaseFinishedEvent("Passing scenario", Status.PASSED)

      listener.handleTestCaseFinished(event)

      val result = listener.getResult
      result.scenarioCount shouldBe 1
      result.scenariosPassed shouldBe 1
      result.scenariosFailed shouldBe 0
      result.scenariosSkipped shouldBe 0

    "increment scenariosFailed and record name when scenario fails" in:
      val listener = new TestExecutionEventListener()
      val event = createMockTestCaseFinishedEvent("Failing scenario", Status.FAILED)

      listener.handleTestCaseFinished(event)

      val result = listener.getResult
      result.scenarioCount shouldBe 1
      result.scenariosPassed shouldBe 0
      result.scenariosFailed shouldBe 1
      result.failedScenarios should contain("Failing scenario")

    "increment scenariosSkipped when scenario is skipped" in:
      val listener = new TestExecutionEventListener()
      val event = createMockTestCaseFinishedEvent("Skipped scenario", Status.SKIPPED)

      listener.handleTestCaseFinished(event)

      val result = listener.getResult
      result.scenarioCount shouldBe 1
      result.scenariosPassed shouldBe 0
      result.scenariosFailed shouldBe 0
      result.scenariosSkipped shouldBe 1

    "increment scenariosSkipped when scenario is pending" in:
      val listener = new TestExecutionEventListener()
      val event = createMockTestCaseFinishedEvent("Pending scenario", Status.PENDING)

      listener.handleTestCaseFinished(event)

      val result = listener.getResult
      result.scenariosSkipped shouldBe 1

    "handle multiple scenarios with different statuses" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Scenario 1", Status.PASSED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Scenario 2", Status.PASSED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Scenario 3", Status.FAILED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Scenario 4", Status.SKIPPED))

      val result = listener.getResult
      result.scenarioCount shouldBe 4
      result.scenariosPassed shouldBe 2
      result.scenariosFailed shouldBe 1
      result.scenariosSkipped shouldBe 1
      result.failedScenarios should contain("Scenario 3")

  "TestExecutionEventListener.handleTestStepFinished" should:

    "count passed steps correctly" in:
      val listener = new TestExecutionEventListener()
      val event = createMockTestStepFinishedEvent("Test scenario", "Given step", Status.PASSED)

      listener.handleTestStepFinished(event)

      val result = listener.getResult
      result.stepCount shouldBe 1
      result.stepsPassed shouldBe 1
      result.stepsFailed shouldBe 0
      result.stepsSkipped shouldBe 0
      result.stepsUndefined shouldBe 0

    "count failed steps correctly" in:
      val listener = new TestExecutionEventListener()
      val event = createMockTestStepFinishedEvent("Test scenario", "When step", Status.FAILED)

      listener.handleTestStepFinished(event)

      val result = listener.getResult
      result.stepCount shouldBe 1
      result.stepsFailed shouldBe 1

    "count skipped steps correctly" in:
      val listener = new TestExecutionEventListener()
      val event = createMockTestStepFinishedEvent("Test scenario", "Then step", Status.SKIPPED)

      listener.handleTestStepFinished(event)

      val result = listener.getResult
      result.stepsSkipped shouldBe 1

    "count pending steps as skipped" in:
      val listener = new TestExecutionEventListener()
      val event = createMockTestStepFinishedEvent("Test scenario", "And step", Status.PENDING)

      listener.handleTestStepFinished(event)

      val result = listener.getResult
      result.stepsSkipped shouldBe 1

    "count unused steps as skipped" in:
      val listener = new TestExecutionEventListener()
      val event = createMockTestStepFinishedEvent("Test scenario", "But step", Status.UNUSED)

      listener.handleTestStepFinished(event)

      val result = listener.getResult
      result.stepsSkipped shouldBe 1

    "count undefined steps correctly" in:
      val listener = new TestExecutionEventListener()
      val event = createMockTestStepFinishedEvent("Test scenario", "Given undefined", Status.UNDEFINED)

      listener.handleTestStepFinished(event)

      val result = listener.getResult
      result.stepsUndefined shouldBe 1

    "count ambiguous steps as undefined" in:
      val listener = new TestExecutionEventListener()
      val event = createMockTestStepFinishedEvent("Test scenario", "When ambiguous", Status.AMBIGUOUS)

      listener.handleTestStepFinished(event)

      val result = listener.getResult
      result.stepsUndefined shouldBe 1

    "handle multiple steps with different statuses" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestStepFinished(createMockTestStepFinishedEvent("Scenario", "Step1", Status.PASSED))
      listener.handleTestStepFinished(createMockTestStepFinishedEvent("Scenario", "Step2", Status.PASSED))
      listener.handleTestStepFinished(createMockTestStepFinishedEvent("Scenario", "Step3", Status.FAILED))
      listener.handleTestStepFinished(createMockTestStepFinishedEvent("Scenario", "Step4", Status.SKIPPED))
      listener.handleTestStepFinished(createMockTestStepFinishedEvent("Scenario", "Step5", Status.UNDEFINED))

      val result = listener.getResult
      result.stepCount shouldBe 5
      result.stepsPassed shouldBe 2
      result.stepsFailed shouldBe 1
      result.stepsSkipped shouldBe 1
      result.stepsUndefined shouldBe 1

  "TestExecutionEventListener.getResult duration calculation" should:

    "calculate duration correctly from start and end times" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestRunStarted(createTestRunStartedEvent(Instant.parse("2025-01-15T10:00:00Z")))
      listener.handleTestRunFinished(createTestRunFinishedEvent(Instant.parse("2025-01-15T10:01:30Z")))

      val result = listener.getResult
      result.durationMillis shouldBe 90000L // 90 seconds

    "return 0 duration if start time is null" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestRunFinished(createTestRunFinishedEvent(Instant.now()))

      val result = listener.getResult
      result.durationMillis shouldBe 0L

    "return 0 duration if end time is null" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestRunStarted(createTestRunStartedEvent(Instant.now()))

      val result = listener.getResult
      result.durationMillis shouldBe 0L

    "return 0 duration if both times are null" in:
      val listener = new TestExecutionEventListener()

      val result = listener.getResult
      result.durationMillis shouldBe 0L

  "TestExecutionEventListener.getResult passed flag" should:

    "set passed=true when all scenarios pass" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Scenario 1", Status.PASSED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Scenario 2", Status.PASSED))

      val result = listener.getResult
      result.passed shouldBe true

    "set passed=false when any scenario fails" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Scenario 1", Status.PASSED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Scenario 2", Status.FAILED))

      val result = listener.getResult
      result.passed shouldBe false

    "set passed=false when scenario is skipped" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Scenario 1", Status.PASSED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Scenario 2", Status.SKIPPED))

      val result = listener.getResult
      result.passed shouldBe false

    "set passed=false when no scenarios run" in:
      val listener = new TestExecutionEventListener()

      val result = listener.getResult
      result.passed shouldBe false

  "TestExecutionEventListener.getResult error message" should:

    "return None when no scenarios fail" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Scenario", Status.PASSED))

      val result = listener.getResult
      result.errorMessage shouldBe None

    "generate error message for single failed scenario" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Failed test", Status.FAILED))

      val result = listener.getResult
      result.errorMessage shouldBe Some("1 scenario(s) failed")

    "generate error message for multiple failed scenarios" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Failed test 1", Status.FAILED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Failed test 2", Status.FAILED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Failed test 3", Status.FAILED))

      val result = listener.getResult
      result.errorMessage shouldBe Some("3 scenario(s) failed")

  "TestExecutionEventListener.getResult failedScenarios" should:

    "return empty list when no scenarios fail" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Passing scenario", Status.PASSED))

      val result = listener.getResult
      result.failedScenarios shouldBe empty

    "return list of failed scenario names" in:
      val listener = new TestExecutionEventListener()

      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("User login fails", Status.FAILED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Passing test", Status.PASSED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Payment processing fails", Status.FAILED))

      val result = listener.getResult
      result.failedScenarios should have size 2
      result.failedScenarios should contain allOf("User login fails", "Payment processing fails")

  "TestExecutionEventListener edge cases" should:

    "handle zero scenarios gracefully" in:
      val listener = new TestExecutionEventListener()

      val result = listener.getResult
      result.scenarioCount shouldBe 0
      result.scenariosPassed shouldBe 0
      result.scenariosFailed shouldBe 0
      result.scenariosSkipped shouldBe 0
      result.passed shouldBe false
      result.errorMessage shouldBe None
      result.failedScenarios shouldBe empty

    "handle zero steps gracefully" in:
      val listener = new TestExecutionEventListener()

      val result = listener.getResult
      result.stepCount shouldBe 0
      result.stepsPassed shouldBe 0
      result.stepsFailed shouldBe 0
      result.stepsSkipped shouldBe 0
      result.stepsUndefined shouldBe 0

    "generate complete TestExecutionResult with all fields" in:
      val listener = new TestExecutionEventListener()
      val startTime = Instant.parse("2025-01-15T10:00:00Z")
      val endTime = Instant.parse("2025-01-15T10:02:30Z")

      listener.handleTestRunStarted(createTestRunStartedEvent(startTime))

      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Passing scenario 1", Status.PASSED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Passing scenario 2", Status.PASSED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Failing scenario", Status.FAILED))
      listener.handleTestCaseFinished(createMockTestCaseFinishedEvent("Skipped scenario", Status.SKIPPED))

      listener.handleTestStepFinished(createMockTestStepFinishedEvent("S1", "Step1", Status.PASSED))
      listener.handleTestStepFinished(createMockTestStepFinishedEvent("S1", "Step2", Status.PASSED))
      listener.handleTestStepFinished(createMockTestStepFinishedEvent("S2", "Step3", Status.FAILED))
      listener.handleTestStepFinished(createMockTestStepFinishedEvent("S3", "Step4", Status.UNDEFINED))

      listener.handleTestRunFinished(createTestRunFinishedEvent(endTime))

      val result = listener.getResult
      result.testId shouldBe testId
      result.passed shouldBe false
      result.scenarioCount shouldBe 4
      result.scenariosPassed shouldBe 2
      result.scenariosFailed shouldBe 1
      result.scenariosSkipped shouldBe 1
      result.stepCount shouldBe 4
      result.stepsPassed shouldBe 2
      result.stepsFailed shouldBe 1
      result.stepsSkipped shouldBe 0
      result.stepsUndefined shouldBe 1
      result.durationMillis shouldBe 150000L // 2 minutes 30 seconds
      result.errorMessage shouldBe Some("1 scenario(s) failed")
      result.failedScenarios should contain("Failing scenario")

  "TestExecutionEventListener.setEventPublisher" should:

    "register handlers for all required event types" in:
      val listener = new TestExecutionEventListener()
      var testRunStartedHandled = false
      var testCaseFinishedHandled = false
      var testStepFinishedHandled = false
      var testRunFinishedHandled = false

      // Create a mock publisher that tracks registrations
      val publisher = new EventPublisher {
        override def registerHandlerFor[T](eventType: Class[T], handler: EventHandler[T]): Unit =
          eventType match
            case c if c == classOf[TestRunStarted] => testRunStartedHandled = true
            case c if c == classOf[TestCaseFinished] => testCaseFinishedHandled = true
            case c if c == classOf[TestStepFinished] => testStepFinishedHandled = true
            case c if c == classOf[TestRunFinished] => testRunFinishedHandled = true
            case _ => // Other event types

        override def removeHandlerFor[T](eventType: Class[T], handler: EventHandler[T]): Unit = ()
      }

      listener.setEventPublisher(publisher)

      testRunStartedHandled shouldBe true
      testCaseFinishedHandled shouldBe true
      testStepFinishedHandled shouldBe true
      testRunFinishedHandled shouldBe true

  // Helper to create a mock Location (Location is a final class, not an interface)
  private def createMockLocation: io.cucumber.plugin.event.Location =
    new io.cucumber.plugin.event.Location(1, 0)

  // Mock helpers using anonymous classes that implement Cucumber interfaces
  private def createMockTestCaseFinishedEvent(scenarioName: String, status: Status): TestCaseFinished =
    val testCase = new TestCase {
      override def getId: UUID = UUID.randomUUID()
      override def getUri: java.net.URI = java.net.URI.create("file:///test.feature")
      override def getName: String = scenarioName
      override def getScenarioDesignation: String = scenarioName
      override def getLine: Integer = 1
      override def getLocation: io.cucumber.plugin.event.Location = createMockLocation
      override def getKeyword: String = "Scenario"
      override def getTags: java.util.List[String] = java.util.Collections.emptyList()
      override def getTestSteps: java.util.List[TestStep] = java.util.Collections.emptyList()
    }

    new TestCaseFinished(Instant.now(), testCase, createResult(status))

  private def createMockTestStepFinishedEvent(scenarioName: String, stepLocation: String, status: Status): TestStepFinished =
    val testCase = new TestCase {
      override def getId: UUID = UUID.randomUUID()
      override def getUri: java.net.URI = java.net.URI.create("file:///test.feature")
      override def getName: String = scenarioName
      override def getScenarioDesignation: String = scenarioName
      override def getLine: Integer = 1
      override def getLocation: io.cucumber.plugin.event.Location = createMockLocation
      override def getKeyword: String = "Scenario"
      override def getTags: java.util.List[String] = java.util.Collections.emptyList()
      override def getTestSteps: java.util.List[TestStep] = java.util.Collections.emptyList()
    }

    val testStep = new TestStep {
      override def getId: UUID = UUID.randomUUID()
      override def getCodeLocation: String = stepLocation
    }

    new TestStepFinished(Instant.now(), testCase, testStep, createResult(status))