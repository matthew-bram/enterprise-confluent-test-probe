package io.distia.probe
package core
package integration
package hooks

import io.distia.probe.core.integration.world.{IntegrationWorld, IntegrationWorldManager}
import io.cucumber.scala.{EN, ScalaDsl}

/**
 * Cucumber hooks for integration test lifecycle management.
 *
 * Provides global cleanup for IntegrationWorld when tests complete.
 * Initialization is lazy - handled by IntegrationTestSteps when first step runs.
 *
 * Note: Hooks must be in object (not class) for static lifecycle.
 */
object IntegrationTestHooks extends ScalaDsl with EN:

  /**
   * Global cleanup after all test scenarios complete.
   *
   * Defensively cleans up IntegrationWorld if it was initialized during test run.
   * This handles both integration test runs (world exists) and component test runs (world doesn't exist).
   */
  AfterAll {
    if IntegrationWorldManager.hasWorld then
      println("[IntegrationTestHooks] AfterAll: Cleaning up IntegrationWorld...")
      val world = IntegrationWorldManager.getWorld()
      world.cleanup()
      IntegrationWorldManager.clearWorld()
      println("[IntegrationTestHooks] IntegrationWorld cleaned up successfully")
    else
      println("[IntegrationTestHooks] AfterAll: No IntegrationWorld to clean up (component tests only)")
  }
