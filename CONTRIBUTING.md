# Contributing to Enterprise Confluent Test Probe

Thank you for your interest in contributing! This document provides guidelines and information about contributing to this project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
- [Development Workflow](#development-workflow)
- [Pull Request Process](#pull-request-process)
- [Style Guidelines](#style-guidelines)
- [Community](#community)

## Code of Conduct

This project adheres to a [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to the maintainers.

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR-USERNAME/enterprise-confluent-test-probe.git
   cd enterprise-confluent-test-probe
   ```
3. **Add the upstream remote**:
   ```bash
   git remote add upstream https://github.com/matthew-bram/enterprise-confluent-test-probe.git
   ```
4. **Set up your development environment** (see README.md for specific instructions)

## How to Contribute

### Reporting Bugs

- Check existing issues to avoid duplicates
- Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md)
- Include detailed steps to reproduce
- Provide environment information

### Suggesting Features

- Use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.md)
- Explain the use case and benefits
- Be open to discussion about implementation

### Contributing Code

1. Find an issue to work on, or create one
2. Comment on the issue to indicate you're working on it
3. Follow the development workflow below

## Development Workflow

1. **Create a feature branch** from `main`:
   ```bash
   git checkout main
   git pull upstream main
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**:
   - Write clean, readable code
   - Add tests for new functionality
   - Update documentation as needed

3. **Commit your changes**:
   ```bash
   git add .
   git commit -m "feat: add your feature description"
   ```

   We follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat:` - New features
   - `fix:` - Bug fixes
   - `docs:` - Documentation changes
   - `test:` - Test additions or modifications
   - `refactor:` - Code refactoring
   - `chore:` - Maintenance tasks

4. **Keep your branch updated**:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

5. **Push your branch**:
   ```bash
   git push origin feature/your-feature-name
   ```

## Pull Request Process

1. **Open a Pull Request** against the `main` branch
2. **Fill out the PR template** completely
3. **Ensure all checks pass**:
   - Tests must pass
   - Code style checks must pass
   - No merge conflicts
4. **Request review** from maintainers
5. **Address feedback** promptly
6. **Squash commits** if requested before merge

### PR Requirements

- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] Conventional commit messages used
- [ ] No breaking changes (or clearly documented)
- [ ] Signed commits (recommended)

## Style Guidelines

### Code Style

- Follow the existing code style in the project
- Use meaningful variable and function names
- Comment complex logic
- Keep functions focused and small

### Documentation

- Update README.md for user-facing changes
- Add inline comments for complex code
- Include docstrings for public APIs

### Commit Messages

Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
type(scope): description

[optional body]

[optional footer]
```

## Community

- **Discussions**: Use [GitHub Discussions](https://github.com/matthew-bram/enterprise-confluent-test-probe/discussions) for questions and ideas
- **Issues**: For bugs and feature requests

## Questions?

If you have questions about contributing, feel free to:
- Open a discussion
- Ask in an existing issue
- Reach out to the maintainers

Thank you for contributing!
