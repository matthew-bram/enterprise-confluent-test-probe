package io.distia.probe.core.fixtures

import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

/**
 * Actor spawning utilities for tests.
 *
 * Provides:
 * - Helper methods for spawning actors in tests
 * - Mock actor creation
 * - Actor supervision testing utilities
 *
 * This consolidates spawning logic that was duplicated across
 * multiple test files in the old architecture.
 *
 * Design Principles:
 * - Simplify actor creation in tests
 * - Reduce boilerplate
 * - Enable testing with mock dependencies
 *
 * Usage:
 * {{{
 *   class MyActorSpec extends AnyWordSpec
 *     with ActorTestingFixtures
 *     with ActorSpawningFixtures {
 *
 *     "MyActor" should {
 *       "spawn successfully" in {
 *         val actor = spawn(MyActor.behavior(config))
 *         // ... test actor
 *       }
 *     }
 *   }
 * }}}
 *
 * Thread Safety: ActorTestKit spawning is thread-safe.
 *
 * Test Strategy: Tested via integration (no standalone tests)
 */
trait ActorSpawningFixtures {
  this: ActorTestingFixtures =>

  /**
   * Spawn actor from behavior.
   *
   * @param behavior Actor behavior
   * @param name Optional actor name
   * @tparam T Message type
   * @return ActorRef for spawned actor
   */
  def spawn[T](behavior: Behavior[T], name: String = ""): ActorRef[T] =
    if name.isEmpty then
      testKit.spawn(behavior)
    else
      testKit.spawn(behavior, name)

  /**
   * Spawn anonymous actor from behavior.
   *
   * Useful when you don't care about the actor name.
   *
   * @param behavior Actor behavior
   * @tparam T Message type
   * @return ActorRef for spawned actor
   */
  def spawnAnonymous[T](behavior: Behavior[T]): ActorRef[T] =
    testKit.spawn(behavior)

  /**
   * Create mock actor using TestProbe.
   *
   * Returns a probe that can be used as a mock actor reference.
   * The probe allows you to verify messages sent to the "actor"
   * and control responses.
   *
   * @param name Optional probe name
   * @tparam T Message type
   * @return TestProbe that acts as a mock actor
   */
  def mockActor[T](name: String = "mockActor"): TestProbe[T] =
    testKit.createTestProbe[T](name)

  /**
   * Create multiple mock actors.
   *
   * @param count Number of mock actors to create
   * @param prefix Name prefix (default: "mock")
   * @tparam T Message type
   * @return List of TestProbes acting as mock actors
   */
  def mockActors[T](count: Int, prefix: String = "mock"): List[TestProbe[T]] =
    (1 to count).map(i => mockActor[T](s"$prefix-$i")).toList
}
