package io.distia.probe
package core
package services
package cucumber

import com.google.common.jimfs.{Configuration, Jimfs}
import io.cucumber.core.gherkin.Feature
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{FileSystem, Files, Path}
import scala.jdk.CollectionConverters.*

/**
 * Comprehensive test suite for JimfsFeatureSupplier
 *
 * Tests cover:
 * - Feature file discovery (single file, directory, recursive)
 * - Feature parsing from jimfs
 * - Empty directories
 * - Non-feature files filtering
 * - Mixed directory structures
 * - Invalid paths handling
 */
class JimfsFeatureSupplierSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  private var jimfs: FileSystem = _

  override def beforeEach(): Unit = {
    // Create jimfs in-memory filesystem
    jimfs = Jimfs.newFileSystem(Configuration.unix())
  }

  override def afterEach(): Unit = {
    // Clean up jimfs after each test
    jimfs.close()
  }

  "JimfsFeatureSupplier.discoverFeatureFiles" should {

    "discover single feature file" in {
      val featurePath = jimfs.getPath("/test.feature")
      Files.write(featurePath, "Feature: Test".getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(featurePath))

      val discovered = supplier.discoverFeatureFiles(featurePath)

      discovered should have size 1
      discovered.head shouldBe featurePath
    }

    "discover multiple feature files in directory" in {
      val dir = jimfs.getPath("/features")
      Files.createDirectories(dir)

      val feature1 = dir.resolve("test1.feature")
      val feature2 = dir.resolve("test2.feature")
      Files.write(feature1, "Feature: Test 1".getBytes)
      Files.write(feature2, "Feature: Test 2".getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(dir))

      val discovered = supplier.discoverFeatureFiles(dir)

      discovered should have size 2
      discovered should contain allOf (feature1, feature2)
    }

    "discover feature files recursively in nested directories" in {
      val rootDir = jimfs.getPath("/features")
      val subDir1 = rootDir.resolve("sub1")
      val subDir2 = rootDir.resolve("sub2")
      Files.createDirectories(subDir1)
      Files.createDirectories(subDir2)

      val feature1 = rootDir.resolve("root.feature")
      val feature2 = subDir1.resolve("sub1.feature")
      val feature3 = subDir2.resolve("sub2.feature")

      Files.write(feature1, "Feature: Root".getBytes)
      Files.write(feature2, "Feature: Sub1".getBytes)
      Files.write(feature3, "Feature: Sub2".getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(rootDir))

      val discovered = supplier.discoverFeatureFiles(rootDir)

      discovered should have size 3
      discovered should contain allOf (feature1, feature2, feature3)
    }

    "filter out non-feature files" in {
      val dir = jimfs.getPath("/mixed")
      Files.createDirectories(dir)

      val feature = dir.resolve("test.feature")
      val readme = dir.resolve("README.md")
      val txt = dir.resolve("notes.txt")

      Files.write(feature, "Feature: Test".getBytes)
      Files.write(readme, "# README".getBytes)
      Files.write(txt, "notes".getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(dir))

      val discovered = supplier.discoverFeatureFiles(dir)

      discovered should have size 1
      discovered.head shouldBe feature
    }

    "return empty list for empty directory" in {
      val emptyDir = jimfs.getPath("/empty")
      Files.createDirectories(emptyDir)

      val supplier = new JimfsFeatureSupplier(Seq(emptyDir))

      val discovered = supplier.discoverFeatureFiles(emptyDir)

      discovered shouldBe empty
    }

    "return empty list for non-feature file" in {
      val txtFile = jimfs.getPath("/test.txt")
      Files.write(txtFile, "not a feature".getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(txtFile))

      val discovered = supplier.discoverFeatureFiles(txtFile)

      discovered shouldBe empty
    }

    "return empty list for nonexistent path" in {
      val nonexistent = jimfs.getPath("/nonexistent")

      val supplier = new JimfsFeatureSupplier(Seq(nonexistent))

      val discovered = supplier.discoverFeatureFiles(nonexistent)

      discovered shouldBe empty
    }
  }

  "JimfsFeatureSupplier.parseFeatureFile" should {

    "parse valid feature file" in {
      val featureContent =
        """Feature: Test Feature
          |  Scenario: Test Scenario
          |    Given a test step
          |""".stripMargin

      val featurePath = jimfs.getPath("/test.feature")
      Files.write(featurePath, featureContent.getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(featurePath))
      val parser = new io.cucumber.core.feature.FeatureParser(() => java.util.UUID.randomUUID())

      val feature = supplier.parseFeatureFile(featurePath, parser)

      feature shouldBe defined
      feature.get.getPickles should not be empty
    }

    "return None for malformed feature file" in {
      val invalidContent = "This is not valid Gherkin syntax"

      val featurePath = jimfs.getPath("/invalid.feature")
      Files.write(featurePath, invalidContent.getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(featurePath))
      val parser = new io.cucumber.core.feature.FeatureParser(() => java.util.UUID.randomUUID())

      val feature = supplier.parseFeatureFile(featurePath, parser)

      feature shouldBe empty
    }

    "parse feature file with jimfs URI" in {
      val featureContent =
        """Feature: Jimfs Test
          |  Scenario: Test jimfs integration
          |    Given a step
          |""".stripMargin

      val featurePath = jimfs.getPath("/jimfs-test.feature")
      Files.write(featurePath, featureContent.getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(featurePath))
      val parser = new io.cucumber.core.feature.FeatureParser(() => java.util.UUID.randomUUID())

      val feature = supplier.parseFeatureFile(featurePath, parser)

      feature shouldBe defined
      feature.get.getUri.getScheme shouldBe "jimfs"
    }
  }

  "JimfsFeatureSupplier.get" should {

    "return empty list for no feature paths" in {
      val supplier = new JimfsFeatureSupplier(Seq.empty)

      val features = supplier.get()

      features shouldBe empty
    }

    "discover and parse single feature file" in {
      val featureContent =
        """Feature: Single Feature
          |  Scenario: Test
          |    Given a step
          |""".stripMargin

      val featurePath = jimfs.getPath("/single.feature")
      Files.write(featurePath, featureContent.getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(featurePath))

      val features = supplier.get()

      features should have size 1
      features.get(0).getPickles should not be empty
    }

    "discover and parse multiple features from directory" in {
      val dir = jimfs.getPath("/features")
      Files.createDirectories(dir)

      val feature1Content =
        """Feature: Feature 1
          |  Scenario: Test 1
          |    Given step 1
          |""".stripMargin

      val feature2Content =
        """Feature: Feature 2
          |  Scenario: Test 2
          |    Given step 2
          |""".stripMargin

      Files.write(dir.resolve("feature1.feature"), feature1Content.getBytes)
      Files.write(dir.resolve("feature2.feature"), feature2Content.getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(dir))

      val features = supplier.get()

      features should have size 2
    }

    "discover features from multiple paths" in {
      val dir1 = jimfs.getPath("/features1")
      val dir2 = jimfs.getPath("/features2")
      Files.createDirectories(dir1)
      Files.createDirectories(dir2)

      val featureContent =
        """Feature: Test
          |  Scenario: Test
          |    Given a step
          |""".stripMargin

      Files.write(dir1.resolve("test1.feature"), featureContent.getBytes)
      Files.write(dir2.resolve("test2.feature"), featureContent.getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(dir1, dir2))

      val features = supplier.get()

      features should have size 2
    }

    "handle mixed file and directory paths" in {
      val dir = jimfs.getPath("/features")
      Files.createDirectories(dir)

      val featureContent =
        """Feature: Test
          |  Scenario: Test
          |    Given a step
          |""".stripMargin

      val file1 = jimfs.getPath("/standalone.feature")
      val file2 = dir.resolve("dir.feature")

      Files.write(file1, featureContent.getBytes)
      Files.write(file2, featureContent.getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(file1, dir))

      val features = supplier.get()

      features should have size 2
    }

    "skip malformed features and continue parsing" in {
      val dir = jimfs.getPath("/features")
      Files.createDirectories(dir)

      val validContent =
        """Feature: Valid
          |  Scenario: Test
          |    Given a step
          |""".stripMargin

      val invalidContent = "Invalid Gherkin syntax"

      Files.write(dir.resolve("valid.feature"), validContent.getBytes)
      Files.write(dir.resolve("invalid.feature"), invalidContent.getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(dir))

      val features = supplier.get()

      // Should parse valid feature, skip invalid
      features should have size 1
    }

    "return Java List compatible with Cucumber API" in {
      val featureContent =
        """Feature: Test
          |  Scenario: Test
          |    Given a step
          |""".stripMargin

      val featurePath = jimfs.getPath("/test.feature")
      Files.write(featurePath, featureContent.getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(featurePath))

      val features: java.util.List[Feature] = supplier.get()

      // Verify it's a Java List
      features shouldBe a[java.util.List[_]]
      features.size() shouldBe 1
    }

    "handle deeply nested directory structure" in {
      val rootDir = jimfs.getPath("/root")
      val level1 = rootDir.resolve("level1")
      val level2 = level1.resolve("level2")
      val level3 = level2.resolve("level3")

      Files.createDirectories(level3)

      val featureContent =
        """Feature: Deep
          |  Scenario: Test
          |    Given a step
          |""".stripMargin

      Files.write(level3.resolve("deep.feature"), featureContent.getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(rootDir))

      val features = supplier.get()

      features should have size 1
    }

    "return empty list for directory with only non-feature files" in {
      val dir = jimfs.getPath("/docs")
      Files.createDirectories(dir)

      Files.write(dir.resolve("README.md"), "# Docs".getBytes)
      Files.write(dir.resolve("notes.txt"), "notes".getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(dir))

      val features = supplier.get()

      features shouldBe empty
    }
  }

  "JimfsFeatureSupplier integration" should {

    "work with real Cucumber feature format" in {
      val cucumberFeature =
        """@smoke
          |Feature: User Authentication
          |  As a user
          |  I want to log in
          |  So that I can access the system
          |
          |  Background:
          |    Given the system is running
          |
          |  @regression
          |  Scenario: Successful login
          |    Given I am on the login page
          |    When I enter valid credentials
          |    Then I should be logged in
          |
          |  Scenario Outline: Invalid login attempts
          |    Given I am on the login page
          |    When I enter username "<username>" and password "<password>"
          |    Then I should see error "<error>"
          |
          |    Examples:
          |      | username | password | error            |
          |      | invalid  | wrong    | Invalid credentials |
          |      | empty    |          | Missing password |
          |""".stripMargin

      val featurePath = jimfs.getPath("/auth.feature")
      Files.write(featurePath, cucumberFeature.getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(featurePath))

      val features = supplier.get()

      features should have size 1
      val feature = features.get(0)
      feature.getPickles should not be empty
      // Should have 3 pickles: 1 from Scenario + 2 from Scenario Outline examples
      feature.getPickles.size() shouldBe 3
    }

    "preserve feature tags and metadata" in {
      val taggedFeature =
        """@integration @slow
          |Feature: Tagged Feature
          |  @smoke @fast
          |  Scenario: Tagged Scenario
          |    Given a step
          |""".stripMargin

      val featurePath = jimfs.getPath("/tagged.feature")
      Files.write(featurePath, taggedFeature.getBytes)

      val supplier = new JimfsFeatureSupplier(Seq(featurePath))

      val features = supplier.get()

      features should have size 1
      features.get(0).getPickles should have size 1
    }
  }
}
