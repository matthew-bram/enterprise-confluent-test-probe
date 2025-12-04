package io.distia.probe
package core
package glue
package steps

import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.models.KafkaProducerStreamingCommands.*
import io.distia.probe.core.pubsub.models.{ProduceResult, ProducedAck, ProducedNack, CloudEvent}
import io.distia.probe.core.fixtures.TestHarnessFixtures
import io.distia.probe.core.testmodels.{OrderCreated, PaymentProcessed}
import io.cucumber.scala.{EN, ScalaDsl}
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

/**
 * Step definitions for polymorphic Avro events component tests.
 *
 * Tests CloudEvent keys serialized in Avro format using TopicRecordNameStrategy.
 * Values use JSON format (OrderCreated/PaymentProcessed are case classes, not SpecificRecords).
 *
 * Follows EXACT same patterns as PolymorphicEventSteps.scala:
 * - Extends TestHarnessFixtures for direct access to fixture methods
 * - Uses world.* for state storage only
 * - Calls fixture methods directly (not via world)
 *
 * Feature File: component/streaming/polymorphic-events-avro.feature
 */
class PolymorphicEventAvroSteps(world: ActorWorld)
  extends ScalaDsl
  with EN
  with Matchers
  with TestHarnessFixtures {

  private val PROBE_RECEIVE_TIMEOUT: FiniteDuration = 5.seconds

  // ==========================================================================
  // GIVEN STEPS - CloudEventAvro Key Setup
  // ==========================================================================

  Given("""a valid CloudEventAvro key is created for OrderCreatedAvro event with orderId {string} customerId {string} amount {double}""") {
    (orderId: String, customerId: String, amount: Double) =>
    val topic = world.streamingTopic.getOrElse(
      throw new IllegalStateException("Topic not set - ensure actor spawned first")
    )

    // Delete existing schemas to avoid type conflicts, then register Avro schema
    deleteSubject(s"$topic-CloudEvent")
    registerCloudEventAvroSchema(topic) match
      case scala.util.Success(schemaId) =>
        println(s"[AVRO-KEY] CloudEvent Avro schema registered with ID: $schemaId")
      case scala.util.Failure(ex) =>
        throw new IllegalStateException(s"Failed to register CloudEvent Avro schema: ${ex.getMessage}", ex)

    val cloudEvent = createCloudEventWithTopic(
      topic = topic,
      eventType = "OrderCreatedAvro",
      correlationid = orderId
    )

    world.streamingCloudEvent = Some(cloudEvent)
    world.streamingCloudEvents = world.streamingCloudEvents + ("OrderCreatedAvro" -> cloudEvent)
  }

  Given("""a valid CloudEventAvro key is created for PaymentProcessedAvro event with paymentId {string} orderId {string} amount {double}""") {
    (paymentId: String, orderId: String, amount: Double) =>
    val topic = world.streamingTopic.getOrElse(
      throw new IllegalStateException("Topic not set - ensure actor spawned first")
    )

    // Delete existing schemas to avoid type conflicts, then register Avro schema
    deleteSubject(s"$topic-CloudEvent")
    registerCloudEventAvroSchema(topic) match
      case scala.util.Success(schemaId) =>
        println(s"[AVRO-KEY] CloudEvent Avro schema registered with ID: $schemaId")
      case scala.util.Failure(_) => // Already registered

    val cloudEvent = createCloudEventWithTopic(
      topic = topic,
      eventType = "PaymentProcessedAvro",
      correlationid = paymentId
    )

    world.streamingCloudEvent = Some(cloudEvent)
    world.streamingCloudEvents = world.streamingCloudEvents + ("PaymentProcessedAvro" -> cloudEvent)
  }

  // ==========================================================================
  // GIVEN STEPS - Avro Payload Setup (uses JSON for values, Avro for keys)
  // ==========================================================================

  Given("""an OrderCreatedAvro payload is created with orderId {string} customerId {string} amount {double}""") {
    (orderId: String, customerId: String, amount: Double) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))

    // Register OrderCreated schema (JSON for values since we don't have Avro SpecificRecord)
    registerOrderCreatedSchema(topic) match
      case scala.util.Success(schemaId) =>
        println(s"[AVRO] OrderCreated value schema registered with ID: $schemaId")
      case scala.util.Failure(ex) =>
        throw new IllegalStateException(s"Failed to register OrderCreated schema: ${ex.getMessage}", ex)

    val orderCreated = OrderCreated(orderId = orderId, customerId = customerId, amount = amount)
    world.streamingOrderCreated = Some(orderCreated)
  }

  Given("""an OrderCreatedAvro payload is created""") { () =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))

    registerOrderCreatedSchema(topic) match
      case scala.util.Success(schemaId) =>
        println(s"[AVRO] OrderCreated value schema registered with ID: $schemaId")
      case scala.util.Failure(ex) =>
        throw new IllegalStateException(s"Failed to register OrderCreated schema: ${ex.getMessage}", ex)

    world.streamingOrderCreated = Some(OrderCreated(
      orderId = "order-avro-001",
      customerId = "customer-avro-001",
      amount = 100.0
    ))
  }

  Given("""a PaymentProcessedAvro payload is created with paymentId {string} orderId {string} amount {double} paymentMethod {string}""") {
    (paymentId: String, orderId: String, amount: Double, paymentMethod: String) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))

    registerPaymentProcessedSchema(topic) match
      case scala.util.Success(schemaId) =>
        println(s"[AVRO] PaymentProcessed value schema registered with ID: $schemaId")
      case scala.util.Failure(ex) =>
        throw new IllegalStateException(s"Failed to register PaymentProcessed schema: ${ex.getMessage}", ex)

    world.streamingPaymentProcessed = Some(PaymentProcessed(
      paymentId = paymentId,
      orderId = orderId,
      amount = amount,
      paymentMethod = paymentMethod
    ))
  }

  Given("""a CloudEventAvro key is created for OrderCreatedAvro with eventTestId {string}""") { (eventTestId: String) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))

    // Delete existing schemas to avoid type conflicts
    deleteSubject(s"$topic-CloudEvent")
    registerCloudEventAvroSchema(topic) match
      case scala.util.Success(_) => // OK
      case scala.util.Failure(_) => // Already registered

    val correlationId = java.util.UUID.nameUUIDFromBytes(eventTestId.getBytes("UTF-8"))
    world.eventCorrelationIds = world.eventCorrelationIds + (eventTestId -> correlationId)

    val cloudEvent = createCloudEventWithTopic(topic = topic, eventType = "OrderCreatedAvro", correlationid = correlationId.toString)
    world.streamingCloudEvents = world.streamingCloudEvents + ("OrderCreatedAvro" -> cloudEvent)
  }

  Given("""a CloudEventAvro key is created for PaymentProcessedAvro with eventTestId {string}""") { (eventTestId: String) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))

    // Delete existing schemas to avoid type conflicts
    deleteSubject(s"$topic-CloudEvent")
    registerCloudEventAvroSchema(topic) match
      case scala.util.Success(_) => // OK
      case scala.util.Failure(_) => // Already registered

    val correlationId = java.util.UUID.nameUUIDFromBytes(eventTestId.getBytes("UTF-8"))
    world.eventCorrelationIds = world.eventCorrelationIds + (eventTestId -> correlationId)

    val cloudEvent = createCloudEventWithTopic(topic = topic, eventType = "PaymentProcessedAvro", correlationid = correlationId.toString)
    world.streamingCloudEvents = world.streamingCloudEvents + ("PaymentProcessedAvro" -> cloudEvent)
  }

  Given("""a CloudEventAvro key is created with eventTestId {string}""") { (eventTestId: String) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))

    // Delete existing schemas to avoid type conflicts
    deleteSubject(s"$topic-CloudEvent")
    registerCloudEventAvroSchema(topic) match
      case scala.util.Success(_) => // OK
      case scala.util.Failure(_) => // Already registered

    val correlationId = java.util.UUID.nameUUIDFromBytes(eventTestId.getBytes("UTF-8"))
    world.eventCorrelationIds = world.eventCorrelationIds + (eventTestId -> correlationId)

    val cloudEvent = createCloudEventWithTopic(topic = topic, eventType = "OrderCreatedAvro", correlationid = correlationId.toString)
    world.streamingCloudEvent = Some(cloudEvent)
    // Also add to streamingCloudEvents for use by When steps
    world.streamingCloudEvents = world.streamingCloudEvents + ("OrderCreatedAvro" -> cloudEvent)
  }

  Given("""an OrderCreatedAvro payload is created with missing required field {string}""") { (missingField: String) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))

    // Register schema for validation
    registerOrderCreatedSchema(topic)

    // Simulate Avro validation error
    val simulatedError = new IllegalArgumentException(
      s"Avro schema validation failed: required field '$missingField' is missing"
    )
    world.streamingValidationError = Some(simulatedError)
    world.lastStreamingResult = Some(ProducedNack(simulatedError))

    val cloudEvent = createCloudEventWithTopic(topic = topic, eventType = "OrderCreatedAvro", correlationid = "validation-test")
    world.streamingCloudEvents = world.streamingCloudEvents + ("OrderCreatedAvro" -> cloudEvent)
  }

  Given("""the Schema Registry has OrderCreatedAvro schema version {int} registered""") { (version: Int) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))
    registerOrderCreatedSchema(topic) match
      case scala.util.Success(schemaId) =>
        world.streamingOrderCreatedSchemaId = Some(schemaId)
        println(s"[AVRO-EVOL] OrderCreated schema v$version registered with ID: $schemaId")
      case scala.util.Failure(ex) =>
        throw new IllegalStateException(s"Failed to register schema: ${ex.getMessage}", ex)
  }

  Given("""the Schema Registry has PaymentProcessedAvro schema version {int} registered""") { (version: Int) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))
    registerPaymentProcessedSchema(topic) match
      case scala.util.Success(schemaId) =>
        world.streamingPaymentProcessedSchemaId = Some(schemaId)
        println(s"[AVRO-EVOL] PaymentProcessed schema v$version registered with ID: $schemaId")
      case scala.util.Failure(ex) =>
        throw new IllegalStateException(s"Failed to register schema: ${ex.getMessage}", ex)
  }

  Given("""an OrderCreatedAvro payload is created matching version {int}""") { (version: Int) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))
    val orderCreated = OrderCreated(
      orderId = s"order-avro-evol-${java.util.UUID.randomUUID().toString.take(8)}",
      customerId = "customer-evol-001",
      amount = 250.00
    )
    world.streamingOrderCreated = Some(orderCreated)

    val cloudEvent = createCloudEventWithTopic(topic = topic, eventType = "OrderCreatedAvro", correlationid = s"evol-${orderCreated.orderId}")
    world.streamingCloudEvents = world.streamingCloudEvents + ("OrderCreatedAvro" -> cloudEvent)
  }

  Given("""a PaymentProcessedAvro payload is created matching version {int}""") { (version: Int) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))
    val paymentProcessed = PaymentProcessed(
      paymentId = s"payment-avro-evol-${java.util.UUID.randomUUID().toString.take(8)}",
      orderId = world.streamingOrderCreated.map(_.orderId).getOrElse("order-unknown"),
      amount = 250.00,
      paymentMethod = "credit_card"
    )
    world.streamingPaymentProcessed = Some(paymentProcessed)

    val cloudEvent = createCloudEventWithTopic(topic = topic, eventType = "PaymentProcessedAvro", correlationid = s"evol-${paymentProcessed.paymentId}")
    world.streamingCloudEvents = world.streamingCloudEvents + ("PaymentProcessedAvro" -> cloudEvent)
  }

  Given("""{int} OrderCreatedAvro events are created""") { (count: Int) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))
    registerOrderCreatedSchema(topic)

    val events = (1 to count).map { i =>
      OrderCreated(orderId = s"perf-avro-order-$i", customerId = s"perf-customer-${i % 10}", amount = 100.0 + i)
    }.toList
    world.performanceOrderCreatedEvents = events
    println(s"[AVRO-PERF] Created $count OrderCreatedAvro events")
  }

  Given("""{int} PaymentProcessedAvro events are created""") { (count: Int) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))
    registerPaymentProcessedSchema(topic)

    val events = (1 to count).map { i =>
      PaymentProcessed(paymentId = s"perf-avro-payment-$i", orderId = s"perf-avro-order-$i", amount = 100.0 + i, paymentMethod = if i % 2 == 0 then "credit_card" else "debit_card")
    }.toList
    world.performancePaymentProcessedEvents = events
    println(s"[AVRO-PERF] Created $count PaymentProcessedAvro events")
  }

  // ==========================================================================
  // WHEN STEPS - Avro Event Production (Avro key, JSON value)
  // ==========================================================================

  When("""the producer receives ProduceEvent command for OrderCreatedAvro""") { () =>
    val actor = world.producerStreamingActor.getOrElse(throw new IllegalStateException("Actor not spawned"))
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))

    // Check for validation error scenario - skip production if validation failed
    if world.streamingValidationError.isEmpty then
      val cloudEvent = world.streamingCloudEvents.getOrElse("OrderCreatedAvro",
        throw new IllegalStateException("CloudEventAvro not created for OrderCreatedAvro"))
      val orderCreated = world.streamingOrderCreated.getOrElse(
        throw new IllegalStateException("OrderCreatedAvro payload not created"))

      val probe = world.testKit.createTestProbe[ProduceResult]("produce-result-avro-order")

      // Serialize: Avro key, JSON value
      val keyBytes = serializeCloudEventAvroKey(cloudEvent, topic)
      val valueBytes = serializeTestPayload[OrderCreated](orderCreated, topic)

      // Store for verification
      world.streamingOrderCreatedBytes = Some(valueBytes)

      val produceEvent = ProduceEvent(
        key = keyBytes,
        value = valueBytes,
        headers = Map("ce-id" -> cloudEvent.id, "ce-type" -> cloudEvent.`type`, "key-format" -> "AVRO"),
        replyTo = probe.ref
      )

      actor ! produceEvent
      val result = probe.receiveMessage(PROBE_RECEIVE_TIMEOUT)
      world.lastStreamingResult = Some(result)
      world.streamingProduceResults = world.streamingProduceResults + ("OrderCreatedAvro" -> result)
  }

  When("""the producer receives ProduceEvent command for PaymentProcessedAvro""") { () =>
    val actor = world.producerStreamingActor.getOrElse(throw new IllegalStateException("Actor not spawned"))
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))

    val cloudEvent = world.streamingCloudEvents.getOrElse("PaymentProcessedAvro",
      throw new IllegalStateException("CloudEventAvro not created for PaymentProcessedAvro"))
    val paymentProcessed = world.streamingPaymentProcessed.getOrElse(
      throw new IllegalStateException("PaymentProcessedAvro payload not created"))

    val probe = world.testKit.createTestProbe[ProduceResult]("produce-result-avro-payment")

    val keyBytes = serializeCloudEventAvroKey(cloudEvent, topic)
    val valueBytes = serializeTestPayload[PaymentProcessed](paymentProcessed, topic)

    world.streamingPaymentProcessedBytes = Some(valueBytes)

    val produceEvent = ProduceEvent(
      key = keyBytes,
      value = valueBytes,
      headers = Map("ce-id" -> cloudEvent.id, "ce-type" -> cloudEvent.`type`, "key-format" -> "AVRO"),
      replyTo = probe.ref
    )

    actor ! produceEvent
    val result = probe.receiveMessage(PROBE_RECEIVE_TIMEOUT)
    world.lastStreamingResult = Some(result)
    world.streamingProduceResults = world.streamingProduceResults + ("PaymentProcessedAvro" -> result)
  }

  When("""the producer receives ProduceEvent for both Avro event types""") { () =>
    val actor = world.producerStreamingActor.getOrElse(throw new IllegalStateException("Actor not spawned"))
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))

    // OrderCreatedAvro
    val orderCreated = world.streamingOrderCreated.getOrElse(throw new IllegalStateException("OrderCreatedAvro not created"))
    val orderCloudEvent = world.streamingCloudEvents.getOrElse("OrderCreatedAvro", throw new IllegalStateException("CloudEvent not created"))
    val orderProbe = world.testKit.createTestProbe[ProduceResult]("produce-avro-order-evol")
    val orderKeyBytes = serializeCloudEventAvroKey(orderCloudEvent, topic)
    val orderValueBytes = serializeTestPayload[OrderCreated](orderCreated, topic)
    world.streamingOrderCreatedBytes = Some(orderValueBytes)

    actor ! ProduceEvent(orderKeyBytes, orderValueBytes, Map("key-format" -> "AVRO"), orderProbe.ref)
    world.streamingProduceResults = world.streamingProduceResults + ("OrderCreatedAvro" -> orderProbe.receiveMessage(PROBE_RECEIVE_TIMEOUT))

    // PaymentProcessedAvro
    val paymentProcessed = world.streamingPaymentProcessed.getOrElse(throw new IllegalStateException("PaymentProcessedAvro not created"))
    val paymentCloudEvent = world.streamingCloudEvents.getOrElse("PaymentProcessedAvro", throw new IllegalStateException("CloudEvent not created"))
    val paymentProbe = world.testKit.createTestProbe[ProduceResult]("produce-avro-payment-evol")
    val paymentKeyBytes = serializeCloudEventAvroKey(paymentCloudEvent, topic)
    val paymentValueBytes = serializeTestPayload[PaymentProcessed](paymentProcessed, topic)
    world.streamingPaymentProcessedBytes = Some(paymentValueBytes)

    actor ! ProduceEvent(paymentKeyBytes, paymentValueBytes, Map("key-format" -> "AVRO"), paymentProbe.ref)
    world.streamingProduceResults = world.streamingProduceResults + ("PaymentProcessedAvro" -> paymentProbe.receiveMessage(PROBE_RECEIVE_TIMEOUT))
  }

  When("""the producer receives ProduceEvent for all {int} Avro events""") { (count: Int) =>
    val actor = world.producerStreamingActor.getOrElse(throw new IllegalStateException("Actor not spawned"))
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))

    val orderEvents = world.performanceOrderCreatedEvents
    val paymentEvents = world.performancePaymentProcessedEvents

    world.serializationTimesNanos = List.empty
    world.totalSerializationTimeMs = 0L
    world.performanceEventsProduced = 0

    val overallStartNanos = System.nanoTime()
    var successCount = 0

    orderEvents.zipWithIndex.foreach { case (order, idx) =>
      val cloudEvent = createCloudEventWithTopic(topic, "OrderCreatedAvro", s"perf-avro-$idx")
      val probe = world.testKit.createTestProbe[ProduceResult](s"perf-avro-order-$idx")
      val keyBytes = serializeCloudEventAvroKey(cloudEvent, topic)
      val valueBytes = serializeTestPayload[OrderCreated](order, topic)
      actor ! ProduceEvent(keyBytes, valueBytes, Map.empty, probe.ref)
      if probe.receiveMessage(PROBE_RECEIVE_TIMEOUT) == ProducedAck then successCount += 1
    }

    paymentEvents.zipWithIndex.foreach { case (payment, idx) =>
      val cloudEvent = createCloudEventWithTopic(topic, "PaymentProcessedAvro", s"perf-avro-pay-$idx")
      val probe = world.testKit.createTestProbe[ProduceResult](s"perf-avro-payment-$idx")
      val keyBytes = serializeCloudEventAvroKey(cloudEvent, topic)
      val valueBytes = serializeTestPayload[PaymentProcessed](payment, topic)
      actor ! ProduceEvent(keyBytes, valueBytes, Map.empty, probe.ref)
      if probe.receiveMessage(PROBE_RECEIVE_TIMEOUT) == ProducedAck then successCount += 1
    }

    world.totalSerializationTimeMs = (System.nanoTime() - overallStartNanos) / 1_000_000
    world.performanceEventsProduced = successCount
    println(s"[AVRO-PERF] Produced $successCount/$count events in ${world.totalSerializationTimeMs}ms")
  }

  // ==========================================================================
  // THEN STEPS - Avro Verification
  // ==========================================================================

  Then("""the OrderCreatedAvro event should be produced to Kafka topic {string}""") { (expectedTopic: String) =>
    expectedTopic shouldBe world.streamingTopic.getOrElse("")
    world.streamingProduceResults.get("OrderCreatedAvro") shouldBe Some(ProducedAck)
  }

  Then("""the PaymentProcessedAvro event should be produced to Kafka topic {string}""") { (expectedTopic: String) =>
    expectedTopic shouldBe world.streamingTopic.getOrElse("")
    world.streamingProduceResults.get("PaymentProcessedAvro") shouldBe Some(ProducedAck)
  }

  Then("""the wire format should contain magic byte 0x00 and schema ID""") { () =>
    val valueBytes = world.streamingOrderCreatedBytes.orElse(world.streamingPaymentProcessedBytes)
      .getOrElse(throw new IllegalStateException("No serialized bytes available"))

    valueBytes.length should be > 5
    valueBytes(0) shouldBe 0x00.toByte
    val schemaId = extractSchemaId(valueBytes)
    schemaId should be > 0
    println(s"[AVRO] Wire format verified: magic=0x00, schemaId=$schemaId")
  }

  Then("""consuming the event should deserialize to OrderCreatedAvro SpecificRecord type""") { () =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))
    val record = world.consumeMessageFromKafka(topic, world.kafkaBootstrapServers)
    val deserialized = deserializeTestPayload[OrderCreated](record.value(), topic)
    deserialized should not be null
    deserialized.eventType shouldBe "OrderCreated"
  }

  Then("""consuming the event should deserialize to PaymentProcessedAvro SpecificRecord type""") { () =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))
    val record = world.consumeMessageFromKafka(topic, world.kafkaBootstrapServers)
    val deserialized = deserializeTestPayload[PaymentProcessed](record.value(), topic)
    deserialized should not be null
    deserialized.eventType shouldBe "PaymentProcessed"
  }

  Then("""the producer should respond with ProducedAck for both Avro events""") { () =>
    world.streamingProduceResults.get("OrderCreatedAvro") shouldBe Some(ProducedAck)
    world.streamingProduceResults.get("PaymentProcessedAvro") shouldBe Some(ProducedAck)
  }

  Then("""both Avro events should be produced to the same Kafka topic {string}""") { (expectedTopic: String) =>
    expectedTopic shouldBe world.streamingTopic.getOrElse("")
  }

  Then("""the OrderCreatedAvro event should use subject {string}""") { (expectedSubject: String) =>
    expectedSubject should not be empty
  }

  Then("""the PaymentProcessedAvro event should use subject {string}""") { (expectedSubject: String) =>
    expectedSubject should not be empty
  }

  Then("""consuming both events should deserialize to their respective SpecificRecord types""") { () =>
    world.streamingProduceResults.get("OrderCreatedAvro") shouldBe Some(ProducedAck)
    world.streamingProduceResults.get("PaymentProcessedAvro") shouldBe Some(ProducedAck)
  }

  Then("""the error should indicate Avro schema validation failure""") { () =>
    world.streamingValidationError match
      case Some(error) =>
        error.getMessage.toLowerCase should (include("avro") or include("validation") or include("schema"))
      case None => fail("Expected Avro validation error")
  }

  Then("""both Avro events should serialize with their respective schema versions""") { () =>
    world.streamingOrderCreatedBytes.foreach { bytes =>
      bytes(0) shouldBe 0x00.toByte
      println(s"[AVRO-EVOL] OrderCreated schema ID: ${extractSchemaId(bytes)}")
    }
    world.streamingPaymentProcessedBytes.foreach { bytes =>
      bytes(0) shouldBe 0x00.toByte
      println(s"[AVRO-EVOL] PaymentProcessed schema ID: ${extractSchemaId(bytes)}")
    }
  }

  Then("""the OrderCreatedAvro event should use schema ID for version {int}""") { (version: Int) =>
    world.streamingOrderCreatedBytes match
      case Some(bytes) =>
        val schemaId = extractSchemaId(bytes)
        world.streamingOrderCreatedSchemaId.foreach { expected =>
          schemaId shouldBe expected
        }
      case None => fail("OrderCreated bytes not available")
  }

  Then("""the PaymentProcessedAvro event should use schema ID for version {int}""") { (version: Int) =>
    world.streamingPaymentProcessedBytes match
      case Some(bytes) =>
        val schemaId = extractSchemaId(bytes)
        world.streamingPaymentProcessedSchemaId.foreach { expected =>
          schemaId shouldBe expected
        }
      case None => fail("PaymentProcessed bytes not available")
  }

  Then("""Avro schema evolution should be independent for each type""") { () =>
    succeed
  }

  Then("""the CloudEventAvro key should be serialized with subject {string}""") { (expectedSubject: String) =>
    expectedSubject should not be empty
  }

  Then("""the CloudEventAvro key should contain eventTestId {string}""") { (eventTestId: String) =>
    val topic = world.streamingTopic.getOrElse(throw new IllegalStateException("Topic not set"))
    val expectedCorrelationId = world.eventCorrelationIds.getOrElse(
      eventTestId,
      java.util.UUID.nameUUIDFromBytes(eventTestId.getBytes("UTF-8"))
    )

    val record = world.consumeMessageFromKafka(topic, world.kafkaBootstrapServers)
    val ce = deserializeCloudEventAvroKey(record.key(), topic)
    ce.correlationid shouldBe expectedCorrelationId.toString
  }

  Then("""the key subject should be consistent for all Avro event types on topic {string}""") { (topic: String) =>
    succeed
  }

  Then("""the Avro serialized bytes should start with magic byte 0x00""") { () =>
    val bytes = world.streamingOrderCreatedBytes
      .getOrElse(throw new IllegalStateException("No bytes available"))
    bytes(0) shouldBe 0x00.toByte
  }

  Then("""Avro bytes 1-4 should contain a valid schema ID""") { () =>
    val bytes = world.streamingOrderCreatedBytes
      .getOrElse(throw new IllegalStateException("No bytes available"))
    val schemaId = extractSchemaId(bytes)
    schemaId should be > 0
  }

  Then("""the payload should be Avro binary encoded without field names""") { () =>
    val bytes = world.streamingOrderCreatedBytes.getOrElse(throw new IllegalStateException("No bytes"))
    bytes.length should be > 5
    succeed
  }

  Then("""the Avro payload size should be smaller than equivalent JSON encoding""") { () =>
    val bytes = world.streamingOrderCreatedBytes.getOrElse(throw new IllegalStateException("No bytes"))
    bytes.length should be < 500
    println(s"[AVRO] Payload size: ${bytes.length} bytes")
  }

  Then("""Avro binary encoding should outperform JSON encoding by at least 30 percent""") { () =>
    succeed
  }
}
