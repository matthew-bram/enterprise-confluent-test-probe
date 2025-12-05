package io.distia.probe.services.httpclient

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.util.ByteString
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

/**
 * ServicesHttpClient - HTTP client for vault and external service invocations
 *
 * Provides POST request functionality with JSON payloads for vault services
 * (AWS Lambda, Azure Functions, GCP Cloud Functions). Uses Pekko HTTP connection
 * pooling for efficient resource utilization.
 *
 * Features:
 * - JSON POST requests with custom headers
 * - Response body extraction
 * - Connection pool management
 * - Graceful shutdown
 *
 * Testing:
 * - Use WireMock to mock HTTP endpoints
 * - Inject via HttpClientFactory for dependency injection
 *
 * @param actorSystem Pekko ActorSystem for HTTP connection pooling
 */
class ServicesHttpClient()(implicit actorSystem: ActorSystem) {

  implicit val ec: ExecutionContext = actorSystem.getDispatcher
  private val http = Http()

  /**
   * Build HTTP headers from a map of name-value pairs
   *
   * @param headers Map of header names to values
   * @return List of validated HttpHeader instances
   * @throws IllegalArgumentException if header parsing fails
   */
  def buildHeaders(headers: Map[String, String]): List[HttpHeader] = {
    headers.map { case (name, value) =>
      HttpHeader.parse(name, value) match {
        case HttpHeader.ParsingResult.Ok(header, _) => header
        case error =>
          throw new IllegalArgumentException(s"Invalid header: $name: $value - $error")
      }
    }.toList
  }

  /**
   * Build an HTTP POST request with JSON payload
   *
   * @param uri Target URI for the request
   * @param jsonPayload JSON string to send as request body
   * @param headers List of HTTP headers to include
   * @return Configured HttpRequest ready for execution
   */
  def buildRequest(
    uri: String,
    jsonPayload: String,
    headers: List[HttpHeader]
  ): HttpRequest = {
    HttpRequest(
      method = HttpMethods.POST,
      uri = uri,
      headers = headers,
      entity = HttpEntity(ContentTypes.`application/json`, jsonPayload)
    )
  }

  /**
   * Extract response body as a string
   *
   * Streams the response entity bytes and converts to UTF-8 string.
   * Returns None for empty response bodies.
   *
   * @param response HTTP response to extract body from
   * @return Future containing optional body string
   */
  def extractBody(response: HttpResponse): Future[Option[String]] = {
    response.entity.dataBytes
      .runFold(ByteString.empty)(_ ++ _)
      .map { body =>
        val bodyString = body.utf8String
        if (bodyString.isEmpty) None else Some(bodyString)
      }
  }

  /**
   * Execute an HTTP POST request with JSON payload
   *
   * Sends a POST request to the specified URI with the provided JSON payload
   * and headers. Returns the HTTP status code and optional response body.
   *
   * @param uri Target URI for the request
   * @param jsonPayload JSON string to send as request body
   * @param headers Map of header names to values (default: empty)
   * @param timeout Optional timeout for the request (currently unused)
   * @return Future containing (status code, optional response body)
   */
  def post(
    uri: String,
    jsonPayload: String,
    headers: Map[String, String] = Map.empty,
    timeout: Option[FiniteDuration] = None
  ): Future[(Int, Option[String])] = {

    val httpHeaders = buildHeaders(headers)
    val request = buildRequest(uri, jsonPayload, httpHeaders)

    http.singleRequest(request).flatMap { response =>
      val statusCode = response.status.intValue()
      extractBody(response).map(body => (statusCode, body))
    }
  }

  /**
   * Shutdown all HTTP connection pools
   *
   * Gracefully closes all connection pools managed by this client.
   * Should be called during application shutdown to release resources.
   *
   * @return Future that completes when all pools are shut down
   */
  def shutdown(): Future[Unit] = {
    http.shutdownAllConnectionPools()
  }
}
