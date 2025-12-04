package io.distia.probe.core.glue

import io.cucumber.scala.{EN, ScalaDsl}

/**
 * Stub step definitions for bdd-framework-minimal-valid-test-stub.feature.
 *
 * Used by CucumberExecutionActorSpec unit tests to verify Cucumber execution.
 * These steps do nothing except pass - they exist purely for testing the framework.
 */
class StubStepDefinitions extends ScalaDsl with EN {

  Given("a stub step that passes") { () =>
    // Stub step - always passes
  }

  When("the stub executes successfully") { () =>
    // Stub step - always passes
  }
}
