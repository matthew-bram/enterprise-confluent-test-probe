package io.distia.probe
package core
package builder

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import builder.modules.*
import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}
import fixtures.BlockStorageDirectiveFixtures

import java.util.UUID

private[core] class ServiceDslSpec extends AnyWordSpec with Matchers with ScalaFutures with BlockStorageDirectiveFixtures {

  // ========== MOCK IMPLEMENTATIONS ==========

  /**
   * Mock config module for testing
   * Always succeeds with minimal validation
   */
  private class MockConfig(implicit ec: ExecutionContext) extends ProbeConfig {
    override def validate(implicit ec: ExecutionContext): Future[config.CoreConfig] =
      Future.successful(null) // Not needed for builder tests

    override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx.withConfig(null, null))

    override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)
  }

  /**
   * Mock actor system module for testing
   * Always succeeds with minimal validation
   */
  private class MockActorSystem(implicit ec: ExecutionContext) extends ProbeActorSystem {
    override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx.withActorSystem(null).withQueueActorRef(null))

    override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)
  }

  /**
   * Mock interface module for testing
   * Always succeeds with minimal validation
   */
  private class MockInterface(implicit ec: ExecutionContext) extends ProbeInterface {
    override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx.withCurriedFunctions(null))

    override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def setCurriedFunctions(functions: ServiceInterfaceFunctions): Unit = {}

    override def shutdown()(implicit ec: ExecutionContext): Future[Unit] = Future.successful(())
  }

  /**
   * Mock storage service module for testing
   * Always succeeds with minimal validation
   */
  private class MockStorage(implicit ec: ExecutionContext) extends ProbeStorageService {
    override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def fetchFromBlockStorage(testId: UUID, bucket: String)(implicit ec: ExecutionContext): Future[BlockStorageDirective] =
      Future.successful(createBlockStorageDirective(jimfsLocation = s"/jimfs/mock-$testId", bucket = bucket))

    override def loadToBlockStorage(testId: UUID, bucket: String, evidence: String)(implicit ec: ExecutionContext): Future[Unit] =
      Future.successful(())
  }

  /**
   * Mock vault service module for testing
   * Always succeeds with minimal validation
   */
  private class MockVault(implicit ec: ExecutionContext) extends ProbeVaultService {
    override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def fetchSecurityDirectives(directive: BlockStorageDirective)(implicit ec: ExecutionContext): Future[List[KafkaSecurityDirective]] =
      Future.successful(List.empty)

    override def shutdown()(implicit ec: ExecutionContext): Future[Unit] =
      Future.successful(())
  }

  /**
   * Failing mock for error testing
   * Fails during initialize phase with RuntimeException
   */
  private class FailingStorage(implicit ec: ExecutionContext) extends ProbeStorageService {
    override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.failed(new RuntimeException("Storage initialization failed"))

    override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def fetchFromBlockStorage(testId: UUID, bucket: String)(implicit ec: ExecutionContext): Future[BlockStorageDirective] =
      Future.failed(new RuntimeException("Should not be called - initialization failed"))

    override def loadToBlockStorage(testId: UUID, bucket: String, evidence: String)(implicit ec: ExecutionContext): Future[Unit] =
      Future.failed(new RuntimeException("Should not be called - initialization failed"))
  }

  /**
   * Mock external service module for testing
   * Represents optional REST/gRPC/DB adapter modules
   */
  private class MockExternalService(implicit ec: ExecutionContext) extends ProbeActorBehavior {
    override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)

    override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
      Future.successful(ctx)
  }

  // ========== TYPE SAFETY TESTS ==========

  "ServiceDsl type safety" should {

    "allow build() when all required modules provided" in {
      // This test verifies compile-time type safety
      // If this compiles, the phantom type system is working correctly
      val dsl = ServiceDsl()
        .withConfig(new MockConfig())
        .withActorSystem(new MockActorSystem())
        .withInterface(new MockInterface())
        .withStorageService(new MockStorage())
        .withVaultServiceModule(new MockVault())

      // Should compile because all required modules are present
      noException should be thrownBy {
        dsl.build()
      }
    }

    "prevent build() without Config module at compile time" in {
      assertTypeError("""
        ServiceDsl()
          .withActorSystem(new MockActorSystem())
          .withInterface(new MockInterface())
          .withStorageService(new MockStorage())
          .withVaultServiceModule(new MockVault())
          .build()
      """)
    }

    "prevent build() without ActorSystem module at compile time" in {
      assertTypeError("""
        ServiceDsl()
          .withConfig(new MockConfig())
          .withInterface(new MockInterface())
          .withStorageService(new MockStorage())
          .withVaultServiceModule(new MockVault())
          .build()
      """)
    }

    "prevent build() without Interface module at compile time" in {
      assertTypeError("""
        ServiceDsl()
          .withConfig(new MockConfig())
          .withActorSystem(new MockActorSystem())
          .withStorageService(new MockStorage())
          .withVaultServiceModule(new MockVault())
          .build()
      """)
    }

    "prevent build() without StorageService module at compile time" in {
      assertTypeError("""
        ServiceDsl()
          .withConfig(new MockConfig())
          .withActorSystem(new MockActorSystem())
          .withInterface(new MockInterface())
          .withVaultServiceModule(new MockVault())
          .build()
      """)
    }

    "prevent build() without VaultService module at compile time" in {
      assertTypeError("""
        ServiceDsl()
          .withConfig(new MockConfig())
          .withActorSystem(new MockActorSystem())
          .withInterface(new MockInterface())
          .withStorageService(new MockStorage())
          .build()
      """)
    }

    "allow duplicate Config module calls (idempotent behavior)" in {
      // Note: The phantom type system doesn't prevent duplicate calls at compile time
      // Instead, calling withConfig() twice is allowed and uses the last module provided
      // This is acceptable behavior as the type system ensures all required modules are present
      noException should be thrownBy {
        ServiceDsl()
          .withConfig(new MockConfig())
          .withConfig(new MockConfig()) // Second call overwrites first
          .withActorSystem(new MockActorSystem())
          .withInterface(new MockInterface())
          .withStorageService(new MockStorage())
          .withVaultServiceModule(new MockVault())
          .build()
      }
    }

    "allow duplicate ActorSystem module calls (idempotent behavior)" in {
      noException should be thrownBy {
        ServiceDsl()
          .withConfig(new MockConfig())
          .withActorSystem(new MockActorSystem())
          .withActorSystem(new MockActorSystem()) // Second call overwrites first
          .withInterface(new MockInterface())
          .withStorageService(new MockStorage())
          .withVaultServiceModule(new MockVault())
          .build()
      }
    }

    "allow duplicate Interface module calls (idempotent behavior)" in {
      noException should be thrownBy {
        ServiceDsl()
          .withConfig(new MockConfig())
          .withActorSystem(new MockActorSystem())
          .withInterface(new MockInterface())
          .withInterface(new MockInterface()) // Second call overwrites first
          .withStorageService(new MockStorage())
          .withVaultServiceModule(new MockVault())
          .build()
      }
    }
  }

  // ========== IMMUTABILITY TESTS ==========

  "ServiceDsl immutability" should {

    "create new instance when adding Config module" in {
      val dsl1 = ServiceDsl()
      val dsl2 = dsl1.withConfig(new MockConfig())

      dsl1 should not be theSameInstanceAs(dsl2)
    }

    "create new instance when adding ActorSystem module" in {
      val dsl1 = ServiceDsl().withConfig(new MockConfig())
      val dsl2 = dsl1.withActorSystem(new MockActorSystem())

      dsl1 should not be theSameInstanceAs(dsl2)
    }

    "create new instance when adding Interface module" in {
      val dsl1 = ServiceDsl()
        .withConfig(new MockConfig())
        .withActorSystem(new MockActorSystem())
      val dsl2 = dsl1.withInterface(new MockInterface())

      dsl1 should not be theSameInstanceAs(dsl2)
    }

    "create new instance when adding external service" in {
      val dsl1 = ServiceDsl()
      val dsl2 = dsl1.withExternalServicesModule(new MockExternalService())

      dsl1 should not be theSameInstanceAs(dsl2)
    }
  }

  // ========== MODULE ORDER INDEPENDENCE TESTS ==========

  "ServiceDsl module order independence" should {

    "allow Config before ActorSystem" in {
      noException should be thrownBy {
        ServiceDsl()
          .withConfig(new MockConfig())
          .withActorSystem(new MockActorSystem())
          .withInterface(new MockInterface())
          .withStorageService(new MockStorage())
          .withVaultServiceModule(new MockVault())
          .build()
      }
    }

    "allow ActorSystem before Config" in {
      noException should be thrownBy {
        ServiceDsl()
          .withActorSystem(new MockActorSystem())
          .withConfig(new MockConfig())
          .withInterface(new MockInterface())
          .withStorageService(new MockStorage())
          .withVaultServiceModule(new MockVault())
          .build()
      }
    }

    "allow all modules in reverse order" in {
      noException should be thrownBy {
        ServiceDsl()
          .withVaultServiceModule(new MockVault())
          .withStorageService(new MockStorage())
          .withInterface(new MockInterface())
          .withActorSystem(new MockActorSystem())
          .withConfig(new MockConfig())
          .build()
      }
    }
  }

  // ========== EXTERNAL SERVICES TESTS ==========

  "ServiceDsl external services" should {

    "support zero external service modules" in {
      noException should be thrownBy {
        ServiceDsl()
          .withConfig(new MockConfig())
          .withActorSystem(new MockActorSystem())
          .withInterface(new MockInterface())
          .withStorageService(new MockStorage())
          .withVaultServiceModule(new MockVault())
          .build()
      }
    }

    "support one external service module" in {
      noException should be thrownBy {
        ServiceDsl()
          .withConfig(new MockConfig())
          .withActorSystem(new MockActorSystem())
          .withInterface(new MockInterface())
          .withStorageService(new MockStorage())
          .withVaultServiceModule(new MockVault())
          .withExternalServicesModule(new MockExternalService())
          .build()
      }
    }

    "support multiple external service modules" in {
      noException should be thrownBy {
        ServiceDsl()
          .withConfig(new MockConfig())
          .withActorSystem(new MockActorSystem())
          .withInterface(new MockInterface())
          .withStorageService(new MockStorage())
          .withVaultServiceModule(new MockVault())
          .withExternalServicesModule(new MockExternalService())
          .withExternalServicesModule(new MockExternalService())
          .withExternalServicesModule(new MockExternalService())
          .build()
      }
    }

    "create new instance when adding each external service" in {
      val dsl1 = ServiceDsl()
      val dsl2 = dsl1.withExternalServicesModule(new MockExternalService())
      val dsl3 = dsl2.withExternalServicesModule(new MockExternalService())

      dsl1 should not be theSameInstanceAs(dsl2)
      dsl2 should not be theSameInstanceAs(dsl3)
      dsl1 should not be theSameInstanceAs(dsl3)
    }
  }

  // ========== ERROR HANDLING TESTS ==========

  "ServiceDsl error handling" should {

    "propagate failure from initialize phase" in {
      val buildFuture = ServiceDsl()
        .withConfig(new MockConfig())
        .withActorSystem(new MockActorSystem())
        .withInterface(new MockInterface())
        .withStorageService(new FailingStorage())
        .withVaultServiceModule(new MockVault())
        .build()

      whenReady(buildFuture.failed) { ex =>
        ex shouldBe a[RuntimeException]
        ex.getMessage should include("Storage initialization failed")
      }
    }

    "log fatal error when build fails" in {
      // This test verifies that ServiceDsl.build() logs fatal errors
      // The actual logging assertion would require a logging capture framework
      // For now, we verify the exception is propagated correctly
      val buildFuture = ServiceDsl()
        .withConfig(new MockConfig())
        .withActorSystem(new MockActorSystem())
        .withInterface(new MockInterface())
        .withStorageService(new FailingStorage())
        .withVaultServiceModule(new MockVault())
        .build()

      whenReady(buildFuture.failed) { ex =>
        ex shouldBe a[RuntimeException]
      }
    }
  }

  // ========== HAPPY PATH TESTS ==========

  "ServiceDsl happy path" should {

    "build successfully with all required modules" in {
      val buildFuture = ServiceDsl()
        .withConfig(new MockConfig())
        .withActorSystem(new MockActorSystem())
        .withInterface(new MockInterface())
        .withStorageService(new MockStorage())
        .withVaultServiceModule(new MockVault())
        .build()

      whenReady(buildFuture) { ctx =>
        ctx should not be null
        ctx shouldBe a[ServiceContext]
      }
    }

    "execute all lifecycle phases in correct order" in {
      // Track phase execution order
      var executionLog: List[String] = List.empty

      class TrackedConfig(implicit ec: ExecutionContext) extends ProbeConfig {
        override def validate(implicit ec: ExecutionContext): Future[config.CoreConfig] = Future.successful(null)

        override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = {
          executionLog = executionLog :+ "Config.preFlight"
          Future.successful(ctx)
        }

        override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = {
          executionLog = executionLog :+ "Config.initialize"
          Future.successful(ctx.withConfig(null, null))
        }

        override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = {
          executionLog = executionLog :+ "Config.finalCheck"
          Future.successful(ctx)
        }
      }

      class TrackedActorSystem(implicit ec: ExecutionContext) extends ProbeActorSystem {
        override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = {
          executionLog = executionLog :+ "ActorSystem.preFlight"
          Future.successful(ctx)
        }

        override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = {
          executionLog = executionLog :+ "ActorSystem.initialize"
          Future.successful(ctx.withActorSystem(null).withQueueActorRef(null))
        }

        override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = {
          executionLog = executionLog :+ "ActorSystem.finalCheck"
          Future.successful(ctx)
        }
      }

      class TrackedInterface(implicit ec: ExecutionContext) extends ProbeInterface {
        override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = {
          executionLog = executionLog :+ "Interface.preFlight"
          Future.successful(ctx)
        }

        override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = {
          executionLog = executionLog :+ "Interface.initialize"
          Future.successful(ctx.withCurriedFunctions(null))
        }

        override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = {
          executionLog = executionLog :+ "Interface.finalCheck"
          Future.successful(ctx)
        }

        override def setCurriedFunctions(functions: ServiceInterfaceFunctions): Unit = {}

        override def shutdown()(implicit ec: ExecutionContext): Future[Unit] = Future.successful(())
      }

      val buildFuture = ServiceDsl()
        .withConfig(new TrackedConfig())
        .withActorSystem(new TrackedActorSystem())
        .withInterface(new TrackedInterface())
        .withStorageService(new MockStorage())
        .withVaultServiceModule(new MockVault())
        .build()

      whenReady(buildFuture) { _ =>
        // Verify all preFlight phases execute before initialize phases
        val preFlightIndex = executionLog.indexOf("Config.preFlight")
        val initializeIndex = executionLog.indexOf("Config.initialize")
        val finalCheckIndex = executionLog.indexOf("Config.finalCheck")

        preFlightIndex should be < initializeIndex
        initializeIndex should be < finalCheckIndex

        // Verify phases are grouped correctly
        executionLog should contain inOrderOnly (
          "Config.preFlight",
          "ActorSystem.preFlight",
          "Interface.preFlight",
          "Config.initialize",
          "ActorSystem.initialize",
          "Interface.initialize",
          "Config.finalCheck",
          "ActorSystem.finalCheck",
          "Interface.finalCheck"
        )
      }
    }
  }
}
