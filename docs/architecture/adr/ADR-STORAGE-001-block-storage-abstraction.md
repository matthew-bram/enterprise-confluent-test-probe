# ADR-STORAGE-001: Block Storage Abstraction Pattern

**Status**: Accepted
**Date**: 2025-10-20
**Component**: Block Storage Services
**Related Documents**:
- [04.1 Service Layer Architecture](../blueprint/04%20Adapters/04.1-service-layer-architecture.md)
- [05.3 Child Actors Integration](../blueprint/05%20State%20Machine/05.3-child-actors-integration.md)

---

## Context

We need to support multiple block storage backends (local jimfs for testing, S3/Azure/GCS for production) with a unified interface. Tests and evidence files must be fetched from and uploaded to different storage providers depending on environment (dev, staging, production).

## Decision

Use **ProbeStorageService trait with multiple implementations**:
- `LocalBlockStorageService` (jimfs - full implementation)
- `AwsBlockStorageService` (S3 - skeleton with TODOs)
- `AzureBlockStorageService` (Azure Blob - skeleton with TODOs)
- `GcpBlockStorageService` (GCS - skeleton with TODOs)

All implementations expose the same interface:
```scala
trait ProbeStorageService extends Feature with BuilderModule {
  def fetchFromBlockStorage(testId: UUID, bucket: String): Future[BlockStorageDirective]
  def loadToBlockStorage(jimfsLocation: String, testResult: TestExecutionResult): Future[Unit]
}
```

Configuration-driven backend selection:
```hocon
probe.storage.backend = "local" | "aws" | "azure" | "gcp"
```

## Consequences

### Positive

✅ **Supports multiple cloud providers out of the box**
- Enterprises can choose their preferred cloud provider
- No vendor lock-in
- Easy to migrate between providers

✅ **Easy to test with jimfs (no external dependencies)**
- Unit and component tests don't require S3/Azure/GCS
- Fast test execution (in-memory filesystem)
- Deterministic test behavior

✅ **Unified interface for all storage backends**
- Same code works with any backend
- Simplified actor layer (BlockStorageActor doesn't know which backend)
- Clear contract via trait definition

✅ **Extensible to new backends**
- Adding new provider = implement ProbeStorageService trait
- No changes to actor layer or core business logic

### Negative

❌ **Need to maintain 4+ implementations**
- Each cloud provider has different SDK
- Different authentication patterns (AWS IAM, Azure SAS tokens, GCP service accounts)
- Different error handling and retry strategies

❌ **Slight complexity in configuration**
- Need to document configuration for each backend
- Environment-specific configuration (dev uses jimfs, prod uses S3)

### Mitigation

- Skeletons include comprehensive TODOs with implementation guidance
- Configuration examples documented for each backend
- jimfs implementation serves as reference pattern
- Phased approach: jimfs first (spike), then production backends

## Alternatives Considered

### Alternative 1: Single S3 Implementation
- **Rejected**: Vendor lock-in, doesn't support Azure/GCS enterprises
- **Concern**: Many enterprises have multi-cloud strategies

### Alternative 2: Generic Object Storage Interface (S3-compatible)
- **Rejected**: Not all providers are S3-compatible (Azure Blob has different API)
- **Concern**: Would force lowest common denominator, lose provider-specific features

### Alternative 3: Plugin Architecture with Dynamic Loading
- **Rejected**: Over-engineered for current needs
- **Concern**: Adds complexity without clear benefit (4 implementations is manageable)

## Implementation Notes

**LocalBlockStorageService (jimfs)**:
- Full implementation (240 lines)
- Two-phase operation: fetch (storage → jimfs) and load (jimfs → storage)
- jimfs directory structure: `/jimfs/test-{testId}/features/` and `/evidence/`
- Stub topic directives for spike phase

**Cloud Service Skeletons (S3/Azure/GCS)**:
- 330-340 lines each
- Comprehensive TODOs with implementation guidance
- Configuration examples included
- Error handling patterns documented

**Future Enhancements**:
- Multipart upload for large files (S3/Azure/GCS all support)
- Presigned URLs for secure browser downloads
- Event notifications for async processing (S3 SNS, Azure Event Grid, GCP Pub/Sub)
- Cross-region replication for disaster recovery

## References

- Implementation: `working/storage/STORAGE-IMPLEMENTATION-SUMMARY.md`
- jimfs documentation: https://github.com/google/jimfs
- AWS S3 SDK: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html
- Azure Blob SDK: https://docs.microsoft.com/en-us/azure/storage/blobs/storage-quickstart-blobs-java
- GCS SDK: https://cloud.google.com/storage/docs/reference/libraries#client-libraries-install-java

---

## Document History

- 2025-10-20: Initial ADR created documenting block storage abstraction decision
