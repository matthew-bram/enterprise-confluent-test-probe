package io.distia.probe.interfaces.models.rest

import io.distia.probe.interfaces.fixtures.RestApiModelsFixtures
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

/**
 * Unit tests for REST API model case classes
 *
 * Target Coverage: 70%+
 *
 * Tests:
 * - Model creation with valid data
 * - Field access
 * - Copy methods
 * - Equality and hash code
 */
class RestApiModelsSpec extends AnyWordSpec with Matchers {

  import RestApiModelsFixtures.*

  "RestApiModels" when {

    // ========================================================================
    // Request Models
    // ========================================================================

    "working with request models" should {

      "create RestInitializeTestRequest" in {
        val request = RestInitializeTestRequest()

        request shouldBe a[RestInitializeTestRequest]
      }

      "create RestStartTestRequest with all fields" in {
        val request = RestStartTestRequest(
          `test-id` = testId1,
          `block-storage-path` = "s3://bucket/path",
          `test-type` = Some("cucumber")
        )

        request.`test-id` shouldBe testId1
        request.`block-storage-path` shouldBe "s3://bucket/path"
        request.`test-type` shouldBe Some("cucumber")
      }

      "create RestStartTestRequest without test type" in {
        val request = RestStartTestRequest(
          `test-id` = testId1,
          `block-storage-path` = "s3://bucket/path",
          `test-type` = None
        )

        request.`test-type` shouldBe None
      }

      "support copy for RestStartTestRequest" in {
        val original = validRestStartRequest
        val modified = original.copy(`test-type` = Some("junit"))

        modified.`test-id` shouldBe original.`test-id`
        modified.`block-storage-path` shouldBe original.`block-storage-path`
        modified.`test-type` shouldBe Some("junit")
      }

      "create RestTestStatusRequest" in {
        val request = RestTestStatusRequest(`test-id` = testId1)

        request.`test-id` shouldBe testId1
      }

      "create RestQueueStatusRequest with test ID" in {
        val request = RestQueueStatusRequest(`test-id` = Some(testId1))

        request.`test-id` shouldBe Some(testId1)
      }

      "create RestQueueStatusRequest without test ID" in {
        val request = RestQueueStatusRequest(`test-id` = None)

        request.`test-id` shouldBe None
      }

      "create RestCancelRequest" in {
        val request = RestCancelRequest(`test-id` = testId1)

        request.`test-id` shouldBe testId1
      }
    }

    // ========================================================================
    // Response Models
    // ========================================================================

    "working with response models" should {

      "create RestInitializeTestResponse" in {
        val response = RestInitializeTestResponse(
          `test-id` = testId1,
          message = "Test initialized"
        )

        response.`test-id` shouldBe testId1
        response.message shouldBe "Test initialized"
      }

      "support copy for RestInitializeTestResponse" in {
        val original = validRestInitializeResponse
        val modified = original.copy(message = "Modified message")

        modified.`test-id` shouldBe original.`test-id`
        modified.message shouldBe "Modified message"
      }

      "create RestStartTestResponse with accepted test" in {
        val response = RestStartTestResponse(
          `test-id` = testId1,
          accepted = true,
          `test-type` = Some("cucumber"),
          message = "Accepted"
        )

        response.`test-id` shouldBe testId1
        response.accepted shouldBe true
        response.`test-type` shouldBe Some("cucumber")
        response.message shouldBe "Accepted"
      }

      "create RestStartTestResponse with rejected test" in {
        val response = RestStartTestResponse(
          `test-id` = testId1,
          accepted = false,
          `test-type` = None,
          message = "Rejected"
        )

        response.accepted shouldBe false
        response.`test-type` shouldBe None
      }

      "create RestTestStatusResponse for in-progress test" in {
        val response = RestTestStatusResponse(
          `test-id` = testId1,
          state = "Testing",
          bucket = Some("s3://bucket"),
          `test-type` = Some("cucumber"),
          `start-time` = Some("2024-01-15T10:30:00Z"),
          `end-time` = None,
          success = None,
          error = None
        )

        response.`test-id` shouldBe testId1
        response.state shouldBe "Testing"
        response.`end-time` shouldBe None
        response.success shouldBe None
        response.error shouldBe None
      }

      "create RestTestStatusResponse for completed test" in {
        val response = completedRestTestStatusResponse

        response.state shouldBe "Completed"
        response.`end-time` shouldBe Some("2024-01-15T10:45:00Z")
        response.success shouldBe Some(true)
        response.error shouldBe None
      }

      "create RestTestStatusResponse for failed test" in {
        val response = failedRestTestStatusResponse

        response.state shouldBe "Exception"
        response.success shouldBe Some(false)
        response.error shouldBe Some("Test execution failed: timeout")
      }

      "support copy for RestTestStatusResponse" in {
        val original = validRestTestStatusResponse
        val modified = original.copy(
          state = "Completed",
          `end-time` = Some("2024-01-15T10:45:00Z"),
          success = Some(true)
        )

        modified.`test-id` shouldBe original.`test-id`
        modified.state shouldBe "Completed"
        modified.`end-time` shouldBe Some("2024-01-15T10:45:00Z")
        modified.success shouldBe Some(true)
      }

      "create RestQueueStatusResponse with tests in queue" in {
        val response = validRestQueueStatusResponse

        response.`total-tests` shouldBe 10
        response.`setup-count` shouldBe 2
        response.`loading-count` shouldBe 1
        response.`loaded-count` shouldBe 3
        response.`testing-count` shouldBe 1
        response.`completed-count` shouldBe 2
        response.`exception-count` shouldBe 1
        response.`currently-testing` shouldBe Some(testId1)
      }

      "create RestQueueStatusResponse for empty queue" in {
        val response = emptyQueueStatusResponse

        response.`total-tests` shouldBe 0
        response.`setup-count` shouldBe 0
        response.`currently-testing` shouldBe None
      }

      "support copy for RestQueueStatusResponse" in {
        val original = validRestQueueStatusResponse
        val modified = original.copy(
          `completed-count` = 3,
          `currently-testing` = None
        )

        modified.`total-tests` shouldBe original.`total-tests`
        modified.`completed-count` shouldBe 3
        modified.`currently-testing` shouldBe None
      }

      "create RestTestCancelledResponse for successful cancellation" in {
        val response = validRestCancelResponse

        response.`test-id` shouldBe testId1
        response.cancelled shouldBe true
        response.message shouldBe Some("Test cancelled successfully")
      }

      "create RestTestCancelledResponse for failed cancellation" in {
        val response = failedRestCancelResponse

        response.`test-id` shouldBe testId2
        response.cancelled shouldBe false
        response.message shouldBe Some("Test not found or already completed")
      }

      "create RestTestCancelledResponse without message" in {
        val response = RestTestCancelledResponse(
          `test-id` = testId1,
          cancelled = true,
          message = None
        )

        response.message shouldBe None
      }

      "support copy for RestTestCancelledResponse" in {
        val original = validRestCancelResponse
        val modified = original.copy(message = Some("Updated message"))

        modified.`test-id` shouldBe original.`test-id`
        modified.cancelled shouldBe original.cancelled
        modified.message shouldBe Some("Updated message")
      }
    }

    // ========================================================================
    // Equality and Hash Code
    // ========================================================================

    "comparing models for equality" should {

      "consider two identical requests equal" in {
        val request1 = RestStartTestRequest(testId1, "s3://bucket", Some("cucumber"))
        val request2 = RestStartTestRequest(testId1, "s3://bucket", Some("cucumber"))

        request1 shouldBe request2
        request1.hashCode shouldBe request2.hashCode
      }

      "consider two different requests unequal" in {
        val request1 = RestStartTestRequest(testId1, "s3://bucket", Some("cucumber"))
        val request2 = RestStartTestRequest(testId2, "s3://bucket", Some("cucumber"))

        request1 should not be request2
      }

      "consider two identical responses equal" in {
        val response1 = RestInitializeTestResponse(testId1, "message")
        val response2 = RestInitializeTestResponse(testId1, "message")

        response1 shouldBe response2
        response1.hashCode shouldBe response2.hashCode
      }

      "consider two different responses unequal" in {
        val response1 = RestInitializeTestResponse(testId1, "message1")
        val response2 = RestInitializeTestResponse(testId1, "message2")

        response1 should not be response2
      }
    }

    // ========================================================================
    // toString for Debugging
    // ========================================================================

    "generating string representations" should {

      "include all fields in toString for requests" in {
        val request = validRestStartRequest
        val string = request.toString

        string should include("11111111-1111-1111-1111-111111111111")
        string should include("s3://bucket/path/to/test")
        string should include("cucumber")
      }

      "include all fields in toString for responses" in {
        val response = validRestTestStatusResponse
        val string = response.toString

        string should include("11111111-1111-1111-1111-111111111111")
        string should include("Testing")
      }
    }
  }
}
