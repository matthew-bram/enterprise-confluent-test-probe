# Feature Progress Tracker: {Feature Name}

**Feature ID**: {feature-identifier}
**Started**: {start-date}
**Current Phase**: Phase {N} - {Phase Name}
**Last Updated**: {timestamp}
**Status**: 🟢 In Progress | 🟡 Blocked | 🔴 On Hold | ✅ Complete

---

## Quick Status

**Overall Progress**: {X}/8 phases complete ({percentage}%)

```
✅ Phase 0: Architecture Documented
✅ Phase 1: Code Implemented
⏳ Phase 2: Code Review (IN PROGRESS)
⬜ Phase 3: Specifications
⬜ Phase 4: Component Tests
⬜ Phase 5: Unit Tests
⬜ Phase 6: As-Built Docs
⬜ Phase 7: Doc Validation
```

**Next Action**: {specific next step to take}

---

## Feature Overview

**Feature Description**: {1-2 sentence summary}

**Primary Goal**: {what problem this solves}

**Target Modules**:
- {module-1}
- {module-2}

**Estimated Effort**: {small/medium/large} or {X hours/days}

---

## Phase Completion Checklist

### ✅ Phase 0: Document Architecture & Plan (COMPLETE)

**Completed**: {date}
**Duration**: {time spent}

**Deliverables**:
- ✅ Architecture document created: `working/{feature}-architecture.md`
- ✅ All open questions resolved
- ✅ User approved architecture: {date}
- ✅ Implementation phases defined

**Key Decisions**:
1. {Decision 1}: {rationale}
2. {Decision 2}: {rationale}

**Files**:
- Created: `working/{feature}-architecture.md`
- Created: `working/{feature}-progress.md` (this file)

---

### ⏳ Phase 1: Code Changes (IN PROGRESS)

**Started**: {date}
**Current Status**: {brief description of where we are}

**Implementation Tasks**:
- ✅ {Completed task 1}
- ✅ {Completed task 2}
- ⏳ {In-progress task 3}
- ⬜ {Pending task 4}

**Files Modified**:
- Created: {new-file-1.scala}
- Modified: {existing-file-1.scala}
- Modified: {existing-file-2.scala}

**Compilation Status**: ✅ Compiles | ⏳ In progress | ❌ Errors

**Notes**:
- {Any important notes about implementation}
- {Challenges encountered}

---

### ⬜ Phase 2: Scala Ninja Code Review (PENDING)

**Status**: Waiting for Phase 1 completion

**Planned Actions**:
- Launch scala-ninja agent
- Review feedback
- Apply recommended changes
- Re-compile

---

### ⬜ Phase 3: Specifications Creation (PENDING)

**Status**: Waiting for Phase 2 completion

**Planned Scenarios**:
- {Scenario 1}: {description}
- {Scenario 2}: {description}
- {Scenario 3}: {description}

---

### ⬜ Phase 4: Component Testing (PENDING)

**Status**: Waiting for Phase 3 completion

---

### ⬜ Phase 5: Unit Testing (PENDING)

**Status**: Waiting for Phase 4 completion

**Coverage Targets**:
- Overall: 70% minimum
- Actors/FSMs: 85%
- Business logic: 80%

---

### ⬜ Phase 6: Document As-Is Architecture (PENDING)

**Status**: Waiting for Phase 5 completion

---

### ⬜ Phase 7: Architecture Doc Validation (PENDING)

**Status**: Waiting for Phase 6 completion

---

## Key Decisions Log

{Chronological log of all significant decisions made during feature development}

### {Date} - {Decision Title}

**Phase**: Phase {N}
**Decision**: {what was decided}
**Context**: {situation that required the decision}
**Rationale**: {why we chose this approach}
**Alternatives Considered**: {other options discussed}
**Impact**: {how this affects the feature}
**Decided By**: {User/Paired decision}

---

### {Date} - {Decision Title}

**Phase**: Phase {N}
**Decision**: {what was decided}
**Rationale**: {why}
**Trade-offs**: {pros vs cons}

---

## Files Created/Modified

### Created Files
```
working/{feature}-architecture.md       # Architecture documentation
working/{feature}-progress.md           # This file
{module}/src/main/scala/.../NewFile.scala
{module}/src/test/scala/.../NewFileSpec.scala
{module}/src/test/resources/features/new-feature.feature
```

### Modified Files
```
{module}/src/main/scala/.../ExistingFile.scala  # {what changed}
{module}/src/test/scala/.../ExistingSpec.scala  # {what changed}
```

---

## Agent Reviews Completed

### scala-ninja Review
- **Date**: {date}
- **Phase**: Phase 2
- **Status**: ✅ Complete | ⏳ In Progress | ⬜ Not Started
- **Summary**: {key findings}
- **Critical Issues**: {count} - {status: all addressed / X remaining}
- **Non-Critical Suggestions**: {count} - {status}

### scala-testing-ninja Review
- **Date**: {date}
- **Phase**: Phase 5
- **Status**: ✅ Complete | ⏳ In Progress | ⬜ Not Started
- **Tests Created**: {count}
- **Coverage Achieved**: {percentage}%
- **Summary**: {key findings}

### architecture-doc-keeper Review
- **Date**: {date}
- **Phase**: Phase 7
- **Status**: ✅ Complete | ⏳ In Progress | ⬜ Not Started
- **Issues Found**: {count}
- **Status**: {all resolved / X remaining}

### documentation-product-engineer Review
- **Date**: {date}
- **Phase**: Phase 7
- **Status**: ✅ Complete | ⏳ In Progress | ⬜ Not Started
- **Clarity Rating**: {score}
- **Improvements Made**: {count}

---

## Blockers and Issues

{Track anything blocking progress or causing delays}

### 🔴 BLOCKER: {Blocker Title}
- **Identified**: {date}
- **Phase**: Phase {N}
- **Description**: {what's blocking us}
- **Impact**: {how this affects progress}
- **Resolution Plan**: {how we plan to resolve}
- **Status**: 🔴 Blocking | 🟡 In Progress | ✅ Resolved

### 🟡 ISSUE: {Issue Title}
- **Identified**: {date}
- **Phase**: Phase {N}
- **Description**: {what the issue is}
- **Severity**: High | Medium | Low
- **Resolution**: {how it was resolved or plan to resolve}
- **Status**: 🟡 Open | ✅ Resolved | 🔵 Deferred

---

## Test Results Summary

### Unit Tests
- **Total Tests**: {count}
- **Passing**: {count} ({percentage}%)
- **Failing**: {count}
- **Coverage**: {percentage}%
- **Last Run**: {timestamp}
- **Status**: ✅ All Passing | ⏳ In Progress | ❌ Failures

### Component Tests
- **Total Scenarios**: {count}
- **Passing**: {count} ({percentage}%)
- **Failing**: {count}
- **Undefined Steps**: {count}
- **Last Run**: {timestamp}
- **Status**: ✅ All Passing | ⏳ In Progress | ❌ Failures

---

## Timeline

{Chronological log of major milestones}

- **{date}**: Feature work started
- **{date}**: Phase 0 architecture approved
- **{date}**: Phase 1 implementation started
- **{date}**: {milestone}
- **{date}**: {milestone}

---

## Lessons Learned (Updated Continuously)

{Capture insights as they emerge during development}

### What Went Well
- {Positive finding 1}
- {Positive finding 2}

### What Could Be Improved
- {Improvement opportunity 1}
- {Improvement opportunity 2}

### Surprises/Discoveries
- {Unexpected finding 1}: {what we learned}
- {Unexpected finding 2}: {what we learned}

### Would Do Differently Next Time
- {Change 1}: {why}
- {Change 2}: {why}

---

## Deferred Work

{Track work that was explicitly deferred or descoped}

### {Deferred Item 1}
- **Originally Planned For**: Phase {N}
- **Deferred On**: {date}
- **Reason**: {why it was deferred}
- **Plan**: {when/how it will be addressed}
- **Tracking**: {link to issue/ticket if applicable}

---

## Context for Next Session

{Critical information needed if development resumes in a new Claude Code session}

**Current State Summary**:
{1-2 paragraphs describing exactly where we are and what's been done}

**Next Steps**:
1. {Specific next action}
2. {Following action}
3. {Subsequent action}

**Important Context**:
- {Key point 1 that next session needs to know}
- {Key point 2 that next session needs to know}

**Open Questions**:
- ❓ {Question that needs answering}
- ❓ {Decision that needs making}

---

## Notes Section

{Free-form notes, observations, reminders}

### {Date} - {Note Title}
{Note content}

### {Date} - {Note Title}
{Note content}

---

## Completion Summary (Phase 8 - After All Phases Complete)

{This section is filled in when all phases are complete}

**Completion Date**: {date}
**Total Duration**: {X days/hours}
**Final Status**: ✅ Complete

**Deliverables**:
- ✅ Feature implemented and tested
- ✅ Architecture documented (before and after)
- ✅ {X}% test coverage achieved
- ✅ All quality gates passed

**Final Metrics**:
- Files created: {count}
- Files modified: {count}
- Unit tests added: {count}
- Component scenarios added: {count}
- Total lines of code: {approximate count}

**Impact**:
{Description of what this feature enables and its business value}

**Recommended Next Steps**:
- {Follow-up work 1}
- {Follow-up work 2}

---

**Progress Tracker Version**: 1.0
**Last Updated By**: {Claude session ID or user}
**Next Review**: {when this should be reviewed}