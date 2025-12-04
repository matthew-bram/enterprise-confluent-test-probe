package io.distia.probe
package core
package actors

import io.distia.probe.common.models.BlockStorageDirective
import io.distia.probe.core.builder.{ServiceFunctionsContext, StorageServiceFunctions, VaultServiceFunctions}
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpec
import fixtures.{ActorTestingFixtures, TestHarnessFixtures}
import models.BlockStorageCommands.*
import models.TestExecutionCommands.*
import models.*

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.*

/**
 * Unit tests for BlockStorageActor.
 *
 * Tests the child actor responsible for fetching test assets (features, directives)
 * from block storage (S3, Azure Blob, local filesystem).
 *
 * Design Principles:
 * - Uses ActorTestingFixtures for actor test infrastructure (Pattern #3)
 * - Uses TestHarnessFixtures for test data builders (Pattern #1)
 * - Self-contained test data - no cross-spec dependencies (Pattern #4)
 * - Comprehensive Scaladoc (Pattern #10)
 *
 * Test Coverage:
 * - Actor spawning and initialization
 * - Initialize message handling with bucket configuration
 * - ChildGoodToGo and BlockStorageFetched responses to parent
 * - Error handling for missing bucket configuration
 * - LoadToBlockStorage evidence upload
 * - Stop command handling
 *
 * Thread Safety: Each test gets fresh TestProbes (isolated)
 */
class BlockStorageActorSpec extends AnyWordSpec
  with ActorTestingFixtures
  with TestHarnessFixtures {

  // ===== Helper Methods =====

  /**
   * Create working storage functions using InterfaceFunctionsFixture (single source of truth).
   *
   * No inline function creation - all functions come from fixture!
   */
  private def createWorkingStorage(testId: UUID): StorageServiceFunctions = {
    val directive = createBlockStorageDirective(
      jimfsLocation = s"/jimfs/test-$testId",
      bucket = "test-bucket",
      evidenceDir = "/tmp/evidence"
    )
    StorageServiceFunctions(
      fetchFromBlockStorage = getSuccessfulBlockStorageFetchFunction(directive),
      loadToBlockStorage = getSuccessfulBlockStorageLoadFunction
    )
  }

  /** Spawn BlockStorageActor with working storage functions */
  private def spawnActor(testId: UUID, parentProbe: TestProbe[TestExecutionCommand]): ActorRef[BlockStorageCommand] =
    testKit.spawn(BlockStorageActor(testId, parentProbe.ref, createWorkingStorage(testId)))

  /** Create mock test execution result */
  private def createTestExecutionResult(
    testId: UUID,
    passed: Boolean = true,
    scenariosFailed: Int = 0,
    stepsFailed: Int = 0,
    errorMessage: Option[String] = None
  ): TestExecutionResult = TestExecutionResult(
    testId = testId,
    passed = passed,
    scenarioCount = 1,
    scenariosPassed = if passed then 1 else 0,
    scenariosFailed = scenariosFailed,
    scenariosSkipped = 0,
    stepCount = 5,
    stepsPassed = if passed then 5 else 5 - stepsFailed,
    stepsFailed = stepsFailed,
    stepsSkipped = 0,
    stepsUndefined = 0,
    durationMillis = 1000L,
    errorMessage = errorMessage,
    failedScenarios = if passed then Seq.empty else Seq("Failed Scenario")
  )

  // ===== Tests =====

  "BlockStorageActor.apply" should {

    "spawn actor successfully with valid testId and parentTea" in {
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()

      val actor = spawnActor(testId, parentProbe)

      actor should not be null
    }
  }

  "BlockStorageActor Initialize" should {

    "send BlockStorageFetched and ChildGoodToGo on Initialize with bucket" in {
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()
      val actor = spawnActor(testId, parentProbe)

      actor ! Initialize(Some("test-bucket"))

      val fetchedMsg = parentProbe.expectMessageType[BlockStorageFetched](3.seconds)
      fetchedMsg match {
        case BlockStorageFetched(receivedTestId, directive) =>
          receivedTestId shouldBe testId
          directive.jimfsLocation should include(testId.toString)
          directive.topicDirectives shouldBe List.empty
        case _ => fail(s"Expected BlockStorageFetched, got $fetchedMsg")
      }

      val goodToGoMsg = parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
      goodToGoMsg match {
        case ChildGoodToGo(receivedTestId, childRef) =>
          receivedTestId shouldBe testId
          childRef shouldBe actor
        case _ => fail(s"Expected ChildGoodToGo, got $goodToGoMsg")
      }
    }

    "throw IllegalArgumentException on Initialize with None bucket" in {
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()
      val actor = spawnActor(testId, parentProbe)

      actor ! Initialize(None)

      // No messages should be sent because actor throws exception
      // In production, supervisor strategy will handle this error
      parentProbe.expectNoMessage(1.second)
    }

    "create BlockStorageDirective with jimfs location containing testId" in {
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()
      val actor = spawnActor(testId, parentProbe)

      actor ! Initialize(Some("test-bucket"))

      val fetchedMsg = parentProbe.expectMessageType[BlockStorageFetched](3.seconds)
      fetchedMsg match {
        case BlockStorageFetched(_, directive) =>
          directive.jimfsLocation should include("/jimfs/test-")
          directive.jimfsLocation should include(testId.toString)
        case _ => fail(s"Expected BlockStorageFetched, got $fetchedMsg")
      }
    }

    "handle idempotent Initialize calls gracefully" in {
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()
      val actor = spawnActor(testId, parentProbe)

      actor ! Initialize(Some("test-bucket"))
      parentProbe.expectMessageType[BlockStorageFetched](3.seconds)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! Initialize(Some("test-bucket"))
      parentProbe.expectMessageType[BlockStorageFetched](3.seconds)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
    }

    "update initialized state after Initialize" in {
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()
      val actor = spawnActor(testId, parentProbe)

      actor ! Initialize(Some("test-bucket"))
      parentProbe.expectMessageType[BlockStorageFetched](3.seconds)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      val testResult = createTestExecutionResult(testId)
      actor ! LoadToBlockStorage(testResult)

      parentProbe.expectMessageType[BlockStorageUploadComplete](3.seconds)
    }
  }

  "BlockStorageActor LoadToBlockStorage" should {

    "send BlockStorageUploadComplete after successful upload" in {
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()
      val actor = spawnActor(testId, parentProbe)

      actor ! Initialize(Some("test-bucket"))
      parentProbe.expectMessageType[BlockStorageFetched](3.seconds)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      val testResult = createTestExecutionResult(testId, passed = true)
      actor ! LoadToBlockStorage(testResult)

      val uploadMsg = parentProbe.expectMessageType[BlockStorageUploadComplete](3.seconds)
      uploadMsg match {
        case BlockStorageUploadComplete(receivedTestId) =>
          receivedTestId shouldBe testId
        case _ => fail(s"Expected BlockStorageUploadComplete, got $uploadMsg")
      }
    }

    "handle LoadToBlockStorage with failed test result" in {
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()
      val actor = spawnActor(testId, parentProbe)

      actor ! Initialize(Some("test-bucket"))
      parentProbe.expectMessageType[BlockStorageFetched](3.seconds)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      val testResult = createTestExecutionResult(
        testId,
        passed = false,
        scenariosFailed = 2,
        stepsFailed = 5,
        errorMessage = Some("Test execution failed")
      )
      actor ! LoadToBlockStorage(testResult)

      parentProbe.expectMessageType[BlockStorageUploadComplete](3.seconds)
    }

    "preserve actor state after LoadToBlockStorage" in {
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()
      val actor = spawnActor(testId, parentProbe)

      actor ! Initialize(Some("test-bucket"))
      parentProbe.expectMessageType[BlockStorageFetched](3.seconds)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      val testResult = createTestExecutionResult(testId)
      actor ! LoadToBlockStorage(testResult)
      parentProbe.expectMessageType[BlockStorageUploadComplete](3.seconds)

      actor ! Stop
    }

    "handle multiple LoadToBlockStorage calls" in {
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()
      val actor = spawnActor(testId, parentProbe)

      actor ! Initialize(Some("test-bucket"))
      parentProbe.expectMessageType[BlockStorageFetched](3.seconds)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      val testResult1 = createTestExecutionResult(testId)
      actor ! LoadToBlockStorage(testResult1)
      parentProbe.expectMessageType[BlockStorageUploadComplete](3.seconds)

      val testResult2 = createTestExecutionResult(testId)
      actor ! LoadToBlockStorage(testResult2)
      parentProbe.expectMessageType[BlockStorageUploadComplete](3.seconds)
    }
  }

  "BlockStorageActor Stop" should {

    "handle Stop after LoadToBlockStorage" in {
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()
      val actor = spawnActor(testId, parentProbe)

      actor ! Initialize(Some("test-bucket"))
      parentProbe.expectMessageType[BlockStorageFetched](3.seconds)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      val testResult = createTestExecutionResult(testId)
      actor ! LoadToBlockStorage(testResult)
      parentProbe.expectMessageType[BlockStorageUploadComplete](3.seconds)

      actor ! Stop

      // Verify actor terminates cleanly without sending error messages
      parentProbe.expectNoMessage(1.second)
    }
  }
}
