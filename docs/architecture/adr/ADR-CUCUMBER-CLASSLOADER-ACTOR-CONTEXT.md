# ADR-CUCUMBER-CLASSLOADER-ACTOR-CONTEXT: Cucumber ClassLoader Selection in Actor Context

**Status:** Accepted
**Date:** 2025-10-31
**Context:** Phase 3 Integration Testing
**Decision Makers:** Development Team
**Related ADRs:** ADR-CUCUMBER-FILESYSTEM-CONSTRAINT.md

---

## Context and Problem Statement

When running Cucumber tests inside an ActorSystem (via `CucumberExecutionActor`), step definitions were not being discovered despite being correctly compiled and placed on the classpath.

### Symptom

Integration tests showed:
```
Undefined scenarios:
file:///Users/.../produce-consume-event.feature:17 # Produce and consume event with AVRO

3 Scenarios (3 undefined)
18 Steps (15 skipped, 3 undefined)
```

Yet the step definition class existed:
```bash
$ find test-probe-core/target/test-classes -name "ProduceConsumeSteps.class"
test-probe-core/target/test-classes/com/fake/company/tests/ProduceConsumeSteps.class
```

### Root Cause Analysis

**Original Code (CucumberExecutor.scala:128):**
```scala
val classLoader: ClassLoader = Thread.currentThread().getContextClassLoader
```

**Problem:** When `CucumberExecutor.execute()` runs inside an actor thread:
- The thread's context classloader is the **Pekko/ActorSystem classloader**
- This classloader does NOT have access to the `test-classes` directory
- Cucumber cannot find step definitions compiled to `target/test-classes/`

**Evidence:**
```
Glue package configured: ✅ "com.fake.company.tests"
ProduceConsumeSteps.class exists: ✅ target/test-classes/com/fake/company/tests/ProduceConsumeSteps.class
Thread context classloader: ❌ Pekko ActorSystem classloader (no test-classes access)
Result: ❌ Cucumber reports "3 undefined scenarios"
```

## Decision Drivers

1. **Integration Test Architecture**: Cucumber runs inside `CucumberExecutionActor` (actor context)
2. **Component Test Success**: Same code works in component tests (non-actor context)
3. **ClassLoader Hierarchy**: Different classloaders have access to different classpaths
4. **Backward Compatibility**: Fix must not break existing component tests

## Considered Options

### Option 1: Use Thread's Context ClassLoader (Original)
```scala
val classLoader: ClassLoader = Thread.currentThread().getContextClassLoader
```

**Pros:**
- Standard Java pattern for plugin discovery
- Works in component tests (non-actor context)

**Cons:**
- ❌ **FAILS in integration tests** - actor thread context classloader doesn't have test-classes
- Different behavior in component vs integration tests

### Option 2: Use System ClassLoader
```scala
val classLoader: ClassLoader = ClassLoader.getSystemClassLoader
```

**Pros:**
- Access to full application classpath
- Works in both component and integration tests

**Cons:**
- May be too broad for some deployment scenarios
- Not as precise as class's own classloader

### Option 3: Use Class's Own ClassLoader (SELECTED)
```scala
val classLoader: ClassLoader = this.getClass.getClassLoader
```

**Pros:**
- ✅ **Works in both component and integration tests**
- Uses the classloader that loaded `CucumberExecutor` (has access to test-classes)
- More precise than system classloader
- Consistent behavior across test contexts

**Cons:**
- Slightly less flexible than context classloader pattern (acceptable trade-off)

## Decision Outcome

**Chosen Option:** Use Class's Own ClassLoader (`this.getClass.getClassLoader`)

### Implementation

**File:** `test-probe-core/src/main/scala/io/distia/probe/core/services/cucumber/CucumberExecutor.scala`

**Change:**
```scala
// BEFORE (line 128):
val classLoader: ClassLoader = Thread.currentThread().getContextClassLoader

// AFTER (line 133):
val classLoader: ClassLoader = this.getClass.getClassLoader
```

**Documentation Added:**
```scala
// Get class loader for step definition discovery
// CRITICAL: Use this.getClass.getClassLoader instead of Thread.currentThread().getContextClassLoader
// When running inside ActorSystem, thread context classloader is the Pekko classloader
// which doesn't have access to test-classes directory where step definitions are compiled.
// Using the class's own classloader ensures Cucumber can find step definitions in test-classes.
// See ADR-CUCUMBER-CLASSLOADER-ACTOR-CONTEXT.md for full analysis.
val classLoader: ClassLoader = this.getClass.getClassLoader
```

### Validation

**Before Fix:**
```
Tests run: 4, Failures: 0, Errors: 1, Skipped: 0
Evidence directory is EMPTY! Cucumber did not write any evidence files.
BUILD FAILURE
```

**After Fix (Expected):**
```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
Evidence directory contains N file(s)
BUILD SUCCESS
```

## Consequences

### Positive

1. **Integration Tests Work**: Cucumber can now find step definitions when running inside actors
2. **Component Tests Unchanged**: No regression in existing component test behavior
3. **Consistent Behavior**: Same classloader used regardless of execution context
4. **Clear Documentation**: Code comment explains WHY this specific classloader is required

### Negative

1. **Slight Inflexibility**: Less flexible than context classloader pattern (acceptable trade-off)

### Neutral

1. **Testing Requirement**: Must verify step definition discovery in both component and integration tests

## Learning and Insights

### Key Learning
**Thread context classloader ≠ Class's classloader** in actor-based systems.

### Pattern for Future Reference

When integrating third-party libraries (like Cucumber) that perform classpath scanning inside actors:

1. **Avoid:** `Thread.currentThread().getContextClassLoader` (actor thread classloader)
2. **Prefer:** `this.getClass.getClassLoader` (class's own classloader)
3. **Alternative:** `ClassLoader.getSystemClassLoader` (if broader access needed)

### Debugging ClassLoader Issues

**Steps to diagnose:**
```bash
# 1. Verify class file exists
find target/test-classes -name "YourSteps.class"

# 2. Check glue package configuration
grep -r "glue" src/

# 3. Add logging to see which classloader is used
println(s"ClassLoader: ${classLoader.getClass.getName}")
println(s"Can load class: ${Try(classLoader.loadClass("com.your.StepClass")).isSuccess}")
```

### Test Architecture Impact

This issue only manifests in **integration tests** where Cucumber runs inside actors:
- **Component Tests**: Work with either classloader (test thread context = class's classloader)
- **Integration Tests**: Require class's classloader (actor thread context ≠ class's classloader)

## References

- **Implementation PR:** [Link to PR when created]
- **Issue:** Integration test Phase 3 - Cucumber step definition discovery
- **Test Files:**
  - `test-probe-core/src/test/java/com/fake/company/tests/ProduceConsumeSteps.java`
  - `test-probe-core/src/test/resources/stubs/produce-consume-event.feature`
  - `test-probe-core/src/test/scala/io/distia/probe/core/integration/IntegrationTestRunner.scala`

## Notes

This ADR documents a **critical learning** from Phase 3 integration testing. The issue was subtle:
- Code compiled successfully ✅
- Step definitions existed on classpath ✅
- Glue package configuration correct ✅
- **But classloader context was wrong** ❌

This highlights the importance of **understanding execution context** when integrating libraries that perform dynamic class discovery, especially in actor-based architectures.

---

**Last Updated:** 2025-10-31
