package io.distia.probe.services
package factories

import org.apache.pekko.actor.ActorSystem
import io.distia.probe.services.httpclient.ServicesHttpClient

/**
 * Factory for creating HTTP clients with configurable behavior.
 *
 * This trait enables dependency injection for testing, allowing WireMock
 * or other mock implementations to override HTTP endpoints.
 */
trait HttpClientFactory {

  /**
   * Creates an HTTP client configured for the given actor system.
   *
   * @param actorSystem Pekko actor system for HTTP connection pooling
   * @return Configured ServicesHttpClient instance
   */
  def createClient()(implicit actorSystem: ActorSystem): ServicesHttpClient
}
