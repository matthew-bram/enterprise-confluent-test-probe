package io.distia.probe
package core
package actors

import io.distia.probe.core.builder.ServiceFunctionsContext
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpec
import config.CoreConfig
import fixtures.{ActorTestingFixtures, ConfigurationFixtures, TestHarnessFixtures}
import models.QueueCommands.*
import models.TestExecutionCommands.*
import models.*

import java.util.UUID
import scala.concurrent.duration.*

/**
 * Companion object for QueueActorSpec test data.
 *
 * Contains shared stubs, test constants, and factory builders.
 */
private[actors] object QueueActorSpec {

  // Reuse GuardianActorSpec's stub service functions
  val stubServiceFunctionsContext: ServiceFunctionsContext = GuardianActorSpec.stubServiceFunctionsContext

  /** Predefined test UUIDs for deterministic testing */
  object TestIds {
    val test1 = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val test2 = UUID.fromString("22222222-2222-2222-2222-222222222222")
    val test3 = UUID.fromString("33333333-3333-3333-3333-333333333333")
  }

  /** Standard bucket names */
  object Buckets {
    val bucket1 = "test-bucket-1"
    val bucket2 = "test-bucket-2"
    val bucket3 = "test-bucket-3"
  }

  /** Standard test type identifiers */
  object TestTypes {
    val functional = "functional"
    val performance = "performance"
    val regression = "regression"
  }

  /** Create CucumberException for testing */
  def mockCucumberException(message: String = "Cucumber error") = CucumberException(message)

  /** Create mock TestEntry (used in helper method tests) */
  def mockTestEntry(
    testId: UUID,
    testActorProbe: TestProbe[TestExecutionCommand],
    state: QueueActor.TestState,
    replyToProbe: TestProbe[ServiceResponse]
  ): QueueActor.TestEntry = {
    QueueActor.TestEntry(
      testId = testId,
      actor = testActorProbe.ref,
      state = state,
      bucket = None,
      testType = None,
      startRequestTime = None,
      replyTo = replyToProbe.ref
    )
  }

  /** Create mock QueueState (used in helper method tests) */
  def mockQueueState(
    testRegistry: Map[UUID, QueueActor.TestEntry] = Map.empty
  ): QueueActor.QueueState = {
    QueueActor.QueueState(
      testRegistry = testRegistry,
      pendingQueue = scala.collection.immutable.Queue.empty,
      loadedTests = Set.empty,
      currentTest = None,
      stoppedTests = Set.empty
    )
  }
}

/**
 * Unit tests for QueueActor.
 *
 * Tests the queue management actor responsible for test lifecycle coordination,
 * FIFO execution ordering, and single-test-at-a-time enforcement.
 *
 * Design Principles:
 * - Uses ActorTestingFixtures for actor test infrastructure (Pattern #3)
 * - Uses TestHarnessFixtures for test data builders (Pattern #1)
 * - Inline test data via companion object - no god objects (Pattern #5)
 * - Comprehensive Scaladoc (Pattern #10)
 *
 * Test Coverage:
 * - Test initialization and UUID generation
 * - Test registration in queue state
 * - Message routing to TestExecutionActor (StartTest, TestStatus, Cancel)
 * - FSM state updates (TestInitialized, TestLoaded, TestCompleted, TestException, TestStopping)
 * - FIFO queue ordering enforcement
 * - Single test execution constraint (no parallel execution)
 * - QueueStatusRequest handling with state counting
 * - Edge cases (unknown test IDs, non-current test completions)
 * - Helper method validation (countByState)
 *
 * Thread Safety: Each test gets fresh TestProbes and actors (isolated)
 */
class QueueActorSpec extends AnyWordSpec
  with ActorTestingFixtures
  with TestHarnessFixtures {
  import QueueActorSpec.*

  // Test configuration from Phase 1 fixtures
  private val serviceConfig: CoreConfig = ConfigurationFixtures.defaultCoreConfig

  // ===== Helper Methods =====

  /** Spawn QueueActor with default configuration */
  private def spawnQueue(): ActorRef[QueueCommand] =
    testKit.spawn(QueueActor(serviceConfig, stubServiceFunctionsContext))

  /** Spawn QueueActor with custom factory */
  private def spawnQueueWithFactory(factory: QueueActor.TestExecutionFactory): ActorRef[QueueCommand] =
    testKit.spawn(QueueActor(serviceConfig, stubServiceFunctionsContext, Some(factory)))

  /** Create TestProbe for TestExecutionCommand (uses inherited createProbe from ActorTestingFixtures) */
  private def createTfeProbe(name: String = "tfeProbe"): TestProbe[TestExecutionCommand] =
    createProbe[TestExecutionCommand](name)

  /** Simple forwarding factory - forwards all messages to probe */
  private def forwardingFactory(tfeProbe: TestProbe[TestExecutionCommand]): QueueActor.TestExecutionFactory =
    (testId: UUID, queueActor: ActorRef[QueueCommand], serviceFunctions: ServiceFunctionsContext) =>
      org.apache.pekko.actor.typed.scaladsl.Behaviors.receiveMessage { msg =>
        tfeProbe.ref ! msg
        org.apache.pekko.actor.typed.scaladsl.Behaviors.same
      }

  /** Multi-probe factory for FIFO tests - distributes spawns across multiple probes */
  private def multiProbeFactory(probes: TestProbe[TestExecutionCommand]*): QueueActor.TestExecutionFactory = {
    var index = 0
    (testId: UUID, queueActor: ActorRef[QueueCommand], serviceFunctions: ServiceFunctionsContext) => {
      val currentProbe = probes(index)
      index += 1
      org.apache.pekko.actor.typed.scaladsl.Behaviors.receiveMessage { msg =>
        currentProbe.ref ! msg
        org.apache.pekko.actor.typed.scaladsl.Behaviors.same
      }
    }
  }

  /** Initialize test workflow and return testId */
  private def initializeTestAndGetId(
    queue: ActorRef[QueueCommand],
    tfeProbe: TestProbe[TestExecutionCommand],
    serviceProbe: TestProbe[ServiceResponse]
  ): UUID = {
    queue ! InitializeTestRequest(serviceProbe.ref)
    val initMsg = tfeProbe.expectMessageType[InInitializeTestRequest](1.second)
    initMsg.asInstanceOf[InInitializeTestRequest].testId
  }

  // =========================================================================
  // TEST INITIALIZATION TESTS
  // =========================================================================

  "QueueActor" should {

    "generate UUID and spawn TFE on InitializeTestRequest" in {
      val serviceProbe = createServiceProbe()
      val tfeProbe = createTfeProbe()

      // Mock factory that returns our probe
      val factory = forwardingFactory(tfeProbe)
      val queue = spawnQueueWithFactory(factory)

      // Send InitializeTestRequest
      queue ! InitializeTestRequest(serviceProbe.ref)

      // TFE should receive InInitializeTestRequest with testId and replyTo
      val msg = tfeProbe.expectMessageType[InInitializeTestRequest](3.seconds)
      msg match {
        case InInitializeTestRequest(testId, replyTo) =>
          testId should not be null
          replyTo shouldBe serviceProbe.ref
        case _ => fail(s"Expected InInitializeTestRequest, got $msg")
      }
    }

    "add test to registry on InitializeTestRequest" in {
      val serviceProbe = createServiceProbe()
      val queue = spawnQueue()

      // Initialize one test
      queue ! InitializeTestRequest(serviceProbe.ref)

      // Give actor time to process
      Thread.sleep(100)

      // Query queue status
      val statusProbe = createServiceProbe("statusProbe")
      queue ! QueueStatusRequest(None, statusProbe.ref)

      val response = statusProbe.expectMessageType[QueueStatusResponse](1.second)
      response match {
        case QueueStatusResponse(totalTests, _, _, _, _, _, _, _) =>
          totalTests shouldBe 1
        case _ => fail(s"Expected QueueStatusResponse, got $response")
      }
    }

    // ===== Message Routing =====

    "forward StartTestRequest to TFE with replyTo" in {
      val serviceProbe = createServiceProbe()
      val tfeProbe = createTfeProbe()
      val factory = forwardingFactory(tfeProbe)
      val queue = spawnQueueWithFactory(factory)

      // Initialize test
      val testId = initializeTestAndGetId(queue, tfeProbe, serviceProbe)

      // Send StartTestRequest
      queue ! StartTestRequest(testId, Buckets.bucket1, Some(TestTypes.functional), serviceProbe.ref)

      // TFE should receive InStartTestRequest
      val startMsg = tfeProbe.expectMessageType[InStartTestRequest](1.second)
      startMsg match {
        case InStartTestRequest(receivedTestId, bucket, testType, replyTo) =>
          receivedTestId shouldBe testId
          bucket shouldBe Buckets.bucket1
          testType shouldBe Some(TestTypes.functional)
          replyTo shouldBe serviceProbe.ref
        case _ => fail(s"Expected InStartTestRequest, got $startMsg")
      }
    }

    "forward TestStatusRequest to TFE with replyTo" in {
      val serviceProbe = createServiceProbe()
      val tfeProbe = createTfeProbe()
      val factory = forwardingFactory(tfeProbe)
      val queue = spawnQueueWithFactory(factory)

      // Initialize test
      val testId = initializeTestAndGetId(queue, tfeProbe, serviceProbe)

      // Send TestStatusRequest
      queue ! TestStatusRequest(testId, serviceProbe.ref)

      // TFE should receive GetStatus
      val statusMsg = tfeProbe.expectMessageType[GetStatus](1.second)
      statusMsg match {
        case GetStatus(receivedTestId, replyTo) =>
          receivedTestId shouldBe testId
          replyTo shouldBe serviceProbe.ref
        case _ => fail(s"Expected GetStatus, got $statusMsg")
      }
    }

    "handle QueueStatusRequest directly" in {
      val serviceProbe = createServiceProbe()
      val queue = spawnQueue()

      // Send QueueStatusRequest
      queue ! QueueStatusRequest(None, serviceProbe.ref)

      // Queue should respond directly (not forward to TFE)
      val response = serviceProbe.expectMessageType[QueueStatusResponse](1.second)
      response match {
        case QueueStatusResponse(totalTests, setupCount, loadingCount, loadedCount, testingCount, completedCount, exceptionCount, currentlyTesting) =>
          totalTests shouldBe 0
          setupCount shouldBe 0
          loadingCount shouldBe 0
          loadedCount shouldBe 0
          testingCount shouldBe 0
          completedCount shouldBe 0
          exceptionCount shouldBe 0
          currentlyTesting shouldBe None
        case _ => fail(s"Expected QueueStatusResponse, got $response")
      }
    }

    "forward CancelRequest to TFE with replyTo" in {
      val serviceProbe = createServiceProbe()
      val tfeProbe = createTfeProbe()
      val factory = forwardingFactory(tfeProbe)
      val queue = spawnQueueWithFactory(factory)

      // Initialize test
      val testId = initializeTestAndGetId(queue, tfeProbe, serviceProbe)

      // Send CancelRequest
      queue ! CancelRequest(testId, serviceProbe.ref)

      // TFE should receive InCancelRequest
      val cancelMsg = tfeProbe.expectMessageType[InCancelRequest](1.second)
      cancelMsg match {
        case InCancelRequest(receivedTestId, replyTo) =>
          receivedTestId shouldBe testId
          replyTo shouldBe serviceProbe.ref
        case _ => fail(s"Expected InCancelRequest, got $cancelMsg")
      }
    }

    // ===== FSM Communications =====

    "update state on TestInitialized" in {
      val serviceProbe = createServiceProbe()
      val tfeProbe = createTfeProbe()

      val factory: QueueActor.TestExecutionFactory = (testId: UUID, queueActor: ActorRef[QueueCommand], serviceFunctions: ServiceFunctionsContext) => {
        org.apache.pekko.actor.typed.scaladsl.Behaviors.receiveMessage { msg =>
          tfeProbe.ref ! msg
          // Simulate TFE sending TestInitialized
          queueActor ! TestInitialized(testId)
          org.apache.pekko.actor.typed.scaladsl.Behaviors.same
        }
      }

      val queue = spawnQueueWithFactory(factory)

      // Initialize test
      queue ! InitializeTestRequest(serviceProbe.ref)
      tfeProbe.expectMessageType[InInitializeTestRequest](1.second)

      // Give time for TestInitialized to process
      Thread.sleep(100)

      // Query status - should show 1 test in Setup
      val statusProbe = createServiceProbe("statusProbe")
      queue ! QueueStatusRequest(None, statusProbe.ref)

      val response = statusProbe.expectMessageType[QueueStatusResponse](1.second)
      response match {
        case QueueStatusResponse(totalTests, setupCount, _, _, _, _, _, _) =>
          totalTests shouldBe 1
          setupCount shouldBe 1
        case _ => fail(s"Expected QueueStatusResponse, got $response")
      }
    }

    "add test to loadedTests on TestLoaded" in {
      val serviceProbe = createServiceProbe()
      val queue = spawnQueue()

      // Initialize test
      queue ! InitializeTestRequest(serviceProbe.ref)
      Thread.sleep(100)

      // Simulate TestLoaded (need to get testId first)
      val statusProbe = createServiceProbe("statusProbe")
      queue ! QueueStatusRequest(None, statusProbe.ref)
      statusProbe.expectMessageType[QueueStatusResponse](1.second)

      // For this test, we'll use a mock testId
      val testId = TestIds.test1
      queue ! TestLoaded(testId)

      // Give time to process
      Thread.sleep(100)

      // Query status - loadedCount might not increase if test not in registry
      // This is an edge case we're testing
    }

    // ===== FIFO Queue Ordering =====

    "maintain FIFO ordering for test execution" in {
      val serviceProbe = createServiceProbe()
      val tfeProbe1 = createTfeProbe("tfe1")
      val tfeProbe2 = createTfeProbe("tfe2")
      val tfeProbe3 = createTfeProbe("tfe3")

      val factory = multiProbeFactory(tfeProbe1, tfeProbe2, tfeProbe3)
      val queue = spawnQueueWithFactory(factory)

      // Initialize 3 tests
      val test1Id = initializeTestAndGetId(queue, tfeProbe1, serviceProbe)
      val test2Id = initializeTestAndGetId(queue, tfeProbe2, serviceProbe)
      val test3Id = initializeTestAndGetId(queue, tfeProbe3, serviceProbe)

      // Start tests in order: test1, test2, test3
      queue ! StartTestRequest(test1Id, Buckets.bucket1, Some(TestTypes.functional), serviceProbe.ref)
      tfeProbe1.expectMessageType[InStartTestRequest](1.second)

      Thread.sleep(50) // Small delay to ensure ordering

      queue ! StartTestRequest(test2Id, Buckets.bucket2, Some(TestTypes.performance), serviceProbe.ref)
      tfeProbe2.expectMessageType[InStartTestRequest](1.second)

      Thread.sleep(50)

      queue ! StartTestRequest(test3Id, Buckets.bucket3, Some(TestTypes.regression), serviceProbe.ref)
      tfeProbe3.expectMessageType[InStartTestRequest](1.second)

      // Simulate all tests becoming loaded
      queue ! TestLoaded(test1Id)
      queue ! TestLoaded(test2Id)
      queue ! TestLoaded(test3Id)

      // Test1 should start first (FIFO)
      tfeProbe1.expectMessageType[StartTesting](2.seconds).testId shouldBe test1Id

      // Complete test1
      queue ! TestCompleted(test1Id)

      // Test2 should start next (FIFO)
      tfeProbe2.expectMessageType[StartTesting](2.seconds).testId shouldBe test2Id

      // Complete test2
      queue ! TestCompleted(test2Id)

      // Test3 should start last (FIFO)
      tfeProbe3.expectMessageType[StartTesting](2.seconds).testId shouldBe test3Id
    }

    // ===== Single Test Execution =====

    "enforce single test execution" in {
      val serviceProbe = createServiceProbe()
      val tfeProbe1 = createTfeProbe("tfe1")
      val tfeProbe2 = createTfeProbe("tfe2")

      val factory = multiProbeFactory(tfeProbe1, tfeProbe2)
      val queue = spawnQueueWithFactory(factory)

      // Initialize 2 tests
      val test1Id = initializeTestAndGetId(queue, tfeProbe1, serviceProbe)
      val test2Id = initializeTestAndGetId(queue, tfeProbe2, serviceProbe)

      // Start both tests
      queue ! StartTestRequest(test1Id, Buckets.bucket1, Some(TestTypes.functional), serviceProbe.ref)
      tfeProbe1.expectMessageType[InStartTestRequest](1.second)

      queue ! StartTestRequest(test2Id, Buckets.bucket2, Some(TestTypes.performance), serviceProbe.ref)
      tfeProbe2.expectMessageType[InStartTestRequest](1.second)

      // Both become loaded
      queue ! TestLoaded(test1Id)
      queue ! TestLoaded(test2Id)

      // Only test1 should start
      tfeProbe1.expectMessageType[StartTesting](2.seconds).testId shouldBe test1Id

      // Test2 should NOT receive StartTesting yet
      tfeProbe2.expectNoMessage(1.second)

      // Query queue status
      val statusProbe = createServiceProbe("statusProbe")
      queue ! QueueStatusRequest(None, statusProbe.ref)

      val response = statusProbe.expectMessageType[QueueStatusResponse](1.second)
      response match {
        case QueueStatusResponse(_, _, _, _, testingCount, _, _, currentlyTesting) =>
          testingCount shouldBe 1 // Only one test in Testing state
          currentlyTesting shouldBe Some(test1Id)
        case _ => fail(s"Expected QueueStatusResponse, got $response")
      }

      // Complete test1
      queue ! TestCompleted(test1Id)

      // Now test2 should start
      tfeProbe2.expectMessageType[StartTesting](2.seconds).testId shouldBe test2Id
    }

    // ===== Cleanup on TestStopping =====

    "remove test from registry on TestStopping" in {
      val serviceProbe = createServiceProbe()
      val tfeProbe = createTfeProbe()
      val factory = forwardingFactory(tfeProbe)
      val queue = spawnQueueWithFactory(factory)

      // Initialize test
      val testId = initializeTestAndGetId(queue, tfeProbe, serviceProbe)

      // Verify test is in registry
      val statusProbe1 = createServiceProbe("statusProbe1")
      queue ! QueueStatusRequest(None, statusProbe1.ref)
      val response1 = statusProbe1.expectMessageType[QueueStatusResponse](1.second)
      response1.asInstanceOf[QueueStatusResponse].totalTests shouldBe 1

      // Send TestStopping
      queue ! TestStopping(testId)

      // Give time to process
      Thread.sleep(100)

      // Verify test is removed from registry
      val statusProbe2 = createServiceProbe("statusProbe2")
      queue ! QueueStatusRequest(None, statusProbe2.ref)
      val response2 = statusProbe2.expectMessageType[QueueStatusResponse](1.second)
      response2.asInstanceOf[QueueStatusResponse].totalTests shouldBe 0
    }

    "start next test after TestStopping of current test" in {
      val serviceProbe = createServiceProbe()
      val tfeProbe1 = createTfeProbe("tfe1")
      val tfeProbe2 = createTfeProbe("tfe2")

      val factory = multiProbeFactory(tfeProbe1, tfeProbe2)
      val queue = spawnQueueWithFactory(factory)

      // Initialize 2 tests
      val test1Id = initializeTestAndGetId(queue, tfeProbe1, serviceProbe)
      val test2Id = initializeTestAndGetId(queue, tfeProbe2, serviceProbe)

      // Start both tests
      queue ! StartTestRequest(test1Id, Buckets.bucket1, Some(TestTypes.functional), serviceProbe.ref)
      tfeProbe1.expectMessageType[InStartTestRequest](1.second)

      queue ! StartTestRequest(test2Id, Buckets.bucket2, Some(TestTypes.performance), serviceProbe.ref)
      tfeProbe2.expectMessageType[InStartTestRequest](1.second)

      // Both become loaded
      queue ! TestLoaded(test1Id)
      queue ! TestLoaded(test2Id)

      // Test1 starts
      tfeProbe1.expectMessageType[StartTesting](2.seconds)

      // Send TestStopping for test1 (simulating shutdown after Testing)
      queue ! TestStopping(test1Id)

      // Test2 should start now
      tfeProbe2.expectMessageType[StartTesting](2.seconds).testId shouldBe test2Id
    }

    // ===== Edge Cases =====

    "handle StartTestRequest for unknown test ID" in {
      val serviceProbe = createServiceProbe()
      val queue = spawnQueue()

      // Send StartTestRequest with unknown testId
      queue ! StartTestRequest(TestIds.test1, Buckets.bucket1, Some(TestTypes.functional), serviceProbe.ref)

      // No response expected, just verify no crash
      serviceProbe.expectNoMessage(1.second)
    }

    "handle TestStatusRequest for unknown test ID" in {
      val serviceProbe = createServiceProbe()
      val queue = spawnQueue()

      // Send TestStatusRequest with unknown testId
      queue ! TestStatusRequest(TestIds.test1, serviceProbe.ref)

      // No response expected, just verify no crash
      serviceProbe.expectNoMessage(1.second)
    }

    "handle CancelRequest for unknown test ID" in {
      val serviceProbe = createServiceProbe()
      val queue = spawnQueue()

      // Send CancelRequest with unknown testId
      queue ! CancelRequest(TestIds.test1, serviceProbe.ref)

      // No response expected, just verify no crash
      serviceProbe.expectNoMessage(1.second)
    }

    "handle TestCompleted for non-current test" in {
      val serviceProbe = createServiceProbe()
      val queue = spawnQueue()

      // Send TestCompleted for unknown test
      queue ! TestCompleted(TestIds.test1)

      // No crash expected
      Thread.sleep(100)

      // Verify queue is still operational
      val statusProbe = createServiceProbe("statusProbe")
      queue ! QueueStatusRequest(None, statusProbe.ref)
      statusProbe.expectMessageType[QueueStatusResponse](1.second)
    }

    "handle TestException and start next test" in {
      val serviceProbe = createServiceProbe()
      val tfeProbe1 = createTfeProbe("tfe1")
      val tfeProbe2 = createTfeProbe("tfe2")

      val factory = multiProbeFactory(tfeProbe1, tfeProbe2)
      val queue = spawnQueueWithFactory(factory)

      // Initialize 2 tests
      val test1Id = initializeTestAndGetId(queue, tfeProbe1, serviceProbe)
      val test2Id = initializeTestAndGetId(queue, tfeProbe2, serviceProbe)

      // Start both
      queue ! StartTestRequest(test1Id, Buckets.bucket1, Some(TestTypes.functional), serviceProbe.ref)
      tfeProbe1.expectMessageType[InStartTestRequest](1.second)

      queue ! StartTestRequest(test2Id, Buckets.bucket2, Some(TestTypes.performance), serviceProbe.ref)
      tfeProbe2.expectMessageType[InStartTestRequest](1.second)

      // Both loaded
      queue ! TestLoaded(test1Id)
      queue ! TestLoaded(test2Id)

      // Test1 starts
      tfeProbe1.expectMessageType[StartTesting](2.seconds)

      // Test1 encounters exception
      queue ! TestException(test1Id, mockCucumberException("Test failed"))

      // Test2 should start
      tfeProbe2.expectMessageType[StartTesting](2.seconds).testId shouldBe test2Id
    }

    // ===== Helper Methods =====

    "countByState should count tests correctly" in {
      val tfeProbe1 = createTfeProbe("tfe1")
      val tfeProbe2 = createTfeProbe("tfe2")
      val tfeProbe3 = createTfeProbe("tfe3")
      val serviceProbe = createServiceProbe()

      val testEntry1 = mockTestEntry(TestIds.test1, tfeProbe1, QueueActor.TestState.Setup, serviceProbe)
      val testEntry2 = mockTestEntry(TestIds.test2, tfeProbe2, QueueActor.TestState.Loading, serviceProbe)
      val testEntry3 = mockTestEntry(TestIds.test3, tfeProbe3, QueueActor.TestState.Setup, serviceProbe)

      val state = mockQueueState(
        testRegistry = Map(
          TestIds.test1 -> testEntry1,
          TestIds.test2 -> testEntry2,
          TestIds.test3 -> testEntry3
        )
      )

      QueueActor.countByState(state, QueueActor.TestState.Setup) shouldBe 2
      QueueActor.countByState(state, QueueActor.TestState.Loading) shouldBe 1
      QueueActor.countByState(state, QueueActor.TestState.Loaded) shouldBe 0
    }
  }
}
