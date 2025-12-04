package io.distia.probe
package services
package builder
package modules

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

import com.typesafe.config.Config
import io.circe.*
import io.circe.parser.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.adapter.*
import org.apache.pekko.pattern.after
import org.slf4j.Logger

import io.distia.probe.common.exceptions.*
import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import io.distia.probe.common.rosetta.RosettaConfig
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.builder.modules.ProbeVaultService
import io.distia.probe.services.config.VaultConfig
import io.distia.probe.services.factories.{DefaultHttpClientFactory, HttpClientFactory}
import io.distia.probe.services.httpclient.ServicesHttpClient
import io.distia.probe.services.vault.{JaasConfigBuilder, RequestBodyBuilder, VaultCredentialsMapper}

/**
 * AzureVaultService - Azure Functions Implementation of ProbeVaultService
 *
 * This service integrates with Azure Functions to fetch Kafka credentials from
 * Azure-based vault systems. It uses HTTP client for REST API invocation and supports
 * OAuth/OIDC authentication via SASL_SSL.
 *
 * Configuration: probe.vault.provider = "azure"
 *
 * Lifecycle:
 * - preFlight: Validates Azure Function URL and function key configuration
 * - initialize: Creates HTTP client, loads Rosetta mapping configuration
 * - finalCheck: Verifies all Azure-specific components are initialized
 *
 * Features:
 * - Automatic retry with exponential backoff for transient failures
 * - Rate limit handling (HTTP 429)
 * - Service unavailability handling (HTTP 503)
 * - Timeout retry logic with configurable attempts
 * - Function key authentication via x-functions-key header
 * - Rosetta mapping for flexible credential field extraction
 *
 * Security Note:
 * - Credentials are fetched from Azure Function HTTP responses
 * - Uses SASL_SSL security protocol with OAuth Bearer authentication
 * - Function keys are passed via x-functions-key HTTP header
 * - Client secrets are passed through JaasConfigBuilder with proper escaping
 * - Never log raw credentials or function keys - use SecretRedactor for logging
 *
 * Thread Safety: Uses @volatile variables for safe publication across threads
 *
 * @see ProbeVaultService for the trait this implements
 * @see ServicesHttpClient for HTTP client integration
 * @see JaasConfigBuilder for JAAS configuration generation
 * @see VaultCredentialsMapper for response parsing
 */
private[services] object AzureVaultService {
  /**
   * Factory method to create AzureVaultService with optional HTTP client factory
   *
   * @param httpClientFactory Optional custom HTTP client factory for testing
   * @return AzureVaultService instance
   */
  def apply(httpClientFactory: Option[HttpClientFactory] = None): AzureVaultService = {
    httpClientFactory match {
      case None => new AzureVaultService(new DefaultHttpClientFactory())
      case Some(factory) => new AzureVaultService(factory)
    }
  }
}

private[services] class AzureVaultService(val httpClientFactory: HttpClientFactory) extends ProbeVaultService {

  @volatile private var logger: Option[Logger] = None
  @volatile private var actorSystem: Option[ActorSystem] = None
  @volatile private var httpClient: Option[ServicesHttpClient] = None
  @volatile private var rosettaConfig: Option[RosettaConfig.RosettaConfig] = None
  @volatile private var appConfig: Option[Config] = None
  @volatile private var tokenEndpoint: Option[String] = None
  @volatile private var scope: Option[String] = None
  @volatile private var functionUrl: Option[String] = None
  @volatile private var functionKey: Option[String] = None
  @volatile private var retryAttempts: Option[Int] = None
  @volatile private var initialBackoff: Option[FiniteDuration] = None
  @volatile private var requestTimeout: Option[FiniteDuration] = None
  @volatile private var requiredFields: Option[List[String]] = None

  /**
   * Pre-flight validation for AzureVaultService initialization
   *
   * Validates that the BuilderContext contains the required configuration and that
   * Azure-specific settings (Function URL and function key) are properly configured.
   *
   * @param ctx BuilderContext containing configuration
   * @param ec ExecutionContext for Future execution
   * @return Future[BuilderContext] containing validated context
   * @throws IllegalArgumentException if config is missing, provider is not "azure",
   *                                  or function key is empty
   */
  override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config not found in BuilderContext")
    Try {
      val vaultConfig = VaultConfig.fromConfig(ctx.config.get)
      require(
        vaultConfig.provider.nonEmpty && vaultConfig.provider.equals("azure"),
        "Azure function URL cannot be empty when provider is 'azure'"
      )
      require(vaultConfig.azure.functionKey.nonEmpty, "Azure function key cannot be empty when provider is 'azure'")
      ctx
    } match {
      case Success(context) => context
      case Failure(ex) => throw ex
    }
  }

  /**
   * Initialize AzureVaultService with HTTP client and configuration
   *
   * Creates the HTTP client for Azure Function invocation, loads the Rosetta
   * mapping configuration for credential field extraction, and registers all
   * Azure-specific settings (Function URL, key, retry policy, timeouts, OAuth endpoints).
   *
   * @param ctx BuilderContext containing configuration and ActorSystem
   * @param ec ExecutionContext for Future execution
   * @return Future[BuilderContext] with vault service registered
   * @throws IllegalStateException if initialization fails
   * @throws IllegalArgumentException if Rosetta mapping path is not defined or fails to load
   */
  override def initialize(ctx: BuilderContext)
    (implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config must be initialized before AzureVaultService")
    require(ctx.actorSystem.isDefined, "ActorSystem not initialized")
    ctx.actorSystem.get.log.info("Initializing AzureVaultService")
    Try {
      val vaultConfig: VaultConfig = VaultConfig.fromConfig(ctx.config.get)
      actorSystem = Some(ctx.actorSystem.get.toClassic)
      logger = Some(ctx.actorSystem.get.log)
      httpClient = Some(httpClientFactory.createClient()(ctx.actorSystem.get.toClassic))
      appConfig = Some(ctx.config.get)
      functionUrl = Some(vaultConfig.azure.functionUrl)
      functionKey = Some(vaultConfig.azure.functionKey)
      retryAttempts = Some(vaultConfig.azure.retryAttempts)
      initialBackoff = Some(1.second)
      requestTimeout = Some(vaultConfig.azure.timeout)
      requiredFields = Some(vaultConfig.azure.requiredFields)
      tokenEndpoint = Some(vaultConfig.oauth.tokenEndpoint)
      scope = Some(vaultConfig.oauth.scope)
      rosettaConfig = vaultConfig.rosettaMappingPath match {
        case Some(path) => RosettaConfig.load(path) match {
          case Right(config) => Some(config)
          case Left(ex: Throwable) => throw new IllegalStateException(s"Failed to load Rosetta mapping from $path", ex)
        }
        case None => throw new IllegalArgumentException("Rosetta mapping path must be defined")
      }
      ctx.actorSystem.get.log.info(s"AzureVaultService initialized with functionUrl=${functionUrl.get}")
      ctx.withVaultService(this)
    } match {
      case Success(context) => context
      case Failure(ex) => throw new IllegalStateException("Could not initialize AzureVaultService", ex)
    }
  }

  /**
   * Final validation that AzureVaultService is fully initialized
   *
   * Verifies that all required Azure-specific components (HTTP client, Function URL,
   * function key, retry settings, OAuth configuration, and Rosetta mapping) have been
   * properly initialized and are ready for operation.
   *
   * @param ctx BuilderContext to validate
   * @param ec ExecutionContext for Future execution
   * @return Future[BuilderContext] if validation succeeds
   * @throws IllegalArgumentException if any required component is not initialized
   */
  override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.vaultService.isDefined, "VaultService not initialized in BuilderContext")
    require(logger.isDefined, "Logger not initialized")
    require(actorSystem.isDefined, "ActorSystem not initialized")
    require(httpClient.isDefined, "HTTP client not initialized")
    require(appConfig.isDefined, "App Config not initialized")
    require(functionUrl.isDefined, "Function URL not initialized")
    require(functionKey.isDefined, "Function Key not initialized")
    require(retryAttempts.isDefined, "Retry Attempts not initialized")
    require(initialBackoff.isDefined, "Initial Backoff not initialized")
    require(requestTimeout.isDefined, "Request Timeout not initialized")
    require(tokenEndpoint.isDefined, "Token Endpoint not initialized")
    require(scope.isDefined, "Scope not initialized")
    require(rosettaConfig.isDefined, "Rosetta Config not initialized")
    require(requiredFields.isDefined, "Required Fields not initialized")
    ctx
  }

  /**
   * Fetch security directives for all topics from Azure vault
   *
   * Retrieves Kafka credentials for each topic by invoking the Azure Function
   * with automatic retry logic. Each topic is processed independently, and all
   * requests are executed in parallel.
   *
   * @param directive BlockStorageDirective containing topic directives
   * @param ec ExecutionContext for Future execution
   * @return Future[List[KafkaSecurityDirective]] with SASL_SSL security directives
   */
  override def fetchSecurityDirectives(
    directive: BlockStorageDirective
  )(implicit ec: ExecutionContext): Future[List[KafkaSecurityDirective]] = Future
    .traverse(directive.topicDirectives) { topicDirective => fetchWithRetry(topicDirective, retryAttempts.get) }

  /**
   * Invoke Azure Function to fetch vault credentials for a topic
   *
   * Builds a request payload using RequestBodyBuilder and Rosetta mapping, then
   * invokes the Azure Function via HTTP POST. Maps HTTP status codes to appropriate
   * exceptions for proper error handling.
   *
   * @param topicDirective TopicDirective specifying the topic and access requirements
   * @param ec ExecutionContext for Future execution
   * @return Future[String] containing the Azure Function's JSON response body
   * @throws VaultAuthException if authentication fails (401/403)
   * @throws VaultNotFoundException if Azure Function not found (404)
   * @throws VaultRateLimitException if rate limit exceeded (429)
   * @throws VaultServiceUnavailableException if Azure service unavailable (503)
   * @throws VaultException for other non-successful status codes
   */
  def invokeVault(topicDirective: TopicDirective)(implicit ec: ExecutionContext): Future[String] = RequestBodyBuilder
    .build(topicDirective, rosettaConfig.get, appConfig.get) match {
      case Left(error) => Future.failed(error)
      case Right(payload) =>
        httpClient.get.post(
          uri = functionUrl.get,
          jsonPayload = payload,
          headers = Map(
            "Content-Type" -> "application/json",
            "x-functions-key" -> functionKey.get
          )
        ).flatMap {
          case (200 | 201, Some(body)) => Future.successful(body)
          case (401 | 403, _) => Future.failed(new VaultAuthException(s"Azure vault authentication failed for topic ${topicDirective.topic}"))
          case (404, _) => Future.failed(new VaultNotFoundException(s"Azure function not found at ${functionUrl.get}"))
          case (429, body) => Future.failed(new VaultRateLimitException(s"Azure vault rate limit exceeded: ${body.getOrElse("no details")}"))
          case (503, body) => Future.failed(new VaultServiceUnavailableException(s"Azure vault unavailable: ${body.getOrElse("no details")}"))
          case (statusCode, body) => Future.failed(new VaultException(s"Azure vault returned HTTP $statusCode: ${body.getOrElse("no response body")}"))
        }
    }

  /**
   * Fetch Kafka security directive with automatic retry on transient failures
   *
   * Invokes the Azure vault, parses the response, maps credentials using Rosetta configuration,
   * and builds a JAAS configuration. Implements exponential backoff retry logic for
   * rate limits (429), service unavailability (503), and timeouts.
   *
   * Retry Strategy:
   * - Rate limit exceeded: Retry with exponential backoff
   * - Service unavailable: Retry with exponential backoff
   * - Timeout: Retry with exponential backoff
   * - Authentication/mapping errors: Fail immediately (no retry)
   *
   * @param topicDirective TopicDirective specifying the topic and access requirements
   * @param retriesLeft Number of retry attempts remaining
   * @param ec ExecutionContext for Future execution
   * @return Future[KafkaSecurityDirective] with SASL_SSL configuration
   * @throws VaultAuthException if authentication fails (not retried)
   * @throws VaultNotFoundException if Azure Function not found (not retried)
   * @throws VaultMappingException if response parsing or mapping fails (not retried)
   * @throws VaultRateLimitException if rate limit exceeded after all retries
   * @throws VaultServiceUnavailableException if service unavailable after all retries
   * @throws VaultTimeoutException if timeout occurs after all retries
   */
  def fetchWithRetry(
    topicDirective: TopicDirective,
    retriesLeft: Int
  )(implicit ec: ExecutionContext): Future[KafkaSecurityDirective] = {
    invokeVault(topicDirective).flatMap { responseBody =>
      parse(responseBody) match {
        case Right(responseJson) =>
          VaultCredentialsMapper.mapToVaultCredentials(responseJson, rosettaConfig.get, requiredFields.get) match {
            case Right(credentials) =>
              val clientId: String = credentials.get("clientId")
                .getOrElse(topicDirective.clientPrincipal)
              val clientSecret: String = credentials("clientSecret")
              val jaasConfig: String = JaasConfigBuilder.build(
                clientId = clientId,
                clientSecret = clientSecret,
                tokenEndpoint = tokenEndpoint.get,
                scope = Some(scope.get)
              )
              Future.successful(KafkaSecurityDirective(
                topic = topicDirective.topic,
                role = topicDirective.role,
                securityProtocol = SecurityProtocol.SASL_SSL,
                jaasConfig = jaasConfig
              ))
            case Left(errors) =>
              val primaryError: Throwable = errors.head
              val allErrorMessages: String = errors.toList.map(_.getMessage).mkString(", ")
              Future.failed(new VaultMappingException(
                s"Failed to map Azure response for topic ${topicDirective.topic}: $allErrorMessages",
                primaryError
              ))
          }
        case Left(parseError) =>
          Future.failed(new VaultMappingException(s"Failed to parse Azure response for topic ${topicDirective.topic}: ${parseError.message}"))
      }
    }.recoverWith {
      case ex: VaultRateLimitException if retriesLeft > 0 =>
        val backoff: FiniteDuration = initialBackoff.get * (retryAttempts.get - retriesLeft + 1)
        logger.get.warn(s"Rate limit hit for topic ${topicDirective.topic}, retrying in $backoff (${retriesLeft} retries left)")
        after(backoff, actorSystem.get.scheduler) { fetchWithRetry(topicDirective, retriesLeft - 1) }
      case ex: VaultServiceUnavailableException if retriesLeft > 0 =>
        val backoff: FiniteDuration = initialBackoff.get * (retryAttempts.get - retriesLeft + 1)
        logger.get.warn(s"Vault unavailable for topic ${topicDirective.topic}, retrying in $backoff (${retriesLeft} retries left)")
        after(backoff, actorSystem.get.scheduler) { fetchWithRetry(topicDirective, retriesLeft - 1) }
      case ex: VaultTimeoutException if retriesLeft > 0 =>
        val backoff: FiniteDuration = initialBackoff.get * (retryAttempts.get - retriesLeft + 1)
        logger.get.warn(s"Timeout fetching credentials for topic ${topicDirective.topic}, retrying in $backoff (${retriesLeft} retries left)")
        after(backoff, actorSystem.get.scheduler) { fetchWithRetry(topicDirective, retriesLeft - 1) }
      case ex => Future.failed(ex)
    }
  }

  /**
   * Shutdown the AzureVaultService and release HTTP resources
   *
   * Closes the HTTP client connection pool, releasing any underlying HTTP
   * connections and resources.
   *
   * @param ec ExecutionContext for Future execution
   * @return Future[Unit] when shutdown completes
   */
  override def shutdown()(implicit ec: ExecutionContext): Future[Unit] = {
    httpClient.get.shutdown()
  }

}
