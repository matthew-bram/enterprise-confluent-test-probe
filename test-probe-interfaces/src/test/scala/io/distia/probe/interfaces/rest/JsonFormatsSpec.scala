package io.distia.probe.interfaces.rest

import io.distia.probe.interfaces.fixtures.RestApiModelsFixtures
import io.distia.probe.interfaces.models.rest.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json.*

import java.util.UUID
import scala.util.{Failure, Success, Try}

/**
 * Unit tests for Spray JSON formats
 *
 * Target Coverage: 85%+ (pure functions, easier to test)
 *
 * Tests:
 * - UUID serialization/deserialization
 * - All REST request model formats
 * - All REST response model formats
 * - Round-trip conversions (object → JSON → object)
 * - Invalid JSON handling (error cases)
 */
class JsonFormatsSpec extends AnyWordSpec with Matchers {

  import JsonFormats.*
  import RestApiModelsFixtures.*

  "JsonFormats" when {

    // ========================================================================
    // UUID Format
    // ========================================================================

    "serializing and deserializing UUIDs" should {

      "serialize UUID to JSON string" in {
        val uuid = testId1
        val json = uuid.toJson

        json shouldBe JsString("11111111-1111-1111-1111-111111111111")
      }

      "deserialize valid UUID from JSON string" in {
        val json = JsString("11111111-1111-1111-1111-111111111111")
        val uuid = json.convertTo[UUID]

        uuid shouldBe testId1
      }

      "round-trip UUID through JSON" in {
        val original = testId1
        val json = original.toJson
        val roundTripped = json.convertTo[UUID]

        roundTripped shouldBe original
      }

      "reject non-string JSON values for UUID" in {
        val json = JsNumber(123)

        val exception = intercept[DeserializationException] {
          json.convertTo[UUID]
        }
        exception.getMessage should include("UUID expected")
      }

      "reject invalid UUID string format" in {
        val json = JsString("not-a-valid-uuid")

        intercept[IllegalArgumentException] {
          json.convertTo[UUID]
        }
      }

      "reject null UUID" in {
        val json = JsNull

        intercept[DeserializationException] {
          json.convertTo[UUID]
        }
      }
    }

    // ========================================================================
    // Request Model Formats
    // ========================================================================

    "serializing and deserializing request models" should {

      "serialize RestInitializeTestRequest to JSON" in {
        val json = validRestInitializeRequest.toJson

        json shouldBe JsObject()
      }

      "deserialize RestInitializeTestRequest from JSON" in {
        val json = JsObject()
        val request = json.convertTo[RestInitializeTestRequest]

        request shouldBe RestInitializeTestRequest()
      }

      "serialize RestStartTestRequest with test type to JSON" in {
        val json = validRestStartRequest.toJson

        json shouldBe JsObject(
          "test-id" -> JsString("11111111-1111-1111-1111-111111111111"),
          "block-storage-path" -> JsString("s3://bucket/path/to/test"),
          "test-type" -> JsString("cucumber")
        )
      }

      "serialize RestStartTestRequest without test type to JSON" in {
        val json = restStartRequestNoTestType.toJson

        val fields = json.asJsObject.fields
        fields("test-id") shouldBe JsString("22222222-2222-2222-2222-222222222222")
        fields("block-storage-path") shouldBe JsString("s3://bucket/another/path")
        // Spray JSON with DefaultJsonProtocol includes None as JsNull
        fields.get("test-type") should (be(Some(JsNull)) or be(None))
      }

      "deserialize RestStartTestRequest with test type from JSON" in {
        val json = """
          |{
          |  "test-id": "11111111-1111-1111-1111-111111111111",
          |  "block-storage-path": "s3://bucket/path/to/test",
          |  "test-type": "cucumber"
          |}
          |""".stripMargin.parseJson

        val request = json.convertTo[RestStartTestRequest]

        request.`test-id` shouldBe testId1
        request.`block-storage-path` shouldBe "s3://bucket/path/to/test"
        request.`test-type` shouldBe Some("cucumber")
      }

      "deserialize RestStartTestRequest without test type from JSON" in {
        val json = """
          |{
          |  "test-id": "22222222-2222-2222-2222-222222222222",
          |  "block-storage-path": "s3://bucket/another/path",
          |  "test-type": null
          |}
          |""".stripMargin.parseJson

        val request = json.convertTo[RestStartTestRequest]

        request.`test-id` shouldBe testId2
        request.`test-type` shouldBe None
      }

      "round-trip RestStartTestRequest through JSON" in {
        val original = validRestStartRequest
        val json = original.toJson
        val roundTripped = json.convertTo[RestStartTestRequest]

        roundTripped shouldBe original
      }

      "serialize RestTestStatusRequest to JSON" in {
        val json = validRestStatusRequest.toJson

        json shouldBe JsObject(
          "test-id" -> JsString("11111111-1111-1111-1111-111111111111")
        )
      }

      "deserialize RestTestStatusRequest from JSON" in {
        val json = """{"test-id": "11111111-1111-1111-1111-111111111111"}""".parseJson
        val request = json.convertTo[RestTestStatusRequest]

        request.`test-id` shouldBe testId1
      }

      "serialize RestQueueStatusRequest with test ID to JSON" in {
        val json = validRestQueueStatusRequest.toJson

        val fields = json.asJsObject.fields
        fields("test-id") shouldBe JsString("11111111-1111-1111-1111-111111111111")
      }

      "serialize RestQueueStatusRequest without test ID to JSON" in {
        val json = restQueueStatusRequestNoTestId.toJson

        val fields = json.asJsObject.fields
        // Spray JSON with DefaultJsonProtocol includes None as JsNull
        fields.get("test-id") should (be(Some(JsNull)) or be(None))
      }

      "deserialize RestQueueStatusRequest with test ID from JSON" in {
        val json = """{"test-id": "11111111-1111-1111-1111-111111111111"}""".parseJson
        val request = json.convertTo[RestQueueStatusRequest]

        request.`test-id` shouldBe Some(testId1)
      }

      "deserialize RestQueueStatusRequest without test ID from JSON" in {
        val json = """{"test-id": null}""".parseJson
        val request = json.convertTo[RestQueueStatusRequest]

        request.`test-id` shouldBe None
      }

      "serialize RestCancelRequest to JSON" in {
        val json = validRestCancelRequest.toJson

        json shouldBe JsObject(
          "test-id" -> JsString("11111111-1111-1111-1111-111111111111")
        )
      }

      "deserialize RestCancelRequest from JSON" in {
        val json = """{"test-id": "11111111-1111-1111-1111-111111111111"}""".parseJson
        val request = json.convertTo[RestCancelRequest]

        request.`test-id` shouldBe testId1
      }
    }

    // ========================================================================
    // Response Model Formats
    // ========================================================================

    "serializing and deserializing response models" should {

      "serialize RestInitializeTestResponse to JSON" in {
        val json = validRestInitializeResponse.toJson

        json shouldBe JsObject(
          "test-id" -> JsString("11111111-1111-1111-1111-111111111111"),
          "message" -> JsString("Test initialized successfully")
        )
      }

      "deserialize RestInitializeTestResponse from JSON" in {
        val json = """
          |{
          |  "test-id": "11111111-1111-1111-1111-111111111111",
          |  "message": "Test initialized successfully"
          |}
          |""".stripMargin.parseJson

        val response = json.convertTo[RestInitializeTestResponse]

        response.`test-id` shouldBe testId1
        response.message shouldBe "Test initialized successfully"
      }

      "round-trip RestInitializeTestResponse through JSON" in {
        val original = validRestInitializeResponse
        val json = original.toJson
        val roundTripped = json.convertTo[RestInitializeTestResponse]

        roundTripped shouldBe original
      }

      "serialize RestStartTestResponse to JSON" in {
        val json = validRestStartResponse.toJson

        val fields = json.asJsObject.fields
        fields("test-id") shouldBe JsString("11111111-1111-1111-1111-111111111111")
        fields("accepted") shouldBe JsBoolean(true)
        fields("test-type") shouldBe JsString("cucumber")
        fields("message") shouldBe JsString("Test accepted and queued")
      }

      "deserialize RestStartTestResponse from JSON" in {
        val json = """
          |{
          |  "test-id": "11111111-1111-1111-1111-111111111111",
          |  "accepted": true,
          |  "test-type": "cucumber",
          |  "message": "Test accepted and queued"
          |}
          |""".stripMargin.parseJson

        val response = json.convertTo[RestStartTestResponse]

        response.`test-id` shouldBe testId1
        response.accepted shouldBe true
        response.`test-type` shouldBe Some("cucumber")
        response.message shouldBe "Test accepted and queued"
      }

      "round-trip RestStartTestResponse through JSON" in {
        val original = validRestStartResponse
        val json = original.toJson
        val roundTripped = json.convertTo[RestStartTestResponse]

        roundTripped shouldBe original
      }

      "serialize RestTestStatusResponse to JSON" in {
        val json = validRestTestStatusResponse.toJson

        val fields = json.asJsObject.fields
        fields("test-id") shouldBe JsString("11111111-1111-1111-1111-111111111111")
        fields("state") shouldBe JsString("Testing")
        fields("bucket") shouldBe JsString("s3://bucket/path")
        fields("test-type") shouldBe JsString("cucumber")
        fields("start-time") shouldBe JsString("2024-01-15T10:30:00Z")
        // Spray JSON with DefaultJsonProtocol includes None as JsNull
        fields.get("end-time") should (be(Some(JsNull)) or be(None))
        fields.get("success") should (be(Some(JsNull)) or be(None))
        fields.get("error") should (be(Some(JsNull)) or be(None))
      }

      "deserialize RestTestStatusResponse from JSON" in {
        val json = """
          |{
          |  "test-id": "11111111-1111-1111-1111-111111111111",
          |  "state": "Testing",
          |  "bucket": "s3://bucket/path",
          |  "test-type": "cucumber",
          |  "start-time": "2024-01-15T10:30:00Z",
          |  "end-time": null,
          |  "success": null,
          |  "error": null
          |}
          |""".stripMargin.parseJson

        val response = json.convertTo[RestTestStatusResponse]

        response.`test-id` shouldBe testId1
        response.state shouldBe "Testing"
        response.bucket shouldBe Some("s3://bucket/path")
        response.`test-type` shouldBe Some("cucumber")
        response.`start-time` shouldBe Some("2024-01-15T10:30:00Z")
        response.`end-time` shouldBe None
        response.success shouldBe None
        response.error shouldBe None
      }

      "round-trip RestTestStatusResponse through JSON" in {
        val original = completedRestTestStatusResponse
        val json = original.toJson
        val roundTripped = json.convertTo[RestTestStatusResponse]

        roundTripped shouldBe original
      }

      "serialize RestQueueStatusResponse to JSON" in {
        val json = validRestQueueStatusResponse.toJson

        val fields = json.asJsObject.fields
        fields("total-tests") shouldBe JsNumber(10)
        fields("setup-count") shouldBe JsNumber(2)
        fields("loading-count") shouldBe JsNumber(1)
        fields("loaded-count") shouldBe JsNumber(3)
        fields("testing-count") shouldBe JsNumber(1)
        fields("completed-count") shouldBe JsNumber(2)
        fields("exception-count") shouldBe JsNumber(1)
        fields("currently-testing") shouldBe JsString("11111111-1111-1111-1111-111111111111")
      }

      "deserialize RestQueueStatusResponse from JSON" in {
        val json = """
          |{
          |  "total-tests": 10,
          |  "setup-count": 2,
          |  "loading-count": 1,
          |  "loaded-count": 3,
          |  "testing-count": 1,
          |  "completed-count": 2,
          |  "exception-count": 1,
          |  "currently-testing": "11111111-1111-1111-1111-111111111111"
          |}
          |""".stripMargin.parseJson

        val response = json.convertTo[RestQueueStatusResponse]

        response.`total-tests` shouldBe 10
        response.`currently-testing` shouldBe Some(testId1)
      }

      "round-trip RestQueueStatusResponse through JSON" in {
        val original = emptyQueueStatusResponse
        val json = original.toJson
        val roundTripped = json.convertTo[RestQueueStatusResponse]

        roundTripped shouldBe original
      }

      "serialize RestTestCancelledResponse to JSON" in {
        val json = validRestCancelResponse.toJson

        val fields = json.asJsObject.fields
        fields("test-id") shouldBe JsString("11111111-1111-1111-1111-111111111111")
        fields("cancelled") shouldBe JsBoolean(true)
        fields("message") shouldBe JsString("Test cancelled successfully")
      }

      "deserialize RestTestCancelledResponse from JSON" in {
        val json = """
          |{
          |  "test-id": "11111111-1111-1111-1111-111111111111",
          |  "cancelled": true,
          |  "message": "Test cancelled successfully"
          |}
          |""".stripMargin.parseJson

        val response = json.convertTo[RestTestCancelledResponse]

        response.`test-id` shouldBe testId1
        response.cancelled shouldBe true
        response.message shouldBe Some("Test cancelled successfully")
      }

      "round-trip RestTestCancelledResponse through JSON" in {
        val original = failedRestCancelResponse
        val json = original.toJson
        val roundTripped = json.convertTo[RestTestCancelledResponse]

        roundTripped shouldBe original
      }
    }

    // ========================================================================
    // Error Handling
    // ========================================================================

    "handling invalid JSON" should {

      "reject malformed JSON for request models" in {
        val json = """{"test-id": "not-a-uuid"}"""

        intercept[Exception] {
          json.parseJson.convertTo[RestTestStatusRequest]
        }
      }

      "reject missing required fields for request models" in {
        val json = """{"block-storage-path": "s3://bucket"}"""

        intercept[DeserializationException] {
          json.parseJson.convertTo[RestStartTestRequest]
        }
      }

      "reject wrong type for boolean field" in {
        val json = """
          |{
          |  "test-id": "11111111-1111-1111-1111-111111111111",
          |  "accepted": "true",
          |  "test-type": null,
          |  "message": "test"
          |}
          |""".stripMargin

        intercept[DeserializationException] {
          json.parseJson.convertTo[RestStartTestResponse]
        }
      }

      "reject wrong type for integer field" in {
        val json = """
          |{
          |  "total-tests": "10",
          |  "setup-count": 2,
          |  "loading-count": 1,
          |  "loaded-count": 3,
          |  "testing-count": 1,
          |  "completed-count": 2,
          |  "exception-count": 1,
          |  "currently-testing": null
          |}
          |""".stripMargin

        intercept[DeserializationException] {
          json.parseJson.convertTo[RestQueueStatusResponse]
        }
      }
    }
  }
}
