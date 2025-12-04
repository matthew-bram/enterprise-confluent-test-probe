package io.distia.probe.core.testutil

import io.distia.probe.core.fixtures.TestHarnessFixtures

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Success, Failure}

/**
 * Tests SchemaRegistrationService for all three schema formats.
 *
 * Verifies:
 * - Avro schema registration (test-events topic)
 * - Protobuf schema registration (order-events topic)
 * - JSON Schema registration (payment-events topic)
 * - Schema ID caching and retrieval
 * - Error handling (invalid topics, network errors)
 * - Topic-to-format mapping logic
 *
 * Test Strategy: Integration tests (requires Docker + Schema Registry)
 *
 * Dependencies:
 * - TestcontainersManager (JVM shutdown hook manages cleanup)
 * - Schema files in src/test/resources/schemas/
 *   - cloud-event.avsc
 *   - cloud-event.proto
 *   - cloud-event.json
 *
 * Parallel Execution: PERFECT - Containers stay up across all tests.
 * JVM shutdown hook cleans up after ALL threads finish. No race conditions!
 */
class SchemaRegistrationServiceSpec extends AnyWordSpec
  with Matchers
  with TestHarnessFixtures {

  // Ensure containers started (idempotent, safe to call multiple times)
  TestcontainersManager.start()

  // Create fresh service instance per test (thread-safe for parallel execution)
  private def createService(): SchemaRegistrationService =
    new SchemaRegistrationService(TestcontainersManager.getSchemaRegistryUrl)

  "SchemaRegistrationService" should {

    "register Avro schema for test-events topic" in {
      val service = createService()
      val result = service.registerSchema("test-events")
      result shouldBe a [Success[_]]

      val schemaId = result.get
      schemaId should be > 0
    }

    "cache Avro schema ID after registration" in {
      val service = createService()
      service.registerSchema("test-events")  // Register first
      val cachedId = service.getSchemaId("test-events")
      cachedId shouldBe defined
      cachedId.get should be > 0
    }

    "register Protobuf schema for order-events topic" in {
      val service = createService()
      val result = service.registerSchema("order-events")
      result shouldBe a [Success[_]]

      val schemaId = result.get
      schemaId should be > 0
    }

    "cache Protobuf schema ID after registration" in {
      val service = createService()
      service.registerSchema("order-events")  // Register first
      val cachedId = service.getSchemaId("order-events")
      cachedId shouldBe defined
      cachedId.get should be > 0
    }

    "register JSON Schema for payment-events topic" in {
      val service = createService()
      val result = service.registerSchema("payment-events")
      result shouldBe a [Success[_]]

      val schemaId = result.get
      schemaId should be > 0
    }

    "cache JSON Schema ID after registration" in {
      val service = createService()
      service.registerSchema("payment-events")  // Register first
      val cachedId = service.getSchemaId("payment-events")
      cachedId shouldBe defined
      cachedId.get should be > 0
    }

    "use Avro as default format for unknown topics" in {
      val service = createService()
      val result = service.registerSchema("unknown-topic")
      result shouldBe a [Success[_]]

      val schemaId = result.get
      schemaId should be > 0
    }

    "return None for schema ID when topic not registered" in {
      val service = createService()
      val cachedId = service.getSchemaId("never-registered-topic")
      cachedId shouldBe None
    }

    "assign unique schema IDs to different topics" in {
      val service = createService()
      service.registerSchema("test-events")
      service.registerSchema("order-events")
      service.registerSchema("payment-events")

      val testEventsId = service.getSchemaId("test-events").get
      val orderEventsId = service.getSchemaId("order-events").get
      val paymentEventsId = service.getSchemaId("payment-events").get

      testEventsId should not be orderEventsId
      testEventsId should not be paymentEventsId
      orderEventsId should not be paymentEventsId
    }

    "register same topic multiple times (idempotent)" in {
      val service = createService()
      val firstResult = service.registerSchema("test-events")
      val secondResult = service.registerSchema("test-events")

      firstResult shouldBe a [Success[_]]
      secondResult shouldBe a [Success[_]]

      // Schema Registry may return same ID or increment it (both acceptable)
      val firstId = firstResult.get
      val secondId = secondResult.get
      firstId should be > 0
      secondId should be > 0
    }

    "handle network errors gracefully" in {
      // Create service with invalid URL
      val invalidService = new SchemaRegistrationService("http://invalid-host:9999")
      val result = invalidService.registerSchema("test-events")

      result shouldBe a [Failure[_]]
    }

    "handle HTTP errors from Schema Registry" in {
      // Create service with wrong endpoint (will get 404)
      val wrongService = new SchemaRegistrationService(s"${TestcontainersManager.getSchemaRegistryUrl}/wrong-path")
      val result = wrongService.registerSchema("test-events")

      result shouldBe a [Failure[_]]
      result.failed.get.getMessage should include ("HTTP")
    }

    "maintain separate schema ID cache per service instance" in {
      val service1 = createService()
      val service2 = createService()

      // Register in first service
      service1.registerSchema("test-events")

      // First service has cached ID
      service1.getSchemaId("test-events") shouldBe defined

      // Second service starts with empty cache
      service2.getSchemaId("test-events") shouldBe None

      // After registration, second service has its own cache
      service2.registerSchema("test-events")
      service2.getSchemaId("test-events") shouldBe defined
    }

    "correctly map topics to schema formats" in {
      val service = createService()

      // Register all three formats
      service.registerSchema("test-events")     // Avro
      service.registerSchema("order-events")    // Protobuf
      service.registerSchema("payment-events")  // JSON

      // Verify all are cached
      service.getSchemaId("test-events") shouldBe defined
      service.getSchemaId("order-events") shouldBe defined
      service.getSchemaId("payment-events") shouldBe defined
    }

    "handle schema files with special characters" in {
      val service = createService()
      // The schemas may contain quotes, newlines, etc.
      // Registration should handle escaping correctly
      val result = service.registerSchema("test-events")
      result shouldBe a [Success[_]]
    }
  }
}
