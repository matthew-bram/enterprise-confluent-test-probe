# ADR-VAULT-001: Rosetta Vault Mapping Pattern

**Status**: Accepted
**Date**: 2025-10-21
**Component**: Vault Services, Common Models
**Related Documents**:
- [SecurityDirective Model](../../code/test-probe-common/models/SecurityDirective.md)
- [VaultActor](../../code/test-probe-core/actors/VaultActor.md)

---

## Context

The Test-Probe framework needs to fetch Kafka OAuth credentials from various vault implementations (AWS Secrets Manager, HashiCorp Vault, Azure Key Vault) and map them to the internal `SecurityDirective` model. Each enterprise vault has a different JSON structure for storing credentials:

**AWS Secrets Manager Example:**
```json
{
  "kafka": {
    "topics": [{"name": "orders.events", "type": "producer"}],
    "credentials": {
      "role": "PRODUCER",
      "oauth": {
        "client_id": "kafka-producer-123",
        "client_secret": "base64encodedSecret=="
      }
    }
  }
}
```

**HashiCorp Vault Example:**
```json
{
  "data": {
    "metadata": {"topic": "orders.events"},
    "credentials": {
      "producer": {
        "clientId": "kafka-producer-123",
        "clientSecret": "base64encodedSecret=="
      }
    }
  }
}
```

**Problems:**
1. Framework requires type-safe `SecurityDirective(topic, role, clientSecret, jaasConfig)`
2. Users' vault structures vary significantly (6+ nesting levels, different field names)
3. Hard-coding mapping logic for each vault structure is not extensible
4. Credentials may require transformations (base64 decode, concat, uppercase)
5. jaasConfig must be constructed from OAuth config + credentials, not stored in vault

## Decision

Implement a **Rosetta mapping utility** with bidirectional mapping support:

### 1. Bidirectional Architecture (Request + Response)

```
REQUEST BUILDING (User-Configured):
TopicDirective + RosettaConfig + Config → [RequestBodyBuilder] → Vault API Request Body

RESPONSE MAPPING (User-Configured):
Vault JSON Response → [RosettaMapper] → VaultCredentials

JAAS CONSTRUCTION (Framework-Controlled):
VaultCredentials + OAuth Config → [JaasConfigBuilder] → SecurityDirective
```

**Request Building Phase** (NEW - Added 2025-10-23)
- RequestBodyBuilder generates dynamic vault API request bodies
- Template variable substitution from three sources:
  1. Config paths: `{{$^request-params.path}}` (from application.conf)
  2. Metadata keys: `{{'key'}}` (from TopicDirective.metadata Map)
  3. Directive fields: `{{fieldName}}` (from TopicDirective built-in fields)
- Security validation: Config paths MUST use `request-params.*` namespace
- Error accumulation: Returns all errors, not just first failure

**Response Mapping Phase** (Original Implementation)
- Maps vault JSON to intermediate `VaultCredentials` model
- User provides YAML/JSON mapping file with JSONPath expressions
- Supports transformations (base64Decode, concat, etc.)
- Generic and reusable across any vault structure

**JAAS Construction Phase** (Framework-Controlled)
- Constructs `jaasConfig` from `VaultCredentials` + OAuth config
- Creates final `SecurityDirective`
- Ensures jaasConfig format is correct and secure

### 2. VaultCredentials Intermediate Model

```scala
/**
 * Intermediate credentials model representing raw vault data
 *
 * This model contains the mapped fields from vault JSON.
 * The vault service will construct jaasConfig from these fields.
 */
private[vault] case class VaultCredentials(
  topic: String,
  role: String,
  clientId: String,
  clientSecret: String
)
```

**Why separate from SecurityDirective?**
- SecurityDirective includes `jaasConfig` which is constructed, not mapped
- Clear separation of concerns: mapping vs construction
- VaultCredentials is private to vault package (vault-specific, not generic Rosetta)
- Enforces that jaasConfig is always correctly formatted by framework

### 3. Rosetta Mapping Configuration

Users provide a YAML or JSON file (detected by extension):

```yaml
# vault-credentials-mapping.yaml
mappings:
  - targetField: topic
    sourcePath: $.kafka.topics[0].name

  - targetField: role
    sourcePath: $.kafka.credentials.role
    transformations:
      - type: toUpper

  - targetField: clientId
    sourcePath: $.kafka.credentials.oauth.client_id

  - targetField: clientSecret
    sourcePath: $.kafka.credentials.oauth.client_secret
    transformations:
      - type: base64Decode
```

Configuration path in `application.conf`:
```hocon
test-probe.vault.rosetta-mapping-path = "classpath:rosetta/vault-credentials-mapping.yaml"
```

### 4. Request Template Configuration (NEW - Added 2025-10-23)

RosettaConfig now includes a `request-template` field for building vault API request bodies:

```yaml
# azure-vault-mapping.yaml (complete bidirectional config)
request-template:
  client-app-id: "{{$^request-params.vault-requests.mf-team.client-app-id}}"
  blood-type: "{{'blood-type'}}"
  topic: "{{topic}}"

mappings:
  - targetField: clientId
    sourcePath: $.credentials.clientId
  # ... response mappings
```

**Three Variable Substitution Patterns:**

**Pattern 1: Config Path** - `{{$^request-params.path}}`
- Resolves from `application.conf`
- Security: MUST start with `request-params.` namespace
- Example: `{{$^request-params.vault-requests.mf-team.client-app-id}}`
- Regex validation: `^request-params\.[a-zA-Z0-9._-]+$`
- Error: "Invalid config path: 'sensitive.password'. Must start with 'request-params.' namespace"

**Pattern 2: Metadata Key** - `{{'key'}}`
- Resolves from `TopicDirective.metadata` Map (note single quotes)
- Populated from block storage YAML (test-metadata.yaml)
- Example: `{{'blood-type'}}` → "O-negative"
- Helpful errors: "Metadata key 'missing-key' not found. Available keys: [blood-type, test-run-id]"

**Pattern 3: Directive Field** - `{{fieldName}}`
- Resolves from `TopicDirective` built-in fields (no quotes, no prefix)
- Valid fields: `topic`, `role`, `clientPrincipal`
- Example: `{{topic}}` → "orders.events"
- Error: "Invalid directive field: 'invalidField'. Valid fields: [topic, role, clientPrincipal]"

**Security Validation:**
```scala
// RequestBodyBuilder.validateConfigPath() enforces namespace
val RequiredConfigPrefix = "request-params."
val SafeConfigPathRegex = """^request-params\.[a-zA-Z0-9._-]+$""".r

// Prevents access to sensitive config outside namespace:
// ✅ Valid: {{$^request-params.vault-requests.oauth-id}}
// ❌ Invalid: {{$^database.password}}
// ❌ Invalid: {{$^vault.sensitive.key}}
```

**Error Accumulation:**
RequestBodyBuilder accumulates all errors (doesn't fail-fast):
```scala
// If template has 3 invalid variables, returns all 3 errors:
Left(VaultMappingException(
  """Request body building failed for topic 'orders.events':
     |  - Invalid config path: 'sensitive.password'
     |  - Metadata key 'missing-key' not found
     |  - Invalid directive field: 'invalidField'""".stripMargin
))
```

**Integration with AzureVaultService:**
```scala
def invokeVault(topicDirective: TopicDirective): Future[String] = {
  RequestBodyBuilder.build(topicDirective, rosettaConfig, appConfig) match {
    case Left(error) => Future.failed(error)
    case Right(payload) =>
      httpClient.post(
        uri = functionUrl,
        jsonPayload = payload, // <-- Dynamic request body
        headers = Map("x-functions-key" -> functionKey)
      )
  }
}
```

### 5. JSONPath + Transformation DSL

**JSONPath Support:**
- Nested field access: `$.meta.requestId`
- Array wildcards: `$.users[*].email`
- Array operations: `$.users.length()`
- Array indexing: `$.users[0].name`
- Optional chaining: `$.a?.b?.c` (return None if any field missing)

**Transformation Functions:**
- `base64Decode`: Decode base64-encoded secrets
- `base64Encode`: Encode to base64
- `concat`: Concatenate multiple fields
- `prefix`: Add prefix to value
- `suffix`: Add suffix to value
- `toUpper`: Convert to uppercase
- `toLower`: Convert to lowercase
- `default`: Provide default value if field missing

**Transformation Chaining:**
```yaml
- targetField: clientSecret
  sourcePath: $.oauth.secret
  transformations:
    - type: base64Decode
    - type: prefix
      value: "oauth:"
```

### 5. JaasConfig Construction

```scala
object JaasConfigBuilder {
  def build(
    clientId: String,
    clientSecret: String,
    tokenEndpoint: String,  // from application.conf
    scope: Option[String] = None
  ): String = {
    s"""org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required
       |oauth.client.id="$clientId"
       |oauth.client.secret="$clientSecret"
       |oauth.token.endpoint.uri="$tokenEndpoint"${scope.map(s => s""" oauth.scope="$s"""").getOrElse("")};""".stripMargin
  }
}
```

**Why separate JaasConfigBuilder?**
- jaasConfig format is security-critical (must be correct)
- Framework controls the template (not user-configurable)
- OAuth endpoint comes from framework config, not vault
- Easier to audit for security compliance
- Single point of change if JAAS format evolves

### 6. Implementation Modules

```
test-probe-common/src/main/scala/io/distia/probe/common/rosetta/
├── RosettaMapper.scala           # Generic JSONPath + transformation engine
├── RosettaTransformations.scala  # Generic transformation ADT + implementations
└── RosettaConfig.scala           # Generic mapping configuration model + parsers

test-probe-services/src/main/scala/io/distia/probe/services/vault/
├── RequestBodyBuilder.scala      # Request template variable substitution (NEW)
├── VaultCredentialsMapper.scala  # Vault-specific VaultCredentials mapper
└── JaasConfigBuilder.scala       # Kafka JAAS-specific config construction

test-probe-services/src/main/scala/io/distia/probe/services/builder/modules/
├── AzureVaultService.scala       # Azure Vault integration (uses RequestBodyBuilder)
├── AwsVaultService.scala         # AWS Secrets Manager integration
└── LocalVaultService.scala       # Local file-based vault (for testing)
```

**Design Rationale:**
- **Generic utilities in common**: RosettaMapper, RosettaTransformations, and RosettaConfig are reusable for any JSON mapping scenario, not vault-specific
- **Domain-specific implementations in services**: RequestBodyBuilder, VaultCredentialsMapper and JaasConfigBuilder are specific to vault/Kafka use cases and belong in the services module
- **Clear separation**: Rosetta is a generic mapping framework; vault/JAAS implementations are consumers of that framework
- **RequestBodyBuilder location**: Lives in services/vault because it's vault-specific (builds vault API request bodies), not a generic utility

## Consequences

### Positive

✅ **Framework users can map any vault structure without code changes**
- Define mapping in YAML/JSON, no Scala code required
- Supports 6+ nesting levels, arrays, complex transformations
- Vault structure changes don't require framework redeployment

✅ **Type-safe with compile-time validation**
- VaultCredentials is a case class with required fields
- Circe decoders ensure type safety during mapping
- Missing fields accumulate errors (not fail-fast) for better diagnostics

✅ **jaasConfig is always correctly formatted**
- Framework controls construction via JaasConfigBuilder
- Security-critical configuration is not user-editable
- OAuth endpoint comes from validated framework config

✅ **Extensible transformation system**
- Easy to add new transformation functions
- Transformations can be chained for complex logic
- Generic design supports any target model (not just VaultCredentials)

✅ **Supports both YAML and JSON mapping files**
- YAML for readability (recommended)
- JSON for programmatic generation
- Auto-detection by file extension

✅ **Clear separation of concerns**
- Rosetta: Dynamic mapping (user-configured)
- VaultService: Business logic + jaasConfig construction (framework-controlled)
- JaasConfigBuilder: Security-critical formatting (framework-controlled)

### Negative

❌ **Adds dependency on circe-yaml**
- Increases artifact size by ~500KB
- Another library to maintain and version

❌ **Learning curve for JSONPath + transformation syntax**
- Framework users must learn the mapping DSL
- More complex than static mapping code
- Need comprehensive documentation with examples

❌ **Runtime mapping vs compile-time**
- Mapping errors discovered at runtime, not compile-time
- Need thorough testing of mapping files
- Validation during preFlight helps but not foolproof

❌ **Two-step architecture adds slight complexity**
- VaultCredentials → SecurityDirective requires understanding of flow
- More classes than direct vault JSON → SecurityDirective mapping

### Mitigation

- **Dependency Size**: circe-yaml 0.15.3 is small (~500KB), acceptable for functionality gain
- **Learning Curve**: Comprehensive documentation with 15+ real-world examples in API docs
- **Runtime Errors**: Validation during preFlight phase fails fast before any tests run
- **Complexity**: Clear documentation of two-step flow + architecture diagrams

## Alternatives Considered

### Alternative 1: Hard-coded Vault Adapters

Create separate adapter classes for each vault type:
- `AwsSecretsManagerAdapter`
- `HashiCorpVaultAdapter`
- `AzureKeyVaultAdapter`

**Rejected Because:**
- ❌ Not extensible to custom enterprise vaults
- ❌ Framework needs code changes for every new vault structure
- ❌ Tight coupling between framework and vault implementations
- ❌ Hard to test without actual vault instances

### Alternative 2: Direct SecurityDirective Mapping (No Intermediate Model)

Map vault JSON directly to SecurityDirective, including jaasConfig in mapping file.

**Rejected Because:**
- ❌ jaasConfig format is security-critical, should not be user-editable
- ❌ OAuth endpoint is framework config, not vault data
- ❌ Users could create invalid jaasConfig formats
- ❌ Harder to audit for security compliance

### Alternative 3: Scala-based Mapping DSL

Provide Scala trait for users to implement custom mappers:
```scala
trait VaultMapper {
  def mapToSecurityDirective(json: Json): Either[Error, SecurityDirective]
}
```

**Rejected Because:**
- ❌ Requires Scala knowledge (excludes Java/Kotlin users)
- ❌ Requires recompiling framework for mapping changes
- ❌ Harder to test mapping changes (compile + deploy vs edit YAML)
- ❌ Doesn't align with "make it easy to test" philosophy

### Alternative 4: GraphQL-style Query Language

Use GraphQL query syntax for field selection.

**Rejected Because:**
- ❌ Over-engineered for simple field mapping
- ❌ Heavier dependency (GraphQL parser)
- ❌ Transformation support would still need custom syntax
- ❌ JSONPath is simpler and well-understood

## Implementation Notes

### Phase 1: Core Rosetta Engine
- `RosettaMapper`: 150-200 lines (JSONPath resolver + transformation integration)
- `RosettaTransformations`: 100-150 lines (ADT + 7 transformation implementations)
- `RosettaConfig`: 100-150 lines (config model + YAML/JSON parsers)

### Phase 2: VaultCredentials Integration
- `VaultCredentialsMapper`: 100-150 lines (mapper + error accumulation)
- `JaasConfigBuilder`: 30-50 lines (construction logic + security validation)

### Phase 3: Vault Service Integration
- Update `AwsVaultService.fetchSecurityDirectives`: 50-100 lines
- Update `LocalVaultService.fetchSecurityDirectives`: 50-100 lines

### Testing Strategy
- **RosettaMapperSpec**: 50+ tests with deep nesting examples (6+ levels)
- **VaultCredentialsMapperSpec**: 25+ tests with real-world vault structures
- **JaasConfigBuilderSpec**: 10+ tests for format validation
- Target: 70%+ code coverage

### Security Considerations
1. **No credential logging**: Rosetta mapper must not log JSON values
2. **Validate field types**: String fields only (prevent injection)
3. **jaasConfig escaping**: Properly escape quotes in client_id/client_secret
4. **Error messages**: Don't leak credential values in error messages
5. **Transformation safety**: base64Decode validates input format

### Future Enhancements
1. **Conditional mappings**: Map different paths based on conditions
   ```yaml
   - targetField: clientId
     sourcePath: $.prod.oauth.client_id
     condition: {field: $.environment, equals: "production"}
   ```
2. **Regex transformations**: Extract values using regex patterns
3. **Multi-vault aggregation**: Fetch from multiple vaults and merge
4. **Mapping validation CLI**: Tool to test mapping files before deployment
5. **Caching layer**: Cache VaultCredentials with TTL to reduce vault queries

## References

**Implementation Files:**
- RequestBodyBuilder: `test-probe-services/src/main/scala/io/distia/probe/services/vault/RequestBodyBuilder.scala` (150 lines)
- AzureVaultService: `test-probe-services/src/main/scala/io/distia/probe/services/builder/modules/AzureVaultService.scala` (lines 118-162: invokeVault)
- VaultCredentialsMapper: `test-probe-services/src/main/scala/io/distia/probe/services/vault/VaultCredentialsMapper.scala`
- JaasConfigBuilder: `test-probe-services/src/main/scala/io/distia/probe/services/vault/JaasConfigBuilder.scala`

**Documentation:**
- API Reference: `docs/api/rosetta-vault-mapping-api.md` (comprehensive guide with examples)
- Bidirectional Flow Diagram: `docs/architecture/diagrams/sequences/rosetta-bidirectional-flow.mermaid`
- Component Diagram: `docs/architecture/diagrams/components/rosetta-vault-mapping-components.mermaid`
- Response Flow Diagram: `docs/architecture/diagrams/sequences/rosetta-vault-mapping-sequence.mermaid`
- Usage Examples: `docs/examples/rosetta/` (YAML templates, application.conf examples, README)

**External References:**
- Circe Documentation: https://circe.github.io/circe/
- circe-yaml: https://github.com/circe/circe-yaml
- JSONPath Specification: https://goessner.net/articles/JsonPath/
- JAAS Configuration: https://docs.oracle.com/javase/8/docs/technotes/guides/security/jgss/tutorials/LoginConfigFile.html
- Kafka SASL/OAUTHBEARER: https://kafka.apache.org/documentation/#security_sasl_oauthbearer

---

## Document History

- 2025-10-23: **Major Update** - Added RequestBodyBuilder implementation section (request template building)
  - Added Section 4: Request Template Configuration with three variable patterns
  - Updated Section 1: Bidirectional Architecture (request + response)
  - Updated Section 6: Implementation Modules (added RequestBodyBuilder, AzureVaultService)
  - Updated References: Added implementation files, diagrams, examples
- 2025-10-21: Initial ADR created documenting Rosetta mapping pattern decision (response mapping only)
