package io.distia.probe
package core
package services
package cucumber

import io.distia.probe.common.models.BlockStorageDirective
import io.distia.probe.core.fixtures.BlockStorageDirectiveFixtures
import models.{CucumberException, TestExecutionResult}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/**
 * Comprehensive test suite for CucumberExecutor with jimfs integration
 *
 * Tests cover:
 * - Configuration validation
 * - Real Cucumber execution with stub features
 * - Registry pattern (ThreadLocal)
 * - Error handling and exception wrapping
 * - Thread safety
 * - Resource cleanup
 */
class CucumberExecutorSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach with BlockStorageDirectiveFixtures {

  // Use random UUIDs to avoid collisions with other tests running in parallel
  // that also use the shared TestExecutionListenerRegistry
  private var testId1: UUID = _
  private var testId2: UUID = _

  override def beforeEach(): Unit = {
    testId1 = UUID.randomUUID()
    testId2 = UUID.randomUUID()
  }

  // Resolve stub feature file from test resources as file URI
  private val stubFeatureUri: String = getClass.getResource("/stubs/bdd-framework-minimal-valid-test-stub.feature").toURI.toString

  override def afterEach(): Unit = {
    // Clean up registry after each test to prevent leaks
    TestExecutionListenerRegistry.unregister(testId1)
    TestExecutionListenerRegistry.unregister(testId2)
    CucumberContext.clear()
  }

  "CucumberExecutor.execute" should {

    "execute real Cucumber test with stub feature file" in {
      val directive: BlockStorageDirective = createBlockStorageDirective(
        jimfsLocation = stubFeatureUri,
        evidenceDir = "file:///tmp/evidence-test1"
      )

      val config: CucumberConfiguration = CucumberConfiguration.fromBlockStorageDirective(
        directive,
        "io.distia.probe.core.glue"
      )

      val result: TestExecutionResult = CucumberExecutor.execute(config, testId1, directive)

      // Verify successful execution
      result.testId shouldBe testId1
      result.passed shouldBe true
      result.scenarioCount shouldBe 1
      result.scenariosPassed shouldBe 1
      result.scenariosFailed shouldBe 0
      result.stepCount shouldBe 3  // 2 Gherkin steps (hooks not counted)
      result.stepsPassed shouldBe 3
    }

    "validate configuration before execution" in {
      val directive: BlockStorageDirective = createBlockStorageDirective(
        jimfsLocation = "", // Invalid: empty path
        evidenceDir = "file:///tmp/evidence"
      )

      val invalidConfig: CucumberConfiguration = CucumberConfiguration.fromBlockStorageDirective(
        directive,
        "io.distia.probe.core.glue"
      )

      val exception = intercept[CucumberException] {
        CucumberExecutor.execute(invalidConfig, testId1, directive)
      }

      exception.getMessage should include("Cucumber execution failed")
      exception.getCause should not be null
      // Empty path causes URI parsing error before validation
      exception.getCause.getMessage should (include("Missing scheme") or include("At least one feature path required"))
    }

    "wrap exceptions in CucumberException when execution fails" in {
      // Use a malformed URI to trigger an exception during path conversion
      val directive: BlockStorageDirective = createBlockStorageDirective(
        jimfsLocation = "not-a-valid-uri", // Invalid URI format
        evidenceDir = "file:///tmp/evidence"
      )

      val config: CucumberConfiguration = CucumberConfiguration(
        featurePaths = Seq("not-a-valid-uri"),
        gluePackages = Seq("io.distia.probe.core.glue"),
        tags = ""
      )

      val exception = intercept[CucumberException] {
        CucumberExecutor.execute(config, testId1, directive)
      }

      exception.getMessage should include("Cucumber execution failed")
      exception.getMessage should include(testId1.toString)
      exception.getCause should not be null
    }

    "preserve original exception as cause" in {
      val directive: BlockStorageDirective = createBlockStorageDirective(
        jimfsLocation = "",
        evidenceDir = "file:///tmp/evidence"
      )

      val config: CucumberConfiguration = CucumberConfiguration(
        featurePaths = Seq.empty, // Invalid configuration
        gluePackages = Seq("io.distia.probe.core.glue"),
        tags = ""
      )

      val exception = intercept[CucumberException] {
        CucumberExecutor.execute(config, testId1, directive)
      }

      exception.getCause shouldBe a[IllegalArgumentException]
      exception.getCause.getMessage should include("At least one feature path required")
    }

    "cleanup registry in finally block even if execution fails" in {
      val directive: BlockStorageDirective = createBlockStorageDirective(
        jimfsLocation = "classpath:nonexistent/path.feature",
        evidenceDir = "file:///tmp/evidence"
      )

      val config: CucumberConfiguration = CucumberConfiguration(
        featurePaths = Seq("classpath:nonexistent/path.feature"),
        gluePackages = Seq("io.distia.probe.core.glue"),
        tags = ""
      )

      try {
        CucumberExecutor.execute(config, testId1, directive)
        fail("Should have thrown CucumberException")
      } catch {
        case _: CucumberException => // Expected - path doesn't exist
      }

      // Verify registry was cleaned up
      val exception = intercept[IllegalStateException] {
        TestExecutionListenerRegistry.getListener(testId1)
      }

      exception.getMessage should include("No listener registered")
    }

    "register testId in ThreadLocal before execution" in {
      val directive: BlockStorageDirective = createBlockStorageDirective(
        jimfsLocation = stubFeatureUri,
        evidenceDir = "file:///tmp/evidence-test2"
      )

      val config: CucumberConfiguration = CucumberConfiguration.fromBlockStorageDirective(
        directive,
        "io.distia.probe.core.glue"
      )

      // Execute successfully - registry pattern should work
      val result: TestExecutionResult = CucumberExecutor.execute(config, testId1, directive)

      // If we got here, registry pattern worked (no IllegalStateException thrown)
      result.testId shouldBe testId1
    }

    "cleanup both testId registry and evidence path after execution" in {
      val directive: BlockStorageDirective = createBlockStorageDirective(
        jimfsLocation = stubFeatureUri,
        evidenceDir = "file:///tmp/evidence-test3"
      )

      val config: CucumberConfiguration = CucumberConfiguration.fromBlockStorageDirective(
        directive,
        "io.distia.probe.core.glue"
      )

      CucumberExecutor.execute(config, testId1, directive)

      // Verify cleanup happened
      intercept[IllegalStateException] {
        TestExecutionListenerRegistry.getTestId
      }

      intercept[IllegalStateException] {
        TestExecutionListenerRegistry.getListener(testId1)
      }

      intercept[IllegalStateException] {
        CucumberContext.getTestId
      }
    }
  }

  "CucumberExecutor registry integration" should {

    "allow listener to read testId from registry during construction" in {
      // Manually test the registry pattern that CucumberExecutor uses
      TestExecutionListenerRegistry.registerTestId(testId1)

      // Listener constructor should read testId from ThreadLocal
      val listener = new TestExecutionEventListener()

      // Listener should have registered itself
      val retrievedListener = TestExecutionListenerRegistry.getListener(testId1)
      retrievedListener shouldBe listener

      // Cleanup
      TestExecutionListenerRegistry.unregister(testId1)
    }

    "support multiple sequential executions with different testIds" in {
      // Register and create listener for test1
      TestExecutionListenerRegistry.registerTestId(testId1)
      val listener1 = new TestExecutionEventListener()

      TestExecutionListenerRegistry.getListener(testId1) shouldBe listener1

      // Unregister test1
      TestExecutionListenerRegistry.unregister(testId1)

      // Register and create listener for test2
      TestExecutionListenerRegistry.registerTestId(testId2)
      val listener2 = new TestExecutionEventListener()

      TestExecutionListenerRegistry.getListener(testId2) shouldBe listener2

      // Listeners should be different instances
      listener1 should not be listener2

      // Cleanup
      TestExecutionListenerRegistry.unregister(testId2)
    }

    "cleanup both ThreadLocal and listener map on unregister" in {
      TestExecutionListenerRegistry.registerTestId(testId1)
      val listener = new TestExecutionEventListener()

      // Verify registered
      TestExecutionListenerRegistry.getListener(testId1) shouldBe listener

      // Unregister
      TestExecutionListenerRegistry.unregister(testId1)

      // Verify ThreadLocal cleared
      intercept[IllegalStateException] {
        TestExecutionListenerRegistry.getTestId
      }

      // Verify listener map cleared
      intercept[IllegalStateException] {
        TestExecutionListenerRegistry.getListener(testId1)
      }
    }
  }

  "CucumberExecutor thread safety" should {

    "support execution on dedicated blocking dispatcher thread" in {
      // CucumberExecutor is designed to be called from cucumber-blocking-dispatcher
      // This test verifies it can execute on any thread (not just actor threads)

      var executionThread: Thread = null
      var testPassed: Boolean = false

      val thread = new Thread(new Runnable {
        override def run(): Unit = {
          executionThread = Thread.currentThread()

          val directive: BlockStorageDirective = createBlockStorageDirective(
            jimfsLocation = stubFeatureUri,
            evidenceDir = "file:///tmp/evidence-thread-test"
          )

          val config: CucumberConfiguration = CucumberConfiguration.fromBlockStorageDirective(
            directive,
            "io.distia.probe.core.glue"
          )

          try {
            val result = CucumberExecutor.execute(config, testId1, directive)
            testPassed = result.passed
          } catch {
            case ex: Throwable =>
              fail(s"Execution on separate thread failed: ${ex.getMessage}")
          }
        }
      })

      thread.start()
      thread.join()

      executionThread should not be null
      executionThread.getName should not include "pekko" // Not an actor thread
      testPassed shouldBe true
    }

    "use ThreadLocal for thread-isolated testId storage" in {
      // Verify that different threads can have different testIds registered
      @volatile var thread1Success = false
      @volatile var thread2Success = false

      val thread1 = new Thread(new Runnable {
        override def run(): Unit = {
          try {
            TestExecutionListenerRegistry.registerTestId(testId1)
            val retrievedId = TestExecutionListenerRegistry.getTestId
            thread1Success = (retrievedId == testId1)
            TestExecutionListenerRegistry.unregister(testId1)
          } catch {
            case _: Exception => thread1Success = false
          }
        }
      })

      val thread2 = new Thread(new Runnable {
        override def run(): Unit = {
          try {
            TestExecutionListenerRegistry.registerTestId(testId2)
            val retrievedId = TestExecutionListenerRegistry.getTestId
            thread2Success = (retrievedId == testId2)
            TestExecutionListenerRegistry.unregister(testId2)
          } catch {
            case _: Exception => thread2Success = false
          }
        }
      })

      thread1.start()
      thread2.start()
      thread1.join()
      thread2.join()

      thread1Success shouldBe true
      thread2Success shouldBe true
    }
  }

  "CucumberExecutor.createJimfsPlugins" should {

    "create plugins array with TestExecutionEventListener and JsonFormatter" in {
      import java.nio.file.{Files, Paths}
      import scala.collection.mutable.ArrayBuffer

      // Setup
      val evidencePath = Paths.get(java.net.URI.create("file:///tmp/test-plugins"))
      Files.createDirectories(evidencePath)

      val outputStreams = ArrayBuffer.empty[java.io.OutputStream]
      TestExecutionListenerRegistry.registerTestId(testId1)
      val listener = new TestExecutionEventListener()

      // Execute
      val plugins = CucumberExecutor.createJimfsPlugins(evidencePath, listener, outputStreams)

      // Verify
      plugins.length shouldBe 2
      plugins(0) shouldBe listener
      plugins(1) shouldBe a[io.cucumber.core.plugin.JsonFormatter]
      outputStreams.size shouldBe 1

      // Cleanup
      outputStreams.foreach(_.close())
      TestExecutionListenerRegistry.unregister(testId1)
    }
  }

  "CucumberExecutor.buildRuntimeOptions" should {

    "build RuntimeOptions with glue packages and tags" in {
      val config = CucumberConfiguration(
        featurePaths = Seq("classpath:features/test.feature"),
        gluePackages = Seq("io.distia.probe.core.glue", "com.customer.steps"),
        tags = "@smoke and not @wip"
      )

      val runtimeOptions = CucumberExecutor.buildRuntimeOptions(config)

      runtimeOptions should not be null
      // RuntimeOptions doesn't expose glue/tags directly, so we verify it builds successfully
    }

    "handle dry-run flag" in {
      val config = CucumberConfiguration(
        featurePaths = Seq("classpath:features/test.feature"),
        gluePackages = Seq("io.distia.probe.core.glue"),
        tags = "",
        dryRun = true
      )

      val runtimeOptions = CucumberExecutor.buildRuntimeOptions(config)

      runtimeOptions should not be null
      runtimeOptions.isDryRun shouldBe true
    }
  }
}
