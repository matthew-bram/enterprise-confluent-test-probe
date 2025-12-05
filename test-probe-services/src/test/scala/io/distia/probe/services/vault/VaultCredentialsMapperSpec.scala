package io.distia.probe
package services
package vault

import io.distia.probe.common.exceptions.VaultMappingException
import io.distia.probe.services.fixtures.{RosettaConfigFixtures, VaultCredentialsFixtures}
import io.circe.Json
import io.circe.syntax.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.data.NonEmptyList

class VaultCredentialsMapperSpec extends AnyWordSpec with Matchers {

  "VaultCredentialsMapper.mapToVaultCredentials" should {

    "successfully map valid vault JSON to credentials" in {
      val vaultJson: Json = VaultCredentialsFixtures.validVaultJson
      val rosettaConfig = RosettaConfigFixtures.defaultRosettaConfig
      val requiredFields: List[String] = List("clientId", "clientSecret")

      val result = VaultCredentialsMapper.mapToVaultCredentials(vaultJson, rosettaConfig, requiredFields)

      result shouldBe a[Right[_, _]]
      val credentials = result.getOrElse(fail("Expected Right but got Left"))
      credentials.get("clientId") shouldBe Some("test-client-id")
      credentials.get("clientSecret") shouldBe Some("test-client-secret-xyz123")
    }

    "successfully map nested vault JSON using nested source path" in {
      val vaultJson: Json = VaultCredentialsFixtures.validNestedVaultJson
      val rosettaConfig = RosettaConfigFixtures.rosettaConfigWithNestedPath
      val requiredFields: List[String] = List("clientId", "clientSecret")

      val result = VaultCredentialsMapper.mapToVaultCredentials(vaultJson, rosettaConfig, requiredFields)

      result shouldBe a[Right[_, _]]
      val credentials = result.getOrElse(fail("Expected Right but got Left"))
      credentials.get("clientId") shouldBe Some("nested-client-id")
      credentials.get("clientSecret") shouldBe Some("nested-client-secret-abc456")
    }

    "fail when missing single required field" in {
      val vaultJson: Json = VaultCredentialsFixtures.missingClientSecretJson
      val rosettaConfig = RosettaConfigFixtures.defaultRosettaConfig
      val requiredFields: List[String] = List("clientId", "clientSecret")

      val result = VaultCredentialsMapper.mapToVaultCredentials(vaultJson, rosettaConfig, requiredFields)

      result shouldBe a[Left[_, _]]
      val errors: NonEmptyList[Throwable] = result.left.getOrElse(fail("Expected Left but got Right"))
      errors.size shouldBe 1
      errors.head shouldBe a[VaultMappingException]
      errors.head.getMessage should include("clientSecret")
    }

    "fail when missing multiple required fields with error accumulation" in {
      val vaultJson: Json = VaultCredentialsFixtures.missingMultipleFieldsJson
      val rosettaConfig = RosettaConfigFixtures.defaultRosettaConfig
      val requiredFields: List[String] = List("clientId", "clientSecret")

      val result = VaultCredentialsMapper.mapToVaultCredentials(vaultJson, rosettaConfig, requiredFields)

      result shouldBe a[Left[_, _]]
      val errors: NonEmptyList[Throwable] = result.left.getOrElse(fail("Expected Left but got Right"))
      errors.size should be >= 1
      errors.toList.foreach { error =>
        error shouldBe a[VaultMappingException]
      }
    }

    "fail when Rosetta config is missing required field mapping" in {
      val vaultJson: Json = VaultCredentialsFixtures.validVaultJson
      val rosettaConfig = RosettaConfigFixtures.rosettaConfigMissingRequiredField
      val requiredFields: List[String] = List("clientId", "clientSecret")

      val result = VaultCredentialsMapper.mapToVaultCredentials(vaultJson, rosettaConfig, requiredFields)

      result shouldBe a[Left[_, _]]
      val errors: NonEmptyList[Throwable] = result.left.getOrElse(fail("Expected Left but got Right"))
      errors.head shouldBe a[VaultMappingException]
      errors.head.getMessage should include("Missing required field mappings")
      errors.head.getMessage should include("clientSecret")
    }

    "successfully apply transformations during mapping" in {
      val vaultJson: Json = VaultCredentialsFixtures.vaultJsonWithBase64EncodedSecret
      val rosettaConfig = RosettaConfigFixtures.rosettaConfigWithTransformations
      val requiredFields: List[String] = List("clientId", "clientSecret")

      val result = VaultCredentialsMapper.mapToVaultCredentials(vaultJson, rosettaConfig, requiredFields)

      result shouldBe a[Right[_, _]]
      val credentials = result.getOrElse(fail("Expected Right but got Left"))
      credentials.get("clientId") shouldBe Some("test-client-id")
      credentials.get("clientSecret") shouldBe Some("test-client-secret-xyz123")
    }

    "apply default value when field is missing from vault JSON" in {
      val vaultJson: Json = VaultCredentialsFixtures.validVaultJson
      val rosettaConfig = RosettaConfigFixtures.rosettaConfigWithDefaultValue
      val requiredFields: List[String] = List("clientId", "clientSecret", "role")

      val result = VaultCredentialsMapper.mapToVaultCredentials(vaultJson, rosettaConfig, requiredFields)

      result shouldBe a[Right[_, _]]
      val credentials = result.getOrElse(fail("Expected Right but got Left"))
      credentials.get("role") shouldBe Some("PRODUCER")
    }
  }

  "VaultCredentialsMapper.extractField" should {

    "successfully extract field using direct path" in {
      val json: Json = Json.obj("oauth" -> Json.obj("clientId" -> "test-id".asJson))
      val rosettaConfig = RosettaConfigFixtures.defaultRosettaConfig

      val result = VaultCredentialsMapper.extractField(json, "clientId", rosettaConfig)

      result.isValid shouldBe true
      result.getOrElse(fail("Expected Valid")) shouldBe "test-id"
    }

    "successfully extract nested field" in {
      val json: Json = VaultCredentialsFixtures.validNestedVaultJson
      val rosettaConfig = RosettaConfigFixtures.rosettaConfigWithNestedPath

      val result = VaultCredentialsMapper.extractField(json, "clientId", rosettaConfig)

      result.isValid shouldBe true
      result.getOrElse(fail("Expected Valid")) shouldBe "nested-client-id"
    }

    "fail when field is not found in JSON" in {
      val json: Json = Json.obj("oauth" -> Json.obj())
      val rosettaConfig = RosettaConfigFixtures.defaultRosettaConfig

      val result = VaultCredentialsMapper.extractField(json, "clientId", rosettaConfig)

      result.isInvalid shouldBe true
      val error: NonEmptyList[Throwable] = result.toEither.left.getOrElse(fail("Expected Invalid"))
      error.head shouldBe a[VaultMappingException]
      error.head.getMessage should include("clientId")
    }

    "fail when no mapping found for field" in {
      val json: Json = VaultCredentialsFixtures.validVaultJson
      val rosettaConfig = RosettaConfigFixtures.defaultRosettaConfig

      val result = VaultCredentialsMapper.extractField(json, "unknownField", rosettaConfig)

      result.isInvalid shouldBe true
      val error: NonEmptyList[Throwable] = result.toEither.left.getOrElse(fail("Expected Invalid"))
      error.head shouldBe a[VaultMappingException]
      error.head.getMessage should include("No mapping found")
      error.head.getMessage should include("unknownField")
    }

    "fail when extracted value is not a string" in {
      val json: Json = VaultCredentialsFixtures.invalidTypeJson
      val rosettaConfig = RosettaConfigFixtures.defaultRosettaConfig

      val result = VaultCredentialsMapper.extractField(json, "clientId", rosettaConfig)

      result.isInvalid shouldBe true
      val error: NonEmptyList[Throwable] = result.toEither.left.getOrElse(fail("Expected Invalid"))
      error.head shouldBe a[VaultMappingException]
      error.head.getMessage should include("Expected string value")
    }

    "apply transformation to extracted field" in {
      val json: Json = VaultCredentialsFixtures.vaultJsonWithBase64EncodedSecret
      val rosettaConfig = RosettaConfigFixtures.rosettaConfigWithTransformations

      val result = VaultCredentialsMapper.extractField(json, "clientSecret", rosettaConfig)

      result.isValid shouldBe true
      result.getOrElse(fail("Expected Valid")) shouldBe "test-client-secret-xyz123"
    }
  }

  "VaultCredentialsMapper.mapMultiple" should {

    "successfully map multiple vault JSON objects" in {
      val jsonList: List[Json] = VaultCredentialsFixtures.multipleCredentialsJsonList
      val rosettaConfig = RosettaConfigFixtures.defaultRosettaConfig
      val requiredFields: List[String] = List("clientId", "clientSecret")

      val result = VaultCredentialsMapper.mapMultiple(jsonList, rosettaConfig, requiredFields)

      result shouldBe a[Right[_, _]]
      val credentialsList = result.getOrElse(fail("Expected Right but got Left"))
      credentialsList.size shouldBe 3
      credentialsList(0).get("clientId") shouldBe Some("client-1")
      credentialsList(1).get("clientId") shouldBe Some("client-2")
      credentialsList(2).get("clientId") shouldBe Some("client-3")
    }

    "fail when any vault JSON in list is invalid with error accumulation" in {
      val jsonList: List[Json] = List(
        VaultCredentialsFixtures.validVaultJson,
        VaultCredentialsFixtures.missingClientSecretJson,
        VaultCredentialsFixtures.missingMultipleFieldsJson
      )
      val rosettaConfig = RosettaConfigFixtures.defaultRosettaConfig
      val requiredFields: List[String] = List("clientId", "clientSecret")

      val result = VaultCredentialsMapper.mapMultiple(jsonList, rosettaConfig, requiredFields)

      result shouldBe a[Left[_, _]]
      val errors: NonEmptyList[Throwable] = result.left.getOrElse(fail("Expected Left but got Right"))
      errors.size should be >= 2
    }

    "return empty list when input list is empty" in {
      val jsonList: List[Json] = List.empty
      val rosettaConfig = RosettaConfigFixtures.defaultRosettaConfig
      val requiredFields: List[String] = List("clientId", "clientSecret")

      val result = VaultCredentialsMapper.mapMultiple(jsonList, rosettaConfig, requiredFields)

      result shouldBe a[Right[_, _]]
      val credentialsList = result.getOrElse(fail("Expected Right but got Left"))
      credentialsList shouldBe empty
    }
  }
}
