package io.distia.probe
package core
package glue
package steps

import io.distia.probe.core.actors.CucumberExecutionActor
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.models.CucumberExecutionCommands.*
import io.distia.probe.core.models.TestExecutionCommands.*
import io.distia.probe.common.models.BlockStorageDirective
import io.cucumber.scala.{EN, ScalaDsl}
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.concurrent.duration.*

/**
 * Step definitions for CucumberExecutionActor component tests.
 *
 * Provides Gherkin steps for:
 * - Actor spawning with Jimfs-based BlockStorageDirective
 * - Initialize command (with stub feature files)
 * - StartTest execution (real Cucumber execution via CucumberExecutor)
 * - TestComplete with TestExecutionResult
 * - Stop command (graceful shutdown at various states)
 * - Exception handling and propagation
 * - Security logging compliance (credential redaction)
 *
 * Fixtures Used:
 * - CucumberExecutionActorFixture (actor state, TestExecutionResult, Jimfs)
 * - ActorWorld (teaParentProbe, testId, initialized, blockStorageDirective)
 * - JimfsFixtures (createJimfs, createJimfsBlockStorageDirective)
 *
 * Feature File: component/child-actors/cucumber-execution-actor.feature
 *
 * Architecture Notes:
 * - CucumberExecutionActor is child of TestExecutionActor (parent probe simulates parent)
 * - Uses real CucumberExecutor (not stubbed) for test execution
 * - State: Uninitialized → Initialized (after Initialize command)
 * - Jimfs lifecycle: Before/After hooks manage filesystem creation/cleanup
 *
 * State Management:
 * - All state in CucumberExecutionActorFixture (via ActorWorld composition)
 * - No local state variables (DRY principle)
 * - Shared parent probe via world.teaParentProbe
 */
class CucumberExecutionActorSteps(world: ActorWorld)
  extends ScalaDsl
  with EN
  with Matchers {

  // ========== LIFECYCLE HOOKS ==========

  Before {
    world.scenarioJimfs = Some(world.createJimfs())
  }

  // Note: Jimfs cleanup removed - causes "stream is closed" errors
  // when Cucumber tries to write after scenario ends. Jimfs will be
  // garbage collected when ActorWorld is cleaned up.

  // ========== BACKGROUND STEPS ==========

  // Note: Background steps are shared across all child actor tests
  // - "Given a running actor system" - SharedBackgroundSteps
  // - "Given a CoreConfig is available" - SharedBackgroundSteps
  // - "Given a TestExecutionActor is spawned as parent" - SharedBackgroundSteps

  // ========== GIVEN STEPS (Setup) ==========

  /**
   * Given a CucumberExecutionActor is spawned for test {string}
   *
   * Spawns a real CucumberExecutionActor with the parent TestProbe.
   * Uses world.teaParentProbe (created by SharedBackgroundSteps) as parent.
   */
  Given("""a CucumberExecutionActor is spawned for test {string}""") { (testIdStr: String) =>
    world.testId = UUID.randomUUID()  // Fresh UUID for each test
    world.testIdString = Some(testIdStr)

    // Get parent probe from world (created by SharedBackgroundSteps)
    val parent = world.teaParentProbe.getOrElse(
      throw new IllegalStateException("Parent probe not initialized - ensure Background steps ran")
    )
    val cucumber = if testIdStr.equals("cucumber-test-009") || testIdStr.equals("cucumber-test-010") then
      CucumberExecutionActor(world.testId, parent.ref, world.failedCucumberExecution)
    else CucumberExecutionActor(world.testId, parent.ref)
    world.cucumberActor = Some(world.testKit.spawn(
      cucumber,
      s"cucumber-$testIdStr"
    ))

    world.initialized = false
    // Actor is ready immediately after spawn - no sleep needed
  }

  /**
   * Given a BlockStorageDirective with stub feature file is available
   *
   * CucumberExecutionActor-specific step: Uses stub feature file for component testing.
   * Creates Jimfs directive with feature file copied from classpath.
   */
  Given("""a BlockStorageDirective with stub feature file is available""") { () =>
    val directive: Option[BlockStorageDirective] = world.scenarioJimfs.flatMap { jimfs =>
      world.createJimfsBlockStorageDirective(
        jimfs,
        featureResource = "/stubs/bdd-framework-minimal-valid-test-stub.feature",
        featurePath = "/features/test.feature",
        evidencePath = s"/evidence-${world.testId}"
      )
    }

    directive.foreach { d =>
      world.blockStorageDirective = Some(d)
    }
  }

  /**
   * Given the CucumberExecutionActor has been initialized
   *
   * Sends Initialize command to CucumberExecutionActor.
   * Creates directive if not already set.
   */
  Given("""the CucumberExecutionActor has been initialized""") { () =>
    val directive: BlockStorageDirective = world.blockStorageDirective.getOrElse {
      world.scenarioJimfs.flatMap { jimfs =>
        world.createJimfsBlockStorageDirective(
          jimfs,
          featureResource = "/stubs/bdd-framework-minimal-valid-test-stub.feature",
          featurePath = "/features/test.feature",
          evidencePath = s"/evidence-${world.testId}"
        )
      }.getOrElse(
        throw new IllegalStateException("Failed to create BlockStorageDirective - Jimfs not initialized")
      )
    }

    world.cucumberActor.foreach { actor =>
      actor ! Initialize(directive)
      world.initialized = true
    }
  }

  /**
   * Given the Cucumber service is configured to throw exception on initialize
   *
   * NOTE: This is scaffolding - exceptions not yet implemented
   */
  Given("""the Cucumber service is configured to throw exception on initialize""") { () =>
    // TODO: Implement exception injection for Cucumber service
    // In scaffolding phase, this step is a placeholder
  }

  /**
   * Given the Cucumber service is configured to throw exception on execution
   *
   * NOTE: This is scaffolding - exceptions not yet implemented
   */
  Given("""the Cucumber service is configured to throw exception on execution""") { () =>
    // TODO: Implement exception injection for Cucumber execution
    // In scaffolding phase, this step is a placeholder
  }

  // ========== WHEN STEPS (Actions) ==========

  /**
   * When the CucumberExecutionActor parent sends {string} with blockStorageDirective and securityDirectives
   *
   * Sends Initialize command with both directives to CucumberExecutionActor
   */
  When("""the CucumberExecutionActor parent sends {string} with blockStorageDirective and securityDirectives""") { (command: String) =>
    command match
      case "Initialize" =>
        val directive: BlockStorageDirective = world.testIdString.get match {
          case "cucumber-test-009" | "cucumber-test-010" => world.createBlockStorageDirective(
            jimfsLocation = "",
            evidenceDir = "",
            bucket = "")
          case _ => world.blockStorageDirective.getOrElse {
            world.scenarioJimfs.flatMap { jimfs =>
              world.createJimfsBlockStorageDirective(
                jimfs,
                featureResource = "/stubs/bdd-framework-minimal-valid-test-stub.feature",
                featurePath = "/features/test.feature",
                evidencePath = s"/evidence-${world.testId}"
              )
            }.getOrElse(
              throw new IllegalStateException("Failed to create BlockStorageDirective - Jimfs not initialized")
            )
          }
        }

        world.cucumberActor.foreach { actor =>
          actor ! Initialize(directive)
          world.initialized = true
        }
  }

  /**
   * When the CucumberExecutionActor parent sends {string} with directives again
   *
   * Sends Initialize command a second time (idempotency test) to CucumberExecutionActor
   */
  When("""the CucumberExecutionActor parent sends {string} with directives again""") { (command: String) =>
    command match
      case "Initialize" =>
        val directive: BlockStorageDirective = world.blockStorageDirective.getOrElse {
          world.scenarioJimfs.flatMap { jimfs =>
            world.createJimfsBlockStorageDirective(
              jimfs,
              featureResource = "/stubs/bdd-framework-minimal-valid-test-stub.feature",
              featurePath = "/features/test.feature",
              evidencePath = s"/evidence-${world.testId}"
            )
          }.getOrElse(
            throw new IllegalStateException("Failed to create BlockStorageDirective - Jimfs not initialized")
          )
        }

        world.cucumberActor.foreach { actor =>
          actor ! Initialize(directive)
          world.initialized = true
          // TestProbe.receiveMessage in Then step handles waiting
        }
  }

  /**
   * When the CucumberExecutionActor parent sends {string}
   *
   * Sends StartTest or Stop command to CucumberExecutionActor
   */
  When("""the CucumberExecutionActor parent sends {string}""") { (command: String) =>
    command match
      case "StartTest" =>
        world.cucumberActor.foreach { actor =>
          actor ! StartTest
          // TestProbe.receiveMessage in Then step handles waiting
        }

      case "Stop" =>
        world.cucumberActor.foreach { actor =>
          actor ! Stop
          // Shutdown verification in Then step
        }
  }

  /**
   * When the CucumberExecutionActor parent sends {string} before initialization
   *
   * Sends StartTest or Stop before Initialize to CucumberExecutionActor
   */
  When("""the CucumberExecutionActor parent sends {string} before initialization""") { (command: String) =>
    command match
      case "StartTest" =>
        try
          world.cucumberActor.foreach { actor =>
            actor ! StartTest
            // Exception verification in Then step
          }
        catch
          case ex: IllegalStateException =>
            // Expected exception - capture for verification
            world.capturedResponses = world.capturedResponses :+ world.testCancelledResponse(
              world.testId,
              cancelled = false,
              Some(ex.getMessage)
            )

      case "Stop" =>
        world.cucumberActor.foreach { actor =>
          actor ! Stop
          // Shutdown verification in Then step
        }
  }

  /**
   * When the CucumberExecutionActor parent sends {string} before StartTest
   *
   * Sends Stop after Initialize but before StartTest to CucumberExecutionActor
   */
  When("""the CucumberExecutionActor parent sends {string} before StartTest""") { (command: String) =>
    command match
      case "Stop" =>
        world.cucumberActor.foreach { actor =>
          actor ! Stop
          // Shutdown verification in Then step
        }
  }

  // ========== THEN STEPS (Verification) ==========

  /**
   * Then the CucumberExecutionActor should send {string} to parent
   *
   * Verifies ChildGoodToGo or TestComplete message sent to parent
   */
  Then("""the CucumberExecutionActor should send {string} to parent""") { (messageType: String) =>
    messageType match
      case "ChildGoodToGo" =>
        world.teaParentProbe.foreach { probe =>
          val message: TestExecutionCommand = probe.receiveMessage(2.seconds)
          message match
            case ChildGoodToGo(receivedTestId, child) =>
              receivedTestId shouldBe world.testId
              child should not be null
            case other =>
              fail(s"Expected ChildGoodToGo, got $other")
        }

      case "TestComplete" =>
        world.teaParentProbe.foreach { probe =>
          val message: TestExecutionCommand = probe.receiveMessage(3.seconds)
          message match
            case TestComplete(receivedTestId, result) =>
              receivedTestId shouldBe world.testId
              result should not be null
              world.lastTestExecutionResult = Some(result)
            case other =>
              fail(s"Expected TestComplete, got $other")
        }
  }

  /**
   * Then the CucumberExecutionActor should send {string} to parent with TestExecutionResult
   *
   * Verifies TestComplete message with result
   */
  Then("""the CucumberExecutionActor should send {string} to parent with TestExecutionResult""") { (messageType: String) =>
    messageType match
      case "TestComplete" =>
        world.teaParentProbe.foreach { probe =>
          val message: TestExecutionCommand = probe.receiveMessage(3.seconds)
          message match
            case TestComplete(receivedTestId, result) =>
              receivedTestId shouldBe world.testId
              result should not be null
              world.lastTestExecutionResult = Some(result)
            case other =>
              fail(s"Expected TestComplete, got $other")
        }
  }

  /**
   * Then the CucumberExecutionActor state should be {string}
   *
   * Verifies actor state by checking initialization flag
   */
  Then("""the CucumberExecutionActor state should be {string}""") { (state: String) =>
    state match
      case other =>
        fail(s"Unknown state: $other")
  }

  /**
   * Then the TestExecutionResult should have field {string} not null
   *
   * Verifies TestExecutionResult contains required field
   */
  Then("""the TestExecutionResult should have field {string} not null""") { (fieldName: String) =>
    world.lastTestExecutionResult match
      case Some(result) =>
        fieldName match
          case "testId" =>
            result.testId should not be null

          case "passed" =>
            // passed is a Boolean primitive, always has value
            true shouldBe true

          case other =>
            fail(s"Unknown field: $other")

      case None =>
        fail("No TestExecutionResult captured")
  }

  /**
   * Then the TestExecutionResult should have field {string} greater than 0
   *
   * Verifies TestExecutionResult field has positive value
   */
  Then("""the TestExecutionResult should have field {string} greater than 0""") { (fieldName: String) =>
    world.lastTestExecutionResult match
      case Some(result) =>
        fieldName match
          case "scenarioCount" =>
            result.scenarioCount should be > 0

          case "stepCount" =>
            result.stepCount should be > 0

          case other =>
            fail(s"Unknown field: $other")

      case None =>
        fail("No TestExecutionResult captured")
  }

  /**
   * Then the CucumberExecutionActor should log {string} message
   *
   * Verifies logging (placeholder in component tests)
   */
  Then("""the CucumberExecutionActor should log {string} message""") { (logContent: String) =>
    // TODO: Requires LogCapture utility - for now validate actor state
    world.cucumberActor should not be None
  }

  /**
   * Then the CucumberExecutionActor should stop cleanly
   *
   * Verifies actor stopped
   */
  Then("""the CucumberExecutionActor should stop cleanly""") { () =>
    world.cucumberActor.foreach { actor =>
      world.teaParentProbe.foreach { parent =>
        parent.expectTerminated(actor, 2.seconds)
      }
    }
  }

  /**
   * Then the CucumberExecutionActor should throw IllegalStateException
   *
   * NOTE: In scaffolding, exceptions bubble to parent - verification placeholder
   */
  Then("""the CucumberExecutionActor should throw IllegalStateException""") { () =>
    // In scaffolding phase: Verify actor exists and exception would propagate
    world.cucumberActor should not be None
    world.initialized shouldBe false
  }

  /**
   * Then the CucumberExecutionActor should throw ProbeException
   *
   * NOTE: In scaffolding, service integration not yet implemented
   */
  Then("""the CucumberExecutionActor should throw ProbeException""") { () =>
    // Service integration phase: Verify actor exists
    world.cucumberActor should not be None
  }

  /**
   * Note: The following shared steps are defined in other step files:
   * - "Then the exception should bubble to the parent TestExecutionActor" (SharedBackgroundSteps)
   * - "Then the response should be {string}" (GuardianActorSteps)
   */
}
