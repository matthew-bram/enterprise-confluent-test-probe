# Documentation Structure Guide

**Purpose**: This guide defines the organization, placement rules, naming conventions, and maintenance requirements for ALL documentation under `docs/`. Following this structure ensures documentation remains discoverable, navigable, and evergreen as the codebase evolves.

**Scope**: Applies to all documentation in `docs/` root directory, including architecture, API specifications, diagrams, ADRs, product requirements, and testing practices.

---

## Table of Contents

1. [Directory Structure Rules](#directory-structure-rules)
2. [Blueprint Schedule System](#blueprint-schedule-system)
3. [Document Placement Decision Tree](#document-placement-decision-tree)
4. [Naming Conventions](#naming-conventions)
5. [Evergreen Architecture Requirement](#evergreen-architecture-requirement)
6. [INDEX.md Maintenance](#indexmd-maintenance)
7. [Cross-Reference Management](#cross-reference-management)
8. [ADR Management](#adr-management)
9. [API Documentation](#api-documentation)
10. [Examples](#examples)

---

## Directory Structure Rules

### Top-Level Organization

```
docs/
├── architecture/          # System architecture documentation
│   ├── INDEX.md          # Navigation hub for architecture docs (CRITICAL)
│   ├── blueprint/        # Blueprint schedule system (organized by topic)
│   └── adr/              # Centralized Architectural Decision Records
├── api/                  # API specifications (OpenAPI, gRPC protobuf, etc.)
├── diagrams/             # Visual diagrams (mermaid, plantuml, etc.)
├── product/              # Product requirements, roadmaps, PRDs
├── testing-practice/     # Testing standards, pyramid, coverage requirements
└── [other top-level directories as needed]
```

### Key Principles

1. **Architecture vs. API Separation**
   - `docs/architecture/`: HOW the system works (design, patterns, decisions)
   - `docs/api/`: WHAT the system exposes (contracts, specifications)

2. **Centralized ADRs**
   - ALL ADRs go in `docs/architecture/adr/`
   - Never place ADRs within blueprint schedules
   - This enables chronological ADR numbering and easy discovery

3. **Single Source of Truth**
   - `docs/architecture/INDEX.md` is the MASTER navigation document
   - ALL architecture documents must be linked from INDEX.md
   - Orphaned documents (not in INDEX.md) will become lost over time

4. **Blueprint Organization**
   - Technical architecture lives in `docs/architecture/blueprint/XX [Topic]/`
   - Use numbered schedules (01-99) for logical ordering
   - Sub-schedules use XX.Y format for hierarchical organization

---

## Blueprint Schedule System

### Schedule Numbering Format

**Top-Level Schedules**: `XX [Topic Name]/`
- XX = Two-digit number (01-99)
- Topic Name = Clear, descriptive name
- Example: `02 Booting/`, `05 State Machine/`

**Sub-Schedules**: `XX.Y [Subtopic Name]/`
- XX = Parent schedule number
- Y = Sub-schedule number (1-9)
- Example: `02.1 Booting the Service DSL/`, `05.2 TestExecutionActor FSM/`

**Sub-Sub-Schedules** (if needed): `XX.Y.Z [Detail Name]/`
- Use sparingly - deep nesting reduces discoverability
- Example: `05.2.1 FSM State Transitions/`

### Current Blueprint Schedules

Based on existing structure (as of last review):

```
01 Security/
02 Booting/
03 APIs/
04 Adapters/
05 State Machine/
06 Triggers/
07 Models/
08 Test Flow/
09 CI-CD/
10 Kafka Streaming/
```

### When to Create New Schedules

**DO create a new schedule when**:
- Topic is a major architectural concern (e.g., Security, Booting, APIs)
- Multiple related documents exist or are planned
- Topic deserves standalone navigation in INDEX.md

**DO NOT create a new schedule when**:
- Only 1-2 documents exist (place in existing schedule or root)
- Topic is a sub-concern of existing schedule (use XX.Y instead)
- Unsure of persistence (place in working/ directory first)

**ALWAYS ask user permission before**:
- Creating schedules 11+ (expanding beyond current structure)
- Renaming existing schedules
- Merging or consolidating schedules

### When to Create Sub-Schedules

**DO create a sub-schedule when**:
- Parent schedule contains 5+ documents on a single subtopic
- Subtopic has distinct phases or components (e.g., 02.1 Service DSL, 02.2 Actor System)
- Improves navigability without deep nesting

**Example**:
```
03 APIs/
├── 03.1 REST API/
│   ├── 03.1-rest-api-architecture.md
│   ├── 03.1-rest-api-endpoints.md
│   ├── 03.1-rest-error-handling.md
│   └── 03.1-rest-testing-strategy.md
└── 03.2 gRPC API/  (future)
```

---

## Document Placement Decision Tree

Use this decision tree to determine where a document belongs:

```
Is it an Architectural Decision Record (ADR)?
├─ YES → docs/architecture/adr/NNNN-kebab-case-title.md
└─ NO ↓

Is it an API specification (OpenAPI, protobuf, etc.)?
├─ YES → docs/api/[api-name]-[version]-spec.yaml
└─ NO ↓

Is it a visual diagram (mermaid, plantuml, etc.)?
├─ YES → docs/diagrams/[topic]-[diagram-type].mermaid
└─ NO ↓

Is it product-related (PRD, roadmap, requirements)?
├─ YES → docs/product/[document-name].md
└─ NO ↓

Is it testing practice (pyramid, standards, coverage)?
├─ YES → docs/testing-practice/[document-name].md
└─ NO ↓

Is it architecture documentation?
├─ YES ↓
│   Does it fit an existing blueprint schedule?
│   ├─ YES → docs/architecture/blueprint/XX [Topic]/XX-kebab-case-title.md
│   └─ NO ↓
│       Is it a new major architectural concern?
│       ├─ YES → Ask user permission to create new schedule
│       └─ NO → Place in docs/architecture/ root (temporary)
└─ NO → Consult with user on appropriate location
```

---

## Naming Conventions

### Blueprint Schedule Documents

**Format**: `XX-kebab-case-title.md` or `XX.Y-kebab-case-title.md`

**Rules**:
1. Start with schedule number (XX) or sub-schedule number (XX.Y)
2. Use kebab-case (lowercase with hyphens)
3. Be descriptive but concise
4. Use consistent terminology with codebase

**Examples**:
- `02-booting-sequence.md` (in 02 Booting/)
- `02.1-service-dsl-bootstrap.md` (in 02.1 Booting the Service DSL/)
- `05-state-machine-overview.md` (in 05 State Machine/)
- `05.2-test-execution-fsm.md` (in 05.2 TestExecutionActor FSM/)

### ADR Naming

**Format**: `NNNN-kebab-case-title.md`

**Rules**:
1. NNNN = Four-digit number (0001, 0002, ..., 9999)
2. Sequential numbering (never reuse numbers)
3. Chronological order (reflects when decision was made)
4. kebab-case title matching ADR content

**Examples**:
- `0001-use-akka-typed-actors.md`
- `0012-rest-api-versioning-strategy.md`
- `0024-pekko-migration-decision.md`

### API Specification Naming

**Format**: `[api-name]-[version]-[spec-type].yaml` or `.json`

**Examples**:
- `rest-api-v1-openapi.yaml`
- `grpc-api-v1-protobuf.proto`
- `kafka-schema-registry-avro.avsc`

### Diagram Naming

**Format**: `[topic]-[diagram-type].mermaid` or `.plantuml`

**Examples**:
- `test-execution-actor-state-diagram.mermaid`
- `guardian-actor-supervision-tree.mermaid`
- `rest-api-request-flow.mermaid`

---

## Evergreen Architecture Requirement

**CRITICAL**: Architecture documentation MUST always reflect the current state of the codebase. Stale documentation is worse than no documentation.

### Evergreen Principles

1. **Documentation-Code Synchronization**
   - When code changes, documentation changes
   - When architecture evolves, diagrams evolve
   - When APIs change, specifications change

2. **No Orphaned Documents**
   - Every document must be linked from INDEX.md or another discoverable location
   - Dead links must be fixed immediately
   - Obsolete documents must be archived or deleted (with ADR explanation)

3. **Cross-Reference Integrity**
   - Links between documents must be validated
   - Moved documents require systematic link updates
   - Broken links are treated as bugs

### Enforcing Evergreen Architecture

The **architecture-doc-keeper agent** is responsible for maintaining evergreen architecture. This agent MUST:

1. **Update INDEX.md** (NON-OPTIONAL)
   - Every document addition/removal requires INDEX.md update
   - Every reorganization requires INDEX.md restructure
   - Last updated timestamp must be refreshed

2. **Fix Cross-References** (NON-OPTIONAL)
   - Use grep/ripgrep to find all references to moved documents
   - Update links in all affected files (docs, code comments, CLAUDE.md, READMEs)
   - Validate links work after updates

3. **Validate Before Completion** (NON-OPTIONAL)
   - Verify no broken links exist
   - Confirm INDEX.md reflects current structure
   - Check cross-references are accurate

4. **Ask Permission for Questionable Changes**
   - Deleting documents (may have historical value)
   - Creating new top-level schedules (impacts navigation structure)
   - Major reorganizations (validate user intent first)

### Evergreen Workflow

**For Every Architecture Change**:

```
1. Make code/architecture changes
2. Update affected documentation files
3. Update diagrams if applicable
4. Fix cross-references (grep for old paths/names)
5. Update INDEX.md navigation
6. Validate all links work
7. Update "Last Updated" timestamps
8. Document changes in commit message
```

**For Every Documentation Move**:

```
1. Identify all references to document (grep/ripgrep)
2. Move document to new location
3. Update all references found in step 1
4. Update INDEX.md to reflect new location
5. Validate all links work
6. Create redirect or tombstone if needed (high-traffic docs)
7. Document move in commit message
```

---

## INDEX.md Maintenance

**Location**: `docs/architecture/INDEX.md`

**Purpose**: Master navigation hub for all architecture documentation. Acts as table of contents and discovery tool.

### INDEX.md Structure

```markdown
# Architecture Documentation Index

Last Updated: YYYY-MM-DD

## Overview
[Brief description of architecture documentation organization]

## Blueprint Schedules

### 01 Security
- [Document Name](blueprint/01 Security/01-document-name.md)
- [Another Document](blueprint/01 Security/01-another-document.md)

### 02 Booting
- [Booting Sequence](blueprint/02 Booting/02-booting-sequence.md)

#### 02.1 Booting the Service DSL
- [Service DSL Bootstrap](blueprint/02 Booting/02.1 Booting the Service DSL/02.1-service-dsl-bootstrap.md)

[... repeat for all schedules ...]

## Architectural Decision Records (ADRs)
- [ADR Index](adr/INDEX.md)
- [0001 - Use Akka Typed Actors](adr/0001-use-akka-typed-actors.md)
- [0002 - Phantom Type Builder](adr/0002-phantom-type-builder.md)
[... list all ADRs chronologically ...]

## Quick Reference
- [Actor Hierarchy Diagram](../diagrams/actor-hierarchy.mermaid)
- [REST API Documentation](blueprint/03 APIs/03.1 REST API/)
- [Testing Strategy](../testing-practice/testing-pyramid.md)
```

### INDEX.md Update Requirements

**MUST update INDEX.md when**:
- Adding new architecture document
- Moving architecture document
- Deleting architecture document
- Creating new blueprint schedule or sub-schedule
- Creating new ADR

**MUST include**:
- "Last Updated" timestamp at top
- Hierarchical structure matching blueprint organization
- Direct links to all documents (no orphans)
- Quick reference section for high-traffic docs

---

## Cross-Reference Management

### Finding References

**Use grep/ripgrep to find all references**:

```bash
# Find references to a specific document
rg "REST-API-ARCHITECTURE.md" docs/

# Find references to a path
rg "docs/architecture/REST-" docs/

# Find references in markdown files only
rg -t md "TestExecutionActor" docs/
```

### Common Reference Locations

When moving or renaming documents, check:

1. **docs/** - Other documentation files
2. **CLAUDE.md** - Root project documentation
3. **README.md** - Project readme
4. **ADRs** - Architectural Decision Records
5. **Code comments** - Inline documentation
6. **.claude/guides/** - Claude Code guides
7. **.claude/agents/** - Agent prompt files

### Link Update Protocol

1. Run grep/ripgrep to find all references
2. Update each reference to new path/name
3. Validate link works (manual click test or automated link checker)
4. Document changes in commit message
5. Verify no broken links remain

---

## ADR Management

### ADR Location

**Centralized Location**: `docs/architecture/adr/`

**NEVER place ADRs**:
- Within blueprint schedules
- In code directories
- In multiple locations (single source of truth)

### ADR Numbering

**Format**: `NNNN-kebab-case-title.md`

**Numbering Rules**:
1. Sequential (0001, 0002, 0003, ...)
2. Chronological (reflects when decision was made)
3. Never reuse numbers (even for deleted ADRs)
4. Use 4 digits (supports up to 9999 ADRs)

### ADR Index

**Location**: `docs/architecture/adr/INDEX.md`

**Purpose**: Quick reference for all ADRs with status and date

**Format**:
```markdown
# Architectural Decision Records

## Active ADRs
- [0001 - Use Akka Typed Actors](0001-use-akka-typed-actors.md) - 2024-01-15 - ACTIVE
- [0002 - Phantom Type Builder](0002-phantom-type-builder.md) - 2024-02-03 - ACTIVE

## Superseded ADRs
- [0012 - Use Akka 2.6](0012-use-akka-2-6.md) - 2024-03-10 - SUPERSEDED by ADR-0024

## Deprecated ADRs
- [0005 - Monolithic Architecture](0005-monolithic-architecture.md) - 2024-01-20 - DEPRECATED
```

---

## API Documentation

### API Specification Location

**Location**: `docs/api/`

**Naming**: `[api-name]-[version]-[spec-type].[ext]`

**Examples**:
- `rest-api-v1-openapi.yaml`
- `grpc-api-v1.proto`
- `kafka-events-avro-schema.avsc`

### API Architecture Documentation

**Location**: `docs/architecture/blueprint/03 APIs/03.X [API Type]/`

**Examples**:
- `docs/architecture/blueprint/03 APIs/03.1 REST API/03.1-rest-api-architecture.md`
- `docs/architecture/blueprint/03 APIs/03.2 gRPC API/03.2-grpc-api-design.md`

### API vs. Architecture Separation

**`docs/api/`** (SPECIFICATIONS):
- OpenAPI YAML files
- Protobuf definitions
- Avro schemas
- GraphQL schemas
- Machine-readable contracts

**`docs/architecture/blueprint/03 APIs/`** (ARCHITECTURE):
- API design philosophy
- Endpoint documentation
- Error handling strategies
- Authentication/authorization
- Versioning strategies
- Testing approaches

---

## Examples

### Example 1: Adding REST API Documentation

**Scenario**: New REST API for test submission has been implemented. Need to document it.

**Steps**:
1. Create sub-schedule: `docs/architecture/blueprint/03 APIs/03.1 REST API/`
2. Create architecture docs:
   - `03.1-rest-api-architecture.md` (design overview)
   - `03.1-rest-api-endpoints.md` (endpoint catalog)
   - `03.1-rest-error-handling.md` (error strategy)
   - `03.1-rest-testing-strategy.md` (testing approach)
3. Copy OpenAPI spec: `docs/api/rest-api-v1-openapi.yaml`
4. Update `docs/architecture/INDEX.md`:
   ```markdown
   ### 03 APIs
   #### 03.1 REST API
   - [Architecture](blueprint/03 APIs/03.1 REST API/03.1-rest-api-architecture.md)
   - [Endpoints](blueprint/03 APIs/03.1 REST API/03.1-rest-api-endpoints.md)
   - [Error Handling](blueprint/03 APIs/03.1 REST API/03.1-rest-error-handling.md)
   - [Testing Strategy](blueprint/03 APIs/03.1 REST API/03.1-rest-testing-strategy.md)
   - [OpenAPI Specification](../../api/rest-api-v1-openapi.yaml)
   ```
5. Update CLAUDE.md if needed
6. Validate all links work

### Example 2: Moving Existing Documentation

**Scenario**: 6 REST documentation files exist at `docs/architecture/` root. Need to move them into blueprint structure.

**Steps**:
1. Create target directory: `docs/architecture/blueprint/03 APIs/03.1 REST API/`
2. Find all references:
   ```bash
   rg "REST-API-ARCHITECTURE.md" docs/
   rg "REST-API-ENDPOINTS.md" docs/
   # ... repeat for all 6 files
   ```
3. Move files with new naming:
   - `REST-API-ARCHITECTURE.md` → `03.1-rest-api-architecture.md`
   - `REST-API-ENDPOINTS.md` → `03.1-rest-api-endpoints.md`
   - (etc.)
4. Update all references found in step 2
5. Update INDEX.md with new locations
6. Validate all links work
7. Document move in commit message

### Example 3: Creating New ADR

**Scenario**: Team decided to migrate from Akka to Apache Pekko. Need to document decision.

**Steps**:
1. Check last ADR number in `docs/architecture/adr/` (e.g., 0023)
2. Create new ADR: `docs/architecture/adr/0024-migrate-akka-to-pekko.md`
3. Write ADR following template
4. Update `docs/architecture/adr/INDEX.md`:
   ```markdown
   ## Active ADRs
   - [0024 - Migrate Akka to Apache Pekko](0024-migrate-akka-to-pekko.md) - 2025-10-16 - ACTIVE
   ```
5. Update main INDEX.md to include new ADR
6. Reference ADR in related architecture docs
7. Commit with message: "ADR-0024: Migrate from Akka to Apache Pekko"

### Example 4: Creating New Blueprint Schedule

**Scenario**: Need to document caching strategy. Existing schedules don't fit. Consider creating "11 Caching" schedule.

**Steps**:
1. **STOP - Ask user permission first**
2. Propose new schedule to user: "I'd like to create '11 Caching/' schedule for caching architecture docs. Approve?"
3. If approved:
   - Create `docs/architecture/blueprint/11 Caching/`
   - Create initial doc: `11-caching-strategy.md`
4. Update INDEX.md:
   ```markdown
   ### 11 Caching
   - [Caching Strategy](blueprint/11 Caching/11-caching-strategy.md)
   ```
5. Update this guide with new schedule
6. Commit changes

---

## Summary Checklist

When working with documentation, use this checklist:

### Adding New Document
- [ ] Determine correct location using decision tree
- [ ] Follow naming conventions (XX-kebab-case.md or XX.Y-kebab-case.md)
- [ ] Create document with appropriate content
- [ ] Update INDEX.md with link to new document
- [ ] Update "Last Updated" timestamp in INDEX.md
- [ ] Validate link works

### Moving Existing Document
- [ ] Run grep/ripgrep to find all references
- [ ] Move document to new location
- [ ] Rename following naming conventions
- [ ] Update all references found
- [ ] Update INDEX.md with new location
- [ ] Validate all links work
- [ ] Document move in commit message

### Creating New ADR
- [ ] Check last ADR number
- [ ] Create NNNN-kebab-case-title.md in docs/architecture/adr/
- [ ] Write ADR following template
- [ ] Update docs/architecture/adr/INDEX.md
- [ ] Update docs/architecture/INDEX.md
- [ ] Reference from related architecture docs
- [ ] Commit with "ADR-NNNN: Title" message

### Creating New Blueprint Schedule
- [ ] **ASK USER PERMISSION FIRST** (schedules 11+)
- [ ] Create XX [Topic Name]/ directory
- [ ] Create initial documents
- [ ] Update INDEX.md with new schedule section
- [ ] Update this guide with new schedule
- [ ] Commit changes

### Reorganizing Multiple Documents
- [ ] Create comprehensive plan
- [ ] Get user approval for questionable changes
- [ ] Find all references (grep/ripgrep)
- [ ] Move/rename documents systematically
- [ ] Update all references
- [ ] Update INDEX.md completely
- [ ] Validate all links work
- [ ] Create summary of changes
- [ ] Commit with detailed message

---

**Last Updated**: 2025-10-19

**Version**: 1.0.0

**Maintained By**: architecture-doc-keeper agent
