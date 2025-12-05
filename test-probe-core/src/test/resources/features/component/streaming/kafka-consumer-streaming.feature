@ComponentTest
Feature: KafkaConsumerStreamingActor - Event Consumption from Kafka
  As a KafkaConsumerStreamingActor
  I need to consume events from Kafka topics and store them in registry
  So that Cucumber tests can fetch and validate consumed events

  Background:
    Given testcontainers infrastructure is started
    And a running actor system
    And a CoreConfig is available

  @Critical @HappyPath
  Scenario Outline: Consume event, store in registry, and fetch successfully
    Given a TopicDirective is configured with event filter <eventType> version <eventVersion>
    And a KafkaSecurityDirective is available for consumer
    Given a KafkaConsumerStreamingActor is spawned for test <eventTestId> and topic <topic>
    When a Kafka message arrives on topic <topic> with eventTestId <eventTestId>
    Then the event should be decoded using SchemaRegistryDecoder
    And the event should be stored in the consumer registry
    When the KafkaConsumerStreamingActor receives FetchConsumedEvent for <eventTestId>
    Then the KafkaConsumerStreamingActor should respond with ConsumedAck
    And the ConsumedAck should contain the CloudEvent

    Examples:
      | testId                               | topic             | eventTestId             | eventType          | eventVersion |
      | "test-consumer-happy-001"            | "test-events"     | "event-001"             | "OrderCreated"     | "v1"         |
#     | "test-consumer-happy-002"            | "order-events"    | "event-002"             | "OrderUpdated"     | "v2"         |
#     | "test-consumer-happy-003"            | "payment-events"  | "event-003"             | "PaymentProcessed" | "v1"      |

  @Critical @Filtering
  Scenario Outline: Event filtering - only store events matching event type and version
    Given a KafkaConsumerStreamingActor is spawned for test <testId> and topic <topic>
    And a TopicDirective is configured with event filter <eventType> version <eventVersion>
    And a KafkaSecurityDirective is available for consumer
    When a Kafka message arrives on topic <topic> with wrong eventType
    Then the event should NOT be stored in the consumer registry
    When a Kafka message arrives on topic <topic> with wrong eventVersion
    Then the event should NOT be stored in the consumer registry
    When a Kafka message arrives on topic <topic> with matching eventType and eventVersion
    Then the event should be stored in the consumer registry

    Examples:
      | testId                               | topic           | eventType       | eventVersion |
      | "test-consumer-filter-001"           | "test-events"   | "OrderCreated"  | "v1"         |
#     | "test-consumer-filter-002"           | "order-events"  | "OrderUpdated"  | "v2"         |

  @Critical @ErrorHandling
  Scenario Outline: Schema decode failure skips event without crashing
    Given a KafkaConsumerStreamingActor is spawned for test <testId> and topic <topic>
    And a TopicDirective is configured with event filter <eventType> version <eventVersion>
    And a KafkaSecurityDirective is available for consumer
    When a Kafka message arrives with malformed Schema Registry payload
    Then the event should be skipped with warning log
    And the KafkaConsumerStreamingActor should continue consuming
    And OpenTelemetry counter should increment for consumer decode errors
    And the event should NOT be stored in the consumer registry

    Examples:
      | testId                               | topic           | eventType       | eventVersion |
      | "test-consumer-decode-fail-001"      | "test-events"   | "OrderCreated"  | "v1"         |
#     | "test-consumer-decode-fail-002"      | "order-events"  | "OrderUpdated"  | "v2"         |

  @Critical @MissingEvent
  Scenario Outline: FetchConsumedEvent for missing event returns ConsumedNack
    Given a KafkaConsumerStreamingActor is spawned for test <testId> and topic <topic>
    And a TopicDirective is configured with event filter <eventType> version <eventVersion>
    And a KafkaSecurityDirective is available for consumer
    When the KafkaConsumerStreamingActor receives FetchConsumedEvent for <eventTestId>
    Then the KafkaConsumerStreamingActor should respond with ConsumedNack
    And the ConsumedNack should have status 0

    Examples:
      | testId                               | topic           | eventTestId                          | eventType       | eventVersion |
      | "test-consumer-missing-001"          | "test-events"   | "event-missing-001"                  | "OrderCreated"  | "v1"         |
#     | "test-consumer-missing-002"          | "order-events"  | "event-missing-002"                  | "OrderUpdated"  | "v2"         |

  @HappyPath @FIFO
  Scenario Outline: Consume multiple events in FIFO order
    Given a KafkaConsumerStreamingActor is spawned for test <testId> and topic <topic>
    And a TopicDirective is configured with event filter <eventType> version <eventVersion>
    And a KafkaSecurityDirective is available for consumer
    When a Kafka message arrives with eventTestId "event-001"
    And a Kafka message arrives with eventTestId "event-002"
    And a Kafka message arrives with eventTestId "event-003"
    When the KafkaConsumerStreamingActor receives FetchConsumedEvent for the following events
      | eventTestId |
      | event-001   |
      | event-002   |
      | event-003   |
    Then the KafkaConsumerStreamingActor should respond with ConsumedAck for the following events
      | eventTestId |
      | event-001   |
      | event-002   |
      | event-003   |
    Then all events should be consumed in FIFO order
    And all events should be stored in the consumer registry

    Examples:
      | testId                               | topic           | eventType       | eventVersion |
      | "test-consumer-fifo-001"             | "test-events"   | "OrderCreated"  | "v1"         |
#     | "test-consumer-fifo-002"             | "order-events"  | "OrderUpdated"  | "v2"         |

  @Lifecycle @Cleanup @ADR004
  Scenario Outline: PostStop triggers control.stop and materializer shutdown per ADR-004
    Given a KafkaConsumerStreamingActor is spawned for test <testId> and topic <topic>
    And a TopicDirective is configured with event filter <eventType> version <eventVersion>
    And a KafkaSecurityDirective is available for consumer
    And the consumer stream is actively consuming
    When the KafkaConsumerStreamingActor receives PostStop signal
    Then the Kafka consumer control should stop immediately
    And no drainAndShutdown should be called
    And the last 0-19 offsets may not be committed
    And no resource leaks should be present
    And the actor should be unregistered from ProbeScalaDsl

    Examples:
      | testId                               | topic           | eventType       | eventVersion |
      | "test-consumer-cleanup-001"          | "test-events"   | "OrderCreated"  | "v1"         |
#     | "test-consumer-cleanup-002"          | "order-events"  | "OrderUpdated"  | "v2"         |

  @Performance @CommitBatching
  Scenario Outline: Offset commits are batched with max 20 events per batch
    Given a KafkaConsumerStreamingActor is spawned for test <testId> and topic <topic>
    And a TopicDirective is configured with event filter <eventType> version <eventVersion>
    And a KafkaSecurityDirective is available for consumer
    When 25 Kafka messages arrive matching event type and version
    Then offsets should be committed in 2 batches
    And the first batch should contain 20 offsets
    And the second batch should contain 5 offsets
    And all 25 events should be stored in the consumer registry

    Examples:
      | testId                               | topic           | eventType       | eventVersion |
      | "test-consumer-batch-001"            | "test-events"   | "OrderCreated"  | "v1"         |
#     | "test-consumer-batch-002"            | "order-events"  | "OrderUpdated"  | "v2"         |

  @Edge @RegistryIdempotency
  Scenario Outline: Duplicate events are not added to registry twice
    Given a KafkaConsumerStreamingActor is spawned for test <testId> and topic <topic>
    And a TopicDirective is configured with event filter <eventType> version <eventVersion>
    And a KafkaSecurityDirective is available for consumer
    When a Kafka message arrives with eventTestId <eventTestId>
    And the event is stored in the consumer registry
    When a Kafka message arrives again with the same eventTestId <eventTestId>
    Then the registry should still contain only one entry for <eventTestId>

    Examples:
      | testId                               | topic           | eventTestId                          | eventType       | eventVersion |
      | "test-consumer-idempotent-001"       | "test-events"   | "event-duplicate-001"                | "OrderCreated"  | "v1"         |
#     | "test-consumer-idempotent-002"       | "order-events"  | "event-duplicate-002"                | "OrderUpdated"  | "v2"         |
