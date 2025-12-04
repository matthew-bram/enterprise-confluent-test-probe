package io.distia.probe
package core
package config

import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters._

/**
 * Configuration validation exception
 *
 * Thrown when configuration validation fails at startup. Contains aggregated
 * validation errors with detailed diagnostic information.
 *
 * @param message Human-readable error summary
 * @param cause Optional underlying exception
 */
final class ConfigurationException(message: String, cause: Throwable = null)
  extends RuntimeException(message, cause)

/**
 * Kafka OAuth configuration
 *
 * OAuth 2.0 configuration for Kafka broker authentication.
 *
 * @param tokenEndpoint OAuth token endpoint URL
 * @param clientScope OAuth client scope for authorization
 */
private[core] case class KafkaOAuthConfig(
  tokenEndpoint: String,
  clientScope: String
)

/**
 * Kafka cluster configuration
 *
 * Configuration for connecting to Kafka brokers and Schema Registry.
 *
 * @param bootstrapServers Comma-separated list of Kafka broker addresses (host:port)
 * @param schemaRegistryUrl Schema Registry HTTP endpoint URL
 * @param oauth OAuth 2.0 authentication configuration
 */
private[core] case class KafkaConfig(
  bootstrapServers: String,
  schemaRegistryUrl: String,
  oauth: KafkaOAuthConfig
)

/**
 * Core module configuration parsed from test-probe.core.* namespace
 *
 * This configuration class contains ONLY core module settings (actor system, supervision, etc.).
 *
 * Design:
 * - Immutable value class - all fields val
 * - Single responsibility - core functionality only
 * - Type-safe - all durations, counts, and enum values validated
 * - Fail-fast - invalid config throws ConfigurationException at startup
 *
 * Module Boundaries:
 * - Core: actor system, supervision, test execution, kafka
 *
 * @param actorSystemName Name of the Pekko ActorSystem
 * @param actorSystemTimeout Default timeout for ask patterns
 * @param shutdownTimeout Maximum time to wait for graceful shutdown
 * @param initializationTimeout Maximum time to wait for system startup
 * @param poolSize Size of actor router pools
 * @param maxExecutionTime Maximum allowed test execution time
 * @param maxRestarts Maximum actor restarts within restart-time-range
 * @param restartTimeRange Time window for counting restarts
 * @param maxRetries Maximum retry attempts for recoverable failures
 * @param cleanupDelay Delay before cleaning up completed tests
 * @param stashBufferSize Maximum messages to stash during state transitions
 * @param setupStateTimeout Timeout for Setup state (file upload phase)
 * @param loadingStateTimeout Timeout for Loading state (test load phase)
 * @param completedStateTimeout Timeout before cleaning up completed tests
 * @param exceptionStateTimeout Timeout before cleaning up failed tests
 * @param gluePackage Cucumber framework glue package for step definitions
 * @param storageTimeout Timeout for storage operations
 * @param kafka Kafka cluster configuration (bootstrap servers, schema registry, OAuth)
 * @param dslAskTimeout Timeout for DSL ask operations (produceEvent, etc.)
 */
private[core] case class CoreConfig(
  // Actor System Configuration
  actorSystemName: String,
  actorSystemTimeout: FiniteDuration,
  shutdownTimeout: FiniteDuration,
  initializationTimeout: FiniteDuration,
  poolSize: Int,
  maxExecutionTime: FiniteDuration,

  // Supervision Configuration
  maxRestarts: Int,
  restartTimeRange: FiniteDuration,

  // Test Execution Configuration
  maxRetries: Int,
  cleanupDelay: FiniteDuration,
  stashBufferSize: Int,

  // State Timers Configuration
  setupStateTimeout: FiniteDuration,
  loadingStateTimeout: FiniteDuration,
  completedStateTimeout: FiniteDuration,
  exceptionStateTimeout: FiniteDuration,

  // Cucumber Configuration
  gluePackage: String,

  // Storage Configuration
  storageTimeout: FiniteDuration,

  // Kafka Configuration
  kafka: KafkaConfig,

  // DSL Configuration
  dslAskTimeout: FiniteDuration
)

private[core] object CoreConfig {

  private val logger = LoggerFactory.getLogger(CoreConfig.getClass)

  /**
   * Factory method to create CoreConfig from Typesafe Config
   *
   * Validates configuration using CoreConfigValidator and fails fast if
   * validation errors are found. Warnings are logged but do not prevent
   * startup.
   *
   * @param config Typesafe Config instance (typically from application.conf)
   * @return CoreConfig instance with validated configuration values
   * @throws ConfigurationException if validation fails with errors
   */
  def fromConfig(config: Config): CoreConfig = {
    CoreConfigValidator.validate(config) match {
      case ValidationSuccess(warnings) =>
        if warnings.nonEmpty then
          logger.warn(s"Configuration loaded with ${warnings.size} warning(s):")
          warnings.foreach(w => logger.warn(w.format))

      case ValidationFailure(errors, warnings) =>
        logger.error(s"Configuration validation failed with ${errors.size} error(s):")
        errors.foreach(e => logger.error(e.format))
        if warnings.nonEmpty then
          logger.warn(s"Additionally, ${warnings.size} warning(s) found:")
          warnings.foreach(w => logger.warn(w.format))

        val errorSummary: String = errors.map(_.path).mkString(", ")
        throw new ConfigurationException(
          s"Configuration validation failed for: $errorSummary. See logs for details."
        )
    }

    val core = config.getConfig("test-probe.core")

    CoreConfig(
      // Actor System
      actorSystemName = core.getString("actor-system.name"),
      actorSystemTimeout = core.getDuration("actor-system.timeout").toScala,
      shutdownTimeout = core.getDuration("actor-system.shutdown-timeout").toScala,
      initializationTimeout = core.getDuration("actor-system.initialization-timeout").toScala,
      poolSize = core.getInt("actor-system.pool-size"),
      maxExecutionTime = core.getDuration("actor-system.max-execution-time").toScala,

      // Supervision
      maxRestarts = core.getInt("supervision.max-restarts"),
      restartTimeRange = core.getDuration("supervision.restart-time-range").toScala,

      // Test Execution
      maxRetries = core.getInt("test-execution.max-retries"),
      cleanupDelay = core.getDuration("test-execution.cleanup-delay").toScala,
      stashBufferSize = core.getInt("test-execution.stash-buffer-size"),

      // State Timers
      setupStateTimeout = core.getDuration("timers.setup-state").toScala,
      loadingStateTimeout = core.getDuration("timers.loading-state").toScala,
      completedStateTimeout = core.getDuration("timers.completed-state").toScala,
      exceptionStateTimeout = core.getDuration("timers.exception-state").toScala,

      // Cucumber
      gluePackage = core.getString("cucumber.glue-packages"),

      // Storage (uses shared services timeout)
      storageTimeout = core.getDuration("services.timeout").toScala,
      
      // Kafka
      kafka = KafkaConfig(
        bootstrapServers = core.getString("kafka.bootstrap-servers"),
        schemaRegistryUrl = core.getString("kafka.schema-registry-url"),
        oauth = KafkaOAuthConfig(
          tokenEndpoint = core.getString("kafka.oauth.token-endpoint"),
          clientScope = core.getString("kafka.oauth.client-scope")
        )
      ),

      // DSL
      dslAskTimeout = core.getDuration("dsl.ask-timeout").toScala
    )
  }
}
