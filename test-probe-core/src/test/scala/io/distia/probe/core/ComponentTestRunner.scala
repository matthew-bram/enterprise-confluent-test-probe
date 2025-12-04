package io.distia.probe.core

import io.cucumber.junit.{Cucumber, CucumberOptions}
import org.junit.runner.RunWith

@RunWith(classOf[Cucumber])
@CucumberOptions(
  features = Array("classpath:features/component"),
  glue = Array("io.distia.probe.core.glue"),
  plugin = Array(
    "pretty",
    "html:target/cucumber-reports/component-test-report.html",
    "json:target/cucumber-reports/component-test-report.json"
  ),
  tags = "@ComponentTest and not @Ignore"
)
class ComponentTestRunner