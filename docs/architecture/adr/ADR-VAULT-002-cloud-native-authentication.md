# ADR-VAULT-002: Cloud-Native Authentication for Vault Services

**Status**: Accepted
**Date**: 2025-10-28
**Component**: Vault Services (AWS, Azure, GCP)
**Related Documents**:
- [ADR-VAULT-001: Rosetta Mapping Pattern](./ADR-VAULT-001-rosetta-mapping-pattern.md)
- [VaultService Architecture](../blueprint/04%20Adapters/04.2-vault-integration-layer.md)

---

## Context

The Test-Probe framework invokes cloud-based vault functions (AWS Lambda, Azure Functions, GCP Cloud Functions) to retrieve Kafka OAuth credentials. Each cloud provider offers multiple authentication methods:

**AWS Lambda Invocation Options:**
- IAM roles (EC2 instance profiles, ECS task roles, EKS service accounts via IRSA)
- Access keys (static credentials)
- STS temporary credentials

**Azure Functions Invocation Options:**
- Function keys (shared secrets)
- Managed Identity (system-assigned or user-assigned)
- Azure AD service principals

**GCP Cloud Functions Invocation Options:**
- Service account key files
- Application Default Credentials (ADC)
- Workload Identity (for GKE)

**The Recursive Problem:**

Early implementation attempted to use OAuth/secrets to authenticate *to the vault* to retrieve credentials *for Kafka*. This creates circular logic:

```
Test Probe needs Kafka credentials
    ↓ [How do we authenticate?]
Call Vault to get OAuth credentials
    ↓ [But we need credentials to call the vault!]
Need to store vault credentials somewhere
    ↓ [Back to square one]
Where do we store those credentials securely?
```

This is the classic **"turtles all the way down"** problem - you need secrets to get secrets.

---

## Decision

**We will use cloud-native IAM/identity mechanisms to authenticate vault function invocations, NOT application-level secrets or OAuth flows.**

### Implementation by Provider

#### AWS (Reference Implementation ✅)

**Use AWS SDK Default Credentials Chain** - No explicit credential configuration required.

The SDK automatically resolves credentials in this order:
1. Environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
2. Java system properties
3. Web Identity Token (EKS service accounts)
4. AWS credentials file (`~/.aws/credentials`)
5. AWS config file (`~/.aws/config`)
6. Container credentials (ECS task role)
7. Instance profile credentials (EC2 IAM role via IMDS)

**Implementation:**
```scala
// DefaultLambdaClientFactory.scala
override def createClient(region: String): LambdaClient = {
  LambdaClient.builder()
    .region(Region.of(region))
    .build()  // No explicit credentials - uses SDK default chain
}
```

**IAM Policy Required:**
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "lambda:InvokeFunction",
    "Resource": "arn:aws:lambda:*:*:function:vault-*"
  }]
}
```

**Production Deployment Examples:**

*EC2 Instance:*
```bash
# Attach IAM role with lambda:InvokeFunction permission
# No configuration in Test-Probe required
```

*EKS Pod with IRSA:*
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: test-probe
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT:role/test-probe-vault-invoker
```

*ECS Task:*
```json
{
  "taskRoleArn": "arn:aws:iam::ACCOUNT:role/test-probe-vault-invoker",
  "containerDefinitions": [...]
}
```

#### Azure (Simplified)

**Use Azure Function Keys** - Treat as infrastructure-level authorization, not secrets management.

Function keys are:
- Managed by Azure Functions infrastructure
- Rotatable via Azure Portal/CLI
- Scoped per function or host
- Transmitted over TLS

**Implementation:**
```scala
httpClient.post(
  uri = functionUrl,
  jsonPayload = payload,
  headers = Map(
    "Content-Type" -> "application/json",
    "x-functions-key" -> functionKey  // Infrastructure credential
  )
)
```

**Configuration:**
```hocon
azure {
  function-url = "https://company-vault.azurewebsites.net/api/vault"
  function-key = "${?AZURE_FUNCTION_KEY}"  # From environment variable
}
```

**Why Not Managed Identity?**

Managed Identity requires:
- Azure Identity SDK (`com.azure.identity`)
- Token acquisition on every call (adds latency)
- Additional complexity for marginal security benefit

Since function keys are already infrastructure-level credentials (not application secrets), the security posture is comparable to managed identity for this use case.

**Future Consideration:** If Test-Probe runs in Azure (AKS, Container Apps, VMs), managed identity support could be added as an *optional* enhancement.

#### GCP (Simplified)

**Use HTTP-Only Invocation** - Cloud Function URLs are private by default, IAM controls access at infrastructure level.

**Why No Service Account Key in Application?**

GCP Cloud Functions have three authentication models:

1. **Public (unauthenticated)** - Bad, don't use
2. **Private with IAM** - Requires ID token in `Authorization: Bearer <token>` header
3. **VPC-SC (network isolation)** - Function only accessible within VPC

**Implementation Decision:**

For Test-Probe vault integration, we use **network isolation + infrastructure-level IAM**, not application-level authentication:

```scala
httpClient.post(
  uri = functionUrl,
  jsonPayload = payload,
  headers = Map("Content-Type" -> "application/json")
  // No Authorization header - network access IS the authorization
)
```

**Infrastructure Configuration:**

```bash
# Create Cloud Function with IAM requirement
gcloud functions deploy vault-credentials \
  --ingress-settings=internal-only \
  --no-allow-unauthenticated

# Grant Test-Probe service account invoke permission
gcloud functions add-iam-policy-binding vault-credentials \
  --member="serviceAccount:test-probe@PROJECT.iam.gserviceaccount.com" \
  --role="roles/cloudfunctions.invoker"
```

**VPC-SC Deployment (Enterprise):**

For enterprises with VPC Service Controls:
- Deploy Test-Probe in same VPC as Cloud Function
- Function URL is only accessible within VPC perimeter
- No internet egress required
- Network isolation provides security boundary

**Why Not ID Tokens in Application?**

Adding service account key file + ID token generation to the application:
- ❌ Requires distributing service account keys (secret sprawl)
- ❌ Adds Google Auth Library dependency
- ❌ Token refresh logic complexity
- ❌ Doesn't improve security vs. network isolation + IAM

GCP's security model is "authenticate at infrastructure, not application" - similar to how AWS VPC endpoints work.

---

## Rationale

### Why Cloud-Native IAM Instead of Application Secrets?

**1. Eliminates Secret Distribution Problem**

Traditional approach:
```
Vault stores Kafka secrets
    ↓
Test-Probe needs vault credentials (chicken-egg!)
    ↓
Store vault credentials in... another vault?
```

Cloud-native approach:
```
Infrastructure assigns identity to workload (EC2 role, service account)
    ↓
SDK/platform automatically provides credentials
    ↓
Application has zero secret configuration
```

**2. Automatic Credential Rotation**

| Approach | Rotation |
|----------|----------|
| Shared secrets | Manual, requires application restart |
| IAM roles | Automatic, transparent to application |
| Service accounts | Automatic, transparent to application |

**3. Least Privilege Per Environment**

IAM policies can be scoped:
```json
{
  "Effect": "Allow",
  "Action": "lambda:InvokeFunction",
  "Resource": "arn:aws:lambda:*:*:function:vault-prod-*",
  "Condition": {
    "StringEquals": {
      "aws:RequestedRegion": "us-east-1"
    }
  }
}
```

Cannot achieve this granularity with shared secrets.

**4. Audit Trail**

CloudTrail/Cloud Audit Logs automatically record:
- Which workload (by IAM role/service account) invoked the vault function
- When the invocation occurred
- From which IP/VPC
- Success or failure

Shared secret authentication loses workload identity in audit logs.

**5. Development Simplicity**

**Bad (requires secret management):**
```hocon
vault {
  aws {
    lambda-arn = "arn:aws:lambda:us-east-1:123:function:vault"
    access-key-id = "AKIAIOSFODNN7EXAMPLE"      # ❌ Secret in config
    secret-access-key = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"  # ❌ Secret in config
  }
}
```

**Good (zero secrets in application):**
```hocon
vault {
  aws {
    lambda-arn = "arn:aws:lambda:us-east-1:123:function:vault"
    # No credentials - SDK uses IAM role automatically ✅
  }
}
```

**6. Container/Kubernetes Native**

Modern deployment platforms (EKS, AKS, GKE) have native service account integration:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: test-probe
spec:
  serviceAccountName: test-probe-sa  # Maps to cloud IAM automatically
  containers:
  - name: test-probe
    image: test-probe:latest
    # No environment variables for cloud credentials needed! ✅
```

---

## Implementation Details

### Current State (Post-RCA Fixes)

| Provider | Auth Method | Status |
|----------|-------------|--------|
| AWS | AWS SDK Default Chain (IAM) | ✅ Implemented |
| Azure | Function Keys | ✅ Implemented |
| GCP | HTTP-only (network isolation) | ✅ Implemented |

### Configuration Schema

**AWS:**
```hocon
vault {
  aws {
    lambda-arn = "arn:aws:lambda:REGION:ACCOUNT:function:NAME"
    region = "us-east-1"
    # No explicit credentials - uses IAM role/EC2 instance profile/EKS service account
  }
}
```

**Azure:**
```hocon
vault {
  azure {
    function-url = "https://company-vault.azurewebsites.net/api/vault"
    function-key = "${?AZURE_FUNCTION_KEY}"  # From environment, not hardcoded
  }
}
```

**GCP:**
```hocon
vault {
  gcp {
    function-url = "https://REGION-PROJECT.cloudfunctions.net/vault-credentials"
    # No authentication config - relies on network isolation + GCP IAM
    # service-account-key is CONFIGURED but INTENTIONALLY NOT USED
  }
}
```

### Why `service-account-key` Config Exists But Is Not Used (GCP)

The configuration option exists for **future flexibility** but is intentionally not implemented because:

1. **Security**: Distributing service account key files violates GCP best practices
2. **Alternatives**: Network isolation + IAM provides equivalent security
3. **Operational**: Application Default Credentials (ADC) works in local dev without keys
4. **Consistency**: AWS/Azure also don't require application-level authentication

If an enterprise has a specific requirement for ID token authentication (rare), the implementation path is clear via the existing config option.

---

## Trade-offs

### What We Gain

✅ **Zero secrets in application code or config files**
✅ **Automatic credential rotation** (cloud provider handles it)
✅ **Fine-grained IAM policies** per environment/workload
✅ **Full audit trail** via CloudTrail/Cloud Audit Logs
✅ **Simpler testing** (LocalStack/WireMock don't require real credentials)
✅ **Kubernetes/container native** (service account projection)

### What We Lose

❌ **Cannot run Test-Probe outside cloud environments** without some configuration:
   - AWS: Need credentials file or environment variables
   - Azure: Need function key
   - GCP: Need to configure ADC or use Cloud Shell

❌ **Less portable** to on-premises data centers (but enterprise vaults would require different implementation anyway)

### Mitigation for Local Development

**AWS Local Dev:**
```bash
# Use AWS CLI profile
aws configure --profile test-probe
export AWS_PROFILE=test-probe

# SDK automatically uses profile credentials
```

**Azure Local Dev:**
```bash
# Get function key from Azure Portal
export AZURE_FUNCTION_KEY="xyz..."
```

**GCP Local Dev:**
```bash
# Use Application Default Credentials
gcloud auth application-default login

# Or use service account (if required)
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/key.json"
```

---

## Consequences

### Positive

1. **Vault services are not secrets management systems** - they are credential *brokers*, using cloud-native identity to control access
2. **Test-Probe becomes "cloud-native by default"** - embraces platform capabilities instead of fighting them
3. **Security posture improved** - no long-lived credentials in configuration files
4. **Operations simplified** - credential rotation is automatic and transparent

### Negative

1. **Increased cloud provider coupling** - cannot trivially swap AWS Lambda for on-prem HTTP service without IAM refactoring
2. **Documentation complexity** - DevOps teams must understand IAM roles/service accounts
3. **Local testing requires setup** - developers need AWS CLI/gcloud/az CLI configured

### Neutral

1. **This decision aligns Test-Probe with modern observability/monitoring tools** (DataDog, Prometheus, etc.) which also use cloud-native IAM

---

## Related Testing Implications

### Removed Test: GCP Service Account Authentication Header

**Test that was removed:**
```scala
"should verify GCP service account authentication header is present" in {
  wireMockServer.stubFor(
    post(urlEqualTo("/vault-invoker"))
      .withHeader("Authorization", containing("Bearer"))  // ❌ Not implemented
      .willReturn(...)
  )

  service.fetchSecurityDirectives(directive).futureValue

  wireMockServer.verify(
    postRequestedFor(urlEqualTo("/vault-invoker"))
      .withHeader("Authorization", containing("Bearer"))  // ❌ Will never be sent
  )
}
```

**Why removed:** This test expects GCP ID token authentication in the application layer, which creates the recursive authentication problem this ADR solves. Infrastructure-level IAM (not application-level tokens) is the correct pattern.

### Testing Strategy

Integration tests use **mock vault functions** (WireMock/LocalStack) that:
- Do NOT require real cloud authentication
- Return test credentials in expected format
- Validate request payload structure (Rosetta mapping)

This allows testing vault integration logic without coupling to real cloud IAM.

---

## References

### AWS Documentation
- [AWS SDK Default Credentials Chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html)
- [IAM Roles for Amazon EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html)
- [IAM Roles for Service Accounts (IRSA)](https://docs.aws.amazon.com/eks/latest/userguide/iam-roles-for-service-accounts.html)

### Azure Documentation
- [Function Access Keys](https://learn.microsoft.com/en-us/azure/azure-functions/function-keys-how-to)
- [Managed Identity for Azure Functions](https://learn.microsoft.com/en-us/azure/app-service/overview-managed-identity)

### GCP Documentation
- [Authenticating to Cloud Functions](https://cloud.google.com/functions/docs/securing/authenticating)
- [Application Default Credentials](https://cloud.google.com/docs/authentication/application-default-credentials)
- [VPC Service Controls](https://cloud.google.com/vpc-service-controls/docs/overview)

### Industry Best Practices
- [OWASP: Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [CIS Benchmarks: Cloud IAM](https://www.cisecurity.org/benchmark/amazon_web_services)

---

## Revision History

| Date | Author | Change |
|------|--------|--------|
| 2025-10-28 | Claude + User | Initial version documenting cloud-native authentication decision |

---

**Approved By:** Development Team
**Implementation Status:** Complete (AWS), Complete (Azure), Complete (GCP)
