package io.distia.probe.core.fixtures

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests StreamingTestConstantsFixtures for predefined constants and objects.
 *
 * Verifies:
 * - Bootstrap server constants (DefaultBootstrapServers, CustomBootstrapServers)
 * - Schema Registry URL constant (DefaultSchemaRegistryUrl)
 * - Test event IDs (TestEventIds.Event1-Event5)
 * - Event types (EventTypes constants)
 * - Event versions (EventVersions constants)
 * - Topics (Topics constants)
 * - Client IDs (ClientIds constants)
 * - Payloads (SmallPayload, MediumPayload, LargePayload)
 * - Trace IDs (TraceIds constants)
 * - Immutability of all constants
 * - Uniqueness of UUIDs
 * - Payload sizes
 *
 * Test Strategy: Unit tests (no external dependencies)
 *
 * Dogfooding: Tests StreamingTestConstantsFixtures using StreamingTestConstantsFixtures itself
 */
class StreamingTestConstantsFixturesSpec extends AnyWordSpec
  with Matchers
  with StreamingTestConstantsFixtures {

  "StreamingTestConstantsFixtures" should {

    "provide DefaultBootstrapServers constant" in {
      DefaultBootstrapServers should not be empty
    }

    "provide CustomBootstrapServers constant" in {
      CustomBootstrapServers should not be empty
    }

    "provide DefaultSchemaRegistryUrl constant" in {
      DefaultSchemaRegistryUrl should not be empty
    }

    "provide TestEventIds object" in {
      TestEventIds should not be null
    }

    "provide EventTypes object" in {
      EventTypes should not be null
    }

    "provide EventVersions object" in {
      EventVersions should not be null
    }

    "provide Topics object" in {
      Topics should not be null
    }

    "provide ClientIds object" in {
      ClientIds should not be null
    }

    "provide Payloads object" in {
      Payloads should not be null
    }

    "provide TraceIds object" in {
      TraceIds should not be null
    }
  }

  "DefaultBootstrapServers" should {

    "point to localhost:9092" in {
      DefaultBootstrapServers shouldBe "localhost:9092"
    }

    "include port number" in {
      DefaultBootstrapServers should include (":9092")
    }
  }

  "CustomBootstrapServers" should {

    "point to custom-kafka:9092" in {
      CustomBootstrapServers shouldBe "custom-kafka:9092"
    }

    "differ from DefaultBootstrapServers" in {
      CustomBootstrapServers should not be DefaultBootstrapServers
    }
  }

  "DefaultSchemaRegistryUrl" should {

    "point to http://localhost:8081" in {
      DefaultSchemaRegistryUrl shouldBe "http://localhost:8081"
    }

    "use HTTP protocol" in {
      DefaultSchemaRegistryUrl should startWith ("http://")
    }

    "include port 8081" in {
      DefaultSchemaRegistryUrl should include (":8081")
    }
  }

  "TestEventIds" should {

    "provide Event1 UUID" in {
      TestEventIds.Event1 should not be null
    }

    "provide Event2 UUID" in {
      TestEventIds.Event2 should not be null
    }

    "provide Event3 UUID" in {
      TestEventIds.Event3 should not be null
    }

    "provide Event4 UUID" in {
      TestEventIds.Event4 should not be null
    }

    "provide Event5 UUID" in {
      TestEventIds.Event5 should not be null
    }

    "have all unique event IDs" in {
      val ids = List(
        TestEventIds.Event1,
        TestEventIds.Event2,
        TestEventIds.Event3,
        TestEventIds.Event4,
        TestEventIds.Event5
      )

      ids.distinct should have size 5
    }

    "use deterministic UUIDs" in {
      TestEventIds.Event1.toString shouldBe "00000000-0000-0000-0000-000000000001"
      TestEventIds.Event2.toString shouldBe "00000000-0000-0000-0000-000000000002"
      TestEventIds.Event3.toString shouldBe "00000000-0000-0000-0000-000000000003"
      TestEventIds.Event4.toString shouldBe "00000000-0000-0000-0000-000000000004"
      TestEventIds.Event5.toString shouldBe "00000000-0000-0000-0000-000000000005"
    }
  }

  "EventTypes" should {

    "provide TestEvent type" in {
      EventTypes.TestEvent shouldBe "TestEvent"
    }

    "provide UserCreated type" in {
      EventTypes.UserCreated shouldBe "UserCreated"
    }

    "provide UserUpdated type" in {
      EventTypes.UserUpdated shouldBe "UserUpdated"
    }

    "provide OrderPlaced type" in {
      EventTypes.OrderPlaced shouldBe "OrderPlaced"
    }

    "provide OrderCancelled type" in {
      EventTypes.OrderCancelled shouldBe "OrderCancelled"
    }

    "have all unique event types" in {
      val types = List(
        EventTypes.TestEvent,
        EventTypes.UserCreated,
        EventTypes.UserUpdated,
        EventTypes.OrderPlaced,
        EventTypes.OrderCancelled
      )

      types.distinct should have size 5
    }
  }

  "EventVersions" should {

    "provide V1_0 version" in {
      EventVersions.V1_0 shouldBe "1.0"
    }

    "provide V1_5 version" in {
      EventVersions.V1_5 shouldBe "1.5"
    }

    "provide V2_0 version" in {
      EventVersions.V2_0 shouldBe "2.0"
    }

    "provide V3_2_1 version" in {
      EventVersions.V3_2_1 shouldBe "3.2.1"
    }

    "have all unique versions" in {
      val versions = List(
        EventVersions.V1_0,
        EventVersions.V1_5,
        EventVersions.V2_0,
        EventVersions.V3_2_1
      )

      versions.distinct should have size 4
    }

    "use semantic versioning format" in {
      EventVersions.V1_0 should fullyMatch regex "\\d+\\.\\d+"
      EventVersions.V1_5 should fullyMatch regex "\\d+\\.\\d+"
      EventVersions.V2_0 should fullyMatch regex "\\d+\\.\\d+"
      EventVersions.V3_2_1 should fullyMatch regex "\\d+\\.\\d+\\.\\d+"
    }
  }

  "Topics" should {

    "provide TestEvents topic" in {
      Topics.TestEvents shouldBe "test-events"
    }

    "provide UserEvents topic" in {
      Topics.UserEvents shouldBe "user-events"
    }

    "provide OrderEvents topic" in {
      Topics.OrderEvents shouldBe "order-events"
    }

    "provide SecureEvents topic" in {
      Topics.SecureEvents shouldBe "secure-events"
    }

    "have all unique topics" in {
      val topics = List(
        Topics.TestEvents,
        Topics.UserEvents,
        Topics.OrderEvents,
        Topics.SecureEvents
      )

      topics.distinct should have size 4
    }

    "use kebab-case naming" in {
      Topics.TestEvents should include ("-")
      Topics.UserEvents should include ("-")
      Topics.OrderEvents should include ("-")
      Topics.SecureEvents should include ("-")
    }
  }

  "ClientIds" should {

    "provide ProducerClient ID" in {
      ClientIds.ProducerClient shouldBe "test-producer-client"
    }

    "provide ConsumerClient ID" in {
      ClientIds.ConsumerClient shouldBe "test-consumer-client"
    }

    "have unique client IDs" in {
      ClientIds.ProducerClient should not be ClientIds.ConsumerClient
    }

    "include role identifier in client IDs" in {
      ClientIds.ProducerClient should include ("producer")
      ClientIds.ConsumerClient should include ("consumer")
    }
  }

  "Payloads" should {

    "provide SmallPayload" in {
      Payloads.SmallPayload should not be empty
    }

    "provide MediumPayload" in {
      Payloads.MediumPayload should not be empty
    }

    "provide LargePayload" in {
      Payloads.LargePayload should not be empty
    }

    "have SmallPayload size less than 1 KB" in {
      Payloads.SmallPayload.length should be < 1024
    }

    "have MediumPayload size of 1 KB" in {
      Payloads.MediumPayload.length shouldBe 1024
    }

    "have LargePayload size of 1 MB" in {
      Payloads.LargePayload.length shouldBe 1024 * 1024
    }

    "have payloads in ascending size order" in {
      Payloads.SmallPayload.length should be < Payloads.MediumPayload.length
      Payloads.MediumPayload.length should be < Payloads.LargePayload.length
    }

    "use consistent fill value for medium payload" in {
      Payloads.MediumPayload.foreach(_ shouldBe 42)
    }

    "use consistent fill value for large payload" in {
      // Spot check first and last bytes
      Payloads.LargePayload.head shouldBe 42
      Payloads.LargePayload.last shouldBe 42
    }
  }

  "TraceIds" should {

    "provide DefaultTrace ID" in {
      TraceIds.DefaultTrace shouldBe "test-trace-id"
    }

    "provide Trace1 ID" in {
      TraceIds.Trace1 shouldBe "trace-00000001"
    }

    "provide Trace2 ID" in {
      TraceIds.Trace2 shouldBe "trace-00000002"
    }

    "have all unique trace IDs" in {
      val traceIds = List(
        TraceIds.DefaultTrace,
        TraceIds.Trace1,
        TraceIds.Trace2
      )

      traceIds.distinct should have size 3
    }

    "use consistent prefix for numbered traces" in {
      TraceIds.Trace1 should startWith ("trace-")
      TraceIds.Trace2 should startWith ("trace-")
    }
  }

  "StreamingConstants object" should {

    "provide standalone access to DefaultBootstrapServers" in {
      StreamingConstants.DefaultBootstrapServers shouldBe "localhost:9092"
    }

    "provide standalone access to TestEventIds" in {
      StreamingConstants.TestEventIds.Event1 should not be null
    }

    "provide standalone access to EventTypes" in {
      StreamingConstants.EventTypes.TestEvent shouldBe "TestEvent"
    }

    "provide standalone access to EventVersions" in {
      StreamingConstants.EventVersions.V1_0 shouldBe "1.0"
    }

    "provide standalone access to Topics" in {
      StreamingConstants.Topics.TestEvents shouldBe "test-events"
    }

    "provide standalone access to ClientIds" in {
      StreamingConstants.ClientIds.ProducerClient should include ("producer")
    }

    "provide standalone access to Payloads" in {
      StreamingConstants.Payloads.SmallPayload should not be empty
    }

    "provide standalone access to TraceIds" in {
      StreamingConstants.TraceIds.DefaultTrace should not be empty
    }
  }
}
