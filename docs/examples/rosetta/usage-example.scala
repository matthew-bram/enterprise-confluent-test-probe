// Example: How AzureVaultService would use RequestBodyBuilder

// BEFORE (hardcoded):
def buildPayload(topicDirective: TopicDirective): String = {
  Json.obj(
    "topic" -> topicDirective.topic.asJson,
    "role" -> topicDirective.role.asJson,
    "clientPrincipal" -> topicDirective.clientPrincipal.asJson
  ).noSpaces
}

// AFTER (dynamic with RequestBodyBuilder):
def buildPayload(topicDirective: TopicDirective): Either[VaultMappingException, String] = {
  RequestBodyBuilder.build(
    topicDirective = topicDirective,
    rosettaConfig = rosettaConfig.get,
    appConfig = config.get
  )
}

// Updated invokeVault method:
def invokeVault(topicDirective: TopicDirective)(implicit ec: ExecutionContext): Future[String] = {

  RequestBodyBuilder.build(topicDirective, rosettaConfig.get, config.get) match {
    case Left(error) =>
      Future.failed(error)

    case Right(payload) =>
      httpClient.get.post(
        uri = functionUrl.get,
        jsonPayload = payload,
        headers = Map(
          "Content-Type" -> "application/json",
          "x-functions-key" -> functionKey.get
        )
      ).flatMap {
        case (200 | 201, Some(body)) =>
          Future.successful(body)
        // ... rest of error handling
      }
  }
}

// Example 1: Simple Hip Team Topic
// TopicDirective:
val hipTopicDirective = TopicDirective(
  topic = "hip-team-events",
  role = "CONSUMER",
  clientPrincipal = "team-a-principal",
  eventFilters = List.empty,
  metadata = Map.empty  // No metadata needed!
)

// Rosetta template (hip-team-simple-vault.yaml):
// request-template:
//   oauth-id: "{{clientPrincipal}}"

// Result payload:
// {"oauth-id":"team-a-principal"}


// Example 2: Complex MF Team Topic
// TopicDirective:
val mfTopicDirective = TopicDirective(
  topic = "mf-team-1-events",
  role = "CONSUMER",
  clientPrincipal = "team-a-principal",
  eventFilters = List.empty,
  metadata = Map(
    "blood-type" -> "O-negative",
    "test-run-id" -> "run-12345"
  )
)

// Rosetta template (mf-team-complex-vault.yaml):
// request-template:
//   auth:
//     credentials:
//       clientPrincipal: "{{clientPrincipal}}"
//       client-application-id: "{{$^custom-fields.vault-requests.mf-team.client-app-id}}"
//     resource:
//       topic: "{{topic}}"
//       role: "{{role}}"
//       blood-type: "{{'blood-type'}}"

// application.conf:
// custom-fields.vault-requests.mf-team.client-app-id = "MF-PROD-CONSUMER-APP-123"

// Result payload:
// {
//   "auth": {
//     "credentials": {
//       "clientPrincipal": "team-a-principal",
//       "client-application-id": "MF-PROD-CONSUMER-APP-123"
//     },
//     "resource": {
//       "topic": "mf-team-1-events",
//       "role": "CONSUMER",
//       "blood-type": "O-negative"
//     }
//   }
// }


// Example 3: Error Cases

// Missing metadata key:
val missingMetadata = TopicDirective(
  topic = "mf-team-1-events",
  role = "CONSUMER",
  clientPrincipal = "team-a-principal",
  eventFilters = List.empty,
  metadata = Map.empty  // Missing 'blood-type' key!
)
// Result: Left(VaultMappingException("Metadata key 'blood-type' not found in TopicDirective..."))

// Missing config path:
// Rosetta has: {{$^custom-fields.vault-requests.mf-team.client-app-id}}
// But application.conf missing that path
// Result: Left(VaultMappingException("Config path not found: custom-fields.vault-requests.mf-team.client-app-id..."))

// No request template in Rosetta:
// Result: Left(VaultMappingException("Request template is required in Rosetta config for topic ..."))
