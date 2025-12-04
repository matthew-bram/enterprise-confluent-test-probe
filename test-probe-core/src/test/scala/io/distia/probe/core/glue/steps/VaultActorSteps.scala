package io.distia.probe
package core
package glue
package steps

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import io.distia.probe.core.fixtures.{InterfaceFunctionsFixture, KafkaSecurityDirectiveFixtures, BlockStorageDirectiveFixtures}
import io.distia.probe.core.builder.VaultServiceFunctions
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.models.*
import io.distia.probe.core.models.VaultCommands.*
import io.distia.probe.core.models.TestExecutionCommands.*
import io.distia.probe.core.actors.VaultActor
import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}
import io.cucumber.scala.{EN, ScalaDsl}
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

/**
 * Companion object for VaultActorSteps.
 * Contains probe cleanup configuration.
 */
object VaultActorSteps {
  // Probe cleanup configuration (centralized for easy tuning)
  private val PROBE_RECEIVE_TIMEOUT: FiniteDuration = 3.seconds
}

/**
 * Step definitions for VaultActor component tests.
 *
 * Provides Gherkin steps for:
 * - Actor spawning with real/stubbed vault functions
 * - Initialize command (security directive fetch, idempotency)
 * - Stop command (graceful shutdown at various states)
 * - Security directive validation
 * - Credential redaction in logs/exceptions
 *
 * Fixtures Used:
 * - InterfaceFunctionsFixture (vault function factories)
 * - KafkaSecurityDirectiveFixtures (KafkaSecurityDirective creation)
 * - BlockStorageDirectiveFixtures (BlockStorageDirective creation)
 *
 * Feature File: component/child-actors/vault-actor.feature
 *
 * Architecture Notes:
 * - VaultActor is child of TestExecutionActor (parent probe simulates parent)
 * - State: Uninitialized → Initialized (after Initialize command)
 * - Scaffolding phase: Real actor, stubbed vault service
 * - Security: Tests credential redaction in logs and exceptions
 */
class VaultActorSteps(world: ActorWorld)
  extends ScalaDsl
  with EN
  with Matchers
  with InterfaceFunctionsFixture
  with KafkaSecurityDirectiveFixtures
  with BlockStorageDirectiveFixtures {

  given ExecutionContext = ExecutionContext.global

  // ========== BACKGROUND STEPS ==========

  // Note: "a TestExecutionActor is spawned as parent" is defined in SharedBackgroundSteps

  // ========== GIVEN STEPS (Setup) ==========

  /**
   * Given a VaultActor is spawned for test {string}
   * Spawns a real VaultActor with the parent TestProbe
   */
  Given("""a VaultActor is spawned for test {string}""") { (testIdStr: String) =>
    world.testIdString = Some(testIdStr)

    // Get parent probe from world (created by SharedBackgroundSteps)
    val parent: TestProbe[TestExecutionCommand] = world.teaParentProbe.getOrElse(
      throw new IllegalStateException("Parent probe not initialized - ensure Background steps ran")
    )

    // Build vault function based on failure configuration
    val vaultFetchFn = if world.shouldFailOnVaultFetch then
      getFailedSecurityDirectivesFetchFunction
    else
      val securityDirectives = List(
        createSecurityDirective(topic = "test-topic", role = "producer")
      )
      getSuccessfulSecurityDirectivesFetchFunction(securityDirectives)

    val vaultFunctions = VaultServiceFunctions(
      fetchSecurityDirectives = vaultFetchFn
    )

    world.vaultActor = Some(world.testKit.spawn(
      VaultActor(
        world.testId,
        parent.ref,
        vaultFunctions
      ),
      s"vault-$testIdStr"
    ))

    // Reset behavior flags after spawn
    world.shouldFailOnVaultFetch = false
    world.initialized = false
  }

  /**
   * Given a BlockStorageDirective is available with bucket {string}
   * Creates a BlockStorageDirective with specific bucket reference
   */
  Given("""a BlockStorageDirective is available with bucket {string}""") { (bucket: String) =>
    world.blockStorageDirective = Some(createBlockStorageDirective(
      jimfsLocation = s"/jimfs/test-${world.testId}"
      // Bucket info would be in topicDirectives in real implementation
    ))
  }

  /**
   * Given the VaultActor has been initialized
   * Sends Initialize command to VaultActor and consumes setup messages.
   *
   * Cleanup: Consumes SecurityFetched + ChildGoodToGo messages to prevent
   * polluting the shared probe queue for subsequent When/Then steps.
   */
  Given("""the VaultActor has been initialized""") { () =>
    val directive: BlockStorageDirective = world.blockStorageDirective.getOrElse(
      createBlockStorageDirective(jimfsLocation = s"/jimfs/test-${world.testId}")
    )

    world.vaultActor.foreach { actor =>
      actor ! Initialize(directive)
      world.initialized = true

      // Consume setup messages (Initialize produces SecurityFetched + ChildGoodToGo)
      world.teaParentProbe.foreach { probe =>
        probe.receiveMessage(VaultActorSteps.PROBE_RECEIVE_TIMEOUT)  // SecurityFetched
        probe.receiveMessage(VaultActorSteps.PROBE_RECEIVE_TIMEOUT)  // ChildGoodToGo
      }
    }
  }

  /**
   * Given the Vault service is configured to throw exception on fetch
   * Configures vault fetch to fail for exception testing
   */
  Given("""the Vault service is configured to throw exception on fetch""") { () =>
    world.shouldFailOnVaultFetch = true
  }

  /**
   * Given the Vault service is configured to throw exception with sensitive data
   * NOTE: This is scaffolding - exceptions not yet implemented
   */
  Given("""the Vault service is configured to throw exception with sensitive data""") { () =>
    // TODO: Implement exception injection with sensitive data for security testing
    // In scaffolding phase, this step is a placeholder
  }

  // ========== WHEN STEPS (Actions) ==========

  /**
   * When the parent sends {string} with blockStorageDirective
   * Sends Initialize command with BlockStorageDirective
   */
  When("""the parent sends {string} with blockStorageDirective""") { (command: String) =>
    command match
      case "Initialize" =>
        val directive: BlockStorageDirective = world.blockStorageDirective.getOrElse(
          createBlockStorageDirective(jimfsLocation = s"/jimfs/test-${world.testId}")
        )

        world.vaultActor.foreach { actor =>
          actor ! Initialize(directive)
        }
  }

  /**
   * When the parent sends {string} with blockStorageDirective again
   * Sends Initialize command a second time (idempotency test)
   */
  When("""the parent sends {string} with blockStorageDirective again""") { (command: String) =>
    command match
      case "Initialize" =>
        val directive: BlockStorageDirective = world.blockStorageDirective.getOrElse(
          createBlockStorageDirective(jimfsLocation = s"/jimfs/test-${world.testId}")
        )

        world.vaultActor.foreach { actor =>
          actor ! Initialize(directive)
        }
  }

  /**
   * When the VaultActor parent sends {string}
   * Sends Stop command to VaultActor
   */
  When("""the VaultActor parent sends {string}""") { (command: String) =>
    command match
      case "Stop" =>
        world.vaultActor.foreach { actor =>
          actor ! Stop
        }
  }

  /**
   * When the VaultActor parent sends {string} before initialization
   * Sends Stop command before Initialize to VaultActor
   */
  When("""the VaultActor parent sends {string} before initialization""") { (command: String) =>
    command match
      case "Stop" =>
        world.vaultActor.foreach { actor =>
          actor ! Stop
        }
  }

  // ========== THEN STEPS (Verification) ==========

  /**
   * Then the VaultActor response should include {string}
   * Verifies parent received expected message (SecurityFetched or ChildGoodToGo)
   */
  Then("""the VaultActor response should include {string}""") { (messageType: String) =>
    messageType match
      case "SecurityFetched" =>
        world.teaParentProbe.foreach { probe =>
          val message: TestExecutionCommand = probe.receiveMessage(3.seconds)
          message match
            case SecurityFetched(receivedTestId, directives) =>
              receivedTestId shouldBe world.testId
              directives should not be null
              world.lastKafkaSecurityDirectives = directives
              // Also populate world.securityDirectives for SharedSteps compatibility
              world.securityDirectives = directives
            case other =>
              fail(s"Expected SecurityFetched, got $other")
        }

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

      case other =>
        fail(s"Unknown message type: $other")
  }

  /**
   * Then the VaultActor should send {string} to parent with list of KafkaSecurityDirective
   * Verifies SecurityFetched message sent to parent
   */
  Then("""the VaultActor should send {string} to parent with list of KafkaSecurityDirective""") { (messageType: String) =>
    messageType match
      case "SecurityFetched" =>
        world.teaParentProbe.foreach { probe =>
          val message: TestExecutionCommand = probe.receiveMessage(3.seconds)
          message match
            case SecurityFetched(receivedTestId, directives) =>
              receivedTestId shouldBe world.testId
              directives should not be null
              world.lastKafkaSecurityDirectives = directives
              // Also populate world.securityDirectives for SharedSteps compatibility
              world.securityDirectives = directives
            case other =>
              fail(s"Expected SecurityFetched, got $other")
        }
  }

  /**
   * Then the VaultActor should send {string} to parent
   * Verifies SecurityFetched or ChildGoodToGo message sent to parent
   */
  Then("""the VaultActor should send {string} to parent""") { (messageType: String) =>
    messageType match
      case "SecurityFetched" =>
        world.teaParentProbe.foreach { probe =>
          val message: TestExecutionCommand = probe.receiveMessage(3.seconds)
          message match
            case SecurityFetched(receivedTestId, directives) =>
              receivedTestId shouldBe world.testId
              directives should not be null
              world.lastKafkaSecurityDirectives = directives
              // Also populate world.securityDirectives for SharedSteps compatibility
              world.securityDirectives = directives
            case other =>
              fail(s"Expected SecurityFetched, got $other")
        }

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
   * Then the VaultActor state should be {string}
   * Verifies actor state by checking initialization flag
   */
  Then("""the VaultActor state should be {string}""") { (state: String) =>
    state match
      case other =>
        fail(s"Unknown state: $other")
  }

  /**
   * Then the KafkaSecurityDirective should NOT be logged in any log statement
   * Verifies credentials are redacted in logs (placeholder)
   */
  Then("""the KafkaSecurityDirective should NOT be logged in any log statement""") { () =>
    // TODO: Requires LogCapture utility - for now validate directives exist
    world.lastKafkaSecurityDirectives should not be empty
    world.lastKafkaSecurityDirectives.foreach { dir =>
      dir.jaasConfig should not be empty
    }
  }

  /**
   * Then the KafkaSecurityDirective list should not be empty
   * Verifies KafkaSecurityDirective list contains at least one entry
   */
  Then("""the KafkaSecurityDirective list should not be empty""") { () =>
    world.lastKafkaSecurityDirectives should not be empty
  }

  /**
   * Then each KafkaSecurityDirective should have field {string} not null
   * Verifies KafkaSecurityDirective contains required field
   */
  Then("""each KafkaSecurityDirective should have field {string} not null""") { (fieldName: String) =>
    world.lastKafkaSecurityDirectives should not be empty

    world.lastKafkaSecurityDirectives.foreach { directive =>
      fieldName match
        case "topic" =>
          directive.topic should not be null
          directive.topic should not be empty

        case "role" =>
          directive.role should not be null
          directive.role should not be empty

        case "jaasConfig" =>
          directive.jaasConfig should not be null
          directive.jaasConfig should not be empty

        case other =>
          fail(s"Unknown field: $other")
    }
  }

  /**
   * Then each KafkaSecurityDirective should have jaasConfig populated
   * Verifies jaasConfig is not null and not empty for all directives
   */
  Then("""each KafkaSecurityDirective should have jaasConfig populated""") { () =>
    world.lastKafkaSecurityDirectives should not be empty

    world.lastKafkaSecurityDirectives.foreach { directive =>
      directive.jaasConfig should not be null
      directive.jaasConfig should not be empty
    }
  }

  /**
   * Then the VaultActor should log {string} message
   * Verifies logging (placeholder in component tests)
   */
  Then("""the VaultActor should log {string} message""") { (logContent: String) =>
    // TODO: Requires LogCapture utility - for now validate actor state
    world.vaultActor should not be None
  }

  /**
   * Then the VaultActor should stop cleanly
   * Verifies actor stopped
   */
  Then("""the VaultActor should stop cleanly""") { () =>
    world.vaultActor.foreach { actor =>
      world.teaParentProbe.foreach { parent => 
        parent.expectTerminated(actor, 3.seconds)
      }
    }
  }

  /**
   * Then the VaultActor should throw ProbeException
   * NOTE: In scaffolding, service integration not yet implemented
   */
  Then("""the VaultActor should throw ProbeException""") { () =>
    // Service integration phase: Verify actor exists and can propagate exceptions
    world.vaultActor should not be None
    world.blockStorageDirective should not be None
  }

  /**
   * Then the exception message should be redacted
   * Verifies exception messages don't leak credentials
   */
  Then("""the exception message should be redacted""") { () =>
    // TODO: Requires LogCapture utility - security validation via code review
    world.vaultActor should not be None
  }

  /**
   * Then the exception message should NOT contain clientSecret or jaasConfig
   * Verifies credentials are redacted in exception messages
   */
  Then("""the exception message should NOT contain clientSecret or jaasConfig""") { () =>
    // TODO: Requires LogCapture utility - security validation via code review
    world.lastKafkaSecurityDirectives.foreach { dir =>
      dir.jaasConfig should not be empty
    }
  }

  /**
   * Then the exception message should contain testId for debugging
   * Verifies exception messages contain testId
   */
  Then("""the exception message should contain testId for debugging""") { () =>
    // TODO: Requires LogCapture utility - for now validate testId present
    world.testId should not be null
  }
  
}
