package io.distia.probe
package services
package fixtures

import io.circe.Json
import io.circe.syntax.*

private[services] object VaultCredentialsFixtures {

  def validCredentials: Map[String, String] = Map(
    "clientId" -> "test-client-id",
    "clientSecret" -> "test-client-secret-xyz123"
  )

  def partialCredentials: Map[String, String] = Map(
    "clientId" -> "test-client-id"
  )

  def validVaultJson: Json = Json.obj(
    "oauth" -> Json.obj(
      "clientId" -> "test-client-id".asJson,
      "clientSecret" -> "test-client-secret-xyz123".asJson
    )
  )

  def validNestedVaultJson: Json = Json.obj(
    "kafka" -> Json.obj(
      "auth" -> Json.obj(
        "oauth" -> Json.obj(
          "credentials" -> Json.obj(
            "id" -> "nested-client-id".asJson,
            "secret" -> "nested-client-secret-abc456".asJson
          )
        )
      )
    )
  )

  def missingClientSecretJson: Json = Json.obj(
    "oauth" -> Json.obj(
      "clientId" -> "test-client-id".asJson
    )
  )

  def missingMultipleFieldsJson: Json = Json.obj(
    "oauth" -> Json.obj()
  )

  def invalidTypeJson: Json = Json.obj(
    "oauth" -> Json.obj(
      "clientId" -> 12345.asJson,
      "clientSecret" -> "test-client-secret-xyz123".asJson
    )
  )

  def vaultJsonWithBase64EncodedSecret: Json = Json.obj(
    "oauth" -> Json.obj(
      "clientId" -> "test-client-id".asJson,
      "encodedSecret" -> "dGVzdC1jbGllbnQtc2VjcmV0LXh5ejEyMw==".asJson
    )
  )

  def multipleCredentialsJsonList: List[Json] = List(
    Json.obj(
      "oauth" -> Json.obj(
        "clientId" -> "client-1".asJson,
        "clientSecret" -> "secret-1".asJson
      )
    ),
    Json.obj(
      "oauth" -> Json.obj(
        "clientId" -> "client-2".asJson,
        "clientSecret" -> "secret-2".asJson
      )
    ),
    Json.obj(
      "oauth" -> Json.obj(
        "clientId" -> "client-3".asJson,
        "clientSecret" -> "secret-3".asJson
      )
    )
  )
}
