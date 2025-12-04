package io.distia.probe.core.fixtures

import io.distia.probe.common.models.EventFilter
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests TopicDirectiveFixtures for factory methods and role-specific constructors.
 *
 * Verifies:
 * - Producer directive factory (createProducerDirective)
 * - Consumer directive factory (createConsumerDirective)
 * - Directive without event filters factory
 * - Directive with multiple event types factory
 * - Default values for each factory
 * - Custom parameter overrides
 * - Role-specific client principals
 *
 * Test Strategy: Unit tests (no external dependencies)
 *
 * Dogfooding: Tests TopicDirectiveFixtures using TopicDirectiveFixtures itself
 */
class TopicDirectiveFixturesSpec extends AnyWordSpec
  with Matchers
  with TopicDirectiveFixtures {

  "TopicDirectiveFixtures" should {

    "provide createProducerDirective factory method" in {
      val directive = createProducerDirective()
      directive should not be null
    }

    "provide createConsumerDirective factory method" in {
      val directive = createConsumerDirective()
      directive should not be null
    }

    "provide createDirectiveWithoutEventFilters factory method" in {
      val directive = createDirectiveWithoutEventFilters()
      directive should not be null
    }

    "provide createDirectiveWithMultipleEventTypes factory method" in {
      val directive = createDirectiveWithMultipleEventTypes()
      directive should not be null
    }
  }

  "createProducerDirective" should {

    "create producer directive with default values" in {
      val directive = createProducerDirective()

      directive.topic shouldBe "test-events"
      directive.role shouldBe "producer"
      directive.clientPrincipal shouldBe "test-producer-client"
      directive.eventFilters should contain theSameElementsAs List(EventFilter("TestEvent", "1.0"))
    }

    "override topic" in {
      val directive = createProducerDirective(topic = "order-events")

      directive.topic shouldBe "order-events"
      directive.role shouldBe "producer"
    }

    "override clientPrincipal" in {
      val directive = createProducerDirective(clientPrincipal = "custom-producer")

      directive.clientPrincipal shouldBe "custom-producer"
      directive.role shouldBe "producer"
    }

    "override eventFilters" in {
      val customFilters = List(EventFilter("UserCreated", "2.0"), EventFilter("UserUpdated", "2.0"))
      val directive = createProducerDirective(eventFilters = customFilters)

      directive.eventFilters should contain theSameElementsAs customFilters
      directive.role shouldBe "producer"
    }

    "support all parameter overrides together" in {
      val customFilters = List(EventFilter("PaymentProcessed", "1.5"))
      val directive = createProducerDirective(
        topic = "payment-events",
        clientPrincipal = "payment-producer",
        eventFilters = customFilters
      )

      directive.topic shouldBe "payment-events"
      directive.role shouldBe "producer"
      directive.clientPrincipal shouldBe "payment-producer"
      directive.eventFilters should contain theSameElementsAs customFilters
    }
  }

  "createConsumerDirective" should {

    "create consumer directive with default values" in {
      val directive = createConsumerDirective()

      directive.topic shouldBe "test-events"
      directive.role shouldBe "consumer"
      directive.clientPrincipal shouldBe "test-consumer-client"
      directive.eventFilters should contain theSameElementsAs List(EventFilter("TestEvent", "1.0"))
    }

    "override topic" in {
      val directive = createConsumerDirective(topic = "user-events")

      directive.topic shouldBe "user-events"
      directive.role shouldBe "consumer"
    }

    "override clientPrincipal" in {
      val directive = createConsumerDirective(clientPrincipal = "custom-consumer")

      directive.clientPrincipal shouldBe "custom-consumer"
      directive.role shouldBe "consumer"
    }

    "override eventFilters" in {
      val customFilters = List(EventFilter("OrderPlaced", "1.0"), EventFilter("OrderCancelled", "1.0"))
      val directive = createConsumerDirective(eventFilters = customFilters)

      directive.eventFilters should contain theSameElementsAs customFilters
      directive.role shouldBe "consumer"
    }

    "support all parameter overrides together" in {
      val customFilters = List(EventFilter("NotificationSent", "3.0"))
      val directive = createConsumerDirective(
        topic = "notification-events",
        clientPrincipal = "notification-consumer",
        eventFilters = customFilters
      )

      directive.topic shouldBe "notification-events"
      directive.role shouldBe "consumer"
      directive.clientPrincipal shouldBe "notification-consumer"
      directive.eventFilters should contain theSameElementsAs customFilters
    }
  }

  "createDirectiveWithoutEventFilters" should {

    "create directive with empty event filters by default" in {
      val directive = createDirectiveWithoutEventFilters()

      directive.eventFilters shouldBe empty
      directive.role shouldBe "producer"
      directive.topic shouldBe "test-events"
      directive.clientPrincipal shouldBe "test-producer-client"
    }

    "create directive for producer role" in {
      val directive = createDirectiveWithoutEventFilters(role = "producer")

      directive.role shouldBe "producer"
      directive.clientPrincipal shouldBe "test-producer-client"
      directive.eventFilters shouldBe empty
    }

    "create directive for consumer role" in {
      val directive = createDirectiveWithoutEventFilters(role = "consumer")

      directive.role shouldBe "consumer"
      directive.clientPrincipal shouldBe "test-consumer-client"
      directive.eventFilters shouldBe empty
    }

    "override topic" in {
      val directive = createDirectiveWithoutEventFilters(topic = "audit-events")

      directive.topic shouldBe "audit-events"
      directive.eventFilters shouldBe empty
    }

    "support both role and topic overrides" in {
      val directive = createDirectiveWithoutEventFilters(
        role = "consumer",
        topic = "metrics-events"
      )

      directive.role shouldBe "consumer"
      directive.topic shouldBe "metrics-events"
      directive.clientPrincipal shouldBe "test-consumer-client"
      directive.eventFilters shouldBe empty
    }
  }

  "createDirectiveWithMultipleEventTypes" should {

    "create directive with 3 event types by default" in {
      val directive = createDirectiveWithMultipleEventTypes()

      directive.eventFilters should have size 3
      directive.eventFilters should contain theSameElementsAs List(
        EventFilter("TestEvent", "1.0"),
        EventFilter("UserCreated", "2.0"),
        EventFilter("OrderPlaced", "1.5")
      )
    }

    "create directive for producer role by default" in {
      val directive = createDirectiveWithMultipleEventTypes()

      directive.role shouldBe "producer"
      directive.clientPrincipal shouldBe "test-producer-client"
    }

    "create directive for consumer role" in {
      val directive = createDirectiveWithMultipleEventTypes(role = "consumer")

      directive.role shouldBe "consumer"
      directive.clientPrincipal shouldBe "test-consumer-client"
      directive.eventFilters should have size 3
    }

    "override topic" in {
      val directive = createDirectiveWithMultipleEventTypes(topic = "aggregated-events")

      directive.topic shouldBe "aggregated-events"
      directive.eventFilters should have size 3
    }

    "support both role and topic overrides" in {
      val directive = createDirectiveWithMultipleEventTypes(
        role = "consumer",
        topic = "stream-events"
      )

      directive.role shouldBe "consumer"
      directive.topic shouldBe "stream-events"
      directive.clientPrincipal shouldBe "test-consumer-client"
      directive.eventFilters should have size 3
    }
  }

  "TopicDirectiveFixture object" should {

    "provide standalone createProducerDirective method" in {
      val directive = TopicDirectiveFixture.createProducerDirective()
      directive.role shouldBe "producer"
    }

    "provide standalone createConsumerDirective method" in {
      val directive = TopicDirectiveFixture.createConsumerDirective()
      directive.role shouldBe "consumer"
    }

    "provide standalone createDirectiveWithoutEventFilters method" in {
      val directive = TopicDirectiveFixture.createDirectiveWithoutEventFilters()
      directive.eventFilters shouldBe empty
    }

    "provide standalone createDirectiveWithMultipleEventTypes method" in {
      val directive = TopicDirectiveFixture.createDirectiveWithMultipleEventTypes()
      directive.eventFilters should have size 3
    }
  }

  "Role-specific client principals" should {

    "use producer-specific principal for producer directives" in {
      val directive = createProducerDirective()
      directive.clientPrincipal should include ("producer")
    }

    "use consumer-specific principal for consumer directives" in {
      val directive = createConsumerDirective()
      directive.clientPrincipal should include ("consumer")
    }

    "dynamically generate principal based on role in createDirectiveWithoutEventFilters" in {
      val producerDirective = createDirectiveWithoutEventFilters(role = "producer")
      producerDirective.clientPrincipal shouldBe "test-producer-client"

      val consumerDirective = createDirectiveWithoutEventFilters(role = "consumer")
      consumerDirective.clientPrincipal shouldBe "test-consumer-client"
    }

    "dynamically generate principal based on role in createDirectiveWithMultipleEventTypes" in {
      val producerDirective = createDirectiveWithMultipleEventTypes(role = "producer")
      producerDirective.clientPrincipal shouldBe "test-producer-client"

      val consumerDirective = createDirectiveWithMultipleEventTypes(role = "consumer")
      consumerDirective.clientPrincipal shouldBe "test-consumer-client"
    }
  }

  "bootstrapServers parameter" should {

    "default to None for createProducerDirective" in {
      val directive = createProducerDirective()

      directive.bootstrapServers shouldBe None
    }

    "default to None for createConsumerDirective" in {
      val directive = createConsumerDirective()

      directive.bootstrapServers shouldBe None
    }

    "accept custom bootstrapServers for producer directive" in {
      val directive = createProducerDirective(bootstrapServers = Some("kafka-1:9092"))

      directive.bootstrapServers shouldBe Some("kafka-1:9092")
      directive.role shouldBe "producer"
    }

    "accept custom bootstrapServers for consumer directive" in {
      val directive = createConsumerDirective(bootstrapServers = Some("kafka-2:9093"))

      directive.bootstrapServers shouldBe Some("kafka-2:9093")
      directive.role shouldBe "consumer"
    }

    "accept multiple bootstrap servers" in {
      val directive = createProducerDirective(bootstrapServers = Some("kafka-1:9092,kafka-2:9093,kafka-3:9094"))

      directive.bootstrapServers shouldBe Some("kafka-1:9092,kafka-2:9093,kafka-3:9094")
    }

    "work with all other parameter overrides" in {
      val customFilters = List(EventFilter("OrderEvent", "1.0"))
      val directive = createProducerDirective(
        topic = "orders",
        clientPrincipal = "order-producer",
        eventFilters = customFilters,
        metadata = Map("env" -> "test"),
        bootstrapServers = Some("secondary-kafka:9092")
      )

      directive.topic shouldBe "orders"
      directive.clientPrincipal shouldBe "order-producer"
      directive.eventFilters should contain theSameElementsAs customFilters
      directive.metadata shouldBe Map("env" -> "test")
      directive.bootstrapServers shouldBe Some("secondary-kafka:9092")
    }

    "not be set in createDirectiveWithoutEventFilters" in {
      val directive = createDirectiveWithoutEventFilters()

      directive.bootstrapServers shouldBe None
    }

    "not be set in createDirectiveWithMultipleEventTypes" in {
      val directive = createDirectiveWithMultipleEventTypes()

      directive.bootstrapServers shouldBe None
    }
  }

  "metadata parameter" should {

    "default to empty Map for createProducerDirective" in {
      val directive = createProducerDirective()

      directive.metadata shouldBe Map.empty
    }

    "default to empty Map for createConsumerDirective" in {
      val directive = createConsumerDirective()

      directive.metadata shouldBe Map.empty
    }

    "accept custom metadata for producer directive" in {
      val directive = createProducerDirective(metadata = Map("environment" -> "staging", "cluster" -> "us-east"))

      directive.metadata shouldBe Map("environment" -> "staging", "cluster" -> "us-east")
    }

    "accept custom metadata for consumer directive" in {
      val directive = createConsumerDirective(metadata = Map("version" -> "2.0", "team" -> "payments"))

      directive.metadata shouldBe Map("version" -> "2.0", "team" -> "payments")
    }

    "combine with bootstrapServers parameter" in {
      val directive = createProducerDirective(
        metadata = Map("cluster" -> "secondary"),
        bootstrapServers = Some("secondary-cluster:9092")
      )

      directive.metadata shouldBe Map("cluster" -> "secondary")
      directive.bootstrapServers shouldBe Some("secondary-cluster:9092")
    }
  }

  "Multiple bootstrap servers feature integration" should {

    "support creating directives for different clusters" in {
      val primaryClusterDirective = createProducerDirective(
        topic = "primary-events",
        bootstrapServers = None
      )

      val secondaryClusterDirective = createProducerDirective(
        topic = "secondary-events",
        bootstrapServers = Some("secondary-kafka:9092")
      )

      primaryClusterDirective.bootstrapServers shouldBe None
      secondaryClusterDirective.bootstrapServers shouldBe Some("secondary-kafka:9092")
    }

    "support producer and consumer targeting different clusters" in {
      val producerOnCluster1 = createProducerDirective(
        topic = "shared-events",
        bootstrapServers = Some("cluster1-kafka:9092")
      )

      val consumerOnCluster2 = createConsumerDirective(
        topic = "shared-events",
        bootstrapServers = Some("cluster2-kafka:9092")
      )

      producerOnCluster1.topic shouldBe "shared-events"
      producerOnCluster1.role shouldBe "producer"
      producerOnCluster1.bootstrapServers shouldBe Some("cluster1-kafka:9092")

      consumerOnCluster2.topic shouldBe "shared-events"
      consumerOnCluster2.role shouldBe "consumer"
      consumerOnCluster2.bootstrapServers shouldBe Some("cluster2-kafka:9092")
    }
  }
}
