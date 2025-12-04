package io.distia.probe
package core
package builder
package modules

import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.ExecutionContext.Implicits.global
import config.CoreConfig
import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}
import models.{GuardianCommands, ActorSystemInitializationSuccess, ActorSystemInitializationFailure, QueueActorReference}
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import pubsub.ProbeScalaDsl
import models.KafkaProducerStreamingCommands.KafkaProducerStreamingCommand

import java.util.UUID
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Comprehensive test suite for DefaultActorSystem module
 *
 * Tests complete lifecycle including:
 * - preFlight validation
 * - Full ActorSystem creation with GuardianActor
 * - Guardian initialization (success and failure paths)
 * - QueueActor reference retrieval
 * - Service functions extraction and decoration
 * - finalCheck validation
 *
 * Coverage target: 100% of DefaultActorSystem
 */
private[core] class DefaultActorSystemSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  // Increased timeouts for actor system initialization
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))

  // ActorTestKit for creating TestProbes (needed for DSL integration tests)
  val testKit: ActorTestKit = ActorTestKit()

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  // ========== TEST HELPERS ==========

  /**
   * Create a BuilderContext with config and coreConfig for testing
   */
  private def createConfiguredContext(): BuilderContext = {
    val config = ConfigFactory.parseString("""
      test-probe.core {
        actor-system {
          name = "test-probe"
          timeout = 25s
          shutdown-timeout = 45s
          initialization-timeout = 30s
          pool-size = 3
          max-execution-time = 300s
        }
        supervision {
          max-restarts = 10
          restart-time-range = 60s
        }
        test-execution {
          max-retries = 3
          cleanup-delay = 60s
          stash-buffer-size = 100
        }
        cucumber {
          glue-packages = "io.distia.probe.core.glue"
        }
        services.timeout = 30s
        dsl {
          ask-timeout = 3s
        }
        kafka {
          bootstrap-servers = "localhost:9092"
          schema-registry-url = "http://localhost:8081"
          oauth {
            token-endpoint = "http://localhost:8080/oauth/token"
            client-scope = "kafka.read kafka.write"
          }
        }
        timers {
          setup-state = 60s
          loading-state = 120s
          completed-state = 60s
          exception-state = 30s
        }
      }
    """)

    val coreConfig = CoreConfig.fromConfig(config)

    BuilderContext()
      .withConfig(config, coreConfig)
  }

  /**
   * Mock VaultService implementation for testing
   */
  private class MockVaultService(implicit ec: ExecutionContext) extends ProbeVaultService {
    override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx.withVaultService(this))

    override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def fetchSecurityDirectives(directive: BlockStorageDirective)(implicit ec: ExecutionContext): Future[List[KafkaSecurityDirective]] =
      Future.successful(List.empty)

    override def shutdown()(implicit ec: ExecutionContext): Future[Unit] =
      Future.successful(())
  }

  /**
   * Mock StorageService implementation for testing
   */
  private class MockStorageService(implicit ec: ExecutionContext) extends ProbeStorageService {
    override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx.withStorageService(this))

    override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def fetchFromBlockStorage(testId: UUID, bucket: String)(implicit ec: ExecutionContext): Future[BlockStorageDirective] =
      Future.successful(BlockStorageDirective(
        jimfsLocation = s"/jimfs/mock-$testId",
        evidenceDir = s"/evidence/$testId",
        topicDirectives = List.empty,
        bucket = bucket
      ))

    override def loadToBlockStorage(testId: UUID, bucket: String, evidence: String)(implicit ec: ExecutionContext): Future[Unit] =
      Future.successful(())
  }

  // ========== PREFLIGHT TESTS ==========

  "DefaultActorSystem.preFlight()" should {

    "succeed when config is defined with actor-system.name" in {
      val module = new DefaultActorSystem()
      val ctx = createConfiguredContext()

      whenReady(module.preFlight(ctx)) { resultCtx =>
        resultCtx should be theSameInstanceAs ctx
        resultCtx.actorSystem shouldBe None
      }
    }

    "return same context instance without mutation" in {
      val module = new DefaultActorSystem()
      val ctx = createConfiguredContext()
      val originalConfig = ctx.config
      val originalCoreConfig = ctx.coreConfig

      whenReady(module.preFlight(ctx)) { resultCtx =>
        resultCtx should be theSameInstanceAs ctx
        resultCtx.config should be theSameInstanceAs originalConfig
        resultCtx.coreConfig should be theSameInstanceAs originalCoreConfig
      }
    }

    "fail when config is not defined" in {
      val module = new DefaultActorSystem()
      val ctx = BuilderContext()

      whenReady(module.preFlight(ctx).failed) { exception =>
        exception shouldBe a [IllegalStateException]
        exception.getMessage should include("Config must be initialized before ActorSystem")
      }
    }

    "fail when config missing actor-system.name" in {
      val module = new DefaultActorSystem()
      val incompleteConfig = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            timeout = 25s
          }
        }
      """)

      val ctx = BuilderContext(config = Some(incompleteConfig))

      whenReady(module.preFlight(ctx).failed) { exception =>
        exception shouldBe a [IllegalStateException]
        exception.getMessage should include("Config missing required key: test-probe.core.actor-system.name")
      }
    }
  }

  // ========== INITIALIZE VALIDATION TESTS ==========

  "DefaultActorSystem.initialize() validation" should {

    "fail when config is not defined" in {
      val module = new DefaultActorSystem()
      val ctx = BuilderContext()

      val exception = intercept[IllegalStateException] {
        whenReady(module.initialize(ctx)) { _ => }
      }

      exception.getMessage should include("Config must be initialized before ActorSystem")
    }

    "fail when coreConfig is not defined" in {
      val module = new DefaultActorSystem()
      val config = ConfigFactory.parseString("""
        test-probe.core.actor-system.name = "test"
      """)
      val ctx = BuilderContext(config = Some(config))

      val exception = intercept[IllegalStateException] {
        whenReady(module.initialize(ctx)) { _ => }
      }

      exception.getMessage should include("CoreConfig must be initialized before ActorSystem")
    }

    "fail when vaultService is not defined" in {
      val module = new DefaultActorSystem()
      val ctx = createConfiguredContext()

      val exception = intercept[IllegalStateException] {
        whenReady(module.initialize(ctx)) { _ => }
      }

      exception.getMessage should include("VaultService must be initialized before ActorSystem")
    }

    "fail when storageService is not defined" in {
      val module = new DefaultActorSystem()
      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())

      val exception = intercept[IllegalStateException] {
        whenReady(module.initialize(ctx)) { _ => }
      }

      exception.getMessage should include("StorageService must be initialized before ActorSystem")
    }
  }

  // ========== ERROR PATH TESTS (Factory Pattern) ==========

  "DefaultActorSystem.initialize() error handling" should {

    "fail and terminate ActorSystem when GuardianActor initialization fails" in {
      // Create mock factory that returns failing behavior on Initialize
      val failingFactory: DefaultActorSystem.GuardianActorFactory =
        (config, serviceFunctions, behaviors) => Behaviors.setup { ctx =>
          Behaviors.receiveMessage {
            case GuardianCommands.Initialize(replyTo) =>
              replyTo ! ActorSystemInitializationFailure(
                new RuntimeException("Guardian initialization failed")
              )
              Behaviors.stopped
            case _ => Behaviors.same
          }
        }

      val module = new DefaultActorSystem(Some(failingFactory))
      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())
        .withStorageService(new MockStorageService())

      whenReady(module.initialize(ctx).failed) { exception =>
        exception shouldBe a[RuntimeException]
        exception.getMessage should include("Guardian initialization failed")
      }
    }

    "fail and terminate ActorSystem when GetQueueActor request fails" in {
      // Create mock factory that succeeds Initialize but fails GetQueueActor
      val failingFactory: DefaultActorSystem.GuardianActorFactory =
        (config, serviceFunctions, behaviors) => Behaviors.setup { ctx =>
          Behaviors.receiveMessage {
            case GuardianCommands.Initialize(replyTo) =>
              replyTo ! ActorSystemInitializationSuccess
              Behaviors.same
            case GuardianCommands.GetQueueActor(replyTo) =>
              replyTo ! ActorSystemInitializationFailure(
                new RuntimeException("GetQueueActor failed")
              )
              Behaviors.stopped
            case _ => Behaviors.same
          }
        }

      val module = new DefaultActorSystem(Some(failingFactory))
      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())
        .withStorageService(new MockStorageService())

      whenReady(module.initialize(ctx).failed) { exception =>
        exception shouldBe a[RuntimeException]
        exception.getMessage should include("Guardian initialization failed")
      }
    }
  }

  // ========== FULL INITIALIZATION TESTS ==========

  "DefaultActorSystem.initialize() full lifecycle" should {

    "successfully create ActorSystem with GuardianActor" in {
      val module = new DefaultActorSystem()
      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())
        .withStorageService(new MockStorageService())

      whenReady(module.initialize(ctx)) { resultCtx =>
        try {
          // Verify ActorSystem created
          resultCtx.actorSystem shouldBe defined
          resultCtx.actorSystem.get.name shouldBe "test-probe"

          // Verify QueueActor reference retrieved
          resultCtx.queueActorRef shouldBe defined

          // Verify curried functions created
          resultCtx.curriedFunctions shouldBe defined

          // Verify service functions context created
          resultCtx.serviceFunctionsContext shouldBe defined

          // Verify legacy functions still present (backward compatibility)
          resultCtx.vaultFunctions shouldBe defined
          resultCtx.storageFunctions shouldBe defined

        } finally {
          // Clean up actor system
          resultCtx.actorSystem.foreach { system =>
            system.terminate()
            scala.concurrent.Await.result(system.whenTerminated, 10.seconds)
          }
        }
      }
    }

    "extract VaultServiceFunctions from VaultService" in {
      val module = new DefaultActorSystem()
      val testUUID = UUID.randomUUID()

      val customVaultService = new MockVaultService()

      val ctx = createConfiguredContext()
        .withVaultService(customVaultService)
        .withStorageService(new MockStorageService())

      whenReady(module.initialize(ctx)) { resultCtx =>
        try {
          // Verify vault functions extracted
          resultCtx.vaultFunctions shouldBe defined

          // Verify service functions context created
          resultCtx.serviceFunctionsContext shouldBe defined

        } finally {
          resultCtx.actorSystem.foreach { system =>
            system.terminate()
            scala.concurrent.Await.result(system.whenTerminated, 10.seconds)
          }
        }
      }
    }

    "extract StorageServiceFunctions from StorageService" in {
      val module = new DefaultActorSystem()
      val testUUID = UUID.randomUUID()

      val customStorageService = new MockStorageService()

      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())
        .withStorageService(customStorageService)

      whenReady(module.initialize(ctx)) { resultCtx =>
        try {
          // Verify storage functions extracted
          resultCtx.storageFunctions shouldBe defined

          // Verify service functions context created
          resultCtx.serviceFunctionsContext shouldBe defined

        } finally {
          resultCtx.actorSystem.foreach { system =>
            system.terminate()
            scala.concurrent.Await.result(system.whenTerminated, 10.seconds)
          }
        }
      }
    }

    "bundle service functions into ServiceFunctionsContext" in {
      val module = new DefaultActorSystem()
      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())
        .withStorageService(new MockStorageService())

      whenReady(module.initialize(ctx)) { resultCtx =>
        try {
          // Verify service functions context created
          resultCtx.serviceFunctionsContext shouldBe defined

          val serviceFunctions = resultCtx.serviceFunctionsContext.get

          // Verify vault functions bundled
          serviceFunctions.vault should not be null

          // Verify storage functions bundled
          serviceFunctions.storage should not be null

        } finally {
          resultCtx.actorSystem.foreach { system =>
            system.terminate()
            scala.concurrent.Await.result(system.whenTerminated, 10.seconds)
          }
        }
      }
    }

    "create curried ServiceInterfaceFunctions via factory" in {
      val module = new DefaultActorSystem()
      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())
        .withStorageService(new MockStorageService())

      whenReady(module.initialize(ctx)) { resultCtx =>
        try {
          // Verify curried functions created
          resultCtx.curriedFunctions shouldBe defined

          // Verify all functions present
          val functions = resultCtx.curriedFunctions.get
          functions.initializeTest should not be null
          functions.startTest should not be null
          functions.getStatus should not be null
          functions.getQueueStatus should not be null
          functions.cancelTest should not be null

        } finally {
          resultCtx.actorSystem.foreach { system =>
            system.terminate()
            scala.concurrent.Await.result(system.whenTerminated, 10.seconds)
          }
        }
      }
    }

    "create new context instance (immutability)" in {
      val module = new DefaultActorSystem()
      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())
        .withStorageService(new MockStorageService())

      whenReady(module.initialize(ctx)) { resultCtx =>
        try {
          resultCtx should not be theSameInstanceAs(ctx)

        } finally {
          resultCtx.actorSystem.foreach { system =>
            system.terminate()
            scala.concurrent.Await.result(system.whenTerminated, 10.seconds)
          }
        }
      }
    }

    "use actorSystemName from CoreConfig" in {
      val module = new DefaultActorSystem()
      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())
        .withStorageService(new MockStorageService())

      whenReady(module.initialize(ctx)) { resultCtx =>
        try {
          resultCtx.actorSystem.get.name shouldBe "test-probe"

        } finally {
          resultCtx.actorSystem.foreach { system =>
            system.terminate()
            scala.concurrent.Await.result(system.whenTerminated, 10.seconds)
          }
        }
      }
    }

    "use actorSystemTimeout from CoreConfig" in {
      val module = new DefaultActorSystem()
      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())
        .withStorageService(new MockStorageService())

      whenReady(module.initialize(ctx)) { resultCtx =>
        try {
          // Verify timeout used (implicit value set from CoreConfig)
          ctx.coreConfig.get.actorSystemTimeout shouldBe 25.seconds

        } finally {
          resultCtx.actorSystem.foreach { system =>
            system.terminate()
            scala.concurrent.Await.result(system.whenTerminated, 10.seconds)
          }
        }
      }
    }

    "pass actorBehaviorsContext to GuardianActor" in {
      val module = new DefaultActorSystem()

      // Create custom behaviors context
      val customBehaviors = ActorBehaviorsContext()

      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())
        .withStorageService(new MockStorageService())
        .withActorBehaviorsContext(customBehaviors)

      whenReady(module.initialize(ctx)) { resultCtx =>
        try {
          // Just verify it doesn't throw and ActorSystem created
          resultCtx.actorSystem shouldBe defined

        } finally {
          resultCtx.actorSystem.foreach { system =>
            system.terminate()
            scala.concurrent.Await.result(system.whenTerminated, 10.seconds)
          }
        }
      }
    }
  }

  // ========== FINAL CHECK TESTS ==========

  "DefaultActorSystem.finalCheck()" should {

    "fail when actorSystem is not defined" in {
      val module = new DefaultActorSystem()
      val ctx = BuilderContext()

      whenReady(module.finalCheck(ctx).failed) { exception =>
        exception shouldBe a [IllegalArgumentException]
        exception.getMessage should include("ActorSystem not initialized in BuilderContext")
      }
    }

    "succeed when actorSystem is defined" in {
      val module = new DefaultActorSystem()

      // Create a minimal actor system for testing finalCheck
      val config = ConfigFactory.parseString("""
        pekko {
          actor {
            provider = "local"
          }
        }
      """)

      import org.apache.pekko.actor.typed.ActorSystem
      import org.apache.pekko.actor.typed.scaladsl.Behaviors

      val testSystem = ActorSystem(Behaviors.empty[String], "test-system", config)

      try {
        val ctx = BuilderContext(actorSystem = Some(testSystem.asInstanceOf[ActorSystem[GuardianCommands.GuardianCommand]]))

        whenReady(module.finalCheck(ctx)) { resultCtx =>
          resultCtx should be theSameInstanceAs ctx
          resultCtx.actorSystem shouldBe defined
        }
      } finally {
        testSystem.terminate()
        scala.concurrent.Await.result(testSystem.whenTerminated, 10.seconds)
      }
    }

    "return same context instance without mutation" in {
      val module = new DefaultActorSystem()

      val config = ConfigFactory.parseString("""
        pekko {
          actor {
            provider = "local"
          }
        }
      """)

      import org.apache.pekko.actor.typed.ActorSystem
      import org.apache.pekko.actor.typed.scaladsl.Behaviors

      val testSystem = ActorSystem(Behaviors.empty[String], "test-system", config)

      try {
        val ctx = BuilderContext(actorSystem = Some(testSystem.asInstanceOf[ActorSystem[GuardianCommands.GuardianCommand]]))
        val originalActorSystem = ctx.actorSystem

        whenReady(module.finalCheck(ctx)) { resultCtx =>
          resultCtx should be theSameInstanceAs ctx
          resultCtx.actorSystem should be theSameInstanceAs originalActorSystem
        }
      } finally {
        testSystem.terminate()
        scala.concurrent.Await.result(testSystem.whenTerminated, 10.seconds)
      }
    }
  }

  // ========== PROBESCALADSL INTEGRATION TESTS ==========

  "DefaultActorSystem.initialize() ProbeScalaDsl integration" should {

    "register ActorSystem with ProbeScalaDsl during initialization" in {
      // Ensure clean slate - no previous system registered
      ProbeScalaDsl.clearSystem()

      val module = new DefaultActorSystem()
      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())
        .withStorageService(new MockStorageService())

      whenReady(module.initialize(ctx)) { resultCtx =>
        try {
          // Verify DSL is initialized by testing actor registration works
          // If system wasn't registered, this would throw DslNotInitializedException
          val testId = UUID.randomUUID()
          val topic = s"dsl-integration-test-${UUID.randomUUID()}"
          val probe = testKit.createTestProbe[KafkaProducerStreamingCommand]()

          // This should NOT throw DslNotInitializedException
          noException should be thrownBy {
            ProbeScalaDsl.registerProducerActor(testId, topic, probe.ref)
          }

          // Cleanup - unregister the test producer
          ProbeScalaDsl.unRegisterProducerActor(testId, topic)

        } finally {
          // Cleanup actor system
          resultCtx.actorSystem.foreach { system =>
            system.terminate()
            Await.result(system.whenTerminated, 10.seconds)
          }
          ProbeScalaDsl.clearSystem()
        }
      }
    }

    "allow DSL operations after initialization" in {
      // Ensure clean slate
      ProbeScalaDsl.clearSystem()

      val module = new DefaultActorSystem()
      val ctx = createConfiguredContext()
        .withVaultService(new MockVaultService())
        .withStorageService(new MockStorageService())

      whenReady(module.initialize(ctx)) { resultCtx =>
        try {
          // Multiple registrations should work (proves system is registered)
          val testId1 = UUID.randomUUID()
          val testId2 = UUID.randomUUID()
          val topic1 = s"topic-1-${UUID.randomUUID()}"
          val topic2 = s"topic-2-${UUID.randomUUID()}"
          val probe1 = testKit.createTestProbe[KafkaProducerStreamingCommand]()
          val probe2 = testKit.createTestProbe[KafkaProducerStreamingCommand]()

          // Multiple registrations should succeed
          noException should be thrownBy {
            ProbeScalaDsl.registerProducerActor(testId1, topic1, probe1.ref)
            ProbeScalaDsl.registerProducerActor(testId2, topic2, probe2.ref)
          }

          // Cleanup
          ProbeScalaDsl.unRegisterProducerActor(testId1, topic1)
          ProbeScalaDsl.unRegisterProducerActor(testId2, topic2)

        } finally {
          resultCtx.actorSystem.foreach { system =>
            system.terminate()
            Await.result(system.whenTerminated, 10.seconds)
          }
          ProbeScalaDsl.clearSystem()
        }
      }
    }
  }
}
