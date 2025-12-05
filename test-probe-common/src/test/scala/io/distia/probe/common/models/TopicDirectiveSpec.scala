package io.distia.probe.common.models

import io.distia.probe.common.exceptions.VaultMappingException
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for TopicDirective, EventFilter, and DirectiveFieldRef
 * Tests model construction, field extraction, and fromString parsing
 */
private[models] class TopicDirectiveSpec extends AnyWordSpec with Matchers {

  // ========== EVENTFILTER TESTS ==========

  "EventFilter" when {

    "constructed with eventType and payloadVersion" should {

      "store eventType correctly" in {
        val filter = EventFilter("OrderCreated", "1.0.0")

        filter.eventType shouldBe "OrderCreated"
      }

      "store payloadVersion correctly" in {
        val filter = EventFilter("OrderCreated", "1.0.0")

        filter.payloadVersion shouldBe "1.0.0"
      }

      "support equality comparison" in {
        val filter1 = EventFilter("OrderCreated", "1.0.0")
        val filter2 = EventFilter("OrderCreated", "1.0.0")
        val filter3 = EventFilter("OrderUpdated", "1.0.0")

        filter1 shouldBe filter2
        filter1 should not be filter3
      }

      "support copy with modifications" in {
        val original = EventFilter("OrderCreated", "1.0.0")
        val modified = original.copy(payloadVersion = "2.0.0")

        modified.eventType shouldBe "OrderCreated"
        modified.payloadVersion shouldBe "2.0.0"
      }

      "support hashCode for collections" in {
        val filter1 = EventFilter("OrderCreated", "1.0.0")
        val filter2 = EventFilter("OrderCreated", "1.0.0")

        filter1.hashCode shouldBe filter2.hashCode
      }

      "support various event types" in {
        val filters = List(
          EventFilter("OrderCreated", "1.0.0"),
          EventFilter("PaymentProcessed", "2.1.0"),
          EventFilter("ShipmentDispatched", "1.5.0"),
          EventFilter("user.registered", "3.0.0")
        )

        filters.map(_.eventType) shouldBe List(
          "OrderCreated", "PaymentProcessed", "ShipmentDispatched", "user.registered"
        )
      }
    }
  }

  // ========== TOPICDIRECTIVE TESTS ==========

  "TopicDirective" when {

    "constructed with required fields" should {

      "store topic correctly" in {
        val directive = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty
        )

        directive.topic shouldBe "orders.events"
      }

      "store role correctly" in {
        val directive = TopicDirective(
          topic = "orders.events",
          role = "consumer",
          clientPrincipal = "kafka-consumer-svc",
          eventFilters = List.empty
        )

        directive.role shouldBe "consumer"
      }

      "store clientPrincipal correctly" in {
        val directive = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty
        )

        directive.clientPrincipal shouldBe "kafka-producer-svc"
      }

      "store eventFilters correctly" in {
        val filters = List(
          EventFilter("OrderCreated", "1.0.0"),
          EventFilter("OrderUpdated", "1.0.0")
        )
        val directive = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = filters
        )

        directive.eventFilters shouldBe filters
        directive.eventFilters.size shouldBe 2
      }

      "default metadata to empty map" in {
        val directive = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty
        )

        directive.metadata shouldBe Map.empty
      }

      "default bootstrapServers to None" in {
        val directive = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty
        )

        directive.bootstrapServers shouldBe None
      }
    }

    "constructed with all fields" should {

      "store metadata correctly" in {
        val directive = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty,
          metadata = Map("serdes" -> "json", "compression" -> "gzip")
        )

        directive.metadata shouldBe Map("serdes" -> "json", "compression" -> "gzip")
        directive.metadata.get("serdes") shouldBe Some("json")
      }

      "store bootstrapServers correctly" in {
        val directive = TopicDirective(
          topic = "orders.events",
          role = "consumer",
          clientPrincipal = "kafka-consumer-svc",
          eventFilters = List.empty,
          bootstrapServers = Some("broker-1:9092,broker-2:9092")
        )

        directive.bootstrapServers shouldBe Some("broker-1:9092,broker-2:9092")
      }
    }

    "used with collections" should {

      "support equality comparison" in {
        val directive1 = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty
        )
        val directive2 = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty
        )

        directive1 shouldBe directive2
      }

      "support hashCode for collections" in {
        val directive1 = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty
        )
        val directive2 = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty
        )

        directive1.hashCode shouldBe directive2.hashCode
      }

      "support copy with modifications" in {
        val original = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty
        )
        val modified = original.copy(role = "consumer")

        modified.topic shouldBe "orders.events"
        modified.role shouldBe "consumer"
      }
    }
  }

  // ========== DIRECTIVEFIELDREF TESTS ==========

  "DirectiveFieldRef" when {

    "Topic case object" should {

      "be a DirectiveFieldRef" in {
        DirectiveFieldRef.Topic shouldBe a[DirectiveFieldRef]
      }

      "extract topic from TopicDirective" in {
        val directive = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty
        )

        DirectiveFieldRef.Topic.extract(directive) shouldBe "orders.events"
      }

      "extract different topic values" in {
        val directive1 = TopicDirective("payments.transactions", "consumer", "svc1", List.empty)
        val directive2 = TopicDirective("user.events", "producer", "svc2", List.empty)

        DirectiveFieldRef.Topic.extract(directive1) shouldBe "payments.transactions"
        DirectiveFieldRef.Topic.extract(directive2) shouldBe "user.events"
      }
    }

    "Role case object" should {

      "be a DirectiveFieldRef" in {
        DirectiveFieldRef.Role shouldBe a[DirectiveFieldRef]
      }

      "extract role from TopicDirective" in {
        val directive = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty
        )

        DirectiveFieldRef.Role.extract(directive) shouldBe "producer"
      }

      "extract consumer role" in {
        val directive = TopicDirective(
          topic = "orders.events",
          role = "consumer",
          clientPrincipal = "kafka-consumer-svc",
          eventFilters = List.empty
        )

        DirectiveFieldRef.Role.extract(directive) shouldBe "consumer"
      }
    }

    "ClientPrincipal case object" should {

      "be a DirectiveFieldRef" in {
        DirectiveFieldRef.ClientPrincipal shouldBe a[DirectiveFieldRef]
      }

      "extract clientPrincipal from TopicDirective" in {
        val directive = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-producer-svc",
          eventFilters = List.empty
        )

        DirectiveFieldRef.ClientPrincipal.extract(directive) shouldBe "kafka-producer-svc"
      }

      "extract different client principals" in {
        val directive1 = TopicDirective("topic1", "producer", "service-account-1", List.empty)
        val directive2 = TopicDirective("topic2", "consumer", "oauth-client-xyz", List.empty)

        DirectiveFieldRef.ClientPrincipal.extract(directive1) shouldBe "service-account-1"
        DirectiveFieldRef.ClientPrincipal.extract(directive2) shouldBe "oauth-client-xyz"
      }
    }

    "validFields list" should {

      "contain topic, role, and clientPrincipal" in {
        DirectiveFieldRef.validFields should contain("topic")
        DirectiveFieldRef.validFields should contain("role")
        DirectiveFieldRef.validFields should contain("clientPrincipal")
      }

      "have exactly 3 fields" in {
        DirectiveFieldRef.validFields.size shouldBe 3
      }
    }

    "fromString" should {

      "return Right(Topic) for 'topic'" in {
        val result = DirectiveFieldRef.fromString("topic")

        result shouldBe Right(DirectiveFieldRef.Topic)
      }

      "return Right(Role) for 'role'" in {
        val result = DirectiveFieldRef.fromString("role")

        result shouldBe Right(DirectiveFieldRef.Role)
      }

      "return Right(ClientPrincipal) for 'clientPrincipal'" in {
        val result = DirectiveFieldRef.fromString("clientPrincipal")

        result shouldBe Right(DirectiveFieldRef.ClientPrincipal)
      }

      "return Left(VaultMappingException) for unknown field" in {
        val result = DirectiveFieldRef.fromString("unknownField")

        result.isLeft shouldBe true
        result.left.getOrElse(null) shouldBe a[VaultMappingException]
      }

      "include unknown field name in error message" in {
        val result = DirectiveFieldRef.fromString("invalidField")

        result.isLeft shouldBe true
        result.left.foreach { ex =>
          ex.getMessage should include("invalidField")
        }
      }

      "include valid fields in error message" in {
        val result = DirectiveFieldRef.fromString("unknown")

        result.isLeft shouldBe true
        result.left.foreach { ex =>
          ex.getMessage should include("topic")
          ex.getMessage should include("role")
          ex.getMessage should include("clientPrincipal")
        }
      }

      "be case-sensitive" in {
        val resultTopic = DirectiveFieldRef.fromString("Topic")
        val resultROLE = DirectiveFieldRef.fromString("ROLE")
        val resultPrincipal = DirectiveFieldRef.fromString("ClientPrincipal")

        resultTopic.isLeft shouldBe true
        resultROLE.isLeft shouldBe true
        resultPrincipal.isLeft shouldBe true
      }

      "handle empty string" in {
        val result = DirectiveFieldRef.fromString("")

        result.isLeft shouldBe true
      }

      "handle whitespace" in {
        val result = DirectiveFieldRef.fromString(" topic ")

        result.isLeft shouldBe true
      }
    }

    "used for extraction" should {

      "work with pattern matching" in {
        val directive = TopicDirective(
          topic = "orders.events",
          role = "producer",
          clientPrincipal = "kafka-svc",
          eventFilters = List.empty
        )

        val fieldRef: DirectiveFieldRef = DirectiveFieldRef.Topic
        val value = fieldRef match {
          case DirectiveFieldRef.Topic => fieldRef.extract(directive)
          case DirectiveFieldRef.Role => fieldRef.extract(directive)
          case DirectiveFieldRef.ClientPrincipal => fieldRef.extract(directive)
        }

        value shouldBe "orders.events"
      }

      "work with fromString and extract" in {
        val directive = TopicDirective(
          topic = "payments.events",
          role = "consumer",
          clientPrincipal = "payments-svc",
          eventFilters = List.empty
        )

        DirectiveFieldRef.fromString("topic").map(_.extract(directive)) shouldBe Right("payments.events")
        DirectiveFieldRef.fromString("role").map(_.extract(directive)) shouldBe Right("consumer")
        DirectiveFieldRef.fromString("clientPrincipal").map(_.extract(directive)) shouldBe Right("payments-svc")
      }
    }
  }
}
