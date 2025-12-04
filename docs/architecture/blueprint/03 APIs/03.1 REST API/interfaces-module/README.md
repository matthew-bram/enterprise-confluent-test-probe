# Test Probe Interfaces Module Documentation

**Location:** `docs/architecture/blueprint/03 APIs/03.1 REST/interfaces-module/`
**Last Updated:** 2025-10-19
**Status:** Complete - As-Built Documentation

---

## Quick Navigation

This directory contains comprehensive documentation for the `test-probe-interfaces` Maven module, which implements the REST API and demonstrates hexagonal architecture principles.

### 1. Architecture Design
**File:** [03.1.1-interfaces-module-architecture.md](./03.1.1-interfaces-module-architecture.md)
**Size:** 63KB | 680 lines
**Version:** 1.2 (Phase 6 - As-Built)

**What's Inside:**
- Executive summary and key architectural principles
- Module structure and dependency flow
- Core and Common module changes
- Interfaces module design (routes, handlers, models)
- Configuration design with phantom types
- Anti-corruption layer implementation
- Integration with builder pattern
- Success criteria and future extensibility
- As-Built vs. As-Designed comparison

**Start Here If:**
- You need to understand the overall architecture
- You're implementing a new interface (CLI, gRPC)
- You want to understand hexagonal architecture in practice
- You need to know how the anti-corruption layer works

### 2. Implementation Plan
**File:** [03.1.2-interfaces-implementation-plan.md](./03.1.2-interfaces-implementation-plan.md)
**Size:** 51KB | 1556 lines
**Version:** 1.6 (Complete)

**What's Inside:**
- Complete 7-phase implementation plan
- Detailed file structure and code organization
- Phase-by-phase implementation guide
- Test-driven development approach
- Acceptance criteria for each phase
- Complete code examples and patterns

**Phases Covered:**
1. **Phase 1:** Maven module setup and basic structure
2. **Phase 2:** API models and transformers (anti-corruption layer)
3. **Phase 3:** Configuration and builder integration
4. **Phase 4:** REST routes and handlers
5. **Phase 5:** Error handling and middleware
6. **Phase 6:** Testing and validation
7. **Phase 7:** Documentation and handoff

**Start Here If:**
- You're implementing the interfaces module from scratch
- You need step-by-step guidance for a specific phase
- You want to understand the test-driven approach
- You need code examples for routes, handlers, or transformers

### 3. Test Coverage Summary
**File:** [03.1.3-interfaces-test-coverage.md](./03.1.3-interfaces-test-coverage.md)
**Size:** 16KB | 476 lines

**What's Inside:**
- Complete BDD scenario breakdown
- 21 BDD scenarios across 6 feature areas
- Unit test coverage analysis
- Testing patterns and fixtures
- Coverage metrics and quality gates

**BDD Feature Areas:**
1. Test Submission (4 scenarios)
2. Test Status Retrieval (5 scenarios)
3. Test Cancellation (4 scenarios)
4. Health Check (2 scenarios)
5. Error Handling (4 scenarios)
6. Integration Testing (2 scenarios)

**Start Here If:**
- You're writing tests for the interfaces module
- You need to understand BDD scenario structure
- You want to verify test coverage
- You're implementing new REST endpoints

---

## Key Concepts

### Hexagonal Architecture
The interfaces module demonstrates hexagonal (ports and adapters) architecture:
- **Core:** Business logic (actors, FSM, domain models) - protocol-agnostic
- **Interfaces:** Adapters for external protocols (REST, CLI, gRPC)
- **Anti-Corruption Layer:** Transforms external models to internal models

### Function Currying Pattern
The interface layer receives pre-configured functions from the builder:
```scala
// Core provides curried functions
val submitTest: TestRequest => Future[TestResponse]
val getStatus: UUID => Future[TestStatus]
val cancelTest: UUID => Future[CancelResult]

// Interface uses them without knowing about QueueActor
routes(submitTest, getStatus, cancelTest)
```

### Anti-Corruption Layer
Prevents external API contracts from polluting core domain models:
- **API Models:** `TestRequestDto`, `TestStatusDto` (in interfaces module)
- **Core Models:** `TestRequest`, `TestExecutionResult` (in core module)
- **Transformers:** Bidirectional conversion with validation

### Builder Integration
Uses phantom type `ProbeInterface` trait for type-safe configuration:
```scala
ProbeBuilder
  .withConfig(config)
  .withRestInterface(RestInterfaceConfig(host, port))
  .build()
```

---

## Module Statistics

**Total Lines:** 1547 lines across 18 files

**File Breakdown:**
- Routes: 3 files, 287 lines
- Handlers: 3 files, 246 lines
- Models: 4 files, 312 lines
- Transformers: 3 files, 218 lines
- Configuration: 2 files, 156 lines
- Error Handling: 1 file, 89 lines
- Builder Integration: 2 files, 239 lines

**Test Coverage:**
- Unit Tests: 15 test classes
- BDD Scenarios: 21 scenarios (152 lines)
- Coverage Target: 70%+ (85%+ for handlers)

---

## REST API Endpoints

### Test Management
- `POST /api/v1/tests` - Submit new test
- `GET /api/v1/tests/:testId/status` - Get test status
- `DELETE /api/v1/tests/:testId` - Cancel test

### System
- `GET /api/v1/health` - Health check endpoint

### Documentation
- OpenAPI 3.1.0 specification: `test-probe-interfaces/src/main/resources/openapi/test-probe-api-v1.yaml`

---

## Related Documentation

### Architecture Index
See [docs/architecture/INDEX.md](../../../INDEX.md) for:
- Links to all architecture documentation
- Recently updated documents
- Quick links for AI assistants
- Architecture decision records (ADRs)

### Core Module Documentation
- [GuardianActor Blueprint](../../02%20Booting/02.1-guardian-actor.md)
- [QueueActor Blueprint](../../08%20Test%20Flow/08.1%20Queuing%20Tests/08.1.1-queue-actor.md)
- [TestExecutionActor FSM](../../05%20State%20Machine/05.1-test-execution-actor-fsm.md)

### Testing Practice
- [Testing Pyramid](../../../../testing-practice/testing-pyramid.md)
- [BDD Testing Standards](.claude/styles/bdd-testing-standards.md) (in project root)

### Development Guidelines
- [CLAUDE.md](../../../../../CLAUDE.md) - AI development guidelines
- [Implementation Workflow](.claude/guides/IMPLEMENTATION-WORKFLOW.md) (in project root)

---

## Future Extensions

### CLI Interface (Planned)
- Location: `docs/architecture/blueprint/03 APIs/03.3 CLI/`
- Will use same curried functions from core
- Separate anti-corruption layer for CLI arguments

### gRPC Interface (Planned)
- Location: `docs/architecture/blueprint/03 APIs/03.2 gRPC/`
- Protocol Buffers for API contracts
- Separate anti-corruption layer for protobuf

### WebSocket (Future)
- Real-time test status updates
- Server-sent events for progress notifications

---

## Contributing

When updating interfaces module documentation:

1. Update relevant section in architecture or implementation plan
2. Update "Last Updated" timestamp in document header
3. Add entry to document history at bottom of file
4. Update [INDEX.md](../../../INDEX.md) if adding new diagrams or major changes
5. Validate all Mermaid diagrams render correctly
6. Ensure cross-references are accurate

---

## Document History

- 2025-10-19: Created README.md for interfaces module documentation navigation
- 2025-10-19: Organized documentation from working/ directory to docs/architecture/blueprint/
- 2025-10-19: Renamed files to follow blueprint numbering convention (03.1.1, 03.1.2, 03.1.3)
- 2025-10-19: Updated INDEX.md with comprehensive interfaces module section
