package io.distia.probe
package core
package pubsub

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.reflect.ClassTag

import org.apache.pekko.actor.typed.scaladsl.AskPattern.Askable
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, DispatcherSelector, Scheduler}
import org.apache.pekko.util.Timeout
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.SchemaProvider

import core.models.KafkaConsumerStreamingCommands.*
import core.models.KafkaProducerStreamingCommands.*
import core.models.*
import models.*

object ProbeScalaDsl {

  implicit val cloudEventEncoder: Encoder[CloudEvent] = deriveEncoder[CloudEvent]

  @volatile private var systemOpt: Option[ActorSystem[_]] = None
  @volatile private var askTimeoutDuration: FiniteDuration = 3.seconds

  private val producers: ConcurrentHashMap[(UUID, String), ActorRef[KafkaProducerStreamingCommand]] =
    new ConcurrentHashMap[(UUID, String), ActorRef[KafkaProducerStreamingCommand]]()

  private val consumers: ConcurrentHashMap[(UUID, String), ActorRef[KafkaConsumerStreamingCommand]] =
    new ConcurrentHashMap[(UUID, String), ActorRef[KafkaConsumerStreamingCommand]]()

  private[core] def registerSystem[T](system: ActorSystem[T]): Unit =
    systemOpt = Some(system)
    askTimeoutDuration = system.settings.config.getDuration("test-probe.core.dsl.ask-timeout").toMillis.millis
    val schemaRegistryUri: String = system.settings.config.getString("test-probe.core.kafka.schema-registry-url")
    val schemaProviders: java.util.List[SchemaProvider] = java.util.Arrays.asList[SchemaProvider](
      new AvroSchemaProvider(),
      new ProtobufSchemaProvider(),
      new JsonSchemaProvider()
    )
    SerdesFactory.setClient(new CachedSchemaRegistryClient(schemaRegistryUri, 100, schemaProviders, null), schemaRegistryUri)

  private[core] def clearSystem(): Unit = systemOpt = None

  private[core] def registerProducerActor(testId: UUID, topic: String, producer: ActorRef[KafkaProducerStreamingCommand]): Unit =
    producers.put((testId, topic), producer)

  private[core] def registerConsumerActor(testId: UUID, topic: String, consumer: ActorRef[KafkaConsumerStreamingCommand]): Unit =
    consumers.put((testId, topic), consumer)

  private[core] def unRegisterProducerActor(testId: UUID, topic: String): Unit =
    producers.remove((testId, topic))

  private[core] def unRegisterConsumerActor(testId: UUID, topic: String): Unit =
    consumers.remove((testId, topic))

  def produceEvent[T: ClassTag](testId: UUID, topic: String, key: CloudEvent, value: T, headers: Map[String, String]): Future[ProduceResult] =
    implicit val actorSystem: ActorSystem[_] = systemOpt.getOrElse(throw DslNotInitializedException())
    implicit val timeout: Timeout = Timeout(askTimeoutDuration)
    implicit val scheduler: Scheduler = actorSystem.scheduler
    val actorEc: ExecutionContext = actorSystem.executionContext
    val blockingEc: ExecutionContext = actorSystem.dispatchers.lookup(DispatcherSelector.fromConfig("pekko.actor.blocking-io-dispatcher"))
    Option(producers.get((testId, topic))) match {
      case Some(producer) => Future {
        val keyBytes: Array[Byte] = SerdesFactory.serialize[CloudEvent](key, topic, true)
        val valueBytes: Array[Byte] = SerdesFactory.serialize[T](value, topic, false)
        (producer, keyBytes, valueBytes)
      }(blockingEc)
        .flatMap { (producer, keyBytes, valueBytes) =>
          sendEventToKafka(keyBytes, valueBytes, headers, producer)(actorEc)
        }(actorEc)
        .recoverWith {
          case ex: DslException => Future.failed(ex)
          case ex: Exception => Future.failed(
            KafkaProduceException(
              s"Failed to produce event for test $testId, topic '$topic': ${ex.getMessage}",
              Some(ex)
            )
          )
        }(actorEc)
      case None => throw ActorNotRegisteredException(s"No producer registered for test $testId, topic $topic")
    }

  def produceEventBlocking[T: ClassTag](testId: UUID, topic: String, key: CloudEvent, value: T, headers: Map[String, String]): ProduceResult =
    Await.result(produceEvent(testId, topic, key, value, headers), 5.seconds)

  def fetchConsumedEvent[T: ClassTag](testId: UUID, topic: String, correlationId: String): Future[ConsumedResult] =
    implicit val actorSystem: ActorSystem[_] = systemOpt.getOrElse(throw DslNotInitializedException())
    implicit val timeout: Timeout = Timeout(askTimeoutDuration)
    implicit val scheduler: Scheduler = actorSystem.scheduler
    val actorEc: ExecutionContext = actorSystem.executionContext
    val blockingEc: ExecutionContext = actorSystem.dispatchers.lookup(DispatcherSelector.fromConfig("pekko.actor.blocking-io-dispatcher"))
    Option(consumers.get((testId, topic))) match {
      case Some(consumer) => fetchFromKafka(correlationId, consumer)(actorEc)
        .flatMap {
          case ack: ConsumedAck => Future {
            val key: CloudEvent = SerdesFactory.deserialize[CloudEvent](ack.key, topic, true)
            val value: T = SerdesFactory.deserialize[T](ack.value, topic, false)
            ConsumedSuccess(key, value, ack.headers.map { h => (h.key, new String(h.value, StandardCharsets.UTF_8)) }.toMap)
          }(blockingEc)
          case ConsumedNack(status) => Future.failed(
            ConsumerNotAvailableException(
              s"Event not found for correlation ID '$correlationId' on topic '$topic'"
            )
          )
        }(blockingEc)
        .recoverWith {
          case ex: DslException => Future.failed(ex)
          case ex: Exception => Future.failed(
            ConsumerNotAvailableException(
              s"Failed to consume event for test $testId, topic '$topic': ${ex.getMessage}",
              Some(ex)
            )
          )
        }(actorEc)
      case None => throw ActorNotRegisteredException(s"No consumer registered for test $testId, topic $topic")
    }

  def fetchConsumedEventBlocking[T: ClassTag](testId: UUID, topic: String, correlationId: String): ConsumedResult =
    Await.result(fetchConsumedEvent(testId, topic, correlationId), 5.seconds)

  private[pubsub] def fetchFromKafka(correlationId: String, consumer: ActorRef[KafkaConsumerStreamingCommand])
    (implicit ec: ExecutionContext, timeout: Timeout, scheduler: Scheduler): Future[ConsumedResult] =
    consumer.ask[ConsumedResult](replyTo => FetchConsumedEvent(correlationId, replyTo))

  private[pubsub] def sendEventToKafka(
    key: Array[Byte],
    value: Array[Byte],
    headers: Map[String, String],
    producer: ActorRef[KafkaProducerStreamingCommand])
    (implicit ec: ExecutionContext, timeout: Timeout, scheduler: Scheduler): Future[ProduceResult] =
    producer.ask[ProduceResult](replyTo => ProduceEvent(key, value, headers, replyTo)).map {
      case ProducedAck => ProducingSuccess()
      case ProducedNack(ex) => throw KafkaProduceException(s"Kafka producer failed: ${ex.getMessage}", Some(ex))
      case unexpected => throw new IllegalStateException(s"Unexpected ProduceResult: $unexpected")
    }

}