package io.distia.probe
package core
package services
package cucumber

import io.distia.probe.common.models.EventFilter
import io.distia.probe.core.fixtures.{BlockStorageDirectiveFixtures, TopicDirectiveFixtures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.URI

/**
 * Tests CucumberConfiguration case class and builder pattern.
 *
 * Verifies:
 * - Configuration construction with valid/default parameters
 * - URI conversion (feature paths, glue packages)
 * - Configuration validation
 * - Builder pattern for type-safe configuration construction
 * - Feature path and glue package handling
 *
 * Test Strategy: Unit tests (no external dependencies)
 */
class CucumberConfigurationSpec extends AnyWordSpec with Matchers with BlockStorageDirectiveFixtures with TopicDirectiveFixtures {

  private val frameworkGlue = "io.distia.probe.core.glue"

  "CucumberConfiguration case class" should {

    "construct with valid parameters" in {
      val config = CucumberConfiguration(
        featurePaths = Seq("classpath:features/test.feature"),
        gluePackages = Seq(frameworkGlue),
        tags = "not @Ignore",
        plugins = Seq.empty,
        dryRun = false
      )

      config.featurePaths should contain("classpath:features/test.feature")
      config.gluePackages should contain(frameworkGlue)
      config.tags shouldBe "not @Ignore"
      config.plugins shouldBe empty
      config.dryRun shouldBe false
    }

    "use default values for optional parameters" in {
      val config = CucumberConfiguration(
        featurePaths = Seq("classpath:features/test.feature"),
        gluePackages = Seq(frameworkGlue),
        tags = ""
      )

      config.tags shouldBe ""
      config.plugins shouldBe empty // Default value
      config.dryRun shouldBe false // Default value
    }

    "convert feature paths to URIs correctly" in {
      val config = CucumberConfiguration(
        featurePaths = Seq(
          "classpath:features/test.feature",
          "file:///jimfs/test-123/features"
        ),
        gluePackages = Seq(frameworkGlue),
        tags = ""
      )

      val uris = config.featurePathUris
      uris should have size 2
      uris(0) shouldBe URI.create("classpath:features/test.feature")
      uris(1) shouldBe URI.create("file:///jimfs/test-123/features")
    }

    "convert glue packages to classpath URIs correctly" in {
      val config = CucumberConfiguration(
        featurePaths = Seq("classpath:features"),
        gluePackages = Seq(
          "io.distia.probe.core.glue",
          "com.customer.tests.steps"
        ),
        tags = ""
      )

      val uris = config.gluePackageUris
      uris should have size 2
      uris(0) shouldBe URI.create("classpath:com/company/probe/core/glue")
      uris(1) shouldBe URI.create("classpath:com/customer/tests/steps")
    }

    "validate successfully with valid configuration" in {
      val config = CucumberConfiguration(
        featurePaths = Seq("classpath:features/test.feature"),
        gluePackages = Seq(frameworkGlue),
        tags = ""
      )

      noException should be thrownBy config.validate()
    }

    "throw IllegalArgumentException when feature paths are empty" in {
      val config = CucumberConfiguration(
        featurePaths = Seq.empty,
        gluePackages = Seq(frameworkGlue),
        tags = ""
      )

      val exception = intercept[IllegalArgumentException] {
        config.validate()
      }

      exception.getMessage should include("At least one feature path required")
    }

    "throw IllegalArgumentException when glue packages are empty" in {
      val config = CucumberConfiguration(
        featurePaths = Seq("classpath:features/test.feature"),
        gluePackages = Seq.empty,
        tags = ""
      )

      val exception = intercept[IllegalArgumentException] {
        config.validate()
      }

      exception.getMessage should include("At least one glue package required")
    }
  }

  "CucumberConfiguration.fromBlockStorageDirective" should {

    "create configuration from BlockStorageDirective with jimfs location" in {
      val directive = createBlockStorageDirective(
        jimfsLocation = "file:///jimfs/test-123/features",
        topicDirectives = List(
          createProducerDirective(
            topic = "test-events",
            clientPrincipal = "client-id",
            eventFilters = List(EventFilter("Event", "1.0"))
          )
        )
      )

      val config = CucumberConfiguration.fromBlockStorageDirective(directive, "io.distia.probe.core.glue")

      config.featurePaths should contain("file:///jimfs/test-123/features")
      config.gluePackages should contain(frameworkGlue)
    }

    "include framework glue package automatically" in {
      val directive = createBlockStorageDirective(
        jimfsLocation = "file:///jimfs/test-123/features"
      )

      val config = CucumberConfiguration.fromBlockStorageDirective(directive, "io.distia.probe.core.glue")

      config.gluePackages should contain(frameworkGlue)
      config.gluePackages.head shouldBe frameworkGlue // Framework glue is first
    }

    "merge user glue packages with framework glue" in {
      val directive = createBlockStorageDirective(
        jimfsLocation = "file:///jimfs/test-123/features",
        userGluePackages = List("com.customer.tests.steps", "com.customer.tests.helpers")
      )
      val config = CucumberConfiguration.fromBlockStorageDirective(directive, "io.distia.probe.core.glue")

      config.gluePackages should have size 3
      config.gluePackages(0) shouldBe frameworkGlue // Framework glue is first
      config.gluePackages(1) shouldBe "com.customer.tests.steps"
      config.gluePackages(2) shouldBe "com.customer.tests.helpers"
    }

    "return empty tags when directive has no tags" in {
      val directive = createBlockStorageDirective(
        jimfsLocation = "file:///jimfs/test-123/features"
      )

      val config = CucumberConfiguration.fromBlockStorageDirective(directive, "io.distia.probe.core.glue")

      config.tags shouldBe ""
    }
  }

  "CucumberConfigurationBuilder" should {

    "build configuration with fluent API" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .withGluePackages(Set("com.customer.tests.steps"))
        .withTags("@smoke")
        .build()

      config.featurePaths should contain("classpath:features/test.feature")
      config.gluePackages should contain("com.customer.tests.steps")
      config.tags shouldBe "@smoke"
    }

    "include framework glue automatically in builder" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .build()

      config.gluePackages should contain(frameworkGlue)
    }

    "merge user glue packages with framework glue in builder" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .withGluePackages(Set("com.customer.tests.steps"))
        .build()

      config.gluePackages should have size 2
      config.gluePackages should contain(frameworkGlue)
      config.gluePackages should contain("com.customer.tests.steps")
    }

    "not duplicate framework glue if already in user glue packages" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .withGluePackages(Set(frameworkGlue, "com.customer.tests.steps"))
        .build()

      config.gluePackages should have size 2
      config.gluePackages.count(_ == frameworkGlue) shouldBe 1 // Only one instance
    }

    "support adding single glue package" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .addGluePackage("com.customer.tests.steps")
        .addGluePackage("com.customer.tests.helpers")
        .build()

      config.gluePackages should have size 3
      config.gluePackages should contain(frameworkGlue)
      config.gluePackages should contain("com.customer.tests.steps")
      config.gluePackages should contain("com.customer.tests.helpers")
    }

    "not duplicate glue package when adding" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .addGluePackage("com.customer.tests.steps")
        .addGluePackage("com.customer.tests.steps") // Duplicate
        .build()

      config.gluePackages.count(_ == "com.customer.tests.steps") shouldBe 1
    }

    "support custom tag expressions" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .withTags("@smoke and @regression")
        .build()

      config.tags shouldBe "@smoke and @regression"
    }

    "support including @Ignore tag for framework testing" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:stubs/bdd-framework-minimal-valid-test-stub.feature"))
        .withTags("@Ignore")
        .build()

      config.tags shouldBe "@Ignore"
    }

    "support adding plugins" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .withPlugins(Seq("json:target/report.json", "html:target/report.html"))
        .build()

      config.plugins should have size 2
      config.plugins should contain("json:target/report.json")
      config.plugins should contain("html:target/report.html")
    }

    "support adding JSON report plugin" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .withJsonReport("target/cucumber.json")
        .build()

      config.plugins should contain("json:target/cucumber.json")
    }

    "support adding HTML report plugin" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .withHtmlReport("target/cucumber.html")
        .build()

      config.plugins should contain("html:target/cucumber.html")
    }

    "support adding multiple report plugins" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .withJsonReport("target/cucumber.json")
        .withHtmlReport("target/cucumber.html")
        .build()

      config.plugins should have size 2
      config.plugins should contain("json:target/cucumber.json")
      config.plugins should contain("html:target/cucumber.html")
    }

    "support dry-run mode" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .withDryRun(true)
        .build()

      config.dryRun shouldBe true
    }

    "use false as default for dry-run" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .build()

      config.dryRun shouldBe false
    }

    "validate configuration on build" in {
      val builder = CucumberConfiguration.builder()
      // Don't set feature paths - validation should fail

      val exception = intercept[IllegalArgumentException] {
        builder.build()
      }

      exception.getMessage should include("At least one feature path required")
    }

    "support method chaining" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .withGluePackages(Set("com.customer.tests.steps"))
        .withTags("@smoke")
        .withJsonReport("target/report.json")
        .withDryRun(false)
        .build()

      config.featurePaths should contain("classpath:features/test.feature")
      config.gluePackages should contain("com.customer.tests.steps")
      config.tags shouldBe "@smoke"
      config.plugins should contain("json:target/report.json")
      config.dryRun shouldBe false
    }

    "replace feature paths when called multiple times" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/old.feature"))
        .withFeaturePaths(Seq("classpath:features/new.feature"))
        .build()

      config.featurePaths should have size 1
      config.featurePaths should contain("classpath:features/new.feature")
      config.featurePaths should not contain "classpath:features/old.feature"
    }

    "replace plugins when called multiple times" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .withPlugins(Seq("json:old.json"))
        .withPlugins(Seq("html:new.html"))
        .build()

      config.plugins should have size 1
      config.plugins should contain("html:new.html")
      config.plugins should not contain "json:old.json"
    }

    "append plugins when using withJsonReport and withHtmlReport" in {
      val config = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test.feature"))
        .withPlugins(Seq("pretty"))
        .withJsonReport("target/cucumber.json")
        .withHtmlReport("target/cucumber.html")
        .build()

      config.plugins should have size 3
      config.plugins should contain("pretty")
      config.plugins should contain("json:target/cucumber.json")
      config.plugins should contain("html:target/cucumber.html")
    }
  }

  "CucumberConfiguration builder factory" should {

    "create new builder instance" in {
      val builder = CucumberConfiguration.builder()
      builder should not be null
    }

    "create independent builder instances" in {
      val builder1 = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test1.feature"))

      val builder2 = CucumberConfiguration.builder()
        .withFeaturePaths(Seq("classpath:features/test2.feature"))

      val config1 = builder1.build()
      val config2 = builder2.build()

      config1.featurePaths should contain("classpath:features/test1.feature")
      config2.featurePaths should contain("classpath:features/test2.feature")
    }
  }
}
