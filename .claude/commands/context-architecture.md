---
description: Load architecture documentation context
---

You are working with architecture documentation. Loading architecture context:

**Key References:**

1. **Architecture Overview:**
   - `.claude/guides/ARCHITECTURE.md` - Complete architecture guide
   - `.claude/guides/DOCUMENTATION-STRUCTURE.md` - Documentation organization
   - `docs/architecture/INDEX.md` - Architecture documentation index

2. **Current Architecture State:**

   **Actor System (Apache Pekko Typed):**
   - ✅ GuardianActor (Error Kernel pattern, 90% coverage)
   - ✅ QueueActor (FIFO queue management)
   - ✅ TestExecutionActor (7-state FSM, 706 lines)
   - ✅ 5 Child Actors (BlockStorage, Vault, CucumberExecution, KafkaProducer, KafkaConsumer)

   **Streaming Layer (Pekko Streams):**
   - ✅ KafkaProducerStreamingActor
   - ✅ KafkaConsumerStreamingActor
   - ✅ ProbeScalaDsl (thread-safe registry)

   **Service Layer:**
   - ⏳ BlockStorageService (S3, Azure, GCP, Local)
   - ⏳ VaultService (AWS, Azure, GCP)
   - ⏳ CucumberService (test execution)

3. **Documentation Structure:**

   ```
   docs/
   ├── architecture/
   │   ├── INDEX.md (navigation hub)
   │   ├── blueprint/ (detailed design docs)
   │   │   ├── 01 Domain/
   │   │   ├── 02 Use Cases/
   │   │   ├── 03 Business Logic/
   │   │   ├── 04 Adapters/
   │   │   └── 05 Infrastructure/
   │   └── adr/ (architectural decision records)
   ├── diagrams/ (mermaid diagrams)
   ├── product/ (requirements)
   └── testing-practice/ (testing standards)
   ```

4. **When to Create ADRs:**
   - Choosing between design alternatives
   - Technology selection decisions
   - Architectural pattern changes
   - Breaking changes to public APIs
   - Security or compliance decisions

5. **ADR Template:**
   ```markdown
   # ADR-XXX-YYY-NNN: Title

   ## Status
   Accepted / Proposed / Deprecated

   ## Context
   What is the issue we're facing?

   ## Decision
   What did we decide?

   ## Consequences
   Positive and negative outcomes

   ## Alternatives Considered
   What else did we evaluate?
   ```

6. **Evergreen Architecture Requirements:**
   - Keep INDEX.md updated with navigation links
   - Maintain blueprint schedule (evergreen vs dated docs)
   - Update diagrams in `docs/diagrams/` when architecture changes
   - Cross-reference ADRs with affected code
   - Keep CLAUDE.md current state section updated

**Ready for architecture documentation work.**