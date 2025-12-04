package io.distia.probe
package core
package pubsub
package models

sealed trait DslException

case class ProducerNotAvailableException(
  message: String = "Producer actor not available - ensure topic is registered before producing events",
  cause: Option[Throwable] = None
) extends RuntimeException(message, cause.orNull) with DslException

case class ConsumerNotAvailableException(
  message: String = "Consumer actor not available - ensure topic is registered before consuming events",
  cause: Option[Throwable] = None
) extends RuntimeException(message, cause.orNull) with DslException

case class KafkaProduceException(
  message: String,
  cause: Option[Throwable] = None
) extends RuntimeException(message, cause.orNull) with DslException

case class DslNotInitializedException(
  message: String = "DSL not initialized - call registerSystem first",
  cause: Option[Throwable] = None
) extends RuntimeException(message, cause.orNull) with DslException

case class ActorNotRegisteredException(
  message: String = "Actor not registered - verify test and topic configuration",
  cause: Option[Throwable] = None
) extends RuntimeException(message, cause.orNull) with DslException

case class SchemaRegistryNotInitializedException(
  message: String = "Schema Registry client not initialized - ensure DSL registerSystem was called",
  cause: Option[Throwable] = None
) extends RuntimeException(message, cause.orNull) with DslException

case class SchemaNotFoundException(
  message: String,
  cause: Option[Throwable] = None
) extends RuntimeException(message, cause.orNull) with DslException