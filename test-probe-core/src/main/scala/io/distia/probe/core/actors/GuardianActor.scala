package io.distia.probe
package core
package actors

import scala.concurrent.duration.*

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior, SupervisorStrategy, Terminated}

import config.CoreConfig
import core.builder.{ActorBehaviorsContext, ServiceFunctionsContext}
import models.*
import models.{GuardianCommands, ServiceResponse}
import models.GuardianCommands.*
import models.QueueCommands


/**
 * GuardianActor - Root Supervisor (Error Kernel)
 *
 * The GuardianActor is the root supervisor of the actor system and implements the Error Kernel pattern.
 * Its primary responsibilities are:
 * 1. Initialize the actor system by spawning QueueActor
 * 2. Provide QueueActor reference to boot service
 * 3. Supervise QueueActor with restart strategy (10 restarts per minute)
 * 4. Protect actor system from fatal exceptions via supervision
 *
 * Design Principles:
 * - Error Kernel: Minimal logic, maximum reliability
 * - Tell-Only: No ask/pipe patterns, pure message passing
 * - Configuration-Driven: Supervision limits from ServiceConfig
 * - Fail-Fast: Bootstrap failures reported immediately
 *
 * State Management:
 * - queueActorRef: Option[ActorRef[QueueCommand]] - Reference to spawned QueueActor
 * - initialized: Boolean - Tracks whether Initialize has been called
 *
 * Message Protocol:
 * - Initialize(replyTo) - Bootstrap actor system, spawn QueueActor
 * - GetQueueActor(replyTo) - Retrieve QueueActor reference
 *
 * Responses:
 * - ActorSystemInitializationSuccess - Initialization succeeded
 * - ActorSystemInitializationFailure(exception) - Initialization failed
 * - QueueActorReference(queueActorRef) - QueueActor reference
 *
 * @see working/GuardianActorImplementationPlan.md for detailed design
 */
private[core] object GuardianActor {

  /**
   * Internal state for GuardianActor
   *
   * @param queueActorRef Reference to spawned QueueActor (None until initialized)
   * @param initialized Whether Initialize has been called
   */
  case class GuardianState(
    queueActorRef: Option[ActorRef[QueueCommands.QueueCommand]],
    initialized: Boolean
  )

  private val InitialState: GuardianState = GuardianState(
    queueActorRef = None,
    initialized = false
  )

  /**
   * Factory method for GuardianActor with optional QueueActor factory injection
   *
   * The queueActorFactory allows mocking QueueActor for testing. In production,
   * it defaults to spawning the real QueueActor.
   *
   * @param coreConfig Configuration for actor system and supervision
   * @param queueActorFactory Optional factory for creating QueueActor (for testing)
   * @return Behavior for GuardianActor
   */
  type QueueActorFactory = (CoreConfig, ServiceFunctionsContext) => Behavior[QueueCommands.QueueCommand]

  def apply(
    coreConfig: CoreConfig,
    serviceFunctions: ServiceFunctionsContext,
    queueActorFactory: Option[QueueActorFactory] = None,
    actorBehaviorsContext: Option[ActorBehaviorsContext] = None
  ): Behavior[GuardianCommands.GuardianCommand] = {
    Behaviors.setup { context =>
      context.log.info("GuardianActor starting - root supervisor initialized")
      
      Behaviors.supervise {
        receiveBehavior(InitialState, coreConfig, serviceFunctions, queueActorFactory, actorBehaviorsContext)
      }.onFailure[Exception](
        SupervisorStrategy.restart
          .withLimit(maxNrOfRetries = 10, withinTimeRange = 1.minute)
          .withLoggingEnabled(true)
      )
    }
  }

  /**
   * Main receive behavior for handling commands and signals
   *
   * @param state Current GuardianState
   * @param coreConfig Core configuration
   * @param serviceFunctions Service functions context (vault, storage)
   * @param queueActorFactory Optional factory for QueueActor creation
   * @return Behavior for next state
   */
  def receiveBehavior(
    state: GuardianState,
    coreConfig: CoreConfig,
    serviceFunctions: ServiceFunctionsContext,
    queueActorFactory: Option[QueueActorFactory],
    actorBehaviorsContext: Option[ActorBehaviorsContext]
  ): Behavior[GuardianCommands.GuardianCommand] = {
    Behaviors.receive[GuardianCommands.GuardianCommand] { (context, message) =>
      message match {
        case Initialize(replyTo) =>
          handleInitialize(context, replyTo, state, coreConfig, serviceFunctions, queueActorFactory, actorBehaviorsContext)

        case GetQueueActor(replyTo) =>
          handleGetQueueActor(context, replyTo, state, coreConfig, serviceFunctions, queueActorFactory)

      }
    }.receiveSignal {
      case (context, Terminated(actorRef)) =>
        handleChildTermination(context, actorRef, state, coreConfig, serviceFunctions, queueActorFactory, actorBehaviorsContext)
    }
  }

  /**
   * Handle Initialize command: Spawn QueueActor and respond with success/failure
   *
   * This command is idempotent - if already initialized, responds with success without
   * spawning a duplicate QueueActor.
   *
   * @param context Actor context
   * @param replyTo Where to send initialization result
   * @param state Current GuardianState
   * @param coreConfig Core configuration
   * @param queueActorFactory Optional factory for QueueActor creation
   * @return Updated behavior with new state
   */
  def handleInitialize(
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[GuardianCommands.GuardianCommand],
    replyTo: ActorRef[ServiceResponse],
    state: GuardianState,
    coreConfig: CoreConfig,
    serviceFunctions: ServiceFunctionsContext,
    queueActorFactory: Option[QueueActorFactory],
    actorBehaviorsContext: Option[ActorBehaviorsContext]
  ): Behavior[GuardianCommands.GuardianCommand] = {

    if state.initialized then
      context.log.warn("GuardianActor already initialized - ignoring duplicate Initialize command")
      replyTo ! ActorSystemInitializationSuccess
      Behaviors.same
    else
      val spawnResult: scala.util.Try[ActorRef[QueueCommands.QueueCommand]] = scala.util.Try {
        context.log.info("Initializing GuardianActor - spawning QueueActor child")

        val queueActorBehavior: Behavior[QueueCommands.QueueCommand] = queueActorFactory match {
          case Some(factory) => factory(coreConfig, serviceFunctions)
          case None => QueueActor(coreConfig, serviceFunctions)
        }

        val queueActor: ActorRef[QueueCommands.QueueCommand] = context.spawn(
          queueActorBehavior,
          "queue-actor"
        )

        context.watch(queueActor)
        context.log.info("QueueActor spawned successfully - GuardianActor initialized")
        queueActor
      }

      spawnResult match {
        case scala.util.Success(queueActor) =>
          val newState: GuardianState = GuardianState(
            queueActorRef = Some(queueActor),
            initialized = true
          )
          replyTo ! ActorSystemInitializationSuccess
          receiveBehavior(newState, coreConfig, serviceFunctions, queueActorFactory, actorBehaviorsContext)

        case scala.util.Failure(ex) =>
          context.log.error("Error during GuardianActor initialization", ex)
          val exception: Exception = ex match {
            case e: Exception => e
            case t: Throwable => new RuntimeException(s"GuardianActor initialization failed: ${t.getMessage}", t)
          }
          replyTo ! ActorSystemInitializationFailure(exception)
          Behaviors.same
      }
  }

  /**
   * Handle GetQueueActor command: Return QueueActor reference or error
   *
   * @param context Actor context
   * @param replyTo Where to send QueueActor reference
   * @param state Current GuardianState
   * @param coreConfig Core configuration
   * @param serviceFunctions Service functions context (vault, storage)
   * @param queueActorFactory Optional factory for QueueActor creation
   * @return Same behavior (state unchanged)
   */
  def handleGetQueueActor(
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[GuardianCommands.GuardianCommand],
    replyTo: ActorRef[ServiceResponse],
    state: GuardianState,
    coreConfig: CoreConfig,
    serviceFunctions: ServiceFunctionsContext,
    queueActorFactory: Option[QueueActorFactory]
  ): Behavior[GuardianCommands.GuardianCommand] = {

    state.queueActorRef match {
      case Some(queueRef) =>
        context.log.debug("Providing QueueActor reference to boot service")
        replyTo ! QueueActorReference(queueRef)
        Behaviors.same

      case None =>
        val ex: IllegalStateException = new IllegalStateException(
          "QueueActor not initialized - call Initialize before GetQueueActor"
        )
        context.log.error("GetQueueActor called before Initialize", ex)
        replyTo ! ActorSystemInitializationFailure(ex)
        Behaviors.same
    }
  }

  /**
   * Handle child termination signal: Log error and update state
   *
   * This handler is called when QueueActor terminates unexpectedly. The supervision
   * strategy will restart the child up to the configured limit (10 restarts per minute).
   * After the limit is exceeded, the child is stopped permanently.
   *
   * @param context Actor context
   * @param actorRef Terminated actor reference
   * @param state Current GuardianState
   * @param coreConfig Core configuration
   * @param serviceFunctions Service functions context (vault, storage)
   * @param queueActorFactory Optional factory for QueueActor creation
   * @return Updated behavior with degraded state
   */
  def handleChildTermination(
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[GuardianCommands.GuardianCommand],
    actorRef: ActorRef[Nothing],
    state: GuardianState,
    coreConfig: CoreConfig,
    serviceFunctions: ServiceFunctionsContext,
    queueActorFactory: Option[QueueActorFactory],
    actorBehaviorsContext: Option[ActorBehaviorsContext]
  ): Behavior[GuardianCommands.GuardianCommand] = {

    if state.queueActorRef.contains(actorRef) then
      context.log.error(
        s"QueueActor terminated unexpectedly - supervision will restart or stop based on limits"
      )

      val newState: GuardianState = state.copy(
        queueActorRef = None,
        initialized = false
      )

      receiveBehavior(newState, coreConfig, serviceFunctions, queueActorFactory, actorBehaviorsContext)
    else
      context.log.warn(s"Unknown actor terminated: $actorRef")
      Behaviors.same
  }
}
