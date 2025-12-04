package io.distia.probe
package core
package pubsub

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}

import scala.reflect.ClassTag
import scala.jdk.CollectionConverters.*

/**
 * Scala-friendly wrapper for Confluent's KafkaJsonSchemaSerializer.
 *
 * Confluent 8.1.0: No static ObjectMapper workaround needed!
 *
 * Configures:
 * - Jackson 2.16.2 with DefaultScalaModule for Scala 3.3.6 LTS
 * - TopicRecordNameStrategy for multi-event-type topics
 * - Production-ready settings (validation, normalization, etc.)
 *
 * Thread-safety: Each instance has its own ObjectMapper (8.1.0 fix)
 */
class ScalaConfluentJsonSerializer[T: ClassTag](
  schemaRegistryClient: SchemaRegistryClient,
  schemaRegistryUrl: String
) extends KafkaJsonSchemaSerializer[T](schemaRegistryClient):

  /**
   * Configure ObjectMapper with Scala module and production settings.
   *
   * Confluent 8.1.0: Directly configure - no workaround needed!
   */
  private def configureObjectMapper(): Unit =
    objectMapper()
      .registerModule(DefaultScalaModule)
      .setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      :: ClassTagExtensions

  /**
   * Serialize value to bytes.
   *
   * @param topic Kafka topic
   * @param t Value to serialize
   * @param isKey Whether this is a key
   * @return Serialized bytes
   */
  def serialize(topic: String, t: T, isKey: Boolean = false): Array[Byte] =
    configure(Map(
      "schema.registry.url" -> schemaRegistryUrl,
      "auto.register.schemas" -> "false",
      "use.latest.version" -> "true",
      "normalize.schemas" -> "true",
      "json.fail.invalid.schema" -> "false",
      "json.oneof.for.nullables" -> "false",
      "json.schema.spec.version" -> "draft_2020_12",
      "key.subject.name.strategy" -> classOf[TopicRecordNameStrategy].getName,
      "value.subject.name.strategy" -> classOf[TopicRecordNameStrategy].getName,
      "latest.cache.size" -> "5000",
      "latest.cache.ttl.sec" -> "3600",
      "latest.compatibility.strict" -> "false"  // Allow using registered schema even if auto-generated differs
    ).asJava, isKey)
    configureObjectMapper()
    serialize(topic, t)