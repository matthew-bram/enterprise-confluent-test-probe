package io.distia.probe
package core

package object pubsub {

  type CloudEvent = models.CloudEvent
  val CloudEvent: models.CloudEvent.type = models.CloudEvent

  type ProducingSuccess = models.ProducingSuccess
  val ProducingSuccess: models.ProducingSuccess.type = models.ProducingSuccess

  type ConsumedSuccess[T] = models.ConsumedSuccess[T]
  val ConsumedSuccess: models.ConsumedSuccess.type = models.ConsumedSuccess

  type DslException = models.DslException

  type ProducerNotAvailableException = models.ProducerNotAvailableException
  val ProducerNotAvailableException: models.ProducerNotAvailableException.type = models.ProducerNotAvailableException

  type ConsumerNotAvailableException = models.ConsumerNotAvailableException
  val ConsumerNotAvailableException: models.ConsumerNotAvailableException.type = models.ConsumerNotAvailableException

  type KafkaProduceException = models.KafkaProduceException
  val KafkaProduceException: models.KafkaProduceException.type = models.KafkaProduceException

  type DslNotInitializedException = models.DslNotInitializedException
  val DslNotInitializedException: models.DslNotInitializedException.type = models.DslNotInitializedException

  type ActorNotRegisteredException = models.ActorNotRegisteredException
  val ActorNotRegisteredException: models.ActorNotRegisteredException.type = models.ActorNotRegisteredException

  type SchemaRegistryNotInitializedException = models.SchemaRegistryNotInitializedException
  val SchemaRegistryNotInitializedException: models.SchemaRegistryNotInitializedException.type = models.SchemaRegistryNotInitializedException

  type SchemaNotFoundException = models.SchemaNotFoundException
  val SchemaNotFoundException: models.SchemaNotFoundException.type = models.SchemaNotFoundException

  val ScalaDsl: ProbeScalaDsl.type = ProbeScalaDsl

}
