package io.distia.probe
package core
package builder

import scala.concurrent.{ExecutionContext, Future}

/**
 * Three-phase lifecycle interface for ServiceDSL builder modules
 *
 * Each module (Config, ActorSystem, Context) implements this trait to participate
 * in the type-safe builder pattern. The three phases execute in strict order:
 *
 * 1. **preFlight** - Validation phase (no side effects, no context mutation)
 *    - Validates that the module can initialize successfully
 *    - Checks dependencies from previous modules
 *    - All validation logic MUST be inside Future block
 *    - Returns the SAME context (no decoration)
 *
 * 2. **initialize** - Initialization phase (context decoration)
 *    - Performs actual initialization work (create services, start actors, etc.)
 *    - Decorates the context with newly created resources
 *    - All initialization logic MUST be inside Future block
 *    - Returns DECORATED context with new data
 *
 * 3. **finalCheck** - Final validation phase (pass/fail)
 *    - Validates that initialization succeeded and resources are ready
 *    - Checks final state consistency
 *    - All validation logic MUST be inside Future block
 *    - Returns the SAME context (no decoration)
 *
 * Example execution order in ServiceDSL.build():
 * {{{
 * for {
 *   // Phase 1: Validation
 *   ctx0 <- configModule.preFlight(emptyContext)
 *   ctx0 <- actorSystemModule.preFlight(ctx0)
 *   ctx0 <- contextModule.preFlight(ctx0)
 *
 *   // Phase 2: Initialization (context threading)
 *   ctx1 <- configModule.initialize(ctx0)      // Adds Config/ServiceConfig
 *   ctx2 <- actorSystemModule.initialize(ctx1) // Adds ActorSystem/GuardianActor
 *   ctx3 <- contextModule.initialize(ctx2)     // Adds StorageService/VaultService
 *
 *   // Phase 3: Final validation
 *   ctx3 <- configModule.finalCheck(ctx3)
 *   ctx3 <- actorSystemModule.finalCheck(ctx3)
 *   ctx3 <- contextModule.finalCheck(ctx3)
 * } yield ctx3.toServiceContext
 * }}}
 *
 * Design principles:
 * - All logic must be inside Future blocks (no synchronous execution before Future creation)
 * - preFlight and finalCheck return the same context (validation only)
 * - Only initialize should decorate the context
 * - Failures propagate as failed Futures (use require(), throw exceptions, or Future.failed)
 */
trait BuilderModule {

  /**
   * Phase 1: Validation (no side effects, no context mutation)
   *
   * Validates that this module can initialize successfully with the current context state.
   * All validation logic must be inside the Future block.
   *
   * @param ctx Current BuilderContext (may be partially decorated by previous modules)
   * @return Future containing the SAME context if validation passes, or failed Future if validation fails
   */
  def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext]

  /**
   * Phase 2: Initialization (decorates context with new data)
   *
   * Performs initialization work and decorates the context with newly created resources.
   * All initialization logic must be inside the Future block.
   *
   * @param ctx Current BuilderContext (validated by preFlight)
   * @return Future containing DECORATED context with new resources, or failed Future if initialization fails
   */
  def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext]

  /**
   * Phase 3: Final validation (pass/fail on initialized state)
   *
   * Validates that initialization succeeded and all resources are ready for use.
   * All validation logic must be inside the Future block.
   *
   * @param ctx Fully decorated BuilderContext (after all modules initialized)
   * @return Future containing the SAME context if validation passes, or failed Future if validation fails
   */
  def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext]
}
