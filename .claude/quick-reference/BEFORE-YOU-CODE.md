# 🚨 BEFORE YOU CODE - QUICK REFERENCE CARD

**READ THIS BEFORE IMPLEMENTING ANY ACTOR, SERVICE, OR UTILITY CLASS**

---

## ⚠️ #1 VIOLATION: Private Methods (20-40% Coverage Loss)

### ❌ WRONG - DO NOT DO THIS
```scala
object GuardianActor {
  private def receiveBehavior(...): Behavior[Command] = { ... }
  private def handleInitialize(...): Behavior[Command] = { ... }
  private def handleGetQueueActor(...): Behavior[Command] = { ... }
}
```
**Result**: Methods CANNOT be unit tested → 45% coverage

---

### ✅ CORRECT - ALWAYS DO THIS
```scala
private[core] object GuardianActor {
  def receiveBehavior(...): Behavior[Command] = { ... }
  def handleInitialize(...): Behavior[Command] = { ... }
  def handleGetQueueActor(...): Behavior[Command] = { ... }
}
```
**Result**: All methods unit testable → 85%+ coverage

---

## The Pattern (3 Simple Rules)

1. **NO `private` on methods** - Keep all methods public
2. **Visibility at object/class level** - Use `private[core]`, `private[common]`, etc.
3. **Write direct unit tests** - Test every helper method directly

---

## Module Scopes

| Module | Scope |
|--------|-------|
| `test-probe-core` | `private[core]` |
| `test-probe-common` | `private[common]` |
| `test-probe-glue` | `private[glue]` |
| `actors` package only | `private[actors]` |

---

## Pre-Code Checklist

Before writing actor/service code:
- [ ] Object/class has `private[moduleName]` modifier
- [ ] Zero methods have `private` keyword
- [ ] Planned unit tests for all helper methods
- [ ] Target: 85%+ coverage for actors/FSMs, 70%+ minimum

---

## Why This Matters

| Pattern | Coverage | Testability | Debugging |
|---------|----------|-------------|-----------|
| `private` methods | 45% | ❌ Cannot test | ❌ Hard |
| Visibility pattern | 85%+ | ✅ Full coverage | ✅ Easy |

**Proven in codebase:**
- TestExecutionActor: Visibility pattern → 84+ tests, 85%+ coverage
- GuardianActor (initial): Private methods → ~45% coverage
- GuardianActor (fixed): Visibility pattern → 85%+ coverage target

---

## Full Style Guides (Read Before First Implementation)

1. `.claude/styles/scala-conventions.md` - Scala coding standards
2. `.claude/styles/testing-standards.md` - Testing patterns
3. `.claude/styles/akka-patterns.md` - Akka actor patterns
4. `.claude/styles/bdd-testing-standards.md` - BDD guidelines

---

## Peer Review Rejection Points

Code WILL be rejected if:
- ❌ Methods marked `private` without justification
- ❌ Helper methods cannot be unit tested directly
- ❌ Coverage below 70% due to untestable private methods
- ❌ Visibility pattern not followed

---

## Quick Decision Tree

```
Are you implementing an actor, service, or utility?
    ↓
YES → Add `private[moduleName]` to object/class
    ↓
Do helper methods need encapsulation?
    ↓
YES → Module scope handles it (NO `private` on methods needed)
    ↓
Write code with ALL public methods
    ↓
Write direct unit tests for ALL methods
    ↓
Run coverage: `mvn clean test scoverage:report`
    ↓
Target achieved (85%+ for actors, 70%+ minimum)?
    ↓
YES → ✅ Ready for commit
NO  → ❌ Add more unit tests for helper methods
```

---

**Remember**: `private` methods = Untestable methods = Low coverage = Rejected PR