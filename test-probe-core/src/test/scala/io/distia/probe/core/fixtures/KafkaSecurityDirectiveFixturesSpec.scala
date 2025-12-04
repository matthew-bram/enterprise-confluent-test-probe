package io.distia.probe.core.fixtures

import io.distia.probe.common.models.SecurityProtocol

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests KafkaSecurityDirectiveFixtures for security configuration factories.
 *
 * Verifies:
 * - Producer security directive factory (createProducerSecurity)
 * - Consumer security directive factory (createConsumerSecurity)
 * - Custom security directive factory (createSecurityDirective)
 * - SASL_SSL security factory (createSecurityWithSaslSsl)
 * - SSL security factory (createSecurityWithSsl)
 * - Security protocol variations (PLAINTEXT, SASL_SSL, SSL, SASL_PLAINTEXT)
 * - Role-specific JAAS configurations
 * - Topic and role combinations
 *
 * Test Strategy: Unit tests (no external dependencies)
 *
 * Dogfooding: Tests KafkaSecurityDirectiveFixtures using KafkaSecurityDirectiveFixtures itself
 */
class KafkaSecurityDirectiveFixturesSpec extends AnyWordSpec
  with Matchers
  with KafkaSecurityDirectiveFixtures {

  "KafkaSecurityDirectiveFixtures" should {

    "provide createProducerSecurity factory method" in {
      val security = createProducerSecurity()
      security should not be null
    }

    "provide createConsumerSecurity factory method" in {
      val security = createConsumerSecurity()
      security should not be null
    }

    "provide createSecurityDirective factory method" in {
      val security = createSecurityDirective(topic = "test-topic", role = "producer")
      security should not be null
    }

    "provide createSecurityWithSaslSsl factory method" in {
      val security = createSecurityWithSaslSsl()
      security should not be null
    }

    "provide createSecurityWithSsl factory method" in {
      val security = createSecurityWithSsl()
      security should not be null
    }
  }

  "createProducerSecurity" should {

    "create producer security with default values" in {
      val security = createProducerSecurity()

      security.topic shouldBe "test-events"
      security.role shouldBe "producer"
      security.securityProtocol shouldBe SecurityProtocol.PLAINTEXT
      security.jaasConfig shouldBe "stub-jaas-producer-config"
    }

    "override topic" in {
      val security = createProducerSecurity(topic = "order-events")

      security.topic shouldBe "order-events"
      security.role shouldBe "producer"
    }

    "override protocol to SASL_SSL" in {
      val security = createProducerSecurity(protocol = SecurityProtocol.SASL_SSL)

      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
      security.role shouldBe "producer"
    }

    "override protocol to PLAINTEXT explicitly" in {
      val security = createProducerSecurity(protocol = SecurityProtocol.PLAINTEXT)

      security.securityProtocol shouldBe SecurityProtocol.PLAINTEXT
    }

    "override jaasConfig" in {
      val customJaas = "org.apache.kafka.common.security.plain.PlainLoginModule required username='user' password='pass';"
      val security = createProducerSecurity(jaasConfig = customJaas)

      security.jaasConfig shouldBe customJaas
      security.role shouldBe "producer"
    }

    "support all parameter overrides together" in {
      val customJaas = "custom-jaas-config"
      val security = createProducerSecurity(
        topic = "payment-events",
        protocol = SecurityProtocol.SASL_SSL,
        jaasConfig = customJaas
      )

      security.topic shouldBe "payment-events"
      security.role shouldBe "producer"
      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
      security.jaasConfig shouldBe customJaas
    }
  }

  "createConsumerSecurity" should {

    "create consumer security with default values" in {
      val security = createConsumerSecurity()

      security.topic shouldBe "test-events"
      security.role shouldBe "consumer"
      security.securityProtocol shouldBe SecurityProtocol.PLAINTEXT
      security.jaasConfig shouldBe "stub-jaas-consumer-config"
    }

    "override topic" in {
      val security = createConsumerSecurity(topic = "user-events")

      security.topic shouldBe "user-events"
      security.role shouldBe "consumer"
    }

    "override protocol to SASL_SSL" in {
      val security = createConsumerSecurity(protocol = SecurityProtocol.SASL_SSL)

      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
      security.role shouldBe "consumer"
    }

    "override protocol to PLAINTEXT explicitly" in {
      val security = createConsumerSecurity(protocol = SecurityProtocol.PLAINTEXT)

      security.securityProtocol shouldBe SecurityProtocol.PLAINTEXT
    }

    "override jaasConfig" in {
      val customJaas = "consumer-specific-jaas"
      val security = createConsumerSecurity(jaasConfig = customJaas)

      security.jaasConfig shouldBe customJaas
      security.role shouldBe "consumer"
    }

    "support all parameter overrides together" in {
      val customJaas = "complete-consumer-jaas"
      val security = createConsumerSecurity(
        topic = "notification-events",
        protocol = SecurityProtocol.SASL_SSL,
        jaasConfig = customJaas
      )

      security.topic shouldBe "notification-events"
      security.role shouldBe "consumer"
      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
      security.jaasConfig shouldBe customJaas
    }
  }

  "createSecurityDirective" should {

    "create security directive with custom role" in {
      val security = createSecurityDirective(
        topic = "custom-events",
        role = "admin"
      )

      security.topic shouldBe "custom-events"
      security.role shouldBe "admin"
      security.securityProtocol shouldBe SecurityProtocol.PLAINTEXT
      security.jaasConfig shouldBe "stub-jaas-config"
    }

    "support custom protocol" in {
      val security = createSecurityDirective(
        topic = "secure-events",
        role = "producer",
        protocol = SecurityProtocol.SASL_SSL
      )

      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
    }

    "support custom jaasConfig" in {
      val customJaas = "generic-jaas-config"
      val security = createSecurityDirective(
        topic = "generic-events",
        role = "generic",
        jaasConfig = customJaas
      )

      security.jaasConfig shouldBe customJaas
    }

    "support all parameters" in {
      val customJaas = "full-jaas-config"
      val security = createSecurityDirective(
        topic = "complete-events",
        role = "custom-role",
        protocol = SecurityProtocol.SASL_SSL,
        jaasConfig = customJaas
      )

      security.topic shouldBe "complete-events"
      security.role shouldBe "custom-role"
      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
      security.jaasConfig shouldBe customJaas
    }
  }

  "createSecurityWithSaslSsl" should {

    "create security with SASL_SSL protocol by default" in {
      val security = createSecurityWithSaslSsl()

      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
      security.topic shouldBe "secure-events"
      security.role shouldBe "producer"
    }

    "include PlainLoginModule in jaasConfig" in {
      val security = createSecurityWithSaslSsl()

      security.jaasConfig should include ("org.apache.kafka.common.security.plain.PlainLoginModule")
      security.jaasConfig should include ("username=")
      security.jaasConfig should include ("password=")
    }

    "use role-specific username in jaasConfig" in {
      val producerSecurity = createSecurityWithSaslSsl(role = "producer")
      producerSecurity.jaasConfig should include ("username='test-producer'")

      val consumerSecurity = createSecurityWithSaslSsl(role = "consumer")
      consumerSecurity.jaasConfig should include ("username='test-consumer'")
    }

    "override topic" in {
      val security = createSecurityWithSaslSsl(topic = "sasl-topic")

      security.topic shouldBe "sasl-topic"
      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
    }

    "override role" in {
      val security = createSecurityWithSaslSsl(role = "consumer")

      security.role shouldBe "consumer"
      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
    }

    "support both topic and role overrides" in {
      val security = createSecurityWithSaslSsl(
        topic = "kafka-secure",
        role = "consumer"
      )

      security.topic shouldBe "kafka-secure"
      security.role shouldBe "consumer"
      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
      security.jaasConfig should include ("username='test-consumer'")
    }
  }

  "createSecurityWithSsl" should {

    "create security with SASL_SSL protocol by default" in {
      val security = createSecurityWithSsl()

      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
      security.topic shouldBe "secure-events"
      security.role shouldBe "producer"
    }

    "use stub jaasConfig" in {
      val security = createSecurityWithSsl()

      security.jaasConfig shouldBe "stub-jaas-config"
    }

    "override topic" in {
      val security = createSecurityWithSsl(topic = "ssl-topic")

      security.topic shouldBe "ssl-topic"
      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
    }

    "override role" in {
      val security = createSecurityWithSsl(role = "consumer")

      security.role shouldBe "consumer"
      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
    }
  }

  "KafkaSecurityDirectiveFixture object" should {

    "provide standalone createProducerSecurity method" in {
      val security = KafkaSecurityDirectiveFixture.createProducerSecurity()
      security.role shouldBe "producer"
    }

    "provide standalone createConsumerSecurity method" in {
      val security = KafkaSecurityDirectiveFixture.createConsumerSecurity()
      security.role shouldBe "consumer"
    }

    "provide standalone createSecurityDirective method" in {
      val security = KafkaSecurityDirectiveFixture.createSecurityDirective(
        topic = "test",
        role = "custom"
      )
      security.role shouldBe "custom"
    }

    "provide standalone createSecurityWithSaslSsl method" in {
      val security = KafkaSecurityDirectiveFixture.createSecurityWithSaslSsl()
      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
    }

    "provide standalone createSecurityWithSsl method" in {
      val security = KafkaSecurityDirectiveFixture.createSecurityWithSsl()
      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
    }
  }

  "Role-specific JAAS configurations" should {

    "use producer-specific JAAS for producer directives" in {
      val security = createProducerSecurity()
      security.jaasConfig should include ("producer")
    }

    "use consumer-specific JAAS for consumer directives" in {
      val security = createConsumerSecurity()
      security.jaasConfig should include ("consumer")
    }

    "use generic JAAS for custom role directives" in {
      val security = createSecurityDirective(topic = "test", role = "custom")
      security.jaasConfig shouldBe "stub-jaas-config"
    }

    "generate role-specific JAAS for SASL_SSL" in {
      val producerSecurity = createSecurityWithSaslSsl(role = "producer")
      producerSecurity.jaasConfig should include ("test-producer")

      val consumerSecurity = createSecurityWithSaslSsl(role = "consumer")
      consumerSecurity.jaasConfig should include ("test-consumer")
    }
  }

  "Security protocol variations" should {

    "support PLAINTEXT protocol" in {
      val security = createProducerSecurity(protocol = SecurityProtocol.PLAINTEXT)
      security.securityProtocol shouldBe SecurityProtocol.PLAINTEXT
    }

    "support SASL_SSL protocol" in {
      val security = createProducerSecurity(protocol = SecurityProtocol.SASL_SSL)
      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
    }

    "default to PLAINTEXT when not specified" in {
      val security = createProducerSecurity()
      security.securityProtocol shouldBe SecurityProtocol.PLAINTEXT
    }

    "use SASL_SSL for production scenarios" in {
      val security = createSecurityWithSaslSsl()
      security.securityProtocol shouldBe SecurityProtocol.SASL_SSL
    }
  }
}
