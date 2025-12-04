package io.distia.probe.interfaces.rest

import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import org.apache.pekko.pattern.AskTimeoutException
import io.distia.probe.core.builder.ServiceInterfaceFunctions
import io.distia.probe.core.models.*
import io.distia.probe.interfaces.config.InterfacesConfig
import io.distia.probe.interfaces.models.rest.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json.*

import scala.concurrent.Future
import scala.concurrent.duration._
import java.util.UUID

/**
 * Comprehensive error handling tests for REST API endpoints
 *
 * Target Coverage: All 5 REST endpoints (excluding health check)
 * - POST /api/v1/test/initialize (success + 4 error paths)
 * - POST /api/v1/test/start (success + 5 error paths + validation)
 * - GET /api/v1/test/{testId}/status (success + 2 error paths)
 * - GET /api/v1/queue/status (success + 2 error paths)
 * - DELETE /api/v1/test/{testId} (success + 2 error paths)
 *
 * Testing Strategy:
 * - All error paths (503, 504, 500, 400)
 * - REST protocol validation errors (empty fields, whitespace, empty test-type)
 * - Exception handling (ServiceUnavailable, Timeout, AskTimeout)
 * - Success cases to ensure routes work as expected
 *
 * Note: Business logic validation (S3 paths, etc.) is tested in services layer
 */
class RestRoutesErrorHandlingSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  import JsonFormats._
  import RestErrorResponse._

  // Fixture: Test UUID
  private val testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

  // ========================================
  // POST /api/v1/test/initialize
  // ========================================

  "POST /api/v1/test/initialize" when {

    "successful" should {
      "return 201 Created with testId" in {
        val mockFunctions = createMockFunctions(
          initializeResult = Future.successful(InitializeTestResponse(testId, "Test initialized"))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Post("/api/v1/test/initialize") ~> routes ~> check {
          status shouldBe StatusCodes.Created
          val response = responseAs[RestInitializeTestResponse]
          response.`test-id` shouldBe testId
          response.message shouldBe "Test initialized"
        }
      }
    }

    "service is unavailable" should {
      "return 503 with circuit breaker message" in {
        val mockFunctions = createMockFunctions(
          initializeResult = Future.failed(ServiceUnavailableException("Circuit breaker open"))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Post("/api/v1/test/initialize") ~> routes ~> check {
          status shouldBe StatusCodes.ServiceUnavailable
          val error = responseAs[RestErrorResponse]
          error.error shouldBe "service_unavailable"
          error.message shouldBe "Circuit breaker open"
          error.retryAfter shouldBe Some("30s")
          error.timestamp should be > 0L
        }
      }
    }

    "service times out" should {
      "return 504 with timeout message" in {
        val mockFunctions = createMockFunctions(
          initializeResult = Future.failed(ServiceTimeoutException(
            "Actor did not respond in time",
            new Exception("Timeout")
          ))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Post("/api/v1/test/initialize") ~> routes ~> check {
          status shouldBe StatusCodes.GatewayTimeout
          val error = responseAs[RestErrorResponse]
          error.error shouldBe "actor_timeout"
          error.message shouldBe "Actor system did not respond in time"
          error.details shouldBe defined
          error.timestamp should be > 0L
        }
      }
    }

    "ask timeout occurs" should {
      "return 504 with ask timeout message" in {
        val mockFunctions = createMockFunctions(
          initializeResult = Future.failed(new AskTimeoutException("Ask timed out after 3 seconds"))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Post("/api/v1/test/initialize") ~> routes ~> check {
          status shouldBe StatusCodes.GatewayTimeout
          val error = responseAs[RestErrorResponse]
          error.error shouldBe "timeout"
          error.message should include("Ask timed out")
          error.timestamp should be > 0L
        }
      }
    }

    "unexpected exception occurs" should {
      "return 500 internal server error" in {
        val mockFunctions = createMockFunctions(
          initializeResult = Future.failed(new RuntimeException("Unexpected error"))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Post("/api/v1/test/initialize") ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          val error = responseAs[RestErrorResponse]
          error.error shouldBe "internal_server_error"
          error.message shouldBe "An unexpected error occurred"
          error.timestamp should be > 0L
        }
      }
    }
  }

  // ========================================
  // POST /api/v1/test/start
  // ========================================

  "POST /api/v1/test/start" when {

    "successful with all fields" should {
      "return 202 Accepted" in {
        val mockFunctions = createMockFunctions(
          startTestResult = Future.successful(StartTestResponse(
            testId,
            accepted = true,
            Some("integration"),
            "Test started successfully"
          ))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        val requestJson =
          s"""
             |{
             |  "test-id": "$testId",
             |  "block-storage-path": "s3://bucket/test-files",
             |  "test-type": "integration"
             |}
             |""".stripMargin

        Post("/api/v1/test/start", HttpEntity(ContentTypes.`application/json`, requestJson)) ~>
          routes ~> check {
            status shouldBe StatusCodes.Accepted
            val response = responseAs[RestStartTestResponse]
            response.`test-id` shouldBe testId
            response.accepted shouldBe true
            response.`test-type` shouldBe Some("integration")
          }
      }
    }

    "successful with optional test-type omitted" should {
      "return 202 Accepted" in {
        val mockFunctions = createMockFunctions(
          startTestResult = Future.successful(StartTestResponse(
            testId,
            accepted = true,
            None,
            "Test started successfully"
          ))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        val requestJson =
          s"""
             |{
             |  "test-id": "$testId",
             |  "block-storage-path": "s3://bucket/test-files"
             |}
             |""".stripMargin

        Post("/api/v1/test/start", HttpEntity(ContentTypes.`application/json`, requestJson)) ~>
          routes ~> check {
            status shouldBe StatusCodes.Accepted
            val response = responseAs[RestStartTestResponse]
            response.accepted shouldBe true
            response.`test-type` shouldBe None
          }
      }
    }

    "validation fails for empty path" should {
      "return 400 Bad Request with validation error" in {
        val mockFunctions = createMockFunctions()
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        val requestJson =
          s"""
             |{
             |  "test-id": "$testId",
             |  "block-storage-path": "",
             |  "test-type": "integration"
             |}
             |""".stripMargin

        Post("/api/v1/test/start", HttpEntity(ContentTypes.`application/json`, requestJson)) ~>
          routes ~> check {
            status shouldBe StatusCodes.BadRequest
            val error = responseAs[RestErrorResponse]
            error.error shouldBe "validation_error"
            error.message shouldBe "Request validation failed"
            error.details shouldBe Some("block-storage-path cannot be empty")
          }
      }
    }

    "validation fails for whitespace-only path" should {
      "return 400 Bad Request with validation error" in {
        val mockFunctions = createMockFunctions()
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        val requestJson =
          s"""
             |{
             |  "test-id": "$testId",
             |  "block-storage-path": "   ",
             |  "test-type": "integration"
             |}
             |""".stripMargin

        Post("/api/v1/test/start", HttpEntity(ContentTypes.`application/json`, requestJson)) ~>
          routes ~> check {
            status shouldBe StatusCodes.BadRequest
            val error = responseAs[RestErrorResponse]
            error.error shouldBe "validation_error"
            error.details shouldBe Some("block-storage-path cannot be empty")
          }
      }
    }

    "validation fails for empty test-type" should {
      "return 400 Bad Request with validation error" in {
        val mockFunctions = createMockFunctions()
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        val requestJson =
          s"""
             |{
             |  "test-id": "$testId",
             |  "block-storage-path": "s3://bucket/test-files",
             |  "test-type": ""
             |}
             |""".stripMargin

        Post("/api/v1/test/start", HttpEntity(ContentTypes.`application/json`, requestJson)) ~>
          routes ~> check {
            status shouldBe StatusCodes.BadRequest
            val error = responseAs[RestErrorResponse]
            error.error shouldBe "validation_error"
            error.details shouldBe Some("test-type cannot be empty string (omit field or provide valid value)")
          }
      }
    }

    "service is unavailable" should {
      "return 503 with service unavailable error" in {
        val mockFunctions = createMockFunctions(
          startTestResult = Future.failed(ServiceUnavailableException("Queue is full"))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        val requestJson =
          s"""
             |{
             |  "test-id": "$testId",
             |  "block-storage-path": "s3://bucket/test-files"
             |}
             |""".stripMargin

        Post("/api/v1/test/start", HttpEntity(ContentTypes.`application/json`, requestJson)) ~>
          routes ~> check {
            status shouldBe StatusCodes.ServiceUnavailable
            val error = responseAs[RestErrorResponse]
            error.error shouldBe "service_unavailable"
            error.message shouldBe "Queue is full"
            error.retryAfter shouldBe Some("30s")
          }
      }
    }

    "service times out" should {
      "return 504 with timeout error" in {
        val mockFunctions = createMockFunctions(
          startTestResult = Future.failed(ServiceTimeoutException(
            "Start test timeout",
            new Exception("Timeout")
          ))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        val requestJson =
          s"""
             |{
             |  "test-id": "$testId",
             |  "block-storage-path": "s3://bucket/test-files"
             |}
             |""".stripMargin

        Post("/api/v1/test/start", HttpEntity(ContentTypes.`application/json`, requestJson)) ~>
          routes ~> check {
            status shouldBe StatusCodes.GatewayTimeout
            val error = responseAs[RestErrorResponse]
            error.error shouldBe "actor_timeout"
            error.message shouldBe "Actor system did not respond in time"
          }
      }
    }

    "illegal argument exception occurs" should {
      "return 400 Bad Request" in {
        val mockFunctions = createMockFunctions(
          startTestResult = Future.failed(new IllegalArgumentException("Test ID not found"))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        val requestJson =
          s"""
             |{
             |  "test-id": "$testId",
             |  "block-storage-path": "s3://bucket/test-files"
             |}
             |""".stripMargin

        Post("/api/v1/test/start", HttpEntity(ContentTypes.`application/json`, requestJson)) ~>
          routes ~> check {
            status shouldBe StatusCodes.BadRequest
            val error = responseAs[RestErrorResponse]
            error.error shouldBe "bad_request"
            error.message shouldBe "Test ID not found"
          }
      }
    }

    "unexpected exception occurs" should {
      "return 500 internal server error" in {
        val mockFunctions = createMockFunctions(
          startTestResult = Future.failed(new RuntimeException("Database connection lost"))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        val requestJson =
          s"""
             |{
             |  "test-id": "$testId",
             |  "block-storage-path": "s3://bucket/test-files"
             |}
             |""".stripMargin

        Post("/api/v1/test/start", HttpEntity(ContentTypes.`application/json`, requestJson)) ~>
          routes ~> check {
            status shouldBe StatusCodes.InternalServerError
            val error = responseAs[RestErrorResponse]
            error.error shouldBe "internal_server_error"
            error.message shouldBe "An unexpected error occurred"
          }
      }
    }
  }

  // ========================================
  // GET /api/v1/test/{testId}/status
  // ========================================

  "GET /api/v1/test/{testId}/status" when {

    "successful" should {
      "return 200 OK with test status" in {
        val mockFunctions = createMockFunctions(
          statusResult = Future.successful(TestStatusResponse(
            testId = testId,
            state = "Testing",
            bucket = Some("s3://bucket/test-files"),
            testType = Some("integration"),
            startTime = None,
            endTime = None,
            success = None,
            error = None
          ))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get(s"/api/v1/test/$testId/status") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[RestTestStatusResponse]
          response.`test-id` shouldBe testId
          response.state shouldBe "Testing"
        }
      }
    }

    "service times out" should {
      "return 504 with timeout error" in {
        val mockFunctions = createMockFunctions(
          statusResult = Future.failed(ServiceTimeoutException(
            "Status query timeout",
            new Exception("Timeout")
          ))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get(s"/api/v1/test/$testId/status") ~> routes ~> check {
          status shouldBe StatusCodes.GatewayTimeout
          val error = responseAs[RestErrorResponse]
          error.error shouldBe "actor_timeout"
          error.message shouldBe "Actor system did not respond in time"
        }
      }
    }

    "unexpected exception occurs" should {
      "return 500 internal server error" in {
        val mockFunctions = createMockFunctions(
          statusResult = Future.failed(new RuntimeException("Actor system failure"))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get(s"/api/v1/test/$testId/status") ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          val error = responseAs[RestErrorResponse]
          error.error shouldBe "internal_server_error"
          error.message shouldBe "An unexpected error occurred"
        }
      }
    }
  }

  // ========================================
  // GET /api/v1/queue/status
  // ========================================

  "GET /api/v1/queue/status" when {

    "successful without testId parameter" should {
      "return 200 OK with queue status" in {
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.successful(QueueStatusResponse(
            totalTests = 5,
            setupCount = 1,
            loadingCount = 1,
            loadedCount = 1,
            testingCount = 1,
            completedCount = 1,
            exceptionCount = 0,
            currentlyTesting = None
          ))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get("/api/v1/queue/status") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[RestQueueStatusResponse]
          response.`total-tests` shouldBe 5
          response.`setup-count` shouldBe 1
        }
      }
    }

    "successful with testId parameter" should {
      "return 200 OK with filtered queue status" in {
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.successful(QueueStatusResponse(
            totalTests = 1,
            setupCount = 0,
            loadingCount = 0,
            loadedCount = 0,
            testingCount = 1,
            completedCount = 0,
            exceptionCount = 0,
            currentlyTesting = Some(testId)
          ))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get(s"/api/v1/queue/status?testId=$testId") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[RestQueueStatusResponse]
          response.`currently-testing` shouldBe Some(testId)
        }
      }
    }

    "service times out" should {
      "return 504 with timeout error" in {
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.failed(ServiceTimeoutException(
            "Queue status timeout",
            new Exception("Timeout")
          ))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get("/api/v1/queue/status") ~> routes ~> check {
          status shouldBe StatusCodes.GatewayTimeout
          val error = responseAs[RestErrorResponse]
          error.error shouldBe "actor_timeout"
          error.message shouldBe "Actor system did not respond in time"
        }
      }
    }

    "unexpected exception occurs" should {
      "return 500 internal server error" in {
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.failed(new RuntimeException("Queue actor crashed"))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get("/api/v1/queue/status") ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          val error = responseAs[RestErrorResponse]
          error.error shouldBe "internal_server_error"
          error.message shouldBe "An unexpected error occurred"
        }
      }
    }
  }

  // ========================================
  // DELETE /api/v1/test/{testId}
  // ========================================

  "DELETE /api/v1/test/{testId}" when {

    "successful with cancelled=true" should {
      "return 200 OK with cancellation response" in {
        val mockFunctions = createMockFunctions(
          cancelResult = Future.successful(TestCancelledResponse(
            testId = testId,
            cancelled = true,
            message = Some("Test cancelled successfully")
          ))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Delete(s"/api/v1/test/$testId") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[RestTestCancelledResponse]
          response.`test-id` shouldBe testId
          response.cancelled shouldBe true
          response.message shouldBe Some("Test cancelled successfully")
        }
      }
    }

    "successful with cancelled=false (already completed)" should {
      "return 200 OK with cancellation response" in {
        val mockFunctions = createMockFunctions(
          cancelResult = Future.successful(TestCancelledResponse(
            testId = testId,
            cancelled = false,
            message = Some("Test already completed")
          ))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Delete(s"/api/v1/test/$testId") ~> routes ~> check {
          status shouldBe StatusCodes.OK
          val response = responseAs[RestTestCancelledResponse]
          response.`test-id` shouldBe testId
          response.cancelled shouldBe false
          response.message shouldBe Some("Test already completed")
        }
      }
    }

    "service times out" should {
      "return 504 with timeout error" in {
        val mockFunctions = createMockFunctions(
          cancelResult = Future.failed(ServiceTimeoutException(
            "Cancel timeout",
            new Exception("Timeout")
          ))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Delete(s"/api/v1/test/$testId") ~> routes ~> check {
          status shouldBe StatusCodes.GatewayTimeout
          val error = responseAs[RestErrorResponse]
          error.error shouldBe "actor_timeout"
          error.message shouldBe "Actor system did not respond in time"
        }
      }
    }

    "unexpected exception occurs" should {
      "return 500 internal server error" in {
        val mockFunctions = createMockFunctions(
          cancelResult = Future.failed(new RuntimeException("Cancel operation failed"))
        )
        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Delete(s"/api/v1/test/$testId") ~> routes ~> check {
          status shouldBe StatusCodes.InternalServerError
          val error = responseAs[RestErrorResponse]
          error.error shouldBe "internal_server_error"
          error.message shouldBe "An unexpected error occurred"
        }
      }
    }
  }

  // ========================================
  // Fixture: Test Config and Mocks
  // ========================================

  /**
   * Helper to create test config
   */
  private def createTestConfig(): InterfacesConfig =
    InterfacesConfig(
      restHost = "0.0.0.0",
      restPort = 8080,
      restTimeout = 30.seconds,
      gracefulShutdownTimeout = 10.seconds,
      askTimeout = 25.seconds,
      circuitBreakerMaxFailures = 5,
      circuitBreakerCallTimeout = 25.seconds,
      circuitBreakerResetTimeout = 30.seconds,
      maxConcurrentRequests = 100,
      maxRequestSize = 10485760L,
      maxUriLength = 8192,
      retryAfterServiceUnavailable = "30s",
      retryAfterNotReady = "5s"
    )

  /**
   * Create mock ServiceInterfaceFunctions for testing
   *
   * All methods default to NotImplementedError unless explicitly provided.
   * This allows tests to only mock the specific methods they need.
   */
  private def createMockFunctions(
    initializeResult: Future[InitializeTestResponse] = Future.failed(new NotImplementedError("Not mocked")),
    startTestResult: Future[StartTestResponse] = Future.failed(new NotImplementedError("Not mocked")),
    statusResult: Future[TestStatusResponse] = Future.failed(new NotImplementedError("Not mocked")),
    queueStatusResult: Future[QueueStatusResponse] = Future.failed(new NotImplementedError("Not mocked")),
    cancelResult: Future[TestCancelledResponse] = Future.failed(new NotImplementedError("Not mocked"))
  ): ServiceInterfaceFunctions = {
    ServiceInterfaceFunctions(
      initializeTest = () => initializeResult,
      startTest = (_, _, _) => startTestResult,
      getStatus = _ => statusResult,
      getQueueStatus = _ => queueStatusResult,
      cancelTest = _ => cancelResult
    )
  }
}
