package io.distia.probe
package core
package models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Unit tests for ProbeExceptions hierarchy.
 *
 * Tests all exception types in the Probe framework exception hierarchy,
 * verifying construction patterns, inheritance relationships, and
 * exception handling behavior.
 *
 * Coverage Focus:
 * - Exception construction (message only, message + cause)
 * - Inheritance hierarchy verification
 * - Throwability and catchability
 * - Proper cause chain preservation
 *
 * Test Strategy:
 * - Each exception type has dedicated test suite
 * - Tests verify both constructors (message-only and message+cause)
 * - Validates exception hierarchy relationships
 */
class ProbeExceptionsSpec extends AnyWordSpec with Matchers {

  "FatalBooting" should {

    "construct with message only" in {
      val exception = FatalBooting("System initialization failed")

      exception.getMessage shouldBe "System initialization failed"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Root cause")
      val exception = FatalBooting("Boot failed", Some(cause))

      exception.getMessage shouldBe "Boot failed"
      exception.getCause shouldBe cause
    }

    "be throwable" in {
      val exception = FatalBooting("Fatal error")

      an[FatalBooting] should be thrownBy {
        throw exception
      }
    }

    "extend Exception and ProbeExceptions" in {
      val exception = FatalBooting("Error")

      exception shouldBe a[Exception]
      exception shouldBe a[ProbeExceptions]
    }
  }

  "CucumberException" should {

    "construct with message only" in {
      val exception = CucumberException("Feature file not found")

      exception.getMessage shouldBe "Feature file not found"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new IllegalStateException("Invalid state")
      val exception = CucumberException("Cucumber execution failed", Some(cause))

      exception.getMessage shouldBe "Cucumber execution failed"
      exception.getCause shouldBe cause
    }

    "extend Exception and ProbeExceptions" in {
      val exception = CucumberException("Error")

      exception shouldBe a[Exception]
      exception shouldBe a[ProbeExceptions]
    }
  }

  "KafkaProducerException" should {

    "construct with message only" in {
      val exception = KafkaProducerException("Failed to send message")

      exception.getMessage shouldBe "Failed to send message"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Broker unavailable")
      val exception = KafkaProducerException("Producer error", Some(cause))

      exception.getMessage shouldBe "Producer error"
      exception.getCause shouldBe cause
    }
  }

  "KafkaConsumerException" should {

    "construct with message only" in {
      val exception = KafkaConsumerException("Failed to consume message")

      exception.getMessage shouldBe "Failed to consume message"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Deserialization error")
      val exception = KafkaConsumerException("Consumer error", Some(cause))

      exception.getMessage shouldBe "Consumer error"
      exception.getCause shouldBe cause
    }
  }

  "BlockStorageException" should {

    "construct with message only" in {
      val exception = BlockStorageException("S3 upload failed")

      exception.getMessage shouldBe "S3 upload failed"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Network timeout")
      val exception = BlockStorageException("Storage error", Some(cause))

      exception.getMessage shouldBe "Storage error"
      exception.getCause shouldBe cause
    }
  }

  "VaultConsumerException" should {

    "construct with message only" in {
      val exception = VaultConsumerException("Failed to fetch secret")

      exception.getMessage shouldBe "Failed to fetch secret"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Authentication failed")
      val exception = VaultConsumerException("Vault error", Some(cause))

      exception.getMessage shouldBe "Vault error"
      exception.getCause shouldBe cause
    }
  }

  "RpcClientException" should {

    "construct with message only" in {
      val exception = RpcClientException("RPC call failed")

      exception.getMessage shouldBe "RPC call failed"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Connection refused")
      val exception = RpcClientException("RPC error", Some(cause))

      exception.getMessage shouldBe "RPC error"
      exception.getCause shouldBe cause
    }
  }

  "ServiceTimeoutException" should {

    "construct with message only (using secondary constructor)" in {
      val exception = new ServiceTimeoutException("Actor did not respond within 25s")

      exception.getMessage shouldBe "Actor did not respond within 25s"
      exception.getCause shouldBe null
    }

    "construct with message and cause" in {
      val cause = new RuntimeException("Timeout")
      val exception = ServiceTimeoutException("Request timed out", cause)

      exception.getMessage shouldBe "Request timed out"
      exception.getCause shouldBe cause
    }

    "extend Exception and ProbeExceptions" in {
      val exception = new ServiceTimeoutException("Timeout")

      exception shouldBe a[Exception]
      exception shouldBe a[ProbeExceptions]
    }

    "be catchable as Exception" in {
      val exception = new ServiceTimeoutException("Timeout")

      a[ServiceTimeoutException] should be thrownBy {
        throw exception
      }
    }
  }

  "ServiceUnavailableException" should {

    "construct with message" in {
      val exception = ServiceUnavailableException("Circuit breaker open")

      exception.getMessage shouldBe "Circuit breaker open"
    }

    "extend Exception and ProbeExceptions" in {
      val exception = ServiceUnavailableException("Service down")

      exception shouldBe a[Exception]
      exception shouldBe a[ProbeExceptions]
    }

    "be throwable" in {
      val exception = ServiceUnavailableException("Unavailable")

      a[ServiceUnavailableException] should be thrownBy {
        throw exception
      }
    }
  }

  "ActorSystemNotReadyException" should {

    "construct with message" in {
      val exception = ActorSystemNotReadyException("Actor system still initializing")

      exception.getMessage shouldBe "Actor system still initializing"
    }

    "extend Exception and ProbeExceptions" in {
      val exception = ActorSystemNotReadyException("Not ready")

      exception shouldBe a[Exception]
      exception shouldBe a[ProbeExceptions]
    }

    "be throwable" in {
      val exception = ActorSystemNotReadyException("Not ready")

      an[ActorSystemNotReadyException] should be thrownBy {
        throw exception
      }
    }
  }

  "ProbeExceptions hierarchy" should {

    "allow catching all exceptions as ProbeExceptions" in {
      val exceptions: Seq[ProbeExceptions] = Seq(
        FatalBooting("fatal"),
        CucumberException("cucumber"),
        KafkaProducerException("producer"),
        KafkaConsumerException("consumer"),
        BlockStorageException("storage"),
        VaultConsumerException("vault"),
        RpcClientException("rpc"),
        new ServiceTimeoutException("timeout"),
        ServiceUnavailableException("unavailable"),
        ActorSystemNotReadyException("not ready")
      )

      exceptions.foreach { ex =>
        ex shouldBe a[ProbeExceptions]
        ex shouldBe a[Exception]
      }
    }
  }
}
