# Test-Probe User Guide

Welcome to the Test-Probe user documentation. This guide will help you get started with event-driven architecture testing using Test-Probe.

## Getting Started

New to Test-Probe? Start here:

1. **[Getting Started Guide](GETTING-STARTED.md)** - Complete tutorial from installation to your first tests
   - Prerequisites and installation
   - Your first test (5-minute quickstart)
   - Understanding the framework
   - Advanced topics

## Core Concepts

Understanding Test-Probe fundamentals:

- **[Builder Pattern Guide](BUILDER-PATTERN.md)** - How to bootstrap the Test-Probe framework
  - ServiceDsl API reference
  - Compile-time safety with phantom types
  - Three-phase module lifecycle (preFlight → initialize → finalCheck)
  - Module implementations (Config, ActorSystem, Storage, Vault, Interface)
  - Complete production examples
  - **START HERE** if you're integrating Test-Probe into your application

## Reference Documentation

- **[FAQ](FAQ.md)** - Frequently asked questions about Test-Probe
  - General framework questions
  - Installation and setup
  - Kafka-specific questions
  - Serialization formats

- **[Troubleshooting Guide](TROUBLESHOOTING.md)** - Solutions to common issues
  - Docker and Testcontainers issues
  - Kafka connection problems
  - Schema Registry errors
  - Test timeout issues

## Additional Resources

### Architecture Documentation
- [Product Requirements Document](../product/PRODUCT-REQUIREMENTS-DOCUMENT.md) - Complete PRD
- [Architecture Guides](../architecture/) - System architecture and ADRs

### Developer Documentation
- [Contributing Guide](../../CONTRIBUTING.md) - How to contribute
- [Build Scripts](../../scripts/README.md) - Build automation
- [Development Guide](../../.claude/guides/DEVELOPMENT.md) - Developer workflow

## Quick Links

### Common Tasks
- [Write your first test](#) → See [Getting Started](GETTING-STARTED.md#your-first-test)
- [Configure multiple Kafka clusters](#) → See [Getting Started](GETTING-STARTED.md#multiple-kafka-clusters)
- [Use Avro serialization](#) → See [Getting Started](GETTING-STARTED.md#avro-serialization)
- [Debug test failures](#) → See [Troubleshooting](TROUBLESHOOTING.md#test-failures)

### Support
- **Issues**: Report bugs or request features via GitHub Issues
- **Discussions**: Ask questions or share ideas via GitHub Discussions
- **Documentation**: Browse the full documentation at `docs/`

---

**Happy Testing!**
