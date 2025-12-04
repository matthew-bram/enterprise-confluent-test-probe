package io.distia.probe
package core
package pubsub
package models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Tests PubSub DSL exceptions.
 *
 * Tests all exception types in pubsub.models.PubSubExceptions:
 * - DslNotInitializedException
 * - ActorNotRegisteredException
 * - ProducerNotAvailableException
 * - ConsumerNotAvailableException
 * - KafkaProduceException
 * - SchemaRegistryNotInitializedException
 * - SchemaNotFoundException
 *
 * Test Strategy: Unit tests (no infrastructure)
 */
class PubSubExceptionsSpec extends AnyWordSpec with Matchers {

  "DslNotInitializedException" should {

    "construct with default message" in {
      val exception = DslNotInitializedException()

      exception.getMessage shouldBe "DSL not initialized - call registerSystem first"
      exception.getCause shouldBe null
    }

    "construct with custom message" in {
      val exception = DslNotInitializedException("Custom error message")

      exception.getMessage shouldBe "Custom error message"
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Init failed")
      val exception = DslNotInitializedException("DSL error", Some(cause))

      exception.getMessage shouldBe "DSL error"
      exception.getCause shouldBe cause
    }

    "extend RuntimeException and DslException" in {
      val exception = DslNotInitializedException("Error")

      exception shouldBe a[RuntimeException]
      exception shouldBe a[DslException]
    }
  }

  "ActorNotRegisteredException" should {

    "construct with message only" in {
      val exception = ActorNotRegisteredException("Actor not found in registry")

      exception.getMessage shouldBe "Actor not found in registry"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new IllegalStateException("Registry empty")
      val exception = ActorNotRegisteredException("Actor missing", Some(cause))

      exception.getMessage shouldBe "Actor missing"
      exception.getCause shouldBe cause
    }

    "extend RuntimeException and DslException" in {
      val exception = ActorNotRegisteredException("Error")

      exception shouldBe a[RuntimeException]
      exception shouldBe a[DslException]
    }
  }

  "ProducerNotAvailableException" should {

    "construct with message only" in {
      val exception = ProducerNotAvailableException("Producer not initialized")

      exception.getMessage shouldBe "Producer not initialized"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Init failed")
      val exception = ProducerNotAvailableException("No producer", Some(cause))

      exception.getMessage shouldBe "No producer"
      exception.getCause shouldBe cause
    }

    "construct with default message" in {
      val exception = ProducerNotAvailableException()

      exception.getMessage should include("Producer actor not available")
    }

    "extend RuntimeException and DslException" in {
      val exception = ProducerNotAvailableException("Error")

      exception shouldBe a[RuntimeException]
      exception shouldBe a[DslException]
    }
  }

  "ConsumerNotAvailableException" should {

    "construct with message only" in {
      val exception = ConsumerNotAvailableException("Consumer not initialized")

      exception.getMessage shouldBe "Consumer not initialized"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Init failed")
      val exception = ConsumerNotAvailableException("No consumer", Some(cause))

      exception.getMessage shouldBe "No consumer"
      exception.getCause shouldBe cause
    }

    "construct with default message" in {
      val exception = ConsumerNotAvailableException()

      exception.getMessage should include("Consumer actor not available")
    }

    "extend RuntimeException and DslException" in {
      val exception = ConsumerNotAvailableException("Error")

      exception shouldBe a[RuntimeException]
      exception shouldBe a[DslException]
    }
  }

  "KafkaProduceException" should {

    "construct with message only" in {
      val exception = KafkaProduceException("Failed to produce to Kafka")

      exception.getMessage shouldBe "Failed to produce to Kafka"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Broker unavailable")
      val exception = KafkaProduceException("Produce failed", Some(cause))

      exception.getMessage shouldBe "Produce failed"
      exception.getCause shouldBe cause
    }

    "extend RuntimeException and DslException" in {
      val exception = KafkaProduceException("Error")

      exception shouldBe a[RuntimeException]
      exception shouldBe a[DslException]
    }
  }

  "SchemaRegistryNotInitializedException" should {

    "construct with message only" in {
      val exception = SchemaRegistryNotInitializedException("Schema Registry not ready")

      exception.getMessage shouldBe "Schema Registry not ready"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Init failed")
      val exception = SchemaRegistryNotInitializedException("Registry error", Some(cause))

      exception.getMessage shouldBe "Registry error"
      exception.getCause shouldBe cause
    }

    "construct with default message" in {
      val exception = SchemaRegistryNotInitializedException()

      exception.getMessage should include("Schema Registry client not initialized")
    }

    "extend RuntimeException and DslException" in {
      val exception = SchemaRegistryNotInitializedException("Error")

      exception shouldBe a[RuntimeException]
      exception shouldBe a[DslException]
    }
  }

  "SchemaNotFoundException" should {

    "construct with message only" in {
      val exception = SchemaNotFoundException("Schema not found for topic")

      exception.getMessage shouldBe "Schema not found for topic"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Registry lookup failed")
      val exception = SchemaNotFoundException("Schema missing", Some(cause))

      exception.getMessage shouldBe "Schema missing"
      exception.getCause shouldBe cause
    }

    "extend RuntimeException and DslException" in {
      val exception = SchemaNotFoundException("Error")

      exception shouldBe a[RuntimeException]
      exception shouldBe a[DslException]
    }
  }

  "DslException hierarchy" should {

    "allow catching all exceptions as DslException" in {
      val exceptions: Seq[DslException] = Seq(
        DslNotInitializedException("dsl"),
        ActorNotRegisteredException("actor"),
        ProducerNotAvailableException("producer"),
        ConsumerNotAvailableException("consumer"),
        KafkaProduceException("kafka"),
        SchemaRegistryNotInitializedException("registry"),
        SchemaNotFoundException("schema")
      )

      exceptions.foreach { ex =>
        ex shouldBe a[DslException]
        ex shouldBe a[RuntimeException]
      }
    }
  }
}
