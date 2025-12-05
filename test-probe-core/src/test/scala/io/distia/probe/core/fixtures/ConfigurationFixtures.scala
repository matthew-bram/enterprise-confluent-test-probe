package io.distia.probe.core.fixtures

import io.distia.probe.core.config.{CoreConfig, KafkaConfig, KafkaOAuthConfig}

import scala.concurrent.duration._
import java.util.UUID

/**
 * Configuration fixtures for testing.
 *
 * Provides:
 * - Pre-built CoreConfig instances with sensible test defaults
 * - Config builders for customization
 * - Common test constants (test IDs, topics, bucket names)
 *
 * This consolidates functionality from:
 * - CoreConfigFixtures (104 lines)
 * - CommonTestData (67 lines)
 * - Various hardcoded values across test files
 *
 * Design Principles:
 * - Sensible defaults (tests just work without config tweaking)
 * - Short timeouts (tests run fast)
 * - Localhost Kafka (Testcontainers-friendly)
 * - Customizable via builder methods
 *
 * Usage:
 * {{{
 *   import ConfigurationFixtures._
 *
 *   // Use defaults:
 *   val config = defaultCoreConfig
 *
 *   // Customize:
 *   val config = customCoreConfig(
 *     setupTimeout = 2.seconds,
 *     loadingTimeout = 1.second
 *   )
 * }}}
 *
 * Thread Safety: All values are immutable - thread-safe.
 *
 * Test Strategy: Tested via ConfigurationFixturesSpec (10 tests)
 */
object ConfigurationFixtures {

  // ==========================================================================
  // Test Constants
  // ==========================================================================

  /**
   * Standard test IDs for use across tests.
   */
  object TestIds {
    val test1: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    val test2: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    val test3: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
  }

  /**
   * Standard test topics.
   */
  object Topics {
    val testEvents: String = "test-events"
    val orderEvents: String = "order-events"
    val paymentEvents: String = "payment-events"
    val userEvents: String = "user-events"
  }

  /**
   * Standard event types.
   */
  object EventTypes {
    val testEvent: String = "TestEvent"
    val orderPlaced: String = "OrderPlaced"
    val paymentProcessed: String = "PaymentProcessed"
    val userCreated: String = "UserCreated"
  }

  /**
   * Standard bucket names.
   */
  object Buckets {
    val testBucket: String = "test-bucket"
    val integrationBucket: String = "integration-bucket"
  }

  // ==========================================================================
  // Default Kafka Config (Testcontainers-friendly)
  // ==========================================================================

  /**
   * Default Kafka configuration for tests.
   *
   * Uses localhost (Testcontainers will override with actual ports).
   * OAuth config included but not used in PLAINTEXT mode.
   */
  def defaultKafkaConfig: KafkaConfig = KafkaConfig(
    bootstrapServers = "localhost:9092",
    schemaRegistryUrl = "http://localhost:8081",
    oauth = KafkaOAuthConfig(
      tokenEndpoint = "http://localhost:8080/oauth/token",
      clientScope = "kafka.producer"
    )
  )

  // ==========================================================================
  // Default CoreConfig (Fast test timeouts)
  // ==========================================================================

  /**
   * Default CoreConfig for tests.
   *
   * Key characteristics:
   * - Short timeouts (tests run fast: 5s max)
   * - Small buffer sizes (minimal memory)
   * - Localhost Kafka (Testcontainers)
   * - Framework glue package configured
   */
  def defaultCoreConfig: CoreConfig = CoreConfig(
    // Actor System
    actorSystemName = "TestProbeSystem",
    actorSystemTimeout = 3.seconds,
    shutdownTimeout = 5.seconds,
    initializationTimeout = 5.seconds,
    poolSize = 2,
    maxExecutionTime = 5.seconds,

    // Supervision
    maxRestarts = 3,
    restartTimeRange = 10.seconds,

    // Test Execution
    maxRetries = 2,
    cleanupDelay = 500.milliseconds,
    stashBufferSize = 10,

    // State Timers (short for fast tests)
    setupStateTimeout = 2.seconds,
    loadingStateTimeout = 2.seconds,
    completedStateTimeout = 1.second,
    exceptionStateTimeout = 1.second,

    // Cucumber
    gluePackage = "io.distia.probe.core.glue",

    // Storage
    storageTimeout = 3.seconds,

    // Kafka
    kafka = defaultKafkaConfig,

    // DSL
    dslAskTimeout = 3.seconds
  )

  /**
   * CoreConfig with very short timeouts (for timeout testing).
   *
   * Use this to test timeout behavior without waiting long.
   */
  def shortTimerCoreConfig: CoreConfig = defaultCoreConfig.copy(
    setupStateTimeout = 100.milliseconds,
    loadingStateTimeout = 100.milliseconds,
    completedStateTimeout = 50.milliseconds,
    exceptionStateTimeout = 50.milliseconds
  )

  /**
   * CoreConfig with custom timeout values.
   *
   * @param setupTimeout Setup state timeout
   * @param loadingTimeout Loading state timeout
   * @param completedTimeout Completed state timeout
   * @param exceptionTimeout Exception state timeout
   * @return CoreConfig with custom timeouts
   */
  def customTimeoutCoreConfig(
    setupTimeout: FiniteDuration = 2.seconds,
    loadingTimeout: FiniteDuration = 2.seconds,
    completedTimeout: FiniteDuration = 1.second,
    exceptionTimeout: FiniteDuration = 1.second
  ): CoreConfig = defaultCoreConfig.copy(
    setupStateTimeout = setupTimeout,
    loadingStateTimeout = loadingTimeout,
    completedStateTimeout = completedTimeout,
    exceptionStateTimeout = exceptionTimeout
  )

  /**
   * CoreConfig with custom Kafka configuration.
   *
   * Use this when testing with real Kafka (non-Testcontainers).
   *
   * @param bootstrapServers Kafka bootstrap servers
   * @param schemaRegistryUrl Schema Registry URL
   * @return CoreConfig with custom Kafka config
   */
  def customKafkaCoreConfig(
    bootstrapServers: String,
    schemaRegistryUrl: String
  ): CoreConfig = defaultCoreConfig.copy(
    kafka = defaultKafkaConfig.copy(
      bootstrapServers = bootstrapServers,
      schemaRegistryUrl = schemaRegistryUrl
    )
  )
}
