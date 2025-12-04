package io.distia.probe
package core
package pubsub

import models.{SchemaNotFoundException, SchemaRegistryNotInitializedException, CloudEvent}

import io.confluent.kafka.schemaregistry.client.{SchemaMetadata, SchemaRegistryClient}
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
import io.confluent.kafka.serializers.protobuf.{KafkaProtobufDeserializer, KafkaProtobufSerializer}
import org.apache.avro.specific.SpecificRecord
import com.google.protobuf.{DynamicMessage, Message}

import scala.reflect.ClassTag
import scala.jdk.CollectionConverters.*

/**
 * SerdesFactory - Unified Serialization/Deserialization Factory for Schema Registry
 *
 * This factory provides type-safe serialization and deserialization for Kafka messages
 * using Confluent Schema Registry. It automatically detects the schema type (Avro, Protobuf,
 * or JSON Schema) based on the registered schema and routes to the appropriate serializer.
 *
 * Supported Schema Types:
 * - AVRO: Apache Avro with SpecificRecord classes or CloudEvent conversion
 * - PROTOBUF: Protocol Buffers with generated Message classes or DynamicMessage
 * - JSON/JSONSCHEMA: JSON Schema with runtime type reflection
 *
 * Schema Subject Naming:
 * Uses TopicRecordNameStrategy which creates subjects as `{topic}-{className}`.
 * This allows multiple record types per topic while maintaining schema evolution.
 *
 * CloudEvent Support:
 * CloudEvent keys are handled specially - they are converted to/from Avro GenericRecord
 * or Protobuf DynamicMessage since CloudEvent is not a generated class.
 *
 * State Management:
 * The factory maintains a singleton SchemaRegistryClient instance. The client must be
 * initialized via setClient() before any serialization operations.
 *
 * Thread Safety:
 * The client and schemaRegistryUrl fields are marked @volatile for safe publication
 * across threads. However, individual serialize/deserialize calls create new serializer
 * instances, which may have performance implications for high-throughput scenarios.
 *
 * @see ProbeScalaDsl for the high-level DSL that uses this factory
 * @see KafkaProducerStreamingActor for producer integration
 * @see KafkaConsumerStreamingActor for consumer integration
 */
object SerdesFactory:

  /** Schema Registry client instance - must be set before serialization operations */
  @volatile private[core] var client: Option[SchemaRegistryClient] = None
  /** Schema Registry URL for serializer configuration */
  @volatile private[core] var schemaRegistryUrl: Option[String] = None

  /**
   * Initialize the Schema Registry client (without URL)
   *
   * Use this overload when the client is already configured with the URL.
   * For full initialization including URL configuration, use the two-parameter overload.
   *
   * @param srClient Configured SchemaRegistryClient instance
   */
  def setClient(srClient: SchemaRegistryClient): Unit = client = Some(srClient)

  /**
   * Initialize the Schema Registry client with URL
   *
   * This is the preferred initialization method as it stores both the client
   * and URL needed for serializer configuration.
   *
   * @param srClient Configured SchemaRegistryClient instance
   * @param url Schema Registry URL (e.g., "http://localhost:8081")
   */
  def setClient(srClient: SchemaRegistryClient, url: String): Unit =
    client = Some(srClient)
    schemaRegistryUrl = Some(url)

  /**
   * Get the Schema Registry client, throwing if not initialized
   *
   * @return The configured SchemaRegistryClient
   * @throws SchemaRegistryNotInitializedException if setClient() was not called
   */
  def extractClient: SchemaRegistryClient = client match
    case Some(c) => c
    case None => throw SchemaRegistryNotInitializedException()

  /**
   * Get the Schema Registry URL, throwing if not initialized
   *
   * @return The Schema Registry URL string
   * @throws SchemaRegistryNotInitializedException if setClient() was not called with URL
   */
  def extractSchemaRegistryUrl: String = schemaRegistryUrl match
    case Some(url) => url
    case None => throw SchemaRegistryNotInitializedException()

  /** Serialize a value using Avro format with Schema Registry */
  def serializeAvro[T : ClassTag](t: T, topic: String, isKey: Boolean, clazz: String): Array[Byte] =
    val ser = new KafkaAvroSerializer(extractClient)
    ser.configure(Map(
      "schema.registry.url" -> extractSchemaRegistryUrl,
      "key.subject.name.strategy" -> classOf[TopicRecordNameStrategy].getName,
      "value.subject.name.strategy" -> classOf[TopicRecordNameStrategy].getName,
      "auto.register.schemas" -> "true",
      "use.latest.version" -> "false",
      "avro.use.logical.type.converters" -> "true",
      "avro.remove.java.properties" -> "true",
    ).asJava, isKey)
    ser.serialize(
      topic,
      if isKey && clazz == "CloudEvent" then
        CloudEventAvroConverter.toAvro(t.asInstanceOf[CloudEvent])
      else
        t.asInstanceOf[SpecificRecord]
    )

  /** Deserialize bytes using Avro format with Schema Registry */
  def deserializeAvro[T: ClassTag](bytes: Array[Byte], topic: String, isKey: Boolean, clazz: String): T =
    val des = new KafkaAvroDeserializer(extractClient)
    val useSpecificReader = !(isKey && clazz == "CloudEvent")
    des.configure(Map(
      "schema.registry.url" -> extractSchemaRegistryUrl,
      "key.subject.name.strategy" -> classOf[TopicRecordNameStrategy].getName,
      "value.subject.name.strategy" -> classOf[TopicRecordNameStrategy].getName,
      "specific.avro.reader" -> useSpecificReader.toString,
      "avro.use.logical.type.converters" -> "true"
    ).asJava, isKey)
    val result = des.deserialize(topic, bytes)
    if isKey && clazz == "CloudEvent" then
      CloudEventAvroConverter.fromGenericRecord(result.asInstanceOf[org.apache.avro.generic.GenericRecord]).asInstanceOf[T]
    else
      if !summon[ClassTag[T]].runtimeClass.isInstance(result) then
        throw new ClassCastException(s"Deserialized type ${result.getClass.getName} does not match expected ${summon[ClassTag[T]].runtimeClass.getName}")
      result.asInstanceOf[T]

  /** Serialize a value using Protobuf format with Schema Registry */
  def serializeProtobuf[T: ClassTag](t: T, topic: String, isKey: Boolean, clazz: String): Array[Byte] =
    val ser = new KafkaProtobufSerializer[Message](extractClient)
    ser.configure(Map(
      "schema.registry.url" -> extractSchemaRegistryUrl,
      "key.subject.name.strategy" -> classOf[TopicRecordNameStrategy].getName,
      "value.subject.name.strategy" -> classOf[TopicRecordNameStrategy].getName,
      "auto.register.schemas" -> "true",
      "use.latest.version" -> "false",
      "skip.known.types" -> "true",
    ).asJava, isKey)
    ser.serialize(
      topic,
      if isKey && clazz == "CloudEvent" then
        val wrapper = CloudEventProtoConverter.toProto(t.asInstanceOf[CloudEvent])
        wrapper.toDynamicMessage(CloudEventProtoWrapper.SCHEMA)
      else
        t.asInstanceOf[Message]
    )

  /** Deserialize bytes using Protobuf format with Schema Registry */
  def deserializeProtobuf[T: ClassTag](bytes: Array[Byte], topic: String, isKey: Boolean, clazz: String): T =
    val des = new KafkaProtobufDeserializer[Message](extractClient)
    val isCloudEventKey = isKey && clazz == "CloudEvent"
    val isDynamicMessage = clazz == "DynamicMessage"
    val baseConfig = Map(
      "schema.registry.url" -> extractSchemaRegistryUrl,
      "key.subject.name.strategy" -> classOf[TopicRecordNameStrategy].getName,
      "value.subject.name.strategy" -> classOf[TopicRecordNameStrategy].getName,
    )
    // Use derive.type=false for CloudEvent keys or DynamicMessage (returns DynamicMessage directly)
    // Use derive.type=true + specific type for generated Protobuf classes
    val config = if isCloudEventKey || isDynamicMessage then
      baseConfig + ("derive.type" -> "false")  // Return DynamicMessage
    else
      baseConfig + ("derive.type" -> "true") + ("specific.protobuf.value.type" -> clazz)
    des.configure(config.asJava, isKey)
    val result = des.deserialize(topic, bytes)
    if isKey && clazz == "CloudEvent" then
      // Convert DynamicMessage back to CloudEvent
      CloudEventProtoConverter.fromDynamicMessage(result.asInstanceOf[DynamicMessage]).asInstanceOf[T]
    else
      if !summon[ClassTag[T]].runtimeClass.isInstance(result) then
        throw new ClassCastException(s"Deserialized type ${result.getClass.getName} does not match expected ${summon[ClassTag[T]].runtimeClass.getName}")
      result.asInstanceOf[T]

  /** Serialize a value using JSON Schema format with Schema Registry */
  def serializeJsonSchema[T: ClassTag](t: T, topic: String, isKey: Boolean): Array[Byte] =
    val ser = new ScalaConfluentJsonSerializer[T](extractClient, extractSchemaRegistryUrl)
    ser.serialize(topic, t, isKey)

  /** Deserialize bytes using JSON Schema format with Schema Registry */
  def deserializeJsonSchema[T: ClassTag](bytes: Array[Byte], topic: String, isKey: Boolean): T =
    val des = new ScalaConfluentJsonDeserializer[T](extractClient, extractSchemaRegistryUrl, isKey)
    des.deserialize(topic, bytes)

  /**
   * Serialize a value to bytes using auto-detected schema type
   *
   * This is the primary entry point for serialization. It automatically detects
   * the schema type (Avro, Protobuf, or JSON Schema) from the Schema Registry
   * based on the topic and class name, then routes to the appropriate serializer.
   *
   * Subject Naming: Uses `{topic}-{className}` pattern (TopicRecordNameStrategy).
   *
   * @param t The value to serialize
   * @param topic Kafka topic name (used in subject naming)
   * @param isKey true if serializing a key, false for value
   * @tparam T Type of the value (used for subject naming and serializer selection)
   * @return Serialized bytes with Schema Registry wire format (magic byte + schema ID + payload)
   * @throws SchemaRegistryNotInitializedException if client not initialized
   * @throws SchemaNotFoundException if no schema registered for subject
   * @throws IllegalArgumentException if schema type is not supported
   */
  def serialize[T: ClassTag](t: T, topic: String, isKey: Boolean): Array[Byte] =
    val clazz = summon[ClassTag[T]].runtimeClass.getSimpleName
    val subject: String = s"$topic-$clazz"
    val schemaType: String = schemaTypeForSubject(subject)
    schemaType match {
      case "AVRO" => serializeAvro(t, topic, isKey, clazz)
      case "PROTOBUF" => serializeProtobuf(t, topic, isKey, clazz)
      case "JSON" | "JSONSCHEMA" => serializeJsonSchema(t, topic, isKey)
      case _ => throw new IllegalArgumentException(s"Unsupported schema type: $schemaType")
    }

  /**
   * Deserialize bytes to a typed value using auto-detected schema type
   *
   * This is the primary entry point for deserialization. It automatically detects
   * the schema type (Avro, Protobuf, or JSON Schema) from the Schema Registry
   * based on the topic and expected class name, then routes to the appropriate deserializer.
   *
   * Subject Naming: Uses `{topic}-{className}` pattern (TopicRecordNameStrategy).
   *
   * @param bytes Serialized bytes in Schema Registry wire format
   * @param topic Kafka topic name (used in subject naming)
   * @param isKey true if deserializing a key, false for value
   * @tparam T Expected type of the deserialized value
   * @return Deserialized value of type T
   * @throws SchemaRegistryNotInitializedException if client not initialized
   * @throws SchemaNotFoundException if no schema registered for subject
   * @throws ClassCastException if deserialized type doesn't match expected type T
   * @throws IllegalArgumentException if schema type is not supported
   */
  def deserialize[T: ClassTag](bytes: Array[Byte], topic: String, isKey: Boolean): T =
    val clazz = summon[ClassTag[T]].runtimeClass.getSimpleName
    val subject: String = s"$topic-$clazz"
    val schemaType: String = schemaTypeForSubject(subject)
    schemaType match {
      case "AVRO" => deserializeAvro[T](bytes, topic, isKey, clazz)
      case "PROTOBUF" => deserializeProtobuf[T](bytes, topic, isKey, clazz)
      case "JSON" | "JSONSCHEMA" => deserializeJsonSchema[T](bytes, topic, isKey)
      case _ => throw new IllegalArgumentException(s"Unsupported schema type: $schemaType")
    }

  /** Look up the schema type (AVRO, PROTOBUF, JSON) for a given subject from Schema Registry */
  def schemaTypeForSubject(subject: String): String =
    val meta: SchemaMetadata = extractClient.getLatestSchemaMetadata(subject)
    Option(meta.getSchemaType) match
      case Some(sType) => sType.toUpperCase
      case None => throw SchemaNotFoundException(s"Schema type not found for subject: $subject")
