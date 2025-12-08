# Implementation Workflow - Phases 0-7

**Purpose**: Systematic approach to implementing features that ensures architecture is documented BEFORE and AFTER implementation, preventing context loss and misalignment.

**Critical Rule**: NEVER skip Phase 0. Architecture documentation is the foundation that prevents wasted effort.

---

## Overview

Every feature implementation follows these 8 phases:

```
Phase 0: Document Architecture & Plan (BEFORE coding)
         ↓
Phase 1: Code Changes (Implementation)
         ↓
Phase 2: Scala Ninja Code Review (Quality gate)
         ↓
Phase 3: Specifications Creation (BDD scenarios)
         ↓
Phase 4: Component Testing (Integration verification)
         ↓
Phase 5: Unit Testing with Scala Testing Ninja (Coverage)
         ↓
Phase 6: Document As-Is Architecture (AFTER coding)
         ↓
Phase 7: Architecture Doc Agent Finalization (Validation)
```

---

## Phase 0: Document Architecture & Implementation Plan

**Goal**: Establish clear architectural vision and implementation scope BEFORE writing any code.

**Entry Criteria**:
- User has requested a feature or change
- High-level requirements are understood

**Activities**:
1. **Create or update architecture document** in `working/`
   - Document current state (if changing existing architecture)
   - Document target state (what we're building)
   - Identify all affected modules
   - Define interfaces and contracts
   - Draw diagrams if needed (sequence, component, state machine)

2. **Create implementation plan** in `working/`
   - Break down into phases
   - Identify dependencies
   - Define success criteria for each phase
   - Estimate effort

3. **Get user approval** on architecture and plan
   - Present the architecture vision
   - Confirm understanding
   - Adjust based on feedback

**Deliverables**:
- `working/{feature}-architecture.md` - Complete architecture documentation
- `working/{feature}-implementation-plan.md` - Phased implementation plan

**Exit Criteria**:
- ✅ Architecture document complete and approved by user
- ✅ Implementation plan documented with clear phases
- ✅ User confirms: "Yes, this is the architecture I want"

**Why This Matters**:
- Prevents implementing the wrong thing
- Provides context that survives session boundaries
- Enables Claude to resume work correctly after context loss
- User and AI have shared understanding

**Example**:
```markdown
# Multi-Interface Architecture

## Current State
- REST endpoints directly call actor system
- No separation between protocol and business logic

## Target State
- ServiceInterfaceFunctions trait in test-probe-common
- Factory creates implementation with QueueActor reference
- REST/CLI/gRPC consume same business logic

## Flow
1. DefaultActorSystem.initialize() gets QueueActor ref
2. Creates ServiceInterfaceFunctions via factory
3. Stores in BuilderContext.curriedFunctions
4. Interface modules consume functions from ServiceContext
```

---

## Phase 1: Code Changes

**Goal**: Implement the code according to the architecture document.

**Entry Criteria**:
- ✅ Phase 0 complete
- ✅ Architecture document approved

**Activities**:
1. Implement code following the architecture document
2. Use TodoWrite to track implementation tasks
3. Refer back to architecture doc when making decisions
4. Update implementation plan as phases complete

**Deliverables**:
- Implementation code in appropriate modules
- Code compiles successfully

**Exit Criteria**:
- ✅ All planned code changes complete
- ✅ Code compiles: `mvn compile -pl {modules} -q`
- ✅ No compilation errors

**Anti-Patterns to Avoid**:
- ❌ Coding without referring to Phase 0 architecture
- ❌ Making architectural decisions that contradict Phase 0 doc
- ❌ Skipping architecture doc updates when design changes

**When Design Changes During Implementation**:
- **STOP coding**
- **UPDATE Phase 0 architecture document**
- **Get user approval** on the change
- **THEN continue coding**

---

## Phase 2: Scala Ninja Code Review

**Goal**: Expert-level code review for patterns, idioms, and quality.

**Entry Criteria**:
- ✅ Phase 1 complete
- ✅ Code compiles successfully

**Activities**:
1. **Launch scala-ninja agent**:
   ```
   Use Task tool with subagent_type=scala-ninja
   ```

2. **Request comprehensive review**:
   - Functional programming patterns
   - Type safety and phantom types
   - Scala 3 idioms
   - Immutability and composition
   - Error handling patterns

3. **Address all feedback**:
   - Fix issues identified
   - Refactor as recommended
   - Re-compile after changes

**Deliverables**:
- Code reviewed by scala-ninja
- All feedback addressed
- Code compiles after fixes

**Exit Criteria**:
- ✅ scala-ninja review complete
- ✅ All critical issues addressed
- ✅ Code follows Scala best practices
- ✅ Code compiles: `mvn compile -pl {modules} -q`

---

## Phase 3: Specifications Creation

**Goal**: Create BDD specifications for the new functionality.

**Entry Criteria**:
- ✅ Phase 2 complete
- ✅ Code reviewed and refined

**Activities**:
1. **Identify test scenarios**:
   - Happy path scenarios
   - Error scenarios
   - Edge cases
   - State transitions (for actors/FSMs)

2. **Create or update feature files**:
   - Location: `test-probe-core/src/test/resources/features/component/`
   - Use Gherkin syntax (Given/When/Then)
   - Follow actor-specific step pattern naming
   - Reference: `.claude/styles/bdd-testing-standards.md`

3. **Document test plan**:
   - What scenarios are covered
   - What edge cases are tested
   - Coverage goals for this feature

**Deliverables**:
- Feature files with Gherkin scenarios
- Test plan documented

**Exit Criteria**:
- ✅ Feature files created
- ✅ Scenarios cover happy path and error cases
- ✅ Step patterns follow actor-specific naming

---

## Phase 4: Component Testing

**Goal**: Verify integration behavior through BDD component tests.

**Entry Criteria**:
- ✅ Phase 3 complete
- ✅ Feature files created

**Activities**:
1. **Implement step definitions** (if needed):
   - Location: `test-probe-core/src/test/scala/com/company/probe/core/bdd/steps/`
   - Follow actor-specific patterns
   - Use fixtures for test actors

2. **Run component tests**:
   ```bash
   mvn test -Pcomponent-only -pl test-probe-core
   ```

3. **Fix failures**:
   - Address undefined steps
   - Fix implementation issues
   - Ensure all scenarios pass

**Deliverables**:
- All component tests passing
- Step definitions for new scenarios

**Exit Criteria**:
- ✅ Component tests run: `mvn test -Pcomponent-only -pl test-probe-core`
- ✅ All scenarios pass (0 failures, 0 errors, 0 undefined steps)
- ✅ Output shows `BUILD SUCCESS`

---

## Phase 5: Unit Testing with Scala Testing Ninja

**Goal**: Achieve comprehensive unit test coverage using testing best practices.

**Entry Criteria**:
- ✅ Phase 4 complete
- ✅ Component tests passing

**Activities**:
1. **Launch scala-testing-ninja agent**:
   ```
   Use Task tool with subagent_type=scala-testing-ninja
   ```

2. **Request comprehensive test coverage**:
   - Unit tests for all public methods
   - Edge case coverage
   - Error handling tests
   - State machine tests (for actors)
   - Fixture-based tests to minimize code

3. **Verify coverage targets**:
   - Minimum: 70% overall
   - Actors/FSMs: 85%+
   - Business logic: 80%+

**Deliverables**:
- Unit test suite created
- Coverage targets met
- All tests passing

**Exit Criteria**:
- ✅ Unit tests run: `mvn test -Punit-only -pl test-probe-core`
- ✅ All tests pass (0 failures, 0 errors)
- ✅ Coverage targets met (verify with scoverage)
- ✅ Output shows `BUILD SUCCESS`

---

## Phase 6: Document As-Is Architecture

**Goal**: Update architecture documentation to reflect actual implementation, including any changes made during development.

**Entry Criteria**:
- ✅ Phase 5 complete
- ✅ All tests passing

**Activities**:
1. **Review architecture document from Phase 0**
2. **Update with as-built reality**:
   - Document any deviations from original plan
   - Update diagrams to match actual implementation
   - Add details discovered during implementation
   - Document design decisions made during coding

3. **Create "What Changed" section**:
   - What was planned vs. what was built
   - Why changes were made
   - Lessons learned

4. **Update related documentation**:
   - `CLAUDE.md` if architecture patterns changed
   - ADRs if architectural decisions were made
   - Implementation plan to mark phases complete

**Deliverables**:
- Updated `working/{feature}-architecture.md`
- Updated implementation plan
- Related docs updated

**Exit Criteria**:
- ✅ Architecture doc reflects actual implementation
- ✅ All deviations from Phase 0 documented
- ✅ Design decisions documented
- ✅ User reviews and approves as-built documentation

**Critical**: This phase ensures the next Claude Code session has accurate architecture context.

---

## Phase 7: Architecture Doc Agent Finalization

**Goal**: Validate architecture documentation accuracy and completeness.

**Entry Criteria**:
- ✅ Phase 6 complete
- ✅ As-built architecture documented

**Activities**:
1. **Launch architecture-doc-keeper agent**:
   ```
   Use Task tool with subagent_type=architecture-doc-keeper
   ```

2. **Request documentation validation**:
   - Verify architecture docs match code
   - Check for inconsistencies
   - Validate diagrams are current
   - Ensure all components documented

3. **Launch documentation-product-engineer agent**:
   ```
   Use Task tool with subagent_type=documentation-product-engineer
   ```

4. **Request quality review**:
   - Clarity for new engineers
   - Completeness of examples
   - Accuracy of technical details

5. **Address all feedback**:
   - Fix inconsistencies
   - Add missing details
   - Improve clarity

**Deliverables**:
- Architecture documentation validated by agents
- All feedback addressed
- Documentation complete and accurate

**Exit Criteria**:
- ✅ architecture-doc-keeper validation passed
- ✅ documentation-product-engineer review passed
- ✅ All feedback addressed
- ✅ Documentation ready for next session

---

## Quick Reference: Phase Checklist

Use this checklist for every feature:

- [ ] **Phase 0**: Architecture doc created and approved by user
- [ ] **Phase 1**: Code implemented and compiles
- [ ] **Phase 2**: scala-ninja review complete, feedback addressed
- [ ] **Phase 3**: Feature files created with scenarios
- [ ] **Phase 4**: Component tests passing (0 errors, 0 undefined)
- [ ] **Phase 5**: Unit tests passing, coverage targets met
- [ ] **Phase 6**: As-built architecture documented
- [ ] **Phase 7**: Documentation validated by agents

---

## Common Mistakes to Avoid

### ❌ Skipping Phase 0
**Problem**: Start coding without architecture doc
**Result**: Wasted effort, wrong implementation, context loss
**Solution**: ALWAYS create architecture doc first

### ❌ Not Updating Phase 0 During Implementation
**Problem**: Design changes during coding, Phase 0 becomes stale
**Result**: Next session has wrong architecture context
**Solution**: Update Phase 0 doc when design changes, get user approval

### ❌ Skipping Phase 6
**Problem**: Architecture doc from Phase 0 doesn't match as-built
**Result**: Next Claude session implements based on outdated plan
**Solution**: ALWAYS update as-built architecture after coding

### ❌ Not Using Specialized Agents
**Problem**: Skip scala-ninja, testing-ninja, or doc agents
**Result**: Lower code quality, missing tests, stale docs
**Solution**: Use agents as specified in each phase

### ❌ Proceeding with Failures
**Problem**: Move to next phase with failing tests
**Result**: Compound errors, harder debugging
**Solution**: Fix all issues before advancing phases

---

## Context Loss Recovery

**If you resume a session and find incomplete architecture**:

1. **STOP immediately** - Do not implement code
2. **Read all `working/*.md` files** to understand what was planned
3. **Ask user for architecture vision** - "What was the intended design?"
4. **Update Phase 0 doc** with user's explanation
5. **Get approval** before proceeding
6. **Consider stashing** if current code doesn't match vision

**Prevention**: Complete Phase 6 and 7 before ending sessions!

---

## Example: Multi-Interface Implementation

**Phase 0**: Created `working/multi-interface-architecture.md` documenting:
- ServiceInterfaceFunctions trait in common module
- Factory pattern with QueueActor reference
- BuilderContext integration
- Interface consumption pattern

**Phase 1**: Implemented code following architecture

**Phase 2**: scala-ninja reviews factory pattern, suggests improvements

**Phase 3**: Created feature files for REST interface scenarios

**Phase 4**: Component tests verify REST → functions → actors flow

**Phase 5**: scala-testing-ninja creates comprehensive unit tests

**Phase 6**: Updated architecture doc with as-built details:
- Added note about QueueActor reference obtained in DefaultActorSystem
- Documented that factory is called during initialization
- Added sequence diagram of actual flow

**Phase 7**: architecture-doc-keeper validates, documentation-product-engineer approves clarity

**Result**: Next session has complete, accurate architecture context ✅

---

## Integration with Existing Guides

This workflow complements existing guides:

- **`.claude/guides/TESTING.md`**: Use in Phases 3-5 for testing standards
- **`.claude/guides/ACTORS.md`**: Use in Phases 0-1 for actor patterns
- **`.claude/guides/BUILD.md`**: Use in Phases 1-5 for build commands
- **`.claude/guides/ARCHITECTURE.md`**: Use in Phase 0 for architecture patterns
- **`.claude/styles/*.md`**: Use in all phases for code standards

---

## User Preferences

- Ask questions and prefer paired programming or don't assume in all phases


---

## Last Updated

2025-10-18 - Created to prevent context loss and ensure architecture documentation before/after implementation