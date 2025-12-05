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

class AzureVaultServiceIntegrationSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  given PatienceConfig = PatienceConfig(timeout = 10.seconds, interval = 500.milliseconds)

  var actorSystem: ActorSystem[GuardianCommand] = _
  given ExecutionContext = ExecutionContext.global

  private var wireMockServer: WireMockServer = _
  private val mockAzureFunctionPort = 9091

  override def beforeAll(): Unit = {
    actorSystem = ActorSystem(Behaviors.empty, "AzureVaultServiceIntegrationSpec")

    // Start WireMock server to mock Azure Function endpoint
    wireMockServer = new WireMockServer(wireMockConfig().port(mockAzureFunctionPort))
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
      |      provider = "azure"
      |      azure {
      |        function-url = "http://localhost:$mockAzureFunctionPort/api/vault"
      |        function-key = "test-function-key-123"
      |        timeout = 30s
      |        retry-attempts = 3
      |        required-fields = ["clientId", "clientSecret"]
      |      }
      |      oauth {
      |        token-endpoint = "https://oauth.example.com/token"
      |        scope = "kafka:read kafka:write"
      |      }
      |      rosetta-mapping-path = "classpath:rosetta/azure-vault-mapping.yaml"
      |    }
      |  }
      |}
    """.stripMargin).withFallback(ConfigFactory.load())
  }

  private def mockAzureFunctionSuccess(responseBody: String): Unit = {
    wireMockServer.stubFor(
      post(urlEqualTo("/api/vault"))
        .withHeader("x-functions-key", equalTo("test-function-key-123"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
        )
    )
  }

  private def mockAzureFunctionFailure(statusCode: Int, errorMessage: String = ""): Unit = {
    val body = if (errorMessage.nonEmpty) s"""{"error":"$errorMessage"}""" else ""
    wireMockServer.stubFor(
      post(urlEqualTo("/api/vault"))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withHeader("Content-Type", "application/json")
            .withBody(body)
        )
    )
  }

  "AzureVaultServiceIntegrationSpec.fetchSecurityDirectives" should {

    "successfully fetch vault credentials and return SASL_SSL security directives" in {
      val service = AzureVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue
      service.finalCheck(ctx.withVaultService(service)).futureValue

      // Mock successful Azure Function response with vault credentials
      val vaultResponse = """{
        |  "credentials": {
        |    "clientId": "azure-client-id-123",
        |    "clientSecret": "azure-client-secret-xyz789"
        |  }
        |}""".stripMargin
      mockAzureFunctionSuccess(vaultResponse)

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
      result.head.jaasConfig should include("azure-client-id-123")
      result.head.jaasConfig should include("azure-client-secret-xyz789")
    }

    "handle multiple topic directives" in {
      val service = AzureVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val vaultResponse = """{
        |  "credentials": {
        |    "clientId": "azure-client-id-multi",
        |    "clientSecret": "azure-secret-multi"
        |  }
        |}""".stripMargin
      mockAzureFunctionSuccess(vaultResponse)

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
      val service = AzureVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      mockAzureFunctionFailure(401, "Unauthorized")

      val topicDirective = TopicDirective("test-topic", "producer", "test-client", List.empty, Map.empty)
      val directive = BlockStorageDirective("test-loc", "test-evidence", List(topicDirective), "test-bucket")

      val exception = service.fetchSecurityDirectives(directive).failed.futureValue
      exception shouldBe a[VaultAuthException]
      exception.getMessage should include("authentication failed")
    }

    "throw VaultNotFoundException on 404 response" in {
      val service = AzureVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      mockAzureFunctionFailure(404)

      val topicDirective = TopicDirective("test-topic", "producer", "test-client", List.empty, Map.empty)
      val directive = BlockStorageDirective("test-loc", "test-evidence", List(topicDirective), "test-bucket")

      val exception = service.fetchSecurityDirectives(directive).failed.futureValue
      exception shouldBe a[VaultNotFoundException]
      exception.getMessage should include("not found")
    }

    "return empty list for BlockStorageDirective with no topic directives" in {
      val service = AzureVaultService()
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

    "verify Azure Function key is sent in request headers" in {
      val service = AzureVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val vaultResponse = """{
        |  "credentials": {
        |    "clientId": "test-id",
        |    "clientSecret": "test-secret"
        |  }
        |}""".stripMargin

      wireMockServer.stubFor(
        post(urlEqualTo("/api/vault"))
          .withHeader("x-functions-key", equalTo("test-function-key-123"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(vaultResponse)
          )
      )

      val topicDirective = TopicDirective("test-topic", "producer", "test-client", List.empty, Map.empty)
      val directive = BlockStorageDirective("test-loc", "test-evidence", List(topicDirective), "test-bucket")

      service.fetchSecurityDirectives(directive).futureValue

      wireMockServer.verify(
        postRequestedFor(urlEqualTo("/api/vault"))
          .withHeader("x-functions-key", equalTo("test-function-key-123"))
      )
    }
  }

  "AzureVaultServiceIntegrationSpec.retry mechanism" should {

    "retry on 429 rate limit with exponential backoff" in {
      val service = AzureVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // First call returns 429, subsequent calls return success
      wireMockServer.stubFor(
        post(urlEqualTo("/api/vault"))
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
        post(urlEqualTo("/api/vault"))
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
      val service = AzureVaultService()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      wireMockServer.stubFor(
        post(urlEqualTo("/api/vault"))
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
        post(urlEqualTo("/api/vault"))
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
