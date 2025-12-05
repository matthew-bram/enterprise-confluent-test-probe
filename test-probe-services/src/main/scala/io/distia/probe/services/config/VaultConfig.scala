package io.distia.probe
package services
package config

import com.typesafe.config.Config
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._
import scala.jdk.CollectionConverters._

/**
 * LocalVaultConfig - Configuration for local vault simulation
 *
 * Parsed from test-probe.services.vault.local in application.conf
 *
 * @param requiredFields List of required metadata fields for local vault
 */
private[services] case class LocalVaultConfig(
  requiredFields: List[String]
)

/**
 * VaultOAuthConfig - OAuth2 configuration for vault authentication
 *
 * Parsed from test-probe.services.vault.oauth in application.conf
 *
 * @param tokenEndpoint OAuth2 token endpoint URL
 * @param scope OAuth2 scope for vault access
 */
private[services] case class VaultOAuthConfig(
  tokenEndpoint: String,
  scope: String
)

/**
 * AwsVaultConfig - Configuration for AWS Lambda vault invocation
 *
 * Parsed from test-probe.services.vault.aws in application.conf
 *
 * @param lambdaArn AWS Lambda function ARN for vault access
 * @param region AWS region (e.g., "us-east-1")
 * @param timeout Operation timeout for Lambda invocation
 * @param retryAttempts Number of retry attempts for failed operations
 * @param requiredFields List of required metadata fields for AWS vault
 */
private[services] case class AwsVaultConfig(
  lambdaArn: String,
  region: String,
  timeout: FiniteDuration,
  retryAttempts: Int,
  requiredFields: List[String]
)

/**
 * AzureVaultConfig - Configuration for Azure Function vault invocation
 *
 * Parsed from test-probe.services.vault.azure in application.conf
 *
 * @param functionUrl Azure Function HTTP endpoint URL
 * @param functionKey Azure Function access key
 * @param timeout Operation timeout for Function invocation
 * @param retryAttempts Number of retry attempts for failed operations
 * @param requiredFields List of required metadata fields for Azure vault
 */
private[services] case class AzureVaultConfig(
  functionUrl: String,
  functionKey: String,
  timeout: FiniteDuration,
  retryAttempts: Int,
  requiredFields: List[String]
)

/**
 * GcpVaultConfig - Configuration for GCP Cloud Function vault invocation
 *
 * Parsed from test-probe.services.vault.gcp in application.conf
 *
 * @param functionUrl GCP Cloud Function HTTP endpoint URL
 * @param serviceAccountKey Optional path to service account JSON key file
 * @param timeout Operation timeout for Function invocation
 * @param retryAttempts Number of retry attempts for failed operations
 * @param requiredFields List of required metadata fields for GCP vault
 */
private[services] case class GcpVaultConfig(
  functionUrl: String,
  serviceAccountKey: Option[String],
  timeout: FiniteDuration,
  retryAttempts: Int,
  requiredFields: List[String]
)

/**
 * VaultConfig - Multi-cloud vault configuration
 *
 * Parsed from test-probe.services.vault in application.conf
 *
 * Supports four vault providers: local (simulation), aws (Lambda), azure (Function), gcp (Cloud Function)
 * The active provider is determined by the `provider` field.
 *
 * Validation:
 * - Provider must be one of: local, aws, azure, gcp
 * - Retry attempts must be positive for all cloud providers
 * - Provider-specific fields validated based on active provider
 *
 * @param provider Active vault provider (local, aws, azure, gcp)
 * @param local Local vault simulation configuration
 * @param aws AWS Lambda vault configuration
 * @param azure Azure Function vault configuration
 * @param gcp GCP Cloud Function vault configuration
 * @param oauth OAuth2 authentication configuration
 * @param rosettaMappingPath Optional path to Rosetta mapping YAML file
 */
private[services] case class VaultConfig(
  provider: String,
  local: LocalVaultConfig,
  aws: AwsVaultConfig,
  azure: AzureVaultConfig,
  gcp: GcpVaultConfig,
  oauth: VaultOAuthConfig,
  rosettaMappingPath: Option[String]
) {
  require(
    Set("local", "aws", "azure", "gcp").contains(provider),
    s"Invalid vault provider: $provider. Must be one of: local, aws, azure, gcp"
  )

  require(aws.retryAttempts > 0, s"AWS retry attempts must be positive, got: ${aws.retryAttempts}")
  require(azure.retryAttempts > 0, s"Azure retry attempts must be positive, got: ${azure.retryAttempts}")
  require(gcp.retryAttempts > 0, s"GCP retry attempts must be positive, got: ${gcp.retryAttempts}")

  provider match {
    case "aws" =>
      require(aws.lambdaArn.nonEmpty, "AWS Lambda ARN cannot be empty when provider is 'aws'")
      require(aws.region.nonEmpty, "AWS region cannot be empty when provider is 'aws'")
    case "azure" =>
      require(azure.functionUrl.nonEmpty, "Azure function URL cannot be empty when provider is 'azure'")
      require(azure.functionKey.nonEmpty, "Azure function key cannot be empty when provider is 'azure'")
    case "gcp" =>
      require(gcp.functionUrl.nonEmpty, "GCP function URL cannot be empty when provider is 'gcp'")
    case "local" =>
  }
}

private[services] object VaultConfig {

  /**
   * Parse VaultConfig from Typesafe Config
   *
   * @param config Application configuration containing test-probe.services.vault
   * @return Parsed and validated VaultConfig
   * @throws com.typesafe.config.ConfigException if required paths are missing
   * @throws IllegalArgumentException if validation requirements fail
   */
  def fromConfig(config: Config): VaultConfig = {
    val vault = config.getConfig("test-probe.services.vault")

    VaultConfig(
      provider = vault.getString("provider"),

      local = LocalVaultConfig(
        requiredFields = vault.getStringList("local.required-fields").asScala.toList
      ),

      aws = AwsVaultConfig(
        lambdaArn = vault.getString("aws.lambda-arn"),
        region = vault.getString("aws.region"),
        timeout = vault.getDuration("aws.timeout").toScala,
        retryAttempts = vault.getInt("aws.retry-attempts"),
        requiredFields = vault.getStringList("aws.required-fields").asScala.toList
      ),

      azure = AzureVaultConfig(
        functionUrl = vault.getString("azure.function-url"),
        functionKey = vault.getString("azure.function-key"),
        timeout = vault.getDuration("azure.timeout").toScala,
        retryAttempts = vault.getInt("azure.retry-attempts"),
        requiredFields = vault.getStringList("azure.required-fields").asScala.toList
      ),

      gcp = GcpVaultConfig(
        functionUrl = vault.getString("gcp.function-url"),
        serviceAccountKey = if vault.hasPath("gcp.service-account-key") then
          Some(vault.getString("gcp.service-account-key"))
        else
          None,
        timeout = vault.getDuration("gcp.timeout").toScala,
        retryAttempts = vault.getInt("gcp.retry-attempts"),
        requiredFields = vault.getStringList("gcp.required-fields").asScala.toList
      ),

      oauth = VaultOAuthConfig(
        tokenEndpoint = vault.getString("oauth.token-endpoint"),
        scope = vault.getString("oauth.scope")
      ),

      rosettaMappingPath = if vault.hasPath("rosetta-mapping-path") 
        && vault.getString("rosetta-mapping-path").nonEmpty then
        Some(vault.getString("rosetta-mapping-path"))
      else
        None
    )
  }
}
