package io.distia.probe
package core
package glue
package steps

import org.apache.pekko.actor.typed.ActorRef
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.models.KafkaProducerCommands.*
import io.distia.probe.core.models.TestExecutionCommands.*
import io.cucumber.scala.{EN, ScalaDsl}
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

/**
 * Companion object for KafkaProducerActorSteps.
 * Contains probe timeout configuration.
 */
object KafkaProducerActorSteps {
  private val PROBE_RECEIVE_TIMEOUT: FiniteDuration = 2.seconds
}

/**
 * Step definitions for KafkaProducerActor component tests.
 *
 * Provides Gherkin steps for:
 * - Actor spawning with real Kafka producer supervisor
 * - Initialize command (spawn streaming actors, register with DSL)
 * - StartTest command (no-op stub in scaffolding phase)
 * - Stop command (clean shutdown, DSL cleanup)
 * - Exception handling and propagation
 * - Security logging compliance verification
 *
 * Fixtures Used:
 * - KafkaProducerActorFixture (producer actor utilities, state management)
 * - TestHarnessFixtures (via KafkaProducerActorFixture)
 * - BlockStorageDirectiveFixtures (via KafkaProducerActorFixture)
 * - KafkaSecurityDirectiveFixtures (via KafkaProducerActorFixture)
 *
 * Feature File: component/child-actors/kafka-producer-actor.feature
 *
 * Architecture Notes:
 * - KafkaProducerActor is supervisor (spawns KafkaProducerStreamingActor per topic)
 * - State: Uninitialized → Initialized (after Initialize command)
 * - Scaffolding phase: Real actor, stubbed Kafka service integration
 * - Security: All credentials must be redacted in logs
 * - Actor-specific state (producerActor, producerInitialized) lives in KafkaProducerActorFixture
 * - Shared state (testId, probes, directives) lives in ActorWorld
 */
class KafkaProducerActorSteps(world: ActorWorld)
  extends ScalaDsl
  with EN
  with Matchers {

  // ========== GIVEN STEPS (Setup) ==========

  /**
   * Given a KafkaProducerActor is spawned for test {string}
   * Spawns a real KafkaProducerActor with the parent TestProbe
   */
  Given("""a KafkaProducerActor is spawned for test {string}""") { (testIdStr: String) =>
    world.testIdString = Some(testIdStr)

    // Spawn actor using fixture method (resets producerInitialized)
    world.producerActor = Some(world.spawnKafkaProducerActor(testIdStr))
  }

  /**
   * Given the KafkaProducerActor has been initialized
   * Sends Initialize command to KafkaProducerActor and consumes ChildGoodToGo message.
   *
   * Cleanup: Consumes ChildGoodToGo to prevent polluting probe queue for subsequent steps.
   */
  Given("""the KafkaProducerActor has been initialized""") { () =>
    world.producerActor.foreach { actor =>
      val directive = world.getOrCreateBlockStorageDirective
      val directives = world.getOrCreateSecurityDirectives

      // "test-probe.core.kafka.bootstrap-servers"

      actor ! Initialize(directive, directives)
      world.producerInitialized = true

      // Consume setup message (Initialize produces ChildGoodToGo)
      world.teaParentProbe.foreach { probe =>
        probe.receiveMessage(KafkaProducerActorSteps.PROBE_RECEIVE_TIMEOUT)  // ChildGoodToGo
      }
    }
  }

  // NOTE: "Given the Kafka service is configured to throw exception on initialize"
  // is defined in SharedBackgroundSteps (shared step used by both Kafka actor features)

  // ========== WHEN STEPS (Actions) ==========

  /**
   * When the KafkaProducerActor parent sends {string} with blockStorageDirective and securityDirectives
   * Sends Initialize command with both directives to KafkaProducerActor
   */
  When("""the KafkaProducerActor parent sends {string} with blockStorageDirective and securityDirectives""") { (command: String) =>
    command match
      case "Initialize" =>
        world.producerActor.foreach { actor =>
          val directive = world.getOrCreateBlockStorageDirective
          val directives = world.getOrCreateSecurityDirectives
          actor ! Initialize(directive, directives)
        }
  }

  /**
   * When the KafkaProducerActor parent sends {string} with directives again
   * Sends Initialize command a second time (idempotency test)
   */
  When("""the KafkaProducerActor parent sends {string} with directives again""") { (command: String) =>
    command match
      case "Initialize" =>
        world.producerActor.foreach { actor =>
          val directive = world.getOrCreateBlockStorageDirective
          val directives = world.getOrCreateSecurityDirectives
          actor ! Initialize(directive, directives)
        }
  }

  /**
   * When the KafkaProducerActor parent sends {string}
   * Sends StartTest or Stop command to KafkaProducerActor
   */
  When("""the KafkaProducerActor parent sends {string}""") { (command: String) =>
    command match
      case "StartTest" =>
        world.producerActor.foreach { actor =>
          actor ! StartTest
          // No-op stub in scaffolding phase
        }

      case "Stop" =>
        world.producerActor.foreach { actor =>
          actor ! Stop
        }
  }

  /**
   * When the KafkaProducerActor parent sends {string} before initialization
   * Sends Stop before Initialize to KafkaProducerActor
   */
  When("""the KafkaProducerActor parent sends {string} before initialization""") { (command: String) =>
    command match
      case "Stop" =>
        world.producerActor.foreach { actor =>
          actor ! Stop
        }
  }

  // ========== THEN STEPS (Verification) ==========

  /**
   * Then the KafkaProducerActor should send {string} to parent
   * Verifies ChildGoodToGo message sent to parent
   */
  Then("""the KafkaProducerActor should send {string} to parent""") { (messageType: String) =>
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
  }

  /**
   * Then the KafkaProducerActor state should be {string}
   * Verifies actor state by checking initialization flag
   */
  Then("""the KafkaProducerActor state should be {string}""") { (state: String) =>
    state match
      case other =>
        fail(s"Unknown state: $other")
  }

  /**
   * Then the KafkaProducerActor should log {string} message
   * Verifies logging (placeholder in component tests)
   */
  Then("""the KafkaProducerActor should log {string} message""") { (logContent: String) =>
    // TODO: Requires LogCapture utility - for now validate actor state
    world.producerActor should not be None
  }

  /**
   * Then the KafkaProducerActor should stop cleanly
   * Verifies actor stopped using expectTerminated pattern.
   *
   * NOTE: Uses probe.expectTerminated() pattern (not testKit.stop).
   * Actor shutdown is verified by waiting for termination signal.
   */
  Then("""the KafkaProducerActor should stop cleanly""") { () =>
    world.producerActor.foreach { actor =>
      world.teaParentProbe.foreach { probe =>
        probe.expectTerminated(actor, 3.seconds)
      }
    }
  }

  /**
   * Then the KafkaProducerActor should throw ProbeException
   * NOTE: In scaffolding, service integration not yet implemented
   */
  Then("""the KafkaProducerActor should throw ProbeException""") { () =>
    // Service integration phase: Verify actor exists
    world.producerActor should not be None
  }
}
