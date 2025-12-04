package io.distia.probe.core.fixtures

import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaRegistryClient}
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.avro.{AvroSchema, AvroSchemaProvider}
import io.confluent.kafka.schemaregistry.protobuf.{ProtobufSchema, ProtobufSchemaProvider}
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.distia.probe.core.pubsub.SerdesFactory
import io.distia.probe.core.pubsub.models.CloudEvent
import io.distia.probe.core.testmodels.TestEventPayload
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import org.apache.avro.specific.SpecificRecord
import com.google.protobuf.Message

import java.util.UUID
import scala.util.{Failure, Success, Try, Using}
import scala.reflect.ClassTag
import scala.io.Source
import scala.jdk.CollectionConverters._

/**
 * Provides SerdesFactory test fixtures for Schema Registry integration.
 *
 * Proxies production SerdesFactory for test convenience with real serialization.
 * Uses REAL Testcontainers Schema Registry (not mocks).
 *
 * Provides:
 * - Schema Registry client initialization (real HTTP client)
 * - Test payload case class (TestEventPayload for JSON schema)
 * - Serialization helpers (proxy to SerdesFactory with magic bytes)
 * - Schema registration utilities
 *
 * Usage:
 * {{{
 *   class MySpec extends AnyWordSpec with SerdesFixtures {
 *     "test" in withTestcontainers { world =>
 *       // Initialize SerdesFactory with real Schema Registry
 *       initializeSerdesFactory(world.schemaRegistryUrl)
 *
 *       // Register JSON schema for test payload
 *       registerTestPayloadSchema("test-events")
 *
 *       // Serialize CloudEvent as key (with magic bytes)
 *       val keyBytes = serializeCloudEventAsKey(cloudEvent, "test-events")
 *
 *       // Serialize test payload as value (with magic bytes)
 *       val valueBytes = serializeTestPayload(testPayload, "test-events")
 *
 *       // Both have Schema Registry magic bytes - real serialization!
 *     }
 *   }
 * }}}
 *
 * Architecture:
 * - Real HTTP client (CachedSchemaRegistryClient)
 * - Proxies production SerdesFactory methods
 * - Real schema registration (JSON schema for TestEventPayload)
 * - Real magic bytes from Schema Registry
 *
 * Thread Safety: Each client instance is independent - thread-safe.
 */
trait SerdesFixtures {

  /**
   * Create default test payload for scenarios.
   */
  def defaultTestPayload: TestEventPayload = TestEventPayload(
    orderId = "order-12345",
    amount = 100.0,
    currency = "USD"
  )

  // ==========================================================================
  // Schema Loading from Resources
  // ==========================================================================

  /**
   * Load JSON schema from test resources.
   *
   * @param resourcePath Path to schema file in test resources (e.g., "schemas/cloud-event.json")
   * @return Schema content as String
   */
  private def loadSchemaFromResource(resourcePath: String): String = {
    val stream = getClass.getClassLoader.getResourceAsStream(resourcePath)
    if (stream == null) {
      throw new IllegalArgumentException(s"Schema file not found: $resourcePath")
    }
    Using.resource(Source.fromInputStream(stream))(_.mkString)
  }

  // ==========================================================================
  // Schema Registry Client Initialization
  // ==========================================================================

  /**
   * Create SchemaRegistryClient connected to real Schema Registry.
   *
   * Configures client with all schema providers (AVRO, Protobuf, JSON) to support
   * all schema types during testing.
   *
   * @param schemaRegistryUrl Schema Registry URL from Testcontainers
   * @return Real SchemaRegistryClient with HTTP connection and full schema type support
   */
  def createSchemaRegistryClient(
    schemaRegistryUrl: String
  ): SchemaRegistryClient = {
    // Configure with all schema providers for comprehensive schema type support
    import io.confluent.kafka.schemaregistry.SchemaProvider
    val schemaProviders: java.util.List[SchemaProvider] = java.util.Arrays.asList[SchemaProvider](
      new AvroSchemaProvider(),
      new ProtobufSchemaProvider(),
      new JsonSchemaProvider()
    )
    new CachedSchemaRegistryClient(schemaRegistryUrl, 1, schemaProviders, null)
  }

  /**
   * Initialize SerdesFactory with Schema Registry client.
   * Safe to call multiple times (idempotent).
   *
   * @param schemaRegistryUrl Schema Registry URL from Testcontainers
   */
  def initializeSerdesFactory(schemaRegistryUrl: String): Try[Unit] = Try {
    val client = createSchemaRegistryClient(schemaRegistryUrl)
    SerdesFactory.setClient(client, schemaRegistryUrl)
  }

  // ==========================================================================
  // Schema Registration (JSON Schema for TestEventPayload)
  // ==========================================================================

  /**
   * Register JSON schema for TestEventPayload.
   * Must be called before serialization.
   *
   * Registers schema with subject: "{topic}-TestEventPayload-value"
   * (TopicRecordNameStrategy naming)
   * Schema type: JSON
   * Schema loaded from: test/resources/schemas/test-event-payload.json
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerTestPayloadSchema(topic: String): Try[Int] = Try {
    val jsonSchemaString = loadSchemaFromResource("schemas/test-event-payload.json")

    // Register with Schema Registry using JsonSchema
    val client = SerdesFactory.extractClient
    val subject = s"$topic-TestEventPayload"
    val jsonSchema = new JsonSchema(jsonSchemaString)
    val schemaId = client.register(subject, jsonSchema)
    schemaId
  }

  /**
   * Register JSON schema for TestEvent (integration test payload).
   * Must be called before serialization in integration tests.
   *
   * Registers schema with subject: "{topic}-TestEvent"
   * (TopicRecordNameStrategy naming)
   * Schema type: JSON
   * Schema loaded from: test/resources/schemas/test-event.json
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerTestEventSchema(topic: String): Try[Int] = Try {
    val jsonSchemaString = loadSchemaFromResource("schemas/test-event.json")

    // Register with Schema Registry using JsonSchema
    val client = SerdesFactory.extractClient
    val subject = s"$topic-TestEvent"
    val jsonSchema = new JsonSchema(jsonSchemaString)
    val schemaId = client.register(subject, jsonSchema)
    schemaId
  }

  /**
   * Register JSON schema for NotificationEvent (integration test payload).
   * Must be called before serialization in integration tests.
   *
   * Registers schema with subject: "{topic}-NotificationEvent"
   * (TopicRecordNameStrategy naming)
   * Schema type: JSON
   * Schema loaded from: test/resources/schemas/notification-event.json
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerNotificationEventSchema(topic: String): Try[Int] = Try {
    val jsonSchemaString = loadSchemaFromResource("schemas/notification-event.json")

    val client = SerdesFactory.extractClient
    val subject = s"$topic-NotificationEvent"
    val jsonSchema = new JsonSchema(jsonSchemaString)
    val schemaId = client.register(subject, jsonSchema)
    schemaId
  }

  /**
   * Register JSON schema for UserEvent (integration test payload).
   * Must be called before serialization in integration tests.
   *
   * Registers schema with subject: "{topic}-UserEvent"
   * (TopicRecordNameStrategy naming)
   * Schema type: JSON
   * Schema loaded from: test/resources/schemas/user-event.json
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerUserEventSchema(topic: String): Try[Int] = Try {
    val jsonSchemaString = loadSchemaFromResource("schemas/user-event.json")

    val client = SerdesFactory.extractClient
    val subject = s"$topic-UserEvent"
    val jsonSchema = new JsonSchema(jsonSchemaString)
    val schemaId = client.register(subject, jsonSchema)
    schemaId
  }

  /**
   * Register JSON schema for ProductEvent (integration test payload).
   * Must be called before serialization in integration tests.
   *
   * Registers schema with subject: "{topic}-ProductEvent"
   * (TopicRecordNameStrategy naming)
   * Schema type: JSON
   * Schema loaded from: test/resources/schemas/product-event.json
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerProductEventSchema(topic: String): Try[Int] = Try {
    val jsonSchemaString = loadSchemaFromResource("schemas/product-event.json")

    val client = SerdesFactory.extractClient
    val subject = s"$topic-ProductEvent"
    val jsonSchema = new JsonSchema(jsonSchemaString)
    val schemaId = client.register(subject, jsonSchema)
    schemaId
  }

  /**
   * Register JSON schema for CloudEvent keys.
   * Must be called before serializing CloudEvent as key.
   *
   * Registers schema with subject: "{topic}-CloudEvent-key"
   * (TopicRecordNameStrategy naming)
   * Schema type: JSON
   * Schema loaded from: test/resources/schemas/cloud-event.json
   *
   * NOTE: This schema MUST match what Confluent auto-generates from the CloudEvent case class.
   * The schema matches mbknor-jackson-jsonSchema behavior with json.oneof.for.nullables=false.
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerCloudEventKeySchema(topic: String): Try[Int] = Try {
    val jsonSchemaString = loadSchemaFromResource("schemas/cloud-event.json")

    // Register with Schema Registry using JsonSchema
    val client = SerdesFactory.extractClient
    val subject = s"$topic-CloudEvent"
    val jsonSchema = new JsonSchema(jsonSchemaString)
    val schemaId = client.register(subject, jsonSchema)
    schemaId
  }

  /**
   * Register JSON schema for OrderCreated value events.
   * Used for polymorphic event testing with oneOf support.
   *
   * Registers schema with subject: "{topic}-OrderCreated"
   * (TopicRecordNameStrategy naming)
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerOrderCreatedSchema(topic: String): Try[Int] = Try {
    val jsonSchemaString = loadSchemaFromResource("schemas/order-created.json")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-OrderCreated"
    val jsonSchema = new JsonSchema(jsonSchemaString)
    client.register(subject, jsonSchema)
  }

  /**
   * Register JSON schema for PaymentProcessed value events.
   * Used for polymorphic event testing with oneOf support.
   *
   * Registers schema with subject: "{topic}-PaymentProcessed"
   * (TopicRecordNameStrategy naming)
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerPaymentProcessedSchema(topic: String): Try[Int] = Try {
    val jsonSchemaString = loadSchemaFromResource("schemas/payment-processed.json")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-PaymentProcessed"
    val jsonSchema = new JsonSchema(jsonSchemaString)
    client.register(subject, jsonSchema)
  }

  // ==========================================================================
  // Avro Schema Registration (TopicRecordNameStrategy)
  // ==========================================================================

  /**
   * Register Avro schema for OrderCreatedAvro value events.
   * Used for polymorphic event testing with TopicRecordNameStrategy.
   *
   * Registers schema with subject: "{topic}-OrderCreatedAvro"
   * Schema loaded from: test/resources/schemas/order-created.avsc
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerOrderCreatedAvroSchema(topic: String): Try[Int] = Try {
    val avroSchemaString = loadSchemaFromResource("schemas/order-created.avsc")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-OrderCreatedAvro"
    val avroSchema = new AvroSchema(avroSchemaString)
    client.register(subject, avroSchema)
  }

  /**
   * Register Avro schema for PaymentProcessedAvro value events.
   * Used for polymorphic event testing with TopicRecordNameStrategy.
   *
   * Registers schema with subject: "{topic}-PaymentProcessedAvro"
   * Schema loaded from: test/resources/schemas/payment-processed.avsc
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerPaymentProcessedAvroSchema(topic: String): Try[Int] = Try {
    val avroSchemaString = loadSchemaFromResource("schemas/payment-processed.avsc")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-PaymentProcessedAvro"
    val avroSchema = new AvroSchema(avroSchemaString)
    client.register(subject, avroSchema)
  }

  /**
   * Register Avro schema for CloudEvent keys.
   * Used for CloudEvent key encapsulation with Avro format.
   *
   * Registers schema with subject: "{topic}-CloudEvent"
   * Schema loaded from: test/resources/schemas/cloud-event.avsc
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerCloudEventAvroSchema(topic: String): Try[Int] = Try {
    val avroSchemaString = loadSchemaFromResource("schemas/cloud-event.avsc")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-CloudEvent"
    val avroSchema = new AvroSchema(avroSchemaString)
    client.register(subject, avroSchema)
  }

  /**
   * Register Avro schema for OrderEvent value events (integration tests).
   * Used by Java integration tests as a reference implementation for teams.
   *
   * Registers schema with subject: "{topic}-OrderEvent"
   * Schema loaded from: test/resources/schemas/order-event.avsc
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerOrderEventAvroSchema(topic: String): Try[Int] = Try {
    val avroSchemaString = loadSchemaFromResource("schemas/order-event.avsc")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-OrderEvent"
    val avroSchema = new AvroSchema(avroSchemaString)
    client.register(subject, avroSchema)
  }

  /**
   * Register Avro schema for InventoryEvent value events (integration tests).
   * Used by Java integration tests as a reference implementation for teams.
   *
   * Registers schema with subject: "{topic}-InventoryEvent"
   * Schema loaded from: test/resources/schemas/inventory-event.avsc
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerInventoryEventAvroSchema(topic: String): Try[Int] = Try {
    val avroSchemaString = loadSchemaFromResource("schemas/inventory-event.avsc")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-InventoryEvent"
    val avroSchema = new AvroSchema(avroSchemaString)
    client.register(subject, avroSchema)
  }

  /**
   * Register Avro schema for ShipmentEvent value events (integration tests).
   * Used by Java integration tests as a reference implementation for teams.
   *
   * Registers schema with subject: "{topic}-ShipmentEvent"
   * Schema loaded from: test/resources/schemas/shipment-event.avsc
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerShipmentEventAvroSchema(topic: String): Try[Int] = Try {
    val avroSchemaString = loadSchemaFromResource("schemas/shipment-event.avsc")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-ShipmentEvent"
    val avroSchema = new AvroSchema(avroSchemaString)
    client.register(subject, avroSchema)
  }

  // ==========================================================================
  // Protobuf Schema Registration (TopicRecordNameStrategy)
  // ==========================================================================

  /**
   * Register Protobuf schema for PaymentEvent value events (integration tests).
   * Used by Java integration tests as a reference implementation for teams.
   *
   * Registers schema with subject: "{topic}-DynamicMessage"
   * Schema loaded from: test/resources/schemas/payment-event.proto
   *
   * IMPORTANT: Uses "DynamicMessage" as the subject name because SerdesFactory
   * uses ClassTag's runtime class name for subject lookup. When Java code passes
   * DynamicMessage.class to the DSL, SerdesFactory looks for "{topic}-DynamicMessage".
   *
   * This means ONE Protobuf event type per topic, which is the recommended pattern
   * for integration tests (dedicated topics per event type).
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerPaymentEventProtoSchema(topic: String): Try[Int] = Try {
    val protoSchemaString = loadSchemaFromResource("schemas/payment-event.proto")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-DynamicMessage"
    val protoSchema = new ProtobufSchema(protoSchemaString)
    client.register(subject, protoSchema)
  }

  /**
   * Register Protobuf schema for MetricsEvent value events (integration tests).
   * Used by Java integration tests as a reference implementation for teams.
   *
   * Registers schema with subject: "{topic}-DynamicMessage"
   * Schema loaded from: test/resources/schemas/metrics-event.proto
   *
   * IMPORTANT: Uses "DynamicMessage" as the subject name because SerdesFactory
   * uses ClassTag's runtime class name for subject lookup. When Java code passes
   * DynamicMessage.class to the DSL, SerdesFactory looks for "{topic}-DynamicMessage".
   *
   * This means ONE Protobuf event type per topic, which is the recommended pattern
   * for integration tests (dedicated topics per event type).
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerMetricsEventProtoSchema(topic: String): Try[Int] = Try {
    val protoSchemaString = loadSchemaFromResource("schemas/metrics-event.proto")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-DynamicMessage"
    val protoSchema = new ProtobufSchema(protoSchemaString)
    client.register(subject, protoSchema)
  }

  /**
   * Register Protobuf schema for OrderCreated value events.
   * Uses DynamicMessage subject naming for SerdesFactory compatibility.
   *
   * Registers schema with subject: "{topic}-DynamicMessage"
   * Schema loaded from: test/resources/schemas/order-created.proto
   *
   * IMPORTANT: Uses "DynamicMessage" as subject name because SerdesFactory
   * uses ClassTag's runtime class name for subject lookup. When step definitions
   * pass DynamicMessage to SerdesFactory, it looks for "{topic}-DynamicMessage".
   *
   * This means ONE Protobuf event type per topic, which is the recommended pattern
   * for Protobuf testing (dedicated topics per event type).
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerOrderCreatedProtoSchema(topic: String): Try[Int] = Try {
    val protoSchemaString = loadSchemaFromResource("schemas/order-created.proto")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-DynamicMessage"
    val protoSchema = new ProtobufSchema(protoSchemaString)
    client.register(subject, protoSchema)
  }

  /**
   * Register Protobuf schema for PaymentProcessed value events.
   * Uses DynamicMessage subject naming for SerdesFactory compatibility.
   *
   * Registers schema with subject: "{topic}-DynamicMessage"
   * Schema loaded from: test/resources/schemas/payment-processed.proto
   *
   * IMPORTANT: Uses "DynamicMessage" as subject name because SerdesFactory
   * uses ClassTag's runtime class name for subject lookup. When step definitions
   * pass DynamicMessage to SerdesFactory, it looks for "{topic}-DynamicMessage".
   *
   * This means ONE Protobuf event type per topic, which is the recommended pattern
   * for Protobuf testing (dedicated topics per event type).
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerPaymentProcessedProtoSchema(topic: String): Try[Int] = Try {
    val protoSchemaString = loadSchemaFromResource("schemas/payment-processed.proto")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-DynamicMessage"
    val protoSchema = new ProtobufSchema(protoSchemaString)
    client.register(subject, protoSchema)
  }

  /**
   * Register Protobuf schema for CloudEvent keys.
   * Used for CloudEvent key encapsulation with Protobuf format.
   *
   * Registers schema with subject: "{topic}-CloudEvent"
   * Schema loaded from: test/resources/schemas/cloud-event.proto
   *
   * @param topic Kafka topic
   * @return Schema ID from Schema Registry
   */
  def registerCloudEventProtoSchema(topic: String): Try[Int] = Try {
    val protoSchemaString = loadSchemaFromResource("schemas/cloud-event.proto")
    val client = SerdesFactory.extractClient
    val subject = s"$topic-CloudEvent"
    val protoSchema = new ProtobufSchema(protoSchemaString)
    client.register(subject, protoSchema)
  }

  // ==========================================================================
  // Schema Cleanup (Delete subjects for clean test isolation)
  // ==========================================================================

  /**
   * Delete Schema Registry subject (all versions).
   * Used for test cleanup to ensure fresh schema registration.
   *
   * @param subject Schema Registry subject to delete (e.g., "test-events-key")
   * @return Success if deleted or didn't exist, Failure if error
   */
  def deleteSubject(subject: String): Try[Unit] = Try {
    val client = SerdesFactory.extractClient
    // Soft delete first (marks all versions as deleted)
    client.deleteSubject(subject)

    // HARD DELETE (permanent removal) - ensures Schema Registry doesn't return deleted schemas
    client.deleteSubject(java.util.Collections.singletonMap("permanent", "true"), subject)
  }



  /**
   * Delete all schemas for a topic (key + value).
   * Convenience method for test cleanup.
   * Uses TopicRecordNameStrategy naming: {topic}-{RecordName}-{key|value}
   *
   * @param topic Kafka topic
   * @return Success if both deleted, Failure if any error
   */
  def deleteAllSchemasForTopic(topic: String): Unit =
    deleteSubject(s"$topic-CloudEvent")
    deleteSubject(s"$topic-TestEventPayload")



  // ==========================================================================
  // Serialization (Proxy to Production SerdesFactory)
  // ==========================================================================

  /**
   * Serialize CloudEvent as Kafka message key.
   * Uses SerdesFactory.serializeJsonSchema() - REAL magic bytes included!
   *
   * @param cloudEvent CloudEvent to serialize
   * @param topic Kafka topic (for schema lookup)
   * @return Serialized bytes with Schema Registry magic bytes
   */
  def serializeCloudEventAsKey(cloudEvent: CloudEvent, topic: String): Array[Byte] = SerdesFactory.serialize[CloudEvent](cloudEvent, topic, true)


  /**
   * Serialize TestEventPayload as Kafka message value.
   * Uses SerdesFactory.serializeJsonSchema() - REAL magic bytes included!
   *
   * @param payload Test payload to serialize
   * @param topic Kafka topic (for schema lookup)
   * @return Serialized bytes with Schema Registry magic bytes
   */
  def serializeTestPayload[T: ClassTag](payload: T, topic: String): Array[Byte] = SerdesFactory.serialize[T](payload, topic, false)

  /**
   * Deserialize bytes to TestEventPayload.
   * Uses SerdesFactory.deserializeJsonSchema() - validates magic bytes!
   *
   * @param bytes Serialized bytes with magic bytes
   * @param topic Kafka topic (for schema lookup)
   * @return Deserialized TestEventPayload
   */
  def deserializeTestPayload[T: ClassTag](bytes: Array[Byte], topic: String): T = SerdesFactory.deserialize[T](bytes, topic, false)


  /**
   * Deserialize bytes to CloudEvent.
   * Uses SerdesFactory.deserializeJsonSchema() - validates magic bytes!
   *
   * @param bytes Serialized bytes with magic bytes
   * @param topic Kafka topic (for schema lookup)
   * @return Deserialized CloudEvent
   */
  def deserializeCloudEventKey(bytes: Array[Byte], topic: String): CloudEvent = SerdesFactory.deserialize[CloudEvent](bytes, topic, true)

  // ==========================================================================
  // Schema Validation (for negative testing)
  // ==========================================================================

  /**
   * Validate JSON bytes against a registered schema.
   * Used for schema validation testing (negative test cases).
   *
   * @param jsonBytes Raw JSON bytes to validate
   * @param topic Kafka topic (for subject naming)
   * @param recordName Record name (e.g., "OrderCreated")
   * @return Some(Throwable) if validation fails, None if validation passes
   */
  def validateJsonAgainstSchema(
    jsonBytes: Array[Byte],
    topic: String,
    recordName: String
  ): Option[Throwable] =
    val subject = s"$topic-$recordName"
    try
      val client = SerdesFactory.extractClient
      val schemaMetadata = client.getLatestSchemaMetadata(subject)
      val schema = client.getSchemaBySubjectAndId(subject, schemaMetadata.getId)

      // Parse JSON and validate against schema
      val jsonString = new String(jsonBytes, "UTF-8")
      val objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
      val jsonNode = objectMapper.readTree(jsonString)

      // Use Confluent's JSON Schema validation
      schema match
        case jsonSchema: JsonSchema =>
          val validationErrors = jsonSchema.validate(jsonNode)
          if validationErrors.isEmpty then
            // Validation passed - check required fields manually
            val schemaNode = objectMapper.readTree(jsonSchema.canonicalString())
            val requiredNode = schemaNode.get("required")
            if requiredNode != null && requiredNode.isArray then
              val missingFields = scala.collection.mutable.ListBuffer[String]()
              requiredNode.elements().forEachRemaining { field =>
                val fieldName = field.asText()
                if !jsonNode.has(fieldName) || jsonNode.get(fieldName).isNull then
                  missingFields += fieldName
              }
              if missingFields.nonEmpty then
                Some(new IllegalArgumentException(
                  s"Schema validation failed: required property '${missingFields.mkString(", ")}' not found in JSON"
                ))
              else
                None
            else
              None
          else
            // Confluent validator found errors - it returns List<String>
            val errorMessages = validationErrors.asScala.mkString("; ")
            Some(new IllegalArgumentException(s"Schema validation failed: $errorMessages"))

        case _ =>
          // Non-JSON schema - cannot validate
          None

    catch
      case ex: Throwable =>
        Some(ex)

  // ==========================================================================
  // Performance Testing State
  // ==========================================================================

  /**
   * OrderCreated events for performance testing.
   * Created by "Given {int} OrderCreated events are created" step.
   */
  var performanceOrderCreatedEvents: List[io.distia.probe.core.testmodels.OrderCreated] = List.empty

  /**
   * PaymentProcessed events for performance testing.
   * Created by "Given {int} PaymentProcessed events are created" step.
   */
  var performancePaymentProcessedEvents: List[io.distia.probe.core.testmodels.PaymentProcessed] = List.empty

  /**
   * Serialization times in nanoseconds for each event.
   * Used for performance testing and cache hit ratio calculation.
   */
  var serializationTimesNanos: List[Long] = List.empty

  /**
   * Total serialization time in milliseconds.
   * Calculated after all events are serialized.
   */
  var totalSerializationTimeMs: Long = 0L

  /**
   * Number of events successfully produced in performance test.
   */
  var performanceEventsProduced: Int = 0

  /**
   * Serialize payload with timing measurement.
   * Records individual serialization time for cache analysis.
   *
   * @param payload Payload to serialize
   * @param topic Kafka topic
   * @return Serialized bytes
   */
  def serializeWithTiming[T: ClassTag](payload: T, topic: String): Array[Byte] =
    val startNanos = System.nanoTime()
    val bytes = SerdesFactory.serialize[T](payload, topic, false)
    val elapsedNanos = System.nanoTime() - startNanos
    serializationTimesNanos = serializationTimesNanos :+ elapsedNanos
    bytes

  /**
   * Serialize CloudEvent key with timing measurement.
   *
   * @param cloudEvent CloudEvent to serialize
   * @param topic Kafka topic
   * @return Serialized bytes
   */
  def serializeCloudEventKeyWithTiming(cloudEvent: CloudEvent, topic: String): Array[Byte] =
    val startNanos = System.nanoTime()
    val bytes = SerdesFactory.serialize[CloudEvent](cloudEvent, topic, true)
    val elapsedNanos = System.nanoTime() - startNanos
    serializationTimesNanos = serializationTimesNanos :+ elapsedNanos
    bytes

  /**
   * Calculate cache hit ratio from serialization times.
   *
   * Cache hit ratio is estimated by comparing:
   * - Cold events: First event of each schema type (schema fetch from registry)
   * - Warm events: Subsequent events (cached schema)
   *
   * A high cache hit ratio (>95%) indicates effective schema caching.
   *
   * @param coldEventCount Number of cold events (typically 2: one per schema type)
   * @return Cache hit ratio as percentage (0-100)
   */
  def calculateCacheHitRatio(coldEventCount: Int = 2): Double =
    if serializationTimesNanos.size <= coldEventCount then
      0.0
    else
      val warmEventCount = serializationTimesNanos.size - coldEventCount
      // Cache hits = warm events (they used cached schema)
      // Cache hit ratio = warm events / total events * 100
      (warmEventCount.toDouble / serializationTimesNanos.size) * 100

  /**
   * Reset performance tracking state.
   * Called between test scenarios.
   */
  def resetPerformanceState(): Unit =
    performanceOrderCreatedEvents = List.empty
    performancePaymentProcessedEvents = List.empty
    serializationTimesNanos = List.empty
    totalSerializationTimeMs = 0L
    performanceEventsProduced = 0

  // ==========================================================================
  // Wire Format Validation Helpers
  // ==========================================================================

  /**
   * Magic byte constant for Confluent Schema Registry wire format.
   * All serialized messages start with this byte.
   */
  val MagicByte: Byte = 0x00.toByte

  /**
   * Extract schema ID from serialized bytes.
   * Wire format: [magic byte (1)][schema ID (4)][payload (N)]
   *
   * @param bytes Serialized bytes with Confluent wire format
   * @return Schema ID (4-byte big-endian integer)
   * @throws IllegalArgumentException if bytes too short or invalid magic byte
   */
  def extractSchemaId(bytes: Array[Byte]): Int =
    if bytes.length < 5 then
      throw new IllegalArgumentException(s"Bytes too short: ${bytes.length} (need at least 5)")
    if bytes(0) != MagicByte then
      throw new IllegalArgumentException(s"Invalid magic byte: ${bytes(0)} (expected 0x00)")
    ((bytes(1) & 0xFF) << 24) |
    ((bytes(2) & 0xFF) << 16) |
    ((bytes(3) & 0xFF) << 8) |
    (bytes(4) & 0xFF)

  /**
   * Validate magic byte in serialized bytes.
   *
   * @param bytes Serialized bytes with Confluent wire format
   * @return true if magic byte is valid (0x00)
   */
  def hasMagicByte(bytes: Array[Byte]): Boolean =
    bytes.nonEmpty && bytes(0) == MagicByte

  /**
   * Extract Protobuf message index array from serialized bytes.
   * Protobuf wire format: [magic byte (1)][schema ID (4)][message index array (N)][payload (M)]
   *
   * For single-message .proto files, message index is typically [0x00] (1 byte).
   * For multi-message files, it's a varint-encoded array.
   *
   * @param bytes Serialized Protobuf bytes
   * @return Message index bytes (variable length)
   */
  def extractProtobufMessageIndex(bytes: Array[Byte]): Array[Byte] =
    if bytes.length < 6 then
      throw new IllegalArgumentException(s"Bytes too short for Protobuf: ${bytes.length}")
    // For single-message schemas, message index is typically 1 byte (0x00)
    // This is a simplified extraction - full implementation would decode varints
    Array(bytes(5))

  /**
   * Get payload bytes (after wire format header).
   * JSON/Avro: [magic byte (1)][schema ID (4)][payload]
   * Protobuf: [magic byte (1)][schema ID (4)][message index (N)][payload]
   *
   * @param bytes Serialized bytes
   * @param payloadStartOffset Starting offset of payload (5 for JSON/Avro, 6+ for Protobuf)
   * @return Payload bytes
   */
  def extractPayload(bytes: Array[Byte], payloadStartOffset: Int = 5): Array[Byte] =
    if bytes.length <= payloadStartOffset then
      Array.empty
    else
      bytes.drop(payloadStartOffset)

  /**
   * Debug wire format by printing header bytes.
   * Useful for troubleshooting serialization issues.
   *
   * @param bytes Serialized bytes
   */
  def debugWireFormat(bytes: Array[Byte]): Unit =
    println(s"Total bytes: ${bytes.length}")
    println(s"Magic byte: 0x${String.format("%02X", bytes(0) & 0xFF)}")
    if bytes.length >= 5 then
      println(s"Schema ID: ${extractSchemaId(bytes)}")
    if bytes.length > 5 then
      val payloadPreview = bytes.slice(5, math.min(15, bytes.length))
      println(s"First payload bytes: ${payloadPreview.map(b => f"$b%02X").mkString(" ")}")

  // ==========================================================================
  // Avro Serialization Helpers (SpecificRecord types)
  // ==========================================================================

  /**
   * Serialize Avro SpecificRecord as Kafka message value.
   * Uses SerdesFactory with TopicRecordNameStrategy subject naming.
   *
   * @param record Avro SpecificRecord to serialize
   * @param topic Kafka topic
   * @return Serialized bytes with Schema Registry magic bytes
   */
  def serializeAvroRecord[T <: SpecificRecord : ClassTag](record: T, topic: String): Array[Byte] =
    SerdesFactory.serialize[T](record, topic, isKey = false)

  /**
   * Deserialize bytes to Avro SpecificRecord.
   * Uses SerdesFactory with TopicRecordNameStrategy subject naming.
   *
   * @param bytes Serialized bytes with magic bytes
   * @param topic Kafka topic
   * @return Deserialized SpecificRecord
   */
  def deserializeAvroRecord[T <: SpecificRecord : ClassTag](bytes: Array[Byte], topic: String): T =
    SerdesFactory.deserialize[T](bytes, topic, isKey = false)

  /**
   * Serialize Avro CloudEvent key.
   * Converts CloudEvent to CloudEventAvro internally.
   *
   * @param cloudEvent CloudEvent to serialize as key
   * @param topic Kafka topic
   * @return Serialized bytes with Schema Registry magic bytes
   */
  def serializeCloudEventAvroKey(cloudEvent: CloudEvent, topic: String): Array[Byte] =
    SerdesFactory.serialize[CloudEvent](cloudEvent, topic, isKey = true)

  /**
   * Deserialize bytes to CloudEvent from Avro format.
   * Converts CloudEventAvro to CloudEvent internally.
   *
   * @param bytes Serialized bytes with magic bytes
   * @param topic Kafka topic
   * @return Deserialized CloudEvent
   */
  def deserializeCloudEventAvroKey(bytes: Array[Byte], topic: String): CloudEvent =
    SerdesFactory.deserialize[CloudEvent](bytes, topic, isKey = true)

  // ==========================================================================
  // Protobuf Serialization Helpers (Message types)
  // ==========================================================================

  /**
   * Serialize Protobuf Message as Kafka message value.
   * Uses SerdesFactory with TopicRecordNameStrategy subject naming.
   *
   * @param message Protobuf Message to serialize
   * @param topic Kafka topic
   * @return Serialized bytes with Schema Registry magic bytes + message index
   */
  def serializeProtobufMessage[T <: Message : ClassTag](message: T, topic: String): Array[Byte] =
    SerdesFactory.serialize[T](message, topic, isKey = false)

  /**
   * Deserialize bytes to Protobuf Message.
   * Uses SerdesFactory with TopicRecordNameStrategy subject naming.
   *
   * @param bytes Serialized bytes with magic bytes
   * @param topic Kafka topic
   * @return Deserialized Message
   */
  def deserializeProtobufMessage[T <: Message : ClassTag](bytes: Array[Byte], topic: String): T =
    SerdesFactory.deserialize[T](bytes, topic, isKey = false)

  /**
   * Serialize Protobuf CloudEvent key.
   * Converts CloudEvent to CloudEventProto internally.
   *
   * @param cloudEvent CloudEvent to serialize as key
   * @param topic Kafka topic
   * @return Serialized bytes with Schema Registry magic bytes
   */
  def serializeCloudEventProtoKey(cloudEvent: CloudEvent, topic: String): Array[Byte] =
    SerdesFactory.serialize[CloudEvent](cloudEvent, topic, isKey = true)

  /**
   * Deserialize bytes to CloudEvent from Protobuf format.
   * Converts CloudEventProto to CloudEvent internally.
   *
   * @param bytes Serialized bytes with magic bytes
   * @param topic Kafka topic
   * @return Deserialized CloudEvent
   */
  def deserializeCloudEventProtoKey(bytes: Array[Byte], topic: String): CloudEvent =
    SerdesFactory.deserialize[CloudEvent](bytes, topic, isKey = true)

  // ==========================================================================
  // Avro/Protobuf Performance Testing State
  // ==========================================================================

  /**
   * OrderCreatedAvro events for Avro performance testing.
   * Created by "Given {int} OrderCreatedAvro events are created" step.
   */
  var performanceOrderCreatedAvroEvents: List[SpecificRecord] = List.empty

  /**
   * PaymentProcessedAvro events for Avro performance testing.
   * Created by "Given {int} PaymentProcessedAvro events are created" step.
   */
  var performancePaymentProcessedAvroEvents: List[SpecificRecord] = List.empty

  /**
   * OrderCreatedProto events for Protobuf performance testing.
   * Created by "Given {int} OrderCreatedProto events are created" step.
   */
  var performanceOrderCreatedProtoEvents: List[Message] = List.empty

  /**
   * PaymentProcessedProto events for Protobuf performance testing.
   * Created by "Given {int} PaymentProcessedProto events are created" step.
   */
  var performancePaymentProcessedProtoEvents: List[Message] = List.empty

  /**
   * Reset Avro/Protobuf performance tracking state.
   * Called between test scenarios.
   */
  def resetAvroProtobufPerformanceState(): Unit =
    performanceOrderCreatedAvroEvents = List.empty
    performancePaymentProcessedAvroEvents = List.empty
    performanceOrderCreatedProtoEvents = List.empty
    performancePaymentProcessedProtoEvents = List.empty

  // ==========================================================================
  // Wire Format Size Comparison Helpers
  // ==========================================================================

  /**
   * Compare payload sizes between formats.
   * Used for verifying binary formats are more compact than JSON.
   *
   * @param jsonBytes JSON-serialized bytes
   * @param binaryBytes Binary-serialized bytes (Avro or Protobuf)
   * @return Size reduction percentage (0-100+)
   */
  def calculateSizeReduction(jsonBytes: Array[Byte], binaryBytes: Array[Byte]): Double =
    if jsonBytes.length == 0 then 0.0
    else ((jsonBytes.length - binaryBytes.length).toDouble / jsonBytes.length) * 100

  /**
   * Verify binary encoding is smaller than JSON encoding.
   *
   * @param jsonBytes JSON-serialized bytes
   * @param binaryBytes Binary-serialized bytes
   * @param expectedReductionPercent Minimum expected size reduction (default 30%)
   * @return true if binary is at least expectedReductionPercent smaller
   */
  def isBinarySmallerThanJson(
    jsonBytes: Array[Byte],
    binaryBytes: Array[Byte],
    expectedReductionPercent: Double = 30.0
  ): Boolean =
    calculateSizeReduction(jsonBytes, binaryBytes) >= expectedReductionPercent

  // ==========================================================================
  // Protobuf DynamicMessage Serialization for Test Models (via SerdesFactory)
  // ==========================================================================

  import io.distia.probe.core.testmodels.{OrderCreatedProtoWrapper, PaymentProcessedProtoWrapper}
  import com.google.protobuf.DynamicMessage

  /**
   * Serialize OrderCreatedProtoWrapper to Protobuf bytes via SerdesFactory.
   * Converts wrapper to DynamicMessage and uses production SerdesFactory path.
   *
   * IMPORTANT: Schema must be registered with subject "{topic}-DynamicMessage"
   * using registerOrderCreatedProtoSchema() before calling this method.
   *
   * @param wrapper OrderCreatedProtoWrapper to serialize
   * @param topic Kafka topic
   * @return Serialized bytes with Schema Registry magic bytes
   */
  def serializeOrderCreatedProto(wrapper: OrderCreatedProtoWrapper, topic: String): Array[Byte] =
    val dynamicMessage = wrapper.toDynamicMessage(OrderCreatedProtoWrapper.SCHEMA)
    SerdesFactory.serialize[DynamicMessage](dynamicMessage, topic, isKey = false)

  /**
   * Serialize PaymentProcessedProtoWrapper to Protobuf bytes via SerdesFactory.
   * Converts wrapper to DynamicMessage and uses production SerdesFactory path.
   *
   * IMPORTANT: Schema must be registered with subject "{topic}-DynamicMessage"
   * using registerPaymentProcessedProtoSchema() before calling this method.
   *
   * @param wrapper PaymentProcessedProtoWrapper to serialize
   * @param topic Kafka topic
   * @return Serialized bytes with Schema Registry magic bytes
   */
  def serializePaymentProcessedProto(wrapper: PaymentProcessedProtoWrapper, topic: String): Array[Byte] =
    val dynamicMessage = wrapper.toDynamicMessage(PaymentProcessedProtoWrapper.SCHEMA)
    SerdesFactory.serialize[DynamicMessage](dynamicMessage, topic, isKey = false)

  /**
   * Deserialize bytes to OrderCreatedProtoWrapper via SerdesFactory.
   * Uses production SerdesFactory.deserialize[DynamicMessage] path,
   * then converts DynamicMessage to wrapper.
   *
   * @param bytes Serialized bytes with magic bytes
   * @param topic Kafka topic
   * @return Deserialized OrderCreatedProtoWrapper
   */
  def deserializeOrderCreatedProto(bytes: Array[Byte], topic: String): OrderCreatedProtoWrapper =
    val dynamicMessage = SerdesFactory.deserialize[DynamicMessage](bytes, topic, isKey = false)
    OrderCreatedProtoWrapper.fromDynamicMessage(dynamicMessage)

  /**
   * Deserialize bytes to PaymentProcessedProtoWrapper via SerdesFactory.
   * Uses production SerdesFactory.deserialize[DynamicMessage] path,
   * then converts DynamicMessage to wrapper.
   *
   * @param bytes Serialized bytes with magic bytes
   * @param topic Kafka topic
   * @return Deserialized PaymentProcessedProtoWrapper
   */
  def deserializePaymentProcessedProto(bytes: Array[Byte], topic: String): PaymentProcessedProtoWrapper =
    val dynamicMessage = SerdesFactory.deserialize[DynamicMessage](bytes, topic, isKey = false)
    PaymentProcessedProtoWrapper.fromDynamicMessage(dynamicMessage)

}
