# ADR-STORAGE-005: Block Storage Abstraction (v2 - Comprehensive)

**Status:** Accepted
**Date:** 2025-10-27
**Decision Makers:** Engineering Team
**Related:** BlockStorageImplementationPlan.md

---

## Context

The Test-Probe framework needs to fetch test artifacts (Cucumber feature files, topic configurations) from cloud storage providers (AWS S3, Azure Blob Storage, GCP Cloud Storage) and local in-memory storage for testing. These artifacts must be streamed to an in-memory file system (JIMFS) for test execution, and evidence must be uploaded back to storage after execution.

### Requirements

1. **Multi-Provider Support:** Must work with AWS, Azure, GCP, and local JIMFS
2. **Streaming:** Handle large test artifacts without loading entire files into memory
3. **Immutability:** Test artifacts are read-only; only evidence is written back
4. **Isolation:** Each test execution must have isolated storage
5. **Cleanup:** JIMFS directories must be cleaned up after evidence upload

### Constraints

- Tests are immutable - no modification of source artifacts
- Evidence must be uploaded regardless of test success/failure
- Must support full URI bucket paths (no config-based URI construction)
- Must validate test structure (features/ directory, topic directive file)

---

## Decision

We will implement a **unified BlockStorageService abstraction** with provider-specific implementations for AWS S3, Azure Blob Storage, GCP Cloud Storage, and local JIMFS.

### Service Interface

```scala
trait BlockStorageService {
  def fetchFromBlockStorage(
    testId: UUID,
    bucketUri: String
  )(using ec: ExecutionContext): Future[BlockStorageDirective]

  def uploadToBlockStorage(
    testId: UUID,
    bucketUri: String,
    evidenceDir: String
  )(using ec: ExecutionContext): Future[Unit]
}
```

### BlockStorageDirective Model

```scala
case class BlockStorageDirective(
  jimfsLocation: String,              // JIMFS path to features/ directory
  evidenceDir: String,                 // JIMFS path to evidence/ directory
  topicDirectives: List[TopicDirective], // Parsed topic configurations
  bucket: String                       // Original bucket URI for evidence upload
)
```

### Provider Implementations

1. **LocalBlockStorageService** - For testing/development (JIMFS only, no actual cloud storage)
2. **AwsBlockStorageService** - AWS S3 using AWS SDK v2
3. **AzureBlockStorageService** - Azure Blob Storage using Azure SDK
4. **GcpBlockStorageService** - GCP Cloud Storage using GCP SDK

---

## Rationale

### Why Abstraction?

**Benefit 1: Provider Agnostic**
- Teams can use any cloud provider without code changes
- Easy migration between providers (just change config)
- Local development doesn't require cloud credentials

**Benefit 2: Testability**
- LocalBlockStorageService enables fast, isolated testing
- No external dependencies for unit/component tests
- Mock implementations for specific test scenarios

**Benefit 3: Extensibility**
- New providers (e.g., MinIO, Ceph) can be added without changing callers
- Custom storage backends for specialized environments
- Easy to add features (compression, encryption) at abstraction layer

**Benefit 4: Consistent Behavior**
- All providers implement same interface with same semantics
- Error handling standardized across implementations
- Validation logic shared (features/ directory, topic directives)

### Why Not Alternatives?

**Alternative 1: Direct SDK Usage in Actors**
- ❌ Couples actors to specific cloud providers
- ❌ Violates separation of concerns
- ❌ Difficult to test without cloud credentials
- ❌ Duplicates validation logic across actors

**Alternative 2: Single Universal Storage Library (e.g., Apache Commons VFS)**
- ❌ Adds dependency bloat
- ❌ May not support all provider features
- ❌ Performance overhead from abstraction layer
- ❌ Less control over streaming implementation

**Alternative 3: Actor-Based Storage Access**
- ❌ Unnecessary actor overhead for I/O operations
- ❌ Complex supervision strategies for transient failures
- ❌ Difficult to manage backpressure
- ✅ **Chosen approach:** Services with Future-based API

---

## Implementation Details

### Factory Pattern for Provider Selection

```scala
object BlockStorageServiceFactory {
  def create(
    config: BlockStorageConfig,
    jimfsManager: JimfsManager,
    topicDirectiveMapper: TopicDirectiveMapper
  ): BlockStorageService = config.provider match {
    case "local" => new LocalBlockStorageService(...)
    case "aws" => new AwsBlockStorageService(...)
    case "azure" => new AzureBlockStorageService(...)
    case "gcp" => new GcpBlockStorageService(...)
  }
}
```

### Lifecycle Integration

1. **Bootstrap:** BlockStorageService created via factory during application startup
2. **Actor Usage:** BlockStorageActor receives service via BuilderContext
3. **Cleanup:** Service handles JIMFS cleanup after evidence upload
4. **Shutdown:** SDK clients closed during graceful shutdown

### Error Handling Strategy

All implementations throw consistent exception types:
- `MissingFeaturesDirectoryException` - features/ directory not found
- `EmptyFeaturesDirectoryException` - features/ directory is empty
- `MissingTopicDirectiveFileException` - Topic directive file not found
- `InvalidTopicDirectiveFormatException` - YAML/JSON parse errors
- `BlockStorageException` - Generic storage operation failures

---

## Consequences

### Positive

✅ **Clean Separation:** Storage concerns isolated from actor system
✅ **Easy Testing:** LocalBlockStorageService enables fast tests
✅ **Provider Flexibility:** Switch providers via configuration
✅ **Consistent API:** Same interface across all implementations
✅ **Future-Proof:** Easy to add new providers or features

### Negative

⚠️ **Implementation Overhead:** Must implement 4 providers (local, AWS, Azure, GCP)
⚠️ **SDK Dependencies:** Adds 3 cloud SDKs to dependencies (AWS, Azure, GCP)
⚠️ **Configuration Complexity:** Separate config section for each provider

### Neutral

⚡ **Performance:** SDK-native streaming should be sufficient (not using Pekko Streams)
⚡ **Memory Usage:** JIMFS overhead mitigated by cleanup after evidence upload
⚡ **SDK Versions:** Must track and update 3 separate SDK dependencies

---

## Follow-Up Decisions

- **ADR-STORAGE-006:** SDK Streaming Approach (why SDK-native vs Pekko Streams)
- **ADR-STORAGE-007:** JIMFS Architecture (shared FileSystem, directory lifecycle)
- **ADR-STORAGE-008:** Topic Directive Format (YAML structure and parsing)

---

## References

- **Implementation Plan:** `working/blockStorage/BlockStorageImplementationPlan.md`
- **Topic Directive Model:** [topic-directive-model.md](../../api/topic-directive-model.md)
- **Service Architecture:** [04.1-block-storage-architecture.md](../blueprint/04%20Adapters/04.1-block-storage-architecture.md)
- **VaultService Pattern:** `test-probe-services/src/main/scala/com/company/probe/services/vault/`

---

## Review History

- **2025-10-27:** Initial ADR created (Paired Programming Session)

---

## Appendix: Example Usage

```scala
val blockStorageService = BlockStorageServiceFactory.create(config, jimfsManager, mapper)

val directive: BlockStorageDirective = blockStorageService
  .fetchFromBlockStorage(
    testId = UUID.fromString("..."),
    bucketUri = "s3://my-bucket/team-a/test-123/"
  )
  .await

directive.jimfsLocation        // "/testId/features"
directive.evidenceDir           // "/testId/evidence"
directive.topicDirectives       // List[TopicDirective]
directive.bucket                // "s3://my-bucket/team-a/test-123/"

blockStorageService.uploadToBlockStorage(
  testId = UUID.fromString("..."),
  bucketUri = directive.bucket,
  evidenceDir = directive.evidenceDir
).await
```
