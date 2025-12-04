package io.distia.probe.interfaces.rest

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import io.distia.probe.core.builder.ServiceInterfaceFunctions
import io.distia.probe.interfaces.config.InterfacesConfig
import org.slf4j.{LoggerFactory, Logger}

import scala.concurrent.{ExecutionContext, Future}

/**
 * REST HTTP server using Pekko HTTP
 *
 * Responsibilities:
 * - Create HTTP server
 * - Bind to configured host/port
 * - Route requests to RestRoutes
 * - Provide server binding for graceful shutdown
 *
 * Visibility: private[interfaces] maintains module encapsulation.
 * All methods remain public for comprehensive unit testing.
 */
private[interfaces] class RestServer(
  config: InterfacesConfig,
  functions: ServiceInterfaceFunctions
)(implicit ec: ExecutionContext, system: ActorSystem[_]) {

  private val logger: Logger = LoggerFactory.getLogger(classOf[RestServer])
  private val routes: Route = new RestRoutes(config, functions).routes

  /**
   * Start HTTP server on configured host/port
   *
   * Uses andThen for logging side effects while preserving Future chain.
   *
   * @param host Host to bind (0.0.0.0 for all interfaces)
   * @param port Port to bind
   * @return Future containing ServerBinding for shutdown (or failed Future)
   */
  def start(host: String, port: Int): Future[ServerBinding] = {
    logger.info(s"Starting REST server on $host:$port")

    Http()
      .newServerAt(host, port)
      .bind(routes)
      .andThen {
        case scala.util.Success(binding) =>
          logger.info(s"✓ REST server bound to ${binding.localAddress}")
        case scala.util.Failure(ex) =>
          logger.error(s"✗ Failed to bind REST server on $host:$port", ex)
      }
  }
}
