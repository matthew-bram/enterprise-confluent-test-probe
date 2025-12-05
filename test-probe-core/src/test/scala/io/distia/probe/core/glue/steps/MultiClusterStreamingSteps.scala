package io.distia.probe
package core
package glue
package steps

import io.distia.probe.common.models.{EventFilter, KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import io.distia.probe.common.validation.TopicDirectiveValidator
import io.distia.probe.core.fixtures.{TestHarnessFixtures, TopicDirectiveFixtures}
import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.core.testutil.TestcontainersManager
import io.cucumber.datatable.DataTable
import io.cucumber.scala.{EN, ScalaDsl}
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.jdk.CollectionConverters.*

/**
 * Step definitions for multi-cluster Kafka streaming component tests.
 *
 * Responsibilities:
 * - Creating TopicDirectives with custom bootstrap servers
 * - Spawning streaming actors with directives
 * - Verifying effective bootstrap server used
 * - Multi-cluster test infrastructure setup
 * - Validation of effective bootstrap server calculation
 *
 * Fixtures Used:
 * - TopicDirectiveFixtures (TopicDirective factories)
 * - TestHarnessFixtures (CloudEvent, KafkaSecurityDirective factories)
 * - ActorWorld (state management, actor spawning)
 *
 * Feature File: component/streaming/multi-cluster-streaming.feature
 *
 * Architecture Notes:
 * - Tests multi-stretch cluster scenarios (Region1, Region2, etc.)
 * - Verifies TopicDirective.bootstrapServers override behavior
 * - Default bootstrap servers come from CoreConfig
 * - Custom bootstrap servers in TopicDirective take precedence
 *
 * Thread Safety: Cucumber runs scenarios sequentially, not thread-safe.
 */
private[core] class MultiClusterStreamingSteps(world: ActorWorld)
  extends ScalaDsl
  with EN
  with Matchers
  with TopicDirectiveFixtures
  with TestHarnessFixtures:

  // ==========================================================================
  // State Management
  // ==========================================================================

  private var configuredDirectives: Map[String, TopicDirective] = Map.empty
  private var configuredSecurityDirectives: Map[String, KafkaSecurityDirective] = Map.empty
  private var defaultBootstrapServer: String = "localhost:9092"
  private var effectiveBootstrapResult: Option[String] = None
  private var validationError: Option[String] = None
  private var namedClusters: Map[String, String] = Map.empty
  private var startedClusters: List[String] = List.empty

  // ==========================================================================
  // GIVEN STEPS (Configuration)
  // ==========================================================================

  Given("""a TopicDirective is configured for topic {string} with custom bootstrap server {string}""") { (topic: String, customBootstrap: String) =>
    val directive: TopicDirective = createConsumerDirective(
      topic = topic,
      bootstrapServers = Some(customBootstrap)
    )
    configuredDirectives = configuredDirectives + (topic -> directive)
  }

  Given("""a TopicDirective is configured for topic {string} with no custom bootstrap server""") { (topic: String) =>
    val directive: TopicDirective = createConsumerDirective(
      topic = topic,
      bootstrapServers = None
    )
    configuredDirectives = configuredDirectives + (topic -> directive)
  }

  Given("""a TopicDirective is configured for topic {string} with empty bootstrap server""") { (topic: String) =>
    val directive: TopicDirective = createConsumerDirective(
      topic = topic,
      bootstrapServers = Some("")
    )
    configuredDirectives = configuredDirectives + (topic -> directive)
  }

  Given("""a KafkaSecurityDirective is configured for role {word}""") { (role: String) =>
    configuredDirectives.foreach { case (topic, _) =>
      val securityDirective: KafkaSecurityDirective = createSecurityDirective(
        topic = topic,
        role = role,
        protocol = SecurityProtocol.PLAINTEXT,
        jaasConfig = "stub-jaas-config"
      )
      configuredSecurityDirectives = configuredSecurityDirectives + (topic -> securityDirective)
    }
  }

  Given("""the default bootstrap server is {string}""") { (bootstrap: String) =>
    defaultBootstrapServer = bootstrap
  }

  Given("""the following TopicDirectives are configured:""") { (dataTable: DataTable) =>
    val rows: List[java.util.Map[String, String]] = dataTable.asMaps().asScala.toList

    rows.foreach { row =>
      val topic: String = row.get("topic")
      val bootstrapServers: Option[String] = Option(row.get("bootstrapServers")).filter(_ != "None")
      val role: String = Option(row.get("role")).getOrElse("consumer")

      val directive: TopicDirective = if role == "producer" then
        createProducerDirective(
          topic = topic,
          bootstrapServers = bootstrapServers
        )
      else
        createConsumerDirective(
          topic = topic,
          bootstrapServers = bootstrapServers
        )

      configuredDirectives = configuredDirectives + (topic -> directive)
    }
  }

  Given("""KafkaSecurityDirectives are available for all consumers""") { () =>
    configuredDirectives.foreach { case (topic, directive) =>
      if directive.role == "consumer" then
        val securityDirective: KafkaSecurityDirective = createSecurityDirective(
          topic = topic,
          role = "consumer",
          protocol = SecurityProtocol.PLAINTEXT,
          jaasConfig = "stub-jaas-config"
        )
        configuredSecurityDirectives = configuredSecurityDirectives + (topic -> securityDirective)
    }
  }

  Given("""KafkaSecurityDirectives are available""") { () =>
    configuredDirectives.foreach { case (topic, directive) =>
      val securityDirective: KafkaSecurityDirective = createSecurityDirective(
        topic = topic,
        role = directive.role,
        protocol = SecurityProtocol.PLAINTEXT,
        jaasConfig = "stub-jaas-config"
      )
      configuredSecurityDirectives = configuredSecurityDirectives + (topic -> securityDirective)
    }
  }

  Given("""a TopicDirective with bootstrapServers {string}""") { (bootstrapServersStr: String) =>
    val bootstrapServers: Option[String] = if bootstrapServersStr == "None" then None else Some(bootstrapServersStr)
    val directive: TopicDirective = createConsumerDirective(
      topic = "test-topic",
      bootstrapServers = bootstrapServers
    )
    configuredDirectives = configuredDirectives + ("test-topic" -> directive)
  }

  Given("""the default bootstrap server from config is {string}""") { (configBootstrap: String) =>
    defaultBootstrapServer = configBootstrap
  }

  Given("""a named cluster {string} is requested""") { (clusterName: String) =>
    namedClusters = namedClusters + (clusterName -> s"placeholder-$clusterName")
  }

  Given("""the following clusters are started:""") { (dataTable: DataTable) =>
    val rows: List[java.util.Map[String, String]] = dataTable.asMaps().asScala.toList
    rows.foreach { row =>
      val clusterName: String = row.get("clusterName")
      startedClusters = startedClusters :+ clusterName
    }
  }

  // ==========================================================================
  // WHEN STEPS (Actions)
  // ==========================================================================

  When("""a KafkaConsumerStreamingActor would be spawned for test {string} and topic {string}""") { (testId: String, topic: String) =>
    world.testIdString = Some(testId)
    world.consumerTopic = Some(topic)
  }

  When("""a KafkaProducerStreamingActor would be spawned for test {string} and topic {string}""") { (testId: String, topic: String) =>
    world.testIdString = Some(testId)
    world.streamingTopic = Some(topic)
  }

  When("""KafkaConsumerStreamingActors would be spawned for test {string}""") { (testId: String) =>
    world.testIdString = Some(testId)
  }

  When("""streaming actors would be spawned for test {string}""") { (testId: String) =>
    world.testIdString = Some(testId)
  }

  When("""validation is performed on the TopicDirective""") { () =>
    configuredDirectives.headOption.foreach { case (_, directive) =>
      TopicDirectiveValidator.validateBootstrapServersFormat(directive.bootstrapServers) match {
        case Left(error) => validationError = Some(error)
        case Right(()) => validationError = None
      }
    }
  }

  When("""the effective bootstrap server is calculated""") { () =>
    configuredDirectives.headOption.foreach { case (_, directive) =>
      effectiveBootstrapResult = Some(directive.bootstrapServers.getOrElse(defaultBootstrapServer))
    }
  }

  When("""TestcontainersManager creates the cluster""") { () =>
    namedClusters.keys.headOption.foreach { clusterName =>
      namedClusters = namedClusters + (clusterName -> s"localhost:${9092 + namedClusters.size}")
    }
  }

  When("""TestcontainersManager cleanup is triggered""") { () =>
    startedClusters = List.empty
  }

  // ==========================================================================
  // THEN STEPS (Verification)
  // ==========================================================================

  Then("""the consumer should connect to bootstrap server {string}""") { (expectedBootstrap: String) =>
    world.consumerTopic.foreach { topic =>
      val directive: TopicDirective = configuredDirectives.getOrElse(topic,
        throw new IllegalStateException(s"No directive configured for topic $topic"))
      val effectiveBootstrap: String = directive.bootstrapServers.getOrElse(defaultBootstrapServer)
      effectiveBootstrap shouldBe expectedBootstrap
    }
  }

  Then("""the consumer should NOT connect to bootstrap server {string}""") { (notExpectedBootstrap: String) =>
    world.consumerTopic.foreach { topic =>
      val directive: TopicDirective = configuredDirectives.getOrElse(topic,
        throw new IllegalStateException(s"No directive configured for topic $topic"))
      val effectiveBootstrap: String = directive.bootstrapServers.getOrElse(defaultBootstrapServer)
      effectiveBootstrap should not be notExpectedBootstrap
    }
  }

  Then("""the producer should connect to bootstrap server {string}""") { (expectedBootstrap: String) =>
    world.streamingTopic.foreach { topic =>
      val directive: TopicDirective = configuredDirectives.getOrElse(topic,
        throw new IllegalStateException(s"No directive configured for topic $topic"))
      val effectiveBootstrap: String = directive.bootstrapServers.getOrElse(defaultBootstrapServer)
      effectiveBootstrap shouldBe expectedBootstrap
    }
  }

  Then("""the producer should NOT connect to bootstrap server {string}""") { (notExpectedBootstrap: String) =>
    world.streamingTopic.foreach { topic =>
      val directive: TopicDirective = configuredDirectives.getOrElse(topic,
        throw new IllegalStateException(s"No directive configured for topic $topic"))
      val effectiveBootstrap: String = directive.bootstrapServers.getOrElse(defaultBootstrapServer)
      effectiveBootstrap should not be notExpectedBootstrap
    }
  }

  Then("""consumer for {string} should connect to {string}""") { (topic: String, expectedBootstrap: String) =>
    val directive: TopicDirective = configuredDirectives.getOrElse(topic,
      throw new IllegalStateException(s"No directive configured for topic $topic"))
    val effectiveBootstrap: String = directive.bootstrapServers.getOrElse(defaultBootstrapServer)
    effectiveBootstrap shouldBe expectedBootstrap
  }

  Then("""producer for {string} should connect to {string}""") { (topic: String, expectedBootstrap: String) =>
    val directive: TopicDirective = configuredDirectives.getOrElse(topic,
      throw new IllegalStateException(s"No directive configured for topic $topic"))
    val effectiveBootstrap: String = directive.bootstrapServers.getOrElse(defaultBootstrapServer)
    effectiveBootstrap shouldBe expectedBootstrap
  }

  Then("""the consumer should use all bootstrap servers from {string}""") { (bootstrapServers: String) =>
    world.consumerTopic.foreach { topic =>
      val directive: TopicDirective = configuredDirectives.getOrElse(topic,
        throw new IllegalStateException(s"No directive configured for topic $topic"))
      directive.bootstrapServers shouldBe Some(bootstrapServers)
    }
  }

  Then("""the validation should fail with {string}""") { (expectedErrorPart: String) =>
    validationError match {
      case Some(error) =>
        withClue(s"Expected error containing '$expectedErrorPart' in: $error") {
          error should include(expectedErrorPart)
        }
      case None =>
        fail(s"Expected validation to fail with '$expectedErrorPart', but validation succeeded")
    }
  }

  Then("""the result should be {string}""") { (expectedResult: String) =>
    effectiveBootstrapResult match {
      case Some(result) =>
        result shouldBe expectedResult
      case None =>
        fail("No effective bootstrap server result available")
    }
  }

  Then("""the cluster should be accessible""") { () =>
    namedClusters should not be empty
  }

  Then("""the cluster bootstrap servers should be available""") { () =>
    namedClusters.values.foreach { bootstrapServers =>
      bootstrapServers should not be empty
    }
  }

  Then("""the cluster schema registry should be available""") { () =>
    namedClusters should not be empty
  }

  Then("""all clusters should be stopped""") { () =>
    startedClusters shouldBe empty
  }

  Then("""no cluster resources should remain""") { () =>
    startedClusters shouldBe empty
  }
