package io.distia.probe
package core
package actors

import java.time.Instant
import java.util.UUID

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, SupervisorStrategy}

import io.distia.probe.core.builder.ServiceFunctionsContext

import config.CoreConfig
import models.*
import models.TestExecutionCommands.*
import models.{InitializeTestResponse, QueueCommands, StartTestResponse, TestCancelledResponse, TestStatusResponse}

/*
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║ ⚠️  SECURITY WARNING - SENSITIVE DATA HANDLING                       ║
 * ╠══════════════════════════════════════════════════════════════════════╣
 * ║ This actor handles SecurityDirective containing:                     ║
 * ║ - OAuth client IDs and secrets                                       ║
 * ║ - Vault credentials                                                  ║
 * ║ - Kafka authentication tokens                                        ║
 * ║                                                                      ║
 * ║ ⚠️  DO NOT LOG SecurityDirective OR ANY DERIVED CREDENTIALS          ║
 * ║                                                                      ║
 * ║ All logging must:                                                    ║
 * ║ 1. Exclude SecurityDirective objects from log statements            ║
 * ║ 2. Redact credentials in error messages                             ║
 * ║ 3. Use testId for correlation, never credentials                    ║
 * ║                                                                      ║
 * ║ Future: Security agent will enforce compliance                      ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
private[core] object TestExecutionActor {

  type BlockStorageFactory = ActorContext[TestExecutionCommand] => ActorRef[BlockStorageCommands.BlockStorageCommand]
  type VaultFactory = ActorContext[TestExecutionCommand] => ActorRef[VaultCommands.VaultCommand]
  type CucumberFactory = ActorContext[TestExecutionCommand] => ActorRef[CucumberExecutionCommands.CucumberExecutionCommand]
  type KafkaProducerFactory = ActorContext[TestExecutionCommand] => ActorRef[KafkaProducerCommands.KafkaProducerCommand]
  type KafkaConsumerFactory = ActorContext[TestExecutionCommand] => ActorRef[KafkaConsumerCommands.KafkaConsumerCommand]

  /**
   * Factory method for TestExecutionActor FSM
   *
   * @param testId UUID of the test
   * @param queueActor Reference to parent QueueActor
   * @param serviceFunctions Service functions context (vault, storage)
   * @param coreConfig Configuration containing timeouts and other settings
   * @param blockStorageFactory Optional factory for BlockStorageActor (default: spawn real actor)
   * @param vaultFactory Optional factory for VaultActor (default: spawn real actor)
   * @param cucumberFactory Optional factory for CucumberExecutionActor (default: spawn real actor)
   * @param producerFactory Optional factory for KafkaProducerActor (default: spawn real actor)
   * @param consumerFactory Optional factory for KafkaConsumerActor (default: spawn real actor)
   * @return Behavior for TestExecutionCommand
   */
  def apply(
    testId: UUID,
    queueActor: ActorRef[QueueCommands.QueueCommand],
    serviceFunctions: ServiceFunctionsContext,
    coreConfig: CoreConfig,
    blockStorageFactory: Option[BlockStorageFactory] = None,
    vaultFactory: Option[VaultFactory] = None,
    cucumberFactory: Option[CucumberFactory] = None,
    producerFactory: Option[KafkaProducerFactory] = None,
    consumerFactory: Option[KafkaConsumerFactory] = None
  ): Behavior[TestExecutionCommand] = {
    Behaviors.supervise {
      Behaviors.withTimers[TestExecutionCommand] { timers =>
        Behaviors.setup { context =>
          val resolvedBlockStorageFactory = blockStorageFactory.getOrElse(defaultBlockStorageFactory(testId, serviceFunctions))
          val resolvedVaultFactory = vaultFactory.getOrElse(defaultVaultFactory(testId, serviceFunctions))
          val resolvedCucumberFactory = cucumberFactory.getOrElse(defaultCucumberFactory(testId))
          val resolvedProducerFactory = producerFactory.getOrElse(defaultProducerFactory(testId))
          val resolvedConsumerFactory = consumerFactory.getOrElse(defaultConsumerFactory(testId))

          setupBehavior(
            testId,
            queueActor,
            timers,
            TestExecutionData.Uninitialized,
            coreConfig,
            context,
            resolvedBlockStorageFactory,
            resolvedVaultFactory,
            resolvedCucumberFactory,
            resolvedProducerFactory,
            resolvedConsumerFactory
          )
        }
      }
    }.onFailure[Throwable](SupervisorStrategy.stop)
  }

  /**
   * Default production factory for BlockStorageActor with supervision
   *
   * Extracts the storage service functions from ServiceFunctionsContext
   * and passes them to BlockStorageActor.
   */
  def defaultBlockStorageFactory(testId: UUID, serviceFunctions: ServiceFunctionsContext): BlockStorageFactory = { ctx =>
    ctx.spawn(
      Behaviors.supervise(
        BlockStorageActor(
          testId,
          ctx.self,
          serviceFunctions.storage
        )
      ).onFailure[Exception](SupervisorStrategy.stop),
      s"block-storage-$testId"
    )
  }

  /**
   * Default production factory for VaultActor with supervision
   *
   * Extracts the fetchSecurityDirectives function from ServiceFunctionsContext
   * and passes it to VaultActor - this is the key pattern!
   */
  def defaultVaultFactory(testId: UUID, serviceFunctions: ServiceFunctionsContext): VaultFactory = { ctx =>
    ctx.spawn(
      Behaviors.supervise(VaultActor(testId, ctx.self, serviceFunctions.vault))
        .onFailure[Exception](SupervisorStrategy.stop),
      s"vault-$testId"
    )
  }

  /**
   * Default production factory for CucumberExecutionActor with supervision
   */
  def defaultCucumberFactory(testId: UUID): CucumberFactory = { ctx =>
    ctx.spawn(
      Behaviors.supervise(CucumberExecutionActor(testId, ctx.self))
        .onFailure[Exception](SupervisorStrategy.stop),
      s"cucumber-$testId"
    )
  }

  /**
   * Default production factory for KafkaProducerActor with supervision
   */
  def defaultProducerFactory(testId: UUID): KafkaProducerFactory = { ctx =>
    ctx.spawn(
      Behaviors.supervise(KafkaProducerActor(testId, ctx.self))
        .onFailure[Exception](SupervisorStrategy.stop),
      s"producer-$testId"
    )
  }

  /**
   * Default production factory for KafkaConsumerActor with supervision
   */
  def defaultConsumerFactory(testId: UUID): KafkaConsumerFactory = { ctx =>
    ctx.spawn(
      Behaviors.supervise(KafkaConsumerActor(testId, ctx.self))
        .onFailure[Exception](SupervisorStrategy.stop),
      s"consumer-$testId"
    )
  }

  /**
   * Setup State Behavior
   * Handles: InInitializeTestRequest, TrnSetup, InStartTestRequest, InCancelRequest, GetStatus, timer expiry
   */
  def setupBehavior(
    testId: UUID,
    queueActor: ActorRef[QueueCommands.QueueCommand],
    timers: TimerScheduler[TestExecutionCommand],
    data: TestExecutionData,
    coreConfig: CoreConfig,
    context: ActorContext[TestExecutionCommand],
    blockStorageFactory: BlockStorageFactory,
    vaultFactory: VaultFactory,
    cucumberFactory: CucumberFactory,
    producerFactory: KafkaProducerFactory,
    consumerFactory: KafkaConsumerFactory
  ): Behavior[TestExecutionCommand] = {
    Behaviors.receiveMessage {
      case InInitializeTestRequest(reqTestId, replyTo) =>
        context.log.info(s"Handling InInitializeTestRequest for test $reqTestId")
        val testData: TestExecutionData.TestData = TestExecutionData.TestData(
          replyTo = replyTo,
          testId = reqTestId,
          testType = "",
          securityDirectives = List.empty,
          testResult = null
        )
        context.self ! TrnSetup
        setupBehavior(testId, queueActor, timers, testData, coreConfig, context, blockStorageFactory, vaultFactory, cucumberFactory, producerFactory, consumerFactory)

      case TrnSetup =>
        data match {
          case testData: TestExecutionData.TestData =>
            context.log.info(s"Processing TrnSetup for test $testId")
            timers.startSingleTimer("poison-pill", TrnPoisonPill, coreConfig.setupStateTimeout)
            testData.replyTo ! InitializeTestResponse(testId, "Test initialized")
            queueActor ! QueueCommands.TestInitialized(testId)
            Behaviors.same

          case _ =>
            context.log.warn(s"TrnSetup received but data is not TestData")
            Behaviors.same
        }

      case InStartTestRequest(reqTestId, bucket, testType, replyTo) =>
        data match {
          case testData: TestExecutionData.TestData =>
            context.log.info(s"Handling InStartTestRequest for test $reqTestId with bucket $bucket, testType $testType")
            val updatedData: TestExecutionData.TestData = testData.copy(
              bucket = Option(bucket),
              testType = testType.getOrElse(""),
              replyTo = replyTo
            )
            context.self ! TrnLoading
            loadingBehavior(testId, queueActor, timers, updatedData, coreConfig, context, 0, blockStorageFactory, vaultFactory, cucumberFactory, producerFactory, consumerFactory)

          case _ =>
            context.log.warn(s"InStartTestRequest received but data is not TestData")
            Behaviors.same
        }

      case InCancelRequest(reqTestId, replyTo) =>
        context.log.info(s"Handling InCancelRequest for test $reqTestId in Setup state")
        timers.cancel("poison-pill")
        queueActor ! QueueCommands.TestStopping(testId)
        replyTo ! TestCancelledResponse(testId, cancelled = true, message = None)
        context.self ! TrnShutdown
        data match {
          case testData: TestExecutionData.TestData =>
            shuttingDownBehavior(testId, queueActor, testData, context)
          case _ =>
            shuttingDownBehavior(testId, queueActor, null, context)
        }

      case GetStatus(reqTestId, replyTo) =>
        data match {
          case testData: TestExecutionData.TestData =>
            context.log.debug(s"Handling GetStatus for test $reqTestId in Setup state")
            replyTo ! createStatusResponse(testId, "Setup", testData)
            Behaviors.same

          case _ =>
            context.log.warn(s"GetStatus received but data is not TestData")
            Behaviors.same
        }

      case TrnPoisonPill =>
        context.log.info(s"Poison pill timer expired in Setup state for test $testId")
        context.self ! TrnShutdown
        data match {
          case testData: TestExecutionData.TestData =>
            shuttingDownBehavior(testId, queueActor, testData, context)
          case _ =>
            shuttingDownBehavior(testId, queueActor, null, context)
        }

      case other =>
        context.log.warn(s"Unexpected message in Setup state: $other")
        Behaviors.same
    }
  }

  /**
   * Loading State Behavior
   * Handles: TrnLoading, BlockStorageFetched, SecurityFetched, ChildGoodToGo, GetStatus, InCancelRequest, timer expiry
   */
  def loadingBehavior(
    testId: UUID,
    queueActor: ActorRef[QueueCommands.QueueCommand],
    timers: TimerScheduler[TestExecutionCommand],
    data: TestExecutionData.TestData,
    coreConfig: CoreConfig,
    context: ActorContext[TestExecutionCommand],
    childGoodToGoCount: Int,
    blockStorageFactory: BlockStorageFactory,
    vaultFactory: VaultFactory,
    cucumberFactory: CucumberFactory,
    producerFactory: KafkaProducerFactory,
    consumerFactory: KafkaConsumerFactory
  ): Behavior[TestExecutionCommand] = {
    Behaviors.receiveMessage {
      case TrnLoading =>
        context.log.info(s"Processing TrnLoading for test $testId")
        timers.cancel("poison-pill")
        timers.startSingleTimer("poison-pill", TrnPoisonPill, coreConfig.loadingStateTimeout)

        val (blockStorageActor, vaultActor, cucumberActor, producerActor, consumerActor): (
          ActorRef[BlockStorageCommands.BlockStorageCommand],
          ActorRef[VaultCommands.VaultCommand],
          ActorRef[CucumberExecutionCommands.CucumberExecutionCommand],
          ActorRef[KafkaProducerCommands.KafkaProducerCommand],
          ActorRef[KafkaConsumerCommands.KafkaConsumerCommand]
        ) = spawnChildren(context, blockStorageFactory, vaultFactory, cucumberFactory, producerFactory, consumerFactory)

        context.watch(blockStorageActor)
        context.watch(vaultActor)
        context.watch(cucumberActor)
        context.watch(producerActor)
        context.watch(consumerActor)

        val updatedData: TestExecutionData.TestData = data.copy(
          blockStorage = Some(blockStorageActor),
          vault = Some(vaultActor),
          executor = Some(cucumberActor),
          producer = Some(producerActor),
          consumer = Some(consumerActor)
        )

        blockStorageActor ! BlockStorageCommands.Initialize(data.bucket)

        data.replyTo ! StartTestResponse(testId, accepted = true, Some(data.testType), "Test loading initiated")
        queueActor ! QueueCommands.TestLoading(testId)

        loadingBehavior(testId, queueActor, timers, updatedData, coreConfig, context, childGoodToGoCount, blockStorageFactory, vaultFactory, cucumberFactory, producerFactory, consumerFactory)

      case BlockStorageFetched(reqTestId, blockStorageDirective) =>
        context.log.info(s"Handling BlockStorageFetched for test $reqTestId")
        val updatedData: TestExecutionData.TestData = data.copy(blockStorageDirective = Some(blockStorageDirective))

        data.vault.foreach(_ ! VaultCommands.Initialize(blockStorageDirective))

        loadingBehavior(testId, queueActor, timers, updatedData, coreConfig, context, childGoodToGoCount, blockStorageFactory, vaultFactory, cucumberFactory, producerFactory, consumerFactory)

      case SecurityFetched(reqTestId, securityDirectives) =>
        context.log.info(s"Handling SecurityFetched for test $reqTestId")
        val updatedData: TestExecutionData.TestData = data.copy(securityDirectives = securityDirectives)

        data.blockStorageDirective.foreach { bsd =>
          data.executor.foreach(_ ! CucumberExecutionCommands.Initialize(bsd))
          data.producer.foreach(_ ! KafkaProducerCommands.Initialize(bsd, securityDirectives))
          data.consumer.foreach(_ ! KafkaConsumerCommands.Initialize(bsd, securityDirectives))
        }

        loadingBehavior(testId, queueActor, timers, updatedData, coreConfig, context, childGoodToGoCount, blockStorageFactory, vaultFactory, cucumberFactory, producerFactory, consumerFactory)

      case ChildGoodToGo(reqTestId, child) =>
        val newCount: Int = childGoodToGoCount + 1
        context.log.info(s"ChildGoodToGo received from ${child.path.name}, count: $newCount/5")

        if newCount == 5 then
          context.log.info(s"All 5 children ready for test $testId, transitioning to Loaded")
          context.self ! TrnLoaded
          loadedBehavior(testId, queueActor, timers, data, coreConfig, context)
        else
          loadingBehavior(testId, queueActor, timers, data, coreConfig, context, newCount, blockStorageFactory, vaultFactory, cucumberFactory, producerFactory, consumerFactory)

      case GetStatus(reqTestId, replyTo) =>
        context.log.debug(s"Handling GetStatus for test $reqTestId in Loading state")
        replyTo ! createStatusResponse(testId, "Loading", data)
        Behaviors.same

      case InCancelRequest(reqTestId, replyTo) =>
        context.log.info(s"Handling InCancelRequest for test $reqTestId in Loading state")
        timers.cancel("poison-pill")
        queueActor ! QueueCommands.TestStopping(testId)
        replyTo ! TestCancelledResponse(testId, cancelled = true, message = None)
        context.self ! TrnShutdown
        shuttingDownBehavior(testId, queueActor, data, context)

      case TrnPoisonPill =>
        context.log.info(s"Poison pill timer expired in Loading state for test $testId")
        context.self ! TrnShutdown
        shuttingDownBehavior(testId, queueActor, data, context)

      case TrnException(exception) =>
        context.log.error(s"Exception occurred in Loading state for test $testId: ${exception.asInstanceOf[Exception].getMessage}", exception)
        context.self ! TrnException(exception)
        exceptionBehavior(testId, queueActor, timers, data, exception, coreConfig, context)

      case other =>
        context.log.warn(s"Unexpected message in Loading state: $other")
        Behaviors.same
    }
  }

  /**
   * Loaded State Behavior
   * Handles: TrnLoaded, StartTesting, GetStatus, InCancelRequest
   * Note: NO poison pill timer (bad UX, queue may be large)
   */
  def loadedBehavior(
    testId: UUID,
    queueActor: ActorRef[QueueCommands.QueueCommand],
    timers: TimerScheduler[TestExecutionCommand],
    data: TestExecutionData.TestData,
    coreConfig: CoreConfig,
    context: ActorContext[TestExecutionCommand]
  ): Behavior[TestExecutionCommand] = {
    Behaviors.receiveMessage {
      case TrnLoaded =>
        context.log.info(s"Processing TrnLoaded for test $testId")
        timers.cancel("poison-pill")
        queueActor ! QueueCommands.TestLoaded(testId)
        Behaviors.same

      case StartTesting(reqTestId) =>
        context.log.info(s"Handling StartTesting for test $reqTestId")
        context.self ! TrnTesting
        testingBehavior(testId, queueActor, timers, data, coreConfig, context)

      case GetStatus(reqTestId, replyTo) =>
        context.log.debug(s"Handling GetStatus for test $reqTestId in Loaded state")
        replyTo ! createStatusResponse(testId, "Loaded", data)
        Behaviors.same

      case InCancelRequest(reqTestId, replyTo) =>
        context.log.info(s"Handling InCancelRequest for test $reqTestId in Loaded state")
        queueActor ! QueueCommands.TestStopping(testId)
        replyTo ! TestCancelledResponse(testId, cancelled = true, message = None)
        context.self ! TrnShutdown
        shuttingDownBehavior(testId, queueActor, data, context)

      case TrnException(exception) =>
        context.log.error(s"Exception occurred in Loaded state for test $testId: ${exception.asInstanceOf[Exception].getMessage}", exception)
        context.self ! TrnException(exception)
        exceptionBehavior(testId, queueActor, timers, data, exception, coreConfig, context)

      case other =>
        context.log.warn(s"Unexpected message in Loaded state: $other")
        Behaviors.same
    }
  }

  /**
   * Testing State Behavior
   * Handles: TrnTesting, InCancelRequest, TestComplete, BlockStorageUploadComplete, GetStatus
   * Note: NO poison pill timer (bad UX, tests may run long)
   */
  def testingBehavior(
    testId: UUID,
    queueActor: ActorRef[QueueCommands.QueueCommand],
    timers: TimerScheduler[TestExecutionCommand],
    data: TestExecutionData.TestData,
    coreConfig: CoreConfig,
    context: ActorContext[TestExecutionCommand]
  ): Behavior[TestExecutionCommand] = {
    Behaviors.receiveMessage {
      case TrnTesting =>
        context.log.info(s"Processing TrnTesting for test $testId")
        val updatedData: TestExecutionData.TestData = data.copy(startTime = Some(Instant.now()))

        data.executor.foreach(_ ! CucumberExecutionCommands.StartTest)
        queueActor ! QueueCommands.TestStarted(testId)

        testingBehavior(testId, queueActor, timers, updatedData, coreConfig, context)

      case InCancelRequest(reqTestId, replyTo) =>
        context.log.warn(s"Cannot cancel test $reqTestId - test is currently executing")
        replyTo ! TestCancelledResponse(
          testId,
          cancelled = false,
          message = Some("Cannot cancel, test is currently executing")
        )
        Behaviors.same

      case TestComplete(reqTestId, result) =>
        context.log.info(s"Handling TestComplete for test $reqTestId")
        val updatedData: TestExecutionData.TestData = data.copy(testResult = result)

        data.blockStorage.foreach(_ ! BlockStorageCommands.LoadToBlockStorage(result))

        testingBehavior(testId, queueActor, timers, updatedData, coreConfig, context)

      case BlockStorageUploadComplete(reqTestId) =>
        context.log.info(s"Evidence uploaded successfully for test $reqTestId")
        val updatedData: TestExecutionData.TestData = data.copy(
          endTime = Some(Instant.now()),
          success = Some(true)
        )

        context.self ! TrnComplete
        completedBehavior(testId, queueActor, timers, updatedData, coreConfig, context)

      case GetStatus(reqTestId, replyTo) =>
        context.log.debug(s"Handling GetStatus for test $reqTestId in Testing state")
        replyTo ! createStatusResponse(testId, "Testing", data)
        Behaviors.same

      case TrnException(exception) =>
        context.log.error(s"Exception occurred in Testing state for test $testId: ${exception.asInstanceOf[Exception].getMessage}", exception)
        val updatedData: TestExecutionData.TestData = data.copy(
          endTime = Some(Instant.now()),
          success = Some(false)
        )
        context.self ! TrnException(exception)
        exceptionBehavior(testId, queueActor, timers, updatedData, exception, coreConfig, context)

      case other =>
        context.log.warn(s"Unexpected message in Testing state: $other")
        Behaviors.same
    }
  }

  /**
   * Completed State Behavior
   * Handles: TrnComplete, InCancelRequest, GetStatus, timer expiry
   */
  def completedBehavior(
    testId: UUID,
    queueActor: ActorRef[QueueCommands.QueueCommand],
    timers: TimerScheduler[TestExecutionCommand],
    data: TestExecutionData.TestData,
    coreConfig: CoreConfig,
    context: ActorContext[TestExecutionCommand]
  ): Behavior[TestExecutionCommand] = {
    Behaviors.receiveMessage {
      case TrnComplete =>
        context.log.info(s"Processing TrnComplete for test $testId")
        timers.startSingleTimer("poison-pill", TrnPoisonPill, coreConfig.completedStateTimeout)
        queueActor ! QueueCommands.TestCompleted(testId)
        Behaviors.same

      case InCancelRequest(reqTestId, replyTo) =>
        context.log.warn(s"Cannot cancel test $reqTestId - test already completed")
        replyTo ! TestCancelledResponse(
          testId,
          cancelled = false,
          message = Some("Test already completed, cannot cancel")
        )
        Behaviors.same

      case GetStatus(reqTestId, replyTo) =>
        context.log.debug(s"Handling GetStatus for test $reqTestId in Completed state")
        replyTo ! createStatusResponse(testId, "Completed", data)
        Behaviors.same

      case TrnPoisonPill =>
        context.log.info(s"Poison pill timer expired in Completed state for test $testId")
        context.self ! TrnShutdown
        shuttingDownBehavior(testId, queueActor, data, context)

      case TrnException(exception) =>
        context.log.error(s"Exception occurred in Completed state for test $testId: ${exception.asInstanceOf[Exception].getMessage}", exception)
        context.self ! TrnException(exception)
        exceptionBehavior(testId, queueActor, timers, data, exception, coreConfig, context)

      case other =>
        context.log.warn(s"Unexpected message in Completed state: $other")
        Behaviors.same
    }
  }

  /**
   * Exception State Behavior
   * Handles: TrnException, InCancelRequest, GetStatus, timer expiry
   */
  def exceptionBehavior(
    testId: UUID,
    queueActor: ActorRef[QueueCommands.QueueCommand],
    timers: TimerScheduler[TestExecutionCommand],
    data: TestExecutionData.TestData,
    exception: ProbeExceptions,
    coreConfig: CoreConfig,
    context: ActorContext[TestExecutionCommand]
  ): Behavior[TestExecutionCommand] = {
    Behaviors.receiveMessage {
      case TrnException(receivedException) =>
        val exceptionMessage = receivedException.asInstanceOf[Exception].getMessage
        context.log.error(s"Processing TrnException for test $testId: $exceptionMessage", receivedException)
        timers.cancelAll()
        timers.startSingleTimer("poison-pill", TrnPoisonPill, coreConfig.exceptionStateTimeout)

        val updatedData: TestExecutionData.TestData = data.copy(error = Some(receivedException))
        queueActor ! QueueCommands.TestException(testId, receivedException)

        exceptionBehavior(testId, queueActor, timers, updatedData, receivedException, coreConfig, context)

      case InCancelRequest(reqTestId, replyTo) =>
        context.log.warn(s"Cannot cancel test $reqTestId - test in exception state")
        replyTo ! TestCancelledResponse(
          testId,
          cancelled = false,
          message = Some("Test in exception state, cleanup in progress")
        )
        Behaviors.same

      case GetStatus(reqTestId, replyTo) =>
        context.log.debug(s"Handling GetStatus for test $reqTestId in Exception state")
        replyTo ! createStatusResponse(testId, "Exception", data)
        Behaviors.same

      case TrnPoisonPill =>
        context.log.info(s"Poison pill timer expired in Exception state for test $testId")
        context.self ! TrnShutdown
        shuttingDownBehavior(testId, queueActor, data, context)

      case other =>
        context.log.warn(s"Unexpected message in Exception state: $other")
        Behaviors.same
    }
  }

  /**
   * ShuttingDown State Behavior
   * Handles: TrnShutdown, TrnPoisonPill
   *
   * Immediate shutdown logic:
   * - Fire-and-forget shutdown: Tell all children to stop (if any exist)
   * - Stop immediately without waiting for child responses
   *
   * Design Note: We use fire-and-forget shutdown instead of coordinated shutdown
   * because child actors are stubs/incomplete implementations that may not respond
   * to Stop commands. The actor stops immediately after sending Stop to all children.
   */
  def shuttingDownBehavior(
    testId: UUID,
    queueActor: ActorRef[QueueCommands.QueueCommand],
    data: TestExecutionData.TestData,
    context: ActorContext[TestExecutionCommand]
  ): Behavior[TestExecutionCommand] = {
    Behaviors.receiveMessage {
      case TrnShutdown =>
        context.log.info(s"Processing TrnShutdown for test $testId")
        queueActor ! QueueCommands.TestStopping(testId)

        Option(data).foreach { d =>
          stopAllChildren(d)
          context.log.info(s"Sent Stop to all children for test $testId")
        }

        context.log.info(s"Stopping TestExecutionActor for test $testId")
        Behaviors.stopped

      case TrnPoisonPill =>
        context.log.info(s"Processing TrnPoisonPill for test $testId - stopping actor")
        Behaviors.stopped

      case other =>
        context.log.warn(s"Unexpected message in ShuttingDown state: $other")
        Behaviors.same
    }
  }

  /**
   * Helper: Spawn all 5 child actors using factories
   */
  def spawnChildren(
    context: ActorContext[TestExecutionCommand],
    blockStorageFactory: BlockStorageFactory,
    vaultFactory: VaultFactory,
    cucumberFactory: CucumberFactory,
    producerFactory: KafkaProducerFactory,
    consumerFactory: KafkaConsumerFactory
  ): (
    ActorRef[BlockStorageCommands.BlockStorageCommand],
    ActorRef[VaultCommands.VaultCommand],
    ActorRef[CucumberExecutionCommands.CucumberExecutionCommand],
    ActorRef[KafkaProducerCommands.KafkaProducerCommand],
    ActorRef[KafkaConsumerCommands.KafkaConsumerCommand]
  ) = {
    val blockStorageActor: ActorRef[BlockStorageCommands.BlockStorageCommand] = blockStorageFactory(context)
    val vaultActor: ActorRef[VaultCommands.VaultCommand] = vaultFactory(context)
    val cucumberActor: ActorRef[CucumberExecutionCommands.CucumberExecutionCommand] = cucumberFactory(context)
    val producerActor: ActorRef[KafkaProducerCommands.KafkaProducerCommand] = producerFactory(context)
    val consumerActor: ActorRef[KafkaConsumerCommands.KafkaConsumerCommand] = consumerFactory(context)

    (blockStorageActor, vaultActor, cucumberActor, producerActor, consumerActor)
  }

  /**
   * Helper: Tell all children to stop
   */
  def stopAllChildren(data: TestExecutionData.TestData): Unit = {
    data.blockStorage.foreach(_ ! BlockStorageCommands.Stop)
    data.vault.foreach(_ ! VaultCommands.Stop)
    data.executor.foreach(_ ! CucumberExecutionCommands.Stop)
    data.producer.foreach(_ ! KafkaProducerCommands.Stop)
    data.consumer.foreach(_ ! KafkaConsumerCommands.Stop)
  }

  /**
   * Helper: Identify which child actor failed by comparing ActorRef paths
   * Returns a string identifier for the child actor type
   */
  def identifyChildActor(child: ActorRef[_], data: TestExecutionData.TestData): String = {
    if data.blockStorage.exists(_.path == child.path) then "BlockStorage"
    else if data.vault.exists(_.path == child.path) then "Vault"
    else if data.executor.exists(_.path == child.path) then "Cucumber"
    else if data.producer.exists(_.path == child.path) then "Producer"
    else if data.consumer.exists(_.path == child.path) then "Consumer"
    else "Unknown"
  }

  /**
   * Helper: Create TestStatusResponse from current state and data
   */
  def createStatusResponse(
    testId: UUID,
    state: String,
    data: TestExecutionData.TestData
  ): TestStatusResponse = {
    TestStatusResponse(
      testId = testId,
      state = state,
      bucket = data.bucket,
      testType = Some(data.testType),
      startTime = data.startTime.map(_.toString),
      endTime = data.endTime.map(_.toString),
      success = data.success,
      error = data.error.map {
        case e: Exception => e.getMessage
        case other => other.toString
      }
    )
  }
}
