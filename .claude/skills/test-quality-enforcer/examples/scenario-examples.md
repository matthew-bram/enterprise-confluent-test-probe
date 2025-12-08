# test-quality-enforcer - Example Scenarios

**Purpose**: Demonstrate skill activation, enforcement, and edge cases
**Use**: Documentation + Phase 4 test cases

---

## Scenario 1: User Claims Tests Pass (But 47 Errors Exist)

**Context**: Classic violation - user sees "BUILD SUCCESS" but ignores errors

### User Message
```
User: "All tests passing! Ready to commit."
```

### Skill Activation
```
TRIGGER: Keywords detected ("tests passing", "ready to commit")
LOAD: test-quality-enforcer skill
```

### Recent Test Output (in context)
```bash
[ERROR] Tests run: 139, Failures: 0, Errors: 47
...
[INFO] BUILD SUCCESS
```

### Skill Response
```
❌ BLOCKED: Tests are NOT passing

I see test output showing errors:

[ERROR] Tests run: 139, Failures: 0, Errors: 47

Zero-tolerance policy: Errors must be zero, not 47.

These are real failures:
1. NullPointerException in QueueActorSpec (12 occurrences)
2. Timeout in TestExecutionActorSpec (8 occurrences)
3. UndefinedStepException in component tests (27 occurrences)

Tests are NOT passing. Let's fix these before proceeding.

Want me to help debug the first error?
```

### Expected Outcome
- ✅ Skill blocks commit
- ✅ User cannot mark task complete
- ✅ Specific errors identified
- ✅ Helpful offer to debug

---

## Scenario 2: Module Tests Pass, Full Regression Needed

**Context**: User's module is stable, skill enforces Phase 2

### User Message
```
User: "My module tests all pass"
```

### Test Output (Phase 1 Complete)
```bash
# Phase 1: test-probe-core
[INFO] Tests run: 247, Failures: 0, Errors: 0
[INFO] BUILD SUCCESS
```

### Skill Response
```
✅ Phase 1 Complete: test-probe-core is 100% stable!

Module: test-probe-core
  ✅ Compiles: Success
  ✅ Unit tests: 247/247 passing
  ✅ Component tests: 94/94 scenarios passing
  ✅ Zero failures, zero errors

Phase 1 complete: 3 minutes

Now running Phase 2: Full Project Regression
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Testing ALL modules to ensure your changes didn't break anything.

Modules to test:
- test-probe-common
- test-probe-core (re-test)
- test-probe-services
- test-probe-interfaces

Estimated time: 8-10 minutes
⏱️ Patience over speed. Let's wait for comprehensive safety...

Running: mvn test
```

### Expected Outcome
- ✅ Phase 1 success acknowledged
- ✅ Phase 2 automatically triggered
- ✅ Patient messaging
- ✅ Full regression runs

---

## Scenario 3: Forgot to Clear /tmp (Stale Data)

**Context**: User skips Step 0, skill hard blocks

### User Message
```
User: "Run the tests"
```

### Skill Detection
```
DETECT: /tmp/*.log files exist from previous run
RISK: Stale data will cause false positives
```

### Skill Response
```
❌ BLOCK: /tmp must be cleared first

⚠️ /tmp contains stale data from previous runs:
  - /tmp/component-test-run.log (modified 2 hours ago)
  - /tmp/unit-test-results.txt (modified 1 hour ago)

This WILL cause false positives in test results!

Clearing /tmp... (non-negotiable)

rm -f /tmp/*.log /tmp/*.txt

✅ /tmp cleared. Now running tests...
```

### Expected Outcome
- ✅ Hard block enforced
- ✅ /tmp automatically cleared
- ✅ Tests proceed after cleanup
- ✅ No user override allowed

---

## Scenario 4: Coverage Below Threshold

**Context**: Tests pass but coverage is 68% (min: 70%)

### Test Output
```bash
[INFO] Tests run: 247, Failures: 0, Errors: 0
[INFO] BUILD SUCCESS
```

### Coverage Report (parsed)
```xml
<scoverage statement-rate="0.68" branch-rate="0.65" ... />
```

### Skill Response
```
❌ BLOCKED: Coverage below minimum threshold

🎮 Coverage Challenge!
Current: 68% | Target: 70%
Gap: 2% (approximately 6 more test cases needed)

Untested code paths:
- CancelTest error handling (QueueActor.scala:145-167)
- Idempotent cancellation logic (QueueActor.scala:201-215)

Testing is fun! Let's level up this coverage. 🎯

Cannot proceed until 70% threshold met.

Want me to suggest test cases for the uncovered areas?
```

### Expected Outcome
- ✅ Coverage enforcement blocks
- ✅ Gamified messaging
- ✅ Specific gaps identified
- ✅ Helpful offer to suggest tests

---

## Scenario 5: Undefined Cucumber Steps

**Context**: Component tests have undefined steps

### Test Output
```bash
[ERROR] io.cucumber.junit.UndefinedStepException

You can implement missing steps with the snippets below:

When("the QueueActor receives CancelTest for completed test") do
  ...
end

Scenarios: 94 (3 undefined)
```

### Skill Response
```
❌ BLOCKED: Undefined Cucumber steps detected

Undefined steps (3):
1. 'When the QueueActor receives CancelTest for completed test'
2. 'Then the response should indicate test already completed'
3. 'And the test should remain in completed state'

Activating step implementation guidance...

I found similar patterns in QueueActorSteps.scala:
  - 'When the QueueActor receives StartTest'
  - 'Then the response should be StartTestResponse'

Following the same pattern, I'll implement:

File: test-probe-core/src/test/scala/.../QueueActorSteps.scala

When("""the QueueActor receives CancelTest for {string} test""") {
  (testState: String) =>
    val testEntry = getTestEntry(testState)
    queueActor ! CancelTest(testEntry.testId, responseProbe.ref)
}

Implementing now...

[Applies fix]

Re-running component tests to verify...
```

### Expected Outcome
- ✅ Undefined steps detected
- ✅ Pattern matching found
- ✅ Implementation generated
- ✅ Auto-fix applied and verified

---

## Scenario 6: User Wants to Skip Phase 1

**Context**: User wants to run full regression immediately

### User Message
```
User: "Skip Phase 1, just run full regression"
```

### Skill Response
```
⚠️ Phase 1 provides fast feedback (3 min vs 10 min)

But this skill guides CLAUDE's testing workflow, not your terminal.

If you want full regression NOW, you can run it yourself:
  mvn test

If you want ME to run tests, I'll follow the two-phase approach:
  Phase 1 (your module): 3 minutes - fast feedback
  Phase 2 (all modules): 8-10 minutes - comprehensive safety

This ensures efficient testing. Should I proceed with Phase 1?
```

### Expected Outcome
- ✅ Enforcement scope explained
- ✅ User autonomy respected (can run themselves)
- ✅ Skill enforces two-phase when Claude runs
- ✅ Efficiency rationale provided

---

## Scenario 7: Tests Pass, Coverage Excellent

**Context**: Everything green, skill celebrates

### Test Results
```bash
# Phase 1
[INFO] Tests run: 247, Failures: 0, Errors: 0
[INFO] Coverage: 88%

# Phase 2
[INFO] Total tests run: 342, Failures: 0, Errors: 0
[INFO] BUILD SUCCESS
```

### Skill Response
```
🏆 FULL PROJECT GREEN! Your changes are production-ready! 🎉

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COMPREHENSIVE TEST RESULTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Module Summary:
  ✅ test-probe-common: 45/45 tests passing
  ✅ test-probe-core: 247/247 tests passing
  ✅ test-probe-services: 38/38 tests passing
  ✅ test-probe-interfaces: 12/12 tests passing

Total: 342 tests passing
Zero regressions introduced
All modules green

Coverage: 88% (18% above minimum!)

Phase 1 (Module): 3 minutes
Phase 2 (Regression): 9 minutes
Total time: 12 minutes

Worth it for comprehensive safety! ✅

Your feature is READY for production.
```

### Expected Outcome
- ✅ Celebration messaging
- ✅ Complete summary
- ✅ Coverage bonus acknowledged
- ✅ User feels accomplished

---

## Scenario 8: Scoverage Broken

**Context**: Coverage reporting fails

### Error
```bash
[ERROR] Failed to execute goal org.scoverage:scoverage-maven-plugin:2.0.2:report
```

### Skill Response
```
❌ BLOCKED: Coverage reporting broken

Scoverage report not found or failed to generate.
Location expected: target/scoverage-report/scoverage.xml

This is a build infrastructure issue that must be fixed:
1. Verify scoverage plugin in pom.xml
2. Run: mvn clean compile scoverage:report
3. Check for build errors

Cannot enforce coverage thresholds without working scoverage.

Fix scoverage before proceeding.

Want me to help debug the scoverage configuration?
```

### Expected Outcome
- ✅ Hard block on broken infrastructure
- ✅ Clear steps to fix
- ✅ No fallback estimation
- ✅ Helpful offer to debug

---

## Edge Cases

### Edge Case 1: User Running Tests in Terminal

```
User runs in terminal: mvn test

Skill: Does NOT interfere with user's terminal
      Skill only activates when CLAUDE runs tests
```

### Edge Case 2: Mixed Results (Some Pass, Some Fail)

```
Module A: 45/45 passing
Module B: 237/240 passing (3 failures)
Module C: 38/38 passing

Skill: BLOCKS due to Module B failures
       Identifies specific module with issues
       "Module B has 3 failures - let's fix those first"
```

### Edge Case 3: Long-Running Tests

```
After 5 minutes:
"⏱️ Phase 2 Progress: 5 minutes elapsed

  ✅ test-probe-common: 45/45 ✅ (1 min)
  ✅ test-probe-core: 247/247 ✅ (3 min)
  ⏳ test-probe-services: 28/38... (running)
  ⬜ test-probe-interfaces: pending

Estimated: 3-5 more minutes

⏱️ Waiting is OK. Better than rushing and breaking production! ☕"
```

---

## Test Validation Checklist

**Use these scenarios in Phase 4 to verify**:

- [ ] Skill activates on test keywords
- [ ] Blocks on errors (Scenario 1)
- [ ] Enforces two-phase testing (Scenario 2)
- [ ] Hard blocks on /tmp skip (Scenario 3)
- [ ] Enforces coverage thresholds (Scenario 4)
- [ ] Detects and fixes undefined steps (Scenario 5)
- [ ] Respects user terminal autonomy (Scenario 6)
- [ ] Celebrates success appropriately (Scenario 7)
- [ ] Blocks on broken infrastructure (Scenario 8)
- [ ] Handles edge cases gracefully

**Success Criteria**: All scenarios behave as documented

---

**Version**: 1.0
**Created**: 2025-10-21
**Purpose**: Documentation + Phase 4 test cases