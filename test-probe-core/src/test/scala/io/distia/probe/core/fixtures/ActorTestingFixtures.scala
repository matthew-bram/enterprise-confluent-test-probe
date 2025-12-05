package io.distia.probe.core.fixtures

import org.apache.pekko.actor.testkit.typed.scaladsl.{ActorTestKit, TestProbe}
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite

import io.distia.probe.core.models.{ServiceResponse, InitializeTestResponse, StartTestResponse, TestStatusResponse}
import io.distia.probe.core.models.QueueCommands

import java.util.UUID
import scala.concurrent.duration._

/**
 * Base trait for actor testing with Pekko Typed.
 *
 * Provides:
 * - ActorTestKit lifecycle management
 * - Common matchers for ServiceResponse types
 * - Helper methods for actor testing
 * - Consistent test probe creation
 *
 * This trait consolidates functionality from the old architecture's:
 * - ActorSpecBase (48 lines)
 * - ActorTestMatchers (547 lines - mostly hardcoded to EventEnvelope)
 * - StreamingActorFixtures (102 lines)
 *
 * Usage:
 * {{{
 *   class MyActorSpec extends AnyWordSpec
 *     with ActorTestingFixtures {
 *
 *     "MyActor" should {
 *       "respond to initialize" in {
 *         val probe = createServiceProbe()
 *         // ... send message
 *         expectInitializeTestResponse(probe, testId)
 *       }
 *     }
 *   }
 * }}}
 *
 * Thread Safety: ActorTestKit is thread-safe. Each test gets isolated actors.
 *
 * Test Strategy: Tested via ActorTestingFixturesSpec (15 tests)
 */
trait ActorTestingFixtures extends BeforeAndAfterAll with Matchers {
  this: Suite =>

  /**
   * ActorTestKit for this test suite.
   *
   * Automatically shut down after all tests complete.
   */
  implicit val testKit: ActorTestKit = ActorTestKit()

  /**
   * Shutdown ActorTestKit after all tests complete.
   */
  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  /**
   * Create test probe for ServiceResponse messages.
   *
   * @param name Optional probe name (for debugging). If provided, will be prefixed with "probe-".
   *             If empty, generates unique name like "probe-{UUID}".
   * @return TestProbe typed for ServiceResponse
   */
  def createServiceProbe(name: String = ""): TestProbe[ServiceResponse] = {
    val probeName = if (name.nonEmpty) s"probe-$name" else s"probe-${UUID.randomUUID()}"
    testKit.createTestProbe[ServiceResponse](probeName)
  }

  /**
   * Create test probe for QueueCommand messages.
   *
   * @param name Optional probe name (for debugging). If provided, will be prefixed with "probe-".
   *             If empty, generates unique name like "probe-{UUID}".
   * @return TestProbe typed for QueueCommand
   */
  def createQueueProbe(name: String = ""): TestProbe[QueueCommands.QueueCommand] = {
    val probeName = if (name.nonEmpty) s"probe-$name" else s"probe-${UUID.randomUUID()}"
    testKit.createTestProbe[QueueCommands.QueueCommand](probeName)
  }

  /**
   * Create generic test probe.
   *
   * @param name Optional probe name (for debugging). If provided, will be prefixed with "probe-".
   *             If empty, generates unique name like "probe-{UUID}".
   * @tparam T Message type
   * @return TestProbe typed for T
   */
  def createProbe[T](name: String = ""): TestProbe[T] = {
    val probeName = if (name.nonEmpty) s"probe-$name" else s"probe-${UUID.randomUUID()}"
    testKit.createTestProbe[T](probeName)
  }

  // ============================================================================
  // ServiceResponse Matchers
  // ============================================================================

  /**
   * Expect InitializeTestResponse with specific test ID.
   *
   * @param probe Test probe
   * @param expectedTestId Expected test ID
   * @param timeout Timeout for receiving message (default: 3 seconds)
   * @return The received InitializeTestResponse
   */
  def expectInitializeTestResponse(
    probe: TestProbe[ServiceResponse],
    expectedTestId: UUID,
    timeout: FiniteDuration = 3.seconds
  ): InitializeTestResponse = {
    val response = probe.expectMessageType[InitializeTestResponse](timeout)
    response.testId shouldBe expectedTestId
    response
  }

  /**
   * Expect StartTestResponse with specific test ID.
   *
   * @param probe Test probe
   * @param expectedTestId Expected test ID
   * @param expectedAccepted Expected accepted status
   * @param timeout Timeout for receiving message (default: 3 seconds)
   * @return The received StartTestResponse
   */
  def expectStartTestResponse(
    probe: TestProbe[ServiceResponse],
    expectedTestId: UUID,
    expectedAccepted: Boolean = true,
    timeout: FiniteDuration = 3.seconds
  ): StartTestResponse = {
    val response = probe.expectMessageType[StartTestResponse](timeout)
    response.testId shouldBe expectedTestId
    response.accepted shouldBe expectedAccepted
    response
  }

  /**
   * Expect TestStatusResponse with specific test ID.
   *
   * @param probe Test probe
   * @param expectedTestId Expected test ID
   * @param timeout Timeout for receiving message (default: 3 seconds)
   * @return The received TestStatusResponse
   */
  def expectTestStatusResponse(
    probe: TestProbe[ServiceResponse],
    expectedTestId: UUID,
    timeout: FiniteDuration = 3.seconds
  ): TestStatusResponse = {
    val response = probe.expectMessageType[TestStatusResponse](timeout)
    response.testId shouldBe expectedTestId
    response
  }

  // ============================================================================
  // QueueCommand Matchers
  // ============================================================================

  /**
   * Expect TestInitialized command with specific test ID.
   *
   * @param probe Queue command probe
   * @param expectedTestId Expected test ID
   * @param timeout Timeout for receiving message (default: 3 seconds)
   * @return The received TestInitialized command
   */
  def expectTestInitialized(
    probe: TestProbe[QueueCommands.QueueCommand],
    expectedTestId: UUID,
    timeout: FiniteDuration = 3.seconds
  ): QueueCommands.TestInitialized = {
    val command = probe.expectMessageType[QueueCommands.TestInitialized](timeout)
    command.testId shouldBe expectedTestId
    command
  }

  /**
   * Expect TestCompleted command with specific test ID.
   *
   * @param probe Queue command probe
   * @param expectedTestId Expected test ID
   * @param timeout Timeout for receiving message (default: 3 seconds)
   * @return The received TestCompleted command
   */
  def expectTestCompleted(
    probe: TestProbe[QueueCommands.QueueCommand],
    expectedTestId: UUID,
    timeout: FiniteDuration = 3.seconds
  ): QueueCommands.TestCompleted = {
    val command = probe.expectMessageType[QueueCommands.TestCompleted](timeout)
    command.testId shouldBe expectedTestId
    command
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  /**
   * Expect no message within timeout.
   *
   * Useful for verifying an actor doesn't send unexpected messages.
   *
   * @param probe Test probe
   * @param timeout Timeout to wait (default: 500 milliseconds)
   */
  def expectNoMessage[T](probe: TestProbe[T], timeout: FiniteDuration = 500.milliseconds): Unit = {
    probe.expectNoMessage(timeout)
  }

  /**
   * Generate random test ID.
   *
   * @return Random UUID
   */
  def randomTestId(): UUID = UUID.randomUUID()
}
