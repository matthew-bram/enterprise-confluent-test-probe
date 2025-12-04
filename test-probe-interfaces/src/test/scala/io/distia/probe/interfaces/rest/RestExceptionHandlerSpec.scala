package io.distia.probe.interfaces.rest

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.{complete, get, handleExceptions, path}
import org.apache.pekko.http.scaladsl.server.{ExceptionHandler, Route}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.apache.pekko.pattern.AskTimeoutException
import io.distia.probe.core.models.{ServiceTimeoutException, ServiceUnavailableException, ActorSystemNotReadyException}
import io.distia.probe.interfaces.config.InterfacesConfig
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json._

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

/**
 * Unit tests for RestExceptionHandler
 *
 * Target Coverage: All exception handling paths (currently 4.23% → 90%+)
 *
 * Tests:
 * - Client errors (4xx): IllegalArgumentException, DeserializationException, NoSuchElementException
 * - Server errors (5xx): ServiceTimeoutException, ServiceUnavailableException, ActorSystemNotReadyException
 * - Timeout errors (504/503): AskTimeoutException, TimeoutException
 * - Catch-all: Unexpected exceptions → 500
 * - Retry-After headers: ServiceUnavailable and NotReady responses
 */
class RestExceptionHandlerSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  import RestErrorResponse._

  /**
   * Creates test route that throws specified exception
   */
  private def createTestRoute(exception: Exception, config: InterfacesConfig): Route = {
    val exceptionHandler = RestExceptionHandler.handler(config)
    handleExceptions(exceptionHandler) {
      path("test") {
        get {
          throw exception
        }
      }
    }
  }

  private val testConfig: InterfacesConfig = InterfacesConfig(
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

  "RestExceptionHandler" when {

    // ========================================
    // Client Errors (4xx)
    // ========================================

    "handling IllegalArgumentException" should {

      "return 400 Bad Request with error message" in {
        val exception = new IllegalArgumentException("Invalid test-id format")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          status shouldBe StatusCodes.BadRequest

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "bad_request"
          response.message should include("Invalid request")
          response.message should include("Invalid test-id format")
          response.timestamp should be > 0L
        }
      }

      "include exception details in response" in {
        val exception = new IllegalArgumentException("block-storage-path cannot be empty")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          val response = responseAs[RestErrorResponse]
          response.message should include("block-storage-path cannot be empty")
        }
      }
    }

    "handling DeserializationException" should {

      "return 400 Bad Request for invalid JSON" in {
        val exception = DeserializationException("Expected String, got Number")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          status shouldBe StatusCodes.BadRequest

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "bad_request"
          response.message should include("Invalid JSON format")
          response.message should include("Expected String, got Number")
        }
      }
    }

    "handling NoSuchElementException" should {

      "return 404 Not Found" in {
        val exception = new NoSuchElementException("Test with ID 12345 not found")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          status shouldBe StatusCodes.NotFound

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "not_found"
          response.message should include("Requested resource not found")
          response.message should include("Test with ID 12345 not found")
        }
      }
    }

    // ========================================
    // Server Errors (5xx) - Actor System
    // ========================================

    "handling ServiceTimeoutException" should {

      "return 504 Gateway Timeout" in {
        val exception = ServiceTimeoutException(
          "QueueActor did not respond",
          new Exception("Ask timeout")
        )
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          status shouldBe StatusCodes.GatewayTimeout

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "actor_timeout"
          response.message shouldBe "Actor system did not respond in time"
        }
      }

      "include details field with exception message" in {
        val exception = ServiceTimeoutException(
          "TestExecutionActor timeout after 25s",
          new Exception("Timeout")
        )
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          val response = responseAs[RestErrorResponse]
          response.details shouldBe Some("TestExecutionActor timeout after 25s")
        }
      }
    }

    "handling ServiceUnavailableException" should {

      "return 503 Service Unavailable" in {
        val exception = ServiceUnavailableException("Circuit breaker open - too many failures")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          status shouldBe StatusCodes.ServiceUnavailable

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "service_unavailable"
          response.message shouldBe "Circuit breaker open - too many failures"
        }
      }

      "include Retry-After header with configured value" in {
        val exception = ServiceUnavailableException("Service overloaded")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          val response = responseAs[RestErrorResponse]
          response.retryAfter shouldBe Some("30s")
        }
      }
    }

    "handling ActorSystemNotReadyException" should {

      "return 503 Service Unavailable" in {
        val exception = ActorSystemNotReadyException("Actor system is still initializing")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          status shouldBe StatusCodes.ServiceUnavailable

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "not_ready"
          response.message shouldBe "Actor system is still initializing"
        }
      }

      "include Retry-After header with shorter configured value" in {
        val exception = ActorSystemNotReadyException("Waiting for dependencies")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          val response = responseAs[RestErrorResponse]
          response.retryAfter shouldBe Some("5s")
        }
      }
    }

    // ========================================
    // Timeout Errors (504/503)
    // ========================================

    "handling AskTimeoutException" should {

      "return 504 Gateway Timeout" in {
        val exception = new AskTimeoutException("Ask timeout after 25 seconds")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          status shouldBe StatusCodes.GatewayTimeout

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "timeout"
          response.message should include("Request timed out")
        }
      }

      "include exception message in response" in {
        val exception = new AskTimeoutException("Recipient[Actor[...]] had already been terminated")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          val response = responseAs[RestErrorResponse]
          response.message should include("Recipient[Actor[...]] had already been terminated")
        }
      }
    }

    "handling TimeoutException" should {

      "return 503 Service Unavailable" in {
        val exception = new TimeoutException("Future timed out after 30 seconds")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          status shouldBe StatusCodes.ServiceUnavailable

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "timeout"
          response.message shouldBe "Request timed out - service may be temporarily unavailable"
        }
      }
    }

    // ========================================
    // Catch-All Handler
    // ========================================

    "handling unexpected Exception" should {

      "return 500 Internal Server Error" in {
        val exception = new RuntimeException("Unexpected database connection failure")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          status shouldBe StatusCodes.InternalServerError

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "internal_server_error"
          response.message shouldBe "An unexpected error occurred. Please contact support if this persists."
        }
      }

      "handle any arbitrary exception type" in {
        val exception = new ArithmeticException("Division by zero")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          status shouldBe StatusCodes.InternalServerError

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "internal_server_error"
        }
      }

      "never expose internal exception details" in {
        val exception = new NullPointerException("userSession.credentials.token")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          val response = responseAs[RestErrorResponse]
          // Should NOT include "userSession.credentials.token"
          response.message shouldBe "An unexpected error occurred. Please contact support if this persists."
        }
      }
    }

    // ========================================
    // Handler Creation
    // ========================================

    "handler method" should {

      "create ExceptionHandler instance" in {
        val handler = RestExceptionHandler.handler(testConfig)
        handler shouldBe a[ExceptionHandler]
      }

      "use config for retry-after values" in {
        val customConfig = testConfig.copy(
          retryAfterServiceUnavailable = "60s",
          retryAfterNotReady = "10s"
        )

        val exception = ServiceUnavailableException("Overloaded")
        val route = createTestRoute(exception, customConfig)

        Get("/test") ~> route ~> check {
          val response = responseAs[RestErrorResponse]
          response.retryAfter shouldBe Some("60s")
        }
      }
    }

    // ========================================
    // Response Format Validation
    // ========================================

    "all error responses" should {

      "return well-formed JSON" in {
        val exception = new IllegalArgumentException("Test error")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          contentType.mediaType.mainType shouldBe "application"
          contentType.mediaType.subType shouldBe "json"

          val json = responseAs[String].parseJson.asJsObject
          json.fields should contain key "error"
          json.fields should contain key "message"
          json.fields should contain key "timestamp"
        }
      }

      "include timestamp in Unix epoch milliseconds" in {
        val exception = new IllegalArgumentException("Test")
        val route = createTestRoute(exception, testConfig)

        Get("/test") ~> route ~> check {
          val response = responseAs[RestErrorResponse]
          response.timestamp should be > 1600000000000L // After Sept 2020
          response.timestamp should be < 2000000000000L // Before May 2033
        }
      }

      "include error type as string" in {
        val cases = List(
          (new IllegalArgumentException("test"), "bad_request"),
          (new NoSuchElementException("test"), "not_found"),
          (ServiceTimeoutException("test", new Exception()), "actor_timeout"),
          (ServiceUnavailableException("test"), "service_unavailable"),
          (ActorSystemNotReadyException("test"), "not_ready"),
          (new AskTimeoutException("test"), "timeout"),
          (new TimeoutException("test"), "timeout"),
          (new RuntimeException("test"), "internal_server_error")
        )

        cases.foreach { case (exception, expectedError) =>
          val route = createTestRoute(exception, testConfig)
          Get("/test") ~> route ~> check {
            val response = responseAs[RestErrorResponse]
            response.error shouldBe expectedError
          }
        }
      }
    }
  }
}
