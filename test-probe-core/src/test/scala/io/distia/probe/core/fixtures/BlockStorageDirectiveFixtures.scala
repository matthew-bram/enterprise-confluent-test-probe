package io.distia.probe.core.fixtures

import io.distia.probe.common.models.{BlockStorageDirective => BlockStorageDirectiveModel, TopicDirective}

/**
 * Provides BlockStorageDirective test fixtures with factory methods.
 *
 * Mix this trait into test specs to access BlockStorageDirective factory:
 * {{{
 *   class MySpec extends AnyWordSpec with BlockStorageDirectiveFixtures {
 *     "test" in {
 *       val directive = createBlockStorageDirective()
 *     }
 *   }
 * }}}
 *
 * Design Principles:
 * - Factory pattern with sensible defaults
 * - Immutable instances
 * - Named parameters for flexibility
 * - Type-safe construction
 *
 * Thread Safety: Immutable, thread-safe
 */
trait BlockStorageDirectiveFixtures {

  /**
   * BlockStorageDirective fixture providing factory methods for block storage configurations.
   *
   * Access via: BlockStorageDirective.createBlockStorageDirective(), BlockStorageDirective.withTopics(), etc.
   */
  protected val BlockStorageDirective = BlockStorageDirectiveFixture

  /**
   * Create a BlockStorageDirective with default test values.
   *
   * Defaults:
   * - jimfsLocation: "/jimfs/test"
   * - evidenceDir: "/tmp/evidence"
   * - topicDirectives: List.empty
   * - bucket: "test-bucket"
   * - userGluePackages: List.empty
   * - tags: List.empty
   *
   * @param jimfsLocation Path to in-memory file system location
   * @param evidenceDir Directory for storing test evidence
   * @param topicDirectives List of Kafka topic directives
   * @param bucket Storage bucket name
   * @param userGluePackages Custom Cucumber glue packages
   * @param tags Cucumber tags for filtering scenarios
   * @return BlockStorageDirective with specified configuration
   */
  def createBlockStorageDirective(
    jimfsLocation: String = "/jimfs/test",
    evidenceDir: String = "/tmp/evidence",
    topicDirectives: List[TopicDirective] = List.empty,
    bucket: String = "test-bucket",
    userGluePackages: List[String] = List.empty,
    tags: List[String] = List.empty
  ): BlockStorageDirectiveModel = BlockStorageDirectiveModel(
    jimfsLocation = jimfsLocation,
    evidenceDir = evidenceDir,
    topicDirectives = topicDirectives,
    bucket = bucket,
    userGluePackages = userGluePackages,
    tags = tags
  )

  /**
   * Create BlockStorageDirective with topic directives.
   *
   * Convenience method for the common pattern of including topics.
   *
   * @param topics List of TopicDirective instances
   * @param jimfsLocation Path to in-memory file system location
   * @param evidenceDir Directory for storing test evidence
   * @param bucket Storage bucket name
   * @return BlockStorageDirective with topics configured
   */
  def createStorageDirectiveWithTopics(
    topics: List[TopicDirective],
    jimfsLocation: String = "/jimfs/test",
    evidenceDir: String = "/tmp/evidence",
    bucket: String = "test-bucket"
  ): BlockStorageDirectiveModel = createBlockStorageDirective(
    jimfsLocation = jimfsLocation,
    evidenceDir = evidenceDir,
    topicDirectives = topics,
    bucket = bucket
  )

  /**
   * Create BlockStorageDirective with Cucumber tags.
   *
   * Convenience method for testing tag filtering scenarios.
   *
   * @param tags List of Cucumber tags (e.g., "@smoke", "@regression")
   * @param jimfsLocation Path to in-memory file system location
   * @param evidenceDir Directory for storing test evidence
   * @param bucket Storage bucket name
   * @return BlockStorageDirective with tags configured
   */
  def createStorageDirectiveWithTags(
    tags: List[String],
    jimfsLocation: String = "/jimfs/test",
    evidenceDir: String = "/tmp/evidence",
    bucket: String = "test-bucket"
  ): BlockStorageDirectiveModel = createBlockStorageDirective(
    jimfsLocation = jimfsLocation,
    evidenceDir = evidenceDir,
    bucket = bucket,
    tags = tags
  )
}

/**
 * Companion object providing object-style access to BlockStorageDirective fixtures.
 *
 * Allows calling fixtures as: `BlockStorageDirectiveFixture.createBlockStorageDirective()`
 */
object BlockStorageDirectiveFixture extends BlockStorageDirectiveFixtures
