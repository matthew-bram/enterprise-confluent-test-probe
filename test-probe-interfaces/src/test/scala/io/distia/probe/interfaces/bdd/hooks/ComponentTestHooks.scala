package io.distia.probe.interfaces.bdd.hooks

import io.distia.probe.interfaces.bdd.world.InterfacesWorldManager
import io.cucumber.scala.{EN, ScalaDsl, Scenario}

/**
 * Cucumber hooks for interfaces component test lifecycle
 *
 * Manages:
 * - InterfacesWorld initialization per scenario (via InterfacesWorldManager)
 * - REST server lifecycle
 * - ActorSystem lifecycle
 * - Cleanup after each scenario
 *
 * Pattern: EXACT copy of ComponentTestHooks from test-probe-core
 *
 * Centralized Hooks:
 * - ONE Before/After hook for ALL scenarios
 * - NO Before/After hooks in step definition files
 * - InterfacesWorldManager handles thread-local world creation
 */
class ComponentTestHooks extends ScalaDsl with EN {

  /**
   * Before each scenario: Initialize InterfacesWorld
   * InterfacesWorldManager will create InterfacesWorld on first access
   */
  Before { (scenario: Scenario) =>
    // Log scenario start
    println(s"[InterfacesComponentTest] Starting scenario: ${scenario.getName}")
  }

  /**
   * After each scenario: Cleanup InterfacesWorld
   * Shuts down REST server, ActorSystem, and removes ThreadLocal
   */
  After { (scenario: Scenario) =>
    // Log scenario completion
    val status: String = if (scenario.isFailed) "FAILED" else "PASSED"
    println(s"[InterfacesComponentTest] Completed scenario: ${scenario.getName} - $status")

    // Shutdown InterfacesWorld for this thread (terminates REST server and ActorSystem)
    InterfacesWorldManager.shutdownWorld()
  }

  /**
   * After scenario with specific tag: Additional cleanup
   */
  After(order = 10, tagExpression = "@Critical") { (scenario: Scenario) =>
    if (scenario.isFailed) {
      println(s"[InterfacesComponentTest] CRITICAL TEST FAILED: ${scenario.getName}")
      // Could add additional logging or notifications here
    }
  }
}
