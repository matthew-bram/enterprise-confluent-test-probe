package io.distia.probe
package services
package builder
package modules

import io.distia.probe.common.exceptions.*
import io.distia.probe.common.models.BlockStorageDirective
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.builder.modules.ProbeStorageService
import config.BlockStorageConfig
import factories.{DefaultStorageClientFactory, StorageClientFactory}
import storage.{JimfsManager, TopicDirectiveMapper}
import com.google.cloud.storage.{BlobId, BlobInfo, Storage, StorageException}
import org.slf4j.Logger

import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

/**
 * Factory for creating GcpBlockStorageService instances.
 *
 * Provides a default factory implementation or accepts a custom StorageClientFactory for testing.
 */
private[services] object GcpBlockStorageService {
  def apply(storageClientFactory: Option[StorageClientFactory] = None): GcpBlockStorageService = storageClientFactory match {
    case None => new GcpBlockStorageService(new DefaultStorageClientFactory())
    case Some(factory) => new GcpBlockStorageService(factory)
  }
}

/**
 * GcpBlockStorageService - Google Cloud Storage implementation of ProbeStorageService
 *
 * Provides test evidence storage using Google Cloud Storage for production and cloud-based scenarios.
 * Uses Google Cloud Storage Java SDK for synchronous operations with Future-based async wrappers.
 * Test artifacts are staged in JIMFS during execution, then evidence is uploaded to GCS after
 * test completion.
 *
 * Storage Backend: Google Cloud Storage (GCS)
 *
 * Lifecycle:
 * - preFlight: Validates that provider is "gcp" and project ID is configured
 * - initialize: Creates Storage client for the configured GCP project
 * - finalCheck: Verifies all components (Storage client, JimfsManager, etc.) are initialized
 *
 * GCP SDK Integration:
 * - Uses Storage client for blob operations
 * - Operations are synchronous but wrapped in Future for consistency
 * - Supports service account key authentication or default credentials
 *
 * Thread Safety: This service uses @volatile fields to ensure visibility across threads during initialization.
 * After initialization is complete, all fields are effectively immutable. The GCP Storage client is thread-safe.
 *
 * @param storageClientFactory factory for creating GCP Storage clients (injectable for testing)
 * @see ProbeStorageService for the trait this implements
 * @see JimfsManager for in-memory filesystem management
 * @see TopicDirectiveMapper for YAML parsing
 * @see Storage for GCP SDK operations
 */
private[services] class GcpBlockStorageService(val storageClientFactory: StorageClientFactory) extends ProbeStorageService {

  @volatile private var logger: Option[Logger] = None
  @volatile private var storageClient: Option[Storage] = None
  @volatile private var jimfsManager: Option[JimfsManager] = None
  @volatile private var topicDirectiveMapper: Option[TopicDirectiveMapper] = None
  @volatile private var config: Option[BlockStorageConfig] = None

  /**
   * Validates GCP-specific configuration before service initialization.
   *
   * Ensures that the storage provider is set to "gcp" and that the GCP project ID is configured.
   *
   * @param ctx the builder context containing configuration
   * @param ec execution context for async operations
   * @return Future containing the validated BuilderContext
   * @throws IllegalArgumentException if config is missing, provider is not "gcp", or project ID is empty
   */
  override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config not found in BuilderContext")
    Try {
      val blockStorageConfig: BlockStorageConfig = BlockStorageConfig.fromConfig(ctx.config.get)
      require(
        blockStorageConfig.provider.nonEmpty && blockStorageConfig.provider.equals("gcp"),
        "Provider must be 'gcp' for GcpBlockStorageService"
      )
      require(blockStorageConfig.gcp.projectId.nonEmpty, "GCP project ID cannot be empty when provider is 'gcp'")
      ctx
    } match {
      case Success(context) => context
      case Failure(ex) => throw ex
    }
  }

  /**
   * Initializes the GcpBlockStorageService with GCP SDK clients.
   *
   * Creates Storage client for the configured GCP project using service account key or default
   * credentials. Also initializes JimfsManager and TopicDirectiveMapper.
   *
   * @param ctx the builder context containing ActorSystem and configuration
   * @param ec execution context for async operations
   * @return Future containing the updated BuilderContext with this service registered
   * @throws IllegalStateException if initialization fails or required components are missing
   */
  override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config must be initialized before GcpBlockStorageService")
    require(ctx.actorSystem.isDefined, "ActorSystem not initialized")
    ctx.actorSystem.get.log.info("Initializing GcpBlockStorageService")
    Try {
      val blockStorageConfig: BlockStorageConfig = BlockStorageConfig.fromConfig(ctx.config.get)
      logger = Some(ctx.actorSystem.get.log)
      config = Some(blockStorageConfig)
      jimfsManager = Some(new JimfsManager())
      topicDirectiveMapper = Some(new TopicDirectiveMapper())

      storageClient = Some(storageClientFactory.createClient(
        blockStorageConfig.gcp.projectId,
        blockStorageConfig.gcp.serviceAccountKey
      ))
      ctx.actorSystem.get.log.info(s"GcpBlockStorageService initialized with projectId=${blockStorageConfig.gcp.projectId}")
      ctx.withStorageService(this)
    } match {
      case Success(context) => context
      case Failure(ex) => throw new IllegalStateException("Could not initialize GcpBlockStorageService", ex)
    }
  }

  /**
   * Verifies that all GCP service components are properly initialized.
   *
   * Performs final validation that Storage client, JimfsManager, TopicDirectiveMapper,
   * and configuration are all present.
   *
   * @param ctx the builder context to validate
   * @param ec execution context for async operations
   * @return Future containing the validated BuilderContext
   * @throws IllegalArgumentException if any required component is missing
   */
  override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.storageService.isDefined, "StorageService not initialized in BuilderContext")
    require(logger.isDefined, "Logger not initialized")
    require(storageClient.isDefined, "Storage client not initialized")
    require(jimfsManager.isDefined, "JimfsManager not initialized")
    require(topicDirectiveMapper.isDefined, "TopicDirectiveMapper not initialized")
    require(config.isDefined, "BlockStorageConfig not initialized")
    ctx
  }

  /**
   * Fetches test artifacts from Google Cloud Storage into JIMFS staging area.
   *
   * Downloads all blobs from the specified GCS bucket and prefix. Files are staged in JIMFS,
   * then validated and parsed.
   *
   * @param testId unique identifier for this test execution
   * @param bucket GCS URI in format "gs://bucket-name/optional-prefix"
   * @param ec execution context for async operations
   * @return Future containing BlockStorageDirective with paths to features, evidence, and topic configuration
   * @throws BucketUriParseException if GCS URI format is invalid
   * @throws StreamingException if GCS download fails
   * @throws MissingFeaturesDirectoryException if features directory is not found
   * @throws EmptyFeaturesDirectoryException if features directory contains no files
   * @throws MissingTopicDirectiveFileException if topic directive file is not found
   */
  override def fetchFromBlockStorage(testId: UUID, bucket: String)(implicit ec: ExecutionContext): Future[BlockStorageDirective] = Future {
    val (bucketName: String, prefix: String) = parseGcsUri(bucket)
    val testDir: Path = jimfsManager.get.createTestDirectory(testId)

    Try {
      val blobs = storageClient.get.list(bucketName, Storage.BlobListOption.prefix(prefix))

      blobs.iterateAll().asScala.foreach { blob =>
        val blobName: String = blob.getName
        val relativePath: String = blobName.stripPrefix(prefix).stripPrefix("/")
        if relativePath.nonEmpty then
          val targetPath: Path = testDir.resolve(relativePath)
          Files.createDirectories(targetPath.getParent)

          blob.downloadTo(targetPath)
      }

      validateAndParse(testDir, bucket)
    } match {
      case Success(directive) => directive
      case Failure(ex: StorageException) =>
        jimfsManager.get.deleteTestDirectory(testId)
        throw new StreamingException(s"Failed to fetch from GCS bucket '$bucketName' with prefix '$prefix': ${ex.getMessage}", ex)
      case Failure(ex) =>
        jimfsManager.get.deleteTestDirectory(testId)
        throw ex
    }
  }

  /**
   * Uploads test evidence to Google Cloud Storage and cleans up JIMFS.
   *
   * Uploads all evidence files to the GCS bucket under the "evidence/" subdirectory.
   * Always cleans up the JIMFS test directory after completion or failure.
   *
   * @param testId unique identifier for the test execution
   * @param bucket GCS URI in format "gs://bucket-name/optional-prefix"
   * @param evidence path to local evidence directory in JIMFS
   * @param ec execution context for async operations
   * @return Future that completes when upload and cleanup are done
   * @throws StreamingException if GCS upload fails
   */
  override def loadToBlockStorage(testId: UUID, bucket: String, evidence: String)(implicit ec: ExecutionContext): Future[Unit] = Future {
    val (bucketName: String, prefix: String) = parseGcsUri(bucket)
    val evidencePath: Path = Path.of(evidence)

    Try {
      if Files.exists(evidencePath) && Files.isDirectory(evidencePath) then
        Files.walk(evidencePath).iterator().asScala
          .filter(Files.isRegularFile(_))
          .foreach { file =>
            val relativePath: String = evidencePath.relativize(file).toString
            val blobName: String = s"$prefix/evidence/$relativePath"

            val blobId: BlobId = BlobId.of(bucketName, blobName)
            val blobInfo: BlobInfo = BlobInfo.newBuilder(blobId).build()

            val fileBytes: Array[Byte] = Files.readAllBytes(file)
            storageClient.get.create(blobInfo, fileBytes)
          }
    } match {
      case Success(_) =>
        jimfsManager.get.deleteTestDirectory(testId)
      case Failure(ex: StorageException) =>
        jimfsManager.get.deleteTestDirectory(testId)
        throw new StreamingException(s"Failed to upload evidence to GCS bucket '$bucketName': ${ex.getMessage}", ex)
      case Failure(ex) =>
        jimfsManager.get.deleteTestDirectory(testId)
        throw ex
    }
  }

  /**
   * Parses and validates a GCS URI.
   *
   * @param uri the GCS URI to parse (format: gs://bucket/prefix)
   * @return tuple of (bucketName, prefix)
   * @throws BucketUriParseException if URI format is invalid
   */
  private[modules] def parseGcsUri(uri: String): (String, String) = Try {
    if !uri.startsWith("gs://") then
      throw new BucketUriParseException(s"Invalid GCS URI format: $uri (expected gs://bucket/prefix)")

    val withoutProtocol: String = uri.stripPrefix("gs://")
    val parts: Array[String] = withoutProtocol.split("/", 2)
    val bucketName: String = parts(0)
    val prefix: String = if parts.length > 1 then parts(1) else ""

    if bucketName.isEmpty then
      throw new BucketUriParseException(s"Bucket name cannot be empty in GCS URI: $uri")

    (bucketName, prefix)
  } match {
    case Success(result) => result
    case Failure(ex: BucketUriParseException) => throw ex
    case Failure(ex) => throw new BucketUriParseException(s"Failed to parse GCS URI '$uri': ${ex.getMessage}", ex)
  }

  /**
   * Validates test directory structure and parses topic directive configuration.
   *
   * @param testDir the JIMFS test directory to validate
   * @param bucket the original GCS URI (for error messages)
   * @return BlockStorageDirective containing all test configuration
   * @throws MissingFeaturesDirectoryException if features directory is missing
   * @throws EmptyFeaturesDirectoryException if features directory is empty
   * @throws MissingTopicDirectiveFileException if topic directive file is missing
   */
  private[modules] def validateAndParse(testDir: Path, bucket: String): BlockStorageDirective = {
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
