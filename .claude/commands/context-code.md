---
description: Load general Scala coding context
---

You are starting a Scala coding session. Loading general coding context:

1. **Load scala-conventions-enforcer skill** - MANDATORY before ANY Scala work
   ```
   Use the Skill tool to load: scala-conventions-enforcer
   ```

2. **Quick Reference (Read First):**
   - `.claude/quick-reference/SCALA-CHEATSHEET.md` - Ultra-condensed Scala patterns
   - `.claude/quick-reference/BEFORE-YOU-CODE.md` - Visibility pattern (critical!)

3. **Full Guides (If in doubt):**
   - `.claude/styles/scala-conventions.md` - Scala 3 coding standards (authoritative)
   - `.claude/styles/akka-patterns.md` - Akka/Pekko patterns

4. **Key Patterns:**

   **Error Handling:**
   - ✅ Throwing in Future blocks (idiomatic, converts to failed Future)
   - ✅ Pure functions with Try/Either
   - ❌ Throwing in pure business logic (not wrapped)

   **Visibility Pattern:**
   - ✅ Methods PUBLIC, use `private[module]` at object level
   - ❌ `private` methods (reduces coverage 20-40%)

   **Scala 3 Syntax:**
   - Use `enum` for ADTs
   - Use `given`/`using` for implicits
   - Use `if...then` (no parentheses)
   - Explicit types on public methods

5. **Mutable State:**
   - ❌ Avoid in actors (use immutable state transitions)
   - ✅ OK in services (`@volatile var` for lazy initialization)

**Ready for Scala coding with conventions enforced.**

---

## Chaining Commands

**Combine with task-specific contexts:**

```bash
# Integration testing
/context-code
/context-testing

# Actor implementation
/context-code (or use /context-actor-work instead - it includes scala-conventions)

# Service refactoring
/context-code
/context-refactor (includes scala-conventions + visibility-pattern-guardian)

# Code review
/context-code
/context-review
```

**Note**: Some commands like `/context-actor-work` and `/context-refactor` already load `scala-conventions-enforcer`, so you don't need `/context-code` first.