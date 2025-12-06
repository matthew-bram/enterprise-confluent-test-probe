# CI/CD Pipeline Integration

**Version:** 2.0.0
**Last Updated:** 2025-12-05
**Target Audience:** DevOps engineers and developers integrating Test-Probe into automated pipelines

---

Test-Probe exposes a REST API that CI/CD pipelines interact with to orchestrate test execution. This guide covers the integration workflow and provides examples for common CI platforms.

## Table of Contents

1. [Integration Overview](#integration-overview)
2. [API Flow](#api-flow)
3. [GitHub Actions](#github-actions)
4. [GitLab CI](#gitlab-ci)
5. [Jenkins Pipeline](#jenkins-pipeline)
6. [Quality Gates](#quality-gates)
7. [Evidence Collection](#evidence-collection)
8. [Troubleshooting](#troubleshooting)

---

## Integration Overview

Test-Probe runs as a service (Docker container or Kubernetes deployment) that your CI/CD pipeline communicates with via REST API. The pipeline:

1. Initializes a test session
2. Deploys test assets to block storage
3. Triggers test execution
4. Polls for completion
5. Acts on the result (quality gate)

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│   CI/CD         │         │   Test-Probe    │         │   Your Kafka    │
│   Pipeline      │◀───────▶│   Service       │◀───────▶│   Cluster       │
└─────────────────┘  REST   └─────────────────┘  Kafka  └─────────────────┘
        │                            │
        │                            │
        ▼                            ▼
┌─────────────────┐         ┌─────────────────┐
│   S3 / Block    │◀────────│   Evidence      │
│   Storage       │  Upload │   + Results     │
└─────────────────┘         └─────────────────┘
```

### Prerequisites

- Test-Probe service deployed and accessible from CI runners
- Block storage configured (S3, Azure Blob, or GCS)
- Network access from CI runners to Test-Probe and block storage
- Test assets: feature files (`.feature`) and configuration (`test-config.yaml`)

---

## API Flow

### Step 1: Initialize Test

```bash
curl -X POST https://test-probe.example.com/api/v1/test/initialize \
  -H "Content-Type: application/json"
```

**Response:**
```json
{
  "test-id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Step 2: Deploy Test Assets

Upload your feature files and configuration to the block storage path using the test ID:

```bash
# Create directory structure
aws s3 cp ./features/ s3://test-bucket/tests/${TEST_ID}/features/ --recursive
aws s3 cp ./test-config.yaml s3://test-bucket/tests/${TEST_ID}/test-config.yaml
```

**Expected structure:**
```
s3://test-bucket/tests/{test-id}/
├── features/
│   ├── order-processing.feature
│   └── inventory-sync.feature
└── test-config.yaml
```

### Step 3: Start Test Execution

```bash
curl -X POST https://test-probe.example.com/api/v1/test/start \
  -H "Content-Type: application/json" \
  -d '{
    "test-id": "550e8400-e29b-41d4-a716-446655440000",
    "block-storage-path": "s3://test-bucket/tests/550e8400-e29b-41d4-a716-446655440000",
    "test-type": "integration"
  }'
```

**Response:**
```json
{
  "test-id": "550e8400-e29b-41d4-a716-446655440000",
  "accepted": true,
  "test-type": "integration"
}
```

### Step 4: Poll for Status

```bash
curl https://test-probe.example.com/api/v1/test/550e8400-e29b-41d4-a716-446655440000/status
```

**Test FSM States:**
- `Setup` - Test initialized, awaiting start
- `Loading` - Loading test bundle from block storage
- `Loaded` - Test loaded and ready for execution
- `Testing` - Test currently executing
- `Completed` - Test completed (check `success` field)
- `Exception` - Test failed with error

**Loading (test bundle being fetched):**
```json
{
  "test-id": "550e8400-e29b-41d4-a716-446655440000",
  "state": "Loading",
  "bucket": "s3://test-bucket/tests/550e8400-e29b-41d4-a716-446655440000",
  "test-type": "integration"
}
```

**Testing (execution in progress):**
```json
{
  "test-id": "550e8400-e29b-41d4-a716-446655440000",
  "state": "Testing",
  "bucket": "s3://test-bucket/tests/550e8400-e29b-41d4-a716-446655440000",
  "test-type": "integration",
  "start-time": "2025-12-05T14:30:00Z"
}
```

**Completed (tests passed):**
```json
{
  "test-id": "550e8400-e29b-41d4-a716-446655440000",
  "state": "Completed",
  "bucket": "s3://test-bucket/tests/550e8400-e29b-41d4-a716-446655440000",
  "test-type": "integration",
  "start-time": "2025-12-05T14:30:00Z",
  "end-time": "2025-12-05T14:32:15Z",
  "success": true
}
```

**Completed (tests failed):**
```json
{
  "test-id": "550e8400-e29b-41d4-a716-446655440000",
  "state": "Completed",
  "bucket": "s3://test-bucket/tests/550e8400-e29b-41d4-a716-446655440000",
  "test-type": "integration",
  "start-time": "2025-12-05T14:30:00Z",
  "end-time": "2025-12-05T14:31:45Z",
  "success": false,
  "error": "2 scenarios failed - see evidence for details"
}
```

**Exception (execution error):**
```json
{
  "test-id": "550e8400-e29b-41d4-a716-446655440000",
  "state": "Exception",
  "bucket": "s3://test-bucket/tests/550e8400-e29b-41d4-a716-446655440000",
  "test-type": "integration",
  "start-time": "2025-12-05T14:30:00Z",
  "end-time": "2025-12-05T14:31:45Z",
  "success": false,
  "error": "Timeout waiting for events on topic orders-created"
}
```

### Step 5: Collect Evidence

After completion, evidence is available at the same block storage path:

```
s3://test-bucket/tests/{test-id}/
├── features/              # Your test assets (input)
├── topic-directives.yml   # Your Kafka topic configuration (input)
└── evidence/              # Generated evidence (output)
    └── cucumber.json      # Cucumber test execution report
```

---

## GitHub Actions

### Complete Workflow

`.github/workflows/test-probe.yml`:

```yaml
name: Test-Probe Integration Tests

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

env:
  TEST_PROBE_URL: ${{ secrets.TEST_PROBE_URL }}
  S3_BUCKET: ${{ secrets.TEST_ASSETS_BUCKET }}

jobs:
  integration-test:
    name: Run Integration Tests
    runs-on: ubuntu-latest
    environment: integration

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: us-east-1

      - name: Initialize Test Session
        id: init
        run: |
          RESPONSE=$(curl -sf -X POST "${TEST_PROBE_URL}/api/v1/test/initialize" \
            -H "Content-Type: application/json")
          TEST_ID=$(echo "$RESPONSE" | jq -r '.["test-id"]')
          echo "test-id=${TEST_ID}" >> $GITHUB_OUTPUT
          echo "Initialized test session: ${TEST_ID}"

      - name: Deploy Test Assets
        run: |
          TEST_ID="${{ steps.init.outputs.test-id }}"
          S3_PATH="s3://${S3_BUCKET}/tests/${TEST_ID}"

          # Upload feature files
          aws s3 cp ./tests/features/ "${S3_PATH}/features/" --recursive

          # Upload configuration
          aws s3 cp ./tests/test-config.yaml "${S3_PATH}/test-config.yaml"

          echo "Deployed test assets to ${S3_PATH}"

      - name: Start Test Execution
        run: |
          TEST_ID="${{ steps.init.outputs.test-id }}"
          S3_PATH="s3://${S3_BUCKET}/tests/${TEST_ID}"

          RESPONSE=$(curl -sf -X POST "${TEST_PROBE_URL}/api/v1/test/start" \
            -H "Content-Type: application/json" \
            -d "{
              \"test-id\": \"${TEST_ID}\",
              \"block-storage-path\": \"${S3_PATH}\",
              \"test-type\": \"integration\"
            }")

          ACCEPTED=$(echo "$RESPONSE" | jq -r '.accepted')
          if [ "$ACCEPTED" != "true" ]; then
            echo "Test start rejected: $(echo $RESPONSE | jq -r '.message')"
            exit 1
          fi

          echo "Test execution started"

      - name: Poll for Completion
        id: poll
        run: |
          TEST_ID="${{ steps.init.outputs.test-id }}"
          MAX_WAIT=600  # 10 minutes
          POLL_INTERVAL=10
          ELAPSED=0

          while [ $ELAPSED -lt $MAX_WAIT ]; do
            RESPONSE=$(curl -sf "${TEST_PROBE_URL}/api/v1/test/${TEST_ID}/status")
            STATE=$(echo "$RESPONSE" | jq -r '.state')

            echo "Status: ${STATE}"

            # Terminal states: Completed or Exception
            if [ "$STATE" = "Completed" ]; then
              SUCCESS=$(echo "$RESPONSE" | jq -r '.success')
              if [ "$SUCCESS" = "true" ]; then
                echo "result=passed" >> $GITHUB_OUTPUT
                echo "Test completed successfully"
                exit 0
              else
                ERROR=$(echo "$RESPONSE" | jq -r '.error // "Tests failed"')
                echo "result=failed" >> $GITHUB_OUTPUT
                echo "Test completed with failures: ${ERROR}"
                exit 1
              fi
            elif [ "$STATE" = "Exception" ]; then
              ERROR=$(echo "$RESPONSE" | jq -r '.error')
              echo "result=failed" >> $GITHUB_OUTPUT
              echo "Test exception: ${ERROR}"
              exit 1
            fi

            # In-progress states: Setup, Loading, Loaded, Testing
            sleep $POLL_INTERVAL
            ELAPSED=$((ELAPSED + POLL_INTERVAL))
          done

          echo "Test timed out after ${MAX_WAIT} seconds"
          exit 1

      - name: Download Evidence
        if: always()
        run: |
          TEST_ID="${{ steps.init.outputs.test-id }}"
          S3_PATH="s3://${S3_BUCKET}/tests/${TEST_ID}"

          mkdir -p ./evidence
          aws s3 cp "${S3_PATH}/evidence/" ./evidence/ --recursive || true

      - name: Upload Evidence Artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-evidence-${{ steps.init.outputs.test-id }}
          path: ./evidence/
          retention-days: 30

      - name: Quality Gate
        if: steps.poll.outputs.result != 'passed'
        run: |
          echo "Quality gate failed - tests did not pass"
          exit 1
```

### Reusable Workflow

`.github/workflows/test-probe-reusable.yml`:

```yaml
name: Test-Probe Reusable Workflow

on:
  workflow_call:
    inputs:
      test-type:
        description: 'Type of test to run'
        required: false
        default: 'integration'
        type: string
      features-path:
        description: 'Path to feature files'
        required: false
        default: './tests/features'
        type: string
    secrets:
      TEST_PROBE_URL:
        required: true
      AWS_ACCESS_KEY_ID:
        required: true
      AWS_SECRET_ACCESS_KEY:
        required: true
      TEST_ASSETS_BUCKET:
        required: true

jobs:
  test:
    runs-on: ubuntu-latest
    outputs:
      test-id: ${{ steps.init.outputs.test-id }}
      result: ${{ steps.poll.outputs.result }}

    steps:
      # ... (same steps as above, parameterized)
```

---

## GitLab CI

### Complete Pipeline

`.gitlab-ci.yml`:

```yaml
variables:
  TEST_PROBE_URL: ${CI_TEST_PROBE_URL}
  S3_BUCKET: ${CI_TEST_ASSETS_BUCKET}

stages:
  - test
  - report

integration-test:
  stage: test
  image: amazon/aws-cli:latest
  environment: integration
  before_script:
    - yum install -y jq
  script:
    # Initialize test session
    - |
      RESPONSE=$(curl -sf -X POST "${TEST_PROBE_URL}/api/v1/test/initialize" \
        -H "Content-Type: application/json")
      TEST_ID=$(echo "$RESPONSE" | jq -r '.["test-id"]')
      echo "TEST_ID=${TEST_ID}" >> test.env
      echo "Initialized test session: ${TEST_ID}"

    # Deploy test assets
    - |
      source test.env
      S3_PATH="s3://${S3_BUCKET}/tests/${TEST_ID}"
      aws s3 cp ./tests/features/ "${S3_PATH}/features/" --recursive
      aws s3 cp ./tests/test-config.yaml "${S3_PATH}/test-config.yaml"

    # Start test execution
    - |
      source test.env
      S3_PATH="s3://${S3_BUCKET}/tests/${TEST_ID}"
      RESPONSE=$(curl -sf -X POST "${TEST_PROBE_URL}/api/v1/test/start" \
        -H "Content-Type: application/json" \
        -d "{
          \"test-id\": \"${TEST_ID}\",
          \"block-storage-path\": \"${S3_PATH}\",
          \"test-type\": \"integration\"
        }")
      ACCEPTED=$(echo "$RESPONSE" | jq -r '.accepted')
      [ "$ACCEPTED" = "true" ] || exit 1

    # Poll for completion
    - |
      source test.env
      MAX_WAIT=600
      ELAPSED=0
      while [ $ELAPSED -lt $MAX_WAIT ]; do
        RESPONSE=$(curl -sf "${TEST_PROBE_URL}/api/v1/test/${TEST_ID}/status")
        STATE=$(echo "$RESPONSE" | jq -r '.state')
        echo "Status: ${STATE}"

        # Terminal states: Completed or Exception
        if [ "$STATE" = "Completed" ]; then
          SUCCESS=$(echo "$RESPONSE" | jq -r '.success')
          if [ "$SUCCESS" = "true" ]; then
            echo "RESULT=passed" >> test.env
            exit 0
          else
            echo "RESULT=failed" >> test.env
            exit 1
          fi
        elif [ "$STATE" = "Exception" ]; then
          echo "RESULT=failed" >> test.env
          exit 1
        fi

        # In-progress states: Setup, Loading, Loaded, Testing
        sleep 10
        ELAPSED=$((ELAPSED + 10))
      done
      exit 1

  after_script:
    # Download evidence
    - |
      source test.env || true
      if [ -n "$TEST_ID" ]; then
        mkdir -p ./evidence
        aws s3 cp "s3://${S3_BUCKET}/tests/${TEST_ID}/evidence/" ./evidence/ --recursive || true
      fi

  artifacts:
    when: always
    paths:
      - evidence/
    reports:
      junit: evidence/junit-report.xml
    expire_in: 1 month

quality-gate:
  stage: report
  needs: [ integration-test ]
  script:
    - |
      # Parse cucumber.json to determine pass/fail
      if [ -f evidence/cucumber.json ]; then
        # Check if any step has a non-passed status
        FAILED=$(jq '[.[].elements[].steps[].result.status] | map(select(. != "passed")) | length' evidence/cucumber.json)
        if [ "$FAILED" -gt 0 ]; then
          echo "Quality gate failed: $FAILED steps did not pass"
          exit 1
        fi
        echo "Quality gate passed: all steps passed"
      fi
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
```

---

## Jenkins Pipeline

### Declarative Pipeline

`Jenkinsfile`:

```groovy
pipeline {
    agent any

    environment {
        TEST_PROBE_URL = credentials('test-probe-url')
        S3_BUCKET = credentials('test-assets-bucket')
        AWS_ACCESS_KEY_ID = credentials('aws-access-key-id')
        AWS_SECRET_ACCESS_KEY = credentials('aws-secret-access-key')
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }

    stages {
        stage('Initialize Test') {
            steps {
                script {
                    def response = sh(
                        script: """
                            curl -sf -X POST "${TEST_PROBE_URL}/api/v1/test/initialize" \
                                -H "Content-Type: application/json"
                        """,
                        returnStdout: true
                    ).trim()

                    def json = readJSON text: response
                    env.TEST_ID = json['test-id']
                    echo "Initialized test session: ${env.TEST_ID}"
                }
            }
        }

        stage('Deploy Test Assets') {
            steps {
                sh """
                    S3_PATH="s3://${S3_BUCKET}/tests/${TEST_ID}"
                    aws s3 cp ./tests/features/ "\${S3_PATH}/features/" --recursive
                    aws s3 cp ./tests/test-config.yaml "\${S3_PATH}/test-config.yaml"
                """
            }
        }

        stage('Start Test Execution') {
            steps {
                script {
                    def s3Path = "s3://${S3_BUCKET}/tests/${TEST_ID}"
                    def response = sh(
                        script: """
                            curl -sf -X POST "${TEST_PROBE_URL}/api/v1/test/start" \
                                -H "Content-Type: application/json" \
                                -d '{
                                    "test-id": "${TEST_ID}",
                                    "block-storage-path": "${s3Path}",
                                    "test-type": "integration"
                                }'
                        """,
                        returnStdout: true
                    ).trim()

                    def json = readJSON text: response
                    if (!json.accepted) {
                        error "Test start rejected: ${json.message}"
                    }
                }
            }
        }

        stage('Poll for Completion') {
            steps {
                script {
                    def maxWait = 600  // 10 minutes
                    def pollInterval = 10
                    def elapsed = 0
                    def testPassed = false

                    while (elapsed < maxWait) {
                        def response = sh(
                            script: "curl -sf '${TEST_PROBE_URL}/api/v1/test/${TEST_ID}/status'",
                            returnStdout: true
                        ).trim()

                        def json = readJSON text: response
                        echo "Status: ${json.state}"

                        // Terminal states: Completed or Exception
                        if (json.state == 'Completed') {
                            if (json.success == true) {
                                testPassed = true
                                echo "Test completed successfully"
                            } else {
                                error "Test completed with failures: ${json.error ?: 'Tests failed'}"
                            }
                            break
                        } else if (json.state == 'Exception') {
                            error "Test exception: ${json.error}"
                        }

                        // In-progress states: Setup, Loading, Loaded, Testing
                        sleep pollInterval
                        elapsed += pollInterval
                    }

                    if (!testPassed && elapsed >= maxWait) {
                        error "Test timed out after ${maxWait} seconds"
                    }

                    env.TEST_PASSED = testPassed.toString()
                }
            }
        }

        stage('Download Evidence') {
            steps {
                sh """
                    mkdir -p ./evidence
                    aws s3 cp "s3://${S3_BUCKET}/tests/${TEST_ID}/evidence/" ./evidence/ --recursive || true
                """
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'evidence/**', allowEmptyArchive: true

            publishHTML([
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'evidence',
                reportFiles: 'cucumber-report.html',
                reportName: 'Test Evidence Report'
            ])
        }

        success {
            echo "Pipeline succeeded - all tests passed"
        }

        failure {
            echo "Pipeline failed - check test evidence for details"
        }
    }
}
```

---

## Quality Gates

### Using Test Results

The test status response includes information you can use for quality gates:

| State | `success` Field | Quality Gate Action |
|-------|-----------------|---------------------|
| `Completed` | `true` | Pass pipeline |
| `Completed` | `false` | Fail pipeline (tests failed) |
| `Exception` | `false` | Fail pipeline (execution error) |
| Timeout (no terminal state) | N/A | Fail pipeline |

**Key fields for quality gate decisions:**
- `state`: Terminal states are `Completed` and `Exception`
- `success`: Boolean indicating test pass/fail (only set when `state` is terminal)
- `error`: Optional error message when `success` is `false`

### Evidence-Based Gates

Parse `evidence/cucumber.json` for detailed metrics:

```bash
# Example quality gate script using cucumber.json
FAILED_STEPS=$(jq '[.[].elements[].steps[].result.status] | map(select(. != "passed")) | length' evidence/cucumber.json)
TOTAL_SCENARIOS=$(jq '[.[].elements[]] | length' evidence/cucumber.json)
PASSED_SCENARIOS=$(jq '[.[].elements[] | select(all(.steps[].result.status == "passed"))] | length' evidence/cucumber.json)

echo "Results: ${PASSED_SCENARIOS}/${TOTAL_SCENARIOS} scenarios passed"

if [ "$FAILED_STEPS" -gt 0 ]; then
  echo "Quality gate failed: ${FAILED_STEPS} steps did not pass"
  exit 1
fi
```

---

## Evidence Collection

### Evidence Structure

After test completion, evidence is uploaded to block storage:

```
s3://bucket/tests/{test-id}/
├── features/              # Input: Your feature files
├── topic-directives.yml   # Input: Kafka topic configuration
└── evidence/              # Output: Generated evidence
    └── cucumber.json      # Cucumber test execution report
```

### Downloading Evidence

```bash
# Download all evidence
aws s3 cp "s3://${BUCKET}/tests/${TEST_ID}/evidence/" ./evidence/ --recursive
```

### Long-Term Storage

Evidence remains in block storage for audit and compliance. Configure retention policies on your S3 bucket:

```json
{
  "Rules": [
    {
      "ID": "test-evidence-retention",
      "Prefix": "tests/",
      "Status": "Enabled",
      "Expiration": {
        "Days": 365
      }
    }
  ]
}
```

---

## Troubleshooting

### Connection Issues

**Problem:** Cannot reach Test-Probe service from CI runner

**Solutions:**
1. Verify network access from CI runner to Test-Probe URL
2. Check firewall rules and security groups
3. Ensure Test-Probe service is running: `curl ${TEST_PROBE_URL}/api/v1/health`

### Authentication Errors

**Problem:** S3 upload fails

**Solutions:**
1. Verify AWS credentials are configured correctly in CI
2. Check IAM permissions for S3 bucket access
3. Ensure bucket policy allows uploads from CI runner

### Test Start Rejected

**Problem:** `/test/start` returns `accepted: false`

**Possible causes:**
- Test already running with same ID
- Invalid block-storage-path format (must be `s3://...`)
- Test-Probe queue is full

### Status Polling Issues

**Problem:** Status endpoint returns errors

**Solutions:**
1. Check Test-Probe service health
2. Verify test-id is correct (UUID format)
3. Check for circuit breaker status (503 errors)

### Timeout Issues

**Problem:** Test never completes

**Solutions:**
1. Check Test-Probe logs for errors
2. Verify Kafka cluster is accessible from Test-Probe
3. Check test configuration for long timeouts
4. Use `/test/{testId}` DELETE to cancel stuck tests

---

## Related Documentation

- [API Reference](../../../test-probe-interfaces/src/main/resources/openapi.yaml) - Complete OpenAPI specification
- [Getting Started](../GETTING-STARTED.md) - Initial setup guide
- [Troubleshooting](../TROUBLESHOOTING.md) - Common issues and solutions

---

**Document Version:** 2.0
**Last Updated:** 2025-12-05
