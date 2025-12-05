# =============================================================================
# Test-Probe Java Quickstart - Multi-Stage Dockerfile
# =============================================================================
# Builds a containerized Test-Probe application.
#
# NOTE: This example depends on test-probe artifacts. In production, you would
# depend on a published test-probe-all fat JAR from your Maven repository.
#
# Build:
#   docker build -t test-probe/java-quickstart:latest -f docker/Dockerfile .
#
# Run:
#   docker run -p 8080:8080 --network quickstart-network test-probe/java-quickstart:latest

# -----------------------------------------------------------------------------
# Stage 1: Build the application
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy POM first to leverage Docker layer caching for dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy Avro schemas (needed for code generation)
COPY src/main/avro ./src/main/avro

# Copy source code and compile
COPY src ./src
COPY config ./config
RUN mvn compile -B

# -----------------------------------------------------------------------------
# Stage 2: Runtime image
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21

LABEL maintainer="Test-Probe Team"
LABEL description="Test-Probe Java Quickstart Example"
LABEL version="1.0.0-SNAPSHOT"

WORKDIR /app

# Copy the entire project (needed for mvn exec:java)
COPY --from=builder /app /app

# Expose REST API port
EXPOSE 8080

# Health check via REST API
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Run via Maven exec plugin
ENTRYPOINT ["mvn", "exec:java", "-q"]
