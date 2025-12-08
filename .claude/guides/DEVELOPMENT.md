# Development Guide

This guide covers common development tasks, configuration, deployment, and troubleshooting for the Test-Probe project.

---

## Common Tasks

### Adding a New REST Endpoint

1. Add route in `test-probe-interface-rest/src/main/scala/.../http/ProbeRoutes.scala`
2. Define request/response models in `test-probe-common/.../models/http/`
3. Add spray-json formats in `test-probe-common/.../models/http/HttpFormats.scala`
4. Create curried function in `test-probe-core/.../service/CurriedFunctions.scala`
5. Add endpoint spec in `test-probe-interface-rest/src/test/scala/.../component/api/`

---

## Configuration Management

### Configuration Files

- **`src/main/resources/reference.conf`**: Default configuration (library defaults)
- **`src/test/resources/application.conf`**: Test overrides
- **Environment variables**: Override via `${?ENV_VAR}` syntax

### Configuration Structure

```hocon
test-probe {
  server {
    host = "0.0.0.0"
    port = 8080
    port = ${?HTTP_PORT}  # Environment variable override
  }

  kafka {
    bootstrap-servers = "localhost:9092"
    bootstrap-servers = ${?KAFKA_BOOTSTRAP_SERVERS}
    producer.acks = "all"
    consumer.auto-offset-reset = "earliest"
  }

  storage {
    type = "s3"  # or "local"
    s3 {
      bucket = ${?S3_BUCKET}
      region = "us-east-1"
    }
  }

  execution {
    max-concurrent-tests = 10
    test-timeout = 600s
    queue-capacity = 10000
  }
}
```

### Secrets Management

- **Never hardcode secrets**: Use Vault integration or environment variables
- **Fallback hierarchy**: Vault → Environment Variables → Fail
- **Logging**: Secrets are masked in all logs

---

## Important Patterns and Conventions

### Error Handling

- Use typed errors: `ProbeException` hierarchy
- All exceptions include: error code, message, optional cause
- Serializable for actor message passing
- Map error codes to HTTP status codes

### JSON Serialization

**Dual library strategy**:
- **Circe**: Internal actor messages, type-safe domain models
- **Spray JSON**: REST API (Akka HTTP integration)

### Service Interface Functions

The framework provides curried functions for common operations:

```scala
// Curried function signature
type SubmitTestFunction = String => String => Future[ServiceResponse]

// Usage
val submitTest: SubmitTestFunction = curriedFunctions.submitTest
submitTest(testId)(bucket).map(response => ...)
```

**Pattern**: Enables partial application and function composition for REST routes.

### Dependency Injection

Use constructor injection with factory pattern:

```scala
class MyClass(adapter: Adapter)

object MyClass {
  def apply(adapter: Option[Adapter] = None): MyClass = adapter match {
    case Some(a) => new MyClass(a)
    case None    => new MyClass(new DefaultAdapter)
  }
}
```

**Use sparingly**: Only when mocking is important for testing.

---

## Scala Code Conventions

### Package Structure

Use stacked package declarations (not dotted):

```scala
// Correct
package io.distia.probe
package core
package actors

import actors.MyActor  // Shorter imports

// Incorrect
package io.distia.probe.core.actors
```

### Explicit Types Required

All variables must have explicit types:

```scala
// Correct
val count: Int = 5
val config: Config = ConfigFactory.load()

// Incorrect
val count = 5
val config = ConfigFactory.load()
```

### Functional Style Everywhere

- **Never use nulls**: Always use `Option[T]`
- **No if/else**: Use `.fold()`, `.map()`, `.isDefined`, `.isEmpty`
- **No Thread.sleep()**: Use `Future` and proper async patterns
- **Prefer `Future[T]`**: Non-blocking bias for all operations

```scala
// Good
option.fold(defaultValue)(value => transform(value))

// Bad
if (option.isDefined) transform(option.get) else defaultValue
```

### Logging

- **Structured logging only**: JSON format with trace IDs (OpenTelemetry)
- **Production-ready logs**: Verbose where needed, semantic
- **Placement**: Only at state changes, errors, external calls
- **No debug logging**: Remove debugging logs after fixing issues

---

## Deployment Models

### Standalone JAR

```bash
# Build uber JAR
mvn clean package -DskipTests

# Run
java -jar target/test-probe-boot-0.0.1-SNAPSHOT.jar
```

### Docker Container

```bash
# Build Docker image (Dockerfile in root)
docker build -t test-probe:latest .

# Run with environment variables
docker run -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e S3_BUCKET=evidence-bucket \
  -p 8080:8080 \
  test-probe:latest
```

### Local Development (Docker Compose)

```bash
# Start all services (Kafka, LocalStack, Vault, Test-Probe)
docker-compose up

# Test-Probe available at http://localhost:8080
```

---

## Troubleshooting

### Common Issues

**Compilation Errors**:
```bash
mvn clean compile -DskipTests
```

**Test Failures**:
```bash
# Run single test with debug
mvn test -Dtest=FailingSpec -X

# Check Testcontainers (component tests)
docker ps | grep kafka
docker logs <container-id>
```

**Dependency Issues**:
```bash
mvn dependency:purge-local-repository
mvn clean compile
```

**Actor System Issues**:
- Check `application.conf` for correct Akka configuration
- Verify actor hierarchy in logs during startup
- Use `ActorTestKit` for isolated actor testing

---

## Related Guides

- **Build Commands**: See `.claude/guides/BUILD.md` for Maven commands
- **Testing**: See `.claude/guides/TESTING.md` for testing practices
- **Actors**: See `.claude/guides/ACTORS.md` for actor patterns
- **Architecture**: See `.claude/guides/ARCHITECTURE.md` for system design
- **Style Conventions**: See `.claude/styles/scala-conventions.md` for code style