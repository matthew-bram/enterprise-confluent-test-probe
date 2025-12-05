package io.distia.probe.core.glue.world.fixtures.actors

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import io.distia.probe.core.testmodels.TestEventPayload
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.fixtures.{ServiceInterfaceResponsesFixture, TestHarnessFixtures}
import io.distia.probe.core.models.KafkaProducerStreamingCommands.*
import io.distia.probe.core.actors.KafkaProducerStreamingActor
import io.distia.probe.core.pubsub.models.{CloudEvent, ProduceResult, ProducedAck, ProducedNack}
import io.distia.probe.core.pubsub.SerdesFactory
import io.distia.probe.common.models.{KafkaSecurityDirective, SecurityProtocol, TopicDirective}

import scala.concurrent.duration.*
import scala.util.{Success, Failure}
import java.util.UUID

/**
 * Fixture for KafkaProducerStreamingActor component tests.
 *
 * Responsibilities:
 * - Actor spawning with Testcontainers Kafka + Schema Registry
 * - State management (actor refs, topics, CloudEvents, results, payloads)
 * - ProduceEvent command creation with REAL serialization
 * - Result tracking (single events + FIFO multi-event scenarios)
 *
 * Fixtures Used:
 * - TestHarnessFixtures (CloudEvent, TopicDirective, KafkaSecurityDirective, SerdesFixtures)
 * - ServiceInterfaceResponsesFixture (if needed for responses)
 *
 * Architecture:
 * - Uses real Testcontainers (Kafka + Schema Registry) via ActorWorld
 * - Component tests use PLAINTEXT security (no SASL)
 * - Real Kafka produce operations (not mocked)
 * - Real Schema Registry serialization with magic bytes
 *
 * Thread Safety: ActorWorld is single-threaded (Cucumber sequential execution).
 */
trait KafkaProducerStreamingActorFixture
  extends ServiceInterfaceResponsesFixture
  with TestHarnessFixtures {
  this: ActorWorld =>

  // ==========================================================================
  // Actor References
  // ==========================================================================

  /**
   * KafkaProducerStreamingActor under test.
   * Spawned by "Given a KafkaProducerStreamingActor is spawned" step.
   */
  var producerStreamingActor: Option[ActorRef[KafkaProducerStreamingCommand]] = None

  // ==========================================================================
  // State Management
  // ==========================================================================

  /**
   * Current topic for streaming tests.
   * Set by actor spawn step, used by CloudEvent creation steps.
   */
  var streamingTopic: Option[String] = None

  /**
   * Shared CloudEvent for single-event scenarios.
   * Created by "Given a valid CloudEvent is created" step.
   */
  var streamingCloudEvent: Option[CloudEvent] = None

  /**
   * CloudEvents map for FIFO multi-event scenarios.
   * Key: eventTestId string, Value: CloudEvent instance
   */
  var streamingCloudEvents: Map[String, CloudEvent] = Map.empty

  /**
   * Test payload for current scenario.
   * Created by step definitions, used in ProduceEvent creation.
   */
  var streamingTestPayload: Option[TestEventPayload] = None

  /**
   * OrderCreated payload for polymorphic event testing.
   * Created by polymorphic event step definitions.
   */
  var streamingOrderCreated: Option[io.distia.probe.core.testmodels.OrderCreated] = None

  /**
   * PaymentProcessed payload for polymorphic event testing.
   * Created by polymorphic event step definitions.
   */
  var streamingPaymentProcessed: Option[io.distia.probe.core.testmodels.PaymentProcessed] = None

  /**
   * OrderCreatedProtoWrapper payload for Protobuf polymorphic event testing.
   * Created by Protobuf step definitions.
   */
  var streamingOrderCreatedProto: Option[io.distia.probe.core.testmodels.OrderCreatedProtoWrapper] = None

  /**
   * PaymentProcessedProtoWrapper payload for Protobuf polymorphic event testing.
   * Created by Protobuf step definitions.
   */
  var streamingPaymentProcessedProto: Option[io.distia.probe.core.testmodels.PaymentProcessedProtoWrapper] = None

  /**
   * Produce results map for FIFO verification.
   * Key: eventTestId string, Value: ProduceResult (ProducedAck | ProducedNack)
   */
  var streamingProduceResults: Map[String, ProduceResult] = Map.empty

  /**
   * Last produce result (for single-event scenarios).
   */
  var lastStreamingResult: Option[ProduceResult] = None

  /**
   * Invalid payload bytes for schema validation testing.
   * Stores raw JSON bytes that intentionally violate schema constraints.
   */
  var streamingInvalidPayloadBytes: Option[Array[Byte]] = None

  /**
   * Missing field name for schema validation error verification.
   * Stores which field was intentionally omitted from the invalid payload.
   */
  var streamingMissingFieldName: Option[String] = None

  /**
   * Schema validation error captured during serialization attempt.
   * Used to verify error messages mention validation failure and missing field.
   */
  var streamingValidationError: Option[Throwable] = None

  /**
   * Schema ID for OrderCreated (for schema evolution testing).
   * Stores the schema ID returned from Schema Registry registration.
   */
  var streamingOrderCreatedSchemaId: Option[Int] = None

  /**
   * Schema ID for PaymentProcessed (for schema evolution testing).
   * Stores the schema ID returned from Schema Registry registration.
   */
  var streamingPaymentProcessedSchemaId: Option[Int] = None

  /**
   * Serialized bytes for OrderCreated (for schema ID verification).
   * Stores the serialized bytes with magic bytes header.
   */
  var streamingOrderCreatedBytes: Option[Array[Byte]] = None

  /**
   * Serialized bytes for PaymentProcessed (for schema ID verification).
   * Stores the serialized bytes with magic bytes header.
   */
  var streamingPaymentProcessedBytes: Option[Array[Byte]] = None

  // ==========================================================================
  // Actor Spawning
  // ==========================================================================

  /**
   * Spawn KafkaProducerStreamingActor with Testcontainers Kafka + Schema Registry.
   *
   * @param testIdStr Test ID string (for actor name)
   * @param topic Kafka topic
   * @param tokenRefreshInterval OAuth token refresh interval (default: 5 minutes)
   * @return Spawned actor reference
   */
  def spawnProducerStreamingActor(
    testIdStr: String,
    topic: String,
    tokenRefreshInterval: FiniteDuration = 5.minutes
  ): ActorRef[KafkaProducerStreamingCommand] = {
    streamingTopic = Some(topic)
    if (!isInfrastructureRunning) {
      startInfrastructure()
    }

    if (SerdesFactory.client.isEmpty) {
      initializeSerdesFactory(schemaRegistryUrl) match
        case Success(_) =>
        case Failure(ex) =>
          throw new IllegalStateException(s"Failed to initialize SerdesFactory: ${ex.getMessage}", ex)
    }
    println(s"[ACTOR-SPAWN] Cleaning up schemas for topic: $topic")
    deleteAllSchemasForTopic(topic)

    println(s"[ACTOR-SPAWN] Registering CloudEvent key schema for topic: $topic")
    registerCloudEventKeySchema(topic) match
      case Success(keySchemaId) =>
        println(s"[ACTOR-SPAWN] ✅ CloudEvent key schema registered with ID: $keySchemaId")
      case Failure(ex) =>
        println(s"[ACTOR-SPAWN] ❌ Failed to register CloudEvent key schema: ${ex.getMessage}")
        throw new IllegalStateException(s"Failed to register CloudEvent key schema: ${ex.getMessage}", ex)

    println(s"[ACTOR-SPAWN] Registering TestEventPayload value schema for topic: $topic")
    registerTestPayloadSchema(topic) match
      case Success(valueSchemaId) =>
        println(s"[ACTOR-SPAWN] ✅ TestEventPayload value schema registered with ID: $valueSchemaId")
      case Failure(ex) =>
        println(s"[ACTOR-SPAWN] ❌ Failed to register TestEventPayload value schema: ${ex.getMessage}")
        throw new IllegalStateException(s"Failed to register TestPayload value schema: ${ex.getMessage}", ex)

    val topicDirective = createProducerDirective(
      topic = topic,
      clientPrincipal = "test-principal"
    )
    val securityDirective = createSecurityDirective(
      topic = topic,
      role = "producer",
      protocol = SecurityProtocol.PLAINTEXT
    )

    // Spawn actor
    val actor = testKit.spawn(
      KafkaProducerStreamingActor(
        testId = UUID.randomUUID(),
        topicDirective = topicDirective,
        securityDirective = securityDirective,
        bootstrapServers = kafkaBootstrapServers
      ),
      s"producer-streaming-$testIdStr"
    )

    producerStreamingActor = Some(actor)
    actor
  }

  // ==========================================================================
  // ProduceEvent Creation
  // ==========================================================================

  /**
   * Create ProduceEvent command from CloudEvent with REAL serialization.
   *
   * Uses SerdesFactory to serialize both key and value:
   * - key: CloudEvent serialized as JSON with Schema Registry magic bytes
   * - value: TestEventPayload serialized as JSON with Schema Registry magic bytes
   * - headers: CloudEvents headers (ce-id, ce-type, etc.)
   *
   * This validates that KafkaProducerStreamingActor can handle:
   * - Real Schema Registry protocol (magic bytes)
   * - Real serialized payloads
   * - Schema validation failures (future scenarios)
   *
   * @param cloudEvent CloudEvent to serialize as key
   * @param payload Test payload to serialize as value
   * @param topic Kafka topic (for schema lookup)
   * @param replyTo Reply-to actor for ProduceResult
   * @return ProduceEvent command with real serialized bytes
   */
  def createProduceEvent(
    cloudEvent: CloudEvent,
    payload: TestEventPayload,
    topic: String,
    replyTo: ActorRef[ProduceResult]
  ): ProduceEvent = ProduceEvent(
      key = serializeCloudEventAsKey(cloudEvent, topic),
      value = serializeTestPayload(payload, topic),
      headers = Map(
        "ce-id" -> cloudEvent.id,
        "ce-type" -> cloudEvent.`type`,
        "ce-source" -> cloudEvent.source,
        "ce-subject" -> cloudEvent.subject
      ),
      replyTo = replyTo
    )

  // ==========================================================================
  // Cleanup
  // ==========================================================================

  /**
   * Reset streaming state between scenarios.
   * Called by ActorWorld.shutdown() after each scenario.
   */
  def resetStreamingState(): Unit = {
    // Clean up Schema Registry schemas for the topic
    streamingTopic.foreach { topic =>
      deleteAllSchemasForTopic(topic)
    }

    producerStreamingActor = None
    streamingTopic = None
    streamingCloudEvent = None
    streamingCloudEvents = Map.empty
    streamingProduceResults = Map.empty
    lastStreamingResult = None
    streamingTestPayload = None
    streamingOrderCreated = None
    streamingPaymentProcessed = None
    streamingOrderCreatedProto = None
    streamingPaymentProcessedProto = None
    streamingInvalidPayloadBytes = None
    streamingMissingFieldName = None
    streamingValidationError = None
    streamingOrderCreatedSchemaId = None
    streamingPaymentProcessedSchemaId = None
    streamingOrderCreatedBytes = None
    streamingPaymentProcessedBytes = None
  }
}
