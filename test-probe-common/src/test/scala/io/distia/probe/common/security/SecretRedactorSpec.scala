package io.distia.probe.common.security

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for SecretRedactor
 * Tests secret redaction for security-sensitive logging
 */
private[security] class SecretRedactorSpec extends AnyWordSpec with Matchers {

  // ========== REDACT TESTS ==========

  "SecretRedactor.redact" when {

    "processing messages with jaasConfig" should {

      "redact simple jaasConfig value" in {
        val message = "Fetched jaasConfig=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule"
        val result = SecretRedactor.redact(message)

        result shouldBe "Fetched jaasConfig=***REDACTED***"
      }

      "redact jaasConfig value up to first delimiter" in {
        // The regex only captures up to whitespace/comma/brace, so it redacts the first token
        val message = "Config: jaasConfig=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required"
        val result = SecretRedactor.redact(message)

        result shouldBe "Config: jaasConfig=***REDACTED*** required"
      }

      "redact multiple jaasConfig values in same message" in {
        val message = "Producer jaasConfig=secret1 Consumer jaasConfig=secret2"
        val result = SecretRedactor.redact(message)

        result shouldBe "Producer jaasConfig=***REDACTED*** Consumer jaasConfig=***REDACTED***"
      }

      "redact jaasConfig at start of message" in {
        val message = "jaasConfig=sensitive-data rest of message"
        val result = SecretRedactor.redact(message)

        result shouldBe "jaasConfig=***REDACTED*** rest of message"
      }

      "redact jaasConfig at end of message" in {
        val message = "Message ends with jaasConfig=oauth-secret"
        val result = SecretRedactor.redact(message)

        result shouldBe "Message ends with jaasConfig=***REDACTED***"
      }

      "preserve message structure after redaction" in {
        val message = "Topic=orders jaasConfig=secret Bootstrap=localhost:9092"
        val result = SecretRedactor.redact(message)

        result should include("Topic=orders")
        result should include("jaasConfig=***REDACTED***")
        result should include("Bootstrap=localhost:9092")
      }
    }

    "processing messages with special characters" should {

      "stop at whitespace" in {
        val message = "jaasConfig=secret more text"
        val result = SecretRedactor.redact(message)

        result shouldBe "jaasConfig=***REDACTED*** more text"
      }

      "stop at comma" in {
        val message = "jaasConfig=secret,otherConfig=value"
        val result = SecretRedactor.redact(message)

        result shouldBe "jaasConfig=***REDACTED***,otherConfig=value"
      }

      "stop at closing brace" in {
        val message = "{jaasConfig=secret}"
        val result = SecretRedactor.redact(message)

        result shouldBe "{jaasConfig=***REDACTED***}"
      }

      "stop at closing parenthesis" in {
        val message = "(jaasConfig=secret)"
        val result = SecretRedactor.redact(message)

        result shouldBe "(jaasConfig=***REDACTED***)"
      }

      "stop at closing bracket" in {
        val message = "[jaasConfig=secret]"
        val result = SecretRedactor.redact(message)

        result shouldBe "[jaasConfig=***REDACTED***]"
      }
    }

    "processing messages without secrets" should {

      "return unchanged message without jaasConfig" in {
        val message = "Normal log message without secrets"
        val result = SecretRedactor.redact(message)

        result shouldBe message
      }

      "return unchanged empty message" in {
        val message = ""
        val result = SecretRedactor.redact(message)

        result shouldBe ""
      }

      "preserve other configuration values" in {
        val message = "bootstrap.servers=localhost:9092 security.protocol=SASL_SSL"
        val result = SecretRedactor.redact(message)

        result shouldBe message
      }

      "not redact partial matches" in {
        val message = "jaasConfigPath=/etc/kafka/jaas.conf"
        val result = SecretRedactor.redact(message)

        // Should not match because jaasConfig= is followed by path
        result should include("/etc/kafka/jaas.conf")
      }
    }

    "processing already redacted messages" should {

      "leave already redacted messages unchanged" in {
        val message = "jaasConfig=***REDACTED***"
        val result = SecretRedactor.redact(message)

        result shouldBe "jaasConfig=***REDACTED***"
      }
    }
  }

  // ========== CONTAINSSECRETS TESTS ==========

  "SecretRedactor.containsSecrets" when {

    "checking messages with secrets" should {

      "return true for message with jaasConfig" in {
        val message = "jaasConfig=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule"
        val result = SecretRedactor.containsSecrets(message)

        result shouldBe true
      }

      "return true for message with OAuth credentials" in {
        val message = "Config: jaasConfig=oauth.client.secret=secret123"
        val result = SecretRedactor.containsSecrets(message)

        result shouldBe true
      }

      "return true for message with any jaasConfig value" in {
        val message = "jaasConfig=anything"
        val result = SecretRedactor.containsSecrets(message)

        result shouldBe true
      }
    }

    "checking messages without secrets" should {

      "return false for message without jaasConfig" in {
        val message = "Normal log message without secrets"
        val result = SecretRedactor.containsSecrets(message)

        result shouldBe false
      }

      "return false for empty message" in {
        val message = ""
        val result = SecretRedactor.containsSecrets(message)

        result shouldBe false
      }

      "return false for already redacted message" in {
        val message = "jaasConfig=***REDACTED***"
        val result = SecretRedactor.containsSecrets(message)

        result shouldBe false
      }

      "return false for message with only jaasConfig mention" in {
        val message = "Please set the jaasConfig property"
        val result = SecretRedactor.containsSecrets(message)

        result shouldBe false
      }
    }

    "validating redaction" should {

      "return false after redact is applied" in {
        val original = "jaasConfig=secret-value"
        val redacted = SecretRedactor.redact(original)

        SecretRedactor.containsSecrets(original) shouldBe true
        SecretRedactor.containsSecrets(redacted) shouldBe false
      }

      "handle complex messages correctly" in {
        val message = "Topic=orders jaasConfig=secret Bootstrap=localhost:9092"
        val redacted = SecretRedactor.redact(message)

        SecretRedactor.containsSecrets(message) shouldBe true
        SecretRedactor.containsSecrets(redacted) shouldBe false
      }
    }
  }

  // ========== INTEGRATION TESTS ==========

  "SecretRedactor integration" when {

    "used in logging scenarios" should {

      "safely redact Kafka producer config first token" in {
        // The regex captures up to first delimiter - remaining content is preserved
        val config = """KafkaProducer(bootstrap.servers=broker:9092, jaasConfig=secretToken)"""
        val safe = SecretRedactor.redact(config)

        safe should not include "secretToken"
        safe should include("jaasConfig=***REDACTED***")
        safe should include("bootstrap.servers=broker:9092")
      }

      "safely redact Kafka consumer config first token" in {
        // The regex captures up to first delimiter (brace in this case)
        val config = """KafkaConsumer{jaasConfig=secretValue}"""
        val safe = SecretRedactor.redact(config)

        safe should not include "secretValue"
        safe should include("jaasConfig=***REDACTED***")
      }

      "handle JSON-like structures" in {
        val json = """{"producer": {"jaasConfig": "secret"}, "topic": "orders"}"""
        // Note: This won't match jaasConfig= pattern in JSON
        val safe = SecretRedactor.redact(json)

        // JSON format doesn't match the jaasConfig= pattern
        safe shouldBe json
      }

      "handle config with multiple secrets" in {
        val multiConfig = """
          producer.jaasConfig=secret1
          consumer.jaasConfig=secret2
        """.trim
        val safe = SecretRedactor.redact(multiConfig)

        safe should not include "secret1"
        safe should not include "secret2"
      }
    }

    "ensuring security" should {

      "redact jaasConfig value token" in {
        // The pattern captures the first token after jaasConfig=
        val sensitiveConfig = "jaasConfig=secretOAuthToken"
        val safe = SecretRedactor.redact(sensitiveConfig)

        safe should not include "secretOAuthToken"
        safe shouldBe "jaasConfig=***REDACTED***"
      }

      "redact jaasConfig in comma-separated list" in {
        val saslConfig = "bootstrap=localhost:9092,jaasConfig=secretValue,acks=all"
        val safe = SecretRedactor.redact(saslConfig)

        safe should not include "secretValue"
        safe shouldBe "bootstrap=localhost:9092,jaasConfig=***REDACTED***,acks=all"
      }
    }
  }
}
