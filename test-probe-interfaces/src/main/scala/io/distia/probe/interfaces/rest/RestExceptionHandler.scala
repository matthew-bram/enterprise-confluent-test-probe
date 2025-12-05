package io.distia.probe.interfaces.rest

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.ExceptionHandler
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.apache.pekko.pattern.AskTimeoutException
import io.distia.probe.core.models.ServiceTimeoutException
import io.distia.probe.core.models.ServiceUnavailableException
import io.distia.probe.core.models.ActorSystemNotReadyException
import io.distia.probe.interfaces.config.InterfacesConfig
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.TimeoutException

/**
 * Custom exception handler for REST API
 *
 * Maps exceptions to appropriate HTTP status codes and error responses.
 * Follows the principle: "Be liberal in what you accept, conservative in what you send"
 *
 * Exception Mapping Strategy:
 * - IllegalArgumentException → 400 Bad Request (client input error)
 * - NoSuchElementException → 404 Not Found (resource not found)
 * - DeserializationException → 400 Bad Request (invalid JSON)
 * - ServiceTimeoutException → 504 Gateway Timeout (actor timeout)
 * - ServiceUnavailableException → 503 Service Unavailable (circuit breaker open)
 * - ActorSystemNotReadyException → 503 Service Unavailable (system initializing)
 * - AskTimeoutException → 504 Gateway Timeout (fallback for unwrapped ask timeout)
 * - TimeoutException → 503 Service Unavailable (temporary failure)
 * - Exception → 500 Internal Server Error (unexpected error)
 *
 * Security: Never expose internal exception details to clients.
 * Logging: Always log full exception for server-side debugging.
 *
 * Visibility: private[interfaces] maintains module encapsulation.
 * All methods remain public for comprehensive unit testing.
 */
private[interfaces] object RestExceptionHandler {

  import RestErrorResponse._

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Pekko HTTP exception handler
   *
   * Applied via handleExceptions directive in routes.
   *
   * @param config InterfacesConfig containing retry-after values
   */
  def handler(config: InterfacesConfig): ExceptionHandler = ExceptionHandler {

    // Client Errors (4xx) - Invalid input from client

    case ex: IllegalArgumentException =>
      logger.warn(s"Bad request: ${ex.getMessage}")
      complete(
        StatusCodes.BadRequest,
        badRequest(s"Invalid request: ${ex.getMessage}")
      )

    case ex: DeserializationException =>
      logger.warn(s"Invalid JSON: ${ex.getMessage}")
      complete(
        StatusCodes.BadRequest,
        badRequest(s"Invalid JSON format: ${ex.getMessage}")
      )

    case ex: NoSuchElementException =>
      logger.warn(s"Resource not found: ${ex.getMessage}")
      complete(
        StatusCodes.NotFound,
        notFound(s"Requested resource not found: ${ex.getMessage}")
      )

    // Server Errors (5xx) - Server-side failures

    // Actor-specific timeout (wrapped by ServiceInterfaceFunctionsFactory)
    case ex: ServiceTimeoutException =>
      logger.error(s"Service timeout: ${ex.getMessage}", ex)
      complete(
        StatusCodes.GatewayTimeout,
        actorTimeout(
          "Actor system did not respond in time",
          Some(ex.getMessage)
        )
      )

    // Circuit breaker open or actor system unavailable
    case ex: ServiceUnavailableException =>
      logger.warn(s"Service unavailable: ${ex.getMessage}")
      complete(
        StatusCodes.ServiceUnavailable,
        serviceUnavailable(ex.getMessage, config.retryAfterServiceUnavailable)
      )

    // Actor system still initializing
    case ex: ActorSystemNotReadyException =>
      logger.warn(s"Actor system not ready: ${ex.getMessage}")
      complete(
        StatusCodes.ServiceUnavailable,
        notReady(ex.getMessage, config.retryAfterNotReady)
      )

    // Fallback for unwrapped AskTimeoutException (should be wrapped by factory)
    case ex: AskTimeoutException =>
      logger.error(s"Unwrapped ask timeout: ${ex.getMessage}", ex)
      complete(
        StatusCodes.GatewayTimeout,
        timeout(s"Request timed out: ${ex.getMessage}")
      )

    // General timeout (Future timeout, etc.)
    case ex: TimeoutException =>
      logger.error(s"Request timeout: ${ex.getMessage}", ex)
      complete(
        StatusCodes.ServiceUnavailable,
        timeout("Request timed out - service may be temporarily unavailable")
      )

    // Catch-all for unexpected exceptions
    case ex: Exception =>
      // Log full stack trace for debugging
      logger.error(s"Unexpected server error: ${ex.getClass.getSimpleName} - ${ex.getMessage}", ex)
      complete(
        StatusCodes.InternalServerError,
        internalServerError("An unexpected error occurred. Please contact support if this persists.")
      )
  }
}
