package io.distia.probe.core.fixtures

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

/**
 * Tests ConfigurationFixtures for config builders and constants.
 *
 * Verifies:
 * - Test constants (TestIds, Topics, EventTypes, Buckets)
 * - Default configurations (Kafka, Core)
 * - Custom config builders
 * - Timeout variations
 *
 * Test Strategy: Unit tests (no external dependencies)
 */
class ConfigurationFixturesSpec extends AnyWordSpec with Matchers {
  import ConfigurationFixtures._

  "ConfigurationFixtures" should {

    "provide standard test IDs" in {
      TestIds.test1.toString shouldBe "11111111-1111-1111-1111-111111111111"
      TestIds.test2.toString shouldBe "22222222-2222-2222-2222-222222222222"
      TestIds.test3.toString shouldBe "33333333-3333-3333-3333-333333333333"
    }

    "provide standard topic names" in {
      Topics.testEvents shouldBe "test-events"
      Topics.orderEvents shouldBe "order-events"
      Topics.paymentEvents shouldBe "payment-events"
      Topics.userEvents shouldBe "user-events"
    }

    "provide standard event types" in {
      EventTypes.testEvent shouldBe "TestEvent"
      EventTypes.orderPlaced shouldBe "OrderPlaced"
      EventTypes.paymentProcessed shouldBe "PaymentProcessed"
      EventTypes.userCreated shouldBe "UserCreated"
    }

    "provide standard bucket names" in {
      Buckets.testBucket shouldBe "test-bucket"
      Buckets.integrationBucket shouldBe "integration-bucket"
    }

    "provide default Kafka config" in {
      val kafka = defaultKafkaConfig

      kafka.bootstrapServers shouldBe "localhost:9092"
      kafka.schemaRegistryUrl shouldBe "http://localhost:8081"
      kafka.oauth.tokenEndpoint should include ("localhost")
      kafka.oauth.clientScope shouldBe "kafka.producer"
    }

    "provide default CoreConfig with fast timeouts" in {
      val config = defaultCoreConfig

      config.actorSystemName shouldBe "TestProbeSystem"
      config.actorSystemTimeout shouldBe 3.seconds
      config.setupStateTimeout shouldBe 2.seconds
      config.loadingStateTimeout shouldBe 2.seconds
      config.kafka.bootstrapServers shouldBe "localhost:9092"
    }

    "provide short timer CoreConfig" in {
      val config = shortTimerCoreConfig

      config.setupStateTimeout shouldBe 100.milliseconds
      config.loadingStateTimeout shouldBe 100.milliseconds
      config.completedStateTimeout shouldBe 50.milliseconds
      config.exceptionStateTimeout shouldBe 50.milliseconds
    }

    "allow custom timeout configuration" in {
      val config = customTimeoutCoreConfig(
        setupTimeout = 5.seconds,
        loadingTimeout = 3.seconds,
        completedTimeout = 2.seconds,
        exceptionTimeout = 1.second
      )

      config.setupStateTimeout shouldBe 5.seconds
      config.loadingStateTimeout shouldBe 3.seconds
      config.completedStateTimeout shouldBe 2.seconds
      config.exceptionStateTimeout shouldBe 1.second
    }

    "allow custom Kafka configuration" in {
      val config = customKafkaCoreConfig(
        bootstrapServers = "kafka:9092",
        schemaRegistryUrl = "http://schema-registry:8081"
      )

      config.kafka.bootstrapServers shouldBe "kafka:9092"
      config.kafka.schemaRegistryUrl shouldBe "http://schema-registry:8081"
    }

    "preserve non-Kafka settings when customizing Kafka" in {
      val config = customKafkaCoreConfig(
        bootstrapServers = "custom:9092",
        schemaRegistryUrl = "http://custom:8081"
      )

      // Non-Kafka settings should remain unchanged
      config.actorSystemName shouldBe "TestProbeSystem"
      config.actorSystemTimeout shouldBe 3.seconds
      config.gluePackage shouldBe "io.distia.probe.core.glue"
    }
  }
}
