# Tutorial 1: Your First Test

**Level:** Basic
**Duration:** 20 minutes
**Prerequisites:** Access to Test-Probe service, Kafka cluster, S3 bucket

---

## What You'll Learn

By the end of this tutorial, you will:

1. Write a simple Gherkin feature file
2. Create test configuration
3. Submit your test to the Test-Probe service
4. Poll for results and review evidence

## Expected Outcome

You'll execute an end-to-end test that produces a JSON event to Kafka, consumes it back, and validates the event payload matches—all through the Test-Probe REST API.

---

## Prerequisites Check

Before starting, verify you have access to:

```bash
# Test-Probe service is running
curl https://test-probe.example.com/api/v1/health
# Should return: {"status": "healthy", ...}

# You have AWS CLI configured for S3 access
aws s3 ls s3://your-test-bucket/

# Your Kafka cluster is accessible (Test-Probe handles this)
```

**What you need:**
- Test-Probe service URL (provided by your platform team)
- S3 bucket for test assets (with write access)
- Kafka cluster already configured in Test-Probe

---

## Step 1: Create Your Feature File

### 1.1 Write the Gherkin Specification

Create a file named `order-created.feature`:

```gherkin
Feature: Order Created Event Validation
  As a platform engineer
  I want to validate that OrderCreated events flow correctly through Kafka
  So that I can ensure my event-driven architecture works as expected

  Scenario: Produce and consume an OrderCreated event
    Given I have a topic "orders-created"
    When I produce a JSON event to topic "orders-created":
      """
      {
        "eventType": "OrderCreatedEvent",
        "eventVersion": "v1",
        "orderId": "order-123",
        "customerId": "customer-456",
        "amount": 99.99,
        "timestamp": 1701820800000
      }
      """
    Then I should receive an event from topic "orders-created" within 10 seconds
    And the event should match JSONPath "$.orderId" with value "order-123"
    And the event should match JSONPath "$.customerId" with value "customer-456"
    And the event should match JSONPath "$.amount" with value 99.99
```

**Key Points:**
- Use business language that describes the test intent
- Each scenario should be independent and self-contained
- Timeouts (e.g., "within 10 seconds") handle async nature of Kafka

---

## Step 2: Create Test Configuration

### 2.1 Write the Configuration File

Create a file named `test-config.yaml`:

```yaml
# Test-Probe Configuration
test-probe:
  # Kafka connection (references cluster configured in Test-Probe service)
  kafka:
    cluster: default  # Use the default cluster configured in Test-Probe

  # Test settings
  test:
    timeout-seconds: 60
    cleanup-topics: false  # Keep topics after test for debugging

  # Serialization
  serialization:
    key-format: cloudevents  # CloudEvents.io specification
    value-format: json       # JSON serialization

  # Consumer settings
  consumer:
    auto-offset-reset: earliest
    group-id-prefix: test-probe
```

---

## Step 3: Organize Your Test Assets

### 3.1 Directory Structure

Create the following structure locally:

```
my-first-test/
├── features/
│   └── order-created.feature
└── test-config.yaml
```

---

## Step 4: Submit Your Test

### 4.1 Initialize Test Session

Call the Test-Probe API to get a test ID:

```bash
# Initialize test session
RESPONSE=$(curl -sf -X POST "https://test-probe.example.com/api/v1/test/initialize" \
  -H "Content-Type: application/json")

# Extract test ID
TEST_ID=$(echo "$RESPONSE" | jq -r '.["test-id"]')
echo "Test ID: $TEST_ID"
```

**Response:**
```json
{
  "test-id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 4.2 Upload Test Assets to S3

Deploy your feature files and configuration to block storage:

```bash
# Set the S3 path using your test ID
S3_PATH="s3://your-test-bucket/tests/${TEST_ID}"

# Upload feature files
aws s3 cp ./my-first-test/features/ "${S3_PATH}/features/" --recursive

# Upload configuration
aws s3 cp ./my-first-test/test-config.yaml "${S3_PATH}/test-config.yaml"

echo "Deployed test assets to ${S3_PATH}"
```

**Verify upload:**
```bash
aws s3 ls "${S3_PATH}/" --recursive
# Should show:
# features/order-created.feature
# test-config.yaml
```

### 4.3 Start Test Execution

Trigger the test:

```bash
RESPONSE=$(curl -sf -X POST "https://test-probe.example.com/api/v1/test/start" \
  -H "Content-Type: application/json" \
  -d "{
    \"test-id\": \"${TEST_ID}\",
    \"block-storage-path\": \"${S3_PATH}\",
    \"test-type\": \"integration\"
  }")

echo "$RESPONSE" | jq .
```

**Response:**
```json
{
  "test-id": "550e8400-e29b-41d4-a716-446655440000",
  "accepted": true,
  "test-type": "integration"
}
```

---

## Step 5: Monitor Test Execution

### 5.1 Poll for Status

Check the test status:

```bash
curl -sf "https://test-probe.example.com/api/v1/test/${TEST_ID}/status" | jq .
```

**In Progress:**
```json
{
  "test-id": "550e8400-e29b-41d4-a716-446655440000",
  "state": "InProgress",
  "current-phase": "Executing",
  "progress-percent": 50
}
```

**Completed:**
```json
{
  "test-id": "550e8400-e29b-41d4-a716-446655440000",
  "state": "Completed",
  "current-phase": "Completed",
  "progress-percent": 100,
  "result": "All tests passed"
}
```

### 5.2 Simple Polling Script

Use this script to wait for completion:

```bash
#!/bin/bash
TEST_ID="$1"
MAX_WAIT=120
ELAPSED=0

while [ $ELAPSED -lt $MAX_WAIT ]; do
  RESPONSE=$(curl -sf "https://test-probe.example.com/api/v1/test/${TEST_ID}/status")
  STATE=$(echo "$RESPONSE" | jq -r '.state')
  PROGRESS=$(echo "$RESPONSE" | jq -r '.["progress-percent"]')

  echo "Status: ${STATE} (${PROGRESS}%)"

  if [ "$STATE" = "Completed" ]; then
    echo "Test passed!"
    exit 0
  elif [ "$STATE" = "Failed" ]; then
    echo "Test failed: $(echo $RESPONSE | jq -r '.error')"
    exit 1
  fi

  sleep 5
  ELAPSED=$((ELAPSED + 5))
done

echo "Timeout waiting for test completion"
exit 1
```

---

## Step 6: Review Evidence

### 6.1 Download Evidence

After the test completes, evidence is available in S3:

```bash
# Download evidence
mkdir -p ./evidence
aws s3 cp "${S3_PATH}/evidence/" ./evidence/ --recursive

# List downloaded files
ls -la ./evidence/
```

### 6.2 Evidence Structure

```
evidence/
└── cucumber.json    # Cucumber test execution report (JSON format)
```

The `cucumber.json` file contains detailed test results including:
- Scenario pass/fail status
- Step execution times
- Error messages and stack traces (if any)
- Feature and scenario metadata

### 6.3 View Results

**Parse the Cucumber JSON report:**
```bash
# Check if tests passed (all scenarios have "passed" status)
cat ./evidence/cucumber.json | jq '.[].elements[].steps[].result.status' | grep -v passed && echo "FAILED" || echo "PASSED"
```

**Extract scenario results:**
```bash
cat ./evidence/cucumber.json | jq '[.[].elements[] | {name: .name, status: (.steps | map(.result.status) | if all(. == "passed") then "passed" else "failed" end)}]'
```

```json
[
  {
    "name": "Produce and consume a simple event",
    "status": "passed"
  }
]
```

---

## Troubleshooting Common Issues

### Issue 1: Test-Probe Service Unavailable

**Symptom:**
```
curl: (7) Failed to connect to test-probe.example.com
```

**Fix:**
- Verify Test-Probe service URL is correct
- Check network connectivity / VPN
- Contact your platform team to verify service status

---

### Issue 2: S3 Upload Fails

**Symptom:**
```
An error occurred (AccessDenied) when calling the PutObject operation
```

**Fix:**
- Verify AWS credentials are configured
- Check IAM permissions for the S3 bucket
- Ensure bucket name is correct

---

### Issue 3: Test Start Rejected

**Symptom:**
```json
{"accepted": false, "message": "Invalid block-storage-path"}
```

**Fix:**
- Ensure `block-storage-path` starts with `s3://`
- Verify the path matches where you uploaded files
- Check that test-config.yaml is present in the path

---

### Issue 4: Test Timeout

**Symptom:** Test stays in "InProgress" state indefinitely

**Fix:**
- Check Test-Probe logs (contact platform team)
- Verify Kafka cluster is accessible from Test-Probe
- Review your feature file for long timeout values
- Cancel the test: `curl -X DELETE "https://test-probe.example.com/api/v1/test/${TEST_ID}"`

---

### Issue 5: Event Not Found (Assertion Failure)

**Symptom:**
```json
{"state": "Failed", "error": "Timeout waiting for events on topic orders-created"}
```

**Fix:**
- Increase the timeout in your feature file (e.g., "within 30 seconds")
- Verify the topic name matches exactly
- Check that your Kafka cluster is configured correctly in Test-Probe

---

## Summary

Congratulations! You've completed your first Test-Probe test. You learned how to:

- Write Gherkin feature files for event-driven testing
- Create test configuration for Test-Probe
- Use the Test-Probe REST API to submit and monitor tests
- Review test evidence and reports

---

## Next Steps

Ready to level up? Try these follow-on tutorials:

1. **[Tutorial 2: Working with JSON Events](02-json-events.md)** - Advanced JSON matching, schema validation, and event patterns
2. **[Tutorial 3: Avro and Protobuf Serialization](03-avro-protobuf.md)** - Use schema-based serialization for production scenarios
3. **[Tutorial 4: Multi-Cluster Testing](04-multi-cluster.md)** - Test event flows across multiple Kafka clusters

---

## Additional Resources

- **[CI/CD Pipeline Integration](../integration/ci-cd-pipelines.md)** - Automate tests in your pipelines
- **[CloudEvents Specification](https://cloudevents.io/)** - Industry standard event envelope
- **[API Reference](../../../test-probe-interfaces/src/main/resources/openapi.yaml)** - Complete OpenAPI specification

---

**Document Version:** 2.0.0
**Last Updated:** 2025-12-05
