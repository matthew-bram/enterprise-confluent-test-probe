package io.distia.probe
package core
package pubsub
package models

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Tests for CloudEvent case class
 *
 * Validates:
 * - Case class instantiation with all parameters
 * - Default value behavior for optional fields
 * - Field accessors and equality
 *
 * Test Strategy: Unit tests (no external dependencies)
 */
private[core] class CloudEventSpec extends AnyWordSpec with Matchers {

  "CloudEvent" should {

    "create instance with all required fields" in {
      val event = CloudEvent(
        id = "test-id-123",
        source = "test-probe/events",
        `type` = "TestEvent",
        subject = "test-subject",
        correlationid = "corr-123",
        payloadversion = "1.0"
      )

      event.id shouldBe "test-id-123"
      event.source shouldBe "test-probe/events"
      event.`type` shouldBe "TestEvent"
      event.subject shouldBe "test-subject"
      event.correlationid shouldBe "corr-123"
      event.payloadversion shouldBe "1.0"
    }

    "use default specversion of 1.0" in {
      val event = CloudEvent(
        id = "test-id",
        source = "test-source",
        `type` = "TestEvent",
        subject = "test-subject",
        correlationid = "corr-id",
        payloadversion = "1.0"
      )

      event.specversion shouldBe "1.0"
    }

    "allow overriding specversion" in {
      val event = CloudEvent(
        id = "test-id",
        source = "test-source",
        specversion = "2.0",
        `type` = "TestEvent",
        subject = "test-subject",
        correlationid = "corr-id",
        payloadversion = "1.0"
      )

      event.specversion shouldBe "2.0"
    }

    "use default datacontenttype of application/octet-stream" in {
      val event = CloudEvent(
        id = "test-id",
        source = "test-source",
        `type` = "TestEvent",
        subject = "test-subject",
        correlationid = "corr-id",
        payloadversion = "1.0"
      )

      event.datacontenttype shouldBe "application/octet-stream"
    }

    "allow overriding datacontenttype" in {
      val event = CloudEvent(
        id = "test-id",
        source = "test-source",
        `type` = "TestEvent",
        subject = "test-subject",
        datacontenttype = "application/json",
        correlationid = "corr-id",
        payloadversion = "1.0"
      )

      event.datacontenttype shouldBe "application/json"
    }

    "use default time_epoch_micro_source of 0L" in {
      val event = CloudEvent(
        id = "test-id",
        source = "test-source",
        `type` = "TestEvent",
        subject = "test-subject",
        correlationid = "corr-id",
        payloadversion = "1.0"
      )

      event.time_epoch_micro_source shouldBe 0L
    }

    "allow overriding time_epoch_micro_source" in {
      val timestamp = System.currentTimeMillis() * 1000
      val event = CloudEvent(
        id = "test-id",
        source = "test-source",
        `type` = "TestEvent",
        subject = "test-subject",
        correlationid = "corr-id",
        payloadversion = "1.0",
        time_epoch_micro_source = timestamp
      )

      event.time_epoch_micro_source shouldBe timestamp
    }

    "generate time field with ISO 8601 format by default" in {
      val beforeCreate = Instant.now()
      val event = CloudEvent(
        id = "test-id",
        source = "test-source",
        `type` = "TestEvent",
        subject = "test-subject",
        correlationid = "corr-id",
        payloadversion = "1.0"
      )
      val afterCreate = Instant.now()

      // Parse the generated time and verify it's a valid ISO timestamp
      val parsedTime = Instant.parse(event.time)
      parsedTime should not be null
      // Time should be between before and after creation
      parsedTime.compareTo(beforeCreate) should be >= 0
      parsedTime.compareTo(afterCreate) should be <= 0
    }

    "allow overriding time field" in {
      val customTime = "2025-01-15T10:30:00Z"
      val event = CloudEvent(
        id = "test-id",
        source = "test-source",
        `type` = "TestEvent",
        time = customTime,
        subject = "test-subject",
        correlationid = "corr-id",
        payloadversion = "1.0"
      )

      event.time shouldBe customTime
    }

    "support equality comparison" in {
      val event1 = CloudEvent(
        id = "same-id",
        source = "test-source",
        specversion = "1.0",
        `type` = "TestEvent",
        time = "2025-01-15T10:30:00Z",
        subject = "test-subject",
        datacontenttype = "application/octet-stream",
        correlationid = "corr-id",
        payloadversion = "1.0",
        time_epoch_micro_source = 0L
      )

      val event2 = CloudEvent(
        id = "same-id",
        source = "test-source",
        specversion = "1.0",
        `type` = "TestEvent",
        time = "2025-01-15T10:30:00Z",
        subject = "test-subject",
        datacontenttype = "application/octet-stream",
        correlationid = "corr-id",
        payloadversion = "1.0",
        time_epoch_micro_source = 0L
      )

      event1 shouldBe event2
      event1.hashCode() shouldBe event2.hashCode()
    }

    "detect inequality when fields differ" in {
      val event1 = CloudEvent(
        id = "id-1",
        source = "test-source",
        `type` = "TestEvent",
        time = "2025-01-15T10:30:00Z",
        subject = "test-subject",
        correlationid = "corr-id",
        payloadversion = "1.0"
      )

      val event2 = CloudEvent(
        id = "id-2",
        source = "test-source",
        `type` = "TestEvent",
        time = "2025-01-15T10:30:00Z",
        subject = "test-subject",
        correlationid = "corr-id",
        payloadversion = "1.0"
      )

      event1 should not be event2
    }

    "support copy with modified fields" in {
      val original = CloudEvent(
        id = "original-id",
        source = "test-source",
        `type` = "TestEvent",
        time = "2025-01-15T10:30:00Z",
        subject = "test-subject",
        correlationid = "corr-id",
        payloadversion = "1.0"
      )

      val copied = original.copy(id = "new-id", `type` = "ModifiedEvent")

      copied.id shouldBe "new-id"
      copied.`type` shouldBe "ModifiedEvent"
      copied.source shouldBe original.source
      copied.subject shouldBe original.subject
    }

    "create with UUID-based id" in {
      val uuid = UUID.randomUUID()
      val event = CloudEvent(
        id = uuid.toString,
        source = "test-source",
        `type` = "TestEvent",
        subject = "test-subject",
        correlationid = uuid.toString,
        payloadversion = "1.0"
      )

      event.id shouldBe uuid.toString
      event.correlationid shouldBe uuid.toString
    }
  }
}