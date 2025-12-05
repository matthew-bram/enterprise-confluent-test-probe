package io.distia
package probe
package common
package rosetta

import io.circe.Json
import io.circe.parser._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

import java.nio.file.{Files, Path}

/**
 * Unit tests for RosettaConfig
 * Tests TransformationConfig.toTransform, RosettaFieldMapping, RosettaConfig,
 * and configuration loading/parsing
 */
class RosettaConfigSpec extends AnyWordSpec with Matchers with EitherValues {

  // Alias for the outer object to avoid shadowing by the inner case class
  private val ConfigLoader = RosettaConfig
  import RosettaConfig._

  // ========== TRANSFORMATIONCONFIG TESTS ==========

  "TransformationConfig" when {

    "toTransform method" should {

      "convert 'base64decode' to Base64Decode" in {
        val config = TransformationConfig("base64decode")
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.Base64Decode)
      }

      "convert 'base64Decode' (case insensitive) to Base64Decode" in {
        val config = TransformationConfig("Base64Decode")
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.Base64Decode)
      }

      "convert 'BASE64DECODE' to Base64Decode" in {
        val config = TransformationConfig("BASE64DECODE")
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.Base64Decode)
      }

      "convert 'base64encode' to Base64Encode" in {
        val config = TransformationConfig("base64encode")
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.Base64Encode)
      }

      "convert 'Base64Encode' to Base64Encode" in {
        val config = TransformationConfig("Base64Encode")
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.Base64Encode)
      }

      "convert 'toupper' to ToUpper" in {
        val config = TransformationConfig("toupper")
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.ToUpper)
      }

      "convert 'ToUpper' to ToUpper" in {
        val config = TransformationConfig("ToUpper")
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.ToUpper)
      }

      "convert 'tolower' to ToLower" in {
        val config = TransformationConfig("tolower")
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.ToLower)
      }

      "convert 'ToLower' to ToLower" in {
        val config = TransformationConfig("ToLower")
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.ToLower)
      }

      "convert 'prefix' with value to Prefix" in {
        val config = TransformationConfig("prefix", value = Some("pre_"))
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.Prefix("pre_"))
      }

      "return error for 'prefix' without value" in {
        val config = TransformationConfig("prefix")
        val result = config.toTransform

        result.isLeft shouldBe true
        result.left.value should include("prefix")
        result.left.value should include("value")
      }

      "convert 'suffix' with value to Suffix" in {
        val config = TransformationConfig("suffix", value = Some("_suffix"))
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.Suffix("_suffix"))
      }

      "return error for 'suffix' without value" in {
        val config = TransformationConfig("suffix")
        val result = config.toTransform

        result.isLeft shouldBe true
        result.left.value should include("suffix")
        result.left.value should include("value")
      }

      "convert 'concat' with default separator" in {
        val config = TransformationConfig("concat")
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.Concat(""))
      }

      "convert 'concat' with custom separator" in {
        val config = TransformationConfig("concat", separator = Some(","))
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.Concat(","))
      }

      "convert 'Concat' with separator" in {
        val config = TransformationConfig("Concat", separator = Some("|"))
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.Concat("|"))
      }

      "convert 'default' with value to Default" in {
        val config = TransformationConfig("default", value = Some("fallback"))
        val result = config.toTransform

        result shouldBe Right(RosettaTransformations.Default("fallback"))
      }

      "return error for 'default' without value" in {
        val config = TransformationConfig("default")
        val result = config.toTransform

        result.isLeft shouldBe true
        result.left.value should include("default")
        result.left.value should include("value")
      }

      "return error for unknown transformation type" in {
        val config = TransformationConfig("unknownTransform")
        val result = config.toTransform

        result.isLeft shouldBe true
        result.left.value should include("Unknown transformation type")
        // Note: the type is lowercased before matching
        result.left.value should include("unknowntransform")
      }

      "return error for empty transformation type" in {
        val config = TransformationConfig("")
        val result = config.toTransform

        result.isLeft shouldBe true
        result.left.value should include("Unknown transformation type")
      }
    }

    "constructed with defaults" should {

      "have None for value" in {
        val config = TransformationConfig("toupper")

        config.value shouldBe None
      }

      "have None for separator" in {
        val config = TransformationConfig("toupper")

        config.separator shouldBe None
      }
    }
  }

  // ========== ROSETTAFIELDMAPPING TESTS ==========

  "RosettaFieldMapping" when {

    "constructed with required fields" should {

      "store targetField correctly" in {
        val mapping = RosettaFieldMapping("clientId", "$.oauth.client_id")

        mapping.targetField shouldBe "clientId"
      }

      "store sourcePath correctly" in {
        val mapping = RosettaFieldMapping("clientId", "$.oauth.client_id")

        mapping.sourcePath shouldBe "$.oauth.client_id"
      }

      "default transformations to empty list" in {
        val mapping = RosettaFieldMapping("clientId", "$.oauth.client_id")

        mapping.transformations shouldBe Nil
      }

      "default defaultValue to None" in {
        val mapping = RosettaFieldMapping("clientId", "$.oauth.client_id")

        mapping.defaultValue shouldBe None
      }
    }

    "constructed with all fields" should {

      "store transformations correctly" in {
        val transformations = List(
          TransformationConfig("base64decode"),
          TransformationConfig("toupper")
        )
        val mapping = RosettaFieldMapping(
          "clientSecret",
          "$.oauth.secret",
          transformations
        )

        mapping.transformations shouldBe transformations
        mapping.transformations.size shouldBe 2
      }

      "store defaultValue correctly" in {
        val mapping = RosettaFieldMapping(
          "role",
          "$.kafka.role",
          Nil,
          Some("PRODUCER")
        )

        mapping.defaultValue shouldBe Some("PRODUCER")
      }
    }

    "used with collections" should {

      "support equality comparison" in {
        val mapping1 = RosettaFieldMapping("field1", "$.path1")
        val mapping2 = RosettaFieldMapping("field1", "$.path1")

        mapping1 shouldBe mapping2
      }

      "support copy with modifications" in {
        val original = RosettaFieldMapping("field", "$.path")
        val modified = original.copy(defaultValue = Some("default"))

        modified.targetField shouldBe "field"
        modified.defaultValue shouldBe Some("default")
      }
    }
  }

  // ========== ROSETTACONFIG TESTS ==========

  "RosettaConfig" when {

    "constructed" should {

      "store mappings correctly" in {
        val mappings = List(
          RosettaFieldMapping("clientId", "$.oauth.client_id"),
          RosettaFieldMapping("clientSecret", "$.oauth.client_secret")
        )
        val config = RosettaConfig(mappings)

        config.mappings shouldBe mappings
        config.mappings.size shouldBe 2
      }

      "default requestTemplate to None" in {
        val config = RosettaConfig(List.empty)

        config.requestTemplate shouldBe None
      }

      "store requestTemplate correctly" in {
        val template = parse("""{"topic": "{{topic}}"}""").getOrElse(Json.Null)
        val config = RosettaConfig(List.empty, Some(template))

        config.requestTemplate shouldBe Some(template)
      }
    }

    "getMapping" should {

      "return Some for existing field" in {
        val mappings = List(
          RosettaFieldMapping("clientId", "$.oauth.client_id"),
          RosettaFieldMapping("clientSecret", "$.oauth.client_secret")
        )
        val config = RosettaConfig(mappings)

        val result = config.getMapping("clientId")

        result shouldBe defined
        result.get.sourcePath shouldBe "$.oauth.client_id"
      }

      "return None for non-existing field" in {
        val config = RosettaConfig(List(
          RosettaFieldMapping("clientId", "$.oauth.client_id")
        ))

        val result = config.getMapping("unknownField")

        result shouldBe None
      }

      "return first matching mapping when duplicates exist" in {
        val mappings = List(
          RosettaFieldMapping("field", "$.first.path"),
          RosettaFieldMapping("field", "$.second.path")
        )
        val config = RosettaConfig(mappings)

        val result = config.getMapping("field")

        result shouldBe defined
        result.get.sourcePath shouldBe "$.first.path"
      }
    }

    "validateRequiredFields" should {

      "return Right(()) when all required fields are present" in {
        val config = RosettaConfig(List(
          RosettaFieldMapping("clientId", "$.oauth.client_id"),
          RosettaFieldMapping("clientSecret", "$.oauth.client_secret"),
          RosettaFieldMapping("topic", "$.kafka.topic")
        ))

        val result = config.validateRequiredFields(List("clientId", "clientSecret", "topic"))

        result shouldBe Right(())
      }

      "return Left with missing fields when some are missing" in {
        val config = RosettaConfig(List(
          RosettaFieldMapping("clientId", "$.oauth.client_id")
        ))

        val result = config.validateRequiredFields(List("clientId", "clientSecret", "topic"))

        result.isLeft shouldBe true
        result.left.value should contain("clientSecret")
        result.left.value should contain("topic")
        result.left.value should not contain "clientId"
      }

      "return Right(()) for empty required fields" in {
        val config = RosettaConfig(List.empty)

        val result = config.validateRequiredFields(List.empty)

        result shouldBe Right(())
      }

      "return Left with all fields when config is empty" in {
        val config = RosettaConfig(List.empty)

        val result = config.validateRequiredFields(List("field1", "field2"))

        result.isLeft shouldBe true
        result.left.value should contain("field1")
        result.left.value should contain("field2")
      }
    }
  }

  // ========== LOAD AND PARSE TESTS ==========

  "ConfigLoader.load" when {

    "loading from filesystem" should {

      "load valid YAML file" in {
        val tempFile = Files.createTempFile("rosetta-test", ".yaml")
        try {
          Files.writeString(tempFile, """
            |mappings:
            |  - targetField: clientId
            |    sourcePath: $.oauth.client_id
            |  - targetField: clientSecret
            |    sourcePath: $.oauth.secret
            |    transformations:
            |      - type: base64decode
            """.stripMargin)

          val result = ConfigLoader.load(tempFile.toString)

          result.isRight shouldBe true
          result.value.mappings.size shouldBe 2
          result.value.mappings.head.targetField shouldBe "clientId"
        } finally {
          Files.deleteIfExists(tempFile)
        }
      }

      "load valid JSON file" in {
        val tempFile = Files.createTempFile("rosetta-test", ".json")
        try {
          Files.writeString(tempFile, """
            {
              "mappings": [
                {"targetField": "clientId", "sourcePath": "$.oauth.client_id"}
              ]
            }
            """)

          val result = ConfigLoader.load(tempFile.toString)

          result.isRight shouldBe true
          result.value.mappings.size shouldBe 1
        } finally {
          Files.deleteIfExists(tempFile)
        }
      }

      "return error for non-existent file" in {
        val result = ConfigLoader.load("/non/existent/path/rosetta.yaml")

        result.isLeft shouldBe true
        result.left.value.getMessage should include("not found")
      }

      "return error for invalid YAML content" in {
        val tempFile = Files.createTempFile("rosetta-test", ".yaml")
        try {
          Files.writeString(tempFile, "invalid: [yaml: content: {")

          val result = ConfigLoader.load(tempFile.toString)

          result.isLeft shouldBe true
        } finally {
          Files.deleteIfExists(tempFile)
        }
      }

      "return error for invalid JSON content" in {
        val tempFile = Files.createTempFile("rosetta-test", ".json")
        try {
          Files.writeString(tempFile, "{invalid json}")

          val result = ConfigLoader.load(tempFile.toString)

          result.isLeft shouldBe true
        } finally {
          Files.deleteIfExists(tempFile)
        }
      }
    }

    "loading from classpath" should {

      "return error for non-existent classpath resource" in {
        val result = ConfigLoader.load("classpath:non/existent/resource.yaml")

        result.isLeft shouldBe true
        result.left.value.getMessage should include("not found")
      }
    }
  }

  "ConfigLoader.loadFileContent" when {

    "loading from filesystem" should {

      "load file content successfully" in {
        val tempFile = Files.createTempFile("test", ".txt")
        try {
          Files.writeString(tempFile, "test content")

          val result = ConfigLoader.loadFileContent(tempFile.toString)

          result shouldBe Right("test content")
        } finally {
          Files.deleteIfExists(tempFile)
        }
      }

      "return error for non-existent file" in {
        val result = ConfigLoader.loadFileContent("/non/existent/file.txt")

        result.isLeft shouldBe true
      }
    }

    "loading from classpath" should {

      "return error for non-existent classpath resource" in {
        val result = ConfigLoader.loadFileContent("classpath:non/existent.txt")

        result.isLeft shouldBe true
        result.left.value.getMessage should include("not found")
      }
    }
  }

  "ConfigLoader.fromJson" when {

    "parsing valid JSON" should {

      "decode simple config" in {
        val json = parse("""
          {
            "mappings": [
              {"targetField": "field1", "sourcePath": "$.path1"}
            ]
          }
        """).getOrElse(Json.Null)

        val result = ConfigLoader.fromJson(json)

        result.isRight shouldBe true
        result.value.mappings.size shouldBe 1
      }

      "decode config with transformations" in {
        val json = parse("""
          {
            "mappings": [
              {
                "targetField": "secret",
                "sourcePath": "$.encoded",
                "transformations": [
                  {"type": "base64decode"},
                  {"type": "toupper"}
                ]
              }
            ]
          }
        """).getOrElse(Json.Null)

        val result = ConfigLoader.fromJson(json)

        result.isRight shouldBe true
        result.value.mappings.head.transformations.size shouldBe 2
      }

      "decode config with default values" in {
        val json = parse("""
          {
            "mappings": [
              {
                "targetField": "role",
                "sourcePath": "$.role",
                "defaultValue": "DEFAULT"
              }
            ]
          }
        """).getOrElse(Json.Null)

        val result = ConfigLoader.fromJson(json)

        result.isRight shouldBe true
        result.value.mappings.head.defaultValue shouldBe Some("DEFAULT")
      }

      "decode config with request template" in {
        val json = parse("""
          {
            "mappings": [],
            "request-template": {"topic": "test-topic"}
          }
        """).getOrElse(Json.Null)

        val result = ConfigLoader.fromJson(json)

        result.isRight shouldBe true
        result.value.requestTemplate shouldBe defined
      }
    }

    "parsing invalid JSON" should {

      "return error for missing mappings field" in {
        val json = parse("""{"other": "data"}""").getOrElse(Json.Null)

        val result = ConfigLoader.fromJson(json)

        result.isLeft shouldBe true
      }

      "return error for invalid structure" in {
        val json = parse("""{"mappings": "not-an-array"}""").getOrElse(Json.Null)

        val result = ConfigLoader.fromJson(json)

        result.isLeft shouldBe true
      }
    }
  }

  // ========== CIRCE DECODER TESTS ==========

  "Circe decoders" when {

    "decoding TransformationConfig" should {

      "decode type only" in {
        val json = parse("""{"type": "toupper"}""").getOrElse(Json.Null)
        val result = json.as[TransformationConfig]

        result.isRight shouldBe true
        result.value.transformType shouldBe "toupper"
        result.value.value shouldBe None
      }

      "decode type with value" in {
        val json = parse("""{"type": "prefix", "value": "test_"}""").getOrElse(Json.Null)
        val result = json.as[TransformationConfig]

        result.isRight shouldBe true
        result.value.transformType shouldBe "prefix"
        result.value.value shouldBe Some("test_")
      }

      "decode type with separator" in {
        val json = parse("""{"type": "concat", "separator": ","}""").getOrElse(Json.Null)
        val result = json.as[TransformationConfig]

        result.isRight shouldBe true
        result.value.separator shouldBe Some(",")
      }
    }

    "decoding RosettaFieldMapping" should {

      "decode required fields" in {
        val json = parse("""{"targetField": "field", "sourcePath": "$.path"}""").getOrElse(Json.Null)
        val result = json.as[RosettaFieldMapping]

        result.isRight shouldBe true
        result.value.targetField shouldBe "field"
        result.value.sourcePath shouldBe "$.path"
      }

      "decode optional fields" in {
        val json = parse("""
          {
            "targetField": "field",
            "sourcePath": "$.path",
            "defaultValue": "default",
            "transformations": [{"type": "toupper"}]
          }
        """).getOrElse(Json.Null)
        val result = json.as[RosettaFieldMapping]

        result.isRight shouldBe true
        result.value.defaultValue shouldBe Some("default")
        result.value.transformations.size shouldBe 1
      }
    }
  }
}
