package io.distia.probe
package services
package builder
package modules

import com.azure.storage.blob.BlobServiceAsyncClient
import com.azure.storage.blob.models.{BlobStorageException, ParallelTransferOptions}
import io.distia.probe.common.exceptions.{BucketUriParseException, EmptyFeaturesDirectoryException, MissingFeaturesDirectoryException, MissingTopicDirectiveFileException, StreamingException}
import io.distia.probe.common.models.BlockStorageDirective
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.builder.modules.ProbeStorageService
import config.BlockStorageConfig
import factories.{BlobClientFactory, DefaultBlobClientFactory}
import storage.{JimfsManager, TopicDirectiveMapper}
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.scaladsl.{FileIO, Source}
import org.apache.pekko.util.ByteString
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem
import org.slf4j.Logger
import reactor.core.publisher.{Flux, Mono}

import java.nio.ByteBuffer
import java.nio.file.{Files, Path}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*
import scala.util.{Failure, Success, Try}

/**
 * Factory for creating AzureBlockStorageService instances.
 *
 * Provides a default factory implementation or accepts a custom BlobClientFactory for testing.
 */
private[services] object AzureBlockStorageService {
  def apply(blobClientFactory: Option[BlobClientFactory] = None): AzureBlockStorageService = blobClientFactory match {
    case None => new AzureBlockStorageService(new DefaultBlobClientFactory())
    case Some(factory) => new AzureBlockStorageService(factory)
  }
}

/**
 * AzureBlockStorageService - Azure Blob Storage implementation of ProbeStorageService
 *
 * Provides test evidence storage using Azure Blob Storage for production and cloud-based scenarios.
 * Uses Azure SDK with async BlobServiceAsyncClient and Project Reactor for high-performance streaming
 * operations. Integrates with Pekko Streams for seamless download/upload. Test artifacts are staged
 * in JIMFS during execution, then evidence is uploaded to Azure Blob Storage after test completion.
 *
 * Storage Backend: Azure Blob Storage
 *
 * Lifecycle:
 * - preFlight: Validates that provider is "azure" and storage account credentials are configured
 * - initialize: Creates BlobServiceAsyncClient for the configured storage account
 * - finalCheck: Verifies all components (BlobServiceAsyncClient, JimfsManager, etc.) are initialized
 *
 * Azure SDK Integration:
 * - Uses BlobServiceAsyncClient for async operations (non-blocking)
 * - Uses Project Reactor Flux/Mono for reactive streaming
 * - Integrates with Pekko Streams via Source.fromPublisher for download operations
 * - Supports parallel file transfers with ParallelTransferOptions
 *
 * Thread Safety: This service uses @volatile fields to ensure visibility across threads during initialization.
 * After initialization is complete, all fields are effectively immutable. The Azure SDK clients are thread-safe.
 *
 * @param blobClientFactory factory for creating Azure Blob clients (injectable for testing)
 * @see ProbeStorageService for the trait this implements
 * @see JimfsManager for in-memory filesystem management
 * @see TopicDirectiveMapper for YAML parsing
 * @see BlobServiceAsyncClient for Azure SDK async operations
 */
private[services] class AzureBlockStorageService(val blobClientFactory: BlobClientFactory) extends ProbeStorageService {

  @volatile private var logger: Option[Logger] = None
  @volatile private var blobServiceAsyncClient: Option[BlobServiceAsyncClient] = None
  @volatile private var jimfsManager: Option[JimfsManager] = None
  @volatile private var topicDirectiveMapper: Option[TopicDirectiveMapper] = None
  @volatile private var config: Option[BlockStorageConfig] = None
  @volatile private var actorSystem: Option[org.apache.pekko.actor.typed.ActorSystem[?]] = None

  /**
   * Validates Azure-specific configuration before service initialization.
   *
   * Ensures that the storage provider is set to "azure" and that Azure storage account name
   * and key are configured.
   *
   * @param ctx the builder context containing configuration
   * @param ec execution context for async operations
   * @return Future containing the validated BuilderContext
   * @throws IllegalArgumentException if config is missing, provider is not "azure", or credentials are empty
   */
  override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config not found in BuilderContext")
    Try {
      val blockStorageConfig: BlockStorageConfig = BlockStorageConfig.fromConfig(ctx.config.get)
      require(
        blockStorageConfig.provider.nonEmpty && blockStorageConfig.provider.equals("azure"),
        "Provider must be 'azure' for AzureBlockStorageService"
      )
      require(blockStorageConfig.azure.storageAccountName.nonEmpty, "Azure storage account name cannot be empty when provider is 'azure'")
      require(blockStorageConfig.azure.storageAccountKey.nonEmpty, "Azure storage account key cannot be empty when provider is 'azure'")
      ctx
    } match {
      case Success(context) => context
      case Failure(ex) => throw ex
    }
  }

  /**
   * Initializes the AzureBlockStorageService with Azure SDK clients.
   *
   * Creates BlobServiceAsyncClient using the configured storage account credentials.
   * Also initializes JimfsManager and TopicDirectiveMapper. Stores ActorSystem reference
   * for Pekko Streams integration.
   *
   * @param ctx the builder context containing ActorSystem and configuration
   * @param ec execution context for async operations
   * @return Future containing the updated BuilderContext with this service registered
   * @throws IllegalStateException if initialization fails or required components are missing
   */
  override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config must be initialized before AzureBlockStorageService")
    require(ctx.actorSystem.isDefined, "ActorSystem not initialized")
    ctx.actorSystem.get.log.info("Initializing AzureBlockStorageService with async BlobAsyncClient")
    Try {
      val blockStorageConfig: BlockStorageConfig = BlockStorageConfig.fromConfig(ctx.config.get)
      logger = Some(ctx.actorSystem.get.log)
      actorSystem = Some(ctx.actorSystem.get)
      config = Some(blockStorageConfig)
      jimfsManager = Some(new JimfsManager())
      topicDirectiveMapper = Some(new TopicDirectiveMapper())

      val connectionString: String = s"DefaultEndpointsProtocol=https;" +
        s"AccountName=${blockStorageConfig.azure.storageAccountName};" +
        s"AccountKey=${blockStorageConfig.azure.storageAccountKey};" +
        s"EndpointSuffix=core.windows.net"

      blobServiceAsyncClient = Some(blobClientFactory.createAsyncClient(connectionString))
      ctx.actorSystem.get.log.info(s"AzureBlockStorageService initialized with async client, account=${blockStorageConfig.azure.storageAccountName}")
      ctx.withStorageService(this)
    } match {
      case Success(context) => context
      case Failure(ex) => throw new IllegalStateException("Could not initialize AzureBlockStorageService", ex)
    }
  }

  /**
   * Verifies that all Azure service components are properly initialized.
   *
   * Performs final validation that BlobServiceAsyncClient, ActorSystem, JimfsManager,
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
    require(actorSystem.isDefined, "ActorSystem not initialized")
    require(blobServiceAsyncClient.isDefined, "BlobServiceAsyncClient not initialized")
    require(jimfsManager.isDefined, "JimfsManager not initialized")
    require(topicDirectiveMapper.isDefined, "TopicDirectiveMapper not initialized")
    require(config.isDefined, "BlockStorageConfig not initialized")
    ctx
  }

  /**
   * Fetches test artifacts from Azure Blob Storage into JIMFS staging area.
   *
   * Downloads all blobs from the specified Azure container and prefix using Project Reactor Flux
   * streaming. Integrates with Pekko Streams to write blob content to JIMFS files. Files are
   * staged in JIMFS, then validated and parsed.
   *
   * @param testId unique identifier for this test execution
   * @param bucket Azure URI in format "https://<account>.blob.core.windows.net/<container>/<prefix>"
   * @param ec execution context for async operations
   * @return Future containing BlockStorageDirective with paths to features, evidence, and topic configuration
   * @throws BucketUriParseException if Azure URI format is invalid
   * @throws StreamingException if Azure Blob download fails
   * @throws MissingFeaturesDirectoryException if features directory is not found
   * @throws EmptyFeaturesDirectoryException if features directory contains no files
   * @throws MissingTopicDirectiveFileException if topic directive file is not found
   */
  override def fetchFromBlockStorage(testId: UUID, bucket: String)(implicit ec: ExecutionContext): Future[BlockStorageDirective] = {
    val (containerName: String, prefix: String) = parseAzureUri(bucket)
    val testDir: Path = jimfsManager.get.createTestDirectory(testId)

    val containerAsyncClient = blobServiceAsyncClient.get.getBlobContainerAsyncClient(containerName)

    given ActorSystem[?] = actorSystem.get

    val promise: Promise[BlockStorageDirective] = Promise()

    containerAsyncClient.listBlobs()
      .filter(blobItem => blobItem.getName.startsWith(prefix))
      .flatMap { blobItem =>
        val blobName: String = blobItem.getName
        val relativePath: String = blobName.stripPrefix(prefix).stripPrefix("/")
        if relativePath.nonEmpty then
          val targetPath: Path = testDir.resolve(relativePath)
          Files.createDirectories(targetPath.getParent)

          val blobAsyncClient = containerAsyncClient.getBlobAsyncClient(blobName)
          val downloadFlux = blobAsyncClient.downloadStream()

          val pekkoSource: Source[ByteBuffer, NotUsed] = Source.fromPublisher(downloadFlux)
          val byteStringSource: Source[ByteString, NotUsed] = pekkoSource.map { bb =>
            val bytes = new Array[Byte](bb.remaining())
            bb.get(bytes)
            ByteString(bytes)
          }

          val writeFuture: Future[IOResult] = byteStringSource.runWith(FileIO.toPath(targetPath))
          Mono.fromFuture(writeFuture.asJava.toCompletableFuture).map(_ => blobName)
        else
          Mono.empty()
      }
      .collectList()
      .subscribe(_ => {
        Try(validateAndParse(testDir, bucket)) match {
          case Success(directive) => promise.success(directive)
          case Failure(ex) =>
            jimfsManager.get.deleteTestDirectory(testId)
            promise.failure(ex)
        }
      },
      error => {
        jimfsManager.get.deleteTestDirectory(testId)
        error match {
          case ex: BlobStorageException =>
            promise.failure(new StreamingException(s"Failed to fetch from Azure container '$containerName' with prefix '$prefix': ${ex.getMessage}", ex))
          case ex =>
            promise.failure(ex)
        }
      }
    )

    promise.future
  }

  /**
   * Uploads test evidence to Azure Blob Storage and cleans up JIMFS.
   *
   * Uses Project Reactor Flux to efficiently upload all evidence files to Azure Blob Storage
   * under the "evidence/" subdirectory. Supports parallel uploads with ParallelTransferOptions.
   * Always cleans up the JIMFS test directory after completion or failure.
   *
   * @param testId unique identifier for the test execution
   * @param bucket Azure URI in format "https://<account>.blob.core.windows.net/<container>/<prefix>"
   * @param evidence path to local evidence directory in JIMFS
   * @param ec execution context for async operations
   * @return Future that completes when upload and cleanup are done
   * @throws StreamingException if Azure Blob upload fails
   */
  override def loadToBlockStorage(testId: UUID, bucket: String, evidence: String)(implicit ec: ExecutionContext): Future[Unit] = {
    val (containerName: String, prefix: String) = parseAzureUri(bucket)
    val evidencePath: Path = Path.of(evidence)

    val containerAsyncClient = blobServiceAsyncClient.get.getBlobContainerAsyncClient(containerName)
    val parallelOptions = new ParallelTransferOptions()
      .setBlockSizeLong(4 * 1024 * 1024)
      .setMaxConcurrency(4)

    given ActorSystem[?] = actorSystem.get

    val promise: Promise[Unit] = Promise()

    Try {
      if Files.exists(evidencePath) && Files.isDirectory(evidencePath) then
        val files = Files.walk(evidencePath).iterator().asScala
          .filter(Files.isRegularFile(_))
          .toList

        if files.nonEmpty then
          val uploadFlux = Flux.fromIterable(files.asJava)
            .flatMap { file =>
              val relativePath: String = evidencePath.relativize(file).toString
              val blobName: String = s"$prefix/evidence/$relativePath"

              val blobAsyncClient = containerAsyncClient.getBlobAsyncClient(blobName)

              val fileBytes: Array[Byte] = Files.readAllBytes(file)
              val chunkSize = 8192
              val chunks: List[ByteBuffer] = fileBytes.grouped(chunkSize)
                .map(bytes => ByteBuffer.wrap(bytes))
                .toList

              val dataFlux: Flux[ByteBuffer] = Flux.fromIterable(chunks.asJava)
              blobAsyncClient.upload(dataFlux, parallelOptions, true)
            }

          uploadFlux.collectList().toFuture.asScala.onComplete {
            case Success(_) =>
              jimfsManager.get.deleteTestDirectory(testId)
              promise.success(())
            case Failure(error) =>
              jimfsManager.get.deleteTestDirectory(testId)
              error match {
                case ex: BlobStorageException =>
                  promise.failure(new StreamingException(s"Failed to upload evidence to Azure container '$containerName': ${ex.getMessage}", ex))
                case ex =>
                  promise.failure(ex)
              }
          }
        else
          jimfsManager.get.deleteTestDirectory(testId)
          promise.success(())
      else
        jimfsManager.get.deleteTestDirectory(testId)
        promise.success(())
    } match {
      case Success(_) => promise.future
      case Failure(ex) =>
        jimfsManager.get.deleteTestDirectory(testId)
        Future.failed(ex)
    }
  }

  /**
   * Parses and validates an Azure Blob Storage URI.
   *
   * Supports both Azure cloud format and Azurite local emulator format.
   *
   * @param uri the Azure URI to parse
   * @return tuple of (containerName, prefix)
   * @throws BucketUriParseException if URI format is invalid
   */
  def parseAzureUri(uri: String): (String, String) = Try {
    if !uri.startsWith("https://") && !uri.startsWith("http://") then
      throw new BucketUriParseException(s"Invalid Azure URI format: $uri (expected https://<account>.blob.core.windows.net/<container>/<prefix>)")

    val withoutProtocol: String = uri.replaceFirst("^https?://", "")
    val parts: Array[String] = withoutProtocol.split("/")

    if parts.length < 2 && !uri.endsWith("/") then
      throw new BucketUriParseException(s"Invalid Azure URI format: $uri (missing container name)")

    val isAzuriteFormat = parts(0).contains(":") && parts.length >= 3

    val (containerName: String, prefix: String) = if isAzuriteFormat then
      (if parts.length > 2 then parts(2) else "", if parts.length > 3 then parts.drop(3).mkString("/") else "")
    else
      (if parts.length > 1 then parts(1) else "", if parts.length > 2 then parts.drop(2).mkString("/") else "")

    if containerName.isEmpty then
      throw new BucketUriParseException(s"Container name cannot be empty in Azure URI: $uri")

    (containerName, prefix)
  } match {
    case Success(result) => result
    case Failure(ex: BucketUriParseException) => throw ex
    case Failure(ex) => throw new BucketUriParseException(s"Failed to parse Azure URI '$uri': ${ex.getMessage}", ex)
  }

  /**
   * Validates test directory structure and parses topic directive configuration.
   *
   * @param testDir the JIMFS test directory to validate
   * @param bucket the original Azure URI (for error messages)
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
