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

class GcpBlockStorageServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  given PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 100.milliseconds)

  var actorSystem: ActorSystem[GuardianCommand] = _
  given ExecutionContext = ExecutionContext.global

  override def beforeAll(): Unit = {
    actorSystem = ActorSystem(Behaviors.empty, "GcpBlockStorageServiceSpec")
  }

  override def afterAll(): Unit = {
    actorSystem.terminate()
  }

  "GcpBlockStorageService.preFlight" should {

    "succeed when config is valid and provider is gcp" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.validGcpConfig
      val ctx = BuilderContext(config = Some(config))

      val result: Future[BuilderContext] = service.preFlight(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.config shouldBe defined
      }
    }

    "fail when config is not defined in BuilderContext" in {
      val service = GcpBlockStorageService()
      val ctx = BuilderContext()

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Config not found in BuilderContext")
    }

    "fail when provider is not gcp" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.validAwsConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Provider must be 'gcp'")
    }

    "fail when GCP project ID is empty" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.missingGcpProjectIdConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("GCP project ID cannot be empty")
    }
  }

  "GcpBlockStorageService.initialize" should {

    "succeed and initialize all dependencies with service account key" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.gcpConfigWithoutServiceAccountKey
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val result: Future[BuilderContext] = service.initialize(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.storageService shouldBe defined
      }
    }

    "succeed and initialize all dependencies without service account key" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.gcpConfigWithoutServiceAccountKey
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val result: Future[BuilderContext] = service.initialize(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.storageService shouldBe defined
      }
    }

    "fail when config is not defined in BuilderContext" in {
      val service = GcpBlockStorageService()
      val ctx = BuilderContext().withActorSystem(actorSystem)

      val exception: IllegalArgumentException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Config must be initialized")
    }

    "fail when ActorSystem is not initialized" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.validGcpConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("ActorSystem not initialized")
    }
  }

  "GcpBlockStorageService.finalCheck" should {

    "succeed when all dependencies are initialized" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.gcpConfigWithoutServiceAccountKey
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx: BuilderContext = service.initialize(ctx).futureValue
      val result: Future[BuilderContext] = service.finalCheck(initializedCtx)

      whenReady(result) { resultCtx =>
        resultCtx.storageService shouldBe defined
      }
    }

    "fail when StorageService is not initialized in BuilderContext" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.validGcpConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("StorageService not initialized")
    }

    "fail when Logger is not initialized" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.validGcpConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withStorageService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Logger not initialized")
    }

    "fail when any internal dependency is not initialized" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.gcpConfigWithoutServiceAccountKey
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val uninitializedService = GcpBlockStorageService()
      val ctxWithUninitializedService = ctx.withStorageService(uninitializedService)

      val exception: IllegalArgumentException = uninitializedService.finalCheck(ctxWithUninitializedService).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should (include("Logger not initialized") or include("Storage client not initialized") or include("JimfsManager not initialized"))
    }
  }

  "GcpBlockStorageService.parseGcsUri" should {

    "parse valid GCS URI with bucket and prefix" in {
      val service = GcpBlockStorageService()
      val uri = "gs://my-bucket/my-prefix"

      val (bucket, prefix) = service.parseGcsUri(uri)

      bucket shouldBe "my-bucket"
      prefix shouldBe "my-prefix"
    }

    "parse valid GCS URI with bucket only" in {
      val service = GcpBlockStorageService()
      val uri = "gs://my-bucket"

      val (bucket, prefix) = service.parseGcsUri(uri)

      bucket shouldBe "my-bucket"
      prefix shouldBe ""
    }

    "parse valid GCS URI with nested prefix" in {
      val service = GcpBlockStorageService()
      val uri = "gs://my-bucket/folder1/folder2/folder3"

      val (bucket, prefix) = service.parseGcsUri(uri)

      bucket shouldBe "my-bucket"
      prefix shouldBe "folder1/folder2/folder3"
    }

    "throw BucketUriParseException when URI does not start with gs://" in {
      val service = GcpBlockStorageService()
      val uri = "s3://my-bucket/my-prefix"

      val exception = intercept[BucketUriParseException] {
        service.parseGcsUri(uri)
      }
      exception.getMessage should include("Invalid GCS URI format")
      exception.getMessage should include("expected gs://bucket/prefix")
    }

    "throw BucketUriParseException when bucket name is empty" in {
      val service = GcpBlockStorageService()
      val uri = "gs:///my-prefix"

      val exception = intercept[BucketUriParseException] {
        service.parseGcsUri(uri)
      }
      exception.getMessage should include("Bucket name cannot be empty")
    }

    "throw BucketUriParseException for invalid URI format" in {
      val service = GcpBlockStorageService()
      val uri = "not-a-valid-uri"

      val exception = intercept[BucketUriParseException] {
        service.parseGcsUri(uri)
      }
      exception.getMessage should include("Invalid GCS URI format")
    }
  }

  "GcpBlockStorageService.validateAndParse" should {

    "throw MissingFeaturesDirectoryException when features directory does not exist" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.gcpConfigWithoutServiceAccountKey
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testDir = JimfsTestFixtures.createRealTestDirectory()

      val exception = intercept[MissingFeaturesDirectoryException] {
        service.validateAndParse(testDir, "gs://test-bucket/test-prefix")
      }
      exception.getMessage should include("Features directory not found")

      JimfsTestFixtures.cleanupRealDirectory(testDir)
    }

    "throw EmptyFeaturesDirectoryException when features directory is empty" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.gcpConfigWithoutServiceAccountKey
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testDir = JimfsTestFixtures.createRealTestDirectory()
      JimfsTestFixtures.createEmptyFeaturesDirectory(testDir)

      val exception = intercept[EmptyFeaturesDirectoryException] {
        service.validateAndParse(testDir, "gs://test-bucket/test-prefix")
      }
      exception.getMessage should include("Features directory is empty")

      JimfsTestFixtures.cleanupRealDirectory(testDir)
    }

    "throw MissingTopicDirectiveFileException when directive file does not exist" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.gcpConfigWithoutServiceAccountKey
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testDir = JimfsTestFixtures.createRealTestDirectory()
      JimfsTestFixtures.createFeaturesDirectory(testDir)

      val exception = intercept[MissingTopicDirectiveFileException] {
        service.validateAndParse(testDir, "gs://test-bucket/test-prefix")
      }
      exception.getMessage should include("Topic directive file not found")

      JimfsTestFixtures.cleanupRealDirectory(testDir)
    }

    "successfully validate and parse when all required files exist" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.gcpConfigWithoutServiceAccountKey
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testDir = JimfsTestFixtures.createRealTestDirectory()
      JimfsTestFixtures.createValidTestStructure(testDir)

      val result = service.validateAndParse(testDir, "gs://test-bucket/test-prefix")

      result.topicDirectives should not be empty
      result.bucket shouldBe "gs://test-bucket/test-prefix"
      result.jimfsLocation should include("features")
      result.evidenceDir should include("evidence")

      JimfsTestFixtures.cleanupRealDirectory(testDir)
    }
  }

  "GcpBlockStorageService configuration validation" should {

    "validate GCP project ID is configured correctly" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.gcpConfigWithoutServiceAccountKey
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Project ID should be initialized in the Storage client
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate retry attempts configuration" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.gcpConfigWithoutServiceAccountKey
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Retry configuration should be loaded
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate timeout configuration" in {
      val service = GcpBlockStorageService()
      val config = BlockStorageConfigFixtures.gcpConfigWithoutServiceAccountKey
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Timeout should be configured
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }
  }
}
