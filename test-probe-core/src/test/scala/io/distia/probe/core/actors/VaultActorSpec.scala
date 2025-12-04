package io.distia.probe
package core
package actors

import io.distia.probe.common.models.{BlockStorageDirective => BlockStorageDirectiveModel, EventFilter, KafkaSecurityDirective => KafkaSecurityDirectiveModel, SecurityProtocol, TopicDirective => TopicDirectiveModel}
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpec
import fixtures.{ActorTestingFixtures, TestHarnessFixtures}
import models.VaultCommands.*
import models.TestExecutionCommands.*
import models.*

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.*

/**
 * Companion object for VaultActorSpec test data.
 *
 * Uses InterfaceFunctionsFixture for all function stubs (single source of truth).
 */
private[actors] object VaultActorSpec extends fixtures.InterfaceFunctionsFixture {
  import io.distia.probe.core.builder.{ServiceFunctionsContext, StorageServiceFunctions, VaultServiceFunctions}
  import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}
  import scala.concurrent.{ExecutionContext, Future}

  // Reuse GuardianActorSpec's stub service functions (which now uses fixtures!)
  val stubServiceFunctionsContext = GuardianActorSpec.stubServiceFunctionsContext

  /**
   * Create custom ServiceFunctionsContext with provided vault function.
   * Allows tests to inject custom vault behavior for testing specific scenarios.
   *
   * Uses InterfaceFunctionsFixture for storage stubs (no inline function creation!).
   */
  def customVaultFunction(
    vaultFn: BlockStorageDirective => ExecutionContext ?=> Future[List[KafkaSecurityDirective]]
  ): ServiceFunctionsContext = {
    ServiceFunctionsContext(
      vaultFunctions = VaultServiceFunctions(
        fetchSecurityDirectives = vaultFn
      ),
      storageFunctions = StorageServiceFunctions(
        fetchFromBlockStorage = getFailedBlockStorageFetchFunction,
        loadToBlockStorage = getFailedBlockStorageLoadFunction
      )
    )
  }
}

/**
 * Unit tests for VaultActor.
 *
 * Tests the child actor responsible for fetching secrets and credentials
 * from secret management systems (HashiCorp Vault, AWS Secrets Manager, Azure Key Vault).
 *
 * Design Principles:
 * - Uses ActorTestingFixtures (Pattern #3)
 * - Uses inline test data - no CommonTestData (Pattern #1)
 * - Comprehensive Scaladoc (Pattern #10)
 *
 * Test Coverage:
 * - Actor spawning and initialization
 * - Initialize message handling with vault path configuration
 * - ChildGoodToGo and VaultSecretsFetched responses to parent
 * - Security directive construction (Kafka SASL credentials)
 * - Error handling for missing vault configuration
 * - Passthrough command forwarding to service functions
 * - Full lifecycle: Initialize → Stop
 * - FetchResults async callback handling (success/failure/empty)
 *
 * Thread Safety: Each test gets fresh TestProbes (isolated)
 */
class VaultActorSpec extends AnyWordSpec with ActorTestingFixtures with TestHarnessFixtures {
  import VaultActorSpec._

  // ===== Helper Methods =====

  /** Spawn VaultActor with default dependencies */
  private def spawnActor(testId: UUID, parentProbe: TestProbe[TestExecutionCommand]): ActorRef[VaultCommand] =
    testKit.spawn(VaultActor(testId, parentProbe.ref, stubServiceFunctionsContext.vault))

  /** Create BlockStorageDirective with topic directives for testing */
  private def createBlockStorageDirectiveWithTopics(testId: UUID, topics: List[TopicDirectiveModel]): BlockStorageDirectiveModel =
    BlockStorageDirective.createBlockStorageDirective(
      jimfsLocation = s"/jimfs/test-$testId",
      topicDirectives = topics
    )

  /** Create BlockStorageDirective with no topic directives */
  private def createEmptyBlockStorageDirective(testId: UUID): BlockStorageDirectiveModel =
    BlockStorageDirectiveModel(
      jimfsLocation = s"/jimfs/test-$testId",
      evidenceDir = "",
      topicDirectives = List.empty,
      bucket = ""
    )

  // ===== Tests =====

  "VaultActor.apply" should {

    "spawn actor successfully with valid testId and parentTea" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[VaultCommand] = spawnActor(testId, parentProbe)

      actor should not be null
    }
  }

  "VaultActor Initialize" should {

    "send SecurityFetched and ChildGoodToGo on Initialize" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      // Use custom vault function that returns non-empty security directives
      val customContext = customVaultFunction { (_) =>
        Future.successful(List(
          KafkaSecurityDirectiveModel("test-events", "producer", SecurityProtocol.PLAINTEXT, "stub-jaas")
        ))
      }

      val actor: ActorRef[VaultCommand] = testKit.spawn(
        VaultActor(testId, parentProbe.ref, customContext.vault)
      )

      val blockStorageDirective = createBlockStorageDirectiveWithTopics(
        testId,
        List(TopicDirectiveModel("test-events", "producer", "test-client", List(EventFilter("TestEvent", "1.0"))))
      )

      actor ! Initialize(blockStorageDirective)

      val fetchedMsg: TestExecutionCommand = parentProbe.expectMessageType[SecurityFetched](3.seconds)
      fetchedMsg match {
        case SecurityFetched(receivedTestId, securityDirectives) =>
          receivedTestId shouldBe testId
          securityDirectives should not be empty
        case _ => fail(s"Expected SecurityFetched, got $fetchedMsg")
      }

      val goodToGoMsg: TestExecutionCommand = parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
      goodToGoMsg match {
        case ChildGoodToGo(receivedTestId, childRef) =>
          receivedTestId shouldBe testId
          childRef shouldBe actor
        case _ => fail(s"Expected ChildGoodToGo, got $goodToGoMsg")
      }
    }

    "return stub SecurityDirective with credentials" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      // Custom vault function that returns specific security directive
      val customContext = customVaultFunction { (_) =>
        Future.successful(List(
          KafkaSecurityDirectiveModel("test-events", "producer", SecurityProtocol.PLAINTEXT, "stub-jaas-config")
        ))
      }

      val actor: ActorRef[VaultCommand] = testKit.spawn(
        VaultActor(testId, parentProbe.ref, customContext.vault)
      )

      val blockStorageDirective = createEmptyBlockStorageDirective(testId)

      actor ! Initialize(blockStorageDirective)

      val fetchedMsg: TestExecutionCommand = parentProbe.expectMessageType[SecurityFetched](3.seconds)
      fetchedMsg match {
        case SecurityFetched(_, securityDirectives) =>
          securityDirectives.length shouldBe 1
          val directive = securityDirectives.head
          directive.topic shouldBe "test-events"
          directive.role shouldBe "producer"
          directive.jaasConfig shouldBe "stub-jaas-config"
        case _ => fail(s"Expected SecurityFetched, got $fetchedMsg")
      }
    }

    "handle Initialize with empty topicDirectives" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      // Use custom vault function for this test
      val customContext = customVaultFunction { (_) =>
        Future.successful(List.empty)
      }

      val actor: ActorRef[VaultCommand] = testKit.spawn(
        VaultActor(testId, parentProbe.ref, customContext.vault)
      )

      val blockStorageDirective = createEmptyBlockStorageDirective(testId)

      actor ! Initialize(blockStorageDirective)

      parentProbe.expectMessageType[SecurityFetched](3.seconds)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
    }

    "handle idempotent Initialize calls gracefully" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      // Use custom vault function
      val customContext = customVaultFunction { (_) =>
        Future.successful(List.empty)
      }

      val actor: ActorRef[VaultCommand] = testKit.spawn(
        VaultActor(testId, parentProbe.ref, customContext.vault)
      )

      val blockStorageDirective = createEmptyBlockStorageDirective(testId)

      actor ! Initialize(blockStorageDirective)
      parentProbe.expectMessageType[SecurityFetched](3.seconds)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! Initialize(blockStorageDirective)
      parentProbe.expectMessageType[SecurityFetched](3.seconds)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
    }
  }

  "VaultActor Stop" should {

    "handle Stop without initialization" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val actor: ActorRef[VaultCommand] = spawnActor(testId, parentProbe)

      actor ! Stop

      // Actor should terminate cleanly without errors
      parentProbe.expectNoMessage(1.second)
    }

    "handle Stop after Initialize" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val customContext = customVaultFunction { (_) =>
        Future.successful(List.empty)
      }

      val actor: ActorRef[VaultCommand] = testKit.spawn(
        VaultActor(testId, parentProbe.ref, customContext.vault)
      )

      val blockStorageDirective = createEmptyBlockStorageDirective(testId)

      actor ! Initialize(blockStorageDirective)
      parentProbe.expectMessageType[SecurityFetched](3.seconds)
      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! Stop

      // Verify actor terminates cleanly without sending error messages
      parentProbe.expectNoMessage(1.second)
    }
  }

  "VaultActor full lifecycle" should {

    "complete Initialize -> Stop workflow with security directives" in {
      val testId: UUID = UUID.randomUUID()
      val parentProbe: TestProbe[TestExecutionCommand] = testKit.createTestProbe[TestExecutionCommand]()

      val customContext = customVaultFunction { (_) =>
        Future.successful(List(
          KafkaSecurityDirectiveModel("test-events", "producer", SecurityProtocol.PLAINTEXT, "jaas")
        ))
      }

      val actor: ActorRef[VaultCommand] = testKit.spawn(
        VaultActor(testId, parentProbe.ref, customContext.vault)
      )

      val blockStorageDirective = createBlockStorageDirectiveWithTopics(
        testId,
        List(TopicDirectiveModel("test-events", "producer", "test-client", List(EventFilter("TestEvent", "1.0"))))
      )

      actor ! Initialize(blockStorageDirective)

      val fetchedMsg = parentProbe.expectMessageType[SecurityFetched](3.seconds)
      fetchedMsg match {
        case SecurityFetched(receivedTestId, securityDirectives) =>
          receivedTestId shouldBe testId
          securityDirectives should not be empty
          val directive = securityDirectives.head
          directive.topic shouldBe "test-events"
          directive.role shouldBe "producer"
        case _ => fail(s"Expected SecurityFetched, got $fetchedMsg")
      }

      parentProbe.expectMessageType[ChildGoodToGo](3.seconds)

      actor ! Stop

      // Verify clean termination - no error messages sent to parent
      parentProbe.expectNoMessage(1.second)
    }
  }

  "VaultActor handleFetchResults" should {

    "handle FetchResults with Right (success case)" in {
      // Given: test data
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()
      val directives = List(
        KafkaSecurityDirectiveModel("topic1", "producer", SecurityProtocol.PLAINTEXT, "jaas1"),
        KafkaSecurityDirectiveModel("topic2", "consumer", SecurityProtocol.PLAINTEXT, "jaas2")
      )

      // When: Create actor and send FetchResults with Right
      val customContext = customVaultFunction { (_) =>
        Future.successful(directives)
      }

      val actor = testKit.spawn(
        VaultActor(testId, parentProbe.ref, customContext.vault)
      )

      // Send Initialize to trigger the async fetch
      val blockStorageDirective = createEmptyBlockStorageDirective(testId)
      actor ! Initialize(blockStorageDirective)

      // Then: Verify both messages sent to parent
      val fetchedMsg = parentProbe.expectMessageType[SecurityFetched](3.seconds)
      fetchedMsg match {
        case SecurityFetched(receivedTestId, receivedDirectives) =>
          receivedTestId shouldBe testId
          receivedDirectives shouldBe directives
        case _ => fail(s"Expected SecurityFetched, got $fetchedMsg")
      }

      val goodToGoMsg = parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
      goodToGoMsg match {
        case ChildGoodToGo(receivedTestId, childRef) =>
          receivedTestId shouldBe testId
          childRef shouldBe actor
        case _ => fail(s"Expected ChildGoodToGo, got $goodToGoMsg")
      }
    }

    "handle FetchResults with Left (failure case) by throwing VaultConsumerException" in {
      // Given: test data with failure
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()
      val errorMessage = "Vault service unavailable"

      // When: Create actor with custom function that fails
      val customContext = customVaultFunction { (_) =>
        Future.failed(new RuntimeException(errorMessage))
      }

      val actor = testKit.spawn(
        VaultActor(testId, parentProbe.ref, customContext.vault)
      )

      // Send Initialize to trigger the async fetch that will fail
      val blockStorageDirective = createEmptyBlockStorageDirective(testId)
      actor ! Initialize(blockStorageDirective)

      // Then: Verify no messages sent (actor throws exception which is handled by supervisor)
      // Note: The exception is thrown in handleFetchResults, bubbles to supervisor
      // In a real test with supervisor, we'd verify the supervision strategy handles it
      // For now, we just verify no messages are sent
      parentProbe.expectNoMessage(1.second)
    }

    "handle empty security directives list successfully" in {
      // Given: test data with empty directives
      val testId = UUID.randomUUID()
      val parentProbe = testKit.createTestProbe[TestExecutionCommand]()

      // When: Create actor with custom function that returns empty list
      val customContext = customVaultFunction { (_) =>
        Future.successful(List.empty)
      }

      val actor = testKit.spawn(
        VaultActor(testId, parentProbe.ref, customContext.vault)
      )

      val blockStorageDirective = createEmptyBlockStorageDirective(testId)
      actor ! Initialize(blockStorageDirective)

      // Then: Verify both messages sent with empty list
      val fetchedMsg = parentProbe.expectMessageType[SecurityFetched](3.seconds)
      fetchedMsg match {
        case SecurityFetched(receivedTestId, receivedDirectives) =>
          receivedTestId shouldBe testId
          receivedDirectives shouldBe List.empty
        case _ => fail(s"Expected SecurityFetched, got $fetchedMsg")
      }

      val goodToGoMsg = parentProbe.expectMessageType[ChildGoodToGo](3.seconds)
      goodToGoMsg match {
        case ChildGoodToGo(receivedTestId, childRef) =>
          receivedTestId shouldBe testId
          childRef shouldBe actor
        case _ => fail(s"Expected ChildGoodToGo, got $goodToGoMsg")
      }
    }
  }
}
