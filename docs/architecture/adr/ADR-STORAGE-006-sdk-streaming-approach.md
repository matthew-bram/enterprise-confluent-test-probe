# ADR-STORAGE-006: SDK-Native Streaming Approach

**Status:** Accepted
**Date:** 2025-10-27
**Decision Makers:** Engineering Team
**Related:** ADR-STORAGE-005, BlockStorageImplementationPlan.md

---

## Context

The Block Storage Service must stream test artifacts from cloud storage (S3, Azure Blob, GCS) to JIMFS and stream evidence back. We need to choose a streaming approach that balances simplicity, performance, and consistency with the rest of the codebase.

### Requirements

1. **Efficient Memory Usage:** Stream large files without loading entire contents into memory
2. **Backpressure Handling:** Prevent overwhelming consumers with data
3. **Error Handling:** Detect and handle streaming failures gracefully
4. **Simplicity:** Minimize complexity for file I/O operations
5. **Consistency:** Align with existing patterns where possible

### Constraints

- Must work with 3 different cloud provider SDKs (AWS, Azure, GCP)
- Must integrate with JIMFS (java.nio.file API)
- Must support both download (cloud → JIMFS) and upload (JIMFS → cloud)
- Evidence files may be large (multi-MB screenshots, logs)

---

## Decision

We will use **SDK-native streaming APIs** directly, rather than wrapping in Pekko Streams.

### Implementation Approach

**Download (Cloud → JIMFS):**
```scala
val getRequest = GetObjectRequest.builder()
  .bucket(bucketName)
  .key(key)
  .build()

s3Client.getObject(getRequest, targetPath)
```

**Upload (JIMFS → Cloud):**
```scala
val putRequest = PutObjectRequest.builder()
  .bucket(bucketName)
  .key(s3Key)
  .build()

s3Client.putObject(putRequest, sourcePath)
```

### SDK-Specific APIs

**AWS S3:**
- `S3Client.getObject(GetObjectRequest, Path)` - Streams to file
- `S3Client.putObject(PutObjectRequest, Path)` - Streams from file

**Azure Blob Storage:**
- `BlobClient.downloadStream(OutputStream)` - Streams to output
- `BlobClient.uploadFromFile(String)` - Streams from file

**GCP Cloud Storage:**
- `Blob.downloadTo(OutputStream)` - Streams to output
- `Storage.create(BlobInfo, InputStream)` - Streams from input

---

## Rationale

### Why SDK-Native Streaming?

**Benefit 1: Simplicity**
- Direct SDK usage with minimal ceremony
- No Pekko Streams setup (Source, Sink, Materializer)
- Fewer layers of abstraction
- Less code to maintain

**Benefit 2: SDK Optimizations**
- Cloud provider SDKs are optimized for their own storage
- Built-in retry logic and error handling
- Efficient chunk sizes and buffering
- Connection pooling handled by SDK

**Benefit 3: Direct java.nio.file Integration**
- AWS SDK can stream directly to `Path`
- Azure/GCP can stream to `OutputStream`/`InputStream`
- No need to bridge between Pekko Streams and Java NIO

**Benefit 4: Proven Pattern**
- Standard approach for file operations
- Well-documented in SDK examples
- Enterprise-friendly (familiar to ops teams)

### Why Not Pekko Streams?

**Reason 1: Overkill for File Operations**
```scala
Source.single(s3Object)
  .via(S3.download(bucket, key))
  .runWith(FileIO.toPath(targetPath))
  .flatMap { result =>
    if result.wasSuccessful then Future.successful(())
    else Future.failed(new IOException(...))
  }
```
This adds 4+ lines of stream setup for what SDK does in 1 line.

**Reason 2: Complexity Without Benefit**
- Pekko Streams excels at complex transformations and backpressure
- Simple file copy doesn't need transformation pipeline
- Backpressure handled by SDK buffering
- Additional complexity adds debugging surface area

**Reason 3: Inconsistent with VaultService**
- VaultService uses direct HTTP client (not Akka HTTP Streams)
- BlockStorageService should follow similar pattern
- Only KafkaProducerStreamingActor/KafkaConsumerStreamingActor use Pekko Streams (justified for Kafka integration)

**Reason 4: Multiple SDK Adapters Required**
- Would need separate Pekko Streams Source/Sink for each SDK
- AWS has Alpakka S3, but Azure/GCP have no official Alpakka connectors
- Would have to write custom connectors (significant effort)

---

## Comparison: SDK vs Pekko Streams

### SDK-Native (Chosen)

```scala
def fetchFile(bucket: String, key: String, targetPath: Path): Unit = {
  val request = GetObjectRequest.builder()
    .bucket(bucket)
    .key(key)
    .build()

  s3Client.getObject(request, targetPath)
}
```

**Lines:** 7
**Dependencies:** AWS SDK only
**Complexity:** Low
**Backpressure:** SDK buffering
**Error Handling:** Try/catch or SDK retry config

### Pekko Streams Alternative

```scala
def fetchFile(bucket: String, key: String, targetPath: Path)(
  implicit system: ActorSystem,
  mat: Materializer
): Future[IOResult] = {

  S3.download(bucket, key)
    .runWith(FileIO.toPath(targetPath))
    .flatMap { result =>
      if result.wasSuccessful then Future.successful(result)
      else Future.failed(new IOException(s"Download failed for $key"))
    }
}
```

**Lines:** 13
**Dependencies:** AWS SDK + Alpakka S3 + Pekko Streams
**Complexity:** Medium (need ActorSystem, Materializer)
**Backpressure:** Pekko Streams backpressure
**Error Handling:** Future combinators + stream error handling

---

## Implementation Details

### Error Handling

**Transient Failures:**
- SDK retry logic handles network timeouts, rate limits
- Configure retry attempts via SDK client builder
- Log retries for observability

**Permanent Failures:**
- Wrap SDK exceptions in custom `BlockStorageException`
- Preserve cause chain for debugging
- Clean up JIMFS on failure

**Example:**
```scala
try {
  s3Client.getObject(request, targetPath)
} catch {
  case ex: S3Exception =>
    jimfsManager.deleteTestDirectory(testId)
    throw BlockStorageException(
      s"Failed to fetch from S3 bucket $bucket: ${ex.getMessage}",
      ex
    )
}
```

### Backpressure Strategy

**SDK Buffering:**
- Cloud provider SDKs implement internal buffering
- Default buffer sizes (e.g., AWS SDK uses 8KB chunks)
- Sufficient for file operations (no need for explicit backpressure)

**JIMFS Write Speed:**
- In-memory filesystem writes are fast
- Unlikely to be bottleneck
- If needed, can tune SDK buffer sizes

---

## Consequences

### Positive

✅ **Simpler Implementation:** 50% less code compared to Pekko Streams
✅ **Fewer Dependencies:** No Alpakka connectors needed
✅ **Consistent Pattern:** Aligns with VaultService approach
✅ **SDK Optimizations:** Leverage provider-specific tuning
✅ **Easy Debugging:** Fewer layers of abstraction

### Negative

⚠️ **No Explicit Backpressure Control:** Rely on SDK buffering
⚠️ **Less Composable:** Can't easily add transformations mid-stream
⚠️ **No Unified Streaming API:** Each SDK has slightly different API

### Neutral

⚡ **Performance:** SDK streaming should be sufficient for this use case
⚡ **Retry Logic:** SDK retry is adequate; can enhance if needed
⚡ **Future Migration:** Could wrap in Pekko Streams later if requirements change

---

## Alternatives Considered

### Alternative 1: Pekko Streams with Alpakka

**Pros:**
- Unified streaming abstraction
- Explicit backpressure control
- Composable transformations

**Cons:**
- Azure and GCP have no official Alpakka connectors
- Would need custom connectors (significant effort)
- Adds complexity for simple file copy
- Requires ActorSystem and Materializer

**Decision:** Rejected due to complexity and missing connectors

---

### Alternative 2: Apache Commons VFS

**Pros:**
- Unified API for all providers
- Well-tested in enterprise environments

**Cons:**
- Another abstraction layer
- May not support all provider features
- Performance overhead
- Adds dependency bloat

**Decision:** Rejected due to unnecessary abstraction

---

### Alternative 3: Custom Streaming Implementation

**Pros:**
- Full control over streaming behavior
- Can optimize for specific use case

**Cons:**
- Significant development effort
- Must handle buffering, retries, errors manually
- Likely less optimized than SDK implementations
- Higher maintenance burden

**Decision:** Rejected due to "not invented here" syndrome

---

## Validation

### Performance Testing (Future Phase)

When performance testing is implemented:

1. **Benchmark large file downloads** (100MB+)
2. **Measure memory usage** during streaming
3. **Test concurrent downloads** (multiple tests)
4. **Validate error recovery** (network interruptions)

**Acceptance Criteria:**
- Memory usage remains constant regardless of file size
- Download speed within 10% of SDK native performance
- No connection leaks after repeated operations

---

## Follow-Up Decisions

- **ADR-STORAGE-007:** JIMFS Architecture (how streaming integrates with in-memory filesystem)
- **Performance Optimization ADR (future):** If SDK streaming proves insufficient

---

## References

- **AWS SDK S3 Client:** https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/s3/S3Client.html
- **Azure Blob SDK:** https://docs.microsoft.com/en-us/java/api/com.azure.storage.blob.blobclient
- **GCP Storage SDK:** https://cloud.google.com/java/docs/reference/google-cloud-storage/latest/com.google.cloud.storage.Storage
- **Alpakka S3 Connector:** https://doc.akka.io/docs/alpakka/current/s3.html
- **VaultService Implementation:** `test-probe-services/src/main/scala/com/company/probe/services/vault/`

---

## Review History

- **2025-10-27:** Initial ADR created (Paired Programming Session)

---

## Appendix: Code Comparison

### SDK-Native: 15 Lines Total

```scala
def fetchFromS3(bucket: String, key: String, target: Path): Unit = {
  val request = GetObjectRequest.builder()
    .bucket(bucket)
    .key(key)
    .build()

  try {
    s3Client.getObject(request, target)
  } catch {
    case ex: S3Exception =>
      throw BlockStorageException(s"S3 fetch failed: ${ex.getMessage}", ex)
  }
}
```

### Pekko Streams: 25+ Lines Total

```scala
def fetchFromS3(bucket: String, key: String, target: Path)(
  implicit system: ActorSystem,
  mat: Materializer,
  ec: ExecutionContext
): Future[Unit] = {

  S3.download(bucket, key)
    .runWith(FileIO.toPath(target))
    .flatMap { result =>
      if result.wasSuccessful then
        Future.successful(())
      else
        Future.failed(
          new IOException(s"Download failed: ${result.status}")
        )
    }
    .recover {
      case ex: S3Exception =>
        throw BlockStorageException(s"S3 fetch failed: ${ex.getMessage}", ex)
    }
}
```

**Verdict:** SDK-Native is 40% shorter and requires no streaming infrastructure.
