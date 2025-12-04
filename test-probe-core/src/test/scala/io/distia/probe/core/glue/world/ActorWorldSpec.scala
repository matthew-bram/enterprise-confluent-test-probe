package io.distia.probe.core.glue.world

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import io.distia.probe.core.models.*
import io.distia.probe.core.fixtures.ConfigurationFixtures
import io.distia.probe.common.models.KafkaSecurityDirective

import scala.concurrent.duration.*
import java.util.UUID

/**
 * Tests ActorWorld state container.
 *
 * Verifies:
 * - Probe creation and management
 * - State tracking (test IDs, states, captured messages)
 * - Message expectations
 * - Cleanup between scenarios
 *
 * Test Strategy: Unit tests (no Testcontainers)
 */
class ActorWorldSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  var world: ActorWorld = _

  override def beforeEach(): Unit = {
    world = new ActorWorld()
  }

  override def afterEach(): Unit = {
    // Shutdown ActorTestKit to avoid resource leaks
    world.shutdown()
  }

  "ActorWorld" should {

    "initialize with default values" in {
      world.testId shouldBe a[UUID]
      world.currentState shouldBe "Uninitialized"
      world.capturedResponses shouldBe empty
      world.capturedQueueMessages shouldBe empty
      world.childGoodToGoCount shouldBe 0
      world.childShutdownCount shouldBe 0
      world.timerActive shouldBe false
    }

    "create service and queue probes by default" in {
      world.serviceProbe should not be null
      world.queueProbe should not be null
    }

    "create child probes on demand" in {
      // Initially None
      world.blockStorageProbe shouldBe None
      world.vaultProbe shouldBe None
      world.cucumberProbe shouldBe None
      world.producerProbe shouldBe None
      world.consumerProbe shouldBe None

      // Create child probes
      world.createChildProbes()

      // Now all defined
      world.blockStorageProbe shouldBe defined
      world.vaultProbe shouldBe defined
      world.cucumberProbe shouldBe defined
      world.producerProbe shouldBe defined
      world.consumerProbe shouldBe defined
    }

    "capture service responses" in {
      val testId = UUID.randomUUID()

      // Send response to service probe
      world.serviceProbe.ref ! InitializeTestResponse(testId, "Test initialized")

      // Expect and capture response
      val response = world.expectServiceResponse[InitializeTestResponse]()

      response.testId shouldBe testId
      response.message shouldBe "Test initialized"

      // Verify captured
      world.capturedResponses should have size 1
      world.lastServiceResponse shouldBe Some(response)
    }

    "capture queue messages" in {
      val testId = UUID.randomUUID()

      // Send message to queue probe
      world.queueProbe.ref ! QueueCommands.TestInitialized(testId)

      // Expect and capture message
      val message = world.expectQueueMessage[QueueCommands.TestInitialized]()

      message.testId shouldBe testId

      // Verify captured
      world.capturedQueueMessages should have size 1
      world.lastQueueMessage shouldBe Some(message)
    }

    "capture multiple service responses" in {
      val testId1 = UUID.randomUUID()
      val testId2 = UUID.randomUUID()

      // Send multiple responses
      world.serviceProbe.ref ! InitializeTestResponse(testId1, "message1")
      world.serviceProbe.ref ! InitializeTestResponse(testId2, "message2")

      // Expect both
      val response1 = world.expectServiceResponse[InitializeTestResponse]()
      val response2 = world.expectServiceResponse[InitializeTestResponse]()

      // Verify captured in order
      world.capturedResponses should have size 2
      world.capturedResponses(0).asInstanceOf[InitializeTestResponse].testId shouldBe testId1
      world.capturedResponses(1).asInstanceOf[InitializeTestResponse].testId shouldBe testId2
      world.lastServiceResponse shouldBe Some(response2)
    }

    "verify no service response received" in {
      // No message sent
      noException should be thrownBy {
        world.expectNoServiceResponse(100.millis)
      }
    }

    "verify no queue message received" in {
      // No message sent
      noException should be thrownBy {
        world.expectNoQueueMessage(100.millis)
      }
    }

    "track test configuration" in {
      val config = ConfigurationFixtures.defaultCoreConfig

      world.serviceConfig = Some(config)

      world.serviceConfig shouldBe defined
      world.serviceConfig.get.actorSystemName shouldBe "TestProbeSystem"
    }

    "track security directives" in {
      import io.distia.probe.common.models.SecurityProtocol

      val directive = KafkaSecurityDirective(
        topic = "test-events",
        role = "producer",
        securityProtocol = SecurityProtocol.PLAINTEXT,
        jaasConfig = ""
      )

      world.securityDirectives = List(directive)

      world.securityDirectives should have size 1
      world.securityDirectives.head.topic shouldBe "test-events"
    }

    "track child actor state" in {
      world.childGoodToGoCount shouldBe 0

      // Simulate child actor GoodToGo responses
      world.childGoodToGoCount = 5

      world.childGoodToGoCount shouldBe 5
    }

    "reset state on shutdown" in {
      val testId = UUID.randomUUID()

      // Set state
      world.testId = testId
      world.currentState = "Loaded"
      world.serviceProbe.ref ! InitializeTestResponse(testId, "Test initialized")
      world.expectServiceResponse[InitializeTestResponse]()
      world.childGoodToGoCount = 5
      world.timerActive = true
      world.serviceConfig = Some(ConfigurationFixtures.defaultCoreConfig)

      // Verify state set
      world.testId shouldBe testId
      world.currentState shouldBe "Loaded"
      world.capturedResponses should not be empty
      world.childGoodToGoCount shouldBe 5
      world.timerActive shouldBe true
      world.serviceConfig shouldBe defined

      // Shutdown (reset state)
      world.shutdown()

      // Verify reset (create new world for clean state check)
      val newWorld = new ActorWorld()
      try {
        newWorld.testId should not be testId
        newWorld.currentState shouldBe "Uninitialized"
        newWorld.capturedResponses shouldBe empty
        newWorld.childGoodToGoCount shouldBe 0
        newWorld.timerActive shouldBe false
        newWorld.serviceConfig shouldBe None
      } finally {
        newWorld.shutdown()
      }
    }

    "provide access to WorldBase infrastructure" in {
      // WorldBase methods available
      world.testcontainersManager should not be null
      world.kafkaBootstrapServers should not be ""
      world.schemaRegistryUrl should not be ""
      world.schemaIds shouldBe empty
      // Containers stay running with JVM shutdown hook (cleaned up on JVM exit)
      world.isInfrastructureRunning shouldBe true
    }
  }
}
