---
description: Load build and compilation context
---

You are working with build or compilation issues. Loading build context:

**Key Reference:**
- `.claude/guides/BUILD.md` - Comprehensive build guide
- `maven-commands-reference.md` - Authoritative Maven command reference
- `scripts/README.md` - Enhanced build scripts documentation

---

## 🚀 RECOMMENDED: Enhanced Build Scripts (NEW!)

The project provides **production-grade build scripts** with professional UX features:

```bash
# Compile module (fast, no tests)
./scripts/compile.sh -m core

# Run unit tests (fast, ~5-10s)
./scripts/test-unit.sh -m core

# Run component tests (Docker required, ~2-3min with 4-thread parallel)
./scripts/test-component.sh -m core

# Run all tests (unit + component)
./scripts/test-all.sh -m core

# Run tests with coverage report (warnings only)
./scripts/test-coverage.sh -m core

# Full CI/CD build (strict coverage enforcement, fat JAR)
./scripts/build-ci.sh
```

**Available modules**: `common`, `core`, `services`, `interfaces`

**Interactive mode**: Omit `-m` flag for interactive prompt

**ALL scripts have --help**: `./scripts/test-unit.sh --help`

**✨ NEW ENHANCEMENTS (all scripts):**
- ✅ Help flags (`--help`) with beautiful formatted documentation
- ✅ Execution timing (per-step and total elapsed time)
- ✅ Progress indicators (⏳ for long-running operations)
- ✅ Disk space validation (10-20GB warnings depending on script)
- ✅ Test failure summaries (shows which tests failed without reading logs)
- ✅ **Coverage table in build-ci.sh** (THE SHOWPIECE - professional table output)

**Key Features:**
- Automatic dependency compilation (no dependency tests)
- K8s scaling + Docker cleanup (component/coverage/CI scripts)
- Parallel execution for core (4 threads, hard-coded)
- Coverage warnings (dev scripts) vs strict failures (CI script)

---

## Direct Maven Commands (Advanced)

**Common Build Commands:**

```bash
# Clean compile (single module)
mvn clean compile -pl test-probe-core -q

# Clean compile all modules
mvn clean compile -q

# Test compile only
mvn test-compile -pl test-probe-core -q

# Install dependencies for downstream modules
mvn install -pl test-probe-common,test-probe-core -DskipTests -q

# Clean everything and rebuild
mvn clean install -DskipTests -q
```

**Test Execution:**

```bash
# Unit tests only (FAST)
mvn test -Punit-only -pl test-probe-core

# Component tests only
mvn test -Pcomponent-only -pl test-probe-core

# Component tests with 4-thread parallel (FASTER - 61% improvement)
mvn test -Pcomponent-only -pl test-probe-core \
  -Dcucumber.parallel.enabled=true \
  -Dcucumber.parallel.threads=4

# All tests
mvn test -pl test-probe-core

# With coverage (runs tests 1x with instrumentation)
mvn clean scoverage:report -pl test-probe-core
```

**Debugging Compilation Issues:**

1. **Clear /tmp logs before testing:**
   ```bash
   rm -f /tmp/*.log /tmp/*.txt
   ```

2. **Check for stale class files:**
   ```bash
   mvn clean -pl test-probe-core
   ```

3. **Verify dependencies installed:**
   ```bash
   mvn dependency:tree -pl test-probe-core
   ```

4. **Check test compilation separately:**
   ```bash
   mvn test-compile -pl test-probe-core
   ```

**Module Dependencies:**
- test-probe-common (foundation - no dependencies)
- test-probe-core (depends on: common)
- test-probe-glue (depends on: common, core)
- test-probe-services (depends on: common, core)
- test-probe-interfaces (depends on: common, core)

**Pro Tips:**
- Use `-q` flag for quieter output
- Use `-pl` to target specific modules
- Always compile with `-DskipTests` when installing dependencies
- Test profiles are your friend (unit-only for fast feedback)

**Ready to tackle build issues.**