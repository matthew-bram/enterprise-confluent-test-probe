# ADR-STORAGE-007: JIMFS Architecture and Lifecycle Management

**Status:** Accepted
**Date:** 2025-10-27
**Decision Makers:** Engineering Team
**Related:** ADR-STORAGE-005, ADR-STORAGE-006

---

## Context

Test-Probe needs an in-memory filesystem to store test artifacts (Cucumber features, topic directives) during test execution. The filesystem must provide isolation between concurrent tests, efficient resource usage, and automatic cleanup after evidence upload.

### Requirements

1. **Test Isolation:** Each test execution must have its own isolated directory
2. **Concurrent Tests:** Support multiple tests running simultaneously
3. **Resource Efficiency:** Minimize memory overhead
4. **Lifecycle Management:** Create directories on fetch, delete after evidence upload
5. **Real Filesystem Semantics:** Support standard java.nio.file APIs

### Constraints

- JIMFS (Google's in-memory filesystem) chosen for implementation
- Must work with java.nio.file Path API
- Must integrate with BlockStorageService streaming
- Test directories identified by UUID
- Evidence directory created within test directory

---

## Decision

We will use a **shared singleton JIMFS FileSystem** with per-test directory isolation managed by a dedicated **JimfsManager** component.

### Architecture

```
JIMFS FileSystem (Singleton)
└── /{testId-1}/
    ├── features/
    │   ├── test1.feature
    │   └── test2.feature
    ├── test-config.yaml
    └── evidence/
        ├── screenshot1.png
        └── test-results.json
└── /{testId-2}/
    ├── features/
    ├── test-config.yaml
    └── evidence/
```

### JimfsManager API

```scala
private[services] class JimfsManager {

  private val fileSystem: FileSystem = Jimfs.newFileSystem(Configuration.unix())

  def createTestDirectory(testId: UUID): Path

  def getTestDirectory(testId: UUID): Path

  def deleteTestDirectory(testId: UUID): Unit

  def testDirectoryExists(testId: UUID): Boolean

  def listTestDirectories(): List[UUID]

  def cleanupAll(): Unit
}
```

---

## Rationale

### Why Shared FileSystem?

**Benefit 1: Efficiency**
- Single FileSystem instance reduces overhead
- Shared resources (file attributes, permissions)
- No startup/shutdown cost per test
- Lower memory footprint

**Benefit 2: Mimics Real Filesystem**
- Real filesystems are shared, not per-process
- Tests run in same environment (realistic)
- Consistent behavior with production

**Benefit 3: Simplified Management**
- Single lifecycle to manage (created at startup)
- No complex pool management
- Easy monitoring (one FileSystem to inspect)

**Benefit 4: Concurrent Test Support**
- Directory-level isolation sufficient
- UUID-based paths prevent collisions
- java.nio.file is thread-safe

### Why Per-Test Directory Isolation?

**Benefit 1: Clean Separation**
- Each test has `/testId/` root
- No risk of file name collisions
- Easy to identify test artifacts

**Benefit 2: Atomic Cleanup**
- Delete entire directory tree in one operation
- No need to track individual files
- Guaranteed cleanup after evidence upload

**Benefit 3: Clear Ownership**
- Directory lifecycle tied to test lifecycle
- Created on fetch, deleted after upload
- No orphaned files

---

## Implementation Details

### FileSystem Configuration

```scala
private val fileSystem: FileSystem = Jimfs.newFileSystem(Configuration.unix())
```

**Configuration.unix():**
- Unix-style paths (`/testId/features/test.feature`)
- Case-sensitive filenames
- Forward slash path separator
- Standard Unix file attributes

**Why Unix over Windows configuration?**
- Simpler path handling (no drive letters)
- Consistent across all platforms
- Matches cloud storage path conventions
- No backslash escaping issues

### Directory Structure Convention

```
/{testId}/                    Root directory for test
  ├── features/               Cucumber feature files (streamed from cloud)
  │   ├── test1.feature
  │   └── test2.feature
  ├── test-config.yaml        Topic directive configuration
  └── evidence/               Evidence artifacts (created during test)
      ├── screenshot1.png
      └── test-results.json
```

**Naming Convention:**
- Test ID used as directory name (e.g., `/550e8400-e29b-41d4-a716-446655440000/`)
- `features/` directory name is standard (validated, not configurable)
- `test-config.yaml` filename configurable via `BlockStorageConfig.topicDirectiveFileName`
- `evidence/` directory name is standard

### Lifecycle Phases

**Phase 1: Creation (During Fetch)**
```scala
val testDir = jimfsManager.createTestDirectory(testId)
```
- Creates `/testId/` directory
- Throws exception if directory already exists
- Returns Path for streaming operations

**Phase 2: Population (During Fetch)**
```scala
blockStorageService.fetchFromBlockStorage(testId, bucketUri)
```
- Streams files from cloud storage to `/testId/`
- Validates `features/` directory exists and is not empty
- Parses `test-config.yaml`
- Creates `evidence/` directory
- Returns BlockStorageDirective

**Phase 3: Usage (During Test Execution)**
```scala
val directive = blockStorageDirective
```
- CucumberExecutionActor reads from `directive.jimfsLocation` (`/testId/features`)
- Evidence written to `directive.evidenceDir` (`/testId/evidence`)
- Test directory remains for entire test duration

**Phase 4: Cleanup (After Evidence Upload)**
```scala
blockStorageService.uploadToBlockStorage(testId, bucket, evidenceDir)
```
- Streams evidence from `/testId/evidence/` to cloud storage
- Deletes `/testId/` directory recursively
- Completes Future[Unit]

---

## Alternatives Considered

### Alternative 1: Per-Test FileSystem Instances

```scala
def createFileSystemForTest(testId: UUID): FileSystem = {
  Jimfs.newFileSystem(Configuration.unix())
}
```

**Pros:**
- Complete isolation (no shared state)
- Can configure per-test (different attributes)

**Cons:**
- ❌ Resource overhead (each FileSystem has metadata tables)
- ❌ Startup/shutdown cost per test
- ❌ More complex lifecycle management
- ❌ Doesn't mimic real filesystem behavior

**Decision:** Rejected due to efficiency concerns

---

### Alternative 2: Temporary Real Filesystem Directories

```scala
val testDir = Files.createTempDirectory(s"test-$testId")
```

**Pros:**
- Real filesystem (most realistic)
- Native OS support

**Cons:**
- ❌ Disk I/O overhead (slower than in-memory)
- ❌ Cleanup complexity (orphaned directories if crash)
- ❌ Requires disk space
- ❌ OS-specific path handling (Windows vs Unix)

**Decision:** Rejected in favor of in-memory filesystem

---

### Alternative 3: Directory Pool Pattern

```scala
object DirectoryPool {
  private val pool = mutable.Queue[Path]()

  def acquire(): Path
  def release(path: Path): Unit
}
```

**Pros:**
- Reuse directories across tests
- Potentially lower allocation cost

**Cons:**
- ❌ Complex cleanup (must delete all files in directory)
- ❌ Risk of leftover files from previous tests
- ❌ No clear benefit over simple create/delete
- ❌ Added complexity for minimal gain

**Decision:** Rejected due to complexity

---

## Concurrency Considerations

### Thread Safety

**JIMFS Thread Safety:**
- JIMFS FileSystem is thread-safe
- java.nio.file operations are thread-safe
- Multiple tests can create/delete directories concurrently

**JimfsManager Thread Safety:**
- All operations delegate to thread-safe JIMFS
- No mutable state in JimfsManager (except singleton FileSystem)
- Safe for concurrent use from multiple actors

### Collision Prevention

**UUID-Based Paths:**
- Test IDs are UUIDs (guaranteed unique)
- Directory paths: `/uuid/` (no collisions possible)
- No need for locks or coordination

**Creation Race Condition:**
```scala
def createTestDirectory(testId: UUID): Path = {
  val testDir = fileSystem.getPath(s"/$testId")
  if Files.exists(testDir) then
    throw new IllegalStateException(s"Test directory already exists: $testDir")
  Files.createDirectories(testDir)
  testDir
}
```
- Check-then-create pattern is safe (UUID collision is impossible)
- Exception indicates programming error (test ID reuse)

---

## Error Handling

### Directory Creation Errors

**Already Exists:**
```scala
if Files.exists(testDir) then
  throw new IllegalStateException(
    s"Test directory already exists: $testDir. Test ID may have been reused."
  )
```

**Permission Errors:**
- Unlikely with JIMFS (in-memory, no OS permissions)
- Would indicate JIMFS bug or memory exhaustion

### Directory Deletion Errors

**Not Found:**
- Acceptable (already cleaned up)
- Log warning for debugging

**Partial Deletion:**
- JIMFS should delete atomically
- If fails mid-delete, log error and continue

---

## Memory Management

### Memory Usage Estimation

**Per-Test Overhead:**
- Directory metadata: ~1 KB
- File entries: ~100 bytes per file
- File contents: Actual file size

**Example:**
- 10 feature files × 10 KB = 100 KB
- 1 config file × 1 KB = 1 KB
- 5 evidence files × 100 KB = 500 KB
- **Total per test:** ~601 KB

**Concurrent Tests:**
- 10 concurrent tests × 601 KB = ~6 MB
- 100 concurrent tests × 601 KB = ~60 MB

**Acceptable:** Modern JVMs can handle hundreds of MB

### Cleanup Strategy

**Automatic Cleanup:**
- Delete after evidence upload (normal path)
- Delete on fetch error (error path)

**Manual Cleanup (Debugging):**
```scala
jimfsManager.cleanupAll()  // Delete all test directories
```

---

## Consequences

### Positive

✅ **Resource Efficient:** Single FileSystem shared across all tests
✅ **Strong Isolation:** Per-test directories prevent collisions
✅ **Simple API:** Easy to use from BlockStorageService
✅ **Mimics Reality:** Behaves like real shared filesystem
✅ **Thread Safe:** Concurrent tests supported
✅ **Clean Lifecycle:** Create on fetch, delete after upload

### Negative

⚠️ **Singleton State:** Global FileSystem instance (but isolated by directories)
⚠️ **Memory Resident:** All test artifacts in memory (acceptable for test workloads)
⚠️ **No Persistence:** Lost on application crash (acceptable, tests are transient)

### Neutral

⚡ **Memory Usage:** Scales with concurrent tests (acceptable for realistic workloads)
⚡ **Cleanup Overhead:** Directory deletion is fast (in-memory operation)

---

## Follow-Up Decisions

- **Monitoring:** Add metrics for test directory count, total JIMFS usage
- **Cleanup Policy:** Define retention policy for failed test directories (future)
- **Memory Limits:** Monitor and tune JVM heap if needed

---

## References

- **JIMFS GitHub:** https://github.com/google/jimfs
- **JIMFS JavaDoc:** https://www.javadoc.io/doc/com.google.jimfs/jimfs/latest/index.html
- **java.nio.file API:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/package-summary.html

---

## Review History

- **2025-10-27:** Initial ADR created (Paired Programming Session)

---

## Appendix: JimfsManager Implementation

```scala
package io.distia.probe
package services
package storage

import com.google.common.jimfs.{Configuration, Jimfs}
import java.nio.file.{FileSystem, Files, Path}
import java.util.UUID
import scala.jdk.StreamConverters._

private[services] class JimfsManager {

  private val fileSystem: FileSystem = Jimfs.newFileSystem(Configuration.unix())

  def createTestDirectory(testId: UUID): Path = {
    val testDir = fileSystem.getPath(s"/$testId")
    if Files.exists(testDir) then
      throw new IllegalStateException(s"Test directory already exists: $testDir")
    Files.createDirectories(testDir)
    testDir
  }

  def getTestDirectory(testId: UUID): Path =
    fileSystem.getPath(s"/$testId")

  def deleteTestDirectory(testId: UUID): Unit = {
    val testDir = fileSystem.getPath(s"/$testId")
    if Files.exists(testDir) then deleteRecursively(testDir)
  }

  def testDirectoryExists(testId: UUID): Boolean =
    Files.exists(fileSystem.getPath(s"/$testId"))

  def listTestDirectories(): List[UUID] = Files
    .list(fileSystem.getPath("/"))
    .toScala(List)
    .flatMap { path =>
      try Some(UUID.fromString(path.getFileName.toString))
      catch case _: IllegalArgumentException => None
    }

  def cleanupAll(): Unit = Files
    .list(fileSystem.getPath("/"))
    .toScala(List)
    .foreach(deleteRecursively)

  private def deleteRecursively(path: Path): Unit = {
    if Files.isDirectory(path) then
      Files.list(path).toScala(List).foreach(deleteRecursively)
    Files.delete(path)
  }
}
```

**Lines:** 50
**Dependencies:** JIMFS, java.nio.file
**Complexity:** Low (simple directory operations)
**Thread Safety:** Yes (delegates to JIMFS)
