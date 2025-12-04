package io.distia.probe.interfaces.rest

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import io.distia.probe.core.builder.ServiceInterfaceFunctions
import io.distia.probe.core.models.{QueueStatusResponse, ServiceTimeoutException, ServiceUnavailableException}
import io.distia.probe.interfaces.config.InterfacesConfig
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import java.util.UUID

/**
 * Unit tests for REST API health check endpoint
 *
 * Target Coverage: Health check endpoint (GET /api/v1/health)
 *
 * Tests:
 * - Health check returns 200 OK when actor system responds
 * - Health check returns 503 when actor times out
 * - Health check returns 503 when service unavailable
 * - Health check returns 503 for unexpected failures
 * - Response format validation (JSON structure)
 */
class RestRoutesHealthCheckSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  // Import JSON formats
  import HealthResponse._

  "GET /api/v1/health" when {

    "actor system is healthy" should {

      "return 200 OK with healthy status" in {
        // Mock successful queue status response (empty queue)
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.successful(QueueStatusResponse(
            totalTests = 0,
            setupCount = 0,
            loadingCount = 0,
            loadedCount = 0,
            testingCount = 0,
            completedCount = 0,
            exceptionCount = 0,
            currentlyTesting = None
          ))
        )

        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get("/api/v1/health") ~> routes ~> check {
          status shouldBe StatusCodes.OK

          val response = responseAs[HealthResponse]
          response.status shouldBe "healthy"
          response.actorSystem shouldBe "running"
          response.error shouldBe None
          response.timestamp should be > 0L
        }
      }

      "ignore queue status details (only cares that actor responded)" in {
        // Mock queue status with active tests - health check should still be healthy
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.successful(QueueStatusResponse(
            totalTests = 5,
            setupCount = 1,
            loadingCount = 1,
            loadedCount = 1,
            testingCount = 1,
            completedCount = 1,
            exceptionCount = 0,
            currentlyTesting = Some(UUID.randomUUID())
          ))
        )

        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get("/api/v1/health") ~> routes ~> check {
          status shouldBe StatusCodes.OK

          val response = responseAs[HealthResponse]
          response.status shouldBe "healthy"
          response.actorSystem shouldBe "running"
        }
      }
    }

    "actor system times out" should {

      "return 503 Service Unavailable with timeout status" in {
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.failed(ServiceTimeoutException(
            "Actor did not respond in time",
            new Exception("Ask timeout")
          ))
        )

        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get("/api/v1/health") ~> routes ~> check {
          status shouldBe StatusCodes.ServiceUnavailable

          val response = responseAs[HealthResponse]
          response.status shouldBe "unhealthy"
          response.actorSystem shouldBe "timeout"
          response.error shouldBe Some("Actor system did not respond in time")
          response.timestamp should be > 0L
        }
      }
    }

    "service is unavailable" should {

      "return 503 Service Unavailable with unavailable status" in {
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.failed(ServiceUnavailableException(
            "Circuit breaker open - too many failures"
          ))
        )

        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get("/api/v1/health") ~> routes ~> check {
          status shouldBe StatusCodes.ServiceUnavailable

          val response = responseAs[HealthResponse]
          response.status shouldBe "unhealthy"
          response.actorSystem shouldBe "unavailable"
          response.error shouldBe Some("Circuit breaker open - too many failures")
          response.timestamp should be > 0L
        }
      }
    }

    "unexpected error occurs" should {

      "return 503 Service Unavailable with error status" in {
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.failed(new RuntimeException("Unexpected actor system error"))
        )

        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get("/api/v1/health") ~> routes ~> check {
          status shouldBe StatusCodes.ServiceUnavailable

          val response = responseAs[HealthResponse]
          response.status shouldBe "unhealthy"
          response.actorSystem shouldBe "error"
          response.error shouldBe Some("Unexpected health check failure")
          response.timestamp should be > 0L
        }
      }
    }

    "validating response format" should {

      "return well-formed JSON with all required fields" in {
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.successful(createEmptyQueueStatus())
        )

        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Get("/api/v1/health") ~> routes ~> check {
          contentType.mediaType.mainType shouldBe "application"
          contentType.mediaType.subType shouldBe "json"

          val json = responseAs[String].parseJson.asJsObject
          json.fields should contain key "status"
          json.fields should contain key "actorSystem"
          json.fields should contain key "timestamp"
        }
      }

      "include error field only when unhealthy" in {
        val healthyFunctions = createMockFunctions(
          queueStatusResult = Future.successful(createEmptyQueueStatus())
        )

        val unhealthyFunctions = createMockFunctions(
          queueStatusResult = Future.failed(ServiceTimeoutException("timeout", new Exception()))
        )

        val healthyRoutes = new RestRoutes(createTestConfig(), healthyFunctions).routes
        val unhealthyRoutes = new RestRoutes(createTestConfig(), unhealthyFunctions).routes

        // Healthy response should not have error field (or it should be null/None)
        Get("/api/v1/health") ~> healthyRoutes ~> check {
          val response = responseAs[HealthResponse]
          response.error shouldBe None
        }

        // Unhealthy response should have error field with message
        Get("/api/v1/health") ~> unhealthyRoutes ~> check {
          val response = responseAs[HealthResponse]
          response.error shouldBe defined
          response.error.get should not be empty
        }
      }
    }

    "handling HTTP methods" should {

      "reject POST method" in {
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.successful(createEmptyQueueStatus())
        )

        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Post("/api/v1/health") ~> routes ~> check {
          status shouldBe StatusCodes.MethodNotAllowed
        }
      }

      "reject PUT method" in {
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.successful(createEmptyQueueStatus())
        )

        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Put("/api/v1/health") ~> routes ~> check {
          status shouldBe StatusCodes.MethodNotAllowed
        }
      }

      "reject DELETE method" in {
        val mockFunctions = createMockFunctions(
          queueStatusResult = Future.successful(createEmptyQueueStatus())
        )

        val routes = new RestRoutes(createTestConfig(), mockFunctions).routes

        Delete("/api/v1/health") ~> routes ~> check {
          status shouldBe StatusCodes.MethodNotAllowed
        }
      }
    }
  }

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
   * Helper to create empty queue status (no tests)
   */
  private def createEmptyQueueStatus(): QueueStatusResponse = {
    QueueStatusResponse(
      totalTests = 0,
      setupCount = 0,
      loadingCount = 0,
      loadedCount = 0,
      testingCount = 0,
      completedCount = 0,
      exceptionCount = 0,
      currentlyTesting = None
    )
  }

  /**
   * Create mock ServiceInterfaceFunctions for testing
   *
   * Only implements getQueueStatus since that's what health check uses.
   * Other methods throw NotImplementedError.
   */
  private def createMockFunctions(
    queueStatusResult: Future[QueueStatusResponse]
  ): ServiceInterfaceFunctions = {
    ServiceInterfaceFunctions(
      initializeTest = () => ???,
      startTest = (_, _, _) => ???,
      getStatus = _ => ???,
      getQueueStatus = _ => queueStatusResult,
      cancelTest = _ => ???
    )
  }
}
