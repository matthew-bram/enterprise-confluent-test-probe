package io.distia.probe.core.fixtures

import io.distia.probe.common.models.{EventFilter, TopicDirective => TopicDirectiveModel}

/**
 * Provides TopicDirective test fixtures with factory methods.
 *
 * Mix this trait into test specs to access TopicDirective factory:
 * {{{
 *   class MySpec extends AnyWordSpec with TopicDirectiveFixtures {
 *     "test" in {
 *       val directive = createProducerDirective()
 *     }
 *   }
 * }}}
 *
 * Design Principles:
 * - Factory pattern with role-specific constructors
 * - Immutable instances
 * - Sensible defaults for common test scenarios
 * - Type-safe construction
 *
 * Thread Safety: Immutable, thread-safe
 */
trait TopicDirectiveFixtures {

  /**
   * TopicDirective fixture providing factory methods for producer/consumer directives.
   *
   * Access via: TopicDirective.createProducerDirective(), TopicDirective.createConsumerDirective(), etc.
   */
  protected val TopicDirective = TopicDirectiveFixture

  /**
   * Create a producer TopicDirective with default test values.
   *
   * Defaults:
   * - topic: "test-events"
   * - role: "producer"
   * - clientPrincipal: "test-producer-client"
   * - eventFilters: List(EventFilter("TestEvent", "1.0"))
   * - metadata: Map.empty
   * - bootstrapServers: None
   *
   * @param topic Kafka topic name
   * @param clientPrincipal Kafka client principal identifier
   * @param eventFilters List of EventFilter instances
   * @param metadata Optional metadata map
   * @param bootstrapServers Optional bootstrap servers (uses default if None)
   * @return TopicDirective configured for producer role
   */
  def createProducerDirective(
    topic: String = "test-events",
    clientPrincipal: String = "test-producer-client",
    eventFilters: List[EventFilter] = List(EventFilter("TestEvent", "1.0")),
    metadata: Map[String, String] = Map.empty,
    bootstrapServers: Option[String] = None
  ): TopicDirectiveModel = TopicDirectiveModel(
    topic = topic,
    role = "producer",
    clientPrincipal = clientPrincipal,
    eventFilters = eventFilters,
    metadata = metadata,
    bootstrapServers = bootstrapServers
  )

  /**
   * Create a consumer TopicDirective with default test values.
   *
   * Defaults:
   * - topic: "test-events"
   * - role: "consumer"
   * - clientPrincipal: "test-consumer-client"
   * - eventFilters: List(EventFilter("TestEvent", "1.0"))
   * - metadata: Map.empty
   * - bootstrapServers: None
   *
   * @param topic Kafka topic name
   * @param clientPrincipal Kafka client principal identifier
   * @param eventFilters List of EventFilter instances
   * @param metadata Optional metadata map
   * @param bootstrapServers Optional bootstrap servers (uses default if None)
   * @return TopicDirective configured for consumer role
   */
  def createConsumerDirective(
    topic: String = "test-events",
    clientPrincipal: String = "test-consumer-client",
    eventFilters: List[EventFilter] = List(EventFilter("TestEvent", "1.0")),
    metadata: Map[String, String] = Map.empty,
    bootstrapServers: Option[String] = None
  ): TopicDirectiveModel = TopicDirectiveModel(
    topic = topic,
    role = "consumer",
    clientPrincipal = clientPrincipal,
    eventFilters = eventFilters,
    metadata = metadata,
    bootstrapServers = bootstrapServers
  )

  /**
   * Create a TopicDirective with empty event filters.
   *
   * Useful for testing edge cases where no event filtering is configured.
   *
   * @param role Either "producer" or "consumer"
   * @param topic Kafka topic name
   * @return TopicDirective with no event filters
   */
  def createDirectiveWithoutEventFilters(
    role: String = "producer",
    topic: String = "test-events"
  ): TopicDirectiveModel = TopicDirectiveModel(
    topic = topic,
    role = role,
    clientPrincipal = s"test-$role-client",
    eventFilters = List.empty
  )

  /**
   * Create a TopicDirective with multiple event types for testing.
   *
   * Useful for testing scenarios with varied event type handling.
   *
   * @param role Either "producer" or "consumer"
   * @param topic Kafka topic name
   * @return TopicDirective with 3 different event types
   */
  def createDirectiveWithMultipleEventTypes(
    role: String = "producer",
    topic: String = "test-events"
  ): TopicDirectiveModel = TopicDirectiveModel(
    topic = topic,
    role = role,
    clientPrincipal = s"test-$role-client",
    eventFilters = List(
      EventFilter("TestEvent", "1.0"),
      EventFilter("UserCreated", "2.0"),
      EventFilter("OrderPlaced", "1.5")
    )
  )
}

/**
 * Companion object providing object-style access to TopicDirective fixtures.
 *
 * Allows calling fixtures as: `TopicDirectiveFixture.createProducerDirective()`
 */
object TopicDirectiveFixture extends TopicDirectiveFixtures
