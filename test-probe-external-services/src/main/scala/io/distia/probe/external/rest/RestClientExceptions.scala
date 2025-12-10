package io.distia.probe.external.rest

import scala.concurrent.duration.FiniteDuration

/**
 * Sealed trait for REST client exceptions.
 * Thrown within Future for proper error propagation.
 * Enables exhaustive pattern matching in error handlers.
 */
sealed trait RestClientException extends RuntimeException:
  def message: String

/**
 * HTTP error response (non-2xx status codes).
 *
 * @param statusCode HTTP status code
 * @param body Response body
 * @param message Error message
 */
case class RestHttpError(
  statusCode: Int,
  body: String,
  override val message: String = "HTTP request failed"
) extends RuntimeException(s"$message: HTTP $statusCode") with RestClientException

/**
 * Connection/network error.
 *
 * @param uri Target URI that failed
 * @param cause Underlying exception
 * @param message Error message
 */
case class RestConnectionError(
  uri: String,
  cause: Throwable,
  override val message: String = "Connection failed"
) extends RuntimeException(s"$message: $uri", cause) with RestClientException

/**
 * Request timeout.
 *
 * @param uri Target URI that timed out
 * @param timeout Timeout duration
 * @param message Error message
 */
case class RestTimeoutError(
  uri: String,
  timeout: FiniteDuration,
  override val message: String = "Request timed out"
) extends RuntimeException(s"$message: $uri after $timeout") with RestClientException

/**
 * JSON serialization/deserialization error.
 *
 * @param operation "serialize" or "deserialize"
 * @param cause Underlying exception
 * @param message Error message
 */
case class RestSerializationError(
  operation: String,
  cause: Throwable,
  override val message: String = "JSON processing failed"
) extends RuntimeException(s"$message during $operation", cause) with RestClientException
