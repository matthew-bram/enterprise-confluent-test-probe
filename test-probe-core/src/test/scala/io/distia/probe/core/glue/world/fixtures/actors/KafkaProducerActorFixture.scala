package io.distia.probe.core.glue.world.fixtures.actors

import org.apache.pekko.actor.typed.ActorRef
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.fixtures.{BlockStorageDirectiveFixtures, KafkaSecurityDirectiveFixtures, ServiceInterfaceResponsesFixture, TestHarnessFixtures}
import io.distia.probe.core.models.KafkaProducerCommands.KafkaProducerCommand
import io.distia.probe.core.actors.KafkaProducerActor
import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}

/**
 * Fixture for KafkaProducerActor component tests.
 *
 * Provides:
 * - Producer actor spawning utilities
 * - Actor-specific state tracking (producerActor, producerInitialized)
 * - Default BlockStorageDirective and KafkaSecurityDirective creation
 * - Helper methods for directive retrieval
 *
 * Fixtures Used:
 * - BlockStorageDirectiveFixtures (BlockStorageDirective creation)
 * - KafkaSecurityDirectiveFixtures (KafkaSecurityDirective creation)
 * - ServiceInterfaceResponsesFixture (response factories)
 * - TestHarnessFixtures (all test harness utilities)
 *
 * Architecture Notes:
 * - Actor-specific state lives HERE (not ActorWorld)
 * - Shared state (testId, probes) lives in ActorWorld
 * - Follows BlockStorageActorFixture pattern
 * - Available to ActorWorld via trait composition
 */
trait KafkaProducerActorFixture
  extends BlockStorageDirectiveFixtures
    with KafkaSecurityDirectiveFixtures
    with ServiceInterfaceResponsesFixture
    with TestHarnessFixtures {
  this: ActorWorld =>

  // ==========================================================================
  // Actor-Specific State (lives in fixture, not ActorWorld)
  // ==========================================================================

  /**
   * KafkaProducerActor under test.
   *
   * Spawned by step definitions using spawnKafkaProducerActor().
   */
  var producerActor: Option[ActorRef[KafkaProducerCommand]] = None

  /**
   * KafkaProducerActor initialization flag.
   * Set to true after Initialize command sent.
   */
  var producerInitialized: Boolean = false

  // ==========================================================================
  // Actor Spawning
  // ==========================================================================

  /**
   * Spawn KafkaProducerActor for testing.
   *
   * Uses parent probe from ActorWorld (created by SharedBackgroundSteps).
   * Resets producerInitialized flag.
   *
   * @param testIdStr Test ID string (for actor name)
   * @return ActorRef for KafkaProducerCommand
   */
  def spawnKafkaProducerActor(testIdStr: String): ActorRef[KafkaProducerCommand] = {
    val parent = teaParentProbe.getOrElse(
      throw new IllegalStateException("Parent probe not initialized - ensure Background steps ran")
    )

    producerInitialized = false  // Reset on spawn

    testKit.spawn(
      KafkaProducerActor(testId, parent.ref),
      s"producer-$testIdStr"
    )
  }

  // ==========================================================================
  // Helper Methods
  // ==========================================================================

  /**
   * Get BlockStorageDirective from ActorWorld or create default.
   *
   * Uses blockStorageDirective from ActorWorld (shared state).
   * Falls back to createBlockStorageDirective from BlockStorageDirectiveFixtures.
   *
   * @return BlockStorageDirective for testing
   */
  def getOrCreateBlockStorageDirective: BlockStorageDirective =
    blockStorageDirective.getOrElse(
      createBlockStorageDirective(jimfsLocation = s"/jimfs/test-$testId")
    )

  /**
   * Get KafkaSecurityDirective list from ActorWorld or create default.
   *
   * Uses securityDirectives from ActorWorld (shared state).
   * Falls back to createProducerSecurity from KafkaSecurityDirectiveFixtures.
   *
   * @return List of KafkaSecurityDirective for testing
   */
  def getOrCreateSecurityDirectives: List[KafkaSecurityDirective] =
    if securityDirectives.isEmpty then
      List(createProducerSecurity(topic = "test-events"))
    else
      securityDirectives
}
