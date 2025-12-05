/*
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║ ⚠️  SECURITY WARNING - SENSITIVE DATA HANDLING                       ║
 * ╠══════════════════════════════════════════════════════════════════════╣
 * ║ This actor handles KafkaSecurityDirective containing:                     ║
 * ║ - OAuth client IDs and secrets                                       ║
 * ║ - Vault credentials                                                  ║
 * ║ - Kafka authentication tokens                                        ║
 * ║                                                                      ║
 * ║ ⚠️  DO NOT LOG KafkaSecurityDirective OR ANY DERIVED CREDENTIALS          ║
 * ║                                                                      ║
 * ║ All logging must:                                                    ║
 * ║ 1. Exclude KafkaSecurityDirective objects from log statements            ║
 * ║ 2. Redact credentials in error messages                             ║
 * ║ 3. Use testId for correlation, never credentials                    ║
 * ║                                                                      ║
 * ║ Future: Security agent will enforce compliance                      ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
package io.distia.probe
package core
package actors

import io.distia.probe.common.models.BlockStorageDirective
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import models.CucumberExecutionCommands.*
import models.TestExecutionCommands.*
import models.*
import services.cucumber.{CucumberConfiguration, CucumberExecutor}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * CucumberExecutionActor - Test Execution Management
 *
 * Responsibilities:
 * 1. Initialize: Setup Cucumber test execution environment (stub)
 * 2. StartTest: Execute Cucumber scenarios (stub)
 * 3. Stop: Clean shutdown
 *
 * Message Protocol:
 * - Receives: Initialize(blockStorageDirective), StartTest, Stop
 * - Sends to parent TEA: ChildGoodToGo, TestComplete
 *
 * State: Implicit (Created → Initializing → Ready → Executing → Stopped)
 *
 * SECURITY: This actor handles sensitive KafkaSecurityDirective data
 * All credentials must be redacted in logs
 *
 * NOTE: This is scaffolding only - service integration stubs marked with TODO
 */
private[core] object CucumberExecutionActor {

  /**
   * Default Cucumber executor function (wraps static CucumberExecutor.execute)
   *
   * Production default: Executes real Cucumber scenarios
   * Tests can inject mock functions for exception scenarios
   *
   * @return Function that executes Cucumber and returns TestExecutionResult
   */
  private[core] val defaultCucumberExecutor: (CucumberConfiguration, UUID, BlockStorageDirective) => ExecutionContext ?=> Future[TestExecutionResult] =
    (config, testId, directive) => ec ?=> Future {
      CucumberExecutor.execute(config, testId, directive)
    }(ec)

  /**
   * Factory method for CucumberExecutionActor
   *
   * @param testId UUID of the test
   * @param parentTea Reference to parent TestExecutionActor
   * @param cucumberExecutor Function to execute Cucumber (default: real execution)
   * @return Behavior for CucumberExecutionCommand
   */
  def apply(
    testId: UUID,
    parentTea: ActorRef[TestExecutionCommand],
    cucumberExecutor: (CucumberConfiguration, UUID, BlockStorageDirective) => ExecutionContext ?=> Future[TestExecutionResult] = defaultCucumberExecutor
  ): Behavior[CucumberExecutionCommand] = {
    Behaviors.setup { context =>
      context.log.info(s"CucumberExecutionActor starting for test $testId")
      implicit val cucumberBlockingEc: ExecutionContext = context.system.dispatchers.lookup(
        DispatcherSelector.fromConfig("pekko.actor.cucumber-blocking-dispatcher")
      )
      activeBehavior(
        testId,
        initialized = false,
        blockStorageDirective = None,
        parentTea,
        context,
        cucumberBlockingEc,
        cucumberExecutor
      )
    }
  }

  /**
   * Active behavior - handles all commands
   *
   * @param testId UUID of the test
   * @param initialized Whether Initialize has been called
   * @param blockStorageDirective Directive from Initialize (contains jimfsLocation for features)
   * @param parentTea Reference to parent TestExecutionActor
   * @param context Actor context from setup
   * @param cucumberBlockingEc Dedicated dispatcher for blocking Cucumber execution
   * @param cucumberExecutor Function to execute Cucumber (injected for testability)
   * @return Behavior for next state
   */
  def activeBehavior(
    testId: UUID,
    initialized: Boolean,
    blockStorageDirective: Option[BlockStorageDirective],
    parentTea: ActorRef[TestExecutionCommand],
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[CucumberExecutionCommand],
    cucumberBlockingEc: ExecutionContext,
    cucumberExecutor: (CucumberConfiguration, UUID, BlockStorageDirective) => ExecutionContext ?=> Future[TestExecutionResult]
  ): Behavior[CucumberExecutionCommand] = {
    Behaviors.receiveMessage {
      case Initialize(directive) =>
        handleInitialize(testId, directive, initialized, parentTea, context, cucumberBlockingEc, cucumberExecutor)

      case StartTest =>
        handleStartTest(testId, initialized, blockStorageDirective, parentTea, context, cucumberBlockingEc, cucumberExecutor)

      case TestExecutionComplete(result) =>
        handleTestExecutionComplete(testId, result, parentTea, context)

      case Stop =>
        handleStop(testId, context)
    }
  }

  /**
   * Handle Initialize command: Store directives for later execution
   *
   * @param testId UUID of the test
   * @param blockStorageDirective Directive containing jimfsLocation for feature files
   * @param initialized Whether already initialized
   * @param parentTea Reference to parent TestExecutionActor
   * @param context Actor context
   * @param cucumberBlockingEc Dedicated dispatcher for blocking execution
   * @param cucumberExecutor Function to execute Cucumber (injected for testability)
   * @return Updated behavior
   */
  def handleInitialize(
    testId: UUID,
    blockStorageDirective: BlockStorageDirective,
    initialized: Boolean,
    parentTea: ActorRef[TestExecutionCommand],
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[CucumberExecutionCommand],
    cucumberBlockingEc: ExecutionContext,
    cucumberExecutor: (CucumberConfiguration, UUID, BlockStorageDirective) => ExecutionContext ?=> Future[TestExecutionResult]
  ): Behavior[CucumberExecutionCommand] = {
    val initResult: Try[Unit] = Try {
      require(
        blockStorageDirective.jimfsLocation.nonEmpty && blockStorageDirective.evidenceDir.nonEmpty,
        s"""Cannot Initialize CucumberExecutionActor without test
           | directory or evidence directory. Test ID: $testId""".stripMargin
      )
    }

    initResult match {
      case Success(_) =>
        parentTea ! ChildGoodToGo(testId, context.self)
        activeBehavior(
          testId,
          initialized = true,
          blockStorageDirective = Some(blockStorageDirective),
          parentTea,
          context,
          cucumberBlockingEc,
          cucumberExecutor
        )

      case Failure(ex) =>
        context.log.error(s"Cucumber initialization failed for test $testId", ex)
        throw CucumberException(
          message = s"Failed to initialize Cucumber for test $testId: ${ex.getMessage}",
          cause = Some(ex)
        )
    }
  }

  /**
   * Handle StartTest command: Execute Cucumber scenarios asynchronously
   *
   * Uses PipeToSelf pattern to execute Cucumber on dedicated blocking dispatcher
   * without blocking the actor thread.
   *
   * Execution Flow:
   * 1. Build CucumberConfiguration from BlockStorageDirective
   * 2. Execute cucumberExecutor function in Future on cucumber-blocking-dispatcher
   * 3. PipeToSelf converts Future result to TestExecutionComplete message
   * 4. handleTestExecutionComplete processes the result
   *
   * @param testId UUID of the test
   * @param initialized Whether Initialize was called
   * @param blockStorageDirective Contains jimfsLocation for feature files
   * @param parentTea Reference to parent TestExecutionActor
   * @param context Actor context
   * @param cucumberBlockingEc Dedicated dispatcher for blocking execution
   * @param cucumberExecutor Function to execute Cucumber (injected for testability)
   * @return Updated behavior
   */
  def handleStartTest(
    testId: UUID,
    initialized: Boolean,
    blockStorageDirective: Option[BlockStorageDirective],
    parentTea: ActorRef[TestExecutionCommand],
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[CucumberExecutionCommand],
    cucumberBlockingEc: ExecutionContext,
    cucumberExecutor: (CucumberConfiguration, UUID, BlockStorageDirective) => ExecutionContext ?=> Future[TestExecutionResult]
  ): Behavior[CucumberExecutionCommand] = {
    if !initialized then
      val ex: IllegalStateException = new IllegalStateException(
        s"CucumberExecutionActor not initialized for test $testId - call Initialize before StartTest"
      )
      context.log.error("StartTest called before Initialize", ex)
      throw ex

    if blockStorageDirective.isEmpty then
      val ex: IllegalStateException = new IllegalStateException(
        s"BlockStorageDirective not available for test $testId - Initialize failed"
      )
      context.log.error("BlockStorageDirective is None", ex)
      throw ex

    val setupResult: scala.util.Try[(String, CucumberConfiguration)] = for {
      gluePackage <- scala.util.Try(context.system.settings.config.getString("test-probe.core.cucumber.glue-packages"))
      config <- scala.util.Try(CucumberConfiguration.fromBlockStorageDirective(blockStorageDirective.get, gluePackage))
    } yield (gluePackage, config)

    setupResult match {
      case scala.util.Success((_, config)) =>
        context.log.info(s"Starting Cucumber execution for test $testId")

        val cucumberFuture: Future[TestExecutionResult] =
          cucumberExecutor(config, testId, blockStorageDirective.get)(using cucumberBlockingEc)

        context.pipeToSelf(cucumberFuture) {
          case Success(result) => TestExecutionComplete(Right(result))
          case Failure(ex) => TestExecutionComplete(Left(ex))
        }
        Behaviors.same

      case scala.util.Failure(ex) =>
        context.log.error(s"Failed to start Cucumber execution for test $testId", ex)
        throw CucumberException(
          message = s"Failed to start Cucumber test for $testId: ${ex.getMessage}",
          cause = Some(ex)
        )
    }
  }

  /**
   * Handle TestExecutionComplete: Process async Cucumber execution result
   *
   * Called by pipeToSelf after Future completes.
   * Sends TestComplete to parent TEA with result.
   *
   * @param testId UUID of the test
   * @param result Either[Throwable, TestExecutionResult] from Future
   * @param parentTea Reference to parent TestExecutionActor
   * @param context Actor context
   * @return Updated behavior
   */
  def handleTestExecutionComplete(
    testId: UUID,
    result: Either[Throwable, TestExecutionResult],
    parentTea: ActorRef[TestExecutionCommand],
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[CucumberExecutionCommand]
  ): Behavior[CucumberExecutionCommand] = result match {
    case Right(testResult) =>
      context.log.info(
        s"Cucumber test execution complete for test $testId: " +
        s"${testResult.scenarioCount} scenarios (${testResult.scenariosPassed} passed, ${testResult.scenariosFailed} failed)"
      )
      parentTea ! TestComplete(testId, testResult)
      Behaviors.same

    case Left(throwable) =>
      context.log.error(s"Cucumber test execution failed for test $testId", throwable)
      throw CucumberException(
        message = s"Cucumber execution failed for test $testId: ${throwable.getMessage}",
        cause = Some(throwable)
      )
  }

  /**
   * Handle Stop command: Clean shutdown
   *
   * @param testId UUID of the test
   * @param context Actor context
   * @return Stopped behavior
   */
  def handleStop(
    testId: UUID,
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[CucumberExecutionCommand]
  ): Behavior[CucumberExecutionCommand] = {
    context.log.info(s"Stopping CucumberExecutionActor for test $testId")
    Behaviors.stopped
  }
}
