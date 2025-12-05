package io.distia.probe
package core
package glue
package steps

import io.distia.probe.core.fixtures.ConfigurationFixtures.*
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.models.TestExecutionCommands
import io.cucumber.datatable.DataTable
import io.cucumber.scala.{EN, ScalaDsl}
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

/**
 * Shared background step definitions for all component tests.
 *
 * Provides common Gherkin steps used in Background sections across feature files:
 * - "Given a running actor system" - Verify ActorTestKit is available
 * - "Given a CoreConfig is available" - Load default CoreConfig
 * - "Given Docker is available" - Stub for component tests (real Docker in SIT)
 * - "Given Kafka is running" - Stub for component tests (real Kafka in SIT)
 * - "Given a QueueActor is spawned as parent" - Verify queueProbe exists
 * - "Given a list of SecurityDirective is available" - No-op prerequisite acknowledgment
 *
 * Design Philosophy:
 * - Single Responsibility: Background setup only
 * - DRY: Shared across all component tests
 * - Minimal: No actor spawning, just infrastructure verification
 * - Component vs SIT: Component tests stub external dependencies
 *
 * Fixtures Used:
 * - ConfigurationFixtures (CoreConfig factories)
 * - ActorWorld (state management, testKit access, probes)
 *
 * Used By:
 * - component/actor-lifecycle/guardian-actor.feature
 * - component/actor-lifecycle/queue-actor.feature
 * - component/actor-lifecycle/test-execution-actor-fsm.feature
 * - component/child-actors/.feature
 * - component/streaming/.feature
 *
 * Architecture Notes:
 * - These steps are idempotent (can be called multiple times safely)
 * - ActorTestKit lifecycle managed by WorldBase (auto-cleanup)
 * - CoreConfig loaded lazily (only when needed)
 * - Component tests mock external systems (Docker, Kafka via TestProbes)
 */
class SharedBackgroundSteps(world: ActorWorld) extends ScalaDsl with EN
  with Matchers {

  // ==========================================================================
  // Bulk Config Manipulation Steps (DataTable)
  // ==========================================================================

  /**
   * Given the following config properties are set:
   *
   * Sets multiple Typesafe Config properties in a single step using DataTable.
   * More efficient than multiple individual steps.
   *
   * DataTable format:
   * | key                                      | value           |
   * | test-probe.core.kafka.bootstrap-servers  | localhost:19092 |
   *
   * Example:
   * Given the following config properties are set:
   * | key                                           | value                      |
   * | test-probe.core.kafka.bootstrap-servers       | localhost:19092            |
   * | test-probe.core.kafka.schema-registry-url     | http://localhost:18081     | */
  Given("""the following config properties are set:""") { (dataTable: DataTable) =>
    import scala.jdk.CollectionConverters._
    val rows = dataTable.asMaps().asScala

    rows.foreach { row =>
      val key = row.get("key")
      val value = row.get("value")
      val coercedValue: Any = value match {
        case "true" => true
        case "false" => false
        case "null"  => null
        case n if n.matches("-?\\d+") => n.toInt
        case n if n.matches("-?\\d+\\.\\d+") => n.toDouble
        case s: String => s
        case a => a
      }
      world.setConfigPathValue(key, coercedValue)
    }
  }

  /**
   * Given the following config properties are removed:
   *
   * Removes multiple Typesafe Config properties in a single step using DataTable.
   * Useful for testing missing config scenarios.
   *
   * DataTable format:
   * | key                                      |
   * | test-probe.core.kafka.oauth.token-endpoint |
   *
   * Example:
   * Given the following config properties are removed:
   * | key                                           |
   * | test-probe.core.kafka.oauth.token-endpoint    | */
  Given("""the following config properties are removed:""") { (dataTable: DataTable) =>
    import scala.jdk.CollectionConverters._
    val rows = dataTable.asMaps().asScala
      rows.foreach { row =>
        val key = row.get("key")
        world.deleteConfigAtPath(key)
      }
    }

  /**
   * Given a running actor system
   *
   * Verifies that ActorTestKit is available and operational.
   * The ActorTestKit is automatically created by WorldBase and available
   * via world.testKit.
   *
   * This step is idempotent and can be called multiple times.
   */
  Given("""a running actor system""") { () =>
    // Verify ActorTestKit is available (created by WorldBase)
//    world.createServiceProbe
//    world.createQueueProbe
    world.testKit should not be null
  }

  /**
   * Given a CoreConfig is available
   *
   * Loads default CoreConfig and stores in ActorWorld for use by other steps.
   * Uses ConfigurationFixtures.defaultCoreConfig (single source of truth).
   *
   * This step is idempotent - if config already exists, it's not overwritten.
   */
  Given("""a CoreConfig is available""") { () =>
    // Load default CoreConfig if not already set
    if world.serviceConfig.isEmpty then
      world.serviceConfig = Some(defaultCoreConfig)

    // Verify config is now available
    world.serviceConfig shouldBe defined
  }

  /**
   * Given testcontainers infrastructure is started
   *
   * Starts Testcontainers (Kafka + Schema Registry) BEFORE ActorTestKit initialization.
   * CRITICAL: This step MUST run before "Given a running actor system" to ensure
   * the schema registry URL (with dynamic port) is in the config before testKit lazy val.
   *
   * Why: testKit = ActorTestKit(config) is a lazy val. Once accessed, config is frozen.
   * If infrastructure starts after testKit init, the dynamic schema registry URL is missed.
   *
   * Pattern:
   * ```gherkin
   * Background:
   *   Given testcontainers infrastructure is started  # <-- First: loads dynamic URLs into config
   *   And a running actor system                       # <-- Second: testKit gets correct config
   *   And a CoreConfig is available
   * ```
   *
   * Used By:
   * - component/streaming/kafka-consumer-streaming.feature
   * - component/streaming/kafka-producer-streaming.feature
   */
  Given("""testcontainers infrastructure is started""") { () =>
    // Start Testcontainers and update config with dynamic URLs
    world.startInfrastructure()

    // Verify schema registry URL updated in config
    world.schemaRegistryUrl should not be empty
    world.kafkaBootstrapServers should not be empty
  }

  /**
   * Given Docker is available
   *
   * For component tests, this is a stub step that acknowledges Docker as a prerequisite.
   * Component tests use TestProbes and mocks instead of real Docker containers.
   * Real Docker verification happens in SIT (System Integration Tests) via Testcontainers.
   *
   * This step exists to satisfy feature file Background sections uniformly across
   * component and SIT tests.
   */
  Given("""Docker is available""") { () =>
    // No-op for component tests
    // Real Docker verification happens in SIT tests with Testcontainers
  }

  /**
   * Given Kafka is running
   *
   * For component tests, this is a stub step that acknowledges Kafka as a prerequisite.
   * Component tests use TestProbes for KafkaProducerActor and KafkaConsumerActor.
   * Real Kafka (via Testcontainers) happens in SIT tests.
   *
   * This step exists to satisfy feature file Background sections uniformly across
   * component and SIT tests.
   */
  Given("""Kafka is running""") { () =>
    // No-op for component tests
    // Real Kafka (Testcontainers) happens in SIT tests
  }

  /**
   * Given a QueueActor is spawned as parent
   *
   * Verifies that queueProbe (TestProbe for QueueActor) is available in ActorWorld.
   * The queueProbe is automatically created by ActorWorld initialization and used
   * to mock QueueActor behavior in component tests.
   *
   * This step confirms the test infrastructure prerequisite is met.
   */
  Given("""a QueueActor is spawned as parent""") { () =>
    // Verify queueProbe is available (created by ActorWorld)
    world.queueProbe should not be null
  }

  /**
   * Given a BlockStorageDirective is available
   *
   * Creates a test BlockStorageDirective and stores in ActorWorld.
   * Used across multiple child actor tests that require storage context.
   *
   * Uses BlockStorageDirectiveFixtures.createBlockStorageDirective (via ActorWorld).
   *
   * Used By:
   * - component/child-actors/kafka-producer-actor.feature
   * - component/child-actors/kafka-consumer-actor.feature
   * - component/child-actors/vault-actor.feature
   */
  Given("""a BlockStorageDirective is available""") { () =>
    world.blockStorageDirective = Some(world.createBlockStorageDirective(
      jimfsLocation = s"/jimfs/test-${world.testId}"
    ))
  }

  /**
   * Given a list of KafkaSecurityDirective is available
   *
   * Populates world.securityDirectives with test security directives.
   * Used across multiple child actor tests that require security context.
   *
   * In component tests, we create minimal test directives (no real credentials).
   * Real credentials would be injected by VaultActor in production.
   *
   * Used By:
   * - component/child-actors/cucumber-execution-actor.feature
   * - component/child-actors/vault-actor.feature (indirectly)
   */
  Given("""a list of KafkaSecurityDirective is available""") { () =>
    // Create minimal test security directives for component testing
    // Real credentials would be injected by VaultActor in production
    world.securityDirectives = List.empty  // Empty list for component tests
  }

  /**
   * Given logging is monitored
   *
   * Sets up log capture for security compliance testing.
   * Used across multiple actors (VaultActor, CucumberExecutionActor) for
   * security logging verification scenarios.
   *
   * NOTE: This is scaffolding - log capture not yet implemented.
   * Future: Implement LogCapture utility for credential redaction verification.
   *
   * Used By:
   * - component/child-actors/vault-actor.feature
   * - component/child-actors/cucumber-execution-actor.feature
   */
  Given("""logging is monitored""") { () =>
    // TODO: Implement log capture utility
    // In scaffolding phase, this step is a placeholder
  }

  /**
   * Given a TestExecutionActor is spawned as parent
   *
   * Creates the parent probe used by child actor tests.
   * Child actors (BlockStorageActor, VaultActor, etc.) use this probe
   * to simulate messages to/from their parent TestExecutionActor.
   *
   * This step is idempotent - if probe already exists, it's not recreated.
   */
  Given("""a TestExecutionActor is spawned as parent""") { () =>
    if world.teaParentProbe.isEmpty then
      world.teaParentProbe = Some(world.testKit.createTestProbe[TestExecutionCommands.TestExecutionCommand]("parent-tea-probe"))
  }

  /**
   * Given the Kafka service is configured to throw exception on initialize
   *
   * Clears serviceConfig to make bootstrap-servers unavailable.
   * This triggers KafkaProducerActor/KafkaConsumerActor initialization failures.
   *
   * Used By:
   * - component/child-actors/kafka-producer-actor.feature (kafkaproducer-test-007)
   * - component/child-actors/kafka-consumer-actor.feature (test-007)
   */
  Given("""the Kafka service is configured to throw exception on initialize""") { () =>
    // Clear serviceConfig to make bootstrap-servers unavailable
    // This will cause Kafka actor initialization to fail
    world.serviceConfig = None
  }

  /**
   * Then the exception should bubble to the parent TestExecutionActor
   * Verifies exception propagation by watching for actor termination (stopped by supervisor)
   * Register all stop propagation here with a unique testIdString*/
  Then("""the exception should bubble to the parent TestExecutionActor""") { () =>
    world.teaParentProbe.foreach { probe =>
      world.testIdString.foreach {
        case "blockstorageactor-test-002" | "blockstorageactor-test-010" | "blockstorageactor-test-011" =>
          world.blockStorageActor.foreach { actor => probe.expectTerminated(actor, 3.second) }
        case "vaultactor-test-007" =>
          world.vaultActor.foreach { actor => probe.expectTerminated(actor, 3.second) }
        case "cucumber-test-009" | "cucumber-test-010" =>
          world.cucumberActor.foreach { actor => probe.expectTerminated(actor, 3.second) }
        case "kafkaproducer-test-007" =>
          world.producerActor.foreach { actor => probe.expectTerminated(actor, 3.second) }
        case "kafkaconsumer-test-007" =>
          world.consumerActor.foreach { actor => probe.expectTerminated(actor, 3.second) }
      }

    }
  }
}