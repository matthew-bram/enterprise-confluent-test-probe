package io.distia.probe.external.rest

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.distia.probe.external.fixtures.RestClientFixtures
import io.distia.probe.external.fixtures.RestClientFixtures.*
import io.distia.probe.external.fixtures.WireMockRestClientFactory
import io.distia.probe.external.rest.RestClientCommands.*
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import java.net.URI
import scala.concurrent.duration.*
import scala.reflect.ClassTag

/**
 * Integration tests for RestClientActor using WireMock.
 *
 * These tests verify actual HTTP request/response behavior with a mock server.
 */
class RestClientActorIntegrationSpec extends AnyWordSpec
  with Matchers
  with BeforeAndAfterAll
  with BeforeAndAfterEach:

  val wireMockServer: WireMockServer = new WireMockServer(wireMockConfig().dynamicPort())
  val testKit: ActorTestKit = ActorTestKit()

  override def beforeAll(): Unit =
    wireMockServer.start()
    super.beforeAll()

  override def afterAll(): Unit =
    wireMockServer.stop()
    testKit.shutdownTestKit()
    super.afterAll()

  override def beforeEach(): Unit =
    wireMockServer.resetAll()
    super.beforeEach()

  def baseUrl: String = s"http://localhost:${wireMockServer.port()}"

  "RestClientActor with WireMock" should:

    "complete POST request successfully" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      wireMockServer.stubFor(
        post(urlEqualTo("/api/orders"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(orderResponseJson)
          )
      )

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-post")

      val request = RestClientEnvelopeReq(
        payload = OrderRequest("prod-123", 2),
        uri = URI.create(s"$baseUrl/api/orders"),
        method = HttpMethod.POST,
        headers = Map("Content-Type" -> "application/json")
      )

      actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

      val result = probe.receiveMessage(5.seconds)
      result shouldBe a[RestClientSuccess[?]]

      val success = result.asInstanceOf[RestClientSuccess[OrderResponse]]
      success.response.statusCode shouldBe 200
      success.response.body.orderId shouldBe "order-456"
      success.response.body.status shouldBe "CREATED"
      success.response.body.total shouldBe 99.99
      success.response.isSuccess shouldBe true

    "complete GET request successfully" in:
      given ClassTag[EmptyPayload] = ClassTag(classOf[EmptyPayload])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      wireMockServer.stubFor(
        get(urlEqualTo("/api/orders/123"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(orderResponseJson)
          )
      )

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-get")

      val request = RestClientEnvelopeReq(
        payload = EmptyPayload(),
        uri = URI.create(s"$baseUrl/api/orders/123"),
        method = HttpMethod.GET
      )

      actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

      val result = probe.receiveMessage(5.seconds)
      result shouldBe a[RestClientSuccess[?]]
      result.asInstanceOf[RestClientSuccess[OrderResponse]].response.body.orderId shouldBe "order-456"

    "complete PUT request successfully" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      wireMockServer.stubFor(
        put(urlEqualTo("/api/orders/123"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody("""{"orderId":"order-123","status":"UPDATED","total":150.00}""")
          )
      )

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-put")

      val request = RestClientEnvelopeReq(
        payload = OrderRequest("prod-123", 5),
        uri = URI.create(s"$baseUrl/api/orders/123"),
        method = HttpMethod.PUT
      )

      actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

      val result = probe.receiveMessage(5.seconds)
      result shouldBe a[RestClientSuccess[?]]
      result.asInstanceOf[RestClientSuccess[OrderResponse]].response.body.status shouldBe "UPDATED"

    "complete DELETE request successfully" in:
      given ClassTag[EmptyPayload] = ClassTag(classOf[EmptyPayload])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      wireMockServer.stubFor(
        delete(urlEqualTo("/api/orders/123"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody("""{"orderId":"order-123","status":"DELETED","total":0.0}""")
          )
      )

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-delete")

      val request = RestClientEnvelopeReq(
        payload = EmptyPayload(),
        uri = URI.create(s"$baseUrl/api/orders/123"),
        method = HttpMethod.DELETE
      )

      actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

      val result = probe.receiveMessage(5.seconds)
      result shouldBe a[RestClientSuccess[?]]
      result.asInstanceOf[RestClientSuccess[OrderResponse]].response.body.status shouldBe "DELETED"

    "return RestClientFailure with RestHttpError for 4xx" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      wireMockServer.stubFor(
        post(urlEqualTo("/api/orders"))
          .willReturn(
            aResponse()
              .withStatus(400)
              .withHeader("Content-Type", "application/json")
              .withBody(errorResponseJson)
          )
      )

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-4xx")

      val request = RestClientEnvelopeReq(
        payload = OrderRequest("", -1), // Invalid request
        uri = URI.create(s"$baseUrl/api/orders"),
        method = HttpMethod.POST
      )

      actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

      val result = probe.receiveMessage(5.seconds)
      result shouldBe a[RestClientFailure]

      val failure = result.asInstanceOf[RestClientFailure]
      failure.exception shouldBe a[RestHttpError]

      val httpError = failure.exception.asInstanceOf[RestHttpError]
      httpError.statusCode shouldBe 400
      httpError.body should include("INVALID_REQUEST")

    "return RestClientFailure with RestHttpError for 5xx" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      wireMockServer.stubFor(
        post(urlEqualTo("/api/orders"))
          .willReturn(
            aResponse()
              .withStatus(500)
              .withHeader("Content-Type", "application/json")
              .withBody("""{"error":"Internal Server Error"}""")
          )
      )

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-5xx")

      val request = RestClientEnvelopeReq(
        payload = OrderRequest("prod-1", 1),
        uri = URI.create(s"$baseUrl/api/orders"),
        method = HttpMethod.POST
      )

      actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

      val result = probe.receiveMessage(5.seconds)
      result shouldBe a[RestClientFailure]

      val httpError = result.asInstanceOf[RestClientFailure].exception.asInstanceOf[RestHttpError]
      httpError.statusCode shouldBe 500

    "include custom headers in request" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      wireMockServer.stubFor(
        post(urlEqualTo("/api/orders"))
          .withHeader("Authorization", equalTo("Bearer test-token"))
          .withHeader("X-Request-Id", equalTo("req-12345"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(orderResponseJson)
          )
      )

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-headers")

      val request = RestClientEnvelopeReq(
        payload = OrderRequest("prod-1", 1),
        uri = URI.create(s"$baseUrl/api/orders"),
        method = HttpMethod.POST,
        headers = Map(
          "Authorization" -> "Bearer test-token",
          "X-Request-Id" -> "req-12345"
        )
      )

      actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

      val result = probe.receiveMessage(5.seconds)
      result shouldBe a[RestClientSuccess[?]]

    "serialize request body as JSON" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      wireMockServer.stubFor(
        post(urlEqualTo("/api/orders"))
          .withRequestBody(matchingJsonPath("$.productId", equalTo("special-product")))
          .withRequestBody(matchingJsonPath("$.quantity", equalTo("42")))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(orderResponseJson)
          )
      )

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-json-body")

      val request = RestClientEnvelopeReq(
        payload = OrderRequest("special-product", 42),
        uri = URI.create(s"$baseUrl/api/orders"),
        method = HttpMethod.POST
      )

      actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

      val result = probe.receiveMessage(5.seconds)
      result shouldBe a[RestClientSuccess[?]]

    "track response time" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      wireMockServer.stubFor(
        post(urlEqualTo("/api/orders"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(orderResponseJson)
              .withFixedDelay(100) // 100ms delay
          )
      )

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-timing")

      val request = RestClientEnvelopeReq(
        payload = OrderRequest("prod-1", 1),
        uri = URI.create(s"$baseUrl/api/orders"),
        method = HttpMethod.POST
      )

      actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

      val result = probe.receiveMessage(5.seconds)
      result shouldBe a[RestClientSuccess[?]]

      val success = result.asInstanceOf[RestClientSuccess[OrderResponse]]
      success.response.responseTime.toMillis should be >= 100L

    "capture response headers" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      wireMockServer.stubFor(
        post(urlEqualTo("/api/orders"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withHeader("X-Request-Id", "resp-12345")
              .withHeader("X-Correlation-Id", "corr-67890")
              .withBody(orderResponseJson)
          )
      )

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-resp-headers")

      val request = RestClientEnvelopeReq(
        payload = OrderRequest("prod-1", 1),
        uri = URI.create(s"$baseUrl/api/orders"),
        method = HttpMethod.POST
      )

      actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

      val result = probe.receiveMessage(5.seconds)
      result shouldBe a[RestClientSuccess[?]]

      val success = result.asInstanceOf[RestClientSuccess[OrderResponse]]
      success.response.headers should contain key "X-Request-Id"
      success.response.headers should contain key "X-Correlation-Id"

    "handle connection reset" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      wireMockServer.stubFor(
        post(urlEqualTo("/api/orders"))
          .willReturn(
            aResponse()
              .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)
          )
      )

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-conn-reset")

      val request = RestClientEnvelopeReq(
        payload = OrderRequest("prod-1", 1),
        uri = URI.create(s"$baseUrl/api/orders"),
        method = HttpMethod.POST
      )

      actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

      val result = probe.receiveMessage(5.seconds)
      result shouldBe a[RestClientFailure]

      val failure = result.asInstanceOf[RestClientFailure]
      failure.exception shouldBe a[RestConnectionError]

    "handle 201 Created response" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      wireMockServer.stubFor(
        post(urlEqualTo("/api/orders"))
          .willReturn(
            aResponse()
              .withStatus(201)
              .withHeader("Content-Type", "application/json")
              .withBody("""{"orderId":"new-order","status":"CREATED","total":50.0}""")
          )
      )

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-201")

      val request = RestClientEnvelopeReq(
        payload = OrderRequest("prod-1", 1),
        uri = URI.create(s"$baseUrl/api/orders"),
        method = HttpMethod.POST
      )

      actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

      val result = probe.receiveMessage(5.seconds)
      result shouldBe a[RestClientSuccess[?]]
      result.asInstanceOf[RestClientSuccess[OrderResponse]].response.statusCode shouldBe 201

    "handle concurrent requests to different endpoints" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])
      given ClassTag[EmptyPayload] = ClassTag(classOf[EmptyPayload])

      wireMockServer.stubFor(
        post(urlEqualTo("/api/orders"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody("""{"orderId":"order-1","status":"CREATED","total":10.0}""")
          )
      )

      wireMockServer.stubFor(
        get(urlEqualTo("/api/users"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody("""{"orderId":"user-1","status":"ACTIVE","total":0.0}""")
          )
      )

      val probe1 = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val probe2 = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "wiremock-concurrent")

      val postRequest = RestClientEnvelopeReq(
        payload = OrderRequest("prod-1", 1),
        uri = URI.create(s"$baseUrl/api/orders"),
        method = HttpMethod.POST
      )

      val getRequest = RestClientEnvelopeReq(
        payload = EmptyPayload(),
        uri = URI.create(s"$baseUrl/api/users"),
        method = HttpMethod.GET
      )

      // Send both requests
      actor ! ExecuteRequest(postRequest, classOf[OrderResponse], probe1.ref)
      actor ! ExecuteRequest(getRequest, classOf[OrderResponse], probe2.ref)

      // Both should complete successfully
      val result1 = probe1.receiveMessage(5.seconds)
      val result2 = probe2.receiveMessage(5.seconds)

      result1 shouldBe a[RestClientSuccess[?]]
      result2 shouldBe a[RestClientSuccess[?]]

      result1.asInstanceOf[RestClientSuccess[OrderResponse]].response.body.orderId shouldBe "order-1"
      result2.asInstanceOf[RestClientSuccess[OrderResponse]].response.body.orderId shouldBe "user-1"
