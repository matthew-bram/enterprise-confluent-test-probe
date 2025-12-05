# Deployment Architecture

**Last Updated:** 2025-11-26
**Status:** Active - Deployment patterns defined
**Component:** High-Level Architecture
**Related Documents:**
- [System Overview](system-overview.md)
- [Component Architecture](component-architecture.md)
- [ADR-VAULT-002: Cloud-Native Authentication](../adr/ADR-VAULT-002-cloud-native-authentication.md)

---

## Table of Contents

- [Overview](#overview)
- [Deployment Options](#deployment-options)
- [Resource Requirements](#resource-requirements)
- [Scaling Considerations](#scaling-considerations)
- [Network Architecture](#network-architecture)
- [Configuration Management](#configuration-management)

---

## Overview

Test-Probe supports multiple deployment models from standalone JVM processes to containerized Kubernetes deployments. The system is designed to run in cloud environments (AWS, Azure, GCP) with cloud-native IAM authentication and elastic scaling.

**Deployment Characteristics:**

1. **Stateless:** No persistent state (all state in actors or external storage)
2. **Cloud-Native:** IAM roles/service accounts for authentication
3. **Container-Ready:** Docker images for Kubernetes deployment
4. **Elastic:** Horizontal scaling via multiple instances
5. **Observable:** Structured logging, metrics, health checks

---

## Deployment Options

### 1. Standalone JVM (Development)

**Use Case:** Local development, debugging

**Architecture:**
```
┌─────────────────────────────────────┐
│  Developer Machine                  │
│  ┌───────────────────────────────┐ │
│  │  test-probe-boot-1.0.0.jar    │ │
│  │  - ActorSystem                │ │
│  │  - Pekko HTTP (port 8080)     │ │
│  │  - jimfs (in-memory storage)  │ │
│  └───────────────────────────────┘ │
└─────────────────────────────────────┘
          ↓
┌─────────────────────────────────────┐
│  Testcontainers                     │
│  - Kafka (PLAINTEXT)                │
│  - Schema Registry                  │
└─────────────────────────────────────┘
```

**Startup:**
```bash
# Build fat JAR
mvn clean package -DskipTests

# Run standalone
java -jar test-probe-boot/target/test-probe-boot-1.0.0.jar
```

**Configuration:**
```bash
# Override defaults
java -Dtest-probe.http.port=9090 \
     -Dtest-probe.vault.provider=local \
     -jar test-probe-boot-1.0.0.jar
```

**Benefits:**
- Fast startup (< 5 seconds)
- Easy debugging
- No container overhead
- jimfs for fast local tests

**Limitations:**
- Single instance (no scaling)
- No production cloud access

---

### 2. Docker Container

**Use Case:** CI/CD pipelines, development environments

**Architecture:**
```
┌─────────────────────────────────────┐
│  Docker Host                        │
│  ┌───────────────────────────────┐ │
│  │  test-probe:latest            │ │
│  │  - JDK 21                     │ │
│  │  - test-probe-boot JAR        │ │
│  │  - Exposed port 8080          │ │
│  └───────────────────────────────┘ │
└─────────────────────────────────────┘
```

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY test-probe-boot/target/test-probe-boot-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build & Run:**
```bash
# Build image
docker build -t test-probe:latest .

# Run container
docker run -d \
  -p 8080:8080 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e SCHEMA_REGISTRY_URL=http://schema-registry:8081 \
  --name test-probe \
  test-probe:latest
```

**Benefits:**
- Portable (same image dev/staging/prod)
- Isolated dependencies
- Easy versioning

**Limitations:**
- Single instance per container
- Manual scaling

---

### 3. Kubernetes (Production)

**Use Case:** Production deployments, auto-scaling, high availability

**Architecture:**
```
┌─────────────────────────────────────────────────────────┐
│  Kubernetes Cluster                                     │
│  ┌───────────────────────────────────────────────────┐ │
│  │  test-probe Deployment (replicas: 3)              │ │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ │ │
│  │  │  Pod 1      │ │  Pod 2      │ │  Pod 3      │ │ │
│  │  │  test-probe │ │  test-probe │ │  test-probe │ │ │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ │ │
│  └───────────────────────────────────────────────────┘ │
│  ┌───────────────────────────────────────────────────┐ │
│  │  Service (LoadBalancer)                           │ │
│  │  - Exposes port 8080                              │ │
│  └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

**Deployment YAML:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: test-probe
  labels:
    app: test-probe
spec:
  replicas: 3
  selector:
    matchLabels:
      app: test-probe
  template:
    metadata:
      labels:
        app: test-probe
    spec:
      serviceAccountName: test-probe-sa
      containers:
      - name: test-probe
        image: test-probe:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka-broker-1:9092,kafka-broker-2:9092"
        - name: SCHEMA_REGISTRY_URL
          value: "http://schema-registry:8081"
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /api/v1/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /api/v1/health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: test-probe-service
spec:
  type: LoadBalancer
  selector:
    app: test-probe
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
```

**Service Account (IAM Integration):**

**AWS EKS:**
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: test-probe-sa
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT:role/test-probe-vault-invoker
```

**Azure AKS:**
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: test-probe-sa
  annotations:
    azure.workload.identity/client-id: <CLIENT_ID>
```

**GCP GKE:**
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: test-probe-sa
  annotations:
    iam.gke.io/gcp-service-account: test-probe@PROJECT.iam.gserviceaccount.com
```

**Benefits:**
- Horizontal auto-scaling (HPA)
- High availability (multiple replicas)
- Rolling updates (zero downtime)
- Health checks (automatic restarts)
- Cloud-native IAM integration

---

## Resource Requirements

### Minimum Requirements (Single Instance)

| Resource | Minimum | Recommended | Production |
|----------|---------|-------------|------------|
| **CPU** | 500m | 1 core | 2 cores |
| **Memory** | 1 GB | 2 GB | 4 GB |
| **Disk** | 500 MB | 1 GB | 2 GB |
| **Network** | 10 Mbps | 100 Mbps | 1 Gbps |

---

### Memory Breakdown

**JVM Heap:**
```bash
# Recommended JVM flags
java -Xms1g -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar test-probe-boot.jar
```

**Memory Usage:**

| Component | Memory |
|-----------|--------|
| JVM Heap | 1-2 GB |
| Off-Heap (jimfs) | 100-500 MB |
| Pekko ActorSystem | 100-200 MB |
| Kafka Client Buffers | 50-100 MB |
| HTTP Server | 50 MB |

**Total:** ~1.5-3 GB per instance

---

### CPU Utilization

**Thread Pools:**

| Pool | Threads | Purpose |
|------|---------|---------|
| Pekko Dispatcher | 8-16 | Actor message processing |
| Blocking IO Pool | 4-8 | File I/O, network calls |
| HTTP Thread Pool | 4-8 | REST API requests |
| Kafka Producer Pool | 2 | Kafka produce operations |
| Kafka Consumer Pool | 2 | Kafka consume operations |

**Total:** ~20-36 threads per instance

**CPU Load:**
- **Idle:** 5-10% (health checks, actor idle)
- **Test Execution:** 50-80% (Cucumber, Kafka, event processing)
- **Peak:** 100% (concurrent tests, high event volume)

---

## Scaling Considerations

### Horizontal Scaling (Multiple Instances)

**Challenge:** Sequential test execution (QueueActor FIFO)

**Current Design:**
- Each instance has independent QueueActor
- No shared state between instances
- Load balancer distributes requests round-robin

**Example:**
```
Instance 1: Test A → Test B → Test C
Instance 2: Test D → Test E
Instance 3: Test F

Total throughput: 3x single instance
```

**Benefits:**
- Simple (no coordination required)
- Independent failure domains
- Easy to scale (add more replicas)

**Limitations:**
- No global FIFO ordering
- Tests may execute out of submission order

---

### Vertical Scaling (Larger Instances)

**When to Scale Vertically:**
- High memory Cucumber tests (large feature files)
- High CPU event processing (many events per second)
- Large jimfs usage (many test artifacts)

**Example:**
```yaml
resources:
  requests:
    memory: "4Gi"   # Increased from 1Gi
    cpu: "2000m"    # Increased from 500m
  limits:
    memory: "8Gi"
    cpu: "4000m"
```

**Benefits:**
- More capacity per instance
- Better single-test performance

**Limitations:**
- Higher cost per instance
- Single point of failure

---

### Auto-Scaling (HPA)

**Horizontal Pod Autoscaler:**

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: test-probe-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: test-probe
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

**Scale-Up Triggers:**
- CPU > 70%
- Memory > 80%
- Queue size > threshold (custom metric)

**Scale-Down:**
- CPU < 50% for 5 minutes
- Memory < 60% for 5 minutes

---

## Network Architecture

### Production Network Topology

```
┌────────────────────────────────────────────────────────┐
│  VPC/VNet                                              │
│  ┌──────────────────────────────────────────────────┐ │
│  │  Public Subnet                                   │ │
│  │  ┌────────────────────────────────────────────┐ │ │
│  │  │  Load Balancer                             │ │ │
│  │  │  - Public IP                               │ │ │
│  │  │  - TLS termination                         │ │ │
│  │  └────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────┘ │
│                      ↓                                 │
│  ┌──────────────────────────────────────────────────┐ │
│  │  Private Subnet (App Tier)                      │ │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐  │ │
│  │  │ test-probe │ │ test-probe │ │ test-probe │  │ │
│  │  │  Pod 1     │ │  Pod 2     │ │  Pod 3     │  │ │
│  │  └────────────┘ └────────────┘ └────────────┘  │ │
│  └──────────────────────────────────────────────────┘ │
│                      ↓                                 │
│  ┌──────────────────────────────────────────────────┐ │
│  │  Private Subnet (Data Tier)                     │ │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐  │ │
│  │  │   Kafka    │ │   Schema   │ │   Vault    │  │ │
│  │  │  Brokers   │ │  Registry  │ │  Function  │  │ │
│  │  └────────────┘ └────────────┘ └────────────┘  │ │
│  └──────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────┘
```

**Firewall Rules:**

| Source | Destination | Port | Protocol | Purpose |
|--------|-------------|------|----------|---------|
| Internet | Load Balancer | 443 | HTTPS | REST API |
| Load Balancer | test-probe Pods | 8080 | HTTP | Internal routing |
| test-probe Pods | Kafka Brokers | 9092 | Kafka (SASL_SSL) | Produce/consume |
| test-probe Pods | Schema Registry | 8081 | HTTPS | Schema operations |
| test-probe Pods | Vault Function | 443 | HTTPS | Credential fetch |
| test-probe Pods | S3/Azure/GCS | 443 | HTTPS | Block storage |

---

### DNS Configuration

**Internal DNS:**
```
kafka-broker-1.internal.company.com:9092
kafka-broker-2.internal.company.com:9092
schema-registry.internal.company.com:8081
vault.internal.company.com:443
```

**External DNS:**
```
test-probe.company.com → Load Balancer
```

---

## Configuration Management

### Environment-Specific Configuration

**Development:**
```hocon
test-probe {
  http.port = 8080
  vault.provider = "local"  # jimfs
  kafka.security-protocol = "PLAINTEXT"
}
```

**Staging:**
```hocon
test-probe {
  http.port = 8080
  vault.provider = "aws"
  vault.aws.lambda-arn = "arn:aws:lambda:us-east-1:123:function:vault-staging"
  kafka.security-protocol = "SASL_SSL"
  kafka.bootstrap-servers = "kafka-staging-1:9092,kafka-staging-2:9092"
}
```

**Production:**
```hocon
test-probe {
  http.port = 8080
  vault.provider = "aws"
  vault.aws.lambda-arn = "arn:aws:lambda:us-east-1:123:function:vault-prod"
  kafka.security-protocol = "SASL_SSL"
  kafka.bootstrap-servers = "kafka-prod-1:9092,kafka-prod-2:9092,kafka-prod-3:9092"
  circuit-breaker.max-failures = 5
}
```

---

### ConfigMap (Kubernetes)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: test-probe-config
data:
  application.conf: |
    test-probe {
      http.port = 8080
      vault.provider = "aws"
      vault.aws.lambda-arn = "${VAULT_LAMBDA_ARN}"
      kafka.bootstrap-servers = "${KAFKA_BOOTSTRAP_SERVERS}"
      schema-registry.url = "${SCHEMA_REGISTRY_URL}"
    }
```

**Mount in Deployment:**
```yaml
volumeMounts:
- name: config
  mountPath: /app/config
volumes:
- name: config
  configMap:
    name: test-probe-config
```

---

### Secrets (Kubernetes)

**Function Keys (Azure):**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: test-probe-secrets
type: Opaque
data:
  azure-function-key: <base64-encoded-key>
```

**Environment Variables:**
```yaml
env:
- name: AZURE_FUNCTION_KEY
  valueFrom:
    secretKeyRef:
      name: test-probe-secrets
      key: azure-function-key
```

---

## Related Documents

**Architecture:**
- [System Overview](system-overview.md)
- [Component Architecture](component-architecture.md)

**Security:**
- [ADR-VAULT-002: Cloud-Native Authentication](../adr/ADR-VAULT-002-cloud-native-authentication.md)
- [01 Security Overview](../blueprint/01%20Security/01-security-overview.md)

**Operations:**
- [Build Guide](.claude/guides/BUILD.md)
- [Scripts README](../../../scripts/README.md)

---

**Last Updated:** 2025-11-26
**Status:** Active - Deployment patterns defined
