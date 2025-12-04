package io.distia.probe.common.models

import io.distia.probe.common.exceptions.VaultMappingException

/**
 * EventFilter - Kafka event type and version filter
 *
 * Defines which event types and payload versions a consumer should process or
 * a producer should generate. Used for event filtering in Kafka consumers and
 * schema validation in producers.
 *
 * @param eventType The event type identifier (e.g., "OrderCreated", "PaymentProcessed")
 * @param payloadVersion The semantic version of the event payload schema (e.g., "1.0.0", "2.1.0")
 */
case class EventFilter(eventType: String, payloadVersion: String)

/**
 * TopicDirective - Kafka topic configuration and metadata
 *
 * Represents the complete configuration needed for a Kafka producer or consumer to
 * operate on a specific topic. This directive combines topic metadata, role designation,
 * event filtering, and optional bootstrap server override.
 *
 * **Field Purposes**:
 * - `topic`: Kafka topic name (source of truth for topic operations)
 * - `role`: Designates whether this directive is for a "producer" or "consumer"
 * - `clientPrincipal`: OAuth client principal identifier for vault credential lookup
 * - `eventFilters`: List of event types and versions this directive handles
 * - `metadata`: Extensible key-value pairs for additional topic-specific configuration
 * - `bootstrapServers`: Optional override for cluster-specific bootstrap servers (for multi-cluster scenarios)
 *
 * **Validation**:
 * - Validated by TopicDirectiveValidator for uniqueness (topic + role) and format (bootstrapServers)
 * - Duplicate topic + role combinations are rejected with DuplicateTopicException
 * - Invalid bootstrapServers format is rejected with InvalidBootstrapServersException
 *
 * **Usage Examples**:
 * {{{
 * // Producer directive with default bootstrap servers
 * val producerDirective = TopicDirective(
 *   topic = "orders.events",
 *   role = "producer",
 *   clientPrincipal = "kafka-producer-svc",
 *   eventFilters = List(EventFilter("OrderCreated", "1.0.0"), EventFilter("OrderUpdated", "1.0.0")),
 *   metadata = Map("serdes" -> "json"),
 *   bootstrapServers = None
 * )
 *
 * // Consumer directive with multi-cluster override
 * val consumerDirective = TopicDirective(
 *   topic = "payments.events",
 *   role = "consumer",
 *   clientPrincipal = "kafka-consumer-svc",
 *   eventFilters = List(EventFilter("PaymentProcessed", "2.1.0")),
 *   metadata = Map("serdes" -> "avro"),
 *   bootstrapServers = Some("eu-west-broker-1:9092,eu-west-broker-2:9092")
 * )
 * }}}
 *
 * @param topic Kafka topic name (e.g., "orders.events", "payments.transactions")
 * @param role Role designation for this topic ("producer" or "consumer")
 * @param clientPrincipal OAuth client principal identifier for vault credential lookup
 * @param eventFilters List of event type and version filters for this topic
 * @param metadata Extensible key-value pairs for additional topic-specific configuration (default: empty map)
 * @param bootstrapServers Optional override for cluster-specific bootstrap servers in "host:port,host:port" format (default: None)
 *
 * @see EventFilter for event filtering configuration
 * @see io.distia.probe.common.validation.TopicDirectiveValidator for validation rules
 * @see io.distia.probe.common.exceptions.DuplicateTopicException for duplicate topic handling
 * @see io.distia.probe.common.exceptions.InvalidBootstrapServersException for bootstrap server validation
 */
case class TopicDirective(
  topic: String,
  role: String,
  clientPrincipal: String,
  eventFilters: List[EventFilter],
  metadata: Map[String, String] = Map.empty,
  bootstrapServers: Option[String] = None
)

/**
 * DirectiveFieldRef - Type-safe field reference for TopicDirective fields
 *
 * Sealed trait representing a type-safe reference to a TopicDirective field.
 * Used by Rosetta mapping system to dynamically extract field values from
 * TopicDirective instances during vault credential lookup.
 *
 * **Pattern**: This is a type-safe alternative to string-based reflection, providing
 * compile-time safety for field references while enabling dynamic field extraction.
 *
 * @see DirectiveFieldRef companion object for field reference constructors
 * @see io.distia.probe.common.rosetta.RosettaMapper for usage in vault credential mapping
 */
sealed trait DirectiveFieldRef {
  /**
   * Extract the referenced field value from a TopicDirective
   *
   * @param directive The TopicDirective instance to extract from
   * @return The field value as a String
   */
  def extract(directive: TopicDirective): String
}

/**
 * DirectiveFieldRef companion object - Field reference constructors and utilities
 *
 * Provides case objects for each TopicDirective field and utilities for
 * creating field references from string names (used in Rosetta mapping YAML).
 *
 * **Valid Fields**: "topic", "role", "clientPrincipal"
 *
 * **Usage in Rosetta Mapping**:
 * {{{
 * # vault-mapping.yaml
 * kafka-producer:
 *   path: "secrets/kafka/{{topic}}/{{role}}"
 *   mapping:
 *     clientId: "oauth.client.id"
 *     clientSecret: "oauth.client.secret"
 * }}}
 */
object DirectiveFieldRef {
  /**
   * Reference to TopicDirective.topic field
   *
   * Extracts the Kafka topic name (e.g., "orders.events")
   */
  case object Topic extends DirectiveFieldRef {
    def extract(directive: TopicDirective): String = directive.topic
  }

  /**
   * Reference to TopicDirective.role field
   *
   * Extracts the role designation ("producer" or "consumer")
   */
  case object Role extends DirectiveFieldRef {
    def extract(directive: TopicDirective): String = directive.role
  }

  /**
   * Reference to TopicDirective.clientPrincipal field
   *
   * Extracts the OAuth client principal identifier for vault credential lookup
   */
  case object ClientPrincipal extends DirectiveFieldRef {
    def extract(directive: TopicDirective): String = directive.clientPrincipal
  }

  /**
   * List of valid field names for error messages and validation
   */
  val validFields: List[String] = List("topic", "role", "clientPrincipal")

  /**
   * Create a DirectiveFieldRef from a string name
   *
   * Used by Rosetta mapping system to parse field references from YAML configuration.
   *
   * @param name Field name string ("topic", "role", or "clientPrincipal")
   * @return Right(DirectiveFieldRef) if valid, Left(VaultMappingException) if invalid
   */
  def fromString(name: String): Either[VaultMappingException, DirectiveFieldRef] = name match {
    case "topic" => Right(Topic)
    case "role" => Right(Role)
    case "clientPrincipal" => Right(ClientPrincipal)
    case unknown => Left(new VaultMappingException(
      s"Unknown TopicDirective field: $unknown. Valid fields: ${validFields.mkString(", ")}"
    ))
  }
}
