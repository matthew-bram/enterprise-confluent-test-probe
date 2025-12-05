package io.distia.probe.core.glue.world.fixtures.actors

import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import io.distia.probe.core.models.*
import io.distia.probe.core.models.QueueCommands.*
import io.distia.probe.core.models.TestExecutionCommands.*
import io.distia.probe.core.config.CoreConfig
import io.distia.probe.core.builder.ServiceFunctionsContext
import io.distia.probe.core.glue.world.{ActorWorld, WorldBase}
import io.distia.probe.core.fixtures.{ServiceInterfaceResponsesFixture, TestHarnessFixtures}

import java.util.UUID
import scala.concurrent.duration.*

/**
 * QueueActor-specific testing fixtures.
 *
 * Provides:
 * - State tracking for multi-test scenarios (testIdMapping, createdTestIds, etc.)
 * - Helper methods (spawnQueueActor, sendMessageToQueue, queryQueueStatus)
 * - TestExecutionActor probe management (per-testId probes)
 *
 * Mixed into ActorWorld via trait composition.
 *
 * Design: Self-type ensures access to ActorWorld members (testKit, queueActor, etc.)
 */
trait QueueActorFixtures 
  extends ServiceInterfaceResponsesFixture
  with TestHarnessFixtures {
  this: ActorWorld =>  // Self-type: requires mixing into ActorWorld

  // ==========================================================================
  // QueueActor-Specific State Tracking
  // ==========================================================================

  /**
   * Maps scenario test IDs (from feature files) to actual generated test IDs.
   *
   * QueueActor generates UUIDs dynamically, so scenarios use fixed UUIDs
   * that get mapped to actual UUIDs for message sending.
   */
  var testIdMapping: Map[UUID, UUID] = Map.empty

  /**
   * Last generated test ID (for implicit test references).
   *
   * Set after InitializeTestRequest, used by steps that don't specify a testId.
   */
  var lastGeneratedTestId: Option[UUID] = None

  /**
   * All created test IDs (for bulk operations).
   *
   * Populated during "Given the queue has N tests" steps.
   */
  var createdTestIds: List[UUID] = List.empty

  /**
   * Test IDs not yet assigned to states (for state distribution).
   *
   * Used by "Given N tests are in X state" steps to allocate tests.
   */
  var untransitionedTestIds: List[UUID] = List.empty

  /**
   * Buffered state assignments (for correct transition ordering).
   *
   * Maps state name -> list of test IDs to transition to that state.
   * Processed in specific order: Completed → Exception → Testing → Loaded → Loading → Setup
   */
  var stateAssignments: Map[String, List[UUID]] = Map.empty

  /**
   * Spawned actor references (for termination testing).
   *
   * Maps testId -> ActorRef for actors spawned by QueueActor.
   * Used to trigger unexpected termination scenarios.
   */
  var spawnedActorRefs: Map[UUID, ActorRef[_]] = Map.empty

  /**
   * TestExecutionActor probes per test ID.
   *
   * Maps testId -> TestProbe[TestExecutionCommand].
   * Created on-demand when QueueActor spawns TestExecutionActors.
   */
  var testExecutionActorProbes: Map[UUID, TestProbe[TestExecutionCommands.TestExecutionCommand]] = Map.empty

  // ==========================================================================
  // Helper Methods
  // ==========================================================================

  /**
   * Spawn QueueActor with mock TestExecutionFactory.
   *
   * The factory returns behaviors that:
   * - Forward messages to per-testId TestProbes
   * - Auto-respond to messages (InInitializeTestRequest → InitializeTestResponse, etc.)
   * - Track spawned actor refs for termination testing
   *
   * @param config CoreConfig for QueueActor
   * @return Spawned QueueActor reference
   */
  def spawnQueueActor(config: CoreConfig): ActorRef[QueueCommands.QueueCommand] = {
    import io.distia.probe.core.actors.QueueActor

    this.serviceConfig = Some(config)

    // Create mock factory that returns TestProbe refs AND responds to messages
    val mockFactory: QueueActor.TestExecutionFactory = {
      (testId: UUID, queueRef: ActorRef[QueueCommands.QueueCommand], serviceFunctions: ServiceFunctionsContext) =>
        // Get or create TestProbe for this testId
        val probe: TestProbe[TestExecutionCommands.TestExecutionCommand] =
          getOrCreateTestExecutionProbe(testId)

        // Use Behaviors.setup to capture the spawned actor ref
        org.apache.pekko.actor.typed.scaladsl.Behaviors.setup[TestExecutionCommands.TestExecutionCommand] { context =>
          // Store the spawned actor ref for termination testing
          spawnedActorRefs = spawnedActorRefs + (testId -> context.self)

          // Return a behavior that forwards messages to probe AND responds
          org.apache.pekko.actor.typed.scaladsl.Behaviors.receiveMessage[TestExecutionCommands.TestExecutionCommand] { msg =>
            // Forward to probe for verification
            probe.ref ! msg

            // Simulate TestExecutionActor responses
            msg match
              case TestExecutionCommands.InInitializeTestRequest(tid, replyTo) =>
                // Send response to service
                replyTo ! initializeTestResponse(tid)
                // Notify queue of initialization
                queueRef ! QueueCommands.TestInitialized(tid)

              case TestExecutionCommands.InStartTestRequest(tid, bucket, testType, replyTo) =>
                // Send response to service
                replyTo ! startTestResponse(tid, accepted = true, testType)
                // Notify queue of loading
                queueRef ! QueueCommands.TestLoading(tid)

              case TestExecutionCommands.GetStatus(tid, replyTo) =>
                // Send status response
                replyTo ! testStatusResponse(tid, "Setup", None, None, None, None, Some(true), None)

              case TestExecutionCommands.InCancelRequest(tid, replyTo) =>
                // Send cancel response
                replyTo ! testCancelledResponse(tid, cancelled = true)

              case _ =>
                // Other messages just forwarded to probe

            org.apache.pekko.actor.typed.scaladsl.Behaviors.same
          }
        }
    }

    val stubServiceFunctionsContext: ServiceFunctionsContext = ServiceFunctionsContext(
      vaultFunctions = io.distia.probe.core.builder.VaultServiceFunctions(
        fetchSecurityDirectives = getFailedSecurityDirectivesFetchFunction
      ),
      storageFunctions = io.distia.probe.core.builder.StorageServiceFunctions(
        fetchFromBlockStorage = getFailedBlockStorageFetchFunction,
        loadToBlockStorage = getFailedBlockStorageLoadFunction
      )
    )

    val actor: ActorRef[QueueCommands.QueueCommand] = testKit.spawn(
      QueueActor(config, stubServiceFunctionsContext, Some(mockFactory)),
      "queue-actor"
    )
    queueActor = Some(actor)
    actor
  }

  /**
   * Get or create TestProbe for a specific test ID.
   *
   * Probes are created on-demand and cached in testExecutionActorProbes.
   *
   * @param testId Test UUID
   * @return TestProbe for this testId
   */
  def getOrCreateTestExecutionProbe(testId: UUID): TestProbe[TestExecutionCommands.TestExecutionCommand] = {
    testExecutionActorProbes.get(testId) match
      case Some(probe) => probe
      case None =>
        val probe: TestProbe[TestExecutionCommands.TestExecutionCommand] =
          testKit.createTestProbe[TestExecutionCommands.TestExecutionCommand](s"test-execution-probe-$testId")
        testExecutionActorProbes = testExecutionActorProbes + (testId -> probe)
        probe
  }

  /**
   * Send message to QueueActor.
   *
   * Wrapper for queueActor.foreach(_ ! message).
   *
   * @param message QueueCommand to send
   */
  def sendMessageToQueue(message: QueueCommands.QueueCommand): Unit = {
    queueActor.foreach(_ ! message)
  }

  /**
   * Expect message on TestExecutionActor probe for specific test ID.
   *
   * @tparam T Message type (InInitializeTestRequest, InStartTestRequest, etc.)
   * @param testId Test UUID
   * @param timeout Max wait time (default: 3 seconds)
   * @return Received message
   */
  def expectTestExecutionMessage[T <: TestExecutionCommands.TestExecutionCommand](
    testId: UUID,
    timeout: FiniteDuration = defaultTimeout
  ): T = {
    val probe: TestProbe[TestExecutionCommands.TestExecutionCommand] =
      getOrCreateTestExecutionProbe(testId)
    probe.receiveMessage(timeout).asInstanceOf[T]
  }

  /**
   * Query QueueActor internal state via QueueStatusRequest.
   *
   * Creates temporary probe, sends QueueStatusRequest, waits for response.
   *
   * @return QueueStatusResponse with state counts
   */
  def queryQueueStatus(): QueueStatusResponse = {
    val statusProbe: TestProbe[ServiceResponse] =
      testKit.createTestProbe[ServiceResponse]("status-probe")
    queueActor.foreach(_ ! QueueCommands.QueueStatusRequest(None, statusProbe.ref))
    statusProbe.receiveMessage().asInstanceOf[QueueStatusResponse]
  }
}
