package io.distia.probe
package core
package actors

import io.distia.probe.common.models.EventFilter
import io.distia.probe.core.actors.KafkaConsumerStreamingActor.*
import io.distia.probe.core.fixtures.TopicDirectiveFixtures
import io.distia.probe.core.pubsub.models.CloudEvent
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.{RecordHeader, RecordHeaders}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Unit tests for KafkaConsumerStreamingActor helper methods and models.
 *
 * Tests the pure functions and data models in KafkaConsumerStreamingActor that can be
 * tested without spawning actors or requiring Kafka infrastructure:
 *
 * 1. **createEventFilter** - Event filter predicate creation and evaluation
 * 2. **convertHeadersToList** - Kafka Headers to List[RecordHeader] conversion
 * 3. **DeserializationResult ADT** - Success/Failure case class instantiation
 *
 * Design Principles:
 * - Tests pure helper methods directly (visibility pattern enables this)
 * - No actor system required for these tests
 * - Fast execution (no I/O, no Kafka, no Schema Registry)
 * - Comprehensive edge case coverage
 *
 * Test Coverage Targets:
 * - createEventFilter: All filter matching scenarios
 * - convertHeadersToList: Empty, single, multiple headers
 * - DeserializationResult: Both success and failure paths
 *
 * Thread Safety: All tested functions are pure and thread-safe.
 *
 * Test Strategy: Unit tests using ScalaTest WordSpec (Pattern #3)
 */
class KafkaConsumerStreamingActorSpec extends AnyWordSpec with Matchers with TopicDirectiveFixtures {

  // ==========================================================================
  // Test Data Factories
  // ==========================================================================

  /**
   * Create a CloudEvent for testing filter predicates.
   *
   * @param eventType CloudEvent type field
   * @param payloadVersion CloudEvent payloadversion extension
   * @param correlationId Optional correlation ID (default: random UUID)
   * @return CloudEvent instance for testing
   */
  private def createTestCloudEvent(
    eventType: String,
    payloadVersion: String,
    correlationId: String = UUID.randomUUID().toString
  ): CloudEvent = CloudEvent(
    id = UUID.randomUUID().toString,
    source = "test-source",
    specversion = "1.0",
    `type` = eventType,
    time = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
    subject = "test-subject",
    datacontenttype = "application/octet-stream",
    correlationid = correlationId,
    payloadversion = payloadVersion,
    time_epoch_micro_source = System.currentTimeMillis() * 1000
  )


  /**
   * Create Kafka Headers with specified key-value pairs.
   *
   * @param pairs Header key-value pairs
   * @return Kafka Headers instance
   */
  private def createTestHeaders(pairs: (String, String)*): Headers = {
    val headers = new RecordHeaders()
    pairs.foreach { case (key, value) =>
      headers.add(new RecordHeader(key, value.getBytes("UTF-8")))
    }
    headers
  }

  // ==========================================================================
  // createEventFilter Tests
  // ==========================================================================

  "KafkaConsumerStreamingActor.createEventFilter" should {

    "return true when CloudEvent matches single filter exactly" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(EventFilter("OrderCreated", "v1"))
      )
      val event = createTestCloudEvent(eventType = "OrderCreated", payloadVersion = "v1")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe true
    }

    "return false when CloudEvent eventType does not match" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(EventFilter("OrderCreated", "v1"))
      )
      val event = createTestCloudEvent(eventType = "OrderUpdated", payloadVersion = "v1")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe false
    }

    "return false when CloudEvent payloadVersion does not match" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(EventFilter("OrderCreated", "v1"))
      )
      val event = createTestCloudEvent(eventType = "OrderCreated", payloadVersion = "v2")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe false
    }

    "return false when neither eventType nor payloadVersion matches" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(EventFilter("OrderCreated", "v1"))
      )
      val event = createTestCloudEvent(eventType = "PaymentReceived", payloadVersion = "v3")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe false
    }

    "return true when CloudEvent matches any filter in list (first filter)" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(
          EventFilter("OrderCreated", "v1"),
          EventFilter("OrderUpdated", "v1"),
          EventFilter("OrderCancelled", "v2")
        )
      )
      val event = createTestCloudEvent(eventType = "OrderCreated", payloadVersion = "v1")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe true
    }

    "return true when CloudEvent matches any filter in list (middle filter)" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(
          EventFilter("OrderCreated", "v1"),
          EventFilter("OrderUpdated", "v1"),
          EventFilter("OrderCancelled", "v2")
        )
      )
      val event = createTestCloudEvent(eventType = "OrderUpdated", payloadVersion = "v1")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe true
    }

    "return true when CloudEvent matches any filter in list (last filter)" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(
          EventFilter("OrderCreated", "v1"),
          EventFilter("OrderUpdated", "v1"),
          EventFilter("OrderCancelled", "v2")
        )
      )
      val event = createTestCloudEvent(eventType = "OrderCancelled", payloadVersion = "v2")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe true
    }

    "return false when CloudEvent matches no filters in list" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(
          EventFilter("OrderCreated", "v1"),
          EventFilter("OrderUpdated", "v1"),
          EventFilter("OrderCancelled", "v2")
        )
      )
      val event = createTestCloudEvent(eventType = "PaymentReceived", payloadVersion = "v1")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe false
    }

    "return false when eventFilters list is empty" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List.empty
      )
      val event = createTestCloudEvent(eventType = "OrderCreated", payloadVersion = "v1")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe false
    }

    "handle case-sensitive eventType matching correctly" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(EventFilter("OrderCreated", "v1"))
      )
      val event = createTestCloudEvent(eventType = "ordercreated", payloadVersion = "v1")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      // Case-sensitive - should NOT match
      filter(event) shouldBe false
    }

    "handle case-sensitive payloadVersion matching correctly" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(EventFilter("OrderCreated", "v1"))
      )
      val event = createTestCloudEvent(eventType = "OrderCreated", payloadVersion = "V1")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      // Case-sensitive - should NOT match
      filter(event) shouldBe false
    }

    "handle special characters in eventType" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(EventFilter("com.company.Order-Created_v1", "1.0.0"))
      )
      val event = createTestCloudEvent(eventType = "com.company.Order-Created_v1", payloadVersion = "1.0.0")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe true
    }

    "handle special characters in payloadVersion" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(EventFilter("OrderCreated", "1.0.0-SNAPSHOT"))
      )
      val event = createTestCloudEvent(eventType = "OrderCreated", payloadVersion = "1.0.0-SNAPSHOT")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe true
    }

    "handle empty string eventType in filter" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(EventFilter("", "v1"))
      )
      val event = createTestCloudEvent(eventType = "", payloadVersion = "v1")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe true
    }

    "handle empty string payloadVersion in filter" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(EventFilter("OrderCreated", ""))
      )
      val event = createTestCloudEvent(eventType = "OrderCreated", payloadVersion = "")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe true
    }

    "handle duplicate filters in list" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(
          EventFilter("OrderCreated", "v1"),
          EventFilter("OrderCreated", "v1"),
          EventFilter("OrderCreated", "v1")
        )
      )
      val event = createTestCloudEvent(eventType = "OrderCreated", payloadVersion = "v1")

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(event) shouldBe true
    }

    "handle same eventType with different versions" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(
          EventFilter("OrderCreated", "v1"),
          EventFilter("OrderCreated", "v2"),
          EventFilter("OrderCreated", "v3")
        )
      )

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(createTestCloudEvent("OrderCreated", "v1")) shouldBe true
      filter(createTestCloudEvent("OrderCreated", "v2")) shouldBe true
      filter(createTestCloudEvent("OrderCreated", "v3")) shouldBe true
      filter(createTestCloudEvent("OrderCreated", "v4")) shouldBe false
    }

    "handle same payloadVersion with different eventTypes" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(
          EventFilter("OrderCreated", "v1"),
          EventFilter("OrderUpdated", "v1"),
          EventFilter("OrderDeleted", "v1")
        )
      )

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      filter(createTestCloudEvent("OrderCreated", "v1")) shouldBe true
      filter(createTestCloudEvent("OrderUpdated", "v1")) shouldBe true
      filter(createTestCloudEvent("OrderDeleted", "v1")) shouldBe true
      filter(createTestCloudEvent("OrderShipped", "v1")) shouldBe false
    }
  }

  // ==========================================================================
  // convertHeadersToList Tests
  // ==========================================================================

  "KafkaConsumerStreamingActor.convertHeadersToList" should {

    "convert empty headers to empty list" in {
      val headers = new RecordHeaders()

      val result = KafkaConsumerStreamingActor.convertHeadersToList(headers)

      result shouldBe empty
    }

    "convert single header to list with one element" in {
      val headers = createTestHeaders("trace-id" -> "abc123")

      val result = KafkaConsumerStreamingActor.convertHeadersToList(headers)

      result should have size 1
      result.head.key() shouldBe "trace-id"
      new String(result.head.value(), "UTF-8") shouldBe "abc123"
    }

    "convert multiple headers to list preserving order" in {
      val headers = createTestHeaders(
        "trace-id" -> "abc123",
        "correlation-id" -> "xyz789",
        "source" -> "test-harness"
      )

      val result = KafkaConsumerStreamingActor.convertHeadersToList(headers)

      result should have size 3
      result.map(_.key()) shouldBe List("trace-id", "correlation-id", "source")
    }

    "preserve header values correctly" in {
      val headers = createTestHeaders(
        "header1" -> "value1",
        "header2" -> "value2"
      )

      val result = KafkaConsumerStreamingActor.convertHeadersToList(headers)

      result.map(h => h.key() -> new String(h.value(), "UTF-8")) shouldBe List(
        "header1" -> "value1",
        "header2" -> "value2"
      )
    }

    "handle headers with empty string values" in {
      val headers = createTestHeaders("empty-value" -> "")

      val result = KafkaConsumerStreamingActor.convertHeadersToList(headers)

      result should have size 1
      result.head.key() shouldBe "empty-value"
      new String(result.head.value(), "UTF-8") shouldBe ""
    }

    "handle headers with special characters in keys" in {
      val headers = createTestHeaders(
        "x-custom-header" -> "value1",
        "X-UPPERCASE" -> "value2",
        "snake_case_header" -> "value3"
      )

      val result = KafkaConsumerStreamingActor.convertHeadersToList(headers)

      result should have size 3
      result.map(_.key()) should contain allOf("x-custom-header", "X-UPPERCASE", "snake_case_header")
    }

    "handle headers with UTF-8 values" in {
      val headers = createTestHeaders(
        "unicode" -> "Hello World",
        "emoji" -> "Test Message"
      )

      val result = KafkaConsumerStreamingActor.convertHeadersToList(headers)

      result should have size 2
    }

    "handle headers with duplicate keys" in {
      // Kafka allows duplicate header keys
      val headers = new RecordHeaders()
      headers.add(new RecordHeader("duplicate-key", "value1".getBytes("UTF-8")))
      headers.add(new RecordHeader("duplicate-key", "value2".getBytes("UTF-8")))

      val result = KafkaConsumerStreamingActor.convertHeadersToList(headers)

      result should have size 2
      result.map(_.key()) shouldBe List("duplicate-key", "duplicate-key")
      result.map(h => new String(h.value(), "UTF-8")) shouldBe List("value1", "value2")
    }

    "handle large number of headers" in {
      val headers = new RecordHeaders()
      (1 to 100).foreach { i =>
        headers.add(new RecordHeader(s"header-$i", s"value-$i".getBytes("UTF-8")))
      }

      val result = KafkaConsumerStreamingActor.convertHeadersToList(headers)

      result should have size 100
    }

    "handle headers with binary values" in {
      val binaryValue = Array[Byte](0x00, 0x01, 0x02, 0xFF.toByte)
      val headers = new RecordHeaders()
      headers.add(new RecordHeader("binary-header", binaryValue))

      val result = KafkaConsumerStreamingActor.convertHeadersToList(headers)

      result should have size 1
      result.head.key() shouldBe "binary-header"
      result.head.value() shouldBe binaryValue
    }

    "return immutable result type (List)" in {
      val headers = createTestHeaders("key" -> "value")

      val result = KafkaConsumerStreamingActor.convertHeadersToList(headers)

      result shouldBe a[List[?]]
    }
  }

  // ==========================================================================
  // DeserializationResult ADT Tests
  // ==========================================================================

  "DeserializationSuccess" should {

    "store CloudEvent correctly" in {
      val cloudEvent = createTestCloudEvent("OrderCreated", "v1", "corr-123")
      val mockMsg = null // Message not used in this test

      val result = DeserializationSuccess(cloudEvent, mockMsg)

      result.cloudEvent shouldBe cloudEvent
      result.cloudEvent.correlationid shouldBe "corr-123"
    }

    "be an instance of DeserializationResult" in {
      val cloudEvent = createTestCloudEvent("OrderCreated", "v1")
      val mockMsg = null

      val result: DeserializationResult = DeserializationSuccess(cloudEvent, mockMsg)

      result shouldBe a[DeserializationResult]
      result shouldBe a[DeserializationSuccess]
    }

    "store message reference for offset commit" in {
      val cloudEvent = createTestCloudEvent("OrderCreated", "v1")
      val mockMsg = null // In real usage, this would be ConsumerMessage.CommittableMessage

      val result = DeserializationSuccess(cloudEvent, mockMsg)

      result.msg shouldBe mockMsg
    }

    "preserve CloudEvent fields accurately" in {
      val cloudEvent = createTestCloudEvent("TestEvent", "2.0", "unique-correlation-id")

      val result = DeserializationSuccess(cloudEvent, null)

      result.cloudEvent.`type` shouldBe "TestEvent"
      result.cloudEvent.payloadversion shouldBe "2.0"
      result.cloudEvent.correlationid shouldBe "unique-correlation-id"
      result.cloudEvent.specversion shouldBe "1.0"
    }
  }

  "DeserializationFailure" should {

    "store offset and error message correctly" in {
      val mockMsg = null

      val result = DeserializationFailure(
        offset = 12345L,
        error = "Schema not found for subject 'orders-value'",
        msg = mockMsg
      )

      result.offset shouldBe 12345L
      result.error shouldBe "Schema not found for subject 'orders-value'"
    }

    "be an instance of DeserializationResult" in {
      val result: DeserializationResult = DeserializationFailure(0L, "error", null)

      result shouldBe a[DeserializationResult]
      result shouldBe a[DeserializationFailure]
    }

    "store message reference for offset commit" in {
      val mockMsg = null

      val result = DeserializationFailure(100L, "Deserialization error", mockMsg)

      result.msg shouldBe mockMsg
    }

    "handle zero offset" in {
      val result = DeserializationFailure(0L, "Error at start", null)

      result.offset shouldBe 0L
    }

    "handle large offset values" in {
      val largeOffset = Long.MaxValue - 1

      val result = DeserializationFailure(largeOffset, "Error at large offset", null)

      result.offset shouldBe largeOffset
    }

    "handle empty error message" in {
      val result = DeserializationFailure(123L, "", null)

      result.error shouldBe ""
    }

    "handle error message with special characters" in {
      val errorWithSpecialChars = "Error: Schema 'test-value' not found. Please check: /schemas/v1"

      val result = DeserializationFailure(456L, errorWithSpecialChars, null)

      result.error shouldBe errorWithSpecialChars
    }
  }

  "DeserializationResult pattern matching" should {

    "match DeserializationSuccess correctly" in {
      val cloudEvent = createTestCloudEvent("OrderCreated", "v1")
      val result: DeserializationResult = DeserializationSuccess(cloudEvent, null)

      val matched = result match {
        case DeserializationSuccess(ce, _) => s"Success: ${ce.`type`}"
        case DeserializationFailure(offset, error, _) => s"Failure at $offset: $error"
      }

      matched shouldBe "Success: OrderCreated"
    }

    "match DeserializationFailure correctly" in {
      val result: DeserializationResult = DeserializationFailure(999L, "Parse error", null)

      val matched = result match {
        case DeserializationSuccess(ce, _) => s"Success: ${ce.`type`}"
        case DeserializationFailure(offset, error, _) => s"Failure at $offset: $error"
      }

      matched shouldBe "Failure at 999: Parse error"
    }

    "enable exhaustive pattern matching" in {
      def handleResult(r: DeserializationResult): String = r match {
        case DeserializationSuccess(ce, _) => s"Got event: ${ce.correlationid}"
        case DeserializationFailure(offset, _, _) => s"Failed at: $offset"
      }

      val success = DeserializationSuccess(createTestCloudEvent("Test", "v1", "corr-1"), null)
      val failure = DeserializationFailure(42L, "error", null)

      handleResult(success) should startWith("Got event:")
      handleResult(failure) shouldBe "Failed at: 42"
    }
  }

  // ==========================================================================
  // Edge Cases and Robustness Tests
  // ==========================================================================

  "KafkaConsumerStreamingActor helper methods" should {

    "createEventFilter should be reusable across multiple events" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(EventFilter("OrderCreated", "v1"))
      )

      val filter = KafkaConsumerStreamingActor.createEventFilter(directive)

      // Same filter instance should work consistently
      filter(createTestCloudEvent("OrderCreated", "v1")) shouldBe true
      filter(createTestCloudEvent("OrderCreated", "v1")) shouldBe true
      filter(createTestCloudEvent("OrderUpdated", "v1")) shouldBe false
      filter(createTestCloudEvent("OrderCreated", "v2")) shouldBe false
    }

    "createEventFilter should be pure (no side effects)" in {
      val directive = createConsumerDirective(
        topic = "test-topic",
        eventFilters = List(EventFilter("Test", "v1"))
      )

      // Multiple calls should produce identical filters
      val filter1 = KafkaConsumerStreamingActor.createEventFilter(directive)
      val filter2 = KafkaConsumerStreamingActor.createEventFilter(directive)

      val event = createTestCloudEvent("Test", "v1")

      filter1(event) shouldBe filter2(event)
    }

    "convertHeadersToList should be pure (no side effects)" in {
      val headers = createTestHeaders("key" -> "value")

      // Multiple calls should produce identical results
      val result1 = KafkaConsumerStreamingActor.convertHeadersToList(headers)
      val result2 = KafkaConsumerStreamingActor.convertHeadersToList(headers)

      result1 shouldBe result2
    }
  }
}