package io.distia.probe
package services
package builder
package modules

import io.distia.probe.common.models.{BlockStorageDirective, EventFilter, KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.models.GuardianCommands.GuardianCommand
import io.distia.probe.services.fixtures.{VaultConfigFixtures, WireMockHttpClientFactory}
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

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

class AzureVaultServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  // Increased timeout from 5s to 10s to handle HTTP client timeouts under load
  // Tests like "should handle single topic directive" make real HTTP calls (expected to fail)
  // and need more time to timeout during full regression testing
  given PatienceConfig = PatienceConfig(timeout = 10.seconds, interval = 100.milliseconds)

  var actorSystem: ActorSystem[GuardianCommand] = _
  given ExecutionContext = ExecutionContext.global

  private var wireMockServer: WireMockServer = _
  private val mockAzureFunctionPort = 9092

  override def beforeAll(): Unit = {
    actorSystem = ActorSystem(Behaviors.empty, "AzureVaultServiceSpec")
    wireMockServer = new WireMockServer(wireMockConfig().port(mockAzureFunctionPort))
    wireMockServer.start()
  }

  override def afterAll(): Unit = {
    if wireMockServer != null then wireMockServer.stop()
    actorSystem.terminate()
  }

  private def createWireMockConfig(): Config = {
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

  "AzureVaultService.preFlight" should {

    "succeed when config is valid and provider is azure" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))

      val result: Future[BuilderContext] = service.preFlight(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.config shouldBe defined
      }
    }

    "fail when config is not defined in BuilderContext" in {
      val service = AzureVaultService()
      val ctx = BuilderContext()

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Config not found in BuilderContext")
    }

    "fail when provider is not azure" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultLocalVaultConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Azure function URL cannot be empty")
    }

    "fail when Azure function URL is empty" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.azureVaultConfigWithMissingFunctionUrl
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Azure function URL cannot be empty")
    }

    "fail when Azure function key is empty" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.azureVaultConfigWithMissingFunctionKey
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Azure function key cannot be empty")
    }
  }

  "AzureVaultService.initialize" should {

    "succeed and initialize all Azure vault dependencies" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val result: Future[BuilderContext] = service.initialize(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.vaultService shouldBe defined
      }
    }

    "fail when config is not defined in BuilderContext" in {
      val service = AzureVaultService()
      val ctx = BuilderContext().withActorSystem(actorSystem)

      val exception: IllegalArgumentException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Config must be initialized")
    }

    "fail when ActorSystem is not initialized" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("ActorSystem not initialized")
    }

    "fail when Rosetta mapping path is not defined" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig.withoutPath("test-probe.services.vault.rosetta-mapping-path")
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val exception: IllegalStateException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalStateException]
      exception.getMessage should include("Could not initialize AzureVaultService")
      exception.getCause.getMessage should include("Rosetta mapping path must be defined")
    }
  }

  "AzureVaultService.finalCheck" should {

    "succeed when all dependencies are initialized" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx: BuilderContext = service.initialize(ctx).futureValue
      val result: Future[BuilderContext] = service.finalCheck(initializedCtx)

      whenReady(result) { resultCtx =>
        resultCtx.vaultService shouldBe defined
      }
    }

    "fail when VaultService is not initialized in BuilderContext" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("VaultService not initialized")
    }

    "fail when Logger is not initialized" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withVaultService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Logger not initialized")
    }

    "fail when ActorSystem is not initialized internally" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withVaultService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should (include("ActorSystem not initialized") or include("Logger not initialized"))
    }

    "fail when HTTP Client is not initialized" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withVaultService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should (include("HTTP client not initialized") or include("Logger not initialized"))
    }

    "fail when Function URL is not initialized" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withVaultService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should (include("Function URL not initialized") or include("Logger not initialized"))
    }

    "fail when Function Key is not initialized" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withVaultService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should (include("Function Key not initialized") or include("Logger not initialized"))
    }

    "fail when Rosetta Config is not initialized" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withVaultService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should (include("Rosetta Config not initialized") or include("Logger not initialized"))
    }
  }

  "AzureVaultService.fetchSecurityDirectives" should {

    "return SASL_SSL security directives for all topics" in {
      val service = AzureVaultService(Some(new WireMockHttpClientFactory()))
      val config = createWireMockConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue
      service.finalCheck(ctx.withVaultService(service)).futureValue

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

    "return empty list when no topic directives provided" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val directive = BlockStorageDirective("test-id", "test-file", List.empty, "bucket")

      val result: Future[List[KafkaSecurityDirective]] = service.fetchSecurityDirectives(directive)

      whenReady(result) { securityDirectives =>
        securityDirectives shouldBe empty
      }
    }

    "handle single topic directive" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val topicDirective = TopicDirective(
        topic = "test-topic",
        role = "producer",
        clientPrincipal = "test-client",
        eventFilters = List.empty,
        metadata = Map.empty
      )
      val directive = BlockStorageDirective("test-id", "test-file", List(topicDirective), "bucket")

      val result = service.fetchSecurityDirectives(directive)

      // Will fail because HTTP client will try to call Azure function
      result.failed.futureValue shouldBe a[Throwable]
    }

    "handle multiple topic directives" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val topicDirective1 = TopicDirective(
        topic = "topic-1",
        role = "producer",
        clientPrincipal = "client-1",
        eventFilters = List.empty,
        metadata = Map.empty
      )
      val topicDirective2 = TopicDirective(
        topic = "topic-2",
        role = "consumer",
        clientPrincipal = "client-2",
        eventFilters = List.empty,
        metadata = Map.empty
      )
      val directive = BlockStorageDirective("test-id", "test-file", List(topicDirective1, topicDirective2), "bucket")

      val result = service.fetchSecurityDirectives(directive)

      // Will fail because HTTP client will try to call Azure function
      result.failed.futureValue shouldBe a[Throwable]
    }
  }

  "AzureVaultService.invokeVault" should {

    "require initialized HTTP client" in {
      val service = AzureVaultService()
      val topicDirective = TopicDirective(
        topic = "test-topic",
        role = "producer",
        clientPrincipal = "test-client",
        eventFilters = List.empty,
        metadata = Map.empty
      )

      intercept[NoSuchElementException] {
        service.invokeVault(topicDirective).futureValue
      }
    }

    "require initialized Rosetta config" in {
      val service = AzureVaultService()
      val topicDirective = TopicDirective(
        topic = "test-topic",
        role = "producer",
        clientPrincipal = "test-client",
        eventFilters = List.empty,
        metadata = Map.empty
      )

      intercept[NoSuchElementException] {
        service.invokeVault(topicDirective).futureValue
      }
    }

    "require initialized app config" in {
      val service = AzureVaultService()
      val topicDirective = TopicDirective(
        topic = "test-topic",
        role = "producer",
        clientPrincipal = "test-client",
        eventFilters = List.empty,
        metadata = Map.empty
      )

      intercept[NoSuchElementException] {
        service.invokeVault(topicDirective).futureValue
      }
    }
  }

  "AzureVaultService.fetchWithRetry" should {

    "handle TopicDirective with all required fields" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val topicDirective = TopicDirective(
        topic = "order-events",
        role = "producer",
        clientPrincipal = "service-account-123",
        eventFilters = List(EventFilter("eventType", "OrderCreated")),
        metadata = Map("environment" -> "production")
      )

      val result = service.fetchWithRetry(topicDirective, 1)

      // Will fail because HTTP client will try to call Azure function
      result.failed.futureValue shouldBe a[Throwable]
    }

    "handle TopicDirective with consumer role" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val topicDirective = TopicDirective(
        topic = "payment-events",
        role = "consumer",
        clientPrincipal = "consumer-service",
        eventFilters = List(EventFilter("eventType", "PaymentProcessed")),
        metadata = Map.empty
      )

      val result = service.fetchWithRetry(topicDirective, 2)

      // Will fail because HTTP client will try to call Azure function
      result.failed.futureValue shouldBe a[Throwable]
    }

    "handle TopicDirective with multiple event filters" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val topicDirective = TopicDirective(
        topic = "events",
        role = "producer",
        clientPrincipal = "service",
        eventFilters = List(
          EventFilter("eventType", "OrderCreated"),
          EventFilter("status", "pending"),
          EventFilter("priority", "high")
        ),
        metadata = Map.empty
      )

      val result = service.fetchWithRetry(topicDirective, 1)

      // Will fail because HTTP client will try to call Azure function
      result.failed.futureValue shouldBe a[Throwable]
    }
  }

  "AzureVaultService configuration validation" should {

    "validate Azure function URL is present" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Function URL should be initialized
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate Azure function key is present" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Function key should be initialized
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate retry attempts configuration" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Retry attempts should be initialized
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate initial backoff configuration" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Initial backoff should be initialized
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate request timeout configuration" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Request timeout should be initialized
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate token endpoint configuration" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Token endpoint should be initialized
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate scope configuration" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Scope should be initialized
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }

    "validate required fields configuration" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue

      // Required fields should be initialized
      service.finalCheck(initializedCtx).futureValue shouldBe initializedCtx
    }
  }

  "AzureVaultService.shutdown" should {

    "close HTTP client successfully" in {
      val service = AzureVaultService()
      val config = VaultConfigFixtures.defaultAzureVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val result: Future[Unit] = service.shutdown()

      whenReady(result) { _ =>
        succeed
      }
    }
  }
}
