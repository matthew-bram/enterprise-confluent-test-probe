package io.distia.probe
package core
package pubsub

import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaMetadata, SchemaRegistryClient}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import models.{SchemaNotFoundException, SchemaRegistryNotInitializedException}

class SerdesFactorySpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  override def beforeEach(): Unit =
    SerdesFactory.client = None
    SerdesFactory.schemaRegistryUrl = None

  override def afterEach(): Unit =
    SerdesFactory.client = None
    SerdesFactory.schemaRegistryUrl = None

  "SerdesFactory.setClient (single-arg)" should {

    "initialize Schema Registry client" in {
      val mockClient = createMockClient()

      SerdesFactory.setClient(mockClient)

      SerdesFactory.client shouldBe defined
    }

    "allow re-initialization with different client" in {
      val client1 = createMockClient()
      val client2 = createMockClient()

      SerdesFactory.setClient(client1)
      SerdesFactory.setClient(client2)

      SerdesFactory.client.get should be theSameInstanceAs client2
    }
  }

  "SerdesFactory.setClient (two-arg)" should {

    "initialize both client and URL" in {
      val mockClient = createMockClient()
      val testUrl = "http://test-url:8081"

      SerdesFactory.setClient(mockClient, testUrl)

      SerdesFactory.client shouldBe defined
      SerdesFactory.schemaRegistryUrl shouldBe Some(testUrl)
    }

    "allow URL to be updated independently" in {
      val mockClient = createMockClient()

      SerdesFactory.setClient(mockClient, "http://first-url:8081")
      SerdesFactory.setClient(mockClient, "http://second-url:8081")

      SerdesFactory.schemaRegistryUrl shouldBe Some("http://second-url:8081")
    }
  }

  "SerdesFactory.extractClient" should {

    "return client when initialized" in {
      val mockClient = createMockClient()
      SerdesFactory.setClient(mockClient)

      val extracted = SerdesFactory.extractClient

      extracted should be theSameInstanceAs mockClient
    }

    "throw SchemaRegistryNotInitializedException when not initialized" in {
      val exception = intercept[SchemaRegistryNotInitializedException] {
        SerdesFactory.extractClient
      }

      exception.getMessage should include("Schema Registry client not initialized")
    }
  }

  "SerdesFactory.extractSchemaRegistryUrl" should {

    "return URL when initialized" in {
      val mockClient = createMockClient()
      val testUrl = "http://schema-registry:8081"
      SerdesFactory.setClient(mockClient, testUrl)

      val extracted = SerdesFactory.extractSchemaRegistryUrl

      extracted shouldBe testUrl
    }

    "throw SchemaRegistryNotInitializedException when URL not initialized" in {
      val mockClient = createMockClient()
      SerdesFactory.setClient(mockClient)

      val exception = intercept[SchemaRegistryNotInitializedException] {
        SerdesFactory.extractSchemaRegistryUrl
      }

      exception.getMessage should include("Schema Registry client not initialized")
    }

    "throw SchemaRegistryNotInitializedException when neither client nor URL set" in {
      val exception = intercept[SchemaRegistryNotInitializedException] {
        SerdesFactory.extractSchemaRegistryUrl
      }

      exception.getMessage should include("Schema Registry client not initialized")
    }
  }

  "SerdesFactory.schemaTypeForSubject" should {

    "return AVRO for Avro schema subject" in {
      val mockClient = createMockClientWithSchema("test-topic-value", "AVRO")
      SerdesFactory.setClient(mockClient)

      val schemaType = SerdesFactory.schemaTypeForSubject("test-topic-value")

      schemaType shouldBe "AVRO"
    }

    "return PROTOBUF for Protobuf schema subject" in {
      val mockClient = createMockClientWithSchema("test-topic-value", "PROTOBUF")
      SerdesFactory.setClient(mockClient)

      val schemaType = SerdesFactory.schemaTypeForSubject("test-topic-value")

      schemaType shouldBe "PROTOBUF"
    }

    "return JSON for JSON Schema subject" in {
      val mockClient = createMockClientWithSchema("test-topic-value", "JSON")
      SerdesFactory.setClient(mockClient)

      val schemaType = SerdesFactory.schemaTypeForSubject("test-topic-value")

      schemaType shouldBe "JSON"
    }

    "return JSONSCHEMA for JSON Schema subject (alternate format)" in {
      val mockClient = createMockClientWithSchema("test-topic-value", "JSONSCHEMA")
      SerdesFactory.setClient(mockClient)

      val schemaType = SerdesFactory.schemaTypeForSubject("test-topic-value")

      schemaType shouldBe "JSONSCHEMA"
    }

    "convert schema type to uppercase" in {
      val mockClient = createMockClientWithSchema("test-topic-value", "avro")
      SerdesFactory.setClient(mockClient)

      val schemaType = SerdesFactory.schemaTypeForSubject("test-topic-value")

      schemaType shouldBe "AVRO"
    }

    "handle mixed case schema types by converting to uppercase" in {
      val testCases = List(
        ("avro", "AVRO"),
        ("Avro", "AVRO"),
        ("AVRO", "AVRO"),
        ("protobuf", "PROTOBUF"),
        ("Protobuf", "PROTOBUF"),
        ("PROTOBUF", "PROTOBUF"),
        ("json", "JSON"),
        ("Json", "JSON"),
        ("JSON", "JSON"),
        ("jsonschema", "JSONSCHEMA"),
        ("JsonSchema", "JSONSCHEMA"),
        ("JSONSCHEMA", "JSONSCHEMA")
      )

      testCases.foreach { case (input, expected) =>
        val mockClient = createMockClientWithSchema("test-subject", input)
        SerdesFactory.setClient(mockClient)

        val result = SerdesFactory.schemaTypeForSubject("test-subject")

        result shouldBe expected
      }
    }

    "throw SchemaNotFoundException when metadata returns null schema type" in {
      val mockClient = createMockClientWithNullSchemaType("null-type-subject")
      SerdesFactory.setClient(mockClient)

      val exception = intercept[SchemaNotFoundException] {
        SerdesFactory.schemaTypeForSubject("null-type-subject")
      }

      exception.getMessage should include("Schema type not found for subject: null-type-subject")
    }

    "throw SchemaRegistryNotInitializedException when client not initialized" in {
      val exception = intercept[SchemaRegistryNotInitializedException] {
        SerdesFactory.schemaTypeForSubject("test-topic-value")
      }

      exception.getMessage should include("Schema Registry client not initialized")
    }
  }

  "SerdesFactory serialize/deserialize schema type dispatch" should {

    "throw IllegalArgumentException for unsupported schema type in serialize" in {
      val mockClient = createMockClientWithSchema("topic-String", "UNKNOWN_TYPE")
      SerdesFactory.setClient(mockClient, "http://localhost:8081")

      val exception = intercept[IllegalArgumentException] {
        SerdesFactory.serialize[String]("test", "topic", isKey = false)
      }

      exception.getMessage should include("Unsupported schema type: UNKNOWN_TYPE")
    }

    "throw IllegalArgumentException for unsupported schema type in deserialize" in {
      val mockClient = createMockClientWithSchema("topic-String", "XML")
      SerdesFactory.setClient(mockClient, "http://localhost:8081")

      val exception = intercept[IllegalArgumentException] {
        SerdesFactory.deserialize[String](Array[Byte](0, 0, 0, 1, 0), "topic", isKey = false)
      }

      exception.getMessage should include("Unsupported schema type: XML")
    }

    "recognize JSONSCHEMA as valid for serialize (alternate JSON format)" in {
      val mockClient = createMockClientWithSchema("topic-String", "JSONSCHEMA")
      SerdesFactory.setClient(mockClient, "http://localhost:8081")

      val exception = intercept[Exception] {
        SerdesFactory.serialize[String]("test", "topic", isKey = false)
      }

      exception.getMessage should not include "Unsupported schema type"
    }
  }

  "SerdesFactory state management" should {

    "maintain separate client and URL state" in {
      val client1 = createMockClient()
      val client2 = createMockClient()

      SerdesFactory.setClient(client1)
      SerdesFactory.client.get should be theSameInstanceAs client1
      SerdesFactory.schemaRegistryUrl shouldBe None

      SerdesFactory.setClient(client2, "http://test:8081")
      SerdesFactory.client.get should be theSameInstanceAs client2
      SerdesFactory.schemaRegistryUrl shouldBe Some("http://test:8081")
    }

    "support volatile semantics for thread visibility" in {
      @volatile var threadSawClient = false

      val mockClient = createMockClient()
      SerdesFactory.setClient(mockClient, "http://test:8081")

      val thread = new Thread(() => {
        threadSawClient = SerdesFactory.client.isDefined
      })
      thread.start()
      thread.join(1000)

      threadSawClient shouldBe true
    }
  }

  private def createMockClient(): SchemaRegistryClient =
    new CachedSchemaRegistryClient("http://localhost:8081", 100)

  private def createMockClientWithSchema(subject: String, schemaType: String): SchemaRegistryClient =
    new CachedSchemaRegistryClient("http://localhost:8081", 100) {
      override def getLatestSchemaMetadata(subj: String): SchemaMetadata =
        if subj == subject then
          new SchemaMetadata(1, 1, """{"type":"string"}""") {
            override def getSchemaType: String = schemaType
          }
        else
          throw new RuntimeException(s"Subject not found: $subj")
    }

  private def createMockClientWithNullSchemaType(subject: String): SchemaRegistryClient =
    new CachedSchemaRegistryClient("http://localhost:8081", 100) {
      override def getLatestSchemaMetadata(subj: String): SchemaMetadata =
        if subj == subject then
          new SchemaMetadata(1, 1, """{"type":"string"}""") {
            override def getSchemaType: String = null
          }
        else
          throw new RuntimeException(s"Subject not found: $subj")
    }
}