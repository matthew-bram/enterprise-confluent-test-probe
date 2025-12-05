package io.distia.probe
package core

import builder.*
import builder.modules.*
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

/**
 * ServiceDsl - Type-Safe Builder for Test-Probe Service Context
 *
 * The ServiceDsl provides a fluent, type-safe builder API for constructing the complete
 * Test-Probe service context. It uses phantom types to enforce at compile-time that all
 * required modules are provided before the service can be built.
 *
 * Design Principles:
 * - Compile-Time Safety: Missing required modules cause compilation errors, not runtime failures
 * - Fluent API: Method chaining enables readable, declarative configuration
 * - Fail-Fast: Module validation occurs during preFlight/initialize/finalCheck phases
 * - Extensibility: External service modules can be added for custom actor behaviors
 *
 * Required Modules (must be added before build()):
 * - ProbeConfig: Application configuration (Typesafe Config wrapper)
 * - ProbeActorSystem: Actor system lifecycle management
 * - ProbeInterface: REST/gRPC interface configuration
 * - ProbeStorageService: Block storage backend (Local, AWS S3, Azure Blob, GCP Storage)
 * - ProbeVaultService: Secrets management backend (Local, AWS Secrets Manager, Azure Key Vault, GCP Secret Manager)
 *
 * Optional Modules:
 * - ProbeActorBehavior: Custom external service actors (can add multiple)
 *
 * Build Lifecycle:
 * 1. preFlight: Validate configuration and prerequisites
 * 2. initialize: Create connections, spawn actors, allocate resources
 * 3. finalCheck: Verify system health and readiness
 *
 * Example Usage:
 * {{{
 *   val context: Future[ServiceContext] = ServiceDsl()
 *     .withConfig(DefaultConfig(config))
 *     .withActorSystem(DefaultActorSystem())
 *     .withStorageService(LocalBlockStorageService())
 *     .withVaultServiceModule(LocalVaultService())
 *     .withInterface(RestInterface())
 *     .build()
 * }}}
 *
 * Thread Safety: ServiceDsl instances are immutable; each `with*` method returns a new instance.
 *
 * @tparam Features Type-level set tracking which modules have been added. Uses phantom types
 *                  from [[PhantomTypes]] to enforce compile-time validation.
 * @see PhantomTypes for the type-level implementation of feature tracking
 * @see BuilderModule for the module lifecycle interface (preFlight/initialize/finalCheck)
 */
object ServiceDsl {
  /**
   * Create a new ServiceDsl builder with no modules configured
   *
   * @return Empty ServiceDsl ready for module configuration
   */
  def apply(): ServiceDsl[Nil] = new ServiceDsl[Nil]()
}

/**
 * ServiceDsl builder instance with accumulated module configuration
 *
 * @param configModule Configuration module (required for build)
 * @param actorSystemModule Actor system module (required for build)
 * @param interfaceModule Interface module - REST or gRPC (required for build)
 * @param storageServiceModule Block storage service module (required for build)
 * @param vaultServiceModule Vault/secrets service module (required for build)
 * @param externalServicesModules Optional list of external actor behaviors
 * @tparam Features Phantom type tracking which modules have been added
 */
class ServiceDsl[Features <: FeatureSet](
  private val configModule: Option[ProbeConfig] = None,
  private val actorSystemModule: Option[ProbeActorSystem] = None,
  private val interfaceModule: Option[ProbeInterface] = None,
  private val storageServiceModule: Option[ProbeStorageService] = None,
  private val vaultServiceModule: Option[ProbeVaultService] = None,
  private val externalServicesModules: Option[List[ProbeActorBehavior]] = None
) {
  private val logger: Logger = LoggerFactory.getLogger(classOf[ServiceDsl[_]])

  /**
   * Add configuration module to the builder
   *
   * The configuration module wraps Typesafe Config and provides validated access
   * to application settings. This is typically the first module added.
   *
   * @param config Configuration module instance (e.g., DefaultConfig)
   * @param add Compile-time evidence that ProbeConfig is not already present
   * @tparam T Specific configuration type (must extend ProbeConfig)
   * @tparam Out Resulting feature set with ProbeConfig added
   * @return New builder with configuration module added
   */
  def withConfig[T <: ProbeConfig, Out <: FeatureSet](config: T)(using add: AddIfAbsent.Aux[Features, ProbeConfig, Out]): ServiceDsl[Out] =
    new ServiceDsl[Out](
      configModule = Some(config),
      actorSystemModule = actorSystemModule,
      interfaceModule = interfaceModule,
      storageServiceModule = storageServiceModule,
      vaultServiceModule = vaultServiceModule,
      externalServicesModules = externalServicesModules
    )

  /**
   * Add actor system module to the builder
   *
   * The actor system module manages the Pekko ActorSystem lifecycle, including
   * spawning the GuardianActor and configuring supervision strategies.
   *
   * @param actorSystem Actor system module instance (e.g., DefaultActorSystem)
   * @param add Compile-time evidence that ProbeActorSystem is not already present
   * @tparam T Specific actor system type (must extend ProbeActorSystem)
   * @tparam Out Resulting feature set with ProbeActorSystem added
   * @return New builder with actor system module added
   */
  def withActorSystem[T <: ProbeActorSystem, Out <: FeatureSet](actorSystem: T)(using add: AddIfAbsent.Aux[Features, ProbeActorSystem, Out]): ServiceDsl[Out] =
    new ServiceDsl[Out](
      configModule = configModule,
      actorSystemModule = Some(actorSystem),
      interfaceModule = interfaceModule,
      storageServiceModule = storageServiceModule,
      vaultServiceModule = vaultServiceModule,
      externalServicesModules = externalServicesModules
    )

  /**
   * Add interface module to the builder
   *
   * The interface module configures the external API (REST or gRPC) that clients
   * use to submit tests and query status.
   *
   * @param interface Interface module instance (e.g., RestInterface)
   * @param add Compile-time evidence that ProbeInterface is not already present
   * @tparam T Specific interface type (must extend ProbeInterface)
   * @tparam Out Resulting feature set with ProbeInterface added
   * @return New builder with interface module added
   */
  def withInterface[T <: ProbeInterface, Out <: FeatureSet](interface: T)(using add: AddIfAbsent.Aux[Features, ProbeInterface, Out]): ServiceDsl[Out] =
    new ServiceDsl[Out](
      configModule = configModule,
      actorSystemModule = actorSystemModule,
      interfaceModule = Some(interface),
      storageServiceModule = storageServiceModule,
      vaultServiceModule = vaultServiceModule,
      externalServicesModules = externalServicesModules
    )

  /**
   * Add storage service module to the builder
   *
   * The storage service module manages test evidence storage (feature files, test results,
   * artifacts). Implementations include LocalBlockStorageService, AwsBlockStorageService,
   * AzureBlockStorageService, and GcpBlockStorageService.
   *
   * @param storageService Storage service module instance
   * @param add Compile-time evidence that ProbeStorageService is not already present
   * @tparam T Specific storage service type (must extend ProbeStorageService)
   * @tparam Out Resulting feature set with ProbeStorageService added
   * @return New builder with storage service module added
   */
  def withStorageService[T <: ProbeStorageService, Out <: FeatureSet](storageService: T)(using add: AddIfAbsent.Aux[Features, ProbeStorageService, Out]): ServiceDsl[Out] =
    new ServiceDsl[Out](
      configModule = configModule,
      actorSystemModule = actorSystemModule,
      interfaceModule = interfaceModule,
      storageServiceModule = Some(storageService),
      vaultServiceModule = vaultServiceModule,
      externalServicesModules = externalServicesModules
    )

  /**
   * Add vault service module to the builder
   *
   * The vault service module manages secrets retrieval for Kafka authentication and
   * other sensitive configuration. Implementations include LocalVaultService,
   * AwsVaultService, AzureVaultService, and GcpVaultService.
   *
   * @param vaultService Vault service module instance
   * @param add Compile-time evidence that ProbeVaultService is not already present
   * @tparam T Specific vault service type (must extend ProbeVaultService)
   * @tparam Out Resulting feature set with ProbeVaultService added
   * @return New builder with vault service module added
   */
  def withVaultServiceModule[T <: ProbeVaultService, Out <: FeatureSet](vaultService: T)(using add: AddIfAbsent.Aux[Features, ProbeVaultService, Out]): ServiceDsl[Out] =
    new ServiceDsl[Out](
      configModule = configModule,
      actorSystemModule = actorSystemModule,
      interfaceModule = interfaceModule,
      storageServiceModule = storageServiceModule,
      vaultServiceModule = Some(vaultService),
      externalServicesModules = externalServicesModules
    )

  /**
   * Add an external services module to the builder (optional, can add multiple)
   *
   * External service modules provide custom actor behaviors that are initialized
   * alongside the core actors. Unlike required modules, this method can be called
   * multiple times to add multiple external services.
   *
   * Note: This method does not modify the Features type parameter, as external
   * services are optional and do not affect the build() compile-time checks.
   *
   * @param externalService External actor behavior module instance
   * @tparam T Specific external service type (must extend ProbeActorBehavior)
   * @return New builder with external service added to the list
   */
  def withExternalServicesModule[T <: ProbeActorBehavior](externalService: T): ServiceDsl[Features] =
    new ServiceDsl[Features](
      configModule = configModule,
      actorSystemModule = actorSystemModule,
      interfaceModule = interfaceModule,
      storageServiceModule = storageServiceModule,
      vaultServiceModule = vaultServiceModule,
      externalServicesModules = Some(externalServicesModules.fold(List(externalService))(modules => externalService :: modules))
    )

  /**
   * Build the ServiceContext with all configured modules
   *
   * This method executes the three-phase initialization lifecycle for all modules:
   * 1. preFlight: Validates configuration and prerequisites for each module
   * 2. initialize: Creates connections, spawns actors, allocates resources
   * 3. finalCheck: Verifies system health and readiness
   *
   * Compile-Time Safety:
   * This method requires implicit evidence that all required modules have been added.
   * Attempting to call build() without all required modules results in a compilation error.
   *
   * Execution Order:
   * - preFlight: config → storage → vault → externals → actorSystem → interface
   * - initialize: config → externals → actorSystem → storage → vault → interface
   * - finalCheck: config → externals → actorSystem → storage → vault → interface
   *
   * Error Handling:
   * If any module fails during initialization, a fatal error is logged and the
   * exception is propagated to the caller. The system must be restarted after
   * a build failure as resources may be in an inconsistent state.
   *
   * @param hasConfig Compile-time evidence that ProbeConfig was added
   * @param hasActorSystem Compile-time evidence that ProbeActorSystem was added
   * @param hasInterface Compile-time evidence that ProbeInterface was added
   * @param hasStorageService Compile-time evidence that ProbeStorageService was added
   * @param hasVaultService Compile-time evidence that ProbeVaultService was added
   * @return Future containing the fully initialized ServiceContext
   * @throws Exception if any module fails during preFlight, initialize, or finalCheck
   */
  def build()(using
    hasConfig: Contains[Features, ProbeConfig],
    hasActorSystem: Contains[Features, ProbeActorSystem],
    hasInterface: Contains[Features, ProbeInterface],
    hasStorageService: Contains[Features, ProbeStorageService],
    hasVaultService: Contains[Features, ProbeVaultService],
  ): Future[ServiceContext] = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val buildFuture = for {
      
      ctx0 <- configModule.get.preFlight(BuilderContext())
      ctx1 <- storageServiceModule.get.preFlight(ctx0)       
      ctx2 <- vaultServiceModule.get.preFlight(ctx1)         
      ctx3 <- externalServicesModules.fold(Future.successful(ctx2))(behaviorModules => behaviorModules
        .foldRight(Future.successful(ctx2)) { (module, futCtx) => futCtx
          .flatMap(ctx => module.preFlight(ctx)) } )
      ctx4 <- actorSystemModule.get.preFlight(ctx3)
      ctx5 <- interfaceModule.get.preFlight(ctx4)

      
      ctx6 <- configModule.get.initialize(ctx5)
      ctx7 <- externalServicesModules.fold(Future.successful(ctx6))(behaviorModules => behaviorModules
        .foldRight(Future.successful(ctx6)) { (module, futCtx) => futCtx
          .flatMap(ctx => module.initialize(ctx)) } )
      ctx8 <- actorSystemModule.get.initialize(ctx7)
      ctx9 <- storageServiceModule.get.initialize(ctx8)
      ctx10 <- vaultServiceModule.get.initialize(ctx9)
      ctx11 <- interfaceModule.get.initialize(ctx10)

      
      ctx12 <- configModule.get.finalCheck(ctx11)
      ctx13 <- externalServicesModules.fold(Future.successful(ctx12))(behaviorModules => behaviorModules
        .foldRight(Future.successful(ctx12)) { (module, futCtx) => futCtx
          .flatMap(ctx => module.finalCheck(ctx)) } )
      ctx14 <- actorSystemModule.get.finalCheck(ctx13)
      ctx15 <- storageServiceModule.get.finalCheck(ctx14)
      ctx16 <- vaultServiceModule.get.finalCheck(ctx15)
      ctx17 <- interfaceModule.get.finalCheck(ctx16)

    } yield {
      ctx17.toServiceContext
    }

    // Fail-fast error handling - log fatal error and bubble exception to caller
    buildFuture.recoverWith { case ex: Throwable =>
      logger.error(
        """
          |FATAL: ServiceDsl.build() failed during initialization.
          |The system has encountered a fatal exception during boot and cannot recover.
          |All partially initialized resources may be in an inconsistent state.
          |The system must be restarted.
          |
          |If running in Kubernetes, the pod should be terminated and restarted by the orchestrator.
          |
          |Error details:
        """.stripMargin,
        ex
      )
      Future.failed(ex) // Re-throw to bubble up to caller
    }
  }

}
