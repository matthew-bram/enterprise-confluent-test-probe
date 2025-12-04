# CI/CD Pipeline Integration

**Version:** 1.0.0
**Last Updated:** 2025-11-26
**Target Audience:** DevOps engineers and developers setting up automated testing

---

## Table of Contents

1. [GitHub Actions](#github-actions)
2. [GitLab CI](#gitlab-ci)
3. [Jenkins Pipeline](#jenkins-pipeline)
4. [Docker-in-Docker for Testcontainers](#docker-in-docker-for-testcontainers)
5. [Test Report Publishing](#test-report-publishing)
6. [Parallel Test Execution](#parallel-test-execution)
7. [Artifact Collection](#artifact-collection)

---

## GitHub Actions

Complete GitHub Actions workflow with Test-Probe integration:

### Basic Workflow

`.github/workflows/test.yml`:

```yaml
name: Test-Probe CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

env:
  MAVEN_OPTS: -Xmx2048m
  JAVA_VERSION: '21'

jobs:
  unit-tests:
    name: Unit Tests
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: 'maven'

      - name: Run unit tests
        run: mvn test -Punit-only -B

      - name: Upload unit test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: unit-test-results
          path: '**/target/surefire-reports/*.xml'

  component-tests:
    name: Component Tests
    runs-on: ubuntu-latest
    needs: unit-tests  # Run after unit tests pass

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: 'maven'

      - name: Start Docker
        run: |
          sudo systemctl start docker
          docker --version

      - name: Run component tests
        run: mvn test -Pcomponent-only -B

      - name: Upload component test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: component-test-results
          path: |
            **/target/surefire-reports/*.xml
            **/target/cucumber-reports/*.html

      - name: Upload Cucumber reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: cucumber-reports
          path: '**/target/cucumber-reports/**'

  publish-test-results:
    name: Publish Test Results
    runs-on: ubuntu-latest
    needs: [ unit-tests, component-tests ]
    if: always()

    steps:
      - name: Download unit test results
        uses: actions/download-artifact@v4
        with:
          name: unit-test-results

      - name: Download component test results
        uses: actions/download-artifact@v4
        with:
          name: component-test-results

      - name: Publish test results
        uses: EnricoMi/publish-unit-test-result-action@v2
        with:
          files: '**/*.xml'
          check_name: 'Test Results'
          comment_mode: 'off'
```

### Advanced Workflow with Coverage

`.github/workflows/test-coverage.yml`:

```yaml
name: Test Coverage

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  coverage:
    name: Test Coverage Report
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'

      - name: Start Docker
        run: sudo systemctl start docker

      - name: Run tests with coverage
        run: mvn clean test scoverage:report -B

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v4
        with:
          files: '**/target/site/scoverage/scoverage.xml'
          flags: unittests
          name: codecov-umbrella
          fail_ci_if_error: true

      - name: Generate coverage badge
        uses: cicirello/jacoco-badge-generator@v2
        with:
          badges-directory: badges
          generate-coverage-badge: true

      - name: Upload coverage badge
        uses: actions/upload-artifact@v4
        with:
          name: coverage-badge
          path: badges/
```

### Matrix Build (Multiple Java Versions)

`.github/workflows/matrix-test.yml`:

```yaml
name: Multi-Version Testing

on:
  push:
    branches: [ main ]

jobs:
  test:
    name: Test on Java ${{ matrix.java }}
    runs-on: ubuntu-latest

    strategy:
      matrix:
        java: [ '17', '21' ]
      fail-fast: false

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK ${{ matrix.java }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java }}
          distribution: 'temurin'
          cache: 'maven'

      - name: Start Docker
        run: sudo systemctl start docker

      - name: Run all tests
        run: mvn test -B

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results-java-${{ matrix.java }}
          path: '**/target/surefire-reports/*.xml'
```

---

## GitLab CI

Complete GitLab CI configuration with Test-Probe integration:

### Basic Pipeline

`.gitlab-ci.yml`:

```yaml
image: maven:3.9-eclipse-temurin-21

variables:
  MAVEN_OPTS: "-Xmx2048m -Dmaven.repo.local=$CI_PROJECT_DIR/.m2/repository"
  DOCKER_DRIVER: overlay2
  DOCKER_TLS_CERTDIR: "/certs"

stages:
  - build
  - test
  - report

cache:
  paths:
    - .m2/repository/
    - target/

compile:
  stage: build
  script:
    - mvn compile -B -DskipTests
  artifacts:
    paths:
      - target/
    expire_in: 1 hour

unit-tests:
  stage: test
  needs: [ compile ]
  script:
    - mvn test -Punit-only -B
  artifacts:
    when: always
    reports:
      junit:
        - '**/target/surefire-reports/TEST-*.xml'
    paths:
      - '**/target/surefire-reports/'
    expire_in: 1 week

component-tests:
  stage: test
  needs: [ compile ]
  services:
    - docker:24-dind
  variables:
    DOCKER_HOST: tcp://docker:2376
    DOCKER_TLS_VERIFY: 1
    DOCKER_CERT_PATH: "$DOCKER_TLS_CERTDIR/client"
    TESTCONTAINERS_HOST_OVERRIDE: docker
  before_script:
    - apt-get update && apt-get install -y docker.io
  script:
    - mvn test -Pcomponent-only -B
  artifacts:
    when: always
    reports:
      junit:
        - '**/target/surefire-reports/TEST-*.xml'
    paths:
      - '**/target/surefire-reports/'
      - '**/target/cucumber-reports/'
    expire_in: 1 week

coverage:
  stage: report
  needs: [ unit-tests, component-tests ]
  script:
    - mvn scoverage:report -B
  coverage: '/Statement Coverage: ([0-9.]+)%/'
  artifacts:
    reports:
      coverage_report:
        coverage_format: cobertura
        path: '**/target/site/scoverage/cobertura.xml'
    paths:
      - '**/target/site/scoverage/'
    expire_in: 1 month
```

### Advanced Pipeline with Parallel Execution

```yaml
image: maven:3.9-eclipse-temurin-21

variables:
  MAVEN_OPTS: "-Xmx2048m"

stages:
  - build
  - test-parallel
  - report

build:
  stage: build
  script:
    - mvn compile -B -DskipTests
  artifacts:
    paths:
      - target/
    expire_in: 1 hour

.test-template: &test-template
  stage: test-parallel
  needs: [ build ]
  services:
    - docker:24-dind
  artifacts:
    when: always
    reports:
      junit:
        - '**/target/surefire-reports/TEST-*.xml'

unit-tests:
  <<: *test-template
  script:
    - mvn test -Punit-only -B

component-tests-module1:
  <<: *test-template
  script:
    - mvn test -Pcomponent-only -pl module1 -B

component-tests-module2:
  <<: *test-template
  script:
    - mvn test -Pcomponent-only -pl module2 -B

aggregate-coverage:
  stage: report
  needs: [ unit-tests, component-tests-module1, component-tests-module2 ]
  script:
    - mvn scoverage:report -Pcoverage-aggregate -B
  artifacts:
    paths:
      - target/site/scoverage/
    expire_in: 1 month
```

---

## Jenkins Pipeline

Declarative and scripted Jenkins pipelines for Test-Probe:

### Declarative Pipeline

`Jenkinsfile`:

```groovy
pipeline {
    agent {
        docker {
            image 'maven:3.9-eclipse-temurin-21'
            args '-v /var/run/docker.sock:/var/run/docker.sock'
        }
    }

    environment {
        MAVEN_OPTS = '-Xmx2048m'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Compile') {
            steps {
                sh 'mvn compile -B -DskipTests'
            }
        }

        stage('Unit Tests') {
            steps {
                sh 'mvn test -Punit-only -B'
            }
            post {
                always {
                    junit '**/target/surefire-reports/TEST-*.xml'
                }
            }
        }

        stage('Component Tests') {
            steps {
                sh 'mvn test -Pcomponent-only -B'
            }
            post {
                always {
                    junit '**/target/surefire-reports/TEST-*.xml'
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target/cucumber-reports',
                        reportFiles: 'index.html',
                        reportName: 'Cucumber Report'
                    ])
                }
            }
        }

        stage('Coverage Report') {
            steps {
                sh 'mvn scoverage:report -B'
            }
            post {
                success {
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target/site/scoverage',
                        reportFiles: 'index.html',
                        reportName: 'Coverage Report'
                    ])
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo 'Pipeline succeeded!'
        }
        failure {
            echo 'Pipeline failed!'
            // Send notification (Slack, email, etc.)
        }
    }
}
```

### Scripted Pipeline with Parallel Stages

```groovy
node {
    def mvnHome = tool 'Maven 3.9'
    def javaHome = tool 'JDK 21'

    env.MAVEN_OPTS = '-Xmx2048m'
    env.JAVA_HOME = javaHome

    stage('Checkout') {
        checkout scm
    }

    stage('Compile') {
        sh "${mvnHome}/bin/mvn compile -B -DskipTests"
    }

    stage('Parallel Tests') {
        parallel(
            'Unit Tests': {
                sh "${mvnHome}/bin/mvn test -Punit-only -B"
                junit '**/target/surefire-reports/TEST-*.xml'
            },
            'Component Tests - Module 1': {
                sh "${mvnHome}/bin/mvn test -Pcomponent-only -pl module1 -B"
                junit 'module1/target/surefire-reports/TEST-*.xml'
            },
            'Component Tests - Module 2': {
                sh "${mvnHome}/bin/mvn test -Pcomponent-only -pl module2 -B"
                junit 'module2/target/surefire-reports/TEST-*.xml'
            }
        )
    }

    stage('Coverage') {
        sh "${mvnHome}/bin/mvn scoverage:report -Pcoverage-aggregate -B"

        publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'target/site/scoverage',
            reportFiles: 'index.html',
            reportName: 'Coverage Report'
        ])
    }

    stage('Archive Artifacts') {
        archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
        archiveArtifacts artifacts: '**/target/cucumber-reports/**', fingerprint: false
    }
}
```

---

## Docker-in-Docker for Testcontainers

Testcontainers requires Docker access in CI/CD environments:

### GitHub Actions (Docker Available by Default)

```yaml
- name: Run component tests
  run: mvn test -Pcomponent-only -B
  # Docker is already available on GitHub Actions runners
```

### GitLab CI (Docker-in-Docker)

```yaml
component-tests:
  services:
    - docker:24-dind
  variables:
    DOCKER_HOST: tcp://docker:2376
    DOCKER_TLS_VERIFY: 1
    DOCKER_CERT_PATH: "$DOCKER_TLS_CERTDIR/client"
    TESTCONTAINERS_HOST_OVERRIDE: docker
  script:
    - mvn test -Pcomponent-only -B
```

### Jenkins (Docker Socket Binding)

```groovy
agent {
    docker {
        image 'maven:3.9-eclipse-temurin-21'
        args '-v /var/run/docker.sock:/var/run/docker.sock'
    }
}
```

### Docker Compose for Local Testing

`docker-compose.ci.yml`:

```yaml
version: '3.8'

services:
  maven:
    image: maven:3.9-eclipse-temurin-21
    volumes:
      - .:/app
      - /var/run/docker.sock:/var/run/docker.sock
      - maven-cache:/root/.m2
    working_dir: /app
    environment:
      - MAVEN_OPTS=-Xmx2048m
    command: mvn test -B

volumes:
  maven-cache:
```

**Usage:**

```bash
docker-compose -f docker-compose.ci.yml up --abort-on-container-exit
```

---

## Test Report Publishing

### JUnit XML Reports

All CI systems support JUnit XML format:

```xml
<!-- Maven Surefire generates reports automatically -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <reportsDirectory>${project.build.directory}/surefire-reports</reportsDirectory>
    </configuration>
</plugin>
```

**Report Location:** `target/surefire-reports/TEST-*.xml`

### Cucumber HTML Reports

Configure Cucumber to generate HTML reports:

`src/test/java/com/mycompany/TestRunner.java`:

```java
import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
    features = "src/test/resources/features",
    glue = "com.mycompany.steps",
    plugin = {
        "pretty",
        "html:target/cucumber-reports/cucumber.html",
        "json:target/cucumber-reports/cucumber.json",
        "junit:target/cucumber-reports/cucumber.xml"
    }
)
public class TestRunner {
}
```

### Coverage Reports

Scoverage generates HTML and XML coverage reports:

```bash
mvn scoverage:report

# Reports generated at:
# - target/site/scoverage/index.html (HTML)
# - target/site/scoverage/scoverage.xml (XML for CI tools)
# - target/site/scoverage/cobertura.xml (Cobertura format)
```

### Publishing to SonarQube

```yaml
# GitHub Actions
- name: SonarQube Scan
  env:
    SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
  run: |
    mvn sonar:sonar \
      -Dsonar.projectKey=my-project \
      -Dsonar.host.url=https://sonarcloud.io \
      -Dsonar.organization=my-org \
      -Dsonar.coverage.jacoco.xmlReportPaths=target/site/scoverage/cobertura.xml
```

---

## Parallel Test Execution

### Maven Parallel Execution

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <!-- Parallel execution -->
        <parallel>classes</parallel>
        <threadCount>4</threadCount>
        <perCoreThreadCount>false</perCoreThreadCount>

        <!-- Testcontainers resource management -->
        <forkCount>1</forkCount>
        <reuseForks>false</reuseForks>
    </configuration>
</plugin>
```

**Note:** Test-Probe component tests run **sequentially by default** to avoid Kafka container conflicts. See ADR-CUCUMBER-002.

### CI-Specific Parallelization

Split tests across multiple CI jobs:

**GitHub Actions:**

```yaml
strategy:
  matrix:
    test-group: [ module1, module2, module3 ]
steps:
  - name: Run tests for ${{ matrix.test-group }}
    run: mvn test -pl ${{ matrix.test-group }} -B
```

**GitLab CI:**

```yaml
component-tests-module1:
  script: mvn test -Pcomponent-only -pl module1 -B

component-tests-module2:
  script: mvn test -Pcomponent-only -pl module2 -B

component-tests-module3:
  script: mvn test -Pcomponent-only -pl module3 -B
```

---

## Artifact Collection

### GitHub Actions Artifacts

```yaml
- name: Upload test artifacts
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: test-results
    path: |
      **/target/surefire-reports/**
      **/target/cucumber-reports/**
      **/target/site/scoverage/**
    retention-days: 30
```

### GitLab CI Artifacts

```yaml
artifacts:
  when: always
  paths:
    - '**/target/surefire-reports/'
    - '**/target/cucumber-reports/'
    - '**/target/site/scoverage/'
  reports:
    junit:
      - '**/target/surefire-reports/TEST-*.xml'
    coverage_report:
      coverage_format: cobertura
      path: '**/target/site/scoverage/cobertura.xml'
  expire_in: 1 month
```

### Jenkins Artifacts

```groovy
post {
    always {
        // Archive JUnit reports
        junit '**/target/surefire-reports/TEST-*.xml'

        // Archive Cucumber reports
        publishHTML([
            reportDir: 'target/cucumber-reports',
            reportFiles: 'index.html',
            reportName: 'Cucumber Report'
        ])

        // Archive coverage reports
        publishHTML([
            reportDir: 'target/site/scoverage',
            reportFiles: 'index.html',
            reportName: 'Coverage Report'
        ])

        // Archive artifacts
        archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
    }
}
```

---

## Troubleshooting CI/CD Issues

### Testcontainers Timeout

**Problem:** Testcontainers fail to start in CI

**Solution:**

```yaml
# Increase Docker resource limits
env:
  TESTCONTAINERS_RYUK_DISABLED: true  # Disable Ryuk in CI
  DOCKER_MEMORY: 4g
  DOCKER_CPUS: 2
```

### Out of Memory Errors

**Problem:** Maven runs out of memory

**Solution:**

```yaml
env:
  MAVEN_OPTS: -Xmx4096m -XX:MaxMetaspaceSize=512m
```

### Flaky Tests

**Problem:** Tests pass locally but fail in CI

**Solution:**

```java
// Add retries for integration tests
@RepeatedTest(3)
public void testOrderProcessing() {
    // Test logic
}
```

---

## Related Documentation

- [Maven Project Integration](./maven-project.md)
- [Build Scripts](../../../scripts/README.md)
- [Testing Guide](../../../.claude/guides/TESTING.md)

---

**Document Version:** 1.0
**Last Updated:** 2025-11-26
