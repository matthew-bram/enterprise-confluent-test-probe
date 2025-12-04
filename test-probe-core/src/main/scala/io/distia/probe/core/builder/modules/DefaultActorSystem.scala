package io.distia.probe
package core
package builder
package modules

import org.apache.pekko.actor.typed.{ActorSystem, Behavior, Scheduler}
import org.apache.pekko.actor.typed.scaladsl.AskPattern.Askable
import org.apache.pekko.util.Timeout
import actors.GuardianActor
import builder.modules.*
import io.distia.probe.core.pubsub.ProbeScalaDsl
import config.CoreConfig
import models.{ActorSystemInitializationFailure, ActorSystemInitializationSuccess, GuardianCommands, QueueActorReference, ServiceResponse}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Companion object for DefaultActorSystem with factory types and defaults
 *
 * Provides type aliases and factory functions for creating GuardianActor behaviors
 * with dependency injection support for testing.
 */
private[core] object DefaultActorSystem {

  /**
   * Type alias for GuardianActor factory function
   *
   * Factory accepts configuration and service dependencies and returns a Behavior
   * for the GuardianActor. Used for dependency injection in tests.
   *
   * @param CoreConfig Core configuration for actor system
   * @param ServiceFunctionsContext Bundle of service functions (vault, storage)
   * @param Option[ActorBehaviorsContext] Optional actor behavior overrides for testing
   * @return Behavior[GuardianCommand] Ready-to-spawn GuardianActor behavior
   */
  type GuardianActorFactory = (
    CoreConfig,
    ServiceFunctionsContext,
    Option[ActorBehaviorsContext]
  ) => Behavior[GuardianCommands.GuardianCommand]

  /**
   * Default production GuardianActor factory
   *
   * Creates standard GuardianActor behavior with provided configuration and services.
   * Used when no custom factory is provided to DefaultActorSystem constructor.
   *
   * @return GuardianActorFactory function that creates production GuardianActor
   */
  def defaultGuardianFactory: GuardianActorFactory =
    (coreConfig, serviceFunctions, behaviors) =>
      GuardianActor(coreConfig, serviceFunctions, None, behaviors)
}

/**
 * Default implementation of ProbeActorSystem module
 *
 * Creates and initializes the Pekko Typed ActorSystem with GuardianActor as the root supervisor.
 * This is the second module in the ServiceDSL builder chain (after ProbeConfig).
 *
 * Key responsibilities:
 * - Creates ActorSystem with validated configuration
 * - Spawns GuardianActor as root supervisor
 * - Initializes GuardianActor and waits for successful initialization
 * - Retrieves QueueActor reference from GuardianActor
 * - Creates curried service interface functions via ServiceInterfaceFunctionsFactory
 * - Registers ActorSystem with ProbeScalaDsl for DSL integration
 *
 * Lifecycle:
 * - preFlight: Validates that Config module has run and required config keys exist
 * - initialize: Creates ActorSystem, spawns GuardianActor, retrieves QueueActor, creates curried functions
 * - finalCheck: Verifies ActorSystem is present in BuilderContext
 *
 * Dependencies (must be initialized before this module):
 * - ProbeConfig module (provides ctx.config and ctx.coreConfig)
 * - ProbeContext module (provides ctx.vaultService and ctx.storageService)
 *
 * Context decoration (adds to BuilderContext):
 * - actorSystem: ActorSystem[GuardianCommand]
 * - queueActorRef: ActorRef[QueueCommand]
 * - curriedFunctions: ServiceInterfaceFunctions (for interface layer)
 * - serviceFunctionsContext: ServiceFunctionsContext (vault + storage functions)
 * - vaultFunctions: VaultServiceFunctions
 * - storageFunctions: StorageServiceFunctions
 *
 * Testability:
 * - guardianFactory parameter allows injection of mock GuardianActor for testing error scenarios
 * - Defaults to production GuardianActor via defaultGuardianFactory when not provided
 * - Test can verify initialization failures by providing factory that returns error responses
 *
 * Example usage in ServiceDSL:
 * {{{
 * ServiceDsl
 *   .newProbeService()
 *   .withConfig(config)
 *   .withContext()
 *   .withActorSystem()  // <- Executes DefaultActorSystem lifecycle
 *   .build()
 * }}}
 *
 * @param guardianFactory Optional factory for GuardianActor (default: DefaultActorSystem.defaultGuardianFactory)
 * @see ServiceDsl for builder integration
 * @see GuardianActor for root supervisor implementation
 * @see ServiceInterfaceFunctionsFactory for curried function creation
 */
class DefaultActorSystem(
  guardianFactory: Option[DefaultActorSystem.GuardianActorFactory] = None
) extends ProbeActorSystem {

  /**
   * Phase 1: Validation (preFlight)
   *
   * Validates that Config module has been initialized and contains required keys.
   * All validation logic is inside the Future block.
   *
   * Validates:
   * - BuilderContext.config is defined
   * - Config contains key: test-probe.core.actor-system.name
   *
   * @param ctx Current BuilderContext (should have config from ProbeConfig module)
   * @param ec ExecutionContext for async operations
   * @return Future containing same context if validation passes
   * @throws IllegalStateException if Config module not initialized or missing required keys
   */
  override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    val resolvedConfig = ctx.config.getOrElse(
      throw new IllegalStateException("Config must be initialized before ActorSystem")
    )
    if !resolvedConfig.hasPath("test-probe.core.actor-system.name") then
      throw new IllegalStateException("Config missing required key: test-probe.core.actor-system.name")
    ctx
  }

  /**
   * Phase 2: Initialization (initialize)
   *
   * Creates ActorSystem, spawns GuardianActor, retrieves QueueActor, and decorates BuilderContext.
   * All initialization logic is inside the Future block.
   *
   * Initialization steps:
   * 1. Validates dependencies (config, coreConfig, vaultService, storageService)
   * 2. Creates VaultServiceFunctions and StorageServiceFunctions from services
   * 3. Creates ServiceFunctionsContext bundle
   * 4. Creates GuardianActor behavior via factory (or default)
   * 5. Spawns ActorSystem with GuardianActor as root
   * 6. Asks GuardianActor to initialize (GuardianCommands.Initialize)
   * 7. Registers ActorSystem with ProbeScalaDsl
   * 8. Asks GuardianActor for QueueActor reference (GuardianCommands.GetQueueActor)
   * 9. Creates curried functions via ServiceInterfaceFunctionsFactory
   * 10. Decorates BuilderContext with all created resources
   *
   * @param ctx Current BuilderContext (validated by preFlight)
   * @param ec ExecutionContext for async operations
   * @return Future containing decorated BuilderContext with ActorSystem, QueueActor, and curried functions
   * @throws IllegalStateException if dependencies not initialized
   * @throws RuntimeException if GuardianActor initialization fails
   */
  override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = {
    val resolvedTypesafeConfig = ctx.config.getOrElse(
      throw new IllegalStateException("Config must be initialized before ActorSystem")
    )
    val resolvedCoreConfig = ctx.coreConfig.getOrElse(
      throw new IllegalStateException("CoreConfig must be initialized before ActorSystem")
    )
    val resolvedVaultService = ctx.vaultService.getOrElse(
      throw new IllegalStateException("VaultService must be initialized before ActorSystem")
    )
    val resolvedStorageService = ctx.storageService.getOrElse(
      throw new IllegalStateException("StorageService must be initialized before ActorSystem")
    )

    val vaultFunctions: VaultServiceFunctions = VaultServiceFunctions.fromService(resolvedVaultService)
    val storageFunctions: StorageServiceFunctions = StorageServiceFunctions.fromService(resolvedStorageService)

    val serviceFunctionsContext: ServiceFunctionsContext = ServiceFunctionsContext(vaultFunctions, storageFunctions)

    val resolvedFactory: DefaultActorSystem.GuardianActorFactory =
      guardianFactory.getOrElse(DefaultActorSystem.defaultGuardianFactory)

    val actorSystemName: String = resolvedCoreConfig.actorSystemName
    val guardianBehavior: Behavior[GuardianCommands.GuardianCommand] =
      resolvedFactory(resolvedCoreConfig, serviceFunctionsContext, ctx.actorBehaviorsContext)
    val system: ActorSystem[GuardianCommands.GuardianCommand] = ActorSystem(guardianBehavior, actorSystemName, resolvedTypesafeConfig)
    implicit val timeout: Timeout = Timeout(resolvedCoreConfig.actorSystemTimeout)
    implicit val scheduler: Scheduler = system.scheduler
    system.ask[ServiceResponse](replyTo => GuardianCommands.Initialize(replyTo)).map {
      case ActorSystemInitializationSuccess =>
        ProbeScalaDsl.registerSystem(system)
        ctx.withActorSystem(system)
      case ActorSystemInitializationFailure(e) =>
        system.terminate()
        throw new RuntimeException(s"Guardian initialization failed: ${e.getMessage}", e)
    }.flatMap { ctx =>
      system.ask[ServiceResponse](replyTo => GuardianCommands.GetQueueActor(replyTo)).map {
        case QueueActorReference(queueActorRef) =>
          val curriedFunctions: ServiceInterfaceFunctions =
            ServiceInterfaceFunctionsFactory(queueActorRef, system)(timeout, ec)

          ctx
            .withQueueActorRef(queueActorRef)
            .withCurriedFunctions(curriedFunctions)
            .withServiceFunctionsContext(serviceFunctionsContext)
            .withVaultFunctions(vaultFunctions)
            .withStorageFunctions(storageFunctions)

        case ActorSystemInitializationFailure(e) =>
          system.terminate()
          throw new RuntimeException(s"Guardian initialization failed: ${e.getMessage}", e)
      }
    }
  }

  /**
   * Phase 3: Final validation (finalCheck)
   *
   * Verifies that ActorSystem and GuardianActor are present in BuilderContext.
   * All validation logic is inside the Future block.
   *
   * Validates:
   * - BuilderContext.actorSystem is defined
   *
   * @param ctx Fully decorated BuilderContext (after all modules initialized)
   * @return Future containing same context if validation passes
   */
  override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.actorSystem.isDefined,
      "ActorSystem not initialized in BuilderContext")
    ctx
  }
}
