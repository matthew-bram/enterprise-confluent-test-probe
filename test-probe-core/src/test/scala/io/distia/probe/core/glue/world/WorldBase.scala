package io.distia.probe.core.glue.world

import io.distia.probe.core.testutil.{SchemaRegistrationService, TestcontainersManager}
import io.distia.probe.core.config.CoreConfig
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit

import scala.concurrent.duration.*
import scala.util.{Failure, Success}

/**
 * Base trait for BDD world objects.
 *
 * Provides shared infrastructure for all Cucumber world objects:
 * - Testcontainers lifecycle (Kafka + Schema Registry)
 * - Schema registration service
 * - ActorTestKit access
 * - Common cleanup logic
 *
 * Design Principles:
 * - Single Responsibility: Infrastructure management only
 * - DRY: Shared logic extracted from individual world objects
 * - Clean Lifecycle: Automatic cleanup in shutdown()
 *
 * Usage:
 * {{{
 *   class ActorWorld extends WorldBase {
 *     // ... actor-specific state
 *   }
 *
 *   class StreamingWorld extends WorldBase {
 *     // ... streaming-specific state
 *   }
 * }}}
 *
 * Lifecycle:
 * 1. World instantiated by Cucumber
 * 2. Step definitions call startInfrastructure()
 * 3. Tests execute
 * 4. Cucumber calls shutdown() after scenario
 *
 * Thread Safety: NOT thread-safe. Cucumber runs scenarios sequentially.
 *
 * Test Strategy: Tested via ActorWorldSpec and integration tests
 */
trait WorldBase {

  var config: Config = ConfigFactory.load().resolve()

  def setConfigPathValue(k: String, v: Any): Unit =
    config = config.withValue(k, ConfigValueFactory.fromAnyRef(v))

  def deleteConfigAtPath(k: String): Unit =
    config = config.withoutPath(k) /// TODO: Currently not working, ActorTestKit is falling back



  /**
   * ActorTestKit for spawning actors and creating probes.
   *
   * Implicit val enables:
   * - testKit.spawn(behavior) calls
   * - testKit.createTestProbe[T]() calls
   * - Automatic shutdown in shutdown()
   */
  lazy implicit val testKit: ActorTestKit = ActorTestKit(config)


  /**
   * Default timeout for actor operations.
   *
   * Used by:
   * - Probe message expectations
   * - Actor responses
   * - State transitions
   */
  implicit val defaultTimeout: FiniteDuration = 3.seconds

  /**
   * Testcontainers manager (singleton object).
   *
   * Manages Kafka + Schema Registry Docker containers.
   * Idempotent start/stop with health checks.
   */
  private[core] def testcontainersManager = TestcontainersManager

  /**
   * Schema registration service (lazy - requires Testcontainers).
   *
   * Registers CloudEvent schemas with Schema Registry.
   * Auto-detects format (Avro, Protobuf, JSON) based on topic.
   */
  private[core] var schemaRegistrationService: Option[SchemaRegistrationService] = None

  /**
   * Kafka bootstrap servers (updated after Testcontainers start).
   *
   * Example: "localhost:49152"
   */
  def kafkaBootstrapServers: String = config.getString("test-probe.core.kafka.bootstrap-servers")

  /**
   * Schema Registry URL from configuration.
   *
   * Def (not lazy val) ensures we always read the current config value,
   * including the updated Testcontainers URL after startInfrastructure().
   * Example: "http://localhost:49153"
   */
  def schemaRegistryUrl: String = config.getString("test-probe.core.kafka.schema-registry-url")

  /**
   * Registered schema IDs (topic -> schemaId).
   *
   * Populated by registerSchema() calls.
   * Used to verify schema registration in tests.
   */
  var schemaIds: Map[String, Int] = Map.empty

  /**
   * Start infrastructure (Testcontainers + Schema Registry).
   *
   * Idempotent - safe to call multiple times.
   *
   * Steps:
   * 1. Start Kafka + Schema Registry containers
   * 2. Get dynamic ports (Testcontainers assigns random ports)
   * 3. Create SchemaRegistrationService with actual URLs
   * 4. Update kafkaBootstrapServers and schemaRegistryUrl
   *
   * @return Success(unit) or Failure(exception)
   */
  def startInfrastructure(): Unit = {
    testcontainersManager.start()

    // Get dynamic URLs from Testcontainers
    setConfigPathValue("test-probe.core.kafka.bootstrap-servers", testcontainersManager.getKafkaBootstrapServers)
    setConfigPathValue("test-probe.core.kafka.schema-registry-url", testcontainersManager.getSchemaRegistryUrl)

    // Clean Schema Registry for idempotent tests (delete all schemas from previous runs)
    testcontainersManager.cleanSchemaRegistry()

    // Create SchemaRegistrationService with actual URLs (schemaRegistryUrl is lazy val from config)
    schemaRegistrationService = Some(
      new SchemaRegistrationService(schemaRegistryUrl)
    )
  }

  /**
   * Register schema with Schema Registry.
   *
   * Auto-detects schema format based on topic:
   * - test-events → Avro
   * - order-events → Protobuf
   * - payment-events → JSON
   * - default → Avro
   *
   * @param topic Kafka topic
   * @return Schema ID assigned by Schema Registry
   * @throws RuntimeException if schema registration fails
   */
  def registerSchema(topic: String): Int = {
    schemaRegistrationService match {
      case Some(service) =>
        service.registerSchema(topic) match {
          case Success(schemaId) =>
            // Track schema ID for verification
            schemaIds = schemaIds + (topic -> schemaId)
            schemaId

          case Failure(ex) =>
            throw new RuntimeException(
              s"Failed to register schema for topic '$topic': ${ex.getMessage}",
              ex
            )
        }

      case None =>
        throw new IllegalStateException(
          "SchemaRegistrationService not initialized. Call startInfrastructure() first."
        )
    }
  }

  /**
   * Shutdown infrastructure and cleanup resources.
   *
   * Called automatically by Cucumber after each scenario.
   *
   * Cleanup order:
   * 1. Shutdown ActorTestKit (terminates all spawned actors)
   * 2. Stop Testcontainers (Kafka + Schema Registry)
   * 3. Clear state
   *
   * Idempotent - safe to call multiple times.
   */
  def shutdown(): Unit = {
    // Shutdown ActorTestKit (terminates all actors)
    testKit.shutdownTestKit()

    // Testcontainers cleanup is automatic via JVM shutdown hook
    // (no manual stop() needed)

    // Clear state
    schemaRegistrationService = None
    // DO NOT clear kafkaBootstrapServers - Testcontainers persist across scenarios
    // kafkaBootstrapServers is set once by startInfrastructure() and reused
    // Clearing it causes "No resolvable bootstrap urls" errors in subsequent scenarios
    // schemaRegistryUrl is lazy val from config - no reset needed
    schemaIds = Map.empty

  }

  /**
   * Check if infrastructure is running.
   *
   * @return true if Testcontainers are running
   */
  def isInfrastructureRunning: Boolean =
    testcontainersManager.isRunning
}
