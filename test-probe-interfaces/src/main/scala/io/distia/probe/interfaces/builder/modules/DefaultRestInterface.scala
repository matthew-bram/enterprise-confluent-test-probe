package io.distia.probe.interfaces.builder.modules

import io.distia.probe.core.builder.{BuilderContext, BuilderModule}
import io.distia.probe.core.builder.modules.ProbeInterface
import io.distia.probe.core.builder.ServiceInterfaceFunctions
import io.distia.probe.interfaces.config.InterfacesConfig
import io.distia.probe.interfaces.rest.RestServer
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.slf4j.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Default REST interface implementation using Pekko HTTP
 *
 * Implements ProbeInterface trait from core module, participating in the
 * phantom type builder pattern.
 *
 * Lifecycle:
 * 1. preFlight - Validates config and dependencies
 * 2. initialize - Creates and starts HTTP server, decorates context
 * 3. finalCheck - Verifies server is bound and ready
 * 4. shutdown - Gracefully unbinds server
 *
 * Dependencies:
 * - Requires Config (needs InterfacesConfig)
 * - Requires ActorSystem (for HTTP server)
 * - Requires ServiceInterfaceFunctions (for request handling)
 *
 * Thread Safety:
 * Uses AtomicReference for state management to ensure thread-safe
 * initialization and shutdown in concurrent actor systems.
 */
private[interfaces] class DefaultRestInterface extends ProbeInterface {

  // Thread-safe state using volatile vars (matches services module pattern)
  @volatile private var binding: Option[ServerBinding] = None
  @volatile private var config: Option[InterfacesConfig] = None
  @volatile private var logger: Option[Logger] = None

  /**
   * No-op: Functions are passed directly to RestServer constructor in initialize()
   * Kept to satisfy ProbeInterface trait requirement.
   */
  override def setCurriedFunctions(functions: ServiceInterfaceFunctions): Unit = ()

  /**
   * Phase 1: Validate dependencies and configuration
   *
   * Accumulates all validation errors and reports them together
   * for better user experience (no fix-one-error-at-a-time loop).
   */
  override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = {
    val errors = List(
      Option.when(ctx.config.isEmpty)(
        "Config must be initialized before REST interface"
      ),
      Option.when(ctx.actorSystem.isEmpty)(
        "ActorSystem must be initialized before REST interface"
      ),
      Option.when(ctx.curriedFunctions.isEmpty)(
        "ServiceInterfaceFunctions must be initialized before REST interface"
      )
    ).flatten

    // If basic dependencies are present, validate config is parseable
    val configValidationError = if ctx.config.isDefined then
      Try(InterfacesConfig.fromConfig(ctx.config.get)) match
        case Failure(ex) => Some(s"Invalid interfaces configuration: ${ex.getMessage}")
        case Success(_) => None
    else
      None

    val allErrors = errors ++ configValidationError.toList

    if allErrors.nonEmpty then
      Future.failed(new IllegalStateException(
        s"REST interface pre-flight validation failed:\n${allErrors.mkString("  - ", "\n  - ", "")}"
      ))
    else
      Future.successful(ctx)
  }

  /**
   * Phase 2: Initialize REST server
   *
   * Thread-safe: Only allows initialization once (checks current state).
   */
  override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = {
    // Validate not already initialized
    require(binding.isEmpty, "REST interface already initialized")

    // Extract validated dependencies
    val appConfig = ctx.config.get
    val interfacesConfig = InterfacesConfig.fromConfig(appConfig)
    val actorSystem = ctx.actorSystem.get
    val functions = ctx.curriedFunctions.get

    // Store config and logger
    config = Some(interfacesConfig)
    logger = Some(actorSystem.log)

    // Create and start REST server
    val restServer = new RestServer(
      interfacesConfig,
      functions
    )(actorSystem.executionContext, actorSystem)

    restServer.start(interfacesConfig.restHost, interfacesConfig.restPort).map { serverBinding =>
      // Store binding for shutdown
      binding = Some(serverBinding)
      logger.get.info(s"REST server initialized and bound to ${serverBinding.localAddress}")
      ctx  // Return context unchanged (state stored internally)
    }
  }

  /**
   * Phase 3: Verify server is running
   *
   * Validates that initialization completed successfully and server is bound.
   */
  override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(binding.isDefined, "REST server binding not initialized")
    require(binding.get.localAddress != null, "REST server not bound to local address")
    require(config.isDefined, "InterfacesConfig not initialized")
    require(logger.isDefined, "Logger not initialized")

    logger.get.info(s"✓ REST interface final check passed - server ready at ${binding.get.localAddress}")
    ctx
  }

  /**
   * Graceful shutdown: unbind server port
   *
   * Thread-safe: Can be called multiple times safely.
   */
  override def shutdown()(implicit ec: ExecutionContext): Future[Unit] = {
    binding match {
      case Some(serverBinding) =>
        logger.foreach(_.info("Shutting down REST interface"))
        serverBinding.unbind().map(_ => ())
      case None =>
        Future.successful(())  // Already shut down or never started
    }
  }

}
