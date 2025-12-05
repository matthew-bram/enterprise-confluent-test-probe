package io.distia.probe
package core
package pubsub

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.json.{KafkaJsonSchemaDeserializer, KafkaJsonSchemaDeserializerConfig}
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import com.fasterxml.jackson.databind.{DeserializationFeature, JsonNode, ObjectMapper}
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}

import scala.reflect.ClassTag
import scala.jdk.CollectionConverters.*

/**
 * Scala-friendly wrapper for Confluent's KafkaJsonSchemaDeserializer.
 *
 * Confluent 8.1.0: Clean implementation, no workarounds!
 *
 * Handles polymorphic deserialization via @JsonTypeInfo annotations.
 * Uses local ObjectMapper for type-safe deserialization with ClassTag.
 *
 * Thread-safety: Creates new ObjectMapper per deserialization (safe, but cacheable)
 */
class ScalaConfluentJsonDeserializer[T: ClassTag](
  schemaRegistryClient: SchemaRegistryClient,
  schemaRegistryUrl: String,
  isKey: Boolean
):

  private val des = new KafkaJsonSchemaDeserializer[JsonNode](schemaRegistryClient)

  des.configure(Map(
    "schema.registry.url" -> schemaRegistryUrl,
    KafkaJsonSchemaDeserializerConfig.JSON_KEY_TYPE -> classOf[JsonNode].getName,
    KafkaJsonSchemaDeserializerConfig.JSON_VALUE_TYPE -> classOf[JsonNode].getName,
    "json.fail.unknown.properties" -> "false",
    "key.subject.name.strategy" -> classOf[TopicRecordNameStrategy].getName,
    "value.subject.name.strategy" -> classOf[TopicRecordNameStrategy].getName,
    "latest.cache.size" -> "5000",
    "latest.cache.ttl.sec" -> "3600"
  ).asJava, isKey)

  /**
   * Create ObjectMapper configured for Scala types.
   *
   * Note: Could be cached for performance, but creating new is safe.
   */
  private lazy val mapper: ObjectMapper =
    val m = JsonMapper.builder()
      .addModule(DefaultScalaModule)
      .build() :: ClassTagExtensions
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    m

  /**
   * Deserialize bytes to typed value.
   *
   * @param topic Kafka topic
   * @param bytes Serialized bytes
   * @return Deserialized value
   */
  def deserialize(topic: String, bytes: Array[Byte]): T =
    val node: JsonNode = des.deserialize(topic, bytes)
    mapper.treeToValue(node, summon[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])