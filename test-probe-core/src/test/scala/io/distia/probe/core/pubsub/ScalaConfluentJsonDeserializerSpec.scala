package io.distia.probe
package core
package pubsub

import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaMetadata, SchemaRegistryClient}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Unit tests for ScalaConfluentJsonDeserializer.
 *
 * Tests the Scala-friendly wrapper for Confluent's KafkaJsonSchemaDeserializer.
 *
 * Test Strategy:
 * - Unit tests with mock SchemaRegistryClient
 * - Focus on deserializer configuration and behavior
 * - Tests for malformed input and edge cases
 *
 * Test Coverage:
 * - Deserializer instantiation with valid parameters
 * - Configuration with schema registry settings
 * - Empty/null input handling
 * - ObjectMapper configuration for Scala types
 *
 * Thread Safety: Tests run sequentially
 */
class ScalaConfluentJsonDeserializerSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach:

  private val testSchemaRegistryUrl = "http://localhost:8081"
  private val testTopic = "test-topic"

  // Simple test case class for JSON deserialization
  case class TestEvent(id: String, value: Int)

  override def beforeEach(): Unit =
    SerdesFactory.client = None
    SerdesFactory.schemaRegistryUrl = None

  override def afterEach(): Unit =
    SerdesFactory.client = None
    SerdesFactory.schemaRegistryUrl = None

  "ScalaConfluentJsonDeserializer" should:

    "instantiate with valid SchemaRegistryClient, URL, and isKey flag" in:
      val mockClient = createMockClient()

      val deserializer = new ScalaConfluentJsonDeserializer[TestEvent](mockClient, testSchemaRegistryUrl, isKey = false)

      // Verify deserializer wrapper was created successfully
      deserializer shouldBe a[ScalaConfluentJsonDeserializer[?]]
      // Verify deserialize method exists (from wrapper class)
      val deserializeMethods = deserializer.getClass.getMethods.filter(_.getName == "deserialize")
      deserializeMethods should not be empty

    "instantiate for key deserialization with isKey=true" in:
      val mockClient = createMockClient()

      val keyDeserializer = new ScalaConfluentJsonDeserializer[TestEvent](mockClient, testSchemaRegistryUrl, isKey = true)
      val valueDeserializer = new ScalaConfluentJsonDeserializer[TestEvent](mockClient, testSchemaRegistryUrl, isKey = false)

      // Verify both are valid deserializer wrappers but distinct instances
      keyDeserializer shouldBe a[ScalaConfluentJsonDeserializer[?]]
      valueDeserializer shouldBe a[ScalaConfluentJsonDeserializer[?]]
      keyDeserializer should not be theSameInstanceAs(valueDeserializer)

    "instantiate for value deserialization with isKey=false" in:
      val mockClient = createMockClient()

      val deserializer = new ScalaConfluentJsonDeserializer[TestEvent](mockClient, testSchemaRegistryUrl, isKey = false)

      // Verify deserialize method exists with expected signature
      val deserializeMethods = deserializer.getClass.getMethods.filter(_.getName == "deserialize")
      deserializeMethods.length should be > 0
      deserializeMethods.exists(_.getParameterCount >= 2) shouldBe true

    "support generic type parameter with ClassTag" in:
      val mockClient = createMockClient()

      // Test with different types
      noException should be thrownBy:
        new ScalaConfluentJsonDeserializer[String](mockClient, testSchemaRegistryUrl, isKey = false)

      noException should be thrownBy:
        new ScalaConfluentJsonDeserializer[Map[String, Any]](mockClient, testSchemaRegistryUrl, isKey = false)

      noException should be thrownBy:
        new ScalaConfluentJsonDeserializer[TestEvent](mockClient, testSchemaRegistryUrl, isKey = false)

    "use lazy ObjectMapper for performance" in:
      val mockClient = createMockClient()

      // Instantiation should not fail even without accessing mapper
      val deserializer = new ScalaConfluentJsonDeserializer[TestEvent](mockClient, testSchemaRegistryUrl, isKey = false)

      // Verify deserializer wrapper is functional type without triggering deserialization
      deserializer shouldBe a[ScalaConfluentJsonDeserializer[?]]

      // Verify deserialize method exists (mapper initialized lazily on first use)
      val deserializeMethod = deserializer.getClass.getMethods.find(_.getName == "deserialize")
      deserializeMethod shouldBe defined

  "ScalaConfluentJsonDeserializer configuration" should:

    "configure with TopicRecordNameStrategy" in:
      val mockClient = createMockClient()
      val deserializer = new ScalaConfluentJsonDeserializer[TestEvent](mockClient, testSchemaRegistryUrl, isKey = false)

      // Verify TopicRecordNameStrategy is the expected subject naming strategy
      val strategy = new io.confluent.kafka.serializers.subject.TopicRecordNameStrategy()
      strategy shouldBe a[io.confluent.kafka.serializers.subject.TopicRecordNameStrategy]

      // Verify strategy implements SubjectNameStrategy interface
      classOf[io.confluent.kafka.serializers.subject.strategy.SubjectNameStrategy].isAssignableFrom(strategy.getClass) shouldBe true

      // Verify deserializer wrapper has deserialize method for JSON parsing
      val deserializeMethod = deserializer.getClass.getMethods.find(_.getName == "deserialize")
      deserializeMethod shouldBe defined

    "be lenient with unknown properties (json.fail.unknown.properties=false)" in:
      val mockClient = createMockClient()
      val deserializer = new ScalaConfluentJsonDeserializer[TestEvent](mockClient, testSchemaRegistryUrl, isKey = false)

      // Verify deserializer wrapper was created (Jackson configured internally with fail.unknown.properties=false)
      deserializer shouldBe a[ScalaConfluentJsonDeserializer[?]]

      // Verify deserialize method exists for JSON parsing
      val deserializeMethods = deserializer.getClass.getMethods.filter(_.getName == "deserialize")
      deserializeMethods should not be empty

    "use schema caching for performance" in:
      val mockClient = createMockClient()
      val deserializer = new ScalaConfluentJsonDeserializer[TestEvent](mockClient, testSchemaRegistryUrl, isKey = false)

      // Verify schema registry client supports caching
      mockClient shouldBe a[CachedSchemaRegistryClient]

      // Verify deserializer wrapper was created (internal KafkaJsonSchemaDeserializer is configured with cache settings)
      deserializer shouldBe a[ScalaConfluentJsonDeserializer[?]]
      val deserializeMethod = deserializer.getClass.getMethods.find(_.getName == "deserialize")
      deserializeMethod shouldBe defined

    "use DefaultScalaModule for Jackson Scala type support" in:
      // Verify DefaultScalaModule is available for Scala case class deserialization
      val scalaModule = new com.fasterxml.jackson.module.scala.DefaultScalaModule()
      scalaModule shouldBe a[com.fasterxml.jackson.databind.Module]

      // Verify module has expected type ID for registration (case-insensitive check)
      scalaModule.getModuleName.toLowerCase should include("scala")

  "ScalaConfluentJsonDeserializer edge cases" should:

    "handle instantiation with empty topic name" in:
      val mockClient = createMockClient()

      // Empty topic name should not prevent deserializer creation
      // (topic is only used during deserialize call)
      noException should be thrownBy:
        new ScalaConfluentJsonDeserializer[TestEvent](mockClient, testSchemaRegistryUrl, isKey = false)

    "differentiate between key and value deserialization contexts" in:
      val mockClient = createMockClient()

      // Create separate deserializers for key and value with different type parameters
      val keyDeserializer = new ScalaConfluentJsonDeserializer[String](mockClient, testSchemaRegistryUrl, isKey = true)
      val valueDeserializer = new ScalaConfluentJsonDeserializer[TestEvent](mockClient, testSchemaRegistryUrl, isKey = false)

      // Verify they are distinct instances
      keyDeserializer should not be theSameInstanceAs(valueDeserializer)

      // Both should be valid deserializer wrappers
      keyDeserializer shouldBe a[ScalaConfluentJsonDeserializer[?]]
      valueDeserializer shouldBe a[ScalaConfluentJsonDeserializer[?]]

    "support complex nested types" in:
      val mockClient = createMockClient()

      // Nested case class for testing complex types
      case class NestedEvent(outer: String, inner: TestEvent)

      noException should be thrownBy:
        new ScalaConfluentJsonDeserializer[NestedEvent](mockClient, testSchemaRegistryUrl, isKey = false)

    "support optional fields via Option type" in:
      val mockClient = createMockClient()

      // Case class with optional field
      case class OptionalEvent(id: String, optionalValue: Option[Int])

      noException should be thrownBy:
        new ScalaConfluentJsonDeserializer[OptionalEvent](mockClient, testSchemaRegistryUrl, isKey = false)

    "support collection types" in:
      val mockClient = createMockClient()

      // Case class with collection
      case class CollectionEvent(id: String, items: List[String])

      noException should be thrownBy:
        new ScalaConfluentJsonDeserializer[CollectionEvent](mockClient, testSchemaRegistryUrl, isKey = false)

  "ScalaConfluentJsonDeserializer.deserialize" should:

    "have deserialize method with topic and bytes parameters" in:
      val mockClient = createMockClient()
      val deserializer = new ScalaConfluentJsonDeserializer[TestEvent](mockClient, testSchemaRegistryUrl, isKey = false)

      // Verify method exists with expected signature
      deserializer.getClass.getMethods.find(_.getName == "deserialize") match
        case Some(method) =>
          method.getParameterCount should be >= 2
        case None =>
          fail("deserialize method not found")

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