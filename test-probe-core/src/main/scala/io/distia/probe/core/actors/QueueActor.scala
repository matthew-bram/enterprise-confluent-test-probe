package io.distia.probe
package core
package actors

import io.distia.probe.core.builder.ServiceFunctionsContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import config.CoreConfig
import models.QueueCommands.*
import models.TestExecutionCommands.*
import models.*

import java.time.Instant
import java.util.UUID
import scala.collection.immutable.Queue

/**
 * Queue Actor - FIFO Test Management
 *
 * Responsibilities:
 * - Generate UUIDs for new tests
 * - Spawn TestExecutionActors
 * - Route service commands to TFEs with replyTo capture
 * - Maintain FIFO queue for fair test scheduling
 * - Ensure single-threaded test execution (max 1 test in Testing state)
 * - Cleanup TFE references only on TestStopping
 *
 * State Management:
 * - testRegistry: All tests by UUID
 * - pendingQueue: Tests awaiting execution (FIFO order)
 * - loadedTests: Tests in Loaded state ready to run
 * - currentTest: Currently executing test (max 1)
 * - stoppedTests: Tests that have sent TestStopping (cleanup tracking)
 */
private[core] object QueueActor {

  /**
   * Test State tracking within Queue
   */
  sealed trait TestState extends Product with Serializable
  object TestState {
    case object Setup extends TestState
    case object Loading extends TestState
    case object Loaded extends TestState
    case object Testing extends TestState
    case object Completed extends TestState
    case object Exception extends TestState
    case object Stopping extends TestState
  }

  /**
   * Test Entry - complete information about a test
   */
  case class TestEntry(
    testId: UUID,
    actor: ActorRef[TestExecutionCommand],
    state: TestState,
    bucket: Option[String] = None,
    testType: Option[String] = None,
    startRequestTime: Option[Instant] = None,
    replyTo: ActorRef[ServiceResponse]
  )

  /**
   * Queue State - all queue management state
   */
  case class QueueState(
    testRegistry: Map[UUID, TestEntry],
    pendingQueue: Queue[UUID],
    loadedTests: Set[UUID],
    currentTest: Option[UUID],
    stoppedTests: Set[UUID]
  )

  /**
   * Factory type aliases for dependency injection
   */
  type TestExecutionFactory = (UUID, ActorRef[QueueCommand], ServiceFunctionsContext) => Behavior[TestExecutionCommand]

  /**
   * Default TestExecutionActor factory
   */
  def defaultTestExecutionFactory(coreConfig: CoreConfig): TestExecutionFactory = {
    (testId: UUID, queueActor: ActorRef[QueueCommand], serviceFunctions: ServiceFunctionsContext) =>
      TestExecutionActor(testId, queueActor, serviceFunctions, coreConfig)
  }

  /**
   * Queue Actor entry point
   */
  def apply(
    coreConfig: CoreConfig,
    serviceFunctions: ServiceFunctionsContext,
    testExecutionFactory: Option[TestExecutionFactory] = None
  ): Behavior[QueueCommand] = {
    Behaviors.setup { context =>
      context.log.info("QueueActor starting")

      val factory: TestExecutionFactory = testExecutionFactory.getOrElse(defaultTestExecutionFactory(coreConfig))

      val initialState: QueueState = QueueState(
        testRegistry = Map.empty,
        pendingQueue = Queue.empty,
        loadedTests = Set.empty,
        currentTest = None,
        stoppedTests = Set.empty
      )

      active(initialState, serviceFunctions, factory)
    }
  }

  /**
   * Active behavior - processes all queue commands
   */
  def active(
    state: QueueState,
    serviceFunctions: ServiceFunctionsContext,
    factory: TestExecutionFactory
  ): Behavior[QueueCommand] = {
    Behaviors.receive[QueueCommand] { (context, message) =>
      message match {
        case InitializeTestRequest(replyTo) =>
          val testId: UUID = UUID.randomUUID()
          val testActor: ActorRef[TestExecutionCommand] = context.spawn(
            factory(testId, context.self, serviceFunctions),
            s"test-execution-$testId"
          )
          context.watch(testActor)
          val testEntry: TestEntry = TestEntry(
            testId = testId,
            actor = testActor,
            state = TestState.Setup,
            replyTo = replyTo
          )
          val newState: QueueState = state.copy(
            testRegistry = state.testRegistry + (testId -> testEntry)
          )
          testActor ! InInitializeTestRequest(testId, replyTo)

          context.log.info(s"Test $testId initialized and TFE spawned")

          active(newState, serviceFunctions, factory)

        case StartTestRequest(testId, bucket, testType, replyTo) =>
          state.testRegistry.get(testId) match {
            case Some(entry) =>
              val updatedEntry: TestEntry = entry.copy(
                bucket = Some(bucket),
                testType = testType,
                startRequestTime = Some(Instant.now()),
                replyTo = replyTo
              )
              val newState: QueueState = state.copy(
                testRegistry = state.testRegistry.updated(testId, updatedEntry),
                pendingQueue = state.pendingQueue.enqueue(testId)
              )
              entry.actor ! InStartTestRequest(testId, bucket, testType, replyTo)
              context.log.info(s"StartTestRequest for $testId forwarded to TFE, added to pending queue")
              active(newState, serviceFunctions, factory)
            case None =>
              context.log.warn(s"StartTestRequest for unknown test $testId")
              Behaviors.same
          }

        case TestStatusRequest(testId, replyTo) =>
          state.testRegistry.get(testId) match {
            case Some(entry) =>
              entry.actor ! GetStatus(testId, replyTo)

              context.log.debug(s"TestStatusRequest for $testId forwarded to TFE")
              Behaviors.same

            case None =>
              context.log.warn(s"TestStatusRequest for unknown test $testId")
              Behaviors.same
          }

        case QueueStatusRequest(testIdFilter, replyTo) =>
          val response: QueueStatusResponse = QueueStatusResponse(
            totalTests = state.testRegistry.size,
            setupCount = countByState(state, TestState.Setup),
            loadingCount = countByState(state, TestState.Loading),
            loadedCount = state.loadedTests.size,
            testingCount = if state.currentTest.isDefined then 1 else 0,
            completedCount = countByState(state, TestState.Completed),
            exceptionCount = countByState(state, TestState.Exception),
            currentlyTesting = state.currentTest
          )

          replyTo ! response

          context.log.debug(s"QueueStatusResponse sent: $response")
          Behaviors.same

        case CancelRequest(testId, replyTo) =>
          state.testRegistry.get(testId) match {
            case Some(entry) =>
              entry.actor ! InCancelRequest(testId, replyTo)
              context.log.info(s"CancelRequest for $testId forwarded to TFE")
              Behaviors.same
            case None =>
              context.log.warn(s"CancelRequest for unknown test $testId")
              Behaviors.same
          }
        case TestInitialized(testId) =>
          updateTestState(context, state, testId, TestState.Setup, serviceFunctions, factory)
        case TestLoading(testId) =>
          updateTestState(context, state, testId, TestState.Loading, serviceFunctions, factory)
        case TestLoaded(testId) =>
          state.testRegistry.get(testId) match {
            case Some(entry) =>
              val updatedEntry: TestEntry = entry.copy(state = TestState.Loaded)
              val newState: QueueState = state.copy(
                testRegistry = state.testRegistry.updated(testId, updatedEntry),
                loadedTests = state.loadedTests + testId
              )
              context.log.info(s"Test $testId loaded and ready for execution")
              processQueue(context, newState, serviceFunctions, factory)
            case None =>
              context.log.warn(s"TestLoaded for unknown test $testId")
              Behaviors.same
          }

        case TestStarted(testId) =>
          updateTestState(context, state, testId, TestState.Testing, serviceFunctions, factory)

        case TestCompleted(testId) =>
          state.testRegistry.get(testId) match {
            case Some(entry) =>
              val updatedEntry: TestEntry = entry.copy(state = TestState.Completed)
              val newState: QueueState = state.copy(
                testRegistry = state.testRegistry.updated(testId, updatedEntry),
                currentTest = if state.currentTest.contains(testId) then None else state.currentTest
              )
              context.log.info(s"Test $testId completed")
              if state.currentTest.contains(testId) then
                processQueue(context, newState, serviceFunctions, factory)
              else
                active(newState, serviceFunctions, factory)
            case None =>
              context.log.warn(s"TestCompleted for unknown test $testId")
              Behaviors.same
          }
        case TestException(testId, exception) =>
          state.testRegistry.get(testId) match {
            case Some(entry) =>
              val updatedEntry: TestEntry = entry.copy(state = TestState.Exception)
              val newState: QueueState = state.copy(
                testRegistry = state.testRegistry.updated(testId, updatedEntry),
                currentTest = if state.currentTest.contains(testId) then None else state.currentTest
              )
              context.log.warn(s"Test $testId exception: ${exception.asInstanceOf[Exception].getMessage}")
              if state.currentTest.contains(testId) then
                processQueue(context, newState, serviceFunctions, factory)
              else
                active(newState, serviceFunctions, factory)
            case None =>
              context.log.warn(s"TestException for unknown test $testId")
              Behaviors.same
          }

        case TestStopping(testId) =>
          val newState: QueueState = state.copy(
            testRegistry = state.testRegistry - testId,
            pendingQueue = state.pendingQueue.filterNot(_ == testId),
            loadedTests = state.loadedTests - testId,
            currentTest = if state.currentTest.contains(testId) then None else state.currentTest,
            stoppedTests = state.stoppedTests + testId
          )

          context.log.info(s"Test $testId stopping, cleaned up from queue")
          if state.currentTest.contains(testId) then
            processQueue(context, newState, serviceFunctions, factory)
          else
            active(newState, serviceFunctions, factory)

        case QueueCommands.TestStatusResponse(_, _, _, _, _, _, _, _) =>
          context.log.warn("Received TestStatusResponse - this should go directly to service")
          Behaviors.same
      }
    }.receiveSignal {
      case (ctx: org.apache.pekko.actor.typed.scaladsl.ActorContext[QueueCommand], org.apache.pekko.actor.typed.Terminated(actor)) =>
        state.testRegistry.find(_._2.actor == actor) match {
          case Some((testId, entry)) =>
            ctx.log.error(s"TestExecutionActor for test $testId terminated unexpectedly")
            val updatedEntry: TestEntry = entry.copy(state = TestState.Exception)
            val newState: QueueState = state.copy(
              testRegistry = state.testRegistry.updated(testId, updatedEntry),
              currentTest = if state.currentTest.contains(testId) then None else state.currentTest
            )
            if state.currentTest.contains(testId) then
              processQueue(ctx, newState, serviceFunctions, factory)
            else
              active(newState, serviceFunctions, factory)
          case None =>
            ctx.log.warn("Terminated actor not found in registry")
            Behaviors.same
        }
    }
  }

  /**
   * Update test state helper
   */
  def updateTestState(
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[QueueCommand],
    state: QueueState,
    testId: UUID,
    newState: TestState,
    serviceFunctions: ServiceFunctionsContext,
    factory: TestExecutionFactory
  ): Behavior[QueueCommand] = {
    state.testRegistry.get(testId) match {
      case Some(entry) =>
        val updatedEntry: TestEntry = entry.copy(state = newState)

        val updatedState: QueueState = state.copy(
          testRegistry = state.testRegistry.updated(testId, updatedEntry)
        )

        context.log.debug(s"Test $testId state updated to $newState")

        active(updatedState, serviceFunctions, factory)

      case None =>
        context.log.warn(s"Update state for unknown test $testId")
        Behaviors.same
    }
  }

  /**
   * Process queue to start next available test
   *
   * FIFO: Start the oldest test (by startRequestTime) that is in Loaded state
   * Single-threaded: Only start if currentTest is None
   */
  def processQueue(
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[QueueCommand],
    state: QueueState,
    serviceFunctions: ServiceFunctionsContext,
    factory: TestExecutionFactory
  ): Behavior[QueueCommand] = {
    if state.currentTest.isEmpty then
      val readyTestId: Option[UUID] = state.pendingQueue.find(state.loadedTests.contains)

      readyTestId match {
        case Some(testId) =>
          state.testRegistry.get(testId) match {
            case Some(entry) =>
              entry.actor ! StartTesting(testId)

              val newState: QueueState = state.copy(
                currentTest = Some(testId),
                pendingQueue = state.pendingQueue.filterNot(_ == testId),
                loadedTests = state.loadedTests - testId
              )

              context.log.info(s"Starting test $testId (FIFO processing)")

              active(newState, serviceFunctions, factory)

            case None =>
              context.log.warn(s"Ready test $testId not found in registry")
              Behaviors.same
          }

        case None =>
          context.log.debug("No tests ready for execution")
          active(state, serviceFunctions, factory)
      }
    else
      state.currentTest.foreach(testId =>
        context.log.debug(s"Test $testId currently executing")
      )
      active(state, serviceFunctions, factory)
  }

  /**
   * Count tests by state
   */
  def countByState(state: QueueState, testState: TestState): Int = {
    state.testRegistry.count(_._2.state == testState)
  }
}
