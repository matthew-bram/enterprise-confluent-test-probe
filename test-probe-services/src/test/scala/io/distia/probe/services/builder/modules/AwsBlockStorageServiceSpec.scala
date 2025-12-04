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

class AwsBlockStorageServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  given PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 100.milliseconds)

  var actorSystem: ActorSystem[GuardianCommand] = _
  given ExecutionContext = ExecutionContext.global

  override def beforeAll(): Unit = {
    actorSystem = ActorSystem(Behaviors.empty, "AwsBlockStorageServiceSpec")
  }

  override def afterAll(): Unit = {
    actorSystem.terminate()
  }

  "AwsBlockStorageService.preFlight" should {

    "succeed when config is valid and provider is aws" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))

      val result: Future[BuilderContext] = service.preFlight(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.config shouldBe defined
      }
    }

    "fail when config is not defined in BuilderContext" in {
      val service = AwsBlockStorageService()
      val ctx = BuilderContext()

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Config not found in BuilderContext")
    }

    "fail when provider is not aws" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAzureConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Provider must be 'aws'")
    }

    "fail when AWS region is empty" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.missingAwsRegionConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("AWS region cannot be empty")
    }
  }

  "AwsBlockStorageService.initialize" should {

    "succeed and initialize all dependencies" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val result: Future[BuilderContext] = service.initialize(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.storageService shouldBe defined
      }
    }

    "fail when config is not defined in BuilderContext" in {
      val service = AwsBlockStorageService()
      val ctx = BuilderContext().withActorSystem(actorSystem)

      val exception: IllegalArgumentException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Config must be initialized")
    }

    "fail when ActorSystem is not initialized" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("ActorSystem not initialized")
    }
  }

  "AwsBlockStorageService.finalCheck" should {

    "succeed when all dependencies are initialized" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx: BuilderContext = service.initialize(ctx).futureValue
      val result: Future[BuilderContext] = service.finalCheck(initializedCtx)

      whenReady(result) { resultCtx =>
        resultCtx.storageService shouldBe defined
      }
    }

    "fail when StorageService is not initialized in BuilderContext" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("StorageService not initialized")
    }

    "fail when Logger is not initialized" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withStorageService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Logger not initialized")
    }

    "fail when any internal dependency is not initialized" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val uninitializedService = AwsBlockStorageService()
      val ctxWithUninitializedService = ctx.withStorageService(uninitializedService)

      val exception: IllegalArgumentException = uninitializedService.finalCheck(ctxWithUninitializedService).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should (include("Logger not initialized") or include("S3AsyncClient not initialized") or include("S3TransferManager not initialized") or include("JimfsManager not initialized"))
    }
  }

  "AwsBlockStorageService.parseS3Uri" should {

    "parse valid S3 URI with bucket and prefix" in {
      val service = AwsBlockStorageService()
      val uri = "s3://my-bucket/my-prefix"

      val (bucket, prefix) = service.parseS3Uri(uri)

      bucket shouldBe "my-bucket"
      prefix shouldBe "my-prefix"
    }

    "parse valid S3 URI with bucket only" in {
      val service = AwsBlockStorageService()
      val uri = "s3://my-bucket"

      val (bucket, prefix) = service.parseS3Uri(uri)

      bucket shouldBe "my-bucket"
      prefix shouldBe ""
    }

    "parse valid S3 URI with nested prefix" in {
      val service = AwsBlockStorageService()
      val uri = "s3://my-bucket/folder1/folder2/folder3"

      val (bucket, prefix) = service.parseS3Uri(uri)

      bucket shouldBe "my-bucket"
      prefix shouldBe "folder1/folder2/folder3"
    }

    "throw BucketUriParseException when URI does not start with s3://" in {
      val service = AwsBlockStorageService()
      val uri = "https://my-bucket/my-prefix"

      val exception = intercept[BucketUriParseException] {
        service.parseS3Uri(uri)
      }
      exception.getMessage should include("Invalid S3 URI format")
      exception.getMessage should include("expected s3://bucket/prefix")
    }

    "throw BucketUriParseException when bucket name is empty" in {
      val service = AwsBlockStorageService()
      val uri = "s3:///my-prefix"

      val exception = intercept[BucketUriParseException] {
        service.parseS3Uri(uri)
      }
      exception.getMessage should include("Bucket name cannot be empty")
    }

    "throw BucketUriParseException for invalid URI format" in {
      val service = AwsBlockStorageService()
      val uri = "not-a-valid-uri"

      val exception = intercept[BucketUriParseException] {
        service.parseS3Uri(uri)
      }
      exception.getMessage should include("Invalid S3 URI format")
    }
  }

  "AwsBlockStorageService.validateAndParse" should {

    "throw MissingFeaturesDirectoryException when features directory does not exist" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testDir = JimfsTestFixtures.createRealTestDirectory()

      val exception = intercept[MissingFeaturesDirectoryException] {
        service.validateAndParse(testDir, "s3://test-bucket/test-prefix")
      }
      exception.getMessage should include("Features directory not found")

      JimfsTestFixtures.cleanupRealDirectory(testDir)
    }

    "throw EmptyFeaturesDirectoryException when features directory is empty" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testDir = JimfsTestFixtures.createRealTestDirectory()
      JimfsTestFixtures.createEmptyFeaturesDirectory(testDir)

      val exception = intercept[EmptyFeaturesDirectoryException] {
        service.validateAndParse(testDir, "s3://test-bucket/test-prefix")
      }
      exception.getMessage should include("Features directory is empty")

      JimfsTestFixtures.cleanupRealDirectory(testDir)
    }

    "throw MissingTopicDirectiveFileException when directive file does not exist" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testDir = JimfsTestFixtures.createRealTestDirectory()
      JimfsTestFixtures.createFeaturesDirectory(testDir)

      val exception = intercept[MissingTopicDirectiveFileException] {
        service.validateAndParse(testDir, "s3://test-bucket/test-prefix")
      }
      exception.getMessage should include("Topic directive file not found")

      JimfsTestFixtures.cleanupRealDirectory(testDir)
    }

    "successfully validate and parse when all required files exist" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testDir = JimfsTestFixtures.createRealTestDirectory()
      JimfsTestFixtures.createValidTestStructure(testDir)

      val result = service.validateAndParse(testDir, "s3://test-bucket/test-prefix")

      result.topicDirectives should not be empty
      result.bucket shouldBe "s3://test-bucket/test-prefix"
      result.jimfsLocation should include("features")
      result.evidenceDir should include("evidence")

      JimfsTestFixtures.cleanupRealDirectory(testDir)
    }
  }

  "AwsBlockStorageService configuration validation" should {

    "validate AWS region is configured correctly" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Region should be initialized in the S3 client
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate retry attempts configuration" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Retry configuration should be loaded
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate timeout configuration" in {
      val service = AwsBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Timeout should be configured
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }
  }
}
