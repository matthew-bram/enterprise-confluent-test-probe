package io.distia.probe
package core
package services
package cucumber

import io.distia.probe.common.models.BlockStorageDirective

import java.net.URI

/**
 * Type-safe configuration for Cucumber test execution
 *
 * Provides builder pattern for constructing Cucumber RuntimeOptions with:
 * - Feature file paths (from BlockStorageDirective.jimfsLocation)
 * - Glue packages (framework glue + user glue automatically merged)
 * - Tag filtering (default: "not @Ignore" to exclude stub files)
 * - Optional plugins (JSON, HTML reports)
 *
 * Glue Package Merging:
 * Framework glue package "io.distia.probe.core.glue" is ALWAYS included automatically.
 * User glue packages are merged on top, allowing extension without replacement.
 *
 * Tag Filtering:
 * Default tag expression "not @Ignore" excludes framework stub files from production tests.
 * Stub file: test/resources/stubs/bdd-framework-minimal-valid-test-stub.feature
 * Framework tests can explicitly include stub by setting tags = "@Ignore"
 *
 * Example Usage:
 * {{{
 *   // From BlockStorageDirective (production tests)
 *   val config = CucumberConfiguration.fromBlockStorageDirective(
 *     directive,
 *     userGluePackages = Seq("com.customer.tests.steps")
 *   )
 *   // Result: featurePaths = [directive.jimfsLocation]
 *   //         gluePackages = ["io.distia.probe.core.glue", "com.customer.tests.steps"]
 *   //         tags = "not @Ignore"
 *
 *   // Custom builder (framework testing)
 *   val config = CucumberConfiguration.builder()
 *     .withFeaturePaths(Seq("classpath:stubs/bdd-framework-minimal-valid-test-stub.feature"))
 *     .withTags("@Ignore")  // Explicitly include stub
 *     .build()
 * }}}
 *
 * @param featurePaths Paths to feature files or directories (jimfs or classpath)
 * @param gluePackages Step definition packages (framework + user)
 * @param tags Cucumber tag filter expression (default: "not @Ignore")
 * @param plugins Optional plugins for reports (JSON, HTML, JUnit)
 * @param dryRun If true, validates step definitions without execution
 */
case class CucumberConfiguration(
  featurePaths: Seq[String],
  gluePackages: Seq[String],
  tags: String,
  plugins: Seq[String] = Seq.empty,
  dryRun: Boolean = false) {

  /**
   * Get feature paths as URIs
   *
   * Converts feature path strings to URI objects for Cucumber Runtime.builder().
   *
   * Feature Paths:
   * - Jimfs paths: file:///jimfs/... (from BlockStorageDirective)
   * - Classpath: classpath:stubs/... (for framework testing)
   *
   * @return Sequence of feature path URIs
   */
  def featurePathUris: Seq[URI] = featurePaths.map(URI.create)

  /**
   * Get glue package URIs
   *
   * Converts glue package names to classpath URIs for Cucumber Runtime.builder().
   *
   * Example: "io.distia.probe.core.glue" → "classpath:com/company/probe/core/glue"
   *
   * @return Sequence of glue package URIs
   */
  def gluePackageUris: Seq[URI] = gluePackages.map { pkg =>
    val classpathPath = s"classpath:${pkg.replace('.', '/')}"
    URI.create(classpathPath)
  }

  /**
   * Validate configuration
   *
   * Ensures that required fields are populated.
   * Called automatically by builder.build() and before execution.
   *
   * @throws IllegalArgumentException if configuration is invalid
   */
  def validate(): Unit =
    require(featurePaths.nonEmpty,
      "At least one feature path required for Cucumber execution")
    require(gluePackages.nonEmpty,
      "At least one glue package required for step definitions")
}

object CucumberConfiguration {

  /**
   * Create configuration from BlockStorageDirective
   *
   * Production test execution flow:
   * 1. User uploads test.feature to S3
   * 2. BlockStorageActor downloads to jimfs
   * 3. CucumberExecutionActor receives BlockStorageDirective
   * 4. This method creates configuration from directive
   *
   * Framework glue package "io.distia.probe.core.glue" is automatically included.
   * User glue packages are merged on top for domain-specific steps.
   *
   * Tag filtering defaults to "not @Ignore" to exclude framework stub files.
   *
   * @param directive BlockStorageDirective containing jimfsLocation
   * @param userGluePackages Optional user glue packages for custom step definitions
   * @return CucumberConfiguration ready for execution
   */
  def fromBlockStorageDirective(
    directive: BlockStorageDirective,
    defaultCucumberGluePackage: String): CucumberConfiguration = {

    val featurePaths: Seq[String] = Seq(directive.jimfsLocation)
    val gluePackages: Seq[String] = defaultCucumberGluePackage +: directive.userGluePackages
    val plugins: Seq[String] = Seq.empty

    CucumberConfiguration(
      featurePaths = featurePaths,
      gluePackages = gluePackages,
      tags = directive.tags.mkString(" "),
      plugins = plugins
    )
  }

  /**
   * Create builder for fluent API
   *
   * Provides fluent builder pattern for custom configuration.
   * Useful for framework testing with explicit stub file inclusion.
   *
   * @return CucumberConfigurationBuilder
   */
  def builder(): CucumberConfigurationBuilder = CucumberConfigurationBuilder()
}

/**
 * Fluent builder for CucumberConfiguration
 *
 * Immutable case class builder using copy pattern for thread-safety.
 * Framework glue package is automatically included.
 *
 * Example:
 * {{{
 *   val config = CucumberConfiguration.builder()
 *     .withFeaturePaths(Seq("classpath:stubs/test.feature"))
 *     .withGluePackages(Seq("com.customer.tests.steps"))
 *     .withTags("@smoke")
 *     .withJsonReport("target/cucumber-report.json")
 *     .build()
 * }}}
 */
case class CucumberConfigurationBuilder(
  featurePaths: Seq[String] = Seq.empty,
  gluePackages: Set[String] = Set("io.distia.probe.core.glue"),
  tags: String = "",
  plugins: Seq[String] = Seq.empty,
  dryRun: Boolean = false
):
  def withFeaturePaths(paths: Seq[String]): CucumberConfigurationBuilder = copy(featurePaths = paths)

  def withGluePackages(packages: Set[String]): CucumberConfigurationBuilder = copy(gluePackages = gluePackages ++ packages)

  def addGluePackage(pkg: String): CucumberConfigurationBuilder = copy(gluePackages = gluePackages + pkg)

  def withTags(expression: String): CucumberConfigurationBuilder =
    copy(tags = if tags.isEmpty then expression else s"$tags and $expression")

  def withPlugins(pluginList: Seq[String]): CucumberConfigurationBuilder = copy(plugins = pluginList)

  def withJsonReport(outputPath: String): CucumberConfigurationBuilder = copy(plugins = plugins :+ s"json:$outputPath")

  def withHtmlReport(outputPath: String): CucumberConfigurationBuilder = copy(plugins = plugins :+ s"html:$outputPath")

  def withDryRun(enabled: Boolean): CucumberConfigurationBuilder = copy(dryRun = enabled)

  /**
   * Build CucumberConfiguration
   *
   * Validates configuration before returning.
   * Converts glue packages Set to Seq.
   *
   * @return Validated CucumberConfiguration
   * @throws IllegalArgumentException if configuration is invalid
   */
  def build(): CucumberConfiguration =
    val config = CucumberConfiguration(featurePaths, gluePackages.toSeq, tags, plugins, dryRun)
    config.validate()
    config

