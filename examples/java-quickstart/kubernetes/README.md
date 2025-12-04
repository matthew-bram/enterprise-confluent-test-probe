# Test-Probe Java Quickstart - Kubernetes Deployment

This directory contains Kubernetes deployment resources for the Java Quickstart example.

**IMPORTANT**: Kubernetes is a SECONDARY deployment option. For local development and testing, we recommend using Docker Compose instead (see parent directory's README).

## Overview

This deployment option is suitable for:
- Kubernetes-native environments
- CI/CD pipelines running in K8s
- Testing K8s-specific configurations
- Multi-environment deployments

## Structure

```
kubernetes/
├── helm/
│   └── java-quickstart/          # Helm chart (recommended)
│       ├── Chart.yaml            # Chart metadata
│       ├── values.yaml           # Configuration values
│       └── templates/            # K8s resource templates
│           ├── deployment.yaml   # Application deployment
│           ├── service.yaml      # Service definition
│           └── configmap.yaml    # Configuration data
├── manifests/                    # Raw K8s manifests (alternative)
│   ├── namespace.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   └── configmap.yaml
└── README.md                     # This file
```

## Prerequisites

1. **Local Kubernetes Cluster** (one of):
   - Docker Desktop with Kubernetes enabled
   - minikube
   - kind (Kubernetes in Docker)
   - k3s/k3d

2. **Command-line Tools**:
   - `kubectl` (v1.25+)
   - `helm` (v3.x)
   - `docker`

3. **Kafka**: Either deploy using the provided `localKafka` chart or connect to an existing Kafka cluster

## Quick Start

### Option 1: Using Scripts (Easiest)

```bash
# Deploy everything (Kafka + Quickstart)
cd examples/java-quickstart
./scripts/deploy-k8s.sh

# Clean up when done
./scripts/cleanup-k8s.sh
```

### Option 2: Using Helm

```bash
# 1. Deploy Kafka first
cd examples/localKafka
./deploy-local-kafka.sh

# 2. Build the Docker image
cd ../java-quickstart
./scripts/build.sh

# 3. Deploy the quickstart application
helm install quickstart ./kubernetes/helm/java-quickstart \
  --namespace kafka \
  --wait

# 4. Setup port forwarding
kubectl port-forward svc/kafka 9092:9092 -n kafka &
kubectl port-forward svc/schema-registry 8081:8081 -n kafka &

# 5. Run tests
./scripts/run-tests.sh
```

### Option 3: Using Raw Manifests

```bash
# 1. Deploy Kafka (using localKafka chart)
cd examples/localKafka
./deploy-local-kafka.sh

# 2. Build the Docker image
cd ../java-quickstart
./scripts/build.sh

# 3. Apply manifests
kubectl apply -f kubernetes/manifests/namespace.yaml
kubectl apply -f kubernetes/manifests/configmap.yaml
kubectl apply -f kubernetes/manifests/deployment.yaml
kubectl apply -f kubernetes/manifests/service.yaml

# 4. Setup port forwarding
kubectl port-forward svc/kafka 9092:9092 -n kafka &
kubectl port-forward svc/schema-registry 8081:8081 -n kafka &
```

## Configuration

### Helm Values

The main configuration is in `helm/java-quickstart/values.yaml`. Key settings:

```yaml
app:
  image:
    repository: test-probe/java-quickstart
    tag: "latest"
  replicas: 1
  resources:
    limits:
      cpu: "500m"
      memory: "512Mi"

kafka:
  bootstrapServers: "kafka:9092"
  schemaRegistryUrl: "http://schema-registry:8081"

namespace: kafka
```

### Custom Values

Create a custom values file:

```yaml
# custom-values.yaml
app:
  replicas: 2
  resources:
    limits:
      cpu: "1000m"
      memory: "1Gi"

kafka:
  bootstrapServers: "external-kafka:9092"
  schemaRegistryUrl: "http://external-schema-registry:8081"
```

Deploy with custom values:

```bash
helm install quickstart ./kubernetes/helm/java-quickstart \
  -f custom-values.yaml \
  --namespace kafka
```

## Troubleshooting

### Check Pod Status

```bash
kubectl get pods -n kafka
kubectl describe pod <pod-name> -n kafka
kubectl logs <pod-name> -n kafka
```

### Check Services

```bash
kubectl get svc -n kafka
```

### Verify Kafka Connection

```bash
# Exec into the pod
kubectl exec -it <pod-name> -n kafka -- /bin/bash

# Test Kafka connection
echo "dump" | nc kafka 9092
```

### Common Issues

1. **Image Pull Errors**: Ensure the Docker image is built and available locally
   ```bash
   docker images | grep java-quickstart
   ```

2. **Connection Refused**: Ensure Kafka is deployed and ready
   ```bash
   kubectl get pods -n kafka
   kubectl wait --for=condition=ready pod/kafka-0 -n kafka
   ```

3. **Port Forward Not Working**: Kill existing port-forward processes
   ```bash
   pkill -f "kubectl port-forward"
   ```

## Cleanup

### Remove Quickstart Only

```bash
helm uninstall quickstart -n kafka
```

### Remove Everything

```bash
# Using script
./scripts/cleanup-k8s.sh

# Or manually
helm uninstall quickstart -n kafka
helm uninstall kafka -n kafka
kubectl delete namespace kafka
```

## Comparison: Kubernetes vs Docker Compose

| Feature | Docker Compose | Kubernetes |
|---------|---------------|------------|
| Setup Time | 1 minute | 5-10 minutes |
| Resource Usage | Low | Medium |
| Production-like | No | Yes |
| Orchestration | Basic | Advanced |
| Multi-node | No | Yes |
| Best for | Local dev/testing | CI/CD, Production-like testing |

**Recommendation**: Use Docker Compose for daily development and Kubernetes for CI/CD integration or when testing K8s-specific features.

## Next Steps

1. **Run Tests**: `./scripts/run-tests.sh`
2. **View Logs**: `kubectl logs -f <pod-name> -n kafka`
3. **Scale Up**: `kubectl scale deployment/java-quickstart --replicas=3 -n kafka`
4. **Update Config**: Edit `values.yaml` and run `helm upgrade`

## Additional Resources

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Helm Documentation](https://helm.sh/docs/)
- [Test-Probe Architecture Guide](../../docs/guides/ARCHITECTURE.md)
- [Local Kafka Chart](../localKafka/README.md)
