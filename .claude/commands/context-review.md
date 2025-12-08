---
description: Load code review context with all enforcement skills
---

You are performing a code review. Loading all enforcement skills:

1. **Load All Enforcement Skills:**
   ```
   Use the Skill tool to load: scala-conventions-enforcer
   Use the Skill tool to load: visibility-pattern-guardian
   Use the Skill tool to load: test-quality-enforcer
   ```

2. **Quick Reference (Check First):**
   - `.claude/quick-reference/SCALA-CHEATSHEET.md` - Scala patterns
   - `.claude/quick-reference/TESTING-CHEATSHEET.md` - Testing standards
   - `.claude/quick-reference/BEFORE-YOU-CODE.md` - Visibility pattern

3. **Review Checklist:**

   **Code Quality:**
   - [ ] Follows Scala 3 conventions (`.claude/styles/scala-conventions.md`)
   - [ ] Visibility pattern applied correctly (no untestable `private` methods)
   - [ ] Proper error handling (Try/Either, not exceptions)
   - [ ] Clear, expressive names (no abbreviations)
   - [ ] Appropriate use of Scala 3 features (enum, given, extension)

   **Architecture:**
   - [ ] Follows actor patterns (`.claude/guides/ACTORS.md`)
   - [ ] Error Kernel pattern for fault tolerance
   - [ ] Proper message protocols (immutable, sealed traits)
   - [ ] Type safety (no Any, no null)

   **Testing:**
   - [ ] All tests pass (Failures: 0, Errors: 0)
   - [ ] Coverage ≥ 70% (85%+ for actors/FSMs)
   - [ ] Unit tests for all public methods
   - [ ] Component tests for integration scenarios
   - [ ] BDD scenarios for business workflows

   **Documentation:**
   - [ ] Scaladoc for public API
   - [ ] Inline comments for complex logic
   - [ ] Architecture docs updated if needed
   - [ ] ADRs written for significant decisions

3. **Peer Review Rejection Points:**
   - 🚫 Using `private` methods (visibility pattern violation)
   - 🚫 Tests failing or showing errors
   - 🚫 Coverage below thresholds
   - 🚫 Java-style code (not Scala 3)
   - 🚫 Throwing exceptions in business logic
   - 🚫 Missing documentation for public API

4. **Common Issues to Watch For:**
   - Inline lambdas that should be named functions ("laws")
   - Mutable state in actors
   - Blocking operations in actor code
   - Missing error handling
   - Overly complex methods (>20 lines)

**Ready for comprehensive code review with all quality gates active.**