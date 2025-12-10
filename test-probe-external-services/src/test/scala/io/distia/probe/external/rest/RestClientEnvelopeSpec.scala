package io.distia.probe.external.rest

import io.distia.probe.external.fixtures.RestClientFixtures
import io.distia.probe.external.fixtures.RestClientFixtures.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.URI
import scala.concurrent.duration.*
import scala.reflect.ClassTag

class RestClientEnvelopeSpec extends AnyWordSpec with Matchers:

  "RestClientEnvelopeReq" should:

    "hold request data correctly" in:
      val request = createOrderRequest()

      request.payload.productId shouldBe "prod-123"
      request.payload.quantity shouldBe 2
      request.uri shouldBe ordersUri
      request.method shouldBe HttpMethod.POST
      request.headers shouldBe authHeaders

    "serialize payload to JSON" in:
      val request = createOrderRequest()
      val json = request.toJson

      json should include("prod-123")
      json should include("2")

    "provide payload class via ClassTag" in:
      val request = createOrderRequest()

      request.payloadClass shouldBe classOf[OrderRequest]

    "support default empty headers" in:
      val request = RestClientEnvelopeReq(
        payload = OrderRequest("p1", 1),
        uri = ordersUri,
        method = HttpMethod.POST
      )

      request.headers shouldBe empty

    "support custom timeout" in:
      val request = createOrderRequest(timeout = Some(10.seconds))

      request.timeout shouldBe Some(10.seconds)

    "support None timeout (use default)" in:
      val request = createOrderRequest()

      request.timeout shouldBe None

    "support all HTTP methods" in:
      val methods = List(
        HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
        HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.HEAD, HttpMethod.OPTIONS
      )

      methods.foreach { method =>
        val request = RestClientEnvelopeReq(
          payload = EmptyPayload(),
          uri = baseUri,
          method = method
        )
        request.method shouldBe method
      }


  "RestClientEnvelopeReq.create (Java-friendly factory)" should:

    "create request with headers" in:
      val headers = new java.util.HashMap[String, String]()
      headers.put("Authorization", "Bearer token")
      headers.put("X-Custom", "value")

      val request = RestClientEnvelopeReq.create[OrderRequest](
        OrderRequest("prod-1", 3),
        ordersUri,
        HttpMethod.POST,
        headers,
        classOf[OrderRequest]
      )

      request.payload.productId shouldBe "prod-1"
      request.headers("Authorization") shouldBe "Bearer token"
      request.headers("X-Custom") shouldBe "value"

    "create request with empty headers" in:
      val request = RestClientEnvelopeReq.create[OrderRequest](
        OrderRequest("prod-2", 5),
        ordersUri,
        HttpMethod.POST,
        classOf[OrderRequest]
      )

      request.payload.quantity shouldBe 5
      request.headers shouldBe empty

    "provide correct payload class" in:
      val request = RestClientEnvelopeReq.create[OrderRequest](
        OrderRequest("p", 1),
        ordersUri,
        HttpMethod.POST,
        classOf[OrderRequest]
      )

      request.payloadClass shouldBe classOf[OrderRequest]


  "RestClientEnvelopeRes" should:

    "hold response data correctly" in:
      val response = createOrderResponse()

      response.statusCode shouldBe 200
      response.body.orderId shouldBe "order-456"
      response.body.status shouldBe "CREATED"
      response.body.total shouldBe 99.99
      response.responseTime shouldBe 100.millis

    "identify success responses (2xx)" in:
      (200 to 299).foreach { code =>
        given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])
        val response = RestClientEnvelopeRes(
          statusCode = code,
          body = OrderResponse("o", "s", 0.0),
          responseTime = 10.millis
        )
        response.isSuccess shouldBe true
        response.isClientError shouldBe false
        response.isServerError shouldBe false
      }

    "identify client error responses (4xx)" in:
      (400 to 499).foreach { code =>
        given ClassTag[ErrorResponse] = ClassTag(classOf[ErrorResponse])
        val response = RestClientEnvelopeRes(
          statusCode = code,
          body = ErrorResponse("ERR", "Error"),
          responseTime = 10.millis
        )
        response.isSuccess shouldBe false
        response.isClientError shouldBe true
        response.isServerError shouldBe false
      }

    "identify server error responses (5xx)" in:
      (500 to 599).foreach { code =>
        given ClassTag[ErrorResponse] = ClassTag(classOf[ErrorResponse])
        val response = RestClientEnvelopeRes(
          statusCode = code,
          body = ErrorResponse("ERR", "Error"),
          responseTime = 10.millis
        )
        response.isSuccess shouldBe false
        response.isClientError shouldBe false
        response.isServerError shouldBe true
      }

    "provide body class via ClassTag" in:
      val response = createOrderResponse()

      response.bodyClass shouldBe classOf[OrderResponse]

    "support default empty headers" in:
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])
      val response = RestClientEnvelopeRes(
        statusCode = 200,
        body = OrderResponse("o", "s", 0.0),
        responseTime = 10.millis
      )

      response.headers shouldBe empty


  "RestClientEnvelopeRes.fromJson" should:

    "deserialize JSON response" in:
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      val response = RestClientEnvelopeRes.fromJson[OrderResponse](
        200,
        orderResponseJson,
        Map("Content-Type" -> "application/json"),
        100.millis
      )

      response.statusCode shouldBe 200
      response.body.orderId shouldBe "order-456"
      response.body.status shouldBe "CREATED"
      response.body.total shouldBe 99.99

    "preserve headers and response time" in:
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      val headers = Map("X-Request-Id" -> "req-123")
      val response = RestClientEnvelopeRes.fromJson[OrderResponse](
        200,
        orderResponseJson,
        headers,
        250.millis
      )

      response.headers shouldBe headers
      response.responseTime shouldBe 250.millis


  "RestClientEnvelopeRes.tryFromJson" should:

    "return Success for valid JSON" in:
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      val result = RestClientEnvelopeRes.tryFromJson[OrderResponse](
        200,
        orderResponseJson,
        Map.empty,
        100.millis
      )

      result.isSuccess shouldBe true
      result.get.body.orderId shouldBe "order-456"

    "return Failure for invalid JSON" in:
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      val result = RestClientEnvelopeRes.tryFromJson[OrderResponse](
        200,
        "invalid json {{{",
        Map.empty,
        100.millis
      )

      result.isFailure shouldBe true


  "RestClientEnvelopeRes.create (Java-friendly factory)" should:

    "deserialize JSON with Java Map headers" in:
      val headers = new java.util.HashMap[String, String]()
      headers.put("Content-Type", "application/json")

      val response = RestClientEnvelopeRes.create[OrderResponse](
        200,
        orderResponseJson,
        headers,
        100L,
        classOf[OrderResponse]
      )

      response.statusCode shouldBe 200
      response.body.orderId shouldBe "order-456"
      response.responseTime shouldBe 100.millis
      response.headers("Content-Type") shouldBe "application/json"
