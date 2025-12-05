# Enterprise Confluent Test Probe

![Build Status](https://github.com/matthew-bram/enterprise-confluent-test-probe/workflows/CI/badge.svg)
[![codecov](https://codecov.io/gh/matthew-bram/enterprise-confluent-test-probe/graph/badge.svg)](https://codecov.io/gh/matthew-bram/enterprise-confluent-test-probe)
![License](https://img.shields.io/github/license/matthew-bram/enterprise-confluent-test-probe)

## Overview

A comprehensive testing and validation framework for enterprise Apache Kafka and Confluent Platform deployments. Built with Scala 3, Apache Pekko actors, and designed for BDD-style integration testing.

## Features

- **Actor-based Architecture**: Built on Apache Pekko for reliable, concurrent test execution
- **Multi-cluster Support**: Test across multiple Kafka clusters with per-topic bootstrap server configuration
- **Multiple Serialization Formats**: JSON, Avro, and Protobuf with Confluent Schema Registry integration
- **Multi-cloud Storage**: Evidence storage support for AWS S3, Azure Blob Storage, and Google Cloud Storage
- **BDD Testing**: Cucumber/Gherkin feature files for readable test scenarios
- **Comprehensive Coverage**: 70%+ code coverage with 85% target for actor FSM code

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for component tests with Testcontainers)

## Quick Start

```bash
# Clone the repository
git clone https://github.com/matthew-bram/enterprise-confluent-test-probe.git
cd enterprise-confluent-test-probe

# Build the project
./mvnw compile

# Run unit tests
./scripts/test-unit.sh

# Run all tests (requires Docker)
./scripts/test-all.sh
```

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
| `./scripts/test-component.sh` | Component tests with Testcontainers |
| `./scripts/test-all.sh` | Unit + component tests |
| `./scripts/test-coverage.sh` | Tests with coverage report |
| `./scripts/build-ci.sh` | Full CI pipeline |

## Maven Profiles

```bash
# Development (default)
./mvnw test

# Unit tests only
./mvnw test -Punit-only

# Component tests only (requires Docker)
./mvnw test -Pcomponent-only

# Coverage report
./mvnw scoverage:report -Pcoverage
```

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

Before contributing, please read our [Code of Conduct](CODE_OF_CONDUCT.md).

## Security

For security concerns, please see our [Security Policy](SECURITY.md). Do not report security vulnerabilities through public GitHub issues.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/matthew-bram/enterprise-confluent-test-probe/issues)
- **Discussions**: [GitHub Discussions](https://github.com/matthew-bram/enterprise-confluent-test-probe/discussions)
