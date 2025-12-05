package io.distia.probe
package core
package builder

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import config.CoreConfig
import models.{GuardianCommands, QueueCommands}
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

/**
 * BuilderContext - Mutable State Container for ServiceDsl Builder Pattern
 *
 * The BuilderContext accumulates configuration and dependencies as modules are added
 * to the ServiceDsl builder. Each `with*` method on ServiceDsl returns a new BuilderContext
 * with the added module, maintaining immutability throughout the build process.
 *
 * Design Principles:
 * - Immutable Updates: Each `with*` method returns a new copy with one field updated
 * - Optional Fields: All fields are Option types during building, resolved at toServiceContext
 * - Fail-Fast Validation: toServiceContext validates all required fields are present
 * - Type-Safe Conversion: Converts to ServiceContext with resolved (non-Option) fields
 *
 * Lifecycle:
 * 1. ServiceDsl creates empty BuilderContext()
 * 2. Each module adds its dependencies via with* methods
 * 3. build() calls toServiceContext to validate and convert to ServiceContext
 * 4. ServiceContext contains resolved, non-optional references for runtime use
 *
 * Fields:
 * - Configuration: config, coreConfig
 * - Actor System: actorSystem, queueActorRef
 * - Interface Functions: curriedFunctions (for REST/CLI/gRPC adapters)
 * - External Behaviors: actorBehaviorsContext (for custom actors behind routers)
 * - Service Modules: vaultService, storageService
 * - Service Functions: vaultFunctions, storageFunctions, serviceFunctionsContext
 *
 * @param config Typesafe Config instance
 * @param coreConfig Parsed CoreConfig from application.conf
 * @param actorSystem Root actor system (GuardianActor)
 * @param queueActorRef Reference to QueueActor for test queue management
 * @param curriedFunctions Pre-curried business logic functions for interface layer
 * @param actorBehaviorsContext External actor behaviors to spawn behind routers
 * @param vaultService Vault service module implementation (Local, AWS, Azure, GCP)
 * @param storageService Block storage service module implementation (Local, AWS, Azure, GCP)
 * @param vaultFunctions Curried vault functions extracted from vaultService
 * @param storageFunctions Curried storage functions extracted from storageService
 * @param serviceFunctionsContext Bundle of service functions for actor consumption
 * @see ServiceDsl for the builder that produces this context
 * @see ServiceContext for the resolved runtime context
 */
case class BuilderContext(
  config: Option[Config] = None,
  coreConfig: Option[CoreConfig] = None,
  actorSystem: Option[ActorSystem[GuardianCommands.GuardianCommand]] = None,
  queueActorRef: Option[ActorRef[QueueCommands.QueueCommand]] = None,
  curriedFunctions: Option[ServiceInterfaceFunctions] = None,
  actorBehaviorsContext: Option[ActorBehaviorsContext] = None,
  vaultService: Option[modules.ProbeVaultService] = None,
  storageService: Option[modules.ProbeStorageService] = None,
  vaultFunctions: Option[VaultServiceFunctions] = None,
  storageFunctions: Option[StorageServiceFunctions] = None,
  serviceFunctionsContext: Option[ServiceFunctionsContext] = None
) {

  /**
   * Add configuration to builder context
   *
   * @param c Typesafe Config instance
   * @param cc Parsed CoreConfig from application.conf
   * @return New BuilderContext with configuration added
   */
  def withConfig(c: Config, cc: CoreConfig): BuilderContext =
    copy(config = Some(c), coreConfig = Some(cc))

  /**
   * Add actor system to builder context
   *
   * @param sys Root actor system with GuardianActor
   * @return New BuilderContext with actor system added
   */
  def withActorSystem(sys: ActorSystem[GuardianCommands.GuardianCommand]): BuilderContext =
    copy(actorSystem = Some(sys))

  /**
   * Add queue actor reference to builder context
   *
   * @param queue Reference to QueueActor for test queue management
   * @return New BuilderContext with queue actor added
   */
  def withQueueActorRef(
    queue: ActorRef[QueueCommands.QueueCommand]
  ): BuilderContext =
    copy(queueActorRef = Some(queue))

  /**
   * Add curried interface functions to builder context
   *
   * These functions are passed to interface modules (REST/CLI/gRPC)
   * for calling business logic without actor knowledge.
   *
   * @param functions Pre-curried business logic functions
   * @return New BuilderContext with curried functions added
   */
  def withCurriedFunctions(functions: ServiceInterfaceFunctions): BuilderContext =
    copy(curriedFunctions = Some(functions))

  /**
   * Add external actor behaviors context to builder
   *
   * External behaviors are spawned behind routers for REST/gRPC clients,
   * database adapters, or other external service interactions.
   *
   * @param behaviors Context containing external behavior configurations
   * @return New BuilderContext with external behaviors added
   */
  def withActorBehaviorsContext(behaviors: ActorBehaviorsContext): BuilderContext =
    copy(actorBehaviorsContext = Some(behaviors))

  /**
   * Add vault service module to builder context
   *
   * @param service Vault service implementation (Local, AWS, Azure, GCP)
   * @return New BuilderContext with vault service added
   */
  def withVaultService(service: modules.ProbeVaultService): BuilderContext =
    copy(vaultService = Some(service))

  /**
   * Add block storage service module to builder context
   *
   * @param service Storage service implementation (Local, AWS S3, Azure Blob, GCP Storage)
   * @return New BuilderContext with storage service added
   */
  def withStorageService(service: modules.ProbeStorageService): BuilderContext =
    copy(storageService = Some(service))

  /**
   * Add vault service functions to builder context
   *
   * @param functions Curried vault functions extracted from vault service
   * @return New BuilderContext with vault functions added
   */
  def withVaultFunctions(functions: VaultServiceFunctions): BuilderContext =
    copy(vaultFunctions = Some(functions))

  /**
   * Add storage service functions to builder context
   *
   * @param functions Curried storage functions extracted from storage service
   * @return New BuilderContext with storage functions added
   */
  def withStorageFunctions(functions: StorageServiceFunctions): BuilderContext =
    copy(storageFunctions = Some(functions))

  /**
   * Add service functions context to builder
   *
   * This bundle contains all service functions needed by actors.
   *
   * @param ctx Service functions context for actor consumption
   * @return New BuilderContext with service functions context added
   */
  def withServiceFunctionsContext(ctx: ServiceFunctionsContext): BuilderContext =
    copy(serviceFunctionsContext = Some(ctx))

  /**
   * Convert BuilderContext to ServiceContext with validation
   *
   * This method validates that all required fields are present and converts
   * the optional fields to resolved (non-optional) references for runtime use.
   *
   * Called by ServiceDsl.build() as the final step in the builder lifecycle.
   *
   * @param ec Implicit execution context for async operations
   * @return ServiceContext with resolved, non-optional references
   * @throws IllegalStateException if any required field is missing
   */
  def toServiceContext(implicit ec: ExecutionContext): ServiceContext =
    val resolvedActorSystem = actorSystem.getOrElse(
      throw new IllegalStateException("ActorSystem not initialized in BuilderContext")
    )
    val resolvedConfig = config.getOrElse(
      throw new IllegalStateException("Config not initialized in BuilderContext")
    )
    val resolvedCoreConfig = coreConfig.getOrElse(
      throw new IllegalStateException("CoreConfig not initialized in BuilderContext")
    )
    val resolvedQueueActorRef = queueActorRef.getOrElse(
      throw new IllegalStateException("QueueActorRef not initialized in BuilderContext")
    )
    val resolvedCurriedFunctions = curriedFunctions.getOrElse(
      throw new IllegalStateException("CurriedFunctions not initialized in BuilderContext")
    )
    ServiceContext(
      resolvedActorSystem, resolvedConfig, resolvedCoreConfig, resolvedQueueActorRef, resolvedCurriedFunctions
    )
}
