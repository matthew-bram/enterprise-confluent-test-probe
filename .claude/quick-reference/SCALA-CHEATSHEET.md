# Scala 3 Quick Reference - Test-Probe Project

**Ultra-condensed. If in doubt, read `.claude/styles/scala-conventions.md`**

---

## Scala 3 Syntax

### ADTs (Use `enum`)
```scala
enum TestState:
  case Idle, Loading, Loaded, Executing, Complete, Failed(error: Throwable)
```

### Implicits (Use `given`/`using`)
```scala
given ExecutionContext = ExecutionContext.global
def runAsync(task: Task)(using ec: ExecutionContext): Future[Result] = ???
```

### Extension Methods
```scala
extension (s: String)
  def toTestId: TestId = TestId(s)
```

### Brace-less Syntax (Mandatory)
```scala
def process(input: String): Result =
  if input.isEmpty then Result.Empty
  else Result.Success(input)
```

---

## Error Handling

| Pattern | Use Case | Example |
|---------|----------|---------|
| `Try[T]` | Operations that may fail | `Try(readFile(path))` |
| `Either[E, T]` | Typed errors (pure functions) | `Either[ParseError, Result]` |
| `Option[T]` | Optional values | `map.get(key)` |
| `Future { throw }` | Async errors | Converts to `Future.failed` |

**Throwing Exceptions - Context Matters**:

```scala
// ✅ CORRECT - In Future block (idiomatic, converts to failed Future)
Future {
  if !isValid then throw new IllegalStateException("Invalid")
  processData()
}

// ✅ CORRECT - Pure function with Either
def parse(s: String): Either[ParseError, Result] =
  if s.isEmpty then Left(ParseError("Empty"))
  else Right(Result(s))

// ❌ WRONG - Throwing in pure business logic (not wrapped)
def calculatePrice(amount: Double): Double =
  if amount < 0 then throw new IllegalArgumentException("Negative")
  else amount * 1.1
```

---

## Visibility Pattern (CRITICAL)

```scala
// ✅ CORRECT - 85%+ coverage possible
private[core] object MyActor:
  def receiveBehavior(...): Behavior[Command] = ???  // Public method
  def handleMessage(...): Behavior[Command] = ???    // Public method
```

**Rule**: Methods PUBLIC, visibility at object/class level. See `BEFORE-YOU-CODE.md` for details.

---

## Actor Patterns

### Typed Actors
```scala
object MyActor:
  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    receiveBehavior(ctx)
  }

  def receiveBehavior(ctx: ActorContext[Command]): Behavior[Command] =
    Behaviors.receiveMessage {
      case cmd: SomeCommand => handleCommand(ctx, cmd)
    }
```

### FSM Pattern
```scala
Behaviors.withStash(100) { buffer =>
  Behaviors.withTimers { timers =>
    Behaviors.setup { ctx =>
      initialBehavior(ctx, buffer, timers)
    }
  }
}
```

### Message Protocols (Sealed Traits)
```scala
sealed trait Command
object Command:
  case class Start(replyTo: ActorRef[Response]) extends Command
  case class Stop(reason: String) extends Command
  case object GetStatus extends Command
```

---

## Common Patterns

### Immutability
```scala
// ✅ Use immutable collections
val list = List(1, 2, 3)
val map = Map("key" -> "value")

// ❌ Avoid mutable state in ACTORS (services can use @volatile var for initialization)
// Actor - WRONG
var state: TestState = Idle

// Service - OK for lazy initialization
@volatile private var client: Option[S3Client] = None
```

### Pattern Matching
```scala
result match
  case Success(value) => processSuccess(value)
  case Failure(error) => handleError(error)
```

### For Comprehensions
```scala
for
  user <- getUser(id)
  profile <- getProfile(user)
  settings <- getSettings(profile)
yield UserData(user, profile, settings)
```

---

## Anti-Patterns (What NOT to Do)

| ❌ Avoid | ✅ Use Instead |
|----------|----------------|
| `private` methods | Visibility at object level |
| Throwing in pure logic | `Try`/`Either` (OK in Future blocks) |
| `var` in actors | Immutable state transitions (OK in services) |
| Java-style loops | `.map`, `.flatMap`, `.foreach` |
| `null` | `Option[T]` |
| `Any` type | Specific types |
| Abbreviations | Full names (`ctx` → `context`) |
| `.formatted()` (deprecated) | `"%.1f".format(value)` |

**String Formatting**:
```scala
// ❌ DEPRECATED - Don't use .formatted()
val rate = successRate.formatted("%.1f")

// ✅ CORRECT - Use format on the format string
val rate = "%.1f".format(successRate)
```

---

## Naming Conventions

```scala
// Classes/Traits: PascalCase
class TestExecutionActor
trait BlockStorageService

// Methods/values: camelCase
def processTest(input: TestInput): Result
val testResult: TestResult

// Constants: PascalCase
val MaxRetries: Int = 3

// Type parameters: Single uppercase
def process[T](input: T): Result[T]
```

---

## Type Safety

```scala
// ✅ Use opaque types for domain modeling
opaque type TestId = String
object TestId:
  def apply(value: String): TestId = value
  extension (id: TestId)
    def value: String = id

// ✅ Use phantom types for builder pattern
sealed trait BuilderState
sealed trait Uninitialized extends BuilderState
sealed trait Initialized extends BuilderState
```

## No Scala/Java Docs
Do not add scala or java docs to the code until
instructed. This will be a pre-release phase operation.
Do not add inline comments unless it's completely
not obvious. Make your code be expressive!

---

## When in Doubt

📖 Full guide: `.claude/styles/scala-conventions.md`
📖 Actor patterns: `.claude/styles/akka-patterns.md`
📖 Testing: `.claude/styles/testing-standards.md`

**Load context**: `/context-actor-work` or `/context-refactor` or `/context-code`