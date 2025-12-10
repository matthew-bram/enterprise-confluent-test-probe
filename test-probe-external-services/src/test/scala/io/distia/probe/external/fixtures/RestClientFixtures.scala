package io.distia.probe.external.fixtures

import io.distia.probe.external.rest.*
import io.distia.probe.external.rest.RestClientCommands.*

import java.net.URI
import scala.concurrent.duration.*
import scala.reflect.ClassTag

/**
 * Test fixtures for REST client tests.
 *
 * Provides reusable test data, sample request/response types, and helper methods
 * for creating test scenarios.
 */
object RestClientFixtures:

  // Sample request/response case classes for testing
  case class OrderRequest(productId: String, quantity: Int)
  case class OrderResponse(orderId: String, status: String, total: Double)
  case class ErrorResponse(code: String, message: String)
  case class EmptyPayload()

  // Sample URIs
  val baseUri: URI = URI.create("http://localhost:8080")
  val ordersUri: URI = URI.create("http://localhost:8080/api/orders")
  val usersUri: URI = URI.create("http://localhost:8080/api/users")

  // Sample headers
  val authHeaders: Map[String, String] = Map(
    "Authorization" -> "Bearer test-token",
    "Content-Type" -> "application/json"
  )

  val emptyHeaders: Map[String, String] = Map.empty

  // Sample request envelopes
  def createOrderRequest(
    productId: String = "prod-123",
    quantity: Int = 2,
    uri: URI = ordersUri,
    headers: Map[String, String] = authHeaders,
    timeout: Option[FiniteDuration] = None
  ): RestClientEnvelopeReq[OrderRequest] =
    RestClientEnvelopeReq(
      payload = OrderRequest(productId, quantity),
      uri = uri,
      method = HttpMethod.POST,
      headers = headers,
      timeout = timeout
    )

  def createGetRequest(
    uri: URI = usersUri,
    headers: Map[String, String] = authHeaders,
    timeout: Option[FiniteDuration] = None
  ): RestClientEnvelopeReq[EmptyPayload] =
    RestClientEnvelopeReq(
      payload = EmptyPayload(),
      uri = uri,
      method = HttpMethod.GET,
      headers = headers,
      timeout = timeout
    )

  // Sample response envelopes
  def createOrderResponse(
    orderId: String = "order-456",
    status: String = "CREATED",
    total: Double = 99.99,
    statusCode: Int = 200,
    responseTime: FiniteDuration = 100.millis
  ): RestClientEnvelopeRes[OrderResponse] =
    given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])
    RestClientEnvelopeRes(
      statusCode = statusCode,
      body = OrderResponse(orderId, status, total),
      headers = Map("Content-Type" -> "application/json"),
      responseTime = responseTime
    )

  def createErrorResponse(
    code: String = "INVALID_REQUEST",
    message: String = "Invalid request payload",
    statusCode: Int = 400,
    responseTime: FiniteDuration = 50.millis
  ): RestClientEnvelopeRes[ErrorResponse] =
    given ClassTag[ErrorResponse] = ClassTag(classOf[ErrorResponse])
    RestClientEnvelopeRes(
      statusCode = statusCode,
      body = ErrorResponse(code, message),
      headers = Map("Content-Type" -> "application/json"),
      responseTime = responseTime
    )

  // JSON samples for WireMock
  val orderResponseJson: String =
    """{"orderId":"order-456","status":"CREATED","total":99.99}"""

  val errorResponseJson: String =
    """{"code":"INVALID_REQUEST","message":"Invalid request payload"}"""

  val orderRequestJson: String =
    """{"productId":"prod-123","quantity":2}"""

  // Sample exceptions
  def createHttpError(
    statusCode: Int = 500,
    body: String = """{"error":"Internal Server Error"}"""
  ): RestHttpError =
    RestHttpError(statusCode, body)

  def createConnectionError(
    uri: String = ordersUri.toString,
    message: String = "Connection refused"
  ): RestConnectionError =
    RestConnectionError(uri, new RuntimeException(message))

  def createTimeoutError(
    uri: String = ordersUri.toString,
    timeout: FiniteDuration = 30.seconds
  ): RestTimeoutError =
    RestTimeoutError(uri, timeout)

  def createSerializationError(
    operation: String = "deserialize",
    message: String = "Invalid JSON"
  ): RestSerializationError =
    RestSerializationError(operation, new RuntimeException(message))
