package io.distia.probe
package core
package actors

import java.nio.charset.StandardCharsets
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop}
import org.apache.pekko.kafka.ProducerSettings
import org.apache.pekko.kafka.scaladsl.{Producer as PekkoProducer}
import org.apache.pekko.stream.{KillSwitch, KillSwitches, Materializer, OverflowStrategy, QueueOfferResult, SystemMaterializer}
import org.apache.pekko.stream.scaladsl.{Keep, Source}
import org.apache.kafka.clients.producer.{Producer as KafkaProducer, ProducerRecord}
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.ByteArraySerializer

import io.distia.probe.common.models.{KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import io.distia.probe.common.security.SecretRedactor
import io.distia.probe.core.pubsub.ProbeScalaDsl
import io.distia.probe.core.pubsub.models.{ProducedAck, ProducedNack}
import models.KafkaProducerStreamingCommands.*

import org.slf4j.{Logger, LoggerFactory}

private[core] object KafkaProducerStreamingActor {

  /** Thread-safe logger for stream operations (ctx.log is NOT thread-safe in streams) */
  private val streamLogger: Logger = LoggerFactory.getLogger(getClass)

  def apply(testId: UUID,
    topicDirective: TopicDirective,
    securityDirective: KafkaSecurityDirective,
    bootstrapServers: String,
    producerFactory: Option[ProducerSettings[Array[Byte], Array[Byte]] => KafkaProducer[Array[Byte], Array[Byte]]] = None): Behavior[KafkaProducerStreamingCommand] =
    Behaviors.setup { ctx =>
      val safeMessage: String = SecretRedactor.redact(s"[PRODUCER-INIT] KafkaProducerStreamingActor starting for testId=$testId, topic=${topicDirective.topic}")
      ctx.log.info(safeMessage)

      implicit val ec: ExecutionContext = ctx.executionContext
      implicit val mat: Materializer = SystemMaterializer(ctx.system).materializer
      ///TODO: Add config for tokenRefreshInterval under kafka
      val tokenRefreshInterval: FiniteDuration = 5.minutes
      ctx.log.info(s"[PRODUCER-INIT] Materializer created")

      val effectiveBootstrapServers: String = topicDirective.bootstrapServers.getOrElse(bootstrapServers)

      val baseSettings: ProducerSettings[Array[Byte], Array[Byte]] =
        ProducerSettings(ctx.system, new ByteArraySerializer, new ByteArraySerializer)
          .withBootstrapServers(effectiveBootstrapServers)
          .withProperty("client.id", s"producer-${testId}")
          .withProperty("enable.idempotence", "true")
          .withProperty("retries", Int.MaxValue.toString)
          .withProperty("acks", "all")
          .withCloseTimeout(2.seconds)

      val securitySettings: ProducerSettings[Array[Byte], Array[Byte]] = securityDirective.securityProtocol match {
        case SecurityProtocol.SASL_SSL =>
          ctx.log.info(s"[PRODUCER-INIT] Configuring SASL_SSL security")
          baseSettings
            .withProperty("security.protocol", "SASL_SSL")
            .withProperty("sasl.mechanism", "OAUTHBEARER")
            .withProperty("sasl.jaas.config", securityDirective.jaasConfig)
//            .withProperty("sasl.login.callback.handler.class", "com.example.CustomOAuthCallbackHandler")
            .withProperty("sasl.login.refresh.interval.seconds", (tokenRefreshInterval.toSeconds / 2).toString)
        case SecurityProtocol.PLAINTEXT =>
          ctx.log.info(s"[PRODUCER-INIT] Configuring PLAINTEXT (local test mode)")
          baseSettings
            .withProperty("security.protocol", "PLAINTEXT")
      }
      val producerSettings: ProducerSettings[Array[Byte], Array[Byte]] = producerFactory match {
        case Some(factory) =>
          val customProducer = factory(securitySettings)
          securitySettings.withProducer(customProducer)
        case None =>
          securitySettings
      }
      val (queue, killSwitch) = Source
        .queue[ProduceEvent](bufferSize = 100, overflowStrategy = OverflowStrategy.backpressure)
        .viaMat(KillSwitches.single)(Keep.both)
        .map { evt =>
          Try {
            val record = new ProducerRecord[Array[Byte], Array[Byte]](topicDirective.topic, evt.key, evt.value)
            evt.headers.foreach {
              case (k, v) => record.headers().add(new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8)))
            }
            streamLogger.debug("Created ProducerRecord for topic {} with {} headers", topicDirective.topic, evt.headers.size.asInstanceOf[AnyRef])
            record
          }.recover {
            case ex: Exception =>
              streamLogger.error("Failed to create ProducerRecord for topic {}: {}", topicDirective.topic, ex.getMessage)
              throw ex
          }.get
        }
        .recover {
          case ex: Exception =>
            streamLogger.error("Stream processing error for topic {}: {}", topicDirective.topic, ex.getMessage, ex)
            throw ex
        }
        .to(PekkoProducer.plainSink(producerSettings))
        .run()

      ctx.log.info(s"[PRODUCER-INIT] Queue-based stream initialized successfully")

      val behavior: Behavior[KafkaProducerStreamingCommand] = Behaviors.receiveMessage[KafkaProducerStreamingCommand] {
        case event: ProduceEvent =>
          queue.offer(event).onComplete {
            case Success(QueueOfferResult.Enqueued) =>
              streamLogger.debug("Event enqueued for topic {}", topicDirective.topic)
              event.replyTo ! ProducedAck
            case Success(QueueOfferResult.Dropped) =>
              streamLogger.warn("Queue full (backpressure) for topic {} - event dropped", topicDirective.topic)
              event.replyTo ! ProducedNack(new Exception(s"Queue full (backpressure) for topic ${topicDirective.topic}"))
            case Success(other) =>
              streamLogger.warn("Queue offer failed for topic {}: {}", topicDirective.topic, other)
              event.replyTo ! ProducedNack(new Exception(s"Queue offer failed for topic ${topicDirective.topic}: $other"))
            case Failure(ex) =>
              streamLogger.error("Queue offer exception for topic {}: {}", topicDirective.topic, ex.getMessage)
              event.replyTo ! ProducedNack(ex)
          }
          Behaviors.same
      }
      .receiveSignal {
        case (_, PostStop) =>
          queue.complete()
          killSwitch.shutdown()
          Behaviors.same
      }
      behavior
    }
}
