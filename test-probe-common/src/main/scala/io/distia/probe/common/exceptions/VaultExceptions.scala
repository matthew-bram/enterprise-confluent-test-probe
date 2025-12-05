package io.distia.probe
package common
package exceptions

/**
 * Exception hierarchy for vault service operations
 *
 * This hierarchy supports the retry logic pattern used in CSP vault services:
 * - Transient exceptions are retried with exponential backoff
 * - Non-transient exceptions fail fast
 *
 * The exceptionType field enables programmatic classification without
 * pattern matching, useful for retry logic and monitoring.
 *
 * @see io.distia.probe.services.builder.modules.AwsVaultService
 * @see io.distia.probe.services.builder.modules.AzureVaultService
 * @see io.distia.probe.services.builder.modules.GcpVaultService
 */
sealed trait VaultExceptionType
case object Transient extends VaultExceptionType
case object NonTransient extends VaultExceptionType

/**
 * Base exception for all vault operations
 *
 * @param message Human-readable error message
 * @param cause Underlying exception (if any)
 * @param exceptionType Classification for retry logic
 */
class VaultException(
  message: String,
  cause: Throwable = null,
  val exceptionType: VaultExceptionType = NonTransient
) extends RuntimeException(message, cause)

/**
 * Base class for transient vault errors that should be retried
 *
 * Transient errors include:
 * - Network timeouts
 * - HTTP 429 (rate limiting)
 * - HTTP 503 (service unavailable)
 * - Cloud provider throttling
 * - Temporary DNS failures
 */
class TransientVaultException(message: String, cause: Throwable = null)
  extends VaultException(message, cause, Transient)

/**
 * Network timeout during vault operation
 *
 * Indicates CSP function or vault service did not respond within timeout period.
 * Should be retried with exponential backoff.
 */
class VaultTimeoutException(message: String, cause: Throwable = null)
  extends TransientVaultException(message, cause)

/**
 * Rate limiting by cloud provider or vault service
 *
 * HTTP 429 Too Many Requests or equivalent.
 * Should be retried with exponential backoff.
 */
class VaultRateLimitException(message: String, cause: Throwable = null)
  extends TransientVaultException(message, cause)

/**
 * Cloud provider quota exceeded or service temporarily unavailable
 *
 * HTTP 503 Service Unavailable or cloud-specific quota errors.
 * Should be retried with exponential backoff.
 */
class VaultServiceUnavailableException(message: String, cause: Throwable = null)
  extends TransientVaultException(message, cause)

/**
 * Base class for non-transient vault errors that should fail fast
 *
 * Non-transient errors include:
 * - Authentication/authorization failures
 * - Invalid configuration
 * - Malformed responses
 * - Missing vault function
 * - Invalid request parameters
 */
class NonTransientVaultException(message: String, cause: Throwable = null)
  extends VaultException(message, cause, NonTransient)

/**
 * Authentication or authorization failure
 *
 * HTTP 401 Unauthorized or 403 Forbidden.
 * Indicates invalid credentials, expired tokens, or insufficient permissions.
 * Should fail fast (no retry).
 */
class VaultAuthException(message: String, cause: Throwable = null)
  extends NonTransientVaultException(message, cause)

/**
 * Failed to parse or map vault response using Rosetta
 *
 * Indicates:
 * - Invalid JSON in vault response
 * - Rosetta mapping errors
 * - Missing required fields in response
 * - Type mismatches
 *
 * Should fail fast (no retry).
 */
class VaultMappingException(message: String, cause: Throwable = null)
  extends NonTransientVaultException(message, cause)

/**
 * Vault function not found
 *
 * HTTP 404 Not Found.
 * Indicates misconfigured function URL/ARN or deleted function.
 * Should fail fast (no retry).
 */
class VaultNotFoundException(message: String, cause: Throwable = null)
  extends NonTransientVaultException(message, cause)

/**
 * Invalid configuration for vault service
 *
 * Indicates:
 * - Missing required configuration
 * - Invalid configuration values
 * - Configuration validation failures
 *
 * Should fail fast (no retry).
 */
class VaultConfigurationException(message: String, cause: Throwable = null)
  extends NonTransientVaultException(message, cause)

/**
 * Companion object with utility methods for exception handling
 */
object VaultException {

  /**
   * Determine if an exception should be retried
   *
   * @param ex The exception to classify
   * @return true if exception is transient and should be retried
   */
  def isTransient(ex: Throwable): Boolean = ex match {
    case _: TransientVaultException => true
    case _: java.net.SocketTimeoutException => true
    case _: java.net.ConnectException => true
    case _: java.io.IOException if ex.getMessage != null && ex.getMessage.contains("timeout") => true
    case _ => false
  }

  /**
   * Determine if an exception is non-transient and should fail fast
   *
   * @param ex The exception to classify
   * @return true if exception is non-transient
   */
  def isNonTransient(ex: Throwable): Boolean = ex match {
    case _: NonTransientVaultException => true
    case _ => false
  }

  /**
   * Convert HTTP status code to appropriate VaultException
   *
   * @param statusCode HTTP status code from vault response
   * @param message Error message
   * @param responseBody Optional response body for additional context
   * @return Appropriate VaultException subclass
   */
  def fromHttpStatus(statusCode: Int, message: String, responseBody: String = ""): VaultException = statusCode match {
    case 401 | 403 =>
      new VaultAuthException(s"Authentication/authorization failed (HTTP $statusCode): $message")
    case 404 =>
      new VaultNotFoundException(s"Vault function not found (HTTP 404): $message")
    case 429 =>
      new VaultRateLimitException(s"Rate limit exceeded (HTTP 429): $message")
    case 500 =>
      new NonTransientVaultException(s"Vault internal error (HTTP 500): $message")
    case 503 =>
      new VaultServiceUnavailableException(s"Vault service unavailable (HTTP 503): $message")
    case code if code >= 400 && code < 500 =>
      new NonTransientVaultException(s"Client error (HTTP $code): $message")
    case code if code >= 500 =>
      new VaultServiceUnavailableException(s"Server error (HTTP $code): $message")
    case _ =>
      new VaultException(s"Unexpected HTTP status $statusCode: $message")
  }
}
