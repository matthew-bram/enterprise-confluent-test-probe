package io.distia.probe
package services
package builder
package modules

import com.azure.storage.blob.{BlobServiceClient, BlobServiceClientBuilder}
import io.distia.probe.common.exceptions.{MissingFeaturesDirectoryException, MissingTopicDirectiveFileException}
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.models.GuardianCommands.GuardianCommand
import io.distia.probe.services.fixtures.AzuriteBlobClientFactory
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
import scala.jdk.CollectionConverters.*

class AzureBlockStorageServiceIntegrationSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  given PatienceConfig = PatienceConfig(timeout = 30.seconds, interval = 500.milliseconds)

  var actorSystem: ActorSystem[GuardianCommand] = _
  given ExecutionContext = ExecutionContext.global

  private val azurite = {
    val container = new GenericContainer(DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:latest"))
    container.withExposedPorts(java.lang.Integer.valueOf(10000))
    container.withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--blobPort", "10000")
    container
  }

  private var blobServiceClient: BlobServiceClient = _
  private val testContainer = "test-probe-container"

  override def beforeAll(): Unit = {
    azurite.start()

    actorSystem = ActorSystem(Behaviors.empty, "AzureBlockStorageServiceIntegrationSpec")

    val azuriteEndpoint = s"http://${azurite.getHost}:${azurite.getMappedPort(10000)}/devstoreaccount1"
    val azuriteConnectionString = s"DefaultEndpointsProtocol=http;" +
      s"AccountName=devstoreaccount1;" +
      s"AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;" +
      s"BlobEndpoint=$azuriteEndpoint;"

    blobServiceClient = new BlobServiceClientBuilder()
      .connectionString(azuriteConnectionString)
      .buildClient()

    blobServiceClient.createBlobContainer(testContainer)
  }

  override def afterAll(): Unit = {
    actorSystem.terminate()
    azurite.stop()
  }

  private def uploadTestFilesToAzure(prefix: String): Unit = {
    val containerClient = blobServiceClient.getBlobContainerClient(testContainer)

    val featureContent = """Feature: Test Feature
      |  Scenario: Test Scenario
      |    Given a test step
      |    When something happens
      |    Then verify result
    """.stripMargin

    val blobClient = containerClient.getBlobClient(s"$prefix/features/test.feature")
    blobClient.upload(java.io.ByteArrayInputStream(featureContent.getBytes), featureContent.length)

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

    val directiveBlob = containerClient.getBlobClient(s"$prefix/topic-directive.yaml")
    directiveBlob.upload(java.io.ByteArrayInputStream(directiveContent.getBytes), directiveContent.length)
  }

  private def createServiceWithAzurite(): AzureBlockStorageService = {
    val azuriteEndpoint = s"http://${azurite.getHost}:${azurite.getMappedPort(10000)}/devstoreaccount1"
    val factory = new AzuriteBlobClientFactory(azuriteEndpoint)
    AzureBlockStorageService(Some(factory))
  }

  private def createTestConfig(): Config = {
    ConfigFactory.parseString("""
      |test-probe.services.block-storage {
      |  provider = "azure"
      |  topic-directive-file-name = "topic-directive.yaml"
      |
      |  aws {
      |    region = "us-east-1"
      |    retry-attempts = 3
      |    timeout = "30s"
      |  }
      |
      |  azure {
      |    storage-account-name = "devstoreaccount1"
      |    storage-account-key = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=="
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

  "AzureBlockStorageService.fetchFromBlockStorage" should {

    "successfully download files from Azure container" in {
      val service = createServiceWithAzurite()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue
      service.finalCheck(initializedCtx).futureValue

      val testPrefix = s"integration-test-${UUID.randomUUID()}"
      uploadTestFilesToAzure(testPrefix)

      val testId = UUID.randomUUID()
      val azuriteEndpoint = s"http://${azurite.getHost}:${azurite.getMappedPort(10000)}/devstoreaccount1"
      val azureUri = s"$azuriteEndpoint/$testContainer/$testPrefix"

      val result = service.fetchFromBlockStorage(testId, azureUri).futureValue

      result.topicDirectives should have size 1
      result.topicDirectives.head.topic shouldBe "test-topic"
      result.topicDirectives.head.role shouldBe "producer"
      result.topicDirectives.head.clientPrincipal shouldBe "test-principal"
      result.bucket shouldBe azureUri
      result.jimfsLocation should include("features")
      result.evidenceDir should include("evidence")
    }

    "throw MissingFeaturesDirectoryException when features directory is missing" in {
      val service = createServiceWithAzurite()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testPrefix = s"no-features-${UUID.randomUUID()}"
      val containerClient = blobServiceClient.getBlobContainerClient(testContainer)
      val directiveBlob = containerClient.getBlobClient(s"$testPrefix/topic-directive.yaml")
      val directiveContent = "topics: []"
      directiveBlob.upload(java.io.ByteArrayInputStream(directiveContent.getBytes), directiveContent.length)

      val testId = UUID.randomUUID()
      val azuriteEndpoint = s"http://${azurite.getHost}:${azurite.getMappedPort(10000)}/devstoreaccount1"
      val azureUri = s"$azuriteEndpoint/$testContainer/$testPrefix"

      val exception = service.fetchFromBlockStorage(testId, azureUri).failed.futureValue
      exception shouldBe a[MissingFeaturesDirectoryException]
      exception.getMessage should include("Features directory not found")
    }

    "throw MissingTopicDirectiveFileException when directive file is missing" in {
      val service = createServiceWithAzurite()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testPrefix = s"no-directive-${UUID.randomUUID()}"
      val containerClient = blobServiceClient.getBlobContainerClient(testContainer)
      val featureBlob = containerClient.getBlobClient(s"$testPrefix/features/test.feature")
      val featureContent = "Feature: Test"
      featureBlob.upload(java.io.ByteArrayInputStream(featureContent.getBytes), featureContent.length)

      val testId = UUID.randomUUID()
      val azuriteEndpoint = s"http://${azurite.getHost}:${azurite.getMappedPort(10000)}/devstoreaccount1"
      val azureUri = s"$azuriteEndpoint/$testContainer/$testPrefix"

      val exception = service.fetchFromBlockStorage(testId, azureUri).failed.futureValue
      exception shouldBe a[MissingTopicDirectiveFileException]
      exception.getMessage should include("Topic directive file not found")
    }

    "handle Azure URI with nested prefix" in {
      val service = createServiceWithAzurite()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testPrefix = s"folder1/folder2/${UUID.randomUUID()}"
      uploadTestFilesToAzure(testPrefix)

      val testId = UUID.randomUUID()
      val azuriteEndpoint = s"http://${azurite.getHost}:${azurite.getMappedPort(10000)}/devstoreaccount1"
      val azureUri = s"$azuriteEndpoint/$testContainer/$testPrefix"

      val result = service.fetchFromBlockStorage(testId, azureUri).futureValue

      result.bucket shouldBe azureUri
      result.topicDirectives should not be empty
    }

    "download multiple feature files from Azure" in {
      val service = createServiceWithAzurite()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testPrefix = s"multi-features-${UUID.randomUUID()}"
      val containerClient = blobServiceClient.getBlobContainerClient(testContainer)

      val feature1 = containerClient.getBlobClient(s"$testPrefix/features/test1.feature")
      feature1.upload(java.io.ByteArrayInputStream("Feature: Test 1".getBytes), "Feature: Test 1".length)

      val feature2 = containerClient.getBlobClient(s"$testPrefix/features/test2.feature")
      feature2.upload(java.io.ByteArrayInputStream("Feature: Test 2".getBytes), "Feature: Test 2".length)

      val directiveContent = """topics:
        |  - topic: multi-topic
        |    role: consumer
        |    clientPrincipal: test-principal
        |    eventFilters: []
        |    metadata: {}
      """.stripMargin
      val directive = containerClient.getBlobClient(s"$testPrefix/topic-directive.yaml")
      directive.upload(java.io.ByteArrayInputStream(directiveContent.getBytes), directiveContent.length)

      val testId = UUID.randomUUID()
      val azuriteEndpoint = s"http://${azurite.getHost}:${azurite.getMappedPort(10000)}/devstoreaccount1"
      val azureUri = s"$azuriteEndpoint/$testContainer/$testPrefix"

      val result = service.fetchFromBlockStorage(testId, azureUri).futureValue

      result.topicDirectives.head.topic shouldBe "multi-topic"
    }
  }

  "AzureBlockStorageService.loadToBlockStorage" should {

    "successfully upload evidence files to Azure" in {
      val service = createServiceWithAzurite()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val evidenceDir = Files.createTempDirectory("evidence-test")
      try {
        Files.write(evidenceDir.resolve("test-evidence.txt"), "Test evidence content".getBytes)
        Files.createDirectory(evidenceDir.resolve("nested"))
        Files.write(evidenceDir.resolve("nested/nested-evidence.txt"), "Nested evidence".getBytes)

        val testId = UUID.randomUUID()
        val testPrefix = s"upload-test-${UUID.randomUUID()}"
        val azuriteEndpoint = s"http://${azurite.getHost}:${azurite.getMappedPort(10000)}/devstoreaccount1"
        val azureUri = s"$azuriteEndpoint/$testContainer/$testPrefix"

        service.loadToBlockStorage(testId, azureUri, evidenceDir.toString).futureValue

        val containerClient = blobServiceClient.getBlobContainerClient(testContainer)
        val uploadedBlobs = containerClient.listBlobs().asScala
          .map(_.getName)
          .filter(_.startsWith(s"$testPrefix/evidence"))
          .toList

        uploadedBlobs should contain(s"$testPrefix/evidence/test-evidence.txt")
        uploadedBlobs should contain(s"$testPrefix/evidence/nested/nested-evidence.txt")

      } finally {
        Files.walk(evidenceDir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(Files.delete)
      }
    }

    "handle non-existent evidence directory gracefully" in {
      val service = createServiceWithAzurite()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testId = UUID.randomUUID()
      val testPrefix = s"non-existent-${UUID.randomUUID()}"
      val azuriteEndpoint = s"http://${azurite.getHost}:${azurite.getMappedPort(10000)}/devstoreaccount1"
      val azureUri = s"$azuriteEndpoint/$testContainer/$testPrefix"
      val nonExistentPath = s"/tmp/does-not-exist-${UUID.randomUUID()}"

      service.loadToBlockStorage(testId, azureUri, nonExistentPath).futureValue
    }

    "upload multiple files with correct blob key structure" in {
      val service = createServiceWithAzurite()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val evidenceDir = Files.createTempDirectory("multi-file-evidence")
      try {
        Files.write(evidenceDir.resolve("file1.txt"), "Content 1".getBytes)
        Files.write(evidenceDir.resolve("file2.txt"), "Content 2".getBytes)
        Files.write(evidenceDir.resolve("file3.txt"), "Content 3".getBytes)

        val testId = UUID.randomUUID()
        val testPrefix = s"multi-file-${UUID.randomUUID()}"
        val azuriteEndpoint = s"http://${azurite.getHost}:${azurite.getMappedPort(10000)}/devstoreaccount1"
        val azureUri = s"$azuriteEndpoint/$testContainer/$testPrefix"

        service.loadToBlockStorage(testId, azureUri, evidenceDir.toString).futureValue

        val containerClient = blobServiceClient.getBlobContainerClient(testContainer)
        val uploadedBlobs = containerClient.listBlobs().asScala
          .map(_.getName)
          .filter(_.startsWith(s"$testPrefix/evidence"))
          .toSet

        uploadedBlobs should contain(s"$testPrefix/evidence/file1.txt")
        uploadedBlobs should contain(s"$testPrefix/evidence/file2.txt")
        uploadedBlobs should contain(s"$testPrefix/evidence/file3.txt")

      } finally {
        Files.walk(evidenceDir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(Files.delete)
      }
    }

    "preserve directory structure when uploading nested files" in {
      val service = createServiceWithAzurite()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val evidenceDir = Files.createTempDirectory("nested-evidence")
      try {
        val subDir1 = evidenceDir.resolve("reports")
        val subDir2 = evidenceDir.resolve("logs/debug")
        Files.createDirectories(subDir1)
        Files.createDirectories(subDir2)

        Files.write(subDir1.resolve("report.txt"), "Report content".getBytes)
        Files.write(subDir2.resolve("debug.log"), "Debug logs".getBytes)

        val testId = UUID.randomUUID()
        val testPrefix = s"nested-upload-${UUID.randomUUID()}"
        val azuriteEndpoint = s"http://${azurite.getHost}:${azurite.getMappedPort(10000)}/devstoreaccount1"
        val azureUri = s"$azuriteEndpoint/$testContainer/$testPrefix"

        service.loadToBlockStorage(testId, azureUri, evidenceDir.toString).futureValue

        val containerClient = blobServiceClient.getBlobContainerClient(testContainer)
        val uploadedBlobs = containerClient.listBlobs().asScala
          .map(_.getName)
          .filter(_.startsWith(s"$testPrefix/evidence"))
          .toSet

        uploadedBlobs should contain(s"$testPrefix/evidence/reports/report.txt")
        uploadedBlobs should contain(s"$testPrefix/evidence/logs/debug/debug.log")

      } finally {
        Files.walk(evidenceDir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(Files.delete)
      }
    }
  }
}