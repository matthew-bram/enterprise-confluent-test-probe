# Adding Test-Probe to Maven Project

**Version:** 1.0.0
**Last Updated:** 2025-11-26
**Target Audience:** Java/Scala developers using Maven

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Required Dependencies](#required-dependencies)
3. [Plugin Configuration](#plugin-configuration)
4. [Test Profile Setup](#test-profile-setup)
5. [Resource Filtering](#resource-filtering)
6. [Multi-Module Project Setup](#multi-module-project-setup)

---

## Quick Start

Minimum viable `pom.xml` configuration to add Test-Probe to your project:

```xml
<project>
    <properties>
        <test-probe.version>0.0.1-SNAPSHOT</test-probe.version>
        <scala.version>3.3.6</scala.version>
        <scala.binary.version>3</scala.binary.version>
        <cucumber.version>7.20.1</cucumber.version>
    </properties>

    <dependencies>
        <!-- Test-Probe Core -->
        <dependency>
            <groupId>io.distia.probe</groupId>
            <artifactId>test-probe-core</artifactId>
            <version>${test-probe.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- Test-Probe Java API (for Java projects) -->
        <dependency>
            <groupId>io.distia.probe</groupId>
            <artifactId>test-probe-java-api</artifactId>
            <version>${test-probe.version}</version>
            <scope>test</scope>
        </dependency>

        <!-- Cucumber for BDD testing -->
        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-java</artifactId>
            <version>${cucumber.version}</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-junit</artifactId>
            <version>${cucumber.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Maven Surefire for Cucumber tests -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.2</version>
                <configuration>
                    <includes>
                        <include>**/*TestRunner.java</include>
                    </includes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Required Dependencies

### Core Dependencies

```xml
<dependencies>
    <!-- ========================================== -->
    <!-- Test-Probe Framework                      -->
    <!-- ========================================== -->

    <!-- Core framework (required) -->
    <dependency>
        <groupId>io.distia.probe</groupId>
        <artifactId>test-probe-core</artifactId>
        <version>${test-probe.version}</version>
        <scope>test</scope>
    </dependency>

    <!-- Java API (for Java projects) -->
    <dependency>
        <groupId>io.distia.probe</groupId>
        <artifactId>test-probe-java-api</artifactId>
        <version>${test-probe.version}</version>
        <scope>test</scope>
    </dependency>

    <!-- ========================================== -->
    <!-- BDD Testing Framework                     -->
    <!-- ========================================== -->

    <dependency>
        <groupId>io.cucumber</groupId>
        <artifactId>cucumber-java</artifactId>
        <version>${cucumber.version}</version>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>io.cucumber</groupId>
        <artifactId>cucumber-junit</artifactId>
        <version>${cucumber.version}</version>
        <scope>test</scope>
    </dependency>

    <!-- ========================================== -->
    <!-- Kafka & Schema Registry                   -->
    <!-- ========================================== -->

    <dependency>
        <groupId>org.apache.kafka</groupId>
        <artifactId>kafka-clients</artifactId>
        <version>${kafka.version}</version>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>io.confluent</groupId>
        <artifactId>kafka-schema-registry-client</artifactId>
        <version>${confluent.version}</version>
        <scope>test</scope>
    </dependency>

    <!-- ========================================== -->
    <!-- Testcontainers (for integration tests)    -->
    <!-- ========================================== -->

    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>kafka</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Optional Dependencies

```xml
<!-- For Scala projects -->
<dependency>
    <groupId>org.scala-lang</groupId>
    <artifactId>scala3-library_3</artifactId>
    <version>${scala.version}</version>
</dependency>

<!-- For Avro serialization -->
<dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-avro-serializer</artifactId>
    <version>${confluent.version}</version>
    <scope>test</scope>
</dependency>

<!-- For Protobuf serialization -->
<dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-protobuf-serializer</artifactId>
    <version>${confluent.version}</version>
    <scope>test</scope>
</dependency>

<!-- For JSON Schema serialization -->
<dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-json-schema-serializer</artifactId>
    <version>${confluent.version}</version>
    <scope>test</scope>
</dependency>
```

---

## Plugin Configuration

### Maven Surefire Plugin (Cucumber Tests)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.2</version>
    <configuration>
        <!-- Include Cucumber test runners -->
        <includes>
            <include>**/*TestRunner.java</include>
            <include>**/*Runner.java</include>
        </includes>

        <!-- Testcontainers requires higher resource limits -->
        <forkCount>1</forkCount>
        <reuseForks>false</reuseForks>
        <argLine>-Xmx2048m -Xms512m</argLine>

        <!-- System properties for Testcontainers -->
        <systemPropertyVariables>
            <testcontainers.reuse.enable>true</testcontainers.reuse.enable>
        </systemPropertyVariables>

        <!-- Parallel execution (optional, for fast tests) -->
        <parallel>methods</parallel>
        <threadCount>2</threadCount>
    </configuration>
</plugin>
```

### Scala Maven Plugin (for Scala projects)

```xml
<plugin>
    <groupId>net.alchim31.maven</groupId>
    <artifactId>scala-maven-plugin</artifactId>
    <version>4.9.2</version>
    <configuration>
        <scalaVersion>${scala.version}</scalaVersion>
        <args>
            <arg>-deprecation</arg>
            <arg>-feature</arg>
            <arg>-unchecked</arg>
        </args>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>compile</goal>
                <goal>testCompile</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Avro Maven Plugin (if using Avro)

```xml
<plugin>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro-maven-plugin</artifactId>
    <version>1.11.3</version>
    <executions>
        <execution>
            <phase>generate-test-sources</phase>
            <goals>
                <goal>schema</goal>
            </goals>
            <configuration>
                <sourceDirectory>${project.basedir}/src/test/avro</sourceDirectory>
                <outputDirectory>${project.build.directory}/generated-test-sources/avro</outputDirectory>
                <stringType>String</stringType>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Protobuf Maven Plugin (if using Protobuf)

```xml
<plugin>
    <groupId>org.xolstice.maven.plugins</groupId>
    <artifactId>protobuf-maven-plugin</artifactId>
    <version>0.6.1</version>
    <configuration>
        <protocArtifact>com.google.protobuf:protoc:3.24.4:exe:${os.detected.classifier}</protocArtifact>
        <protoTestSourceRoot>${project.basedir}/src/test/protobuf</protoTestSourceRoot>
        <outputDirectory>${project.build.directory}/generated-test-sources/protobuf</outputDirectory>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>test-compile</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## Test Profile Setup

Separate unit and component tests for faster feedback cycles:

```xml
<properties>
    <!-- Test execution control -->
    <skipUnitTests>false</skipUnitTests>
    <skipComponentTests>false</skipComponentTests>
</properties>

<profiles>
    <!-- Unit tests only (fast, no Docker) -->
    <profile>
        <id>unit-only</id>
        <properties>
            <skipUnitTests>false</skipUnitTests>
            <skipComponentTests>true</skipComponentTests>
        </properties>
    </profile>

    <!-- Component tests only (integration, requires Docker) -->
    <profile>
        <id>component-only</id>
        <properties>
            <skipUnitTests>true</skipUnitTests>
            <skipComponentTests>false</skipComponentTests>
        </properties>
    </profile>

    <!-- All tests (default) -->
    <profile>
        <id>all-tests</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <properties>
            <skipUnitTests>false</skipUnitTests>
            <skipComponentTests>false</skipComponentTests>
        </properties>
    </profile>
</profiles>
```

**Usage:**

```bash
# Run unit tests only (fast)
mvn test -Punit-only

# Run component tests only (Docker required)
mvn test -Pcomponent-only

# Run all tests
mvn test
```

---

## Resource Filtering

Configure Maven to filter test resources for environment-specific values:

### Enable Resource Filtering

```xml
<build>
    <testResources>
        <testResource>
            <directory>src/test/resources</directory>
            <filtering>true</filtering>
            <includes>
                <include>application-test.conf</include>
                <include>cucumber.properties</include>
            </includes>
        </testResource>
        <testResource>
            <directory>src/test/resources</directory>
            <filtering>false</filtering>
            <excludes>
                <exclude>application-test.conf</exclude>
                <exclude>cucumber.properties</exclude>
            </excludes>
        </testResource>
    </testResources>
</build>
```

### Define Filtered Properties

```xml
<properties>
    <!-- Test environment -->
    <test.kafka.bootstrap.servers>${env.KAFKA_BOOTSTRAP_SERVERS}</test.kafka.bootstrap.servers>
    <test.schema.registry.url>${env.SCHEMA_REGISTRY_URL}</test.schema.registry.url>

    <!-- Fallback to Testcontainers defaults -->
    <test.kafka.bootstrap.servers>localhost:9092</test.kafka.bootstrap.servers>
    <test.schema.registry.url>http://localhost:8081</test.schema.registry.url>
</properties>
```

### Use Filtered Properties in Test Configuration

`src/test/resources/application-test.conf`:

```hocon
test-probe {
  core {
    kafka {
      bootstrap-servers = "${test.kafka.bootstrap.servers}"
      schema-registry-url = "${test.schema.registry.url}"
    }
  }
}
```

---

## Multi-Module Project Setup

For projects with multiple modules using Test-Probe:

### Parent POM

```xml
<project>
    <groupId>com.mycompany</groupId>
    <artifactId>my-project-parent</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <modules>
        <module>my-project-common</module>
        <module>my-project-services</module>
        <module>my-project-api</module>
    </modules>

    <properties>
        <test-probe.version>0.0.1-SNAPSHOT</test-probe.version>
        <cucumber.version>7.20.1</cucumber.version>
    </properties>

    <!-- Dependency management -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.distia.probe</groupId>
                <artifactId>test-probe-core</artifactId>
                <version>${test-probe.version}</version>
                <scope>test</scope>
            </dependency>

            <dependency>
                <groupId>io.distia.probe</groupId>
                <artifactId>test-probe-java-api</artifactId>
                <version>${test-probe.version}</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <!-- Plugin management -->
    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.2.2</version>
                    <configuration>
                        <includes>
                            <include>**/*TestRunner.java</include>
                        </includes>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

### Child Module POM

```xml
<project>
    <parent>
        <groupId>com.mycompany</groupId>
        <artifactId>my-project-parent</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>my-project-services</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <!-- Inherit Test-Probe dependencies from parent -->
        <dependency>
            <groupId>io.distia.probe</groupId>
            <artifactId>test-probe-core</artifactId>
        </dependency>

        <dependency>
            <groupId>io.distia.probe</groupId>
            <artifactId>test-probe-java-api</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Inherit plugin configuration from parent -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Building Specific Modules

```bash
# Build single module with dependencies
mvn test -pl my-project-services -am

# Build multiple modules
mvn test -pl my-project-services,my-project-api -am

# Build all modules
mvn test
```

---

## Complete Example POM

Full working example for a typical project:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <groupId>com.mycompany</groupId>
    <artifactId>order-service</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>Order Service</name>

    <properties>
        <!-- Build properties -->
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- Dependency versions -->
        <test-probe.version>0.0.1-SNAPSHOT</test-probe.version>
        <cucumber.version>7.20.1</cucumber.version>
        <kafka.version>3.8.1</kafka.version>
        <confluent.version>8.1.0</confluent.version>
        <testcontainers.version>1.20.4</testcontainers.version>
        <junit.version>5.10.1</junit.version>

        <!-- Test execution control -->
        <skipUnitTests>false</skipUnitTests>
        <skipComponentTests>false</skipComponentTests>

        <!-- Test environment -->
        <test.kafka.bootstrap.servers>localhost:9092</test.kafka.bootstrap.servers>
        <test.schema.registry.url>http://localhost:8081</test.schema.registry.url>
    </properties>

    <dependencies>
        <!-- Application dependencies -->
        <dependency>
            <groupId>org.apache.kafka</groupId>
            <artifactId>kafka-clients</artifactId>
            <version>${kafka.version}</version>
        </dependency>

        <!-- Test dependencies -->
        <dependency>
            <groupId>io.distia.probe</groupId>
            <artifactId>test-probe-core</artifactId>
            <version>${test-probe.version}</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>io.distia.probe</groupId>
            <artifactId>test-probe-java-api</artifactId>
            <version>${test-probe.version}</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-java</artifactId>
            <version>${cucumber.version}</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-junit</artifactId>
            <version>${cucumber.version}</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>kafka</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.2</version>
                <configuration>
                    <skipTests>${skipComponentTests}</skipTests>
                    <includes>
                        <include>**/*TestRunner.java</include>
                    </includes>
                    <forkCount>1</forkCount>
                    <reuseForks>false</reuseForks>
                    <argLine>-Xmx2048m</argLine>
                </configuration>
            </plugin>
        </plugins>

        <testResources>
            <testResource>
                <directory>src/test/resources</directory>
                <filtering>true</filtering>
                <includes>
                    <include>application-test.conf</include>
                </includes>
            </testResource>
            <testResource>
                <directory>src/test/resources</directory>
                <filtering>false</filtering>
                <excludes>
                    <include>application-test.conf</include>
                </excludes>
            </testResource>
        </testResources>
    </build>

    <profiles>
        <profile>
            <id>unit-only</id>
            <properties>
                <skipUnitTests>false</skipUnitTests>
                <skipComponentTests>true</skipComponentTests>
            </properties>
        </profile>

        <profile>
            <id>component-only</id>
            <properties>
                <skipUnitTests>true</skipUnitTests>
                <skipComponentTests>false</skipComponentTests>
            </properties>
        </profile>
    </profiles>

    <repositories>
        <repository>
            <id>confluent</id>
            <url>https://packages.confluent.io/maven/</url>
        </repository>
    </repositories>

</project>
```

---

## Next Steps

- [CI/CD Pipeline Integration](./ci-cd-pipelines.md)
- [Writing Your First Test](../getting-started/first-test.md)
- [Probe Java DSL Examples](../../api/examples/probe-java-dsl-examples.md)

---

**Document Version:** 1.0
**Last Updated:** 2025-11-26
