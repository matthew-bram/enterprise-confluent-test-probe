package io.distia.probe.interfaces

import io.cucumber.junit.{Cucumber, CucumberOptions}
import org.junit.runner.RunWith

/**
 * Cucumber test runner for component tests
 *
 * Runs all BDD scenarios in the features/component directory.
 *
 * Usage:
 *   mvn test -Pcomponent-only -pl test-probe-interfaces
 */
@RunWith(classOf[Cucumber])
@CucumberOptions(
  features = Array("classpath:features/component"),
  glue = Array("io.distia.probe.interfaces.bdd.steps"),
  plugin = Array("pretty", "html:target/cucumber-reports"),
  tags = "not @Pending"
)
class ComponentTestRunner
