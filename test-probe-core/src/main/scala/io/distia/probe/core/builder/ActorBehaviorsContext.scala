package io.distia.probe
package core
package builder

import io.distia.probe.common.models.ProbeExternalActorCommand
import org.apache.pekko.actor.typed.Behavior

/**
 * Configuration for an external actor behavior that will be placed behind a router.
 *
 * External behaviors are optional actor systems that can be added to the ServiceDsl
 * for handling REST/gRPC clients, database adapters, or other external service interactions.
 *
 * @param routerPoolSize Number of router instances to spawn for load balancing
 * @param actorNamePrefix Prefix for actor names (e.g., "http-client" → "http-client-1", "http-client-2")
 * @param externalAddress External routing address for DSL access (e.g., "rest-api", "grpc-client")
 * @param behavior The actor behavior to spawn behind the router
 */
case class ExternalBehaviorConfig(
  routerPoolSize: Int,
  actorNamePrefix: String,
  externalAddress: String,
  behavior: Behavior[_ <: ProbeExternalActorCommand]
)

/**
 * Context for managing external actor behaviors that will be routed.
 *
 * The ActorBehaviorsContext contains configurations for external actor behaviors
 * that will be spawned behind routers and registered with external routing addresses
 * in the Probe DSL for access and response handling.
 *
 * Design Principles:
 * - Router Pattern: Each behavior is spawned behind a router for load balancing
 * - External Integration: Enables actors to make thread-safe calls to external services
 * - DSL Registration: Behaviors are registered by external address for DSL access
 *
 * Mainly used for:
 * - REST/gRPC client calls
 * - Thread-safe database and adapter calls
 * - Other external service interactions
 *
 * Lifecycle:
 * 1. Interface modules create ExternalBehaviorConfig for their client actors
 * 2. Config added to ActorBehaviorsContext via addBehavior()
 * 3. Context passed to ServiceDsl via withActorBehavior()
 * 4. DefaultActorSystem spawns behaviors behind routers during initialize()
 * 5. Routers registered in Probe DSL for test step access
 *
 * @param behaviors List of external behavior configurations to spawn
 * @see ExternalBehaviorConfig for individual behavior configuration
 */
case class ActorBehaviorsContext(
  behaviors: List[ExternalBehaviorConfig] = Nil
) {
  /**
   * Add an external behavior configuration to the context
   *
   * Behaviors are prepended to the list (LIFO order).
   *
   * @param config External behavior configuration
   * @return New ActorBehaviorsContext with behavior added
   */
  def addBehavior(config: ExternalBehaviorConfig): ActorBehaviorsContext =
    copy(behaviors = config :: behaviors)
}
