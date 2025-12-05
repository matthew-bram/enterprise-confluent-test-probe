package io.distia.probe.services
package factories

import org.apache.pekko.actor.ActorSystem
import io.distia.probe.services.httpclient.ServicesHttpClient

/**
 * Default factory that creates standard ServicesHttpClient instances.
 *
 * Used in production environments where HTTP calls go to real endpoints.
 */
class DefaultHttpClientFactory extends HttpClientFactory {

  override def createClient()(implicit actorSystem: ActorSystem): ServicesHttpClient = {
    new ServicesHttpClient()
  }
}
