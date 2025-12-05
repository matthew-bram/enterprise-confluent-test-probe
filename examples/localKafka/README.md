# Local Kafka Helm Chart

This Helm chart deploys Apache Kafka with KRaft controllers and Confluent Schema Registry to a Kubernetes cluster for local development.

## Components

- **Apache Kafka 3.7.0**: Single-node Kafka broker with integrated KRaft controller (no Zookeeper required)
- **Confluent Schema Registry 7.6.0**: Schema management and validation for Kafka messages

## Prerequisites

- Kubernetes cluster (Docker Desktop with Kubernetes enabled)
- Helm 3.x installed
- `kubectl` configured to access your cluster

## Quick Start (Recommended)

The easiest way to deploy Kafka locally with automatic port-forwarding:

```bash
# Deploy and start port-forwarding (runs in foreground)
cd examples/localKafka
./deploy-local-kafka.sh
```

This script will:
1. Install Kafka and Schema Registry using Helm
2. Wait for all pods to be ready
3. Automatically set up port-forwarding to `localhost:9092` (Kafka) and `localhost:8081` (Schema Registry)
4. Keep running until you press Ctrl+C

**To stop:**
```bash
# From another terminal
cd examples/localKafka
./stop-local-kafka.sh
```

## Manual Installation

If you prefer manual control:

### Install the chart

```bash
# From the project root directory
helm install local-kafka examples/localKafka -n kafka --create-namespace --wait

# Or specify custom values
helm install local-kafka examples/localKafka -n kafka --create-namespace -f my-values.yaml --wait
```

### Verify deployment

```bash
# Check pods (Kafka takes ~60 seconds to start)
kubectl get pods -n kafka

# Check services
kubectl get svc -n kafka

# View logs
kubectl logs -n kafka kafka-0
kubectl logs -n kafka deployment/schema-registry
```

### Set up port-forwarding manually

```bash
# Kafka broker (in one terminal)
kubectl port-forward -n kafka svc/kafka 9092:9092

# Schema Registry (in another terminal)
kubectl port-forward -n kafka svc/schema-registry 8081:8081
```

## Configuration

Key configuration options in `values.yaml`:

### Kafka Settings

- `kafka.image.tag`: Kafka version (default: "3.7.0")
- `kafka.kraft.clusterId`: KRaft cluster ID (base64-encoded, 16 bytes)
- `kafka.config.numPartitions`: Default partition count (default: 3)
- `kafka.config.defaultReplicationFactor`: Replication factor (default: 1 for single-node)
- `kafka.persistence.enabled`: Enable persistent storage (default: true)
- `kafka.persistence.size`: Storage size (default: "10Gi")

### Schema Registry Settings

- `schemaRegistry.enabled`: Enable/disable Schema Registry (default: true)
- `schemaRegistry.image.tag`: Schema Registry version (default: "7.6.0")
- `schemaRegistry.config.schemaCompatibilityLevel`: Compatibility mode (default: "BACKWARD")

## Accessing Services

### From your local machine

After running `./deploy-local-kafka.sh` or setting up port-forwarding manually:

- **Kafka Broker**: `localhost:9092`
- **Schema Registry**: `http://localhost:8081`

### From within the cluster

For applications running inside Kubernetes:

- **Kafka**: `kafka.kafka.svc.cluster.local:9092` or `kafka:9092` (within kafka namespace)
- **Schema Registry**: `http://schema-registry.kafka.svc.cluster.local:8081` or `http://schema-registry:8081` (within kafka namespace)

## Testing the Installation

### Test Kafka from your local machine

With port-forwarding active (via `./deploy-local-kafka.sh` or manual setup):

```bash
# Using kafkacat/kcat (if installed)
echo "test message" | kcat -P -b localhost:9092 -t test-topic

# Or use Kafka tools from inside the pod
# Create a test topic
kubectl exec -n kafka kafka-0 -- /opt/kafka/bin/kafka-topics.sh \
  --create \
  --topic test-topic \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

# List topics
kubectl exec -n kafka kafka-0 -- /opt/kafka/bin/kafka-topics.sh \
  --list \
  --bootstrap-server localhost:9092

# Produce messages (interactive)
kubectl exec -it -n kafka kafka-0 -- /opt/kafka/bin/kafka-console-producer.sh \
  --topic test-topic \
  --bootstrap-server localhost:9092

# Consume messages (in another terminal)
kubectl exec -it -n kafka kafka-0 -- /opt/kafka/bin/kafka-console-consumer.sh \
  --topic test-topic \
  --from-beginning \
  --bootstrap-server localhost:9092
```

### Test Schema Registry

```bash
# Check Schema Registry health (requires port-forward)
curl http://localhost:8081/

# List subjects
curl http://localhost:8081/subjects

# Register a test schema
curl -X POST http://localhost:8081/subjects/test-value/versions \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{"schema": "{\"type\":\"record\",\"name\":\"Test\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}"}'
```

## Uninstallation

### Using the stop script (recommended)

```bash
cd examples/localKafka
./stop-local-kafka.sh
```

The script will:
1. Stop all port-forward processes
2. Optionally uninstall the Helm release
3. Optionally delete the namespace and all resources (including PVCs)

### Manual uninstallation

```bash
# Stop port-forwarding
pkill -f "port-forward.*kafka.*9092"
pkill -f "port-forward.*schema-registry.*8081"

# Remove the Helm release
helm uninstall local-kafka -n kafka

# Delete the namespace (removes all resources including PVCs)
kubectl delete namespace kafka
```

## Troubleshooting

### Kafka pod keeps restarting

**Issue**: Pod shows `CrashLoopBackOff` or continuous restarts

**Solution**:
- Kafka with KRaft takes ~60 seconds to fully start
- Check logs: `kubectl logs -n kafka kafka-0`
- Ensure health probe delays are set correctly (liveness: 60s, readiness: 45s)
- Verify numeric config values are quoted in `values.yaml` to prevent scientific notation

### Cannot connect to localhost:9092

**Issue**: Connection refused when trying to connect from local machine

**Solution**:
- Ensure port-forwarding is active: `kubectl get pods -n kafka` should show `kafka-0` as `Running`
- Run `./deploy-local-kafka.sh` which automatically sets up port-forwarding
- Or manually: `kubectl port-forward -n kafka svc/kafka 9092:9092`
- Verify with: `lsof -i :9092` (should show kubectl process)

### Schema Registry fails to start

**Issue**: Schema Registry pod in `CrashLoopBackOff` with "PORT is deprecated" error

**Root cause**: Kubernetes automatically injects a `SCHEMA_REGISTRY_PORT` environment variable from the service definition, which the Confluent Docker image rejects as deprecated.

**Solution**:
- The chart includes a wrapper script that unsets this variable before starting Schema Registry
- Schema Registry also requires Kafka to be fully running (uses 120s timeout for health checks)
- If still failing, check logs: `kubectl logs -n kafka -l app=schema-registry`

### Port already in use

**Issue**: Port-forward fails with "address already in use"

**Solution**:
```bash
# Find and kill processes using the ports
lsof -ti:9092 | xargs kill -9
lsof -ti:8081 | xargs kill -9

# Or use the stop script
./stop-local-kafka.sh
```

## Notes

- This configuration is optimized for **local development only**
- Single-node setup (no high availability)
- Uses KRaft mode (Zookeeper-free, no Zookeeper required)
- Namespace created automatically by Helm's `--create-namespace` flag
- Persistence enabled by default using Docker Desktop's `hostpath` storage class
- Resource limits set for local development (1 CPU, 1Gi RAM for Kafka)
- Replication factors set to 1 (suitable for single broker)
- Health probes configured for KRaft startup time (~60 seconds)

## Customization

To customize the deployment, create a custom values file:

```yaml
# my-values.yaml
kafka:
  config:
    numPartitions: 5
    logRetentionHours: 72

schemaRegistry:
  config:
    schemaCompatibilityLevel: "FULL"
```

Then install with:

```bash
helm install local-kafka examples/localKafka -n kafka --create-namespace -f my-values.yaml
```
