# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 🧭 Quick Navigation - Read What You Need

**Before ANY code work**: Read the 🚨 CRITICAL sections below

**⚡ Ultra-Fast Context (Start Here)**:
- 📋 **[`.claude/quick-reference/SCALA-CHEATSHEET.md`](.claude/quick-reference/SCALA-CHEATSHEET.md)** - All Scala patterns in one page
- 🧪 **[`.claude/quick-reference/TESTING-CHEATSHEET.md`](.claude/quick-reference/TESTING-CHEATSHEET.md)** - All testing patterns in one page
- 🚨 **[`.claude/quick-reference/BEFORE-YOU-CODE.md`](.claude/quick-reference/BEFORE-YOU-CODE.md)** - Visibility pattern (critical!)

**Context Loaders (Use slash commands)**:
- `/context-code` - General Scala coding context (use before any coding session)
- `/context-actor-work` - Actor development context
- `/context-testing` - Testing workflow context
- `/context-refactor` - Refactoring context
- `/context-review` - Code review context
- `/context-build` - Build and compilation help
- `/context-architecture` - Architecture documentation
- `/context-quickref` - Quick reference guide

**Chaining Commands for Combined Workflows**:
```bash
# Integration testing work
/context-code + /context-testing

# Actor testing
/context-actor-work + /context-testing

# Service refactoring with tests
/context-refactor + /context-testing
```

**Then, based on your task (full guides)**:
- 🧪 **Writing/fixing tests?** → Read [`.claude/guides/TESTING.md`](.claude/guides/TESTING.md)
- 🎭 **Working with actors?** → Read [`.claude/guides/ACTORS.md`](.claude/guides/ACTORS.md)
- 🔨 **Build/compile issues?** → Read [`.claude/guides/BUILD.md`](.claude/guides/BUILD.md)
- 📐 **Architecture questions?** → Read [`.claude/guides/ARCHITECTURE.md`](.claude/guides/ARCHITECTURE.md)
- 🚀 **Development tasks?** → Read [`.claude/guides/DEVELOPMENT.md`](.claude/guides/DEVELOPMENT.md)

**📚 For Teams Adopting Test-Probe (NEW)**:
- ☕ **Java API** → [`docs/api/probe-java-dsl-api.md`](docs/api/probe-java-dsl-api.md) - Complete Java DSL reference
- 📖 **Getting Started** → [`docs/user-guide/GETTING-STARTED.md`](docs/user-guide/GETTING-STARTED.md) - Setup and first test
- 🎓 **Tutorials** → [`docs/user-guide/tutorials/`](docs/user-guide/tutorials/) - Progressive learning (5 tutorials)
- ❓ **FAQ** → [`docs/user-guide/FAQ.md`](docs/user-guide/FAQ.md) - Common questions
- 🔧 **Troubleshooting** → [`docs/user-guide/TROUBLESHOOTING.md`](docs/user-guide/TROUBLESHOOTING.md) - Problem solving
- 📋 **Docs Overview** → [`docs/README.md`](docs/README.md) - Documentation hub

---

## 🛡️ Enforcement Skills Suite

This project uses **Anthropic Skills** to actively enforce critical policies and maintain code quality. Skills are lazy-loaded documentation that activates when needed, providing enforcement, education, and assistance.

**Enforcement Skills**:
- **feature-builder** - Enforces 8-phase feature workflow (Phase 0 Architecture → Phase 7 Documentation), architecture-first development
- **test-quality-enforcer** - Zero-tolerance test quality, two-phase testing, coverage enforcement
- **visibility-pattern-guardian** - Prevents 20-40% coverage loss from `private` methods
- **scala-conventions-enforcer** - **🚨 MANDATORY BEFORE ANY SCALA WORK** - Must be loaded before writing OR reviewing Scala code. Enforces project standards from `.claude/styles/scala-conventions.md` (Scala 3 syntax, Try/Either patterns, visibility, expressive style). Failure to load = incomplete reviews, wrong severity calibration, Java-looking code

**Development Skills**:
- **skill-creator** - Guide for creating effective skills with specialized knowledge, workflows, or tool integrations
- **vibe-mode** - Customize Claude's interaction style (🎩 Professional, 💬 Conversational, 🎉 Enthusiastic, 🤙 Casual/Buddy)
- **paired-programming** - Collaborative iterative development (ask-before-assuming, show-before-writing, iterate-in-small-chunks)

**How Skills Work**:
1. **Trigger**: Keywords, tool usage, or context detection activates the skill
2. **Load**: Skill documentation (SKILL.md) expands with detailed enforcement rules
3. **Enforce**: Skill blocks violations, offers alternatives, or auto-fixes with approval
4. **Educate**: Skills reference authoritative guides and explain the "why"

**Benefits**:
- ✅ Consistent enforcement across sessions
- ✅ Saves ~2,500 tokens in base context (lazy-loaded when needed)
- ✅ Educational - teaches patterns rather than just blocking
- ✅ Collaborative - offers alternatives and assistance

📖 **Skills Directory**: [`.claude/skills/`](.claude/skills/)

If you deviate from any of these guidance, skills and mandatory requirements,
I'll be forced to fine you a $1000.00 for each infraction.

---

## 🚨 CRITICAL: DEFINITION OF DONE 🚨

**CRITICAL FOR NEW CLAUDE ENGINEERS**: Work is NOT complete until ALL criteria are met. We maintain a **100% clean codebase** at all times.

### Zero-Tolerance Test Quality

**All tests MUST pass with zero errors**:
```bash
# REQUIRED OUTPUT:
[INFO] Tests run: X, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Common Violation** - Test shows errors but engineer says "tests pass":
```bash
[ERROR] Tests run: 139, Failures: 0, Errors: 47  # ❌ NOT PASSING
```

### Pre-Test Critical Step

**⚠️ MOST COMMON MISTAKE**: Clear `/tmp` BEFORE testing to avoid stale data:
```bash
rm -f /tmp/*.log /tmp/*.txt  # REQUIRED - prevents false positives
```

### Feature Complete Criteria

1. ✅ Code compiles, all tests pass (zero errors)
2. ✅ Coverage: 70%+ min, 85%+ for actors/FSMs
3. ✅ Feature files match step definitions (no undefined steps)
4. ✅ Visibility pattern applied (no untestable private methods)

**🛡️ Enforcement**: The **test-quality-enforcer** skill enforces zero-tolerance test quality, two-phase testing (module → full regression), and coverage thresholds. Hard blocks on violations.

📖 **Full Standards & Examples**: [`.claude/skills/test-quality-enforcer/SKILL.md`](.claude/skills/test-quality-enforcer/SKILL.md)
📖 **Testing Guide**: [`.claude/guides/TESTING.md`](.claude/guides/TESTING.md)

---

## 🚨 CRITICAL: VISIBILITY PATTERN - PEER REVIEW REJECTION POINT 🚨

**THE MOST COMMON VIOLATION**: Using `private` methods instead of the visibility pattern.

**❌ WRONG - Reduces coverage by 20-40%:**
```scala
object GuardianActor {
  private def receiveBehavior(...) = { ... }  // ❌ CANNOT UNIT TEST
}
```

**✅ CORRECT - Enables 85%+ coverage:**
```scala
private[core] object GuardianActor {
  def receiveBehavior(...) = { ... }  // ✅ CAN UNIT TEST (public method, module-scoped object)
}
```

**The Pattern**: Keep methods PUBLIC, apply `private[module]` at object/class level. Module boundaries protect external access while enabling comprehensive test coverage.

**Proven Impact**: GuardianActor went from 45% → 85% coverage after applying this pattern.

**🛡️ Enforcement**: The **visibility-pattern-guardian** skill detects `private` methods, auto-refactors to the pattern (with approval), and blocks at code review (scala-ninja) if violations remain.

📖 **Full Pattern & Examples**: [`.claude/skills/visibility-pattern-guardian/SKILL.md`](.claude/skills/visibility-pattern-guardian/SKILL.md)
📖 **Quick Reference**: [`.claude/quick-reference/BEFORE-YOU-CODE.md`](.claude/quick-reference/BEFORE-YOU-CODE.md)
📖 **Scala Cheat Sheet**: [`.claude/quick-reference/SCALA-CHEATSHEET.md`](.claude/quick-reference/SCALA-CHEATSHEET.md)
📖 **Style Guides**: [`.claude/styles/scala-conventions.md`](.claude/styles/scala-conventions.md) (lines 111-160)

---

## Project Overview

**Test-Probe** is an enterprise-grade event-driven architecture (EDA) testing framework built on Scala 3.3.6 LTS, Apache Pekko Typed Actors, and Cucumber. It enables teams to write BDD-style tests for Kafka-based event systems using Gherkin specifications.

**Philosophy**: Make it as simple as possible for teams to adopt the framework so that they actually test.

**Five Core Principles**:
1. **Easability** - Low-effort capability to test event-driven architectures
2. **Testability** - Eat our own dogfood, thoroughly tested end-to-end
3. **Performability** - High-performance runtimes for performant testing
4. **Reliability** - Proven technologies and patterns, no drama
5. **Extensibility** - Fits with enterprise tools, languages, and environments

### Current Development Status (as of 2025-11-25)

- ✅ GuardianActor: Complete (266 lines, 18 unit tests, 90% coverage, 6 BDD scenarios)
- ✅ QueueActor: Complete (444 lines, 18 unit tests, 18 BDD scenarios)
- ✅ TestExecutionActor FSM: Complete (706 lines, 83 unit tests, 7-state FSM)
- ✅ ActorCommands protocol: All message types defined
- ✅ TestExecutionActorStatesData: State and data models complete
- ✅ Child Actors: ALL 5 COMPLETE (scaffolding phase - 957 lines, 56 unit tests, 52 BDD scenarios)
  - ✅ BlockStorageActor: Complete (191 lines, 16 unit tests, 14 BDD scenarios)
  - ✅ VaultActor: Complete (162 lines, 8 unit tests, 10 BDD scenarios)
  - ✅ CucumberExecutionActor: **REAL EXECUTION COMPLETE** (312 lines, 12 unit tests, 12 BDD scenarios)
  - ✅ KafkaProducerActor: Complete (237 lines, supervisor pattern)
  - ✅ KafkaConsumerActor: Complete (237 lines, supervisor pattern)
- ✅ Cucumber Service Layer: **COMPLETE** (846 lines, 70+ tests, real Cucumber execution)
  - ✅ TestExecutionEventListener: Complete (237 lines, event-based result collection)
  - ✅ TestExecutionListenerRegistry: Complete (120 lines, ThreadLocal registry pattern)
  - ✅ CucumberConfiguration: Complete (332 lines, type-safe builder)
  - ✅ CucumberExecutor: Complete (157 lines, Main.run() CLI integration)
  - ✅ io.distia.probe.core.glue: Complete (75 lines, framework glue package)
- ✅ Kafka Streaming Layer: COMPLETE (848 lines, full integration)
  - ✅ KafkaProducerStreamingActor: Complete (89 lines, Pekko Streams)
  - ✅ KafkaConsumerStreamingActor: Complete (168 lines, Pekko Streams + registry)
  - ✅ ProbeScalaDsl: Complete (150 lines, thread-safe DSL registry)
  - ✅ ProbeJavaDsl: Stub (CompletableFuture wrapper)
  - ✅ ADRs 001-004: All architectural decisions documented
- ✅ **Multiple Bootstrap Servers Feature: COMPLETE (2025-11-22)**
  - ✅ TopicDirective: Updated with `bootstrapServers: Option[String] = None`
  - ✅ TopicDirectiveValidator: NEW - uniqueness and format validation
  - ✅ Exceptions: DuplicateTopicException, InvalidBootstrapServersException
  - ✅ Streaming Actors: Fallback logic for per-topic bootstrap servers
  - ✅ TestcontainersManager: Multi-cluster registry with named clusters
  - ✅ ADR-KAFKA-001: Comprehensive architecture decision record
  - ✅ Tests: 1,502 unit tests (100% coverage on new code), 31 BDD scenarios
- ✅ **End-to-End Integration Tests: COMPLETE (2025-11-26)**
  - ✅ IntegrationWorld: JIMFS integration, Testcontainers lifecycle, ActorSystem management
  - ✅ Evidence Verification: Circe-based JSON parsing, Cucumber report analysis
  - ✅ Schema Registration: All 3 serdes types (JSON, Avro, Protobuf) with SerdesFactory
  - ✅ Inner Test Execution: Real Cucumber scenarios with Kafka produce/consume
  - ✅ **6 Event Types**: TestEvent, OrderEvent, PaymentEvent, UserEvent, ProductEvent + more
  - ✅ **6 Feature Files**: FirstIntegrationTest, HighVolume (single/multi), RapidFire, Concurrent, LargePayload
  - ✅ **15 Integration Scenarios**: All passing with zero errors
  - ✅ ADR-CUCUMBER-002: Parallel execution constraints documented (sequential by design)
  - ✅ Result: 100% end-to-end validation across JSON, Avro, and Protobuf serialization

---

## Project Structure

This is a multi-module Maven project with 7 modules:

```
test-probe-parent/
├── test-probe-common/         # Shared models, utilities, contracts
├── test-probe-core/           # Actor system, FSM, business logic
├── test-probe-glue/           # Cucumber framework & step definitions
├── test-probe-client-adapters/# Clients REST, gRPC
├── test-probe-interface-rest/ # REST API for test submission
├── test-probe-boot/           # Application bootstrap & lifecycle
└── test-probe-interface-grpc/ # gRPC interface (optional)
```

### Key Directories

- **`docs/`**: Architecture, testing practices, diagrams, product requirements
- **`docs/product/PRODUCT-REQUIREMENTS-DOCUMENT.md`**: Complete PRD for AI-driven development
- **`.claude/`**: Custom agents, commands, code style conventions, and guides
  - **`.claude/guides/`**: Task-oriented guides (NEW - read based on your task!)
  - **`.claude/styles/`**: Code style and pattern references
- **`maven-commands-reference.md`**: Authoritative Maven command reference
- **`working/`**: Implementation plans, BDD feature files, mermaid diagrams
  - `TestExecutionActorImplementationPlan.md`: Complete FSM implementation specification
  - `test-execution-actor-fsm.feature`: 20+ BDD scenarios for TestExecutionActor
  - `TestExecutionActor-StateTransitionDiagram.mermaid`: State machine diagram
  - `TestExecutionActor-MessageFlow.mermaid`: Message flow diagram
  - `TestExecutionActor-ErrorPropogationRecoveryFlow.mermaid`: Error handling diagram

---

## Working with This Codebase

### Build Scripts (Production-Grade!)

The project provides **production-grade build scripts** with professional UX features (enhanced 2025-10-30):

**Quick Reference**:
```bash
./scripts/compile.sh -m core          # Fast compilation (no tests)
./scripts/test-unit.sh -m core        # Unit tests (~5-10s)
./scripts/test-component.sh -m core   # Component tests (~2-3min, 4-thread parallel)
./scripts/test-all.sh -m core         # All tests (unit + component)
./scripts/test-coverage.sh -m core    # Tests + coverage (warnings only)
./scripts/build-ci.sh                 # Full CI/CD (strict, fat JAR, coverage table)
```

**ALL scripts have --help**: `./scripts/test-unit.sh --help`

**Available modules**: `common`, `core`, `services`, `interfaces`

**Interactive mode**: Omit `-m` flag for module selection prompt

**✨ New Enhancements** (all scripts):
- Help flags with formatted documentation
- Execution timing (per-step + total)
- Progress indicators for long operations
- Disk space validation (10-20GB warnings)
- Test failure summaries (no log hunting)
- Coverage table (build-ci.sh - THE SHOWPIECE!)

**Key Features**:
- Compiles dependencies WITHOUT running dependency tests
- K8s scaling and Docker cleanup (component/coverage scripts)
- Parallel execution for core (hard-coded, 4 threads - 61% faster!)
- Coverage warnings (dev) vs strict failures (CI)

**Full Documentation**: `scripts/README.md` | See: `.claude/guides/BUILD.md`

### Key Files to Understand

1. **`scripts/README.md`**: Complete build script documentation (NEW!)
2. **`docs/product/PRODUCT-REQUIREMENTS-DOCUMENT.md`**: Complete requirements for AI development
3. **`maven-commands-reference.md`**: All Maven commands
4. **`.claude/quick-reference/SCALA-CHEATSHEET.md`**: Ultra-condensed Scala patterns (START HERE FOR SCALA)
5. **`.claude/quick-reference/TESTING-CHEATSHEET.md`**: Ultra-condensed testing patterns (START HERE FOR TESTS)
6. **`.claude/quick-reference/BEFORE-YOU-CODE.md`**: Visibility pattern visual guide (CRITICAL!)
7. **`.claude/guides/DOCUMENTATION-STRUCTURE.md`**: Documentation organization, placement rules, evergreen architecture requirements
8. **`.claude/styles/scala-conventions.md`**: Scala coding standards (full guide)
9. **`.claude/styles/akka-patterns.md`**: Akka patterns and practices (full guide)
10. **`.claude/styles/testing-standards.md`**: Testing guidelines (full guide)
11. **`.claude/styles/bdd-testing-standards.md`**: BDD testing guidelines (full guide)
12. **`working/TestExecutionActorImplementationPlan.md`**: Comprehensive FSM implementation specification
13. **`test-probe-core/src/main/scala/com/company/probe/core/actors/TestExecutionActor.scala`**: Complete FSM implementation (706 lines)
14. **`test-probe-core/src/main/scala/com/company/probe/core/actors/TestExecutionActorStatesData.scala`**: FSM state and data models
15. **`test-probe-core/src/main/scala/com/company/probe/core/models/ActorCommands.scala`**: All actor message protocols
16. **`test-probe-core/src/main/scala/com/company/probe/core/builder/BuilderContext.scala`**: Bootstrap pattern

### Task-Oriented Guides

- **[`.claude/guides/DOCUMENTATION-STRUCTURE.md`](.claude/guides/DOCUMENTATION-STRUCTURE.md)** - Documentation organization, blueprint schedules, evergreen architecture
- **[`.claude/guides/TESTING.md`](.claude/guides/TESTING.md)** - Testing standards, pyramid, coverage, @Pending tests
- **[`.claude/guides/ACTORS.md`](.claude/guides/ACTORS.md)** - Akka patterns, FSM, message protocols, visibility pattern
- **[`.claude/guides/BUILD.md`](.claude/guides/BUILD.md)** - Maven commands, test profiles, debugging
- **[`.claude/guides/ARCHITECTURE.md`](.claude/guides/ARCHITECTURE.md)** - Actor hierarchy, FSM, Kafka streaming, integration points
- **[`.claude/guides/DEVELOPMENT.md`](.claude/guides/DEVELOPMENT.md)** - Common tasks, configuration, deployment, troubleshooting

---

## References

- **Product Requirements**: `docs/product/PRODUCT-REQUIREMENTS-DOCUMENT.md`
- **Maven Commands**: `maven-commands-reference.md`
- **Testing Pyramid**: `docs/testing-practice/testing-pyramid.md`
- **Directory Structure**: `docs/testing-practice/directory-structure.md`
- **Architecture Diagrams**: `docs/diagrams/`
- **Custom Agents**: `.claude/agents/` (akka-expert, scala-ninja, etc.)

---

## AI Engineering Notes

This project is designed for **AI-driven incremental development**:

1. **PRD-Driven**: `docs/product/PRODUCT-REQUIREMENTS-DOCUMENT.md` contains all requirements with acceptance criteria
2. **Modular**: 7 modules enable independent development and testing
3. **Type-Safe**: Phantom type builder and typed actors provide compile-time guarantees
4. **Well-Tested**: 70%+ coverage requirement ensures quality
5. **Documented**: Extensive inline documentation and external guides

**Development Approach**:
- ✅ Start with `test-probe-common` (foundation models) - Models defined
- ✅ Build `test-probe-core` (actor system and FSM) - **CORE ACTORS COMPLETE**
  - ✅ GuardianActor: Complete (266 lines, 18 tests, 90% coverage, Error Kernel pattern)
  - ✅ QueueActor: Complete (444 lines, 18 tests, FIFO queue management)
  - ✅ TestExecutionActor: Complete FSM implementation (706 lines, 83 tests, 7-state FSM)
  - ✅ Child Actors: ALL 5 COMPLETE (957 lines, 56 tests, 52 BDD scenarios)
    - BlockStorageActor, VaultActor, CucumberExecutionActor, KafkaProducerActor, KafkaConsumerActor
    - Scaffolding phase: Message passing and exception handling complete
    - Service integration: Marked with TODO for future phase
- ⏳ Add `test-probe-glue` (Cucumber framework)
- ⏳ Integrate `test-probe-client-adapters` (external systems)
- ⏳ Complete with `test-probe-interface-rest` and `test-probe-boot`

**Testing Strategy**:
- Write unit tests first (TDD)
- Add component tests for integration behavior
- Create BDD scenarios for end-to-end validation
- **Current**: All core actors tested + full integration test suite
- **Total Tests**: 1,272 tests (unit + component + 15 integration scenarios)
- **Test Success Rate**: 100% (1,272/1,272 passing)
- **Integration Coverage**: 6 event types, 3 serdes formats, 6 feature files

---

**Last Updated**: 2025-12-08 (Public Release Cleanup)
**Skills Suite**: 7 active skills (4 enforcement: feature-builder, test-quality-enforcer, visibility-pattern-guardian, scala-conventions-enforcer; 3 development: skill-creator, vibe-mode, paired-programming)