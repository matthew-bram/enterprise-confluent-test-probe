# ADR-CUCUMBER-JIMFS-URI-PATH-RESOLUTION: jimfs Path Context Preservation via URIs

**Status:** Accepted
**Date:** 2025-11-01
**Context:** Phase 3 Integration Testing - jimfs Implementation
**Decision Makers:** Development Team
**Related ADRs:** ADR-CUCUMBER-CLASSLOADER-ACTOR-CONTEXT.md, ADR-STORAGE-001 through ADR-STORAGE-004

---

## Context and Problem Statement

After successfully implementing custom `JimfsResource` and `JimfsFeatureSupplier` to bypass Cucumber's PathScanner filesystem constraint, we encountered a subtle but critical issue: **jimfs paths were losing their FileSystem context when converted to strings and back**.

### Symptom

Integration tests showed successful Cucumber configuration but discovered 0 scenarios:

```
[CucumberExecutor] Running Cucumber with glue: [com.fake.company.tests]
[CucumberExecutor] Features: /integration-test-6ca4c63e-73a2-40bf-82cf-97d562df2c47/produce-consume-event.feature
0 Scenarios
0 Steps
```

Yet the feature file existed in jimfs and contained 3 valid scenarios.

### Root Cause Analysis

**Problem Chain:**

1. **IntegrationWorld creates jimfs Path:**
   ```scala
   val featureFile: Path = jimfsInstance.getPath(
     "/integration-test-UUID/produce-consume-event.feature"
   )
   ```

2. **BlockStorageDirective stores as String:**
   ```scala
   BlockStorageDirective(
     jimfsLocation = featureFile.toString,  // ❌ Returns "/integration-test-UUID/..."
     evidenceDir = evidenceDir.toString      // ❌ Loses jimfs FileSystem context!
   )
   ```

3. **CucumberExecutor converts String back to Path:**
   ```scala
   val featurePaths: Seq[Path] = config.featurePaths.map(Paths.get(_))
   // ❌ Paths.get(String) creates Path on DEFAULT filesystem, NOT jimfs!
   ```

4. **JimfsFeatureSupplier receives default filesystem Path:**
   ```scala
   Files.walk(path)  // ❌ Walks DEFAULT filesystem, not jimfs
   // Result: No .feature files found (they're in jimfs, not default filesystem)
   ```

**Evidence:**

```scala
// jimfs Path
val jimfsPath: Path = jimfs.getPath("/test.feature")
println(jimfsPath.toString)              // "/test.feature"
println(jimfsPath.getFileSystem)         // "jimfs://uuid" ✅

// String → DEFAULT filesystem Path (WRONG!)
val recreatedPath: Path = Paths.get("/test.feature")
println(recreatedPath.getFileSystem)     // "default" ❌ DIFFERENT FILESYSTEM!

// Files API uses the Path's FileSystem
Files.exists(jimfsPath)                  // true ✅
Files.exists(recreatedPath)              // false ❌ (looking in default FS)
```

## Decision Drivers

1. **FileSystem Context Preservation**: Paths must maintain their FileSystem association across serialization boundaries
2. **Type System Constraints**: BlockStorageDirective uses `String` fields (cross-module compatibility)
3. **URI Standard**: `java.net.URI` is the standard mechanism for encoding FileSystem context
4. **Backward Compatibility**: Existing code uses String-based paths

## Considered Options

### Option 1: Change BlockStorageDirective to use Path (Rejected)

```scala
case class BlockStorageDirective(
  jimfsLocation: Path,   // ❌ Path not serializable across module boundaries
  evidenceDir: Path
)
```

**Pros:**
- Type-safe
- Preserves FileSystem context naturally

**Cons:**
- ❌ **BREAKING CHANGE** - All existing code breaks
- ❌ Path is not serializable (cross-JVM issues)
- ❌ Common module can't depend on specific FileSystem implementations
- ❌ Creates tight coupling between modules

### Option 2: Use toString() with FileSystem Type Tag (Rejected)

```scala
jimfsLocation = s"jimfs:${featureFile.toString}"  // Manual prefix
```

**Pros:**
- Simple string manipulation
- Backward compatible

**Cons:**
- ❌ Ad-hoc encoding (not standard)
- ❌ Requires manual parsing logic
- ❌ Error-prone (easy to forget prefix)
- ❌ Doesn't work with `Paths.get()` API

### Option 3: Use URI.toString() (SELECTED)

```scala
// IntegrationWorld.scala
jimfsLocation = featureFile.toUri.toString  // "jimfs://uuid/integration-test-UUID/produce-consume-event.feature"

// CucumberExecutor.scala
Paths.get(URI.create(jimfsLocation))        // Resolves to jimfs FileSystem! ✅
```

**Pros:**
- ✅ **Standard Java URI encoding** - No custom format
- ✅ **Preserves FileSystem context** - jimfs:// scheme indicates FileSystem
- ✅ **Works with Paths.get(URI)** - Standard API resolves FileSystem correctly
- ✅ **Backward compatible** - String field unchanged
- ✅ **Type-safe at boundaries** - Conversion only at edges

**Cons:**
- ⚠️ Requires `Paths.get(URI.create())` instead of `Paths.get()` (minor API change)

## Decision Outcome

**Chosen Option:** Use URI.toString() for FileSystem-agnostic path serialization

### Implementation

**File 1: IntegrationWorld.scala** (jimfs Path creation)

```scala
// BEFORE:
BlockStorageDirective(
  jimfsLocation = featureFile.toString,   // ❌ "/path" (loses FS)
  evidenceDir = evidenceDir.toString      // ❌ "/path" (loses FS)
)

// AFTER:
BlockStorageDirective(
  jimfsLocation = featureFile.toUri.toString,  // ✅ "jimfs://uuid/path"
  evidenceDir = evidenceDir.toUri.toString      // ✅ "jimfs://uuid/path"
)
```

**File 2: CucumberExecutor.scala** (String → Path conversion)

```scala
// BEFORE:
val featurePaths: Seq[Path] = config.featurePaths.map(Paths.get(_))
// ❌ Creates Path on DEFAULT filesystem

// AFTER:
val featurePaths: Seq[Path] = config.featurePaths.map(uriStr =>
  Paths.get(java.net.URI.create(uriStr))
)
// ✅ Resolves to correct FileSystem (jimfs or default)
```

**File 3: CucumberExecutor.scala** (evidenceDir conversion - 2 locations)

```scala
// BEFORE:
CucumberContext.registerEvidencePath(Paths.get(directive.evidenceDir))
val plugins = createJimfsPlugins(
  evidencePath = Paths.get(directive.evidenceDir),
  ...
)

// AFTER:
CucumberContext.registerEvidencePath(
  Paths.get(java.net.URI.create(directive.evidenceDir))
)
val plugins = createJimfsPlugins(
  evidencePath = Paths.get(java.net.URI.create(directive.evidenceDir)),
  ...
)
```

### Validation

**Before Fix:**
```
[CucumberExecutor] Features: /integration-test-UUID/produce-consume-event.feature
0 Scenarios
0 Steps
```

**After Fix:**
```
[CucumberExecutor] Features: jimfs://uuid/integration-test-UUID/produce-consume-event.feature
3 Scenarios
18 Steps (9 passed, 9 pending)
```

**Integration Test Results:**
```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Consequences

### Positive

1. **jimfs Integration Complete**: 100% in-memory filesystem operation achieved
2. **No Filesystem Pollution**: Zero files created in ~/tmp/ or /tmp/
3. **Automatic Cleanup**: jimfs.close() auto-deletes all files
4. **Type Safety**: Compiler enforces correct Path → URI → String → URI → Path conversion
5. **Standard API**: Uses java.net.URI (well-documented, stable)
6. **Future-Proof**: Works with any FileSystem implementation (not jimfs-specific)

### Negative

1. **API Change Required**: Must use `Paths.get(URI.create())` instead of `Paths.get()`
   - Impact: 3 lines changed in CucumberExecutor.scala
   - Risk: Low (compiler catches incorrect usage)

### Neutral

1. **URI String Format**: jimfs URIs look like `jimfs://uuid/path` (not `/path`)
   - Different from default filesystem paths
   - Actually beneficial for debugging (can see which FS in logs)

## Learning and Insights

### Key Learning

**Path.toString() vs Path.toUri().toString() are FUNDAMENTALLY DIFFERENT:**

| Method | Result | FileSystem Context | Use Case |
|--------|--------|-------------------|----------|
| `path.toString()` | `/path/to/file` | ❌ LOST | Display only |
| `path.toUri().toString()` | `jimfs://uuid/path/to/file` | ✅ PRESERVED | Serialization |

### Pattern for Future Reference

When passing Paths across serialization boundaries (String fields, network, database):

1. **Serialize:** `path.toUri().toString`
2. **Deserialize:** `Paths.get(URI.create(uriString))`

**Example:**
```scala
// Serialization (Path → String)
val pathString: String = jimfsPath.toUri.toString

// Deserialization (String → Path)
val recreatedPath: Path = Paths.get(URI.create(pathString))

// Verify FileSystem preserved
recreatedPath.getFileSystem == jimfsPath.getFileSystem  // true ✅
```

### Testing Strategy

**Unit Test:**
```scala
"Path URI serialization" should {
  "preserve jimfs FileSystem context" in {
    val jimfs = Jimfs.newFileSystem(Configuration.unix())
    val original = jimfs.getPath("/test.feature")

    // Serialize and deserialize
    val uriString = original.toUri.toString
    val restored = Paths.get(URI.create(uriString))

    // FileSystem preserved
    restored.getFileSystem shouldBe original.getFileSystem
    Files.exists(restored) shouldBe Files.exists(original)
  }
}
```

### Debugging Path Issues

**Symptoms of DEFAULT filesystem Path (incorrect):**
- `Files.exists(path)` returns false (file exists in jimfs)
- `Files.walk(path)` returns 0 results (walking wrong FS)
- Logs show `/path` instead of `jimfs://uuid/path`

**Diagnosis:**
```scala
println(s"FileSystem: ${path.getFileSystem}")
// If shows "default" but should be "jimfs" → Path lost FS context
```

**Fix:**
```scala
// Change:
Paths.get(stringPath)  // ❌

// To:
Paths.get(URI.create(stringPath))  // ✅
```

## Alternative FileSystem Support

This pattern works for **ALL FileSystem implementations**, not just jimfs:

| FileSystem | URI Scheme | Example |
|------------|-----------|---------|
| Default | `file://` | `file:///Users/user/test.feature` |
| jimfs | `jimfs://` | `jimfs://uuid/test.feature` |
| ZipFileSystem | `jar://` | `jar:file:/path/to/archive.jar!/test.feature` |
| Custom FS | Custom scheme | `custom://server/path` |

**Key:** `Paths.get(URI)` delegates to the correct `FileSystemProvider` based on URI scheme.

## References

- **Java NIO FileSystem API**: https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html
- **URI Schemes**: https://docs.oracle.com/javase/8/docs/api/java/net/URI.html
- **jimfs Documentation**: https://github.com/google/jimfs
- **Implementation Files:**
  - `test-probe-core/src/test/scala/io/distia/probe/core/integration/world/IntegrationWorld.scala` (lines 293-306)
  - `test-probe-core/src/main/scala/io/distia/probe/core/services/cucumber/CucumberExecutor.scala` (lines 102, 117, 130)

## Related ADRs

- **ADR-CUCUMBER-CLASSLOADER-ACTOR-CONTEXT.md**: ClassLoader selection for step definition discovery (complementary issue)
- **ADR-STORAGE-001 through ADR-STORAGE-004**: jimfs architecture decisions that this ADR completes

## Notes

This ADR documents the **final piece** of the jimfs integration puzzle:

1. **ADR-STORAGE-001 through ADR-STORAGE-004**: Architecture for jimfs usage
2. **Custom JimfsResource/JimfsFeatureSupplier**: Bypass Cucumber PathScanner
3. **ADR-CUCUMBER-CLASSLOADER-ACTOR-CONTEXT**: Solve step definition discovery
4. **THIS ADR**: Preserve FileSystem context across String serialization

All four pieces combined enable **100% in-memory integration testing** with zero filesystem pollution.

---

**Last Updated:** 2025-11-01
**Implementation Status:** ✅ COMPLETE - All integration tests passing with jimfs
