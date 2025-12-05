package io.distia.probe
package services
package builder
package modules

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.google.common.jimfs.{Configuration, Jimfs}
import org.slf4j.Logger

import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.builder.modules.ProbeVaultService
import io.distia.probe.services.config.VaultConfig

import java.nio.file.{FileSystem, Files}

/**
 * LocalVaultService - In-Memory Local Implementation of ProbeVaultService
 *
 * This service provides a lightweight, in-memory vault implementation using Google's Jimfs
 * (Java In-Memory File System). It is designed for local development, testing, and scenarios
 * where no external vault infrastructure is available or required.
 *
 * Configuration: probe.vault.provider = "local"
 *
 * Lifecycle:
 * - preFlight: Validates that provider is set to "local"
 * - initialize: Creates Jimfs in-memory filesystem and vault directory structure
 * - finalCheck: Verifies logger and jimfs vault filesystem are initialized
 *
 * Security Note:
 * - Returns PLAINTEXT security protocol (no encryption)
 * - Suitable ONLY for local development and testing
 * - Never use in production environments with sensitive data
 * - No credentials are stored or fetched from external sources
 *
 * Thread Safety: Uses @volatile variables for safe publication across threads
 *
 * @see ProbeVaultService for the trait this implements
 * @see com.google.common.jimfs.Jimfs for in-memory filesystem implementation
 */
class LocalVaultService extends ProbeVaultService {

  @volatile private var logger: Option[Logger] = None
  @volatile private var jimfsVault: Option[FileSystem] = None

  /**
   * Pre-flight validation for LocalVaultService initialization
   *
   * Validates that the BuilderContext contains the required configuration and that
   * the provider is correctly set to "local".
   *
   * @param ctx BuilderContext containing configuration
   * @param ec ExecutionContext for Future execution
   * @return Future[BuilderContext] containing validated context
   * @throws IllegalArgumentException if config is missing or provider is not "local"
   */
  override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config not found in BuilderContext")
    Try {
      val vaultConfig = VaultConfig.fromConfig(ctx.config.get)
      require(
        vaultConfig.provider.nonEmpty && vaultConfig.provider.equals("local"),
        "Provider must be 'local' for LocalVaultService"
      )
      ctx
    } match {
      case Success(context) => context
      case Failure(ex) => throw ex
    }
  }

  /**
   * Initialize LocalVaultService with in-memory Jimfs filesystem
   *
   * Creates a Unix-style in-memory filesystem using Jimfs and sets up the vault
   * directory structure. Registers the logger from the ActorSystem and updates
   * the BuilderContext with this service instance.
   *
   * @param ctx BuilderContext containing configuration and ActorSystem
   * @param ec ExecutionContext for Future execution
   * @return Future[BuilderContext] with vault service registered
   * @throws IllegalStateException if initialization fails
   */
  override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config must be initialized before LocalVaultService")
    require(ctx.actorSystem.isDefined, "ActorSystem not initialized")
    ctx.actorSystem.get.log.info("Initializing LocalVaultService")
    Try {
      logger = Some(ctx.actorSystem.get.log)
      jimfsVault = Some(Jimfs.newFileSystem(Configuration.unix()))
      loadVaultData()
      ctx.actorSystem.get.log.info("LocalVaultService initialized with jimfs in-memory vault")
      ctx.withVaultService(this)
    } match {
      case Success(context) => context
      case Failure(ex) => throw new IllegalStateException("Could not initialize LocalVaultService", ex)
    }
  }

  /**
   * Final validation that LocalVaultService is fully initialized
   *
   * Verifies that all required components (vault service, logger, jimfs filesystem)
   * have been properly initialized and are ready for operation.
   *
   * @param ctx BuilderContext to validate
   * @param ec ExecutionContext for Future execution
   * @return Future[BuilderContext] if validation succeeds
   * @throws IllegalArgumentException if any required component is not initialized
   */
  override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.vaultService.isDefined, "VaultService not initialized in BuilderContext")
    require(logger.isDefined, "Logger not initialized")
    require(jimfsVault.isDefined, "jimfs vault filesystem not created")
    ctx
  }

  /**
   * Fetch security directives for Kafka topics (local mode - PLAINTEXT)
   *
   * Generates KafkaSecurityDirective instances for all topics in the block storage
   * directive. Since this is a local development service, all topics are configured
   * with PLAINTEXT security protocol and empty JAAS configuration.
   *
   * @param directive BlockStorageDirective containing topic directives
   * @param ec ExecutionContext for Future execution
   * @return Future[List[KafkaSecurityDirective]] with PLAINTEXT security directives
   */
  override def fetchSecurityDirectives(
    directive: BlockStorageDirective
  )(implicit ec: ExecutionContext): Future[List[KafkaSecurityDirective]] =
    Future.successful {
      directive.topicDirectives.map { topicDirective =>
        KafkaSecurityDirective(
          topic = topicDirective.topic,
          role = topicDirective.role,
          securityProtocol = SecurityProtocol.PLAINTEXT,
          jaasConfig = ""
        )
      }
    }

  /** Initialize the in-memory vault directory structure */
  private[modules] def loadVaultData(): Unit = {
    val vaultDir = jimfsVault.get.getPath("/vault")
    Files.createDirectories(vaultDir)
  }

  /**
   * Shutdown the LocalVaultService and release resources
   *
   * Closes the Jimfs in-memory filesystem if it was created. This operation
   * is safe to call multiple times.
   *
   * @param ec ExecutionContext for Future execution
   * @return Future[Unit] when shutdown completes
   */
  override def shutdown()(implicit ec: ExecutionContext): Future[Unit] =
    jimfsVault match {
      case Some(fs) => Future { fs.close() }
      case None => Future.successful(())
    }
}
