package io.distia.probe
package core
package glue.world
package fixtures.actors

import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import config.CoreConfig
import models.*
import builder.ServiceFunctionsContext
import core.fixtures.{ServiceInterfaceResponsesFixture, TestHarnessFixtures}

import java.util.UUID
import scala.concurrent.duration.*

/**
 * Fixture methods for TestExecutionActor component tests.
 *
 * Provides:
 * - TestExecutionActor spawning (with/without child actor mocks)
 * - Message sending and expectation helpers
 * - TestExecutionResult factory (domain model for test results)
 *
 * Design: Self-type ensures access to ActorWorld members (testKit, probes, etc.)
 *
 * Does NOT include:
 * - ServiceResponse models (use ServiceInterfaceResponsesFixture from core.fixtures)
 * - KafkaSecurityDirective (use KafkaSecurityDirectiveFixtures from core.fixtures)
 * - BlockStorageDirective (use BlockStorageDirectiveFixtures from core.fixtures)
 * - Actor commands (internal to actors, not needed in BDD steps)
 *
 * Used by: TestExecutionActorSteps
 * Architecture: Trait composition pattern (mixin to ActorWorld)
 *
 * Thread Safety: NOT thread-safe. Cucumber runs scenarios sequentially.
 */
private[core] trait TestExecutionActorFixtures 
  extends ServiceInterfaceResponsesFixture
  with TestHarnessFixtures {
  this: ActorWorld =>  // Self-type: requires mixing into ActorWorld

  // ==========================================================================
  // Actor Spawning
  // ==========================================================================

  /**
   * Spawn TestExecutionActor with real child actors (no mocks).
   *
   * @param testId Test UUID
   * @param config CoreConfig for TestExecutionActor
   * @return Spawned TestExecutionActor reference
   */
  def spawnTestExecutionActor(
    testId: UUID,
    config: CoreConfig
  ): ActorRef[TestExecutionCommands.TestExecutionCommand] = {
    import actors.TestExecutionActor

    this.serviceConfig = Some(config)
    this.testId = testId

    // Create stub ServiceFunctionsContext (not used in component tests)
    val stubServiceFunctionsContext: ServiceFunctionsContext = ServiceFunctionsContext(
      vaultFunctions = builder.VaultServiceFunctions(
        fetchSecurityDirectives = getSuccessfulSecurityDirectivesFetchFunction(List.empty)
      ),
      storageFunctions = builder.StorageServiceFunctions(
        fetchFromBlockStorage = getSuccessfulBlockStorageFetchFunction(createBlockStorageDirective()),
        loadToBlockStorage = getSuccessfulBlockStorageLoadFunction
      )
    )

    val actor: ActorRef[TestExecutionCommands.TestExecutionCommand] = testKit.spawn(
      TestExecutionActor(
        testId = testId,
        queueActor = queueProbe.ref,
        serviceFunctions = stubServiceFunctionsContext,
        coreConfig = config
      ),
      s"test-execution-actor-$testId"
    )
    testExecutionActor = Some(actor)
    actor
  }

  /**
   * Spawn TestExecutionActor with child actor mocks (TestProbes).
   *
   * Creates TestProbes for all child actors (BlockStorage, Vault, Cucumber, Producer, Consumer)
   * and provides them as factories to TestExecutionActor.
   *
   * @param testId Test UUID
   * @param config CoreConfig for TestExecutionActor
   * @return Spawned TestExecutionActor reference
   */
  def spawnTestExecutionActorWithMocks(
    testId: UUID,
    config: CoreConfig
  ): ActorRef[TestExecutionCommands.TestExecutionCommand] = {
    import actors.TestExecutionActor

    this.serviceConfig = Some(config)
    this.testId = testId

    // Create child actor probes
    createChildProbes()

    // Create mock factories that return TestProbe refs
    val blockStorageFactory: TestExecutionActor.BlockStorageFactory = _ => blockStorageProbe.get.ref
    val vaultFactory: TestExecutionActor.VaultFactory = _ => vaultProbe.get.ref
    val cucumberFactory: TestExecutionActor.CucumberFactory = _ => cucumberProbe.get.ref
    val producerFactory: TestExecutionActor.KafkaProducerFactory = _ => producerProbe.get.ref
    val consumerFactory: TestExecutionActor.KafkaConsumerFactory = _ => consumerProbe.get.ref

    // Create stub ServiceFunctionsContext (uses fixture methods)
    val stubServiceFunctionsContext: ServiceFunctionsContext = ServiceFunctionsContext(
      vaultFunctions = builder.VaultServiceFunctions(
        fetchSecurityDirectives = getFailedSecurityDirectivesFetchFunction
      ),
      storageFunctions = builder.StorageServiceFunctions(
        fetchFromBlockStorage = getFailedBlockStorageFetchFunction,
        loadToBlockStorage = getFailedBlockStorageLoadFunction
      )
    )

    val actor: ActorRef[TestExecutionCommands.TestExecutionCommand] = testKit.spawn(
      TestExecutionActor(
        testId = testId,
        queueActor = queueProbe.ref,
        serviceFunctions = stubServiceFunctionsContext,
        coreConfig = config,
        blockStorageFactory = Some(blockStorageFactory),
        vaultFactory = Some(vaultFactory),
        cucumberFactory = Some(cucumberFactory),
        producerFactory = Some(producerFactory),
        consumerFactory = Some(consumerFactory)
      ),
      s"test-execution-actor-mocks-$testId"
    )
    testExecutionActor = Some(actor)
    actor
  }

  // ==========================================================================
  // Message Sending
  // ==========================================================================

  /**
   * Send message to TestExecutionActor.
   *
   * @param command TestExecutionCommand to send
   */
  def sendMessageToTestExecutionActor(command: TestExecutionCommands.TestExecutionCommand): Unit = {
    testExecutionActor match
      case Some(actor) => actor ! command
      case None => throw new IllegalStateException("TestExecutionActor not spawned")
  }

  // ==========================================================================
  // Child Actor Message Expectations
  // ==========================================================================

  /**
   * Expect message from BlockStorageActor probe.
   *
   * @tparam T Message type
   * @param timeout Max wait time (default: 3 seconds)
   * @return Received message
   */
  def expectBlockStorageMessage[T <: BlockStorageCommands.BlockStorageCommand](
    timeout: FiniteDuration = defaultTimeout
  ): T = {
    blockStorageProbe match
      case Some(probe) => probe.receiveMessage(timeout).asInstanceOf[T]
      case None => throw new IllegalStateException("BlockStorageProbe not created. Call createChildProbes() first.")
  }

  /**
   * Expect message from VaultActor probe.
   *
   * @tparam T Message type
   * @param timeout Max wait time (default: 3 seconds)
   * @return Received message
   */
  def expectVaultMessage[T <: VaultCommands.VaultCommand](
    timeout: FiniteDuration = defaultTimeout
  ): T = {
    vaultProbe match
      case Some(probe) => probe.receiveMessage(timeout).asInstanceOf[T]
      case None => throw new IllegalStateException("VaultProbe not created. Call createChildProbes() first.")
  }

  /**
   * Expect message from CucumberExecutionActor probe.
   *
   * @tparam T Message type
   * @param timeout Max wait time (default: 3 seconds)
   * @return Received message
   */
  def expectCucumberMessage[T <: CucumberExecutionCommands.CucumberExecutionCommand](
    timeout: FiniteDuration = defaultTimeout
  ): T = {
    cucumberProbe match
      case Some(probe) => probe.receiveMessage(timeout).asInstanceOf[T]
      case None => throw new IllegalStateException("CucumberProbe not created. Call createChildProbes() first.")
  }

  /**
   * Expect message from KafkaProducerActor probe.
   *
   * @tparam T Message type
   * @param timeout Max wait time (default: 3 seconds)
   * @return Received message
   */
  def expectProducerMessage[T <: KafkaProducerCommands.KafkaProducerCommand](
    timeout: FiniteDuration = defaultTimeout
  ): T = {
    producerProbe match
      case Some(probe) => probe.receiveMessage(timeout).asInstanceOf[T]
      case None => throw new IllegalStateException("ProducerProbe not created. Call createChildProbes() first.")
  }

  /**
   * Expect message from KafkaConsumerActor probe.
   *
   * @tparam T Message type
   * @param timeout Max wait time (default: 3 seconds)
   * @return Received message
   */
  def expectConsumerMessage[T <: KafkaConsumerCommands.KafkaConsumerCommand](
    timeout: FiniteDuration = defaultTimeout
  ): T = {
    consumerProbe match
      case Some(probe) => probe.receiveMessage(timeout).asInstanceOf[T]
      case None => throw new IllegalStateException("ConsumerProbe not created. Call createChildProbes() first.")
  }

  // ==========================================================================
  // Test Data Factory
  // ==========================================================================

  /**
   * Create mock TestExecutionResult with sensible defaults.
   *
   * Used in BDD scenarios to simulate test completion results.
   *
   * @param testId Test UUID
   * @param passed Whether test passed (affects scenario/step counts)
   * @return TestExecutionResult with realistic test metrics
   */
  def mockTestExecutionResult(
    testId: UUID,
    passed: Boolean = true
  ): TestExecutionResult = {
    TestExecutionResult(
      testId = testId,
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
  }
}
