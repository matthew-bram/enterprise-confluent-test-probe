package io.distia.probe
package core
package integration

import io.cucumber.junit.{Cucumber, CucumberOptions}
import org.junit.runner.RunWith

/**
 * JUnit runner for integration tests
 *
 * This runner executes black-box integration tests that verify:
 * - Complete end-to-end flow (initializeTest → startTest → Cucumber execution)
 * - Real ActorSystem with GuardianActor, QueueActor, and child actors
 * - Real Kafka + Schema Registry (via Testcontainers)
 * - ServiceInterfaceFunctions API
 * - Kafka produce/consume with all schema types (AVRO, Protobuf, JSON)
 * - Evidence generation (Cucumber reports in JIMFS)
 *
 * Test Flow:
 * 1. Background: Initialize IntegrationWorld (Testcontainers, ActorSystem, JIMFS)
 * 2. Scenario 1: Call initializeTest() and verify test ID
 * 3. Scenario 2: Call startTest() and verify Cucumber execution
 * 4. Scenario 3: Verify Kafka events (produced/consumed)
 * 5. Scenario 4: Verify evidence files (JSON report)
 *
 * Maven Execution:
 * {{{
 *   # Run integration tests only
 *   mvn test -Dtest=IntegrationTestRunner -pl test-probe-core
 *
 *   # With profile (if configured)
 *   mvn test -Pintegration-only -pl test-probe-core
 * }}}
 *
 * Reports Generated:
 * - HTML: target/cucumber-reports/integration.html
 * - JSON: target/cucumber-reports/integration.json
 */
@RunWith(classOf[Cucumber])
@CucumberOptions(
  features = Array("classpath:features/integration"),
  glue = Array(
    "io.distia.probe.core.integration.steps",
    "io.distia.probe.core.integration.hooks",
    "com.fake.company.tests"  // Inner test step definitions
  ),
  plugin = Array(
    "pretty",
    "html:target/cucumber-reports/integration.html",
    "json:target/cucumber-reports/integration.json"
  ),
  monochrome = true
)
class IntegrationTestRunner
