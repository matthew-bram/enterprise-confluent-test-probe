# {Feature Name} - Architecture Documentation

**Feature ID**: {feature-identifier}
**Status**: Phase 0 - Architecture Design
**Created**: {date}
**Last Updated**: {date}

---

## Feature Summary

{2-3 sentence description of what this feature does and why it's valuable}

---

## Current State

### What Exists Today

{Describe the current state of the system before this feature is implemented}

**Affected Components**:
- {Component 1}: {current behavior}
- {Component 2}: {current behavior}

**Current Limitations**:
- {Limitation 1}
- {Limitation 2}

**Current Architecture** (if applicable):
```
{Simple diagram or description of current system state}
```

---

## Target State

### What We're Building

{Detailed description of the target state after feature implementation}

**New Capabilities**:
- {Capability 1}: {description}
- {Capability 2}: {description}

**Modified Components**:
- {Component 1}: {new behavior}
- {Component 2}: {new behavior}

**New Components** (if applicable):
- {New Component 1}: {purpose and responsibilities}
- {New Component 2}: {purpose and responsibilities}

**Target Architecture**:
```
{Diagram showing target state - can use Mermaid, ASCII art, or text description}

Example:
User Request
    ↓
REST Interface
    ↓
ServiceFunction
    ↓
QueueActor
    ↓
{Your New Component}
    ↓
External System
```

---

## Affected Modules

List all Maven modules that will be modified:

- **{module-name}**: {why this module is affected}
  - New files: {list}
  - Modified files: {list}
  - Purpose: {what changes and why}

---

## Interface Contracts

### API Contracts

**New REST Endpoints** (if applicable):
```
POST /api/v1/{resource}
  Request: { ... }
  Response: { ... }
  Status Codes: 200, 400, 404, 500
```

### Actor Message Protocols

**New Messages** (if applicable):
```scala
// Commands
case class {CommandName}(param1: Type1, param2: Type2) extends {ActorCommand}

// Responses
case class {ResponseName}(data: Type) extends {ActorResponse}
```

**Message Flow**:
```
Actor A -> Message1 -> Actor B
Actor B -> Message2 -> Actor C
Actor C -> Message3 -> Actor A
```

### Data Models

**New Models** (if applicable):
```scala
case class {ModelName}(
  field1: Type1,
  field2: Type2,
  field3: Option[Type3]
)
```

---

## Data Flow

{Describe how data flows through the system for this feature}

**Happy Path**:
1. {Step 1}: {description}
2. {Step 2}: {description}
3. {Step 3}: {description}

**Error Path**:
1. {Error condition}: {how it's handled}
2. {Error propagation}: {where errors go}

**Sequence Diagram** (if complex):
```
{Mermaid sequence diagram or text description showing the complete flow}
```

---

## Integration Points

### External Systems

**{External System 1}**:
- Purpose: {why we integrate}
- Protocol: {HTTP, gRPC, Kafka, etc.}
- Data format: {JSON, Protobuf, Avro, etc.}
- Error handling: {how failures are handled}

### Internal Actors/Services

**{Internal Component 1}**:
- Interaction: {describe the interaction pattern}
- Messages exchanged: {list message types}
- Dependencies: {what this component depends on}

---

## Success Criteria

How we'll know this feature works correctly:

- ✅ {Criterion 1}: {specific measurable outcome}
- ✅ {Criterion 2}: {specific measurable outcome}
- ✅ {Criterion 3}: {specific measurable outcome}

**Testing Strategy**:
- Unit tests: {what will be unit tested}
- Component tests: {what integration scenarios will be tested}
- Manual testing: {what requires manual verification}

---

## Open Questions

{List any unresolved questions or decisions that need to be made before implementation}

❓ **Question 1**: {describe the question}
   - **Options**: {Option A, Option B, Option C}
   - **Trade-offs**: {discuss pros/cons of each}
   - **Decision needed by**: {who needs to decide}

❓ **Question 2**: {describe the question}
   - **Impact if unresolved**: {what happens if we don't decide}
   - **Recommendation**: {your suggestion with reasoning}

**IMPORTANT**: All open questions must be resolved before moving to Phase 1.

---

## Design Decisions Log

{As decisions are made during architecture design, log them here with rationale}

### Decision 1: {Decision Title}
- **Date**: {date}
- **Decision**: {what was decided}
- **Rationale**: {why this decision was made}
- **Alternatives Considered**: {what other options were discussed}
- **Implications**: {impact on implementation}

### Decision 2: {Decision Title}
- **Date**: {date}
- **Decision**: {what was decided}
- **Rationale**: {why this decision was made}
- **Trade-offs**: {what we're optimizing for vs. what we're sacrificing}

---

## Implementation Phases

Break down the implementation into logical phases:

### Phase 1.1: {Foundation Work}
- {Task 1}
- {Task 2}
- Exit criteria: {how we know this phase is done}

### Phase 1.2: {Core Implementation}
- {Task 1}
- {Task 2}
- Exit criteria: {how we know this phase is done}

### Phase 1.3: {Integration}
- {Task 1}
- {Task 2}
- Exit criteria: {how we know this phase is done}

---

## Risks and Mitigations

### Risk 1: {Risk Description}
- **Likelihood**: {High/Medium/Low}
- **Impact**: {High/Medium/Low}
- **Mitigation**: {how we'll address or reduce this risk}

### Risk 2: {Risk Description}
- **Likelihood**: {High/Medium/Low}
- **Impact**: {High/Medium/Low}
- **Mitigation**: {how we'll address or reduce this risk}

---

## Dependencies

**Upstream Dependencies** (what this feature depends on):
- {Dependency 1}: {description and current state}
- {Dependency 2}: {description and current state}

**Downstream Impact** (what depends on this feature):
- {Impacted Component 1}: {how it's affected}
- {Impacted Component 2}: {how it's affected}

---

## Approval

**Architecture Review**:
- Reviewed by: {user name/email}
- Date: {date}
- Status: ✅ Approved / ⏳ Pending / ❌ Rejected
- Comments: {any feedback or conditions}

**Ready for Phase 1**: {Yes/No}

---

## As-Built Updates (Phase 6)

{This section is populated during Phase 6 after implementation is complete}

### What Changed During Implementation

{Document differences between planned and actual implementation}

**Change 1**: {description}
- **Reason**: {why the change was necessary}
- **Impact**: {how this affected the design}

### Implementation Decisions

{Decisions made during coding that weren't covered in original architecture}

**Decision**: {description}
- **Context**: {situation that required the decision}
- **Rationale**: {why we chose this approach}

### Lessons Learned

{What we'd do differently if starting over}

- {Lesson 1}
- {Lesson 2}

### Final Architecture

{Update diagrams and descriptions to match as-built reality}

**Actual Data Flow**:
```
{Updated diagram showing how it actually works}
```

**Actual Component Structure**:
```
{Updated structure showing what was actually built}
```

---

## Related Documentation

- Feature specification: {link or path}
- User stories: {link or path}
- API documentation: {link or path}
- Test plan: {link or path}

---

**Document Version**: 1.0 (Phase 0) | As-Built: TBD (Phase 6)