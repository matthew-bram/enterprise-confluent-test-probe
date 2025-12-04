package io.distia.probe
package services
package fixtures

import java.nio.file.{Files, Path}
import java.util.UUID

/**
 * Test fixtures for JIMFS filesystem testing
 * Provides helpers for creating test directory structures and files
 */
private[services] object JimfsTestFixtures {

  /**
   * Create a temporary test directory on real filesystem
   * Used for LocalBlockStorageService testing (copy from real filesystem to JIMFS)
   */
  def createRealTestDirectory(): Path = {
    val tempDir: Path = Files.createTempDirectory("test-probe-test")
    tempDir.toFile.deleteOnExit()
    tempDir
  }

  /**
   * Create a features directory with sample feature files
   * @param rootDir Root directory (can be real filesystem or JIMFS)
   */
  def createFeaturesDirectory(rootDir: Path): Path = {
    val featuresDir: Path = rootDir.resolve("features")
    Files.createDirectories(featuresDir)

    // Create a sample feature file
    val sampleFeature: Path = featuresDir.resolve("sample.feature")
    Files.writeString(sampleFeature, sampleFeatureContent)

    featuresDir
  }

  /**
   * Create an empty features directory (for testing empty directory detection)
   */
  def createEmptyFeaturesDirectory(rootDir: Path): Path = {
    val featuresDir: Path = rootDir.resolve("features")
    Files.createDirectories(featuresDir)
    featuresDir
  }

  /**
   * Create a topic-directive.yaml file with sample content
   * @param rootDir Root directory (can be real filesystem or JIMFS)
   * @param fileName Name of the file (default: "topic-directive.yaml")
   */
  def createTopicDirectiveFile(rootDir: Path, fileName: String = "topic-directive.yaml"): Path = {
    val directiveFile: Path = rootDir.resolve(fileName)
    Files.writeString(directiveFile, validTopicDirectiveYaml)
    directiveFile
  }

  /**
   * Create a complete valid test structure (features dir + topic directive file)
   */
  def createValidTestStructure(rootDir: Path, fileName: String = "topic-directive.yaml"): Unit = {
    createFeaturesDirectory(rootDir)
    createTopicDirectiveFile(rootDir, fileName)
  }

  /**
   * Create a nested directory structure for testing recursive copy
   */
  def createNestedDirectoryStructure(rootDir: Path): Unit = {
    // features/
    val featuresDir: Path = createFeaturesDirectory(rootDir)

    // features/subfolder/
    val subFolder: Path = featuresDir.resolve("subfolder")
    Files.createDirectories(subFolder)

    val nestedFeature: Path = subFolder.resolve("nested.feature")
    Files.writeString(nestedFeature, nestedFeatureContent)

    // Top-level topic directive
    createTopicDirectiveFile(rootDir)

    // Additional top-level file
    val readme: Path = rootDir.resolve("README.md")
    Files.writeString(readme, "# Test Structure\n")
  }

  /**
   * Sample Gherkin feature file content
   */
  val sampleFeatureContent: String =
    """Feature: Sample Test Feature
      |
      |  Scenario: Sample test scenario
      |    Given a test setup
      |    When an action is performed
      |    Then the result is verified
      |""".stripMargin

  /**
   * Nested feature file content
   */
  val nestedFeatureContent: String =
    """Feature: Nested Feature
      |
      |  Scenario: Nested scenario
      |    Given a nested test setup
      |    When a nested action occurs
      |    Then nested results are checked
      |""".stripMargin

  /**
   * Valid topic directive YAML content with multiple topics
   */
  val validTopicDirectiveYaml: String =
    """topics:
      |  - topic: "order-events"
      |    role: "producer"
      |    clientPrincipal: "service-account-1"
      |    eventFilters:
      |      - key: "eventType"
      |        value: "OrderCreated"
      |      - key: "region"
      |        value: "us-east-1"
      |    metadata:
      |      description: "Order creation events"
      |
      |  - topic: "payment-events"
      |    role: "consumer"
      |    clientPrincipal: "service-account-2"
      |    eventFilters:
      |      - key: "paymentStatus"
      |        value: "completed"
      |    metadata:
      |      priority: "high"
      |""".stripMargin

  /**
   * Valid topic directive YAML with single topic (minimal)
   */
  val minimalTopicDirectiveYaml: String =
    """topics:
      |  - topic: "test-topic"
      |    role: "producer"
      |    clientPrincipal: "test-service"
      |    eventFilters: []
      |    metadata: {}
      |""".stripMargin

  /**
   * Invalid topic directive YAML (malformed YAML syntax)
   */
  val malformedYaml: String =
    """topics:
      |  - topic: "test-topic"
      |    role: "producer
      |    clientPrincipal: "test-service"
      |""".stripMargin

  /**
   * Invalid topic directive YAML (missing required field: topic)
   */
  val missingTopicFieldYaml: String =
    """topics:
      |  - role: "producer"
      |    clientPrincipal: "test-service"
      |    eventFilters: []
      |    metadata: {}
      |""".stripMargin

  /**
   * Invalid topic directive YAML (missing required field: role)
   */
  val missingRoleFieldYaml: String =
    """topics:
      |  - topic: "test-topic"
      |    clientPrincipal: "test-service"
      |    eventFilters: []
      |    metadata: {}
      |""".stripMargin

  /**
   * Invalid topic directive YAML (missing required field: clientPrincipal)
   */
  val missingClientPrincipalYaml: String =
    """topics:
      |  - topic: "test-topic"
      |    role: "producer"
      |    eventFilters: []
      |    metadata: {}
      |""".stripMargin

  /**
   * Invalid topic directive YAML (wrong structure - not a list)
   */
  val wrongStructureYaml: String =
    """topic: "test-topic"
      |role: "producer"
      |clientPrincipal: "test-service"
      |""".stripMargin

  /**
   * Empty YAML
   */
  val emptyYaml: String = ""

  /**
   * Generate a random test ID
   */
  def randomTestId(): UUID = UUID.randomUUID()

  /**
   * Cleanup a real filesystem directory recursively
   */
  def cleanupRealDirectory(path: Path): Unit = {
    if Files.exists(path) then
      Files.walk(path)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }
}
