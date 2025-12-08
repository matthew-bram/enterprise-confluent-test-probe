# visibility-pattern-guardian - Example Scenarios

**Purpose**: Demonstrate skill activation, enforcement, and edge cases
**Use**: Documentation + Phase 4 test cases

---

## Scenario 1: Private Methods in Actor Object (Classic Violation)

**Context**: Developer creates actor with private methods (reduces coverage by 20-40%)

### User Message
```
User: "I've implemented GuardianActor with the core behavior methods"
```

### Code Written
```scala
// File: test-probe-core/src/main/scala/.../GuardianActor.scala
object GuardianActor {
  private def receiveBehavior(...): Behavior[Command] = { ... }
  private def handleInitialize(...): Behavior[Command] = { ... }
  private def handleGetQueueActor(...): Behavior[Command] = { ... }
  // 8 total private methods
}
```

### Layer 1: Light Warning (Real-time)

**Trigger**: Write tool detects `private def` in actor code

**Response**:
```
⚠️ Testability pattern suggestion

Detected: private def in actor code

📖 Pattern reference:
   .claude/styles/scala-conventions.md (lines 111-160)
   "Visibility Pattern for Testability (REQUIRED)"

From the guide:
"Keep methods public, apply visibility restriction at class/object level"

Quick example (from guide, line 119):
  ✅ private[core] object MyActor {
       def myMethod() = { ... }  // Public method, testable
     }

This will be enforced during code review phase (Phase 2).
```

### Expected Outcome
- ✅ Gentle education provided
- ✅ Points to authoritative guide
- ✅ Shows correct pattern
- ✅ Work continues (not blocked yet)

---

## Scenario 2: scala-ninja Review Detects Violations (Hard Block)

**Context**: User asks for code review, scala-ninja triggers visibility-pattern-guardian

### User Message
```
User: "Can you review GuardianActor? I think it's ready."
```

### scala-ninja Analysis
```
scala-ninja review:
  "Reviewing code against project standards...

  References:
  - .claude/styles/scala-conventions.md
  - .claude/styles/testing-standards.md

  Checking visibility pattern (scala-conventions.md lines 111-160)...

  ❌ Violations found: GuardianActor.scala (8 private methods)

  Triggering visibility-pattern-guardian for enforcement..."
```

### Skill Response (Layer 2: BLOCK)
```
❌ PEER REVIEW REJECTION POINT

Visibility pattern violations detected in GuardianActor.scala

📖 Reference: .claude/styles/scala-conventions.md (lines 111-160)
"Visibility Pattern for Testability (REQUIRED)"

From the guide:
"Keep methods public, apply visibility restriction at class/object level"

Violations found:
  ❌ Line 45: private def receiveBehavior(...): Behavior[Command]
     Cannot unit test this method directly

  ❌ Line 78: private def handleInitialize(...): Behavior[Command]
     Cannot unit test this method directly

  ❌ Line 102: private def handleGetQueueActor(...): Behavior[Command]
     Cannot unit test this method directly

  (5 more private methods - total 8 violations)

Impact:
- 8 methods untestable → ~180 lines of code without regression protection
- Estimated coverage loss: 30-40%
- This was proven in GuardianActor: 45% → 85% coverage after fix

Pattern from guide (line 119):
  ✅ Use private[core] on object
  ✅ Keep methods public (no private keyword)

I can automatically refactor to match the pattern from:
📖 .claude/styles/scala-conventions.md (lines 111-160)

BEFORE (violates guide):
  object GuardianActor {
    private def receiveBehavior(...): Behavior[Command] = { ... }
    private def handleInitialize(...): Behavior[Command] = { ... }
    private def handleGetQueueActor(...): Behavior[Command] = { ... }
  }

AFTER (matches guide):
  private[core] object GuardianActor {
    def receiveBehavior(...): Behavior[Command] = { ... }
    def handleInitialize(...): Behavior[Command] = { ... }
    def handleGetQueueActor(...): Behavior[Command] = { ... }
  }

Changes:
  - Add private[core] to object (line 119 of guide)
  - Remove private from 8 methods (lines 120-131 of guide)
  - Makes all methods unit testable

Benefits (from guide):
  ✅ Comprehensive unit testing: All helper methods directly testable
  ✅ Module encapsulation: private[core] prevents external access
  ✅ Test coverage: Enables 85%+ coverage without hacky workarounds
  ✅ Refactoring safety: Helper methods have regression protection

Approve auto-refactor? (yes/no)
```

### Expected Outcome
- ✅ Hard block at peer review
- ✅ Comprehensive explanation with guide references
- ✅ Clear before/after comparison
- ✅ Offers auto-refactoring with approval

---

## Scenario 3: User Approves Auto-Refactor

**Context**: User approves the auto-refactoring

### User Response
```
User: "yes"
```

### Skill Execution
```
✅ Applying visibility pattern from style guide...

Changes made:
  ✓ GuardianActor.scala: Added private[core] to object (line 10)
  ✓ GuardianActor.scala: Removed private from 8 methods (lines 45, 78, 102, ...)

Recompiling to verify...

  mvn compile -pl test-probe-core -q

✅ Code compiles successfully after refactoring

Pattern applied matches:
  .claude/styles/scala-conventions.md (line 119)

Violations resolved. Ready to proceed to testing phase.
```

### Verification
```bash
# Before refactor (compilation failed with private methods causing test issues)
# After refactor
mvn compile -pl test-probe-core -q
# [INFO] BUILD SUCCESS

# Coverage improvement
# Before: 45% (8 methods untestable)
# After: 85%+ (all methods testable)
```

### Expected Outcome
- ✅ Automatic refactoring applied
- ✅ Code recompiled successfully
- ✅ Pattern matches authoritative guide
- ✅ Ready to proceed to Phase 3 (testing)

---

## Scenario 4: User Defers Refactor ("later")

**Context**: User wants to defer the fix

### User Response
```
User: "later"
```

### Skill Response
```
✅ Noted. Continuing for now.

⚠️ WARNING: This WILL block at Phase 2 (scala-ninja code review)

From the style guide:
"This is a peer review rejection point - all new code must
follow this pattern unless specifically justified."

scala-ninja will require this fix before proceeding to Phase 3.
```

### Later: scala-ninja Review (Hard Block)
```
scala-ninja review (Phase 2):
  "❌ Visibility pattern violations still present

  GuardianActor.scala: 8 private methods violate testability pattern

  This is a peer review rejection point per scala-conventions.md (line 116)

  BLOCKED: Cannot proceed to Phase 3 until violations resolved"
```

### Expected Outcome
- ✅ Deferral allowed temporarily
- ✅ Clear warning about future block
- ✅ Hard block enforced at Phase 2
- ✅ User informed of consequences

---

## Scenario 5: User Rejects Refactor ("no")

**Context**: User explicitly rejects the refactoring

### User Response
```
User: "no"
```

### Skill Response
```
⚠️ Visibility pattern violation remains.

This WILL block at Phase 2 (scala-ninja code review).

From the style guide (line 116):
"This is a peer review rejection point - all new code must
follow this pattern unless specifically justified."

Do you have a justification for the exception?
Valid exceptions:
1. Internal state that genuinely must be hidden
2. Temporary/intermediate methods during refactoring
3. Security-sensitive operations

If yes, I'll document the exception for peer review.
If no, this will block during code review.
```

### User Provides Justification
```
User: "Yes, these methods manage internal actor state that must not be exposed"
```

### Skill Documents Exception
```
✅ Exception documented for peer review

Justification: Internal state management (security-sensitive)
File: GuardianActor.scala
Methods: receiveBehavior, handleInitialize (8 total)
Reference: scala-conventions.md line 156

This will be included in code review documentation.

NOTE: Reviewer may still request changes if justification is insufficient.
```

### Expected Outcome
- ✅ User can provide justification
- ✅ Exception documented for review
- ✅ Reviewer retains final authority
- ✅ Principle: justify exceptions, don't ignore pattern

---

## Scenario 6: Inline Lambda with Business Logic (Law Extraction)

**Context**: Business logic buried in lambda, untestable

### Code Written
```scala
// File: test-probe-core/src/main/scala/.../QueueActor.scala
class QueueActor {
  def processQueue(): Unit = {
    val eligibleTests = allTests.filter(t =>
      t.status == "pending" &&
      t.priority > 3 &&
      !t.isExpired &&
      t.createdAt.isBefore(cutoffTime)
    )
  }
}
```

### Skill Detection
```
💡 Testability Pattern: Extract Business Logic to Named Functions

Detected:
  allTests.filter(t => t.status == "pending" && t.priority > 3 && !t.isExpired && t.createdAt.isBefore(cutoffTime))

Issue:
  Business logic (eligibility rules) is inline and untestable

📖 Principle (from discussions + testing-standards.md):
"Extract business logic into named functions ('laws').
One can be tested, one cannot without testing List operations."

Suggested refactor:
  val isEligibleForExecution: TestEntry => Boolean =
    t => t.status == "pending" &&
         t.priority > 3 &&
         !t.isExpired &&
         t.createdAt.isBefore(cutoffTime)

  val eligibleTests = allTests.filter(isEligibleForExecution)

Benefits:
  ✅ isEligibleForExecution is a 'law' (pure function)
  ✅ Can be unit tested independently:
     isEligibleForExecution(TestEntry(status="pending", priority=5, ...)) shouldBe true
     isEligibleForExecution(TestEntry(status="complete", ...)) shouldBe false
  ✅ Business logic is discoverable and named
  ✅ Reusable across codebase

Shall I refactor this? (yes/no)
```

### User Approves
```
User: "yes"
```

### Skill Refactors
```scala
// REFACTORED CODE
class QueueActor {
  // Law: Pure function expressing business rule
  private[core] val isEligibleForExecution: TestEntry => Boolean =
    t => t.status == "pending" &&
         t.priority > 3 &&
         !t.isExpired &&
         t.createdAt.isBefore(cutoffTime)

  def processQueue(): Unit = {
    val eligibleTests = allTests.filter(isEligibleForExecution)
  }
}
```

### Unit Test Created
```scala
// New unit test for the law
"isEligibleForExecution law" should {
  "return true for eligible test" in {
    val test = TestEntry(status = "pending", priority = 5, isExpired = false, createdAt = now.minusMinutes(1))
    queueActor.isEligibleForExecution(test) shouldBe true
  }

  "return false for non-pending test" in {
    val test = TestEntry(status = "complete", priority = 5, isExpired = false, createdAt = now.minusMinutes(1))
    queueActor.isEligibleForExecution(test) shouldBe false
  }

  "return false for low priority test" in {
    val test = TestEntry(status = "pending", priority = 2, isExpired = false, createdAt = now.minusMinutes(1))
    queueActor.isEligibleForExecution(test) shouldBe false
  }
}
```

### Expected Outcome
- ✅ Business logic extracted to named function (law)
- ✅ Law is independently testable
- ✅ Unit tests created for law
- ✅ Code is more discoverable and reusable

---

## Scenario 7: Coverage Improvement Demonstration

**Context**: Before/after coverage comparison

### Before Visibility Pattern
```bash
# GuardianActor with 8 private methods
mvn scoverage:report -pl test-probe-core -q

# Coverage report
Statement coverage: 45%
Branch coverage: 38%

# Analysis
Total statements: 180
Covered statements: 81
Untestable (private methods): 99 (~55%)
```

### After Visibility Pattern
```bash
# GuardianActor with private[core] object, public methods
mvn scoverage:report -pl test-probe-core -q

# Coverage report
Statement coverage: 87%
Branch coverage: 82%

# Analysis
Total statements: 180
Covered statements: 157
Testable (all methods): 180 (100%)
```

### Skill Celebration
```
🏆 COVERAGE SUCCESS! Visibility pattern delivered results! 🎉

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
BEFORE/AFTER COMPARISON
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

BEFORE (private methods):
  ❌ Statement coverage: 45%
  ❌ Branch coverage: 38%
  ❌ Untestable code: 99 statements (55%)

AFTER (visibility pattern):
  ✅ Statement coverage: 87% (+42%)
  ✅ Branch coverage: 82% (+44%)
  ✅ All code testable: 180 statements (100%)

Impact:
  +42% statement coverage
  +76 statements with regression protection
  8 methods now directly testable

This proves the pattern works! 🎯

Your GuardianActor now has comprehensive test coverage.
```

### Expected Outcome
- ✅ Measurable coverage improvement
- ✅ Before/after comparison shown
- ✅ Pattern effectiveness demonstrated
- ✅ Celebration reinforces positive behavior

---

## Scenario 8: Multiple Modules with Correct Scoping

**Context**: Auto-detecting appropriate module scope

### Files Analyzed
```
test-probe-core/src/main/scala/com/company/probe/core/actors/GuardianActor.scala
test-probe-common/src/main/scala/com/company/probe/common/utils/ConfigValidator.scala
test-probe-services/src/main/scala/com/company/probe/services/S3Service.scala
```

### Skill Analysis
```
✅ Auto-detecting module scope for refactoring...

File: GuardianActor.scala
  Module: test-probe-core
  Suggested scope: private[core]

File: ConfigValidator.scala
  Module: test-probe-common
  Suggested scope: private[common]

File: S3Service.scala
  Module: test-probe-services
  Suggested scope: private[services]

📖 Scope selection reference:
   .claude/styles/scala-conventions.md (lines 150-154)

From the guide:
- private[core]       → core module actors/services
- private[common]     → common module utilities
- private[services]   → services module implementations

All scopes correctly identified. Applying refactoring...
```

### Expected Outcome
- ✅ Correct scope auto-detected per module
- ✅ References authoritative guide
- ✅ Consistent pattern across codebase
- ✅ Module boundaries properly enforced

---

## Edge Cases

### Edge Case 1: User Writes Private, Doesn't Ask for Review

```
User writes code with private methods in terminal/IDE.
User does not request code review from scala-ninja.

Skill: Does NOT activate
      Only warns if Claude is writing the code
      Hard block only during scala-ninja review (Phase 2)
```

**Why**: Skill guides Claude's code, not user's code. User has full autonomy.

---

### Edge Case 2: Justified Exception (Security-Sensitive)

```
private def decryptCredentials(encrypted: String): String = {
  // Security-sensitive operation - genuinely needs hiding
}

User justification: "Security-sensitive credential decryption"

Skill: ✅ Accepts exception
       Documents for peer review
       Reviewer may still challenge if insufficient
```

**Why**: Pattern has legitimate exceptions per style guide (line 156).

---

### Edge Case 3: Temporary Private During Refactoring

```
User: "I'm mid-refactor, methods are temporarily private while I extract them"

Skill: ✅ Accepts "temporary/intermediate methods during refactoring"
       Marks as exception with timeline
       Expects fix before final review
```

**Why**: Valid exception per style guide. Refactoring is a process.

---

### Edge Case 4: Complex Lambda (Multiple Rules)

```scala
// BEFORE: Multiple business rules in one lambda
results.filter(r =>
  r.status == "complete" &&
  r.duration < maxDuration &&
  r.errorCount == 0 &&
  r.timestamp.isAfter(startTime) &&
  !r.tags.contains("ignore")
)

// AFTER: Extract to multiple composable laws
val isComplete: Result => Boolean = r => r.status == "complete"
val isWithinDuration: Result => Boolean = r => r.duration < maxDuration
val hasNoErrors: Result => Boolean = r => r.errorCount == 0
val isAfterStart: Result => Boolean = r => r.timestamp.isAfter(startTime)
val isNotIgnored: Result => Boolean = r => !r.tags.contains("ignore")

val isSuccessfulResult: Result => Boolean = r =>
  isComplete(r) && isWithinDuration(r) && hasNoErrors(r) && isAfterStart(r) && isNotIgnored(r)

results.filter(isSuccessfulResult)
```

**Skill Guidance**:
```
💡 Multiple business rules detected in lambda

Each rule can be a separate law (testable independently):
  ✅ isComplete → test completion logic
  ✅ isWithinDuration → test duration logic
  ✅ hasNoErrors → test error logic
  ✅ isAfterStart → test timestamp logic
  ✅ isNotIgnored → test tag logic

Then compose into final law:
  ✅ isSuccessfulResult → test complete eligibility

Benefits:
  - 6 independently testable laws
  - Each law has clear single responsibility
  - Discoverable business rules
  - Reusable across codebase
```

---

## Test Validation Checklist

**Use these scenarios in Phase 4 to verify**:

- [ ] Light warning triggers on `private def` detection (Scenario 1)
- [ ] Hard block during scala-ninja review (Scenario 2)
- [ ] Auto-refactoring works correctly (Scenario 3)
- [ ] Deferral handling: warn → block at review (Scenario 4)
- [ ] Exception documentation for justified cases (Scenario 5)
- [ ] Lambda extraction to laws (Scenario 6)
- [ ] Coverage improvement measured (Scenario 7)
- [ ] Module scope auto-detection (Scenario 8)
- [ ] Edge cases handled gracefully (User autonomy, exceptions, refactoring)

**Success Criteria**: All scenarios behave as documented

---

**Version**: 1.0
**Created**: 2025-10-21
**Purpose**: Documentation + Phase 4 test cases