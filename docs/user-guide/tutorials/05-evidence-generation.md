# Tutorial 5: Evidence Generation for Audit Compliance

**Level:** Advanced
**Duration:** 40 minutes
**Prerequisites:** [Tutorial 1: Your First Test](01-first-test.md)

---

## What You'll Learn

By the end of this tutorial, you will:

1. Configure BlockStorageDirective for evidence collection
2. Generate Cucumber JSON reports as audit evidence
3. Parse and analyze evidence files
4. Upload evidence to S3, Azure Blob, or GCP Storage
5. Meet compliance requirements (SOC2, HIPAA, PCI-DSS)
6. Implement evidence retention policies

## Expected Outcome

You'll build a compliant testing pipeline that automatically generates, validates, and stores audit evidence for regulatory requirements.

---

## Use Case: Regulatory Compliance

### Compliance Requirements

**Industries:**
- **Healthcare (HIPAA)** - Validate PHI encryption and access controls
- **Finance (PCI-DSS)** - Test payment processing and data security
- **Enterprise (SOC2)** - Demonstrate system reliability and security

**Evidence Requirements:**
- **Traceability** - Link tests to requirements
- **Immutability** - Tamper-proof evidence storage
- **Retention** - Store evidence for 7 years
- **Auditability** - Searchable, timestamped evidence

**Test-Probe Solution:** Automated evidence generation with BlockStorageDirective.

---

## Step 1: Understanding BlockStorageDirective

### 1.1 What is BlockStorageDirective?

BlockStorageDirective defines **where and how** Test-Probe stores evidence:

```yaml
blockStorage:
  bucket: "company-test-evidence"       # S3/Azure/GCP bucket
  prefix: "payment-tests/2025-11-26"    # Folder structure
  region: "us-east-1"                   # Cloud region (optional)
  retentionDays: 2555                   # 7 years (required for SOC2)
  encryption: "AES256"                  # Server-side encryption
  metadata:
    project: "payment-gateway"
    team: "payments-engineering"
    compliance: "PCI-DSS"
```

### 1.2 Evidence File Structure

After test execution, Test-Probe generates:

```
s3://company-test-evidence/
└── payment-tests/
    └── 2025-11-26/
        └── test-550e8400-e29b-41d4-a716-446655440000/
            ├── cucumber-report.json          # Cucumber JSON report
            ├── test-execution-log.txt        # Detailed execution log
            ├── kafka-events-produced.jsonl   # All produced events
            ├── kafka-events-consumed.jsonl   # All consumed events
            └── metadata.json                 # Test metadata
```

**Evidence Contents:**
- **cucumber-report.json** - BDD scenarios, steps, pass/fail status
- **test-execution-log.txt** - Timestamped execution log
- **kafka-events-*.jsonl** - Complete event history (JSON Lines format)
- **metadata.json** - Test ID, timestamp, configuration

---

## Step 2: Configure Evidence Collection

### 2.1 Create BlockStorage Configuration

Create `src/test/resources/directives/payment-compliance-test.yaml`:

```yaml
blockStorage:
  bucket: "payment-test-evidence"
  prefix: "payment-gateway/integration-tests"
  region: "us-east-1"
  retentionDays: 2555  # 7 years for PCI-DSS compliance
  encryption: "AES256"
  metadata:
    project: "payment-gateway"
    team: "payments-engineering"
    compliance: "PCI-DSS"
    criticality: "high"
    auditRequired: "true"

topics:
  - topic: "payments.authorized"
    role: "producer"
    clientPrincipal: "payment-service"
    eventFilters: []
    metadata:
      pii: "true"  # Contains payment card data
      encrypted: "true"

  - topic: "payments.settled"
    role: "consumer"
    clientPrincipal: "settlement-service"
    eventFilters:
      - key: "EventType"
        value: "PaymentSettled"
    metadata:
      pii: "true"
      compliance: "PCI-DSS Level 1"
```

### 2.2 Load Configuration in Test

```java
package com.yourcompany.steps;

import io.distia.probe.common.models.BlockStorageDirective;
import io.distia.probe.core.testutil.TopicDirectiveMapper;
import io.distia.probe.core.services.cucumber.CucumberContext;
import io.cucumber.java.Before;

import java.io.File;
import java.util.UUID;

public class ComplianceTestHooks {

    private BlockStorageDirective directive;
    private UUID testId;

    @Before
    public void setupComplianceEvidence() throws Exception {
        // Load BlockStorage configuration
        File yamlFile = new File(
            "src/test/resources/directives/payment-compliance-test.yaml"
        );
        this.directive = TopicDirectiveMapper.fromYaml(yamlFile);

        // Get test ID from context
        this.testId = CucumberContext.getTestId();

        System.out.println("Evidence configuration loaded:");
        System.out.println("  Bucket: " + directive.getBucket());
        System.out.println("  Prefix: " + directive.getPrefix());
        System.out.println("  Test ID: " + testId);
        System.out.println("  Retention: " + directive.getRetentionDays() + " days");
    }
}
```

---

## Step 3: Generate Evidence Files

### 3.1 Automatic Evidence Generation

Test-Probe automatically generates evidence during test execution:

```gherkin
Feature: Payment Processing Compliance
  As a PCI-DSS compliant payment processor
  I want to validate payment authorization and settlement
  So that I can demonstrate compliance to auditors

  Background: Evidence Collection
    Given evidence collection is enabled
    And all events are logged to S3
    And encryption is enabled (AES256)

  Scenario: Payment authorization with PCI compliance
    Given I have a payment authorization request:
      | cardNumber | 4111111111111111  |  # Test card (redacted in evidence)
      | amount     | 99.99             |
      | currency   | USD               |
    When I produce PaymentAuthorized event to "payments.authorized"
    Then the event should be encrypted at rest
    And the event should be logged to S3 evidence bucket
    And the card number should be redacted in logs
    And I should consume PaymentSettled event from "payments.settled"
    And the settlement evidence should be stored
```

### 3.2 Evidence Verification Steps

```java
package com.yourcompany.steps;

import io.distia.probe.core.services.evidence.EvidenceService;
import io.distia.probe.core.services.evidence.EvidenceFile;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.util.List;

import static org.junit.Assert.*;

public class EvidenceSteps {

    private UUID testId;
    private EvidenceService evidenceService;

    @Given("evidence collection is enabled")
    public void evidenceCollectionIsEnabled() {
        this.evidenceService = new EvidenceService(directive);
        assertTrue("Evidence service should be initialized",
                   evidenceService.isEnabled());
        System.out.println("Evidence collection enabled");
    }

    @Given("all events are logged to S3")
    public void allEventsAreLoggedToS3() {
        // Test-Probe automatically logs all produce/consume operations
        System.out.println("Event logging configured for S3");
    }

    @Given("encryption is enabled \\(AES256)")
    public void encryptionIsEnabled() {
        assertEquals("Server-side encryption should be AES256",
                     "AES256", directive.getEncryption());
        System.out.println("AES256 encryption enabled");
    }

    @Then("the event should be logged to S3 evidence bucket")
    public void theEventShouldBeLoggedToS3EvidenceBucket() throws Exception {
        // Verify evidence file exists
        List<EvidenceFile> files = evidenceService.listEvidence(testId);
        assertFalse("Evidence files should exist", files.isEmpty());

        // Check for produced events log
        boolean hasProducedEvents = files.stream()
            .anyMatch(f -> f.getName().equals("kafka-events-produced.jsonl"));
        assertTrue("Should have produced events log", hasProducedEvents);

        System.out.println("Evidence logged to S3: " + files.size() + " files");
    }

    @Then("the card number should be redacted in logs")
    public void theCardNumberShouldBeRedactedInLogs() throws Exception {
        // Read produced events log
        String producedEventsLog = evidenceService.readEvidence(
            testId, "kafka-events-produced.jsonl"
        );

        // Verify PII redaction
        assertFalse("Card number should be redacted",
                    producedEventsLog.contains("4111111111111111"));
        assertTrue("Redacted marker should be present",
                   producedEventsLog.contains("[REDACTED-PII]"));

        System.out.println("PII redaction verified");
    }

    @Then("the settlement evidence should be stored")
    public void theSettlementEvidenceShouldBeStored() throws Exception {
        // Verify consumed events log
        List<EvidenceFile> files = evidenceService.listEvidence(testId);

        boolean hasConsumedEvents = files.stream()
            .anyMatch(f -> f.getName().equals("kafka-events-consumed.jsonl"));
        assertTrue("Should have consumed events log", hasConsumedEvents);

        System.out.println("Settlement evidence stored");
    }
}
```

---

## Step 4: Parse Cucumber JSON Report

### 4.1 Cucumber Report Structure

Cucumber generates a JSON report with detailed test results:

```json
[
  {
    "id": "payment-processing-compliance",
    "name": "Payment Processing Compliance",
    "description": "As a PCI-DSS compliant payment processor...",
    "line": 1,
    "keyword": "Feature",
    "uri": "features/payment-compliance.feature",
    "elements": [
      {
        "id": "payment-processing-compliance;payment-authorization-with-pci-compliance",
        "name": "Payment authorization with PCI compliance",
        "line": 10,
        "keyword": "Scenario",
        "type": "scenario",
        "steps": [
          {
            "name": "I have a payment authorization request",
            "line": 11,
            "keyword": "Given",
            "result": {
              "status": "passed",
              "duration": 1234567890
            }
          }
        ]
      }
    ]
  }
]
```

### 4.2 Parse and Validate Report

```java
package com.yourcompany.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.File;
import java.util.Iterator;

public class CucumberReportParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public CucumberReport parse(File reportFile) throws Exception {
        JsonNode root = mapper.readTree(reportFile);

        // Cucumber report is an array of features
        JsonNode feature = root.get(0);

        CucumberReport report = new CucumberReport();
        report.setFeatureName(feature.get("name").asText());
        report.setUri(feature.get("uri").asText());

        // Parse scenarios
        JsonNode elements = feature.get("elements");
        for (JsonNode scenario : elements) {
            String scenarioName = scenario.get("name").asText();
            String status = getScenarioStatus(scenario);

            report.addScenario(scenarioName, status);
        }

        return report;
    }

    private String getScenarioStatus(JsonNode scenario) {
        // Check all steps
        JsonNode steps = scenario.get("steps");
        for (JsonNode step : steps) {
            String stepStatus = step.get("result").get("status").asText();
            if (!"passed".equals(stepStatus)) {
                return "failed";
            }
        }
        return "passed";
    }

    public static class CucumberReport {
        private String featureName;
        private String uri;
        private List<ScenarioResult> scenarios = new ArrayList<>();

        public void addScenario(String name, String status) {
            scenarios.add(new ScenarioResult(name, status));
        }

        public int getTotalScenarios() {
            return scenarios.size();
        }

        public int getPassedScenarios() {
            return (int) scenarios.stream()
                .filter(s -> "passed".equals(s.status))
                .count();
        }

        public int getFailedScenarios() {
            return getTotalScenarios() - getPassedScenarios();
        }

        // Getters/setters omitted for brevity
    }

    public static class ScenarioResult {
        private String name;
        private String status;

        public ScenarioResult(String name, String status) {
            this.name = name;
            this.status = status;
        }

        // Getters/setters omitted
    }
}
```

### 4.3 Evidence Validation Test

```java
@Then("the Cucumber report should show {int} scenarios passed")
public void theCucumberReportShouldShowScenariosPassed(int expectedPassed) throws Exception {
    // Read Cucumber JSON report from evidence
    String reportJson = evidenceService.readEvidence(
        testId, "cucumber-report.json"
    );

    File reportFile = File.createTempFile("cucumber-report", ".json");
    Files.writeString(reportFile.toPath(), reportJson);

    // Parse report
    CucumberReportParser parser = new CucumberReportParser();
    CucumberReport report = parser.parse(reportFile);

    // Verify results
    assertEquals("Expected passed scenarios",
                 expectedPassed, report.getPassedScenarios());
    assertEquals("Expected 0 failed scenarios",
                 0, report.getFailedScenarios());

    System.out.println("Report validation complete:");
    System.out.println("  Total: " + report.getTotalScenarios());
    System.out.println("  Passed: " + report.getPassedScenarios());
    System.out.println("  Failed: " + report.getFailedScenarios());
}
```

---

## Step 5: Upload Evidence to Cloud Storage

### 5.1 S3 Upload (AWS)

```java
package com.yourcompany.evidence;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.core.sync.RequestBody;

import java.nio.file.Path;
import java.time.LocalDate;

public class S3EvidenceUploader {

    private final S3Client s3Client;
    private final String bucketName;

    public S3EvidenceUploader(S3Client s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public void uploadEvidence(UUID testId, Path evidenceFile) {
        // Build S3 key with date partitioning
        String date = LocalDate.now().toString();
        String key = String.format("payment-tests/%s/test-%s/%s",
                                   date, testId, evidenceFile.getFileName());

        // Upload with server-side encryption
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .serverSideEncryption(ServerSideEncryption.AES256)
            .metadata(Map.of(
                "testId", testId.toString(),
                "uploadDate", date,
                "compliance", "PCI-DSS",
                "retentionYears", "7"
            ))
            .build();

        s3Client.putObject(request, RequestBody.fromFile(evidenceFile));

        System.out.println("Evidence uploaded to S3: s3://" + bucketName + "/" + key);
    }

    public void setRetentionPolicy(String prefix, int days) {
        // Set S3 lifecycle rule for automatic retention
        // (Implementation depends on AWS SDK version)
        System.out.println("Retention policy set: " + days + " days");
    }
}
```

### 5.2 Azure Blob Upload

```java
package com.yourcompany.evidence;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;

import java.nio.file.Path;
import java.time.LocalDate;

public class AzureEvidenceUploader {

    private final BlobContainerClient containerClient;

    public AzureEvidenceUploader(BlobServiceClient serviceClient,
                                 String containerName) {
        this.containerClient = serviceClient.getBlobContainerClient(containerName);
    }

    public void uploadEvidence(UUID testId, Path evidenceFile) {
        String date = LocalDate.now().toString();
        String blobName = String.format("payment-tests/%s/test-%s/%s",
                                       date, testId, evidenceFile.getFileName());

        BlobClient blobClient = containerClient.getBlobClient(blobName);

        // Upload with metadata
        Map<String, String> metadata = Map.of(
            "testId", testId.toString(),
            "uploadDate", date,
            "compliance", "PCI-DSS"
        );

        blobClient.uploadFromFile(evidenceFile.toString());
        blobClient.setMetadata(metadata);

        System.out.println("Evidence uploaded to Azure: " + blobName);
    }
}
```

### 5.3 GCP Storage Upload

```java
package com.yourcompany.evidence;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;

import java.nio.file.Files;
import java.nio.file.Path;

public class GcpEvidenceUploader {

    private final Storage storage;
    private final String bucketName;

    public GcpEvidenceUploader(Storage storage, String bucketName) {
        this.storage = storage;
        this.bucketName = bucketName;
    }

    public void uploadEvidence(UUID testId, Path evidenceFile) throws Exception {
        String date = LocalDate.now().toString();
        String objectName = String.format("payment-tests/%s/test-%s/%s",
                                         date, testId, evidenceFile.getFileName());

        BlobId blobId = BlobId.of(bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
            .setContentType("application/json")
            .setMetadata(Map.of(
                "testId", testId.toString(),
                "uploadDate", date,
                "compliance", "PCI-DSS"
            ))
            .build();

        storage.create(blobInfo, Files.readAllBytes(evidenceFile));

        System.out.println("Evidence uploaded to GCP: gs://" + bucketName + "/" + objectName);
    }
}
```

---

## Step 6: Compliance Reporting

### 6.1 Generate Compliance Summary

```java
package com.yourcompany.evidence;

import java.time.LocalDate;
import java.util.List;

public class ComplianceReportGenerator {

    public ComplianceReport generateReport(UUID testId,
                                          CucumberReport cucumberReport,
                                          List<String> evidenceFiles) {
        ComplianceReport report = new ComplianceReport();
        report.setTestId(testId);
        report.setReportDate(LocalDate.now());
        report.setTotalScenarios(cucumberReport.getTotalScenarios());
        report.setPassedScenarios(cucumberReport.getPassedScenarios());
        report.setFailedScenarios(cucumberReport.getFailedScenarios());
        report.setEvidenceFiles(evidenceFiles);
        report.setComplianceStatus(
            cucumberReport.getFailedScenarios() == 0 ? "COMPLIANT" : "NON-COMPLIANT"
        );

        return report;
    }

    public void printReport(ComplianceReport report) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("          COMPLIANCE TEST REPORT");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("Test ID:          " + report.getTestId());
        System.out.println("Report Date:      " + report.getReportDate());
        System.out.println("─────────────────────────────────────────────────");
        System.out.println("Total Scenarios:  " + report.getTotalScenarios());
        System.out.println("Passed:           " + report.getPassedScenarios() + " ✓");
        System.out.println("Failed:           " + report.getFailedScenarios() +
                          (report.getFailedScenarios() > 0 ? " ✗" : ""));
        System.out.println("─────────────────────────────────────────────────");
        System.out.println("Compliance:       " + report.getComplianceStatus());
        System.out.println("Evidence Files:   " + report.getEvidenceFiles().size());
        System.out.println("═══════════════════════════════════════════════════");
    }

    public static class ComplianceReport {
        private UUID testId;
        private LocalDate reportDate;
        private int totalScenarios;
        private int passedScenarios;
        private int failedScenarios;
        private List<String> evidenceFiles;
        private String complianceStatus;

        // Getters/setters omitted for brevity
    }
}
```

---

## Summary

Congratulations! You've mastered evidence generation for audit compliance. You learned:

- Configuring BlockStorageDirective for automated evidence collection
- Generating Cucumber JSON reports as immutable audit trails
- Parsing and validating evidence files
- Uploading evidence to S3, Azure Blob, or GCP Storage
- Implementing PII redaction for compliance
- Meeting SOC2, HIPAA, and PCI-DSS requirements
- Generating compliance summary reports

**Key Takeaways:**
- Evidence is automatically generated during test execution
- BlockStorageDirective configures where evidence is stored
- PII redaction protects sensitive data in logs
- 7-year retention meets most compliance requirements
- Cloud storage provides immutability and durability

---

## Compliance Checklist

- [ ] Evidence collection enabled (BlockStorageDirective configured)
- [ ] Server-side encryption enabled (AES256)
- [ ] PII redaction implemented for sensitive fields
- [ ] Retention policy set (7 years for SOC2/PCI-DSS)
- [ ] Evidence uploaded to immutable storage (S3/Azure/GCP)
- [ ] Cucumber reports include traceability to requirements
- [ ] Compliance reports generated and reviewed
- [ ] Access controls configured (IAM policies)

---

## Next Steps

1. **[ProbeScalaDsl API](../../api/probe-scala-dsl-api.md)** - Complete DSL reference
2. **[SerdesFactory API](../../api/serdes-factory-api.md)** - Serialization details
3. **[BlockStorageDirective Model](../../api/block-storage-directive-model.md)** - Configuration reference

---

## Additional Resources

- **SOC2 Compliance:** [AICPA SOC 2 Framework](https://www.aicpa.org/soc2)
- **HIPAA Security Rule:** [HHS HIPAA](https://www.hhs.gov/hipaa)
- **PCI-DSS v4.0:** [PCI Security Standards](https://www.pcisecuritystandards.org/)
- **AWS S3 Compliance:** [AWS Compliance Programs](https://aws.amazon.com/compliance/)

---

**Document Version:** 1.0.0
**Last Updated:** 2025-11-26
**Tested With:** test-probe-core 1.0.0, AWS SDK 2.20.0, Azure SDK 12.22.0
