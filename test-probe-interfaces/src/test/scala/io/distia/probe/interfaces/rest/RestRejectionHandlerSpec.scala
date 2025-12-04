package io.distia.probe.interfaces.rest

import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.{RejectionHandler, Route}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.apache.pekko.http.scaladsl.server.ValidationRejection
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json._

/**
 * Unit tests for RestRejectionHandler
 *
 * Target Coverage: All rejection handling paths (currently 42.55% → 90%+)
 *
 * Tests:
 * - Malformed request content → 400
 * - Unsupported Content-Type → 415
 * - Validation rejection → 400
 * - Missing query parameter → 400
 * - Method not allowed → 405
 * - Endpoint not found → 404
 */
class RestRejectionHandlerSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  import RestErrorResponse._

  "RestRejectionHandler" when {

    // ========================================
    // Malformed Request Content (Invalid JSON)
    // ========================================

    "handling MalformedRequestContentRejection" should {

      "return 400 Bad Request for invalid JSON" in {
        import JsonFormats._

        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            post {
              entity(as[io.distia.probe.interfaces.models.rest.RestInitializeTestRequest]) { _ =>
                complete(StatusCodes.OK, "success")
              }
            }
          }
        }

        val invalidJson = HttpEntity(ContentTypes.`application/json`, "{invalid json")

        Post("/test", invalidJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "bad_request"
          response.message should include("Invalid request body")
        }
      }

      "include JSON parsing error details in response" in {
        import JsonFormats._

        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            post {
              entity(as[io.distia.probe.interfaces.models.rest.RestInitializeTestRequest]) { _ =>
                complete(StatusCodes.OK, "success")
              }
            }
          }
        }

        val invalidJson = HttpEntity(ContentTypes.`application/json`, "{unclosed")

        Post("/test", invalidJson) ~> routes ~> check {
          val response = responseAs[RestErrorResponse]
          response.message should include("Invalid request body")
        }
      }
    }

    // ========================================
    // Unsupported Content-Type
    // ========================================

    "handling UnsupportedRequestContentTypeRejection" should {

      "return 415 Unsupported Media Type for text/plain" in {
        import JsonFormats._

        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            post {
              entity(as[io.distia.probe.interfaces.models.rest.RestInitializeTestRequest]) { _ =>
                complete(StatusCodes.OK, "success")
              }
            }
          }
        }

        val textEntity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "some text")

        Post("/test", textEntity) ~> routes ~> check {
          status shouldBe StatusCodes.UnsupportedMediaType

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "unsupported_media_type"
          response.message should include("Content-Type must be")
        }
      }

      "list supported Content-Types in error message" in {
        import JsonFormats._

        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            post {
              entity(as[io.distia.probe.interfaces.models.rest.RestInitializeTestRequest]) { _ =>
                complete(StatusCodes.OK, "success")
              }
            }
          }
        }

        val textEntity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "data")

        Post("/test", textEntity) ~> routes ~> check {
          val response = responseAs[RestErrorResponse]
          response.message should include("application/json")
        }
      }
    }

    // ========================================
    // Validation Rejection
    // ========================================

    "handling ValidationRejection" should {

      "return 400 Bad Request for failed validation" in {
        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            get {
              validate(false, "Test ID must be a valid UUID") {
                complete(StatusCodes.OK, "success")
              }
            }
          }
        }

        Get("/test") ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "validation_error"
          response.message shouldBe "Request validation failed"
          response.details shouldBe Some("Test ID must be a valid UUID")
        }
      }

      "include validation failure reason in details" in {
        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            get {
              validate(1 == 2, "Expected 1 to equal 2") {
                complete(StatusCodes.OK, "success")
              }
            }
          }
        }

        Get("/test") ~> routes ~> check {
          val response = responseAs[RestErrorResponse]
          response.details shouldBe Some("Expected 1 to equal 2")
        }
      }
    }

    // ========================================
    // Missing Query Parameter
    // ========================================

    "handling MissingQueryParamRejection" should {

      "return 400 Bad Request for missing required query param" in {
        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            get {
              parameters("testId") { testId =>
                complete(StatusCodes.OK, s"testId=$testId")
              }
            }
          }
        }

        Get("/test") ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "bad_request"
          response.message should include("Required query parameter missing")
          response.message should include("testId")
        }
      }

      "include parameter name in error message" in {
        val routes = handleRejections(RestRejectionHandler.handler) {
          path("search") {
            get {
              parameters("query", "limit") { (query, limit) =>
                complete(StatusCodes.OK, s"query=$query, limit=$limit")
              }
            }
          }
        }

        // Missing both parameters, but only first rejection is handled
        Get("/search") ~> routes ~> check {
          val response = responseAs[RestErrorResponse]
          response.message should (include("query") or include("limit"))
        }
      }
    }

    // ========================================
    // Method Rejection (Wrong HTTP Method)
    // ========================================

    "handling MethodRejection" should {

      "return 405 Method Not Allowed for wrong HTTP method" in {
        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            post {
              complete(StatusCodes.OK, "success")
            }
          }
        }

        Get("/test") ~> routes ~> check {
          status shouldBe StatusCodes.MethodNotAllowed

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "method_not_allowed"
          response.message should include("Supported methods")
        }
      }

      "list supported methods in error message" in {
        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            post {
              complete(StatusCodes.OK, "created")
            }
          }
        }

        Get("/test") ~> routes ~> check {
          val response = responseAs[RestErrorResponse]
          response.message should include("POST")
        }
      }

      "handle multiple supported methods" in {
        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            (get | post) {
              complete(StatusCodes.OK, "success")
            }
          }
        }

        Delete("/test") ~> routes ~> check {
          status shouldBe StatusCodes.MethodNotAllowed

          val response = responseAs[RestErrorResponse]
          response.message should (include("GET") and include("POST"))
        }
      }
    }

    // ========================================
    // Not Found (Endpoint Does Not Exist)
    // ========================================

    "handling NotFound rejection" should {

      "return 404 Not Found for non-existent endpoint" in {
        val routes = handleRejections(RestRejectionHandler.handler) {
          pathPrefix("api" / "v1") {
            path("test") {
              get {
                complete(StatusCodes.OK, "success")
              }
            }
          }
        }

        Get("/api/v1/nonexistent") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound

          val response = responseAs[RestErrorResponse]
          response.error shouldBe "not_found"
          response.message shouldBe "The requested endpoint does not exist"
        }
      }

      "handle root path not found" in {
        val routes = handleRejections(RestRejectionHandler.handler) {
          path("api") {
            get {
              complete(StatusCodes.OK, "api root")
            }
          }
        }

        Get("/") ~> routes ~> check {
          status shouldBe StatusCodes.NotFound
          val response = responseAs[RestErrorResponse]
          response.error shouldBe "not_found"
        }
      }
    }

    // ========================================
    // Handler Creation
    // ========================================

    "handler method" should {

      "create RejectionHandler instance" in {
        val handler = RestRejectionHandler.handler
        handler should not be null
      }

      "be reusable across multiple routes" in {
        val handler = RestRejectionHandler.handler

        val route1 = handleRejections(handler) {
          path("test1") {
            get {
              complete(StatusCodes.OK, "test1")
            }
          }
        }

        val route2 = handleRejections(handler) {
          path("test2") {
            get {
              complete(StatusCodes.OK, "test2")
            }
          }
        }

        Get("/nonexistent") ~> route1 ~> check {
          status shouldBe StatusCodes.NotFound
        }

        Get("/nonexistent") ~> route2 ~> check {
          status shouldBe StatusCodes.NotFound
        }
      }
    }

    // ========================================
    // Response Format Validation
    // ========================================

    "all error responses" should {

      "return well-formed JSON" in {
        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            post {
              complete(StatusCodes.OK, "success")
            }
          }
        }

        Get("/test") ~> routes ~> check {
          contentType.mediaType.mainType shouldBe "application"
          contentType.mediaType.subType shouldBe "json"

          val json = responseAs[String].parseJson.asJsObject
          json.fields should contain key "error"
          json.fields should contain key "message"
          json.fields should contain key "timestamp"
        }
      }

      "include timestamp in Unix epoch milliseconds" in {
        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            post {
              complete(StatusCodes.OK, "success")
            }
          }
        }

        Get("/test") ~> routes ~> check {
          val response = responseAs[RestErrorResponse]
          response.timestamp should be > 1600000000000L // After Sept 2020
          response.timestamp should be < 2000000000000L // Before May 2033
        }
      }

      "include error type as snake_case string" in {
        val routes = handleRejections(RestRejectionHandler.handler) {
          path("test") {
            post {
              validate(false, "test validation") {
                complete(StatusCodes.OK, "success")
              }
            }
          }
        }

        Get("/test") ~> routes ~> check {
          val response = responseAs[RestErrorResponse]
          response.error should (be("method_not_allowed") or be("validation_error"))
        }
      }
    }
  }
}
