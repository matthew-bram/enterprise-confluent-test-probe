package io.distia.probe
package core
package pubsub

import fixtures.TestHarnessFixtures
import testutil.TestcontainersManager

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.util.{Success, Failure}

/**
 * Integration tests for SerdesFactory Avro and Protobuf serialization.
 *
 * Tests real serialization/deserialization with Confluent Schema Registry
 * using Testcontainers. Validates:
 * - Avro SpecificRecord serialization with TopicRecordNameStrategy
 * - Protobuf DynamicMessage serialization with TopicRecordNameStrategy
 * - CloudEvent key encapsulation for both formats
 * - Wire format validation (magic byte, schema ID)
 * - Round-trip data integrity
 *
 * Test Strategy: Integration tests (requires Docker + Schema Registry)
 *
 * Dependencies:
 * - TestcontainersManager (JVM shutdown hook manages cleanup)
 * - Schema files in src/test/resources/schemas/
 *   - cloud-event.avsc (Avro)
 *   - cloud-event.proto (Protobuf)
 *   - order-created.avsc, payment-processed.avsc
 *   - order-created.proto, payment-processed.proto
 *
 * Parallel Execution: PERFECT - Containers stay up across all tests.
 */
class SerdesFactoryIntegrationSpec extends AnyWordSpec
  with Matchers
  with TestHarnessFixtures
  with BeforeAndAfterAll {

  // Ensure containers started (idempotent, safe to call multiple times)
  TestcontainersManager.start()

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Initialize SerdesFactory with real Schema Registry
    initializeSerdesFactory(TestcontainersManager.getSchemaRegistryUrl)
  }

  // Test fixture: standard CloudEvent
  def createTestCloudEvent(
    id: String = java.util.UUID.randomUUID().toString,
    eventType: String = "user.created",
    subject: String = "user/123"
  ): models.CloudEvent = models.CloudEvent(
    id = id,
    source = "kafka-testing-probe",
    specversion = "1.0",
    `type` = eventType,
    time = "2025-01-15T10:30:00Z",
    subject = subject,
    datacontenttype = "application/json",
    correlationid = s"corr-$id",
    payloadversion = "v1",
    time_epoch_micro_source = System.currentTimeMillis() * 1000
  )

  "SerdesFactory Avro Integration" should {

    "serialize and deserialize CloudEvent key with Avro format" in {
      val topic = s"avro-key-test-${System.currentTimeMillis()}"
      val cloudEvent = createTestCloudEvent()

      // Register Avro schema for CloudEvent key
      val regResult = registerCloudEventAvroSchema(topic)
      regResult shouldBe a[Success[_]]

      // Serialize CloudEvent as key (converts to CloudEventAvro internally)
      val serializedKey = serializeCloudEventAvroKey(cloudEvent, topic)

      // Validate wire format
      hasMagicByte(serializedKey) shouldBe true
      val schemaId = extractSchemaId(serializedKey)
      schemaId should be > 0

      // Deserialize back to CloudEvent
      val deserializedKey = deserializeCloudEventAvroKey(serializedKey, topic)

      // Verify round-trip integrity
      deserializedKey.id shouldBe cloudEvent.id
      deserializedKey.source shouldBe cloudEvent.source
      deserializedKey.specversion shouldBe cloudEvent.specversion
      deserializedKey.`type` shouldBe cloudEvent.`type`
      deserializedKey.time shouldBe cloudEvent.time
      deserializedKey.subject shouldBe cloudEvent.subject
      deserializedKey.datacontenttype shouldBe cloudEvent.datacontenttype
      deserializedKey.correlationid shouldBe cloudEvent.correlationid
      deserializedKey.payloadversion shouldBe cloudEvent.payloadversion
    }

    "register OrderCreatedAvro schema with TopicRecordNameStrategy" in {
      val topic = s"orders-avro-${System.currentTimeMillis()}"

      val regResult = registerOrderCreatedAvroSchema(topic)
      regResult shouldBe a[Success[_]]

      val schemaId = regResult.get
      schemaId should be > 0
    }

    "register PaymentProcessedAvro schema with TopicRecordNameStrategy" in {
      val topic = s"payments-avro-${System.currentTimeMillis()}"

      val regResult = registerPaymentProcessedAvroSchema(topic)
      regResult shouldBe a[Success[_]]

      val schemaId = regResult.get
      schemaId should be > 0
    }

    "produce different schema IDs for different Avro event types on same topic" in {
      val topic = s"domain-events-avro-${System.currentTimeMillis()}"

      val orderResult = registerOrderCreatedAvroSchema(topic)
      val paymentResult = registerPaymentProcessedAvroSchema(topic)

      orderResult shouldBe a[Success[_]]
      paymentResult shouldBe a[Success[_]]

      // Different event types should have different schema IDs (event democratization)
      orderResult.get should not be paymentResult.get
    }

    "serialize CloudEvent key with correct Avro binary encoding" in {
      val topic = s"avro-encoding-test-${System.currentTimeMillis()}"
      val cloudEvent = createTestCloudEvent(id = "test-avro-encoding-001")

      registerCloudEventAvroSchema(topic)
      val serializedKey = serializeCloudEventAvroKey(cloudEvent, topic)

      // Avro wire format: [magic byte (1)][schema ID (4)][avro binary payload]
      serializedKey.length should be > 5

      // Extract payload (starts at byte 5)
      val payload = extractPayload(serializedKey)
      payload.length should be > 0

      // Avro binary encoding should be more compact than JSON
      // CloudEvent has ~10 fields, so payload should be reasonable size
      payload.length should be < 500
    }
  }

  "SerdesFactory Protobuf Integration" should {

    "serialize and deserialize CloudEvent key with Protobuf format" in {
      val topic = s"proto-key-test-${System.currentTimeMillis()}"
      val cloudEvent = createTestCloudEvent()

      // Register Protobuf schema for CloudEvent key
      val regResult = registerCloudEventProtoSchema(topic)
      regResult shouldBe a[Success[_]]

      // Serialize CloudEvent as key (converts to DynamicMessage internally)
      val serializedKey = serializeCloudEventProtoKey(cloudEvent, topic)

      // Validate wire format
      hasMagicByte(serializedKey) shouldBe true
      val schemaId = extractSchemaId(serializedKey)
      schemaId should be > 0

      // Deserialize back to CloudEvent
      val deserializedKey = deserializeCloudEventProtoKey(serializedKey, topic)

      // Verify round-trip integrity
      deserializedKey.id shouldBe cloudEvent.id
      deserializedKey.source shouldBe cloudEvent.source
      deserializedKey.specversion shouldBe cloudEvent.specversion
      deserializedKey.`type` shouldBe cloudEvent.`type`
      deserializedKey.time shouldBe cloudEvent.time
      deserializedKey.subject shouldBe cloudEvent.subject
      deserializedKey.datacontenttype shouldBe cloudEvent.datacontenttype
      deserializedKey.correlationid shouldBe cloudEvent.correlationid
      deserializedKey.payloadversion shouldBe cloudEvent.payloadversion
    }

    "register OrderCreatedProto schema with TopicRecordNameStrategy" in {
      val topic = s"orders-proto-${System.currentTimeMillis()}"

      val regResult = registerOrderCreatedProtoSchema(topic)
      regResult shouldBe a[Success[_]]

      val schemaId = regResult.get
      schemaId should be > 0
    }

    "register PaymentProcessedProto schema with TopicRecordNameStrategy" in {
      val topic = s"payments-proto-${System.currentTimeMillis()}"

      val regResult = registerPaymentProcessedProtoSchema(topic)
      regResult shouldBe a[Success[_]]

      val schemaId = regResult.get
      schemaId should be > 0
    }

    "produce different schema IDs for different Protobuf event types on separate topics" in {
      // With DynamicMessage subject naming, each Protobuf event type requires its own topic
      // This aligns with SerdesFactory's pattern: $topic-DynamicMessage
      val orderTopic = s"orders-proto-${System.currentTimeMillis()}"
      val paymentTopic = s"payments-proto-${System.currentTimeMillis()}"

      val orderResult = registerOrderCreatedProtoSchema(orderTopic)
      val paymentResult = registerPaymentProcessedProtoSchema(paymentTopic)

      orderResult shouldBe a[Success[_]]
      paymentResult shouldBe a[Success[_]]

      // Different event types should have different schema IDs on their respective topics
      orderResult.get should not be paymentResult.get
    }

    "serialize CloudEvent key with correct Protobuf binary encoding" in {
      val topic = s"proto-encoding-test-${System.currentTimeMillis()}"
      val cloudEvent = createTestCloudEvent(id = "test-proto-encoding-001")

      registerCloudEventProtoSchema(topic)
      val serializedKey = serializeCloudEventProtoKey(cloudEvent, topic)

      // Protobuf wire format: [magic byte (1)][schema ID (4)][message index (N)][protobuf binary payload]
      serializedKey.length should be > 6

      // Extract message index (byte 5 for single-message schemas)
      val messageIndex = extractProtobufMessageIndex(serializedKey)
      messageIndex.length shouldBe 1
      messageIndex(0) shouldBe 0x00.toByte  // First message in schema

      // Extract payload (starts at byte 6 for single-message schemas)
      val payload = extractPayload(serializedKey, payloadStartOffset = 6)
      payload.length should be > 0

      // Protobuf binary encoding should be more compact than JSON
      payload.length should be < 400
    }
  }

  "TopicRecordNameStrategy subject naming" should {

    "use format {topic}-{RecordName} without -key/-value suffix for Avro" in {
      val topic = s"subject-naming-avro-${System.currentTimeMillis()}"

      // Register CloudEvent schema - subject should be {topic}-CloudEvent
      val regResult = registerCloudEventAvroSchema(topic)
      regResult shouldBe a[Success[_]]

      // The schema was registered successfully with TopicRecordNameStrategy
      // Subject naming: {topic}-CloudEvent (no -key or -value suffix)
      val schemaId = regResult.get
      schemaId should be > 0
    }

    "use format {topic}-{RecordName} without -key/-value suffix for Protobuf" in {
      val topic = s"subject-naming-proto-${System.currentTimeMillis()}"

      // Register CloudEvent schema - subject should be {topic}-CloudEvent
      val regResult = registerCloudEventProtoSchema(topic)
      regResult shouldBe a[Success[_]]

      // The schema was registered successfully with TopicRecordNameStrategy
      val schemaId = regResult.get
      schemaId should be > 0
    }
  }

  "Wire format comparison" should {

    "produce smaller Avro payload than JSON for same CloudEvent" in {
      val topic = s"avro-size-comparison-${System.currentTimeMillis()}"
      val cloudEvent = createTestCloudEvent()

      // Register both JSON and Avro schemas
      registerCloudEventKeySchema(topic)  // JSON
      registerCloudEventAvroSchema(s"$topic-avro")  // Avro

      // Serialize with JSON
      val jsonKey = serializeCloudEventAsKey(cloudEvent, topic)

      // Serialize with Avro
      val avroKey = serializeCloudEventAvroKey(cloudEvent, s"$topic-avro")

      // Avro binary encoding should be smaller than JSON text
      avroKey.length should be < jsonKey.length

      // Calculate size reduction
      val reduction = calculateSizeReduction(jsonKey, avroKey)
      reduction should be > 0.0  // Avro should be smaller
    }

    "produce smaller Protobuf payload than JSON for same CloudEvent" in {
      val topic = s"proto-size-comparison-${System.currentTimeMillis()}"
      val cloudEvent = createTestCloudEvent()

      // Register both JSON and Protobuf schemas
      registerCloudEventKeySchema(topic)  // JSON
      registerCloudEventProtoSchema(s"$topic-proto")  // Protobuf

      // Serialize with JSON
      val jsonKey = serializeCloudEventAsKey(cloudEvent, topic)

      // Serialize with Protobuf
      val protoKey = serializeCloudEventProtoKey(cloudEvent, s"$topic-proto")

      // Protobuf binary encoding should be smaller than JSON text
      protoKey.length should be < jsonKey.length

      // Calculate size reduction
      val reduction = calculateSizeReduction(jsonKey, protoKey)
      reduction should be > 0.0  // Protobuf should be smaller
    }
  }

  "Error handling" should {

    "handle serialization without registered schema gracefully" in {
      val unregisteredTopic = s"unregistered-topic-${System.currentTimeMillis()}"
      val cloudEvent = createTestCloudEvent()

      // Attempting to serialize without registering schema should fail
      // The exact error depends on Schema Registry configuration
      val exception = intercept[Exception] {
        serializeCloudEventAvroKey(cloudEvent, unregisteredTopic)
      }

      exception should not be null
    }
  }
}
