package io.distia.probe.external.rest

import java.net.URI
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag
import scala.util.Try

/**
 * Strongly typed request envelope for REST client calls.
 *
 * Contains the payload T along with REST-specific concerns like URI, method, and headers.
 * Uses ClassTag for type-safe Jackson serialization/deserialization.
 *
 * @tparam T The payload type - must have ClassTag for Jackson deserialization
 * @param payload The business data to send (case class or Java POJO)
 * @param uri Target endpoint URI
 * @param method HTTP method (GET, POST, PUT, DELETE, etc.)
 * @param headers Custom HTTP headers
 * @param timeout Per-request timeout override
 */
case class RestClientEnvelopeReq[T: ClassTag](
  payload: T,
  uri: URI,
  method: HttpMethod,
  headers: Map[String, String] = Map.empty,
  timeout: Option[FiniteDuration] = None
):
  /** Serialize payload to JSON using Jackson */
  def toJson: String = RestClientObjectMapper.mapper.writeValueAsString(payload)

  /** Runtime class for type information */
  def payloadClass: Class[T] = summon[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]

object RestClientEnvelopeReq:
  /**
   * Java-friendly factory method that accepts Class[T] explicitly.
   * Enables Java teams to create envelopes without Scala ClassTag magic.
   *
   * @param payload The business data to send
   * @param uri Target endpoint URI
   * @param method HTTP method
   * @param headers Custom HTTP headers as Java Map
   * @param clazz Runtime class for type information
   * @return RestClientEnvelopeReq configured with provided parameters
   */
  def create[T](
    payload: T,
    uri: URI,
    method: HttpMethod,
    headers: java.util.Map[String, String],
    clazz: Class[T]
  ): RestClientEnvelopeReq[T] =
    given ClassTag[T] = ClassTag(clazz)
    RestClientEnvelopeReq(
      payload = payload,
      uri = uri,
      method = method,
      headers = scala.jdk.CollectionConverters.MapHasAsScala(headers).asScala.toMap
    )

  /** Java-friendly overload with default empty headers */
  def create[T](
    payload: T,
    uri: URI,
    method: HttpMethod,
    clazz: Class[T]
  ): RestClientEnvelopeReq[T] =
    create(payload, uri, method, java.util.Collections.emptyMap(), clazz)


/**
 * Strongly typed response envelope from REST client calls.
 *
 * Contains the deserialized response body along with HTTP metadata.
 *
 * @tparam T The response body type
 * @param statusCode HTTP status code
 * @param body Deserialized response body
 * @param headers Response headers
 * @param responseTime Time taken for the request
 */
case class RestClientEnvelopeRes[T: ClassTag](
  statusCode: Int,
  body: T,
  headers: Map[String, String] = Map.empty,
  responseTime: FiniteDuration
):
  /** Runtime class for type information */
  def bodyClass: Class[T] = summon[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]

  /** Check if response indicates success (2xx) */
  def isSuccess: Boolean = statusCode >= 200 && statusCode < 300

  /** Check if response indicates client error (4xx) */
  def isClientError: Boolean = statusCode >= 400 && statusCode < 500

  /** Check if response indicates server error (5xx) */
  def isServerError: Boolean = statusCode >= 500

object RestClientEnvelopeRes:
  /**
   * Deserialize JSON response body to type T.
   * Used internally by the REST client actor.
   *
   * @param statusCode HTTP status code
   * @param json JSON string to deserialize
   * @param headers Response headers
   * @param responseTime Time taken for the request
   * @return RestClientEnvelopeRes with deserialized body
   */
  def fromJson[T: ClassTag](
    statusCode: Int,
    json: String,
    headers: Map[String, String],
    responseTime: FiniteDuration
  ): RestClientEnvelopeRes[T] =
    val body = RestClientObjectMapper.mapper.readValue(
      json,
      summon[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    )
    RestClientEnvelopeRes(statusCode, body, headers, responseTime)

  /**
   * Try to deserialize, returning Try for error handling.
   *
   * @param statusCode HTTP status code
   * @param json JSON string to deserialize
   * @param headers Response headers
   * @param responseTime Time taken for the request
   * @return Try containing RestClientEnvelopeRes or failure
   */
  def tryFromJson[T: ClassTag](
    statusCode: Int,
    json: String,
    headers: Map[String, String],
    responseTime: FiniteDuration
  ): Try[RestClientEnvelopeRes[T]] =
    Try(fromJson[T](statusCode, json, headers, responseTime))

  /**
   * Java-friendly factory method.
   *
   * @param statusCode HTTP status code
   * @param json JSON string to deserialize
   * @param headers Response headers as Java Map
   * @param responseTimeMillis Response time in milliseconds
   * @param clazz Runtime class for deserialization
   * @return RestClientEnvelopeRes with deserialized body
   */
  def create[T](
    statusCode: Int,
    json: String,
    headers: java.util.Map[String, String],
    responseTimeMillis: Long,
    clazz: Class[T]
  ): RestClientEnvelopeRes[T] =
    import scala.concurrent.duration.*
    given ClassTag[T] = ClassTag(clazz)
    fromJson[T](
      statusCode,
      json,
      scala.jdk.CollectionConverters.MapHasAsScala(headers).asScala.toMap,
      responseTimeMillis.millis
    )
