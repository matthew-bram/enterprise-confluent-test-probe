package io.distia.probe.common.models

/**
 * Kafka security protocol enumeration
 *
 * Defines the supported security protocols for Kafka connections with compile-time safety.
 *
 * **Usage**:
 * {{{
 * val protocol = SecurityProtocol.PLAINTEXT  // Local test mode
 * val protocol = SecurityProtocol.SASL_SSL   // Production OAuth mode
 * }}}
 *
 * @see KafkaSecurityDirective for usage in security directives
 */
enum SecurityProtocol:
  /** No authentication or encryption (local Testcontainers only) */
  case PLAINTEXT

  /** OAuth 2.0 authentication with TLS encryption (AWS/Azure/GCP production) */
  case SASL_SSL

/**
 * Kafka security directive for topic authentication
 *
 * Represents the complete security configuration needed for a Kafka producer or consumer
 * to authenticate and authorize operations on a specific topic. This directive combines
 * topic metadata with security protocol and JAAS configuration.
 *
 * **Field Sources**:
 * - `topic`: Derived from TopicDirective.topic (source of truth for topic metadata)
 * - `role`: Derived from TopicDirective.role (producer/consumer designation)
 * - `securityProtocol`: SecurityProtocol enum (PLAINTEXT for local tests, SASL_SSL for production)
 * - `jaasConfig`: Constructed by framework using JaasConfigBuilder from vault credentials + OAuth config (empty for PLAINTEXT)
 *
 * **Security Note**: The jaasConfig contains sensitive OAuth credentials (clientId, clientSecret,
 * tokenEndpoint) and must be handled securely. The clientSecret is encoded within the jaasConfig
 * string and should never be logged or exposed.
 *
 * **Usage Examples**:
 * {{{
 * // Production (SASL_SSL with OAuth)
 * val prodDirective = KafkaSecurityDirective(
 *   topic = "orders.events",
 *   role = "producer",
 *   securityProtocol = SecurityProtocol.SASL_SSL,
 *   jaasConfig = "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
 *                "oauth.client.id=\"kafka-producer-123\" " +
 *                "oauth.client.secret=\"***\" " +
 *                "oauth.token.endpoint.uri=\"https://oauth.company.com/token\" " +
 *                "oauth.scope=\"kafka.producer\";"
 * )
 *
 * // Local tests (PLAINTEXT, no JAAS)
 * val localDirective = KafkaSecurityDirective(
 *   topic = "orders.events",
 *   role = "producer",
 *   securityProtocol = SecurityProtocol.PLAINTEXT,
 *   jaasConfig = ""
 * )
 * }}}
 *
 * @param topic Kafka topic name (e.g., "orders.events", "payments.transactions")
 * @param role Role for this topic ("producer" or "consumer")
 * @param securityProtocol SecurityProtocol enum (PLAINTEXT or SASL_SSL)
 * @param jaasConfig Complete JAAS configuration string for Kafka OAuth authentication (empty for PLAINTEXT)
 *
 * @see SecurityProtocol for protocol options
 * @see TopicDirective for topic metadata source
 * @see io.distia.probe.services.vault.JaasConfigBuilder for jaasConfig construction
 */
case class KafkaSecurityDirective(
  topic: String,
  role: String,
  securityProtocol: SecurityProtocol,
  jaasConfig: String
)
