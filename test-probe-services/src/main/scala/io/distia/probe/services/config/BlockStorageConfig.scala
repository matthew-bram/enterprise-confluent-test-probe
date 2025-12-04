package io.distia.probe
package services
package config

import com.typesafe.config.Config
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._

/**
 * LocalBlockStorageConfig - Configuration for local (JIMFS) block storage
 *
 * Parsed from test-probe.services.block-storage.local in application.conf
 */
private[services] case class LocalBlockStorageConfig()

/**
 * AwsBlockStorageConfig - Configuration for AWS S3 block storage
 *
 * Parsed from test-probe.services.block-storage.aws in application.conf
 *
 * @param region AWS region (e.g., "us-east-1")
 * @param timeout Operation timeout for S3 client
 * @param retryAttempts Number of retry attempts for failed operations
 */
private[services] case class AwsBlockStorageConfig(
  region: String,
  timeout: FiniteDuration,
  retryAttempts: Int
)

/**
 * AzureBlockStorageConfig - Configuration for Azure Blob Storage
 *
 * Parsed from test-probe.services.block-storage.azure in application.conf
 *
 * @param storageAccountName Azure storage account name
 * @param storageAccountKey Azure storage account access key
 * @param timeout Operation timeout for Blob client
 * @param retryAttempts Number of retry attempts for failed operations
 */
private[services] case class AzureBlockStorageConfig(
  storageAccountName: String,
  storageAccountKey: String,
  timeout: FiniteDuration,
  retryAttempts: Int
)

/**
 * GcpBlockStorageConfig - Configuration for GCP Cloud Storage
 *
 * Parsed from test-probe.services.block-storage.gcp in application.conf
 *
 * @param projectId GCP project ID
 * @param serviceAccountKey Optional path to service account JSON key file
 * @param timeout Operation timeout for Storage client
 * @param retryAttempts Number of retry attempts for failed operations
 */
private[services] case class GcpBlockStorageConfig(
  projectId: String,
  serviceAccountKey: Option[String],
  timeout: FiniteDuration,
  retryAttempts: Int
)

/**
 * BlockStorageConfig - Multi-cloud block storage configuration
 *
 * Parsed from test-probe.services.block-storage in application.conf
 *
 * Supports four storage providers: local (JIMFS), aws (S3), azure (Blob), gcp (Cloud Storage)
 * The active provider is determined by the `provider` field.
 *
 * Validation:
 * - Provider must be one of: local, aws, azure, gcp
 * - Topic directive file name cannot be empty
 * - Retry attempts must be positive for all cloud providers
 * - Provider-specific fields validated based on active provider
 *
 * @param provider Active storage provider (local, aws, azure, gcp)
 * @param topicDirectiveFileName Name of the topic directive YAML file
 * @param local Local JIMFS configuration
 * @param aws AWS S3 configuration
 * @param azure Azure Blob configuration
 * @param gcp GCP Cloud Storage configuration
 */
private[services] case class BlockStorageConfig(
  provider: String,
  topicDirectiveFileName: String,
  local: LocalBlockStorageConfig,
  aws: AwsBlockStorageConfig,
  azure: AzureBlockStorageConfig,
  gcp: GcpBlockStorageConfig
) {
  require(
    Set("local", "aws", "azure", "gcp").contains(provider),
    s"Invalid block storage provider: $provider. Must be one of: local, aws, azure, gcp"
  )

  require(
    topicDirectiveFileName.nonEmpty,
    "Topic directive file name cannot be empty"
  )

  require(aws.retryAttempts > 0, s"AWS retry attempts must be positive, got: ${aws.retryAttempts}")
  require(azure.retryAttempts > 0, s"Azure retry attempts must be positive, got: ${azure.retryAttempts}")
  require(gcp.retryAttempts > 0, s"GCP retry attempts must be positive, got: ${gcp.retryAttempts}")

  provider match {
    case "aws" =>
      require(aws.region.nonEmpty, "AWS region cannot be empty when provider is 'aws'")
    case "azure" =>
      require(azure.storageAccountName.nonEmpty, "Azure storage account name cannot be empty when provider is 'azure'")
      require(azure.storageAccountKey.nonEmpty, "Azure storage account key cannot be empty when provider is 'azure'")
    case "gcp" =>
      require(gcp.projectId.nonEmpty, "GCP project ID cannot be empty when provider is 'gcp'")
    case "local" =>
  }
}

private[services] object BlockStorageConfig {

  /**
   * Parse BlockStorageConfig from Typesafe Config
   *
   * @param config Application configuration containing test-probe.services.block-storage
   * @return Parsed and validated BlockStorageConfig
   * @throws com.typesafe.config.ConfigException if required paths are missing
   * @throws IllegalArgumentException if validation requirements fail
   */
  def fromConfig(config: Config): BlockStorageConfig = {
    val blockStorage: Config = config.getConfig("test-probe.services.block-storage")

    BlockStorageConfig(
      provider = blockStorage.getString("provider"),
      topicDirectiveFileName = blockStorage.getString("topic-directive-file-name"),

      local = LocalBlockStorageConfig(),

      aws = AwsBlockStorageConfig(
        region = blockStorage.getString("aws.region"),
        timeout = blockStorage.getDuration("aws.timeout").toScala,
        retryAttempts = blockStorage.getInt("aws.retry-attempts")
      ),

      azure = AzureBlockStorageConfig(
        storageAccountName = blockStorage.getString("azure.storage-account-name"),
        storageAccountKey = blockStorage.getString("azure.storage-account-key"),
        timeout = blockStorage.getDuration("azure.timeout").toScala,
        retryAttempts = blockStorage.getInt("azure.retry-attempts")
      ),

      gcp = GcpBlockStorageConfig(
        projectId = blockStorage.getString("gcp.project-id"),
        serviceAccountKey = if blockStorage.hasPath("gcp.service-account-key")
          && blockStorage.getString("gcp.service-account-key").nonEmpty then
          Some(blockStorage.getString("gcp.service-account-key"))
        else
          None,
        timeout = blockStorage.getDuration("gcp.timeout").toScala,
        retryAttempts = blockStorage.getInt("gcp.retry-attempts")
      )
    )
  }
}
