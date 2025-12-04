package io.distia.probe.core.glue.world

import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import io.distia.probe.core.models.*
import io.distia.probe.core.config.CoreConfig
import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}
import io.distia.probe.core.glue.world.fixtures.actors._

import scala.concurrent.duration.FiniteDuration
import java.util.UUID

/**
 * Actor testing world object for Cucumber BDD scenarios.
 *
 * This is a STATE CONTAINER ONLY - no infrastructure implementation.
 *
 * Responsibilities:
 * - Test probe management (create and access probes)
 * - Test state tracking (test IDs, states, captured messages)
 * - Test data storage (directives, config, test fixtures)
 *
 * NOT Responsible For:
 * - Testcontainers (delegated to WorldBase.testcontainersManager)
 * - Schema Registration (delegated to WorldBase.schemaRegistrationService)
 * - Actor factories (delegated to ActorSpawningFixtures)
 *
 * Design: Slim state container (150 lines vs old 870 lines)
 *
 * Usage:
 * {{{
 *   // In Cucumber step definition:
 *   class TestExecutionSteps(world: ActorWorld) {
 *     Given("infrastructure is started") {
 *       world.startInfrastructure()  // From WorldBase
 *     }
 *
 *     When("I send InitializeTest") {
 *       world.serviceProbe ! InitializeTest(world.testId)
 *     }
 *
 *     Then("I should receive InitializeTestResponse") {
 *       val response = world.expectServiceResponse[InitializeTestResponse]()
 *       // ...
 *     }
 *   }
 * }}}
 *
 * Thread Safety: NOT thread-safe. Cucumber runs scenarios sequentially.
 *
 * Test Strategy: Tested via ActorWorldSpec (12 tests)
 */
class ActorWorld
  extends WorldBase
  with QueueActorFixtures
  with TestExecutionActorFixtures
  with BlockStorageActorFixture
  with VaultActorFixture
  with CucumberExecutionActorFixture
  with KafkaProducerActorFixture
  with KafkaProducerStreamingActorFixture
  with KafkaConsumerActorFixture
  with KafkaConsumerStreamingActorFixture {

  // ==========================================================================
  // Test Probes (for message expectations)
  // ==========================================================================

  /**
   * Service probe for TestExecutionActor responses.
   *
   * Used to receive:
   * - InitializeTestResponse
   * - StartTestResponse
   * - TestStatusResponse
   */
  lazy val serviceProbe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]("service-probe")

  /**
   * Queue probe for QueueActor messages.
   *
   * Used to receive:
   * - TestInitialized
   * - TestCompleted
   * - TestFailed
   */
  lazy val queueProbe: TestProbe[QueueCommands.QueueCommand] = testKit.createTestProbe[QueueCommands.QueueCommand]("queue-probe")

  /**
   * TestExecutionActor parent probe (for child actor tests).
   *
   * Used by child actor step definitions (BlockStorageActorSteps, VaultActorSteps, etc.)
   * to mock the parent TestExecutionActor that spawns child actors.
   */
  var teaParentProbe: Option[TestProbe[TestExecutionCommands.TestExecutionCommand]] = None

  /**
   * Child actor probes (created on demand).
   *
   * Used for testing child actor message flows:
   * - BlockStorageActor
   * - VaultActor
   * - CucumberExecutionActor
   * - KafkaProducerActor
   * - KafkaConsumerActor
   */
  var blockStorageProbe: Option[TestProbe[BlockStorageCommands.BlockStorageCommand]] = None
  var vaultProbe: Option[TestProbe[VaultCommands.VaultCommand]] = None
  var cucumberProbe: Option[TestProbe[CucumberExecutionCommands.CucumberExecutionCommand]] = None
  var producerProbe: Option[TestProbe[KafkaProducerCommands.KafkaProducerCommand]] = None
  var consumerProbe: Option[TestProbe[KafkaConsumerCommands.KafkaConsumerCommand]] = None

  // ==========================================================================
  // Actors Under Test
  // ==========================================================================

  /**
   * TestExecutionActor under test.
   *
   * Spawned by step definitions using ActorSpawningFixtures.
   */
  var testExecutionActor: Option[ActorRef[TestExecutionCommands.TestExecutionCommand]] = None

  /**
   * GuardianActor under test (for GuardianActor BDD scenarios).
   */
  var guardianActor: Option[ActorRef[GuardianCommands.GuardianCommand]] = None

  /**
   * QueueActor under test (for QueueActor BDD scenarios).
   */
  var queueActor: Option[ActorRef[QueueCommands.QueueCommand]] = None

  // ==========================================================================
  // State Tracking
  // ==========================================================================

  /**
   * Current test ID.
   *
   * Set by step definitions when creating tests.
   */
  var testId: UUID = UUID.randomUUID()

  /**
   * Used to change behaviors in common steps
   */
  var testIdString: Option[String] = None

  /**
   * Current FSM state (for verification).
   *
   * Example: "Uninitialized", "Setup", "Loading", "Loaded"
   */
  var currentState: String = "Uninitialized"

  var initialized: Boolean = false

  /**
   * Captured service responses (for multi-step verification).
   *
   * Populated by expectServiceResponse[T]().
   */
  var capturedResponses: List[ServiceResponse] = List.empty

  /**
   * Captured queue messages (for multi-step verification).
   *
   * Populated by expectQueueMessage[T]().
   */
  var capturedQueueMessages: List[QueueCommands.QueueCommand] = List.empty

  /**
   * Child actor "GoodToGo" count (for Loaded state verification).
   *
   * Increments when child actors respond with GoodToGo.
   */
  var childGoodToGoCount: Int = 0

  /**
   * Child actor shutdown count (for Completed state verification).
   *
   * Increments when child actors confirm shutdown.
   */
  var childShutdownCount: Int = 0

  /**
   * Timer active flag (for timeout testing).
   *
   * Set when timer message sent, cleared on state transition.
   */
  var timerActive: Boolean = false

  // ==========================================================================
  // Test Data (Directives and Configuration)
  // ==========================================================================

  /**
   * Service configuration (CoreConfig).
   *
   * Created by step definitions using ConfigurationFixtures.
   */
  var serviceConfig: Option[CoreConfig] = None

  /**
   * Block storage directive.
   *
   * Created by step definitions using BlockStorageDirectiveBuilder.
   */
  var blockStorageDirective: Option[BlockStorageDirective] = None

  /**
   * Kafka security directives.
   *
   * Created by step definitions using SecurityDirectiveBuilder.
   */
  var securityDirectives: List[KafkaSecurityDirective] = List.empty

  /**
   * S3 bucket name (for BlockStorageActor testing).
   */
  var bucket: Option[String] = None

  /**
   * Test type (e.g., "component", "integration").
   */
  var testType: String = ""

  // ==========================================================================
  // Probe Creation
  // ==========================================================================

  /**
   * Create child actor probes on demand.
   *
   * Called by step definitions when testing child actor interactions.
   */
  def createChildProbes(): Unit = {
    blockStorageProbe = Some(testKit.createTestProbe[BlockStorageCommands.BlockStorageCommand]("block-storage-probe"))
    vaultProbe = Some(testKit.createTestProbe[VaultCommands.VaultCommand]("vault-probe"))
    cucumberProbe = Some(testKit.createTestProbe[CucumberExecutionCommands.CucumberExecutionCommand]("cucumber-probe"))
    producerProbe = Some(testKit.createTestProbe[KafkaProducerCommands.KafkaProducerCommand]("producer-probe"))
    consumerProbe = Some(testKit.createTestProbe[KafkaConsumerCommands.KafkaConsumerCommand]("consumer-probe"))
  }

  // ==========================================================================
  // Message Expectations (Wrappers for probe.receiveMessage)
  // ==========================================================================

  /**
   * Expect service response from TestExecutionActor.
   *
   * Captures response for later verification.
   *
   * @tparam T Response type (InitializeTestResponse, etc.)
   * @param timeout Max wait time (default: 3 seconds)
   * @return Received response
   */
  def expectServiceResponse[T <: ServiceResponse](timeout: FiniteDuration = defaultTimeout): T = {
    val response: ServiceResponse = serviceProbe.receiveMessage(timeout)
    capturedResponses = capturedResponses :+ response
    response.asInstanceOf[T]
  }

  /**
   * Expect queue message from QueueActor.
   *
   * Captures message for later verification.
   *
   * @tparam T Message type (TestInitialized, etc.)
   * @param timeout Max wait time (default: 3 seconds)
   * @return Received message
   */
  def expectQueueMessage[T <: QueueCommands.QueueCommand](timeout: FiniteDuration = defaultTimeout): T = {
    val message: QueueCommands.QueueCommand = queueProbe.receiveMessage(timeout)
    capturedQueueMessages = capturedQueueMessages :+ message
    message.asInstanceOf[T]
  }

  /**
   * Verify no service response received.
   *
   * @param duration Wait duration (default: 500ms)
   */
  def expectNoServiceResponse(duration: FiniteDuration = defaultTimeout): Unit = {
    serviceProbe.expectNoMessage(duration)
  }

  /**
   * Verify no queue message received.
   *
   * @param duration Wait duration (default: 500ms)
   */
  def expectNoQueueMessage(duration: FiniteDuration = defaultTimeout): Unit = {
    queueProbe.expectNoMessage(duration)
  }

  /**
   * Get last captured service response.
   *
   * @return Most recent service response, or None
   */
  def lastServiceResponse: Option[ServiceResponse] =
    capturedResponses.lastOption

  /**
   * Get last captured queue message.
   *
   * @return Most recent queue message, or None
   */
  def lastQueueMessage: Option[QueueCommands.QueueCommand] =
    capturedQueueMessages.lastOption

  /**
   * Expect actor termination.
   *
   * Uses TestKit's expectTerminated to wait for actor shutdown.
   *
   * @param timeout Max wait time (default: 5 seconds)
   * @throws AssertionError if actor doesn't terminate within timeout
   */
  def expectActorTerminated(timeout: FiniteDuration = defaultTimeout): Unit = {
    testExecutionActor match {
      case Some(actor) =>
        val watcher = testKit.createTestProbe[Nothing]("termination-watcher")
        watcher.expectTerminated(actor, timeout)
        testExecutionActor = None  // Clear reference after termination

      case None =>
        throw new IllegalStateException("No TestExecutionActor to watch for termination")
    }
  }

  // ==========================================================================
  // Cleanup
  // ==========================================================================

  // ==========================================================================
  // Shutdown and Cleanup
  // ==========================================================================

  /**
   * Reset world state between scenarios.
   *
   * Called automatically by Cucumber after each scenario.
   * Also calls WorldBase.shutdown() for infrastructure cleanup.
   */
  override def shutdown(): Unit = {
    // Clear probes (don't recreate - ActorTestKit will be shutdown)
    teaParentProbe = None
    blockStorageProbe = None
    vaultProbe = None
    cucumberProbe = None
    producerProbe = None
    consumerProbe = None

    // Clear actors
    testExecutionActor = None
    guardianActor = None
    queueActor = None

    // Reset state
    testId = UUID.randomUUID()
    testIdString = None
    currentState = "Uninitialized"
    capturedResponses = List.empty
    capturedQueueMessages = List.empty
    childGoodToGoCount = 0
    childShutdownCount = 0
    timerActive = false

    // Clear test data
    serviceConfig = None
    blockStorageDirective = None
    securityDirectives = List.empty
    bucket = None
    testType = ""

    // Reset actor fixture state
    resetStreamingState()
    resetConsumerStreamingState()

    // Call parent shutdown (shuts down ActorTestKit and infrastructure)
    super.shutdown()
  }
}
