package io.distia.probe
package services
package builder
package modules

import io.distia.probe.common.exceptions.{VaultAuthException, VaultNotFoundException}
import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.models.GuardianCommands.GuardianCommand
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class GcpVaultServiceIntegrationSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  given PatienceConfig = PatienceConfig(timeout = 10.seconds, interval = 500.milliseconds)

  var actorSystem: ActorSystem[GuardianCommand] = _
  given ExecutionContext = ExecutionContext.global

  private var wireMockServer: WireMockServer = _
  private val mockGcpFunctionPort = 9092

  override def beforeAll(): Unit = {
    actorSystem = ActorSystem(Behaviors.empty, "GcpVaultServiceIntegrationSpec")

    // Start WireMock server to mock GCP Cloud Function endpoint
    wireMockServer = new WireMockServer(wireMockConfig().port(mockGcpFunctionPort))
    wireMockServer.start()
  }

  override def afterAll(): Unit = {
    if wireMockServer != null then wireMockServer.stop()
    actorSystem.terminate()
  }

  private def createTestConfig(): Config = {
    ConfigFactory.parseString(s"""
      |test-probe {
      |  services {
      |    vault {
      |      provider = "gcp"
      |      gcp {
      |        function-url = "http://localhost:$mockGcpFunctionPort/vault-invoker"
      |        service-account-key = "/path/to/test-key.json"
      |        timeout = 30s
      |        retry-attempts = 3
      |        required-fields = ["clientId", "clientSecret"]
      |      }
      |      oauth {
      |        token-endpoint = "https://oauth.example.com/token"
      |        scope = "kafka:read kafka:write"
      |      }
      |      rosetta-mapping-path = "classpath:rosetta/gcp-vault-mapping.yaml"
      |    }
      |  }
      |}
    """.stripMargin).withFallback(ConfigFactory.load())
  }

  private def mockGcpFunctionSuccess(responseBody: String): Unit = {
    wireMockServer.stubFor(
      post(urlEqualTo("/vault-invoker"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
        )
    )
  }

  private def mockGcpFunctionFailure(statusCode: Int, errorMessage: String = ""): Unit = {
    val body = if (errorMessage.nonEmpty) s"""{"error":"$errorMessage"}""" else ""
    wireMockServer.stubFor(
      post(urlEqualTo("/vault-invoker"))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withHeader("Content-Type", "application/json")
            .withBody(body)
        )
    )
  }

  "GcpVaultServiceIntegrationSpec.fetchSecurityDirectives" should {

    "successfully fetch vault credentials and return SASL_SSL security directives" in {
      val service = GcpVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue
      service.finalCheck(ctx.withVaultService(service)).futureValue

      // Mock successful GCP Function response with vault credentials
      val vaultResponse = """{
        |  "credentials": {
        |    "clientId": "gcp-client-id-456",
        |    "clientSecret": "gcp-client-secret-abc123"
        |  }
        |}""".stripMargin
      mockGcpFunctionSuccess(vaultResponse)

      val topicDirective = TopicDirective(
        topic = "test-topic",
        role = "producer",
        clientPrincipal = "test-client",
        eventFilters = List.empty,
        metadata = Map.empty
      )
      val directive = BlockStorageDirective(
        jimfsLocation = "test-location",
        evidenceDir = "test-evidence",
        topicDirectives = List(topicDirective),
        bucket = "test-bucket"
      )

      val result = service.fetchSecurityDirectives(directive).futureValue

      result should have size 1
      result.head.topic shouldBe "test-topic"
      result.head.role shouldBe "producer"
      result.head.securityProtocol shouldBe SecurityProtocol.SASL_SSL
      result.head.jaasConfig should include("gcp-client-id-456")
      result.head.jaasConfig should include("gcp-client-secret-abc123")
    }

    "handle multiple topic directives" in {
      val service = GcpVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val vaultResponse = """{
        |  "credentials": {
        |    "clientId": "gcp-client-multi",
        |    "clientSecret": "gcp-secret-multi"
        |  }
        |}""".stripMargin
      mockGcpFunctionSuccess(vaultResponse)

      val topicDirectives = List(
        TopicDirective("topic-1", "producer", "client-1", List.empty, Map.empty),
        TopicDirective("topic-2", "consumer", "client-2", List.empty, Map.empty),
        TopicDirective("topic-3", "producer", "client-3", List.empty, Map.empty)
      )
      val directive = BlockStorageDirective(
        jimfsLocation = "test-location",
        evidenceDir = "test-evidence",
        topicDirectives = topicDirectives,
        bucket = "test-bucket"
      )

      val result = service.fetchSecurityDirectives(directive).futureValue

      result should have size 3
      result.map(_.topic) should contain allOf ("topic-1", "topic-2", "topic-3")
      result.foreach { securityDirective =>
        securityDirective.securityProtocol shouldBe SecurityProtocol.SASL_SSL
        securityDirective.jaasConfig should not be empty
      }
    }

    "throw VaultAuthException on 401 response" in {
      val service = GcpVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      mockGcpFunctionFailure(401, "Unauthorized")

      val topicDirective = TopicDirective("test-topic", "producer", "test-client", List.empty, Map.empty)
      val directive = BlockStorageDirective("test-loc", "test-evidence", List(topicDirective), "test-bucket")

      val exception = service.fetchSecurityDirectives(directive).failed.futureValue
      exception shouldBe a[VaultAuthException]
      exception.getMessage should include("authentication failed")
    }

    "throw VaultNotFoundException on 404 response" in {
      val service = GcpVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      mockGcpFunctionFailure(404)

      val topicDirective = TopicDirective("test-topic", "producer", "test-client", List.empty, Map.empty)
      val directive = BlockStorageDirective("test-loc", "test-evidence", List(topicDirective), "test-bucket")

      val exception = service.fetchSecurityDirectives(directive).failed.futureValue
      exception shouldBe a[VaultNotFoundException]
      exception.getMessage should include("not found")
    }

    "return empty list for BlockStorageDirective with no topic directives" in {
      val service = GcpVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val directive = BlockStorageDirective(
        jimfsLocation = "test-location",
        evidenceDir = "test-evidence",
        topicDirectives = List.empty,
        bucket = "test-bucket"
      )

      val result = service.fetchSecurityDirectives(directive).futureValue

      result shouldBe empty
    }

    // Test removed: "verify GCP service account authentication header is present"
    // Reason: This test expected application-level OAuth authentication to the vault function,
    //         which creates recursive authentication problem (need vault credentials to get vault credentials).
    //         Per ADR-VAULT-002, we use infrastructure-level IAM (network isolation + GCP IAM roles)
    //         instead of application-level authentication tokens.
    //         See: docs/architecture/adr/ADR-VAULT-002-cloud-native-authentication.md
  }

  "GcpVaultServiceIntegrationSpec.retry mechanism" should {

    "retry on 429 rate limit with exponential backoff" in {
      val service = GcpVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // First call returns 429, subsequent calls return success
      wireMockServer.stubFor(
        post(urlEqualTo("/vault-invoker"))
          .inScenario("Rate Limit Scenario")
          .whenScenarioStateIs("Started")
          .willReturn(
            aResponse()
              .withStatus(429)
              .withBody("""{"error":"Rate limit exceeded"}""")
          )
          .willSetStateTo("First Retry")
      )

      wireMockServer.stubFor(
        post(urlEqualTo("/vault-invoker"))
          .inScenario("Rate Limit Scenario")
          .whenScenarioStateIs("First Retry")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""{
                |  "credentials": {
                |    "clientId": "retry-client-id",
                |    "clientSecret": "retry-secret"
                |  }
                |}""".stripMargin)
          )
      )

      val topicDirective = TopicDirective("test-topic", "producer", "test-client", List.empty, Map.empty)
      val directive = BlockStorageDirective("test-loc", "test-evidence", List(topicDirective), "test-bucket")

      val result = service.fetchSecurityDirectives(directive).futureValue

      result should have size 1
      result.head.topic shouldBe "test-topic"
    }

    "retry on 503 service unavailable" in {
      val service = GcpVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      wireMockServer.stubFor(
        post(urlEqualTo("/vault-invoker"))
          .inScenario("Unavailable Scenario")
          .whenScenarioStateIs("Started")
          .willReturn(
            aResponse()
              .withStatus(503)
              .withBody("""{"error":"Service temporarily unavailable"}""")
          )
          .willSetStateTo("Recovered")
      )

      wireMockServer.stubFor(
        post(urlEqualTo("/vault-invoker"))
          .inScenario("Unavailable Scenario")
          .whenScenarioStateIs("Recovered")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""{
                |  "credentials": {
                |    "clientId": "recovered-client-id",
                |    "clientSecret": "recovered-secret"
                |  }
                |}""".stripMargin)
          )
      )

      val topicDirective = TopicDirective("test-topic", "producer", "test-client", List.empty, Map.empty)
      val directive = BlockStorageDirective("test-loc", "test-evidence", List(topicDirective), "test-bucket")

      val result = service.fetchSecurityDirectives(directive).futureValue

      result should have size 1
    }
  }
}
