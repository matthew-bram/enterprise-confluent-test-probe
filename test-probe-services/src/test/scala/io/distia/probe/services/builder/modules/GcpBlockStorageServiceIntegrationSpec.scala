package io.distia.probe
package services
package builder
package modules

import io.distia.probe.common.exceptions.{MissingFeaturesDirectoryException, MissingTopicDirectiveFileException}
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.models.GuardianCommands.GuardianCommand
import io.distia.probe.services.fixtures.FakeGcsStorageClientFactory
import com.google.cloud.storage.{BlobId, BlobInfo, Storage, StorageOptions}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

import java.nio.file.Files
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class GcpBlockStorageServiceIntegrationSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  given PatienceConfig = PatienceConfig(timeout = 30.seconds, interval = 500.milliseconds)

  var actorSystem: ActorSystem[GuardianCommand] = _
  given ExecutionContext = ExecutionContext.global

  private val fakeGcsServer = {
    val container = new GenericContainer(DockerImageName.parse("fsouza/fake-gcs-server:latest"))
    container.withExposedPorts(java.lang.Integer.valueOf(4443))
    container.withCommand("-scheme", "http")
    container
  }

  private var storageClient: Storage = _
  private val testBucket = "test-probe-bucket"

  override def beforeAll(): Unit = {
    fakeGcsServer.start()

    actorSystem = ActorSystem(Behaviors.empty, "GcpBlockStorageServiceIntegrationSpec")

    val fakeGcsHost = s"http://${fakeGcsServer.getHost}:${fakeGcsServer.getMappedPort(4443)}"
    storageClient = StorageOptions.newBuilder()
      .setHost(fakeGcsHost)
      .setProjectId("test-project")
      .build()
      .getService

    storageClient.create(com.google.cloud.storage.BucketInfo.of(testBucket))
  }

  override def afterAll(): Unit = {
    actorSystem.terminate()
    fakeGcsServer.stop()
  }

  private def uploadTestFilesToGcs(prefix: String): Unit = {
    val featureContent = """Feature: Test Feature
      |  Scenario: Test Scenario
      |    Given a test step
      |    When something happens
      |    Then verify result
    """.stripMargin

    val featureBlobId = BlobId.of(testBucket, s"$prefix/features/test.feature")
    val featureBlobInfo = BlobInfo.newBuilder(featureBlobId).build()
    storageClient.create(featureBlobInfo, featureContent.getBytes)

    val directiveContent = """topics:
      |  - topic: test-topic
      |    role: producer
      |    clientPrincipal: test-principal
      |    eventFilters:
      |      - key: "eventType"
      |        value: "TestEvent"
      |    metadata:
      |      version: "1.0"
    """.stripMargin

    val directiveBlobId = BlobId.of(testBucket, s"$prefix/topic-directive.yaml")
    val directiveBlobInfo = BlobInfo.newBuilder(directiveBlobId).build()
    storageClient.create(directiveBlobInfo, directiveContent.getBytes)
  }

  private def createServiceWithFakeGcs(): GcpBlockStorageService = {
    val fakeGcsHost = s"http://${fakeGcsServer.getHost}:${fakeGcsServer.getMappedPort(4443)}"
    val factory = new FakeGcsStorageClientFactory(fakeGcsHost)
    GcpBlockStorageService(Some(factory))
  }

  private def createTestConfig(): Config = {
    ConfigFactory.parseString("""
      |test-probe.services.block-storage {
      |  provider = "gcp"
      |  topic-directive-file-name = "topic-directive.yaml"
      |
      |  aws {
      |    region = "us-east-1"
      |    retry-attempts = 3
      |    timeout = "30s"
      |  }
      |
      |  azure {
      |    storage-account-name = "test"
      |    storage-account-key = "test"
      |    timeout = "30s"
      |    retry-attempts = 3
      |  }
      |
      |  gcp {
      |    project-id = "test-project"
      |    service-account-key = ""
      |    timeout = "30s"
      |    retry-attempts = 3
      |  }
      |}
    """.stripMargin)
  }

  "GcpBlockStorageService.fetchFromBlockStorage" should {

    "successfully download files from GCS bucket" in {
      val service = createServiceWithFakeGcs()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue
      service.finalCheck(initializedCtx).futureValue

      val testPrefix = s"integration-test-${UUID.randomUUID()}"
      uploadTestFilesToGcs(testPrefix)

      val testId = UUID.randomUUID()
      val gcsUri = s"gs://$testBucket/$testPrefix"

      val result = service.fetchFromBlockStorage(testId, gcsUri).futureValue

      result.topicDirectives should have size 1
      result.topicDirectives.head.topic shouldBe "test-topic"
      result.topicDirectives.head.role shouldBe "producer"
      result.topicDirectives.head.clientPrincipal shouldBe "test-principal"
      result.bucket shouldBe gcsUri
      result.jimfsLocation should include("features")
      result.evidenceDir should include("evidence")
    }

    "throw MissingFeaturesDirectoryException when features directory is missing" in {
      val service = createServiceWithFakeGcs()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testPrefix = s"no-features-${UUID.randomUUID()}"
      val directiveBlobId = BlobId.of(testBucket, s"$testPrefix/topic-directive.yaml")
      val directiveBlobInfo = BlobInfo.newBuilder(directiveBlobId).build()
      val directiveContent = "topics: []"
      storageClient.create(directiveBlobInfo, directiveContent.getBytes)

      val testId = UUID.randomUUID()
      val gcsUri = s"gs://$testBucket/$testPrefix"

      val exception = service.fetchFromBlockStorage(testId, gcsUri).failed.futureValue
      exception shouldBe a[MissingFeaturesDirectoryException]
      exception.getMessage should include("Features directory not found")
    }

    "throw MissingTopicDirectiveFileException when directive file is missing" in {
      val service = createServiceWithFakeGcs()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testPrefix = s"no-directive-${UUID.randomUUID()}"
      val featureBlobId = BlobId.of(testBucket, s"$testPrefix/features/test.feature")
      val featureBlobInfo = BlobInfo.newBuilder(featureBlobId).build()
      val featureContent = "Feature: Test"
      storageClient.create(featureBlobInfo, featureContent.getBytes)

      val testId = UUID.randomUUID()
      val gcsUri = s"gs://$testBucket/$testPrefix"

      val exception = service.fetchFromBlockStorage(testId, gcsUri).failed.futureValue
      exception shouldBe a[MissingTopicDirectiveFileException]
      exception.getMessage should include("Topic directive file not found")
    }

    "download multiple feature files from nested directories" in {
      val service = createServiceWithFakeGcs()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testPrefix = s"nested-test-${UUID.randomUUID()}"

      // Create multiple feature files in nested structure
      val feature1Content = "Feature: First Feature\n  Scenario: Test 1"
      val feature1BlobId = BlobId.of(testBucket, s"$testPrefix/features/module1/feature1.feature")
      storageClient.create(BlobInfo.newBuilder(feature1BlobId).build(), feature1Content.getBytes)

      val feature2Content = "Feature: Second Feature\n  Scenario: Test 2"
      val feature2BlobId = BlobId.of(testBucket, s"$testPrefix/features/module2/feature2.feature")
      storageClient.create(BlobInfo.newBuilder(feature2BlobId).build(), feature2Content.getBytes)

      // Create directive file
      val directiveContent = """topics:
        |  - topic: test-topic
        |    role: producer
        |    clientPrincipal: test-principal
        |    eventFilters: []
        |    metadata: {}
      """.stripMargin
      val directiveBlobId = BlobId.of(testBucket, s"$testPrefix/topic-directive.yaml")
      storageClient.create(BlobInfo.newBuilder(directiveBlobId).build(), directiveContent.getBytes)

      val testId = UUID.randomUUID()
      val gcsUri = s"gs://$testBucket/$testPrefix"

      val result = service.fetchFromBlockStorage(testId, gcsUri).futureValue

      result.topicDirectives should have size 1
      result.bucket shouldBe gcsUri
      result.jimfsLocation should include("features")
    }

    "handle bucket URI with trailing slash" in {
      val service = createServiceWithFakeGcs()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testPrefix = s"trailing-slash-${UUID.randomUUID()}"
      uploadTestFilesToGcs(testPrefix)

      val testId = UUID.randomUUID()
      val gcsUri = s"gs://$testBucket/$testPrefix/"

      val result = service.fetchFromBlockStorage(testId, gcsUri).futureValue

      result.topicDirectives should have size 1
      result.bucket shouldBe gcsUri
    }
  }

  "GcpBlockStorageService.loadToBlockStorage" should {

    "successfully upload evidence files to GCS bucket" in {
      val service = createServiceWithFakeGcs()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testId = UUID.randomUUID()
      val testPrefix = s"upload-test-${UUID.randomUUID()}"

      // Create local evidence directory with test files
      val evidenceDir = Files.createTempDirectory("evidence")
      val testFile1 = evidenceDir.resolve("result1.txt")
      Files.writeString(testFile1, "Test evidence 1")
      val testFile2 = evidenceDir.resolve("result2.txt")
      Files.writeString(testFile2, "Test evidence 2")

      val gcsUri = s"gs://$testBucket/$testPrefix"

      service.loadToBlockStorage(testId, gcsUri, evidenceDir.toString).futureValue

      // Verify files were uploaded
      val blob1 = storageClient.get(BlobId.of(testBucket, s"$testPrefix/evidence/result1.txt"))
      blob1 should not be null
      new String(blob1.getContent()) shouldBe "Test evidence 1"

      val blob2 = storageClient.get(BlobId.of(testBucket, s"$testPrefix/evidence/result2.txt"))
      blob2 should not be null
      new String(blob2.getContent()) shouldBe "Test evidence 2"

      // Cleanup
      Files.deleteIfExists(testFile1)
      Files.deleteIfExists(testFile2)
      Files.deleteIfExists(evidenceDir)
    }

    "successfully upload nested evidence files" in {
      val service = createServiceWithFakeGcs()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testId = UUID.randomUUID()
      val testPrefix = s"nested-upload-${UUID.randomUUID()}"

      // Create nested evidence structure
      val evidenceDir = Files.createTempDirectory("evidence")
      val subDir = evidenceDir.resolve("screenshots")
      Files.createDirectories(subDir)
      val screenshot = subDir.resolve("test.png")
      Files.writeString(screenshot, "fake-png-data")

      val gcsUri = s"gs://$testBucket/$testPrefix"

      service.loadToBlockStorage(testId, gcsUri, evidenceDir.toString).futureValue

      // Verify nested file was uploaded
      val blob = storageClient.get(BlobId.of(testBucket, s"$testPrefix/evidence/screenshots/test.png"))
      blob should not be null
      new String(blob.getContent()) shouldBe "fake-png-data"

      // Cleanup
      Files.deleteIfExists(screenshot)
      Files.deleteIfExists(subDir)
      Files.deleteIfExists(evidenceDir)
    }

    "handle empty evidence directory gracefully" in {
      val service = createServiceWithFakeGcs()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testId = UUID.randomUUID()
      val testPrefix = s"empty-upload-${UUID.randomUUID()}"

      // Create empty evidence directory
      val evidenceDir = Files.createTempDirectory("evidence-empty")

      val gcsUri = s"gs://$testBucket/$testPrefix"

      noException should be thrownBy {
        service.loadToBlockStorage(testId, gcsUri, evidenceDir.toString).futureValue
      }

      // Cleanup
      Files.deleteIfExists(evidenceDir)
    }

    "handle non-existent evidence directory gracefully" in {
      val service = createServiceWithFakeGcs()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testId = UUID.randomUUID()
      val testPrefix = s"nonexistent-upload-${UUID.randomUUID()}"

      val nonExistentDir = s"/tmp/does-not-exist-${UUID.randomUUID()}"

      val gcsUri = s"gs://$testBucket/$testPrefix"

      noException should be thrownBy {
        service.loadToBlockStorage(testId, gcsUri, nonExistentDir).futureValue
      }
    }
  }
}
