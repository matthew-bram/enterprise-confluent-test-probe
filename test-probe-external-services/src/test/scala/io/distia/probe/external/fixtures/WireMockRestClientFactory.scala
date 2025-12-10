package io.distia.probe.external.fixtures

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.distia.probe.external.factories.RestClientFactory
import io.distia.probe.external.rest.{RestClientActor, RestClientCommands}

import org.apache.pekko.actor.typed.Behavior
import scala.concurrent.duration.*

/**
 * WireMock-based test factory for REST client integration tests.
 *
 * Provides utilities for setting up WireMock stubs and creating
 * REST client behaviors configured to use the WireMock server.
 */
object WireMockRestClientFactory:

  /**
   * Create a WireMock server on a dynamic port.
   *
   * @return Configured WireMockServer instance (not started)
   */
  def createServer(): WireMockServer =
    new WireMockServer(wireMockConfig().dynamicPort())

  /**
   * Get the base URL for a running WireMock server.
   *
   * @param server Running WireMock server
   * @return Base URL string (e.g., "http://localhost:8080")
   */
  def baseUrl(server: WireMockServer): String =
    s"http://localhost:${server.port()}"

  /**
   * Stub a successful POST response.
   *
   * @param server WireMock server
   * @param path URL path to stub
   * @param responseBody JSON response body
   * @param statusCode HTTP status code (default: 200)
   */
  def stubPost(
    server: WireMockServer,
    path: String,
    responseBody: String,
    statusCode: Int = 200
  ): Unit =
    server.stubFor(
      post(urlEqualTo(path))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
        )
    )

  /**
   * Stub a successful GET response.
   *
   * @param server WireMock server
   * @param path URL path to stub
   * @param responseBody JSON response body
   * @param statusCode HTTP status code (default: 200)
   */
  def stubGet(
    server: WireMockServer,
    path: String,
    responseBody: String,
    statusCode: Int = 200
  ): Unit =
    server.stubFor(
      get(urlEqualTo(path))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
        )
    )

  /**
   * Stub a timeout (delayed response exceeding timeout).
   *
   * @param server WireMock server
   * @param path URL path to stub
   * @param delayMs Delay in milliseconds
   */
  def stubTimeout(
    server: WireMockServer,
    path: String,
    delayMs: Int = 60000
  ): Unit =
    server.stubFor(
      post(urlEqualTo(path))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withFixedDelay(delayMs)
        )
    )

  /**
   * Stub an error response.
   *
   * @param server WireMock server
   * @param path URL path to stub
   * @param statusCode HTTP error status code
   * @param responseBody Error response body
   */
  def stubError(
    server: WireMockServer,
    path: String,
    statusCode: Int,
    responseBody: String
  ): Unit =
    server.stubFor(
      post(urlEqualTo(path))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
        )
    )

  /**
   * Stub a connection failure (network fault).
   *
   * @param server WireMock server
   * @param path URL path to stub
   */
  def stubConnectionFailure(
    server: WireMockServer,
    path: String
  ): Unit =
    server.stubFor(
      post(urlEqualTo(path))
        .willReturn(
          aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)
        )
    )

  /**
   * Factory implementation that creates RestClientActor with default timeout.
   */
  val factory: RestClientFactory = new RestClientFactory:
    override def createBehavior(defaultTimeout: FiniteDuration): Behavior[RestClientCommands.RestClientCommand] =
      RestClientActor(defaultTimeout)
