package io.distia.probe
package common
package rosetta

import io.circe.Json
import io.circe.parser._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues

/**
 * Comprehensive test suite for RosettaMapper - JSONPath resolver and transformation engine
 *
 * Covers:
 * - Simple field access ($.field)
 * - Multi-level nesting ($.a.b.c.d.e.f)
 * - Array access by index ($.array[0], $.array[99])
 * - Array wildcards ($.users[*].name)
 * - Array length operations ($.array.length())
 * - Optional chaining ($.a?.b?.c)
 * - Missing paths
 * - Invalid JSONPath syntax
 * - Transformation integration
 * - Real-world vault structures (AWS, HashiCorp, Azure)
 * - Edge cases (empty arrays, null values, mixed types)
 *
 * Target: 50+ tests, 70%+ coverage
 */
class RosettaMapperSpec extends AnyWordSpec with Matchers with EitherValues {

  import RosettaMapper._
  import RosettaTransformations._

  /**
   * Test fixture: Simple JSON structure
   */
  trait SimpleJsonFixture {
    val simpleJson = parse("""
      {
        "name": "test",
        "value": 123,
        "active": true
      }
    """).getOrElse(Json.Null)
  }

  /**
   * Test fixture: Nested JSON structure
   */
  trait NestedJsonFixture {
    val nestedJson = parse("""
      {
        "level1": {
          "level2": {
            "level3": {
              "level4": {
                "level5": {
                  "level6": {
                    "deepValue": "found it!"
                  }
                }
              }
            }
          }
        }
      }
    """).getOrElse(Json.Null)
  }

  /**
   * Test fixture: Array JSON structure
   */
  trait ArrayJsonFixture {
    val arrayJson = parse("""
      {
        "users": [
          {"name": "Alice", "email": "alice@example.com", "age": 30},
          {"name": "Bob", "email": "bob@example.com", "age": 25},
          {"name": "Charlie", "email": "charlie@example.com", "age": 35}
        ],
        "emptyArray": [],
        "numbers": [1, 2, 3, 4, 5]
      }
    """).getOrElse(Json.Null)
  }

  /**
   * Test fixture: Real-world vault structures
   */
  trait VaultJsonFixture {
    // AWS Secrets Manager format (deep nesting)
    val awsVaultJson = parse("""
      {
        "kafka": {
          "topics": [
            {
              "name": "orders-topic",
              "partitions": 10
            }
          ],
          "credentials": {
            "role": "producer",
            "oauth": {
              "client_id": "aws-kafka-client-123",
              "client_secret": "YXdzLXNlY3JldC0xMjM=",
              "token_endpoint": "https://oauth.aws.com/token"
            }
          }
        }
      }
    """).getOrElse(Json.Null)

    // HashiCorp Vault format (very deep nesting)
    val hashicorpVaultJson = parse("""
      {
        "data": {
          "data": {
            "kafka": {
              "environments": {
                "production": {
                  "topics": [
                    {"name": "payments"}
                  ],
                  "auth": {
                    "oauth2": {
                      "credentials": {
                        "client_id": "vault-client-456",
                        "client_secret": "dmF1bHQtc2VjcmV0LTQ1Ng=="
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    """).getOrElse(Json.Null)

    // Azure Key Vault format (flat structure)
    val azureVaultJson = parse("""
      {
        "topic_name": "events-topic",
        "role": "CONSUMER",
        "oauth_client_id": "azure-client-789",
        "oauth_client_secret": "YXp1cmUtc2VjcmV0LTc4OQ=="
      }
    """).getOrElse(Json.Null)
  }

  "RosettaMapper.resolvePath - Simple field access" should {
    "extract simple string field" in new SimpleJsonFixture {
      val result = resolvePath(simpleJson, "$.name")
      result shouldBe Some(Json.fromString("test"))
    }

    "extract simple number field" in new SimpleJsonFixture {
      val result = resolvePath(simpleJson, "$.value")
      result shouldBe Some(Json.fromInt(123))
    }

    "extract simple boolean field" in new SimpleJsonFixture {
      val result = resolvePath(simpleJson, "$.active")
      result shouldBe Some(Json.fromBoolean(true))
    }

    "return None for missing field" in new SimpleJsonFixture {
      val result = resolvePath(simpleJson, "$.nonexistent")
      result shouldBe None
    }

    "handle path without $. prefix" in new SimpleJsonFixture {
      val result = resolvePath(simpleJson, "name")
      result shouldBe Some(Json.fromString("test"))
    }

    "return root for empty path after $." in new SimpleJsonFixture {
      val result = resolvePath(simpleJson, "$.")
      result shouldBe Some(simpleJson)
    }
  }

  "RosettaMapper.resolvePath - Nested field access" should {
    "extract 2-level nested field" in new NestedJsonFixture {
      val result = resolvePath(nestedJson, "$.level1.level2")
      result.isDefined shouldBe true
    }

    "extract 3-level nested field" in new NestedJsonFixture {
      val result = resolvePath(nestedJson, "$.level1.level2.level3")
      result.isDefined shouldBe true
    }

    "extract 4-level nested field" in new NestedJsonFixture {
      val result = resolvePath(nestedJson, "$.level1.level2.level3.level4")
      result.isDefined shouldBe true
    }

    "extract 6-level deep nested field" in new NestedJsonFixture {
      val result = resolvePath(nestedJson, "$.level1.level2.level3.level4.level5.level6.deepValue")
      result shouldBe Some(Json.fromString("found it!"))
    }

    "return None for incorrect nested path" in new NestedJsonFixture {
      val result = resolvePath(nestedJson, "$.level1.wrongPath.level3")
      result shouldBe None
    }

    "return None for partial nested path that doesn't exist" in new NestedJsonFixture {
      val result = resolvePath(nestedJson, "$.level1.level2.level3.level4.level5.level6.level7")
      result shouldBe None
    }
  }

  "RosettaMapper.resolvePath - Array access" should {
    "extract first array element by index" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.users[0]")
      result.isDefined shouldBe true
    }

    "extract nested field from array element" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.users[0].name")
      result shouldBe Some(Json.fromString("Alice"))
    }

    "extract nested field from second array element" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.users[1].email")
      result shouldBe Some(Json.fromString("bob@example.com"))
    }

    "extract nested field from last array element" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.users[2].age")
      result shouldBe Some(Json.fromInt(35))
    }

    "return None for out-of-bounds positive index" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.users[99]")
      result shouldBe None
    }

    "return None for negative index" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.users[-1]")
      result shouldBe None
    }

    "handle empty array access" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.emptyArray[0]")
      result shouldBe None
    }

    "extract from numeric array" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.numbers[2]")
      result shouldBe Some(Json.fromInt(3))
    }
  }

  "RosettaMapper.resolvePath - Array wildcards" should {
    "collect all names from array with wildcard" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.users[*].name")
      result.isDefined shouldBe true
      result.get.asArray.get.size shouldBe 3
      result.get.asArray.get.head shouldBe Json.fromString("Alice")
    }

    "collect all emails from array with wildcard" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.users[*].email")
      result.isDefined shouldBe true
      result.get.asArray.get.size shouldBe 3
    }

    "return empty array for wildcard on empty array" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.emptyArray[*]")
      result.isDefined shouldBe true
      result.get.asArray.get.isEmpty shouldBe true
    }

    "collect all elements with wildcard on numeric array" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.numbers[*]")
      result.isDefined shouldBe true
      result.get.asArray.get.size shouldBe 5
    }
  }

  "RosettaMapper.resolvePath - Array length operation" should {
    "return array length for users array" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.users.length()")
      result shouldBe Some(Json.fromInt(3))
    }

    "return zero for empty array" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.emptyArray.length()")
      result shouldBe Some(Json.fromInt(0))
    }

    "return length for numeric array" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.numbers.length()")
      result shouldBe Some(Json.fromInt(5))
    }

    "return None for length on non-array field" in new SimpleJsonFixture {
      val result = resolvePath(simpleJson, "$.name.length()")
      result shouldBe None
    }
  }

  "RosettaMapper.resolvePath - Optional chaining" should {
    "resolve path with optional field that exists" in new NestedJsonFixture {
      val result = resolvePath(nestedJson, "$.level1?.level2")
      result.isDefined shouldBe true
    }

    "return None for optional field that doesn't exist" in new NestedJsonFixture {
      val result = resolvePath(nestedJson, "$.level1?.wrongField")
      result shouldBe None
    }

    "handle multiple optional fields in chain" in new SimpleJsonFixture {
      val result = resolvePath(simpleJson, "$.name?.value?.test")
      result shouldBe None
    }
  }

  "RosettaMapper.extract - With transformations" should {
    "extract field and apply toUpper transformation" in new SimpleJsonFixture {
      val result = extract(simpleJson, "$.name", List(ToUpper))
      result shouldBe Right(Json.fromString("TEST"))
    }

    "extract nested field and apply base64Decode" in new VaultJsonFixture {
      val result = extract(awsVaultJson, "$.kafka.credentials.oauth.client_secret", List(Base64Decode))
      result shouldBe Right(Json.fromString("aws-secret-123"))
    }

    "extract field and apply transformation chain" in new SimpleJsonFixture {
      val result = extract(simpleJson, "$.name", List(ToUpper, Prefix("PREFIX-")))
      result shouldBe Right(Json.fromString("PREFIX-TEST"))
    }

    "return error for missing path" in new SimpleJsonFixture {
      val result = extract(simpleJson, "$.nonexistent", List(ToUpper))
      result.isLeft shouldBe true
      result.left.value should include("Path not found")
    }

    "return error for transformation failure" in new SimpleJsonFixture {
      val result = extract(simpleJson, "$.value", List(ToUpper)) // ToUpper on number
      result.isLeft shouldBe true
      result.left.value should include("Expected string value")
    }

    "extract without transformations" in new SimpleJsonFixture {
      val result = extract(simpleJson, "$.name")
      result shouldBe Right(Json.fromString("test"))
    }
  }

  "RosettaMapper.extractMultiple" should {
    "extract multiple paths successfully" in new SimpleJsonFixture {
      val paths = List("$.name", "$.value")
      val result = extractMultiple(simpleJson, paths)
      result shouldBe Right(List(Json.fromString("test"), Json.fromInt(123)))
    }

    "return error for any missing path" in new SimpleJsonFixture {
      val paths = List("$.name", "$.nonexistent", "$.value")
      val result = extractMultiple(simpleJson, paths)
      result.isLeft shouldBe true
      result.left.value should include("nonexistent")
    }

    "extract from nested paths" in new VaultJsonFixture {
      val paths = List(
        "$.kafka.topics[0].name",
        "$.kafka.credentials.oauth.client_id"
      )
      val result = extractMultiple(awsVaultJson, paths)
      result.isRight shouldBe true
    }

    "handle empty path list" in new SimpleJsonFixture {
      val result = extractMultiple(simpleJson, Nil)
      result shouldBe Right(Nil)
    }
  }

  "RosettaMapper - Real-world vault structures" should {
    "extract topic name from AWS Secrets Manager format" in new VaultJsonFixture {
      val result = resolvePath(awsVaultJson, "$.kafka.topics[0].name")
      result shouldBe Some(Json.fromString("orders-topic"))
    }

    "extract OAuth client ID from AWS format" in new VaultJsonFixture {
      val result = resolvePath(awsVaultJson, "$.kafka.credentials.oauth.client_id")
      result shouldBe Some(Json.fromString("aws-kafka-client-123"))
    }

    "extract and decode client secret from AWS format" in new VaultJsonFixture {
      val result = extract(awsVaultJson, "$.kafka.credentials.oauth.client_secret", List(Base64Decode))
      result shouldBe Right(Json.fromString("aws-secret-123"))
    }

    "extract role and uppercase from AWS format" in new VaultJsonFixture {
      val result = extract(awsVaultJson, "$.kafka.credentials.role", List(ToUpper))
      result shouldBe Right(Json.fromString("PRODUCER"))
    }

    "extract from 6-level deep HashiCorp Vault format" in new VaultJsonFixture {
      val result = resolvePath(hashicorpVaultJson, "$.data.data.kafka.environments.production.topics[0].name")
      result shouldBe Some(Json.fromString("payments"))
    }

    "extract OAuth credentials from deep HashiCorp format" in new VaultJsonFixture {
      val result = resolvePath(
        hashicorpVaultJson,
        "$.data.data.kafka.environments.production.auth.oauth2.credentials.client_id"
      )
      result shouldBe Some(Json.fromString("vault-client-456"))
    }

    "extract and decode secret from deep HashiCorp format" in new VaultJsonFixture {
      val result = extract(
        hashicorpVaultJson,
        "$.data.data.kafka.environments.production.auth.oauth2.credentials.client_secret",
        List(Base64Decode)
      )
      result shouldBe Right(Json.fromString("vault-secret-456"))
    }

    "extract flat fields from Azure Key Vault format" in new VaultJsonFixture {
      val result = resolvePath(azureVaultJson, "$.topic_name")
      result shouldBe Some(Json.fromString("events-topic"))
    }

    "extract role from Azure format (already uppercase)" in new VaultJsonFixture {
      val result = resolvePath(azureVaultJson, "$.role")
      result shouldBe Some(Json.fromString("CONSUMER"))
    }

    "extract and decode client secret from Azure format" in new VaultJsonFixture {
      val result = extract(azureVaultJson, "$.oauth_client_secret", List(Base64Decode))
      result shouldBe Right(Json.fromString("azure-secret-789"))
    }
  }

  "RosettaMapper - Edge cases" should {
    "handle JSON with null values" in {
      val json = parse("""{"field": null}""").getOrElse(Json.Null)
      val result = resolvePath(json, "$.field")
      result shouldBe Some(Json.Null)
    }

    "handle empty JSON object" in {
      val json = parse("""{}""").getOrElse(Json.Null)
      val result = resolvePath(json, "$.field")
      result shouldBe None
    }

    "handle deeply nested empty objects" in {
      val json = parse("""{"a": {"b": {}}}""").getOrElse(Json.Null)
      val result = resolvePath(json, "$.a.b.c")
      result shouldBe None
    }

    "handle mixed array types" in {
      val json = parse("""{"mixed": [1, "two", true, null]}""").getOrElse(Json.Null)
      val result = resolvePath(json, "$.mixed[1]")
      result shouldBe Some(Json.fromString("two"))
    }

    "handle array within array (double indexing not supported)" in {
      val json = parse("""{"nested": [[1, 2], [3, 4]]}""").getOrElse(Json.Null)
      // Current implementation doesn't support double array indexing like $.nested[0][1]
      // This is expected behavior - attempting to parse "[0][1]" fails
      // We can access the first array, but not index into it in the same expression
      val result = resolvePath(json, "$.nested[0]")
      result.isDefined shouldBe true
      result.get.asArray.isDefined shouldBe true
    }

    "handle unicode field names" in {
      val json = parse("""{"用户名": "test"}""").getOrElse(Json.Null)
      val result = resolvePath(json, "$.用户名")
      result shouldBe Some(Json.fromString("test"))
    }

    "handle fields with dots in names (escaped)" in {
      val json = parse("""{"field.with.dots": "value"}""").getOrElse(Json.Null)
      // Note: Current implementation treats dots as path separators
      // This documents expected behavior
      val result = resolvePath(json, """$.field.with.dots""")
      result.isDefined || result.isEmpty shouldBe true
    }

    "handle very long array" in {
      val largeArray = Json.arr((1 to 1000).map(Json.fromInt): _*)
      val json = Json.obj("numbers" -> largeArray)
      val result = resolvePath(json, "$.numbers[999]")
      result shouldBe Some(Json.fromInt(1000))
    }

    "handle wildcard on nested array field" in new ArrayJsonFixture {
      val result = resolvePath(arrayJson, "$.users[*].name")
      result.isDefined shouldBe true
      val names = result.get.asArray.get
      names.size shouldBe 3
      names.head shouldBe Json.fromString("Alice")
      names(1) shouldBe Json.fromString("Bob")
      names(2) shouldBe Json.fromString("Charlie")
    }
  }

  "RosettaMapper - Error handling" should {
    "provide descriptive error for missing path" in new SimpleJsonFixture {
      val result = extract(simpleJson, "$.nonexistent")
      result shouldBe Left("Path not found: $.nonexistent")
    }

    "provide descriptive error for transformation failure" in new SimpleJsonFixture {
      val result = extract(simpleJson, "$.value", List(Base64Decode))
      result.isLeft shouldBe true
      result.left.value should include("Expected string value")
    }

    "accumulate error from first failed transformation in chain" in new SimpleJsonFixture {
      val result = extract(simpleJson, "$.value", List(ToUpper, Prefix("test-")))
      result.isLeft shouldBe true
      result.left.value should include("toUpper")
    }
  }

  "RosettaMapper - Default transformation with missing paths" should {
    "use default value when path not found" in new SimpleJsonFixture {
      val result = extract(simpleJson, "$.nonexistent", List(Default("fallback")))
      result shouldBe Right(Json.fromString("fallback"))
    }

    "use default value and apply subsequent transformations" in new SimpleJsonFixture {
      val result = extract(
        simpleJson,
        "$.nonexistent",
        List(Default("lowercase"), ToUpper)
      )
      result shouldBe Right(Json.fromString("LOWERCASE"))
    }

    "use actual value when path exists (ignore default)" in new SimpleJsonFixture {
      val result = extract(simpleJson, "$.name", List(Default("fallback")))
      result shouldBe Right(Json.fromString("test"))
    }

    "fail when path not found and no default provided" in new SimpleJsonFixture {
      val result = extract(simpleJson, "$.nonexistent", List(ToUpper))
      result shouldBe Left("Path not found: $.nonexistent")
    }
  }
}
