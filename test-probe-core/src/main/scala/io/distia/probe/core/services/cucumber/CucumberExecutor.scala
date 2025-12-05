package io.distia.probe
package core
package services
package cucumber

import io.cucumber.core.options.{RuntimeOptions, RuntimeOptionsBuilder}
import io.cucumber.core.plugin.JsonFormatter
import io.cucumber.core.runtime.{FeatureSupplier, Runtime}
import io.cucumber.plugin.Plugin
import io.cucumber.tagexpressions.TagExpressionParser
import models.{CucumberException, TestExecutionResult}

import io.distia.probe.common.models.BlockStorageDirective
import java.io.OutputStream
import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.util.function.Supplier
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success, Try}

/**
 * Synchronous Cucumber test executor with jimfs integration
 *
 * Executes Cucumber tests using Runtime.Builder API with custom jimfs resource loading.
 * This executor BLOCKS the calling thread until all tests complete.
 *
 * Architecture Changes (2025-11-01):
 * - Replaced Main.run() with Runtime.Builder for custom FeatureSupplier
 * - Uses JimfsFeatureSupplier to load .feature files from jimfs (bypasses PathScanner)
 * - Creates plugins directly with jimfs OutputStreams (bypasses PluginFactory)
 * - Enables 100% jimfs operation - no filesystem touching required
 *
 * Architecture Pattern:
 * This executor is designed to be called inside a Future on a dedicated blocking dispatcher
 * (cucumber-blocking-dispatcher) to avoid blocking actor threads.
 *
 * Execution Flow:
 * 1. Extract jimfs paths from BlockStorageDirective
 * 2. Register evidence path in CucumberContext (for custom plugins)
 * 3. Create JimfsFeatureSupplier from feature paths
 * 4. Create plugins with jimfs OutputStreams
 * 5. Build RuntimeOptions (glue, tags, dry-run)
 * 6. Build Runtime with custom supplier and plugins
 * 7. Execute runtime.run() - BLOCKS until all scenarios complete
 * 8. Close all OutputStreams
 * 9. Extract TestExecutionResult from listener
 * 10. Cleanup context and close resources
 *
 * Usage Pattern in CucumberExecutionActor:
 * {{{
 *   val cucumberFuture = Future {
 *     CucumberExecutor.execute(config, testId, directive)
 *   }(cucumberBlockingEc)
 *
 *   context.pipeToSelf(cucumberFuture) {
 *     case Success(result) => TestExecutionComplete(Right(result))
 *     case Failure(ex) => TestExecutionComplete(Left(ex))
 *   }
 * }}}
 *
 * Thread Safety:
 * This object is stateless and thread-safe.
 * Each invocation creates isolated Runtime, EventListener, and plugin instances.
 */
private[core] object CucumberExecutor:

  /**
   * Execute Cucumber tests synchronously with jimfs integration
   *
   * CRITICAL: This method BLOCKS until all tests complete.
   * Typical execution time: 30 seconds to 5 minutes depending on test count.
   *
   * The blocking behavior is intentional - caller must run this inside a Future
   * on the cucumber-blocking-dispatcher to isolate from actor thread pool.
   *
   * Cucumber Execution Phases:
   * 1. Feature Discovery: Load .feature files from jimfs via JimfsFeatureSupplier
   * 2. Step Definition Discovery: Load step definitions from glue packages (classpath)
   * 3. Tag Filtering: Apply tag expression to scenarios
   * 4. Scenario Execution: Run scenarios sequentially (or parallel if configured)
   * 5. Event Firing: TestExecutionEventListener accumulates results
   * 6. Evidence Writing: Plugins write JSON/HTML to jimfs
   *
   * @param config Validated CucumberConfiguration with feature paths and glue
   * @param testId UUID for test correlation and logging
   * @param directive BlockStorageDirective containing jimfs paths (jimfsLocation, evidenceDir)
   * @return TestExecutionResult with complete statistics from event listener
   * @throws CucumberException if configuration is invalid or execution fails
   */
  def execute(
    config: CucumberConfiguration,
    testId: UUID,
    directive: BlockStorageDirective): TestExecutionResult = Try {

    TestExecutionListenerRegistry.registerTestId(testId)
    CucumberContext.registerTestId(testId)
    CucumberContext.registerEvidencePath(Paths.get(URI.create(directive.evidenceDir)))

    val outputStreams: ArrayBuffer[OutputStream] = ArrayBuffer.empty


    config.validate()

    val jimfsPaths: Seq[Path] = config.featurePaths.map(uriStr => Paths.get(URI.create(uriStr)))
    val featureSupplier: FeatureSupplier = new JimfsFeatureSupplier(jimfsPaths)

    val listener: TestExecutionEventListener = new TestExecutionEventListener()

    val plugins: Array[Plugin] = createJimfsPlugins(
      evidencePath = Paths.get(URI.create(directive.evidenceDir)),
      listener = listener,
      outputStreams = outputStreams
    )

    val runtimeOptions = buildRuntimeOptions(config)

    val classLoader: ClassLoader = this.getClass.getClassLoader

    val runtime: Runtime = Runtime.builder()
      .withClassLoader(() => classLoader)
      .withAdditionalPlugins(plugins: _*)
      .withRuntimeOptions(runtimeOptions)
      .withFeatureSupplier(featureSupplier)
      .build()

    runtime.run()

    val finalListener: TestExecutionEventListener = TestExecutionListenerRegistry.getListener(testId)

    outputStreams.foreach { stream => Try(stream.close()) }
    TestExecutionListenerRegistry.unregister(testId)
    CucumberContext.clear()
    finalListener.getResult
  } match {
    case Success(result) => result
    case Failure(ex: Exception) => throw CucumberException(message = s"Cucumber execution failed for test $testId: ${ex.getMessage}", cause = Some(ex))
    case Failure(ex) => throw ex
  }

  def createJimfsPlugins(
    evidencePath: Path,
    listener: TestExecutionEventListener,
    outputStreams: ArrayBuffer[OutputStream]): Array[Plugin] =

    val plugins: ArrayBuffer[Plugin] = ArrayBuffer(listener)

    val jsonPath: Path = evidencePath.resolve("cucumber.json")
    Files.createDirectories(jsonPath.getParent)
    val jsonOut: OutputStream = Files.newOutputStream(jsonPath)
    outputStreams += jsonOut
    plugins += new JsonFormatter(jsonOut)

    plugins.toArray

  def buildRuntimeOptions(config: CucumberConfiguration): RuntimeOptions =
    val builder: RuntimeOptionsBuilder = new RuntimeOptionsBuilder()

    config.gluePackages.foreach { pkg =>
      val classpathUri: URI = URI.create(s"classpath:${pkg.replace('.', '/')}")
      builder.addGlue(classpathUri)
    }

    val tagExpression = TagExpressionParser.parse(config.tags)
    builder.addTagFilter(tagExpression)

    if config.dryRun then
      builder.setDryRun(): Unit

    builder.build()
