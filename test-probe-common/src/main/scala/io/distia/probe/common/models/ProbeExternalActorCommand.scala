package io.distia.probe.common.models

/**
 * Marker trait for external actor commands.
 *
 * All external actor behaviors that are registered with the ServiceDsl
 * via withExternalServicesModule() must handle commands that extend this trait.
 *
 * This enables the framework to:
 * - Place these actors behind routers for scalability
 * - Register them with external routing addresses for DSL access
 * - Support REST/gRPC clients, database adapters, and other external service actors
 *
 * Example usage:
 * {{{
 * // User-defined command for REST client actor
 * case class HttpGet(url: String, replyTo: ActorRef[HttpResponse])
 *   extends ProbeExternalActorCommand
 *
 * // User-defined behavior
 * val httpClientBehavior: Behavior[HttpGet] = ...
 *
 * // Register with ServiceDsl
 * ServiceDsl()
 *   .withExternalServicesModule(
 *     ExternalBehaviorConfig(
 *       routerPoolSize = 5,
 *       actorNamePrefix = "http-client",
 *       externalAddress = "rest-api",
 *       behavior = httpClientBehavior
 *     )
 *   )
 * }}}
 */
trait ProbeExternalActorCommand
