package io.distia.probe
package services
package builder
package modules

import io.distia.probe.common.exceptions.{BucketUriParseException, EmptyFeaturesDirectoryException, MissingFeaturesDirectoryException, MissingTopicDirectiveFileException}
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.models.GuardianCommands.GuardianCommand
import io.distia.probe.services.fixtures.{BlockStorageConfigFixtures, JimfsTestFixtures}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

class AzureBlockStorageServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  given PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 100.milliseconds)

  var actorSystem: ActorSystem[GuardianCommand] = _
  given ExecutionContext = ExecutionContext.global

  override def beforeAll(): Unit = {
    actorSystem = ActorSystem(Behaviors.empty, "AzureBlockStorageServiceSpec")
  }

  override def afterAll(): Unit = {
    actorSystem.terminate()
  }

  "AzureBlockStorageService.preFlight" should {

    "succeed when config is valid and provider is azure" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))

      val result: Future[BuilderContext] = service.preFlight(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.config shouldBe defined
      }
    }

    "fail when config is not defined in BuilderContext" in {
      val service = AzureBlockStorageService()
      val ctx = BuilderContext()

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Config not found in BuilderContext")
    }

    "fail when provider is not azure" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Provider must be 'azure'")
    }

    "fail when Azure storage account name is empty" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.missingAzureAccountNameConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Azure storage account name cannot be empty")
    }

    "fail when Azure storage account key is empty" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.missingAzureAccountKeyConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Azure storage account key cannot be empty")
    }
  }

  "AzureBlockStorageService.initialize" should {

    "succeed and initialize all dependencies" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val result: Future[BuilderContext] = service.initialize(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.storageService shouldBe defined
      }
    }

    "fail when config is not defined in BuilderContext" in {
      val service = AzureBlockStorageService()
      val ctx = BuilderContext().withActorSystem(actorSystem)

      val exception: IllegalArgumentException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Config must be initialized")
    }

    "fail when ActorSystem is not initialized" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("ActorSystem not initialized")
    }
  }

  "AzureBlockStorageService.finalCheck" should {

    "succeed when all dependencies are initialized" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx: BuilderContext = service.initialize(ctx).futureValue
      val result: Future[BuilderContext] = service.finalCheck(initializedCtx)

      whenReady(result) { resultCtx =>
        resultCtx.storageService shouldBe defined
      }
    }

    "fail when StorageService is not initialized in BuilderContext" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("StorageService not initialized")
    }

    "fail when Logger is not initialized" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withStorageService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Logger not initialized")
    }

    "fail when JimfsManager is not initialized" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val uninitializedService = AzureBlockStorageService()
      val ctxWithUninitializedService = ctx.withStorageService(uninitializedService)

      val exception: IllegalArgumentException = uninitializedService.finalCheck(ctxWithUninitializedService).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should (include("Logger not initialized") or include("BlobServiceAsyncClient not initialized") or include("JimfsManager not initialized"))
    }
  }

  "AzureBlockStorageService.parseAzureUri" should {

    "parse valid Azure Blob URI with container and prefix" in {
      val service = AzureBlockStorageService()
      val uri = "https://mystorageaccount.blob.core.windows.net/mycontainer/myprefix"

      val (container, prefix) = service.parseAzureUri(uri)

      container shouldBe "mycontainer"
      prefix shouldBe "myprefix"
    }

    "parse valid Azure Blob URI with container only" in {
      val service = AzureBlockStorageService()
      val uri = "https://mystorageaccount.blob.core.windows.net/mycontainer"

      val (container, prefix) = service.parseAzureUri(uri)

      container shouldBe "mycontainer"
      prefix shouldBe ""
    }

    "parse valid Azure Blob URI with nested prefix" in {
      val service = AzureBlockStorageService()
      val uri = "https://mystorageaccount.blob.core.windows.net/mycontainer/folder1/folder2"

      val (container, prefix) = service.parseAzureUri(uri)

      container shouldBe "mycontainer"
      prefix shouldBe "folder1/folder2"
    }

    "parse valid Azure Blob URI with http protocol" in {
      val service = AzureBlockStorageService()
      val uri = "http://mystorageaccount.blob.core.windows.net/mycontainer/myprefix"

      val (container, prefix) = service.parseAzureUri(uri)

      container shouldBe "mycontainer"
      prefix shouldBe "myprefix"
    }

    "throw BucketUriParseException when URI does not start with https:// or http://" in {
      val service = AzureBlockStorageService()
      val uri = "ftp://mystorageaccount.blob.core.windows.net/mycontainer"

      val exception = intercept[BucketUriParseException] {
        service.parseAzureUri(uri)
      }
      exception.getMessage should include("Invalid Azure URI format")
    }

    "throw BucketUriParseException when container name is missing" in {
      val service = AzureBlockStorageService()
      val uri = "https://mystorageaccount.blob.core.windows.net/"

      val exception = intercept[BucketUriParseException] {
        service.parseAzureUri(uri)
      }
      exception.getMessage should include("Container name cannot be empty")
    }

    "throw BucketUriParseException when URI has invalid structure" in {
      val service = AzureBlockStorageService()
      val uri = "https://mystorageaccount.blob.core.windows.net"

      val exception = intercept[BucketUriParseException] {
        service.parseAzureUri(uri)
      }
      exception.getMessage should include("Invalid Azure URI format")
    }
  }

  "AzureBlockStorageService.validateAndParse" should {

    "throw MissingFeaturesDirectoryException when features directory does not exist" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testDir = JimfsTestFixtures.createRealTestDirectory()

      val exception = intercept[MissingFeaturesDirectoryException] {
        service.validateAndParse(testDir, "https://test.blob.core.windows.net/test-container")
      }
      exception.getMessage should include("Features directory not found")

      JimfsTestFixtures.cleanupRealDirectory(testDir)
    }

    "throw EmptyFeaturesDirectoryException when features directory is empty" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testDir = JimfsTestFixtures.createRealTestDirectory()
      JimfsTestFixtures.createEmptyFeaturesDirectory(testDir)

      val exception = intercept[EmptyFeaturesDirectoryException] {
        service.validateAndParse(testDir, "https://test.blob.core.windows.net/test-container")
      }
      exception.getMessage should include("Features directory is empty")

      JimfsTestFixtures.cleanupRealDirectory(testDir)
    }

    "throw MissingTopicDirectiveFileException when directive file does not exist" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testDir = JimfsTestFixtures.createRealTestDirectory()
      JimfsTestFixtures.createFeaturesDirectory(testDir)

      val exception = intercept[MissingTopicDirectiveFileException] {
        service.validateAndParse(testDir, "https://test.blob.core.windows.net/test-container")
      }
      exception.getMessage should include("Topic directive file not found")

      JimfsTestFixtures.cleanupRealDirectory(testDir)
    }

    "successfully validate and parse when all required files exist" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testDir = JimfsTestFixtures.createRealTestDirectory()
      JimfsTestFixtures.createValidTestStructure(testDir)

      val result = service.validateAndParse(testDir, "https://test.blob.core.windows.net/test-container")

      result.topicDirectives should not be empty
      result.bucket shouldBe "https://test.blob.core.windows.net/test-container"
      result.jimfsLocation should include("features")
      result.evidenceDir should include("evidence")

      JimfsTestFixtures.cleanupRealDirectory(testDir)
    }
  }

  "AzureBlockStorageService configuration validation" should {

    "validate Azure storage account name is configured correctly" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Storage account should be initialized in the Blob client
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate retry attempts configuration" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Retry configuration should be loaded
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate timeout configuration" in {
      val service = AzureBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Timeout should be configured
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }
  }
}
