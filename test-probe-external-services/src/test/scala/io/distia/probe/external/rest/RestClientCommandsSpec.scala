package io.distia.probe.external.rest

import io.distia.probe.external.fixtures.RestClientFixtures
import io.distia.probe.external.fixtures.RestClientFixtures.*
import io.distia.probe.external.rest.RestClientCommands.*
import io.distia.probe.common.models.ProbeExternalActorCommand
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*
import scala.reflect.ClassTag

class RestClientCommandsSpec extends AnyWordSpec with Matchers:

  "RestClientCommand" should:

    "extend ProbeExternalActorCommand" in:
      // ExecuteRequest is the only public command type
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      val request = createOrderRequest()
      val cmd: RestClientCommand = ExecuteRequest(
        request = request,
        responseClass = classOf[OrderResponse],
        replyTo = null // Will be tested with ActorTestKit
      )

      cmd shouldBe a[ProbeExternalActorCommand]


  "ExecuteRequest" should:

    "hold request envelope and response class" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      val request = createOrderRequest()
      val cmd = ExecuteRequest(
        request = request,
        responseClass = classOf[OrderResponse],
        replyTo = null
      )

      cmd.request shouldBe request
      cmd.responseClass shouldBe classOf[OrderResponse]

    "preserve type information" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      val request = createOrderRequest("custom-product", 10)
      val cmd = ExecuteRequest(
        request = request,
        responseClass = classOf[OrderResponse],
        replyTo = null
      )

      cmd.request.payload.productId shouldBe "custom-product"
      cmd.request.payload.quantity shouldBe 10


  "RestClientResult" should:

    "be covariant in type parameter" in:
      val success: RestClientResult[OrderResponse] = RestClientSuccess(createOrderResponse())
      val result: RestClientResult[Any] = success

      result shouldBe a[RestClientSuccess[?]]


  "RestClientSuccess" should:

    "hold response envelope" in:
      val response = createOrderResponse()
      val success = RestClientSuccess(response)

      success.response shouldBe response
      success.response.body.orderId shouldBe "order-456"

    "be a RestClientResult" in:
      val success: RestClientResult[OrderResponse] = RestClientSuccess(createOrderResponse())

      success shouldBe a[RestClientResult[?]]


  "RestClientFailure" should:

    "hold RestClientException" in:
      val exception = createHttpError(500)
      val failure = RestClientFailure(exception)

      failure.exception shouldBe exception
      failure.exception.asInstanceOf[RestHttpError].statusCode shouldBe 500

    "be a RestClientResult for any type (Nothing covariance)" in:
      val failure: RestClientResult[OrderResponse] = RestClientFailure(createHttpError(400))

      failure shouldBe a[RestClientResult[?]]

    "work with all exception types" in:
      val httpError: RestClientResult[Any] = RestClientFailure(createHttpError(500))
      val connError: RestClientResult[Any] = RestClientFailure(createConnectionError())
      val timeoutError: RestClientResult[Any] = RestClientFailure(createTimeoutError())
      val serError: RestClientResult[Any] = RestClientFailure(createSerializationError())

      httpError shouldBe a[RestClientFailure]
      connError shouldBe a[RestClientFailure]
      timeoutError shouldBe a[RestClientFailure]
      serError shouldBe a[RestClientFailure]


  "RestClientResult pattern matching" should:

    "support exhaustive matching" in:
      def handleResult[T](result: RestClientResult[T]): String = result match
        case RestClientSuccess(response) => s"Success: ${response.statusCode}"
        case RestClientFailure(ex: RestHttpError) => s"HTTP Error: ${ex.statusCode}"
        case RestClientFailure(ex: RestConnectionError) => s"Connection Error: ${ex.uri}"
        case RestClientFailure(ex: RestTimeoutError) => s"Timeout: ${ex.timeout}"
        case RestClientFailure(ex: RestSerializationError) => s"Serialization: ${ex.operation}"

      handleResult(RestClientSuccess(createOrderResponse())) should startWith("Success")
      handleResult(RestClientFailure(createHttpError(404))) should include("404")
      handleResult(RestClientFailure(createConnectionError("http://test"))) should include("http://test")
      handleResult(RestClientFailure(createTimeoutError(timeout = 15.seconds))) should include("15 seconds")
      handleResult(RestClientFailure(createSerializationError("serialize"))) should include("serialize")

    "support simplified success/failure matching" in:
      def isSuccessful[T](result: RestClientResult[T]): Boolean = result match
        case _: RestClientSuccess[?] => true
        case _: RestClientFailure => false

      isSuccessful(RestClientSuccess(createOrderResponse())) shouldBe true
      isSuccessful(RestClientFailure(createHttpError(500))) shouldBe false
