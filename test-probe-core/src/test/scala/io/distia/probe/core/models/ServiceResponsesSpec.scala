package io.distia.probe
package core
package models

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/**
 * Unit tests for ServiceResponses model classes.
 *
 * Tests all service response types used for actor-to-parent communication,
 * including success responses, error responses, and state transitions.
 *
 * Coverage Focus:
 * - Response construction with all required fields
 * - Field access and validation
 * - ActorRef handling in responses
 * - Error response message preservation
 *
 * Test Strategy:
 * - Each response type has dedicated test suite
 * - Validates correct field assignment
 * - Tests response type hierarchy
 */
class ServiceResponsesSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val testKit: ActorTestKit = ActorTestKit()
  private val testId: UUID = UUID.fromString("12345678-1234-1234-1234-123456789012")

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }

  "ActorSystemInitializationSuccess" should {

    "be a ServiceResponse" in {
      ActorSystemInitializationSuccess shouldBe a[ServiceResponse]
    }

    "be a case object (singleton)" in {
      ActorSystemInitializationSuccess shouldBe ActorSystemInitializationSuccess
    }
  }

  "ActorSystemInitializationFailure" should {

    "construct with exception" in {
      val exception = new RuntimeException("Boot failed")
      val response = ActorSystemInitializationFailure(exception)

      response.exception shouldBe exception
      response shouldBe a[ServiceResponse]
    }

    "store exception correctly" in {
      val exception = new IllegalStateException("Invalid config")
      val response = ActorSystemInitializationFailure(exception)

      response.exception.getMessage shouldBe "Invalid config"
      response.exception shouldBe a[IllegalStateException]
    }
  }

  "QueueActorReference" should {

    "construct with ActorRef" in {
      val probe = testKit.createTestProbe[QueueCommands.QueueCommand]()
      val response = QueueActorReference(probe.ref)

      response.queueActorRef shouldBe probe.ref
      response shouldBe a[ServiceResponse]
    }
  }

  "InitializeTestResponse" should {

    "construct with testId and default message" in {
      val response = InitializeTestResponse(testId)

      response.testId shouldBe testId
      response.message shouldBe "Test initialized - upload files to {bucket}/{testId}/"
      response shouldBe a[ServiceResponse]
    }

    "construct with testId and custom message" in {
      val customMessage = "Custom initialization message"
      val response = InitializeTestResponse(testId, customMessage)

      response.testId shouldBe testId
      response.message shouldBe customMessage
    }
  }

  "StartTestResponse" should {

    "construct with all required fields (no testType)" in {
      val response = StartTestResponse(
        testId = testId,
        accepted = true,
        testType = None
      )

      response.testId shouldBe testId
      response.accepted shouldBe true
      response.testType shouldBe None
      response.message shouldBe "Test accepted and will be executed"
      response shouldBe a[ServiceResponse]
    }

    "construct with all required fields (with testType)" in {
      val response = StartTestResponse(
        testId = testId,
        accepted = true,
        testType = Some("integration")
      )

      response.testId shouldBe testId
      response.accepted shouldBe true
      response.testType shouldBe Some("integration")
      response.message shouldBe "Test accepted and will be executed"
    }

    "construct with custom message" in {
      val customMessage = "Test queued for execution"
      val response = StartTestResponse(
        testId = testId,
        accepted = false,
        testType = Some("smoke"),
        message = customMessage
      )

      response.message shouldBe customMessage
      response.accepted shouldBe false
    }
  }

  "TestStatusResponse" should {

    "construct with all required fields and no optional fields" in {
      val response = TestStatusResponse(
        testId = testId,
        state = "Setup",
        bucket = None,
        testType = None,
        startTime = None,
        endTime = None,
        success = None,
        error = None
      )

      response.testId shouldBe testId
      response.state shouldBe "Setup"
      response.bucket shouldBe None
      response.testType shouldBe None
      response.startTime shouldBe None
      response.endTime shouldBe None
      response.success shouldBe None
      response.error shouldBe None
      response shouldBe a[ServiceResponse]
    }

    "construct with all optional fields provided" in {
      val response = TestStatusResponse(
        testId = testId,
        state = "Complete",
        bucket = Some("test-bucket"),
        testType = Some("functional"),
        startTime = Some("2025-01-01T10:00:00Z"),
        endTime = Some("2025-01-01T10:05:00Z"),
        success = Some(true),
        error = None
      )

      response.bucket shouldBe Some("test-bucket")
      response.testType shouldBe Some("functional")
      response.startTime shouldBe Some("2025-01-01T10:00:00Z")
      response.endTime shouldBe Some("2025-01-01T10:05:00Z")
      response.success shouldBe Some(true)
    }

    "construct with error message" in {
      val response = TestStatusResponse(
        testId = testId,
        state = "Exception",
        bucket = Some("test-bucket"),
        testType = Some("smoke"),
        startTime = Some("2025-01-01T10:00:00Z"),
        endTime = Some("2025-01-01T10:02:00Z"),
        success = Some(false),
        error = Some("Test execution failed: Connection timeout")
      )

      response.error shouldBe Some("Test execution failed: Connection timeout")
      response.success shouldBe Some(false)
    }
  }

  "QueueStatusResponse" should {

    "construct with all counts" in {
      val response = QueueStatusResponse(
        totalTests = 10,
        setupCount = 2,
        loadingCount = 1,
        loadedCount = 3,
        testingCount = 2,
        completedCount = 1,
        exceptionCount = 1,
        currentlyTesting = Some(testId)
      )

      response.totalTests shouldBe 10
      response.setupCount shouldBe 2
      response.loadingCount shouldBe 1
      response.loadedCount shouldBe 3
      response.testingCount shouldBe 2
      response.completedCount shouldBe 1
      response.exceptionCount shouldBe 1
      response.currentlyTesting shouldBe Some(testId)
      response shouldBe a[ServiceResponse]
    }

    "construct with no currently testing test" in {
      val response = QueueStatusResponse(
        totalTests = 5,
        setupCount = 0,
        loadingCount = 0,
        loadedCount = 0,
        testingCount = 0,
        completedCount = 5,
        exceptionCount = 0,
        currentlyTesting = None
      )

      response.currentlyTesting shouldBe None
      response.totalTests shouldBe 5
      response.completedCount shouldBe 5
    }

    "construct with zero counts" in {
      val response = QueueStatusResponse(
        totalTests = 0,
        setupCount = 0,
        loadingCount = 0,
        loadedCount = 0,
        testingCount = 0,
        completedCount = 0,
        exceptionCount = 0,
        currentlyTesting = None
      )

      response.totalTests shouldBe 0
      response.setupCount shouldBe 0
      response.loadingCount shouldBe 0
      response.loadedCount shouldBe 0
      response.testingCount shouldBe 0
      response.completedCount shouldBe 0
      response.exceptionCount shouldBe 0
      response.currentlyTesting shouldBe None
    }
  }

  "TestCancelledResponse" should {

    "construct with cancellation success" in {
      val response = TestCancelledResponse(
        testId = testId,
        cancelled = true
      )

      response.testId shouldBe testId
      response.cancelled shouldBe true
      response.message shouldBe None
      response shouldBe a[ServiceResponse]
    }

    "construct with cancellation failure" in {
      val response = TestCancelledResponse(
        testId = testId,
        cancelled = false
      )

      response.testId shouldBe testId
      response.cancelled shouldBe false
      response.message shouldBe None
    }

    "construct with custom message" in {
      val response = TestCancelledResponse(
        testId = testId,
        cancelled = true,
        message = Some("Test successfully cancelled")
      )

      response.message shouldBe Some("Test successfully cancelled")
      response.cancelled shouldBe true
    }

    "construct with failure message" in {
      val response = TestCancelledResponse(
        testId = testId,
        cancelled = false,
        message = Some("Test not found or already completed")
      )

      response.message shouldBe Some("Test not found or already completed")
      response.cancelled shouldBe false
    }
  }

  "QueueActorInterfaceResponse" should {

    "construct with ActorRef" in {
      val probe = testKit.createTestProbe[QueueCommands.QueueCommand]()
      val response = QueueActorInterfaceResponse(probe.ref)

      response.queue shouldBe probe.ref
      response shouldBe a[ServiceResponse]
    }
  }

  "ServiceResponse hierarchy" should {

    "allow all responses to be treated as ServiceResponse" in {
      val probe = testKit.createTestProbe[QueueCommands.QueueCommand]()

      val responses: Seq[ServiceResponse] = Seq(
        ActorSystemInitializationSuccess,
        ActorSystemInitializationFailure(new RuntimeException("error")),
        QueueActorReference(probe.ref),
        InitializeTestResponse(testId),
        StartTestResponse(testId, accepted = true, testType = None),
        TestStatusResponse(testId, "Setup", None, None, None, None, None, None),
        QueueStatusResponse(0, 0, 0, 0, 0, 0, 0, None),
        TestCancelledResponse(testId, cancelled = true),
        QueueActorInterfaceResponse(probe.ref)
      )

      responses.foreach { response =>
        response shouldBe a[ServiceResponse]
      }
    }
  }
}
