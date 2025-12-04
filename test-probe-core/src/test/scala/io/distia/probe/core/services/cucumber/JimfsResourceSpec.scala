package io.distia.probe
package core
package services
package cucumber

import com.google.common.jimfs.{Configuration, Jimfs}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{BufferedReader, InputStreamReader}
import java.net.URI
import java.nio.file.{FileSystem, Files, Path}
import scala.jdk.CollectionConverters.*

/**
 * Comprehensive test suite for JimfsResource
 *
 * Tests cover:
 * - URI generation with jimfs scheme
 * - InputStream opening for feature file content
 * - Integration with jimfs filesystem
 * - Error handling for nonexistent files
 */
class JimfsResourceSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  private var jimfs: FileSystem = _

  override def beforeEach(): Unit = {
    // Create jimfs in-memory filesystem
    jimfs = Jimfs.newFileSystem(Configuration.unix())
  }

  override def afterEach(): Unit = {
    // Clean up jimfs after each test
    jimfs.close()
  }

  "JimfsResource.getUri" should {

    "return URI with jimfs scheme" in {
      val featurePath = jimfs.getPath("/test.feature")
      Files.write(featurePath, "Feature: Test".getBytes)

      val resource = new JimfsResource(featurePath)

      val uri = resource.getUri()

      uri.getScheme shouldBe "jimfs"
    }

    "return URI with absolute path" in {
      val featurePath = jimfs.getPath("/features/test.feature")
      Files.createDirectories(featurePath.getParent)
      Files.write(featurePath, "Feature: Test".getBytes)

      val resource = new JimfsResource(featurePath)

      val uri = resource.getUri()

      uri.toString should include("/features/test.feature")
    }

    "handle nested directory paths in URI" in {
      val featurePath = jimfs.getPath("/root/level1/level2/test.feature")
      Files.createDirectories(featurePath.getParent)
      Files.write(featurePath, "Feature: Test".getBytes)

      val resource = new JimfsResource(featurePath)

      val uri = resource.getUri()

      uri.toString should include("/root/level1/level2/test.feature")
      uri.getScheme shouldBe "jimfs"
    }

    "return consistent URI for same path" in {
      val featurePath = jimfs.getPath("/test.feature")
      Files.write(featurePath, "Feature: Test".getBytes)

      val resource = new JimfsResource(featurePath)

      val uri1 = resource.getUri()
      val uri2 = resource.getUri()

      uri1 shouldBe uri2
    }

    "handle paths with special characters" in {
      val featurePath = jimfs.getPath("/features/my-test_scenario.feature")
      Files.createDirectories(featurePath.getParent)
      Files.write(featurePath, "Feature: Test".getBytes)

      val resource = new JimfsResource(featurePath)

      val uri = resource.getUri()

      uri.toString should include("my-test_scenario.feature")
    }
  }

  "JimfsResource.getInputStream" should {

    "open InputStream for existing file" in {
      val content = "Feature: Test Feature"
      val featurePath = jimfs.getPath("/test.feature")
      Files.write(featurePath, content.getBytes)

      val resource = new JimfsResource(featurePath)

      val inputStream = resource.getInputStream()

      inputStream should not be null
      inputStream.close()
    }

    "read feature file content correctly" in {
      val content =
        """Feature: Test Feature
          |  Scenario: Test Scenario
          |    Given a test step
          |""".stripMargin

      val featurePath = jimfs.getPath("/test.feature")
      Files.write(featurePath, content.getBytes)

      val resource = new JimfsResource(featurePath)

      val inputStream = resource.getInputStream()
      val reader = new BufferedReader(new InputStreamReader(inputStream))
      val lines = reader.lines().toArray.mkString("\n")
      reader.close()

      lines should include("Feature: Test Feature")
      lines should include("Scenario: Test Scenario")
      lines should include("Given a test step")
    }

    "read empty file correctly" in {
      val featurePath = jimfs.getPath("/empty.feature")
      Files.write(featurePath, Array.emptyByteArray)

      val resource = new JimfsResource(featurePath)

      val inputStream = resource.getInputStream()
      val content = new String(inputStream.readAllBytes())
      inputStream.close()

      content shouldBe ""
    }

    "read large feature file correctly" in {
      val largeContent = (1 to 1000).map { i =>
        s"""
           |  Scenario: Test Scenario $i
           |    Given step $i
           |    When action $i
           |    Then result $i
           |""".stripMargin
      }.mkString("\n")

      val fullContent = s"Feature: Large Feature$largeContent"

      val featurePath = jimfs.getPath("/large.feature")
      Files.write(featurePath, fullContent.getBytes)

      val resource = new JimfsResource(featurePath)

      val inputStream = resource.getInputStream()
      val content = new String(inputStream.readAllBytes())
      inputStream.close()

      content should include("Feature: Large Feature")
      content should include("Scenario: Test Scenario 1")
      content should include("Scenario: Test Scenario 1000")
    }

    "read UTF-8 content correctly" in {
      val unicodeContent =
        """Feature: Unicode Test
          |  Scenario: Test with special characters
          |    Given a user "François"
          |    And currency "€100"
          |    Then result is "成功"
          |""".stripMargin

      val featurePath = jimfs.getPath("/unicode.feature")
      Files.write(featurePath, unicodeContent.getBytes("UTF-8"))

      val resource = new JimfsResource(featurePath)

      val inputStream = resource.getInputStream()
      val content = new String(inputStream.readAllBytes(), "UTF-8")
      inputStream.close()

      content should include("François")
      content should include("€100")
      content should include("成功")
    }

    "throw IOException for nonexistent file" in {
      val nonexistentPath = jimfs.getPath("/nonexistent.feature")

      val resource = new JimfsResource(nonexistentPath)

      intercept[java.io.IOException] {
        resource.getInputStream()
      }
    }

    "allow multiple sequential reads" in {
      val content = "Feature: Multiple Reads"
      val featurePath = jimfs.getPath("/test.feature")
      Files.write(featurePath, content.getBytes)

      val resource = new JimfsResource(featurePath)

      // First read
      val stream1 = resource.getInputStream()
      val content1 = new String(stream1.readAllBytes())
      stream1.close()

      // Second read
      val stream2 = resource.getInputStream()
      val content2 = new String(stream2.readAllBytes())
      stream2.close()

      content1 shouldBe content
      content2 shouldBe content
      content1 shouldBe content2
    }
  }

  "JimfsResource thread safety" should {

    "support concurrent URI access from multiple threads" in {
      val featurePath = jimfs.getPath("/concurrent.feature")
      Files.write(featurePath, "Feature: Concurrent".getBytes)

      val resource = new JimfsResource(featurePath)

      // Track failures from threads - exceptions don't propagate automatically
      @volatile var failures = List.empty[Throwable]
      val failureLock = new Object()

      val threads = (1 to 10).map { i =>
        new Thread(new Runnable {
          override def run(): Unit = {
            try {
              val uri = resource.getUri()
              // Capture assertion failures
              if (uri.getScheme != "jimfs") {
                failureLock.synchronized {
                  failures = new AssertionError(s"Thread $i: scheme was ${uri.getScheme}, expected jimfs") :: failures
                }
              }
            } catch {
              case e: Throwable =>
                failureLock.synchronized {
                  failures = e :: failures
                }
            }
          }
        })
      }

      threads.foreach(_.start())
      threads.foreach(_.join())

      // Now failures are captured and will fail the test if any occurred
      failures shouldBe empty
    }

    "support concurrent InputStream opening from multiple threads" in {
      val content = "Feature: Concurrent Reads"
      val featurePath = jimfs.getPath("/concurrent.feature")
      Files.write(featurePath, content.getBytes)

      val resource = new JimfsResource(featurePath)

      @volatile var allSucceeded = true

      val threads = (1 to 5).map { _ =>
        new Thread(new Runnable {
          override def run(): Unit = {
            try {
              val inputStream = resource.getInputStream()
              val readContent = new String(inputStream.readAllBytes())
              inputStream.close()

              if (readContent != content) {
                allSucceeded = false
              }
            } catch {
              case _: Exception => allSucceeded = false
            }
          }
        })
      }

      threads.foreach(_.start())
      threads.foreach(_.join())

      allSucceeded shouldBe true
    }
  }

  "JimfsResource integration" should {

    "work with Cucumber FeatureParser" in {
      val featureContent =
        """Feature: Integration Test
          |  Scenario: Parse via JimfsResource
          |    Given a jimfs resource
          |    When Cucumber parses it
          |    Then it should succeed
          |""".stripMargin

      val featurePath = jimfs.getPath("/integration.feature")
      Files.write(featurePath, featureContent.getBytes)

      val resource = new JimfsResource(featurePath)
      val parser = new io.cucumber.core.feature.FeatureParser(() => java.util.UUID.randomUUID())

      val feature = parser.parseResource(resource)

      feature.isPresent shouldBe true
      feature.get().getPickles should not be empty
    }

    "provide correct URI for Cucumber error messages" in {
      val featurePath = jimfs.getPath("/error-test.feature")
      Files.write(featurePath, "Feature: Error Test".getBytes)

      val resource = new JimfsResource(featurePath)

      val uri = resource.getUri()

      // Cucumber uses this URI in error messages
      uri.toString should include("error-test.feature")
      uri.toString should startWith("jimfs:")
    }

    "handle real-world Cucumber feature format" in {
      val realWorldFeature =
        """@integration @kafka
          |Feature: Kafka Event Processing
          |  As a system
          |  I want to process Kafka events
          |  So that I can maintain data consistency
          |
          |  Background:
          |    Given Kafka is running
          |    And Schema Registry is available
          |
          |  @smoke
          |  Scenario: Process valid event
          |    Given I have a valid AVRO event
          |    When I produce the event to topic "test-events"
          |    Then the event should be consumed successfully
          |    And the event data should match the original
          |
          |  @negative
          |  Scenario: Handle invalid schema
          |    Given I have an event with invalid schema
          |    When I attempt to produce the event
          |    Then I should receive a schema validation error
          |""".stripMargin

      val featurePath = jimfs.getPath("/kafka-test.feature")
      Files.write(featurePath, realWorldFeature.getBytes)

      val resource = new JimfsResource(featurePath)

      // Verify content can be read
      val inputStream = resource.getInputStream()
      val content = new String(inputStream.readAllBytes())
      inputStream.close()

      content should include("@integration @kafka")
      content should include("Feature: Kafka Event Processing")
      content should include("Background:")
      content should include("@smoke")
      content should include("Scenario: Process valid event")
    }
  }

  "JimfsResource immutability" should {

    "be immutable after construction" in {
      val featurePath = jimfs.getPath("/immutable.feature")
      Files.write(featurePath, "Original content".getBytes)

      val resource = new JimfsResource(featurePath)

      val uri1 = resource.getUri()

      // Modify file content
      Files.write(featurePath, "Modified content".getBytes)

      val uri2 = resource.getUri()

      // URI should remain the same (path didn't change)
      uri1 shouldBe uri2
    }

    "reflect file modifications in InputStream" in {
      val featurePath = jimfs.getPath("/mutable-content.feature")
      Files.write(featurePath, "Original".getBytes)

      val resource = new JimfsResource(featurePath)

      // Read original content
      val stream1 = resource.getInputStream()
      val content1 = new String(stream1.readAllBytes())
      stream1.close()

      // Modify file
      Files.write(featurePath, "Modified".getBytes)

      // Read modified content
      val stream2 = resource.getInputStream()
      val content2 = new String(stream2.readAllBytes())
      stream2.close()

      content1 shouldBe "Original"
      content2 shouldBe "Modified"
    }
  }
}
