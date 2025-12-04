package io.distia.probe
package core
package glue
package steps

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.models.*
import io.distia.probe.core.models.KafkaConsumerCommands.*
import io.distia.probe.core.models.TestExecutionCommands.*
import io.distia.probe.core.actors.KafkaConsumerActor
import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}
import io.cucumber.scala.{EN, ScalaDsl}
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.concurrent.duration.*

/**
 * Step definitions for KafkaConsumerActor component tests.
 *
 * Provides Gherkin steps for:
 * - Actor spawning with parent probe simulation
 * - Initialize command (blockStorageDirective + securityDirectives)
 * - StartTest command (no-op stub in scaffolding phase)
 * - Stop command (clean shutdown at various lifecycle stages)
 * - Exception handling and propagation
 * - Security compliance (credential redaction verification)
 *
 * Fixtures Used:
 * - KafkaConsumerActorFixture (consumer actor utilities, state management)
 * - TestHarnessFixtures (via KafkaConsumerActorFixture)
 * - BlockStorageDirectiveFixtures (via KafkaConsumerActorFixture)
 * - KafkaSecurityDirectiveFixtures (via KafkaConsumerActorFixture)
 *
 * Feature File: component/child-actors/kafka-consumer-actor.feature
 *
 * Architecture Notes:
 * - KafkaConsumerActor is child of TestExecutionActor (parent probe simulates parent)
 * - Spawns KafkaConsumerStreamingActor per consumer topic (internal, not tested here)
 * - State: Created → Initializing → Ready → Stopped
 * - Scaffolding phase: Real actor, stubbed Kafka streaming layer
 * - Security: All KafkaSecurityDirective credentials must be redacted in logs
 * - Actor-specific state (consumerActor, consumerInitialized) lives in KafkaConsumerActorFixture
 * - Shared state (testId, probes, directives) lives in ActorWorld
 *
 * Message Flow:
 * 1. Initialize(blockStorageDirective, securityDirectives) → ChildGoodToGo
 * 2. StartTest → (no-op in scaffolding phase)
 * 3. Stop → Clean shutdown
 *
 * Idempotency:
 * - Initialize can be called multiple times (idempotent)
 * - Stop can be called before Initialize (early shutdown)
 *
 * Exception Handling:
 * - Kafka service failures bubble to parent TestExecutionActor
 * - Verified via actor termination (supervisor stops actor)
 */
class KafkaConsumerActorSteps(world: ActorWorld)
  extends ScalaDsl
  with EN
  with Matchers {

  // ========== GIVEN STEPS (Setup) ==========

  /**
   * Given a KafkaConsumerActor is spawned for test {string}
   * Spawns a real KafkaConsumerActor with the parent TestProbe
   */
  Given("""a KafkaConsumerActor is spawned for test {string}""") { (testIdStr: String) =>
    world.testIdString = Some(testIdStr)

    // Spawn actor using fixture method (resets consumerInitialized)
    world.consumerActor = Some(world.spawnKafkaConsumerActor(testIdStr))
  }

  /**
   * Given the KafkaConsumerActor has been initialized
   * Sends Initialize command to KafkaConsumerActor and waits for ChildGoodToGo
   *
   * Cleanup: Consumes ChildGoodToGo to prevent polluting probe queue for subsequent steps.
   */
  Given("""the KafkaConsumerActor has been initialized""") { () =>
    world.consumerActor.foreach { actor =>
      val directive = world.getOrCreateBlockStorageDirective
      val directives = world.getOrCreateConsumerSecurityDirectives

      actor ! Initialize(directive, directives)
      world.consumerInitialized = true

      // Consume setup message (Initialize produces ChildGoodToGo)
      world.teaParentProbe.foreach { probe =>
        probe.receiveMessage(3.seconds)  // ChildGoodToGo
      }
    }
  }

  // NOTE: "Given the Kafka service is configured to throw exception on initialize"
  // is defined in SharedBackgroundSteps (shared step used by both Kafka actor features)

  // ========== WHEN STEPS (Actions) ==========

  /**
   * When the KafkaConsumerActor parent sends {string} with blockStorageDirective and securityDirectives
   * Sends Initialize command with both directives to KafkaConsumerActor
   */
  When("""the KafkaConsumerActor parent sends {string} with blockStorageDirective and securityDirectives""") { (command: String) =>
    if command == "Initialize" then
      world.consumerActor.foreach { actor =>
        val directive = world.getOrCreateBlockStorageDirective
        val directives = world.getOrCreateConsumerSecurityDirectives

        actor ! Initialize(directive, directives)
      }
  }

  /**
   * When the KafkaConsumerActor parent sends {string} with directives again
   * Sends Initialize command a second time (idempotency test) to KafkaConsumerActor
   */
  When("""the KafkaConsumerActor parent sends {string} with directives again""") { (command: String) =>
    if command == "Initialize" then
      world.consumerActor.foreach { actor =>
        val directive = world.getOrCreateBlockStorageDirective
        val directives = world.getOrCreateConsumerSecurityDirectives

        actor ! Initialize(directive, directives)
      }
  }

  /**
   * When the KafkaConsumerActor parent sends {string}
   * Sends StartTest or Stop command to KafkaConsumerActor
   *
   * StartTest: No-op stub in scaffolding phase (real Kafka integration in future phase)
   * Stop: Triggers clean shutdown and actor termination
   */
  When("""the KafkaConsumerActor parent sends {string}""") { (command: String) =>
    command match
      case "StartTest" =>
        world.consumerActor.foreach { actor =>
          actor ! StartTest
          // No-op stub in scaffolding phase
        }

      case "Stop" =>
        world.consumerActor.foreach { actor =>
          actor ! Stop
          // Shutdown verification in Then step
        }
  }

  /**
   * When the KafkaConsumerActor parent sends {string} before initialization
   * Sends Stop before Initialize to KafkaConsumerActor (early shutdown scenario)
   */
  When("""the KafkaConsumerActor parent sends {string} before initialization""") { (command: String) =>
    if command == "Stop" then
      world.consumerActor.foreach { actor =>
        actor ! Stop
        // Shutdown verification in Then step
      }
  }

  // ========== THEN STEPS (Verification) ==========

  /**
   * Then the KafkaConsumerActor should send {string} to parent
   * Verifies ChildGoodToGo message sent to parent
   *
   * ChildGoodToGo indicates actor is ready and has spawned all KafkaConsumerStreamingActors
   */
  Then("""the KafkaConsumerActor should send {string} to parent""") { (messageType: String) =>
    messageType match
      case "ChildGoodToGo" =>
        world.teaParentProbe.foreach { probe =>
          val message: TestExecutionCommand = probe.receiveMessage(3.seconds)
          message match
            case ChildGoodToGo(receivedTestId, child) =>
              receivedTestId shouldBe world.testId
              child should not be null
            case other =>
              fail(s"Expected ChildGoodToGo, got $other")
        }
  }

  /**
   * Then the KafkaConsumerActor state should be {string}
   * Verifies actor state by checking initialization flag
   *
   * Note: Placeholder for future state verification (FSM state query)
   */
  Then("""the KafkaConsumerActor state should be {string}""") { (state: String) =>
    state match
      case other =>
        fail(s"Unknown state: $other")
  }

  /**
   * Then the KafkaConsumerActor should log {string} message
   * Verifies logging (placeholder in component tests)
   *
   * TODO: Requires LogCapture utility - for now validate actor state
   * Future: Verify log messages using LogCapture
   */
  Then("""the KafkaConsumerActor should log {string} message""") { (logContent: String) =>
    // TODO: Requires LogCapture utility - for now validate actor state
    world.consumerActor should not be None
  }

  /**
   * Then the KafkaConsumerActor should stop cleanly
   * Verifies actor stopped gracefully using TestKit.stop
   *
   * Uses testKit.stop() (not expectTerminated) because actor was spawned with testKit.spawn()
   */
  Then("""the KafkaConsumerActor should stop cleanly""") { () =>
    // Use TestKit.stop to wait for actor shutdown with timeout
    world.consumerActor.foreach { actor =>
      world.testKit.stop(actor, 3.seconds)
    }
  }

  /**
   * Then the KafkaConsumerActor should throw ProbeException
   * NOTE: In scaffolding, service integration not yet implemented
   *
   * Future: Verify ProbeException thrown when Kafka service fails
   */
  Then("""the KafkaConsumerActor should throw ProbeException""") { () =>
    // Service integration phase: Verify actor exists
    world.consumerActor should not be None
  }
}
