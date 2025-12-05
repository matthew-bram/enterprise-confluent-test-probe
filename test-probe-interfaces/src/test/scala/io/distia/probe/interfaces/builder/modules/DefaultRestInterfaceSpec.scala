package io.distia.probe.interfaces.builder.modules

import io.distia.probe.core.builder.{BuilderContext, ServiceInterfaceFunctions}
import io.distia.probe.core.models.GuardianCommands.GuardianCommand
import io.distia.probe.core.models.QueueStatusResponse
import io.distia.probe.interfaces.fixtures.ConfigFixtures
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

/**
 * Unit tests for DefaultRestInterface
 *
 * Target Coverage: Lifecycle management (currently 0% → 70%+)
 *
 * Tests:
 * - preFlight validation (missing dependencies, invalid config)
 * - initialize (successful start, already initialized check)
 * - finalCheck (verify server is bound)
 * - shutdown (graceful unbind, idempotent)
 * - setCurriedFunctions (no-op implementation)
 */
class DefaultRestInterfaceSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  implicit val system: ActorSystem[GuardianCommand] =
    ActorSystem(Behaviors.empty[GuardianCommand], "DefaultRestInterfaceSpec")
  implicit val ec: ExecutionContext = system.executionContext

  private val validConfig: Config = ConfigFactory.parseString(ConfigFixtures.validRestConfig)

  private val mockFunctions = ServiceInterfaceFunctions(
    initializeTest = () => ???,
    startTest = (_, _, _) => ???,
    getStatus = _ => ???,
    getQueueStatus = _ => Future.successful(QueueStatusResponse(
      totalTests = 0,
      setupCount = 0,
      loadingCount = 0,
      loadedCount = 0,
      testingCount = 0,
      completedCount = 0,
      exceptionCount = 0,
      currentlyTesting = None
    )),
    cancelTest = _ => ???
  )

  override def afterAll(): Unit = {
    system.terminate()
    Await.result(system.whenTerminated, 10.seconds)
    ()
  }

  "DefaultRestInterface" when {

    // ========================================
    // preFlight Validation
    // ========================================

    "running preFlight validation" should {

      "succeed when all dependencies are present" in {
        val interface = new DefaultRestInterface()

        val ctx = BuilderContext(
          config = Some(validConfig),
          actorSystem = Some(system),
          curriedFunctions = Some(mockFunctions)
        )

        val result = Await.result(interface.preFlight(ctx), 3.seconds)
        result shouldBe ctx
      }

      "fail when config is missing" in {
        val interface = new DefaultRestInterface()

        val ctx = BuilderContext(
          config = None,
          actorSystem = Some(system),
          curriedFunctions = Some(mockFunctions)
        )

        val future = interface.preFlight(ctx)
        val result = Try(Await.result(future, 3.seconds))

        result.isFailure shouldBe true
        result.failed.get shouldBe an[IllegalStateException]
        result.failed.get.getMessage should include("Config must be initialized")
      }

      "fail when ActorSystem is missing" in {
        val interface = new DefaultRestInterface()

        val ctx = BuilderContext(
          config = Some(validConfig),
          actorSystem = None,
          curriedFunctions = Some(mockFunctions)
        )

        val future = interface.preFlight(ctx)
        val result = Try(Await.result(future, 3.seconds))

        result.isFailure shouldBe true
        result.failed.get.getMessage should include("ActorSystem must be initialized")
      }

      "fail when ServiceInterfaceFunctions is missing" in {
        val interface = new DefaultRestInterface()

        val ctx = BuilderContext(
          config = Some(validConfig),
          actorSystem = Some(system),
          curriedFunctions = None
        )

        val future = interface.preFlight(ctx)
        val result = Try(Await.result(future, 3.seconds))

        result.isFailure shouldBe true
        result.failed.get.getMessage should include("ServiceInterfaceFunctions must be initialized")
      }

      "fail with all missing dependencies listed" in {
        val interface = new DefaultRestInterface()

        val ctx = BuilderContext() // All None

        val future = interface.preFlight(ctx)
        val result = Try(Await.result(future, 3.seconds))

        result.isFailure shouldBe true
        val message = result.failed.get.getMessage
        message should include("Config must be initialized")
        message should include("ActorSystem must be initialized")
        message should include("ServiceInterfaceFunctions must be initialized")
      }

      "fail when config is invalid" in {
        val interface = new DefaultRestInterface()

        val invalidConfig = ConfigFactory.parseString("""
          test-probe {
            interfaces {
              rest {
                host = "0.0.0.0"
                # Missing required fields
              }
            }
          }
        """)

        val ctx = BuilderContext(
          config = Some(invalidConfig),
          actorSystem = Some(system),
          curriedFunctions = Some(mockFunctions)
        )

        val future = interface.preFlight(ctx)
        val result = Try(Await.result(future, 3.seconds))

        result.isFailure shouldBe true
        result.failed.get.getMessage should include("Invalid interfaces configuration")
      }
    }

    // ========================================
    // Initialize Phase
    // ========================================

    "initializing interface" should {

      "successfully start REST server and store binding" in {
        val interface = new DefaultRestInterface()

        val ctx = BuilderContext(
          config = Some(validConfig),
          actorSystem = Some(system),
          curriedFunctions = Some(mockFunctions)
        )

        val resultCtx = Await.result(interface.initialize(ctx), 5.seconds)
        resultCtx shouldBe ctx

        // Clean up
        Await.result(interface.shutdown(), 3.seconds)
      }

      "fail if already initialized" in {
        val interface = new DefaultRestInterface()

        val ctx = BuilderContext(
          config = Some(validConfig),
          actorSystem = Some(system),
          curriedFunctions = Some(mockFunctions)
        )

        // First initialization succeeds
        Await.result(interface.initialize(ctx), 5.seconds)

        // Second initialization should fail
        val result = Try(Await.result(interface.initialize(ctx), 5.seconds))
        result.isFailure shouldBe true
        result.failed.get shouldBe an[IllegalArgumentException]
        result.failed.get.getMessage should include("already initialized")

        // Clean up
        Await.result(interface.shutdown(), 3.seconds)
      }
    }

    // ========================================
    // Final Check Phase
    // ========================================

    "running finalCheck" should {

      "succeed when server is initialized and bound" in {
        val interface = new DefaultRestInterface()

        val ctx = BuilderContext(
          config = Some(validConfig),
          actorSystem = Some(system),
          curriedFunctions = Some(mockFunctions)
        )

        Await.result(interface.initialize(ctx), 5.seconds)
        val resultCtx = Await.result(interface.finalCheck(ctx), 3.seconds)

        resultCtx shouldBe ctx

        // Clean up
        Await.result(interface.shutdown(), 3.seconds)
      }

      "fail when server not initialized" in {
        val interface = new DefaultRestInterface()

        val ctx = BuilderContext(
          config = Some(validConfig),
          actorSystem = Some(system),
          curriedFunctions = Some(mockFunctions)
        )

        // Skip initialize, go straight to finalCheck
        val future = interface.finalCheck(ctx)
        val result = Try(Await.result(future, 3.seconds))

        result.isFailure shouldBe true
        result.failed.get shouldBe an[IllegalArgumentException]
        result.failed.get.getMessage should include("binding not initialized")
      }
    }

    // ========================================
    // Shutdown Phase
    // ========================================

    "shutting down interface" should {

      "successfully unbind server" in {
        val interface = new DefaultRestInterface()

        val ctx = BuilderContext(
          config = Some(validConfig),
          actorSystem = Some(system),
          curriedFunctions = Some(mockFunctions)
        )

        Await.result(interface.initialize(ctx), 5.seconds)
        val shutdownResult = Await.result(interface.shutdown(), 3.seconds)

        shutdownResult shouldBe (())
      }

      "be idempotent (can call multiple times safely)" in {
        val interface = new DefaultRestInterface()

        val ctx = BuilderContext(
          config = Some(validConfig),
          actorSystem = Some(system),
          curriedFunctions = Some(mockFunctions)
        )

        Await.result(interface.initialize(ctx), 5.seconds)

        // First shutdown
        Await.result(interface.shutdown(), 3.seconds)

        // Second shutdown should succeed (no-op)
        val result = Await.result(interface.shutdown(), 3.seconds)
        result shouldBe (())
      }

      "succeed when called without initialization (never started)" in {
        val interface = new DefaultRestInterface()

        // Shutdown without ever calling initialize
        val result = Await.result(interface.shutdown(), 3.seconds)
        result shouldBe (())
      }
    }

    // ========================================
    // setCurriedFunctions
    // ========================================

    "calling setCurriedFunctions" should {

      "be a no-op (does nothing)" in {
        val interface = new DefaultRestInterface()

        // Should not throw
        interface.setCurriedFunctions(mockFunctions)

        // Verify we can still use interface normally
        val ctx = BuilderContext(
          config = Some(validConfig),
          actorSystem = Some(system),
          curriedFunctions = Some(mockFunctions)
        )

        Await.result(interface.initialize(ctx), 5.seconds)
        Await.result(interface.shutdown(), 3.seconds)
      }
    }

    // ========================================
    // Full Lifecycle Integration
    // ========================================

    "running full lifecycle" should {

      "succeed with preFlight → initialize → finalCheck → shutdown" in {
        val interface = new DefaultRestInterface()

        val ctx = BuilderContext(
          config = Some(validConfig),
          actorSystem = Some(system),
          curriedFunctions = Some(mockFunctions)
        )

        // Phase 1: preFlight
        val preFlightCtx = Await.result(interface.preFlight(ctx), 3.seconds)
        preFlightCtx shouldBe ctx

        // Phase 2: initialize
        val initCtx = Await.result(interface.initialize(preFlightCtx), 5.seconds)
        initCtx shouldBe ctx

        // Phase 3: finalCheck
        val checkCtx = Await.result(interface.finalCheck(initCtx), 3.seconds)
        checkCtx shouldBe ctx

        // Phase 4: shutdown
        val shutdownResult = Await.result(interface.shutdown(), 3.seconds)
        shutdownResult shouldBe (())
      }
    }
  }
}
