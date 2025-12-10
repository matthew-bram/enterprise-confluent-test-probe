package io.distia.probe.external.builder.modules

import io.distia.probe.core.builder.{ActorBehaviorsContext, BuilderContext, BuilderModule, ExternalBehaviorConfig}
import io.distia.probe.core.builder.modules.ProbeActorBehavior
import io.distia.probe.external.rest.{RestClientActor, RestClientObjectMapper}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

/**
 * Default REST client module for the ServiceDsl builder.
 *
 * Provides a non-blocking REST client backed by Pekko HTTP, exposed as a typed
 * actor behavior behind a router pool. Teams can send ExecuteRequest commands
 * to make HTTP calls from step definitions.
 *
 * Integration with ServiceDsl:
 * {{{
 * val ctx = ServiceDsl()
 *   .withConfig(new DefaultConfig)
 *   .withActorSystem(new DefaultActorSystem)
 *   .withExternalServicesModule(new DefaultRestClient(
 *     routerPoolSize = 5,
 *     defaultTimeout = 30.seconds
 *   ))
 *   .build()
 * }}}
 *
 * @param routerPoolSize Number of router instances for load balancing (default: 5)
 * @param defaultTimeout Default request timeout, can be overridden per-request (default: 30s)
 * @param externalAddress External routing address for DSL access (default: "rest-client")
 */
class DefaultRestClient(
  routerPoolSize: Int = 5,
  defaultTimeout: FiniteDuration = 30.seconds,
  externalAddress: String = "rest-client"
) extends ProbeActorBehavior:

  /**
   * Phase 1: Validate that ObjectMapper can initialize.
   *
   * Triggers lazy initialization of Jackson ObjectMapper to fail fast
   * if there are class loading or configuration issues.
   */
  override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
    Future {
      // Trigger lazy ObjectMapper initialization to validate configuration
      val _ = RestClientObjectMapper.mapper
      ctx
    }

  /**
   * Phase 2: Create ExternalBehaviorConfig and register with ActorBehaviorsContext.
   *
   * Creates the REST client actor behavior configuration and adds it to
   * the builder context for spawning during actor system initialization.
   */
  override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
    Future {
      val config = ExternalBehaviorConfig(
        routerPoolSize = routerPoolSize,
        actorNamePrefix = "rest-client",
        externalAddress = externalAddress,
        behavior = RestClientActor(defaultTimeout)
      )

      // Get existing behaviors context or create new one
      val behaviorsContext = ctx.actorBehaviorsContext
        .getOrElse(ActorBehaviorsContext())
        .addBehavior(config)

      ctx.withActorBehaviorsContext(behaviorsContext)
    }

  /**
   * Phase 3: Validate that REST client behavior was registered.
   *
   * Ensures the behavior configuration exists in the context and
   * the router pool size is valid.
   */
  override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
    Future {
      require(
        ctx.actorBehaviorsContext.exists(_.behaviors.nonEmpty),
        "REST client behavior not registered in ActorBehaviorsContext"
      )
      require(
        routerPoolSize > 0,
        s"Router pool size must be positive, got: $routerPoolSize"
      )
      ctx
    }
