package io.distia.probe
package core
package pubsub

import models.CloudEvent
import com.google.protobuf.DynamicMessage
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Unit tests for CloudEvent Avro and Protobuf converters.
 *
 * Tests the bidirectional conversion between CloudEvent (Scala case class)
 * and format-specific types (CloudEventAvro/CloudEventProtoWrapper) used
 * for key serialization with TopicRecordNameStrategy.
 *
 * Test Strategy:
 * - Unit tests (no Testcontainers)
 * - Focus on field mapping and round-trip preservation
 * - Test edge cases (empty strings, zero values, max values)
 *
 * Test Coverage:
 * - Avro: CloudEvent -> CloudEventAvro -> CloudEvent round-trip
 * - Protobuf: CloudEvent -> CloudEventProtoWrapper -> CloudEvent round-trip
 * - Protobuf: CloudEventProtoWrapper -> DynamicMessage conversion
 * - Edge cases: empty fields, null timestamps, special characters
 */
class CloudEventConvertersSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  // Test fixture: standard CloudEvent
  def createTestCloudEvent(
    id: String = "test-id-123",
    source: String = "kafka-testing-probe",
    specversion: String = "1.0",
    `type`: String = "user.created",
    time: String = "2025-01-15T10:30:00Z",
    subject: String = "user/123",
    datacontenttype: String = "application/json",
    correlationid: String = "corr-456",
    payloadversion: String = "v1",
    time_epoch_micro_source: Long = 1705312200000000L
  ): CloudEvent = CloudEvent(
    id = id,
    source = source,
    specversion = specversion,
    `type` = `type`,
    time = time,
    subject = subject,
    datacontenttype = datacontenttype,
    correlationid = correlationid,
    payloadversion = payloadversion,
    time_epoch_micro_source = time_epoch_micro_source
  )

  "CloudEventAvroConverter" should {

    "convert CloudEvent to CloudEventAvro preserving all fields" in {
      val cloudEvent = createTestCloudEvent()

      val avro = CloudEventAvroConverter.toAvro(cloudEvent)

      avro.getId shouldBe "test-id-123"
      avro.getSource shouldBe "kafka-testing-probe"
      avro.getSpecversion shouldBe "1.0"
      avro.getType shouldBe "user.created"
      avro.getTime shouldBe "2025-01-15T10:30:00Z"
      avro.getSubject shouldBe "user/123"
      avro.getDatacontenttype shouldBe "application/json"
      avro.getCorrelationid shouldBe "corr-456"
      avro.getPayloadversion shouldBe "v1"
      avro.getTimeEpochMicroSource shouldBe 1705312200000000L
    }

    "convert CloudEventAvro back to CloudEvent preserving all fields" in {
      val cloudEvent = createTestCloudEvent()
      val avro = CloudEventAvroConverter.toAvro(cloudEvent)

      val result = CloudEventAvroConverter.toCloudEvent(avro)

      result shouldBe cloudEvent
    }

    "round-trip CloudEvent through Avro conversion" in {
      val original = createTestCloudEvent()

      val roundTripped = CloudEventAvroConverter.toCloudEvent(
        CloudEventAvroConverter.toAvro(original)
      )

      roundTripped shouldBe original
    }

    "handle empty string fields" in {
      val cloudEvent = createTestCloudEvent(
        id = "",
        subject = "",
        correlationid = ""
      )

      val avro = CloudEventAvroConverter.toAvro(cloudEvent)
      val result = CloudEventAvroConverter.toCloudEvent(avro)

      result.id shouldBe ""
      result.subject shouldBe ""
      result.correlationid shouldBe ""
    }

    "handle zero timestamp" in {
      val cloudEvent = createTestCloudEvent(time_epoch_micro_source = 0L)

      val avro = CloudEventAvroConverter.toAvro(cloudEvent)
      val result = CloudEventAvroConverter.toCloudEvent(avro)

      result.time_epoch_micro_source shouldBe 0L
    }

    "handle max long timestamp" in {
      val cloudEvent = createTestCloudEvent(time_epoch_micro_source = Long.MaxValue)

      val avro = CloudEventAvroConverter.toAvro(cloudEvent)
      val result = CloudEventAvroConverter.toCloudEvent(avro)

      result.time_epoch_micro_source shouldBe Long.MaxValue
    }

    "handle special characters in string fields" in {
      val cloudEvent = createTestCloudEvent(
        id = "id-with-unicode-\u00e9\u00e0\u00f1",
        subject = "user/special/chars!@#$%^&*()"
      )

      val avro = CloudEventAvroConverter.toAvro(cloudEvent)
      val result = CloudEventAvroConverter.toCloudEvent(avro)

      result.id shouldBe "id-with-unicode-\u00e9\u00e0\u00f1"
      result.subject shouldBe "user/special/chars!@#$%^&*()"
    }
  }

  "CloudEventAvroRecord" should {

    "implement SpecificRecord get/put interface" in {
      val avro = CloudEventAvroConverter.toAvro(createTestCloudEvent())

      // Test get by field index (0-based)
      avro.get(0) shouldBe "test-id-123"  // id
      avro.get(1) shouldBe "kafka-testing-probe"  // source
      avro.get(2) shouldBe "1.0"  // specversion
      avro.get(3) shouldBe "user.created"  // type
      avro.get(4) shouldBe "2025-01-15T10:30:00Z"  // time
      avro.get(5) shouldBe "user/123"  // subject
      avro.get(6) shouldBe "application/json"  // datacontenttype
      avro.get(7) shouldBe "corr-456"  // correlationid
      avro.get(8) shouldBe "v1"  // payloadversion
      avro.get(9) shouldBe java.lang.Long.valueOf(1705312200000000L)  // time_epoch_micro_source
    }

    "throw IndexOutOfBoundsException for invalid field index" in {
      val avro = CloudEventAvroConverter.toAvro(createTestCloudEvent())

      intercept[IndexOutOfBoundsException] {
        avro.get(10)
      }

      intercept[IndexOutOfBoundsException] {
        avro.get(-1)
      }
    }

    "return valid Avro schema" in {
      val avro = CloudEventAvroConverter.toAvro(createTestCloudEvent())

      val schema = avro.getSchema

      schema should not be null
      schema.getName shouldBe "CloudEvent"
      schema.getNamespace shouldBe "io.distia.probe.core.pubsub.models"
      schema.getFields.size shouldBe 10
    }
  }

  "CloudEventProtoConverter" should {

    "convert CloudEvent to CloudEventProtoWrapper preserving all fields" in {
      val cloudEvent = createTestCloudEvent()

      val wrapper = CloudEventProtoConverter.toProto(cloudEvent)

      wrapper.id shouldBe "test-id-123"
      wrapper.source shouldBe "kafka-testing-probe"
      wrapper.specversion shouldBe "1.0"
      wrapper.`type` shouldBe "user.created"
      wrapper.time shouldBe "2025-01-15T10:30:00Z"
      wrapper.subject shouldBe "user/123"
      wrapper.datacontenttype shouldBe "application/json"
      wrapper.correlationid shouldBe "corr-456"
      wrapper.payloadversion shouldBe "v1"
      wrapper.timeEpochMicroSource shouldBe 1705312200000000L
    }

    "convert CloudEventProtoWrapper back to CloudEvent preserving all fields" in {
      val cloudEvent = createTestCloudEvent()
      val wrapper = CloudEventProtoConverter.toProto(cloudEvent)

      val result = CloudEventProtoConverter.toCloudEvent(wrapper)

      result shouldBe cloudEvent
    }

    "round-trip CloudEvent through Protobuf conversion" in {
      val original = createTestCloudEvent()

      val roundTripped = CloudEventProtoConverter.toCloudEvent(
        CloudEventProtoConverter.toProto(original)
      )

      roundTripped shouldBe original
    }

    "handle empty string fields" in {
      val cloudEvent = createTestCloudEvent(
        id = "",
        subject = "",
        correlationid = ""
      )

      val wrapper = CloudEventProtoConverter.toProto(cloudEvent)
      val result = CloudEventProtoConverter.toCloudEvent(wrapper)

      result.id shouldBe ""
      result.subject shouldBe ""
      result.correlationid shouldBe ""
    }

    "handle zero timestamp" in {
      val cloudEvent = createTestCloudEvent(time_epoch_micro_source = 0L)

      val wrapper = CloudEventProtoConverter.toProto(cloudEvent)
      val result = CloudEventProtoConverter.toCloudEvent(wrapper)

      result.time_epoch_micro_source shouldBe 0L
    }
  }

  "CloudEventProtoWrapper" should {

    "convert to DynamicMessage with correct field values" in {
      val wrapper = CloudEventProtoConverter.toProto(createTestCloudEvent())

      val dynamicMsg = wrapper.toDynamicMessage(CloudEventProtoWrapper.SCHEMA)

      dynamicMsg should not be null
      dynamicMsg.getDescriptorForType.getName shouldBe "CloudEvent"

      // Verify field values by field number
      val descriptor = dynamicMsg.getDescriptorForType
      dynamicMsg.getField(descriptor.findFieldByNumber(1)) shouldBe "test-id-123"
      dynamicMsg.getField(descriptor.findFieldByNumber(2)) shouldBe "kafka-testing-probe"
      dynamicMsg.getField(descriptor.findFieldByNumber(3)) shouldBe "1.0"
      dynamicMsg.getField(descriptor.findFieldByNumber(4)) shouldBe "user.created"
      dynamicMsg.getField(descriptor.findFieldByNumber(5)) shouldBe "2025-01-15T10:30:00Z"
      dynamicMsg.getField(descriptor.findFieldByNumber(6)) shouldBe "user/123"
      dynamicMsg.getField(descriptor.findFieldByNumber(7)) shouldBe "application/json"
      dynamicMsg.getField(descriptor.findFieldByNumber(8)) shouldBe "corr-456"
      dynamicMsg.getField(descriptor.findFieldByNumber(9)) shouldBe "v1"
      dynamicMsg.getField(descriptor.findFieldByNumber(10)) shouldBe java.lang.Long.valueOf(1705312200000000L)
    }

    "round-trip through DynamicMessage" in {
      val original = createTestCloudEvent()
      val wrapper = CloudEventProtoConverter.toProto(original)

      val dynamicMsg = wrapper.toDynamicMessage(CloudEventProtoWrapper.SCHEMA)
      val reconstructed = CloudEventProtoWrapper.fromDynamicMessage(dynamicMsg)
      val result = CloudEventProtoConverter.toCloudEvent(reconstructed)

      result shouldBe original
    }

    "have valid Protobuf schema" in {
      val schema = CloudEventProtoWrapper.SCHEMA

      schema should not be null
      schema.toDescriptor.getName shouldBe "CloudEvent"
      schema.toDescriptor.getFields.size shouldBe 10
    }
  }

  "CloudEventAvroBuilder" should {

    "build CloudEventAvro with all fields set" in {
      val avro = CloudEventAvroRecord.newBuilder()
        .setId("builder-id")
        .setSource("builder-source")
        .setSpecversion("1.0")
        .setType("builder.type")
        .setTime("2025-01-15T12:00:00Z")
        .setSubject("builder/subject")
        .setDatacontenttype("application/xml")
        .setCorrelationid("builder-corr")
        .setPayloadversion("v2")
        .setTimeEpochMicroSource(999999L)
        .build()

      avro.getId shouldBe "builder-id"
      avro.getSource shouldBe "builder-source"
      avro.getType shouldBe "builder.type"
      avro.getTimeEpochMicroSource shouldBe 999999L
    }

    "use default values when not set" in {
      val avro = CloudEventAvroRecord.newBuilder()
        .setId("minimal-id")
        .setSource("minimal-source")
        .setType("minimal.type")
        .setTime("2025-01-15T12:00:00Z")
        .setSubject("minimal/subject")
        .setCorrelationid("minimal-corr")
        .setPayloadversion("v1")
        .build()

      // Defaults
      avro.getSpecversion shouldBe "1.0"
      avro.getDatacontenttype shouldBe "application/octet-stream"
    }
  }

  "CloudEventProtoBuilder" should {

    "build CloudEventProtoWrapper with all fields set" in {
      val wrapper = CloudEventProtoWrapper.newBuilder()
        .setId("builder-id")
        .setSource("builder-source")
        .setSpecversion("1.0")
        .setType("builder.type")
        .setTime("2025-01-15T12:00:00Z")
        .setSubject("builder/subject")
        .setDatacontenttype("application/xml")
        .setCorrelationid("builder-corr")
        .setPayloadversion("v2")
        .setTimeEpochMicroSource(999999L)
        .build()

      wrapper.id shouldBe "builder-id"
      wrapper.source shouldBe "builder-source"
      wrapper.`type` shouldBe "builder.type"
      wrapper.timeEpochMicroSource shouldBe 999999L
    }

    "use default values when not set" in {
      val wrapper = CloudEventProtoWrapper.newBuilder()
        .setId("minimal-id")
        .setSource("minimal-source")
        .setType("minimal.type")
        .setTime("2025-01-15T12:00:00Z")
        .setSubject("minimal/subject")
        .setCorrelationid("minimal-corr")
        .setPayloadversion("v1")
        .build()

      // Defaults
      wrapper.specversion shouldBe "1.0"
      wrapper.datacontenttype shouldBe "application/octet-stream"
      wrapper.timeEpochMicroSource shouldBe 0L
    }
  }
}
