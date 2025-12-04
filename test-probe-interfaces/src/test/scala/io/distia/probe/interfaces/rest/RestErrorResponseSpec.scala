package io.distia.probe.interfaces.rest

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json._

/**
 * Unit tests for RestErrorResponse factory methods
 *
 * Target Coverage: All factory methods in RestErrorResponse object
 * - badRequest (2 tests)
 * - validationError (1 test)
 * - notFound (1 test)
 * - unsupportedMediaType (1 test)
 * - internalServerError (1 test)
 * - serviceUnavailable (2 tests)
 * - timeout (1 test)
 * - actorTimeout (2 tests)
 * - notReady (1 test)
 * - methodNotAllowed (1 test)
 *
 * Testing Strategy:
 * - Verify error codes match expected values
 * - Verify messages are set correctly
 * - Verify optional fields (details, retryAfter) are set correctly
 * - Verify JSON serialization works correctly
 * - Verify timestamps are generated
 */
class RestErrorResponseSpec extends AnyWordSpec with Matchers {

  import RestErrorResponse._

  // ========================================
  // badRequest
  // ========================================

  "badRequest" when {

    "called with message only" should {
      "create error with bad_request code and no details" in {
        val error = badRequest("Invalid input")

        error.error shouldBe "bad_request"
        error.message shouldBe "Invalid input"
        error.details shouldBe None
        error.retryAfter shouldBe None
        error.timestamp should be > 0L
      }
    }

    "called with message and details" should {
      "create error with bad_request code and details" in {
        val error = badRequest("Invalid input", Some("Field 'name' is required"))

        error.error shouldBe "bad_request"
        error.message shouldBe "Invalid input"
        error.details shouldBe Some("Field 'name' is required")
        error.retryAfter shouldBe None
        error.timestamp should be > 0L
      }
    }
  }

  // ========================================
  // validationError
  // ========================================

  "validationError" when {

    "called with message and details" should {
      "create error with validation_error code and details" in {
        val error = validationError("Request validation failed", "block-storage-path cannot be empty")

        error.error shouldBe "validation_error"
        error.message shouldBe "Request validation failed"
        error.details shouldBe Some("block-storage-path cannot be empty")
        error.retryAfter shouldBe None
        error.timestamp should be > 0L
      }
    }
  }

  // ========================================
  // notFound
  // ========================================

  "notFound" when {

    "called with message" should {
      "create error with not_found code" in {
        val error = notFound("Test not found")

        error.error shouldBe "not_found"
        error.message shouldBe "Test not found"
        error.details shouldBe None
        error.retryAfter shouldBe None
        error.timestamp should be > 0L
      }
    }
  }

  // ========================================
  // unsupportedMediaType
  // ========================================

  "unsupportedMediaType" when {

    "called with message" should {
      "create error with unsupported_media_type code" in {
        val error = unsupportedMediaType("Content-Type must be application/json")

        error.error shouldBe "unsupported_media_type"
        error.message shouldBe "Content-Type must be application/json"
        error.details shouldBe None
        error.retryAfter shouldBe None
        error.timestamp should be > 0L
      }
    }
  }

  // ========================================
  // internalServerError
  // ========================================

  "internalServerError" when {

    "called with message" should {
      "create error with internal_server_error code" in {
        val error = internalServerError("An unexpected error occurred")

        error.error shouldBe "internal_server_error"
        error.message shouldBe "An unexpected error occurred"
        error.details shouldBe None
        error.retryAfter shouldBe None
        error.timestamp should be > 0L
      }
    }
  }

  // ========================================
  // serviceUnavailable
  // ========================================

  "serviceUnavailable" when {

    "called with retryAfter from config" should {
      "create error with service_unavailable code and configured retry" in {
        val error = serviceUnavailable("Circuit breaker open", "30s")

        error.error shouldBe "service_unavailable"
        error.message shouldBe "Circuit breaker open"
        error.details shouldBe None
        error.retryAfter shouldBe Some("30s")
        error.timestamp should be > 0L
      }
    }

    "called with custom retryAfter" should {
      "create error with service_unavailable code and custom retry" in {
        val error = serviceUnavailable("System maintenance", "60s")

        error.error shouldBe "service_unavailable"
        error.message shouldBe "System maintenance"
        error.details shouldBe None
        error.retryAfter shouldBe Some("60s")
        error.timestamp should be > 0L
      }
    }
  }

  // ========================================
  // timeout
  // ========================================

  "timeout" when {

    "called with message" should {
      "create error with timeout code" in {
        val error = timeout("Request timed out")

        error.error shouldBe "timeout"
        error.message shouldBe "Request timed out"
        error.details shouldBe None
        error.retryAfter shouldBe None
        error.timestamp should be > 0L
      }
    }
  }

  // ========================================
  // actorTimeout
  // ========================================

  "actorTimeout" when {

    "called with message only" should {
      "create error with actor_timeout code and no details" in {
        val error = actorTimeout("Actor system did not respond in time")

        error.error shouldBe "actor_timeout"
        error.message shouldBe "Actor system did not respond in time"
        error.details shouldBe None
        error.retryAfter shouldBe None
        error.timestamp should be > 0L
      }
    }

    "called with message and details" should {
      "create error with actor_timeout code and details" in {
        val error = actorTimeout("Actor system did not respond in time", Some("Timeout after 3 seconds"))

        error.error shouldBe "actor_timeout"
        error.message shouldBe "Actor system did not respond in time"
        error.details shouldBe Some("Timeout after 3 seconds")
        error.retryAfter shouldBe None
        error.timestamp should be > 0L
      }
    }
  }

  // ========================================
  // notReady
  // ========================================

  "notReady" when {

    "called with retryAfter from config" should {
      "create error with not_ready code and configured retry" in {
        val error = notReady("Actor system is initializing", "5s")

        error.error shouldBe "not_ready"
        error.message shouldBe "Actor system is initializing"
        error.details shouldBe None
        error.retryAfter shouldBe Some("5s")
        error.timestamp should be > 0L
      }
    }
  }

  // ========================================
  // methodNotAllowed
  // ========================================

  "methodNotAllowed" when {

    "called with message" should {
      "create error with method_not_allowed code" in {
        val error = methodNotAllowed("Supported methods: GET, POST")

        error.error shouldBe "method_not_allowed"
        error.message shouldBe "Supported methods: GET, POST"
        error.details shouldBe None
        error.retryAfter shouldBe None
        error.timestamp should be > 0L
      }
    }
  }

  // ========================================
  // JSON Serialization
  // ========================================

  "JSON serialization" when {

    "serializing error with all fields" should {
      "produce valid JSON with all fields" in {
        val error = RestErrorResponse(
          error = "test_error",
          message = "Test message",
          details = Some("Test details"),
          retryAfter = Some("10s"),
          timestamp = 1234567890L
        )

        val json = error.toJson.asJsObject

        json.fields("error") shouldBe JsString("test_error")
        json.fields("message") shouldBe JsString("Test message")
        json.fields("details") shouldBe JsString("Test details")
        json.fields("retryAfter") shouldBe JsString("10s")
        json.fields("timestamp") shouldBe JsNumber(1234567890L)
      }
    }

    "serializing error with minimal fields" should {
      "produce valid JSON with null for optional fields" in {
        val error = RestErrorResponse(
          error = "test_error",
          message = "Test message",
          details = None,
          retryAfter = None,
          timestamp = 1234567890L
        )

        val json = error.toJson.asJsObject

        json.fields("error") shouldBe JsString("test_error")
        json.fields("message") shouldBe JsString("Test message")
        // Spray JSON with DefaultJsonProtocol serializes None as JsNull in the JSON
        // but some configurations may omit the field entirely
        json.fields.get("details").foreach(_ shouldBe JsNull)
        json.fields.get("retryAfter").foreach(_ shouldBe JsNull)
        json.fields("timestamp") shouldBe JsNumber(1234567890L)
      }
    }

    "deserializing JSON" should {
      "recreate error object correctly" in {
        val jsonString =
          """
            |{
            |  "error": "validation_error",
            |  "message": "Validation failed",
            |  "details": "Field error",
            |  "retryAfter": null,
            |  "timestamp": 1234567890
            |}
            |""".stripMargin

        val error = jsonString.parseJson.convertTo[RestErrorResponse]

        error.error shouldBe "validation_error"
        error.message shouldBe "Validation failed"
        error.details shouldBe Some("Field error")
        error.retryAfter shouldBe None
        error.timestamp shouldBe 1234567890L
      }
    }
  }

  // ========================================
  // Timestamp Generation
  // ========================================

  "timestamp generation" when {

    "creating errors at different times" should {
      "generate different timestamps" in {
        val error1 = badRequest("Error 1")
        Thread.sleep(5) // Small delay to ensure different timestamps
        val error2 = badRequest("Error 2")

        error1.timestamp should be < error2.timestamp
      }
    }

    "creating error with explicit timestamp" should {
      "use provided timestamp" in {
        val explicitTimestamp = 9999999999L
        val error = RestErrorResponse(
          error = "test",
          message = "test",
          timestamp = explicitTimestamp
        )

        error.timestamp shouldBe explicitTimestamp
      }
    }
  }
}
