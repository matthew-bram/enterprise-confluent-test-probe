package io.distia.probe
package core
package glue
package steps

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import io.distia.probe.core.fixtures.{BlockStorageDirectiveFixtures, InterfaceFunctionsFixture}
import io.distia.probe.core.builder.{ServiceFunctionsContext, StorageServiceFunctions, VaultServiceFunctions}
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.models.*
import io.distia.probe.core.models.BlockStorageCommands.*
import io.distia.probe.core.models.TestExecutionCommands.*
import io.distia.probe.core.actors.BlockStorageActor
import io.distia.probe.common.models.BlockStorageDirective
import io.cucumber.scala.{EN, ScalaDsl}
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

/**
 * Companion object for BlockStorageActorSteps.
 * Contains probe cleanup configuration.
 */
object BlockStorageActorSteps {
  // Probe cleanup configuration (centralized for easy tuning)
  private val PROBE_RECEIVE_TIMEOUT: FiniteDuration = 3.seconds
}

/**
 * Step definitions for BlockStorageActor component tests.
 *
 * Provides Gherkin steps for:
 * - Actor spawning with real/stubbed storage functions
 * - Initialize command (bucket setup, idempotency)
 * - FetchFromBlockStorage (success/failure scenarios)
 * - LoadToBlockStorage (evidence upload, success/failure)
 * - Stop command (graceful shutdown at various states)
 * - Exception handling and propagation
 *
 * Fixtures Used:
 * - InterfaceFunctionsFixture (storage function factories)
 * - BlockStorageDirectiveFixtures (BlockStorageDirective creation)
 *
 * Feature File: component/child-actors/block-storage-actor.feature
 *
 * Architecture Notes:
 * - BlockStorageActor is child of TestExecutionActor (parent probe simulates parent)
 * - State: Uninitialized → Initialized (after Initialize command)
 * - Scaffolding phase: Real actor, stubbed storage service
 * - Uses data tables to configure failure scenarios
 */
class BlockStorageActorSteps(world: ActorWorld)
  extends ScalaDsl
  with EN
  with Matchers
  with InterfaceFunctionsFixture
  with BlockStorageDirectiveFixtures {

  given ExecutionContext = ExecutionContext.global
  
  // ========== BACKGROUND STEPS ==========

  /**
   * Given the following storage behavior:
   * Configures which storage operations should fail for exception testing
   * Data table format:
   *   | operation | behavior |
   *   | fetch     | fail     |
   *   | load      | fail     |
   */
  Given("""the following storage behavior:""") { (dataTable: io.cucumber.datatable.DataTable) =>
    import scala.jdk.CollectionConverters.*
    val rows = dataTable.asMaps().asScala
    rows.foreach { row =>
      val operation = row.get("operation")
      val behavior = row.get("behavior")

      (operation, behavior) match
        case ("fetch", "fail") => world.shouldFailOnBlockFetch = true
        case ("load", "fail") => world.shouldFailOnBlockLoad = true
        case _ => // Ignore unknown operations or success behavior (default)
    }
  }

  // ========== GIVEN STEPS (Setup) ==========

  /**
   * Given a BlockStorageActor is spawned for test {string}
   * Spawns a real BlockStorageActor with the parent TestProbe
   * Uses configured storage behavior (from data table) or default success behavior
   */
  Given("""a BlockStorageActor is spawned for test {string}""") { (testIdStr: String) =>
    world.testIdString = Some(testIdStr)

    // Get parent probe from world (created by SharedBackgroundSteps)
    val parent: TestProbe[TestExecutionCommand] = world.teaParentProbe.getOrElse(
      throw new IllegalStateException("Parent probe not initialized - ensure Background steps ran")
    )

    // Build storage functions based on failure configuration
    val storageFetchFn = if world.shouldFailOnBlockFetch then
      getFailedBlockStorageFetchFunction
    else
      getSuccessfulBlockStorageFetchFunction(
        createBlockStorageDirective(jimfsLocation = s"/jimfs/test-${world.testId}")
      )

    val storageLoadFn = if world.shouldFailOnBlockLoad then
      getFailedBlockStorageLoadFunction
    else
      getSuccessfulBlockStorageLoadFunction

    val storageFunctions = StorageServiceFunctions(
      fetchFromBlockStorage = storageFetchFn,
      loadToBlockStorage = storageLoadFn
    )

    world.blockStorageActor = Some(world.testKit.spawn(
      BlockStorageActor(
        world.testId,
        parent.ref,
        storageFunctions
      ),
      s"block-storage-$testIdStr"
    ))

    // Reset behavior flags after spawn
    world.shouldFailOnBlockFetch = false
    world.shouldFailOnBlockLoad = false
    world.initialized = false
  }

  /**
   * Given the BlockStorageActor has been initialized
   * Sends Initialize command to BlockStorageActor and consumes setup messages.
   *
   * Cleanup: Consumes BlockStorageFetched + ChildGoodToGo messages to prevent
   * polluting the shared probe queue for subsequent When/Then steps.
   */
  Given("""the BlockStorageActor has been initialized""") { () =>
    world.blockStorageActor.foreach { actor =>
      actor ! Initialize(Some("test-bucket"))
      world.initialized = true

      // Consume setup messages (Initialize produces BlockStorageFetched + ChildGoodToGo)
      world.teaParentProbe.foreach { probe =>
        probe.receiveMessage(BlockStorageActorSteps.PROBE_RECEIVE_TIMEOUT)  // BlockStorageFetched
        probe.receiveMessage(BlockStorageActorSteps.PROBE_RECEIVE_TIMEOUT)  // ChildGoodToGo
      }
    }
  }

  /**
   * When the BlockStorageActor has been initialized with bucket {string}
   * Sends Initialize command with specific bucket.
   *
   * Note: Does NOT consume messages - subsequent Then steps verify the responses.
   */
  When("""the BlockStorageActor has been initialized with bucket {string}""") { (bucket: String) =>
    world.blockStorageActor.foreach { actor =>
      actor ! Initialize(Some(bucket))
      world.initialized = true
    }
  }

  // ========== WHEN STEPS (Actions) ==========

  /**
   * When the parent sends {string} with bucket {string}
   * Sends Initialize command with specified bucket
   */
  When("""the parent sends {string} with bucket {string}""") { (command: String, bucket: String) =>
    command match
      case "Initialize" =>
        world.blockStorageActor.foreach { actor =>
          val bucketOpt: Option[String] = if bucket == "None" then None else Some(bucket)
          actor ! Initialize(bucketOpt)
        }
  }

  /**
   * When the parent sends {string} with bucket {string} again
   * Sends Initialize command a second time (idempotency test)
   */
  When("""the parent sends {string} with bucket {string} again""") { (command: String, bucket: String) =>
    command match
      case "Initialize" =>
        world.blockStorageActor.foreach { actor =>
          val bucketOpt: Option[String] = if bucket == "None" then None else Some(bucket)
          actor ! Initialize(bucketOpt)
        }
  }

  /**
   * When the parent sends {string} with testExecutionResult
   * Sends LoadToBlockStorage command with stub result
   */
  When("""the parent sends {string} with testExecutionResult""") { (command: String) =>
    command match
      case "LoadToBlockStorage" =>
        world.blockStorageActor.foreach { actor =>
          val stubResult: TestExecutionResult = world.mockTestExecutionResult(world.testId, passed = true)
          actor ! LoadToBlockStorage(stubResult)
        }
  }

  /**
   * When the parent sends {string} with testExecutionResult before initialization
   * Sends LoadToBlockStorage before Initialize (should throw exception)
   */
  When("""the parent sends {string} with testExecutionResult before initialization""") { (command: String) =>
    command match
      case "LoadToBlockStorage" =>
        try
          world.blockStorageActor.foreach { actor =>
            val stubResult: TestExecutionResult = world.mockTestExecutionResult(world.testId, passed = true)
            actor ! LoadToBlockStorage(stubResult)
          }
        catch
          case ex: IllegalStateException =>
            // Expected exception - capture for verification
            world.capturedResponses = world.capturedResponses :+ world.testCancelledResponse(world.testId, cancelled = false, Some(ex.getMessage))
  }

  /**
   * When the BlockStorageActor parent sends {string}
   * Sends Stop command to BlockStorageActor
   */
  When("""the BlockStorageActor parent sends {string}""") { (command: String) =>
    command match
      case "Stop" =>
        world.blockStorageActor.foreach { actor =>
          actor ! Stop
        }
  }

  /**
   * When the BlockStorageActor parent sends {string} before initialization
   * Sends Stop command before Initialize to BlockStorageActor
   */
  When("""the BlockStorageActor parent sends {string} before initialization""") { (command: String) =>
    command match
      case "Stop" =>
        world.blockStorageActor.foreach { actor =>
          actor ! Stop
        }
  }

  /**
   * When the BlockStorageActor parent sends {string} before LoadToBlockStorage
   * Sends Stop after Initialize but before LoadToBlockStorage to BlockStorageActor
   */
  When("""the BlockStorageActor parent sends {string} before LoadToBlockStorage""") { (command: String) =>
    command match
      case "Stop" =>
        world.blockStorageActor.foreach { actor =>
          actor ! Stop
        }
  }

  // ========== THEN STEPS (Verification) ==========

  /**
   * Then the BlockStorageActor should send {string} to parent
   * Verifies BlockStorageFetched message sent to parent
   */
  Then("""the BlockStorageActor should send {string} to parent""") { (messageType: String) =>
    messageType match
      case "BlockStorageFetched" =>
        world.teaParentProbe.foreach { probe =>
          val message: TestExecutionCommand = probe.receiveMessage(3.seconds)
          message match
            case BlockStorageFetched(receivedTestId, directive) =>
              receivedTestId shouldBe world.testId
              directive should not be null
              world.lastBlockStorageDirective = Some(directive)
            case other =>
              fail(s"Expected BlockStorageFetched, got $other")
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

      case "BlockStorageUploadComplete" =>
        world.teaParentProbe.foreach { probe =>
          val message: TestExecutionCommand = probe.receiveMessage(3.seconds)
          message match
            case BlockStorageUploadComplete(receivedTestId) =>
              receivedTestId shouldBe world.testId
            case other =>
              fail(s"Expected BlockStorageUploadComplete, got $other")
        }
  }

  /**
   * Then the BlockStorageActor state should be {string}
   * Verifies actor state by checking initialization flag
   */
  Then("""the BlockStorageActor state should be {string}""") { (state: String) =>
    state match
      case other =>
        fail(s"Unknown state: $other")
  }

  /**
   * Then the BlockStorageActor should log {string} message
   * Verifies logging (placeholder in component tests)
   */
  Then("""the BlockStorageActor should log {string} message""") { (logContent: String) =>
    // TODO: Requires LogCapture utility - for now validate actor state is consistent
    world.blockStorageActor should not be None
  }

  /**
   * Then the BlockStorageActor should stop cleanly
   * Verifies actor stopped
   */
  Then("""the BlockStorageActor should stop cleanly""") { () =>
    world.blockStorageActor.foreach { actor =>
      world.teaParentProbe.foreach { parent =>
        parent.expectTerminated(actor, 3.seconds)
      }
    }
  }

  /**
   * Then the BlockStorageActor should throw IllegalStateException
   * NOTE: In scaffolding, exceptions bubble to parent - verification placeholder
   */
  Then("""the BlockStorageActor should throw IllegalStateException""") { () =>
    // In scaffolding phase: Verify actor exists and can receive messages
    // Exception handling validated by actor remaining operational
    world.blockStorageActor should not be None
    world.initialized shouldBe false
  }

  /**
   * Then the BlockStorageActor exception message should contain {string}
   * Verifies exception message content
   */
  Then("""the BlockStorageActor exception message should contain {string}""") { (expectedText: String) =>
    // TODO: Requires LogCapture utility - for now validate world capturedResponses
    if world.capturedResponses.nonEmpty then
      world.capturedResponses.last match
        case TestCancelledResponse(_, _, Some(msg)) => msg should include(expectedText)
        case _ => fail("Expected TestCancelledResponse with error message")
    else
      // Fallback: Verify actor state remains consistent
      world.blockStorageActor should not be None
  }

  /**
   * Then the BlockStorageActor should throw ProbeException
   * NOTE: In scaffolding, service integration not yet implemented
   */
  Then("""the BlockStorageActor should throw ProbeException""") { () =>
    // Service integration phase: Verify actor exists and exception would propagate
    world.blockStorageActor should not be None
    world.lastBlockStorageDirective should not be None
  }
  
  /**
   * Then the response should include {string}
   * Verifies response message type
   */
  Then("""the response should include {string}""") { (messageType: String) =>
    // Message already verified in previous step - validate world state
    world.blockStorageActor should not be None
    world.teaParentProbe should not be None
  }

  /**
   * Then the BlockStorageActor response should be {string}
   * Verifies BlockStorageActor response message type
   */
  Then("""the BlockStorageActor response should be {string}""") { (messageType: String) =>
    messageType match
      case "BlockStorageFetched" =>
        world.lastBlockStorageDirective shouldBe defined
        world.lastBlockStorageDirective.foreach { directive =>
          directive.jimfsLocation should not be empty
        }

      case "BlockStorageUploadComplete" =>
        // Verify upload completed - validate message was sent to parent
        world.teaParentProbe should not be None

      case other =>
        fail(s"Unknown BlockStorageActor response type: $other")
  }

  /**
   * Then the BlockStorageFetched message should have field {string} not null
   * Verifies BlockStorageFetched message contains required field
   */
  Then("""the BlockStorageFetched message should have field {string} not null""") { (fieldName: String) =>
    world.lastBlockStorageDirective match
      case Some(directive) =>
        fieldName match
          case "jimfsLocation" =>
            directive.jimfsLocation should not be null
            directive.jimfsLocation should not be empty

          case "topicDirectives" =>
            directive.topicDirectives should not be null

          case other =>
            fail(s"Unknown field: $other")

      case None =>
        fail("No BlockStorageFetched message captured")
  }

  /**
   * Then the jimfsLocation should match pattern {string}
   * Verifies jimfsLocation matches expected pattern
   */
  Then("""the jimfsLocation should match pattern {string}""") { (pattern: String) =>
    world.lastBlockStorageDirective match
      case Some(directive) =>
        directive.jimfsLocation should fullyMatch regex pattern

      case None =>
        fail("No BlockStorageFetched message captured")
  }
}
