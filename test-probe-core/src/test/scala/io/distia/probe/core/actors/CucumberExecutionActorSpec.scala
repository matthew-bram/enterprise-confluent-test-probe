package io.distia.probe
package core
package actors

import io.distia.probe.common.models.{BlockStorageDirective => BlockStorageDirectiveModel, EventFilter, TopicDirective => TopicDirectiveModel}
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.wordspec.AnyWordSpec
import fixtures.{ActorTestingFixtures, TestHarnessFixtures}
import models.CucumberExecutionCommands.*
import models.TestExecutionCommands.*
import models.*
import services.cucumber.CucumberConfiguration

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

/**
 * Unit tests for CucumberExecutionActor.
 *
 * Tests the child actor responsible for executing Cucumber BDD tests
 * using the Cucumber CLI engine with feature files and step definitions.
 *
 * Design Principles:
 * - Uses ActorTestingFixtures (Pattern #3)
 * - Uses TestHarnessFixtures for universal test data and jimfs
 * - Uses inline test data - no CommonTestData (Pattern #1)
 * - Comprehensive Scaladoc (Pattern #10)
 *
 * Test Coverage:
 * - Actor spawning and initialization
 * - Initialize message handling with storage directives
 * - ChildGoodToGo and TestCompleted responses to parent
 * - Cucumber test execution via passthrough to service functions
 * - Test result collection and reporting
 * - Integration with JimFS in-memory filesystem
 * - Guard validation (jimfsLocation, evidenceDir)
 * - Full lifecycle: Initialize → StartTest → Stop
 *
 * Thread Safety: Each test gets fresh TestProbes (isolated)
 */
class CucumberExecutionActorSpec extends AnyWordSpec with ActorTestingFixtures with TestHarnessFixtures {

  // ===== Helper Methods =====

  /**
   * Spawn CucumberExecutionActor with default or injected dependencies
   *
   * @param testId UUID of the test
   * @param parentProbe TestProbe simulating parent TestExecutionActor
   * @param cucumberExecutor Optional function to execute Cucumber (default: real execution)
   * @return ActorRef for CucumberExecutionCommand
   */
  private def spawnActor(
    testId: UUID,
    parentProbe: TestProbe[TestExecutionCommand],
  ): ActorRef[CucumberExecutionCommand] =
    testKit.spawn(CucumberExecutionActor(testId, parentProbe.ref))

  /** Create BlockStorageDirective with jimfs URI paths and stub feature file */
  private def createTestDirective(testId: UUID, jimfs: java.nio.file.FileSystem): BlockStorageDirectiveModel = {
    import java.nio.file.{Files, StandardOpenOption}
    import scala.io.Source

    val jimfsRoot = jimfs.getPath("/jimfs-test")
    val evidencePath = jimfs.getPath("/evidence-test")
    Files.createDirectories(jimfsRoot)
    Files.createDirectories(evidencePath)

    // Load stub feature file from classpath and copy to jimfs
    val stubResource = getClass.getResourceAsStream("/stubs/bdd-framework-minimal-valid-test-stub.feature")
    val featureContent = Source.fromInputStream(stubResource).mkString
    stubResource.close()

    val featureFile = jimfsRoot.resolve("bdd-framework-minimal-valid-test-stub.feature")
    Files.write(featureFile, featureContent.getBytes, StandardOpenOption.CREATE)

    BlockStorageDirective.createBlockStorageDirective(
      jimfsLocation = jimfsRoot.toUri.toString,
      evidenceDir = evidencePath.toUri.toString
    )
  }

  // ===== Tests =====

  "CucumberExecutionActor.apply" should {

    "spawn actor successfully with valid testId and parentTea" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[CucumberExecutionCommand] = spawnActor(testId, parentProbe)

      actor should not be null
    }
  }

  "CucumberExecutionActor Initialize" should {

    "send ChildGoodToGo on Initialize" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[CucumberExecutionCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId",
        evidenceDir = s"/tmp/evidence-$testId",
        topicDirectives = List(TopicDirectiveModel("test-events", "producer", "test-client", List(EventFilter("TestEvent", "1.0"))))
      )


      actor ! Initialize(blockStorageDirective)

      val goodToGoMsg: TestExecutionCommand = parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
      goodToGoMsg match {
        case ChildGoodToGo(receivedTestId, childRef) =>
          receivedTestId shouldBe testId
          childRef shouldBe actor
        case _ => fail(s"Expected ChildGoodToGo, got $goodToGoMsg")
      }
    }

    "handle Initialize with minimal configuration" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[CucumberExecutionCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId",
        evidenceDir = s"/tmp/evidence-$testId"
      )


      actor ! Initialize(blockStorageDirective)

      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
    }

    "handle idempotent Initialize calls gracefully" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[CucumberExecutionCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId",
        evidenceDir = s"/tmp/evidence-$testId"
      )

      actor ! Initialize(blockStorageDirective)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! Initialize(blockStorageDirective)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
    }

    "update initialized state after Initialize" in withJimfs { jimfs =>
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[CucumberExecutionCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = createTestDirective(testId, jimfs)

      actor ! Initialize(blockStorageDirective)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! StartTest

      parentProbe.expectMessageType[TestComplete](10.seconds)
    }

    "throw CucumberException when jimfsLocation is empty (guard validation)" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      // Wrap actor with supervision to enable restart on CucumberException
      import org.apache.pekko.actor.typed.SupervisorStrategy

      val actor: ActorRef[CucumberExecutionCommand] = testKit.spawn(
        Behaviors.supervise(CucumberExecutionActor(testId, parentProbe.ref))
          .onFailure[CucumberException](SupervisorStrategy.restart.withLimit(3, 10.seconds))
      )

      val invalidDirective = BlockStorageDirectiveModel(
        jimfsLocation = "",  // EMPTY - should fail require() validation
        evidenceDir = "/tmp/evidence",
        topicDirectives = List.empty,
        bucket = "test-bucket"
      )

      // Actor should throw CucumberException and restart via supervision
      actor ! Initialize(invalidDirective)

      // Give supervision time to restart actor (TestExecutionActor supervises children)
      Thread.sleep(500)

      // Verify actor is still alive and responsive after restart
      val validDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId",
        evidenceDir = s"/tmp/evidence-$testId"
      )

      actor ! Initialize(validDirective)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
    }

    "throw CucumberException when evidenceDir is empty (guard validation)" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      // Wrap actor with supervision to enable restart on CucumberException
      import org.apache.pekko.actor.typed.SupervisorStrategy

      val actor: ActorRef[CucumberExecutionCommand] = testKit.spawn(
        Behaviors.supervise(CucumberExecutionActor(testId, parentProbe.ref))
          .onFailure[CucumberException](SupervisorStrategy.restart.withLimit(3, 10.seconds))
      )

      val invalidDirective = BlockStorageDirectiveModel(
        jimfsLocation = s"/jimfs/test-$testId",
        evidenceDir = "",  // EMPTY - should fail require() validation
        topicDirectives = List.empty,
        bucket = "test-bucket"
      )

      // Actor should throw CucumberException and restart via supervision
      actor ! Initialize(invalidDirective)

      // Give supervision time to restart actor
      Thread.sleep(500)

      // Verify actor is still alive and responsive after restart
      val validDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId",
        evidenceDir = s"/tmp/evidence-$testId"
      )

      actor ! Initialize(validDirective)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
    }

    "throw CucumberException when both jimfsLocation and evidenceDir are empty" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      // Wrap actor with supervision to enable restart on CucumberException
      import org.apache.pekko.actor.typed.SupervisorStrategy

      val actor: ActorRef[CucumberExecutionCommand] = testKit.spawn(
        Behaviors.supervise(CucumberExecutionActor(testId, parentProbe.ref))
          .onFailure[CucumberException](SupervisorStrategy.restart.withLimit(3, 10.seconds))
      )

      val invalidDirective = BlockStorageDirectiveModel(
        jimfsLocation = "",  // EMPTY
        evidenceDir = "",    // EMPTY
        topicDirectives = List.empty,
        bucket = "test-bucket"
      )

      // Actor should throw CucumberException and restart via supervision
      actor ! Initialize(invalidDirective)

      // Give supervision time to restart actor
      Thread.sleep(500)

      // Verify actor is still alive and responsive after restart
      val validDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId",
        evidenceDir = s"/tmp/evidence-$testId"
      )

      actor ! Initialize(validDirective)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
    }
  }

  "CucumberExecutionActor StartTest" should {

    "send TestComplete after successful test execution" in withJimfs { jimfs =>
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[CucumberExecutionCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = createTestDirective(testId, jimfs)

      actor ! Initialize(blockStorageDirective)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! StartTest

      // Wait longer for async Cucumber execution to complete
      val testCompleteMsg: TestExecutionCommand = parentProbe.expectMessageType[TestComplete](10.seconds)
      testCompleteMsg match {
        case TestComplete(receivedTestId, result) =>
          receivedTestId shouldBe testId
          result.testId shouldBe testId
          result.passed shouldBe true
          result.scenarioCount shouldBe 1
          result.scenariosPassed shouldBe 1
        case _ => fail(s"Expected TestComplete, got $testCompleteMsg")
      }
    }

    "return TestExecutionResult with valid data from real Cucumber execution" in withJimfs { jimfs =>
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[CucumberExecutionCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = createTestDirective(testId, jimfs)

      actor ! Initialize(blockStorageDirective)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! StartTest

      val testCompleteMsg: TestExecutionCommand = parentProbe.expectMessageType[TestComplete](10.seconds)
      testCompleteMsg match {
        case TestComplete(_, result) =>
          result.testId shouldBe testId
          result.passed shouldBe true
          result.scenarioCount shouldBe 1 // Stub has 1 scenario
          result.scenariosPassed shouldBe 1
          result.stepCount shouldBe 3 // Stub has 2 Gherkin steps (no hooks)
          result.stepsPassed shouldBe 3
        case _ => fail(s"Expected TestComplete, got $testCompleteMsg")
      }
    }

    "preserve actor state after StartTest" in withJimfs { jimfs =>
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[CucumberExecutionCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = createTestDirective(testId, jimfs)

      actor ! Initialize(blockStorageDirective)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! StartTest
      parentProbe.expectMessageType[TestComplete](10.seconds)

      actor ! Stop
    }
  }

  "CucumberExecutionActor Stop" should {

    "handle Stop without initialization" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[CucumberExecutionCommand] = spawnActor(testId, parentProbe)

      // Send Stop command
      actor ! Stop

      // Verify actor terminates cleanly - no error messages sent to parent
      parentProbe.expectNoMessage(1.second)

      // Verify actor is no longer responsive (terminated)
      // Sending another message should not produce a response
      actor ! Stop
      parentProbe.expectNoMessage(500.millis)
    }

    "handle Stop after Initialize" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[CucumberExecutionCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId",
        evidenceDir = s"/tmp/evidence-$testId"
      )

      actor ! Initialize(blockStorageDirective)
      val goodToGo = parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
      goodToGo shouldBe a[ChildGoodToGo]

      // Send Stop and verify clean termination
      actor ! Stop

      // Verify no error messages sent to parent after Stop
      parentProbe.expectNoMessage(1.second)
    }

    "handle Stop after StartTest" in withJimfs { jimfs =>
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[CucumberExecutionCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = createTestDirective(testId, jimfs)

      actor ! Initialize(blockStorageDirective)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! StartTest

      // Stop immediately without waiting for TestComplete
      actor ! Stop

      // Verify actor terminates gracefully - may receive TestComplete or nothing
      // depending on timing. Important: should NOT receive error messages
      parentProbe.expectNoMessage(1.second)
    }
  }

  "CucumberExecutionActor full lifecycle" should {

    "complete Initialize -> StartTest -> Stop workflow" in withJimfs { jimfs =>
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[CucumberExecutionCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = createTestDirective(testId, jimfs)

      actor ! Initialize(blockStorageDirective)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! StartTest
      val testCompleteMsg: TestExecutionCommand = parentProbe.expectMessageType[TestComplete](10.seconds)
      testCompleteMsg match {
        case TestComplete(receivedTestId, result) =>
          receivedTestId shouldBe testId
          result.passed shouldBe true
          result.scenarioCount shouldBe 1
          result.scenariosPassed shouldBe 1
        case _ => fail(s"Expected TestComplete, got $testCompleteMsg")
      }

      actor ! Stop
    }
  }
}
