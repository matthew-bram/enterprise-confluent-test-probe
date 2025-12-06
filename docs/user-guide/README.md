# Test-Probe User Guide

Welcome to the Test-Probe user documentation. This guide will help you get started with event-driven architecture testing using Test-Probe.

## Overview

Test-Probe is a **REST API service** that enables teams to validate event-driven system behaviors against real Kafka clusters. You write Gherkin feature files, submit them via API, and receive test results with evidence.

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│   Your CI/CD    │         │   Test-Probe    │         │   Your Kafka    │
│   Pipeline      │◀───────▶│   Service       │◀───────▶│   Cluster       │
└─────────────────┘  REST   └─────────────────┘  Kafka  └─────────────────┘
```

## Getting Started

New to Test-Probe? Start here:

1. **[Getting Started Guide](GETTING-STARTED.md)** - Complete guide to using Test-Probe
   - Prerequisites and cluster configuration
   - Connecting to your Kafka cluster
   - Authentication and vault integration
   - Understanding test patterns (CQRS, CQERS)

2. **[Tutorial: Your First Test](tutorials/01-first-test.md)** - Step-by-step tutorial
   - Write a Gherkin feature file
   - Submit to Test-Probe API
   - Poll for results
   - Review evidence

## CI/CD Integration

- **[CI/CD Pipeline Integration](integration/ci-cd-pipelines.md)** - Integrate Test-Probe into your pipelines
  - REST API workflow (initialize → deploy → start → poll)
  - GitHub Actions examples
  - GitLab CI examples
  - Jenkins Pipeline examples
  - Quality gates and evidence collection

## Tutorials

Step-by-step tutorials for common scenarios:

| Tutorial | Description |
|----------|-------------|
| [01: Your First Test](tutorials/01-first-test.md) | Write and submit your first test |
| [02: JSON Events](tutorials/02-json-events.md) | Work with JSON serialization *(updating)* |
| [03: Avro & Protobuf](tutorials/03-avro-protobuf.md) | Schema-based serialization *(updating)* |
| [04: Multi-Cluster](tutorials/04-multi-cluster.md) | Cross-datacenter testing *(updating)* |

## Reference Documentation

- **[FAQ](FAQ.md)** - Frequently asked questions
  - What is Test-Probe?
  - System requirements
  - Kafka connection questions
  - Serialization formats

- **[Troubleshooting Guide](TROUBLESHOOTING.md)** - Solutions to common issues
  - Kafka connection problems
  - Authentication issues
  - Schema Registry errors
  - Test timeout issues

## For Framework Contributors

If you're contributing to the Test-Probe framework itself:

- **[Contributing Guide](../../CONTRIBUTING.md)** - Contribution workflow
- **[Framework Testing Guide](../dev/FRAMEWORK-TESTING.md)** - Running framework tests (Docker/Testcontainers)
- **[Build Scripts](../../scripts/README.md)** - Build automation for framework development

## Architecture Documentation

- [Architecture Guides](../architecture/) - System architecture and ADRs
- [API Reference (OpenAPI)](../../test-probe-interfaces/src/main/resources/openapi.yaml) - REST API specification

## Quick Links

### Common Tasks

| Task | Documentation |
|------|---------------|
| Write your first test | [Tutorial 1](tutorials/01-first-test.md) |
| Configure Kafka connection | [Getting Started](GETTING-STARTED.md#connecting-to-your-kafka-cluster) |
| Integrate with CI/CD | [CI/CD Pipelines](integration/ci-cd-pipelines.md) |
| Debug test failures | [Troubleshooting](TROUBLESHOOTING.md) |

### Support

- **Issues**: Report bugs or request features via GitHub Issues
- **Discussions**: Ask questions or share ideas via GitHub Discussions

---

**Last Updated:** 2025-12-05
