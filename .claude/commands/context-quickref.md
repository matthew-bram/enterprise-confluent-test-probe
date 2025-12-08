---
description: Load quick reference materials
---

**QUICK REFERENCE MATERIALS**

## Ultra-Condensed Cheat Sheets
📖 `.claude/quick-reference/SCALA-CHEATSHEET.md` - All Scala patterns in one page
📖 `.claude/quick-reference/TESTING-CHEATSHEET.md` - All testing patterns in one page
📖 `.claude/quick-reference/BEFORE-YOU-CODE.md` - Visibility pattern visual guide

## Critical Patterns

### Visibility Pattern (Prevents 20-40% Coverage Loss)
```scala
// ❌ WRONG - Cannot unit test
object MyActor {
  private def receiveBehavior(...) = { ... }
}

// ✅ CORRECT - Can unit test
private[core] object MyActor {
  def receiveBehavior(...) = { ... }  // Public method, module-scoped object
}
```

### Error Handling
```scala
// Use Try for operations that may fail
def readFile(path: String): Try[String] = Try {
  Source.fromFile(path).mkString
}

// Use Either for typed errors
def parse(input: String): Either[ParseError, Result] = {
  if (valid(input)) Right(Result(input))
  else Left(ParseError("Invalid input"))
}
```

### Scala 3 ADTs
```scala
// Use enum for sum types
enum TestState:
  case Idle
  case Loading
  case Loaded
  case Executing
  case Complete
```

### Implicits (Scala 3)
```scala
// Use given/using
given ExecutionContext = ExecutionContext.global

def runAsync(task: Task)(using ec: ExecutionContext): Future[Result] = ???
```

## Testing Commands

```bash
# Fast unit tests only
mvn test -Punit-only -pl test-probe-core

# Component tests
mvn test -Pcomponent-only -pl test-probe-core

# Coverage report
mvn scoverage:report -pl test-probe-core
```

## Build Commands

```bash
# Clean compile
mvn clean compile -pl test-probe-core -q

# Install deps for downstream
mvn install -pl test-probe-common,test-probe-core -DskipTests -q

# Full rebuild
mvn clean install -DskipTests -q
```

## Definition of Done
✅ Tests run: X, Failures: 0, Errors: 0
✅ BUILD SUCCESS
✅ Coverage ≥ 70% (85%+ for actors)
✅ Visibility pattern applied

## Context Loaders

**Single Context:**
- `/context-code` - General Scala coding (use before any coding session)
- `/context-new-feature` - Feature development workflow
- `/context-actor-work` - Akka/Pekko actor development
- `/context-testing` - Test development and quality
- `/context-refactor` - Code refactoring with quality checks
- `/context-review` - Comprehensive code review
- `/context-build` - Build and compilation help
- `/context-architecture` - Architecture documentation

**Chaining for Combined Workflows:**
```bash
# Integration testing
/context-code
/context-testing

# Actor implementation + testing
/context-actor-work
/context-testing

# Service refactoring + testing
/context-refactor
/context-testing
```

**Quick reference loaded. Use context loaders for full workflow support.**