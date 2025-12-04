package io.distia.probe
package common
package validation

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import models.{EventFilter, TopicDirective}

private[common] class TopicDirectiveValidatorSpec extends AnyWordSpec with Matchers {

  def createTestDirective(
    topic: String,
    role: String = "producer",
    clientPrincipal: String = "test-client",
    bootstrapServers: Option[String] = None
  ): TopicDirective = TopicDirective(
    topic = topic,
    role = role,
    clientPrincipal = clientPrincipal,
    eventFilters = List(EventFilter("TestEvent", "1.0")),
    metadata = Map.empty,
    bootstrapServers = bootstrapServers
  )

  "TopicDirectiveValidator" when {

    "validateUniqueness" when {

      "given an empty list" should {

        "return Right(())" in {
          val result: Either[List[String], Unit] = TopicDirectiveValidator.validateUniqueness(List.empty)

          result shouldBe Right(())
        }
      }

      "given a single directive" should {

        "return Right(())" in {
          val directives: List[TopicDirective] = List(createTestDirective("topic-a"))
          val result: Either[List[String], Unit] = TopicDirectiveValidator.validateUniqueness(directives)

          result shouldBe Right(())
        }
      }

      "given multiple unique topics" should {

        "return Right(()) for two unique topics" in {
          val directives: List[TopicDirective] = List(
            createTestDirective("topic-a"),
            createTestDirective("topic-b")
          )
          val result: Either[List[String], Unit] = TopicDirectiveValidator.validateUniqueness(directives)

          result shouldBe Right(())
        }

        "return Right(()) for many unique topics" in {
          val directives: List[TopicDirective] = List(
            createTestDirective("orders"),
            createTestDirective("payments"),
            createTestDirective("notifications"),
            createTestDirective("analytics"),
            createTestDirective("audit-log")
          )
          val result: Either[List[String], Unit] = TopicDirectiveValidator.validateUniqueness(directives)

          result shouldBe Right(())
        }
      }

      "given duplicate topics" should {

        "return Left with error for single duplicate" in {
          val directives: List[TopicDirective] = List(
            createTestDirective("topic-a"),
            createTestDirective("topic-a")
          )
          val result: Either[List[String], Unit] = TopicDirectiveValidator.validateUniqueness(directives)

          result.isLeft shouldBe true
          val errors: List[String] = result.swap.getOrElse(List.empty)
          errors should have size 1
          errors.head should include("topic-a")
          errors.head should include("2 times")
        }

        "return Left with errors for multiple duplicates" in {
          val directives: List[TopicDirective] = List(
            createTestDirective("topic-a"),
            createTestDirective("topic-b"),
            createTestDirective("topic-a"),
            createTestDirective("topic-b"),
            createTestDirective("topic-c")
          )
          val result: Either[List[String], Unit] = TopicDirectiveValidator.validateUniqueness(directives)

          result.isLeft shouldBe true
          val errors: List[String] = result.swap.getOrElse(List.empty)
          errors should have size 2
          errors.exists(_.contains("topic-a")) shouldBe true
          errors.exists(_.contains("topic-b")) shouldBe true
        }

        "report correct count for triple occurrence" in {
          val directives: List[TopicDirective] = List(
            createTestDirective("topic-a"),
            createTestDirective("topic-a"),
            createTestDirective("topic-a")
          )
          val result: Either[List[String], Unit] = TopicDirectiveValidator.validateUniqueness(directives)

          result.isLeft shouldBe true
          val errors: List[String] = result.swap.getOrElse(List.empty)
          errors.head should include("3 times")
        }

        "sort errors alphabetically by topic name" in {
          val directives: List[TopicDirective] = List(
            createTestDirective("zebra-topic"),
            createTestDirective("alpha-topic"),
            createTestDirective("zebra-topic"),
            createTestDirective("alpha-topic")
          )
          val result: Either[List[String], Unit] = TopicDirectiveValidator.validateUniqueness(directives)

          result.isLeft shouldBe true
          val errors: List[String] = result.swap.getOrElse(List.empty)
          errors.head should include("alpha-topic")
          errors.last should include("zebra-topic")
        }
      }

      "handling case sensitivity" should {

        "treat different cases as different topics" in {
          val directives: List[TopicDirective] = List(
            createTestDirective("Topic-A"),
            createTestDirective("topic-a"),
            createTestDirective("TOPIC-A")
          )
          val result: Either[List[String], Unit] = TopicDirectiveValidator.validateUniqueness(directives)

          result shouldBe Right(())
        }

        "detect duplicates with same case" in {
          val directives: List[TopicDirective] = List(
            createTestDirective("Topic-A"),
            createTestDirective("Topic-A")
          )
          val result: Either[List[String], Unit] = TopicDirectiveValidator.validateUniqueness(directives)

          result.isLeft shouldBe true
          val errors: List[String] = result.swap.getOrElse(List.empty)
          errors.head should include("Topic-A")
        }
      }

      "handling special characters in topic names" should {

        "correctly identify duplicates with special characters" in {
          val directives: List[TopicDirective] = List(
            createTestDirective("my-topic_v1.0"),
            createTestDirective("my-topic_v1.0")
          )
          val result: Either[List[String], Unit] = TopicDirectiveValidator.validateUniqueness(directives)

          result.isLeft shouldBe true
          val errors: List[String] = result.swap.getOrElse(List.empty)
          errors.head should include("my-topic_v1.0")
        }

        "treat similar-looking topics as unique" in {
          val directives: List[TopicDirective] = List(
            createTestDirective("my-topic_v1.0"),
            createTestDirective("my-topic-v1.0"),
            createTestDirective("my_topic_v1.0")
          )
          val result: Either[List[String], Unit] = TopicDirectiveValidator.validateUniqueness(directives)

          result shouldBe Right(())
        }
      }
    }

    "validateBootstrapServersFormat" when {

      "given None" should {

        "return Right(())" in {
          val result: Either[String, Unit] = TopicDirectiveValidator.validateBootstrapServersFormat(None)

          result shouldBe Right(())
        }
      }

      "given valid single server" should {

        "accept standard host:port format" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("localhost:9092"))

          result shouldBe Right(())
        }

        "accept hostname with port" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("kafka-broker:9092"))

          result shouldBe Right(())
        }

        "accept IP address with port" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("192.168.1.100:9092"))

          result shouldBe Right(())
        }

        "accept FQDN with port" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("kafka.example.com:9092"))

          result shouldBe Right(())
        }

        "accept minimum valid port (1)" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("localhost:1"))

          result shouldBe Right(())
        }

        "accept maximum valid port (65535)" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("localhost:65535"))

          result shouldBe Right(())
        }

        "accept subdomain notation" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("kafka.prod.cluster.internal:9092"))

          result shouldBe Right(())
        }
      }

      "given valid multiple servers" should {

        "accept two servers" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("host1:9092,host2:9093"))

          result shouldBe Right(())
        }

        "accept three servers" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("broker1:9092,broker2:9092,broker3:9092"))

          result shouldBe Right(())
        }

        "accept mixed formats" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("localhost:9092,192.168.1.100:9093,kafka.example.com:9094"))

          result shouldBe Right(())
        }

        "handle whitespace between servers" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("host1:9092, host2:9093, host3:9094"))

          result shouldBe Right(())
        }
      }

      "given invalid input" should {

        "reject empty string" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some(""))

          result.isLeft shouldBe true
          result.swap.getOrElse("") should include("cannot be empty")
        }

        "reject whitespace-only string" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("   "))

          result.isLeft shouldBe true
          result.swap.getOrElse("") should include("cannot be empty")
        }

        "reject missing port" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("localhost"))

          result.isLeft shouldBe true
          result.swap.getOrElse("") should include("Invalid bootstrap server format")
          result.swap.getOrElse("") should include("localhost")
        }

        "reject non-numeric port" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("localhost:abc"))

          result.isLeft shouldBe true
          result.swap.getOrElse("") should include("Invalid bootstrap server format")
        }

        "reject port zero" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("localhost:0"))

          result.isLeft shouldBe true
          result.swap.getOrElse("") should include("Invalid bootstrap server format")
        }

        "reject port above 65535" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("localhost:65536"))

          result.isLeft shouldBe true
          result.swap.getOrElse("") should include("Invalid bootstrap server format")
        }

        "reject port above 99999" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("localhost:100000"))

          result.isLeft shouldBe true
          result.swap.getOrElse("") should include("Invalid bootstrap server format")
        }

        "reject missing host" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some(":9092"))

          result.isLeft shouldBe true
          result.swap.getOrElse("") should include("Invalid bootstrap server format")
        }

        "reject host with only special characters" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("---:9092"))

          result.isLeft shouldBe true
          result.swap.getOrElse("") should include("Invalid bootstrap server format")
        }

        "reject host starting with hyphen" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("-host:9092"))

          result.isLeft shouldBe true
          result.swap.getOrElse("") should include("Invalid bootstrap server format")
        }

        "reject host ending with hyphen" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("host-:9092"))

          result.isLeft shouldBe true
          result.swap.getOrElse("") should include("Invalid bootstrap server format")
        }

        "report all invalid entries in a list" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("valid:9092,invalid,also-invalid"))

          result.isLeft shouldBe true
          val errorMessage: String = result.swap.getOrElse("")
          errorMessage should include("invalid")
          errorMessage should include("also-invalid")
        }

        "include expected format hint in error" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("bad-entry"))

          result.isLeft shouldBe true
          result.swap.getOrElse("") should include("Expected format: host:port")
        }
      }

      "edge cases" should {

        "accept single character hostname" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("a:9092"))

          result shouldBe Right(())
        }

        "accept hostname with numbers" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("kafka1:9092"))

          result shouldBe Right(())
        }

        "accept hostname starting with number" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("1kafka:9092"))

          result shouldBe Right(())
        }

        "reject multiple colons" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("host:port:extra"))

          result.isLeft shouldBe true
        }

        "reject protocol prefix" in {
          val result: Either[String, Unit] =
            TopicDirectiveValidator.validateBootstrapServersFormat(Some("http://localhost:9092"))

          result.isLeft shouldBe true
        }
      }
    }

    "isValidHostPort" when {

      "checking valid entries" should {

        "return true for standard format" in {
          val result: Boolean = TopicDirectiveValidator.isValidHostPort("localhost:9092")

          result shouldBe true
        }

        "return true for IP address" in {
          val result: Boolean = TopicDirectiveValidator.isValidHostPort("192.168.1.1:9092")

          result shouldBe true
        }

        "return true for FQDN" in {
          val result: Boolean = TopicDirectiveValidator.isValidHostPort("kafka.example.com:9092")

          result shouldBe true
        }
      }

      "checking invalid entries" should {

        "return false for missing port" in {
          val result: Boolean = TopicDirectiveValidator.isValidHostPort("localhost")

          result shouldBe false
        }

        "return false for port zero" in {
          val result: Boolean = TopicDirectiveValidator.isValidHostPort("localhost:0")

          result shouldBe false
        }

        "return false for port above max" in {
          val result: Boolean = TopicDirectiveValidator.isValidHostPort("localhost:65536")

          result shouldBe false
        }

        "return false for empty host" in {
          val result: Boolean = TopicDirectiveValidator.isValidHostPort(":9092")

          result shouldBe false
        }
      }
    }

    "HOST_PORT_PATTERN constant" should {

      "be accessible and properly defined" in {
        TopicDirectiveValidator.HOST_PORT_PATTERN should not be null
        TopicDirectiveValidator.HOST_PORT_PATTERN.regex should include(":\\d")
      }

      "match valid hostname patterns" in {
        TopicDirectiveValidator.HOST_PORT_PATTERN.matches("localhost:9092") shouldBe true
        TopicDirectiveValidator.HOST_PORT_PATTERN.matches("kafka-1:9092") shouldBe true
        TopicDirectiveValidator.HOST_PORT_PATTERN.matches("a:1") shouldBe true
      }

      "not match invalid patterns" in {
        TopicDirectiveValidator.HOST_PORT_PATTERN.matches(":9092") shouldBe false
        TopicDirectiveValidator.HOST_PORT_PATTERN.matches("host:") shouldBe false
        TopicDirectiveValidator.HOST_PORT_PATTERN.matches("host") shouldBe false
      }
    }
  }
}
