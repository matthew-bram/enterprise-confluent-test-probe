package io.distia.probe
package core

/**
 * Cucumber Glue Package - Step Definitions and Hooks
 *
 * This package contains the framework's built-in Cucumber step definitions and hooks.
 * User test projects can extend or override these definitions with their own glue packages.
 *
 * Package Structure:
 * - Framework Glue: io.distia.probe.core.glue (this package)
 * - User Glue: Specified in test metadata, merged automatically
 *
 * Glue Package Merging:
 * CucumberConfiguration.fromBlockStorageDirective automatically merges:
 * 1. Framework glue: "io.distia.probe.core.glue" (always included)
 * 2. User glue: Custom step definitions from user test projects
 *
 * Example Configuration:
 * {{{
 *   val config = CucumberConfiguration.fromBlockStorageDirective(
 *     directive,
 *     userGluePackages = Seq("com.customer.tests.steps")  // User's custom steps
 *   )
 *   // Result: gluePackages = ["io.distia.probe.core.glue", "com.customer.tests.steps"]
 * }}}
 *
 * Framework Step Definitions (Future Phase):
 * The framework will provide built-in step definitions for:
 * - Kafka event production: "When I produce event {string} to topic {string}"
 * - Kafka event consumption: "Then I should receive event {string} from topic {string}"
 * - Event matching: "And the event should match {string}"
 * - Schema validation: "And the event should validate against schema {string}"
 * - Timing: "And I wait {int} seconds"
 *
 * User Extension:
 * Users can add domain-specific step definitions in their own glue packages.
 * Framework steps remain available and can be mixed with user steps in feature files.
 *
 * Tag Filtering:
 * Default tag expression "not @Ignore" excludes framework stub files from production tests.
 * Stub file: test/resources/stubs/bdd-framework-minimal-valid-test-stub.feature
 *
 * Current Status (Scaffolding Phase):
 * This package is currently empty - step definitions will be added in future phase.
 * CucumberExecutor is functional and will load steps from this package once defined.
 *
 * Development Roadmap:
 * - Phase 6 (Current): Create package structure and documentation
 * - Phase 7: Implement ProbeScalaDsl integration steps
 * - Phase 8: Implement Kafka event production/consumption steps
 * - Phase 9: Implement schema validation steps
 * - Phase 10: Implement timing and synchronization steps
 *
 * @see CucumberConfiguration for glue package configuration
 * @see CucumberExecutor for execution with merged glue packages
 */
package object glue {

  /**
   * Glue package documentation marker
   *
   * This package object serves as documentation and namespace for framework step definitions.
   * Step definition classes will be added here in future phases.
   *
   * Users do NOT need to modify this package - they should create their own glue packages
   * and specify them in test metadata.
   */
  private[glue] val FrameworkGluePackage: String = "io.distia.probe.core.glue"

  /**
   * Version identifier for framework step definitions
   *
   * Increment this when adding/changing framework step definitions.
   * Future: Can be used for compatibility checking.
   */
  private[glue] val StepDefinitionsVersion: String = "0.1.0-SNAPSHOT"
}
