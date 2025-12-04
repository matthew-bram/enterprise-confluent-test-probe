package io.distia.probe
package services
package builder
package modules

import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.models.GuardianCommands.GuardianCommand
import io.distia.probe.services.fixtures.VaultConfigFixtures
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

class LocalVaultServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  given PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 100.milliseconds)

  var actorSystem: ActorSystem[GuardianCommand] = _
  given ExecutionContext = ExecutionContext.global

  override def beforeAll(): Unit = {
    actorSystem = ActorSystem(Behaviors.empty, "LocalVaultServiceSpec")
  }

  override def afterAll(): Unit = {
    actorSystem.terminate()
  }

  "LocalVaultService.preFlight" should {

    "succeed when config is valid and provider is local" in {
      val service = new LocalVaultService()
      val config = VaultConfigFixtures.defaultLocalVaultConfig
      val ctx = BuilderContext(config = Some(config))

      val result: Future[BuilderContext] = service.preFlight(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.config shouldBe defined
      }
    }

    "fail when config is not defined in BuilderContext" in {
      val service = new LocalVaultService()
      val ctx = BuilderContext()

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Config not found in BuilderContext")
    }

    "fail when provider is not local" in {
      val service = new LocalVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Provider must be 'local'")
    }
  }

  "LocalVaultService.initialize" should {

    "succeed and initialize jimfs vault filesystem" in {
      val service = new LocalVaultService()
      val config = VaultConfigFixtures.defaultLocalVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val result: Future[BuilderContext] = service.initialize(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.vaultService shouldBe defined
      }
    }

    "fail when config is not defined in BuilderContext" in {
      val service = new LocalVaultService()
      val ctx = BuilderContext().withActorSystem(actorSystem)

      val exception: IllegalArgumentException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Config must be initialized")
    }

    "fail when ActorSystem is not initialized" in {
      val service = new LocalVaultService()
      val config = VaultConfigFixtures.defaultLocalVaultConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("ActorSystem not initialized")
    }
  }

  "LocalVaultService.finalCheck" should {

    "succeed when all dependencies are initialized" in {
      val service = new LocalVaultService()
      val config = VaultConfigFixtures.defaultLocalVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx: BuilderContext = service.initialize(ctx).futureValue
      val result: Future[BuilderContext] = service.finalCheck(initializedCtx)

      whenReady(result) { resultCtx =>
        resultCtx.vaultService shouldBe defined
      }
    }

    "fail when VaultService is not initialized in BuilderContext" in {
      val service = new LocalVaultService()
      val config = VaultConfigFixtures.defaultLocalVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("VaultService not initialized")
    }
  }

  "LocalVaultService.fetchSecurityDirectives" should {

    "return PLAINTEXT security directives for all topics" in {
      val service = new LocalVaultService()
      val config = VaultConfigFixtures.defaultLocalVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val topicDirectives: List[TopicDirective] = List(
        TopicDirective("topic1", "PRODUCER", "client1", List.empty),
        TopicDirective("topic2", "CONSUMER", "client2", List.empty)
      )
      val directive = BlockStorageDirective("test-id", "test-file", topicDirectives, "bucket")

      val result: Future[List[KafkaSecurityDirective]] = service.fetchSecurityDirectives(directive)

      whenReady(result) { securityDirectives =>
        securityDirectives.size shouldBe 2
        securityDirectives(0).topic shouldBe "topic1"
        securityDirectives(0).role shouldBe "PRODUCER"
        securityDirectives(0).securityProtocol shouldBe SecurityProtocol.PLAINTEXT
        securityDirectives(0).jaasConfig shouldBe ""
        securityDirectives(1).topic shouldBe "topic2"
        securityDirectives(1).role shouldBe "CONSUMER"
        securityDirectives(1).securityProtocol shouldBe SecurityProtocol.PLAINTEXT
        securityDirectives(1).jaasConfig shouldBe ""
      }
    }

    "return empty list when no topic directives provided" in {
      val service = new LocalVaultService()
      val config = VaultConfigFixtures.defaultLocalVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val directive = BlockStorageDirective("test-id", "test-file", List.empty, "bucket")

      val result: Future[List[KafkaSecurityDirective]] = service.fetchSecurityDirectives(directive)

      whenReady(result) { securityDirectives =>
        securityDirectives shouldBe empty
      }
    }
  }

  "LocalVaultService.loadVaultData" should {

    "create /vault directory in jimfs" in {
      val service = new LocalVaultService()
      val config = VaultConfigFixtures.defaultLocalVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      noException should be thrownBy service.loadVaultData()
    }
  }

  "LocalVaultService.shutdown" should {

    "close jimfs filesystem successfully" in {
      val service = new LocalVaultService()
      val config = VaultConfigFixtures.defaultLocalVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val result: Future[Unit] = service.shutdown()

      whenReady(result) { _ =>
        succeed
      }
    }

    "succeed even when jimfs is not initialized" in {
      val service = new LocalVaultService()

      val result: Future[Unit] = service.shutdown()

      whenReady(result) { _ =>
        succeed
      }
    }
  }
}
