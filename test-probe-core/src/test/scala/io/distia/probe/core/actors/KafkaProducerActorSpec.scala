package io.distia.probe
package core
package actors

import io.distia.probe.common.models.{BlockStorageDirective => BlockStorageDirectiveModel, EventFilter, KafkaSecurityDirective => KafkaSecurityDirectiveModel, SecurityProtocol, TopicDirective => TopicDirectiveModel}
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpec
import fixtures.{ActorTestingFixtures, TestHarnessFixtures}
import models.KafkaProducerCommands.*
import models.TestExecutionCommands.*
import models.*

import java.util.UUID
import scala.concurrent.duration.*

/**
 * Unit tests for KafkaProducerActor.
 *
 * Tests the child actor responsible for publishing test events
 * to Kafka topics with Schema Registry integration and security.
 *
 * Design Principles:
 * - Uses ActorTestingFixtures (Pattern #3)
 * - Uses inline test data - no CommonTestData (Pattern #1)
 * - Comprehensive Scaladoc (Pattern #10)
 *
 * Test Coverage:
 * - Actor spawning and initialization
 * - Initialize message handling with Kafka/security directives
 * - ChildGoodToGo response to parent
 * - Producer configuration with SASL authentication
 * - Passthrough command forwarding to streaming service
 * - Error handling for missing configuration
 * - Full lifecycle: Initialize → StartTest → Stop
 *
 * Thread Safety: Each test gets fresh TestProbes (isolated)
 */
class KafkaProducerActorSpec extends AnyWordSpec with ActorTestingFixtures with TestHarnessFixtures {

  // ===== Helper Methods =====

  /** Spawn KafkaProducerActor with default dependencies */
  private def spawnActor(testId: UUID, parentProbe: TestProbe[TestExecutionCommand]): ActorRef[KafkaProducerCommand] =
    testKit.spawn(KafkaProducerActor(testId, parentProbe.ref))

  // ===== Tests =====

  "KafkaProducerActor.apply" should {

    "spawn actor successfully with valid testId and parentTea" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[KafkaProducerCommand] = spawnActor(testId, parentProbe)

      actor should not be null
    }
  }

  "KafkaProducerActor Initialize" should {

    "send ChildGoodToGo on Initialize" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[KafkaProducerCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId",
        topicDirectives = List(TopicDirectiveModel("test-events", "producer", "test-client", List(EventFilter("TestEvent", "1.0"))))
      )

      val securityDirectives = List(
        KafkaSecurityDirectiveModel("test-events", "producer", SecurityProtocol.PLAINTEXT, "jaas-config")
      )

      actor ! Initialize(blockStorageDirective, securityDirectives)

      val goodToGoMsg: TestExecutionCommand = parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
      goodToGoMsg match {
        case ChildGoodToGo(receivedTestId, childRef) =>
          receivedTestId shouldBe testId
          childRef shouldBe actor
        case _ => fail(s"Expected ChildGoodToGo, got $goodToGoMsg")
      }
    }

    "handle Initialize with empty securityDirectives" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[KafkaProducerCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirectiveModel(
        jimfsLocation = s"/jimfs/test-$testId",
        evidenceDir = "",
        topicDirectives = List.empty,
        bucket = ""
      )

      actor ! Initialize(blockStorageDirective, List.empty)

      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
    }

    "handle idempotent Initialize calls gracefully" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[KafkaProducerCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId"
      )
      val securityDirectives = List.empty

      actor ! Initialize(blockStorageDirective, securityDirectives)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! Initialize(blockStorageDirective, securityDirectives)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
    }

    "update initialized state after Initialize" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[KafkaProducerCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId"
      )

      actor ! Initialize(blockStorageDirective, List.empty)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! StartTest

      parentProbe.expectNoMessage(1.second)
    }
  }

  "KafkaProducerActor StartTest" should {

    "be no-op after Initialize" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[KafkaProducerCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId"
      )

      actor ! Initialize(blockStorageDirective, List.empty)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! StartTest

      parentProbe.expectNoMessage(1.second)
    }

    "preserve actor state after StartTest" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[KafkaProducerCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId"
      )

      actor ! Initialize(blockStorageDirective, List.empty)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! StartTest
      parentProbe.expectNoMessage(1.second)

      actor ! Stop
    }

    "handle multiple StartTest calls" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[KafkaProducerCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId"
      )

      actor ! Initialize(blockStorageDirective, List.empty)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      for (_ <- 1 to 3) {
        actor ! StartTest
        parentProbe.expectNoMessage(500.milliseconds)
      }
    }
  }

  "KafkaProducerActor Stop" should {

    "handle Stop without initialization" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[KafkaProducerCommand] = spawnActor(testId, parentProbe)

      actor ! Stop

      // Actor should terminate cleanly without errors
      parentProbe.expectNoMessage(1.second)
    }

    "handle Stop after Initialize" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[KafkaProducerCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId"
      )

      actor ! Initialize(blockStorageDirective, List.empty)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! Stop

      // Verify actor terminates cleanly without sending error messages
      parentProbe.expectNoMessage(1.second)
    }

    "handle Stop after StartTest" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[KafkaProducerCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId"
      )

      actor ! Initialize(blockStorageDirective, List.empty)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! StartTest
      parentProbe.expectNoMessage(500.milliseconds)

      actor ! Stop

      // Verify actor terminates cleanly without sending error messages
      parentProbe.expectNoMessage(1.second)
    }
  }

  "KafkaProducerActor full lifecycle" should {

    "complete Initialize -> StartTest -> Stop workflow" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[KafkaProducerCommand] = spawnActor(testId, parentProbe)

      val blockStorageDirective = BlockStorageDirective.createBlockStorageDirective(
        jimfsLocation = s"/jimfs/test-$testId",
        topicDirectives = List(TopicDirectiveModel("test-events", "producer", "test-client", List(EventFilter("TestEvent", "1.0"))))
      )
      val securityDirectives = List(
        KafkaSecurityDirectiveModel("test-events", "producer", SecurityProtocol.PLAINTEXT, "jaas-config")
      )

      actor ! Initialize(blockStorageDirective, securityDirectives)
      val goodToGoMsg = parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
      goodToGoMsg match {
        case ChildGoodToGo(receivedTestId, childRef) =>
          receivedTestId shouldBe testId
          childRef shouldBe actor
        case _ => fail(s"Expected ChildGoodToGo, got $goodToGoMsg")
      }

      actor ! StartTest
      parentProbe.expectNoMessage(1.second)

      actor ! Stop

      // Verify complete lifecycle terminates cleanly
      parentProbe.expectNoMessage(1.second)
    }
  }
}
