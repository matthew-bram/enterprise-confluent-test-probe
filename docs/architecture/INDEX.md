# Probe Core Architecture Documentation Index

**Last Updated:** 2025-11-26 (Comprehensive Documentation Update: User Guides, Java API, Tutorials, Architecture Fill-Ins)
**Status:** Active
**Maintainer:** Engineering Team

## Overview

This index provides a comprehensive guide to the Probe Core architecture documentation. The architecture is organized into four main categories:

1. **User Guide & Tutorials:** Getting started guides, tutorials, and adoption documentation
2. **Architecture Decision Records (ADRs):** Key architectural decisions with context and rationale
3. **Blueprint Documentation:** Detailed component designs and implementation guides
4. **High-Level Diagrams:** System-wide architectural views

---

## User Guide & Getting Started

**For Teams Adopting Test-Probe** - Start here!

### Quick Start
- [Root README](../../README.md) - Project overview and 5-minute quick start
- [Getting Started Guide](../user-guide/GETTING-STARTED.md) - Comprehensive setup and first test tutorial
- [FAQ](../user-guide/FAQ.md) - Frequently asked questions
- [Troubleshooting](../user-guide/TROUBLESHOOTING.md) - Common issues and solutions

### Java API Documentation (Primary)
- [Java DSL API Reference](../api/probe-java-dsl-api.md) - **NEW** Complete ProbeJavaDsl documentation
- [Java Step Definitions Guide](../user-guide/java-step-definitions-guide.md) - **NEW** Writing Java Cucumber steps
- [Java Event Models Guide](../user-guide/java-event-models.md) - **NEW** POJOs, Avro, Protobuf in Java
- [Java Module README](../../test-probe-java-api/README.md) - **NEW** Quick start for Java developers

### Tutorial Series (Java-First)
Progressive tutorials from basic to advanced:
1. [01 - Your First Test](../user-guide/tutorials/01-first-test.md) - **NEW** Local Kafka, simple feature
2. [02 - JSON Events](../user-guide/tutorials/02-json-events.md) - **NEW** JSON Schema, CloudEvent envelope
3. [03 - Avro & Protobuf](../user-guide/tutorials/03-avro-protobuf.md) - **NEW** Multi-format serialization
4. [04 - Multi-Cluster Testing](../user-guide/tutorials/04-multi-cluster.md) - **NEW** Cross-datacenter testing
5. [05 - Evidence Generation](../user-guide/tutorials/05-evidence-generation.md) - **NEW** Audit compliance

### API Examples
- [Java DSL Examples](../api/examples/probe-java-dsl-examples.md) - **NEW** CompletionStage patterns, blocking patterns
- [Scala DSL Examples](../api/examples/probe-scala-dsl-examples.md) - **NEW** Future-based examples
- [SerdesFactory Examples](../api/examples/serdes-factory-examples.md) - **NEW** JSON, Avro, Protobuf examples

### Integration Guides
- [Maven Project Setup](../user-guide/integration/maven-project.md) - **NEW** Adding Test-Probe to existing projects
- [CI/CD Pipelines](../user-guide/integration/ci-cd-pipelines.md) - **NEW** GitHub Actions, GitLab CI, Jenkins

---

## Quick Links

### Most Recently Updated
- **ADR-KAFKA-001: Multiple Bootstrap Servers** - 2025-11-22 **NEW**
  - [ADR-KAFKA-001](./adr/ADR-KAFKA-001-multiple-bootstrap-servers.md) - Multi-cluster support for topic directives with per-topic bootstrap servers
  - Enables testing of cross-cluster event propagation (on-premise to cloud, Region1 to Region2)
  - Backward compatible: existing tests work without changes
  - Validation: TopicDirectiveValidator for uniqueness and format checking
  - Test infrastructure: Multi-cluster registry in TestcontainersManager
- **Testing Practice Documentation Update** - 2025-11-22
  - [Assertion Quality Guidelines](../testing-practice/assertion-quality-guidelines.md) - CRISP model, anti-patterns, ScalaTest patterns
  - [Actor Testing Patterns](../testing-practice/actor-testing-patterns.md) - FSM testing, ActorTestKit vs BehaviorTestKit
  - [Test Data Builders Guide](../testing-practice/test-data-builders.md) - Builder patterns, KafkaMessage abstraction
  - Updated: Testing Pyramid (inverted pyramid anti-pattern), Unit Testing (visibility pattern), Component Testing (BDD step quality), Directory Structure (fixture layers)
- [10.2 Serdes & DSL Architecture](./blueprint/10%20Kafka%20Streaming/10.2%20Serialization/10.2-serdes-dsl-architecture.md) - 2025-11-22 (Multi-format serialization, schema type dispatch, ProbeScalaDsl thread safety)
- [ProbeScalaDsl API Reference](../api/probe-scala-dsl-api.md) - 2025-11-22 **NEW** (Complete DSL API documentation with Cucumber examples)
- [ADR-SERDES-001: JSON Schema Serialization](./adr/ADR-SERDES-001-confluent-json-schema-serialization-strategy.md) - 2025-11-19 **NEW** (JsonNode pattern, isKey parameter fix)
- [ADR-SERDES-002: OneOf Polymorphic Events](./adr/ADR-SERDES-002-json-schema-oneof-polymorphic-events.md) - 2025-11-21 **NEW** (Event democratization, TopicRecordNameStrategy)
- [ADR-STORAGE-005 through 009](./adr/) - 2025-10-27 (5 comprehensive block storage ADRs: Abstraction v2, SDK Streaming, JIMFS, Topic Directive Format, Async Client Strategy)
- [Topic Directive Model API](../api/topic-directive-model.md) - 2025-10-27 (Complete YAML format specification)
- [04.1 Block Storage Architecture](./blueprint/04%20Adapters/04.1-block-storage-architecture.md) - 2025-10-27 (Builder modules, async optimization)
- [Rosetta Vault Mapping API v2.1](../api/rosetta-vault-mapping-api.md) - 2025-10-23 **MAJOR UPDATE** (Configuration loading, path resolution, best practices)
- [04.2.1 Vault Service Integration](./blueprint/04%20Adapters/04.2%20Vault%20Key%20Store/04.2.1-vault-service-integration.md) - 2025-10-23 **NEW** (RequestBodyBuilder, bidirectional vault architecture)
- [Cucumber Integration Architecture](../../working/cucumber/cucumber-architecture.md) - 2025-10-20 **AS-BUILT** (Phases 1-6 complete, 409/411 tests passing)
- [04.1 Service Layer Architecture](./blueprint/04%20Adapters/04.1-service-layer-architecture.md) - 2025-10-20 **UPDATED** (Added Cucumber service layer)
- [05.3 Child Actors Integration](./blueprint/05%20State%20Machine/05.3-child-actors-integration.md) - 2025-10-20 **UPDATED** (CucumberExecutionActor real execution complete)
- [ADR-STORAGE-001: Block Storage Abstraction](./adr/ADR-STORAGE-001-block-storage-abstraction.md) - 2025-10-20 **NEW**
- [ADR-STORAGE-002: Function Passing Pattern](./adr/ADR-STORAGE-002-function-passing-pattern.md) - 2025-10-20 **NEW**
- [ADR-STORAGE-003: jimfsLocation State Management](./adr/ADR-STORAGE-003-jimfs-state-management.md) - 2025-10-20 **NEW**
- [ADR-STORAGE-004: Option Resolution Strategy](./adr/ADR-STORAGE-004-option-resolution-strategy.md) - 2025-10-20 **NEW**
- [REST API Architecture Documentation](blueprint/03 APIs/03.1 REST API/03.1-rest-api-architecture.md) - 2025-10-19
- [REST Error Handling Architecture](blueprint/03 APIs/03.1 REST API/03.1-rest-error-handling.md) - 2025-10-19 **NEW**
- [REST API Endpoints Reference](blueprint/03 APIs/03.1 REST API/03.1-rest-api-endpoints.md) - 2025-10-19 **NEW**
- [REST Timeouts & Resilience Configuration](blueprint/03 APIs/03.1 REST API/03.1-rest-timeouts-resilience.md) - 2025-10-19 **NEW**
- [OpenAPI Specification Guide](../api/rest-api-openapi-specification.md) - 2025-10-19 **NEW**
- [REST Testing Strategy](blueprint/03 APIs/03.1 REST API/03.1-rest-testing-strategy.md) - 2025-10-19 **NEW**
- [ADR-REST-001: Error Handling Strategy](./adr/ADR-REST-001-ERROR-HANDLING-STRATEGY.md) - 2025-10-19 **NEW**
- [ADR-REST-002: Validation Pattern](./adr/ADR-REST-002-VALIDATION-PATTERN.md) - 2025-10-19 **NEW**
- [ADR-REST-003: Pekko HTTP Technology](./adr/ADR-REST-003-PEKKO-HTTP-TECHNOLOGY.md) - 2025-10-19 **NEW**
- [09.5.1 Maven Test Filtering Architecture](./blueprint/09%20CICD/09.5%20Maven%20Build%20Patterns/09.5.1-maven-test-filtering-architecture.md) - 2025-10-19
- [03.1.1 Interfaces Module Architecture](./blueprint/03%20APIs/03.1%20REST%20API/interfaces-module/03.1.1-interfaces-module-architecture.md) - 2025-10-19
- [10.1 Kafka Streaming Architecture](./blueprint/10%20Kafka%20Streaming/10.1-kafka-streaming-architecture.md) - 2025-10-16

### For New Developers
Start here to understand the system:
1. [Product Requirements Document](../product/PRODUCT-REQUIREMENTS-DOCUMENT.md)
2. [ADR 001: FSM Pattern for Test Execution](./adr/001-fsm-pattern-for-test-execution.md)
3. [05.1 TestExecutionActor FSM Blueprint](./blueprint/05%20State%20Machine/05.1-test-execution-actor-fsm.md)
4. [Testing Pyramid](../testing-practice/testing-pyramid.md)

### For AI Assistants
Key documents for understanding system design:
- [02.1 GuardianActor Blueprint](./blueprint/02%20Booting/02.1-guardian-actor.md) - Root supervisor, Error Kernel, system bootstrap
- [03.1.1 Interfaces Module Architecture](./blueprint/03%20APIs/03.1%20REST%20API/interfaces-module/03.1.1-interfaces-module-architecture.md) - REST API, hexagonal architecture, anti-corruption layer
- [04.1 Service Layer Architecture](./blueprint/04%20Adapters/04.1-service-layer-architecture.md) - Curried functions, ServiceFunctionsContext, block storage services
- [08.1.1 QueueActor Blueprint](./blueprint/08%20Test%20Flow/08.1%20Queuing%20Tests/08.1.1-queue-actor.md) - Test queue management and coordination
- [08.1.2 QueueActor Message Routing](./blueprint/08%20Test%20Flow/08.1%20Queuing%20Tests/08.1.2-queue-actor-message-routing.md) - Message routing patterns
- [05.1 TestExecutionActor FSM Blueprint](./blueprint/05%20State%20Machine/05.1-test-execution-actor-fsm.md) - Actor lifecycle and state machine
- [05.2 TestExecutionActor Error Handling](./blueprint/05%20State%20Machine/05.2-test-execution-actor-error-handling.md) - Error propagation patterns
- [09.5.1 Maven Test Filtering Architecture](./blueprint/09%20CICD/09.5%20Maven%20Build%20Patterns/09.5.1-maven-test-filtering-architecture.md) - Maven build patterns, test filtering, module dependencies
- [10.1 Kafka Streaming Architecture](./blueprint/10%20Kafka%20Streaming/10.1-kafka-streaming-architecture.md) - Complete Kafka integration with DSL layer
- [ADR Index](#architecture-decision-records-adrs) - All architectural decisions
- [CLAUDE.md](../../CLAUDE.md) - AI development guidelines and patterns

---

## Architecture Decision Records (ADRs)

ADRs document significant architectural decisions, their context, alternatives considered, and consequences.

### Actor System Architecture

| ADR | Title | Status | Date | Component |
|-----|-------|--------|------|-----------|
| [001](./adr/001-fsm-pattern-for-test-execution.md) | FSM Pattern for Test Execution | Accepted | 2025-10-13 | TestExecutionActor |
| [002](./adr/002-self-message-continuation-pattern.md) | Self-Message Continuation for State Transitions | Accepted | 2025-10-13 | TestExecutionActor, QueueActor |
| [003](./adr/003-poison-pill-timer-pattern.md) | Poison Pill Timer Pattern for Actor Cleanup | Accepted | 2025-10-13 | TestExecutionActor |
| [004](./adr/004-factory-injection-for-child-actors.md) | Factory Injection Pattern for Child Actors | Accepted | 2025-10-13 | TestExecutionActor |
| [005](./adr/005-error-kernel-pattern.md) | Error Kernel Pattern for Exception Handling | Accepted | 2025-10-13 | TestExecutionActor, QueueActor |

### Kafka Streaming Architecture

| ADR | Title | Status | Date | Component |
|-----|-------|--------|------|-----------|
| [ADR-KAFKA-001](./adr/ADR-KAFKA-001-multiple-bootstrap-servers.md) | Multiple Bootstrap Servers per Topic Directive | Accepted | 2025-11-22 | TopicDirective, Streaming Actors, TestcontainersManager |
| [ADR-001](../adr/ADR-001-Consumer-Registry-Memory-Management.md) | Consumer Registry Memory Management | Accepted | 2025-10-16 | KafkaConsumerStreamingActor |
| [ADR-002](../adr/ADR-002-Producer-Stream-Performance-Optimization.md) | Producer Stream Performance Optimization | Accepted (Deferred) | 2025-10-16 | KafkaProducerStreamingActor |
| [ADR-003](../adr/ADR-003-Schema-Registry-Error-Handling.md) | Schema Registry Error Handling Strategy | Accepted | 2025-10-16 | Kafka Streaming Layer |
| [ADR-004](../adr/ADR-004-Consumer-Stream-Lifecycle-Management.md) | Consumer Stream Lifecycle Management | Accepted | 2025-10-16 | KafkaConsumerStreamingActor |

### Serialization Architecture (SerdesFactory)

| ADR | Title | Status | Date | Component |
|-----|-------|--------|------|-----------|
| [ADR-SERDES-001](./adr/ADR-SERDES-001-confluent-json-schema-serialization-strategy.md) | JSON Schema Serialization Strategy | Accepted | 2025-11-19 | SerdesFactory, ScalaConfluentJsonSerializer/Deserializer |
| [ADR-SERDES-002](./adr/ADR-SERDES-002-json-schema-oneof-polymorphic-events.md) | OneOf Polymorphic Event Types | Accepted | 2025-11-21 | SerdesFactory, Schema Registry |

### REST API Architecture

| ADR | Title | Status | Date | Component |
|-----|-------|--------|------|-----------|
| [ADR-REST-001](./adr/ADR-REST-001-ERROR-HANDLING-STRATEGY.md) | RFC 7807-Inspired Error Response Format | Accepted | 2025-10-19 | REST API |
| [ADR-REST-002](./adr/ADR-REST-002-VALIDATION-PATTERN.md) | Early Validation Before Actor Communication | Accepted | 2025-10-19 | REST API |
| [ADR-REST-003](./adr/ADR-REST-003-PEKKO-HTTP-TECHNOLOGY.md) | Apache Pekko HTTP 1.1.0 Technology Choice | Accepted | 2025-10-19 | REST API |

### Service Layer Architecture

| ADR | Title | Status | Date | Component |
|-----|-------|--------|------|-----------|
| [ADR-VAULT-001](./adr/ADR-VAULT-001-rosetta-mapping-pattern.md) | Rosetta Bidirectional Mapping Pattern | Accepted | 2025-10-23 (Updated) | Vault Services, RequestBodyBuilder |
| [ADR-STORAGE-001](./adr/ADR-STORAGE-001-block-storage-abstraction.md) | Block Storage Abstraction Pattern | Accepted | 2025-10-20 | Block Storage Services |
| [ADR-STORAGE-002](./adr/ADR-STORAGE-002-function-passing-pattern.md) | Function Passing vs Service Objects | Accepted | 2025-10-20 | Service Layer Integration |
| [ADR-STORAGE-003](./adr/ADR-STORAGE-003-jimfs-state-management.md) | jimfsLocation State Management | Accepted | 2025-10-20 | BlockStorageActor |
| [ADR-STORAGE-004](./adr/ADR-STORAGE-004-option-resolution-strategy.md) | Option Resolution Strategy | Accepted | 2025-10-20 | BlockStorageActor |
| [ADR-STORAGE-005](./adr/ADR-STORAGE-005-block-storage-abstraction-v2.md) | Block Storage Abstraction (v2 - Comprehensive) | Accepted | 2025-10-27 | Block Storage Builder Modules |
| [ADR-STORAGE-006](./adr/ADR-STORAGE-006-sdk-streaming-approach.md) | SDK-Native Streaming Approach | Accepted | 2025-10-27 | Block Storage Services |
| [ADR-STORAGE-007](./adr/ADR-STORAGE-007-jimfs-architecture.md) | JIMFS Architecture and Lifecycle Management | Accepted | 2025-10-27 | Block Storage Services |
| [ADR-STORAGE-008](./adr/ADR-STORAGE-008-topic-directive-format.md) | Topic Directive YAML Format | Accepted | 2025-10-27 | Block Storage Services |
| [ADR-STORAGE-009](./adr/ADR-STORAGE-009-async-client-strategy.md) | Async Client Strategy for Block Storage Providers | Accepted | 2025-10-27 | AWS/Azure Block Storage |

### Planned ADRs

The following ADRs are referenced but not yet created:
- ADR 006: QueueActor Design and Queue Management Strategy
- ADR 007: Dual JSON Serialization Strategy (Spray vs Circe)
- ADR 008: BDD Component Testing with Cucumber and Akka TestKit
- ADR 009: Service Layer Design and DSL Patterns

---

## Blueprint Documentation

Blueprints provide detailed technical documentation for specific components and subsystems.

### 01 Security

**Status:** Active - Complete security documentation **NEW 2025-11-26**
**Documents:**
- [01 Security Overview](./blueprint/01%20Security/01-security-overview.md) - **NEW** Security architecture, defense-in-depth, threat model
- [01.1 Vault Integration](./blueprint/01%20Security/01.1-vault-integration.md) - **NEW** AWS/Azure/GCP vault services, Rosetta mapping
- [01.2 Kafka Authentication](./blueprint/01%20Security/01.2-kafka-authentication.md) - **NEW** SASL_SSL, JAAS config, OAuth token acquisition

**Key Topics Covered:**
- 4 security layers (Infrastructure, Vault, Kafka Auth, Kafka ACLs)
- OAuth 2.0 Client Credentials flow
- Cloud provider IAM integrations
- Local testing with jimfs vault

### 02 Booting

**Status:** Active - GuardianActor fully documented
**Documents:**
- [02.1 GuardianActor](./blueprint/02%20Booting/02.1-guardian-actor.md) - Root supervisor, Error Kernel pattern, actor system initialization

**Planned:**
- 02.2 Boot Sequence and Builder Pattern
- 02.3 Configuration Loading and Validation

**Key Diagrams:**

1. **Actor Hierarchy** (Mermaid)
   - GuardianActor as root supervisor
   - QueueActor spawn and supervision
   - Location: [02.1-guardian-actor.md](./blueprint/02%20Booting/02.1-guardian-actor.md#architecture)

2. **Bootstrap Sequence** (Mermaid)
   - ActorSystem creation to service layer setup
   - Initialize and GetQueueActor message flow
   - Location: [02.1-guardian-actor.md](./blueprint/02%20Booting/02.1-guardian-actor.md#bootstrap-sequence)

3. **State Transitions** (Mermaid)
   - Uninitialized → Initialized → Degraded states
   - Idempotency handling
   - Location: [02.1-guardian-actor.md](./blueprint/02%20Booting/02.1-guardian-actor.md#state-transitions)

4. **Supervision Flow** (Mermaid)
   - QueueActor restart and termination handling
   - Restart limit enforcement
   - Location: [02.1-guardian-actor.md](./blueprint/02%20Booting/02.1-guardian-actor.md#supervision-flow)

**Related Test Documentation:**
- BDD Feature File: `test-probe-core/src/test/resources/features/component/actor-lifecycle/guardian-actor.feature`
- Step Definitions: `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/GuardianActorSteps.scala`
- Unit Tests: `test-probe-core/src/test/scala/com/company/probe/core/actors/GuardianActorSpec.scala`

### 03 APIs

**Status:** Active - REST API fully implemented and documented (Phase 1-4 complete)
**Documents:**

**Architecture & Design:**
- [REST API Architecture](blueprint/03 APIs/03.1 REST API/03.1-rest-api-architecture.md) - Complete architecture overview, technology stack, design patterns **NEW 2025-10-19**
- [REST Error Handling](blueprint/03 APIs/03.1 REST API/03.1-rest-error-handling.md) - Exception and rejection handling, error response format **NEW 2025-10-19**
- [REST API Endpoints](blueprint/03 APIs/03.1 REST API/03.1-rest-api-endpoints.md) - Complete endpoint reference with examples **NEW 2025-10-19**
- [REST Timeouts & Resilience](blueprint/03 APIs/03.1 REST API/03.1-rest-timeouts-resilience.md) - Timeout hierarchy, circuit breaker, graceful shutdown **NEW 2025-10-19**
- [OpenAPI Specification Guide](../api/rest-api-openapi-specification.md) - How to use and update the API spec **NEW 2025-10-19**
- [REST Testing Strategy](blueprint/03 APIs/03.1 REST API/03.1-rest-testing-strategy.md) - Unit testing approach with mock patterns **NEW 2025-10-19**

**Blueprint Documentation:**
- [03.1.1 Interfaces Module Architecture](./blueprint/03%20APIs/03.1%20REST%20API/interfaces-module/03.1.1-interfaces-module-architecture.md) - Complete hexagonal architecture design, module structure, anti-corruption layer
- [03.1.2 Interfaces Implementation Plan](./blueprint/03%20APIs/03.1%20REST%20API/interfaces-module/03.1.2-interfaces-implementation-plan.md) - Full 7-phase implementation plan with file structure
- [03.1.3 Interfaces Test Coverage](./blueprint/03%20APIs/03.1%20REST%20API/interfaces-module/03.1.3-interfaces-test-coverage.md) - Comprehensive BDD scenario breakdown and coverage analysis

**Implementation Summary:**
- Maven module: `test-probe-interfaces` (2,400+ lines across 25+ files)
- REST API: Apache Pekko HTTP 1.1.0 with OpenAPI 3.0.3 specification
- Anti-Corruption Layer: Transforms external API models (kebab-case) to internal core models (camelCase)
- Circuit Breaker: Fail-fast pattern with 5-failure threshold, 30s reset timeout
- Error Handling: RFC 7807-inspired error responses with consistent format
- Testing: 186 tests (122 existing + 64 new), 100% success rate

**Key Diagrams:**

1. **REST API Request Flow** (Mermaid)
   - Shows: Complete request flow from client → Pekko HTTP → validation → actor ask → response
   - Includes: Success path, validation errors, timeout errors, circuit breaker open scenarios
   - Location: [diagrams/03-apis/rest-api-request-flow.mermaid](diagrams/03-apis/rest-api-request-flow.mermaid) **NEW**

2. **Error Handling Flow** (Mermaid)
   - Shows: Exception and rejection handling paths, HTTP status code mapping
   - Includes: All error types, logging points, response formatting
   - Location: [diagrams/03-apis/rest-error-handling-flow.mermaid](diagrams/03-apis/rest-error-handling-flow.mermaid) **NEW**

3. **Module Dependencies** (Mermaid)
   - Shows: `interfaces` → `core` → `common` dependency chain, Pekko framework integration
   - Highlights: Hexagonal architecture, anti-corruption layer, clean boundaries
   - Location: [diagrams/03-apis/rest-module-dependencies.mermaid](diagrams/03-apis/rest-module-dependencies.mermaid) **NEW**

4. **Circuit Breaker Integration** (Mermaid)
   - Shows: Closed → Open → Half-Open state transitions with timeline example
   - Highlights: Fail-fast behavior, auto-recovery, REST response mapping
   - Location: [diagrams/03-apis/rest-circuit-breaker.mermaid](diagrams/03-apis/rest-circuit-breaker.mermaid) **NEW**

5. **Module Dependency Flow** (Mermaid)
   - Shows: `common` <- `core` <- `interfaces` dependency chain
   - Highlights: No circular dependencies, clean hexagonal architecture
   - Location: [03.1.1-interfaces-module-architecture.md](./blueprint/03%20APIs/03.1%20REST%20API/interfaces-module/03.1.1-interfaces-module-architecture.md#module-structure)

6. **REST API Component Structure** (Mermaid)
   - Shows: Client -> Routes -> Handlers -> Service Functions -> QueueActor
   - Highlights: Anti-corruption layer, function currying pattern
   - Location: [03.1.1-interfaces-module-architecture.md](./blueprint/03%20APIs/03.1%20REST%20API/interfaces-module/03.1.1-interfaces-module-architecture.md#rest-interface-architecture)

7. **Builder Integration Flow** (Mermaid)
   - Shows: ProbeBuilder with phantom types -> RestInterface initialization
   - Highlights: Type-safe configuration, compile-time validation
   - Location: [03.1.1-interfaces-module-architecture.md](./blueprint/03%20APIs/03.1%20REST%20API/interfaces-module/03.1.1-interfaces-module-architecture.md#integration-with-builder-pattern)

**REST Endpoints:**
- `GET /api/v1/health` - Health check (actor system responsiveness)
- `POST /api/v1/test/initialize` - Create new test execution context
- `POST /api/v1/test/start` - Start test execution with S3 bucket path
- `GET /api/v1/test/{testId}/status` - Get test execution status
- `GET /api/v1/queue/status` - Get queue status (all tests or filtered by testId)
- `DELETE /api/v1/test/{testId}` - Cancel test execution
- OpenAPI spec: `test-probe-interfaces/src/main/resources/openapi.yaml` (700+ lines)

**Anti-Corruption Layer:**
- REST Models: JSON-serializable DTOs with kebab-case naming (Spray JSON)
- Core Models: Rich domain objects with camelCase naming in `test-probe-core`
- RestModelConversions: Bidirectional transformations (REST ↔ Core)
- Error Mapping: Typed exceptions (ServiceTimeoutException, etc.) → HTTP status codes

**Related Test Documentation:**
- Unit Tests: 186 tests (100% success rate)
  - RestRoutesHealthCheckSpec.scala (280 lines, 10 tests)
  - RestRoutesErrorHandlingSpec.scala (704 lines, 27 tests)
  - RestValidationSpec.scala (247 lines, 19 tests)
  - RestErrorResponseSpec.scala (360 lines, 18 tests)
- Component Tests: @Pending (waiting for test-probe-boot module)

**Future Extensibility:**
- CLI Interface: Planned in `03.3 CLI/` (uses same service functions)
- gRPC Interface: Planned in `03.2 gRPC/` (separate anti-corruption layer)
- WebSocket: Potential future addition for real-time test status updates

**Planned:**
- 03.2 gRPC Interface Design
- 03.3 CLI Interface Design
- 03.4 API Versioning Strategy
- 03.5 Authentication and Authorization

### 04 Adapters

**Status:** Active - Service Layer, Block Storage, Cucumber, and Vault services implemented
**Documents:**
- [04.1 Service Layer Architecture](./blueprint/04%20Adapters/04.1-service-layer-architecture.md) - Complete service layer pattern documentation **UPDATED 2025-10-20**
- [04.1 Block Storage Architecture](./blueprint/04%20Adapters/04.1-block-storage-architecture.md) - Block storage builder modules, async optimization, provider implementations **NEW 2025-10-27**
- [04.2.1 Vault Service Integration](./blueprint/04%20Adapters/04.2%20Vault%20Key%20Store/04.2.1-vault-service-integration.md) - RequestBodyBuilder, bidirectional vault architecture **NEW 2025-10-23**

**Implementation Summary:**
- **Service Layer Pattern**: Curried functions passed through ServiceFunctionsContext
- **Block Storage Services**: 4 implementations (jimfs complete, S3/Azure/GCS skeletons)
  - LocalBlockStorageService: Full jimfs implementation (240 lines)
  - AwsBlockStorageService: S3 skeleton with comprehensive TODOs (330 lines)
  - AzureBlockStorageService: Azure Blob skeleton with comprehensive TODOs (330 lines)
  - GcpBlockStorageService: GCS skeleton with comprehensive TODOs (340 lines)
- **Cucumber Service Layer**: **COMPLETE** (846 lines, 70+ tests, real Cucumber execution)
  - TestExecutionEventListener: Event-based result collection (237 lines)
  - TestExecutionListenerRegistry: ThreadLocal registry for plugin instantiation (120 lines)
  - CucumberConfiguration: Type-safe configuration builder (332 lines)
  - CucumberExecutor: Main.run() CLI integration (157 lines)
  - Framework glue package: io.distia.probe.core.glue (75 lines)
- **Vault Services**: **Bidirectional architecture complete** (request + response)
  - RequestBodyBuilder: Template variable substitution (150 lines, 3 variable patterns)
  - VaultCredentialsMapper: JSONPath response mapping
  - JaasConfigBuilder: JAAS config construction
  - AzureVaultService: Azure Function App integration (invokeVault implemented)
  - Security: request-params.* namespace enforcement
- **Integration Pattern**: Service → Curried Functions → ServiceFunctionsContext → Actor Consumption
- **BlockStorageActor**: Complete refactor with pipeToSelf pattern (340 lines, 20 tests)
- **CucumberExecutionActor**: **Real execution complete** (312 lines, pipeToSelf + cucumber-blocking-dispatcher)

**Key Diagrams:**

1. **Service Integration Flow** (Mermaid)
   - Shows: ServiceDsl → DefaultActorSystem → Function Extraction → Actor Hierarchy
   - Highlights: Curried function pattern, ServiceFunctionsContext bundling
   - Location: [04.1-service-layer-architecture.md](./blueprint/04%20Adapters/04.1-service-layer-architecture.md#architecture-pattern)

**Implementation Files:**
- Service Trait: `test-probe-core/src/main/scala/com/company/probe/core/builder/modules/package.scala`
- Function Bundles: `test-probe-core/src/main/scala/com/company/probe/core/builder/*ServiceFunctions.scala`
- Service Implementations: `test-probe-services/src/main/scala/com/company/probe/services/builder/modules/`
  - LocalBlockStorageService.scala (240 lines)
  - AwsBlockStorageService.scala (330 lines)
  - AzureBlockStorageService.scala (330 lines)
  - GcpBlockStorageService.scala (340 lines)
- Test Helpers: `test-probe-core/src/test/scala/com/company/probe/core/helpers/ServiceFunctionsTestHelper.scala`

**Related ADRs:**
- [ADR-VAULT-001: Rosetta Bidirectional Mapping Pattern](./adr/ADR-VAULT-001-rosetta-mapping-pattern.md) **UPDATED 2025-10-23**
- [ADR-STORAGE-001: Block Storage Abstraction (v1)](./adr/ADR-STORAGE-001-block-storage-abstraction.md)
- [ADR-STORAGE-002: Function Passing Pattern](./adr/ADR-STORAGE-002-function-passing-pattern.md)
- [ADR-STORAGE-003: jimfsLocation State Management](./adr/ADR-STORAGE-003-jimfs-state-management.md)
- [ADR-STORAGE-004: Option Resolution Strategy](./adr/ADR-STORAGE-004-option-resolution-strategy.md)
- [ADR-STORAGE-005: Block Storage Abstraction (v2 - Comprehensive)](./adr/ADR-STORAGE-005-block-storage-abstraction-v2.md) **NEW 2025-10-27**
- [ADR-STORAGE-006: SDK Streaming Approach](./adr/ADR-STORAGE-006-sdk-streaming-approach.md) **NEW 2025-10-27**
- [ADR-STORAGE-007: JIMFS Architecture](./adr/ADR-STORAGE-007-jimfs-architecture.md) **NEW 2025-10-27**
- [ADR-STORAGE-008: Topic Directive Format](./adr/ADR-STORAGE-008-topic-directive-format.md) **NEW 2025-10-27**
- [ADR-STORAGE-009: Async Client Strategy](./adr/ADR-STORAGE-009-async-client-strategy.md) **NEW 2025-10-27**

**Vault Integration Documentation:**
- [Bidirectional Flow Diagram](./diagrams/sequences/rosetta-bidirectional-flow.mermaid) - Request + response sequence **NEW 2025-10-23**
- [Component Diagram](./diagrams/components/rosetta-vault-mapping-components.mermaid) - Rosetta architecture
- [Response Flow Diagram](./diagrams/sequences/rosetta-vault-mapping-sequence.mermaid) - Response mapping detail
- [Rosetta API Reference](../api/rosetta-vault-mapping-api.md) - Complete API documentation (v2.1) **UPDATED 2025-10-23**
- [Rosetta Examples](../examples/rosetta/) - YAML templates, application.conf examples, README **NEW 2025-10-23**

**Block Storage Documentation:**
- [Topic Directive Model API](../api/topic-directive-model.md) - Complete YAML format specification **NEW 2025-10-27**
- [Block Storage Architecture](./blueprint/04%20Adapters/04.1-block-storage-architecture.md) - Builder modules, async optimization **NEW 2025-10-27**

**Working Documents:**
- Implementation Summary: `working/storage/STORAGE-IMPLEMENTATION-SUMMARY.md`
- Architecture Changes: `working/storage/ARCHITECTURE-CHANGES.md`
- File Changes Reference: `working/storage/FILE-CHANGES-REFERENCE.md`

**Planned:**
- 04.3 Kafka Adapter (streaming layer complete)
- 04.4 Cucumber Adapter (future phase)

### 05 State Machine

**Status:** Active - TestExecutionActor and all child actors fully documented
**Documents:**
- [05.1 TestExecutionActor FSM](./blueprint/05%20State%20Machine/05.1-test-execution-actor-fsm.md) - Complete FSM design, states, transitions, message protocols
- [05.2 TestExecutionActor Error Handling](./blueprint/05%20State%20Machine/05.2-test-execution-actor-error-handling.md) - Exception handling, error propagation, recovery strategies
- [05.3 Child Actors Integration](./blueprint/05%20State%20Machine/05.3-child-actors-integration.md) - All 5 child actors, parent reference pattern, security compliance

**Planned:**
- 05.4 QueueActor FSM and Queue Management
- 05.5 Actor Message Protocols and Integration Patterns

**Key Diagrams:**

1. **State Transition Diagram** (Mermaid)
   - All 7 states: Setup, Loading, Loaded, Testing, Completed, Exception, ShuttingDown
   - Valid transitions and triggering events
   - Location: [05.1-test-execution-actor-fsm.md](./blueprint/05%20State%20Machine/05.1-test-execution-actor-fsm.md#state-transition-diagram)

2. **Message Flow Sequence** (Mermaid)
   - Interaction between QueueActor, TestExecutionActor, and 5 child actors
   - Initialization sequence with dependencies
   - Location: [05.1-test-execution-actor-fsm.md](./blueprint/05%20State%20Machine/05.1-test-execution-actor-fsm.md#child-actor-orchestration)

3. **Error Propagation Flow** (Mermaid)
   - Exception bubbling from child actors
   - Supervision strategy and exception translation
   - Location: [05.2-test-execution-actor-error-handling.md](./blueprint/05%20State%20Machine/05.2-test-execution-actor-error-handling.md#error-propagation-flow)

**Related Test Documentation:**
- BDD Feature File: `test-probe-core/src/test/resources/features/component/actor-lifecycle/test-execution-actor-fsm.feature`
- Step Definitions: `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/TestExecutionActorSteps.scala`
- Test Fixtures: `test-probe-core/src/test/scala/com/company/probe/core/fixtures/TestExecutionActorFixtures.scala`

### 06 Triggers

**Status:** Active - Complete trigger documentation **NEW 2025-11-26**
**Documents:**
- [06 Triggers Overview](./blueprint/06%20Triggers/06-triggers-overview.md) - **NEW** Trigger types, adapter pattern, unified architecture
- [06.1 REST API Triggers](./blueprint/06%20Triggers/06.1-rest-api-triggers.md) - **NEW** Complete endpoint reference, request/response flow

**Key Topics Covered:**
- REST API triggers (current implementation)
- Planned triggers: CLI, Event-Driven, Scheduled, Webhook
- Integration examples (GitHub Actions, Jenkins, curl)

### 07 Models

**Status:** Active - Complete models documentation **NEW 2025-11-26**
**Documents:**
- [07 Models Overview](./blueprint/07%20Models/07-models-overview.md) - **NEW** Domain model architecture, immutability principles
- [07.1 Probe Testing Models](./blueprint/07%20Models/07.1-probe-testing-models.md) - **NEW** TopicDirective, BlockStorageDirective, KafkaSecurityDirective
- [07.2 Event Models](./blueprint/07%20Models/07.2-event-models.md) - **NEW** CloudEvent structure, serialization formats, schema evolution

**Key Topics Covered:**
- 4 model categories (Probe Testing, Events, Actor Commands, REST API)
- Immutable data patterns with copy-on-update
- Serialization strategies (Circe, Spray JSON, Confluent)

### 08 Test Flow

**Status:** Active - QueueActor fully documented
**Documents:**
- [08.1.1 QueueActor Blueprint](./blueprint/08%20Test%20Flow/08.1%20Queuing%20Tests/08.1.1-queue-actor.md) - Test queue management, FIFO scheduling, state tracking
- [08.1.2 QueueActor Message Routing](./blueprint/08%20Test%20Flow/08.1%20Queuing%20Tests/08.1.2-queue-actor-message-routing.md) - Message routing patterns, service command forwarding

**Planned:**
- 08.2 Kafka Producer to Consumer Flow
- 08.3 Kafka Consumer Patterns
- 08.4 REST Request Handling
- 08.5 gRPC Request Handling

**Key Diagrams:**

1. **Test Initialization Flow** (Mermaid)
   - Service → QueueActor → TestExecutionActor interaction
   - UUID generation and actor spawning
   - Location: [08.1.1-queue-actor.md](./blueprint/08%20Test%20Flow/08.1%20Queuing%20Tests/08.1.1-queue-actor.md#test-initialization-flow)

2. **FIFO Processing Flow** (Mermaid)
   - Queue processing algorithm
   - TestLoaded → processQueue → StartTesting sequence
   - Location: [08.1.1-queue-actor.md](./blueprint/08%20Test%20Flow/08.1%20Queuing%20Tests/08.1.1-queue-actor.md#fifo-execution-flow)

3. **Full Test Lifecycle** (Mermaid)
   - Complete message flow from initialization to cleanup
   - All FSM communications with QueueActor
   - Location: [08.1.2-queue-actor-message-routing.md](./blueprint/08%20Test%20Flow/08.1%20Queuing%20Tests/08.1.2-queue-actor-message-routing.md#full-test-lifecycle-message-flow)

**Related Test Documentation:**
- BDD Feature File: `test-probe-core/src/test/resources/features/component/actor-lifecycle/queue-actor.feature`
- Step Definitions: `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/QueueActorSteps.scala`
- Test Fixtures: `test-probe-core/src/test/scala/com/company/probe/core/fixtures/QueueActorFixtures.scala`

### 09 CI/CD

**Status:** Active - Maven Build Patterns documented
**Documents:**
- [09.5.1 Maven Test Filtering Architecture](./blueprint/09%20CICD/09.5%20Maven%20Build%20Patterns/09.5.1-maven-test-filtering-architecture.md) - Complete Maven test filtering implementation with profile-based execution control

**Implementation Summary:**
- Profile-based test filtering (`unit-only`, `component-only`, `coverage-aggregate`)
- Property-driven plugin configuration (`skipUnitTests`, `skipComponentTests`)
- Module dependency handling with `-am` flag for correct build order
- 3-tier testing pyramid support (70% unit, 20% component, 10% SIT)
- Fast feedback loop (unit tests ~5-10s vs full suite ~5-10min)

**Key Diagrams:**

1. **Maven Build Architecture** (ASCII)
   - Parent POM properties and profiles flow to module POMs
   - Plugin configuration (ScalaTest + Surefire)
   - Test execution separation (unit vs component)
   - Location: [09.5.1-maven-test-filtering-architecture.md](./blueprint/09%20CICD/09.5%20Maven%20Build%20Patterns/09.5.1-maven-test-filtering-architecture.md#architecture-diagram)

**Commands Quick Reference:**
```bash
# Unit tests only - fast feedback (~5-10s)
./mvnw test -Punit-only -pl test-probe-core -am

# Component tests only - full integration (~4-5min)
./mvnw test -Pcomponent-only -pl test-probe-core -am

# Aggregate coverage across all modules
./mvnw clean test scoverage:report -Pcoverage-aggregate
```

**Related Documentation:**
- Command Reference: `maven-commands-reference.md` (root directory)
- Build Guide: `.claude/guides/BUILD.md`
- Testing Standards: `.claude/guides/TESTING.md`

**Planned:**
- 09.1 CI Pipeline Configuration (GitHub Actions, GitLab CI)
- 09.2 Deployment Strategy (Docker, Kubernetes)
- 09.3 Release Management (Semantic Versioning, Changelog)
- 09.4 Artifact Publishing (Maven Central, Docker Hub)

### 10 Kafka Streaming

**Status:** Complete - Full Kafka streaming layer with serialization & DSL architecture
**Documents:**
- [10.1 Kafka Streaming Architecture](./blueprint/10%20Kafka%20Streaming/10.1-kafka-streaming-architecture.md) - Complete end-to-end Kafka integration with DSL layer
- [10.2 Serdes & DSL Architecture](./blueprint/10%20Kafka%20Streaming/10.2%20Serialization/10.2-serdes-dsl-architecture.md) - **NEW 2025-11-22** Multi-format serialization (Avro, Protobuf, JSON Schema), ProbeScalaDsl thread safety
- [ProbeScalaDsl API Reference](../api/probe-scala-dsl-api.md) - **NEW 2025-11-22** Complete DSL API documentation with Cucumber examples

**Implementation Summary**:
- Supervisor-Streaming actor separation pattern (isolation, scalability, testability)
- DSL registry pattern for thread-safe Cucumber access
- Custom materializer + control.stop for fast shutdown
- Blocking dispatcher for Schema Registry HTTP calls
- Per-message stream pattern (deferred optimization)
- Event filtering + batched commits (consumer resilience)
- Layered error handling (producer NACKs, consumer skips)
- **Multi-format SerdesFactory**: Automatic Avro/Protobuf/JSON Schema dispatch
- **CloudEvent key encapsulation**: Transparent conversion for all formats
- **TopicRecordNameStrategy**: Polymorphic events support (oneOf unions)

**Key Diagrams:**

1. **Component Hierarchy** (Mermaid)
   - TestExecutionActor → Supervisor → Streaming actors hierarchy
   - DSL registry integration with ConcurrentHashMap
   - Schema Registry and Kafka broker connections
   - Location: [10.1-kafka-streaming-architecture.md](./blueprint/10%20Kafka%20Streaming/10.1-kafka-streaming-architecture.md#component-hierarchy)

2. **Producer Flow Sequence** (Mermaid)
   - Cucumber → ProbeScalaDsl → KafkaProducerStreamingActor → Kafka
   - Schema Registry encoding on blocking dispatcher
   - Ask pattern with ProducedAck/Nack responses
   - Location: [10.1-kafka-streaming-architecture.md](./blueprint/10%20Kafka%20Streaming/10.1-kafka-streaming-architecture.md#producer-flow)

3. **Consumer Flow Sequence** (Mermaid)
   - Kafka → KafkaConsumerStreamingActor → Registry → ProbeScalaDsl → Cucumber
   - Schema Registry decoding on blocking dispatcher
   - Event filtering by testId and eventType/version
   - Batched offset commits (20 per batch)
   - Location: [10.1-kafka-streaming-architecture.md](./blueprint/10%20Kafka%20Streaming/10.1-kafka-streaming-architecture.md#consumer-flow)

4. **Full Integration Flow** (Mermaid)
   - Complete lifecycle from TestExecutionActor initialization to shutdown
   - Actor spawning, DSL registration, test execution, cleanup
   - Location: [10.1-kafka-streaming-architecture.md](./blueprint/10%20Kafka%20Streaming/10.1-kafka-streaming-architecture.md#full-integration-flow)

5. **SerdesFactory Class Diagram** (Mermaid) **NEW 2025-11-22**
   - Schema type dispatch architecture
   - CloudEvent converters (Avro, Protobuf)
   - ScalaConfluentJson serializers
   - Location: [serdes-factory-class-diagram.mermaid](../diagrams/10-kafka-streaming/serdes-factory-class-diagram.mermaid)

6. **ProbeScalaDsl Sequence Diagram** (Mermaid) **NEW 2025-11-22**
   - System initialization and Schema Registry client setup
   - Producer/Consumer actor registration flow
   - Produce event flow with serialization on blocking dispatcher
   - Fetch consumed event flow with deserialization
   - Location: [probe-scala-dsl-sequence-diagram.mermaid](../diagrams/10-kafka-streaming/probe-scala-dsl-sequence-diagram.mermaid)

7. **DSL Data Flow Diagram** (Mermaid) **NEW 2025-11-22**
   - End-to-end data flow from Cucumber to Kafka
   - Schema type dispatch decision tree
   - Blocking dispatcher separation
   - Location: [dsl-data-flow-diagram.mermaid](../diagrams/10-kafka-streaming/dsl-data-flow-diagram.mermaid)

**Related ADRs:**
- [ADR-KAFKA-001: Multiple Bootstrap Servers per Topic Directive](./adr/ADR-KAFKA-001-multiple-bootstrap-servers.md) - **NEW** Multi-cluster support, per-topic bootstrap servers, TopicDirectiveValidator
- [ADR-001: Consumer Registry Memory Management](../adr/ADR-001-Consumer-Registry-Memory-Management.md) - No cache eviction needed for bounded test scenarios
- [ADR-002: Producer Stream Performance Optimization](../adr/ADR-002-Producer-Stream-Performance-Optimization.md) - Defer persistent stream until smoke testing
- [ADR-003: Schema Registry Error Handling](../adr/ADR-003-Schema-Registry-Error-Handling.md) - Producer NACKs immediately, consumer skips malformed events
- [ADR-004: Consumer Stream Lifecycle Management](../adr/ADR-004-Consumer-Stream-Lifecycle-Management.md) - Custom materializer + control.stop for fast shutdown
- [ADR-SERDES-001: JSON Schema Serialization](./adr/ADR-SERDES-001-confluent-json-schema-serialization-strategy.md) - JsonNode pattern, ClassCastException fix, isKey parameter
- [ADR-SERDES-002: OneOf Polymorphic Events](./adr/ADR-SERDES-002-json-schema-oneof-polymorphic-events.md) - Event democratization, TopicRecordNameStrategy

**Implementation Files:**
- Supervisors: `KafkaProducerActor.scala` (237 lines), `KafkaConsumerActor.scala` (237 lines)
- Streaming: `KafkaProducerStreamingActor.scala` (89 lines), `KafkaConsumerStreamingActor.scala` (168 lines)
- DSL Layer: `ProbeScalaDsl.scala` (150 lines), `ProbeJavaDsl.scala` (stub)
- Serdes: `SerdesFactory.scala` (150 lines), `ScalaConfluentJsonSerializer.scala`, `ScalaConfluentJsonDeserializer.scala`
- Converters: `CloudEventAvroConverter.scala`, `CloudEventProtoConverter.scala`
- **New (ADR-KAFKA-001):**
  - Model: `TopicDirective.scala` (updated with `bootstrapServers: Option[String]` field)
  - Validation: `TopicDirectiveValidator.scala` (NEW - uniqueness & format validation)
  - Exceptions: `BlockStorageExceptions.scala` (updated with `DuplicateTopicException`, `InvalidBootstrapServersException`)
  - Test Infrastructure: `TestcontainersManager.scala` (updated with named cluster registry)
  - Test Fixtures: `TopicDirectiveFixtures.scala` (updated with `bootstrapServers` parameter)
- Total: 1000+ lines across 10+ files (updated for ADR-KAFKA-001)

**Working Documents:**
- Implementation Summary: `working/KafkaStreamingImplementationSummary-2025-10-16.md` (725 lines)
- Serdes Refactor Research: `working/serdesRefactor/` (Historical research documents)
  - `UNION-SCHEMA-STRATEGY.md` - Avro/Protobuf/JSON union patterns
  - `CLOUDEVENT-TERMINOLOGY-ARCHITECTURE.md` - CloudEvent field semantics
  - `AVRO-SPECIFICRECORD-ISSUE.md` - SpecificRecord vs GenericRecord
  - `CONFLUENT-8.0.2-JSON-SCHEMA-UPDATES.md` - Platform version research
  - `CONFLUENT-8.1.0-PRODUCTION-CODE-EXAMPLES.md` - Production code patterns
  - `KAFKA-CONSUMER-STREAM-INVESTIGATION.md` - Consumer stream debugging

---

## High-Level Architecture

**Status:** Active - Complete high-level documentation **NEW 2025-11-26**
**Documents:**
- [System Overview](./high-level/system-overview.md) - **NEW** C4 Context diagram, system boundaries, external dependencies
- [Component Architecture](./high-level/component-architecture.md) - **NEW** C4 Component diagram, module relationships, actor hierarchy
- [Deployment Architecture](./high-level/deployment-architecture.md) - **NEW** Standalone, Docker, Kubernetes options, scaling considerations

**Key Topics Covered:**
- C4 Model diagrams (Context, Component)
- External system integrations (Kafka, Schema Registry, Vault, Block Storage)
- Module dependency graph
- Resource requirements and scaling strategies
- Network architecture and firewall rules

---

## Implementation Files Reference

### GuardianActor

**Core Implementation:**
- Actor: `test-probe-core/src/main/scala/com/company/probe/core/actors/GuardianActor.scala` (266 lines)
- Commands: `test-probe-core/src/main/scala/com/company/probe/core/models/ActorCommands.scala`

**Testing:**
- Unit Tests: `test-probe-core/src/test/scala/com/company/probe/core/actors/GuardianActorSpec.scala` (18 tests, 90% coverage)
- BDD Feature: `test-probe-core/src/test/resources/features/component/actor-lifecycle/guardian-actor.feature` (6 scenarios)
- Step Definitions: `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/GuardianActorSteps.scala` (448 lines)

**Working Documents (Historical):**
- Implementation Plan: `working/GuardianActorImplementationPlan.md`
- Understanding Document: `working/GuardianActorUnderstanding.md`
- Definition: `working/guardianActorDefinition.md`

### QueueActor

**Core Implementation:**
- Actor: `test-probe-core/src/main/scala/com/company/probe/core/actors/QueueActor.scala` (444 lines)
- Commands: `test-probe-core/src/main/scala/com/company/probe/core/models/ActorCommands.scala`

**Testing:**
- Unit Tests: `test-probe-core/src/test/scala/com/company/probe/core/actors/QueueActorSpec.scala` (18 tests)
- BDD Feature: `test-probe-core/src/test/resources/features/component/actor-lifecycle/queue-actor.feature` (18 scenarios)
- Step Definitions: `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/QueueActorSteps.scala`
- Fixtures: `test-probe-core/src/test/scala/com/company/probe/core/fixtures/QueueActorFixtures.scala`

**Working Documents (Historical):**
- Implementation Plan: `working/QueueActorImplementationPlan.md`
- Understanding Document: `working/QueueActorUnderstanding.md`

### TestExecutionActor

**Core Implementation:**
- Actor: `test-probe-core/src/main/scala/com/company/probe/core/actors/TestExecutionActor.scala` (706 lines)
- States & Data: `test-probe-core/src/main/scala/com/company/probe/core/actors/TestExecutionActorStatesData.scala` (57 lines)
- Commands: `test-probe-core/src/main/scala/com/company/probe/core/models/ActorCommands.scala`

**7-State FSM:**
- Setup → Loading → Loaded → Testing → Completed | Exception → ShuttingDown → Stopped
- Coordinates 5 child actors (BlockStorage, Vault, Cucumber, KafkaProducer, KafkaConsumer)
- Sequential initialization: BlockStorage → Vault → Cucumber/Producer/Consumer (parallel)
- Deferred self-messages for deterministic state transitions
- Poison pill timers for Setup, Completed, and Exception states

**Testing:**
- Unit Tests: `test-probe-core/src/test/scala/com/company/probe/core/actors/TestExecutionActorSpec.scala` (83 tests)
- BDD Feature: `test-probe-core/src/test/resources/features/component/actor-lifecycle/test-execution-actor-fsm.feature` (471 lines)
- Step Definitions: `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/TestExecutionActorSteps.scala` (551 lines)
- World/Context: `test-probe-core/src/test/scala/com/company/probe/core/glue/world/ActorWorld.scala`
- Fixtures: `test-probe-core/src/test/scala/com/company/probe/core/fixtures/TestExecutionActorFixtures.scala`

**Child Actor Integration:**
- Factory injection pattern for all 5 child actors
- Parent reference passed to children via constructor
- Supervised with restart strategy on exceptions
- Coordinated shutdown in ShuttingDown state (fire-and-forget Stop messages)

**Working Documents (Historical):**
- Requirements: `working/TestExecutionActorFSMRequirements.md`
- State Diagram: `working/TestExecutionActor-StateTransitionDiagram.mermaid`
- Message Flow: `working/TestExecutionActor-MessageFlow.mermaid`
- Error Flow: `working/TestExecutionActor-ErrorPropogationRecoveryFlow.mermaid`
- Prompts: `working/TestExecutionSpecificPrompts.md`

### Child Actors

**Status:** All 5 COMPLETE (scaffolding phase)

All child actors spawned by TestExecutionActor are fully implemented with message passing protocols, exception handling, and parent reference pattern (Akka Typed).

**Implementation Phase**: Scaffolding complete - service integration marked with TODO for future phase.

#### BlockStorageActor

**Core Implementation:**
- Actor: `test-probe-core/src/main/scala/com/company/probe/core/actors/BlockStorageActor.scala` (191 lines)
- Commands: `BlockStorageCommands` in `test-probe-core/src/main/scala/com/company/probe/core/models/ActorCommands.scala`

**Responsibilities:**
- Stream test data from S3 bucket to jimfs local file system (stub)
- Upload test evidence from jimfs to S3 bucket (stub)
- Message protocol: Initialize → BlockStorageFetched, ChildGoodToGo | LoadToBlockStorage → BlockStorageUploadComplete

**Testing:**
- Unit Tests: `test-probe-core/src/test/scala/com/company/probe/core/actors/BlockStorageActorSpec.scala` (16 tests)
- BDD Feature: `test-probe-core/src/test/resources/features/component/child-actors/block-storage-actor.feature` (14 scenarios)
- Step Definitions: `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/BlockStorageActorSteps.scala`

**Working Documents (Historical):**
- Implementation Plan: `working/ChildActorsImplementationPlan.md`
- Definition: `working/childActorDefinitions.md`

#### VaultActor

**Core Implementation:**
- Actor: `test-probe-core/src/main/scala/com/company/probe/core/actors/VaultActor.scala` (162 lines)
- Commands: `VaultCommands` in `test-probe-core/src/main/scala/com/company/probe/core/models/ActorCommands.scala`

**Responsibilities:**
- Fetch client ID and client secret from Vault based on BlockStorageDirective (stub)
- Message protocol: Initialize → SecurityFetched, ChildGoodToGo
- Security: All credentials redacted in logs

**Testing:**
- Unit Tests: `test-probe-core/src/test/scala/com/company/probe/core/actors/VaultActorSpec.scala` (8 tests)
- BDD Feature: `test-probe-core/src/test/resources/features/component/child-actors/vault-actor.feature` (10 scenarios)
- Step Definitions: `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/VaultActorSteps.scala`

**Security:**
- Security warning banner in source file
- Credentials redacted in all log statements
- Exception messages do not contain sensitive data

#### CucumberExecutionActor

**Core Implementation:**
- Actor: `test-probe-core/src/main/scala/com/company/probe/core/actors/CucumberExecutionActor.scala` (312 lines) **UPDATED 2025-10-20**
- Commands: `CucumberExecutionCommands` in `test-probe-core/src/main/scala/com/company/probe/core/models/ActorCommands.scala`
  - Added: `TestExecutionComplete(result: Either[Throwable, TestExecutionResult])` for pipeToSelf pattern

**Responsibilities:**
- Initialize Cucumber test execution environment
- **Execute real Cucumber scenarios** using Main.run() CLI API (COMPLETE)
- Message protocol: Initialize → ChildGoodToGo | StartTest → (async) TestExecutionComplete → TestComplete
- Security: All credentials redacted in logs
- Thread isolation: cucumber-blocking-dispatcher (4 threads) prevents actor blocking

**Integration Pattern:**
- **PipeToSelf**: Future { CucumberExecutor.execute() }(cucumberBlockingEc) + pipeToSelf
- **ThreadLocal Registry**: Solves plugin no-arg constructor limitation
- **Event-Based Results**: ConcurrentEventListener accumulates statistics in real-time
- **Blocking Execution**: Main.run() blocks inside Future on dedicated dispatcher (30s-5min typical)

**Service Layer** (`test-probe-core/src/main/scala/com/company/probe/core/services/cucumber/`):
- TestExecutionEventListener.scala (237 lines) - Event-based result collection
- TestExecutionListenerRegistry.scala (120 lines) - ThreadLocal registry
- CucumberConfiguration.scala (332 lines) - Type-safe configuration builder
- CucumberExecutor.scala (157 lines) - Main.run() synchronous executor
- io.distia.probe.core.glue/package.scala (75 lines) - Framework glue package

**Testing:**
- Unit Tests: `test-probe-core/src/test/scala/com/company/probe/core/actors/CucumberExecutionActorSpec.scala` (12 tests)
- Service Tests: 70+ new tests (TestExecutionListenerRegistry: 14, CucumberConfiguration: 40+, CucumberExecutor: 20+)
- BDD Feature: `test-probe-core/src/test/resources/features/component/child-actors/cucumber-execution-actor.feature` (12 scenarios)
- Step Definitions: `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/CucumberExecutionActorSteps.scala`
- **Total**: 409/411 tests passing (99.5%)

**Configuration:**
- cucumber-blocking-dispatcher: 4 fixed threads in reference.conf
- Framework glue: io.distia.probe.core.glue (auto-merged with user glue)
- Default tag filter: "not @Ignore" (excludes framework stub files)

**Architecture Documentation:**
- [Cucumber Integration Architecture](../../working/cucumber/cucumber-architecture.md) - AS-BUILT (v2.0)
- ThreadLocal registry pattern, Main.run() vs RuntimeBuilder, event-based collection

**Security:**
- Security warning banner in source file
- Credentials redacted in all log statements

#### KafkaProducerActor

**Core Implementation:**
- Actor: `test-probe-core/src/main/scala/com/company/probe/core/actors/KafkaProducerActor.scala` (189 lines)
- Commands: `KafkaProducerCommands` in `test-probe-core/src/main/scala/com/company/probe/core/models/ActorCommands.scala`

**Responsibilities:**
- Configure Kafka producers with credentials (stub)
- StartTest is no-op stub (producers used by Cucumber scenarios)
- Message protocol: Initialize → ChildGoodToGo
- Security: All credentials redacted in logs

**Testing:**
- Unit Tests: `test-probe-core/src/test/scala/com/company/probe/core/actors/KafkaProducerActorSpec.scala` (12 tests)
- BDD Feature: `test-probe-core/src/test/resources/features/component/child-actors/kafka-producer-actor.feature` (8 scenarios)
- Step Definitions: `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/KafkaProducerActorSteps.scala`

**Security:**
- Security warning banner in source file
- Credentials redacted in all log statements

#### KafkaConsumerActor

**Core Implementation:**
- Actor: `test-probe-core/src/main/scala/com/company/probe/core/actors/KafkaConsumerActor.scala` (189 lines)
- Commands: `KafkaConsumerCommands` in `test-probe-core/src/main/scala/com/company/probe/core/models/ActorCommands.scala`

**Responsibilities:**
- Configure Kafka consumers with credentials (stub)
- StartTest is no-op stub (consumers used by Cucumber scenarios)
- Message protocol: Initialize → ChildGoodToGo
- Security: All credentials redacted in logs

**Testing:**
- Unit Tests: `test-probe-core/src/test/scala/com/company/probe/core/actors/KafkaConsumerActorSpec.scala` (12 tests)
- BDD Feature: `test-probe-core/src/test/resources/features/component/child-actors/kafka-consumer-actor.feature` (8 scenarios)
- Step Definitions: `test-probe-core/src/test/scala/com/company/probe/core/glue/steps/KafkaConsumerActorSteps.scala`

**Security:**
- Security warning banner in source file
- Credentials redacted in all log statements

**Parent Reference Pattern (All Child Actors):**
All child actors receive TestExecutionActor reference as constructor parameter (Akka Typed pattern):
```scala
def apply(testId: UUID, parentTea: ActorRef[TestExecutionCommand]): Behavior[Command]
```

TestExecutionActor factories updated to pass `ctx.self`:
```scala
def defaultBlockStorageFactory(testId: UUID): BlockStorageFactory = { ctx =>
  ctx.spawn(
    Behaviors.supervise(BlockStorageActor(testId, ctx.self))
      .onFailure[Exception](SupervisorStrategy.restart),
    s"block-storage-$testId"
  )
}
```

**Testing Summary:**
- Total Unit Tests: 56 tests across 5 actors
- Total BDD Scenarios: 52 scenarios across 5 features
- All tests passing (179/179 total including all actors)

---

## Testing Documentation

Comprehensive testing documentation is maintained separately:

### Testing Practice Guides

- [Testing Pyramid](../testing-practice/testing-pyramid.md) - Overall testing strategy, inverted pyramid anti-pattern **UPDATED 2025-11-22**
- [Unit Testing](../testing-practice/unit-testing.md) - ScalaTest patterns, visibility pattern for testability **UPDATED 2025-11-22**
- [Component Behavior Testing](../testing-practice/component-behavior-testing.md) - BDD with Cucumber, step quality guidelines **UPDATED 2025-11-22**
- [SIT Behavior Testing](../testing-practice/sit-behavior-testing.md) - System integration tests
- [Tools and Libraries](../testing-practice/tools-and-libraries.md) - Testing stack overview
- [Directory Structure](../testing-practice/directory-structure.md) - Test organization, fixture layers **UPDATED 2025-11-22**
- [Kafka Environment Setup](../testing-practice/kafka-env-setup.md) - Testcontainers and Kafka

### New Testing Practice Guides (2025-11-22)

- [Assertion Quality Guidelines](../testing-practice/assertion-quality-guidelines.md) - CRISP model, assertion anti-patterns, ScalaTest patterns **NEW**
- [Actor Testing Patterns](../testing-practice/actor-testing-patterns.md) - FSM testing, ActorTestKit vs BehaviorTestKit **NEW**
- [Test Data Builders Guide](../testing-practice/test-data-builders.md) - Builder patterns, factory methods, KafkaMessage abstraction **NEW**

### QueueActor Test Coverage

**Unit Tests:** 18 tests covering:
- Test initialization and spawning (2 tests)
- Message routing verification (4 tests)
- FIFO ordering validation (1 test)
- Single test execution enforcement (1 test)
- State management (2 tests)
- Cleanup operations (2 tests)
- Edge cases (5 tests)
- Helper methods (1 test)

**BDD Scenarios:** 18 scenarios covering:
- Test initialization (2 scenarios)
- Message routing (9 scenarios)
- FSM communication handling (10 scenarios)
- Edge cases (8 scenarios)
- Performance (2 scenarios)

### TestExecutionActor Test Coverage

**BDD Scenarios (471 lines):**
- 26 scenarios across 9 tags
- Tags: @Critical, @InitializeRequest, @StartRequest, @ExceptionHandling, @CancelRequests, @GetStatus, @TimerExpiry, @MessageIgnoring, @Edge

**Scenario Breakdown:**
- **Initialize/Start:** Basic lifecycle (6 scenarios)
- **Exception Handling:** Loading, Testing, Loaded states (3 scenarios)
- **Cancellation:** All states (7 scenarios)
- **GetStatus:** All states (6 scenarios)
- **Timer Expiry:** Setup, Loading, Completed, Exception (4 scenarios)
- **Edge Cases:** Invalid message handling (2 scenarios)

---

## Design Patterns Used

### Actor Patterns

1. **Finite State Machine (FSM)** - TestExecutionActor
   - See: [ADR 001](./adr/001-fsm-pattern-for-test-execution.md)
   - Reference: Akka Typed FSM https://doc.akka.io/docs/akka/current/typed/fsm.html

2. **Error Kernel / Supervision** - TestExecutionActor, QueueActor
   - See: [ADR 005](./adr/005-error-kernel-pattern.md)
   - Reference: Erlang Supervision Trees https://www.erlang.org/doc/design_principles/des_princ.html

3. **Self-Message Continuation** - All FSM actors
   - See: [ADR 002](./adr/002-self-message-continuation-pattern.md)
   - Ensures deterministic state transitions

4. **Factory Injection** - TestExecutionActor child actors
   - See: [ADR 004](./adr/004-factory-injection-for-child-actors.md)
   - Enables testability without DI framework

5. **Poison Pill Timer** - TestExecutionActor
   - See: [ADR 003](./adr/003-poison-pill-timer-pattern.md)
   - Prevents orphaned actors

### Domain Patterns

6. **Service Layer DSL** - EventRegistryDSL, ServiceDSL
   - Functional programming with implicit contexts
   - Composable operations

7. **Builder Pattern with Phantom Types** - BuilderContext
   - Type-safe configuration building
   - Compile-time validation of required fields

8. **Adapter Pattern** - Storage, Vault, Kafka adapters
   - Abstraction over external services
   - Pluggable implementations (S3, GCS, Azure)

---

## Maintenance Guidelines

### Updating This Index

When adding or modifying documentation:

1. **Add new ADRs:**
   - Use sequential numbering (ADR-NNN)
   - Include in ADR table with status, date, component
   - Link from relevant blueprint documents

2. **Add new blueprints:**
   - Place in appropriate category (01-09)
   - Use hierarchical numbering (e.g., 05.1, 05.2)
   - Update "Most Recently Updated" section
   - Add to relevant component section

3. **Update diagrams:**
   - Embed Mermaid diagrams in blueprint documents
   - Reference diagram location in index
   - Validate Mermaid syntax renders correctly

4. **Link cross-references:**
   - Use relative paths for internal links
   - Reference ADRs from blueprints
   - Reference blueprints from ADRs
   - Link to implementation files with line numbers when useful

### Documentation Standards

1. **ADR Format:**
   - Title: "ADR NNN: Decision Title"
   - Sections: Context, Decision, Consequences, Alternatives, Implementation Notes, Related Decisions, References
   - Include "Document History" at bottom

2. **Blueprint Format:**
   - Title: "Component Name - Topic"
   - Include: Last Updated, Status, Component path
   - Table of Contents for documents > 50 lines
   - Use Mermaid for diagrams
   - Code examples with syntax highlighting
   - Cross-reference related documents

3. **Mermaid Diagrams:**
   - Use appropriate diagram type (stateDiagram, sequenceDiagram, flowchart, C4)
   - Include notes for clarity
   - Keep diagrams focused (one concern per diagram)
   - Validate rendering with Mermaid Live Editor

4. **Code References:**
   - Use absolute paths from project root
   - Include line numbers for specific references
   - Keep references up-to-date with code changes

---

## Contributing

To contribute to architecture documentation:

1. **Propose new ADR:** Create PR with ADR-NNN-draft.md, discuss in PR
2. **Update blueprint:** Create PR with changes, update "Last Updated" date
3. **Update index:** Ensure this INDEX.md reflects all changes
4. **Validate diagrams:** Test Mermaid rendering before committing
5. **Review guidelines:** Follow [CLAUDE.md](../../CLAUDE.md) for AI-assisted documentation

---

## Document History

- 2025-11-22: **ADR-KAFKA-001: Multiple Bootstrap Servers COMPLETE** - Multi-cluster support for topic directives with per-topic bootstrap servers, validation, and test infrastructure
- 2025-11-22: **SerdesFactory & DSL Architecture COMPLETE** - Created 10.2 blueprint (10.2-serdes-dsl-architecture.md) with multi-format serialization
- 2025-11-22: Created ProbeScalaDsl API Reference (probe-scala-dsl-api.md) with complete producer/consumer documentation
- 2025-11-22: Created 3 Mermaid diagrams: SerdesFactory class diagram, ProbeScalaDsl sequence diagram, DSL data flow diagram
- 2025-11-22: Updated "10 Kafka Streaming" section with new 10.2 sub-schedule, diagrams, and API reference
- 2025-11-22: Documented thread safety considerations, CloudEvent converters (Avro, Protobuf), ScalaConfluentJson wrappers
- 2025-10-27: **Block Storage Architecture COMPLETE** - Deployed 5 comprehensive ADRs (STORAGE-005 through 009), topic directive API, blueprint architecture
- 2025-10-27: Added ADR-STORAGE-005 (Block Storage Abstraction v2 - Comprehensive), ADR-STORAGE-006 (SDK Streaming), ADR-STORAGE-007 (JIMFS Architecture)
- 2025-10-27: Added ADR-STORAGE-008 (Topic Directive Format), ADR-STORAGE-009 (Async Client Strategy for AWS/Azure)
- 2025-10-27: Created topic-directive-model.md API specification (complete YAML format documentation)
- 2025-10-27: Created 04.1-block-storage-architecture.md blueprint (builder modules, async optimization, all 4 providers)
- 2025-10-27: Updated INDEX.md with new ADRs, API docs, blueprint; updated "Most Recently Updated" section
- 2025-10-23: **Rosetta API v2.1 COMPLETE** - Comprehensive configuration loading, path resolution, and integration guide
- 2025-10-23: Updated [Rosetta API v2.1](../api/rosetta-vault-mapping-api.md) with 9 new sections (2,248 total lines)
- 2025-10-23: Added configuration flow, VaultConfig integration, path resolution examples, best practices
- 2025-10-23: Added error handling, testing guide, migration guides, and summary table
- 2025-10-23: **Vault Services COMPLETE** - Added RequestBodyBuilder and bidirectional vault architecture
- 2025-10-23: Created [04.2.1 Vault Service Integration](./blueprint/04%20Adapters/04.2%20Vault%20Key%20Store/04.2.1-vault-service-integration.md) blueprint
- 2025-10-23: Updated [ADR-VAULT-001](./adr/ADR-VAULT-001-rosetta-mapping-pattern.md) with request template building section
- 2025-10-23: Created [Bidirectional Flow Diagram](./diagrams/sequences/rosetta-bidirectional-flow.mermaid) showing request + response
- 2025-10-23: Moved rosetta examples from working/ to [docs/examples/rosetta/](../examples/rosetta/)
- 2025-10-23: Updated "04 Adapters" section with vault services documentation
- 2025-10-23: Added ADR-VAULT-001 to Service Layer Architecture ADR table
- 2025-10-20: **Cucumber Integration COMPLETE** - Added Cucumber service layer (846 lines, 70+ tests, 409/411 passing)
- 2025-10-20: Updated CucumberExecutionActor documentation - real execution complete with PipeToSelf pattern
- 2025-10-20: Added Cucumber Architecture Documentation link (working/cucumber/cucumber-architecture.md AS-BUILT v2.0)
- 2025-10-20: Documented ThreadLocal registry pattern, Main.run() approach, event-based result collection
- 2025-10-20: Updated "04 Adapters" with Cucumber service layer details (TestExecutionEventListener, Registry, Configuration, Executor, Glue)
- 2025-10-20: Added Service Layer Architecture blueprint (04.1-service-layer-architecture.md)
- 2025-10-20: Added 4 storage ADRs (ADR-STORAGE-001 through ADR-STORAGE-004)
- 2025-10-20: Updated BlockStorageActor documentation in 05.3-child-actors-integration.md (service integration complete)
- 2025-10-20: Updated section "04 Adapters" from placeholder to Active status with full implementation details
- 2025-10-20: Added block storage service implementations (jimfs + 3 cloud skeletons, 1,240 total lines)
- 2025-10-20: Documented curried function pattern and ServiceFunctionsContext integration
- 2025-10-19: Added Maven Test Filtering Architecture blueprint (09.5.1-maven-test-filtering-architecture.md)
- 2025-10-19: Documented profile-based test filtering, module dependency handling with -am flag, coverage-aggregate profile
- 2025-10-19: Updated CI/CD section (09) from placeholder to Active status with comprehensive build patterns documentation
- 2025-10-19: Added Maven Build Architecture ASCII diagram, technical decisions rationale, troubleshooting guide
- 2025-10-19: Updated "Most Recently Updated" and "For AI Assistants" sections with Maven build patterns link
- 2025-10-19: Added complete Interfaces module documentation (blueprint/03 APIs/03.1 REST/interfaces-module)
- 2025-10-19: Added 3 comprehensive documents: Architecture (03.1.1), Implementation Plan (03.1.2), Test Coverage (03.1.3)
- 2025-10-19: Documented hexagonal architecture, anti-corruption layer, REST API endpoints, builder integration
- 2025-10-19: Updated "Most Recently Updated" and "For AI Assistants" sections with interfaces module links
- 2025-10-19: Updated section "03 APIs" from placeholder to Active status with full implementation details
- 2025-10-16: Added complete Kafka streaming architecture documentation (blueprint/10 Kafka Streaming)
- 2025-10-16: Added 4 Kafka ADRs (ADR-001 through ADR-004: Consumer Registry, Producer Stream, Schema Registry Error Handling, Consumer Stream Lifecycle)
- 2025-10-16: Created comprehensive Kafka Streaming Architecture blueprint (10.1) with 3 sequence diagrams
- 2025-10-16: Updated INDEX.md with Kafka streaming section including all diagrams and ADR links
- 2025-10-16: Updated "Most Recently Updated" and "For AI Assistants" sections with Kafka streaming links
- 2025-10-14: Added all 5 child actors documentation (BlockStorageActor, VaultActor, CucumberExecutionActor, KafkaProducerActor, KafkaConsumerActor)
- 2025-10-14: Updated TestExecutionActor documentation with child actor integration details
- 2025-10-14: Added testing statistics: 179 total tests (123 unit, 56 child unit, 94 BDD scenarios)
- 2025-10-14: Documented parent reference pattern (Akka Typed) for all child actors
- 2025-10-14: Documented security compliance for actors handling SecurityDirective
- 2025-10-14: Added GuardianActor documentation to blueprint/02 Booting (root supervisor, Error Kernel pattern)
- 2025-10-14: Added GuardianActor to Implementation Files Reference section
- 2025-10-14: Updated AI Assistants section with GuardianActor blueprint link
- 2025-10-14: Added QueueActor documentation to blueprint/08 Test Flow
- 2025-10-14: Added 2 blueprints (QueueActor, QueueActor Message Routing)
- 2025-10-13: Initial index created with TestExecutionActor documentation consolidated
- 2025-10-13: Added 5 ADRs (FSM, Self-Message, Poison Pill, Factory Injection, Error Kernel)
- 2025-10-13: Added 2 blueprints (TestExecutionActor FSM, TestExecutionActor Error Handling)
