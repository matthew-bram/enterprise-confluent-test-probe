# Rosetta Template Examples

This directory contains example Rosetta YAML configurations demonstrating dynamic request body building for vault integrations.

## What is Rosetta?

**Rosetta** is the test-probe's configuration system for mapping between different vault API formats. It allows teams to define:

1. **Request templates** - Dynamic JSON request bodies with variable substitution
2. **Response mappings** - JSONPath extraction and transformation of vault responses

This enables support for heterogeneous vault implementations without changing code.

## Files in This Directory

| File | Purpose |
|------|---------|
| `hip-team-simple-vault.yaml` | Simple flat vault (only needs oauth-id) |
| `mf-team-complex-vault.yaml` | Complex nested vault (demonstrates all 3 variable types) |
| `mixed-sources-example.yaml` | Shows all three variable sources in one template |
| `block-storage-metadata-example.yaml` | Example metadata YAML loaded from block storage |
| `example-application.conf` | Required config values for template substitution |
| `README.md` | This file |

## Template Variable Patterns

Rosetta templates support **three variable substitution patterns**:

### 1. Config Path: `{{$^request-params.path.to.value}}`

Resolves from `application.conf` configuration file.

**Pattern**: `{{$^request-params.path.to.value}}`

**Security Requirement**: MUST start with `request-params.` prefix

**Example**:
```yaml
request-template:
  client-app-id: "{{$^request-params.vault-requests.mf-team.client-app-id}}"
```

Resolves to:
```hocon
# application.conf
request-params {
  vault-requests {
    mf-team {
      client-app-id = "MF-PROD-CONSUMER-APP-123"
    }
  }
}
```

Result: `"client-app-id": "MF-PROD-CONSUMER-APP-123"`

**Implementation**: `RequestBodyBuilder.resolveConfigPath()` (lines 97-112)

---

### 2. Metadata Key: `{{'metadataKey'}}`

Resolves from `TopicDirective.metadata` Map (note the single quotes).

**Pattern**: `{{'key-name'}}`

**Source**: Populated from block storage YAML during `BlockStorageService` phase

**Example**:
```yaml
request-template:
  blood-type: "{{'blood-type'}}"
  test-run-id: "{{'test-run-id'}}"
```

Resolves from:
```yaml
# test-metadata.yaml (loaded from S3/Azure Block Storage)
test-metadata:
  blood-type: "O-negative"
  test-run-id: "run-2025-01-22-12345"
```

Result:
```json
{
  "blood-type": "O-negative",
  "test-run-id": "run-2025-01-22-12345"
}
```

**Implementation**: `RequestBodyBuilder.resolveMetadataKey()` (lines 114-122)

---

### 3. Directive Field: `{{fieldName}}`

Resolves from `TopicDirective` built-in fields (no quotes, no prefix).

**Pattern**: `{{fieldName}}`

**Available Fields**: `topic`, `role`, `clientPrincipal`

**Example**:
```yaml
request-template:
  oauth-id: "{{clientPrincipal}}"
  topic-name: "{{topic}}"
  access-role: "{{role}}"
```

Resolves from:
```scala
TopicDirective(
  topic = "mf-team-1-topic",
  role = "CONSUMER",
  clientPrincipal = "team-a-principal",
  eventFilters = List.empty,
  metadata = Map.empty
)
```

Result:
```json
{
  "oauth-id": "team-a-principal",
  "topic-name": "mf-team-1-topic",
  "access-role": "CONSUMER"
}
```

**Implementation**: `RequestBodyBuilder.resolveDirectiveField()` (lines 124-128)

---

## Complete Example: MF Team Complex Vault

**File**: `mf-team-complex-vault.yaml`

```yaml
request-template:
  auth:
    credentials:
      # Pattern 3: Directive field
      clientPrincipal: "{{clientPrincipal}}"

      # Pattern 1: Config path
      client-application-id: "{{$^request-params.vault-requests.mf-team.client-app-id}}"

    resource:
      # Pattern 3: Directive fields
      topic: "{{topic}}"
      role: "{{role}}"

      # Pattern 2: Metadata key
      blood-type: "{{'blood-type'}}"

      # Pattern 1: Config path
      organization-id: "{{$^request-params.vault-requests.mf-team.org-id}}"

mappings:
  - targetField: clientSecret
    sourcePath: $.auth.credentials.secret
    transformations:
      - type: base64Decode

  - targetField: clientId
    sourcePath: $.auth.credentials.client_id
```

**Required Configuration** (`application.conf`):
```hocon
request-params {
  vault-requests {
    mf-team {
      client-app-id = "MF-PROD-CONSUMER-APP-123"
      org-id = "ORG-MF-PRODUCTION-456"
    }
  }
}
```

**Required Metadata** (from block storage):
```yaml
test-metadata:
  blood-type: "O-negative"
```

**Input**:
```scala
TopicDirective(
  topic = "mf-team-1-topic",
  role = "CONSUMER",
  clientPrincipal = "team-a-principal",
  metadata = Map("blood-type" -> "O-negative")
)
```

**Generated Request Body**:
```json
{
  "auth": {
    "credentials": {
      "clientPrincipal": "team-a-principal",
      "client-application-id": "MF-PROD-CONSUMER-APP-123"
    },
    "resource": {
      "topic": "mf-team-1-topic",
      "role": "CONSUMER",
      "blood-type": "O-negative",
      "organization-id": "ORG-MF-PRODUCTION-456"
    }
  }
}
```

**Vault Response** (example):
```json
{
  "auth": {
    "credentials": {
      "secret": "YmFzZTY0LWVuY29kZWQtc2VjcmV0",
      "client_id": "actual-client-id-123"
    }
  }
}
```

**Mapped Credentials**:
```scala
Map(
  "clientSecret" -> "base64-encoded-secret",  // After base64Decode
  "clientId" -> "actual-client-id-123"
)
```

---

## Security Validation

`RequestBodyBuilder` enforces security rules on config path references:

### ✅ Valid Config Paths

- `{{$^request-params.vault-requests.team.id}}`
- `{{$^request-params.my-app.setting}}`
- `{{$^request-params.azure.function-key}}`

### ❌ Invalid Config Paths

| Pattern | Error | Reason |
|---------|-------|--------|
| `{{$^database.password}}` | Invalid namespace | Missing `request-params.` prefix |
| `{{$^request-params.}}` | Insufficient depth | No subpath after prefix |
| `{{$^request-params.bad$char}}` | Invalid characters | Only `a-z A-Z 0-9 . _ -` allowed |

**Allowed Characters**: `a-z`, `A-Z`, `0-9`, `.`, `_`, `-`

**Implementation**: `RequestBodyBuilder.validateConfigPath()` (lines 67-88)

---

## Integration Flow

```
┌─────────────────────┐
│ BlockStorageService │
│   (Phase 1: Boot)   │
└──────────┬──────────┘
           │
           │ Loads test-metadata.yaml from S3/Azure
           │ Extracts metadata Map
           │
           ▼
┌─────────────────────┐
│  TopicDirective     │
│                     │
│  topic: String      │  ◄── Pattern 3: {{topic}}
│  role: String       │  ◄── Pattern 3: {{role}}
│  clientPrincipal    │  ◄── Pattern 3: {{clientPrincipal}}
│  metadata: Map      │  ◄── Pattern 2: {{'key'}}
└──────────┬──────────┘
           │
           │
           ▼
┌─────────────────────┐      ┌──────────────────────┐
│  AzureVaultService  │      │  RosettaConfig       │
│                     │──┬──▶│  - request-template  │
│ invokeVault()       │  │   │  - mappings          │
└──────────┬──────────┘  │   └──────────────────────┘
           │              │
           │              │   ┌──────────────────────┐
           │              └──▶│  application.conf    │
           │                  │  request-params { }  │  ◄── Pattern 1: {{$^...}}
           │                  └──────────────────────┘
           ▼
┌─────────────────────┐
│ RequestBodyBuilder  │
│                     │
│ build()             │
│  ├─ substituteVariables()
│  ├─ resolveConfigPath()      (Pattern 1)
│  ├─ resolveMetadataKey()     (Pattern 2)
│  └─ resolveDirectiveField()  (Pattern 3)
└──────────┬──────────┘
           │
           │ Dynamic JSON request body
           │
           ▼
┌─────────────────────┐
│  Azure Function     │
│  Vault Endpoint     │
└──────────┬──────────┘
           │
           │ JSON response
           │
           ▼
┌─────────────────────────┐
│ VaultCredentialsMapper  │
│                         │
│ mapToVaultCredentials() │
│  └─ Apply JSONPath      │
│  └─ Apply transformations
└──────────┬──────────────┘
           │
           │ Map[String, String]
           │
           ▼
┌─────────────────────┐
│  JaasConfigBuilder  │
│                     │
│  build()            │
└──────────┬──────────┘
           │
           │ JAAS config string
           │
           ▼
┌─────────────────────────┐
│ KafkaSecurityDirective  │
│                         │
│  topic: String          │
│  role: String           │
│  jaasConfig: String     │
└─────────────────────────┘
```

---

## Usage Instructions

### Step 1: Create Rosetta Configuration

Create a YAML file defining your vault's request template and response mappings:

```yaml
# my-team-vault-config.yaml
request-template:
  credentials:
    oauth-id: "{{clientPrincipal}}"
    app-id: "{{$^request-params.my-team.app-id}}"
  metadata:
    test-id: "{{'test-run-id'}}"

mappings:
  - targetField: clientSecret
    sourcePath: $.credentials.secret
```

### Step 2: Configure Application

Add required config values to `application.conf`:

```hocon
request-params {
  my-team {
    app-id = "MY-TEAM-APP-123"
  }
}
```

### Step 3: Configure VaultService

Point to your Rosetta config:

```hocon
test-probe {
  vault {
    provider = "azure"
    rosetta-mapping-path = "/path/to/my-team-vault-config.yaml"
  }
}
```

### Step 4: Provide Metadata

Create test metadata YAML (loaded from S3/Azure):

```yaml
test-metadata:
  test-run-id: "run-2025-01-22-12345"
```

---

## Error Handling

`RequestBodyBuilder` provides helpful error messages:

### Missing Config Path

```
Invalid config path: 'database.password'.
Config paths must start with 'request-params.' namespace.
```

### Missing Metadata Key

```
Metadata key 'blood-type' not found in TopicDirective for topic mf-team-1-topic.
Available keys: test-run-id, correlation-id
```

### Unknown Directive Field

```
Unknown TopicDirective field: unknownField.
Valid fields: topic, role, clientPrincipal
```

### Multiple Errors (Accumulated)

```
Multiple template errors for topic mf-team-1-topic:
  - Config path not found: request-params.missing.value for topic mf-team-1-topic
  - Metadata key 'blood-type' not found in TopicDirective for topic mf-team-1-topic. Available keys: test-run-id
  - Unknown TopicDirective field: badField. Valid fields: topic, role, clientPrincipal
```

---

## Implementation Details

| Component | File | Key Methods |
|-----------|------|-------------|
| Request building | `RequestBodyBuilder.scala` | `build()`, `substituteVariables()` |
| Config path resolution | `RequestBodyBuilder.scala` | `resolveConfigPath()`, `validateConfigPath()` |
| Metadata resolution | `RequestBodyBuilder.scala` | `resolveMetadataKey()` |
| Field resolution | `RequestBodyBuilder.scala` | `resolveDirectiveField()` |
| Response mapping | `VaultCredentialsMapper.scala` | `mapToVaultCredentials()` |
| JAAS building | `JaasConfigBuilder.scala` | `build()` |
| Integration | `AzureVaultService.scala` | `invokeVault()`, `fetchWithRetry()` |

---

## Testing

To validate your Rosetta configuration:

```scala
val topicDirective = TopicDirective(
  topic = "test-topic",
  role = "CONSUMER",
  clientPrincipal = "test-principal",
  eventFilters = List.empty,
  metadata = Map("test-run-id" -> "run-123")
)

val rosettaConfig = RosettaConfig.load("/path/to/config.yaml")
val appConfig = ConfigFactory.load()

RequestBodyBuilder.build(topicDirective, rosettaConfig, appConfig) match {
  case Right(requestBody) =>
    println(s"Generated request body: $requestBody")
  case Left(error) =>
    println(s"Template error: ${error.getMessage}")
}
```

---

## References

- **RequestBodyBuilder**: `test-probe-services/src/main/scala/com/company/probe/services/vault/RequestBodyBuilder.scala`
- **VaultCredentialsMapper**: `test-probe-services/src/main/scala/com/company/probe/services/vault/VaultCredentialsMapper.scala`
- **AzureVaultService**: `test-probe-services/src/main/scala/com/company/probe/services/builder/modules/AzureVaultService.scala`
- **RosettaConfig Model**: `test-probe-common/src/main/scala/com/company/probe/common/rosetta/RosettaConfig.scala`

---

**Last Updated**: 2025-10-23
