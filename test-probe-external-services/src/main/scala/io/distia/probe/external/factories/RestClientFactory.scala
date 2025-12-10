package io.distia.probe.external.factories

import io.distia.probe.external.rest.RestClientCommands.RestClientCommand
import org.apache.pekko.actor.typed.Behavior

import scala.concurrent.duration.FiniteDuration

/**
 * Factory trait for creating REST client actor behaviors.
 *
 * This trait enables dependency injection for testing, allowing mock
 * implementations to override actual HTTP behavior for unit tests.
 *
 * Default implementation uses Pekko HTTP client. Test implementations
 * can provide mock behaviors that respond with predefined results.
 *
 * Example usage in tests:
 * {{{
 * class MockRestClientFactory extends RestClientFactory {
 *   override def createBehavior(defaultTimeout: FiniteDuration): Behavior[RestClientCommand] =
 *     Behaviors.receiveMessage { msg =>
 *       msg match {
 *         case ExecuteRequest(_, _, replyTo) =>
 *           replyTo ! RestClientSuccess(mockResponse)
 *           Behaviors.same
 *       }
 *     }
 * }
 * }}}
 */
trait RestClientFactory:

  /**
   * Create a REST client actor behavior.
   *
   * @param defaultTimeout Default timeout for HTTP requests
   * @return Behavior for handling RestClientCommand messages
   */
  def createBehavior(defaultTimeout: FiniteDuration): Behavior[RestClientCommand]

object RestClientFactory:

  /**
   * Default factory implementation using Pekko HTTP client.
   *
   * Creates the standard RestClientActor behavior that makes
   * real HTTP requests via Pekko HTTP.
   */
  val default: RestClientFactory = new RestClientFactory:
    import io.distia.probe.external.rest.RestClientActor

    override def createBehavior(defaultTimeout: FiniteDuration): Behavior[RestClientCommand] =
      RestClientActor(defaultTimeout)
