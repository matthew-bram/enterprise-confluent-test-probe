package io.distia.probe
package core
package services
package cucumber

import io.cucumber.plugin.ConcurrentEventListener
import io.cucumber.plugin.event.*
import models.TestExecutionResult

import java.time.{Duration, Instant}
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.jdk.CollectionConverters.*

/**
 * Event-based result collector for Cucumber test execution
 *
 * Implements ConcurrentEventListener for thread-safe event handling during Cucumber test execution.
 * Accumulates scenario and step statistics in real-time as Cucumber fires events.
 *
 * Thread Safety:
 * Uses ConcurrentHashMap and ConcurrentLinkedQueue for thread-safe accumulation.
 * Cucumber may fire events from multiple threads during parallel scenario execution.
 *
 * Event Flow:
 * 1. TestRunStarted → Record start time
 * 2. TestCaseFinished (per scenario) → Accumulate scenario results
 * 3. TestStepFinished (per step) → Accumulate step results
 * 4. TestRunFinished → Record end time
 * 5. getResult() → Aggregate all statistics into TestExecutionResult
 *
 * Status Mapping:
 * - PASSED: Scenario/step succeeded
 * - FAILED: Scenario/step failed (assertion or exception)
 * - SKIPPED: Scenario/step skipped (conditional or after failure)
 * - PENDING: Scenario/step marked @Pending
 * - UNDEFINED: Step has no matching step definition
 * - UNUSED: Step not executed (scenario failed earlier)
 * - AMBIGUOUS: Multiple step definitions match (error)
 *
 * Plugin Instantiation:
 * Cucumber instantiates this listener via no-arg constructor when specified as --plugin.
 * The testId is retrieved from TestExecutionListenerRegistry.getTestId (ThreadLocal).
 * 
 */
private[cucumber] class TestExecutionEventListener extends ConcurrentEventListener {

  private val testId: UUID = TestExecutionListenerRegistry.getTestId
  TestExecutionListenerRegistry.registerListener(testId, this)
  private val scenarioResults = new ConcurrentHashMap[String, Status]()
  private val stepResults = new ConcurrentHashMap[String, Status]()
  private val failedScenarios = new ConcurrentLinkedQueue[String]()
  @volatile private var startTime: Instant = _
  @volatile private var endTime: Instant = _
  
  override def setEventPublisher(publisher: EventPublisher): Unit =
    publisher.registerHandlerFor(classOf[TestRunStarted], new EventHandler[TestRunStarted] {
      override def receive(event: TestRunStarted): Unit = handleTestRunStarted(event)
    })
    publisher.registerHandlerFor(classOf[TestCaseFinished], new EventHandler[TestCaseFinished] {
      override def receive(event: TestCaseFinished): Unit = handleTestCaseFinished(event)
    })
    publisher.registerHandlerFor(classOf[TestStepFinished], new EventHandler[TestStepFinished] {
      override def receive(event: TestStepFinished): Unit = handleTestStepFinished(event)
    })
    publisher.registerHandlerFor(classOf[TestRunFinished], new EventHandler[TestRunFinished] {
      override def receive(event: TestRunFinished): Unit = handleTestRunFinished(event)
    })
  
  def handleTestRunStarted(event: TestRunStarted): Unit = startTime = event.getInstant
  
  
  def handleTestCaseFinished(event: TestCaseFinished): Unit =
    val status = event.getResult.getStatus
    val scenarioName = event.getTestCase.getName

    scenarioResults.put(scenarioName, status): Unit

    if status == Status.FAILED then failedScenarios.add(scenarioName): Unit
  
  def handleTestStepFinished(event: TestStepFinished): Unit =
    val status = event.getResult.getStatus
    val stepId: String = s"${event.getTestCase.getName}:${event.getTestStep.getCodeLocation}"

    stepResults.put(stepId, status): Unit
  
  def handleTestRunFinished(event: TestRunFinished): Unit = endTime = event.getInstant
  
  def getResult: TestExecutionResult =
    val scenarios = scenarioResults.values.asScala
    val steps = stepResults.values.asScala

    val duration = if startTime != null && endTime != null then Duration.between(startTime, endTime).toMillis else 0L

    val scenarioCount = scenarios.size
    val scenariosPassed = scenarios.count(_ == Status.PASSED)
    val scenariosFailed = scenarios.count(_ == Status.FAILED)

    val scenariosSkipped = scenarios.count(s => s == Status.SKIPPED || s == Status.PENDING)

    val stepCount = steps.size
    val stepsPassed = steps.count(_ == Status.PASSED)
    val stepsFailed = steps.count(_ == Status.FAILED)

    val stepsSkipped = steps.count(s => s == Status.SKIPPED || s == Status.PENDING || s == Status.UNUSED)

    val stepsUndefined = steps.count(s => s == Status.UNDEFINED || s == Status.AMBIGUOUS)

    val passed = scenarios.nonEmpty && scenarios.forall(_ == Status.PASSED)

    val errorMessage = if failedScenarios.isEmpty then None else
      Some(s"${failedScenarios.size} scenario(s) failed")

    TestExecutionResult(
      testId = testId,
      passed = passed,
      scenarioCount = scenarioCount,
      scenariosPassed = scenariosPassed,
      scenariosFailed = scenariosFailed,
      scenariosSkipped = scenariosSkipped,
      stepCount = stepCount,
      stepsPassed = stepsPassed,
      stepsFailed = stepsFailed,
      stepsSkipped = stepsSkipped,
      stepsUndefined = stepsUndefined,
      durationMillis = duration,
      errorMessage = errorMessage,
      failedScenarios = failedScenarios.asScala.toSeq
    )
}
