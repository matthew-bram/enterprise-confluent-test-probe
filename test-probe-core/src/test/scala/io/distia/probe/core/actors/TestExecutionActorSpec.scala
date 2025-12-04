package io.distia.probe
package core
package actors

import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpec
import io.distia.probe.common.models.{BlockStorageDirective, EventFilter, KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import config.CoreConfig
import fixtures.{ActorTestingFixtures, ConfigurationFixtures, TestHarnessFixtures}
import models.*
import models.BlockStorageCommands.*
import models.VaultCommands.*
import models.CucumberExecutionCommands.*
import models.KafkaProducerCommands.*
import models.KafkaConsumerCommands.*
import models.QueueCommands.*
import models.TestExecutionCommands.*
import io.distia.probe.core.models.{TestStatusResponse as ServiceTestStatusResponse}

import java.util.UUID
import scala.concurrent.duration.*

/**
 * Companion object for TestExecutionActorSpec test data.
 *
 * Contains shared stubs, test constants, child probe management, and helpers.
 */
private[actors] object TestExecutionActorSpec {

  // Reuse GuardianActorSpec's stub service functions
  val stubServiceFunctionsContext = GuardianActorSpec.stubServiceFunctionsContext

  // ===== Test Constants (inline from CommonTestData) =====

  object TestIds {
    val test1 = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val test2 = UUID.fromString("22222222-2222-2222-2222-222222222222")
    val test3 = UUID.fromString("33333333-3333-3333-3333-333333333333")
    val test4 = UUID.fromString("44444444-4444-4444-4444-444444444444")
    val test5 = UUID.fromString("55555555-5555-5555-5555-555555555555")
    val test6 = UUID.fromString("66666666-6666-6666-6666-666666666666")
    val test7 = UUID.fromString("77777777-7777-7777-7777-777777777777")
    val test8 = UUID.fromString("88888888-8888-8888-8888-888888888888")
    val test9 = UUID.fromString("99999999-9999-9999-9999-999999999999")
    val test10 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
  }

  object Buckets {
    val bucket1 = "test-bucket-1"
    val bucket2 = "test-bucket-2"
    val bucket3 = "test-bucket-3"
  }

  object TestTypes {
    val functional = "functional"
    val performance = "performance"
    val regression = "regression"
  }

  // ===== Exception Helpers (inline from ExceptionFixtures) =====

  def mockCucumberException(message: String = "Cucumber error") = CucumberException(message)
  def mockBlockStorageException(message: String = "Block storage error") = BlockStorageException(message)
  def mockVaultConsumerException(message: String = "Vault error") = VaultConsumerException(message)
  def mockKafkaProducerException(message: String = "Kafka producer error") = KafkaProducerException(message)
  def mockKafkaConsumerException(message: String = "Kafka consumer error") = KafkaConsumerException(message)

  // ===== TestExecutionActorFixtures Helpers =====
  // Note: mockSecurityDirective removed - use createSecurityDirective from TestHarnessFixtures
  // Note: createBlockStorageDirective removed - use createBlockStorageDirective from TestHarnessFixtures

  def mockTestExecutionResult(passed: Boolean = true) = TestExecutionResult(
    testId = UUID.randomUUID(),
    passed = passed,
    scenarioCount = 1,
    scenariosPassed = if passed then 1 else 0,
    scenariosFailed = if passed then 0 else 1,
    scenariosSkipped = 0,
    stepCount = 5,
    stepsPassed = if passed then 5 else 3,
    stepsFailed = if passed then 0 else 2,
    stepsSkipped = 0,
    stepsUndefined = 0,
    durationMillis = 1000L,
    errorMessage = if passed then None else Some("Test failed"),
    failedScenarios = if passed then Seq.empty else Seq("Test Scenario")
  )

  // ===== Child Actor Probes (convert trait to case class) =====

  /**
   * Container for all child actor test probes and their factories.
   *
   * Supports both simple forwarding (default) and custom behavior injection.
   *
   * Example - Simple forwarding (default):
   * {{{
   *   val probes = createChildProbes()
   *   val actor = spawnActor(testId, queueProbe, childProbes = Some(probes))
   * }}}
   *
   * Example - Custom behavior:
   * {{{
   *   val probes = createChildProbes()
   *   val customFactory = probes.blockStorageFactory(customBehavior = Some(myBehavior))
   *   val actor = spawnActor(..., blockStorageFactory = Some(customFactory))
   * }}}
   */
  case class ChildProbes(
    blockStorageProbe: TestProbe[BlockStorageCommand],
    vaultProbe: TestProbe[VaultCommand],
    cucumberProbe: TestProbe[CucumberExecutionCommand],
    producerProbe: TestProbe[KafkaProducerCommand],
    consumerProbe: TestProbe[KafkaConsumerCommand]
  ) {
    /**
     * Create BlockStorage factory with optional custom ActorRef.
     * Default: Simple forwarding to probe.
     */
    def blockStorageFactory(
      customRef: Option[ActorRef[BlockStorageCommand]] = None
    ): TestExecutionActor.BlockStorageFactory = {
      customRef match {
        case Some(ref) => _ => ref
        case None => _ => blockStorageProbe.ref
      }
    }

    /**
     * Create Vault factory with optional custom ActorRef.
     * Default: Simple forwarding to probe.
     */
    def vaultFactory(
      customRef: Option[ActorRef[VaultCommand]] = None
    ): TestExecutionActor.VaultFactory = {
      customRef match {
        case Some(ref) => _ => ref
        case None => _ => vaultProbe.ref
      }
    }

    /**
     * Create Cucumber factory with optional custom ActorRef.
     * Default: Simple forwarding to probe.
     */
    def cucumberFactory(
      customRef: Option[ActorRef[CucumberExecutionCommand]] = None
    ): TestExecutionActor.CucumberFactory = {
      customRef match {
        case Some(ref) => _ => ref
        case None => _ => cucumberProbe.ref
      }
    }

    /**
     * Create Kafka Producer factory with optional custom ActorRef.
     * Default: Simple forwarding to probe.
     */
    def producerFactory(
      customRef: Option[ActorRef[KafkaProducerCommand]] = None
    ): TestExecutionActor.KafkaProducerFactory = {
      customRef match {
        case Some(ref) => _ => ref
        case None => _ => producerProbe.ref
      }
    }

    /**
     * Create Kafka Consumer factory with optional custom ActorRef.
     * Default: Simple forwarding to probe.
     */
    def consumerFactory(
      customRef: Option[ActorRef[KafkaConsumerCommand]] = None
    ): TestExecutionActor.KafkaConsumerFactory = {
      customRef match {
        case Some(ref) => _ => ref
        case None => _ => consumerProbe.ref
      }
    }
  }
}

/**
 * Unit tests for TestExecutionActor.
 *
 * Tests the FSM actor responsible for orchestrating individual test execution
 * through a 7-state lifecycle (Setup → Loading → Loaded → Testing → Completed/Exception → ShuttingDown).
 *
 * Design Principles:
 * - Uses ActorTestingFixtures for actor test infrastructure (Pattern #3)
 * - Uses TestHarnessFixtures for test data builders (Pattern #1)
 * - Inline test data via companion object - no god objects (Pattern #5)
 * - Comprehensive Scaladoc (Pattern #10)
 *
 * Test Coverage:
 * - Setup state: Initialization, poison pill timer, cancellation, status
 * - Loading state: Child actor spawning (5 children), sequential initialization (BlockStorage → Vault → Cucumber/Producer/Consumer),
 *   ChildGoodToGo counting (requires 5), BlockStorageFetched/SecurityFetched handling, poison pill timer
 * - Loaded state: StartTesting transition, cancellation, status, no poison pill timer
 * - Testing state: TestComplete handling, BlockStorageUploadComplete, cancellation rejection, status, no poison pill timer
 * - Completed state: Poison pill timer, cancellation rejection, status
 * - Exception state: Poison pill timer, timer cancellation, cancellation rejection, status, exception preservation
 * - ShuttingDown state: Stop message broadcasting to all 5 children, queue notification
 * - FSM state transitions and data preservation
 * - Factory injection for testing (5 child actor factories)
 * - Helper methods (transition helpers, state setup)
 *
 * Thread Safety: Each test gets fresh TestProbes and actors (isolated)
 */
class TestExecutionActorSpec extends AnyWordSpec
  with ActorTestingFixtures
  with TestHarnessFixtures {
  import TestExecutionActorSpec.*

  // ===== Helper Methods =====

  /** Create all child actor probes at once */
  private def createChildProbes(): ChildProbes = ChildProbes(
    blockStorageProbe = testKit.createTestProbe[BlockStorageCommand](),
    vaultProbe = testKit.createTestProbe[VaultCommand](),
    cucumberProbe = testKit.createTestProbe[CucumberExecutionCommand](),
    producerProbe = testKit.createTestProbe[KafkaProducerCommand](),
    consumerProbe = testKit.createTestProbe[KafkaConsumerCommand]()
  )

  /** Spawn TestExecutionActor with optional child factories */
  private def spawnActor(
    testId: UUID,
    queueProbe: TestProbe[QueueCommand],
    config: CoreConfig = ConfigurationFixtures.defaultCoreConfig,
    childProbes: Option[ChildProbes] = None
  ): ActorRef[TestExecutionCommand] = {
    childProbes match {
      case Some(probes) =>
        testKit.spawn(TestExecutionActor(
          testId, queueProbe.ref, stubServiceFunctionsContext, config,
          blockStorageFactory = Some(probes.blockStorageFactory()),
          vaultFactory = Some(probes.vaultFactory()),
          cucumberFactory = Some(probes.cucumberFactory()),
          producerFactory = Some(probes.producerFactory()),
          consumerFactory = Some(probes.consumerFactory())
        ))
      case None =>
        testKit.spawn(TestExecutionActor(testId, queueProbe.ref, stubServiceFunctionsContext, config))
    }
  }

  /** Create custom config with specific timeouts */
  private def customConfig(
    setupTimeout: FiniteDuration = 60.seconds,
    loadingTimeout: FiniteDuration = 60.seconds,
    completedTimeout: FiniteDuration = 60.seconds,
    exceptionTimeout: FiniteDuration = 30.seconds
  ): CoreConfig = ConfigurationFixtures.customTimeoutCoreConfig(
    setupTimeout, loadingTimeout, completedTimeout, exceptionTimeout
  )

  // ===== FSM Workflow Helpers =====

  /** Initialize actor (Setup → Setup state) */
  private def initialize(
    actor: ActorRef[TestExecutionCommand],
    testId: UUID,
    replyProbe: TestProbe[ServiceResponse],
    queueProbe: TestProbe[QueueCommand]
  ): Unit = {
    actor ! InInitializeTestRequest(testId, replyProbe.ref)
    replyProbe.expectMessageType[InitializeTestResponse]
    queueProbe.expectMessageType[TestInitialized]
  }

  /** Start test (Setup → Loading state) */
  private def startTest(
    actor: ActorRef[TestExecutionCommand],
    testId: UUID,
    bucket: String = Buckets.bucket1,
    testType: Option[String] = Some(TestTypes.functional),
    replyProbe: TestProbe[ServiceResponse],
    queueProbe: TestProbe[QueueCommand]
  ): Unit = {
    actor ! InStartTestRequest(testId, bucket, testType, replyProbe.ref)
    replyProbe.expectMessageType[StartTestResponse]
    queueProbe.expectMessageType[TestLoading]
  }

  /** Complete loading workflow (Loading → Loaded state) */
  private def completeLoading(
    actor: ActorRef[TestExecutionCommand],
    testId: UUID,
    childProbes: ChildProbes
  ): Unit = {
    // ONLY expect BlockStorage Initialize (the only one sent in TrnLoading)
    // Other Initialize messages are sent later after BlockStorageFetched and SecurityFetched
    childProbes.blockStorageProbe.expectMessageType[BlockStorageCommands.Initialize]

    // Send ChildGoodToGo messages for all 5 children to signal they're ready
    // (In tests, mocked child actors respond even without receiving Initialize first)
    actor ! ChildGoodToGo(testId, childProbes.blockStorageProbe.ref)
    actor ! ChildGoodToGo(testId, childProbes.vaultProbe.ref)
    actor ! ChildGoodToGo(testId, childProbes.cucumberProbe.ref)
    actor ! ChildGoodToGo(testId, childProbes.producerProbe.ref)
    actor ! ChildGoodToGo(testId, childProbes.consumerProbe.ref)
  }

  /** Start testing (Loaded → Testing state) */
  private def beginTesting(
    actor: ActorRef[TestExecutionCommand],
    testId: UUID,
    queueProbe: TestProbe[QueueCommand],
    childProbes: ChildProbes
  ): Unit = {
    actor ! StartTesting(testId)
    queueProbe.expectMessageType[TestStarted]
    childProbes.cucumberProbe.expectMessageType[CucumberExecutionCommands.StartTest.type]
  }

  /** Complete test successfully (Testing → Completed state) */
  private def completeTestSuccessfully(
    actor: ActorRef[TestExecutionCommand],
    testId: UUID,
    childProbes: ChildProbes,
    queueProbe: TestProbe[QueueCommand],
    result: TestExecutionResult = mockTestExecutionResult(passed = true)
  ): Unit = {
    // Send TestComplete to actor (not to cucumber probe directly!)
    actor ! TestComplete(testId, result)

    // Actor will ask BlockStorage to upload evidence
    childProbes.blockStorageProbe.expectMessageType[BlockStorageCommands.LoadToBlockStorage]

    // Tell actor that upload is complete
    actor ! BlockStorageUploadComplete(testId)

    // Now TestCompleted will be sent to queue
    queueProbe.expectMessageType[TestCompleted]
  }

  // ===== State Transition Helpers =====

  /**
   * Transitions actor from fresh spawn to Loaded state.
   * Returns: (actor, testId, queueProbe, replyProbe, childProbes)
   */
  private def transitionToLoaded(
    testId: UUID = TestIds.test1,
    bucket: String = Buckets.bucket1,
    testType: Option[String] = Some(TestTypes.functional),
    config: CoreConfig = ConfigurationFixtures.defaultCoreConfig
  ): (ActorRef[TestExecutionCommand], UUID, TestProbe[QueueCommand], TestProbe[ServiceResponse], ChildProbes) = {
    val queueProbe = createQueueProbe()
    val replyProbe = createServiceProbe()
    val childProbes = createChildProbes()

    val actor = spawnActor(testId, queueProbe, config, childProbes = Some(childProbes))

    // Initialize
    initialize(actor, testId, replyProbe, queueProbe)

    // Start test
    startTest(actor, testId, bucket, testType, replyProbe, queueProbe)

    // Complete loading
    completeLoading(actor, testId, childProbes)
    queueProbe.expectMessageType[TestLoaded]

    (actor, testId, queueProbe, replyProbe, childProbes)
  }

  /**
   * Transitions actor from fresh spawn to Testing state.
   * Returns: (actor, testId, queueProbe, replyProbe, childProbes)
   */
  private def transitionToTesting(
    testId: UUID = TestIds.test1,
    bucket: String = Buckets.bucket1,
    testType: Option[String] = Some(TestTypes.functional),
    config: CoreConfig = ConfigurationFixtures.defaultCoreConfig
  ): (ActorRef[TestExecutionCommand], UUID, TestProbe[QueueCommand], TestProbe[ServiceResponse], ChildProbes) = {
    val (actor, id, queueProbe, replyProbe, childProbes) = transitionToLoaded(testId, bucket, testType, config)

    // Begin testing
    beginTesting(actor, id, queueProbe, childProbes)

    (actor, id, queueProbe, replyProbe, childProbes)
  }

  /**
   * Transitions actor from fresh spawn to Completed state.
   * Returns: (actor, testId, queueProbe, replyProbe, childProbes)
   */
  private def transitionToCompleted(
    testId: UUID = TestIds.test1,
    bucket: String = Buckets.bucket1,
    testType: Option[String] = Some(TestTypes.functional),
    config: CoreConfig = ConfigurationFixtures.defaultCoreConfig,
    passed: Boolean = true
  ): (ActorRef[TestExecutionCommand], UUID, TestProbe[QueueCommand], TestProbe[ServiceResponse], ChildProbes) = {
    val (actor, id, queueProbe, replyProbe, childProbes) = transitionToTesting(testId, bucket, testType, config)

    // Complete test
    val result = mockTestExecutionResult(passed = passed)
    completeTestSuccessfully(actor, id, childProbes, queueProbe, result)

    (actor, id, queueProbe, replyProbe, childProbes)
  }

  /**
   * Transitions actor to Exception state from specified starting state.
   * Returns: (actor, testId, queueProbe, replyProbe, childProbes)
   */
  private def transitionToException(
    testId: UUID = TestIds.test1,
    exception: ProbeExceptions = mockCucumberException("Test exception"),
    fromState: String = "Testing",
    bucket: String = Buckets.bucket1,
    testType: Option[String] = Some(TestTypes.functional),
    config: CoreConfig = ConfigurationFixtures.defaultCoreConfig
  ): (ActorRef[TestExecutionCommand], UUID, TestProbe[QueueCommand], TestProbe[ServiceResponse], ChildProbes) = {
    val (actor, id, queueProbe, replyProbe, childProbes) = fromState match {
      case "Testing" => transitionToTesting(testId, bucket, testType, config)
      case "Loaded" => transitionToLoaded(testId, bucket, testType, config)
      case "Loading" =>
        val queueProbe = createQueueProbe()
        val replyProbe = createServiceProbe()
        val childProbes = createChildProbes()
        val actor = spawnActor(testId, queueProbe, config, childProbes = Some(childProbes))
        initialize(actor, testId, replyProbe, queueProbe)
        startTest(actor, testId, bucket, testType, replyProbe, queueProbe)
        (actor, testId, queueProbe, replyProbe, childProbes)
    }

    // Send exception
    actor ! TrnException(exception)
    queueProbe.expectMessageType[TestException]

    (actor, id, queueProbe, replyProbe, childProbes)
  }

  // =========================================================================
  // SETUP STATE TESTS
  // =========================================================================

  "TestExecutionActor in Setup state" should {

    "handle InInitializeTestRequest and transition to setup" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test1

      val actor = spawnActor(testId, queueProbe)

      actor ! InInitializeTestRequest(testId, replyProbe.ref)

      // Should reply with InitializeTestResponse
      val response = replyProbe.expectMessageType[InitializeTestResponse]
      response.testId shouldBe testId

      // Should notify queue
      val queueMsg = queueProbe.expectMessageType[TestInitialized]
      queueMsg.testId shouldBe testId
    }

    "start poison pill timer on TrnSetup" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test2
      val config = ConfigurationFixtures.shortTimerCoreConfig

      val actor = spawnActor(testId, queueProbe, config)

      actor ! InInitializeTestRequest(testId, replyProbe.ref)
      replyProbe.expectMessageType[InitializeTestResponse]
      queueProbe.expectMessageType[TestInitialized]

      // Wait for poison pill timer to expire (2 seconds)
      queueProbe.expectMessageType[TestStopping](3.seconds)
    }

    "cancel poison pill timer when transitioning to Loading" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test3
      val config = customConfig(
        setupTimeout = 5.seconds,     // Long setup timer
        loadingTimeout = 2.seconds    // Short loading timer
      )
      val probes = createChildProbes()

      val actor = spawnActor(testId, queueProbe, config, Some(probes))

      // Initialize
      initialize(actor, testId, replyProbe, queueProbe)

      // Start test before setup timer expires (5 seconds)
      startTest(actor, testId, replyProbe = replyProbe, queueProbe = queueProbe)

      // Expect BlockStorage Initialize message
      probes.blockStorageProbe.expectMessageType[BlockStorageCommands.Initialize]

      // Setup poison pill timer should be cancelled, but loading timer (2s) will fire
      queueProbe.expectMessageType[TestStopping](3.seconds)
    }

    "handle InCancelRequest in Setup state" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test4

      val actor = spawnActor(testId, queueProbe)

      initialize(actor, testId, replyProbe, queueProbe)

      actor ! InCancelRequest(testId, replyProbe.ref)

      // Should reply with cancelled response
      val cancelResponse = replyProbe.expectMessageType[TestCancelledResponse]
      cancelResponse.testId shouldBe testId
      cancelResponse.cancelled shouldBe true

      // Should notify queue
      queueProbe.expectMessageType[TestStopping]
    }

    "handle GetStatus in Setup state" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test5

      val actor = spawnActor(testId, queueProbe)

      initialize(actor, testId, replyProbe, queueProbe)

      actor ! GetStatus(testId, replyProbe.ref)

      val status = replyProbe.expectMessageType[ServiceTestStatusResponse]
      status.testId shouldBe testId
      status.state shouldBe "Setup"
    }

    "ignore unexpected messages in Setup state" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test6

      val actor = spawnActor(testId, queueProbe)

      initialize(actor, testId, replyProbe, queueProbe)

      // Send unexpected message
      actor ! StartTesting(testId)

      // Actor should ignore it and remain functional
      actor ! GetStatus(testId, replyProbe.ref)
      val status = replyProbe.expectMessageType[ServiceTestStatusResponse]
      status.state shouldBe "Setup"
    }
  }

  // =========================================================================
  // LOADING STATE TESTS
  // =========================================================================

  "TestExecutionActor in Loading state" should {

    "spawn all 5 child actors on TrnLoading" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test1
      val probes = createChildProbes()

      val actor = spawnActor(testId, queueProbe, childProbes = Some(probes))

      initialize(actor, testId, replyProbe, queueProbe)
      startTest(actor, testId, replyProbe = replyProbe, queueProbe = queueProbe)

      // BlockStorage should be initialized first
      val initMsg = probes.blockStorageProbe.expectMessageType[BlockStorageCommands.Initialize]
      initMsg.bucket shouldBe Some(Buckets.bucket1)
    }

    "initialize Vault after BlockStorageFetched" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test2
      val probes = createChildProbes()

      val actor = spawnActor(testId, queueProbe, childProbes = Some(probes))

      initialize(actor, testId, replyProbe, queueProbe)
      startTest(actor, testId, replyProbe = replyProbe, queueProbe = queueProbe)
      probes.blockStorageProbe.expectMessageType[BlockStorageCommands.Initialize]

      // Send BlockStorageFetched
      val blockStorageDirective = createBlockStorageDirective(
        jimfsLocation = s"/jimfs/${Buckets.bucket1}",
        evidenceDir = "evidence/",
        topicDirectives = List(TopicDirective.createProducerDirective("test-events", "test-client", List(EventFilter("TestEvent", "1.0")))),
        bucket = Buckets.bucket1
      )
      actor ! BlockStorageFetched(testId, blockStorageDirective)

      // Vault should be initialized
      val vaultInit = probes.vaultProbe.expectMessageType[VaultCommands.Initialize]
      vaultInit.blockStorageDirective shouldBe blockStorageDirective
    }

    "initialize Cucumber, Producer, Consumer after SecurityFetched" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test3
      val probes = createChildProbes()

      val actor = spawnActor(testId, queueProbe, childProbes = Some(probes))

      initialize(actor, testId, replyProbe, queueProbe)
      startTest(actor, testId, replyProbe = replyProbe, queueProbe = queueProbe)
      probes.blockStorageProbe.expectMessageType[BlockStorageCommands.Initialize]

      // Send BlockStorageFetched
      val blockStorageDirective = createBlockStorageDirective(
        jimfsLocation = s"/jimfs/${Buckets.bucket1}",
        evidenceDir = "evidence/",
        topicDirectives = List(TopicDirective.createProducerDirective("test-events", "test-client", List(EventFilter("TestEvent", "1.0")))),
        bucket = Buckets.bucket1
      )
      actor ! BlockStorageFetched(testId, blockStorageDirective)
      probes.vaultProbe.expectMessageType[VaultCommands.Initialize]

      // Send SecurityFetched (using createSecurityDirective from TestHarnessFixtures)
      val securityDirectives = List(createSecurityDirective("test-topic-topic1", "test-role"))
      actor ! SecurityFetched(testId, securityDirectives)

      // All 3 children should be initialized
      val cucumberInit = probes.cucumberProbe.expectMessageType[CucumberExecutionCommands.Initialize]
      cucumberInit.blockStorageDirective shouldBe blockStorageDirective

      val producerInit = probes.producerProbe.expectMessageType[KafkaProducerCommands.Initialize]
      producerInit.blockStorageDirective shouldBe blockStorageDirective
      producerInit.securityDirectives shouldBe securityDirectives

      val consumerInit = probes.consumerProbe.expectMessageType[KafkaConsumerCommands.Initialize]
      consumerInit.blockStorageDirective shouldBe blockStorageDirective
      consumerInit.securityDirectives shouldBe securityDirectives
    }

    "count ChildGoodToGo messages and transition to Loaded after 5" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test4
      val probes = createChildProbes()

      val actor = spawnActor(testId, queueProbe, childProbes = Some(probes))

      initialize(actor, testId, replyProbe, queueProbe)
      startTest(actor, testId, replyProbe = replyProbe, queueProbe = queueProbe)

      // Send 5 ChildGoodToGo messages
      actor ! ChildGoodToGo(testId, probes.blockStorageProbe.ref)
      actor ! ChildGoodToGo(testId, probes.vaultProbe.ref)
      actor ! ChildGoodToGo(testId, probes.cucumberProbe.ref)
      actor ! ChildGoodToGo(testId, probes.producerProbe.ref)
      actor ! ChildGoodToGo(testId, probes.consumerProbe.ref)

      // Should transition to Loaded
      queueProbe.expectMessageType[TestLoaded]
    }

    "not transition to Loaded with only 4 ChildGoodToGo messages" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test5
      val probes = createChildProbes()

      val actor = spawnActor(testId, queueProbe, childProbes = Some(probes))

      initialize(actor, testId, replyProbe, queueProbe)
      startTest(actor, testId, replyProbe = replyProbe, queueProbe = queueProbe)

      // Send only 4 ChildGoodToGo messages
      actor ! ChildGoodToGo(testId, probes.blockStorageProbe.ref)
      actor ! ChildGoodToGo(testId, probes.vaultProbe.ref)
      actor ! ChildGoodToGo(testId, probes.cucumberProbe.ref)
      actor ! ChildGoodToGo(testId, probes.producerProbe.ref)

      // Should NOT transition to Loaded
      queueProbe.expectNoMessage(500.millis)
    }

    "handle InCancelRequest in Loading state" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test6
      val probes = createChildProbes()

      val actor = spawnActor(testId, queueProbe, childProbes = Some(probes))

      initialize(actor, testId, replyProbe, queueProbe)
      startTest(actor, testId, replyProbe = replyProbe, queueProbe = queueProbe)

      actor ! InCancelRequest(testId, replyProbe.ref)

      val cancelResponse = replyProbe.expectMessageType[TestCancelledResponse]
      cancelResponse.cancelled shouldBe true
      queueProbe.expectMessageType[TestStopping]
    }

    "handle TrnException in Loading state" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test7
      val probes = createChildProbes()

      val actor = spawnActor(testId, queueProbe, childProbes = Some(probes))

      initialize(actor, testId, replyProbe, queueProbe)
      startTest(actor, testId, replyProbe = replyProbe, queueProbe = queueProbe)

      // Send exception
      val exception = mockBlockStorageException("Block storage failed")
      actor ! TrnException(exception)

      // Should notify queue
      val exceptionMsg = queueProbe.expectMessageType[TestException]
      exceptionMsg.testId shouldBe testId
      exceptionMsg.exception shouldBe exception
    }

    "expire poison pill timer in Loading state" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test1
      val config = ConfigurationFixtures.shortTimerCoreConfig
      val probes = createChildProbes()

      val actor = spawnActor(testId, queueProbe, config, Some(probes))

      initialize(actor, testId, replyProbe, queueProbe)
      startTest(actor, testId, replyProbe = replyProbe, queueProbe = queueProbe)

      // Wait for poison pill timer to expire (2 seconds)
      queueProbe.expectMessageType[TestStopping](3.seconds)
    }
  }

  // =========================================================================
  // LOADED STATE TESTS
  // =========================================================================

  "TestExecutionActor in Loaded state" should {

    "not have poison pill timer in Loaded state" in {
      val (actor, testId, queueProbe, _, _) =
        transitionToLoaded(testId = TestIds.test1, config = ConfigurationFixtures.shortTimerCoreConfig)

      queueProbe.expectNoMessage(3.seconds)
    }

    "handle StartTesting and transition to Testing" in {
      val (actor, testId, queueProbe, _, childProbes) = transitionToLoaded(testId = TestIds.test2)

      actor ! StartTesting(testId)
      queueProbe.expectMessageType[TestStarted]
      childProbes.cucumberProbe.expectMessage(CucumberExecutionCommands.StartTest)
    }

    "handle GetStatus in Loaded state" in {
      val (actor, testId, _, replyProbe, _) = transitionToLoaded(testId = TestIds.test3)

      actor ! GetStatus(testId, replyProbe.ref)

      val status = replyProbe.expectMessageType[ServiceTestStatusResponse]
      status.state shouldBe "Loaded"
    }

    "handle InCancelRequest in Loaded state" in {
      val (actor, testId, queueProbe, replyProbe, _) = transitionToLoaded(testId = TestIds.test4)

      actor ! InCancelRequest(testId, replyProbe.ref)

      val cancelResponse = replyProbe.expectMessageType[TestCancelledResponse]
      cancelResponse.cancelled shouldBe true
      queueProbe.expectMessageType[TestStopping]
    }

    "handle TrnException in Loaded state" in {
      val (actor, testId, queueProbe, _, _) = transitionToLoaded(testId = TestIds.test5)

      val exception = mockCucumberException("Cucumber initialization failed")
      actor ! TrnException(exception)

      val exceptionMsg = queueProbe.expectMessageType[TestException]
      exceptionMsg.exception shouldBe exception
    }
  }

  // =========================================================================
  // TESTING STATE TESTS
  // =========================================================================

  "TestExecutionActor in Testing state" should {

    "not have poison pill timer in Testing state" in {
      val (actor, testId, queueProbe, _, _) =
        transitionToTesting(testId = TestIds.test1, config = ConfigurationFixtures.shortTimerCoreConfig)

      queueProbe.expectNoMessage(3.seconds)
    }

    "reject InCancelRequest in Testing state" in {
      val (actor, testId, queueProbe, replyProbe, _) = transitionToTesting(testId = TestIds.test2)

      actor ! InCancelRequest(testId, replyProbe.ref)

      val cancelResponse = replyProbe.expectMessageType[TestCancelledResponse]
      cancelResponse.cancelled shouldBe false
      cancelResponse.message should contain("Cannot cancel, test is currently executing")
    }

    "handle TestComplete and upload evidence to block storage" in {
      val (actor, testId, _, _, childProbes) = transitionToTesting(testId = TestIds.test3)

      val testResult = mockTestExecutionResult(passed = true)
      actor ! TestComplete(testId, testResult)

      val uploadMsg = childProbes.blockStorageProbe.expectMessageType[BlockStorageCommands.LoadToBlockStorage]
      uploadMsg.testExecutionResult shouldBe testResult
    }

    "handle BlockStorageUploadComplete and transition to Completed" in {
      val (actor, testId, queueProbe, _, childProbes) = transitionToTesting(testId = TestIds.test4)

      val testResult = mockTestExecutionResult(passed = true)
      actor ! TestComplete(testId, testResult)
      childProbes.blockStorageProbe.expectMessageType[BlockStorageCommands.LoadToBlockStorage]

      actor ! BlockStorageUploadComplete(testId)

      queueProbe.expectMessageType[TestCompleted]
    }

    "handle TrnException in Testing state" in {
      val (actor, testId, queueProbe, _, _) = transitionToTesting(testId = TestIds.test5)

      val exception = mockCucumberException("Test execution failed")
      actor ! TrnException(exception)

      val exceptionMsg = queueProbe.expectMessageType[TestException]
      exceptionMsg.exception shouldBe exception
    }

    "handle GetStatus in Testing state" in {
      val (actor, testId, _, replyProbe, _) = transitionToTesting(testId = TestIds.test6)

      actor ! GetStatus(testId, replyProbe.ref)

      val status = replyProbe.expectMessageType[ServiceTestStatusResponse]
      status.state shouldBe "Testing"
    }
  }

  // =========================================================================
  // COMPLETED STATE TESTS
  // =========================================================================

  "TestExecutionActor in Completed state" should {

    "start poison pill timer on TrnComplete" in {
      val (actor, testId, queueProbe, _, _) =
        transitionToCompleted(testId = TestIds.test1, config = ConfigurationFixtures.shortTimerCoreConfig)

      queueProbe.expectMessageType[TestStopping](3.seconds)
    }

    "reject InCancelRequest in Completed state" in {
      val (actor, testId, _, replyProbe, _) = transitionToCompleted(testId = TestIds.test2)

      actor ! InCancelRequest(testId, replyProbe.ref)

      val cancelResponse = replyProbe.expectMessageType[TestCancelledResponse]
      cancelResponse.cancelled shouldBe false
      cancelResponse.message should contain("Test already completed, cannot cancel")
    }

    "handle GetStatus in Completed state" in {
      val (actor, testId, _, replyProbe, _) = transitionToCompleted(testId = TestIds.test3)

      actor ! GetStatus(testId, replyProbe.ref)

      val status = replyProbe.expectMessageType[ServiceTestStatusResponse]
      status.state shouldBe "Completed"
    }

    "handle TrnException in Completed state" in {
      val (actor, testId, queueProbe, _, _) = transitionToCompleted(testId = TestIds.test4)

      val exception = mockCucumberException("Post-completion error")
      actor ! TrnException(exception)

      val exceptionMsg = queueProbe.expectMessageType[TestException]
      exceptionMsg.exception shouldBe exception
    }
  }

  // =========================================================================
  // EXCEPTION STATE TESTS
  // =========================================================================

  "TestExecutionActor in Exception state" should {

    "start poison pill timer on TrnException" in {
      val (actor, testId, queueProbe, _, _) =
        transitionToException(testId = TestIds.test1, config = ConfigurationFixtures.shortTimerCoreConfig)

      queueProbe.expectMessageType[TestStopping](3.seconds)
    }

    "cancel all timers on TrnException" in {
      val (actor, testId, queueProbe, _, _) =
        transitionToException(
          testId = TestIds.test2,
          fromState = "Testing",
          config = ConfigurationFixtures.customTimeoutCoreConfig(
            setupTimeout = 10.seconds,
            loadingTimeout = 10.seconds,
            completedTimeout = 10.seconds,
            exceptionTimeout = 10.seconds
          )
        )

      queueProbe.expectNoMessage(3.seconds)
    }

    "reject InCancelRequest in Exception state" in {
      val (actor, testId, _, replyProbe, _) = transitionToException(testId = TestIds.test3)

      actor ! InCancelRequest(testId, replyProbe.ref)

      val cancelResponse = replyProbe.expectMessageType[TestCancelledResponse]
      cancelResponse.cancelled shouldBe false
      cancelResponse.message should contain("Test in exception state, cleanup in progress")
    }

    "handle GetStatus in Exception state" in {
      val (actor, testId, _, replyProbe, _) = transitionToException(testId = TestIds.test4)

      actor ! GetStatus(testId, replyProbe.ref)

      val status = replyProbe.expectMessageType[ServiceTestStatusResponse]
      status.state shouldBe "Exception"
    }

    "preserve exception reference" in {
      val testException = mockCucumberException("Critical failure")

      val (actor, testId, _, replyProbe, _) = transitionToException(
        testId = TestIds.test5,
        exception = testException
      )

      // Verify exception preserved in status (TestException already verified by helper)
      actor ! GetStatus(testId, replyProbe.ref)
      val status = replyProbe.expectMessageType[ServiceTestStatusResponse]
      status.state shouldBe "Exception"
      status.error shouldBe Some("Critical failure")
    }
  }

  // =========================================================================
  // SHUTTING DOWN STATE TESTS
  // =========================================================================

  "TestExecutionActor in ShuttingDown state" should {

    "send Stop to all children on TrnShutdown" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val childProbes = createChildProbes()
      val testId = TestIds.test1

      val actor = spawnActor(testId, queueProbe, childProbes = Some(childProbes))

      // Initialize and transition to Loading state
      initialize(actor, testId, replyProbe, queueProbe)
      startTest(actor, testId, Buckets.bucket1, Some(TestTypes.functional), replyProbe, queueProbe)
      childProbes.blockStorageProbe.expectMessageType[BlockStorageCommands.Initialize]

      // Cancel triggers TrnShutdown internally
      actor ! InCancelRequest(testId, replyProbe.ref)
      replyProbe.expectMessageType[TestCancelledResponse]
      queueProbe.expectMessageType[TestStopping]

      // All children should receive Stop
      childProbes.blockStorageProbe.expectMessageType[BlockStorageCommands.Stop.type]
      childProbes.vaultProbe.expectMessageType[VaultCommands.Stop.type]
      childProbes.cucumberProbe.expectMessageType[CucumberExecutionCommands.Stop.type]
      childProbes.producerProbe.expectMessageType[KafkaProducerCommands.Stop.type]
      childProbes.consumerProbe.expectMessageType[KafkaConsumerCommands.Stop.type]
    }

    "notify queue on TrnShutdown before stopping" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test2

      val actor = spawnActor(testId, queueProbe)

      // Initialize the actor
      initialize(actor, testId, replyProbe, queueProbe)

      // Cancel triggers TrnShutdown
      actor ! InCancelRequest(testId, replyProbe.ref)
      replyProbe.expectMessageType[TestCancelledResponse]

      // Queue should receive TestStopping before actor stops
      queueProbe.expectMessageType[TestStopping]
    }

    "handle poison pill timer in Setup state" in {
      val queueProbe = createQueueProbe()
      val replyProbe = createServiceProbe()
      val testId = TestIds.test3
      val config = ConfigurationFixtures.shortTimerCoreConfig

      val actor = spawnActor(testId, queueProbe, config)

      initialize(actor, testId, replyProbe, queueProbe)

      queueProbe.expectMessageType[TestStopping](3.seconds)
    }
  }
}
