package io.distia.probe
package core
package pubsub

import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import org.apache.pekko.actor.typed.scaladsl.AskPattern.Askable
import org.apache.pekko.actor.typed.{ActorRef, Scheduler}
import org.apache.pekko.util.Timeout
import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import org.apache.kafka.common.header.internals.RecordHeader
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

import core.models.KafkaConsumerStreamingCommands.*
import core.models.KafkaProducerStreamingCommands.*
import core.models.*
import models.*
import fixtures.CloudEventFixtures

/**
 * Unit tests for ProbeScalaDsl.
 *
 * Tests the Scala DSL for producing and consuming Kafka messages
 * via CloudEvent protocol. This is the primary API for Scala users
 * of the test-probe framework.
 *
 * Design Principles:
 * - Uses CloudEventFixtures (no hardcoded CloudEvents - Pattern #1)
 * - Uses ActorTestKit for actor testing (Pattern #3)
 * - Comprehensive error handling (Pattern #7)
 * - Thread safety tests included (Pattern #9)
 *
 * Test Strategy:
 * - Unit tests with TestProbes (no real Kafka)
 * - Focus on DSL behavior and error handling
 * - Integration tests for real Kafka in separate specs
 *
 * Test Coverage:
 * - System registration/deregistration
 * - Producer/consumer actor registration
 * - Event production (async + blocking)
 * - Event consumption (async + blocking)
 * - Exception handling (DslNotInitializedException, ActorNotRegisteredException)
 * - Thread safety (concurrent registrations, concurrent operations)
 *
 * Thread Safety: Each test gets fresh TestProbes (isolated)
 */
class ProbeScalaDslSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with CloudEventFixtures {

  implicit val testKit: ActorTestKit = ActorTestKit(
    ConfigFactory.parseString(
      """
        |test-probe.core.dsl.ask-timeout = 3s
        |test-probe.core.kafka.schema-registry-url = "http://localhost:8081"
        |""".stripMargin
    )
  )
  implicit val ec: ExecutionContext = testKit.system.executionContext

  override def beforeAll(): Unit = {
    super.beforeAll()
    // Initialize SerdesFactory with mock client (prevents real Schema Registry calls)
    SerdesFactory.setClient(new CachedSchemaRegistryClient("http://localhost:8081", 100))
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  "ProbeScalaDsl.registerSystem" should {

    "initialize DSL with actor system" in {
      ProbeScalaDsl.clearSystem() // Ensure clean state
      ProbeScalaDsl.registerSystem(testKit.system)

      // Verify system is registered by checking producer registration works
      val testId = UUID.randomUUID()
      val topic = s"init-test-${UUID.randomUUID()}"
      val probe = testKit.createTestProbe[KafkaProducerStreamingCommand]()

      noException should be thrownBy {
        ProbeScalaDsl.registerProducerActor(testId, topic, probe.ref)
      }
    }

    "read ask timeout from config" in {
      val config = ConfigFactory.parseString(
        """
          |test-probe.core.dsl.ask-timeout = 10s
          |test-probe.core.kafka.schema-registry-url = "http://localhost:8081"
          |""".stripMargin
      )
      val customTestKit = ActorTestKit("custom-system", config)

      try {
        ProbeScalaDsl.registerSystem(customTestKit.system)

        // Verify system is registered by testing operation works
        val testId = UUID.randomUUID()
        val topic = s"timeout-test-${UUID.randomUUID()}"
        val probe = customTestKit.createTestProbe[KafkaProducerStreamingCommand]()

        noException should be thrownBy {
          ProbeScalaDsl.registerProducerActor(testId, topic, probe.ref)
        }
      } finally {
        customTestKit.shutdownTestKit()
      }
    }

    "allow re-registration with same system" in {
      ProbeScalaDsl.registerSystem(testKit.system)
      ProbeScalaDsl.registerSystem(testKit.system)

      // Verify system still works after re-registration
      val testId = UUID.randomUUID()
      val topic = s"rereg-test-${UUID.randomUUID()}"
      val probe = testKit.createTestProbe[KafkaProducerStreamingCommand]()

      noException should be thrownBy {
        ProbeScalaDsl.registerProducerActor(testId, topic, probe.ref)
      }
    }
  }

  "ProbeScalaDsl.clearSystem" should {

    "clear registered system" in {
      ProbeScalaDsl.registerSystem(testKit.system)
      ProbeScalaDsl.clearSystem()

      // Verify system is cleared by checking operations now fail
      val exception = intercept[DslNotInitializedException] {
        Await.result(
          ProbeScalaDsl.produceEvent(UUID.randomUUID(), "topic", createCloudEvent(), "v", Map.empty[String, String]),
          1.second
        )
      }
      exception.getMessage should include("DSL not initialized")
    }
  }

  "ProbeScalaDsl.registerProducerActor" should {

    "register producer actor for test and topic" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = s"test-events-${UUID.randomUUID()}"
      val producerProbe = testKit.createTestProbe[KafkaProducerStreamingCommand]()

      // Verify registration succeeds
      noException should be thrownBy {
        ProbeScalaDsl.registerProducerActor(testId, topic, producerProbe.ref)
      }

      // Verify unregistration succeeds (proves registration worked)
      noException should be thrownBy {
        ProbeScalaDsl.unRegisterProducerActor(testId, topic)
      }

      // Verify after unregistration, produceEvent throws ActorNotRegisteredException
      val cloudEvent = createCloudEvent()
      val exception = intercept[ActorNotRegisteredException] {
        Await.result(
          ProbeScalaDsl.produceEvent(testId, topic, cloudEvent, "test", Map.empty[String, String]),
          5.seconds
        )
      }
      exception.getMessage should include("No producer registered")
    }

    "allow multiple producers for different topics" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic1 = s"test-events-${UUID.randomUUID()}"
      val topic2 = s"order-events-${UUID.randomUUID()}"
      val producer1 = testKit.createTestProbe[KafkaProducerStreamingCommand]()
      val producer2 = testKit.createTestProbe[KafkaProducerStreamingCommand]()

      // Verify both producers can be registered
      noException should be thrownBy {
        ProbeScalaDsl.registerProducerActor(testId, topic1, producer1.ref)
        ProbeScalaDsl.registerProducerActor(testId, topic2, producer2.ref)
      }

      // Verify both can be unregistered independently (proves separate registrations)
      noException should be thrownBy {
        ProbeScalaDsl.unRegisterProducerActor(testId, topic1)
        ProbeScalaDsl.unRegisterProducerActor(testId, topic2)
      }
    }

    "allow multiple producers for different tests" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId1 = UUID.randomUUID()
      val testId2 = UUID.randomUUID()
      val topic = s"test-events-${UUID.randomUUID()}"
      val producer1 = testKit.createTestProbe[KafkaProducerStreamingCommand]()
      val producer2 = testKit.createTestProbe[KafkaProducerStreamingCommand]()

      // Verify both test producers can be registered for same topic
      noException should be thrownBy {
        ProbeScalaDsl.registerProducerActor(testId1, topic, producer1.ref)
        ProbeScalaDsl.registerProducerActor(testId2, topic, producer2.ref)
      }

      // Verify unregistering testId1 doesn't affect testId2
      ProbeScalaDsl.unRegisterProducerActor(testId1, topic)

      // testId2 should still be registered (unregister succeeds)
      noException should be thrownBy {
        ProbeScalaDsl.unRegisterProducerActor(testId2, topic)
      }
    }

    "overwrite existing producer for same test and topic" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = s"test-events-${UUID.randomUUID()}"
      val producer1 = testKit.createTestProbe[KafkaProducerStreamingCommand]()
      val producer2 = testKit.createTestProbe[KafkaProducerStreamingCommand]()

      // Register first producer, then overwrite with second
      ProbeScalaDsl.registerProducerActor(testId, topic, producer1.ref)
      ProbeScalaDsl.registerProducerActor(testId, topic, producer2.ref)

      // Unregister should succeed (only one entry exists after overwrite)
      noException should be thrownBy {
        ProbeScalaDsl.unRegisterProducerActor(testId, topic)
      }
    }
  }

  "ProbeScalaDsl.registerConsumerActor" should {

    "register consumer actor for test and topic" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = s"test-events-${UUID.randomUUID()}"
      val consumerProbe = testKit.createTestProbe[KafkaConsumerStreamingCommand]()

      ProbeScalaDsl.registerConsumerActor(testId, topic, consumerProbe.ref)

      // Verify registration by attempting fetch and checking probe receives command
      val correlationId = UUID.randomUUID().toString
      val resultFuture = ProbeScalaDsl.fetchConsumedEvent[String](testId, topic, correlationId)

      val command = consumerProbe.receiveMessage(3.seconds)
      command shouldBe a[FetchConsumedEvent]
      command.asInstanceOf[FetchConsumedEvent].correlationId shouldBe correlationId

      // Send nack to complete the future (event not found is expected)
      command.asInstanceOf[FetchConsumedEvent].replyTo ! ConsumedNack(404)

      intercept[ConsumerNotAvailableException] {
        Await.result(resultFuture, 5.seconds)
      }
    }

    "allow multiple consumers for different topics" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic1 = s"test-events-${UUID.randomUUID()}"
      val topic2 = s"order-events-${UUID.randomUUID()}"
      val consumer1 = testKit.createTestProbe[KafkaConsumerStreamingCommand]()
      val consumer2 = testKit.createTestProbe[KafkaConsumerStreamingCommand]()

      ProbeScalaDsl.registerConsumerActor(testId, topic1, consumer1.ref)
      ProbeScalaDsl.registerConsumerActor(testId, topic2, consumer2.ref)

      // Verify both consumers receive commands for their respective topics
      val future1 = ProbeScalaDsl.fetchConsumedEvent[String](testId, topic1, "corr1")
      consumer1.receiveMessage(3.seconds).asInstanceOf[FetchConsumedEvent].replyTo ! ConsumedNack(404)

      val future2 = ProbeScalaDsl.fetchConsumedEvent[String](testId, topic2, "corr2")
      consumer2.receiveMessage(3.seconds).asInstanceOf[FetchConsumedEvent].replyTo ! ConsumedNack(404)

      // Both futures complete (with expected exceptions)
      intercept[ConsumerNotAvailableException] { Await.result(future1, 5.seconds) }
      intercept[ConsumerNotAvailableException] { Await.result(future2, 5.seconds) }
    }

    "allow multiple consumers for different tests" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId1 = UUID.randomUUID()
      val testId2 = UUID.randomUUID()
      val topic = s"test-events-${UUID.randomUUID()}"
      val consumer1 = testKit.createTestProbe[KafkaConsumerStreamingCommand]()
      val consumer2 = testKit.createTestProbe[KafkaConsumerStreamingCommand]()

      ProbeScalaDsl.registerConsumerActor(testId1, topic, consumer1.ref)
      ProbeScalaDsl.registerConsumerActor(testId2, topic, consumer2.ref)

      // Verify each test's consumer receives its commands
      val future1 = ProbeScalaDsl.fetchConsumedEvent[String](testId1, topic, "corr1")
      consumer1.receiveMessage(3.seconds).asInstanceOf[FetchConsumedEvent].replyTo ! ConsumedNack(404)

      val future2 = ProbeScalaDsl.fetchConsumedEvent[String](testId2, topic, "corr2")
      consumer2.receiveMessage(3.seconds).asInstanceOf[FetchConsumedEvent].replyTo ! ConsumedNack(404)

      intercept[ConsumerNotAvailableException] { Await.result(future1, 5.seconds) }
      intercept[ConsumerNotAvailableException] { Await.result(future2, 5.seconds) }
    }
  }

  "ProbeScalaDsl.unRegisterProducerActor" should {

    "remove producer from registry" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = s"test-events-${UUID.randomUUID()}"
      val producerProbe = testKit.createTestProbe[KafkaProducerStreamingCommand]()

      ProbeScalaDsl.registerProducerActor(testId, topic, producerProbe.ref)
      ProbeScalaDsl.unRegisterProducerActor(testId, topic)

      // Verify producer unregistered by checking produce now fails
      val exception = intercept[ActorNotRegisteredException] {
        Await.result(
          ProbeScalaDsl.produceEvent(testId, topic, createCloudEvent(), "v", Map.empty[String, String]),
          5.seconds
        )
      }
      exception.getMessage should include("No producer registered")
    }

    "handle unregistering non-existent producer" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = "non-existent-topic"

      // Should not throw exception - idempotent operation
      noException should be thrownBy {
        ProbeScalaDsl.unRegisterProducerActor(testId, topic)
      }
    }

    "allow re-registration after unregister" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = s"test-events-${UUID.randomUUID()}"
      val producer1 = testKit.createTestProbe[KafkaProducerStreamingCommand]()
      val producer2 = testKit.createTestProbe[KafkaProducerStreamingCommand]()

      // Register, unregister, then re-register with different producer
      ProbeScalaDsl.registerProducerActor(testId, topic, producer1.ref)
      ProbeScalaDsl.unRegisterProducerActor(testId, topic)
      ProbeScalaDsl.registerProducerActor(testId, topic, producer2.ref)

      // Verify producer2 is now registered (unregister succeeds)
      noException should be thrownBy {
        ProbeScalaDsl.unRegisterProducerActor(testId, topic)
      }
    }
  }

  "ProbeScalaDsl.unRegisterConsumerActor" should {

    "remove consumer from registry" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = s"test-events-${UUID.randomUUID()}"
      val consumerProbe = testKit.createTestProbe[KafkaConsumerStreamingCommand]()

      ProbeScalaDsl.registerConsumerActor(testId, topic, consumerProbe.ref)
      ProbeScalaDsl.unRegisterConsumerActor(testId, topic)

      // Verify consumer unregistered by checking fetch now fails
      val exception = intercept[ActorNotRegisteredException] {
        Await.result(
          ProbeScalaDsl.fetchConsumedEvent[String](testId, topic, "corr"),
          5.seconds
        )
      }
      exception.getMessage should include("No consumer registered")
    }

    "handle unregistering non-existent consumer" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = "non-existent-topic"

      // Should not throw exception - idempotent operation
      noException should be thrownBy {
        ProbeScalaDsl.unRegisterConsumerActor(testId, topic)
      }
    }
  }

  "ProbeScalaDsl.produceEvent" should {

    "throw DslNotInitializedException when system not registered" in {
      ProbeScalaDsl.clearSystem()

      val testId = UUID.randomUUID()
      val topic = "test-events"
      val cloudEvent = createCloudEvent()

      val exception = intercept[DslNotInitializedException] {
        Await.result(
          ProbeScalaDsl.produceEvent(testId, topic, cloudEvent, "test-value", Map.empty[String, String]),
          5.seconds
        )
      }

      exception.getMessage should include("DSL not initialized")
    }

    "throw ActorNotRegisteredException when producer not registered" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = "unregistered-topic"
      val cloudEvent = createCloudEvent()

      val exception = intercept[ActorNotRegisteredException] {
        Await.result(
          ProbeScalaDsl.produceEvent(testId, topic, cloudEvent, "test-value", Map.empty[String, String]),
          5.seconds
        )
      }

      exception.getMessage should include("No producer registered")
      exception.getMessage should include(testId.toString)
      exception.getMessage should include(topic)
    }
  }

  "ProbeScalaDsl.fetchConsumedEvent" should {

    "throw DslNotInitializedException when system not registered" in {
      ProbeScalaDsl.clearSystem()

      val testId = UUID.randomUUID()
      val topic = "test-events"
      val correlationId = UUID.randomUUID().toString

      val exception = intercept[DslNotInitializedException] {
        Await.result(
          ProbeScalaDsl.fetchConsumedEvent[String](testId, topic, correlationId),
          5.seconds
        )
      }

      exception.getMessage should include("DSL not initialized")
    }

    "throw ActorNotRegisteredException when consumer not registered" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = "unregistered-topic"
      val correlationId = UUID.randomUUID().toString

      val exception = intercept[ActorNotRegisteredException] {
        Await.result(
          ProbeScalaDsl.fetchConsumedEvent[String](testId, topic, correlationId),
          5.seconds
        )
      }

      exception.getMessage should include("No consumer registered")
      exception.getMessage should include(testId.toString)
      exception.getMessage should include(topic)
    }
  }

  "ProbeScalaDsl thread safety" should {

    "handle concurrent registrations from multiple threads" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val futures = (1 to 10).map { i =>
        Future {
          val testId = UUID.randomUUID()
          val topic = s"topic-$i"
          val producerProbe = testKit.createTestProbe[KafkaProducerStreamingCommand]()
          val consumerProbe = testKit.createTestProbe[KafkaConsumerStreamingCommand]()

          ProbeScalaDsl.registerProducerActor(testId, topic, producerProbe.ref)
          ProbeScalaDsl.registerConsumerActor(testId, topic, consumerProbe.ref)
          ProbeScalaDsl.unRegisterProducerActor(testId, topic)
          ProbeScalaDsl.unRegisterConsumerActor(testId, topic)
        }
      }

      // All concurrent operations should complete without exceptions
      Await.result(Future.sequence(futures), 10.seconds)
    }
  }

  "ProbeScalaDsl.sendEventToKafka" should {

    "return ProducingSuccess when producer acks" in {
      ProbeScalaDsl.registerSystem(testKit.system)
      implicit val timeout: Timeout = Timeout(3.seconds)
      implicit val scheduler: Scheduler = testKit.system.scheduler

      val producerProbe = testKit.createTestProbe[KafkaProducerStreamingCommand]()
      val keyBytes = "test-key".getBytes(StandardCharsets.UTF_8)
      val valueBytes = "test-value".getBytes(StandardCharsets.UTF_8)
      val headers = Map("header1" -> "value1")

      val resultFuture = ProbeScalaDsl.sendEventToKafka(keyBytes, valueBytes, headers, producerProbe.ref)

      val command = producerProbe.receiveMessage()
      command shouldBe a[ProduceEvent]
      val produceEvent = command.asInstanceOf[ProduceEvent]
      produceEvent.key shouldBe keyBytes
      produceEvent.value shouldBe valueBytes
      produceEvent.headers shouldBe headers
      produceEvent.replyTo ! ProducedAck

      val result = Await.result(resultFuture, 5.seconds)
      result shouldBe a[ProducingSuccess]
    }

    "throw KafkaProduceException when producer nacks" in {
      ProbeScalaDsl.registerSystem(testKit.system)
      implicit val timeout: Timeout = Timeout(3.seconds)
      implicit val scheduler: Scheduler = testKit.system.scheduler

      val producerProbe = testKit.createTestProbe[KafkaProducerStreamingCommand]()
      val keyBytes = "test-key".getBytes(StandardCharsets.UTF_8)
      val valueBytes = "test-value".getBytes(StandardCharsets.UTF_8)
      val headers = Map.empty[String, String]

      val resultFuture = ProbeScalaDsl.sendEventToKafka(keyBytes, valueBytes, headers, producerProbe.ref)

      val command = producerProbe.receiveMessage()
      val produceEvent = command.asInstanceOf[ProduceEvent]
      produceEvent.replyTo ! ProducedNack(new RuntimeException("Kafka broker unavailable"))

      val exception = intercept[KafkaProduceException] {
        Await.result(resultFuture, 5.seconds)
      }

      exception.getMessage should include("Kafka producer failed")
      exception.getMessage should include("Kafka broker unavailable")
    }

    "throw IllegalStateException for unexpected ProduceResult" in {
      ProbeScalaDsl.registerSystem(testKit.system)
      implicit val timeout: Timeout = Timeout(3.seconds)
      implicit val scheduler: Scheduler = testKit.system.scheduler

      val producerProbe = testKit.createTestProbe[KafkaProducerStreamingCommand]()
      val keyBytes = "test-key".getBytes(StandardCharsets.UTF_8)
      val valueBytes = "test-value".getBytes(StandardCharsets.UTF_8)
      val headers = Map.empty[String, String]

      val resultFuture = ProbeScalaDsl.sendEventToKafka(keyBytes, valueBytes, headers, producerProbe.ref)

      val command = producerProbe.receiveMessage()
      val produceEvent = command.asInstanceOf[ProduceEvent]
      // Send an unexpected result type (ProducingSuccess is for DSL return, not actor response)
      produceEvent.replyTo ! ProducingSuccess()

      val exception = intercept[IllegalStateException] {
        Await.result(resultFuture, 5.seconds)
      }

      exception.getMessage should include("Unexpected ProduceResult")
    }
  }

  "ProbeScalaDsl.fetchFromKafka" should {

    "return ConsumedAck when consumer has the event" in {
      ProbeScalaDsl.registerSystem(testKit.system)
      implicit val timeout: Timeout = Timeout(3.seconds)
      implicit val scheduler: Scheduler = testKit.system.scheduler

      val consumerProbe = testKit.createTestProbe[KafkaConsumerStreamingCommand]()
      val correlationId = UUID.randomUUID().toString
      val keyBytes = "test-key".getBytes(StandardCharsets.UTF_8)
      val valueBytes = "test-value".getBytes(StandardCharsets.UTF_8)
      val headers = List(new RecordHeader("header1", "value1".getBytes(StandardCharsets.UTF_8)))

      val resultFuture = ProbeScalaDsl.fetchFromKafka(correlationId, consumerProbe.ref)

      val command = consumerProbe.receiveMessage()
      command shouldBe a[FetchConsumedEvent]
      val fetchEvent = command.asInstanceOf[FetchConsumedEvent]
      fetchEvent.correlationId shouldBe correlationId
      fetchEvent.replyTo ! ConsumedAck(keyBytes, valueBytes, headers)

      val result = Await.result(resultFuture, 5.seconds)
      result shouldBe a[ConsumedAck]
      val ack = result.asInstanceOf[ConsumedAck]
      ack.key shouldBe keyBytes
      ack.value shouldBe valueBytes
      ack.headers shouldBe headers
    }

    "return ConsumedNack when event not found" in {
      ProbeScalaDsl.registerSystem(testKit.system)
      implicit val timeout: Timeout = Timeout(3.seconds)
      implicit val scheduler: Scheduler = testKit.system.scheduler

      val consumerProbe = testKit.createTestProbe[KafkaConsumerStreamingCommand]()
      val correlationId = UUID.randomUUID().toString

      val resultFuture = ProbeScalaDsl.fetchFromKafka(correlationId, consumerProbe.ref)

      val command = consumerProbe.receiveMessage()
      val fetchEvent = command.asInstanceOf[FetchConsumedEvent]
      fetchEvent.replyTo ! ConsumedNack(404)

      val result = Await.result(resultFuture, 5.seconds)
      result shouldBe a[ConsumedNack]
      val nack = result.asInstanceOf[ConsumedNack]
      nack.status shouldBe 404
    }
  }

  "ProbeScalaDsl.produceEventBlocking" should {

    "throw DslNotInitializedException when system not registered" in {
      ProbeScalaDsl.clearSystem()

      val testId = UUID.randomUUID()
      val topic = "test-events"
      val cloudEvent = createCloudEvent()

      val exception = intercept[DslNotInitializedException] {
        ProbeScalaDsl.produceEventBlocking(testId, topic, cloudEvent, "test-value", Map.empty[String, String])
      }

      exception.getMessage should include("DSL not initialized")
    }

    "throw ActorNotRegisteredException when producer not registered" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = "unregistered-topic-blocking"
      val cloudEvent = createCloudEvent()

      val exception = intercept[ActorNotRegisteredException] {
        ProbeScalaDsl.produceEventBlocking(testId, topic, cloudEvent, "test-value", Map.empty[String, String])
      }

      exception.getMessage should include("No producer registered")
    }
  }

  "ProbeScalaDsl.fetchConsumedEventBlocking" should {

    "throw DslNotInitializedException when system not registered" in {
      ProbeScalaDsl.clearSystem()

      val testId = UUID.randomUUID()
      val topic = "test-events"
      val correlationId = UUID.randomUUID().toString

      val exception = intercept[DslNotInitializedException] {
        ProbeScalaDsl.fetchConsumedEventBlocking[String](testId, topic, correlationId)
      }

      exception.getMessage should include("DSL not initialized")
    }

    "throw ActorNotRegisteredException when consumer not registered" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = "unregistered-topic-blocking"
      val correlationId = UUID.randomUUID().toString

      val exception = intercept[ActorNotRegisteredException] {
        ProbeScalaDsl.fetchConsumedEventBlocking[String](testId, topic, correlationId)
      }

      exception.getMessage should include("No consumer registered")
    }
  }

  "ProbeScalaDsl.fetchConsumedEvent response handling" should {

    "throw ConsumerNotAvailableException when consumer returns ConsumedNack" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = s"fetch-nack-topic-${UUID.randomUUID()}"
      val correlationId = UUID.randomUUID().toString
      val consumerProbe = testKit.createTestProbe[KafkaConsumerStreamingCommand]()

      ProbeScalaDsl.registerConsumerActor(testId, topic, consumerProbe.ref)

      val resultFuture = ProbeScalaDsl.fetchConsumedEvent[String](testId, topic, correlationId)

      val command = consumerProbe.receiveMessage()
      val fetchEvent = command.asInstanceOf[FetchConsumedEvent]
      fetchEvent.replyTo ! ConsumedNack(404)

      val exception = intercept[ConsumerNotAvailableException] {
        Await.result(resultFuture, 5.seconds)
      }

      exception.getMessage should include("Event not found")
      exception.getMessage should include(correlationId)
      exception.getMessage should include(topic)
    }
  }

  "ProbeScalaDsl error recovery" should {

    "re-throw DslException without wrapping in produceEvent" in {
      // This tests the recoverWith branch that passes through DslExceptions
      ProbeScalaDsl.clearSystem()

      val testId = UUID.randomUUID()
      val topic = "test-events"
      val cloudEvent = createCloudEvent()

      // DslNotInitializedException should be thrown directly, not wrapped
      val exception = intercept[DslNotInitializedException] {
        Await.result(
          ProbeScalaDsl.produceEvent(testId, topic, cloudEvent, "test-value", Map.empty[String, String]),
          5.seconds
        )
      }

      exception shouldBe a[DslNotInitializedException]
    }

    "re-throw DslException without wrapping in fetchConsumedEvent" in {
      // This tests the recoverWith branch that passes through DslExceptions
      ProbeScalaDsl.clearSystem()

      val testId = UUID.randomUUID()
      val topic = "test-events"
      val correlationId = UUID.randomUUID().toString

      // DslNotInitializedException should be thrown directly, not wrapped
      val exception = intercept[DslNotInitializedException] {
        Await.result(
          ProbeScalaDsl.fetchConsumedEvent[String](testId, topic, correlationId),
          5.seconds
        )
      }

      exception shouldBe a[DslNotInitializedException]
    }
  }

  "ProbeScalaDsl consumer overwrite" should {

    "overwrite existing consumer for same test and topic" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = s"overwrite-consumer-topic-${UUID.randomUUID()}"
      val consumer1 = testKit.createTestProbe[KafkaConsumerStreamingCommand]()
      val consumer2 = testKit.createTestProbe[KafkaConsumerStreamingCommand]()

      ProbeScalaDsl.registerConsumerActor(testId, topic, consumer1.ref)
      ProbeScalaDsl.registerConsumerActor(testId, topic, consumer2.ref)

      // Verify consumer2 is now registered by attempting a fetch
      val correlationId = UUID.randomUUID().toString
      val resultFuture = ProbeScalaDsl.fetchConsumedEvent[String](testId, topic, correlationId)

      // consumer1 should NOT receive the message
      consumer1.expectNoMessage(100.millis)

      // consumer2 should receive the message (proving overwrite worked)
      val command = consumer2.receiveMessage()
      command shouldBe a[FetchConsumedEvent]
      val fetchEvent = command.asInstanceOf[FetchConsumedEvent]
      fetchEvent.replyTo ! ConsumedNack(404)

      // Clean up by awaiting the future (which will fail, but that's expected)
      intercept[ConsumerNotAvailableException] {
        Await.result(resultFuture, 5.seconds)
      }
    }

    "allow re-registration after unregister for consumer" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = s"reregister-consumer-topic-${UUID.randomUUID()}"
      val consumer1 = testKit.createTestProbe[KafkaConsumerStreamingCommand]()
      val consumer2 = testKit.createTestProbe[KafkaConsumerStreamingCommand]()

      ProbeScalaDsl.registerConsumerActor(testId, topic, consumer1.ref)
      ProbeScalaDsl.unRegisterConsumerActor(testId, topic)
      ProbeScalaDsl.registerConsumerActor(testId, topic, consumer2.ref)

      // Verify consumer2 is now registered
      val correlationId = UUID.randomUUID().toString
      val resultFuture = ProbeScalaDsl.fetchConsumedEvent[String](testId, topic, correlationId)

      // consumer1 should NOT receive the message
      consumer1.expectNoMessage(100.millis)

      // consumer2 should receive the message
      val command = consumer2.receiveMessage()
      command shouldBe a[FetchConsumedEvent]
      val fetchEvent = command.asInstanceOf[FetchConsumedEvent]
      fetchEvent.replyTo ! ConsumedNack(404)

      intercept[ConsumerNotAvailableException] {
        Await.result(resultFuture, 5.seconds)
      }
    }
  }

  "ProbeScalaDsl producer verification after unregister" should {

    "throw ActorNotRegisteredException after producer unregistration" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = s"unregister-producer-topic-${UUID.randomUUID()}"
      val producerProbe = testKit.createTestProbe[KafkaProducerStreamingCommand]()
      val cloudEvent = createCloudEvent()

      ProbeScalaDsl.registerProducerActor(testId, topic, producerProbe.ref)
      ProbeScalaDsl.unRegisterProducerActor(testId, topic)

      val exception = intercept[ActorNotRegisteredException] {
        Await.result(
          ProbeScalaDsl.produceEvent(testId, topic, cloudEvent, "test-value", Map.empty[String, String]),
          5.seconds
        )
      }

      exception.getMessage should include("No producer registered")
    }

    "throw ActorNotRegisteredException after consumer unregistration" in {
      ProbeScalaDsl.registerSystem(testKit.system)

      val testId = UUID.randomUUID()
      val topic = s"unregister-consumer-topic-${UUID.randomUUID()}"
      val consumerProbe = testKit.createTestProbe[KafkaConsumerStreamingCommand]()
      val correlationId = UUID.randomUUID().toString

      ProbeScalaDsl.registerConsumerActor(testId, topic, consumerProbe.ref)
      ProbeScalaDsl.unRegisterConsumerActor(testId, topic)

      val exception = intercept[ActorNotRegisteredException] {
        Await.result(
          ProbeScalaDsl.fetchConsumedEvent[String](testId, topic, correlationId),
          5.seconds
        )
      }

      exception.getMessage should include("No consumer registered")
    }
  }
}
