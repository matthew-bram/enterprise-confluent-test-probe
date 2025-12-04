package io.distia.probe.core.fixtures

import io.distia.probe.common.models.{KafkaSecurityDirective => KafkaSecurityDirectiveModel, SecurityProtocol}

/**
 * Provides KafkaSecurityDirective test fixtures with factory methods.
 *
 * Mix this trait into test specs to access KafkaSecurityDirective factory:
 * {{{
 *   class MySpec extends AnyWordSpec with KafkaSecurityDirectiveFixtures {
 *     "test" in {
 *       val security = createProducerSecurity()
 *     }
 *   }
 * }}}
 *
 * Design Principles:
 * - Factory pattern with role-specific constructors
 * - Immutable instances
 * - Sensible defaults for common security configurations
 * - Type-safe construction
 *
 * Thread Safety: Immutable, thread-safe
 */
trait KafkaSecurityDirectiveFixtures {

  /**
   * KafkaSecurityDirective fixture providing factory methods for security configurations.
   *
   * Access via: KafkaSecurityDirective.createProducerSecurity(), KafkaSecurityDirective.withSaslSsl(), etc.
   */
  protected val KafkaSecurityDirective = KafkaSecurityDirectiveFixture

  /**
   * Create a producer KafkaSecurityDirective with default test values.
   *
   * Defaults:
   * - topic: "test-events"
   * - role: "producer"
   * - protocol: SecurityProtocol.PLAINTEXT
   * - jaasConfig: "stub-jaas-producer-config"
   *
   * @param topic Kafka topic name
   * @param protocol Security protocol (PLAINTEXT, SASL_SSL, SSL, SASL_PLAINTEXT)
   * @param jaasConfig JAAS configuration string
   * @return KafkaSecurityDirective for producer
   */
  def createProducerSecurity(
    topic: String = "test-events",
    protocol: SecurityProtocol = SecurityProtocol.PLAINTEXT,
    jaasConfig: String = "stub-jaas-producer-config"
  ): KafkaSecurityDirectiveModel = KafkaSecurityDirectiveModel(
    topic = topic,
    role = "producer",
    securityProtocol = protocol,
    jaasConfig = jaasConfig
  )

  /**
   * Create a consumer KafkaSecurityDirective with default test values.
   *
   * Defaults:
   * - topic: "test-events"
   * - role: "consumer"
   * - protocol: SecurityProtocol.PLAINTEXT
   * - jaasConfig: "stub-jaas-consumer-config"
   *
   * @param topic Kafka topic name
   * @param protocol Security protocol (PLAINTEXT, SASL_SSL, SSL, SASL_PLAINTEXT)
   * @param jaasConfig JAAS configuration string
   * @return KafkaSecurityDirective for consumer
   */
  def createConsumerSecurity(
    topic: String = "test-events",
    protocol: SecurityProtocol = SecurityProtocol.PLAINTEXT,
    jaasConfig: String = "stub-jaas-consumer-config"
  ): KafkaSecurityDirectiveModel = KafkaSecurityDirectiveModel(
    topic = topic,
    role = "consumer",
    securityProtocol = protocol,
    jaasConfig = jaasConfig
  )

  /**
   * Create a KafkaSecurityDirective with custom role.
   *
   * Generic factory for non-standard roles or custom configurations.
   *
   * @param topic Kafka topic name
   * @param role Custom role identifier
   * @param protocol Security protocol
   * @param jaasConfig JAAS configuration string
   * @return KafkaSecurityDirective with custom configuration
   */
  def createSecurityDirective(
    topic: String,
    role: String,
    protocol: SecurityProtocol = SecurityProtocol.PLAINTEXT,
    jaasConfig: String = "stub-jaas-config"
  ): KafkaSecurityDirectiveModel = KafkaSecurityDirectiveModel(
    topic = topic,
    role = role,
    securityProtocol = protocol,
    jaasConfig = jaasConfig
  )

  /**
   * Create a security directive with SASL_SSL for testing secure connections.
   *
   * @param topic Kafka topic name
   * @param role Either "producer" or "consumer"
   * @return KafkaSecurityDirective with SASL_SSL protocol
   */
  def createSecurityWithSaslSsl(
    topic: String = "secure-events",
    role: String = "producer"
  ): KafkaSecurityDirectiveModel = KafkaSecurityDirectiveModel(
    topic = topic,
    role = role,
    securityProtocol = SecurityProtocol.SASL_SSL,
    jaasConfig = s"org.apache.kafka.common.security.plain.PlainLoginModule required username='test-$role' password='test-password';"
  )

  /**
   * Create a security directive with SSL for testing TLS connections.
   *
   * @param topic Kafka topic name
   * @param role Either "producer" or "consumer"
   * @return KafkaSecurityDirective with SSL protocol
   */
  def createSecurityWithSsl(
    topic: String = "secure-events",
    role: String = "producer"
  ): KafkaSecurityDirectiveModel = KafkaSecurityDirectiveModel(
    topic = topic,
    role = role,
    securityProtocol = SecurityProtocol.SASL_SSL,
    jaasConfig = "stub-jaas-config" // SASL_SSL requires JAAS config
  )
}

/**
 * Companion object providing object-style access to KafkaSecurityDirective fixtures.
 *
 * Allows calling fixtures as: `KafkaSecurityDirectiveFixture.createProducerSecurity()`
 */
object KafkaSecurityDirectiveFixture extends KafkaSecurityDirectiveFixtures
