package io.distia.probe
package core
package models

sealed trait ProbeExceptions
case class FatalBooting(message: String = "", cause: Option[Throwable] = None) extends Exception(message, cause.orNull) with ProbeExceptions
case class CucumberException(message: String = "", cause: Option[Throwable] = None) extends Exception(message, cause.orNull) with ProbeExceptions
case class KafkaProducerException(message: String = "", cause: Option[Throwable] = None) extends Exception(message, cause.orNull) with ProbeExceptions
case class KafkaConsumerException(message: String = "", cause: Option[Throwable] = None) extends Exception(message, cause.orNull) with ProbeExceptions
case class BlockStorageException(message: String = "", cause: Option[Throwable] = None) extends Exception(message, cause.orNull) with ProbeExceptions
case class VaultConsumerException(message: String = "", cause: Option[Throwable] = None) extends Exception(message, cause.orNull) with ProbeExceptions
case class RpcClientException(message: String = "", cause: Option[Throwable] = None) extends Exception(message, cause.orNull) with ProbeExceptions




// Service interface exceptions (for REST/gRPC → Actor communication)
// Maps to HTTP status codes:
// - ServiceTimeoutException → 504 Gateway Timeout
// - ServiceUnavailableException → 503 Service Unavailable
// - ActorSystemNotReadyException → 503 Service Unavailable

/**
 * Actor ask timeout or Future timeout
 *
 * Thrown when actor doesn't respond within configured timeout (e.g., 25s).
 * REST layer maps this to 504 Gateway Timeout.
 */
case class ServiceTimeoutException(message: String, cause: Throwable) extends Exception(message, cause) with ProbeExceptions {
  def this(message: String) = this(message, null)
}

/**
 * Service temporarily unavailable
 *
 * Thrown when:
 * - Circuit breaker is open (too many failures)
 * - Actor system is down or unreachable
 * - System is overloaded
 *
 * REST layer maps this to 503 Service Unavailable with Retry-After header.
 */
case class ServiceUnavailableException(message: String) extends Exception(message) with ProbeExceptions

/**
 * Actor system not ready (still initializing)
 *
 * Thrown when requests arrive before actor system fully initialized.
 * REST layer maps this to 503 Service Unavailable with short Retry-After (5s).
 */
case class ActorSystemNotReadyException(message: String) extends Exception(message) with ProbeExceptions
