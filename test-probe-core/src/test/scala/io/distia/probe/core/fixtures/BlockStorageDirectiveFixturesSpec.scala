package io.distia.probe.core.fixtures

import io.distia.probe.common.models.TopicDirective

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests BlockStorageDirectiveFixtures for factory methods and configuration builders.
 *
 * Verifies:
 * - BlockStorageDirective factory (createBlockStorageDirective)
 * - Directive with topics factory (createStorageDirectiveWithTopics)
 * - Directive with tags factory (createStorageDirectiveWithTags)
 * - Default values for each factory
 * - Custom parameter overrides (jimfsLocation, evidenceDir, bucket, etc.)
 * - Topic directive integration
 * - Cucumber tag integration
 *
 * Test Strategy: Unit tests (no external dependencies)
 *
 * Dogfooding: Tests BlockStorageDirectiveFixtures using BlockStorageDirectiveFixtures itself
 */
class BlockStorageDirectiveFixturesSpec extends AnyWordSpec
  with Matchers
  with BlockStorageDirectiveFixtures
  with TopicDirectiveFixtures {

  "BlockStorageDirectiveFixtures" should {

    "provide createBlockStorageDirective factory method" in {
      val directive = createBlockStorageDirective()
      directive should not be null
    }

    "provide createStorageDirectiveWithTopics factory method" in {
      val topics = List(createProducerDirective())
      val directive = createStorageDirectiveWithTopics(topics)
      directive should not be null
    }

    "provide createStorageDirectiveWithTags factory method" in {
      val tags = List("@smoke", "@regression")
      val directive = createStorageDirectiveWithTags(tags)
      directive should not be null
    }
  }

  "createBlockStorageDirective" should {

    "create directive with default values" in {
      val directive = createBlockStorageDirective()

      directive.jimfsLocation shouldBe "/jimfs/test"
      directive.evidenceDir shouldBe "/tmp/evidence"
      directive.topicDirectives shouldBe empty
      directive.bucket shouldBe "test-bucket"
      directive.userGluePackages shouldBe empty
      directive.tags shouldBe empty
    }

    "override jimfsLocation" in {
      val directive = createBlockStorageDirective(jimfsLocation = "/custom/jimfs")

      directive.jimfsLocation shouldBe "/custom/jimfs"
    }

    "override evidenceDir" in {
      val directive = createBlockStorageDirective(evidenceDir = "/custom/evidence")

      directive.evidenceDir shouldBe "/custom/evidence"
    }

    "override topicDirectives" in {
      val topics = List(
        createProducerDirective(topic = "test-events"),
        createConsumerDirective(topic = "order-events")
      )
      val directive = createBlockStorageDirective(topicDirectives = topics)

      directive.topicDirectives should have size 2
      directive.topicDirectives should contain theSameElementsAs topics
    }

    "override bucket" in {
      val directive = createBlockStorageDirective(bucket = "production-bucket")

      directive.bucket shouldBe "production-bucket"
    }

    "override userGluePackages" in {
      val gluePackages = List("com.custom.glue", "com.other.glue")
      val directive = createBlockStorageDirective(userGluePackages = gluePackages)

      directive.userGluePackages should have size 2
      directive.userGluePackages should contain theSameElementsAs gluePackages
    }

    "override tags" in {
      val tags = List("@smoke", "@unit", "@fast")
      val directive = createBlockStorageDirective(tags = tags)

      directive.tags should have size 3
      directive.tags should contain theSameElementsAs tags
    }

    "support all parameter overrides together" in {
      val topics = List(createProducerDirective())
      val gluePackages = List("com.custom.steps")
      val tags = List("@integration")

      val directive = createBlockStorageDirective(
        jimfsLocation = "/integration/jimfs",
        evidenceDir = "/integration/evidence",
        topicDirectives = topics,
        bucket = "integration-bucket",
        userGluePackages = gluePackages,
        tags = tags
      )

      directive.jimfsLocation shouldBe "/integration/jimfs"
      directive.evidenceDir shouldBe "/integration/evidence"
      directive.topicDirectives should contain theSameElementsAs topics
      directive.bucket shouldBe "integration-bucket"
      directive.userGluePackages should contain theSameElementsAs gluePackages
      directive.tags should contain theSameElementsAs tags
    }
  }

  "createStorageDirectiveWithTopics" should {

    "create directive with topics" in {
      val topics = List(
        createProducerDirective(topic = "user-events"),
        createConsumerDirective(topic = "payment-events")
      )
      val directive = createStorageDirectiveWithTopics(topics)

      directive.topicDirectives should have size 2
      directive.topicDirectives should contain theSameElementsAs topics
    }

    "use default values for other parameters" in {
      val topics = List(createProducerDirective())
      val directive = createStorageDirectiveWithTopics(topics)

      directive.jimfsLocation shouldBe "/jimfs/test"
      directive.evidenceDir shouldBe "/tmp/evidence"
      directive.bucket shouldBe "test-bucket"
      directive.userGluePackages shouldBe empty
      directive.tags shouldBe empty
    }

    "override jimfsLocation" in {
      val topics = List(createProducerDirective())
      val directive = createStorageDirectiveWithTopics(
        topics = topics,
        jimfsLocation = "/custom/jimfs"
      )

      directive.jimfsLocation shouldBe "/custom/jimfs"
      directive.topicDirectives should contain theSameElementsAs topics
    }

    "override evidenceDir" in {
      val topics = List(createProducerDirective())
      val directive = createStorageDirectiveWithTopics(
        topics = topics,
        evidenceDir = "/custom/evidence"
      )

      directive.evidenceDir shouldBe "/custom/evidence"
      directive.topicDirectives should contain theSameElementsAs topics
    }

    "override bucket" in {
      val topics = List(createProducerDirective())
      val directive = createStorageDirectiveWithTopics(
        topics = topics,
        bucket = "custom-bucket"
      )

      directive.bucket shouldBe "custom-bucket"
      directive.topicDirectives should contain theSameElementsAs topics
    }

    "support all parameter overrides" in {
      val topics = List(createProducerDirective())
      val directive = createStorageDirectiveWithTopics(
        topics = topics,
        jimfsLocation = "/complete/jimfs",
        evidenceDir = "/complete/evidence",
        bucket = "complete-bucket"
      )

      directive.jimfsLocation shouldBe "/complete/jimfs"
      directive.evidenceDir shouldBe "/complete/evidence"
      directive.bucket shouldBe "complete-bucket"
      directive.topicDirectives should contain theSameElementsAs topics
    }
  }

  "createStorageDirectiveWithTags" should {

    "create directive with tags" in {
      val tags = List("@smoke", "@regression", "@critical")
      val directive = createStorageDirectiveWithTags(tags)

      directive.tags should have size 3
      directive.tags should contain theSameElementsAs tags
    }

    "use default values for other parameters" in {
      val tags = List("@smoke")
      val directive = createStorageDirectiveWithTags(tags)

      directive.jimfsLocation shouldBe "/jimfs/test"
      directive.evidenceDir shouldBe "/tmp/evidence"
      directive.bucket shouldBe "test-bucket"
      directive.topicDirectives shouldBe empty
      directive.userGluePackages shouldBe empty
    }

    "override jimfsLocation" in {
      val tags = List("@smoke")
      val directive = createStorageDirectiveWithTags(
        tags = tags,
        jimfsLocation = "/tagged/jimfs"
      )

      directive.jimfsLocation shouldBe "/tagged/jimfs"
      directive.tags should contain theSameElementsAs tags
    }

    "override evidenceDir" in {
      val tags = List("@regression")
      val directive = createStorageDirectiveWithTags(
        tags = tags,
        evidenceDir = "/tagged/evidence"
      )

      directive.evidenceDir shouldBe "/tagged/evidence"
      directive.tags should contain theSameElementsAs tags
    }

    "override bucket" in {
      val tags = List("@integration")
      val directive = createStorageDirectiveWithTags(
        tags = tags,
        bucket = "tagged-bucket"
      )

      directive.bucket shouldBe "tagged-bucket"
      directive.tags should contain theSameElementsAs tags
    }

    "support all parameter overrides" in {
      val tags = List("@smoke", "@fast")
      val directive = createStorageDirectiveWithTags(
        tags = tags,
        jimfsLocation = "/fast/jimfs",
        evidenceDir = "/fast/evidence",
        bucket = "fast-bucket"
      )

      directive.jimfsLocation shouldBe "/fast/jimfs"
      directive.evidenceDir shouldBe "/fast/evidence"
      directive.bucket shouldBe "fast-bucket"
      directive.tags should contain theSameElementsAs tags
    }

    "handle single tag" in {
      val directive = createStorageDirectiveWithTags(List("@smoke"))

      directive.tags should have size 1
      directive.tags should contain ("@smoke")
    }

    "handle multiple tags" in {
      val tags = List("@smoke", "@unit", "@fast", "@critical")
      val directive = createStorageDirectiveWithTags(tags)

      directive.tags should have size 4
      directive.tags should contain allOf ("@smoke", "@unit", "@fast", "@critical")
    }
  }

  "BlockStorageDirectiveFixture object" should {

    "provide standalone createBlockStorageDirective method" in {
      val directive = BlockStorageDirectiveFixture.createBlockStorageDirective()
      directive.bucket shouldBe "test-bucket"
    }

    "provide standalone createStorageDirectiveWithTopics method" in {
      val topics = List(createProducerDirective())
      val directive = BlockStorageDirectiveFixture.createStorageDirectiveWithTopics(topics)
      directive.topicDirectives should contain theSameElementsAs topics
    }

    "provide standalone createStorageDirectiveWithTags method" in {
      val tags = List("@smoke")
      val directive = BlockStorageDirectiveFixture.createStorageDirectiveWithTags(tags)
      directive.tags should contain theSameElementsAs tags
    }
  }

  "Topic directive integration" should {

    "support empty topic list" in {
      val directive = createStorageDirectiveWithTopics(List.empty)
      directive.topicDirectives shouldBe empty
    }

    "support single topic" in {
      val topic = createProducerDirective(topic = "single-topic")
      val directive = createStorageDirectiveWithTopics(List(topic))

      directive.topicDirectives should have size 1
      directive.topicDirectives.head.topic shouldBe "single-topic"
    }

    "support multiple producer topics" in {
      val topics = List(
        createProducerDirective(topic = "topic-1"),
        createProducerDirective(topic = "topic-2"),
        createProducerDirective(topic = "topic-3")
      )
      val directive = createStorageDirectiveWithTopics(topics)

      directive.topicDirectives should have size 3
      directive.topicDirectives.map(_.role) should contain only "producer"
    }

    "support multiple consumer topics" in {
      val topics = List(
        createConsumerDirective(topic = "consumer-1"),
        createConsumerDirective(topic = "consumer-2")
      )
      val directive = createStorageDirectiveWithTopics(topics)

      directive.topicDirectives should have size 2
      directive.topicDirectives.map(_.role) should contain only "consumer"
    }

    "support mixed producer and consumer topics" in {
      val topics = List(
        createProducerDirective(topic = "produce-topic"),
        createConsumerDirective(topic = "consume-topic")
      )
      val directive = createStorageDirectiveWithTopics(topics)

      directive.topicDirectives should have size 2
      directive.topicDirectives.map(_.role) should contain allOf ("producer", "consumer")
    }
  }

  "Cucumber tag integration" should {

    "support empty tag list" in {
      val directive = createStorageDirectiveWithTags(List.empty)
      directive.tags shouldBe empty
    }

    "support tags with @ prefix" in {
      val tags = List("@smoke", "@regression")
      val directive = createStorageDirectiveWithTags(tags)

      directive.tags should contain allOf ("@smoke", "@regression")
    }

    "preserve tag order" in {
      val tags = List("@first", "@second", "@third")
      val directive = createStorageDirectiveWithTags(tags)

      directive.tags shouldBe tags
    }
  }
}
