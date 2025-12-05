/*
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║ ⚠️  SECURITY WARNING - SENSITIVE DATA HANDLING                       ║
 * ╠══════════════════════════════════════════════════════════════════════╣
 * ║ This actor handles KafkaSecurityDirective containing:                     ║
 * ║ - OAuth client IDs and secrets                                       ║
 * ║ - Vault credentials                                                  ║
 * ║ - Kafka authentication tokens                                        ║
 * ║                                                                      ║
 * ║ ⚠️  DO NOT LOG KafkaSecurityDirective OR ANY DERIVED CREDENTIALS          ║
 * ║                                                                      ║
 * ║ All logging must:                                                    ║
 * ║ 1. Exclude KafkaSecurityDirective objects from log statements            ║
 * ║ 2. Redact credentials in error messages                             ║
 * ║ 3. Use testId for correlation, never credentials                    ║
 * ║                                                                      ║
 * ║ Future: Security agent will enforce compliance                      ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
package io.distia.probe
package core
package actors

import java.io.IOException
import java.util.UUID

import scala.concurrent.duration.*
import scala.util.{Try, Success, Failure}

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior, SupervisorStrategy}

import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective, TopicDirective}
import com.typesafe.config.ConfigException

import models.*
import models.KafkaConsumerCommands.*
import models.KafkaConsumerStreamingCommands.*
import models.TestExecutionCommands.*
import pubsub.ProbeScalaDsl

/**
 * KafkaConsumerActor - Kafka Consumer Supervisor
 *
 * Responsibilities:
 * 1. Initialize: Spawn KafkaConsumerStreamingActor per consumer topic
 * 2. Supervise: Resume strategy for streaming actor failures
 * 3. Register: Add streaming actors to ProbeScalaDsl for Cucumber access
 * 4. Stop: Coordinated shutdown and DSL cleanup
 *
 * Architecture:
 * - Spawns one KafkaConsumerStreamingActor per consumer topic
 * - Matches topics from BlockStorageDirective with KafkaSecurityDirective credentials
 * - Registers actors with ProbeScalaDsl for DSL-based event consumption
 * - Watches spawned actors for termination
 *
 * Message Protocol:
 * - Receives: Initialize(blockStorageDirective, securityDirectives), StartTest, Stop
 * - Sends to parent TEA: ChildGoodToGo
 *
 * State: Implicit (Created → Initializing → Ready → Stopped)
 *
 * SECURITY: This actor handles sensitive KafkaSecurityDirective data
 * All credentials must be redacted in logs
 */
private[core] object KafkaConsumerActor {

  /**
   * Factory method for KafkaConsumerActor
   *
   * @param testId UUID of the test
   * @param parentTea Reference to parent TestExecutionActor
   * @return Behavior for KafkaConsumerCommand
   */
  def apply(
    testId: UUID,
    parentTea: ActorRef[TestExecutionCommand]
  ): Behavior[KafkaConsumerCommand] = {
    Behaviors.setup { context =>
      context.log.info(s"KafkaConsumerActor starting for test $testId")
      activeBehavior(testId, initialized = false, Map.empty, parentTea, context)
    }
  }

  /**
   * Active behavior - handles all commands
   *
   * @param testId UUID of the test
   * @param initialized Whether Initialize has been called
   * @param streamingActors Map of topic -> streaming actor ref
   * @param parentTea Reference to parent TestExecutionActor
   * @param context Actor context from setup
   * @return Behavior for next state
   */
  def activeBehavior(
    testId: UUID,
    initialized: Boolean,
    streamingActors: Map[String, ActorRef[KafkaConsumerStreamingCommand]],
    parentTea: ActorRef[TestExecutionCommand],
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[KafkaConsumerCommand]
  ): Behavior[KafkaConsumerCommand] = {
    Behaviors.receiveMessage {
      case Initialize(blockStorageDirective, securityDirectives) =>
        handleInitialize(testId, blockStorageDirective, securityDirectives, initialized, parentTea, context)

      case StartTest =>
        handleStartTest(testId, initialized, streamingActors, context)

      case Stop =>
        handleStop(testId, streamingActors, context)
    }
  }

  /**
   * Validate and retrieve Kafka bootstrap servers from configuration
   *
   * @param config Typesafe config
   * @param context Actor context for logging
   * @return Bootstrap servers string if valid
   * @throws KafkaConsumerException if config is missing, empty, or invalid
   */
  def validateBootstrapServers(
    config: com.typesafe.config.Config,
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[KafkaConsumerCommand]
  ): String = {
    val path = "test-probe.core.kafka.bootstrap-servers"

    Try(config.getString(path)) match {
      case Success(value) if value.trim.isEmpty =>
        val msg = s"Kafka bootstrap servers cannot be empty. Set '$path' to a non-empty value (e.g., 'localhost:9092') or set KAFKA_BOOTSTRAP_SERVERS environment variable"
        context.log.error(msg)
        throw KafkaConsumerException(message = msg)

      case Success(value) =>
        context.log.debug(s"Kafka bootstrap servers configured: ${value.split(',').length} server(s)")
        value

      case Failure(ex: ConfigException.Missing) =>
        val msg = s"Required configuration '$path' is missing. Add to reference.conf or set KAFKA_BOOTSTRAP_SERVERS environment variable"
        context.log.error(msg, ex)
        throw KafkaConsumerException(message = msg, cause = Some(ex))

      case Failure(ex) =>
        val msg = s"Invalid configuration format for '$path': ${ex.getMessage}"
        context.log.error(msg, ex)
        throw KafkaConsumerException(message = msg, cause = Some(ex))
    }
  }

  /**
   * Handle Initialize command: Spawn streaming actors for each consumer topic
   *
   * @param testId UUID of the test
   * @param blockStorageDirective Directive containing test location and topics
   * @param securityDirectives Security credentials for Kafka
   * @param initialized Whether already initialized
   * @param parentTea Reference to parent TestExecutionActor
   * @param context Actor context
   * @return Updated behavior with spawned streaming actors
   */
  def handleInitialize(
    testId: UUID,
    blockStorageDirective: BlockStorageDirective,
    securityDirectives: List[KafkaSecurityDirective],
    initialized: Boolean,
    parentTea: ActorRef[TestExecutionCommand],
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[KafkaConsumerCommand]
  ): Behavior[KafkaConsumerCommand] = {
    // Validate configuration first (fail-fast)
    val bootstrapServers: String = validateBootstrapServers(context.system.settings.config, context)

    // Filter and match consumer topics with security directives
    val consumerTopics = blockStorageDirective.topicDirectives.filter(_.role == "consumer")
    val topicSecurityPairs = consumerTopics.flatMap { topicDir =>
      securityDirectives.find(sec => sec.topic == topicDir.topic && sec.role == "consumer")
        .map(sec => (topicDir, sec))
    }

    // Spawn streaming actors
    val initResult: Try[Map[String, ActorRef[KafkaConsumerStreamingCommand]]] = Try {
      topicSecurityPairs.map { case (topicDir, secDir) =>
        val streamingActor: ActorRef[KafkaConsumerStreamingCommand] = context.spawn(
          Behaviors
            .supervise(
              Behaviors
                .supervise(
                  Behaviors
                    .supervise(KafkaConsumerStreamingActor(testId, topicDir, secDir, bootstrapServers))
                    .onFailure[IllegalArgumentException](SupervisorStrategy.restart)
                )
                .onFailure[IOException](SupervisorStrategy.restart.withLimit(3, 1.minute))
            )
            .onFailure[VirtualMachineError](SupervisorStrategy.stop),
          s"kafka-consumer-streaming-${topicDir.topic}-$testId"
        )

        context.watch(streamingActor)
        ProbeScalaDsl.registerConsumerActor(testId, topicDir.topic, streamingActor)

        topicDir.topic -> streamingActor
      }.toMap
    }

    initResult match {
      case Success(streamingActors) =>
        // ⚠️  SECURITY: DO NOT LOG the KafkaSecurityDirective objects
        context.log.info(s"Spawned ${streamingActors.size} Kafka consumer streaming actors for test $testId (credentials REDACTED)")
        parentTea ! ChildGoodToGo(testId, context.self)
        activeBehavior(testId, initialized = true, streamingActors, parentTea, context)

      case Failure(ex) =>
        context.log.error(s"Kafka consumer initialization failed for test $testId", ex)
        throw KafkaConsumerException(
          message = s"Failed to initialize Kafka consumers for test $testId: ${ex.getMessage}",
          cause = Some(ex)
        )
    }
  }

  /**
   * Handle StartTest command: No-op (consumers accessed via DSL by Cucumber scenarios)
   *
   * @param testId UUID of the test
   * @param initialized Whether Initialize was called
   * @param streamingActors Map of topic -> streaming actor ref
   * @param context Actor context
   * @return Updated behavior
   */
  def handleStartTest(
    testId: UUID,
    initialized: Boolean,
    streamingActors: Map[String, ActorRef[KafkaConsumerStreamingCommand]],
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[KafkaConsumerCommand]
  ): Behavior[KafkaConsumerCommand] = {
    if !initialized then
      val ex: IllegalStateException = new IllegalStateException(
        s"KafkaConsumerActor not initialized for test $testId - call Initialize before StartTest"
      )
      context.log.error("StartTest called before Initialize", ex)
      throw ex

    context.log.debug(s"StartTest received for KafkaConsumerActor with ${streamingActors.size} streaming actors")

    Behaviors.same
  }

  /**
   * Handle Stop command: Coordinated shutdown of streaming actors
   *
   * @param testId UUID of the test
   * @param streamingActors Map of topic -> streaming actor ref
   * @param context Actor context
   * @return Stopped behavior
   */
  def handleStop(
    testId: UUID,
    streamingActors: Map[String, ActorRef[KafkaConsumerStreamingCommand]],
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[KafkaConsumerCommand]
  ): Behavior[KafkaConsumerCommand] = {
    context.log.info(s"Stopping KafkaConsumerActor for test $testId")

    streamingActors.foreach { case (topic, _) =>
      ProbeScalaDsl.unRegisterConsumerActor(testId, topic)
    }

    streamingActors.values.foreach(context.stop)

    Behaviors.stopped
  }
}
