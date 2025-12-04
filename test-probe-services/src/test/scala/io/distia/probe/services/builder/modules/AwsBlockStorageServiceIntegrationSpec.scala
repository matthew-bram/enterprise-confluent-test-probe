package io.distia.probe
package services
package builder
package modules

import io.distia.probe.common.exceptions.{EmptyFeaturesDirectoryException, MissingFeaturesDirectoryException, MissingTopicDirectiveFileException, StreamingException}
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.models.GuardianCommands.GuardianCommand
import io.distia.probe.services.fixtures.{BlockStorageConfigFixtures, LocalStackS3ClientFactory}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, PutObjectRequest}

import java.nio.file.Files
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class AwsBlockStorageServiceIntegrationSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  given PatienceConfig = PatienceConfig(timeout = 30.seconds, interval = 500.milliseconds)

  var actorSystem: ActorSystem[GuardianCommand] = _
  given ExecutionContext = ExecutionContext.global

  // LocalStack container for S3 integration testing
  private val localstack = new LocalStackContainer(
    DockerImageName.parse("localstack/localstack:3.0")
  ).withServices(Service.S3)

  private var s3Client: S3Client = _
  private val testBucket = "test-probe-bucket"

  override def beforeAll(): Unit = {
    localstack.start()

    actorSystem = ActorSystem(Behaviors.empty, "AwsBlockStorageServiceIntegrationSpec")

    // Create sync S3 client for test setup (uploading test files)
    s3Client = S3Client.builder()
      .endpointOverride(localstack.getEndpointOverride(Service.S3))
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(
            localstack.getAccessKey,
            localstack.getSecretKey
          )
        )
      )
      .region(Region.of(localstack.getRegion))
      .build()

    // Create test bucket
    s3Client.createBucket(CreateBucketRequest.builder().bucket(testBucket).build())
  }

  override def afterAll(): Unit = {
    if s3Client != null then s3Client.close()
    actorSystem.terminate()
    localstack.stop()
  }

  private def uploadTestFilesToS3(prefix: String): Unit = {
    // Upload features directory with .feature file
    s3Client.putObject(
      PutObjectRequest.builder()
        .bucket(testBucket)
        .key(s"$prefix/features/test.feature")
        .build(),
      RequestBody.fromString("""Feature: Test Feature
        |  Scenario: Test Scenario
        |    Given a test step
        |    When something happens
        |    Then verify result
      """.stripMargin)
    )

    // Upload topic-directive.yaml
    s3Client.putObject(
      PutObjectRequest.builder()
        .bucket(testBucket)
        .key(s"$prefix/topic-directive.yaml")
        .build(),
      RequestBody.fromString("""topics:
        |  - topic: test-topic
        |    role: producer
        |    clientPrincipal: test-principal
        |    eventFilters:
        |      - key: "eventType"
        |        value: "TestEvent"
        |    metadata:
        |      version: "1.0"
      """.stripMargin)
    )
  }

  private def createServiceWithLocalStack(): AwsBlockStorageService = {
    val factory = new LocalStackS3ClientFactory(
      localstack.getEndpointOverride(Service.S3),
      localstack.getRegion,
      localstack.getAccessKey,
      localstack.getSecretKey
    )
    AwsBlockStorageService(Some(factory))
  }

  private def createTestConfig(): Config = {
    ConfigFactory.parseString(s"""
      |test-probe.services.block-storage {
      |  provider = "aws"
      |  topic-directive-file-name = "topic-directive.yaml"
      |
      |  aws {
      |    region = "${localstack.getRegion}"
      |    retry-attempts = 3
      |    timeout = "30s"
      |  }
      |
      |  azure {
      |    storage-account-name = "teststorage"
      |    storage-account-key = "dummykey123"
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

  "AwsBlockStorageService.fetchFromBlockStorage" should {

    "successfully download files from S3 bucket" in {
      val service = createServiceWithLocalStack()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      // Initialize service
      val initializedCtx = service.initialize(ctx).futureValue
      service.finalCheck(initializedCtx).futureValue

      // Upload test files to S3
      val testPrefix = s"integration-test-${UUID.randomUUID()}"
      uploadTestFilesToS3(testPrefix)

      // Test fetchFromBlockStorage
      val testId = UUID.randomUUID()
      val s3Uri = s"s3://$testBucket/$testPrefix"

      val result = service.fetchFromBlockStorage(testId, s3Uri).futureValue

      // Verify results
      result.topicDirectives should have size 1
      result.topicDirectives.head.topic shouldBe "test-topic"
      result.topicDirectives.head.role shouldBe "producer"
      result.topicDirectives.head.clientPrincipal shouldBe "test-principal"
      result.bucket shouldBe s3Uri
      result.jimfsLocation should include("features")
      result.evidenceDir should include("evidence")
    }

    "throw MissingFeaturesDirectoryException when features directory is missing" in {
      val service = createServiceWithLocalStack()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // Upload only the directive file, no features directory
      val testPrefix = s"no-features-${UUID.randomUUID()}"
      s3Client.putObject(
        PutObjectRequest.builder()
          .bucket(testBucket)
          .key(s"$testPrefix/topic-directive.yaml")
          .build(),
        RequestBody.fromString("topics: []")
      )

      val testId = UUID.randomUUID()
      val s3Uri = s"s3://$testBucket/$testPrefix"

      val exception = service.fetchFromBlockStorage(testId, s3Uri).failed.futureValue
      exception shouldBe a[MissingFeaturesDirectoryException]
      exception.getMessage should include("Features directory not found")
    }

    "throw MissingTopicDirectiveFileException when directive file is missing" in {
      val service = createServiceWithLocalStack()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // Upload only features file, no directive
      val testPrefix = s"no-directive-${UUID.randomUUID()}"
      s3Client.putObject(
        PutObjectRequest.builder()
          .bucket(testBucket)
          .key(s"$testPrefix/features/test.feature")
          .build(),
        RequestBody.fromString("Feature: Test")
      )

      val testId = UUID.randomUUID()
      val s3Uri = s"s3://$testBucket/$testPrefix"

      val exception = service.fetchFromBlockStorage(testId, s3Uri).failed.futureValue
      exception shouldBe a[MissingTopicDirectiveFileException]
      exception.getMessage should include("Topic directive file not found")
    }

    "handle S3 URI with nested prefix" in {
      val service = createServiceWithLocalStack()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // Upload test files with nested prefix
      val testPrefix = s"folder1/folder2/${UUID.randomUUID()}"
      uploadTestFilesToS3(testPrefix)

      val testId = UUID.randomUUID()
      val s3Uri = s"s3://$testBucket/$testPrefix"

      val result = service.fetchFromBlockStorage(testId, s3Uri).futureValue

      result.bucket shouldBe s3Uri
      result.topicDirectives should not be empty
    }

    "download multiple feature files from S3" in {
      val service = createServiceWithLocalStack()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // Upload multiple feature files
      val testPrefix = s"multi-features-${UUID.randomUUID()}"
      s3Client.putObject(
        PutObjectRequest.builder()
          .bucket(testBucket)
          .key(s"$testPrefix/features/test1.feature")
          .build(),
        RequestBody.fromString("Feature: Test 1")
      )
      s3Client.putObject(
        PutObjectRequest.builder()
          .bucket(testBucket)
          .key(s"$testPrefix/features/test2.feature")
          .build(),
        RequestBody.fromString("Feature: Test 2")
      )
      s3Client.putObject(
        PutObjectRequest.builder()
          .bucket(testBucket)
          .key(s"$testPrefix/topic-directive.yaml")
          .build(),
        RequestBody.fromString("""topics:
          |  - topic: multi-topic
          |    role: consumer
          |    clientPrincipal: test-principal
          |    eventFilters: []
          |    metadata: {}
        """.stripMargin)
      )

      val testId = UUID.randomUUID()
      val s3Uri = s"s3://$testBucket/$testPrefix"

      val result = service.fetchFromBlockStorage(testId, s3Uri).futureValue

      result.topicDirectives.head.topic shouldBe "multi-topic"
    }
  }

  "AwsBlockStorageService.loadToBlockStorage" should {

    "successfully upload evidence files to S3" in {
      val service = createServiceWithLocalStack()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // Create temporary evidence directory with test files
      val evidenceDir = Files.createTempDirectory("evidence-test")
      try {
        Files.write(evidenceDir.resolve("test-evidence.txt"), "Test evidence content".getBytes)
        Files.createDirectory(evidenceDir.resolve("nested"))
        Files.write(evidenceDir.resolve("nested/nested-evidence.txt"), "Nested evidence".getBytes)

        val testId = UUID.randomUUID()
        val testPrefix = s"upload-test-${UUID.randomUUID()}"
        val s3Uri = s"s3://$testBucket/$testPrefix"

        // Test loadToBlockStorage
        service.loadToBlockStorage(testId, s3Uri, evidenceDir.toString).futureValue

        // Verify files were uploaded to S3
        val objects = s3Client.listObjectsV2(builder =>
          builder.bucket(testBucket).prefix(s"$testPrefix/evidence")
        )

        val uploadedKeys = objects.contents().asScala.map(_.key()).toList
        uploadedKeys should contain(s"$testPrefix/evidence/test-evidence.txt")
        uploadedKeys should contain(s"$testPrefix/evidence/nested/nested-evidence.txt")

      } finally {
        // Cleanup
        Files.walk(evidenceDir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(Files.delete)
      }
    }

    "handle non-existent evidence directory gracefully" in {
      val service = createServiceWithLocalStack()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val testId = UUID.randomUUID()
      val testPrefix = s"non-existent-${UUID.randomUUID()}"
      val s3Uri = s"s3://$testBucket/$testPrefix"
      val nonExistentPath = s"/tmp/does-not-exist-${UUID.randomUUID()}"

      // Should complete without error (no files to upload)
      service.loadToBlockStorage(testId, s3Uri, nonExistentPath).futureValue
    }

    "upload multiple files with correct S3 key structure" in {
      val service = createServiceWithLocalStack()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // Create evidence directory with multiple files
      val evidenceDir = Files.createTempDirectory("multi-file-evidence")
      try {
        Files.write(evidenceDir.resolve("file1.txt"), "Content 1".getBytes)
        Files.write(evidenceDir.resolve("file2.txt"), "Content 2".getBytes)
        Files.write(evidenceDir.resolve("file3.txt"), "Content 3".getBytes)

        val testId = UUID.randomUUID()
        val testPrefix = s"multi-file-${UUID.randomUUID()}"
        val s3Uri = s"s3://$testBucket/$testPrefix"

        service.loadToBlockStorage(testId, s3Uri, evidenceDir.toString).futureValue

        // Verify all files were uploaded
        val objects = s3Client.listObjectsV2(builder =>
          builder.bucket(testBucket).prefix(s"$testPrefix/evidence")
        )

        val uploadedKeys = objects.contents().asScala.map(_.key()).toSet
        uploadedKeys should contain(s"$testPrefix/evidence/file1.txt")
        uploadedKeys should contain(s"$testPrefix/evidence/file2.txt")
        uploadedKeys should contain(s"$testPrefix/evidence/file3.txt")

      } finally {
        Files.walk(evidenceDir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(Files.delete)
      }
    }

    "preserve directory structure when uploading nested files" in {
      val service = createServiceWithLocalStack()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // Create nested directory structure
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
        val s3Uri = s"s3://$testBucket/$testPrefix"

        service.loadToBlockStorage(testId, s3Uri, evidenceDir.toString).futureValue

        // Verify nested structure is preserved
        val objects = s3Client.listObjectsV2(builder =>
          builder.bucket(testBucket).prefix(s"$testPrefix/evidence")
        )

        val uploadedKeys = objects.contents().asScala.map(_.key()).toSet
        uploadedKeys should contain(s"$testPrefix/evidence/reports/report.txt")
        uploadedKeys should contain(s"$testPrefix/evidence/logs/debug/debug.log")

      } finally {
        Files.walk(evidenceDir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(Files.delete)
      }
    }
  }
}
