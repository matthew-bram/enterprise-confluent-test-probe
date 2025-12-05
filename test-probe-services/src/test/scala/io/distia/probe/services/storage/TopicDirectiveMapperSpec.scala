package io.distia.probe
package services
package storage

import common.exceptions.InvalidTopicDirectiveFormatException
import common.models.{EventFilter, TopicDirective}
import fixtures.JimfsTestFixtures
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for TopicDirectiveMapper
 * Tests YAML parsing and Circe integration for topic directives
 */
private[services] class TopicDirectiveMapperSpec extends AnyWordSpec with Matchers {

  private val mapper: TopicDirectiveMapper = new TopicDirectiveMapper()

  // ========== VALID YAML PARSING TESTS ==========

  "TopicDirectiveMapper.parse" when {

    "parsing valid YAML with multiple topics" should {

      "parse all topics correctly" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.validTopicDirectiveYaml
        )

        directives should have size 2
      }

      "parse first topic with all fields" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.validTopicDirectiveYaml
        )

        val firstTopic: TopicDirective = directives.head
        firstTopic.topic shouldBe "order-events"
        firstTopic.role shouldBe "producer"
        firstTopic.clientPrincipal shouldBe "service-account-1"
      }

      "parse event filters correctly" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.validTopicDirectiveYaml
        )

        val firstTopic: TopicDirective = directives.head
        firstTopic.eventFilters should have size 2
        firstTopic.eventFilters should contain allOf (
          EventFilter("eventType", "OrderCreated"),
          EventFilter("region", "us-east-1")
        )
      }

      "parse metadata correctly" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.validTopicDirectiveYaml
        )

        val firstTopic: TopicDirective = directives.head
        firstTopic.metadata should contain key "description"
        firstTopic.metadata("description") shouldBe "Order creation events"
      }

      "parse second topic with different values" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.validTopicDirectiveYaml
        )

        val secondTopic: TopicDirective = directives(1)
        secondTopic.topic shouldBe "payment-events"
        secondTopic.role shouldBe "consumer"
        secondTopic.clientPrincipal shouldBe "service-account-2"
      }

      "parse second topic event filters" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.validTopicDirectiveYaml
        )

        val secondTopic: TopicDirective = directives(1)
        secondTopic.eventFilters should have size 1
        secondTopic.eventFilters should contain(EventFilter("paymentStatus", "completed"))
      }

      "parse second topic metadata" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.validTopicDirectiveYaml
        )

        val secondTopic: TopicDirective = directives(1)
        secondTopic.metadata should contain key "priority"
        secondTopic.metadata("priority") shouldBe "high"
      }
    }

    "parsing minimal YAML with single topic" should {

      "parse single topic successfully" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.minimalTopicDirectiveYaml
        )

        directives should have size 1
      }

      "parse all required fields" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.minimalTopicDirectiveYaml
        )

        val topic: TopicDirective = directives.head
        topic.topic shouldBe "test-topic"
        topic.role shouldBe "producer"
        topic.clientPrincipal shouldBe "test-service"
      }

      "handle empty event filters list" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.minimalTopicDirectiveYaml
        )

        val topic: TopicDirective = directives.head
        topic.eventFilters shouldBe empty
      }

      "default metadata to empty map" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.minimalTopicDirectiveYaml
        )

        val topic: TopicDirective = directives.head
        topic.metadata shouldBe empty
      }
    }
  }

  // ========== INVALID YAML TESTS ==========

  "TopicDirectiveMapper.parse" when {

    "parsing malformed YAML" should {

      "throw InvalidTopicDirectiveFormatException" in {
        val exception = intercept[InvalidTopicDirectiveFormatException] {
          mapper.parse(JimfsTestFixtures.malformedYaml)
        }

        exception.getMessage should include("Failed to parse YAML")
      }

      "include error details in exception message" in {
        val exception = intercept[InvalidTopicDirectiveFormatException] {
          mapper.parse(JimfsTestFixtures.malformedYaml)
        }

        exception.getMessage should not be empty
      }

      "wrap underlying parsing exception" in {
        val exception = intercept[InvalidTopicDirectiveFormatException] {
          mapper.parse(JimfsTestFixtures.malformedYaml)
        }

        exception.getCause should not be null
      }
    }

    "parsing YAML with missing topic field" should {

      "throw InvalidTopicDirectiveFormatException" in {
        val exception = intercept[InvalidTopicDirectiveFormatException] {
          mapper.parse(JimfsTestFixtures.missingTopicFieldYaml)
        }

        exception.getMessage should include("Failed to decode topic directives")
      }
    }

    "parsing YAML with missing role field" should {

      "throw InvalidTopicDirectiveFormatException" in {
        val exception = intercept[InvalidTopicDirectiveFormatException] {
          mapper.parse(JimfsTestFixtures.missingRoleFieldYaml)
        }

        exception.getMessage should include("Failed to decode topic directives")
      }
    }

    "parsing YAML with missing clientPrincipal field" should {

      "throw InvalidTopicDirectiveFormatException" in {
        val exception = intercept[InvalidTopicDirectiveFormatException] {
          mapper.parse(JimfsTestFixtures.missingClientPrincipalYaml)
        }

        exception.getMessage should include("Failed to decode topic directives")
      }
    }

    "parsing YAML with wrong structure" should {

      "throw InvalidTopicDirectiveFormatException" in {
        val exception = intercept[InvalidTopicDirectiveFormatException] {
          mapper.parse(JimfsTestFixtures.wrongStructureYaml)
        }

        exception.getMessage should include("Failed to decode topic directives")
      }
    }

    "parsing empty YAML" should {

      "throw InvalidTopicDirectiveFormatException" in {
        val exception = intercept[InvalidTopicDirectiveFormatException] {
          mapper.parse(JimfsTestFixtures.emptyYaml)
        }

        exception.getMessage should include("Failed to decode")
      }
    }
  }

  // ========== CIRCE DECODER TESTS ==========

  "TopicDirectiveMapper Circe decoders" when {

    "eventFilterDecoder" should {

      "decode event filter with key and value" in {
        val yaml: String =
          """topics:
            |  - topic: "test"
            |    role: "producer"
            |    clientPrincipal: "test-service"
            |    eventFilters:
            |      - key: "filterKey"
            |        value: "filterValue"
            |    metadata: {}
            |""".stripMargin

        val directives: List[TopicDirective] = mapper.parse(yaml)

        directives.head.eventFilters should contain(EventFilter("filterKey", "filterValue"))
      }

      "decode multiple event filters" in {
        val yaml: String =
          """topics:
            |  - topic: "test"
            |    role: "producer"
            |    clientPrincipal: "test-service"
            |    eventFilters:
            |      - key: "key1"
            |        value: "value1"
            |      - key: "key2"
            |        value: "value2"
            |      - key: "key3"
            |        value: "value3"
            |    metadata: {}
            |""".stripMargin

        val directives: List[TopicDirective] = mapper.parse(yaml)

        directives.head.eventFilters should have size 3
        directives.head.eventFilters should contain allOf (
          EventFilter("key1", "value1"),
          EventFilter("key2", "value2"),
          EventFilter("key3", "value3")
        )
      }
    }

    "topicDirectiveDecoder" should {

      "use derived decoder for TopicDirective" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.minimalTopicDirectiveYaml
        )

        // If decoder works, we should get a valid TopicDirective
        directives.head shouldBe a[TopicDirective]
      }
    }

    "topicsWrapperDecoder" should {

      "decode topics wrapper with list of topics" in {
        val directives: List[TopicDirective] = mapper.parse(
          JimfsTestFixtures.validTopicDirectiveYaml
        )

        // Wrapper should extract the topics list
        directives shouldBe a[List[_]]
        directives should not be empty
      }
    }
  }

  // ========== EDGE CASE TESTS ==========

  "TopicDirectiveMapper edge cases" when {

    "parsing YAML with special characters in values" should {

      "handle special characters correctly" in {
        val yaml: String =
          """topics:
            |  - topic: "test-topic.v1"
            |    role: "producer/consumer"
            |    clientPrincipal: "service-account@example.com"
            |    eventFilters:
            |      - key: "event.type"
            |        value: "Order::Created"
            |    metadata: {}
            |""".stripMargin

        val directives: List[TopicDirective] = mapper.parse(yaml)

        directives.head.topic shouldBe "test-topic.v1"
        directives.head.role shouldBe "producer/consumer"
        directives.head.clientPrincipal shouldBe "service-account@example.com"
        directives.head.eventFilters should contain(EventFilter("event.type", "Order::Created"))
      }
    }

    "parsing YAML with empty metadata" should {

      "default to empty map" in {
        val yaml: String =
          """topics:
            |  - topic: "test"
            |    role: "producer"
            |    clientPrincipal: "test-service"
            |    eventFilters: []
            |    metadata: {}
            |""".stripMargin

        val directives: List[TopicDirective] = mapper.parse(yaml)

        directives.head.metadata shouldBe empty
      }
    }

    "parsing YAML with whitespace variations" should {

      "handle different indentation and spacing" in {
        val yaml: String =
          """topics:
            |  - topic:    "test-topic"
            |    role:     "producer"
            |    clientPrincipal:  "test-service"
            |    eventFilters:  []
            |    metadata: {}
            |""".stripMargin

        val directives: List[TopicDirective] = mapper.parse(yaml)

        directives.head.topic shouldBe "test-topic"
        directives.head.role shouldBe "producer"
        directives.head.clientPrincipal shouldBe "test-service"
      }
    }

    "parsing YAML with unicode characters" should {

      "handle unicode correctly" in {
        val yaml: String =
          """topics:
            |  - topic: "测试主题"
            |    role: "producer"
            |    clientPrincipal: "测试服务"
            |    eventFilters: []
            |    metadata: {}
            |""".stripMargin

        val directives: List[TopicDirective] = mapper.parse(yaml)

        directives.head.topic shouldBe "测试主题"
        directives.head.clientPrincipal shouldBe "测试服务"
      }
    }
  }

  // ========== EXCEPTION HIERARCHY TESTS ==========

  "TopicDirectiveMapper exception handling" when {

    "catching InvalidTopicDirectiveFormatException" should {

      "be catchable as BlockStorageException" in {
        try {
          mapper.parse(JimfsTestFixtures.malformedYaml)
          fail("Expected InvalidTopicDirectiveFormatException")
        } catch {
          case _: common.exceptions.BlockStorageException =>
            // Should be catchable as parent exception type
            succeed
          case ex: Throwable =>
            fail(s"Expected BlockStorageException but got ${ex.getClass.getName}")
        }
      }

      "preserve exception chain" in {
        val exception = intercept[InvalidTopicDirectiveFormatException] {
          mapper.parse(JimfsTestFixtures.malformedYaml)
        }

        // Should have underlying Circe exception as cause
        exception.getCause should not be null
      }
    }
  }
}
