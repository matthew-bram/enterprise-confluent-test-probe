# Test-Probe Troubleshooting Guide

Solutions to common issues when using Test-Probe for event-driven architecture testing against real Kafka clusters.

## Table of Contents

- [Kafka Connection Problems](#kafka-connection-problems)
- [Authentication Issues](#authentication-issues)
- [Schema Registry Errors](#schema-registry-errors)
- [Test Timeout Issues](#test-timeout-issues)
- [Test Failures](#test-failures)
- [Build and Compilation Issues](#build-and-compilation-issues)
- [Performance Issues](#performance-issues)

> **For Framework Contributors**: If you're developing Test-Probe itself and experiencing Docker/Testcontainers issues, see [Framework Testing Guide](../../docs/dev/FRAMEWORK-TESTING.md).

---

## Kafka Connection Problems

### Issue: "Connection refused to Kafka broker"

**Error Message**:
```
org.apache.kafka.common.errors.TimeoutException: Failed to update metadata after 60000 ms
```

**Cause**: Cannot connect to Kafka broker.

**Solutions**:

1. **Verify bootstrap servers configuration**:
   ```bash
   # Check your application.conf
   cat src/main/resources/application.conf | grep bootstrap-servers

   # Or check environment variable
   echo $KAFKA_BOOTSTRAP_SERVERS
   ```

2. **Test network connectivity**:
   ```bash
   # Check if broker is reachable
   nc -zv your-kafka-broker.com 9092

   # For Confluent Cloud
   nc -zv pkc-xxxxx.us-east-1.aws.confluent.cloud 9092
   ```

3. **Check firewall/security groups**:
   - Ensure port 9092 (or your configured port) is open
   - For AWS MSK: Check security group inbound rules
   - For Azure: Check network security groups

4. **Verify VPN/Network access**:
   - Ensure you're on the correct network/VPN
   - Check if the cluster requires private network access

5. **Test with Kafka CLI tools**:
   ```bash
   kafka-topics.sh --bootstrap-server your-broker:9092 --list
   ```

### Issue: "Topic not found"

**Error Message**:
```
org.apache.kafka.common.errors.UnknownTopicOrPartitionException: Topic 'my-topic' not found
```

**Cause**: Topic doesn't exist or you don't have permissions.

**Solutions**:

1. **Verify topic exists on cluster**:
   ```bash
   kafka-topics.sh --bootstrap-server your-broker:9092 --list | grep my-topic
   ```

2. **Check topic permissions**:
   - Ensure your credentials have read/write access to the topic
   - For Confluent Cloud: Check ACLs in the Confluent Cloud Console
   - For AWS MSK: Check IAM policies

3. **Create topic if needed**:
   ```bash
   kafka-topics.sh --bootstrap-server your-broker:9092 \
     --create --topic my-topic \
     --partitions 3 --replication-factor 3
   ```

4. **Check topic naming conventions**:
   - Some environments have topic naming restrictions
   - Ensure topic name matches what's registered in Schema Registry

---

## Authentication Issues

### Issue: "SASL authentication failed"

**Error Message**:
```
org.apache.kafka.common.errors.SaslAuthenticationException: Authentication failed
```

**Cause**: Invalid credentials or misconfigured SASL settings.

**Solutions**:

1. **Verify vault credentials**:
   ```bash
   # Check vault configuration
   echo $VAULT_PROVIDER
   echo $VAULT_SECRET_ID

   # Test vault access (AWS example)
   aws secretsmanager get-secret-value --secret-id kafka-credentials
   ```

2. **Check SASL mechanism**:
   ```hocon
   # Confluent Cloud uses PLAIN
   security {
     protocol = "SASL_SSL"
     sasl-mechanism = "PLAIN"
   }

   # AWS MSK can use SCRAM-SHA-512 or IAM
   security {
     protocol = "SASL_SSL"
     sasl-mechanism = "AWS_MSK_IAM"  # or "SCRAM-SHA-512"
   }
   ```

3. **Verify API key/secret format**:
   - Confluent Cloud: API key should be ~16 characters
   - Check for trailing whitespace or newlines in secrets

4. **Test authentication directly**:
   ```bash
   # Using kafka-console-consumer with SASL
   kafka-console-consumer.sh \
     --bootstrap-server your-broker:9092 \
     --topic test-topic \
     --consumer.config client.properties
   ```

### Issue: "SSL handshake failed"

**Error Message**:
```
javax.net.ssl.SSLHandshakeException: PKIX path building failed
```

**Cause**: SSL certificate validation issues.

**Solutions**:

1. **For Confluent Cloud/public clouds**: Certificates are typically trusted by default

2. **For self-managed clusters with custom CA**:
   ```hocon
   security {
     ssl {
       truststore-location = "/path/to/truststore.jks"
       truststore-password = "changeit"
     }
   }
   ```

3. **Verify certificate chain**:
   ```bash
   openssl s_client -connect your-broker:9092 -showcerts
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

3. **Check Schema Registry connection**:
   ```bash
   # Test Schema Registry connectivity
   curl https://your-schema-registry:8081/subjects
   # Should return: JSON list of registered subjects

   # For Confluent Cloud
   curl -u "$SR_API_KEY:$SR_API_SECRET" \
     https://psrc-xxxxx.us-east-1.aws.confluent.cloud/subjects
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
   kafka-console-consumer.sh \
     --bootstrap-server your-broker:9092 \
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
   kafka-console-consumer.sh \
     --bootstrap-server your-broker:9092 \
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

**Symptom**: Tests taking longer than expected.

**Solutions**:

1. **Check network latency**:
   - Ensure your test runner is in the same region as your Kafka cluster
   - Consider using a dedicated test cluster closer to CI/CD runners

2. **Reduce test timeouts**:
   - If events arrive quickly, reduce wait times in assertions
   - Use appropriate timeouts for your environment

3. **Run tests in parallel**:
   ```xml
   <configuration>
     <parallel>scenarios</parallel>
     <threadCount>4</threadCount>
   </configuration>
   ```

4. **Use faster test commands**:
   ```bash
   # Fast: Unit tests only (~30s)
   ./scripts/test-unit.sh -m core

   # Integration tests
   mvn test -Pintegration
   ```

5. **Optimize Kafka client settings**:
   - Adjust `linger.ms` and `batch.size` for producers
   - Use appropriate consumer fetch settings

### Issue: CI/CD builds timing out

**Symptom**: Builds exceed CI/CD timeout.

**Solutions**:

1. **Increase build timeout**:
   ```yaml
   # GitLab CI
   test:
     timeout: 20m
   ```

2. **Use a Kafka cluster close to CI/CD runners**:
   - Deploy test cluster in same region as CI/CD infrastructure
   - Consider dedicated CI/CD Kafka cluster

3. **Use faster runners**:
   ```yaml
   # GitLab CI
   test:
     tags:
       - high-cpu  # Runners with more resources
   ```

4. **Split test stages**:
   ```yaml
   stages:
     - unit-tests        # Fast
     - integration-tests # Slower
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
   ```

2. **Enable debug logging**:
   ```xml
   <!-- logback-test.xml -->
   <logger name="io.distia.probe" level="DEBUG"/>
   <logger name="org.apache.kafka" level="DEBUG"/>
   ```

3. **Search existing issues**: Check GitHub Issues for similar problems

4. **Open a new issue** with:
   - Test-Probe version
   - Java version (`java -version`)
   - Kafka cluster type (Confluent Cloud, AWS MSK, etc.)
   - Feature file (Gherkin scenario)
   - Complete error message and stack trace
   - Logs from `target/surefire-reports/`

5. **Ask the community**: Open a GitHub Discussion

---

**Related Documentation**:
- [Getting Started Guide](GETTING-STARTED.md)
- [FAQ](FAQ.md)
- [Build Scripts README](../../scripts/README.md)
- [Framework Testing Guide](../dev/FRAMEWORK-TESTING.md) (for contributors)
