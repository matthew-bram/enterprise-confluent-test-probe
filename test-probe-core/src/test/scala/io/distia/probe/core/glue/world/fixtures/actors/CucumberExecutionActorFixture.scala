package io.distia.probe.core.glue.world.fixtures.actors

import io.distia.probe.common.models.BlockStorageDirective
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.fixtures.{ServiceInterfaceResponsesFixture, TestHarnessFixtures}
import io.distia.probe.core.models.CucumberExecutionCommands.CucumberExecutionCommand
import io.distia.probe.core.models.{CucumberException, TestExecutionResult}
import io.distia.probe.core.services.cucumber.CucumberConfiguration
import org.apache.pekko.actor.typed.ActorRef

import java.nio.file.FileSystem
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/**
 * Fixture for CucumberExecutionActor component tests.
 *
 * Provides:
 * - Actor reference state management
 * - TestExecutionResult capture
 * - Jimfs lifecycle management
 * - ServiceResponse fixtures (via composition)
 * - TestHarness fixtures (Jimfs, directives via composition)
 *
 * State Variables:
 * - cucumberActor: Actor under test
 * - lastTestExecutionResult: Captured from TestComplete message
 * - scenarioJimfs: In-memory filesystem for feature files (lifecycle: Before → After)
 *
 * Usage Pattern:
 * {{{
 *   class CucumberExecutionActorSteps(world: ActorWorld) {
 *     Before {
 *       world.scenarioJimfs = Some(world.createJimfs())
 *     }
 *
 *     After {
 *       world.scenarioJimfs.foreach(_.close())
 *       world.scenarioJimfs = None
 *     }
 *
 *     // In steps:
 *     world.cucumberActor.foreach { actor => actor ! Initialize(...) }
 *     world.lastTestExecutionResult match {
 *       case Some(result) => result.passed shouldBe true
 *       case None => fail("No result captured")
 *     }
 *   }
 * }}}
 *
 * Pattern: Self-type to ActorWorld, state vars for actor-specific test data
 */
trait CucumberExecutionActorFixture
  extends ServiceInterfaceResponsesFixture
  with TestHarnessFixtures {
  this: ActorWorld =>

  /**
   * CucumberExecutionActor under test.
   */
  var cucumberActor: Option[ActorRef[CucumberExecutionCommand]] = None

  /**
   * Last captured TestExecutionResult from TestComplete message.
   * Used for Then step verification.
   */
  var lastTestExecutionResult: Option[TestExecutionResult] = None

  /**
   * Jimfs filesystem for current scenario.
   * Lifecycle: Created in Before hook, closed in After hook.
   */
  var scenarioJimfs: Option[FileSystem] = None


  val failedCucumberExecution: (CucumberConfiguration, UUID, BlockStorageDirective) => ExecutionContext ?=> Future[TestExecutionResult] = (config, testId, directive) => ec ?=>
    Future.failed(CucumberException())

}
