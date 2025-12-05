package io.distia.probe.core.fixtures

import java.util.UUID

/**
 * Provides shared constants for Kafka streaming actor tests.
 *
 * Mix this trait into test specs to access streaming test constants:
 * {{{
 *   class MySpec extends AnyWordSpec with StreamingTestConstantsFixtures {
 *     "test" in {
 *       val eventId = TestEventIds.Event1
 *       val topic = Topics.TestEvents
 *     }
 *   }
 * }}}
 *
 * Design Principles:
 * - Single source of truth (DRY)
 * - Immutable values
 * - Well-documented constants
 * - Type-safe identifiers
 *
 * Thread Safety: All values are immutable
 */
trait StreamingTestConstantsFixtures {

  /**
   * Streaming test constants providing predefined UUIDs, topics, event types, etc.
   *
   * Access via: StreamingConstants.TestEventIds.Event1, StreamingConstants.Topics.TestEvents, etc.
   */

  /**
   * Default Kafka bootstrap servers for testing.
   *
   * Points to localhost:9092 which is the standard Kafka port.
   * For integration tests with Testcontainers, this will be overridden
   * with the container's dynamic port.
   */
  val DefaultBootstrapServers: String = "localhost:9092"

  /**
   * Alternative bootstrap servers for custom configuration testing.
   */
  val CustomBootstrapServers: String = "custom-kafka:9092"

  /**
   * Default Schema Registry URL for testing.
   *
   * Points to localhost:8081 which is the standard Schema Registry port.
   */
  val DefaultSchemaRegistryUrl: String = "http://localhost:8081"

  /**
   * Predefined test event IDs for consistent event identification across tests.
   *
   * These UUIDs are deterministic to enable predictable test scenarios
   * and make test failures easier to debug.
   */
  object TestEventIds {
    val Event1: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val Event2: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    val Event3: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
    val Event4: UUID = UUID.fromString("00000000-0000-0000-0000-000000000004")
    val Event5: UUID = UUID.fromString("00000000-0000-0000-0000-000000000005")
  }

  /**
   * Common event types for testing.
   */
  object EventTypes {
    val TestEvent: String = "TestEvent"
    val UserCreated: String = "UserCreated"
    val UserUpdated: String = "UserUpdated"
    val OrderPlaced: String = "OrderPlaced"
    val OrderCancelled: String = "OrderCancelled"
  }

  /**
   * Common event versions for testing.
   */
  object EventVersions {
    val V1_0: String = "1.0"
    val V1_5: String = "1.5"
    val V2_0: String = "2.0"
    val V3_2_1: String = "3.2.1"
  }

  /**
   * Common topic names for testing.
   */
  object Topics {
    val TestEvents: String = "test-events"
    val UserEvents: String = "user-events"
    val OrderEvents: String = "order-events"
    val SecureEvents: String = "secure-events"
  }

  /**
   * Common client IDs for testing.
   */
  object ClientIds {
    val ProducerClient: String = "test-producer-client"
    val ConsumerClient: String = "test-consumer-client"
  }

  /**
   * Payload constants for testing different message sizes.
   */
  object Payloads {
    val SmallPayload: Array[Byte] = "test-payload".getBytes
    val MediumPayload: Array[Byte] = Array.fill[Byte](1024)(42) // 1 KB
    val LargePayload: Array[Byte] = Array.fill[Byte](1024 * 1024)(42) // 1 MB
  }

  /**
   * Common trace IDs for testing distributed tracing scenarios.
   */
  object TraceIds {
    val DefaultTrace: String = "test-trace-id"
    val Trace1: String = "trace-00000001"
    val Trace2: String = "trace-00000002"
  }
}

/**
 * Companion object providing object-style access to streaming test constants.
 *
 * Allows calling constants as: `StreamingConstants.TestEventIds.Event1`
 */
object StreamingConstants extends StreamingTestConstantsFixtures
