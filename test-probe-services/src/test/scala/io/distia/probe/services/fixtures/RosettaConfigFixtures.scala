package io.distia.probe
package services
package fixtures

import io.distia.probe.common.rosetta.RosettaConfig
import io.circe.Json
import io.circe.syntax.*

private[services] object RosettaConfigFixtures {

  def defaultRosettaConfig: RosettaConfig.RosettaConfig = RosettaConfig.RosettaConfig(
    mappings = List(
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientId",
        sourcePath = "$.oauth.clientId"
      ),
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientSecret",
        sourcePath = "$.oauth.clientSecret"
      )
    )
  )

  def rosettaConfigWithTransformations: RosettaConfig.RosettaConfig = RosettaConfig.RosettaConfig(
    mappings = List(
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientId",
        sourcePath = "$.oauth.clientId"
      ),
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientSecret",
        sourcePath = "$.oauth.encodedSecret",
        transformations = List(
          RosettaConfig.TransformationConfig(transformType = "base64Decode")
        )
      )
    )
  )

  def rosettaConfigWithDefaultValue: RosettaConfig.RosettaConfig = RosettaConfig.RosettaConfig(
    mappings = List(
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientId",
        sourcePath = "$.oauth.clientId"
      ),
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientSecret",
        sourcePath = "$.oauth.clientSecret"
      ),
      RosettaConfig.RosettaFieldMapping(
        targetField = "role",
        sourcePath = "$.kafka.role",
        defaultValue = Some("PRODUCER")
      )
    )
  )

  def rosettaConfigWithNestedPath: RosettaConfig.RosettaConfig = RosettaConfig.RosettaConfig(
    mappings = List(
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientId",
        sourcePath = "$.kafka.auth.oauth.credentials.id"
      ),
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientSecret",
        sourcePath = "$.kafka.auth.oauth.credentials.secret"
      )
    )
  )

  def rosettaConfigWithRequestTemplate: RosettaConfig.RosettaConfig = RosettaConfig.RosettaConfig(
    mappings = List(
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientId",
        sourcePath = "$.oauth.clientId"
      ),
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientSecret",
        sourcePath = "$.oauth.clientSecret"
      )
    ),
    requestTemplate = Some(Json.obj(
      "topic" -> "{{topic}}".asJson,
      "role" -> "{{role}}".asJson,
      "environment" -> "{{$^request-params.environment}}".asJson,
      "region" -> "{{'region'}}".asJson
    ))
  )

  def rosettaConfigWithComplexRequestTemplate: RosettaConfig.RosettaConfig = RosettaConfig.RosettaConfig(
    mappings = List(
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientId",
        sourcePath = "$.oauth.clientId"
      ),
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientSecret",
        sourcePath = "$.oauth.clientSecret"
      )
    ),
    requestTemplate = Some(Json.obj(
      "request" -> Json.obj(
        "topic" -> "{{topic}}".asJson,
        "role" -> "{{role}}".asJson,
        "clientPrincipal" -> "{{clientPrincipal}}".asJson
      ),
      "context" -> Json.obj(
        "environment" -> "{{$^request-params.environment}}".asJson,
        "region" -> "{{'region'}}".asJson,
        "cluster" -> "{{'cluster'}}".asJson
      ),
      "metadata" -> Json.obj(
        "requestId" -> "{{$^request-params.requestId}}".asJson
      )
    ))
  )

  def rosettaConfigMissingRequiredField: RosettaConfig.RosettaConfig = RosettaConfig.RosettaConfig(
    mappings = List(
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientId",
        sourcePath = "$.oauth.clientId"
      )
    )
  )

  def rosettaConfigWithMultipleTransformations: RosettaConfig.RosettaConfig = RosettaConfig.RosettaConfig(
    mappings = List(
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientId",
        sourcePath = "$.oauth.clientId",
        transformations = List(
          RosettaConfig.TransformationConfig(transformType = "toUpper"),
          RosettaConfig.TransformationConfig(transformType = "prefix", value = Some("PREFIX_"))
        )
      ),
      RosettaConfig.RosettaFieldMapping(
        targetField = "clientSecret",
        sourcePath = "$.oauth.clientSecret"
      )
    )
  )
}
