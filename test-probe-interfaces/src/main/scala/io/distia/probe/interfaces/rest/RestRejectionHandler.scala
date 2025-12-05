package io.distia.probe.interfaces.rest

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.slf4j.LoggerFactory

/**
 * Custom rejection handler for REST API
 *
 * Maps Pekko HTTP rejections to user-friendly error responses.
 *
 * Common Rejections:
 * - MalformedRequestContentRejection → 400 (invalid JSON)
 * - UnsupportedRequestContentTypeRejection → 415 (wrong Content-Type)
 * - ValidationRejection → 400 (failed validation)
 * - MissingQueryParamRejection → 400 (required param missing)
 * - MethodRejection → 405 (wrong HTTP method)
 * - NotFound → 404 (endpoint not found)
 *
 * Visibility: private[interfaces] maintains module encapsulation.
 * All methods remain public for comprehensive unit testing.
 */
private[interfaces] object RestRejectionHandler {

  import RestErrorResponse._

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Pekko HTTP rejection handler
   *
   * Applied via handleRejections directive in routes.
   */
  def handler: RejectionHandler = RejectionHandler.newBuilder()

    // Invalid JSON or request body
    .handle {
      case MalformedRequestContentRejection(message, _) =>
        logger.warn(s"Malformed request: $message")
        complete(
          StatusCodes.BadRequest,
          badRequest(s"Invalid request body: $message")
        )
    }

    // Wrong Content-Type (e.g., text/plain instead of application/json)
    .handle {
      case UnsupportedRequestContentTypeRejection(supported) =>
        logger.warn(s"Unsupported Content-Type, expected: $supported")
        complete(
          StatusCodes.UnsupportedMediaType,
          unsupportedMediaType(s"Content-Type must be: ${supported.mkString(", ")}")
        )
    }

    // Validation failures (e.g., invalid UUID format)
    .handle {
      case ValidationRejection(message, _) =>
        logger.warn(s"Validation failed: $message")
        complete(
          StatusCodes.BadRequest,
          validationError("Request validation failed", message)
        )
    }

    // Missing required query parameter
    .handle {
      case MissingQueryParamRejection(paramName) =>
        logger.warn(s"Missing query parameter: $paramName")
        complete(
          StatusCodes.BadRequest,
          badRequest(s"Required query parameter missing: $paramName")
        )
    }

    // Wrong HTTP method (e.g., GET to POST endpoint)
    .handleAll[MethodRejection] { rejections =>
      val supportedMethods = rejections.map(_.supported.name).mkString(", ")
      logger.warn(s"Method not allowed, supported: $supportedMethods")
      complete(
        StatusCodes.MethodNotAllowed,
        methodNotAllowed(s"Supported methods: $supportedMethods")
      )
    }

    // Catch-all for other rejections (endpoint not found)
    .handleNotFound {
      logger.warn("Endpoint not found")
      complete(
        StatusCodes.NotFound,
        notFound("The requested endpoint does not exist")
      )
    }

    .result()
}
