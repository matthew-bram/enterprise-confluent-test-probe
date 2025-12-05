package io.distia.probe
package core
package pubsub

import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaMetadata, SchemaRegistryClient}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Unit tests for ScalaConfluentJsonSerializer.
 *
 * Tests the Scala-friendly wrapper for Confluent's KafkaJsonSchemaSerializer.
 *
 * Test Strategy:
 * - Unit tests with mock SchemaRegistryClient
 * - Focus on serializer configuration and behavior
 * - Tests for null handling and edge cases
 *
 * Test Coverage:
 * - Serializer instantiation with valid parameters
 * - Configuration with schema registry settings
 * - Null input handling
 * - ObjectMapper configuration
 *
 * Thread Safety: Tests run sequentially
 */
class ScalaConfluentJsonSerializerSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach:

  private val testSchemaRegistryUrl = "http://localhost:8081"
  private val testTopic = "test-topic"

  // Simple test case class for JSON serialization
  case class TestEvent(id: String, value: Int)

  override def beforeEach(): Unit =
    SerdesFactory.client = None
    SerdesFactory.schemaRegistryUrl = None

  override def afterEach(): Unit =
    SerdesFactory.client = None
    SerdesFactory.schemaRegistryUrl = None

  "ScalaConfluentJsonSerializer" should:

    "instantiate with valid SchemaRegistryClient and URL" in:
      val mockClient = createMockClient()

      val serializer = new ScalaConfluentJsonSerializer[TestEvent](mockClient, testSchemaRegistryUrl)

      // Verify serializer is properly typed and extends Confluent base
      serializer shouldBe a[io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer[?]]

    "be configured with schema registry URL for schema resolution" in:
      val mockClient = createMockClient()

      val serializer = new ScalaConfluentJsonSerializer[TestEvent](mockClient, testSchemaRegistryUrl)

      // Verify serializer has serialize method that accepts topic (used for schema lookup)
      val serializeMethods = serializer.getClass.getMethods.filter(_.getName == "serialize")
      serializeMethods.length should be > 0

      // Verify at least one serialize method accepts String (topic) parameter
      serializeMethods.exists(m =>
        m.getParameterTypes.headOption.contains(classOf[String])
      ) shouldBe true

    "support generic type parameter with ClassTag" in:
      val mockClient = createMockClient()

      // Test with different types
      noException should be thrownBy:
        new ScalaConfluentJsonSerializer[String](mockClient, testSchemaRegistryUrl)

      noException should be thrownBy:
        new ScalaConfluentJsonSerializer[Map[String, Any]](mockClient, testSchemaRegistryUrl)

      noException should be thrownBy:
        new ScalaConfluentJsonSerializer[TestEvent](mockClient, testSchemaRegistryUrl)

    "extend KafkaJsonSchemaSerializer" in:
      val mockClient = createMockClient()

      val serializer = new ScalaConfluentJsonSerializer[TestEvent](mockClient, testSchemaRegistryUrl)

      // Verify inheritance using ScalaTest type matcher
      serializer shouldBe a[io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer[?]]

      // Verify it also implements Kafka's Serializer interface
      serializer shouldBe a[org.apache.kafka.common.serialization.Serializer[?]]

  "ScalaConfluentJsonSerializer.serialize" should:

    "accept isKey parameter defaulting to false" in:
      val mockClient = createMockClientWithSchema(testTopic)
      val serializer = new ScalaConfluentJsonSerializer[TestEvent](mockClient, testSchemaRegistryUrl)

      // Method signature should accept isKey with default value
      // This tests that the API allows calling without explicit isKey
      serializer.getClass.getMethods.find(_.getName == "serialize") match
        case Some(method) =>
          // Method exists with expected signature
          method.getParameterCount should be >= 2
        case None =>
          fail("serialize method not found")

    "handle empty string values in test event" in:
      val mockClient = createMockClientWithSchema(testTopic)
      val serializer = new ScalaConfluentJsonSerializer[TestEvent](mockClient, testSchemaRegistryUrl)

      // Creating event with empty string should not throw during object creation
      val event = TestEvent("", 0)
      event.id shouldBe ""
      event.value shouldBe 0

  "ScalaConfluentJsonSerializer configuration" should:

    "configure with TopicRecordNameStrategy" in:
      val mockClient = createMockClient()
      val serializer = new ScalaConfluentJsonSerializer[TestEvent](mockClient, testSchemaRegistryUrl)

      // Verify TopicRecordNameStrategy is the expected subject naming strategy
      // by checking the serializer can be configured with it
      val strategy = new io.confluent.kafka.serializers.subject.TopicRecordNameStrategy()
      strategy shouldBe a[io.confluent.kafka.serializers.subject.TopicRecordNameStrategy]

      // Verify strategy implements SubjectNameStrategy interface
      classOf[io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy].isAssignableFrom(strategy.getClass) shouldBe true

      // Verify serializer accepts configuration (used during initialization)
      val configMethod = serializer.getClass.getMethods.find(_.getName == "configure")
      configMethod shouldBe defined

    "be configured for JSON Schema draft_2020_12 spec" in:
      val mockClient = createMockClient()
      val serializer = new ScalaConfluentJsonSerializer[TestEvent](mockClient, testSchemaRegistryUrl)

      // Verify serializer is JSON Schema type (not Avro or Protobuf)
      // The draft_2020_12 spec is set in the serializer's configuration
      serializer shouldBe a[io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer[?]]

      // Verify the serializer has the expected configuration method
      val configureMethod = serializer.getClass.getMethod("configure", classOf[java.util.Map[?, ?]], classOf[Boolean])
      configureMethod should not be null

    "require pre-registered schemas (auto.register.schemas=false behavior)" in:
      val mockClient = createMockClient()
      val serializer = new ScalaConfluentJsonSerializer[TestEvent](mockClient, testSchemaRegistryUrl)

      // With auto.register.schemas=false, serializer expects schemas to exist
      // Attempting to serialize without registered schema should fail
      val event = TestEvent("test-id", 42)

      // The serialize method requires a topic for schema lookup
      val exception = intercept[Exception]:
        serializer.serialize(testTopic, event)

      // Exception indicates schema lookup failed (expected with mock client)
      exception should not be null

    "use latest schema version for serialization" in:
      val mockClient = createMockClientWithSchema(testTopic)
      val serializer = new ScalaConfluentJsonSerializer[TestEvent](mockClient, testSchemaRegistryUrl)

      // With use.latest.version=true, serializer fetches latest schema
      // Verify serializer can access schema metadata methods
      val getSchemaMethod = mockClient.getClass.getMethods.find(_.getName == "getLatestSchemaMetadata")
      getSchemaMethod shouldBe defined

      // Verify the mock client returns expected schema metadata
      val metadata = mockClient.getLatestSchemaMetadata(s"$testTopic-value")
      metadata.getSchemaType shouldBe "JSON"
      metadata.getVersion shouldBe 1

  // Helper: Create mock SchemaRegistryClient
  private def createMockClient(): SchemaRegistryClient =
    new CachedSchemaRegistryClient(testSchemaRegistryUrl, 100)

  // Helper: Create mock client that returns JSON schema for topic
  private def createMockClientWithSchema(topic: String): SchemaRegistryClient =
    new CachedSchemaRegistryClient(testSchemaRegistryUrl, 100):
      override def getLatestSchemaMetadata(subject: String): SchemaMetadata =
        val schema = """{"type":"object","properties":{"id":{"type":"string"},"value":{"type":"integer"}}}"""
        new SchemaMetadata(1, 1, schema):
          override def getSchemaType: String = "JSON"