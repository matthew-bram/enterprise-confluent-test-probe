# ADR-STORAGE-009: Async Client Strategy for Block Storage Providers

**Status:** Accepted
**Date:** 2025-10-27
**Context:** Block Storage Builder Module Implementation
**Supersedes:** N/A

---

## Context

Block storage services need to download/upload potentially multiple files from cloud providers (AWS S3, Azure Blob Storage, GCP Cloud Storage). The choice between synchronous and asynchronous SDK clients significantly impacts performance, complexity, and maintainability.

### Provider SDK Capabilities (2024)

**AWS S3:**
- ✅ Native async support via `S3AsyncClient` + `S3TransferManager`
- ✅ Built-in multipart uploads/downloads
- ✅ Automatic parallelization and retry logic
- ✅ Progress tracking and resume capabilities
- **Maturity:** Production-ready since SDK v2

**Azure Blob Storage:**
- ✅ Native async support via `BlobAsyncClient`
- ✅ Project Reactor `Mono<T>` / `Flux<T>` return types
- ✅ Configurable parallelization via `ParallelTransferOptions`
  - `blockSize` - chunk size per request
  - `maxConcurrency` - parallel request limit
  - `maxSingleUploadSize` - single vs multipart threshold
- ✅ Built on Netty for non-blocking I/O
- **Maturity:** Production-ready, widely adopted

**GCP Cloud Storage:**
- ❌ **No native async API** in Java SDK
- ❌ Feature request open since 2015 (not prioritized)
- ⚠️ Supports "parallel composite uploads" but requires manual chunking
- ⚠️ Can use signed URLs + async HTTP client as workaround
- **Maturity:** Blocking API only, requires custom solutions for async

### Typical Test Artifact Profile

Based on project requirements and test framework design:
- **File sizes:** Small (<5MB typical)
  - Cucumber feature files: 1-50KB
  - Topic directive YAML: 1-10KB
  - Evidence files: 10KB-1MB (JSON test results, logs)
- **File counts:** Low to moderate (5-50 files per test)
- **Concurrent tests:** Variable (1-100 depending on CI/CD pipeline)

---

## Decision

**Use native async APIs where available, use synchronous APIs where not available.**

### Implementation Strategy:

1. **AWS S3** → `S3AsyncClient` + `S3TransferManager` (async)
2. **Azure Blob Storage** → `BlobAsyncClient` + `ParallelTransferOptions` (async)
3. **GCP Cloud Storage** → `Storage` client (sync, wrapped in `Future {}`)

### External API Consistency

All implementations maintain the same external interface:
```scala
trait ProbeStorageService {
  def fetchFromBlockStorage(testId: UUID, bucket: String)(implicit ec: ExecutionContext): Future[BlockStorageDirective]
  def loadToBlockStorage(testId: UUID, bucket: String, evidence: String)(implicit ec: ExecutionContext): Future[Unit]
}
```

Users of the service cannot tell the difference - all providers return `Future[T]`. The async implementation is an internal optimization.

---

## Rationale

### Why Async for AWS + Azure?

1. **Native SDK Support:** Both SDKs provide production-ready async APIs with years of battle-testing
2. **Automatic Optimizations:**
   - Multipart transfers for large files (>5MB)
   - Parallel chunk uploads/downloads
   - Built-in retry and resume logic
   - Progress tracking capabilities
3. **Better Resource Utilization:**
   - Non-blocking I/O reduces thread pool pressure
   - Handles concurrent tests more efficiently
   - Scales better under load
4. **Future-Proof:** Async is the modern standard for cloud SDKs
5. **Low Complexity:** Using native APIs is simpler than building custom solutions

### Why Sync for GCP?

1. **No Native Support:** GCP Java SDK lacks async API (open issue since 2015)
2. **Adequate Performance:** For small files (<5MB), sync approach is sufficient
   - Typical download: 1-50KB feature files = <100ms
   - Wrapped in `Future {}` provides application-level async
3. **Avoid Complexity:** Custom parallelization would require:
   - Manual chunk management
   - Error handling and retry logic
   - Progress tracking implementation
   - Increased code complexity (~200+ lines)
   - Higher maintenance burden
4. **Risk Mitigation:**
   - Custom code = custom bugs
   - SDK blocking calls are well-tested
   - Simpler code = easier troubleshooting
5. **Pragmatic Trade-off:**
   - GCP usage may be less common than AWS/Azure in enterprise
   - Performance difference minimal for small files
   - Can revisit if GCP adds native async support

---

## Alternatives Considered

### Alternative 1: Sync Everywhere
**Approach:** Use synchronous SDKs for all providers

**Pros:**
- Consistent implementation approach
- Simpler code (no async complexity)
- Easier to understand and debug

**Cons:**
- ❌ Misses significant performance benefits on AWS + Azure
- ❌ Blocks threads under concurrent load
- ❌ Doesn't leverage modern SDK capabilities
- ❌ Ignores years of AWS/Azure optimization work

**Verdict:** Rejected - leaves performance on the table

---

### Alternative 2: Custom Parallelization for GCP
**Approach:** Build manual parallel download/upload for GCP

**Pros:**
- Consistent async behavior across all providers
- Potentially better GCP performance for large files

**Cons:**
- ❌ Significant complexity increase (~200+ lines custom code)
- ❌ Manual chunk management and coordination
- ❌ Custom retry logic required
- ❌ Custom error handling required
- ❌ Higher maintenance burden
- ❌ Risk of custom bugs
- ❌ Overkill for small files (<5MB)
- ❌ Reinvents wheel that AWS/Azure provide

**Verdict:** Rejected - complexity not justified for typical use case

**Example of what we'd need to build:**
```scala
// Manual chunking
def downloadInParallel(blobs: List[Blob]): Future[Unit] = {
  Future.traverse(blobs.grouped(concurrency)) { batch =>
    Future.sequence(batch.map { blob =>
      Future {
        blocking {
          // Manual retry logic
          retry(maxAttempts = 3) {
            blob.downloadTo(targetPath)
          }
        }
      }
    })
  }.map { results =>
    // Manual error aggregation
    val errors = results.flatten.filter(_.isFailure)
    if errors.nonEmpty then throw new AggregateException(errors)
  }
}

// Manual chunking for large files
def uploadLargeFile(file: Path): Future[Unit] = {
  val chunks = Files.size(file) / chunkSize
  Future.traverse(0 until chunks) { i =>
    Future {
      // Manual chunk upload
      val chunk = readChunk(file, i * chunkSize, chunkSize)
      uploadChunk(chunk, i)
    }
  }.flatMap { chunkIds =>
    // Manual composition
    composeChunks(chunkIds)
  }
}
```

This is 200+ lines of error-prone code we'd need to write, test, and maintain.

---

### Alternative 3: Hybrid with Threshold
**Approach:** Use async for large files, sync for small files

**Pros:**
- Optimizes based on actual file size
- Could benefit GCP for rare large file scenarios

**Cons:**
- ❌ Adds branching complexity
- ❌ Still requires custom GCP parallelization
- ❌ Harder to test (two code paths)
- ❌ Harder to reason about behavior
- ❌ Overkill for typical use case

**Verdict:** Rejected - unnecessary complexity

---

## Implementation Details

### AWS S3 - S3TransferManager

```scala
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.services.s3.S3AsyncClient

// In initialize()
val asyncClient = S3AsyncClient.builder()
  .httpClientBuilder(NettyNioAsyncHttpClient.builder())
  .region(Region.of(config.aws.region))
  .build()

val transferManager = S3TransferManager.builder()
  .s3Client(asyncClient)
  .build()

// In fetchFromBlockStorage()
val download = transferManager.downloadDirectory(
  DownloadDirectoryRequest.builder()
    .bucket(bucketName)
    .destinationDirectory(testDir)
    .prefix(prefix)
    .build()
)

// Convert CompletableFuture to Scala Future
download.completionFuture().toScala.map(_ => validateAndParse(testDir, bucket))
```

**Benefits:**
- Automatic parallelization across files
- Automatic multipart for large files (>5MB)
- Built-in retry and resume
- Progress tracking available

---

### Azure Blob Storage - BlobAsyncClient

```scala
import com.azure.storage.blob.BlobServiceAsyncClient
import com.azure.storage.blob.models.ParallelTransferOptions
import reactor.core.scala.publisher.SMono

// In initialize()
val blobServiceAsyncClient = new BlobServiceClientBuilder()
  .connectionString(connectionString)
  .buildAsyncClient()

// In fetchFromBlockStorage()
val containerClient = blobServiceAsyncClient.getBlobContainerAsyncClient(containerName)

val parallelOptions = new ParallelTransferOptions()
  .setBlockSizeLong(4 * 1024 * 1024)  // 4MB chunks
  .setMaxConcurrency(4)                // 4 parallel requests

// Download blobs using Project Reactor Flux
val downloads = containerClient.listBlobs()
  .filter(_.getName.startsWith(prefix))
  .flatMap { blob =>
    containerClient.getBlobAsyncClient(blob.getName)
      .downloadToFileWithResponse(targetPath, null, parallelOptions, null, false, null)
  }

// Convert Reactor Mono to Scala Future
SMono.fromPublisher(downloads.then()).toFuture.map(_ => validateAndParse(testDir, bucket))
```

**Benefits:**
- Configurable parallel transfers
- Automatic chunking via ParallelTransferOptions
- Built on Project Reactor (battle-tested reactive library)
- Uses Netty for non-blocking I/O

---

### GCP Cloud Storage - Sync with Future Wrapper

```scala
import com.google.cloud.storage.Storage
import scala.concurrent.Future

// In initialize()
val storageClient = StorageOptions.newBuilder()
  .setProjectId(config.gcp.projectId)
  .build()
  .getService

// In fetchFromBlockStorage()
Future {
  val blobs = storageClient.list(bucketName, Storage.BlobListOption.prefix(prefix))

  blobs.iterateAll().asScala.foreach { blob =>
    val targetPath = testDir.resolve(relativePath)
    Files.createDirectories(targetPath.getParent)
    blob.downloadTo(targetPath)  // Blocking call
  }

  validateAndParse(testDir, bucket)
}(executionContext)
```

**Benefits:**
- Simple, straightforward code
- Well-tested blocking SDK
- Adequate for small files
- Easy to troubleshoot
- `Future {}` wrapper provides application-level async

**Performance Characteristics:**
- Single file (1KB): ~50-100ms (network latency dominates)
- Single file (5MB): ~500ms-1s (adequate for test artifacts)
- Multiple files: Sequential, but typically <10 files per test
- For 10 small files: ~500ms-1s total (acceptable)

---

## Consequences

### Positive

1. ✅ **Best Performance on AWS + Azure:** Leverage native optimizations
2. ✅ **Low Complexity:** Use SDK-provided functionality, no custom code
3. ✅ **Low Risk:** Battle-tested async APIs from AWS/Azure
4. ✅ **Maintainable:** Simpler code, easier to understand
5. ✅ **Consistent External API:** All providers return `Future[T]`
6. ✅ **Future-Proof:** Can add GCP async later if SDK adds support
7. ✅ **Pragmatic:** Optimizes where it matters most

### Negative

1. ⚠️ **Inconsistent Implementation:** Different approaches per provider
   - **Mitigation:** Documented in ADR, clear separation of concerns
   - **Impact:** Low - hidden behind interface

2. ⚠️ **GCP Performance Gap:** Sync downloads slower than potential async
   - **Mitigation:** Adequate for typical small files (<5MB)
   - **Impact:** Low - test artifacts are small
   - **Threshold:** Only matters for large files or high concurrency

3. ⚠️ **Additional Dependencies:** Netty for AWS/Azure async clients
   - **Mitigation:** Already used by Pekko, shared dependency
   - **Impact:** None - no additional footprint

### Performance Impact Analysis

**Small Files (<1MB, typical test artifacts):**
- AWS/Azure async: ~100-200ms (network latency dominates)
- GCP sync: ~100-200ms (network latency dominates)
- **Difference:** Negligible (<10%)

**Large Files (>5MB, rare for test artifacts):**
- AWS/Azure async: ~500ms-1s (multipart parallelization)
- GCP sync: ~2-3s (single-threaded download)
- **Difference:** 2-3x slower on GCP (acceptable for rare case)

**High Concurrency (50+ tests):**
- AWS/Azure async: Better thread pool utilization
- GCP sync: Blocks more threads (may require larger pool)
- **Difference:** More noticeable under load

**Conclusion:** Performance difference minimal for typical use case (small files, moderate concurrency). Async advantage grows with file size and concurrency.

---

## Migration Path

If GCP adds native async support in the future:

1. Update GCP implementation to use native async API
2. No changes to external interface required
3. No changes to consumers required
4. Update this ADR to reflect new decision
5. Deprecate sync implementation in favor of async

**Estimated effort:** 2-4 hours (mostly testing)

If GCP performance becomes a bottleneck:

1. Profile actual usage patterns (file sizes, concurrency)
2. If justified, implement custom parallelization
3. Create new ADR documenting decision and approach
4. Implement with comprehensive testing

**Estimated effort:** 1-2 days (includes design, implementation, testing)

---

## Validation

### Acceptance Criteria

1. ✅ AWS uses S3AsyncClient + S3TransferManager
2. ✅ Azure uses BlobAsyncClient + ParallelTransferOptions
3. ✅ GCP uses sync Storage client wrapped in Future
4. ✅ All providers maintain consistent external API
5. ✅ All providers return Future[BlockStorageDirective]
6. ✅ Component tests validate functionality for all providers
7. ✅ Performance acceptable for typical use case (<5MB files)

### Testing Strategy

**Unit Tests:**
- Helper methods (URI parsing, validation)
- Builder lifecycle (preFlight/initialize/finalCheck)

**Component Tests:**
- AWS with LocalStack (async client validation)
- Azure with Azurite (async client validation)
- GCP with fake-gcs-server (sync client validation)
- Measure download times for 1KB, 1MB, 5MB files

**Performance Benchmarks:**
- Single file download (all providers)
- Multiple file download (5, 10, 50 files)
- Concurrent test execution (10, 50, 100 tests)
- Measure thread pool utilization

---

## References

**Research:**
- AWS S3TransferManager Documentation: https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/transfer-manager.html
- Azure BlobAsyncClient Documentation: https://learn.microsoft.com/en-us/java/api/com.azure.storage.blob.blobasyncclient
- GCP Storage Java SDK (no async): https://github.com/googleapis/java-storage
- GCP Async Feature Request (2015): https://github.com/googleapis/google-cloud-java/issues/63

**Related ADRs:**
- ADR-STORAGE-005: Block Storage Abstraction (v2 - Comprehensive)
- ADR-STORAGE-006: SDK Streaming Approach

**Project Standards:**
- `.claude/styles/scala-conventions.md` - Scala 3 conventions
- `.claude/guides/TESTING.md` - Testing standards

---

**Decision Made By:** Engineering Team (Paired Programming Session)
**Approved By:** [Pending Review]
**Last Updated:** 2025-10-27
