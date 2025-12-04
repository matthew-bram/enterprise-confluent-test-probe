package io.distia.probe
package services
package builder
package modules

import java.net.URI

import io.distia.probe.common.exceptions.{VaultAuthException, VaultNotFoundException}
import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective, SecurityProtocol, TopicDirective}
import io.distia.probe.core.builder.BuilderContext
import io.distia.probe.core.models.GuardianCommands.GuardianCommand
import io.distia.probe.services.fixtures.LocalStackLambdaClientFactory
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

class AwsVaultServiceIntegrationSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  given PatienceConfig = PatienceConfig(timeout = 10.seconds, interval = 500.milliseconds)

  var actorSystem: ActorSystem[GuardianCommand] = _
  given ExecutionContext = ExecutionContext.global

  private var wireMockServer: WireMockServer = _
  private val mockLambdaPort = 9090

  override def beforeAll(): Unit = {
    actorSystem = ActorSystem(Behaviors.empty, "AwsVaultServiceIntegrationSpec")

    // Start WireMock server to mock AWS Lambda API
    wireMockServer = new WireMockServer(wireMockConfig().port(mockLambdaPort))
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

  private def mockLambdaInvokeFailure(statusCode: Int, errorMessage: String = ""): Unit = {
    val body = if (errorMessage.nonEmpty) s"""{"message":"$errorMessage"}""" else ""
    wireMockServer.stubFor(
      post(urlPathMatching("/2015-03-31/functions/.*/invocations"))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withHeader("Content-Type", "application/json")
            .withBody(body)
        )
    )
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

  "AwsVaultServiceIntegrationSpec.fetchSecurityDirectives" should {

    "successfully fetch vault credentials and return SASL_SSL security directives" in {
      val service = createServiceWithFactory()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue
      service.finalCheck(ctx.withVaultService(service)).futureValue

      // Mock successful Lambda response with vault credentials
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

    "handle multiple topic directives" in {
      val service = createServiceWithFactory()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      val vaultResponse = """{
        |  "credentials": {
        |    "clientId": "aws-client-id-789",
        |    "clientSecret": "aws-client-secret-def456"
        |  }
        |}""".stripMargin
      mockLambdaInvokeSuccess(vaultResponse)

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
      val service = createServiceWithFactory()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      mockLambdaInvokeFailure(401, "Unauthorized")

      val topicDirective = TopicDirective("test-topic", "producer", "test-client", List.empty, Map.empty)
      val directive = BlockStorageDirective("test-loc", "test-evidence", List(topicDirective), "test-bucket")

      val exception = service.fetchSecurityDirectives(directive).failed.futureValue
      exception shouldBe a[VaultAuthException]
      exception.getMessage should include("authentication failed")
    }

    "throw VaultNotFoundException on 404 response" in {
      val service = createServiceWithFactory()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      mockLambdaInvokeFailure(404)

      val topicDirective = TopicDirective("test-topic", "producer", "test-client", List.empty, Map.empty)
      val directive = BlockStorageDirective("test-loc", "test-evidence", List(topicDirective), "test-bucket")

      val exception = service.fetchSecurityDirectives(directive).failed.futureValue
      exception shouldBe a[VaultNotFoundException]
      exception.getMessage should include("not found")
    }

    "return empty list for BlockStorageDirective with no topic directives" in {
      val service = createServiceWithFactory()
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
  }

  "AwsVaultServiceIntegrationSpec.retry mechanism" should {

    "retry on 429 rate limit with exponential backoff" in {
      val service = createServiceWithFactory()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      // First call returns 429, subsequent calls return success
      wireMockServer.stubFor(
        post(urlPathMatching("/2015-03-31/functions/.*/invocations"))
          .inScenario("Rate Limit Scenario")
          .whenScenarioStateIs("Started")
          .willReturn(
            aResponse()
              .withStatus(429)
              .withBody("""{"message":"Rate limit exceeded"}""")
          )
          .willSetStateTo("First Retry")
      )

      wireMockServer.stubFor(
        post(urlPathMatching("/2015-03-31/functions/.*/invocations"))
          .inScenario("Rate Limit Scenario")
          .whenScenarioStateIs("First Retry")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""{
                |  "credentials": {
                |    "clientId": "aws-client-id-789",
                |    "clientSecret": "aws-client-secret-def456"
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
      val service = createServiceWithFactory()
      val config = createTestConfig()
      val ctx = BuilderContext(config = Some(config))
        .withActorSystem(actorSystem)

      service.initialize(ctx).futureValue

      wireMockServer.stubFor(
        post(urlPathMatching("/2015-03-31/functions/.*/invocations"))
          .inScenario("Unavailable Scenario")
          .whenScenarioStateIs("Started")
          .willReturn(
            aResponse()
              .withStatus(503)
              .withBody("""{"message":"Service temporarily unavailable"}""")
          )
          .willSetStateTo("Recovered")
      )

      wireMockServer.stubFor(
        post(urlPathMatching("/2015-03-31/functions/.*/invocations"))
          .inScenario("Unavailable Scenario")
          .whenScenarioStateIs("Recovered")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody("""{
                |  "credentials": {
                |    "clientId": "aws-client-id-789",
                |    "clientSecret": "aws-client-secret-def456"
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
