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

import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.lambda.LambdaClient
import software.amazon.awssdk.services.lambda.model.InvokeRequest

import io.distia.probe.common.exceptions.*
import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import io.distia.probe.common.rosetta.RosettaConfig
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.builder.modules.ProbeVaultService
import io.distia.probe.services.config.VaultConfig
import io.distia.probe.services.factories.{DefaultLambdaClientFactory, LambdaClientFactory}
import io.distia.probe.services.vault.{JaasConfigBuilder, RequestBodyBuilder, VaultCredentialsMapper}

/**
 * AwsVaultService - AWS Lambda Implementation of ProbeVaultService
 *
 * This service integrates with AWS Lambda functions to fetch Kafka credentials from
 * AWS-based vault systems. It uses the AWS SDK for Lambda invocation and supports
 * OAuth/OIDC authentication via SASL_SSL.
 *
 * Configuration: probe.vault.provider = "aws"
 *
 * Lifecycle:
 * - preFlight: Validates AWS Lambda ARN and region configuration
 * - initialize: Creates Lambda client, loads Rosetta mapping configuration
 * - finalCheck: Verifies all AWS-specific components are initialized
 *
 * Features:
 * - Automatic retry with exponential backoff for transient failures
 * - Rate limit handling (HTTP 429)
 * - Service unavailability handling (HTTP 503)
 * - Timeout retry logic with configurable attempts
 * - Rosetta mapping for flexible credential field extraction
 *
 * Security Note:
 * - Credentials are fetched from AWS Lambda function responses
 * - Uses SASL_SSL security protocol with OAuth Bearer authentication
 * - Client secrets are passed through JaasConfigBuilder with proper escaping
 * - Never log raw credentials - use SecretRedactor for logging
 *
 * Thread Safety: Uses @volatile variables for safe publication across threads
 *
 * @see ProbeVaultService for the trait this implements
 * @see software.amazon.awssdk.services.lambda.LambdaClient for AWS Lambda integration
 * @see JaasConfigBuilder for JAAS configuration generation
 * @see VaultCredentialsMapper for response parsing
 */
private[services] object AwsVaultService {
  /**
   * Factory method to create AwsVaultService with optional Lambda client factory
   *
   * @param lambdaClientFactory Optional custom Lambda client factory for testing
   * @return AwsVaultService instance
   */
  def apply(lambdaClientFactory: Option[LambdaClientFactory] = None): AwsVaultService = {
    lambdaClientFactory match {
      case None => new AwsVaultService(new DefaultLambdaClientFactory())
      case Some(factory) => new AwsVaultService(factory)
    }
  }
}

private[services] class AwsVaultService(val lambdaClientFactory: LambdaClientFactory) extends ProbeVaultService {

  @volatile private var logger: Option[Logger] = None
  @volatile private var actorSystem: Option[ActorSystem] = None
  @volatile private var lambdaClient: Option[LambdaClient] = None
  @volatile private var rosettaConfig: Option[RosettaConfig.RosettaConfig] = None
  @volatile private var appConfig: Option[Config] = None
  @volatile private var tokenEndpoint: Option[String] = None
  @volatile private var scope: Option[String] = None
  @volatile private var lambdaFunctionArn: Option[String] = None
  @volatile private var region: Option[String] = None
  @volatile private var retryAttempts: Option[Int] = None
  @volatile private var initialBackoff: Option[FiniteDuration] = None
  @volatile private var requestTimeout: Option[FiniteDuration] = None
  @volatile private var requiredFields: Option[List[String]] = None

  /**
   * Pre-flight validation for AwsVaultService initialization
   *
   * Validates that the BuilderContext contains the required configuration and that
   * AWS-specific settings (Lambda ARN and region) are properly configured.
   *
   * @param ctx BuilderContext containing configuration
   * @param ec ExecutionContext for Future execution
   * @return Future[BuilderContext] containing validated context
   * @throws IllegalArgumentException if config is missing, provider is not "aws",
   *                                  Lambda ARN is empty, or region is empty
   */
  override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config not found in BuilderContext")
    Try {
      val vaultConfig = VaultConfig.fromConfig(ctx.config.get)
      require(
        vaultConfig.provider.nonEmpty && vaultConfig.provider.equals("aws"),
        "Provider must be 'aws' for AwsVaultService"
      )
      require(vaultConfig.aws.lambdaArn.nonEmpty, "AWS Lambda ARN cannot be empty when provider is 'aws'")
      require(vaultConfig.aws.region.nonEmpty, "AWS region cannot be empty when provider is 'aws'")
      ctx
    } match {
      case Success(context) => context
      case Failure(ex) => throw ex
    }
  }

  /**
   * Initialize AwsVaultService with AWS Lambda client and configuration
   *
   * Creates the AWS Lambda client using the configured region, loads the Rosetta
   * mapping configuration for credential field extraction, and registers all
   * AWS-specific settings (ARN, retry policy, timeouts, OAuth endpoints).
   *
   * @param ctx BuilderContext containing configuration and ActorSystem
   * @param ec ExecutionContext for Future execution
   * @return Future[BuilderContext] with vault service registered
   * @throws IllegalStateException if initialization fails
   * @throws IllegalArgumentException if Rosetta mapping path is not defined or fails to load
   */
  override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined, "Config must be initialized before AwsVaultService")
    require(ctx.actorSystem.isDefined, "ActorSystem not initialized")
    ctx.actorSystem.get.log.info("Initializing AwsVaultService")
    Try {
      val vaultConfig: VaultConfig = VaultConfig.fromConfig(ctx.config.get)
      actorSystem = Some(ctx.actorSystem.get.toClassic)
      logger = Some(ctx.actorSystem.get.log)
      appConfig = Some(ctx.config.get)
      lambdaFunctionArn = Some(vaultConfig.aws.lambdaArn)
      region = Some(vaultConfig.aws.region)
      retryAttempts = Some(vaultConfig.aws.retryAttempts)
      initialBackoff = Some(1.second)
      requestTimeout = Some(vaultConfig.aws.timeout)
      requiredFields = Some(vaultConfig.aws.requiredFields)
      tokenEndpoint = Some(vaultConfig.oauth.tokenEndpoint)
      scope = Some(vaultConfig.oauth.scope)
      lambdaClient = Some(lambdaClientFactory.createClient(vaultConfig.aws.region))
      rosettaConfig = vaultConfig.rosettaMappingPath match {
        case Some(path) => RosettaConfig.load(path) match {
          case Right(config) => Some(config)
          case Left(ex: Throwable) => throw new IllegalStateException(s"Failed to load Rosetta mapping from $path", ex)
        }
        case None => throw new IllegalArgumentException("Rosetta mapping path must be defined")
      }
      ctx.actorSystem.get.log.info(s"AwsVaultService initialized with lambdaArn=${lambdaFunctionArn.get}, region=${region.get}")
      ctx.withVaultService(this)
    } match {
      case Success(context) => context
      case Failure(ex) => throw new IllegalStateException("Could not initialize AwsVaultService", ex)
    }
  }

  /**
   * Final validation that AwsVaultService is fully initialized
   *
   * Verifies that all required AWS-specific components (Lambda client, ARN, region,
   * retry settings, OAuth configuration, and Rosetta mapping) have been properly
   * initialized and are ready for operation.
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
    require(lambdaClient.isDefined, "Lambda Client not initialized")
    require(appConfig.isDefined, "App Config not initialized")
    require(lambdaFunctionArn.isDefined, "Lambda Function ARN not initialized")
    require(region.isDefined, "AWS Region not initialized")
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
   * Fetch security directives for all topics from AWS vault
   *
   * Retrieves Kafka credentials for each topic by invoking the AWS Lambda function
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
   * Invoke AWS Lambda function to fetch vault credentials for a topic
   *
   * Builds a request payload using RequestBodyBuilder and Rosetta mapping, then
   * invokes the Lambda function synchronously. Maps HTTP status codes to appropriate
   * exceptions for proper error handling.
   *
   * @param topicDirective TopicDirective specifying the topic and access requirements
   * @param ec ExecutionContext for Future execution
   * @return Future[String] containing the Lambda function's JSON response body
   * @throws VaultAuthException if authentication fails (401/403)
   * @throws VaultNotFoundException if Lambda function not found (404)
   * @throws VaultRateLimitException if rate limit exceeded (429)
   * @throws VaultServiceUnavailableException if Lambda service unavailable (503)
   * @throws VaultException for other non-successful status codes
   */
  def invokeVault(topicDirective: TopicDirective)(implicit ec: ExecutionContext): Future[String] =
    RequestBodyBuilder.build(topicDirective, rosettaConfig.get, appConfig.get) match {
      case Left(error) => Future.failed(error)
      case Right(payload) =>
        Future {
          val request: InvokeRequest = InvokeRequest.builder()
            .functionName(lambdaFunctionArn.get)
            .payload(SdkBytes.fromUtf8String(payload))
            .build()

          Try {
            val response = lambdaClient.get.invoke(request)
            val statusCode: Int = response.statusCode()
            val responsePayload: Option[String] = Option(response.payload())
              .map(_.asUtf8String())
              .filter(_.nonEmpty)
            (statusCode, responsePayload)
          } match {
            case Success((statusCode, responsePayload)) => (statusCode, responsePayload, None)
            case Failure(ex: software.amazon.awssdk.services.lambda.model.LambdaException) =>
              (ex.statusCode(), None, Some(ex))
            case Failure(ex) => throw ex
          }
        }.flatMap {
          case (200 | 201, Some(body), _) => Future.successful(body)
          case (401 | 403, _, _) => Future.failed(new VaultAuthException(s"AWS vault authentication failed for topic ${topicDirective.topic}"))
          case (404, _, _) => Future.failed(new VaultNotFoundException(s"AWS Lambda function not found: ${lambdaFunctionArn.get}"))
          case (429, body, _) => Future.failed(new VaultRateLimitException(s"AWS vault rate limit exceeded: ${body.getOrElse("no details")}"))
          case (503, body, _) => Future.failed(new VaultServiceUnavailableException(s"AWS vault unavailable: ${body.getOrElse("no details")}"))
          case (statusCode, body, _) => Future.failed(new VaultException(s"AWS vault returned status $statusCode: ${body.getOrElse("no response body")}"))
        }
    }

  /**
   * Fetch Kafka security directive with automatic retry on transient failures
   *
   * Invokes the AWS vault, parses the response, maps credentials using Rosetta configuration,
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
   * @throws VaultNotFoundException if Lambda function not found (not retried)
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
              Future.failed(new VaultMappingException(s"Failed to map AWS response for topic ${topicDirective.topic}: $allErrorMessages", primaryError))
          }
        case Left(parseError) => Future.failed(new VaultMappingException(s"Failed to parse AWS response for topic ${topicDirective.topic}: ${parseError.message}"))
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
   * Shutdown the AwsVaultService and release AWS resources
   *
   * Closes the AWS Lambda client connection, releasing any underlying HTTP
   * connections and resources.
   *
   * @param ec ExecutionContext for Future execution
   * @return Future[Unit] when shutdown completes
   */
  override def shutdown()(implicit ec: ExecutionContext): Future[Unit] = Future {
    lambdaClient.get.close()
  }
}
