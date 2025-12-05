package io.distia.probe.core.fixtures

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

/**
 * Tests CloudEventFixtures for factory methods.
 *
 * Verifies:
 * - Factory method defaults
 * - Named parameter overrides
 * - CloudEvents 1.0 spec compliance
 * - Convenience factory methods
 *
 * Test Strategy: Unit tests (no external dependencies)
 *
 * Dogfooding: Tests CloudEventFixtures using CloudEventFixtures itself
 */
class CloudEventFixturesSpec extends AnyWordSpec
  with Matchers
  with CloudEventFixtures {

  "CloudEventFixtures" should {

    "provide createCloudEvent factory method with defaults" in {
      val event = createCloudEvent()

      // Verify CloudEvents 1.0 spec fields
      event.id should not be empty
      event.source shouldBe "test-probe/test-events"
      event.specversion shouldBe "1.0"
      event.`type` shouldBe "TestEvent"
      event.subject should not be empty
      event.correlationid should not be empty
      event.payloadversion shouldBe "1.0"
      event.datacontenttype shouldBe "application/octet-stream"
      event.time_epoch_micro_source shouldBe 0L
    }

    "allow overriding id" in {
      val customId = UUID.randomUUID().toString
      val event = createCloudEvent(id = customId)

      event.id shouldBe customId
    }

    "allow overriding source" in {
      val event = createCloudEvent(source = "test-probe/order-events")

      event.source shouldBe "test-probe/order-events"
    }

    "allow overriding eventType" in {
      val event = createCloudEvent(eventType = "OrderPlaced")

      event.`type` shouldBe "OrderPlaced"
    }

    "allow overriding correlationid" in {
      val correlationId = UUID.randomUUID().toString
      val event = createCloudEvent(correlationid = correlationId)

      event.correlationid shouldBe correlationId
      event.subject shouldBe correlationId
    }

    "allow overriding payloadversion" in {
      val event = createCloudEvent(payloadversion = "2.0")

      event.payloadversion shouldBe "2.0"
    }

    "allow overriding multiple parameters" in {
      val testId = UUID.randomUUID().toString
      val event = createCloudEvent(
        id = testId,
        source = "test-probe/user-events",
        eventType = "UserCreated",
        payloadversion = "1.5"
      )

      event.id shouldBe testId
      event.source shouldBe "test-probe/user-events"
      event.`type` shouldBe "UserCreated"
      event.payloadversion shouldBe "1.5"
    }

    "generate unique IDs for each call" in {
      val event1 = createCloudEvent()
      val event2 = createCloudEvent()

      event1.id should not be event2.id
      event1.correlationid should not be event2.correlationid
    }
  }

  "createCloudEventForTestId" should {

    "set both id and correlationid to testId" in {
      val testId = UUID.randomUUID()
      val event = createCloudEventForTestId(testId)

      event.id shouldBe testId.toString
      event.correlationid shouldBe testId.toString
      event.subject shouldBe testId.toString
    }

    "use default source" in {
      val testId = UUID.randomUUID()
      val event = createCloudEventForTestId(testId)

      event.source shouldBe "test-probe/test-events"
    }

    "allow overriding source" in {
      val testId = UUID.randomUUID()
      val event = createCloudEventForTestId(
        testId = testId,
        source = "test-probe/payment-events"
      )

      event.source shouldBe "test-probe/payment-events"
    }

    "allow overriding eventType" in {
      val testId = UUID.randomUUID()
      val event = createCloudEventForTestId(
        testId = testId,
        eventType = "PaymentProcessed"
      )

      event.`type` shouldBe "PaymentProcessed"
    }

    "allow overriding payloadversion" in {
      val testId = UUID.randomUUID()
      val event = createCloudEventForTestId(
        testId = testId,
        payloadversion = "3.0"
      )

      event.payloadversion shouldBe "3.0"
    }
  }

  "createCloudEventWithTopic" should {

    "convert topic to source format" in {
      val event = createCloudEventWithTopic(topic = "order-events")

      event.source shouldBe "test-probe/order-events"
    }

    "use default eventType" in {
      val event = createCloudEventWithTopic(topic = "order-events")

      event.`type` shouldBe "TestEvent"
    }

    "allow overriding eventType" in {
      val event = createCloudEventWithTopic(
        topic = "user-events",
        eventType = "UserCreated"
      )

      event.`type` shouldBe "UserCreated"
    }

    "allow setting correlationid" in {
      val correlationId = UUID.randomUUID().toString
      val event = createCloudEventWithTopic(
        topic = "payment-events",
        correlationid = correlationId
      )

      event.correlationid shouldBe correlationId
      event.subject shouldBe correlationId
    }

    "allow overriding payloadversion" in {
      val event = createCloudEventWithTopic(
        topic = "order-events",
        payloadversion = "2.0"
      )

      event.payloadversion shouldBe "2.0"
    }
  }

  "createCloudEventWithTypeAndVersion" should {

    "set eventType and payloadversion" in {
      val event = createCloudEventWithTypeAndVersion(
        eventType = "OrderCreated",
        payloadversion = "2.0"
      )

      event.`type` shouldBe "OrderCreated"
      event.payloadversion shouldBe "2.0"
    }

    "use default correlationid" in {
      val event = createCloudEventWithTypeAndVersion(
        eventType = "UserCreated",
        payloadversion = "1.0"
      )

      event.correlationid should not be empty
    }

    "allow setting correlationid" in {
      val correlationId = UUID.randomUUID().toString
      val event = createCloudEventWithTypeAndVersion(
        eventType = "PaymentProcessed",
        payloadversion = "3.2.1",
        correlationid = correlationId
      )

      event.correlationid shouldBe correlationId
      event.subject shouldBe correlationId
    }

    "preserve CloudEvents 1.0 spec compliance" in {
      val event = createCloudEventWithTypeAndVersion(
        eventType = "OrderPlaced",
        payloadversion = "1.5"
      )

      // Required CloudEvents 1.0 fields
      event.id should not be empty
      event.source should not be empty
      event.`type` shouldBe "OrderPlaced"

      // Test-specific extensions
      event.subject should not be empty
      event.correlationid should not be empty
      event.payloadversion shouldBe "1.5"
    }
  }

  "CloudEventFixture object" should {

    "provide standalone createCloudEvent method" in {
      val event = CloudEventFixture.createCloudEvent()
      event.id should not be empty
    }

    "provide standalone createCloudEventForTestId method" in {
      val testId = UUID.randomUUID()
      val event = CloudEventFixture.createCloudEventForTestId(testId)
      event.id shouldBe testId.toString
    }

    "provide standalone createCloudEventWithTopic method" in {
      val event = CloudEventFixture.createCloudEventWithTopic(topic = "test-topic")
      event.source shouldBe "test-probe/test-topic"
    }

    "provide standalone createCloudEventWithTypeAndVersion method" in {
      val event = CloudEventFixture.createCloudEventWithTypeAndVersion(
        eventType = "TestEvent",
        payloadversion = "1.0"
      )
      event.`type` shouldBe "TestEvent"
      event.payloadversion shouldBe "1.0"
    }
  }
}
