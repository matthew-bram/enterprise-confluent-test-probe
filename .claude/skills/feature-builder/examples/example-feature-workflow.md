# Example Feature Workflow: Add Test Cancellation

This example demonstrates the feature-builder skill workflow for implementing a "Test Cancellation" feature that allows users to cancel running tests.

---

## Skill Activation

**User**: "I want to add a feature that lets users cancel tests that are currently running"

**Claude** (skill auto-activates):
```
I've detected you want to implement a new feature. I'm activating the feature-builder
skill to guide us through the 8-phase workflow.

Let's start with Phase 0: Architecture Documentation.

I'll create the architecture document and we'll design this together before writing
any code. This ensures we have a clear plan and prevents context loss if our session
ends.

Creating:
- working/test-cancellation-architecture.md
- working/test-cancellation-progress.md
```

---

## Phase 0: Architecture Documentation

**Claude**:
```
Let me ask some clarifying questions to help design the architecture:

1. Should cancellation be immediate (kill the test actor) or graceful (let current
   scenario finish)?
2. What happens to partial test results when a test is cancelled?
3. Should users be able to cancel tests in any state, or only Running tests?
4. How should cancelled tests appear in queue status?

I'm thinking we'll need:
- New CancelTest command to QueueActor
- TestExecutionActor needs to handle cancellation signal
- CancelTestResponse to indicate success/failure
- Update to QueueState to track cancelled tests

What do you think?
```

**User**: "Graceful cancellation - finish current scenario. Save partial results. Only running tests can be cancelled. Mark them as 'cancelled' state."

**Claude**:
```
Great! That clarifies the design. Let me create the architecture document.

[Creates architecture document with sections filled in based on discussion]

Here's the proposed architecture:

**Target State**:
- CancelTest(testId) command sent to QueueActor
- QueueActor validates test is in Running state
- Sends CancelExecution to TestExecutionActor
- TestExecutionActor finishes current scenario gracefully, then stops
- Partial results saved to storage
- Test marked as "Cancelled" in queue

**Message Flow**:
User → REST → QueueActor → CancelTest
QueueActor → TestExecutionActor → CancelExecution
TestExecutionActor → QueueActor → TestCancelled
QueueActor → User → CancelTestResponse

**Open Questions**:
❓ Should cancelled tests stay in queue or be removed?
   - Option A: Keep in queue with "Cancelled" state
   - Option B: Remove from queue entirely
   - Recommendation: Keep in queue so status API shows them

Does this architecture match your vision?
```

**User**: "Yes, keep them in queue. Looks good!"

**Claude**:
```
Perfect! I've updated the architecture document to reflect that decision.

✅ Architecture document complete
✅ All open questions resolved
✅ Design decisions documented

📋 Summary:
- New CancelTest command and CancelTestResponse
- Graceful cancellation (finish current scenario)
- Partial results saved
- Tests remain in queue with "Cancelled" state
- Only Running tests can be cancelled

**Approval Request**: Please review working/test-cancellation-architecture.md and
confirm: "approve architecture" when ready to proceed to Phase 1.
```

**User**: "approve architecture"

**Claude**:
```
✅ Phase 0 Complete - Architecture Approved

Updating progress tracker and proceeding to Phase 1: Code Implementation.
```

---

## Phase 1: Code Implementation

**Claude**:
```
Starting Phase 1: Code Changes

Based on the approved architecture, I'll implement:
1. Add CancelTest and CancelTestResponse to ActorCommands.scala
2. Add cancelledTests set to QueueState
3. Add CancelTest handler to QueueActor
4. Add CancelExecution message to TestExecutionActor
5. Update REST interface to expose cancellation endpoint

Let me implement these changes...

[Implements code changes]

✅ All planned changes implemented
✅ Code compiles: mvn compile -pl test-probe-core -q

Phase 1 exit criteria met. Proceeding to Phase 2: Code Review.
```

---

## Phase 2: Scala Ninja Review

**Claude**:
```
Starting Phase 2: Scala Ninja Code Review

Launching scala-ninja agent to review the implementation...

[Auto-launches scala-ninja agent]
```

**scala-ninja agent returns**:
```
Review findings:
- ✅ Message protocols follow typed actor patterns
- ✅ State management is immutable
- ⚠️  CancelTest handler should validate test exists before checking state
- ⚠️  Consider using Either[ErrorResponse, CancelTestResponse] for better error handling
- 💡 Suggestion: Add logging for cancellation events
```

**Claude**:
```
scala-ninja review complete. Found 2 warnings and 1 suggestion:

1. **Validation order**: We should check if test exists before checking if it's running.
   This prevents confusing error messages. Do you agree we should fix this?

2. **Error handling**: Using Either would make error cases more explicit and type-safe.
   Worth the extra complexity for this feature?

3. **Logging**: Adding logs for cancellations would help debugging. Should we add
   structured logging?

What do you think? Should we apply all of these, or only some?
```

**User**: "Fix #1 and #3. Skip #2 for now - keep it simple."

**Claude**:
```
Perfect! Applying fixes for validation order and adding logging.

[Applies fixes]

✅ Validation order corrected
✅ Logging added for cancellation events
✅ Code re-compiles successfully

Phase 2 exit criteria met. Proceeding to Phase 3: Specifications.
```

---

## Phase 3: Specifications Creation

**Claude**:
```
Starting Phase 3: Specifications Creation

Based on the architecture, I'm proposing these BDD scenarios:

**Happy Path**:
1. Cancel a running test - test stops gracefully
2. Cancelled test returns partial results
3. Queue status shows cancelled test

**Error Scenarios**:
4. Cancel a test that doesn't exist - returns error
5. Cancel a test that's already completed - returns error
6. Cancel a test that's in Setup state - returns error

**Edge Cases**:
7. Cancel test during scenario execution - current scenario completes
8. Multiple cancel requests for same test - idempotent behavior

Am I missing any important scenarios?
```

**User**: "What about cancelling a test that's already been cancelled?"

**Claude**:
```
Excellent catch! That's an important idempotency scenario. Adding:

9. Cancel already-cancelled test - returns success (idempotent)

Creating feature file: test-probe-core/src/test/resources/features/component/queue-actor.feature

[Creates feature file with all scenarios]

✅ Feature file created with 9 scenarios
✅ Scenarios cover happy path, errors, and edge cases
✅ Step patterns follow QueueActor-specific naming

Phase 3 exit criteria met. Proceeding to Phase 4: Component Testing.
```

---

## Phase 4: Component Testing

**Claude**:
```
Starting Phase 4: Component Testing

Running component tests: mvn test -Pcomponent-only -pl test-probe-core

...

❌ Tests failed: 2 undefined steps

Results:
- 7 scenarios passing
- 2 scenarios with undefined steps:
  - "the QueueActor should mark the test as cancelled"
  - "the partial results should be saved"

I need to implement these step definitions. Let me add them...

[Implements missing step definitions]

Re-running tests...

✅ All 9 scenarios passing
✅ 0 failures, 0 errors, 0 undefined steps
✅ BUILD SUCCESS

Phase 4 exit criteria met. Proceeding to Phase 5: Unit Testing.
```

---

## Phase 5: Unit Testing

**Claude**:
```
Starting Phase 5: Unit Testing with Scala Testing Ninja

Launching scala-testing-ninja agent to create comprehensive unit tests...

[Auto-launches scala-testing-ninja agent]
```

**scala-testing-ninja returns**:
```
Unit test suite created:
- QueueActorCancellationSpec: 12 tests covering all cancellation scenarios
- TestExecutionActorCancellationSpec: 8 tests for graceful shutdown
- Total: 20 new tests

Coverage estimate: 88% for new cancellation code
```

**Claude**:
```
scala-testing-ninja created 20 unit tests.

Running: mvn test -Punit-only -pl test-probe-core

Results:
- Tests run: 20, Failures: 0, Errors: 0
- Coverage: 88% (exceeds 70% target)
- BUILD SUCCESS

✅ All unit tests passing
✅ Coverage target exceeded (88% > 70%)

Phase 5 exit criteria met. Proceeding to Phase 6: As-Built Documentation.
```

---

## Phase 6: As-Built Architecture Documentation

**Claude**:
```
Starting Phase 6: Document As-Is Architecture

Reviewing original Phase 0 architecture vs. actual implementation...

**Changes During Implementation**:

1. **Validation Order Change** (from scala-ninja feedback):
   - Original: Check state first
   - Actual: Check existence first, then state
   - Reason: Better error messages and user experience

2. **Logging Added**:
   - Not in original architecture
   - Added structured logging for cancellation events
   - Reason: Debugging and audit trail

3. **Idempotency**:
   - Original architecture didn't specify
   - Implemented: Cancelling already-cancelled test returns success
   - Reason: User suggestion during Phase 3 - better API design

Let me update the architecture document to reflect these changes...

[Updates architecture document with as-built reality]

✅ Architecture document updated with actual implementation
✅ All deviations documented with rationale
✅ Lessons learned captured

**Approval Request**: Please review the updated
working/test-cancellation-architecture.md (As-Built section) and confirm:
"approve as-built docs" when ready to proceed to Phase 7.
```

**User**: "approve as-built docs"

**Claude**:
```
✅ Phase 6 Complete - As-Built Documentation Approved

Proceeding to Phase 7: Final Documentation Validation.
```

---

## Phase 7: Architecture Doc Validation

**Claude**:
```
Starting Phase 7: Architecture Doc Agent Finalization

Launching validation agents...

[Auto-launches architecture-doc-keeper and documentation-product-engineer agents]
```

**architecture-doc-keeper returns**:
```
Validation complete:
- ✅ Architecture doc matches actual code
- ✅ All message types documented match ActorCommands.scala
- ✅ State machine updates correctly described
- ⚠️  Sequence diagram could show error paths more clearly
```

**documentation-product-engineer returns**:
```
Quality review complete:
- ✅ Clear and well-structured
- ✅ Good use of diagrams
- 💡 Suggestion: Add example REST API curl command for user reference
- 💡 Suggestion: Link to related ADR if one exists for cancellation pattern
```

**Claude**:
```
Agent reviews complete. Findings:

**architecture-doc-keeper**: 1 improvement suggestion
- Add error paths to sequence diagram

**documentation-product-engineer**: 2 suggestions
- Add example curl command
- Link to related ADR

These are minor enhancements. Should we apply them before considering the feature
complete?
```

**User**: "Yes, apply all of them."

**Claude**:
```
Applying documentation improvements...

✅ Sequence diagram updated with error paths
✅ Example curl command added
✅ Related ADR linked (ADR-CANCELLATION-001-graceful-shutdown)

All agent feedback addressed.

✅ Phase 7 Complete - Documentation Validated

🎉 Feature Complete! All 8 phases finished.
```

---

## Completion Summary

**Claude**:
```
🎉 Test Cancellation Feature - COMPLETE

**Summary**:
- ✅ Architecture documented (before and after implementation)
- ✅ Code implemented and reviewed by scala-ninja
- ✅ 9 BDD scenarios created and passing
- ✅ 20 unit tests created and passing
- ✅ 88% test coverage achieved (exceeds 70% target)
- ✅ Documentation validated by specialized agents
- ✅ All quality gates passed

**Deliverables**:
- Architecture: working/test-cancellation-architecture.md
- Progress: working/test-cancellation-progress.md
- Code: 5 files modified, 2 files created
- Tests: 9 component scenarios, 20 unit tests
- Coverage: 88%

**Total Duration**: 3 hours across Phases 0-7

**Files Modified**:
- ActorCommands.scala: Added CancelTest, CancelTestResponse
- QueueActor.scala: Added cancellation handler
- TestExecutionActor.scala: Added graceful shutdown
- ProbeRoutes.scala: Added DELETE /tests/{id}/cancel endpoint
- QueueActorSteps.scala: Added cancellation step definitions

**Files Created**:
- QueueActorCancellationSpec.scala: 12 unit tests
- TestExecutionActorCancellationSpec.scala: 8 unit tests

Next steps:
- Ready to commit this work
- Consider adding cancellation metrics to monitoring
- Update user documentation with cancellation API

Would you like to commit these changes, or is there anything else to refine?
```

---

## Key Observations

This example demonstrates:

1. **Phase 0 Enforcement**: Claude stopped to create architecture before coding
2. **Paired Programming**: User and Claude collaborated on design decisions
3. **Auto-Agent Launch**: scala-ninja and testing-ninja launched automatically
4. **Warning System**: Undefined steps caught, fixed before proceeding
5. **Approval Gates**: Explicit approval at Phase 0 and Phase 6
6. **State Persistence**: Progress tracked throughout for session recovery
7. **Quality Gates**: All tests passing, coverage targets met
8. **Documentation Validation**: Agents ensure docs match reality

The workflow ensured:
- No coding without architecture
- Quality maintained through reviews
- Context preserved across potential session boundaries
- Collaborative decision-making throughout
- Complete documentation before and after implementation