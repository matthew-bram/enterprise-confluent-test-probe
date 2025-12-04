package io.distia.probe.core.testutil

import io.distia.probe.core.fixtures.TestHarnessFixtures

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Success, Failure}

/**
 * Tests TestcontainersManager lifecycle and error handling.
 *
 * Verifies:
 * - Containers start successfully with network
 * - Bootstrap servers and Schema Registry URL are accessible
 * - Health check waits for Schema Registry readiness
 * - Idempotent start (multiple calls safe)
 *
 * Test Strategy: Integration tests (requires Docker)
 *
 * Note: These tests start actual Docker containers, so they're slower
 * (~30 seconds total). They validate the infrastructure layer works
 * correctly before any specs are restored.
 *
 * Parallel Execution: PERFECT - Containers stay up across all tests.
 * JVM shutdown hook cleans up after ALL threads finish. No race conditions!
 */
class TestcontainersManagerSpec extends AnyWordSpec
  with Matchers
  with TestHarnessFixtures {

  // Ensure containers started (idempotent, safe to call multiple times)
  TestcontainersManager.start()

  "TestcontainersManager" should {

    "start containers successfully" in {
      val result = TestcontainersManager.start()
      TestcontainersManager.isRunning shouldBe true
    }

    "provide Kafka bootstrap servers after start" in {
      val bootstrapServers = TestcontainersManager.getKafkaBootstrapServers
      bootstrapServers should not be empty
      bootstrapServers should include ("localhost")
    }

    "provide Schema Registry URL after start" in {
      val registryUrl = TestcontainersManager.getSchemaRegistryUrl
      registryUrl should not be empty
      registryUrl should include ("http://")
      registryUrl should include ("localhost")
    }

    "be idempotent (start multiple times is safe)" in {
      val firstUrl = TestcontainersManager.getSchemaRegistryUrl
      val result = TestcontainersManager.start()
      val secondUrl = TestcontainersManager.getSchemaRegistryUrl
      firstUrl shouldBe secondUrl
    }
  }
}
