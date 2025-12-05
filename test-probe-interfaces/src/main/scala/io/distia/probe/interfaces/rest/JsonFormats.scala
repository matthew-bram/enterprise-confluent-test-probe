package io.distia.probe.interfaces.rest

import spray.json._
import io.distia.probe.interfaces.models.rest._
import java.util.UUID

/**
 * Spray JSON formats for REST API models
 *
 * Provides JSON serialization/deserialization for all REST models.
 *
 * Visibility: private[interfaces] maintains module encapsulation.
 * All methods remain public for comprehensive unit testing.
 */
private[interfaces] object JsonFormats extends DefaultJsonProtocol {

  // UUID format
  implicit val uuidFormat: JsonFormat[UUID] = new JsonFormat[UUID] {
    def write(uuid: UUID): JsValue = JsString(uuid.toString)
    def read(value: JsValue): UUID = value match {
      case JsString(uuid) => UUID.fromString(uuid)
      case _ => deserializationError("UUID expected")
    }
  }

  // Request formats
  implicit val restInitializeTestRequestFormat: RootJsonFormat[RestInitializeTestRequest] =
    jsonFormat0(RestInitializeTestRequest.apply)

  implicit val restStartTestRequestFormat: RootJsonFormat[RestStartTestRequest] =
    jsonFormat3(RestStartTestRequest.apply)

  implicit val restTestStatusRequestFormat: RootJsonFormat[RestTestStatusRequest] =
    jsonFormat1(RestTestStatusRequest.apply)

  implicit val restQueueStatusRequestFormat: RootJsonFormat[RestQueueStatusRequest] =
    jsonFormat1(RestQueueStatusRequest.apply)

  implicit val restCancelRequestFormat: RootJsonFormat[RestCancelRequest] =
    jsonFormat1(RestCancelRequest.apply)

  // Response formats
  implicit val restInitializeTestResponseFormat: RootJsonFormat[RestInitializeTestResponse] =
    jsonFormat2(RestInitializeTestResponse.apply)

  implicit val restStartTestResponseFormat: RootJsonFormat[RestStartTestResponse] =
    jsonFormat4(RestStartTestResponse.apply)

  implicit val restTestStatusResponseFormat: RootJsonFormat[RestTestStatusResponse] =
    jsonFormat8(RestTestStatusResponse.apply)

  implicit val restQueueStatusResponseFormat: RootJsonFormat[RestQueueStatusResponse] =
    jsonFormat8(RestQueueStatusResponse.apply)

  implicit val restTestCancelledResponseFormat: RootJsonFormat[RestTestCancelledResponse] =
    jsonFormat3(RestTestCancelledResponse.apply)
}
