package io.distia.probe
package services
package builder
package modules

import io.distia.probe.common.exceptions.*
import io.distia.probe.common.models.BlockStorageDirective
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.builder.modules.ProbeStorageService
import config.BlockStorageConfig
import factories.{DefaultS3ClientFactory, S3ClientFactory}
import storage.{JimfsManager, TopicDirectiveMapper}
import org.slf4j.Logger
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{ListObjectsV2Request, S3Exception}
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.{CompletedFileDownload, CompletedFileUpload, DownloadFileRequest, UploadFileRequest}

import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*
import scala.util.{Failure, Success, Try}

/**
 * Factory for creating AwsBlockStorageService instances.
 *
 * Provides a default factory implementation or accepts a custom S3ClientFactory for testing.
 */
private[services] object AwsBlockStorageService {
  def apply(s3ClientFactory: Option[S3ClientFactory] = None): AwsBlockStorageService = s3ClientFactory match {
    case None => new AwsBlockStorageService(new DefaultS3ClientFactory())
    case Some(factory) => new AwsBlockStorageService(factory)
  }
}

/**
 * AwsBlockStorageService - AWS S3 implementation of ProbeStorageService
 *
 * Provides test evidence storage using Amazon S3 for production and cloud-based scenarios.
 * Uses AWS SDK v2 with async S3TransferManager for high-performance streaming uploads and
 * downloads. Test artifacts are staged in JIMFS during execution, then evidence is uploaded
 * to S3 after test completion.
 *
 * Storage Backend: AWS S3
 *
 * Lifecycle:
 * - preFlight: Validates that provider is "aws" and region is configured
 * - initialize: Creates S3AsyncClient and S3TransferManager instances for the configured region
 * - finalCheck: Verifies all components (S3AsyncClient, S3TransferManager, JimfsManager, etc.) are initialized
 *
 * AWS SDK Integration:
 * - Uses S3AsyncClient for async operations (non-blocking)
 * - Uses S3TransferManager for efficient multi-part uploads/downloads
 * - Supports parallel file transfers for improved performance
 *
 * Thread Safety: This service uses @volatile fields to ensure visibility across threads during initialization.
 * After initialization is complete, all fields are effectively immutable. The AWS SDK clients are thread-safe.
 *
 * @param s3ClientFactory factory for creating AWS S3 clients (injectable for testing)
 * @see ProbeStorageService for the trait this implements
 * @see JimfsManager for in-memory filesystem management
 * @see TopicDirectiveMapper for YAML parsing
 * @see S3TransferManager for AWS SDK streaming operations
 */
private[services] class AwsBlockStorageService(val s3ClientFactory: S3ClientFactory) extends ProbeStorageService {

  @volatile private var logger: Option[Logger] = None
  @volatile private var s3AsyncClient: Option[S3AsyncClient] = None
  @volatile private var transferManager: Option[S3TransferManager] = None
  @volatile private var jimfsManager: Option[JimfsManager] = None
  @volatile private var topicDirectiveMapper: Option[TopicDirectiveMapper] = None
  @volatile private var config: Option[BlockStorageConfig] = None

  /**
   * Validates AWS-specific configuration before service initialization.
   *
   * Ensures that the storage provider is set to "aws" and that the AWS region is configured.
   *
   * @param ctx the builder context containing configuration
   * @param ec execution context for async operations
   * @return Future containing the validated BuilderContext
   * @throws IllegalArgumentException if config is missing, provider is not "aws", or region is empty
   */
  override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config not found in BuilderContext")
    Try {
      val blockStorageConfig: BlockStorageConfig = BlockStorageConfig.fromConfig(ctx.config.get)
      require(
        blockStorageConfig.provider.nonEmpty && blockStorageConfig.provider.equals("aws"),
        "Provider must be 'aws' for AwsBlockStorageService"
      )
      require(blockStorageConfig.aws.region.nonEmpty, "AWS region cannot be empty when provider is 'aws'")
      ctx
    } match {
      case Success(context) => context
      case Failure(ex) => throw ex
    }
  }

  /**
   * Initializes the AwsBlockStorageService with AWS SDK clients.
   *
   * Creates S3AsyncClient for the configured region and S3TransferManager for high-performance
   * streaming operations. Also initializes JimfsManager and TopicDirectiveMapper.
   *
   * @param ctx the builder context containing ActorSystem and configuration
   * @param ec execution context for async operations
   * @return Future containing the updated BuilderContext with this service registered
   * @throws IllegalStateException if initialization fails or required components are missing
   */
  override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config must be initialized before AwsBlockStorageService")
    require(ctx.actorSystem.isDefined, "ActorSystem not initialized")
    ctx.actorSystem.get.log.info("Initializing AwsBlockStorageService with async S3TransferManager")
    Try {
      val blockStorageConfig: BlockStorageConfig = BlockStorageConfig.fromConfig(ctx.config.get)
      logger = Some(ctx.actorSystem.get.log)
      config = Some(blockStorageConfig)
      jimfsManager = Some(new JimfsManager())
      topicDirectiveMapper = Some(new TopicDirectiveMapper())

      val asyncClient = s3ClientFactory.createAsyncClient(blockStorageConfig.aws.region)
      s3AsyncClient = Some(asyncClient)
      transferManager = Some(s3ClientFactory.createTransferManager(asyncClient))

      ctx.actorSystem.get.log.info(s"AwsBlockStorageService initialized with async client, region=${blockStorageConfig.aws.region}")
      ctx.withStorageService(this)
    } match {
      case Success(context) => context
      case Failure(ex) => throw new IllegalStateException("Could not initialize AwsBlockStorageService", ex)
    }
  }

  /**
   * Verifies that all AWS service components are properly initialized.
   *
   * Performs final validation that S3AsyncClient, S3TransferManager, JimfsManager,
   * TopicDirectiveMapper, and configuration are all present.
   *
   * @param ctx the builder context to validate
   * @param ec execution context for async operations
   * @return Future containing the validated BuilderContext
   * @throws IllegalArgumentException if any required component is missing
   */
  override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.storageService.isDefined, "StorageService not initialized in BuilderContext")
    require(logger.isDefined, "Logger not initialized")
    require(s3AsyncClient.isDefined, "S3AsyncClient not initialized")
    require(transferManager.isDefined, "S3TransferManager not initialized")
    require(jimfsManager.isDefined, "JimfsManager not initialized")
    require(topicDirectiveMapper.isDefined, "TopicDirectiveMapper not initialized")
    require(config.isDefined, "BlockStorageConfig not initialized")
    ctx
  }

  /**
   * Fetches test artifacts from S3 bucket into JIMFS staging area.
   *
   * Downloads all objects from the specified S3 bucket and prefix using S3TransferManager for
   * efficient parallel transfers. Files are staged in JIMFS, then validated and parsed.
   *
   * @param testId unique identifier for this test execution
   * @param bucket S3 URI in format "s3://bucket-name/optional-prefix"
   * @param ec execution context for async operations
   * @return Future containing BlockStorageDirective with paths to features, evidence, and topic configuration
   * @throws BucketUriParseException if S3 URI format is invalid
   * @throws StreamingException if S3 download fails
   * @throws MissingFeaturesDirectoryException if features directory is not found
   * @throws EmptyFeaturesDirectoryException if features directory contains no files
   * @throws MissingTopicDirectiveFileException if topic directive file is not found
   */
  override def fetchFromBlockStorage(testId: UUID, bucket: String)(implicit ec: ExecutionContext): Future[BlockStorageDirective] = {
    val (bucketName: String, prefix: String) = parseS3Uri(bucket)
    val testDir: Path = jimfsManager.get.createTestDirectory(testId)

    val listRequest = ListObjectsV2Request.builder()
      .bucket(bucketName)
      .prefix(prefix)
      .build()

    s3AsyncClient.get.listObjectsV2(listRequest).asScala.flatMap { response =>
      val downloadFutures = response.contents().asScala.collect {
        case s3Object if {
          val relativePath = s3Object.key().stripPrefix(prefix).stripPrefix("/")
          relativePath.nonEmpty
        } =>
          val objectKey: String = s3Object.key()
          val relativePath: String = objectKey.stripPrefix(prefix).stripPrefix("/")
          val targetPath: Path = testDir.resolve(relativePath)
          Files.createDirectories(targetPath.getParent)

          val downloadRequest = DownloadFileRequest.builder()
            .getObjectRequest(builder => builder.bucket(bucketName).key(objectKey))
            .destination(targetPath)
            .build()

          transferManager.get.downloadFile(downloadRequest).completionFuture().asScala
      }.toSeq

      Future.sequence(downloadFutures).map { _ =>
        validateAndParse(testDir, bucket)
      }
    }.recoverWith {
      case ex: S3Exception =>
        jimfsManager.get.deleteTestDirectory(testId)
        Future.failed(new StreamingException(s"Failed to fetch from S3 bucket '$bucketName' with prefix '$prefix': ${ex.getMessage}", ex))
      case ex =>
        jimfsManager.get.deleteTestDirectory(testId)
        Future.failed(ex)
    }
  }

  /**
   * Uploads test evidence to S3 bucket and cleans up JIMFS.
   *
   * Uses S3TransferManager to efficiently upload all evidence files to the S3 bucket under
   * the "evidence/" subdirectory. Supports parallel uploads for improved performance.
   * Always cleans up the JIMFS test directory after completion or failure.
   *
   * @param testId unique identifier for the test execution
   * @param bucket S3 URI in format "s3://bucket-name/optional-prefix"
   * @param evidence path to local evidence directory in JIMFS
   * @param ec execution context for async operations
   * @return Future that completes when upload and cleanup are done
   * @throws StreamingException if S3 upload fails
   */
  override def loadToBlockStorage(testId: UUID, bucket: String, evidence: String)(implicit ec: ExecutionContext): Future[Unit] = {
    val (bucketName: String, prefix: String) = parseS3Uri(bucket)
    val evidencePath: Path = Path.of(evidence)

    Try {
      if Files.exists(evidencePath) && Files.isDirectory(evidencePath) then
        val uploadFutures = Files.walk(evidencePath).iterator().asScala
          .filter(Files.isRegularFile(_))
          .map { file =>
            val relativePath: String = evidencePath.relativize(file).toString
            val s3Key: String = s"$prefix/evidence/$relativePath"

            val uploadRequest = UploadFileRequest.builder()
              .putObjectRequest(builder => builder.bucket(bucketName).key(s3Key))
              .source(file)
              .build()

            transferManager.get.uploadFile(uploadRequest).completionFuture().asScala
          }.toSeq

        Future.sequence(uploadFutures)
      else
        Future.successful(Seq.empty)
    } match {
      case Success(uploadsFuture) =>
        uploadsFuture.map { _ =>
          jimfsManager.get.deleteTestDirectory(testId)
        }.recoverWith {
          case ex: S3Exception =>
            jimfsManager.get.deleteTestDirectory(testId)
            Future.failed(new StreamingException(s"Failed to upload evidence to S3 bucket '$bucketName': ${ex.getMessage}", ex))
          case ex =>
            jimfsManager.get.deleteTestDirectory(testId)
            Future.failed(ex)
        }
      case Failure(ex) =>
        jimfsManager.get.deleteTestDirectory(testId)
        Future.failed(ex)
    }
  }

  /**
   * Parses and validates an S3 URI.
   *
   * @param uri the S3 URI to parse (format: s3://bucket/prefix)
   * @return tuple of (bucketName, prefix)
   * @throws BucketUriParseException if URI format is invalid
   */
  def parseS3Uri(uri: String): (String, String) = Try {
    if !uri.startsWith("s3://") then
      throw new BucketUriParseException(s"Invalid S3 URI format: $uri (expected s3://bucket/prefix)")

    val withoutProtocol: String = uri.stripPrefix("s3://")
    val parts: Array[String] = withoutProtocol.split("/", 2)
    val bucketName: String = parts(0)
    val prefix: String = if parts.length > 1 then parts(1) else ""

    if bucketName.isEmpty then
      throw new BucketUriParseException(s"Bucket name cannot be empty in S3 URI: $uri")

    (bucketName, prefix)
  } match {
    case Success(result) => result
    case Failure(ex: BucketUriParseException) => throw ex
    case Failure(ex) => throw new BucketUriParseException(s"Failed to parse S3 URI '$uri': ${ex.getMessage}", ex)
  }

  /**
   * Validates test directory structure and parses topic directive configuration.
   *
   * @param testDir the JIMFS test directory to validate
   * @param bucket the original S3 URI (for error messages)
   * @return BlockStorageDirective containing all test configuration
   * @throws MissingFeaturesDirectoryException if features directory is missing
   * @throws EmptyFeaturesDirectoryException if features directory is empty
   * @throws MissingTopicDirectiveFileException if topic directive file is missing
   */
  def validateAndParse(testDir: Path, bucket: String): BlockStorageDirective = {
    val featuresDir: Path = testDir.resolve("features")
    if !Files.exists(featuresDir) then
      throw new MissingFeaturesDirectoryException(s"Features directory not found in bucket '$bucket': $featuresDir")

    if !Files.list(featuresDir).findAny().isPresent then
      throw new EmptyFeaturesDirectoryException(s"Features directory is empty in bucket '$bucket': $featuresDir")

    val directiveFile: Path = testDir.resolve(config.get.topicDirectiveFileName)
    if !Files.exists(directiveFile) then
      throw new MissingTopicDirectiveFileException(
        s"Topic directive file not found in bucket '$bucket': $directiveFile (expected: ${config.get.topicDirectiveFileName})"
      )

    val yamlContent: String = Files.readString(directiveFile)
    val topicDirectives = topicDirectiveMapper.get.parse(yamlContent)

    val evidenceDir: Path = testDir.resolve("evidence")
    Files.createDirectories(evidenceDir)

    BlockStorageDirective(
      jimfsLocation = featuresDir.toString,
      evidenceDir = evidenceDir.toString,
      topicDirectives = topicDirectives,
      bucket = bucket
    )
  }
}
