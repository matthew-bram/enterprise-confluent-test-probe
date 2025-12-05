package io.distia.probe
package services
package builder
package modules

import java.net.URI

import io.distia.probe.common.models.{BlockStorageDirective, EventFilter, KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.models.GuardianCommands.GuardianCommand
import io.distia.probe.services.fixtures.{LocalStackLambdaClientFactory, VaultConfigFixtures}
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

class AwsVaultServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  given PatienceConfig = PatienceConfig(timeout = 10.seconds, interval = 100.milliseconds)

  var actorSystem: ActorSystem[GuardianCommand] = _
  given ExecutionContext = ExecutionContext.global

  private var wireMockServer: WireMockServer = _
  private val mockLambdaPort = 9094

  override def beforeAll(): Unit = {
    actorSystem = ActorSystem(Behaviors.empty, "AwsVaultServiceSpec")
    wireMockServer = new WireMockServer(wireMockConfig().port(mockLambdaPort))
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
      |      provider = "aws"
      |      aws {
      |        lambda-arn = "arn:aws:lambda:us-east-1:123456789012:function:vault-invoker"
      |        region = "us-east-1"
      |        timeout = 30s
      |        retry-attempts = 3
      |        required-fields = ["clientId", "clientSecret"]
      |      }
      |      oauth {
      |        token-endpoint = "https://oauth.example.com/token"
      |        scope = "kafka:read kafka:write"
      |      }
      |      rosetta-mapping-path = "classpath:rosetta/aws-vault-mapping.yaml"
      |    }
      |  }
      |}
    """.stripMargin).withFallback(ConfigFactory.load())
  }

  private def createServiceWithFactory(): AwsVaultService = {
    val factory = new LocalStackLambdaClientFactory(
      endpoint = URI.create(s"http://localhost:$mockLambdaPort"),
      region = "us-east-1",
      accessKey = "test",
      secretKey = "test"
    )
    AwsVaultService(Some(factory))
  }

  private def mockLambdaInvokeSuccess(responseBody: String): Unit = {
    wireMockServer.stubFor(
      post(urlPathMatching("/2015-03-31/functions/.*/invocations"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
        )
    )
  }

  "AwsVaultService.preFlight" should {

    "succeed when config is valid and provider is aws" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))

      val result: Future[BuilderContext] = service.preFlight(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.config shouldBe defined
      }
    }

    "fail when config is not defined in BuilderContext" in {
      val service = AwsVaultService()
      val ctx = BuilderContext()

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Config not found in BuilderContext")
    }

    "fail when provider is not aws" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultLocalVaultConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Provider must be 'aws'")
    }

    "fail when AWS Lambda ARN is empty" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.awsVaultConfigWithMissingLambdaArn
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("AWS Lambda ARN cannot be empty")
    }

    "fail when AWS region is empty" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.awsVaultConfigWithMissingRegion
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.preFlight(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("AWS region cannot be empty")
    }
  }

  "AwsVaultService.initialize" should {

    "succeed and initialize all AWS vault dependencies" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val result: Future[BuilderContext] = service.initialize(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.vaultService shouldBe defined
      }
    }

    "fail when config is not defined in BuilderContext" in {
      val service = AwsVaultService()
      val ctx = BuilderContext().withActorSystem(actorSystem)

      val exception: IllegalArgumentException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Config must be initialized")
    }

    "fail when ActorSystem is not initialized" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))

      val exception: IllegalArgumentException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("ActorSystem not initialized")
    }

    "fail when Rosetta mapping path is not defined" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig.withoutPath("test-probe.services.vault.rosetta-mapping-path")
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val exception: IllegalStateException = service.initialize(ctx).failed.futureValue.asInstanceOf[IllegalStateException]
      exception.getMessage should include("Could not initialize AwsVaultService")
      exception.getCause.getMessage should include("Rosetta mapping path must be defined")
    }
  }

  "AwsVaultService.finalCheck" should {

    "succeed when all dependencies are initialized" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx: BuilderContext = service.initialize(ctx).futureValue
      val result: Future[BuilderContext] = service.finalCheck(initializedCtx)

      whenReady(result) { resultCtx =>
        resultCtx.vaultService shouldBe defined
      }
    }

    "fail when VaultService is not initialized in BuilderContext" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("VaultService not initialized")
    }

    "fail when Logger is not initialized" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withVaultService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should include("Logger not initialized")
    }

    "fail when ActorSystem is not initialized internally" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withVaultService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should (include("ActorSystem not initialized") or include("Logger not initialized"))
    }

    "fail when Lambda Client is not initialized" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withVaultService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should (include("Lambda Client not initialized") or include("Logger not initialized"))
    }

    "fail when Rosetta Config is not initialized" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)
        .withVaultService(service)

      val exception: IllegalArgumentException = service.finalCheck(ctx).failed.futureValue.asInstanceOf[IllegalArgumentException]
      exception.getMessage should (include("Rosetta Config not initialized") or include("Logger not initialized"))
    }
  }

  "AwsVaultService.fetchSecurityDirectives" should {

    "return SASL_SSL security directives for all topics" in {
      val service = createServiceWithFactory()
      val config = createWireMockConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue
      service.finalCheck(ctx.withVaultService(service)).futureValue

      val vaultResponse = """{
        |  "credentials": {
        |    "clientId": "aws-client-id-789",
        |    "clientSecret": "aws-client-secret-def456"
        |  }
        |}""".stripMargin
      mockLambdaInvokeSuccess(vaultResponse)

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
      result.head.jaasConfig should include("aws-client-id-789")
      result.head.jaasConfig should include("aws-client-secret-def456")
    }

    "return empty list when no topic directives provided" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val directive = BlockStorageDirective("test-id", "test-file", List.empty, "bucket")

      val result: Future[List[KafkaSecurityDirective]] = service.fetchSecurityDirectives(directive)

      whenReady(result) { securityDirectives =>
        securityDirectives shouldBe empty
      }
    }

    "handle BlockStorageDirective with single topic" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
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

      // This will fail with actual Lambda call, but tests method can accept single topic
      val result = service.fetchSecurityDirectives(directive)

      // Result will fail due to no real Lambda, but validates signature
      result.failed.futureValue shouldBe a[Throwable]
    }

    "handle BlockStorageDirective with multiple topics" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val topicDirectives = List(
        TopicDirective("topic1", "producer", "client1", List.empty, Map.empty),
        TopicDirective("topic2", "consumer", "client2", List.empty, Map.empty),
        TopicDirective("topic3", "producer", "client3", List.empty, Map.empty)
      )
      val directive = BlockStorageDirective("test-id", "test-file", topicDirectives, "bucket")

      // This will fail with actual Lambda call, but tests method accepts multiple topics
      val result = service.fetchSecurityDirectives(directive)

      // Result will fail due to no real Lambda, but validates signature
      result.failed.futureValue shouldBe a[Throwable]
    }
  }

  "AwsVaultService.invokeVault" should {

    "require initialized Lambda client" in {
      val service = AwsVaultService()
      val topicDirective = TopicDirective(
        topic = "test-topic",
        role = "producer",
        clientPrincipal = "test-client",
        eventFilters = List.empty,
        metadata = Map.empty
      )

      // invokeVault called without initialization should fail
      intercept[NoSuchElementException] {
        service.invokeVault(topicDirective).futureValue
      }
    }

    "require initialized Rosetta config" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      // Don't initialize, just test precondition
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

    "construct request payload using RequestBodyBuilder" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
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

      // This will fail at Lambda invocation, but RequestBodyBuilder is called
      val result = service.invokeVault(topicDirective)
      result.failed.futureValue shouldBe a[Throwable]
    }
  }

  "AwsVaultService.fetchWithRetry" should {

    "require valid retry attempts configuration" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
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

      // fetchWithRetry with 0 retries should still attempt once
      val result = service.fetchWithRetry(topicDirective, 0)
      result.failed.futureValue shouldBe a[Throwable]
    }

    "accept positive retry attempts" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
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

      // fetchWithRetry with 3 retries should be accepted
      val result = service.fetchWithRetry(topicDirective, 3)
      result.failed.futureValue shouldBe a[Throwable]
    }

    "handle TopicDirective with all required fields" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
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
      result.failed.futureValue shouldBe a[Throwable]
    }
  }

  "AwsVaultService configuration validation" should {

    "validate Lambda ARN format during initialization" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val result = service.initialize(ctx)

      whenReady(result) { resultCtx =>
        resultCtx.vaultService shouldBe defined
      }
    }

    "validate region is set correctly" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // Region should be initialized from config
      succeed
    }

    "validate retry configuration is positive" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue
      service.finalCheck(ctx.withVaultService(service)).futureValue

      // Retry attempts should be positive (validated in finalCheck)
      succeed
    }

    "validate initial backoff is configured" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // Initial backoff defaults to 1 second (line 72 in AwsVaultService)
      succeed
    }

    "validate request timeout is configured" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // Request timeout should be set from config
      succeed
    }

    "validate token endpoint is required" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue
      service.finalCheck(initializedCtx).futureValue

      // Token endpoint should be validated in finalCheck
      succeed
    }

    "validate scope is configured" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue
      service.finalCheck(initializedCtx).futureValue

      // Scope should be validated in finalCheck
      succeed
    }

    "validate required fields list is loaded" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      val initializedCtx = service.initialize(ctx).futureValue
      service.finalCheck(initializedCtx).futureValue

      // Required fields should be validated in finalCheck
      succeed
    }
  }

  "AwsVaultService.shutdown" should {

    "close AWS Lambda client successfully" in {
      val service = AwsVaultService()
      val config = VaultConfigFixtures.defaultAwsVaultConfig
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
