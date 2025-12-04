package io.distia.probe.interfaces.rest

import spray.json._

/**
 * Standard error response model for REST API
 *
 * RFC 7807-inspired error structure (simplified for this API)
 *
 * Fields:
 * - error: Machine-readable error code (e.g., "validation_error", "not_found")
 * - message: Human-readable error description
 * - details: Optional structured details (e.g., field validation errors)
 * - retryAfter: Optional Retry-After value for 503 responses (e.g., "30s")
 * - timestamp: Epoch milliseconds of when error occurred
 *
 * Visibility: private[interfaces] maintains module encapsulation.
 * All methods remain public for comprehensive unit testing.
 */
private[interfaces] case class RestErrorResponse(
  error: String,
  message: String,
  details: Option[String] = None,
  retryAfter: Option[String] = None,
  timestamp: Long = System.currentTimeMillis()
)

private[interfaces] object RestErrorResponse {
  import DefaultJsonProtocol._

  // JSON format
  implicit val restErrorResponseFormat: RootJsonFormat[RestErrorResponse] =
    jsonFormat5(RestErrorResponse.apply)

  // Factory methods for common errors

  /**
   * 400 Bad Request - Generic client error
   */
  def badRequest(message: String, details: Option[String] = None): RestErrorResponse =
    RestErrorResponse("bad_request", message, details)

  /**
   * 400 Bad Request - Validation error with field details
   */
  def validationError(message: String, details: String): RestErrorResponse =
    RestErrorResponse("validation_error", message, Some(details))

  /**
   * 404 Not Found - Resource not found
   */
  def notFound(message: String): RestErrorResponse =
    RestErrorResponse("not_found", message)

  /**
   * 415 Unsupported Media Type - Wrong Content-Type
   */
  def unsupportedMediaType(message: String): RestErrorResponse =
    RestErrorResponse("unsupported_media_type", message)

  /**
   * 500 Internal Server Error - Unexpected server error
   */
  def internalServerError(message: String): RestErrorResponse =
    RestErrorResponse("internal_server_error", message)

  /**
   * 503 Service Unavailable - System temporarily unavailable (circuit breaker open)
   *
   * @param message Human-readable error message
   * @param retryAfter Retry-After header value from config.retryAfterServiceUnavailable
   */
  def serviceUnavailable(message: String, retryAfter: String): RestErrorResponse =
    RestErrorResponse("service_unavailable", message, retryAfter = Some(retryAfter))

  /**
   * 504 Gateway Timeout - Actor ask timeout or general timeout
   */
  def timeout(message: String): RestErrorResponse =
    RestErrorResponse("timeout", message)

  /**
   * 504 Gateway Timeout - Actor-specific timeout
   */
  def actorTimeout(message: String, details: Option[String] = None): RestErrorResponse =
    RestErrorResponse("actor_timeout", message, details)

  /**
   * 503 Service Unavailable - Actor system not ready (initializing)
   *
   * @param message Human-readable error message
   * @param retryAfter Retry-After header value from config.retryAfterNotReady
   */
  def notReady(message: String, retryAfter: String): RestErrorResponse =
    RestErrorResponse("not_ready", message, retryAfter = Some(retryAfter))

  /**
   * 405 Method Not Allowed - Wrong HTTP method
   */
  def methodNotAllowed(message: String): RestErrorResponse =
    RestErrorResponse("method_not_allowed", message)
}
