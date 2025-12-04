package io.distia.probe
package services
package builder
package modules

import io.distia.probe.common.exceptions.{BucketUriParseException, EmptyFeaturesDirectoryException, MissingFeaturesDirectoryException, MissingTopicDirectiveFileException}
import io.distia.probe.common.models.BlockStorageDirective
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.builder.modules.ProbeStorageService
import io.distia.probe.services.config.BlockStorageConfig
import io.distia.probe.services.storage.{JimfsManager, TopicDirectiveMapper}

import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * LocalBlockStorageService - Local filesystem implementation of ProbeStorageService
 *
 * Provides test evidence storage using the local filesystem for development and testing scenarios.
 * Uses JIMFS (Java in-memory filesystem) to stage test artifacts during execution, then discards
 * them after test completion. This service is ideal for local development, CI/CD environments,
 * and scenarios where cloud storage is not available or required.
 *
 * Storage Backend: Local filesystem (with in-memory JIMFS staging)
 *
 * Lifecycle:
 * - preFlight: Validates that provider is "local" in configuration
 * - initialize: Creates JimfsManager and TopicDirectiveMapper instances
 * - finalCheck: Verifies all components (JimfsManager, TopicDirectiveMapper, config) are initialized
 *
 * Thread Safety: This service uses @volatile fields to ensure visibility across threads during initialization.
 * After initialization is complete, all fields are effectively immutable.
 *
 * @see ProbeStorageService for the trait this implements
 * @see JimfsManager for in-memory filesystem management
 * @see TopicDirectiveMapper for YAML parsing
 */
class LocalBlockStorageService extends ProbeStorageService {

  @volatile private var jimfsManager: Option[JimfsManager] = None
  @volatile private var topicDirectiveMapper: Option[TopicDirectiveMapper] = None
  @volatile private var config: Option[BlockStorageConfig] = None

  /**
   * Validates configuration before service initialization.
   *
   * Ensures that the storage provider is set to "local" and that all required configuration
   * is present in the BuilderContext.
   *
   * @param ctx the builder context containing configuration
   * @param ec execution context for async operations
   * @return Future containing the validated BuilderContext
   * @throws IllegalArgumentException if config is missing or provider is not "local"
   */
  override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config not found in BuilderContext")
    Try {
      val blockStorageConfig: BlockStorageConfig = BlockStorageConfig.fromConfig(ctx.config.get)
      require(
        blockStorageConfig.provider.nonEmpty && blockStorageConfig.provider.equals("local"),
        "Provider must be 'local' for LocalBlockStorageService"
      )
      ctx
    } match {
      case Success(context) => context
      case Failure(ex) => throw ex
    }
  }

  /**
   * Initializes the LocalBlockStorageService with required components.
   *
   * Creates instances of JimfsManager for in-memory filesystem management and TopicDirectiveMapper
   * for parsing YAML topic directive files. Registers this service with the BuilderContext.
   *
   * @param ctx the builder context containing ActorSystem and configuration
   * @param ec execution context for async operations
   * @return Future containing the updated BuilderContext with this service registered
   * @throws IllegalStateException if initialization fails or required components are missing
   */
  override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config must be initialized before LocalBlockStorageService")
    require(ctx.actorSystem.isDefined, "ActorSystem not initialized")
    ctx.actorSystem.get.log.info("Initializing LocalBlockStorageService")
    Try {
      config = Some(BlockStorageConfig.fromConfig(ctx.config.get))
      jimfsManager = Some(new JimfsManager())
      topicDirectiveMapper = Some(new TopicDirectiveMapper())
      ctx.actorSystem.get.log.info("LocalBlockStorageService initialized with jimfs in-memory storage")
      ctx.withStorageService(this)
    } match {
      case Success(context) => context
      case Failure(ex) => throw new IllegalStateException("Could not initialize LocalBlockStorageService", ex)
    }
  }

  /**
   * Verifies that all service components are properly initialized.
   *
   * Performs final validation that StorageService, JimfsManager, TopicDirectiveMapper, and
   * configuration are all present before the service is considered ready for use.
   *
   * @param ctx the builder context to validate
   * @param ec execution context for async operations
   * @return Future containing the validated BuilderContext
   * @throws IllegalArgumentException if any required component is missing
   */
  override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.storageService.isDefined, "StorageService not initialized in BuilderContext")
    require(jimfsManager.isDefined, "JimfsManager not initialized")
    require(topicDirectiveMapper.isDefined, "TopicDirectiveMapper not initialized")
    require(config.isDefined, "BlockStorageConfig not initialized")
    ctx
  }

  /**
   * Fetches test artifacts from local filesystem into JIMFS staging area.
   *
   * Copies the contents of the specified local directory (bucket) into an in-memory JIMFS
   * test directory. Validates the presence of required directories (features) and files
   * (topic directive YAML), then parses the configuration.
   *
   * @param testId unique identifier for this test execution
   * @param bucket local filesystem path (must be absolute) pointing to test artifacts
   * @param ec execution context for async operations
   * @return Future containing BlockStorageDirective with paths to features, evidence, and topic configuration
   * @throws BucketUriParseException if bucket path is invalid (empty, relative, non-existent, or not a directory)
   * @throws MissingFeaturesDirectoryException if features directory is not found
   * @throws EmptyFeaturesDirectoryException if features directory contains no files
   * @throws MissingTopicDirectiveFileException if topic directive file is not found
   */
  override def fetchFromBlockStorage(testId: UUID, bucket: String)(implicit ec: ExecutionContext): Future[BlockStorageDirective] = Future {
    val sourcePath: Path = parseLocalPath(bucket)
    val testDir: Path = jimfsManager.get.createTestDirectory(testId)

    Try {
      copyDirectoryContents(sourcePath, testDir)
      validateAndParse(testDir, bucket)
    } match {
      case Success(directive) => directive
      case Failure(ex) =>
        jimfsManager.get.deleteTestDirectory(testId)
        throw ex
    }
  }

  /**
   * Cleans up JIMFS test directory after test execution.
   *
   * For local storage, evidence is not persisted to the filesystem - it exists only in JIMFS
   * during test execution. This method simply cleans up the in-memory test directory.
   *
   * @param testId unique identifier for the test execution
   * @param bucket bucket path (unused for local storage)
   * @param evidence path to evidence directory (unused for local storage)
   * @param ec execution context for async operations
   * @return Future that completes when cleanup is done
   */
  override def loadToBlockStorage(testId: UUID, bucket: String, evidence: String)(implicit ec: ExecutionContext): Future[Unit] = Future {
    jimfsManager.get.deleteTestDirectory(testId)
  }

  /**
   * Parses and validates a local filesystem path.
   *
   * @param path the local filesystem path to validate
   * @return Path object representing the validated path
   * @throws BucketUriParseException if path is invalid
   */
  def parseLocalPath(path: String): Path = Try {
    if path.isEmpty then
      throw new BucketUriParseException(s"Local path cannot be empty")

    val localPath: Path = Path.of(path)

    if !localPath.isAbsolute then
      throw new BucketUriParseException(s"Local path must be absolute: $path")

    if !Files.exists(localPath) then
      throw new BucketUriParseException(s"Local path does not exist: $path")

    if !Files.isDirectory(localPath) then
      throw new BucketUriParseException(s"Local path must be a directory: $path")

    localPath
  } match {
    case Success(result) => result
    case Failure(ex: BucketUriParseException) => throw ex
    case Failure(ex) => throw new BucketUriParseException(s"Failed to parse local path '$path': ${ex.getMessage}", ex)
  }

  /** Recursively copies directory contents from source to target. */
  def copyDirectoryContents(source: Path, target: Path): Unit = {
    Files.walk(source).forEach { sourcePath =>
      val relativePath: String = source.relativize(sourcePath).toString
      if relativePath.nonEmpty then
        val targetPath: Path = target.resolve(relativePath)
        if Files.isDirectory(sourcePath) then
          Files.createDirectories(targetPath)
        else
          Files.createDirectories(targetPath.getParent)
          Files.copy(sourcePath, targetPath)
    }
  }

  /**
   * Validates test directory structure and parses topic directive configuration.
   *
   * @param testDir the JIMFS test directory to validate
   * @param bucket the original bucket path (for error messages)
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
