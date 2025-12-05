package io.distia.probe.core.glue.world.fixtures.actors

import org.apache.pekko.actor.typed.ActorRef
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.fixtures.{ServiceInterfaceResponsesFixture, TestHarnessFixtures}
import io.distia.probe.core.models.KafkaConsumerStreamingCommands.*
import io.distia.probe.core.actors.KafkaConsumerStreamingActor
import io.distia.probe.core.pubsub.models.{ConsumedAck, ConsumedNack, ConsumedResult}
import io.distia.probe.core.pubsub.SerdesFactory
import io.distia.probe.common.models.{EventFilter, KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.util.{Failure, Success}
import java.util.UUID
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/**
 * Fixture for KafkaConsumerStreamingActor component tests.
 *
 * Responsibilities:
 * - Actor spawning with Testcontainers Kafka + Schema Registry
 * - State management (actor refs, topics, consumed events, results)
 * - Raw KafkaProducer setup for seeding test data
 * - Result tracking (consumed events by correlationId)
 *
 * Fixtures Used:
 * - TestHarnessFixtures (CloudEvent, TopicDirective, KafkaSecurityDirective, SerdesFixtures)
 * - ServiceInterfaceResponsesFixture (if needed for responses)
 *
 * Architecture:
 * - Uses real Testcontainers (Kafka + Schema Registry) via ActorWorld
 * - Component tests use PLAINTEXT security (no SASL)
 * - Real Kafka consume operations via Pekko Streams committableSource
 * - Real Schema Registry deserialization with magic bytes
 * - ProbeScalaDsl integration for fetching consumed events
 *
 * Thread Safety: ActorWorld is single-threaded (Cucumber sequential execution).
 */
trait KafkaConsumerStreamingActorFixture
  extends ServiceInterfaceResponsesFixture
  with TestHarnessFixtures {
  this: ActorWorld =>

  // ==========================================================================
  // Actor References
  // ==========================================================================

  /**
   * KafkaConsumerStreamingActor under test.
   * Spawned by "Given a KafkaConsumerStreamingActor is spawned" step.
   */
  var consumerStreamingActor: Option[ActorRef[KafkaConsumerStreamingCommand]] = None

  // ==========================================================================
  // State Management
  // ==========================================================================

  /**
   * Current topic for consumer streaming tests.
   * Set by actor spawn step, used by message production steps.
   */
  var consumerTopic: Option[String] = None

  /**
   * Consumed events map for verification.
   * Key: correlationId string (from feature file)
   * Value: ConsumedResult (ConsumedAck | ConsumedNack)
   */
  var consumedEvents: Map[String, ConsumedResult] = Map.empty

  /**
   * Last consumed result (for single-event scenarios).
   */
  var lastConsumedResult: Option[ConsumedResult] = None

  /**
   * Event correlation ID map.
   * Maps event identifiers from feature files to their correlation UUIDs.
   * Key: event identifier string (e.g., "event-001" from feature file)
   * Value: UUID used as CloudEvent correlationid field
   */
  var eventCorrelationIds: Map[String, UUID] = Map.empty

  /**
   * Count of events stored in consumer registry (for verification).
   */
  var eventsStoredCount: Int = 0

  /**
   * Event filters for TopicDirective (from Given step).
   * Stores EventFilter instances configured in feature file.
   */
  var eventFilters: List[EventFilter] = List.empty

  /**
   * Last consumed record from Kafka (for step verification).
   * Stores the raw ConsumerRecord from the last consume operation.
   */
  var lastConsumedRecord: Option[ConsumerRecord[Array[Byte], Array[Byte]]] = None

  // ==========================================================================
  // Actor Spawning
  // ==========================================================================

  /**
   * Spawn KafkaConsumerStreamingActor with Testcontainers Kafka + Schema Registry.
   *
   * @param testIdStr Test ID string (for actor name)
   * @param topic Kafka topic
   * @param directive TopicDirective with event filters
   * @param securityDirective KafkaSecurityDirective (PLAINTEXT for tests)
   * @return Spawned actor reference
   */
  def spawnConsumerStreamingActor(
    testIdStr: String,
    topic: String,
    directive: TopicDirective,
    securityDirective: KafkaSecurityDirective
  ): ActorRef[KafkaConsumerStreamingCommand] = {
    consumerTopic = Some(topic)
    if (!isInfrastructureRunning) {
      startInfrastructure()
    }
    if (SerdesFactory.client.isEmpty) {
      initializeSerdesFactory(schemaRegistryUrl) match
        case Success(_) =>
        case Failure(ex) =>
          throw new IllegalStateException(s"Failed to initialize SerdesFactory: ${ex.getMessage}", ex)
    }
    deleteAllSchemasForTopic(topic)
    registerCloudEventKeySchema(topic) match
      case Success(keySchemaId) =>
      case Failure(ex) =>
        throw new IllegalStateException(s"Failed to register CloudEvent key schema: ${ex.getMessage}", ex)
    registerTestPayloadSchema(topic) match
      case Success(valueSchemaId) =>
      case Failure(ex) =>
        throw new IllegalStateException(s"Failed to register TestPayload value schema: ${ex.getMessage}", ex)
    val testId: UUID = UUID.nameUUIDFromBytes(testIdStr.getBytes("UTF-8"))
    val actor = testKit.spawn(
      KafkaConsumerStreamingActor(
        testId = testId,
        directive = directive,
        securityDirective = securityDirective,
        bootstrapServers = kafkaBootstrapServers
      ),
      s"consumer-streaming-$testIdStr"
    )

    consumerStreamingActor = Some(actor)
    actor
  }

  // ==========================================================================
  // Cleanup
  // ==========================================================================

  /**
   * Reset consumer streaming state between scenarios.
   * Called by ActorWorld.shutdown() after each scenario.
   */
  def resetConsumerStreamingState(): Unit = {
    // Clean up Schema Registry schemas for the topic (if any)
    consumerTopic.foreach { topic =>
      deleteAllSchemasForTopic(topic)
    }

    consumerStreamingActor = None
    consumerTopic = None
    consumedEvents = Map.empty
    lastConsumedResult = None
    lastConsumedRecord = None
    eventCorrelationIds = Map.empty
    eventsStoredCount = 0
    eventFilters = List.empty[EventFilter]
  }


  /**
   * Consume latest message from Kafka topic.
   *
   * Creates temporary consumer, polls for message, cleans up.
   *
   * @param topic            Kafka topic to consume from
   * @param bootstrapServers Kafka bootstrap servers
   * @param timeout          Poll timeout (default 5 seconds)
   * @return Latest ConsumerRecord
   *
   *         Example:
   * {{{
   *   val record = consumeMessageFromKafka("orders", "localhost:9092")
   *   validateSchemaRegistryFormat(record.key())
   *   validateSchemaRegistryFormat(record.value())
   * }}} */
  def consumeMessageFromKafka(
    topic: String,
    bootstrapServers: String,
    timeout: Duration = 5.seconds
  ): ConsumerRecord[Array[Byte], Array[Byte]] =
    val consumerProps = new java.util.Properties()
    consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, s"test-consumer-verify-${UUID.randomUUID()}")
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getName)
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getName)

    val consumer = new KafkaConsumer[Array[Byte], Array[Byte]](consumerProps)
    consumer.subscribe(List(topic).asJava)

    try
      val records = consumer.poll(java.time.Duration.ofSeconds(timeout.toSeconds))
      withClue(s"No messages found in topic '$topic' within $timeout:") {
        records.isEmpty shouldBe false
      }
      records.iterator().next()
    finally
      consumer.close()

  /**
   * Consume all messages from Kafka topic.
   *
   * Useful for FIFO order verification.
   *
   * @param topic            Kafka topic to consume from
   * @param bootstrapServers Kafka bootstrap servers
   * @param expectedCount    Expected number of messages
   * @param timeout          Poll timeout (default 5 seconds)
   * @return List of ConsumerRecords
   *
   *         Example:
   * {{{
   *   val records = consumeAllMessagesFromKafka("orders", "localhost:9092", 3)
   *   records.size shouldBe 3
   * }}} */
  def consumeAllMessagesFromKafka(
    topic: String,
    bootstrapServers: String,
    expectedCount: Int,
    timeout: Duration = 5.seconds
  ): List[ConsumerRecord[Array[Byte], Array[Byte]]] =
    val consumerProps = new java.util.Properties()
    consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, s"test-consumer-all-${UUID.randomUUID()}")
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getName)
    consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer].getName)

    val consumer = new KafkaConsumer[Array[Byte], Array[Byte]](consumerProps)
    consumer.subscribe(List(topic).asJava)

    try
      val records = consumer.poll(java.time.Duration.ofSeconds(timeout.toSeconds))
      withClue(s"Expected $expectedCount messages in topic '$topic', found ${records.count()}:") {
        records.count() shouldBe expectedCount
      }
      records.iterator().asScala.toList
    finally
      consumer.close

}
