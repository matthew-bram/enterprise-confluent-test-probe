package io.distia.probe.core.fixtures

import io.distia.probe.core.pubsub.models.CloudEvent as CloudEventModel

import java.time.Instant
import java.util.UUID

/**
 * Provides CloudEvent test fixtures with factory methods.
 *
 * Mix this trait into test specs to access CloudEvent factory:
 * {{{
 *   class MySpec extends AnyWordSpec with CloudEventFixtures {
 *     "test" in {
 *       val event = createCloudEvent()
 *     }
 *   }
 * }}}
 *
 * Design Principles:
 * - Factory pattern with sensible defaults
 * - Immutable instances
 * - Named parameters for flexibility
 * - Type-safe construction
 *
 * Thread Safety: Immutable, thread-safe
 */
trait CloudEventFixtures {

  /**
   * CloudEvent fixture providing factory methods for CloudEvent instances.
   *
   * Access via: CloudEvent.createCloudEvent(), CloudEvent.createCloudEventForTestId(), etc.
   */
  protected val CloudEvent = CloudEventFixture

  /**
   * Create a CloudEvent with default test values.
   *
   * Defaults:
   * - id: random UUID
   * - source: "test-probe/test-events"
   * - specversion: "1.0"
   * - type: "TestEvent"
   * - time: current time
   * - subject: random UUID (correlationid)
   * - datacontenttype: "application/octet-stream"
   * - correlationid: random UUID
   * - payloadversion: "1.0"
   * - time_epoch_micro_source: 0L
   *
   * @param id Unique event ID
   * @param source Event source (e.g., "test-probe/order-events")
   * @param specversion CloudEvents spec version
   * @param eventType Event type (e.g., "OrderCreated")
   * @param time Event timestamp
   * @param correlationid Correlation ID for tracking events (used by DSL fetch)
   * @param datacontenttype MIME type of the payload
   * @param payloadversion Payload schema version
   * @param timeEpochMicroSource Optional source timestamp in microseconds
   * @return CloudEvent with specified configuration
   */
  def createCloudEvent(
    id: String = UUID.randomUUID().toString,
    source: String = "test-probe/test-events",
    specversion: String = "1.0",
    eventType: String = "TestEvent",
    correlationid: String = UUID.randomUUID().toString,
    datacontenttype: String = "application/octet-stream",
    payloadversion: String = "1.0",
    timeEpochMicroSource: Long = 0L
  ): CloudEventModel = CloudEventModel(
    id = id,
    source = source,
    specversion = specversion,
    `type` = eventType,
    subject = correlationid,
    datacontenttype = datacontenttype,
    correlationid = correlationid,
    payloadversion = payloadversion,
    time_epoch_micro_source = timeEpochMicroSource
  )

  /**
   * Create a CloudEvent for a specific test ID.
   *
   * Convenience method for the common pattern of setting both id and correlationid
   * to the same value for test correlation.
   *
   * @param testId Test ID (used for both id and correlationid)
   * @param source Event source (default: "test-probe/test-events")
   * @param eventType Event type (default: "TestEvent")
   * @param payloadversion Payload schema version (default: "1.0")
   * @return CloudEvent with test ID set
   */
  def createCloudEventForTestId(
    testId: UUID,
    source: String = "test-probe/test-events",
    eventType: String = "TestEvent",
    payloadversion: String = "1.0"
  ): CloudEventModel = createCloudEvent(
    id = testId.toString,
    source = source,
    eventType = eventType,
    correlationid = testId.toString,
    payloadversion = payloadversion
  )

  /**
   * Create a CloudEvent with a specific topic.
   *
   * Convenience method for setting the source based on a topic name.
   * Topic is converted to source format: "test-probe/{topic}"
   *
   * @param topic Topic name (e.g., "order-events")
   * @param eventType Event type (default: "TestEvent")
   * @param correlationid Correlation ID (default: random UUID)
   * @param payloadversion Payload schema version (default: "1.0")
   * @return CloudEvent with topic-based source
   */
  def createCloudEventWithTopic(
    topic: String,
    eventType: String = "TestEvent",
    correlationid: String = UUID.randomUUID().toString,
    payloadversion: String = "1.0"
  ): CloudEventModel = createCloudEvent(
    source = s"test-probe/$topic",
    eventType = eventType,
    correlationid = correlationid,
    payloadversion = payloadversion
  )

  /**
   * Create a CloudEvent with specific event type and version.
   *
   * Convenience method for testing event filtering scenarios.
   *
   * @param eventType Event type (e.g., "OrderCreated")
   * @param payloadversion Payload schema version (e.g., "2.0")
   * @param correlationid Correlation ID (default: random UUID)
   * @return CloudEvent with specified type and version
   */
  def createCloudEventWithTypeAndVersion(
    eventType: String,
    payloadversion: String,
    correlationid: String = UUID.randomUUID().toString
  ): CloudEventModel = createCloudEvent(
    eventType = eventType,
    correlationid = correlationid,
    payloadversion = payloadversion
  )
}

/**
 * Companion object providing object-style access to CloudEvent fixtures.
 *
 * Allows calling fixtures as: `CloudEventFixture.createCloudEvent()`
 */
object CloudEventFixture extends CloudEventFixtures
