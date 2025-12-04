package io.distia.probe
package core
package actors

import io.distia.probe.core.builder.{ServiceFunctionsContext, StorageServiceFunctions, VaultServiceFunctions}
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpec
import config.CoreConfig
import fixtures.{ActorTestingFixtures, ConfigurationFixtures, InterfaceFunctionsFixture, TestHarnessFixtures}
import models.GuardianCommands.*
import models.QueueCommands.*
import models.*

import scala.concurrent.Future
import scala.concurrent.duration.*

/**
 * Companion object for GuardianActorSpec test data.
 *
 * Contains stub service functions for GuardianActor testing.
 * GuardianActor never calls these functions - they exist only for construction.
 *
 * Uses InterfaceFunctionsFixture for all function stubs (single source of truth).
 */
private[actors] object GuardianActorSpec extends InterfaceFunctionsFixture {

  /** Stub service functions - GuardianActor doesn't use these, they're just required for construction */
  val stubServiceFunctionsContext: ServiceFunctionsContext = ServiceFunctionsContext(
    vaultFunctions = VaultServiceFunctions(
      fetchSecurityDirectives = getFailedSecurityDirectivesFetchFunction
    ),
    storageFunctions = StorageServiceFunctions(
      fetchFromBlockStorage = getFailedBlockStorageFetchFunction,
      loadToBlockStorage = getFailedBlockStorageLoadFunction
    )
  )
}

class GuardianActorSpec extends AnyWordSpec
  with ActorTestingFixtures
  with TestHarnessFixtures {
  import GuardianActorSpec.*

  // Test configuration from ConfigurationFixtures (single source of truth)
  private val serviceConfig: CoreConfig = ConfigurationFixtures.defaultCoreConfig

  // ===== Helper Methods =====

  /** Spawn GuardianActor with default configuration */
  private def spawnGuardian(): ActorRef[GuardianCommand] =
    testKit.spawn(GuardianActor(serviceConfig, stubServiceFunctionsContext))

  /** Spawn GuardianActor with custom factory (for dependency injection testing) */
  private def spawnGuardianWithFactory(factory: GuardianActor.QueueActorFactory): ActorRef[GuardianCommand] =
    testKit.spawn(GuardianActor(serviceConfig, stubServiceFunctionsContext, Some(factory)))

  /** Expect ActorSystemInitializationSuccess response */
  private def expectActorSystemInitializationSuccess(probe: TestProbe[ServiceResponse]): Unit =
    probe.expectMessage(3.seconds, ActorSystemInitializationSuccess)

  /** Expect ActorSystemInitializationFailure and return the exception */
  private def expectActorSystemInitializationFailure(probe: TestProbe[ServiceResponse]): Exception =
    probe.expectMessageType[ActorSystemInitializationFailure](3.seconds).exception

  /** Expect QueueActorReference and return the actor ref */
  private def expectQueueActorReference(probe: TestProbe[ServiceResponse]): ActorRef[QueueCommand] =
    probe.expectMessageType[QueueActorReference](3.seconds).queueActorRef

  // =========================================================================
  // INITIALIZATION TESTS (handleInitialize coverage)
  // =========================================================================

  "GuardianActor initialization" should {

    "spawn QueueActor on Initialize command" in {
      val serviceProbe = createServiceProbe()
      var spawnCalled = false

      // Mock factory that tracks spawn calls
      val factory: GuardianActor.QueueActorFactory = (_: CoreConfig, _: ServiceFunctionsContext) => {
        spawnCalled = true
        QueueActor(serviceConfig, stubServiceFunctionsContext)
      }

      val guardian = spawnGuardianWithFactory(factory)
      guardian ! Initialize(serviceProbe.ref)
      expectActorSystemInitializationSuccess(serviceProbe)

      spawnCalled shouldBe true
    }

    "respond with ActorSystemInitializationSuccess after initialization" in {
      val serviceProbe = createServiceProbe()
      val guardian = spawnGuardian()

      guardian ! Initialize(serviceProbe.ref)
      expectActorSystemInitializationSuccess(serviceProbe)
    }

    "handle duplicate Initialize gracefully (idempotent)" in {
      val serviceProbe = createServiceProbe()
      val guardian = spawnGuardian()

      guardian ! Initialize(serviceProbe.ref)
      expectActorSystemInitializationSuccess(serviceProbe)

      guardian ! Initialize(serviceProbe.ref)
      expectActorSystemInitializationSuccess(serviceProbe)
    }

    "not spawn duplicate QueueActor on duplicate Initialize" in {
      val serviceProbe = createServiceProbe()
      var spawnCount = 0

      // Factory that counts spawns
      val factory: GuardianActor.QueueActorFactory = (_: CoreConfig, _: ServiceFunctionsContext) => {
        spawnCount = spawnCount + 1
        QueueActor(serviceConfig, stubServiceFunctionsContext)
      }

      val guardian = spawnGuardianWithFactory(factory)

      // First Initialize
      guardian ! Initialize(serviceProbe.ref)
      expectActorSystemInitializationSuccess(serviceProbe)
      spawnCount shouldBe 1

      // Second Initialize (duplicate)
      guardian ! Initialize(serviceProbe.ref)
      expectActorSystemInitializationSuccess(serviceProbe)
      spawnCount shouldBe 1 // Still 1 - no duplicate spawn
    }

    "catch and respond with failure when exception occurs during spawn" in {
      val serviceProbe = createServiceProbe()

      // Factory that throws exception
      val factory: GuardianActor.QueueActorFactory = (_: CoreConfig, _: ServiceFunctionsContext) =>
        throw new RuntimeException("Intentional test failure during spawn")

      val guardian = spawnGuardianWithFactory(factory)

      guardian ! Initialize(serviceProbe.ref)

      // Should respond with failure
      val exception = expectActorSystemInitializationFailure(serviceProbe)
      exception.getMessage should include("Intentional test failure")
    }

    "remain functional after spawn failure (Behaviors.same)" in {
      val serviceProbe = createServiceProbe()
      var attemptCount = 0

      // Factory that fails first time, succeeds second time
      val factory: GuardianActor.QueueActorFactory = (_: CoreConfig, _: ServiceFunctionsContext) => {
        attemptCount = attemptCount + 1
        if attemptCount == 1 then
          throw new RuntimeException("First attempt fails")
        else
          QueueActor(serviceConfig, stubServiceFunctionsContext)
      }

      val guardian = spawnGuardianWithFactory(factory)

      // First attempt - should fail
      guardian ! Initialize(serviceProbe.ref)
      serviceProbe.expectMessageType[ActorSystemInitializationFailure](3.seconds)
      // Second attempt - should succeed (actor stayed alive)
      guardian ! Initialize(serviceProbe.ref)
      serviceProbe.expectMessage(3.seconds, ActorSystemInitializationSuccess)
    }

    "convert non-Exception Throwable to RuntimeException (guard for edge case)" in {
      val serviceProbe = createServiceProbe()

      // Custom Throwable that is NOT an Exception (simulates edge case without fatal JVM shutdown)
      class CustomThrowable(msg: String) extends Throwable(msg)

      // Factory that throws a Throwable that is NOT an Exception
      val factory: GuardianActor.QueueActorFactory = (_: CoreConfig, _: ServiceFunctionsContext) =>
        // CustomThrowable extends Throwable but NOT Exception
        throw new CustomThrowable("Simulated Throwable - not an Exception!")

      val guardian = spawnGuardianWithFactory(factory)

      guardian ! Initialize(serviceProbe.ref)

      // Should receive ActorSystemInitializationFailure with wrapped RuntimeException
      val response: ServiceResponse = serviceProbe.expectMessageType[ActorSystemInitializationFailure](3.seconds)
      response match {
        case ActorSystemInitializationFailure(exception) =>
          // Should be wrapped in RuntimeException (not the original CustomThrowable)
          exception shouldBe a[RuntimeException]
          exception.getMessage should include("GuardianActor initialization failed")
          exception.getMessage should include("Simulated Throwable")

          // Cause should be the original CustomThrowable
          exception.getCause should not be null
          exception.getCause shouldBe a[CustomThrowable]
          exception.getCause.getMessage should include("not an Exception")

        case other =>
          fail(s"Expected ActorSystemInitializationFailure, got $other")
      }
    }

    "update state after successful initialization (verifiable via GetQueueActor)" in {
      val serviceProbe = createServiceProbe()

      val guardian = spawnGuardian()

      // Initialize
      guardian ! Initialize(serviceProbe.ref)
      expectActorSystemInitializationSuccess(serviceProbe)

      // Verify state updated - GetQueueActor should now work
      guardian ! GetQueueActor(serviceProbe.ref)
      expectQueueActorReference(serviceProbe)
    }
  }

  // =========================================================================
  // GET QUEUE ACTOR TESTS (handleGetQueueActor coverage)
  // =========================================================================

  "GuardianActor GetQueueActor" should {

    "provide QueueActor reference after initialization" in {
      val serviceProbe = createServiceProbe()

      val guardian = spawnGuardian()

      // Initialize first
      guardian ! Initialize(serviceProbe.ref)
      expectActorSystemInitializationSuccess(serviceProbe)

      // Request QueueActor reference
      guardian ! GetQueueActor(serviceProbe.ref)
      expectQueueActorReference(serviceProbe)
    }

    "provide same QueueActor reference on multiple GetQueueActor calls" in {
      val serviceProbe = createServiceProbe()

      val guardian = spawnGuardian()

      // Initialize
      guardian ! Initialize(serviceProbe.ref)
      serviceProbe.expectMessage(3.seconds, ActorSystemInitializationSuccess)

      // First GetQueueActor
      guardian ! GetQueueActor(serviceProbe.ref)
      val response1: ServiceResponse = serviceProbe.expectMessageType[QueueActorReference](3.seconds)
      val queueRef1: ActorRef[QueueCommand] = response1.asInstanceOf[QueueActorReference].queueActorRef

      // Second GetQueueActor
      guardian ! GetQueueActor(serviceProbe.ref)
      val response2: ServiceResponse = serviceProbe.expectMessageType[QueueActorReference](3.seconds)
      val queueRef2: ActorRef[QueueCommand] = response2.asInstanceOf[QueueActorReference].queueActorRef

      // Should be the same reference
      queueRef1 shouldBe queueRef2
    }

    "respond with ActorSystemInitializationFailure when called before Initialize" in {
      val serviceProbe = createServiceProbe()

      val guardian = spawnGuardian()

      // Call GetQueueActor WITHOUT initializing first
      guardian ! GetQueueActor(serviceProbe.ref)

      // Should receive ActorSystemInitializationFailure
      val response: ServiceResponse = serviceProbe.expectMessageType[ActorSystemInitializationFailure](3.seconds)
      response match {
        case ActorSystemInitializationFailure(exception) =>
          exception should not be null
          exception.getMessage should include("not initialized")
        case _ => fail(s"Expected ActorSystemInitializationFailure, got $response")
      }
    }

    "remain functional after GetQueueActor before Initialize error" in {
      val serviceProbe = createServiceProbe()

      val guardian = spawnGuardian()

      // Call GetQueueActor before Initialize (error)
      guardian ! GetQueueActor(serviceProbe.ref)
      serviceProbe.expectMessageType[ActorSystemInitializationFailure](3.seconds)

      // Now properly initialize
      guardian ! Initialize(serviceProbe.ref)
      serviceProbe.expectMessage(3.seconds, ActorSystemInitializationSuccess)

      // GetQueueActor should now work
      guardian ! GetQueueActor(serviceProbe.ref)
      val response: ServiceResponse = serviceProbe.expectMessageType[QueueActorReference](3.seconds)
      response match {
        case QueueActorReference(queueActorRef) =>
          queueActorRef should not be null
        case _ => fail(s"Expected QueueActorReference, got $response")
      }
    }

    "preserve state on GetQueueActor calls (Behaviors.same)" in {
      val serviceProbe = createServiceProbe()

      val guardian = spawnGuardian()

      // Initialize
      guardian ! Initialize(serviceProbe.ref)
      serviceProbe.expectMessage(3.seconds, ActorSystemInitializationSuccess)

      // Multiple GetQueueActor calls - all should work (state preserved)
      for (_ <- 1 to 3) {
        guardian ! GetQueueActor(serviceProbe.ref)
        serviceProbe.expectMessageType[QueueActorReference](3.seconds)
      }
    }
  }

  // =========================================================================
  // CHILD TERMINATION TESTS (handleChildTermination coverage)
  // =========================================================================
  // NOTE: Child termination (handleChildTermination) is covered by Akka supervision
  // and is difficult to test directly due to actor lifecycle constraints.
  // The supervision strategy (10 restarts per minute) is configured and will be
  // tested via integration/SIT tests when QueueActor failures occur.

  // =========================================================================
  // FACTORY INJECTION TESTS
  // =========================================================================

  "GuardianActor factory injection" should {

    "accept optional QueueActorFactory for testing" in {
      val serviceProbe = createServiceProbe()
      val queueProbe = createQueueProbe()

      // Custom factory that creates a mock behavior
      val factory: GuardianActor.QueueActorFactory = (config: CoreConfig, _: ServiceFunctionsContext) => {
        // Verify config is passed
        config should not be null

        queueProbe.ref.unsafeUpcast[QueueCommand]
        org.apache.pekko.actor.typed.scaladsl.Behaviors.receiveMessage { msg =>
          queueProbe.ref ! msg
          org.apache.pekko.actor.typed.scaladsl.Behaviors.same
        }
      }

      val guardian = spawnGuardianWithFactory(factory)

      // Initialize
      guardian ! Initialize(serviceProbe.ref)
      serviceProbe.expectMessage(3.seconds, ActorSystemInitializationSuccess)

      // GetQueueActor should return the mock
      guardian ! GetQueueActor(serviceProbe.ref)
      val response: ServiceResponse = serviceProbe.expectMessageType[QueueActorReference](3.seconds)
      response match {
        case QueueActorReference(queueActorRef) =>
          queueActorRef should not be null
        case _ => fail(s"Expected QueueActorReference, got $response")
      }
    }

    "use default QueueActor when no factory provided" in {
      val serviceProbe = createServiceProbe()

      // No factory - should use default QueueActor behavior
      val guardian = spawnGuardian()

      // Initialize
      guardian ! Initialize(serviceProbe.ref)
      serviceProbe.expectMessage(3.seconds, ActorSystemInitializationSuccess)

      // GetQueueActor should return real QueueActor
      guardian ! GetQueueActor(serviceProbe.ref)
      val response: ServiceResponse = serviceProbe.expectMessageType[QueueActorReference](3.seconds)
      response match {
        case QueueActorReference(queueActorRef) =>
          queueActorRef should not be null
        case _ => fail(s"Expected QueueActorReference, got $response")
      }
    }
  }

  // =========================================================================
  // PERFORMANCE TESTS
  // =========================================================================

  "GuardianActor performance" should {

    "respond to Initialize within expected timeout" in {
      val serviceProbe = createServiceProbe()

      val guardian = spawnGuardian()

      // Initialize
      guardian ! Initialize(serviceProbe.ref)

      // Should respond within 1 second (performance requirement)
      serviceProbe.expectMessage(1.second, ActorSystemInitializationSuccess)
    }

    "respond to GetQueueActor within expected timeout" in {
      val serviceProbe = createServiceProbe()

      val guardian = spawnGuardian()

      // Initialize first
      guardian ! Initialize(serviceProbe.ref)
      serviceProbe.expectMessage(1.second, ActorSystemInitializationSuccess)

      // GetQueueActor
      guardian ! GetQueueActor(serviceProbe.ref)

      // Should respond within 100 milliseconds (performance requirement)
      serviceProbe.expectMessageType[QueueActorReference](100.milliseconds)
    }
  }

  // =========================================================================
  // INTEGRATION TESTS - Full Lifecycle
  // =========================================================================

  "GuardianActor full lifecycle" should {

    "complete full initialization and reference retrieval workflow" in {
      val serviceProbe = createServiceProbe()

      val guardian = spawnGuardian()

      // Step 1: Initialize
      guardian ! Initialize(serviceProbe.ref)
      serviceProbe.expectMessage(3.seconds, ActorSystemInitializationSuccess)

      // Step 2: Get QueueActor reference
      guardian ! GetQueueActor(serviceProbe.ref)
      val response: ServiceResponse = serviceProbe.expectMessageType[QueueActorReference](3.seconds)

      // Step 3: Verify reference is valid
      response match {
        case QueueActorReference(queueActorRef) =>
          queueActorRef should not be null

          // Step 4: Verify QueueActor is alive by getting status
          val statusProbe: TestProbe[ServiceResponse] = testKit.createTestProbe[ServiceResponse]()
          queueActorRef ! QueueStatusRequest(None, statusProbe.ref)

          // Should receive QueueStatusResponse
          statusProbe.expectMessageType[QueueStatusResponse](3.seconds)

        case _ => fail(s"Expected QueueActorReference, got $response")
      }
    }

    "handle error-recovery-success workflow" in {
      val serviceProbe = createServiceProbe()

      val guardian = spawnGuardian()

      // Step 1: Attempt GetQueueActor before Initialize (error)
      guardian ! GetQueueActor(serviceProbe.ref)
      serviceProbe.expectMessageType[ActorSystemInitializationFailure](3.seconds)

      // Step 2: Initialize (recovery)
      guardian ! Initialize(serviceProbe.ref)
      serviceProbe.expectMessage(3.seconds, ActorSystemInitializationSuccess)

      // Step 3: GetQueueActor now succeeds
      guardian ! GetQueueActor(serviceProbe.ref)
      serviceProbe.expectMessageType[QueueActorReference](3.seconds)

      // Step 4: Multiple GetQueueActor calls work
      guardian ! GetQueueActor(serviceProbe.ref)
      serviceProbe.expectMessageType[QueueActorReference](3.seconds)
    }
  }
}