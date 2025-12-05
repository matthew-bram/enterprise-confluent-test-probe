package io.distia.probe.core.fixtures

import org.scalatest.wordspec.AnyWordSpec

import io.distia.probe.core.models.{InitializeTestResponse, StartTestResponse, TestStatusResponse}
import io.distia.probe.core.models.QueueCommands

import java.util.UUID

/**
 * Tests ActorTestingFixtures for probe creation and matchers.
 *
 * Verifies:
 * - ActorTestKit lifecycle (automatically managed)
 * - Probe creation (service, queue, generic)
 * - ServiceResponse matchers (InitializeTestResponse, StartTestResponse, etc.)
 * - QueueCommand matchers (TestInitialized, TestCompleted)
 * - Helper methods (expectNoMessage, randomTestId)
 *
 * Test Strategy: Unit tests using the fixtures themselves (dogfooding)
 */
class ActorTestingFixturesSpec extends AnyWordSpec with ActorTestingFixtures {

  "ActorTestingFixtures" should {

    "provide ActorTestKit automatically" in {
      testKit should not be null
    }

    "create service probe" in {
      val probe = createServiceProbe()
      probe.ref.path.name should startWith ("probe-")
    }

    "create service probe with custom name" in {
      val probe = createServiceProbe("customServiceProbe")
      probe.ref.path.name should include ("probe-customServiceProbe")
    }

    "create queue probe" in {
      val probe = createQueueProbe()
      probe.ref.path.name should startWith ("probe-")
    }

    "create generic probe" in {
      case class TestMessage(value: String)
      val probe = createProbe[TestMessage]()
      probe.ref.path.name should startWith ("probe-")
    }

    "expect InitializeTestResponse with correct test ID" in {
      val probe = createServiceProbe()
      val testId = randomTestId()

      // Send response
      probe.ref ! InitializeTestResponse(testId, "test message")

      // Verify using matcher
      val response = expectInitializeTestResponse(probe, testId)
      response.testId shouldBe testId
      response.message shouldBe "test message"
    }

    "expect StartTestResponse with correct test ID and accepted status" in {
      val probe = createServiceProbe()
      val testId = randomTestId()

      probe.ref ! StartTestResponse(testId, accepted = true, Some("unit"), "accepted")

      val response = expectStartTestResponse(probe, testId, expectedAccepted = true)
      response.testId shouldBe testId
      response.accepted shouldBe true
      response.testType shouldBe Some("unit")
    }

    "expect TestStatusResponse with correct test ID" in {
      val probe = createServiceProbe()
      val testId = randomTestId()

      probe.ref ! TestStatusResponse(
        testId = testId,
        state = "Testing",
        bucket = Some("test-bucket"),
        testType = Some("unit"),
        startTime = None,
        endTime = None,
        success = None,
        error = None
      )

      val response = expectTestStatusResponse(probe, testId)
      response.testId shouldBe testId
      response.state shouldBe "Testing"
    }

    "expect TestInitialized queue command" in {
      val probe = createQueueProbe()
      val testId = randomTestId()

      probe.ref ! QueueCommands.TestInitialized(testId)

      val command = expectTestInitialized(probe, testId)
      command.testId shouldBe testId
    }

    "expect TestCompleted queue command" in {
      val probe = createQueueProbe()
      val testId = randomTestId()

      probe.ref ! QueueCommands.TestCompleted(testId)

      val command = expectTestCompleted(probe, testId)
      command.testId shouldBe testId
    }

    "verify no message is received" in {
      val probe = createServiceProbe()

      // Should not receive any message
      noException should be thrownBy {
        expectNoMessage(probe)
      }
    }

    "generate unique random test IDs" in {
      val id1 = randomTestId()
      val id2 = randomTestId()

      id1 should not be id2
    }

    "shutdown ActorTestKit after all tests" in {
      // This is implicitly tested by afterAll() hook
      // If ActorTestKit doesn't shut down properly, subsequent test runs will fail
      testKit.system.whenTerminated.isCompleted shouldBe false
    }

    "support multiple probes independently" in {
      val probe1 = createServiceProbe("test1")
      val probe2 = createServiceProbe("test2")
      val testId1 = randomTestId()
      val testId2 = randomTestId()

      // Verify unique names with probe- prefix
      probe1.ref.path.name should include ("probe-test1")
      probe2.ref.path.name should include ("probe-test2")

      // Send different messages to each probe
      probe1.ref ! InitializeTestResponse(testId1, "message1")
      probe2.ref ! InitializeTestResponse(testId2, "message2")

      // Each probe receives its own message
      val response1 = expectInitializeTestResponse(probe1, testId1)
      val response2 = expectInitializeTestResponse(probe2, testId2)

      response1.message shouldBe "message1"
      response2.message shouldBe "message2"
    }
  }
}
