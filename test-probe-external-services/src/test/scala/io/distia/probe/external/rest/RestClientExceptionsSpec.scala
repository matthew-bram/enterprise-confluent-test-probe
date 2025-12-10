package io.distia.probe.external.rest

import io.distia.probe.external.fixtures.RestClientFixtures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*

class RestClientExceptionsSpec extends AnyWordSpec with Matchers:

  "RestHttpError" should:

    "be a RestClientException" in:
      val error = RestHttpError(500, """{"error":"Internal Server Error"}""")
      error shouldBe a[RestClientException]

    "be a RuntimeException" in:
      val error = RestHttpError(500, """{"error":"Internal Server Error"}""")
      error shouldBe a[RuntimeException]

    "include status code in message" in:
      val error = RestHttpError(404, """{"error":"Not Found"}""")
      error.getMessage should include("404")

    "include default message" in:
      val error = RestHttpError(500, """{}""")
      error.getMessage should include("HTTP request failed")

    "support custom message" in:
      val error = RestHttpError(400, """{}""", "Validation failed")
      error.getMessage should include("Validation failed")

    "preserve status code" in:
      val error = RestHttpError(502, """{}""")
      error.statusCode shouldBe 502

    "preserve body" in:
      val body = """{"code":"ERR001","message":"Something went wrong"}"""
      val error = RestHttpError(500, body)
      error.body shouldBe body

    "support pattern matching in sealed hierarchy" in:
      val exception: RestClientException = RestHttpError(500, "{}")
      val result = exception match
        case RestHttpError(code, _, _) => s"http-$code"
        case RestConnectionError(_, _, _) => "connection"
        case RestTimeoutError(_, _, _) => "timeout"
        case RestSerializationError(_, _, _) => "serialization"

      result shouldBe "http-500"


  "RestConnectionError" should:

    "be a RestClientException" in:
      val error = RestConnectionError("http://localhost:8080", new RuntimeException("Connection refused"))
      error shouldBe a[RestClientException]

    "include URI in message" in:
      val uri = "http://localhost:8080/api"
      val error = RestConnectionError(uri, new RuntimeException("Refused"))
      error.getMessage should include(uri)

    "include default message" in:
      val error = RestConnectionError("http://localhost:8080", new RuntimeException(""))
      error.getMessage should include("Connection failed")

    "preserve cause" in:
      val cause = new RuntimeException("Network unreachable")
      val error = RestConnectionError("http://localhost:8080", cause)
      error.getCause shouldBe cause

    "preserve URI" in:
      val uri = "https://api.example.com/orders"
      val error = RestConnectionError(uri, new RuntimeException(""))
      error.uri shouldBe uri


  "RestTimeoutError" should:

    "be a RestClientException" in:
      val error = RestTimeoutError("http://localhost:8080", 30.seconds)
      error shouldBe a[RestClientException]

    "include URI in message" in:
      val uri = "http://localhost:8080/api"
      val error = RestTimeoutError(uri, 30.seconds)
      error.getMessage should include(uri)

    "include timeout duration in message" in:
      val error = RestTimeoutError("http://localhost:8080", 30.seconds)
      error.getMessage should include("30 seconds")

    "include default message" in:
      val error = RestTimeoutError("http://localhost:8080", 30.seconds)
      error.getMessage should include("Request timed out")

    "preserve timeout duration" in:
      val timeout = 45.seconds
      val error = RestTimeoutError("http://localhost:8080", timeout)
      error.timeout shouldBe timeout


  "RestSerializationError" should:

    "be a RestClientException" in:
      val error = RestSerializationError("deserialize", new RuntimeException("Invalid JSON"))
      error shouldBe a[RestClientException]

    "include operation in message" in:
      val error = RestSerializationError("deserialize", new RuntimeException(""))
      error.getMessage should include("deserialize")

    "include default message" in:
      val error = RestSerializationError("serialize", new RuntimeException(""))
      error.getMessage should include("JSON processing failed")

    "preserve cause" in:
      val cause = new RuntimeException("Unexpected token")
      val error = RestSerializationError("deserialize", cause)
      error.getCause shouldBe cause

    "preserve operation" in:
      val error = RestSerializationError("serialize", new RuntimeException(""))
      error.operation shouldBe "serialize"


  "RestClientException hierarchy" should:

    "support exhaustive pattern matching" in:
      def handleException(ex: RestClientException): String = ex match
        case RestHttpError(code, body, _) => s"HTTP error $code: $body"
        case RestConnectionError(uri, cause, _) => s"Connection to $uri failed: ${cause.getMessage}"
        case RestTimeoutError(uri, timeout, _) => s"Timeout after $timeout to $uri"
        case RestSerializationError(op, cause, _) => s"$op failed: ${cause.getMessage}"

      val httpError = RestHttpError(404, "Not found")
      val connError = RestConnectionError("http://localhost", new RuntimeException("Refused"))
      val timeoutError = RestTimeoutError("http://localhost", 30.seconds)
      val serError = RestSerializationError("deserialize", new RuntimeException("Bad JSON"))

      handleException(httpError) should include("HTTP error 404")
      handleException(connError) should include("Connection to http://localhost failed")
      handleException(timeoutError) should include("Timeout after 30 seconds")
      handleException(serError) should include("deserialize failed")
