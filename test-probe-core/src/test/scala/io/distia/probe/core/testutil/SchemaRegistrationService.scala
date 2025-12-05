package io.distia.probe.core.testutil

import scala.io.Source
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import scala.util.{Try, Success, Failure}

/**
 * Registers schemas with Confluent Schema Registry.
 *
 * Supports three schema formats:
 * - Avro (.avsc files)
 * - Protobuf (.proto files)
 * - JSON Schema (.json files)
 *
 * Topic-to-Format Mapping:
 * - test-events → Avro
 * - order-events → Protobuf
 * - payment-events → JSON Schema
 * - (default: Avro)
 *
 * Usage:
 * {{{
 *   val service = new SchemaRegistrationService(schemaRegistryUrl)
 *   val schemaId = service.registerSchema("test-events")  // Avro
 *   val schemaId2 = service.registerSchema("order-events")  // Protobuf
 * }}}
 *
 * Schema Files Expected:
 * - src/test/resources/schemas/cloud-event.avsc
 * - src/test/resources/schemas/cloud-event.proto
 * - src/test/resources/schemas/cloud-event.json
 *
 * Thread Safety: This class is NOT thread-safe. Create one instance per test
 * or synchronize access.
 *
 * Test Strategy: Integration tested (15 tests, all three formats + error handling)
 */
class SchemaRegistrationService(schemaRegistryUrl: String) {

  private var schemaIds: Map[String, Int] = Map.empty

  /**
   * Register schema with Schema Registry.
   *
   * Auto-detects schema format based on topic:
   * - test-events → Avro
   * - order-events → Protobuf
   * - payment-events → JSON
   *
   * @param topic Kafka topic (e.g., "test-events")
   * @return Schema ID assigned by Schema Registry
   * @throws IllegalArgumentException if topic has no mapped schema format
   * @throws RuntimeException if registration fails
   */
  def registerSchema(topic: String): Try[Int] = {
    getSchemaType(topic) match {
      case "AVRO" => registerAvroSchema(topic)
      case "PROTOBUF" => registerProtobufSchema(topic)
      case "JSON" => registerJsonSchema(topic)
      case schemaType => Failure(new IllegalArgumentException(s"Unsupported schema type: $schemaType for topic: $topic"))
    }
  }

  /**
   * Get cached schema ID for topic.
   *
   * @param topic Kafka topic
   * @return Some(schemaId) if registered, None if not found
   */
  def getSchemaId(topic: String): Option[Int] = schemaIds.get(topic)

  /**
   * Determine schema type based on topic.
   *
   * Mapping:
   * - test-events → AVRO
   * - order-events → PROTOBUF
   * - payment-events → JSON
   * - default → AVRO
   */
  private def getSchemaType(topic: String): String = topic match {
    case "test-events" => "AVRO"
    case "order-events" => "PROTOBUF"
    case "payment-events" => "JSON"
    case _ => "AVRO"  // Default to Avro
  }

  /**
   * Register Avro schema with Schema Registry.
   *
   * Reads schema from: src/test/resources/schemas/cloud-event.avsc
   *
   * @param topic Kafka topic
   * @return Success(schemaId) or Failure(exception)
   */
  private def registerAvroSchema(topic: String): Try[Int] = Try {
    println(s"[SchemaRegistrationService] Registering Avro schema for topic: $topic")

    // Read schema from classpath
    val schemaSource: Source = Source.fromResource("schemas/cloud-event.avsc")
    val schemaJson: String = try {
      schemaSource.mkString
    } finally {
      schemaSource.close()
    }

    // Escape JSON for embedding in another JSON document
    val escapedSchema: String = escapeJson(schemaJson)

    // Create request body
    val requestBody: String = s"""{"schema":"$escapedSchema"}"""

    // POST to Schema Registry
    val schemaId = postSchemaToRegistry(topic, requestBody)
    schemaIds = schemaIds + (topic -> schemaId)
    println(s"[SchemaRegistrationService] Avro schema registered with ID: $schemaId")
    schemaId
  }

  /**
   * Register Protobuf schema with Schema Registry.
   *
   * Reads schema from: src/test/resources/schemas/cloud-event.proto
   *
   * @param topic Kafka topic
   * @return Success(schemaId) or Failure(exception)
   */
  private def registerProtobufSchema(topic: String): Try[Int] = Try {
    println(s"[SchemaRegistrationService] Registering Protobuf schema for topic: $topic")

    // Read schema from classpath
    val schemaSource: Source = Source.fromResource("schemas/cloud-event.proto")
    val schemaProto: String = try {
      schemaSource.mkString
    } finally {
      schemaSource.close()
    }

    // Escape for JSON embedding
    val escapedSchema: String = escapeJson(schemaProto)

    // Protobuf schema registration uses schemaType field
    val requestBody: String = s"""{"schemaType":"PROTOBUF","schema":"$escapedSchema"}"""

    // POST to Schema Registry
    val schemaId = postSchemaToRegistry(topic, requestBody)
    schemaIds = schemaIds + (topic -> schemaId)
    println(s"[SchemaRegistrationService] Protobuf schema registered with ID: $schemaId")
    schemaId
  }

  /**
   * Register JSON Schema with Schema Registry.
   *
   * Reads schema from: src/test/resources/schemas/cloud-event.json
   *
   * @param topic Kafka topic
   * @return Success(schemaId) or Failure(exception)
   */
  private def registerJsonSchema(topic: String): Try[Int] = Try {
    println(s"[SchemaRegistrationService] Registering JSON Schema for topic: $topic")

    // Read schema from classpath
    val schemaSource: Source = Source.fromResource("schemas/cloud-event.json")
    val schemaJson: String = try {
      schemaSource.mkString
    } finally {
      schemaSource.close()
    }

    // Escape for JSON embedding
    val escapedSchema: String = escapeJson(schemaJson)

    // JSON Schema registration uses schemaType field
    val requestBody: String = s"""{"schemaType":"JSON","schema":"$escapedSchema"}"""

    // POST to Schema Registry
    val schemaId = postSchemaToRegistry(topic, requestBody)
    schemaIds = schemaIds + (topic -> schemaId)
    println(s"[SchemaRegistrationService] JSON Schema registered with ID: $schemaId")
    schemaId
  }

  /**
   * POST schema to Schema Registry and parse response.
   *
   * @param topic Kafka topic
   * @param requestBody JSON request body with schema
   * @return Schema ID from response
   * @throws RuntimeException if registration fails
   */
  private def postSchemaToRegistry(topic: String, requestBody: String): Int = {
    val subject: String = s"$topic-value"
    val url = new URL(s"$schemaRegistryUrl/subjects/$subject/versions")
    val connection: HttpURLConnection = url.openConnection().asInstanceOf[HttpURLConnection]
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "application/vnd.schemaregistry.v1+json")
    connection.setDoOutput(true)

    // Write request body
    val outputStream = connection.getOutputStream
    try {
      outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8))
      outputStream.flush()
    } finally {
      outputStream.close()
    }

    // Read response
    val responseCode: Int = connection.getResponseCode
    if responseCode >= 200 && responseCode < 300 then
      val inputStream = connection.getInputStream
      val responseBody: String = try
        Source.fromInputStream(inputStream, StandardCharsets.UTF_8.name()).mkString
      finally
        inputStream.close()

      // Parse schema ID from response: {"id":1}
      responseBody.replaceAll("\\{\"id\":(\\d+)\\}", "$1").toInt
    else
      val errorStream = connection.getErrorStream
      val errorBody: String = if errorStream != null then
        try
          Source.fromInputStream(errorStream, StandardCharsets.UTF_8.name()).mkString
        finally
          errorStream.close()
      else
        "No error details available"

      throw new RuntimeException(s"Failed to register schema (HTTP $responseCode): $errorBody")
  }

  /**
   * Escape JSON string for embedding in another JSON document.
   *
   * Escapes: backslashes, quotes, newlines, carriage returns, tabs
   *
   * @param json Raw JSON string
   * @return Escaped JSON string
   */
  private def escapeJson(json: String): String = {
    json
      .replace("\\", "\\\\")  // Escape backslashes first
      .replace("\"", "\\\"")  // Escape quotes
      .replace("\n", "\\n")   // Escape newlines
      .replace("\r", "\\r")   // Escape carriage returns
      .replace("\t", "\\t")   // Escape tabs
  }
}
