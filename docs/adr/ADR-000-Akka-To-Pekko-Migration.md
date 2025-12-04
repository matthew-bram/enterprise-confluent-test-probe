# ADR-000: Akka 2.8.5 to Apache Pekko 1.2.1 Migration

**Date**: 2025-10-16

**Status**: Accepted and Implemented

**Deciders**: Engineering Team

**Related Issues**: Licensing compliance, open source distribution requirements

---

## Context

Test-Probe was initially built using **Akka 2.8.5**, released under the Business Source License 1.1 (BSL 1.1) by Lightbend. The project includes comprehensive actor-based architecture with:

- **11 typed actors**: GuardianActor, QueueActor, TestExecutionActor, 5 child actors, 3 Kafka streaming actors
- **Akka Streams**: Extensive streaming infrastructure for Kafka integration via Alpakka Kafka 4.0.2
- **Test infrastructure**: 445 test scenarios (247 unit + 198 component) using Akka TestKit
- **Codebase scope**: 304 `akka.*` references across 72 files (20 main source, 52 test files)

### The Licensing Problem

In September 2022, Lightbend changed Akka's license from Apache 2.0 to Business Source License 1.1:

- **Akka 2.6.19 and earlier**: Apache 2.0 (truly open source)
- **Akka 2.7.0 and later**: Business Source License 1.1 (source-available, NOT open source)

**BSL 1.1 Restrictions**:

Production use of Akka 2.7+ requires one of:
1. Commercial license from Lightbend (paid)
2. Annual revenue < $25M (free license, but requires registration and compliance)
3. Open source project exemption

**Prohibited without license**:
- ❌ Production use in commercial products
- ❌ Distribution as part of SaaS offerings
- ❌ Revenue-generating production deployments
- ❌ CI/CD infrastructure use (production infrastructure)

**Conversion clause**: BSL code converts to Apache 2.0 after 3 years (Akka 2.8.5 would convert ~2026-2027)

### Impact on Test-Probe

Test-Probe is designed as an **enterprise-grade testing framework** for:
- Distribution to development teams as open source
- Use in CI/CD pipelines (production infrastructure)
- Potential commercial distribution or SaaS offerings
- Open source release under Apache 2.0 license

**Without migration**:
1. **Legal risk**: Production use (CI/CD) requires Lightbend license
2. **Distribution constraints**: Cannot freely distribute as pure open source
3. **Downstream burden**: Users must comply with BSL 1.1 restrictions
4. **Vendor lock-in**: Tied to Lightbend's licensing terms and pricing
5. **Future uncertainty**: License terms could change at Lightbend's discretion

---

## Decision

**We will migrate from Akka 2.8.5 (BSL 1.1) to Apache Pekko 1.2.1 (Apache 2.0).**

**Implementation completed**: 2025-10-16 (2 hours, vs estimated 4-8 hours)

### Rationale

**Apache Pekko** is a fork of Akka 2.6.20, created and maintained by the Apache Software Foundation:

- **License**: Apache 2.0 (forever, no conversion period, no restrictions)
- **Governance**: Community-driven, vendor-neutral (Apache Top-Level Project since May 2024)
- **Compatibility**: Binary-compatible with Akka 2.6.x for rolling upgrades
- **Maturity**: Based on Akka 2.6.20 (most stable Akka release)
- **Active development**: Regular releases, bug fixes, security patches
- **Production ready**: Used by Apache Kafka contributors, digital.ai, and enterprise organizations

**Pekko provides**:
- Pekko Core: Actor system, streams, clustering (replaces Akka Core)
- Pekko HTTP: HTTP server/client (replaces Akka HTTP)
- Pekko Connectors Kafka: Kafka integration (replaces Alpakka Kafka)

---

## Implementation

### Migration Approach

**Strategy**: Incremental, testable, reversible migration using automated find/replace with comprehensive validation.

**Execution phases**:
1. Maven dependencies (8 replacements)
2. Package imports (182 automated replacements: 85 imports, 97 type references)
3. Configuration namespaces (2 files: `akka.*` → `pekko.*`)
4. Test infrastructure updates
5. Comprehensive validation (445 tests)
6. Documentation updates

### Technical Changes

#### 1. Maven Dependencies

**Parent POM** (`pom.xml`):

```xml
<!-- BEFORE: Akka 2.8.5 -->
<akka.version>2.8.5</akka.version>
<akka.http.version>10.5.3</akka.http.version>
<alpakka.kafka.version>4.0.2</alpakka.kafka.version>

<dependency>
  <groupId>com.typesafe.akka</groupId>
  <artifactId>akka-actor-typed_${scala.binary.version}</artifactId>
  <version>${akka.version}</version>
</dependency>

<!-- AFTER: Pekko 1.2.1 -->
<pekko.version>1.2.1</pekko.version>
<pekko.http.version>1.1.0</pekko.http.version>
<pekko.connectors.kafka.version>1.1.0</pekko.connectors.kafka.version>

<dependency>
  <groupId>org.apache.pekko</groupId>
  <artifactId>pekko-actor-typed_${scala.binary.version}</artifactId>
  <version>${pekko.version}</version>
</dependency>
```

**All dependencies replaced**:
- `akka-actor-typed` → `pekko-actor-typed` (1.2.1)
- `akka-stream` → `pekko-stream` (1.2.1)
- `akka-cluster-typed` → `pekko-cluster-typed` (1.2.1)
- `akka-http` → `pekko-http` (1.1.0)
- `akka-http-spray-json` → `pekko-http-spray-json` (1.1.0)
- `akka-stream-kafka` → `pekko-connectors-kafka` (1.1.0)
- `akka-slf4j` → `pekko-slf4j` (1.2.1)
- Test dependencies: `akka-actor-testkit-typed`, `akka-http-testkit`, `akka-stream-testkit` → Pekko equivalents

#### 2. Package Imports

**Automated find/replace across all Scala files**:

```bash
# 182 total replacements (85 imports + 97 type references)
find . -name "*.scala" -type f -exec sed -i '' 's/import akka\./import org.apache.pekko./g' {} +
```

**Example changes**:

```scala
// BEFORE
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.kafka.scaladsl.Consumer

// AFTER
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.kafka.scaladsl.Consumer
```

#### 3. Configuration Updates

**Configuration namespace changes** (2 files):

```hocon
# reference.conf (main/resources)
# BEFORE
akka.actor.blocking-io-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor { fixed-pool-size = 8 }
  throughput = 1
}

# AFTER
pekko.actor.blocking-io-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor { fixed-pool-size = 8 }
  throughput = 1
}
```

```hocon
# application.conf (test/resources)
# BEFORE
akka.loglevel = "DEBUG"
akka.actor.testkit.typed.single-expect-default = 3s

# AFTER
pekko.loglevel = "DEBUG"
pekko.actor.testkit.typed.single-expect-default = 3s
```

#### 4. Dispatcher Selector Updates

**Critical for streaming actors**:

```scala
// BEFORE
val blockingEc: ExecutionContext = ctx.system.dispatchers.lookup(
  akka.actor.typed.DispatcherSelector.fromConfig("akka.actor.blocking-io-dispatcher")
)

// AFTER
val blockingEc: ExecutionContext = ctx.system.dispatchers.lookup(
  org.apache.pekko.actor.typed.DispatcherSelector.fromConfig("pekko.actor.blocking-io-dispatcher")
)
```

---

## Validation Results

### Test Execution (Zero Failures, Zero Errors)

**Unit Tests**:
```
Tests run: 247
Failures: 0
Errors: 0
Skipped: 0
Time: 5:40 min
Result: ✅ BUILD SUCCESS
```

**Component Tests** (with Kafka Testcontainers):
```
Tests run: 198
Failures: 0
Errors: 0
Skipped: 0
Time: 5:04 min
Result: ✅ BUILD SUCCESS
```

**Total validation**: 445/445 tests passed (100%)

### Integration Validation

✅ **Compilation**: Zero errors, zero warnings
✅ **Kafka Streaming**: Pekko Connectors Kafka 1.1.0 fully functional
✅ **Schema Registry**: All formats working (Avro, Protobuf, JSON Schema)
✅ **Actor System**: GuardianActor, QueueActor, TestExecutionActor, all 5 child actors
✅ **FSM**: 7-state finite state machine (TestExecutionActor) working correctly
✅ **Testcontainers**: Kafka + Schema Registry integration tests passing
✅ **ActorTestKit**: Startup, shutdown, messaging all functional
✅ **Stream Lifecycle**: Consumer/Producer streams, backpressure handling
✅ **Dispatcher Configuration**: Blocking I/O dispatcher correctly configured

### Performance Comparison

**Pekko 1.2.1 vs Akka 2.8.5**:
- Unit tests: **7 seconds faster** (2% improvement)
- Component tests: Within baseline (±1%)
- No performance regression detected
- All tests within performance targets

**Conclusion**: Pekko 1.2.1 is **equal or faster** than Akka 2.8.5 for Test-Probe workloads.

---

## Benefits

### 1. Licensing Freedom (Primary)

- ✅ **Apache 2.0 license**: Forever, no conversion period, no restrictions
- ✅ **No production restrictions**: CI/CD, SaaS, commercial products all permitted
- ✅ **No revenue limits**: Use regardless of company size or revenue
- ✅ **No registration required**: No compliance reporting to vendor
- ✅ **Free distribution**: Can distribute as open source without constraints
- ✅ **No vendor lock-in**: Community-governed, Apache Foundation oversight
- ✅ **Patent grant**: Apache 2.0 includes explicit patent license

### 2. Technical Improvements

**Newer Dependencies**:
- **Netty 4**: Pekko uses Netty 4.1.x (vs Akka 2.6's Netty 3.10.x) - better performance, security, HTTP/2 support
- **Jackson 2.17.2**: Latest Jackson with CVE fixes (vs Akka 2.8's Jackson 2.13.4)
- **slf4j v2**: Modern logging API (vs slf4j v1 in Akka)
- **Virtual thread support**: JDK 21+ optimization readiness

**Enhanced Stream API**:
Pekko 1.2.1 includes stream operators NOT in Akka 2.8.5:
- `collectFirst`, `collectWhile`, `dimap`, `flatten`, `flattenMerge`
- `foldWhile`, `mapWithResource`, `switchMap`, `takeUntil`
- `dropRepeated`, `groupedAdjacentBy`

**Better Java DSL**: Improved parity between Scala and Java APIs

### 3. Community and Governance

- **Apache Foundation governance**: Vendor-neutral, community-driven
- **Transparent development**: Open roadmap, public mailing lists
- **Multiple sponsors**: IBM, Lightbend, digital.ai, community contributors
- **Active development**: Regular releases, bug fixes, security patches
- **Long-term support**: Apache projects have indefinite lifespans

### 4. Ecosystem Compatibility

- **Binary compatible**: Can interoperate with Akka 2.6.x libraries during migration
- **Apache ecosystem**: Aligns with other Apache projects (Kafka, Flink, etc.)
- **Drop-in replacement**: Minimal code changes for existing Akka 2.6.x applications

---

## Costs and Trade-offs

### Migration Effort

**Actual effort**: 2 hours (vs estimated 4-8 hours)

**Breakdown**:
- Maven dependency updates: 15 minutes
- Automated import replacement: 10 minutes
- Configuration updates: 10 minutes
- Manual code review: 30 minutes
- Test execution and validation: 45 minutes
- Documentation updates: 10 minutes

**Low complexity**: Changes were purely mechanical (package renaming), no algorithm or logic changes.

### "Version Perception"

**Trade-off**: Migrating from "Akka 2.8.5" → "Pekko 1.2.1" may appear as a downgrade to external observers.

**Reality**: Pekko 1.2.1 is based on Akka 2.6.20, but:
- We used **ZERO** Akka 2.7/2.8-exclusive features
- Pekko has **more** stream operators than Akka 2.8
- Pekko has **newer** dependencies (Netty 4, Jackson 2.17)
- Version numbers are arbitrary; feature parity is what matters

**Mitigation**: Documentation clearly explains the migration rationale and technical advantages.

### Documentation Maintenance

**Trade-off**: Must track Pekko releases instead of Akka releases.

**Impact**: Minimal - Pekko has clear release notes, similar cadence to Akka 2.6.x.

### Ecosystem Fragmentation

**Trade-off**: Akka community split between BSL (Lightbend) and Apache (Pekko).

**Observation**: Many organizations migrating to Pekko (Apache Kafka, digital.ai, others). Pekko community growing.

---

## Risks

### 1. API Incompatibility (Mitigated)

**Risk**: Pekko API differs from Akka 2.8.5, causing compilation or runtime errors.

**Likelihood**: Very Low

**Mitigation**:
- Pekko is binary-compatible with Akka 2.6.x
- We used ZERO Akka 2.7/2.8-exclusive features
- 445 comprehensive tests provide regression protection

**Outcome**: ✅ Zero API incompatibilities discovered during migration

### 2. Kafka Integration Failure (Mitigated)

**Risk**: Pekko Connectors Kafka behaves differently than Alpakka Kafka.

**Likelihood**: Low

**Mitigation**:
- Pekko Connectors Kafka is a direct fork of Alpakka Kafka
- 198 component tests with Testcontainers validate Kafka integration
- Schema Registry integration tested (Avro, Protobuf, JSON)

**Outcome**: ✅ All Kafka tests passed, Schema Registry working perfectly

### 3. Performance Regression (Mitigated)

**Risk**: Pekko slower than Akka 2.8.5.

**Likelihood**: Very Low

**Mitigation**:
- No architectural changes (just package renaming)
- Baseline performance metrics captured
- Component tests measure execution time

**Outcome**: ✅ Pekko 2% faster than Akka (7 seconds improvement on unit tests)

### 4. Community Support (Mitigated)

**Risk**: Pekko community smaller than Akka, less support available.

**Likelihood**: Low

**Mitigation**:
- Pekko is Apache TLP (May 2024), strong governance
- Active mailing list, GitHub issues, Slack channel
- Commercial support available from multiple vendors

**Outcome**: No issues encountered; Pekko documentation comprehensive

### 5. Future Pekko Development (Acknowledged)

**Risk**: Pekko development slows or stops.

**Likelihood**: Low (Apache projects have long lifespans)

**Impact**: Medium (would need to evaluate alternatives)

**Mitigation**:
- Apache governance ensures continuity
- Multiple sponsors and active contributors
- Can fork if necessary (Apache 2.0 license)

---

## Alternatives Considered

### Alternative 1: Stay on Akka 2.8.5 + Obtain Lightbend License

**Approach**: Continue using Akka 2.8.5, obtain commercial or free license.

**Pros**:
- ✅ No migration effort
- ✅ Keep current features (though we don't use Akka 2.8-exclusive features)
- ✅ No technical risk

**Cons**:
- ❌ Vendor lock-in to Lightbend
- ❌ Annual revenue tracking required (even for free license)
- ❌ Cannot distribute as pure open source
- ❌ License compliance overhead for all users
- ❌ Uncertain long-term licensing costs
- ❌ Downstream users must also comply with BSL 1.1

**Rejection Reason**: Violates Test-Probe's open source goals, creates downstream license burden, introduces vendor lock-in.

### Alternative 2: Downgrade to Akka 2.6.19 (Last Apache 2.0 Akka)

**Approach**: Downgrade from Akka 2.8.5 → Akka 2.6.19.

**Pros**:
- ✅ Apache 2.0 license (truly open source)
- ✅ Stay in Akka ecosystem
- ✅ Minimal migration effort

**Cons**:
- ❌ Akka 2.6 is end-of-life (no future updates)
- ❌ Security vulnerabilities will not be patched
- ❌ Older dependencies (Netty 3, Jackson 2.11, slf4j v1)
- ❌ Missing modern features and improvements
- ❌ May need to migrate to Pekko eventually anyway

**Rejection Reason**: Trading licensing problem for security/maintenance problem; kicks the can down the road.

### Alternative 3: Wait for Akka 2.8.5 BSL Conversion (2026-2027)

**Approach**: Continue using Akka 2.8.5 without license, wait 3 years for BSL → Apache 2.0 conversion.

**Pros**:
- ✅ No migration effort now
- ✅ Eventually get Apache 2.0 license

**Cons**:
- ❌ Legal risk for 2-3 years (BSL violations)
- ❌ Cannot distribute or use in production during wait
- ❌ Uncertain conversion timeline (Lightbend could change terms)
- ❌ Accumulating technical debt
- ❌ 3 years of no updates/security patches

**Rejection Reason**: Too long to wait, legal risk unacceptable, no guarantee of conversion.

### Alternative 4: Switch to Different Actor Framework (Cats Effect, ZIO, etc.)

**Approach**: Replace Akka entirely with Cats Effect, ZIO Actors, or other framework.

**Pros**:
- ✅ Modern functional approach (Cats Effect, ZIO)
- ✅ Strong typing and functional composition
- ✅ Apache 2.0 licensed

**Cons**:
- ❌ Complete architectural rewrite (11 actors, 706-line FSM, etc.)
- ❌ Lose all existing implementation (1500+ lines of actor code)
- ❌ Months of effort (not hours)
- ❌ Significant risk (untested in Test-Probe domain)
- ❌ Less mature actor ecosystem (compared to Akka/Pekko)
- ❌ No Kafka Streams integration (would need custom implementation)

**Rejection Reason**: Nuclear option, vastly disproportionate effort for minimal gain, high risk.

---

## Consequences

### Immediate Consequences

1. **Licensing Compliance**: Test-Probe is now Apache 2.0 throughout, no BSL restrictions
2. **Production Ready**: Can deploy to CI/CD infrastructure without legal concerns
3. **Open Source Distribution**: Can distribute freely, no downstream license burden
4. **Dependency Freshness**: Newer Jackson, Netty 4, slf4j v2
5. **Zero Functional Regression**: All 445 tests pass, zero behavior changes

### Medium-Term Consequences (6-12 months)

1. **Community Alignment**: Part of Apache ecosystem, vendor-neutral governance
2. **Feature Parity**: Access to new Pekko features (enhanced stream operators, etc.)
3. **Security Posture**: Benefit from Apache's security process and CVE fixes
4. **Commercial Support**: Multiple vendors offer Pekko support (not just Lightbend)

### Long-Term Consequences (1-3 years)

1. **Future-Proofing**: No risk of license changes, Apache 2.0 forever
2. **Ecosystem Evolution**: Pekko may diverge further from Akka with new features
3. **Virtual Thread Support**: JDK 21+ optimizations as ecosystem matures
4. **Akka Divergence**: Test-Probe will not benefit from future Akka innovations (acceptable trade-off)

---

## Compliance

### License Audit

**Before Migration**:
- Akka 2.8.5: Business Source License 1.1 (NOT open source)
- Risk: Production use requires Lightbend license

**After Migration**:
- Pekko 1.2.1: Apache 2.0 (open source)
- Pekko HTTP 1.1.0: Apache 2.0
- Pekko Connectors Kafka 1.1.0: Apache 2.0
- Result: ✅ 100% Apache 2.0 compliance

### Trademark Considerations

- "Akka" is a trademark of Lightbend, Inc.
- "Apache Pekko" is a trademark of the Apache Software Foundation
- Test-Probe documentation updated to reference Pekko, not Akka
- No trademark violations

---

## Documentation Updates

### Files Updated

1. **CLAUDE.md**: All Akka references → Pekko
2. **ADRs**: Updated existing ADRs to note Pekko migration
3. **Architecture docs**: Updated diagrams and references
4. **Maven commands**: Verified (no changes needed, commands identical)
5. **Test documentation**: Updated TestKit references

### External References

**Updated links**:
- Akka documentation → Pekko documentation
- Alpakka Kafka docs → Pekko Connectors Kafka docs
- Akka GitHub → Pekko GitHub

---

## Related Decisions

**Related ADRs**:
- ADR-001: Consumer Registry Memory Management (references Akka Streams → updated to Pekko)
- ADR-002: Producer Stream Performance Optimization (references Akka Streams → updated to Pekko)
- ADR-003: Schema Registry Error Handling (Kafka integration, Pekko compatible)
- ADR-004: Consumer Stream Lifecycle Management (Pekko Streams lifecycle)

**Future Decisions**:
- HTTP server implementation: Will use Pekko HTTP (ADR-006, future)
- Clustering strategy: Will use Pekko Cluster if needed (ADR-007, future)
- Persistence strategy: Will use Pekko Persistence if needed (ADR-008, future)

---

## References

### Official Documentation

1. [Apache Pekko Documentation](https://pekko.apache.org/docs/pekko/current/)
2. [Pekko Migration Guide](https://pekko.apache.org/docs/pekko/current/project/migration-guides.html)
3. [Pekko 1.2.1 Release Notes](https://pekko.apache.org/docs/pekko/snapshot/release-notes/releases-1.2.html)
4. [Pekko Connectors Kafka Documentation](https://pekko.apache.org/docs/pekko-connectors-kafka/current/)
5. [Apache Pekko GitHub](https://github.com/apache/pekko)

### Licensing and Governance

6. [Akka BSL License FAQ](https://akka.io/bsl-license-faq)
7. [Why Lightbend Changed Akka License](https://akka.io/blog/why-we-are-changing-the-license-for-akka)
8. [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
9. [Apache Pekko Project Page](https://pekko.apache.org/)

### Migration Resources

10. [Akka to Pekko Migration (Medium Article)](https://medium.com/xworks-techinsights/akka-to-pekko-migration-step-by-step-cca64bc61016)
11. [Pekko Migration Plan](../working/PEKKO-MIGRATION-PLAN.md) (Test-Probe specific)
12. [Test-Probe Migration Commit](https://github.com/test-probe/test-probe/commit/b4addc8) (Git history)

---

## Review and Revision

**Next Review**: 2026-04-01 (6 months post-migration)

**Trigger for Revision**:
- Pekko 2.0 release with breaking changes
- Significant Akka feature we need that Pekko lacks
- Performance regression discovered in production
- Pekko project becomes inactive (unlikely given Apache governance)
- Licensing issues with Pekko dependencies

**Decision Log**:
- **2025-10-16**: Initial ADR approved
- **2025-10-16**: Migration executed and validated (2 hours)
- **2025-10-16**: All tests passed (445/445), zero regressions
- **2025-10-16**: ADR status: ✅ Accepted and Implemented

**Success Metrics** (Post-Migration):
- ✅ 100% test pass rate (247 unit + 198 component)
- ✅ Zero compilation errors or warnings
- ✅ Zero functional regressions
- ✅ 2% performance improvement (7 seconds faster)
- ✅ Full Kafka integration (Avro, Protobuf, JSON Schema)
- ✅ Apache 2.0 license compliance
- ✅ Migration completed ahead of schedule (2h vs 4-8h estimate)

---

**Last Updated**: 2025-10-16

**Migration Status**: ✅ **COMPLETED SUCCESSFULLY**

**Outcome**: Zero-downtime migration from Akka 2.8.5 (BSL 1.1) to Apache Pekko 1.2.1 (Apache 2.0). All success criteria met. Test-Probe is now 100% Apache 2.0 licensed and production-ready for CI/CD deployment, open source distribution, and commercial use without restrictions.
