# Test-Probe Troubleshooting Guide

Solutions to common issues when using Test-Probe for event-driven architecture testing.

## Table of Contents

- [Docker and Testcontainers Issues](#docker-and-testcontainers-issues)
- [Kafka Connection Problems](#kafka-connection-problems)
- [Schema Registry Errors](#schema-registry-errors)
- [Test Timeout Issues](#test-timeout-issues)
- [Test Failures](#test-failures)
- [Build and Compilation Issues](#build-and-compilation-issues)
- [Performance Issues](#performance-issues)

---

## Docker and Testcontainers Issues

### Issue: "Cannot connect to Docker daemon"

**Error Message**:
```
org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy
Cannot connect to the Docker daemon at unix:///var/run/docker.sock
```

**Cause**: Docker daemon is not running or not accessible.

**Solutions**:

<details>
<summary><strong>macOS with Docker Desktop</strong></summary>

1. **Check Docker Desktop is running**:
   - Look for Docker whale icon in menu bar
   - If not running: Launch Docker Desktop from Applications

2. **Verify Docker is accessible**:
   ```bash
   docker ps
   # Expected: List of containers (or empty list)
   # Error: "Cannot connect..." means Docker isn't running
   ```

3. **Restart Docker Desktop**:
   - Click Docker icon → Quit Docker Desktop
   - Relaunch Docker Desktop
   - Wait for "Docker Desktop is running" message
</details>

<details>
<summary><strong>macOS with Colima</strong></summary>

1. **Start Colima**:
   ```bash
   colima start
   ```

2. **Check status**:
   ```bash
   colima status
   # Expected: "colima is running"
   ```

3. **Restart if needed**:
   ```bash
   colima restart
   ```
</details>

<details>
<summary><strong>Linux</strong></summary>

1. **Check Docker service**:
   ```bash
   sudo systemctl status docker
   ```

2. **Start Docker service**:
   ```bash
   sudo systemctl start docker
   ```

3. **Enable Docker on boot**:
   ```bash
   sudo systemctl enable docker
   ```

4. **Add user to docker group** (avoid sudo):
   ```bash
   sudo usermod -aG docker $USER
   newgrp docker
   ```
</details>

### Issue: "Testcontainers timing out"

**Error Message**:
```
org.testcontainers.containers.ContainerLaunchException: Timed out waiting for container port to open (localhost:32768 should be listening)
```

**Cause**: Container startup taking longer than timeout (usually due to resource constraints).

**Solutions**:

1. **Increase Docker RAM allocation**:
   ```
   Docker Desktop → Preferences → Resources
   - RAM: 8GB minimum, 16GB recommended
   - CPU: 4+ cores
   - Disk: 20GB free space
   ```

2. **Scale down local Kubernetes** (if running):
   ```bash
   ./scripts/k8s-scale.sh down
   ```

3. **Increase test timeout** (in your test code):
   ```java
   // Java
   @Timeout(value = 5, unit = TimeUnit.MINUTES)
   @Test
   public void myTest() { ... }
   ```

   ```scala
   // Scala
   implicit val patience: PatienceConfig = PatienceConfig(
     timeout = Span(5, Minutes),
     interval = Span(500, Millis)
   )
   ```

4. **Pre-pull Docker images** (speeds up first run):
   ```bash
   docker pull confluentinc/cp-kafka:7.6.0
   docker pull confluentinc/cp-schema-registry:7.6.0
   ```

5. **Restart Docker**:
   ```bash
   # Docker Desktop: Click Docker icon → Restart
   # Colima:
   colima restart
   ```

### Issue: "Port already in use"

**Error Message**:
```
Bind for 0.0.0.0:9092 failed: port is already allocated
```

**Cause**: Port 9092 (or other Kafka ports) already in use by another process.

**Solutions**:

1. **Find process using port**:
   ```bash
   # macOS/Linux
   lsof -i :9092

   # Windows
   netstat -ano | findstr :9092
   ```

2. **Stop conflicting process**:
   ```bash
   # macOS/Linux
   kill -9 <PID>

   # Stop local Kafka if running
   ./scripts/k8s-scale.sh down
   ```

3. **Let Testcontainers use random ports** (default behavior):
   - Testcontainers automatically assigns available ports
   - No configuration needed

4. **Clean up orphaned containers**:
   ```bash
   docker ps -a --filter "label=org.testcontainers" --format "{{.ID}}" | xargs docker rm -f
   ```

### Issue: "Out of disk space"

**Error Message**:
```
no space left on device
```

**Cause**: Docker has consumed all available disk space with images/containers.

**Solutions**:

1. **Check Docker disk usage**:
   ```bash
   docker system df
   ```

2. **Clean up unused resources**:
   ```bash
   # Remove stopped containers, unused networks, dangling images
   docker system prune -f

   # Remove ALL unused images (more aggressive)
   docker system prune -a -f

   # Remove volumes (WARNING: deletes data)
   docker volume prune -f
   ```

3. **Increase Docker disk allocation**:
   ```
   Docker Desktop → Preferences → Resources → Disk image size
   - Increase to 60GB+ for development
   ```

4. **Clean build artifacts**:
   ```bash
   cd test-probe
   mvn clean
   rm -rf target/
   ```

---

## Kafka Connection Problems

### Issue: "Connection refused to Kafka broker"

**Error Message**:
```
org.apache.kafka.common.errors.TimeoutException: Failed to update metadata after 60000 ms
```

**Cause**: Cannot connect to Kafka broker (usually means broker didn't start).

**Solutions**:

1. **Check Testcontainers started Kafka**:
   ```bash
   docker ps
   # Look for: confluentinc/cp-kafka
   ```

2. **Check container logs**:
   ```bash
   docker logs <kafka-container-id>
   # Look for errors like:
   # - "FATAL: Insufficient memory"
   # - "ERROR: Failed to bind to port"
   ```

3. **Verify Docker resources**:
   ```bash
   docker system info | grep "Total Memory"
   # Should be: 8GB+ available
   ```

4. **Wait for Kafka to be ready**:
   - Kafka takes 20-40 seconds to start
   - Increase `KAFKA_STARTUP_TIMEOUT` if needed

5. **Check bootstrap servers configuration**:
   ```gherkin
   # Correct: Use Testcontainers-provided address
   Given I have a topic "test-events"

   # Incorrect: Hardcoded localhost
   Given I have a topic "test-events" with bootstrap servers "localhost:9092"
   ```

### Issue: "Topic not found"

**Error Message**:
```
org.apache.kafka.common.errors.UnknownTopicOrPartitionException: Topic 'my-topic' not found
```

**Cause**: Topic doesn't exist or auto-create disabled.

**Solutions**:

1. **Use Test-Probe topic creation step**:
   ```gherkin
   Given I have a topic "my-topic"
   ```

2. **Enable auto-create in Kafka** (Testcontainers default):
   ```bash
   # Check container env vars
   docker inspect <kafka-container> | grep AUTO_CREATE_TOPICS
   # Should be: KAFKA_AUTO_CREATE_TOPICS_ENABLE=true
   ```

3. **Create topic manually** (in test setup):
   ```java
   AdminClient admin = AdminClient.create(props);
   NewTopic topic = new NewTopic("my-topic", 1, (short) 1);
   admin.createTopics(Collections.singletonList(topic));
   ```

### Issue: "Consumer group rebalance timeout"

**Error Message**:
```
org.apache.kafka.clients.consumer.CommitFailedException: Commit cannot be completed since the group has already rebalanced
```

**Cause**: Consumer took too long to process messages, triggering rebalance.

**Solutions**:

1. **Increase consumer timeout**:
   ```properties
   # application.conf
   kafka {
     consumer {
       max.poll.interval.ms = 300000  # 5 minutes
       session.timeout.ms = 30000     # 30 seconds
     }
   }
   ```

2. **Reduce processing time** in step definitions

3. **Use dedicated consumer group** per test:
   - Test-Probe automatically uses unique consumer groups
   - Format: `test-probe-{testId}`

---

## Schema Registry Errors

### Issue: "Schema not found"

**Error Message**:
```
io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException: Subject 'my-topic-value' not found
```

**Cause**: Avro/Protobuf schema not registered with Schema Registry.

**Solutions**:

1. **Register schema in test setup**:
   ```gherkin
   Background:
     Given CloudEvent Avro key schema is registered for topic "my-topic"
     And MyEvent Avro value schema is registered for topic "my-topic"
   ```

2. **Verify schema files exist**:
   ```bash
   # Avro schemas
   ls src/main/avro/*.avsc

   # Protobuf schemas
   ls src/main/proto/*.proto
   ```

3. **Check Schema Registry is running**:
   ```bash
   docker ps | grep schema-registry
   # Should show: confluentinc/cp-schema-registry
   ```

4. **Test Schema Registry connection**:
   ```bash
   curl http://localhost:8081/subjects
   # Should return: JSON list of registered subjects
   ```

### Issue: "Schema compatibility error"

**Error Message**:
```
io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException: Schema being registered is incompatible with an earlier schema
```

**Cause**: New schema violates compatibility rules (e.g., removed required field).

**Solutions**:

1. **Check compatibility mode**:
   ```bash
   curl http://localhost:8081/config
   # Returns: {"compatibilityLevel": "BACKWARD"}
   ```

2. **Update compatibility mode** (if needed):
   ```bash
   # Set to NONE (for testing only!)
   curl -X PUT http://localhost:8081/config \
     -H "Content-Type: application/json" \
     -d '{"compatibility": "NONE"}'
   ```

3. **Fix schema compatibility**:
   - BACKWARD: New schema can read old data (add optional fields only)
   - FORWARD: Old schema can read new data (remove optional fields only)
   - FULL: Both BACKWARD and FORWARD

4. **Use versioned schemas** in tests:
   ```gherkin
   Scenario: Test schema evolution
     Given MyEvent schema version 1 is registered for topic "events"
     And MyEvent schema version 2 is registered for topic "events"
   ```

---

## Test Timeout Issues

### Issue: "Test times out waiting for event"

**Error Message**:
```
java.util.concurrent.TimeoutException: Timed out after 10 seconds waiting for event
```

**Cause**: Event not produced/consumed within timeout period.

**Solutions**:

1. **Increase timeout**:
   ```gherkin
   # Increase from 10 to 30 seconds
   Then I should receive an event from topic "my-topic" within 30 seconds
   ```

2. **Verify event was produced**:
   ```bash
   # Check Kafka topic contents
   docker exec <kafka-container> kafka-console-consumer \
     --bootstrap-server localhost:9092 \
     --topic my-topic \
     --from-beginning \
     --timeout-ms 5000
   ```

3. **Check producer succeeded**:
   ```gherkin
   # Add explicit verification
   When I produce event {"test": "data"} to topic "my-topic"
   And the event should be acknowledged
   ```

4. **Verify topic exists**:
   ```gherkin
   Given I have a topic "my-topic"
   # Add this BEFORE producing events
   ```

5. **Check consumer configuration**:
   - Ensure `auto.offset.reset=earliest`
   - Verify consumer group is unique per test

### Issue: "Testcontainer startup timeout"

**Error Message**:
```
org.testcontainers.containers.ContainerLaunchException: Timed out waiting for log output matching '.*started.*'
```

**Cause**: Kafka container taking too long to start (resource constraints).

**Solutions**:

1. **Allocate more resources to Docker** (see Docker section above)

2. **Pre-pull images**:
   ```bash
   docker pull confluentinc/cp-kafka:7.6.0
   docker pull confluentinc/cp-schema-registry:7.6.0
   ```

3. **Increase startup timeout** (in test configuration):
   ```java
   KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
     .withStartupTimeout(Duration.ofMinutes(3));
   ```

4. **Scale down competing resources**:
   ```bash
   ./scripts/k8s-scale.sh down
   ```

---

## Test Failures

### Issue: "JSONPath matching failed"

**Error Message**:
```
AssertionError: Expected JSONPath '$.status' to match 'completed', but was 'pending'
```

**Cause**: Event content doesn't match expected value.

**Solutions**:

1. **Print actual event** (for debugging):
   ```gherkin
   Then I should receive an event from topic "my-topic" within 10 seconds
   And I print the event  # Add debugging step
   ```

2. **Check JSONPath syntax**:
   ```gherkin
   # Correct: Use $ root and dot notation
   And the event should match JSONPath "$.status" with value "completed"

   # Incorrect: Missing $
   And the event should match JSONPath "status" with value "completed"
   ```

3. **Verify event structure**:
   ```bash
   # Consume raw event from Kafka
   docker exec <kafka-container> kafka-console-consumer \
     --bootstrap-server localhost:9092 \
     --topic my-topic \
     --from-beginning \
     --max-messages 1
   ```

4. **Use pattern matching** (for flexible matching):
   ```gherkin
   # Instead of exact match
   And the event should match JSONPath "$.timestamp" with pattern "^2025-.*"
   ```

### Issue: "Expected N events but received M"

**Error Message**:
```
AssertionError: Expected exactly 5 events, but received 3
```

**Cause**: Not all expected events were produced or consumed.

**Solutions**:

1. **Increase timeout**:
   ```gherkin
   # Give more time for async processing
   Then I should receive exactly 5 events from topic "my-topic" within 60 seconds
   ```

2. **Verify all events produced**:
   ```gherkin
   When I produce the following events to topic "my-topic":
     | eventId |
     | evt-001 |
     | evt-002 |
     | evt-003 |
     | evt-004 |
     | evt-005 |
   And all events should be acknowledged
   ```

3. **Check consumer offset**:
   - Ensure `auto.offset.reset=earliest`
   - Verify consumer is reading from beginning of topic

4. **Verify partition count**:
   ```gherkin
   # All events go to single partition (guaranteed order)
   Given I have a topic "my-topic" with 1 partition
   ```

### Issue: "Step definition not found"

**Error Message**:
```
io.cucumber.junit.UndefinedStepException: The step "I have a topic 'my-topic'" is undefined
```

**Cause**: Step definition not found by Cucumber (classpath or glue path issue).

**Solutions**:

1. **Verify glue path**:
   ```java
   @CucumberOptions(
     glue = {"io.distia.probe.glue"}  // Must include Test-Probe glue package
   )
   ```

2. **Check dependencies**:
   ```xml
   <dependency>
     <groupId>io.distia.probe</groupId>
     <artifactId>test-probe-glue</artifactId>
     <version>0.0.1-SNAPSHOT</version>
     <scope>test</scope>
   </dependency>
   ```

3. **Run with Maven** (not IDE):
   ```bash
   mvn test
   # IDE runners may not scan classpath correctly
   ```

4. **Add custom glue path** (if using custom steps):
   ```java
   @CucumberOptions(
     glue = {"io.distia.probe.glue", "com.example.steps"}
   )
   ```

---

## Build and Compilation Issues

### Issue: "Scala compiler errors"

**Error Message**:
```
[ERROR] value X is not a member of Y
```

**Cause**: Incorrect Scala version or missing dependencies.

**Solutions**:

1. **Verify Scala version**:
   ```xml
   <scala.version>3.3.6</scala.version>
   <scala.binary.version>3</scala.binary.version>
   ```

2. **Clean and rebuild**:
   ```bash
   mvn clean install -DskipTests
   ```

3. **Check Maven plugins**:
   ```xml
   <plugin>
     <groupId>net.alchim31.maven</groupId>
     <artifactId>scala-maven-plugin</artifactId>
     <version>4.8.1</version>
   </plugin>
   ```

### Issue: "Maven dependency resolution failure"

**Error Message**:
```
[ERROR] Failed to execute goal on project test-probe-core: Could not resolve dependencies
```

**Cause**: Dependencies not found in Maven repositories.

**Solutions**:

1. **Update Maven repositories**:
   ```bash
   mvn clean install -U
   # -U forces update of snapshots
   ```

2. **Check Maven settings** (`~/.m2/settings.xml`):
   ```xml
   <repositories>
     <repository>
       <id>central</id>
       <url>https://repo.maven.apache.org/maven2</url>
     </repository>
   </repositories>
   ```

3. **Install Test-Probe locally**:
   ```bash
   cd test-probe
   mvn clean install -DskipTests
   ```

---

## Performance Issues

### Issue: Tests running slowly

**Symptom**: Tests taking 2x-3x longer than expected.

**Solutions**:

1. **Check Docker resources**:
   ```bash
   docker system info | grep "Total Memory"
   # Should be: 8GB+ (16GB recommended)
   ```

2. **Scale down Kubernetes**:
   ```bash
   ./scripts/k8s-scale.sh down
   ```

3. **Pre-pull images**:
   ```bash
   docker pull confluentinc/cp-kafka:7.6.0
   docker pull confluentinc/cp-schema-registry:7.6.0
   ```

4. **Use faster test commands**:
   ```bash
   # Fast: Unit tests only (~30s)
   ./scripts/test-unit.sh -m core

   # Medium: Component tests (~3min)
   ./scripts/test-component.sh -m core

   # Slow: All tests (~5min)
   ./scripts/test-all.sh -m core
   ```

5. **Run tests in parallel** (requires 16GB RAM):
   ```xml
   <configuration>
     <parallel>scenarios</parallel>
     <threadCount>4</threadCount>
   </configuration>
   ```

### Issue: CI/CD builds timing out

**Symptom**: Builds exceed CI/CD timeout (e.g., 15 minutes).

**Solutions**:

1. **Increase build timeout**:
   ```yaml
   # GitLab CI
   test:
     timeout: 20m
   ```

2. **Cache Docker images**:
   ```yaml
   # GitLab CI
   before_script:
     - docker pull confluentinc/cp-kafka:7.6.0 || true
   ```

3. **Use faster runners**:
   ```yaml
   # GitLab CI
   test:
     tags:
       - docker
       - high-cpu  # Runners with more resources
   ```

4. **Split test stages**:
   ```yaml
   stages:
     - unit-tests     # Fast (2-3 min)
     - component-tests  # Medium (5-7 min)
     - integration-tests  # Slow (10-15 min)
   ```

---

## Still Having Issues?

If you couldn't find a solution here:

1. **Check logs**:
   ```bash
   # Test logs
   cat target/surefire-reports/*.txt

   # Cucumber logs
   cat target/cucumber-reports/cucumber.json

   # Docker logs
   docker logs <container-id>
   ```

2. **Enable debug logging**:
   ```xml
   <!-- logback-test.xml -->
   <logger name="io.distia.probe" level="DEBUG"/>
   <logger name="org.testcontainers" level="DEBUG"/>
   ```

3. **Search existing issues**: Check GitHub Issues for similar problems

4. **Open a new issue** with:
   - Test-Probe version
   - Java version (`java -version`)
   - Docker version (`docker --version`)
   - Docker RAM allocation
   - Feature file (Gherkin scenario)
   - Complete error message and stack trace
   - Logs from `target/surefire-reports/`

5. **Ask the community**: Open a GitHub Discussion

---

**Related Documentation**:
- [Getting Started Guide](GETTING-STARTED.md)
- [FAQ](FAQ.md)
- [Build Scripts README](../../scripts/README.md)
- [Kafka Environment Setup](../testing-practice/kafka-env-setup.md)
