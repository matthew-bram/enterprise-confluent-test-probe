---
description: Load refactoring context with code quality enforcement
---

You are refactoring code. Loading code quality context:

1. **Load Required Skills:**
   ```
   Use the Skill tool to load: scala-conventions-enforcer
   Use the Skill tool to load: visibility-pattern-guardian
   ```

2. **Quick Reference (Read First):**
   - `.claude/quick-reference/SCALA-CHEATSHEET.md` - Ultra-condensed Scala patterns
   - `.claude/quick-reference/BEFORE-YOU-CODE.md` - Visibility pattern (critical!)

3. **Full Guides (If in doubt):**
   - `.claude/styles/scala-conventions.md` - Scala 3 standards
   - `.claude/skills/visibility-pattern-guardian/SKILL.md` - Visibility pattern details

3. **Critical Refactoring Patterns:**

   **Visibility Pattern (MOST IMPORTANT):**
   ```scala
   // ❌ WRONG - Reduces coverage by 20-40%
   object MyActor {
     private def receiveBehavior(...) = { ... }  // Cannot unit test
   }

   // ✅ CORRECT - Enables 85%+ coverage
   private[core] object MyActor {
     def receiveBehavior(...) = { ... }  // Can unit test
   }
   ```

   **Scala 3 Syntax:**
   - Use `enum` for ADTs
   - Use `given`/`using` for implicits
   - Use extension methods
   - Brace-less syntax where appropriate

   **Error Handling:**
   - Use `Try[T]` for operations that may fail
   - Use `Either[Error, T]` for typed errors
   - Avoid throwing exceptions in business logic

4. **Refactoring Checklist:**
   - [ ] Apply visibility pattern to all objects/classes
   - [ ] Extract inline lambdas to named functions
   - [ ] Update to Scala 3 syntax
   - [ ] Maintain/improve test coverage (run tests before & after)
   - [ ] Update documentation if behavior changes

**Ready for safe refactoring with quality enforcement active.**