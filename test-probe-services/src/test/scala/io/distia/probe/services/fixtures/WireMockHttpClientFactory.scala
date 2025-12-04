package io.distia.probe.services
package fixtures

import org.apache.pekko.actor.ActorSystem
import io.distia.probe.services.factories.HttpClientFactory
import io.distia.probe.services.httpclient.ServicesHttpClient

/**
 * Test factory that creates HTTP clients for use with WireMock.
 *
 * The actual WireMock URL is configured in the test config (function-url),
 * so this factory simply creates the standard ServicesHttpClient.
 * WireMock intercepts calls based on the configured URL.
 */
class WireMockHttpClientFactory extends HttpClientFactory {

  override def createClient()(implicit actorSystem: ActorSystem): ServicesHttpClient = {
    new ServicesHttpClient()
  }
}
