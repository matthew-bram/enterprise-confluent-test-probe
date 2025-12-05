package io.distia.probe.core.glue.world.fixtures.actors

import org.apache.pekko.actor.typed.ActorRef
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.fixtures.{BlockStorageDirectiveFixtures, KafkaSecurityDirectiveFixtures, ServiceInterfaceResponsesFixture, TestHarnessFixtures}
import io.distia.probe.core.models.KafkaConsumerCommands.KafkaConsumerCommand
import io.distia.probe.core.actors.KafkaConsumerActor
import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}

/**
 * Fixture for KafkaConsumerActor component tests.
 *
 * Provides:
 * - Consumer actor spawning utilities
 * - Actor-specific state tracking (consumerActor, consumerInitialized)
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
 * - Follows KafkaProducerActorFixture pattern
 * - Available to ActorWorld via trait composition
 */
trait KafkaConsumerActorFixture
  extends BlockStorageDirectiveFixtures
    with KafkaSecurityDirectiveFixtures
    with ServiceInterfaceResponsesFixture
    with TestHarnessFixtures {
  this: ActorWorld =>

  // ==========================================================================
  // Actor-Specific State (lives in fixture, not ActorWorld)
  // ==========================================================================

  /**
   * KafkaConsumerActor under test.
   *
   * Spawned by step definitions using spawnKafkaConsumerActor().
   */
  var consumerActor: Option[ActorRef[KafkaConsumerCommand]] = None

  /**
   * KafkaConsumerActor initialization flag.
   * Set to true after Initialize command sent.
   */
  var consumerInitialized: Boolean = false

  // ==========================================================================
  // Actor Spawning
  // ==========================================================================

  /**
   * Spawn KafkaConsumerActor for testing.
   *
   * Uses parent probe from ActorWorld (created by SharedBackgroundSteps).
   * Resets consumerInitialized flag.
   *
   * @param testIdStr Test ID string (for actor name)
   * @return ActorRef for KafkaConsumerCommand
   */
  def spawnKafkaConsumerActor(testIdStr: String): ActorRef[KafkaConsumerCommand] = {
    val parent = teaParentProbe.getOrElse(
      throw new IllegalStateException("Parent probe not initialized - ensure Background steps ran")
    )

    consumerInitialized = false  // Reset on spawn

    testKit.spawn(
      KafkaConsumerActor(testId, parent.ref),
      s"consumer-$testIdStr"
    )
  }

  // ==========================================================================
  // Helper Methods
  // ==========================================================================

  /**
   * Get KafkaSecurityDirective list from ActorWorld or create default.
   *
   * Uses securityDirectives from ActorWorld (shared state).
   * Falls back to empty list (consumer doesn't require security for basic tests).
   *
   * Note: getOrCreateBlockStorageDirective is inherited from KafkaProducerActorFixture
   * via ActorWorld trait composition (shared implementation, no duplication needed).
   *
   * @return List of KafkaSecurityDirective for testing
   */
  def getOrCreateConsumerSecurityDirectives: List[KafkaSecurityDirective] =
    if securityDirectives.isEmpty then
      List.empty  // Empty list for basic component tests
    else
      securityDirectives
}
