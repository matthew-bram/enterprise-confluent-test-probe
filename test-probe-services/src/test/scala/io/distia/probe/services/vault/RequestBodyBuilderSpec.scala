package io.distia.probe
package services
package vault

import io.distia.probe.common.exceptions.VaultMappingException
import io.distia.probe.common.models.TopicDirective
import io.distia.probe.services.fixtures.RosettaConfigFixtures
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import io.circe.syntax.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RequestBodyBuilderSpec extends AnyWordSpec with Matchers {

  val sampleTopicDirective: TopicDirective = TopicDirective(
    topic = "test-topic",
    role = "PRODUCER",
    clientPrincipal = "test-principal",
    eventFilters = List.empty,
    metadata = Map(
      "region" -> "us-east-1",
      "cluster" -> "kafka-cluster-1",
      "environment" -> "production"
    )
  )

  val sampleAppConfig: Config = ConfigFactory.parseString(
    """
      |request-params {
      |  environment = "production"
      |  requestId = "req-12345"
      |  apiKey = "api-key-xyz"
      |}
      |""".stripMargin
  )

  "RequestBodyBuilder.build" should {

    "successfully build request body with all variable types" in {
      val rosettaConfig = RosettaConfigFixtures.rosettaConfigWithComplexRequestTemplate
      val result: Either[VaultMappingException, String] = RequestBodyBuilder.build(
        sampleTopicDirective,
        rosettaConfig,
        sampleAppConfig
      )

      result shouldBe a[Right[_, _]]
      val requestBody: String = result.getOrElse(fail("Expected Right but got Left"))
      requestBody should include("test-topic")
      requestBody should include("PRODUCER")
      requestBody should include("test-principal")
      requestBody should include("production")
      requestBody should include("us-east-1")
      requestBody should include("kafka-cluster-1")
      requestBody should include("req-12345")
    }

    "fail when request template is missing in Rosetta config" in {
      val rosettaConfig = RosettaConfigFixtures.defaultRosettaConfig
      val result: Either[VaultMappingException, String] = RequestBodyBuilder.build(
        sampleTopicDirective,
        rosettaConfig,
        sampleAppConfig
      )

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("Request template is required")
    }
  }

  "RequestBodyBuilder.substituteVariables" should {

    "substitute config path variables ({{$^config.path}})" in {
      val template: Json = Json.obj(
        "environment" -> "{{$^request-params.environment}}".asJson,
        "apiKey" -> "{{$^request-params.apiKey}}".asJson
      )

      val result: Either[VaultMappingException, Json] = RequestBodyBuilder.substituteVariables(
        template,
        sampleTopicDirective,
        sampleAppConfig
      )

      result shouldBe a[Right[_, _]]
      val substituted: Json = result.getOrElse(fail("Expected Right but got Left"))
      substituted.hcursor.downField("environment").as[String] shouldBe Right("production")
      substituted.hcursor.downField("apiKey").as[String] shouldBe Right("api-key-xyz")
    }

    "substitute metadata key variables ({{'key'}})" in {
      val template: Json = Json.obj(
        "region" -> "{{'region'}}".asJson,
        "cluster" -> "{{'cluster'}}".asJson
      )

      val result: Either[VaultMappingException, Json] = RequestBodyBuilder.substituteVariables(
        template,
        sampleTopicDirective,
        sampleAppConfig
      )

      result shouldBe a[Right[_, _]]
      val substituted: Json = result.getOrElse(fail("Expected Right but got Left"))
      substituted.hcursor.downField("region").as[String] shouldBe Right("us-east-1")
      substituted.hcursor.downField("cluster").as[String] shouldBe Right("kafka-cluster-1")
    }

    "substitute directive field variables ({{fieldName}})" in {
      val template: Json = Json.obj(
        "topic" -> "{{topic}}".asJson,
        "role" -> "{{role}}".asJson,
        "clientPrincipal" -> "{{clientPrincipal}}".asJson
      )

      val result: Either[VaultMappingException, Json] = RequestBodyBuilder.substituteVariables(
        template,
        sampleTopicDirective,
        sampleAppConfig
      )

      result shouldBe a[Right[_, _]]
      val substituted: Json = result.getOrElse(fail("Expected Right but got Left"))
      substituted.hcursor.downField("topic").as[String] shouldBe Right("test-topic")
      substituted.hcursor.downField("role").as[String] shouldBe Right("PRODUCER")
      substituted.hcursor.downField("clientPrincipal").as[String] shouldBe Right("test-principal")
    }

    "substitute mixed variable types in nested objects" in {
      val template: Json = Json.obj(
        "request" -> Json.obj(
          "topic" -> "{{topic}}".asJson,
          "environment" -> "{{$^request-params.environment}}".asJson
        ),
        "metadata" -> Json.obj(
          "region" -> "{{'region'}}".asJson
        )
      )

      val result: Either[VaultMappingException, Json] = RequestBodyBuilder.substituteVariables(
        template,
        sampleTopicDirective,
        sampleAppConfig
      )

      result shouldBe a[Right[_, _]]
      val substituted: Json = result.getOrElse(fail("Expected Right but got Left"))
      substituted.hcursor.downField("request").downField("topic").as[String] shouldBe Right("test-topic")
      substituted.hcursor.downField("request").downField("environment").as[String] shouldBe Right("production")
      substituted.hcursor.downField("metadata").downField("region").as[String] shouldBe Right("us-east-1")
    }

    "substitute variables in arrays" in {
      val template: Json = Json.obj(
        "topics" -> Json.arr("{{topic}}".asJson, "another-topic".asJson)
      )

      val result: Either[VaultMappingException, Json] = RequestBodyBuilder.substituteVariables(
        template,
        sampleTopicDirective,
        sampleAppConfig
      )

      result shouldBe a[Right[_, _]]
      val substituted: Json = result.getOrElse(fail("Expected Right but got Left"))
      val topics = substituted.hcursor.downField("topics").as[List[String]]
      topics shouldBe Right(List("test-topic", "another-topic"))
    }

    "preserve non-string JSON types (numbers, booleans, null)" in {
      val template: Json = Json.obj(
        "count" -> 42.asJson,
        "enabled" -> true.asJson,
        "optional" -> Json.Null
      )

      val result: Either[VaultMappingException, Json] = RequestBodyBuilder.substituteVariables(
        template,
        sampleTopicDirective,
        sampleAppConfig
      )

      result shouldBe a[Right[_, _]]
      val substituted: Json = result.getOrElse(fail("Expected Right but got Left"))
      substituted.hcursor.downField("count").as[Int] shouldBe Right(42)
      substituted.hcursor.downField("enabled").as[Boolean] shouldBe Right(true)
      substituted.hcursor.downField("optional").as[Option[String]] shouldBe Right(None)
    }

    "fail when config path not found" in {
      val template: Json = Json.obj(
        "value" -> "{{$^request-params.nonexistent}}".asJson
      )

      val result: Either[VaultMappingException, Json] = RequestBodyBuilder.substituteVariables(
        template,
        sampleTopicDirective,
        sampleAppConfig
      )

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("Config path not found")
      exception.getMessage should include("nonexistent")
    }

    "fail when metadata key not found" in {
      val template: Json = Json.obj(
        "value" -> "{{'nonexistentKey'}}".asJson
      )

      val result: Either[VaultMappingException, Json] = RequestBodyBuilder.substituteVariables(
        template,
        sampleTopicDirective,
        sampleAppConfig
      )

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("Metadata key")
      exception.getMessage should include("nonexistentKey")
    }

    "fail when directive field is invalid" in {
      val template: Json = Json.obj(
        "value" -> "{{invalidField}}".asJson
      )

      val result: Either[VaultMappingException, Json] = RequestBodyBuilder.substituteVariables(
        template,
        sampleTopicDirective,
        sampleAppConfig
      )

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("Unknown TopicDirective field")
    }

    "accumulate multiple errors when multiple substitutions fail" in {
      val template: Json = Json.obj(
        "value1" -> "{{$^request-params.missing1}}".asJson,
        "value2" -> "{{'missingKey'}}".asJson,
        "value3" -> "{{invalidField}}".asJson
      )

      val result: Either[VaultMappingException, Json] = RequestBodyBuilder.substituteVariables(
        template,
        sampleTopicDirective,
        sampleAppConfig
      )

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("Multiple template errors")
    }
  }

  "RequestBodyBuilder.validateConfigPath" should {

    "accept valid config path with request-params prefix" in {
      val result: Either[VaultMappingException, Unit] = RequestBodyBuilder.validateConfigPath("request-params.environment")

      result shouldBe Right(())
    }

    "accept valid config path with nested sub-paths" in {
      val result: Either[VaultMappingException, Unit] = RequestBodyBuilder.validateConfigPath("request-params.oauth.token-endpoint")

      result shouldBe Right(())
    }

    "accept config path with alphanumeric, dots, underscores, and hyphens" in {
      val result: Either[VaultMappingException, Unit] = RequestBodyBuilder.validateConfigPath("request-params.oauth_token-endpoint.url123")

      result shouldBe Right(())
    }

    "reject config path without request-params prefix" in {
      val result: Either[VaultMappingException, Unit] = RequestBodyBuilder.validateConfigPath("oauth.token-endpoint")

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("must start with 'request-params.'")
    }

    "reject config path that is exactly 'request-params.' without sub-path" in {
      val result: Either[VaultMappingException, Unit] = RequestBodyBuilder.validateConfigPath("request-params.")

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("must have at least one sub-path")
    }

    "reject config path with invalid characters" in {
      val result: Either[VaultMappingException, Unit] = RequestBodyBuilder.validateConfigPath("request-params.oauth@token")

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("alphanumeric characters, dots, underscores, and hyphens only")
    }

    "reject config path with spaces" in {
      val result: Either[VaultMappingException, Unit] = RequestBodyBuilder.validateConfigPath("request-params.oauth token")

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("alphanumeric characters, dots, underscores, and hyphens only")
    }
  }

  "RequestBodyBuilder.resolveConfigPath" should {

    "successfully resolve existing config path" in {
      val result: Either[VaultMappingException, String] = RequestBodyBuilder.resolveConfigPath(
        "request-params.environment",
        sampleAppConfig,
        "test-topic"
      )

      result shouldBe Right("production")
    }

    "fail when config path does not exist" in {
      val result: Either[VaultMappingException, String] = RequestBodyBuilder.resolveConfigPath(
        "request-params.nonexistent",
        sampleAppConfig,
        "test-topic"
      )

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("Config path not found")
      exception.getMessage should include("nonexistent")
    }

    "fail with invalid config path format" in {
      val result: Either[VaultMappingException, String] = RequestBodyBuilder.resolveConfigPath(
        "oauth.token",
        sampleAppConfig,
        "test-topic"
      )

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("must start with 'request-params.'")
    }
  }

  "RequestBodyBuilder.resolveMetadataKey" should {

    "successfully resolve existing metadata key" in {
      val result: Either[VaultMappingException, String] = RequestBodyBuilder.resolveMetadataKey(
        "region",
        sampleTopicDirective
      )

      result shouldBe Right("us-east-1")
    }

    "fail when metadata key does not exist" in {
      val result: Either[VaultMappingException, String] = RequestBodyBuilder.resolveMetadataKey(
        "nonexistentKey",
        sampleTopicDirective
      )

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("Metadata key 'nonexistentKey' not found")
      exception.getMessage should include("Available keys")
    }
  }

  "RequestBodyBuilder.resolveDirectiveField" should {

    "successfully resolve topic field" in {
      val result: Either[VaultMappingException, String] = RequestBodyBuilder.resolveDirectiveField(
        "topic",
        sampleTopicDirective
      )

      result shouldBe Right("test-topic")
    }

    "successfully resolve role field" in {
      val result: Either[VaultMappingException, String] = RequestBodyBuilder.resolveDirectiveField(
        "role",
        sampleTopicDirective
      )

      result shouldBe Right("PRODUCER")
    }

    "successfully resolve clientPrincipal field" in {
      val result: Either[VaultMappingException, String] = RequestBodyBuilder.resolveDirectiveField(
        "clientPrincipal",
        sampleTopicDirective
      )

      result shouldBe Right("test-principal")
    }

    "fail when directive field name is invalid" in {
      val result: Either[VaultMappingException, String] = RequestBodyBuilder.resolveDirectiveField(
        "invalidField",
        sampleTopicDirective
      )

      result shouldBe a[Left[_, _]]
      val exception: VaultMappingException = result.left.getOrElse(fail("Expected Left but got Right"))
      exception.getMessage should include("Unknown TopicDirective field")
      exception.getMessage should include("invalidField")
    }
  }

  "RequestBodyBuilder.validate" should {

    "validate correct template with all variables resolvable" in {
      val template: Json = Json.obj(
        "topic" -> "{{topic}}".asJson,
        "environment" -> "{{$^request-params.environment}}".asJson,
        "region" -> "{{'region'}}".asJson
      )

      val result: Either[VaultMappingException, Unit] = RequestBodyBuilder.validate(
        template,
        sampleTopicDirective,
        sampleAppConfig
      )

      result shouldBe Right(())
    }

    "fail validation when template has unresolvable variables" in {
      val template: Json = Json.obj(
        "topic" -> "{{topic}}".asJson,
        "invalid" -> "{{$^request-params.nonexistent}}".asJson
      )

      val result: Either[VaultMappingException, Unit] = RequestBodyBuilder.validate(
        template,
        sampleTopicDirective,
        sampleAppConfig
      )

      result shouldBe a[Left[_, _]]
    }
  }
}
