package io.distia.probe.interfaces.rest

import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.apache.pekko.pattern.AskTimeoutException
import spray.json._
import io.distia.probe.core.builder.ServiceInterfaceFunctions
import io.distia.probe.core.models.ServiceTimeoutException
import io.distia.probe.core.models.ServiceUnavailableException
import io.distia.probe.core.models.ActorSystemNotReadyException
import io.distia.probe.interfaces.config.InterfacesConfig
import io.distia.probe.interfaces.models.rest._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * Health check response
 */
private[interfaces] case class HealthResponse(
  status: String,
  actorSystem: String,
  error: Option[String] = None,
  timestamp: Long = System.currentTimeMillis()
)

private[interfaces] object HealthResponse {
  import DefaultJsonProtocol._
  implicit val healthResponseFormat: RootJsonFormat[HealthResponse] = jsonFormat4(HealthResponse.apply)
}

/**
 * REST API routes for test management
 *
 * Endpoints:
 * - GET    /api/v1/health                - Health check (actor system status)
 * - POST   /api/v1/test/initialize       - Create new test
 * - POST   /api/v1/test/start            - Start test execution
 * - GET    /api/v1/test/{testId}/status  - Get test status
 * - GET    /api/v1/queue/status          - Get queue status
 * - DELETE /api/v1/test/{testId}         - Cancel test
 *
 * Error Handling:
 * - Custom ExceptionHandler for server errors (500, 503, 504)
 * - Custom RejectionHandler for client errors (400, 404, 415)
 * - Request validation before actor communication
 *
 * Visibility: private[interfaces] maintains module encapsulation.
 * All methods remain public for comprehensive unit testing.
 */
private[interfaces] class RestRoutes(
  config: InterfacesConfig,
  functions: ServiceInterfaceFunctions
)(implicit ec: ExecutionContext) {

  import JsonFormats._
  import RestModelConversions._
  import RestRejectionHandler.handler as rejectionHandler
  import HealthResponse._

  private val logger: Logger = LoggerFactory.getLogger(classOf[RestRoutes])

  // Create exception handler with config
  private val exceptionHandler = RestExceptionHandler.handler(config)

  val routes: Route =
    // Apply exception and rejection handlers to all routes
    handleExceptions(exceptionHandler) {
      handleRejections(rejectionHandler) {
        pathPrefix("api" / "v1") {
          concat(

            // GET /api/v1/health
            // Health check using getQueueStatus (Success = healthy, Failure = unhealthy)
            path("health") {
              get {
                logger.debug("GET /health")
                onComplete(functions.getQueueStatus(None)) {
                  case Success(_) =>
                    // Ignore queue status details - just care that actor responded
                    complete(StatusCodes.OK, HealthResponse(
                      status = "healthy",
                      actorSystem = "running"
                    ))

                  case Failure(ex: ServiceTimeoutException) =>
                    logger.error("Health check failed: actor timeout", ex)
                    complete(StatusCodes.ServiceUnavailable, HealthResponse(
                      status = "unhealthy",
                      actorSystem = "timeout",
                      error = Some("Actor system did not respond in time")
                    ))

                  case Failure(ex: ServiceUnavailableException) =>
                    logger.error("Health check failed: service unavailable", ex)
                    complete(StatusCodes.ServiceUnavailable, HealthResponse(
                      status = "unhealthy",
                      actorSystem = "unavailable",
                      error = Some(ex.getMessage)
                    ))

                  case Failure(ex) =>
                    logger.error("Health check failed: unexpected error", ex)
                    complete(StatusCodes.ServiceUnavailable, HealthResponse(
                      status = "unhealthy",
                      actorSystem = "error",
                      error = Some("Unexpected health check failure")
                    ))
                }
              }
            },

            // POST /api/v1/test/initialize
            path("test" / "initialize") {
              post {
                logger.info("POST /test/initialize")
                onComplete(functions.initializeTest()) {
                  case Success(response) =>
                    logger.info(s"Test initialized: testId=${response.testId}")
                    complete(StatusCodes.Created, toRest(response))

                  case Failure(ex: ServiceUnavailableException) =>
                    logger.warn(s"Service unavailable: ${ex.getMessage}")
                    complete(StatusCodes.ServiceUnavailable, RestErrorResponse.serviceUnavailable(
                      ex.getMessage,
                      config.retryAfterServiceUnavailable
                    ))

                  case Failure(ex: ServiceTimeoutException) =>
                    logger.error("Actor timeout initializing test", ex)
                    complete(StatusCodes.GatewayTimeout, RestErrorResponse.actorTimeout(
                      "Actor system did not respond in time",
                      Some(ex.getMessage)
                    ))

                  case Failure(ex: AskTimeoutException) =>
                    logger.error("Ask timeout (unwrapped)", ex)
                    complete(StatusCodes.GatewayTimeout, RestErrorResponse.timeout(s"Actor ask timeout: ${ex.getMessage}"))

                  case Failure(ex) =>
                    logger.error("Unexpected error initializing test", ex)
                    complete(StatusCodes.InternalServerError, RestErrorResponse.internalServerError("An unexpected error occurred"))
                }
              }
            },

            // POST /api/v1/test/start
            path("test" / "start") {
              post {
                entity(as[RestStartTestRequest]) { request =>
                  logger.info(s"POST /test/start testId=${request.`test-id`} bucket=${request.`block-storage-path`}")

                  // Validate request before actor communication
                  RestValidation.validateStartRequest(request) match {
                    case Left(errorMessage) =>
                      logger.warn(s"Validation failed: $errorMessage")
                      complete(
                        StatusCodes.BadRequest,
                        RestErrorResponse.validationError("Request validation failed", errorMessage)
                      )

                    case Right(_) =>
                      val (testId, bucket, testType) = toCore(request)
                      onComplete(functions.startTest(testId, bucket, testType)) {
                        case Success(response) =>
                          logger.info(s"Test started: testId=$testId accepted=${response.accepted}")
                          complete(StatusCodes.Accepted, toRest(response))

                        case Failure(ex: ServiceUnavailableException) =>
                          logger.warn(s"Service unavailable: ${ex.getMessage}")
                          complete(StatusCodes.ServiceUnavailable, RestErrorResponse.serviceUnavailable(
                            ex.getMessage,
                            config.retryAfterServiceUnavailable
                          ))

                        case Failure(ex: ServiceTimeoutException) =>
                          logger.error(s"Timeout starting test testId=$testId", ex)
                          complete(StatusCodes.GatewayTimeout, RestErrorResponse.actorTimeout(
                            "Actor system did not respond in time"
                          ))

                        case Failure(ex: IllegalArgumentException) =>
                          logger.warn(s"Invalid start request testId=$testId: ${ex.getMessage}")
                          complete(StatusCodes.BadRequest, RestErrorResponse.badRequest(ex.getMessage))

                        case Failure(ex) =>
                          logger.error(s"Error starting test testId=$testId", ex)
                          complete(StatusCodes.InternalServerError, RestErrorResponse.internalServerError("An unexpected error occurred"))
                      }
                  }
                }
              }
            },

            // GET /api/v1/test/{testId}/status
            path("test" / JavaUUID / "status") { testId =>
              get {
                logger.info(s"GET /test/$testId/status")
                onComplete(functions.getStatus(testId)) {
                  case Success(response) =>
                    complete(StatusCodes.OK, toRest(response))

                  case Failure(ex: ServiceTimeoutException) =>
                    logger.error(s"Timeout getting status testId=$testId", ex)
                    complete(StatusCodes.GatewayTimeout, RestErrorResponse.actorTimeout(
                      "Actor system did not respond in time"
                    ))

                  case Failure(ex) =>
                    logger.error(s"Error getting status testId=$testId", ex)
                    complete(StatusCodes.InternalServerError, RestErrorResponse.internalServerError("An unexpected error occurred"))
                }
              }
            },

            // GET /api/v1/queue/status?testId={uuid}
            path("queue" / "status") {
              get {
                parameters("testId".as[java.util.UUID].?) { testIdOpt =>
                  logger.info(s"GET /queue/status testId=$testIdOpt")
                  onComplete(functions.getQueueStatus(testIdOpt)) {
                    case Success(response) =>
                      complete(StatusCodes.OK, toRest(response))

                    case Failure(ex: ServiceTimeoutException) =>
                      logger.error("Timeout getting queue status", ex)
                      complete(StatusCodes.GatewayTimeout, RestErrorResponse.actorTimeout(
                        "Actor system did not respond in time"
                      ))

                    case Failure(ex) =>
                      logger.error("Error getting queue status", ex)
                      complete(StatusCodes.InternalServerError, RestErrorResponse.internalServerError("An unexpected error occurred"))
                  }
                }
              }
            },

            // DELETE /api/v1/test/{testId}
            path("test" / JavaUUID) { testId =>
              delete {
                logger.info(s"DELETE /test/$testId")
                onComplete(functions.cancelTest(testId)) {
                  case Success(response) =>
                    logger.info(s"Test cancelled: testId=$testId cancelled=${response.cancelled}")
                    complete(StatusCodes.OK, toRest(response))

                  case Failure(ex: ServiceTimeoutException) =>
                    logger.error(s"Timeout cancelling test testId=$testId", ex)
                    complete(StatusCodes.GatewayTimeout, RestErrorResponse.actorTimeout(
                      "Actor system did not respond in time"
                    ))

                  case Failure(ex) =>
                    logger.error(s"Error cancelling test testId=$testId", ex)
                    complete(StatusCodes.InternalServerError, RestErrorResponse.internalServerError("An unexpected error occurred"))
                }
              }
            }
          )
        }
      }
    }
}
