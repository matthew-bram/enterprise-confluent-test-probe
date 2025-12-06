# Enterprise Confluent Test Probe

![Build Status](https://github.com/matthew-bram/enterprise-confluent-test-probe/workflows/CI/badge.svg)
[![codecov](https://codecov.io/gh/matthew-bram/enterprise-confluent-test-probe/graph/badge.svg)](https://codecov.io/gh/matthew-bram/enterprise-confluent-test-probe)
![License](https://img.shields.io/github/license/matthew-bram/enterprise-confluent-test-probe)

## Overview

A BDD testing framework for **functionally validating event-driven systems against real Kafka clusters** in integration environments (SIT, UAT, production-like). Built with Scala 3, Apache Pekko actors, and Cucumber/Gherkin for business-readable test scenarios.

**Test-Probe enables teams to validate that their services and cells behave correctly throughout the SDLC and CD pipelines.**

## Why Test-Probe?

- **Functional Testing for Kafka Services**: Validate CQRS, CQERS, event sourcing, and other event-driven patterns
- **Event Democratization Testing**: Validate consumers handle multiple event schemas per topic correctly
- **Real Cluster Testing**: Test against your actual Kafka deployments, not mocks or local containers
- **BDD/Gherkin Scenarios**: Write business-readable tests that bridge the gap between technical and domain teams
- **Multi-Cloud Support**: Evidence storage across AWS S3, Azure Blob Storage, and Google Cloud Storage
- **Compliance & Audit**: Generate test evidence and audit trails for regulated industries

## Use Cases

Test-Probe supports common enterprise event-driven patterns:

### CQERS Pattern (Command Query Event Responsibility Segregation)

```
┌─────────────────┐    ┌──────────────────────────┐    ┌─────────────────┐
│    Event A      │───▶│         Cell             │───▶│    Event B      │
│ (order.created) │    │  • Updates materialized  │    │ (inventory.     │
└─────────────────┘    │    view / data store     │    │    reserved)    │
                       │  • Merges with other     │    └─────────────────┘
                       │    data sources          │
                       └──────────────────────────┘
```

```gherkin
Feature: Order Processing Cell
  Scenario: Order event triggers inventory reservation
    Given the inventory service is consuming from "order-events"
    When I produce an OrderCreated event to "order-events"
    Then I should receive an InventoryReserved event from "inventory-events" within 30 seconds
```

### CQRS Pattern (Command Query Responsibility Segregation)

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────────┐
│   Command   │───▶│     SOR     │───▶│    Event    │───▶│   Read Model    │
│     API     │    │   Update    │    │   Emitted   │    │    Hydrated     │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────────┘
                   (System of Record)                    (Materialized View)
```

```gherkin
Feature: Customer Read Model Hydration
  Scenario: Customer update propagates to read model
    Given the read model service is consuming from "customer-events"
    When I POST a customer update to the command API
    Then the read model should contain the updated customer within 10 seconds
```

### Cross-Cell / Cross-Service Testing

Validate event contracts between services, ensuring upstream producers and downstream consumers remain compatible.

### Event Democratization (Multi-Schema Topics)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Shared Domain Topic                               │
│                     (e.g., "customer-events")                            │
├─────────────────────────────────────────────────────────────────────────┤
│  CustomerCreated   │  CustomerUpdated   │  CustomerDeleted   │  ...     │
│  (Schema v1)       │  (Schema v2)       │  (Schema v1)       │          │
└─────────────────────────────────────────────────────────────────────────┘
                              │
           ┌──────────────────┼──────────────────┐
           ▼                  ▼                  ▼
    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
    │  Marketing  │    │   Billing   │    │  Analytics  │
    │   Service   │    │   Service   │    │   Service   │
    └─────────────┘    └─────────────┘    └─────────────┘
```

Enterprise Kafka deployments often consolidate multiple event types onto shared domain topics, enabling:

- **Reduced Topic Sprawl**: Fewer topics to manage, monitor, and secure
- **Domain Cohesion**: All events for a bounded context live together
- **Consumer Flexibility**: Services subscribe once and filter by event type

**Test-Probe validates event democratization by:**

- Producing multiple event types (different schemas) to a single topic
- Verifying consumers correctly filter and process only relevant event types
- Testing schema compatibility when multiple versions coexist on a topic
- Validating that consumers gracefully ignore unknown event types

## Assumptions

Test-Probe is designed for enterprise Kafka deployments with the following assumptions:

| Category | Assumption |
|----------|------------|
| **Authentication** | OAuth 2.0 Client Credentials Grant (client ID + client secret via vault) |
| **Platform** | Confluent Platform or industrialized Apache Kafka (RedPanda may work as alternative) |
| **Schema Registry** | Schemas are preloaded before test execution; schema evolution handled externally |
| **Data Model** | Topic keys follow the [CloudEvents.io](https://cloudevents.io/) specification |
| **Evidence** | Evidence drop storage is configured (S3, Azure Blob, GCS, or local filesystem) |

## Features

- **Actor-based Architecture**: Built on Apache Pekko for reliable, concurrent test execution
- **Multi-cluster Support**: Test across multiple Kafka clusters with per-topic bootstrap server configuration
- **Multiple Serialization Formats**: JSON, Avro, and Protobuf with Confluent Schema Registry integration
- **Multi-cloud Vault Integration**: Secrets from AWS Secrets Manager, Azure Key Vault, GCP Secret Manager
- **Multi-cloud Storage**: Evidence storage support for AWS S3, Azure Blob Storage, and Google Cloud Storage
- **BDD Testing**: Cucumber/Gherkin feature files for readable test scenarios
- **Comprehensive Coverage**: 70%+ code coverage with 85% target for actor FSM code

## Prerequisites

- Java 21+
- Maven 3.9+
- Access to a Kafka cluster (Confluent Platform, AWS MSK, Azure Event Hubs, etc.)
- Schema Registry access (Confluent Schema Registry)
- Vault access for credentials (AWS Secrets Manager, Azure Key Vault, or GCP Secret Manager)

## Quick Start

```bash
# Clone the repository
git clone https://github.com/matthew-bram/enterprise-confluent-test-probe.git
cd enterprise-confluent-test-probe

# Configure your Kafka cluster connection (see Getting Started guide)
# Set environment variables or update application.conf

# Build the project
./mvnw compile

# Run unit tests
./scripts/test-unit.sh
```

See the [Getting Started Guide](docs/user-guide/GETTING-STARTED.md) for detailed cluster configuration.

## Project Structure

```
test-probe-parent/
├── test-probe-common/      # Shared models, Rosetta mapping, exceptions
├── test-probe-core/        # Actor system, FSM, Kafka streaming
├── test-probe-services/    # Cloud integrations (vault, storage)
├── test-probe-interfaces/  # REST API endpoints
├── test-probe-java-api/    # Java DSL wrapper
├── test-probe/             # Main application module
├── scripts/                # Build and test automation
├── docs/                   # User guides and architecture docs
└── examples/               # Quickstart examples
```

## Documentation

- [Getting Started Guide](docs/user-guide/GETTING-STARTED.md)
- [User Guide](docs/user-guide/README.md)
- [Architecture](docs/architecture/)
- [Contributing Guidelines](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Security Policy](SECURITY.md)

## Build Scripts

| Script | Description |
|--------|-------------|
| `./scripts/compile.sh` | Fast compilation |
| `./scripts/test-unit.sh` | Unit tests only (~10s) |
| `./scripts/test-component.sh` | Component tests (framework development) |
| `./scripts/test-all.sh` | Unit + component tests |
| `./scripts/test-coverage.sh` | Tests with coverage report |
| `./scripts/build-ci.sh` | Full CI pipeline |

> **Note**: Component tests are used for framework development and require Docker. See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup.

## Maven Profiles

```bash
# Development (default)
./mvnw test

# Unit tests only
./mvnw test -Punit-only

# Coverage report
./mvnw scoverage:report -Pcoverage
```

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

Before contributing, please read our [Code of Conduct](CODE_OF_CONDUCT.md).

> **Note**: Framework development uses Docker and Testcontainers for internal testing. See [CONTRIBUTING.md](CONTRIBUTING.md) for development environment setup.

## Security

For security concerns, please see our [Security Policy](SECURITY.md). Do not report security vulnerabilities through public GitHub issues.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/matthew-bram/enterprise-confluent-test-probe/issues)
- **Discussions**: [GitHub Discussions](https://github.com/matthew-bram/enterprise-confluent-test-probe/discussions)
