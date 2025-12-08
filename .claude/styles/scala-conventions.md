# Scala Style Conventions

## Formatting
- **Indentation**: 2 spaces, no tabs
- **Line length**: Maximum 180 characters
- **Braces**: Opening brace on same line as declaration
- **Scala 3 Syntax**: Use Scala 3 "then" keyword for if/else
  - `if condition then ...` NOT `if (condition) { ... }`
  - Use `then` keyword for single-line and multi-line blocks
  - Omit parentheses in if conditions when using `then` 

## Naming Conventions
- **Classes/Objects/Traits**: PascalCase (`QueueActor`, `TestExecutionActor`)
- **Methods/Variables**: camelCase (`submitTest`, `testRunner`)
- **Constants**: UPPER_SNAKE_CASE (`MAX_RETRIES`, `DEFAULT_TIMEOUT`)
- **Packages**: lowercase with dots (`com.company.testing.probe`)
- **Classes**: Classes should never be post fixed with *Impl

## Import Organization
1. Standard library imports (java.*, scala.*)
2. Third-party library imports (akka.*, play.*)
3. Project imports (com.company.*)
4. Relative imports

Group imports and sort alphabetically within each group. Use wildcard imports sparingly.

Example:
```scala
import java.util.UUID
import java.time.Instant

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import play.api.libs.json._

import com.company.testing.probe.models._
import com.company.testing.probe.services.S3Service
```

Do not perform inline imports.

Example:
```scala
import scala.concurrent.{ExecutionContext, Future}

Future {}
```
Do NOT:
```scala
scala.concurrent.Future {}
// OR
val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
```

## Package Structuring
- Instead of:
```scala
package com.mycompany.probe.actors.routers
```
- Use this instead:
```scala
package com.mycompany.probe
package actors
package routers
```
- This allows for specific imports and not having to provide
fully qualified package paths for every import. An example:
```scala
package com.mycompany.probe
package actors
package routers

import actors.MyActor
```

### -DO NOT DO THIS-
```scala
val a: Either[java.io.IOException, BlockStorageDirective] = ???
```
Do not put inline imports into the code. It's *embarassing*. This needs to be an
immediate peer review rejection.
Do this instead:
```scala
import java.io.IOException

val a: Either[IOException, BlockStorageDirective] = ???
```

When needing to override and use two scoped Types, do this instead of
inline imports:
```scala
import io.distia.probe.core.models.Data
import io.distia.probe.common.models.Data as CommonData
```

## Code Structure
- One public class/object per file
- Companion objects in same file as class
- Package objects for shared types and implicits
- Sealed traits for ADTs should have all cases in same file

## Method Definitions
- Use `def` for methods that might be overridden
- Use `val` for computed values that won't change
- Use `lazy val` for expensive computations that may not be used
- Explicit return types for public methods

## Explicit Return Types
- **Public methods**: MUST have explicit return types
- **Public vals/vars**: MUST have explicit types
- **Local variables**: PREFER explicit type annotations for clarity
  - ✅ `val resourcePath: String = path.stripPrefix("classpath:")`
  - ✅ `val filePath: Path = Paths.get(path)`
  - ✅ Explicit types make code self-documenting and easier to read
  - Simple cases can use inference: `val name = "test"` is acceptable
- Prefer human readable and expressible statements that read naturally
- Typing is paramount - when in doubt, add the type annotation 

## Pattern Matching
- Exhaustive pattern matching preferred
- Use sealed traits for match completeness checking
- Align case statements

Example:
```scala
state match {
  case Setup    => handleSetup()
  case Loading  => handleLoading()
  case Loaded   => handleLoaded()
  case Testing  => handleTesting()
  case _        => handleUnknown()
}
```

## Error Handling

### Pure Business Logic
- Use `Try`, `Either`, or `Option` instead of exceptions for pure functions
- Exceptions should be wrapped in these types for composability

### Futures and Async Code
- **Throwing exceptions inside `Future { ... }` blocks is CORRECT**: The Future will catch them and convert to a failed Future
- This is idiomatic Scala - exceptions in Future blocks are expected behavior
- Example: `Future { throw new IllegalStateException("error") }` → becomes `Future.failed`

### Actors
- Akka actors should handle errors gracefully and return to a known state
- Use supervision strategies for fault tolerance
- Log errors with appropriate context

```scala
// ✅ CORRECT - Throwing in Future block (converts to failed Future)
def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
  if !isValid(ctx) then
    throw new IllegalStateException("Invalid context")
  ctx
}

// ✅ CORRECT - Pure function using Try/Either
def parseUri(uri: String): Either[ParseError, Uri] =
  if uri.isEmpty then Left(ParseError("Empty URI"))
  else Right(Uri(uri))

// ❌ WRONG - Throwing in pure business logic (not wrapped)
def calculateDiscount(price: Double): Double =
  if price < 0 then throw new IllegalArgumentException("Negative price")
  else price * 0.1
```

## Non-Blocking Bias
- As much as reasonably possible, `Future[T]` should be preferred
- Use `.map()` as opposed to `.onSuccess()`
- Only use `.successful(...)` when a new thread does not need to be spawned,
or when in the midsts of functional flows
- Tread safety is of the utmost importance. `Thread.sleep(...)` should never be found
in the code base, including tests. Find another way using Futures

## Functional Styling - Applies EVERYWHERE

### If/Else Syntax
- **Use Scala 3 "then" keyword**: `if condition then ...`
- **Omit parentheses**: No parentheses in if conditions
- **Use negation operator**: `!condition` NOT `condition == false`

```scala
// ✅ CORRECT - Scala 3 style
if path.startsWith("classpath:") then
  loadFromClasspath()
else
  loadFromFilesystem()

// ✅ CORRECT - negation with !
if !Files.exists(filePath) then
  throw new IllegalArgumentException(s"File not found: $path")

// ❌ WRONG - Scala 2 style with parentheses and braces
if (path.startsWith("classpath:")) {
  loadFromClasspath()
} else {
  loadFromFilesystem()
}
```

### Option Handling
- **Wrap Java nulls in Option**: `Option(javaMethod())` for Java interop
- **Use pattern matching**: NEVER use `.get`, `.isEmpty` checks, or null comparisons
- **Be explicit and safe**: Pattern matching makes null handling visible

```scala
// ✅ CORRECT - Option wrapping + pattern matching
Option(getClass.getClassLoader.getResourceAsStream(resourcePath)) match {
  case Some(stream) => Using.resource(Source.fromInputStream(stream))(_.mkString)
  case None => throw new IllegalArgumentException(s"Classpath resource not found: $resourcePath")
}

// ❌ WRONG - direct null check (brittle, easy to forget)
val stream = getClass.getClassLoader.getResourceAsStream(path)
if (stream == null) {
  throw new IllegalArgumentException(s"Resource not found: $path")
}

// ❌ WRONG - .isEmpty + .get (defeats the purpose of Option)
val stream: Option[InputStream] = Option(getClass.getResourceAsStream(path))
if stream.isEmpty then throw new IllegalArgumentException(...)
Using.resource(Source.fromInputStream(stream.get))(_.mkString)
```

### Try/Either Conversion
- **Use `.toEither`**: Don't manually fold Try to Either
- **Use `.left.map`**: Transform error to desired type (Throwable, String, custom error)
- **Preserve exception context**: Wrap in new exception with cause when needed

```scala
// ✅ CORRECT - .toEither with .left.map to wrap exception
Try {
  Option(getClass.getClassLoader.getResourceAsStream(resourcePath)) match {
    case Some(stream) => Using.resource(Source.fromInputStream(stream))(_.mkString)
    case None => throw new IllegalArgumentException(s"Classpath resource not found: $resourcePath")
  }
}.toEither.left.map(ex =>
  new IllegalStateException(s"Failed to load classpath resource '$resourcePath': ${ex.getMessage}", ex)
)

// ✅ CORRECT - .toEither with .left.map to String (when Either[String, T] is needed)
Try {
  loadResource(path)
}.toEither.left.map(ex => s"Failed to load '$path': ${ex.getMessage}")

// ❌ WRONG - manual fold (verbose and harder to read)
Try {
  loadResource(path)
}.fold(
  ex => Left(new IllegalStateException(s"Failed: ${ex.getMessage}", ex)),
  result => Right(result)
)
```

### General Principles
- **Explicit over implicit**: Type annotations make code self-documenting
- **Pattern matching over conditionals**: Safer and more expressive
- **Functional transformations**: Prefer `.map`, `.flatMap`, `.fold` over explicit if/else
- **Be expressive, not imperative**: Let the code read naturally

**Instead**: Write expressive code:
```scala
// ❌ WRONG
val x = data.map(d => d.value * 2)  // Double each value

// ✅ CORRECT
val doubledValues: List[Int] = data.map(value => value * 2)
```

## Comments and Documentation Policy

**ABSOLUTE RULE**: NO comments in code during development.

**Why**: Comments become stale, misleading, and create maintenance burden. Code should be self-documenting through expressive naming, clear structure, and explicit types.

**Forbidden**:
- ❌ Inline comments explaining what code does
- ❌ Block comments describing implementation
- ❌ TODO comments (use TodoWrite tool instead)
- ❌ JavaDocs / ScalaDocs (added separately in documentation phase)
- ❌ Commented-out code (delete it, git remembers)

**ONE EXCEPTION - Security Warnings ONLY**:
✅ Security warnings like those in VaultActor are the ONLY acceptable comments:

```scala
// ⚠️  SECURITY: DO NOT LOG KafkaSecurityDirective - contains client secrets
// ⚠️  SECURITY: Credentials must be redacted from all log output
```

**Security Warning Format**:
- Must start with `// ⚠️  SECURITY:`
- Must describe a concrete security risk
- Must provide specific guidance (what NOT to do)
- Use sparingly - only for genuine security concerns

**Examples of UNACCEPTABLE comments**:
```scala
// This method processes the test directive  ❌ States the obvious
// TODO: Add error handling                  ❌ Use TodoWrite tool
// Calculate the total sum                   ❌ Code should be self-explanatory
```

## JavaDocs / ScalaDocs
Do not add javadocs or scaladocs. These will be added in a separate documentation phase after code is complete and stable.

## Code for Testing

### Visibility Pattern for Testability (REQUIRED)

**Pattern**: Keep methods public, apply visibility restriction at class/object level

This is a **peer review rejection point** - all new code must follow this pattern unless specifically justified.

```scala
// CORRECT: Methods public, object scoped to module
private[core] object TestExecutionActor {

  // All methods are public (no visibility modifier)
  def setupBehavior(...): Behavior[Command] = { ... }

  def identifyChildActor(child: ActorRef[_], data: TestData): String = { ... }

  def createStatusResponse(testId: UUID, state: String, data: TestData): Response = { ... }

  // Factory methods also public
  def defaultBlockStorageFactory(testId: UUID): BlockStorageFactory = { ... }
}
```

```scala
// INCORRECT: Methods marked private, prevents unit testing
object TestExecutionActor {

  private def setupBehavior(...): Behavior[Command] = { ... }  // ❌ Cannot unit test

  private def identifyChildActor(...): String = { ... }        // ❌ Cannot unit test
}
```

**Benefits**:
- ✅ **Comprehensive unit testing**: All helper methods directly testable
- ✅ **Module encapsulation**: `private[core]` prevents external access
- ✅ **Test coverage**: Enables 85%+ coverage without hacky workarounds
- ✅ **Refactoring safety**: Helper methods have regression protection

**Scope Guidelines**:
- Use `private[core]` for core module actors/services
- Use `private[common]` for common module utilities
- Use `private[glue]` for Cucumber step definitions
- Use `private[actors]` only when limiting to actors subpackage

**Exceptions** (require justification in PR):
- Internal state that genuinely must be hidden
- Temporary/intermediate methods during refactoring
- Security-sensitive operations

### Dependency Injection Pattern

- Use parameters over "has a" whenever possible
- Use parameter factories as dependency injection in class construction.
While the example below is trivial, it demonstrates the foundation
for building clean IOC within the code. Not everything must be coded this way -
it is mainly used when testing with mocks is important and other cases where
it makes sense to IOC the class members. -CAUTION- Use sparingly and don't be
overzealous.
```scala
class MyClass(val a: Adapter) {
  // uses "a" somewhere in the class
}
object MyClass {
  def apply(a: Option[Adapter] = None): MyClass = a match {
    case Some(a) => new MyClass(a)
    case None => new MyClass(new DefaultAdapter)
  }
}
```
As opposed to:
```scala
class MyClassHasA {
  val a: Adapter = new DefaultAdapter
}
```

## General Coding Style
- **NO comments** - See "Comments and Documentation Policy" above (exception: security warnings only)
- **Self-documenting code** - Express intent through naming, types, and structure
- **Expressive over clever** - Clarity beats brevity

---

## Expressive Scala Patterns - "Code That Sings"

**Philosophy**: "When you look at elegant code, it just sings to you like reading your favorite book."

These patterns transform Java-looking "barely compiles" code into beautiful, expressive Scala that is more readable, more maintainable, and dramatically more compact.

### Expression-Oriented Method Bodies

**Pattern**: Method body is a direct expression, not a statement block

**❌ STATEMENT-ORIENTED (Java-like)**:
```scala
def build(args): Result = {
  value match {
    case None =>
      Left(new ValidationError(
        s"Value is required for operation"
      ))
    case Some(v) =>
      processValue(v)
        .map(_.serialize)
  }
}
```

**✅ EXPRESSION-ORIENTED (Beautiful Scala)**:
```scala
def build(args): Result = value match {
  case None => Left(new ValidationError(s"Value is required for operation"))
  case Some(v) => processValue(v).map(_.serialize)
}
```

**Key Changes**:
- Remove `= {` and start directly with the expression
- Single-line cases when under ~160 characters
- Still break at logical points for longer lines

**Reduction**: Typically 50-70% fewer lines while maintaining clarity

### Line Breaking Conventions

#### Rule 1: Line Length Tolerance
- **Target**: 180 characters
- **Maximum tolerance**: ~132 characters (~10% over)
- **Action if exceeded**: Break at first logical point

```scala
// ❌ TOO LONG (exceeds 132 chars significantly)
case None => Left(new VaultMappingException(s"Request template is required in Rosetta config for topic ${topicDirective.topic} and environment ${config.environment}"))

// ✅ BREAK AT ERROR MESSAGE PARAMETER
case None => Left(new VaultMappingException(
  s"Request template is required in Rosetta config for topic ${topicDirective.topic}"
))
```

#### Rule 2: Nested Match Expressions
Break immediately after opening brace: `match {`

```scala
// ✅ CORRECT - Break after match {
def process(value: Option[Int]): String = value match {
  case Some(n) => n.toString
  case None => "empty"
}

// ✅ NESTED MATCH - Each match breaks after {
def processNested(outer: Option[Option[Int]]): String = outer match {
  case Some(inner) => inner match {
    case Some(n) => n.toString
    case None => "inner empty"
  }
  case None => "outer empty"
}
```

#### Rule 3: Chained Expressions
If chain exceeds line length, break on the FIRST chained function and indent

```scala
// ❌ TOO LONG
val result = myList.filter(x => x > 0).map(x => x * 2).flatMap(x => Some(x)).getOrElse(0)

// ✅ BREAK ON FIRST CHAIN (.filter goes to new line, indented)
val result = myList
  .filter(x => x > 0)
  .map(x => x * 2)
  .flatMap(x => Some(x))
  .getOrElse(0)

// ✅ ALSO ACCEPTABLE (if within tolerance)
val result = myList.filter(x => x > 0).map(x => x * 2)
```

#### Rule 4: Long Error Messages

**Standard break**: Move message to next line
```scala
Left(new VaultMappingException(
  s"Config path not found: $configPath for topic $topic"
))
```

**Ultra-long messages**: Use triple-quote with stripMargin, close on last content line
```scala
Left(new VaultMappingException(
  s"""Metadata key '$metadataKey' not found in TopicDirective for topic ${topicDirective.topic}.
     | Available keys: ${topicDirective.metadata.keys.mkString(", ")}""".stripMargin ))
```

**Key Pattern**: Close `"""` and add `.stripMargin` at END of last content line (not on separate line)

```scala
// ❌ VERBOSE (extra line with just closing)
s"""First line of message.
   | Second line of message.
   |""".stripMargin

// ✅ COMPACT (closing on last content line)
s"""First line of message.
   | Second line of message.""".stripMargin
```

### Try/Match Pattern (Not Try/Catch)

**Pattern**: Use `Try { } match { case Success/Failure }` instead of if/try/catch with Either

**❌ IMPERATIVE (Java-like) - 17 lines**:
```scala
def readConfig(path: String): Either[ConfigError, String] = {
  if (!configExists(path)) {
    Left(new ConfigError(s"Config not found: $path"))
  } else {
    try {
      Right(loadConfig(path))
    } catch {
      case ex: Exception =>
        Left(new ConfigError(s"Failed to read: ${ex.getMessage}", ex))
    }
  }
}
```

**✅ FUNCTIONAL (Beautiful Scala) - 10 lines**:
```scala
def readConfig(path: String): Either[ConfigError, String] = Try {
  if !configExists(path) then throw new ConfigError(s"Config not found: $path")
  loadConfig(path)
} match {
  case Success(config) => Right(config)
  case Failure(ex) => ex match {
    case ex: ConfigError => Left(ex)
    case ex: Exception => Left(new ConfigError(s"Failed to read: ${ex.getMessage}", ex))
  }
}
```

**Key Patterns**:
- `Try { }` wraps potentially failing code
- Use `if/then/throw` for validation (cleaner than nested if/else)
- Match on `Success` and `Failure` ADT constructors
- Nest match in `Failure` case for exception type discrimination
- Single expression body (no `= {`)

**Benefits**:
- More functional (Try is an ADT, not control flow)
- Pattern matching on Success/Failure
- Nested exception type matching
- Dramatically more compact

### Single-Line Pattern Matching

**Pattern**: When all cases fit under ~132 chars, use single lines

**❌ VERBOSE**:
```scala
def substituteValue(value: String): Either[Error, String] = {
  value match {
    case ConfigPattern(path) =>
      resolveConfig(path)
    case MetadataPattern(key) =>
      resolveMetadata(key)
    case DirectivePattern(field) =>
      resolveDirective(field)
    case _ =>
      Right(value)
  }
}
```

**✅ COMPACT**:
```scala
def substituteValue(value: String): Either[Error, String] = value match {
  case ConfigPattern(path) => resolveConfig(path)
  case MetadataPattern(key) => resolveMetadata(key)
  case DirectivePattern(field) => resolveDirective(field)
  case _ => Right(value)
}
```

**When to use**: All case arms fit on one line within tolerance

### Condensed Conditionals with Scala 3 `then`

**Pattern**: Use `if cond then expr else expr` on one line when possible

**❌ VERBOSE (Scala 2 style)**:
```scala
if (errors.nonEmpty) {
  Left(errors.head)
} else {
  Right(processResults(results))
}
```

**✅ COMPACT (Scala 3 style)**:
```scala
if errors.nonEmpty then Left(errors.head) else Right(processResults(results))
```

**Applies to**:
- Simple if/then/else expressions
- Guard clauses
- Ternary-like conditionals

**Note**: No parentheses around condition, use `then` keyword

### Fluent Chaining with Leading Dot

**Pattern**: Start method chain with object and first operation, indent subsequent operations

**❌ NO VISUAL FLOW**:
```scala
def substituteVariables(json: Json, ...): Either[Error, Json] = {
  json.fold(
    jsonNull = Right(Json.Null),
    jsonBoolean = bool => Right(Json.fromBoolean(bool)),
    // ...
  )
}
```

**✅ FLUENT CHAINING**:
```scala
def substituteVariables(json: Json, ...): Either[Error, Json] = json
  .fold(
    jsonNull = Right(Json.Null),
    jsonBoolean = bool => Right(Json.fromBoolean(bool)),
    // ...
  )
```

**Benefits**:
- Shows fluent API design
- Easy to add more operations in chain
- Visually emphasizes transformation pipeline

### Real-World Example: Complete Transformation

**BEFORE** - 17 lines (Java-looking):
```scala
def build(
  topicDirective: TopicDirective,
  rosettaConfig: RosettaConfig,
  appConfig: Config
): Either[VaultMappingException, String] = {

  rosettaConfig.requestTemplate match {
    case None =>
      Left(new VaultMappingException(
        s"Request template is required in Rosetta config for topic ${topicDirective.topic}"
      ))

    case Some(template) =>
      substituteVariables(template, topicDirective, appConfig)
        .map(_.noSpaces)
  }
}
```

**AFTER** - 7 lines (Beautiful Scala):
```scala
def build(
  topicDirective: TopicDirective,
  rosettaConfig: RosettaConfig,
  appConfig: Config
): Either[VaultMappingException, String] = rosettaConfig.requestTemplate match {
  case None => Left(new VaultMappingException(s"Request template is required in Rosetta config for topic ${topicDirective.topic}"))
  case Some(template) => substituteVariables(template, topicDirective, appConfig).map(_.noSpaces)
}
```

**Reduction**: 59% fewer lines, still completely readable

### Summary: Patterns for Beautiful Scala

1. **Expression-Oriented Bodies** - Remove `= {`, start with expression
2. **Single-Line Cases** - When under ~132 chars tolerance
3. **Try/Match Pattern** - Instead of if/try/catch with Either
4. **Scala 3 `then`** - No parens, use `then`, single-line when possible
5. **Compact stripMargin** - Close `"""` on last content line
6. **Strategic Line Breaks** - Break at first logical point when needed
7. **Fluent Chaining** - Lead with object, indent operations

**Goal**: Code that "sings" - expressive, compact, and a joy to read.

---

## Logging and Debugging
- Logging statements should be production ready, verbose where needed and semantic 
logs (Open Telemetry Models )
- Logging should only be placed in locations that will provide good telemetry and 
distributed stack tracing
- Logging and debugging can be used where using an IDE debugger is not feasible. 
However, all added logging used during development and for the expressed purpose of
debugging should be removed once the bug has been determined and rectified