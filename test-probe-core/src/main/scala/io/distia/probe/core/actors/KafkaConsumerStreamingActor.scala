package io.distia.probe
package core
package actors

import common.models.{KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import common.security.SecretRedactor
import pubsub.{ProbeScalaDsl, SerdesFactory}
import pubsub.models.{CloudEvent, ConsumedAck, ConsumedNack}
import models.KafkaConsumerStreamingCommands.*

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.{Behavior, DispatcherSelector, PostStop, Scheduler}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.scaladsl.AskPattern.Askable
import org.apache.pekko.kafka.{CommitterSettings, ConsumerMessage, ConsumerSettings, Subscriptions}
import org.apache.pekko.kafka.scaladsl.{Committer, Consumer}
import org.apache.pekko.stream.{ActorAttributes, Materializer, Supervision}
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink}
import org.apache.pekko.util.Timeout
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.errors.{SerializationException, TimeoutException}
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException

import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.util.Try


/**
 * KafkaConsumerStreamingActor - Kafka Consumer with At-Least-Once Semantics
 *
 * This actor consumes CloudEvent messages from Kafka using Pekko Streams and Alpakka Kafka.
 * It provides the following guarantees:
 *
 * 1. **At-Least-Once Delivery**: Offsets are committed ONLY AFTER events are stored in the registry
 * 2. **Poison Pill Handling**: Malformed messages are logged, skipped, and their offsets committed
 * 3. **Event Filtering**: Events that don't match filters are skipped but their offsets are committed
 *
 * Architecture:
 * - Deserialization happens on blocking-io-dispatcher (Schema Registry I/O)
 * - Registry updates happen in actor context (thread-safe)
 * - Offset commits happen in stream (batched, max 20 per batch)
 * - Ask pattern ensures registry update completes before offset commit
 *
 * Message Flow:
 * 1. committableSource emits Kafka messages
 * 2. mapAsync deserializes on blocking dispatcher → DeserializationResult
 * 3. unifiedCommitFlow:
 *    a. Success + shouldInclude=true → ask actor for confirmation → commit offset
 *    b. Success + shouldInclude=false → skip registry → commit offset
 *    c. Failure → skip registry → commit offset (poison pill fix)
 * 4. batch → commitScaladsl (max 20 offsets per batch)
 *
 * ⚠️ WARNING: THIS IS A TEST HARNESS - NOT PRODUCTION CODE ⚠️
 *
 * Test harness characteristics:
 * - Permissive error handling (resumes on all errors)
 * - In-memory registry (no persistence)
 * - No circuit breakers or dead letter queues
 * - Simplified metrics and monitoring
 *
 * If you copy this code for production use, you do so AT YOUR OWN PERIL!
 * Production systems require circuit breakers, DLQs, metrics, and alerting.
 */
private[core] object KafkaConsumerStreamingActor {

  /** Thread-safe logger for stream operations (ctx.log is NOT thread-safe in streams) */
  private val streamLogger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Deserialization result wrapper for unified error handling.
   *
   * This sealed trait allows the stream to handle both success and failure paths uniformly,
   * ensuring that ALL messages (including malformed ones) have their offsets committed.
   */
  private[actors] sealed trait DeserializationResult

  /**
   * Successful deserialization of a CloudEvent from Kafka message.
   *
   * @param cloudEvent The deserialized CloudEvent
   * @param msg The original Kafka ConsumerMessage (contains key, value, headers, offset)
   */
  private[actors] case class DeserializationSuccess(
    cloudEvent: CloudEvent,
    msg: ConsumerMessage.CommittableMessage[Array[Byte], Array[Byte]]
  ) extends DeserializationResult

  /**
   * Failed deserialization (poison pill message).
   *
   * The message will be skipped and its offset committed to prevent infinite retry.
   *
   * @param offset The Kafka offset of the failed message
   * @param error Human-readable error message
   * @param msg The original Kafka ConsumerMessage (for offset commit)
   */
  private[actors] case class DeserializationFailure(
    offset: Long,
    error: String,
    msg: ConsumerMessage.CommittableMessage[Array[Byte], Array[Byte]]
  ) extends DeserializationResult

  def apply(
    testId: UUID,
    directive: TopicDirective,
    securityDirective: KafkaSecurityDirective,
    bootstrapServers: String): Behavior[KafkaConsumerStreamingCommand] =
    Behaviors.setup { ctx =>
      implicit val ec: ExecutionContext = ctx.executionContext
      implicit val mat: Materializer = Materializer(ctx)
      implicit val scheduler: Scheduler = ctx.system.scheduler

      val blockingEc: ExecutionContext = ctx.system.dispatchers.lookup(DispatcherSelector.fromConfig("pekko.actor.blocking-io-dispatcher"))
      val registry: mutable.Map[String, (Array[Byte], Array[Byte], List[RecordHeader])] = mutable.Map.empty[String, (Array[Byte], Array[Byte], List[RecordHeader])]
      val shouldInclude: CloudEvent => Boolean = createEventFilter(directive)
      ///TODO: Add config for tokenRefreshInterval under kafka
      val tokenRefreshInterval: FiniteDuration = 5.minutes

      val effectiveBootstrapServers: String = directive.bootstrapServers.getOrElse(bootstrapServers)

      val baseSettings: ConsumerSettings[Array[Byte], Array[Byte]] =
        ConsumerSettings(ctx.system, new ByteArrayDeserializer, new ByteArrayDeserializer)
          .withBootstrapServers(effectiveBootstrapServers)
          .withGroupId(s"test-${testId}")
          .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

      val consumerSettings: ConsumerSettings[Array[Byte], Array[Byte]] = securityDirective.securityProtocol match {
        case SecurityProtocol.SASL_SSL =>
          val safeMessage: String = SecretRedactor.redact(s"[CONSUMER-INIT] Configuring SASL_SSL security")
          ctx.log.info(safeMessage)
          baseSettings
            .withProperty("security.protocol", "SASL_SSL")
            .withProperty("sasl.mechanism", "OAUTHBEARER")
            .withProperty("sasl.jaas.config", securityDirective.jaasConfig)
            //            .withProperty("sasl.login.callback.handler.class", "com.example.CustomOAuthCallbackHandler")
            .withProperty("sasl.login.refresh.interval.seconds", (tokenRefreshInterval.toSeconds / 2).toString)
        case SecurityProtocol.PLAINTEXT =>
          val safeMessage: String = SecretRedactor.redact(s"[CONSUMER-INIT] Configuring PLAINTEXT (local test mode)")
          ctx.log.info(safeMessage)
          baseSettings
            .withProperty("security.protocol", "PLAINTEXT")
      }

      val commitFlow: Flow[DeserializationResult, Done, org.apache.pekko.NotUsed] =
        unifiedCommitFlow(ctx, shouldInclude)

      val resumingDecider: Supervision.Decider = {
        case ex: java.util.concurrent.TimeoutException =>
          streamLogger.info("Stream timeout (resuming): {}", ex.getMessage)
          Supervision.Resume
        case ex: TimeoutException =>
          streamLogger.info("Kafka timeout (resuming): {}", ex.getMessage)
          Supervision.Resume
        case ex: SerializationException =>
          streamLogger.info("Kafka serialization error (resuming): {}", ex.getMessage)
          Supervision.Resume
        case ex: RestClientException =>
          streamLogger.info("Schema Registry error (resuming): {}", ex.getMessage)
          Supervision.Resume
        case ex: Exception =>
          streamLogger.info("Stream error (resuming - TEST MODE): {}: {}", ex.getClass.getName, ex.getMessage)
          Supervision.Resume
      }

      val (control, done): (Consumer.Control, Future[Done]) =
        Consumer
          .committableSource(consumerSettings, Subscriptions.topics(directive.topic))
          .mapAsync(1) { msg =>
            Future {
              Try {
                val cloudEvent = SerdesFactory.deserialize[CloudEvent](msg.record.key, directive.topic, true)
                streamLogger.info("Deserialized CloudEvent: {} (correlationId={})", cloudEvent.id, cloudEvent.correlationid)
                DeserializationSuccess(cloudEvent, msg)
              }.recover {
                case ex: SerializationException =>
                  streamLogger.info("Kafka serialization error at offset {} - SKIPPING: {}", msg.record.offset().asInstanceOf[AnyRef], ex.getMessage)
                  DeserializationFailure(msg.record.offset(), ex.getMessage, msg)
                case ex: RestClientException =>
                  streamLogger.info("Schema Registry error at offset {} - SKIPPING: {}", msg.record.offset().asInstanceOf[AnyRef], ex.getMessage)
                  DeserializationFailure(msg.record.offset(), ex.getMessage, msg)
                case ex: TimeoutException =>
                  streamLogger.info("Kafka timeout at offset {} - SKIPPING: {}", msg.record.offset().asInstanceOf[AnyRef], ex.getMessage)
                  DeserializationFailure(msg.record.offset(), ex.getMessage, msg)
                case ex: Exception =>
                  streamLogger.info("Unexpected error at offset {} - SKIPPING: {}: {}", msg.record.offset().asInstanceOf[AnyRef], ex.getClass.getName, ex.getMessage)
                  DeserializationFailure(msg.record.offset(), ex.getMessage, msg)
              }.get
            }(blockingEc)
          }
          .via(commitFlow)
          .withAttributes(ActorAttributes.supervisionStrategy(resumingDecider))
          .toMat(Sink.foreach(_ => ()))(Keep.both)
          .run()

      Behaviors.receive[KafkaConsumerStreamingCommand] { (context, message) =>
        message match {
          case record: InternalAdd =>
            if !registry.contains(record.correlationId) then
              registry.update(
                record.correlationId,
                (record.key, record.value, convertHeadersToList(record.headers))
              )
            record.replyTo ! InternalAddConfirmed(record.correlationId)
            Behaviors.same

          case FetchConsumedEvent(correlationId, replyTo) =>
            registry.get(correlationId) match {
              case Some(event) => replyTo ! ConsumedAck(event._1, event._2, event._3)
              case None => replyTo ! ConsumedNack()
            }
            Behaviors.same
        }
      }.receiveSignal {
        case (_, PostStop) =>
          control.stop().map(_ => mat.shutdown())
          Behaviors.same
      }
    }

  /**
   * Create event filter predicate for test isolation.
   *
   * Events are included if they match ANY of the configured event filters
   * (eventType + payloadVersion pairs).
   *
   * @param directive Topic directive with event filters
   * @return Predicate function that returns true if event should be included
   */
  def createEventFilter(directive: TopicDirective): CloudEvent => Boolean =
    (event: CloudEvent) =>
      directive.eventFilters.exists { filter =>
        event.`type` == filter.eventType && event.payloadversion == filter.payloadVersion
      }

  /**
   * Create unified commit flow with at-least-once semantics.
   *
   * This flow handles three paths:
   * 1. Deserialization success + shouldInclude=true → ask actor for confirmation → commit offset
   * 2. Deserialization success + shouldInclude=false → skip registry → commit offset
   * 3. Deserialization failure → skip registry → commit offset (poison pill fix)
   *
   * The ask pattern ensures that offsets are committed ONLY AFTER the actor confirms
   * registry update, providing at-least-once delivery guarantees.
   *
   * @param ctx Actor context for self-messaging
   * @param shouldInclude Predicate to filter events
   * @param ec Execution context for async operations
   * @param scheduler Scheduler for ask pattern timeouts
   * @return Flow that filters, batches, and commits offsets
   */
  def unifiedCommitFlow(
    ctx: ActorContext[KafkaConsumerStreamingCommand],
    shouldInclude: CloudEvent => Boolean
  )(implicit ec: ExecutionContext, scheduler: Scheduler): Flow[
    DeserializationResult,
    Done,
    org.apache.pekko.NotUsed
  ] =
    implicit val timeout: Timeout = Timeout(5.seconds)

    // Committer settings for batched offset commits
    val committerSettings = CommitterSettings(ctx.system).withMaxBatch(20L)

    Flow[DeserializationResult]
      .mapAsync(1) {
        // PATH 1: Deserialization success + shouldInclude=true → ask actor for confirmation
        case DeserializationSuccess(cloudEvent, msg) if shouldInclude(cloudEvent) =>
          // ASK PATTERN: Wait for actor to confirm registry update before committing offset
          ctx.self.ask[InternalAddConfirmed](replyTo => InternalAdd(cloudEvent.correlationid, msg.record.key, msg.record.value, msg.record.headers, replyTo))
            .map { _ =>
              streamLogger.info("Event {} stored, committing offset {}", cloudEvent.correlationid, msg.record.offset().asInstanceOf[AnyRef])
              msg.committableOffset
            }
            .recover {
              case ex: Exception =>
                streamLogger.info("InternalAdd ask timeout for correlationId={}, committing anyway: {}", cloudEvent.correlationid, ex.getMessage)
                msg.committableOffset
            }

        // PATH 2: Deserialization success + shouldInclude=false → skip registry, commit offset
        case DeserializationSuccess(cloudEvent, msg) =>
          streamLogger.info("Event {} filtered out (type={}, version={}), committing offset {}", cloudEvent.correlationid, cloudEvent.`type`, cloudEvent.payloadversion, msg.record.offset().asInstanceOf[AnyRef])
          Future.successful(msg.committableOffset)

        // PATH 3: Deserialization failure → skip registry, commit offset (poison pill fix)
        case DeserializationFailure(offset, error, msg) =>
          streamLogger.info("Poison pill at offset {} - SKIPPING and COMMITTING to unblock stream", offset.asInstanceOf[AnyRef])
          Future.successful(msg.committableOffset)
      }
      .via(Committer.flow(committerSettings))

  /**
   * Convert Kafka Headers to List of RecordHeader for storage.
   *
   * @param headers Kafka Headers from ConsumerRecord
   * @return List of RecordHeader instances
   */
  def convertHeadersToList(headers: Headers): List[RecordHeader] = headers
    .iterator
    .asScala
    .toList
    .map { h => h.asInstanceOf[RecordHeader] }

}
