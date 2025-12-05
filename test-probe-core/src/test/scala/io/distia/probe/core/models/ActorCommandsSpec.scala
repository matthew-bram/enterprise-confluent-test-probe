package io.distia.probe
package core
package models

import java.time.Instant
import java.util.UUID

import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import io.distia.probe.common.models.{
  BlockStorageDirective,
  EventFilter,
  KafkaSecurityDirective,
  SecurityProtocol
}
import io.distia.probe.core.fixtures.TopicDirectiveFixtures

import actors.TestExecutionState
import pubsub.models.{ConsumedResult, ProduceResult}

/**
 * Comprehensive unit tests for ActorCommands.
 *
 * Tests all 10 command objects with maximal coverage:
 * - GuardianCommands: System initialization and actor discovery
 * - QueueCommands: Test lifecycle management with FSM states
 * - TestExecutionCommands: FSM transitions and child actor coordination
 * - CucumberExecutionCommands: Test execution control
 * - KafkaProducerCommands: Producer lifecycle
 * - KafkaConsumerCommands: Consumer lifecycle
 * - BlockStorageCommands: S3/Azure storage operations
 * - VaultCommands: Security credential retrieval
 * - KafkaConsumerStreamingCommands: Stream consumption registry
 * - KafkaProducerStreamingCommands: Stream production
 *
 * Coverage Focus:
 * - Command construction and field access
 * - ActorRef typing verification
 * - Optional field handling (defaults vs explicit)
 * - Edge cases (empty lists, None values)
 * - Sealed trait exhaustiveness
 */
class ActorCommandsSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with TopicDirectiveFixtures {

  val testKit: ActorTestKit = ActorTestKit()

  override def afterAll(): Unit = testKit.shutdownTestKit()

  // ============================================================
  // Test Fixtures
  // ============================================================

  private val testId: UUID = UUID.fromString("12345678-1234-1234-1234-123456789012")
  private val testBucket: String = "test-bucket"
  private val testType: Option[String] = Some("integration")

  private val topicDirective = createProducerDirective(
    topic = "test-topic",
    clientPrincipal = "test-client",
    eventFilters = List(EventFilter("TestEvent", "1.0"))
  )

  private val blockStorageDirective: BlockStorageDirective = BlockStorageDirective(
    jimfsLocation = "/test/features",
    evidenceDir = "/test/evidence",
    topicDirectives = List(topicDirective),
    bucket = testBucket
  )

  private val kafkaSecurityDirective: KafkaSecurityDirective = KafkaSecurityDirective(
    topic = "test-topic",
    role = "producer",
    securityProtocol = SecurityProtocol.PLAINTEXT,
    jaasConfig = ""
  )

  private val securityDirectives: List[KafkaSecurityDirective] = List(kafkaSecurityDirective)

  private val testExecutionResult: TestExecutionResult = TestExecutionResult(
    testId = testId,
    passed = true,
    scenarioCount = 5,
    scenariosPassed = 5,
    scenariosFailed = 0,
    scenariosSkipped = 0,
    stepCount = 25,
    stepsPassed = 25,
    stepsFailed = 0,
    stepsSkipped = 0,
    stepsUndefined = 0,
    durationMillis = 3000L
  )

  private val probeException: ProbeExceptions = CucumberException("Test failure")

  // ============================================================
  // GuardianCommands Tests
  // ============================================================

  "GuardianCommands" should {

    "Initialize should store replyTo ActorRef" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: GuardianCommands.Initialize = GuardianCommands.Initialize(probe.ref)

      cmd.replyTo shouldBe probe.ref
    }

    "Initialize should be a GuardianCommand" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: GuardianCommands.GuardianCommand = GuardianCommands.Initialize(probe.ref)

      cmd shouldBe a[GuardianCommands.GuardianCommand]
    }

    "GetQueueActor should store replyTo ActorRef" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: GuardianCommands.GetQueueActor = GuardianCommands.GetQueueActor(probe.ref)

      cmd.replyTo shouldBe probe.ref
    }

    "GetQueueActor should be a GuardianCommand" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: GuardianCommands.GuardianCommand = GuardianCommands.GetQueueActor(probe.ref)

      cmd shouldBe a[GuardianCommands.GuardianCommand]
    }
  }

  // ============================================================
  // QueueCommands Tests
  // ============================================================

  "QueueCommands" should {

    "InitializeTestRequest should store replyTo ActorRef" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: QueueCommands.InitializeTestRequest = QueueCommands.InitializeTestRequest(probe.ref)

      cmd.replyTo shouldBe probe.ref
    }

    "StartTestRequest should store all fields correctly" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: QueueCommands.StartTestRequest = QueueCommands.StartTestRequest(
        testId = testId,
        bucket = testBucket,
        testType = testType,
        replyTo = probe.ref
      )

      cmd.testId shouldBe testId
      cmd.bucket shouldBe testBucket
      cmd.testType shouldBe testType
      cmd.replyTo shouldBe probe.ref
    }

    "StartTestRequest should handle None testType" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: QueueCommands.StartTestRequest = QueueCommands.StartTestRequest(
        testId = testId,
        bucket = testBucket,
        testType = None,
        replyTo = probe.ref
      )

      cmd.testType shouldBe None
    }

    "TestStatusRequest should store testId and replyTo" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: QueueCommands.TestStatusRequest = QueueCommands.TestStatusRequest(testId, probe.ref)

      cmd.testId shouldBe testId
      cmd.replyTo shouldBe probe.ref
    }

    "QueueStatusRequest should handle Some and None testId" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()

      val cmdWithId: QueueCommands.QueueStatusRequest = QueueCommands.QueueStatusRequest(Some(testId), probe.ref)
      cmdWithId.testId shouldBe Some(testId)

      val cmdWithoutId: QueueCommands.QueueStatusRequest = QueueCommands.QueueStatusRequest(None, probe.ref)
      cmdWithoutId.testId shouldBe None
    }

    "CancelRequest should store testId and replyTo" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: QueueCommands.CancelRequest = QueueCommands.CancelRequest(testId, probe.ref)

      cmd.testId shouldBe testId
      cmd.replyTo shouldBe probe.ref
    }

    "TestInitialized should store testId" in {
      val cmd: QueueCommands.TestInitialized = QueueCommands.TestInitialized(testId)
      cmd.testId shouldBe testId
    }

    "TestLoading should store testId" in {
      val cmd: QueueCommands.TestLoading = QueueCommands.TestLoading(testId)
      cmd.testId shouldBe testId
    }

    "TestLoaded should store testId" in {
      val cmd: QueueCommands.TestLoaded = QueueCommands.TestLoaded(testId)
      cmd.testId shouldBe testId
    }

    "TestStarted should store testId" in {
      val cmd: QueueCommands.TestStarted = QueueCommands.TestStarted(testId)
      cmd.testId shouldBe testId
    }

    "TestCompleted should store testId" in {
      val cmd: QueueCommands.TestCompleted = QueueCommands.TestCompleted(testId)
      cmd.testId shouldBe testId
    }

    "TestException should store testId and exception" in {
      val cmd: QueueCommands.TestException = QueueCommands.TestException(testId, probeException)

      cmd.testId shouldBe testId
      cmd.exception shouldBe probeException
    }

    "TestStopping should store testId" in {
      val cmd: QueueCommands.TestStopping = QueueCommands.TestStopping(testId)
      cmd.testId shouldBe testId
    }

    "TestStatusResponse should store all fields with defaults" in {
      val cmd: QueueCommands.TestStatusResponse = QueueCommands.TestStatusResponse(
        testId = testId,
        state = TestExecutionState.Setup,
        bucket = Some(testBucket),
        testType = testType
      )

      cmd.testId shouldBe testId
      cmd.state shouldBe TestExecutionState.Setup
      cmd.bucket shouldBe Some(testBucket)
      cmd.testType shouldBe testType
      cmd.startTime shouldBe None
      cmd.endTime shouldBe None
      cmd.success shouldBe None
      cmd.error shouldBe None
    }

    "TestStatusResponse should store all fields with explicit values" in {
      val now: Instant = Instant.now()
      val cmd: QueueCommands.TestStatusResponse = QueueCommands.TestStatusResponse(
        testId = testId,
        state = TestExecutionState.Completed,
        bucket = Some(testBucket),
        testType = testType,
        startTime = Some(now),
        endTime = Some(now.plusSeconds(60)),
        success = Some(true),
        error = None
      )

      cmd.startTime shouldBe Some(now)
      cmd.endTime shouldBe Some(now.plusSeconds(60))
      cmd.success shouldBe Some(true)
      cmd.error shouldBe None
    }

    "TestStatusResponse should store error when test fails" in {
      val cmd: QueueCommands.TestStatusResponse = QueueCommands.TestStatusResponse(
        testId = testId,
        state = TestExecutionState.Exception,
        bucket = Some(testBucket),
        testType = testType,
        error = Some(probeException)
      )

      cmd.error shouldBe Some(probeException)
    }

    "all QueueCommands should extend QueueCommand trait" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()

      val commands: List[QueueCommands.QueueCommand] = List(
        QueueCommands.InitializeTestRequest(probe.ref),
        QueueCommands.StartTestRequest(testId, testBucket, testType, probe.ref),
        QueueCommands.TestStatusRequest(testId, probe.ref),
        QueueCommands.QueueStatusRequest(None, probe.ref),
        QueueCommands.CancelRequest(testId, probe.ref),
        QueueCommands.TestInitialized(testId),
        QueueCommands.TestLoading(testId),
        QueueCommands.TestLoaded(testId),
        QueueCommands.TestStarted(testId),
        QueueCommands.TestCompleted(testId),
        QueueCommands.TestException(testId, probeException),
        QueueCommands.TestStopping(testId),
        QueueCommands.TestStatusResponse(testId, TestExecutionState.Setup, None, None)
      )

      commands.foreach(_ shouldBe a[QueueCommands.QueueCommand])
      commands.size shouldBe 13
    }
  }

  // ============================================================
  // TestExecutionCommands Tests
  // ============================================================

  "TestExecutionCommands" should {

    "InInitializeTestRequest should store testId and replyTo" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: TestExecutionCommands.InInitializeTestRequest =
        TestExecutionCommands.InInitializeTestRequest(testId, probe.ref)

      cmd.testId shouldBe testId
      cmd.replyTo shouldBe probe.ref
    }

    "InStartTestRequest should store all fields" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: TestExecutionCommands.InStartTestRequest = TestExecutionCommands.InStartTestRequest(
        testId = testId,
        bucket = testBucket,
        testType = testType,
        replyTo = probe.ref
      )

      cmd.testId shouldBe testId
      cmd.bucket shouldBe testBucket
      cmd.testType shouldBe testType
      cmd.replyTo shouldBe probe.ref
    }

    "InCancelRequest should store testId and replyTo" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: TestExecutionCommands.InCancelRequest = TestExecutionCommands.InCancelRequest(testId, probe.ref)

      cmd.testId shouldBe testId
      cmd.replyTo shouldBe probe.ref
    }

    "GetStatus should store testId and replyTo" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val cmd: TestExecutionCommands.GetStatus = TestExecutionCommands.GetStatus(testId, probe.ref)

      cmd.testId shouldBe testId
      cmd.replyTo shouldBe probe.ref
    }

    "TrnSetup should be a case object" in {
      val cmd: TestExecutionCommands.TestExecutionCommand = TestExecutionCommands.TrnSetup
      cmd shouldBe TestExecutionCommands.TrnSetup
    }

    "TrnLoading should be a case object" in {
      val cmd: TestExecutionCommands.TestExecutionCommand = TestExecutionCommands.TrnLoading
      cmd shouldBe TestExecutionCommands.TrnLoading
    }

    "TrnLoaded should be a case object" in {
      val cmd: TestExecutionCommands.TestExecutionCommand = TestExecutionCommands.TrnLoaded
      cmd shouldBe TestExecutionCommands.TrnLoaded
    }

    "TrnTesting should be a case object" in {
      val cmd: TestExecutionCommands.TestExecutionCommand = TestExecutionCommands.TrnTesting
      cmd shouldBe TestExecutionCommands.TrnTesting
    }

    "TrnComplete should be a case object" in {
      val cmd: TestExecutionCommands.TestExecutionCommand = TestExecutionCommands.TrnComplete
      cmd shouldBe TestExecutionCommands.TrnComplete
    }

    "TrnException should store exception" in {
      val cmd: TestExecutionCommands.TrnException = TestExecutionCommands.TrnException(probeException)
      cmd.exception shouldBe probeException
    }

    "TrnShutdown should be a case object" in {
      val cmd: TestExecutionCommands.TestExecutionCommand = TestExecutionCommands.TrnShutdown
      cmd shouldBe TestExecutionCommands.TrnShutdown
    }

    "TrnPoisonPill should be a case object" in {
      val cmd: TestExecutionCommands.TestExecutionCommand = TestExecutionCommands.TrnPoisonPill
      cmd shouldBe TestExecutionCommands.TrnPoisonPill
    }

    "StartTesting should store testId" in {
      val cmd: TestExecutionCommands.StartTesting = TestExecutionCommands.StartTesting(testId)
      cmd.testId shouldBe testId
    }

    "ChildGoodToGo should store testId and child ActorRef" in {
      val childProbe: TestProbe[Any] = testKit.createTestProbe[Any]()
      val cmd: TestExecutionCommands.ChildGoodToGo = TestExecutionCommands.ChildGoodToGo(testId, childProbe.ref)

      cmd.testId shouldBe testId
      cmd.child shouldBe childProbe.ref
    }

    "BlockStorageFetched should store testId and blockStorageDirective" in {
      val cmd: TestExecutionCommands.BlockStorageFetched =
        TestExecutionCommands.BlockStorageFetched(testId, blockStorageDirective)

      cmd.testId shouldBe testId
      cmd.blockStorageDirective shouldBe blockStorageDirective
    }

    "SecurityFetched should store testId and securityDirectives" in {
      val cmd: TestExecutionCommands.SecurityFetched =
        TestExecutionCommands.SecurityFetched(testId, securityDirectives)

      cmd.testId shouldBe testId
      cmd.securityDirectives shouldBe securityDirectives
    }

    "SecurityFetched should handle empty security directives list" in {
      val cmd: TestExecutionCommands.SecurityFetched =
        TestExecutionCommands.SecurityFetched(testId, List.empty)

      cmd.securityDirectives shouldBe empty
    }

    "TestComplete should store testId and result" in {
      val cmd: TestExecutionCommands.TestComplete =
        TestExecutionCommands.TestComplete(testId, testExecutionResult)

      cmd.testId shouldBe testId
      cmd.result shouldBe testExecutionResult
    }

    "BlockStorageUploadComplete should store testId" in {
      val cmd: TestExecutionCommands.BlockStorageUploadComplete =
        TestExecutionCommands.BlockStorageUploadComplete(testId)

      cmd.testId shouldBe testId
    }

    "all TestExecutionCommands should extend TestExecutionCommand trait" in {
      val probe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
      val childProbe: TestProbe[Any] = testKit.createTestProbe[Any]()

      val commands: List[TestExecutionCommands.TestExecutionCommand] = List(
        TestExecutionCommands.InInitializeTestRequest(testId, probe.ref),
        TestExecutionCommands.InStartTestRequest(testId, testBucket, testType, probe.ref),
        TestExecutionCommands.InCancelRequest(testId, probe.ref),
        TestExecutionCommands.GetStatus(testId, probe.ref),
        TestExecutionCommands.TrnSetup,
        TestExecutionCommands.TrnLoading,
        TestExecutionCommands.TrnLoaded,
        TestExecutionCommands.TrnTesting,
        TestExecutionCommands.TrnComplete,
        TestExecutionCommands.TrnException(probeException),
        TestExecutionCommands.TrnShutdown,
        TestExecutionCommands.TrnPoisonPill,
        TestExecutionCommands.StartTesting(testId),
        TestExecutionCommands.ChildGoodToGo(testId, childProbe.ref),
        TestExecutionCommands.BlockStorageFetched(testId, blockStorageDirective),
        TestExecutionCommands.SecurityFetched(testId, securityDirectives),
        TestExecutionCommands.TestComplete(testId, testExecutionResult),
        TestExecutionCommands.BlockStorageUploadComplete(testId)
      )

      commands.foreach(_ shouldBe a[TestExecutionCommands.TestExecutionCommand])
      commands.size shouldBe 18
    }
  }

  // ============================================================
  // CucumberExecutionCommands Tests
  // ============================================================

  "CucumberExecutionCommands" should {

    "Initialize should store blockStorageDirective" in {
      val cmd: CucumberExecutionCommands.Initialize =
        CucumberExecutionCommands.Initialize(blockStorageDirective)

      cmd.blockStorageDirective shouldBe blockStorageDirective
    }

    "StartTest should be a case object" in {
      val cmd: CucumberExecutionCommands.CucumberExecutionCommand = CucumberExecutionCommands.StartTest
      cmd shouldBe CucumberExecutionCommands.StartTest
    }

    "Stop should be a case object" in {
      val cmd: CucumberExecutionCommands.CucumberExecutionCommand = CucumberExecutionCommands.Stop
      cmd shouldBe CucumberExecutionCommands.Stop
    }

    "TestExecutionComplete should store Right result" in {
      val cmd: CucumberExecutionCommands.TestExecutionComplete =
        CucumberExecutionCommands.TestExecutionComplete(Right(testExecutionResult))

      cmd.result shouldBe Right(testExecutionResult)
      cmd.result.isRight shouldBe true
    }

    "TestExecutionComplete should store Left throwable" in {
      val exception: Throwable = new RuntimeException("Test failed")
      val cmd: CucumberExecutionCommands.TestExecutionComplete =
        CucumberExecutionCommands.TestExecutionComplete(Left(exception))

      cmd.result shouldBe Left(exception)
      cmd.result.isLeft shouldBe true
    }

    "all CucumberExecutionCommands should extend CucumberExecutionCommand trait" in {
      val commands: List[CucumberExecutionCommands.CucumberExecutionCommand] = List(
        CucumberExecutionCommands.Initialize(blockStorageDirective),
        CucumberExecutionCommands.StartTest,
        CucumberExecutionCommands.Stop,
        CucumberExecutionCommands.TestExecutionComplete(Right(testExecutionResult))
      )

      commands.foreach(_ shouldBe a[CucumberExecutionCommands.CucumberExecutionCommand])
      commands.size shouldBe 4
    }
  }

  // ============================================================
  // KafkaProducerCommands Tests
  // ============================================================

  "KafkaProducerCommands" should {

    "Initialize should store blockStorageDirective and securityDirectives" in {
      val cmd: KafkaProducerCommands.Initialize =
        KafkaProducerCommands.Initialize(blockStorageDirective, securityDirectives)

      cmd.blockStorageDirective shouldBe blockStorageDirective
      cmd.securityDirectives shouldBe securityDirectives
    }

    "Initialize should handle empty security directives" in {
      val cmd: KafkaProducerCommands.Initialize =
        KafkaProducerCommands.Initialize(blockStorageDirective, List.empty)

      cmd.securityDirectives shouldBe empty
    }

    "StartTest should be a case object" in {
      val cmd: KafkaProducerCommands.KafkaProducerCommand = KafkaProducerCommands.StartTest
      cmd shouldBe KafkaProducerCommands.StartTest
    }

    "Stop should be a case object" in {
      val cmd: KafkaProducerCommands.KafkaProducerCommand = KafkaProducerCommands.Stop
      cmd shouldBe KafkaProducerCommands.Stop
    }

    "all KafkaProducerCommands should extend KafkaProducerCommand trait" in {
      val commands: List[KafkaProducerCommands.KafkaProducerCommand] = List(
        KafkaProducerCommands.Initialize(blockStorageDirective, securityDirectives),
        KafkaProducerCommands.StartTest,
        KafkaProducerCommands.Stop
      )

      commands.foreach(_ shouldBe a[KafkaProducerCommands.KafkaProducerCommand])
      commands.size shouldBe 3
    }
  }

  // ============================================================
  // KafkaConsumerCommands Tests
  // ============================================================

  "KafkaConsumerCommands" should {

    "Initialize should store blockStorageDirective and securityDirectives" in {
      val cmd: KafkaConsumerCommands.Initialize =
        KafkaConsumerCommands.Initialize(blockStorageDirective, securityDirectives)

      cmd.blockStorageDirective shouldBe blockStorageDirective
      cmd.securityDirectives shouldBe securityDirectives
    }

    "Initialize should handle empty security directives" in {
      val cmd: KafkaConsumerCommands.Initialize =
        KafkaConsumerCommands.Initialize(blockStorageDirective, List.empty)

      cmd.securityDirectives shouldBe empty
    }

    "StartTest should be a case object" in {
      val cmd: KafkaConsumerCommands.KafkaConsumerCommand = KafkaConsumerCommands.StartTest
      cmd shouldBe KafkaConsumerCommands.StartTest
    }

    "Stop should be a case object" in {
      val cmd: KafkaConsumerCommands.KafkaConsumerCommand = KafkaConsumerCommands.Stop
      cmd shouldBe KafkaConsumerCommands.Stop
    }

    "all KafkaConsumerCommands should extend KafkaConsumerCommand trait" in {
      val commands: List[KafkaConsumerCommands.KafkaConsumerCommand] = List(
        KafkaConsumerCommands.Initialize(blockStorageDirective, securityDirectives),
        KafkaConsumerCommands.StartTest,
        KafkaConsumerCommands.Stop
      )

      commands.foreach(_ shouldBe a[KafkaConsumerCommands.KafkaConsumerCommand])
      commands.size shouldBe 3
    }
  }

  // ============================================================
  // BlockStorageCommands Tests
  // ============================================================

  "BlockStorageCommands" should {

    "Initialize should store Some bucket" in {
      val cmd: BlockStorageCommands.Initialize = BlockStorageCommands.Initialize(Some(testBucket))
      cmd.bucket shouldBe Some(testBucket)
    }

    "Initialize should handle None bucket" in {
      val cmd: BlockStorageCommands.Initialize = BlockStorageCommands.Initialize(None)
      cmd.bucket shouldBe None
    }

    "FetchResults should store Right BlockStorageDirective" in {
      val cmd: BlockStorageCommands.FetchResults =
        BlockStorageCommands.FetchResults(Right(blockStorageDirective))

      cmd.results shouldBe Right(blockStorageDirective)
      cmd.results.isRight shouldBe true
    }

    "FetchResults should store Left Throwable" in {
      val exception: Throwable = new RuntimeException("S3 connection failed")
      val cmd: BlockStorageCommands.FetchResults = BlockStorageCommands.FetchResults(Left(exception))

      cmd.results shouldBe Left(exception)
      cmd.results.isLeft shouldBe true
    }

    "LoadToBlockStorage should store testExecutionResult" in {
      val cmd: BlockStorageCommands.LoadToBlockStorage =
        BlockStorageCommands.LoadToBlockStorage(testExecutionResult)

      cmd.testExecutionResult shouldBe testExecutionResult
    }

    "LoadResults should store Right Unit for success" in {
      val cmd: BlockStorageCommands.LoadResults = BlockStorageCommands.LoadResults(Right(()))

      cmd.results shouldBe Right(())
      cmd.results.isRight shouldBe true
    }

    "LoadResults should store Left Throwable for failure" in {
      val exception: Throwable = new RuntimeException("Upload failed")
      val cmd: BlockStorageCommands.LoadResults = BlockStorageCommands.LoadResults(Left(exception))

      cmd.results shouldBe Left(exception)
      cmd.results.isLeft shouldBe true
    }

    "Stop should be a case object" in {
      val cmd: BlockStorageCommands.BlockStorageCommand = BlockStorageCommands.Stop
      cmd shouldBe BlockStorageCommands.Stop
    }

    "all BlockStorageCommands should extend BlockStorageCommand trait" in {
      val commands: List[BlockStorageCommands.BlockStorageCommand] = List(
        BlockStorageCommands.Initialize(Some(testBucket)),
        BlockStorageCommands.FetchResults(Right(blockStorageDirective)),
        BlockStorageCommands.LoadToBlockStorage(testExecutionResult),
        BlockStorageCommands.LoadResults(Right(())),
        BlockStorageCommands.Stop
      )

      commands.foreach(_ shouldBe a[BlockStorageCommands.BlockStorageCommand])
      commands.size shouldBe 5
    }
  }

  // ============================================================
  // VaultCommands Tests
  // ============================================================

  "VaultCommands" should {

    "Initialize should store blockStorageDirective" in {
      val cmd: VaultCommands.Initialize = VaultCommands.Initialize(blockStorageDirective)
      cmd.blockStorageDirective shouldBe blockStorageDirective
    }

    "FetchResults should store Right security directives" in {
      val cmd: VaultCommands.FetchResults = VaultCommands.FetchResults(Right(securityDirectives))

      cmd.results shouldBe Right(securityDirectives)
      cmd.results.isRight shouldBe true
    }

    "FetchResults should store empty Right list" in {
      val cmd: VaultCommands.FetchResults = VaultCommands.FetchResults(Right(List.empty))

      cmd.results shouldBe Right(List.empty)
      cmd.results.map(_.isEmpty) shouldBe Right(true)
    }

    "FetchResults should store Left Throwable" in {
      val exception: Throwable = new RuntimeException("Vault connection failed")
      val cmd: VaultCommands.FetchResults = VaultCommands.FetchResults(Left(exception))

      cmd.results shouldBe Left(exception)
      cmd.results.isLeft shouldBe true
    }

    "Stop should be a case object" in {
      val cmd: VaultCommands.VaultCommand = VaultCommands.Stop
      cmd shouldBe VaultCommands.Stop
    }

    "all VaultCommands should extend VaultCommand trait" in {
      val commands: List[VaultCommands.VaultCommand] = List(
        VaultCommands.Initialize(blockStorageDirective),
        VaultCommands.FetchResults(Right(securityDirectives)),
        VaultCommands.Stop
      )

      commands.foreach(_ shouldBe a[VaultCommands.VaultCommand])
      commands.size shouldBe 3
    }
  }

  // ============================================================
  // KafkaConsumerStreamingCommands Tests
  // ============================================================

  "KafkaConsumerStreamingCommands" should {

    "FetchConsumedEvent should store correlationId and replyTo" in {
      val probe: TestProbe[ConsumedResult] = testKit.createTestProbe[ConsumedResult]()
      val correlationId: String = "corr-12345"

      val cmd: KafkaConsumerStreamingCommands.FetchConsumedEvent =
        KafkaConsumerStreamingCommands.FetchConsumedEvent(correlationId, probe.ref)

      cmd.correlationId shouldBe correlationId
      cmd.replyTo shouldBe probe.ref
    }

    "FetchConsumedEvent should handle empty correlationId" in {
      val probe: TestProbe[ConsumedResult] = testKit.createTestProbe[ConsumedResult]()

      val cmd: KafkaConsumerStreamingCommands.FetchConsumedEvent =
        KafkaConsumerStreamingCommands.FetchConsumedEvent("", probe.ref)

      cmd.correlationId shouldBe ""
    }

    "InternalAdd should store all fields" in {
      val probe: TestProbe[KafkaConsumerStreamingCommands.InternalAddConfirmed] = testKit.createTestProbe[KafkaConsumerStreamingCommands.InternalAddConfirmed]()
      val correlationId: String = "corr-67890"
      val key: Array[Byte] = "test-key".getBytes
      val value: Array[Byte] = """{"event":"test"}""".getBytes
      val headers: Headers = new RecordHeaders()

      val cmd: KafkaConsumerStreamingCommands.InternalAdd =
        KafkaConsumerStreamingCommands.InternalAdd(correlationId, key, value, headers, probe.ref)

      cmd.correlationId shouldBe correlationId
      cmd.key shouldBe key
      cmd.value shouldBe value
      cmd.headers shouldBe headers
      cmd.replyTo shouldBe probe.ref
    }

    "InternalAdd should handle empty byte arrays" in {
      val probe: TestProbe[KafkaConsumerStreamingCommands.InternalAddConfirmed] = testKit.createTestProbe[KafkaConsumerStreamingCommands.InternalAddConfirmed]()
      val headers: Headers = new RecordHeaders()

      val cmd: KafkaConsumerStreamingCommands.InternalAdd =
        KafkaConsumerStreamingCommands.InternalAdd("corr", Array.empty, Array.empty, headers, probe.ref)

      cmd.key shouldBe empty
      cmd.value shouldBe empty
    }

    "InternalAddConfirmed should store correlationId" in {
      val correlationId: String = "corr-confirmed"
      val cmd: KafkaConsumerStreamingCommands.InternalAddConfirmed =
        KafkaConsumerStreamingCommands.InternalAddConfirmed(correlationId)

      cmd.correlationId shouldBe correlationId
    }

    "all KafkaConsumerStreamingCommands should extend KafkaConsumerStreamingCommand trait" in {
      val consumedProbe: TestProbe[ConsumedResult] = testKit.createTestProbe[ConsumedResult]()
      val addProbe: TestProbe[KafkaConsumerStreamingCommands.InternalAddConfirmed] = testKit.createTestProbe[KafkaConsumerStreamingCommands.InternalAddConfirmed]()
      val headers: Headers = new RecordHeaders()

      val commands: List[KafkaConsumerStreamingCommands.KafkaConsumerStreamingCommand] = List(
        KafkaConsumerStreamingCommands.FetchConsumedEvent("corr1", consumedProbe.ref),
        KafkaConsumerStreamingCommands.InternalAdd("corr2", Array.empty, Array.empty, headers, addProbe.ref),
        KafkaConsumerStreamingCommands.InternalAddConfirmed("corr3")
      )

      commands.foreach(_ shouldBe a[KafkaConsumerStreamingCommands.KafkaConsumerStreamingCommand])
      commands.size shouldBe 3
    }
  }

  // ============================================================
  // KafkaProducerStreamingCommands Tests
  // ============================================================

  "KafkaProducerStreamingCommands" should {

    "ProduceEvent should store all fields" in {
      val probe: TestProbe[ProduceResult] = testKit.createTestProbe[ProduceResult]()
      val key: Array[Byte] = "event-key".getBytes
      val value: Array[Byte] = """{"data":"payload"}""".getBytes
      val headers: Map[String, String] = Map("ce_type" -> "TestEvent", "ce_source" -> "test")

      val cmd: KafkaProducerStreamingCommands.ProduceEvent =
        KafkaProducerStreamingCommands.ProduceEvent(key, value, headers, probe.ref)

      cmd.key shouldBe key
      cmd.value shouldBe value
      cmd.headers shouldBe headers
      cmd.replyTo shouldBe probe.ref
    }

    "ProduceEvent should handle empty byte arrays" in {
      val probe: TestProbe[ProduceResult] = testKit.createTestProbe[ProduceResult]()

      val cmd: KafkaProducerStreamingCommands.ProduceEvent =
        KafkaProducerStreamingCommands.ProduceEvent(Array.empty, Array.empty, Map.empty, probe.ref)

      cmd.key shouldBe empty
      cmd.value shouldBe empty
      cmd.headers shouldBe empty
    }

    "ProduceEvent should handle large headers map" in {
      val probe: TestProbe[ProduceResult] = testKit.createTestProbe[ProduceResult]()
      val headers: Map[String, String] = (1 to 10).map(i => s"header_$i" -> s"value_$i").toMap

      val cmd: KafkaProducerStreamingCommands.ProduceEvent =
        KafkaProducerStreamingCommands.ProduceEvent(Array.empty, Array.empty, headers, probe.ref)

      cmd.headers.size shouldBe 10
    }

    "ProduceEvent should be a KafkaProducerStreamingCommand" in {
      val probe: TestProbe[ProduceResult] = testKit.createTestProbe[ProduceResult]()

      val cmd: KafkaProducerStreamingCommands.KafkaProducerStreamingCommand =
        KafkaProducerStreamingCommands.ProduceEvent(Array.empty, Array.empty, Map.empty, probe.ref)

      cmd shouldBe a[KafkaProducerStreamingCommands.KafkaProducerStreamingCommand]
    }
  }

  // ============================================================
  // Cross-Command Object Tests
  // ============================================================

  "ActorCommands across all objects" should {

    "have distinct namespaces preventing naming collisions" in {
      // Multiple commands share the same name but are in different objects
      val guardianInit: GuardianCommands.GuardianCommand =
        GuardianCommands.Initialize(testKit.createTestProbe[ServiceResponse]().ref)

      val cucumberInit: CucumberExecutionCommands.CucumberExecutionCommand =
        CucumberExecutionCommands.Initialize(blockStorageDirective)

      val kafkaProducerInit: KafkaProducerCommands.KafkaProducerCommand =
        KafkaProducerCommands.Initialize(blockStorageDirective, securityDirectives)

      val kafkaConsumerInit: KafkaConsumerCommands.KafkaConsumerCommand =
        KafkaConsumerCommands.Initialize(blockStorageDirective, securityDirectives)

      val blockStorageInit: BlockStorageCommands.BlockStorageCommand =
        BlockStorageCommands.Initialize(Some(testBucket))

      val vaultInit: VaultCommands.VaultCommand =
        VaultCommands.Initialize(blockStorageDirective)

      // All are distinct types despite same class name
      guardianInit shouldBe a[GuardianCommands.Initialize]
      cucumberInit shouldBe a[CucumberExecutionCommands.Initialize]
      kafkaProducerInit shouldBe a[KafkaProducerCommands.Initialize]
      kafkaConsumerInit shouldBe a[KafkaConsumerCommands.Initialize]
      blockStorageInit shouldBe a[BlockStorageCommands.Initialize]
      vaultInit shouldBe a[VaultCommands.Initialize]
    }

    "have distinct Stop commands in different objects" in {
      val cucumberStop: CucumberExecutionCommands.CucumberExecutionCommand = CucumberExecutionCommands.Stop
      val producerStop: KafkaProducerCommands.KafkaProducerCommand = KafkaProducerCommands.Stop
      val consumerStop: KafkaConsumerCommands.KafkaConsumerCommand = KafkaConsumerCommands.Stop
      val blockStorageStop: BlockStorageCommands.BlockStorageCommand = BlockStorageCommands.Stop
      val vaultStop: VaultCommands.VaultCommand = VaultCommands.Stop

      // All are case objects but different types
      cucumberStop shouldBe CucumberExecutionCommands.Stop
      producerStop shouldBe KafkaProducerCommands.Stop
      consumerStop shouldBe KafkaConsumerCommands.Stop
      blockStorageStop shouldBe BlockStorageCommands.Stop
      vaultStop shouldBe VaultCommands.Stop
    }

    "have distinct StartTest commands" in {
      val cucumberStart: CucumberExecutionCommands.CucumberExecutionCommand = CucumberExecutionCommands.StartTest
      val producerStart: KafkaProducerCommands.KafkaProducerCommand = KafkaProducerCommands.StartTest
      val consumerStart: KafkaConsumerCommands.KafkaConsumerCommand = KafkaConsumerCommands.StartTest

      cucumberStart shouldBe CucumberExecutionCommands.StartTest
      producerStart shouldBe KafkaProducerCommands.StartTest
      consumerStart shouldBe KafkaConsumerCommands.StartTest
    }
  }
}