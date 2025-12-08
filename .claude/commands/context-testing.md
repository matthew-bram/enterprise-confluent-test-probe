---
description: Load testing workflow context
---

You are working on tests. Loading test quality enforcement context:

1. **Load test-quality-enforcer skill**
   ```
   Use the Skill tool to load: test-quality-enforcer
   ```

2. **Quick Reference (Read First):**
   - `.claude/quick-reference/TESTING-CHEATSHEET.md` - Ultra-condensed test commands & patterns

3. **Full Guides (If in doubt):**
   - `.claude/guides/TESTING.md` - Comprehensive testing guide
   - `.claude/styles/testing-standards.md` - Unit test standards
   - `.claude/styles/bdd-testing-standards.md` - BDD/Cucumber standards
   - `maven-commands-reference.md` - Test execution commands

3. **Zero-Tolerance Test Quality:**
   - ✅ All tests MUST pass (Failures: 0, Errors: 0)
   - ✅ Coverage: 70%+ min, 85%+ for actors/FSMs
   - ✅ Clear `/tmp` before testing (prevents stale data)
   - ✅ Two-phase testing: Module (fast feedback) → Full regression

4. **Test Profiles:**
   ```bash
   # Unit tests only (FAST - use for module testing)
   mvn test -Punit-only -pl test-probe-core

   # Component tests only
   mvn test -Pcomponent-only -pl test-probe-core

   # Full regression (both unit + component)
   mvn test -pl test-probe-core
   ```

5. **Coverage Reporting:**
   ```bash
   mvn scoverage:report -pl test-probe-core
   # Report: test-probe-core/target/site/scoverage/index.html
   ```

**Definition of Done:**
- [INFO] Tests run: X, Failures: 0, Errors: 0, Skipped: 0
- [INFO] BUILD SUCCESS

**Ready for test development with quality enforcement active.**