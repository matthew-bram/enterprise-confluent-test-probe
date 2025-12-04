# Scala Ninja Code Review: RequestBodyBuilder.scala

**Date**: 2025-10-23
**File**: `/Users/matthewbram/projects/test-probe/test-probe-services/src/main/scala/com/company/probe/services/vault/RequestBodyBuilder.scala`
**Lines of Code**: 161
**Reviewer**: Scala Ninja (Expert-Level Peer Review Agent)

---

## Executive Summary

RequestBodyBuilder.scala implements a template variable substitution system for building Vault API request bodies from Rosetta configuration. The implementation demonstrates solid functional programming principles with comprehensive error handling using Either. However, there are **CRITICAL visibility pattern violations** that will block test coverage, and several opportunities exist for type safety improvements, error accumulation, and functional composition enhancements.

**Overall Grade: B- (Requires Revision)**

**Status: ❌ PEER REVIEW REJECTION POINT - Visibility Pattern Violations**

---

## Critical Issues (MUST FIX)

### ❌ **ISSUE #1: Visibility Pattern Violation - Reduces Coverage by 20-40%**
**Severity: CRITICAL - Peer Review Rejection Point**
**Lines: 38, 77, 98, 121, 138**

**Problem**: All helper methods are marked `private`, preventing comprehensive unit testing.

📖 **Reference**: `.claude/styles/scala-conventions.md` (lines 111-160)

> "This is a **peer review rejection point** - all new code must follow this pattern unless specifically justified."

**Current (Violates Pattern)**:
```scala
private[services] object RequestBodyBuilder {
  private def substituteVariables(...) = { ... }       // ❌ Cannot unit test
  private def substituteStringValue(...) = { ... }     // ❌ Cannot unit test
  private def resolveConfigPath(...) = { ... }         // ❌ Cannot unit test
  private def resolveMetadataKey(...) = { ... }        // ❌ Cannot unit test
  private def resolveDirectiveField(...) = { ... }     // ❌ Cannot unit test
}
```

**Required Pattern**:
```scala
private[services] object RequestBodyBuilder {
  // All methods PUBLIC (no private keyword)
  // Object-level private[services] provides module encapsulation

  def substituteVariables(...) = { ... }       // ✅ Unit testable
  def substituteStringValue(...) = { ... }     // ✅ Unit testable
  def resolveConfigPath(...) = { ... }         // ✅ Unit testable
  def resolveMetadataKey(...) = { ... }        // ✅ Unit testable
  def resolveDirectiveField(...) = { ... }     // ✅ Unit testable
}
```

**Impact**:
- **5 methods untestable** → ~80+ lines of business logic without regression protection
- **Estimated coverage loss: 30-40%**
- Cannot directly test regex matching logic
- Cannot test error message quality
- Cannot test edge cases in config resolution

**Proven Pattern**: GuardianActor went from 45% → 85% coverage after applying this pattern.

**Required Action**: Remove `private` from all methods. The `private[services]` at object level provides sufficient encapsulation.

---

### ❌ **ISSUE #2: Error Accumulation - Only Returns First Error**
**Severity: CRITICAL - Poor User Experience**
**Lines: 54-60, 64-72**

**Problem**: When multiple template variables fail validation, only the first error is returned. Users must fix errors iteratively rather than seeing all issues at once.

**Current Implementation**:
```scala
jsonArray = arr => {
  val results = arr.map(elem => substituteVariables(elem, topicDirective, appConfig))
  val errors = results.collect { case Left(err) => err }
  if (errors.nonEmpty) {
    Left(errors.head)  // ❌ Only returns FIRST error
  } else {
    Right(Json.fromValues(results.collect { case Right(v) => v }))
  }
}
```

**Scenario**:
```json
{
  "username": "{{unknownField}}",
  "password": "{{'missingKey'}}",
  "endpoint": "{{$^missing.config}}"
}
```

**Current Behavior**:
- First run: "Unknown TopicDirective field: unknownField"
- Second run (after fix): "Metadata key 'missingKey' not found"
- Third run (after fix): "Config path not found: missing.config"

**Better Approach - Accumulate All Errors**:
```scala
jsonArray = arr => {
  val results = arr.map(elem => substituteVariables(elem, topicDirective, appConfig))
  val errors = results.collect { case Left(err) => err }
  if (errors.nonEmpty) {
    // Combine all error messages
    val combinedMessage = errors.map(_.getMessage).mkString("; ")
    Left(new VaultMappingException(
      s"Multiple template errors for topic ${topicDirective.topic}: $combinedMessage"
    ))
  } else {
    Right(Json.fromValues(results.collect { case Right(v) => v }))
  }
}
```

**Alternative - Use Validated (cats)**:
```scala
import cats.data.Validated
import cats.data.ValidatedNec
import cats.implicits.*

// Accumulates ALL errors, not just first
jsonArray = arr => {
  val results: List[ValidatedNec[VaultMappingException, Json]] =
    arr.map(elem => substituteVariables(elem, topicDirective, appConfig).toValidatedNec)

  results.combineAll.toEither.left.map { errors =>
    new VaultMappingException(
      s"Template validation failed for topic ${topicDirective.topic}: " +
      errors.toList.map(_.getMessage).mkString("; ")
    )
  }
}
```

**Benefits**:
- ✅ Users see ALL errors in one run
- ✅ Faster debugging (fix all issues at once)
- ✅ Better developer experience
- ✅ More professional error reporting

---

### ⚠️ **ISSUE #3: Hardcoded Field Names - Type Safety Missing**
**Severity: MAJOR - Breaks at Runtime**
**Lines: 144-150**

**Problem**: TopicDirective field names are hardcoded strings. If TopicDirective model changes, compiler won't catch breakage.

**Current (Brittle)**:
```scala
private def resolveDirectiveField(
  fieldName: String,
  topicDirective: TopicDirective
): Either[VaultMappingException, String] = {
  fieldName match {
    case "topic" => Right(topicDirective.topic)
    case "role" => Right(topicDirective.role)
    case "clientPrincipal" => Right(topicDirective.clientPrincipal)
    case unknown =>
      Left(new VaultMappingException(
        s"Unknown TopicDirective field: $unknown. Valid fields: topic, role, clientPrincipal"
      ))
  }
}
```

**Problem Scenario**:
```scala
// TopicDirective model evolves
case class TopicDirective(
  topic: String,
  role: String,
  principal: String,  // ❌ Renamed from clientPrincipal
  eventFilters: List[(String, String)],
  metadata: Map[String, String] = Map.empty
)

// RequestBodyBuilder still compiles but BREAKS at runtime!
// No compile-time safety
```

**Better Approach - Sealed ADT with Compile-Time Safety**:

```scala
// In RequestBodyBuilder companion
sealed trait DirectiveFieldRef {
  def extract(directive: TopicDirective): String
}

object DirectiveFieldRef {
  case object Topic extends DirectiveFieldRef {
    def extract(directive: TopicDirective): String = directive.topic
  }

  case object Role extends DirectiveFieldRef {
    def extract(directive: TopicDirective): String = directive.role
  }

  case object ClientPrincipal extends DirectiveFieldRef {
    def extract(directive: TopicDirective): String = directive.clientPrincipal
  }

  def fromString(name: String): Either[VaultMappingException, DirectiveFieldRef] =
    name match {
      case "topic" => Right(Topic)
      case "role" => Right(Role)
      case "clientPrincipal" => Right(ClientPrincipal)
      case unknown =>
        Left(new VaultMappingException(
          s"Unknown TopicDirective field: $unknown. Valid fields: ${validFields.mkString(", ")}"
        ))
    }

  val validFields: List[String] = List("topic", "role", "clientPrincipal")
}

// Refactored method
def resolveDirectiveField(
  fieldName: String,
  topicDirective: TopicDirective
): Either[VaultMappingException, String] = {
  DirectiveFieldRef.fromString(fieldName).map(_.extract(topicDirective))
}
```

**Benefits**:
- ✅ Compiler enforces field access (if TopicDirective changes, code won't compile)
- ✅ Single source of truth for valid fields
- ✅ Easier to extend (add new fields in one place)
- ✅ Better error messages (validFields auto-updated)
- ✅ Type-safe extraction

---

## Major Issues (SHOULD FIX)

### ⚠️ **ISSUE #4: Exception Wrapping - Loss of Context**
**Severity: MAJOR - Debugging Difficulty**
**Lines: 112-116**

**Problem**: Generic `Exception` catch loses specific exception type information needed for debugging.

**Current**:
```scala
try {
  Right(appConfig.getString(configPath))
} catch {
  case ex: Exception =>  // ❌ Too broad, loses exception type info
    Left(new VaultMappingException(
      s"Failed to read config path '$configPath' for topic $topic: ${ex.getMessage}",
      ex
    ))
}
```

**Issues**:
- Catches EVERYTHING (even fatal errors like OutOfMemoryError)
- Loses type information (was it ConfigException.Missing? ConfigException.WrongType?)
- Can mask programming errors

**Better Approach**:
```scala
import com.typesafe.config.ConfigException

Try {
  appConfig.getString(configPath)
}.toEither.left.map {
  case ex: ConfigException.Missing =>
    new VaultMappingException(
      s"Config path not found: $configPath for topic $topic",
      ex
    )
  case ex: ConfigException.WrongType =>
    new VaultMappingException(
      s"Config path '$configPath' is not a string for topic $topic (found: ${ex.getMessage})",
      ex
    )
  case ex =>
    new VaultMappingException(
      s"Failed to read config path '$configPath' for topic $topic: ${ex.getMessage}",
      ex
    )
}
```

**Benefits**:
- ✅ More specific error messages based on failure type
- ✅ Follows Scala 3 conventions (Try + .toEither + .left.map)
- ✅ Preserves exception cause chain
- ✅ Doesn't catch fatal errors

---

### ⚠️ **ISSUE #5: Regex Pattern Compilation - Performance Issue**
**Severity: MAJOR - Performance**
**Lines: 16-18**

**Problem**: Regex patterns are compiled as object-level vals (✅ GOOD), but pattern description could be clearer, and there's a subtle ordering dependency.

**Current**:
```scala
private val ConfigPathPattern: Regex = """\{\{\$\^([^}]+)\}\}""".r
private val MetadataKeyPattern: Regex = """\{\{'([^']+)'\}\}""".r
private val DirectiveFieldPattern: Regex = """\{\{([a-zA-Z]+)\}\}""".r
```

**Issues**:
1. **Ordering Dependency**: Pattern matching order in `substituteStringValue` matters. DirectiveFieldPattern is most permissive and must come LAST.
2. **Unclear Semantics**: No indication of what these patterns match.

**Better Approach**:
```scala
/**
 * Template variable patterns for request body substitution.
 *
 * Pattern matching order is critical - most specific patterns MUST be checked first:
 * 1. ConfigPathPattern: {{$^app.config.path}}
 * 2. MetadataKeyPattern: {{'metadataKey'}}
 * 3. DirectiveFieldPattern: {{fieldName}} (most permissive, checked last)
 */
private[services] object TemplatePatterns {
  val ConfigPath: Regex = """\{\{\$\^([^}]+)\}\}""".r
  val MetadataKey: Regex = """\{\{'([^']+)'\}\}""".r
  val DirectiveField: Regex = """\{\{([a-zA-Z]+)\}\}""".r

  // Example matches (for documentation/testing)
  val examples: Map[Regex, String] = Map(
    ConfigPath -> "{{$^vault.endpoint}}",
    MetadataKey -> "{{'tenantId'}}",
    DirectiveField -> "{{topic}}"
  )
}
```

**Then use**:
```scala
import TemplatePatterns.*

def substituteStringValue(
  value: String,
  topicDirective: TopicDirective,
  appConfig: Config
): Either[VaultMappingException, String] = {
  value match {
    case ConfigPath(configPath) =>
      resolveConfigPath(configPath, appConfig, topicDirective.topic)
    case MetadataKey(metadataKey) =>
      resolveMetadataKey(metadataKey, topicDirective)
    case DirectiveField(fieldName) =>
      resolveDirectiveField(fieldName, topicDirective)
    case _ =>
      Right(value)
  }
}
```

**Benefits**:
- ✅ Clearer organization
- ✅ Documented pattern order dependency
- ✅ Examples for testing/documentation
- ✅ Easier to extend with new patterns

---

### ⚠️ **ISSUE #6: Validate Method - Duplicate Effort**
**Severity: MINOR - API Design**
**Lines: 154-160**

**Problem**: `validate()` method just calls `substituteVariables()` and throws away result. Wasteful computation if caller will immediately call `build()`.

**Current**:
```scala
def validate(
  template: Json,
  topicDirective: TopicDirective,
  appConfig: Config
): Either[VaultMappingException, Unit] = {
  substituteVariables(template, topicDirective, appConfig).map(_ => ())
}
```

**Issues**:
- If validation succeeds, user calls `build()` which repeats identical work
- Double traversal of JSON structure
- Wasted CPU/memory allocation

**Better API Design**:

**Option 1: Remove validate() entirely**
```scala
// Users just call build() and check for errors
build(topicDirective, rosettaConfig, appConfig) match {
  case Right(requestBody) => // use it
  case Left(error) => // handle validation error
}
```

**Option 2: Return validated result**
```scala
def validate(
  template: Json,
  topicDirective: TopicDirective,
  appConfig: Config
): Either[VaultMappingException, Json] = {
  substituteVariables(template, topicDirective, appConfig)
}

// Then build() can use it:
def build(
  topicDirective: TopicDirective,
  rosettaConfig: RosettaConfig.RosettaConfig,
  appConfig: Config
): Either[VaultMappingException, String] = {
  rosettaConfig.requestTemplate match {
    case None =>
      Left(new VaultMappingException(
        s"Request template is required in Rosetta config for topic ${topicDirective.topic}"
      ))
    case Some(template) =>
      validate(template, topicDirective, appConfig).map(_.noSpaces)
  }
}
```

**Recommendation**: Option 1 (remove `validate`). The error-returning nature of `build()` already provides validation.

---

## Minor Issues (NICE TO HAVE)

### 💡 **ISSUE #7: Scala 3 Syntax - Not Using `then` Keyword**
**Severity: MINOR - Style Consistency**
**Lines: 26, 104, 109**

**Problem**: Code uses Scala 2 style braces instead of Scala 3 `then` keyword.

📖 **Reference**: `.claude/styles/scala-conventions.md` (lines 7-11)

> "**Scala 3 Syntax**: Use Scala 3 'then' keyword for if/else"

**Current (Scala 2 Style)**:
```scala
case None =>
  Left(new VaultMappingException(
    s"Request template is required in Rosetta config for topic ${topicDirective.topic}"
  ))
```

**This is actually fine** - the issue is with `if` statements, not pattern matching. However:

**Lines 104, 56, 68**:
```scala
if (!appConfig.hasPath(configPath)) {  // ❌ Scala 2 style
  Left(...)
} else {
  try { ... }
}

if (errors.nonEmpty) {  // ❌ Scala 2 style
  Left(errors.head)
} else {
  Right(...)
}
```

**Scala 3 Style**:
```scala
if !appConfig.hasPath(configPath) then
  Left(...)
else
  try { ... }

if errors.nonEmpty then
  Left(errors.head)
else
  Right(...)
```

---

### 💡 **ISSUE #8: Error Messages - Could Include More Context**
**Severity: MINOR - Developer Experience**
**Lines: 105-106, 131-134**

**Current Error Messages are Good**, but could be even better:

**Line 105-106**:
```scala
Left(new VaultMappingException(
  s"Config path not found: $configPath for topic $topic"
))
```

**Enhanced**:
```scala
Left(new VaultMappingException(
  s"Config path not found: $configPath for topic $topic. " +
  s"Verify application.conf has this path defined."
))
```

**Line 131-134** (✅ Already excellent!):
```scala
s"Metadata key '$metadataKey' not found in TopicDirective for topic ${topicDirective.topic}. " +
s"Available keys: ${topicDirective.metadata.keys.mkString(", ")}"
```

This is **exemplary** - it tells the user exactly what's available!

---

### 💡 **ISSUE #9: Import Organization - Minor Style Issue**
**Severity: MINOR - Style**
**Lines: 5-12**

**Current**:
```scala
import io.distia.probe.common.models.TopicDirective
import io.distia.probe.common.rosetta.RosettaConfig
import io.distia.probe.common.exceptions.VaultMappingException
import com.typesafe.config.Config
import io.circe.Json
import io.circe.syntax.*

import scala.util.matching.Regex
```

📖 **Reference**: `.claude/styles/scala-conventions.md` (lines 19-38)

**Better Organization (Group and Sort)**:
```scala
import scala.util.matching.Regex

import com.typesafe.config.Config
import io.circe.Json
import io.circe.syntax.*

import io.distia.probe.common.exceptions.VaultMappingException
import io.distia.probe.common.models.TopicDirective
import io.distia.probe.common.rosetta.RosettaConfig
```

**Groups**:
1. Scala standard library (`scala.*`)
2. Third-party libraries (`com.typesafe`, `io.circe`)
3. Project imports (`io.distia.probe.*`)
4. Relative imports (none here)

---

## Strengths (Exemplary Patterns)

### ✅ **STRENGTH #1: Excellent Error Messages**

**Lines 131-134**:
```scala
s"Metadata key '$metadataKey' not found in TopicDirective for topic ${topicDirective.topic}. " +
s"Available keys: ${topicDirective.metadata.keys.mkString(", ")}"
```

This is **production-quality error reporting**:
- ✅ Tells user exactly what's wrong
- ✅ Shows what WAS provided (available keys)
- ✅ Includes context (topic name)
- ✅ Actionable (user knows exactly what to fix)

### ✅ **STRENGTH #2: Comprehensive Either-Based Error Handling**

**No exceptions thrown** - all errors are captured in Either. This is **excellent functional programming**:
- ✅ Errors are values, not control flow
- ✅ Composable with `.map`, `.flatMap`
- ✅ Type-safe error propagation
- ✅ Forces caller to handle errors

### ✅ **STRENGTH #3: Recursive JSON Traversal**

**Lines 44-74**: The `json.fold()` approach is **idiomatic circe usage**:
- ✅ Handles all JSON types exhaustively
- ✅ Preserves structure while transforming strings
- ✅ Properly propagates errors through recursion

### ✅ **STRENGTH #4: Template Variable Patterns**

The three pattern types (config paths, metadata keys, directive fields) provide **flexible templating**:
- ✅ Clear separation of concerns
- ✅ Regex patterns are well-formed
- ✅ Fallthrough to literal string is correct

### ✅ **STRENGTH #5: Module Scoping**

**Line 14**: `private[services]` is correct scoping for this utility:
- ✅ Visible within services module for testing
- ✅ Hidden from external modules
- ✅ Follows project conventions

---

## Recommendations Summary

### Must Fix (Blocks Merge)

1. **Remove `private` from all methods** (visibility pattern violation)
2. **Accumulate all errors** instead of returning only first error
3. **Add type-safe field reference** ADT instead of hardcoded strings

### Should Fix (Before Production)

4. **Use specific exception types** in config reading (ConfigException.*)
5. **Document regex pattern ordering** dependency
6. **Remove or redesign validate()** method to avoid duplicate work

### Nice to Have (Polish)

7. **Apply Scala 3 `then` keyword** to if statements
8. **Enhance error messages** with hints
9. **Reorganize imports** per style guide

---

## Code Quality Metrics

| Metric | Score | Notes |
|--------|-------|-------|
| **Functional Programming** | A | Excellent Either usage, pure functions |
| **Type Safety** | B- | Hardcoded strings reduce compile-time safety |
| **Error Handling** | B+ | Comprehensive but loses first error only |
| **Testability** | **D** | **Private methods block 30-40% coverage** |
| **Maintainability** | B | Clear structure, could be more extensible |
| **Style Conformance** | B- | Minor Scala 3 syntax issues |
| **Documentation** | C+ | No docs (but per policy, added later) |

**Overall: B- (Requires Revision)**

---

## Testing Requirements

**Unit Tests Needed** (currently missing):
```scala
// After visibility pattern fix, write:
class RequestBodyBuilderTest extends AnyFunSuite with Matchers {

  test("substituteStringValue resolves config path pattern") {
    // Test regex matching for {{$^config.path}}
  }

  test("substituteStringValue resolves metadata key pattern") {
    // Test regex matching for {{'key'}}
  }

  test("substituteStringValue resolves directive field pattern") {
    // Test regex matching for {{fieldName}}
  }

  test("resolveConfigPath returns error when path not found") {
    // Test error message quality
  }

  test("resolveMetadataKey returns error with available keys") {
    // Verify error includes available keys list
  }

  test("resolveDirectiveField rejects unknown fields") {
    // Test all valid fields, test rejection message
  }

  test("substituteVariables processes nested JSON objects") {
    // Test recursive traversal
  }

  test("substituteVariables processes JSON arrays") {
    // Test array traversal
  }

  test("substituteVariables accumulates multiple errors") {
    // After fixing Issue #2
  }
}
```

---

## Final Verdict

**REJECT - Requires Revision**

This implementation demonstrates solid functional programming foundations and comprehensive error handling, but **CRITICAL visibility pattern violations** prevent it from meeting project quality standards. The private methods block 30-40% of potential test coverage, which is unacceptable for production code.

**Required before merge**:
1. ✅ Apply visibility pattern (remove `private` from methods)
2. ✅ Fix error accumulation (return all errors, not just first)
3. ✅ Add type-safe field references (sealed ADT)
4. ✅ Write comprehensive unit tests (target: 85%+ coverage)

**After these fixes**, this will be **excellent production-quality code**.

---

**Review Conducted By**: Scala Ninja (Expert-Level Peer Review Agent)
**Review Date**: 2025-10-23
**Lines Analyzed**: 161
**Complexity Assessment**: Medium (recursive JSON traversal, regex matching, error handling)