# Produce event successfully and return ProducedAck"Tag: @Phase1Target (to run only this one)
@ComponentTest
Feature: KafkaProducerStreamingActor - Event Production to Kafka
  As a KafkaProducerStreamingActor
  I need to encode CloudEvents and produce them to Kafka topics
  So that Cucumber tests can publish events for testing event-driven systems

  Background:
    Given testcontainers infrastructure is started
    And a running actor system
    And a CoreConfig is available

  @Critical @HappyPath
  Scenario Outline: Produce event successfully and return ProducedAck
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a valid CloudEvent is created with eventTestId <eventTestId> type <eventType> version <eventVersion>
    And a test payload is created
    When the KafkaProducerStreamingActor receives ProduceEvent command
    Then the KafkaProducerStreamingActor should respond with ProducedAck
    And the event should be encoded using SchemaRegistryEncoder
    And the event should be produced to Kafka topic <topic>

    Examples:
      | testId                               | topic             | eventTestId                          | eventType                        | eventVersion |
      | "test-producer-happy-001"            | "test-events"     | "event-001"                          | "io.distia.probe.test.event"   | "v1"         |
#     | "test-producer-happy-002"            | "order-events"    | "event-002"                          | "OrderCreated"                   | "v2"         |
#     | "test-producer-happy-003"            | "payment-events"  | "event-003"                          | "PaymentProcessed"               | "v1"         |

  @Critical @ErrorHandling @Ignore
  Scenario Outline: Kafka network failure returns ProducedNack
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a valid CloudEvent is created with eventTestId <eventTestId>
    And a test payload is created
    And the Kafka producer is configured to fail with network exception
    When the KafkaProducerStreamingActor receives ProduceEvent command
    Then the KafkaProducerStreamingActor should respond with ProducedNack
    And the ProducedNack should contain the network exception
    And the exception should be logged with error level
    And OpenTelemetry counter should increment for producer errors
    # TODO: Requires mocking infrastructure for live Kafka - cannot easily simulate network failures with Testcontainers

    Examples:
      | testId                               | topic           | eventTestId                          |
      | "test-producer-network-fail-001"     | "test-events"   | "event-network-001"                  |
#     | "test-producer-network-fail-002"     | "order-events"  | "event-network-002"                  |

  @HappyPath @FIFO
  Scenario Outline: Produce multiple events in FIFO order
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a valid CloudEvent is created with eventTestId "event-001"
    And a valid CloudEvent is created with eventTestId "event-002"
    And a valid CloudEvent is created with eventTestId "event-003"
    And a test payload is created
    When the KafkaProducerStreamingActor receives ProduceEvent for the following events
      | eventTestId |
      | event-001   |
      | event-002   |
      | event-003   |
    Then the KafkaProducerStreamingActor should respond with ProducedAck for the following events
      | eventTestId |
      | event-001   |
      | event-002   |
      | event-003   |
    And all events should be produced in FIFO order to topic <topic>

    Examples:
      | testId                               | topic                |
      | "test-producer-fifo-001"             | "fifo-test-events"   |
#     | "test-producer-fifo-002"             | "order-events"  |

  @Lifecycle @Cleanup
  Scenario Outline: PostStop triggers stream shutdown and materializer cleanup
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a valid CloudEvent is created with eventTestId <eventTestId>
    And a test payload is created
    And the KafkaProducerStreamingActor receives ProduceEvent command
    When the KafkaProducerStreamingActor receives Stop signal
    Then the Kafka producer stream should shutdown cleanly
    And no resource leaks should be present

    Examples:
      | testId                               | topic           | eventTestId                          |
      | "test-producer-cleanup-001"          | "test-events"   | "event-cleanup-001"                  |
#     | "test-producer-cleanup-002"          | "order-events"  | "event-cleanup-002"                  |

  @Edge @Timeout
  Scenario Outline: Produce event with ask timeout
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a valid CloudEvent is created with eventTestId <eventTestId>
    And a test payload is created
    And the ask timeout is configured to <timeout> seconds
    When the KafkaProducerStreamingActor receives ProduceEvent command
    Then the ProducedAck should be received within <timeout> seconds
    And no timeout exception should occur

    Examples:
      | testId                               | topic           | eventTestId                          | timeout |
      | "test-producer-timeout-001"          | "test-events"   | "event-timeout-001"                  | 5       |
#     | "test-producer-timeout-002"          | "order-events"  | "event-timeout-002"                  | 3       |
