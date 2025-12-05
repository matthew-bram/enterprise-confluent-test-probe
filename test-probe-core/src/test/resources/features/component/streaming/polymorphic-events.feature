@ComponentTest
Feature: Polymorphic Events - OneOf JSON Schema Support
  As a test framework
  I need to produce and consume multiple event types on a single Kafka topic
  So that event democratization is enabled with type-safe schema validation

  Background:
    Given testcontainers infrastructure is started
    And a running actor system
    And a CoreConfig is available

  @Critical @HappyPath @OneOf
  Scenario Outline: Produce and consume OrderCreated event with CloudEvent key
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a valid CloudEvent is created for OrderCreated event with orderId <orderId> customerId <customerId> amount <amount>
    And an OrderCreated payload is created with orderId <orderId> customerId <customerId> amount <amount>
    When the producer receives ProduceEvent command for OrderCreated
    Then the producer should respond with ProducedAck
    And the OrderCreated event should be produced to Kafka topic <topic>
    And the event should be serialized with subject <subject>
    And consuming the event should deserialize to OrderCreated type

    Examples:
      | testId                               | topic            | subject             | orderId         | customerId      | amount |
      | "test-order-created-001"             | "orders"         | "orders-OrderCreated" | "order-12345"   | "customer-001"  | 100.00 |

  @Critical @HappyPath @OneOf
  Scenario Outline: Produce and consume PaymentProcessed event with CloudEvent key
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a valid CloudEvent is created for PaymentProcessed event with paymentId <paymentId> orderId <orderId> amount <amount>
    And a PaymentProcessed payload is created with paymentId <paymentId> orderId <orderId> amount <amount> paymentMethod <paymentMethod>
    When the producer receives ProduceEvent command for PaymentProcessed
    Then the producer should respond with ProducedAck
    And the PaymentProcessed event should be produced to Kafka topic <topic>
    And the event should be serialized with subject <subject>
    And consuming the event should deserialize to PaymentProcessed type

    Examples:
      | testId                               | topic            | subject                    | paymentId       | orderId         | amount | paymentMethod |
      | "test-payment-processed-001"         | "payments"       | "payments-PaymentProcessed" | "payment-67890" | "order-12345"   | 100.00 | "credit_card" |

  @Critical @HappyPath @OneOf @EventDemocratization @Matt
  Scenario Outline: Produce multiple event types to same topic (Event Democratization)
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And an OrderCreated payload is created with orderId <orderId1> customerId <customerId> amount <amount1>
    And a CloudEvent is created for OrderCreated with eventTestId <eventTestId1>
    And a PaymentProcessed payload is created with paymentId <paymentId> orderId <orderId2> amount <amount2> paymentMethod <paymentMethod>
    And a CloudEvent is created for PaymentProcessed with eventTestId <eventTestId2>
    When the producer receives ProduceEvent command for OrderCreated
    And the producer receives ProduceEvent command for PaymentProcessed
    Then the producer should respond with ProducedAck for both events
    And both events should be produced to the same Kafka topic <topic>
    And the OrderCreated event should use subject <orderSubject>
    And the PaymentProcessed event should use subject <paymentSubject>
    And consuming both events should deserialize to their respective types

    Examples:
      | testId                               | topic            | orderId1        | customerId      | amount1 | paymentId       | orderId2        | amount2 | paymentMethod | eventTestId1  | eventTestId2  | orderSubject                   | paymentSubject                      |
      | "test-event-democratization-001"     | "domain-events"  | "order-11111"   | "customer-001"  | 150.00  | "payment-22222" | "order-11111"   | 150.00  | "credit_card" | "event-001"   | "event-002"   | "domain-events-OrderCreated"   | "domain-events-PaymentProcessed"    |

  @Critical @SchemaValidation @OneOf
  Scenario Outline: Schema validation enforces correct event type structure
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And an OrderCreated payload is created with missing required field <missingField>
    When the producer receives ProduceEvent command for OrderCreated
    Then the producer should respond with ProducedNack
    And the error should indicate schema validation failure
    And the error message should mention the missing field <missingField>

    Examples:
      | testId                               | topic            | missingField   |
      | "test-schema-validation-001"         | "orders"         | "customerId"   |

  @Critical @SchemaEvolution @OneOf
  Scenario Outline: Independent schema evolution for each event type
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And the Schema Registry has OrderCreated schema version <orderVersion> registered
    And the Schema Registry has PaymentProcessed schema version <paymentVersion> registered
    And an OrderCreated payload is created matching version <orderVersion>
    And a PaymentProcessed payload is created matching version <paymentVersion>
    When the producer receives ProduceEvent for both event types
    Then both events should serialize with their respective schema versions
    And the OrderCreated event should use schema ID for version <orderVersion>
    And the PaymentProcessed event should use schema ID for version <paymentVersion>
    And schema evolution should be independent for each type

    Examples:
      | testId                               | topic            | orderVersion | paymentVersion |
      | "test-schema-evolution-001"          | "domain-events"  | 1            | 1              |

  @Edge @OneOf @SubjectNaming
  Scenario Outline: TopicRecordNameStrategy subject naming without key-value suffix
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And an OrderCreated payload is created
    And a CloudEvent is created for OrderCreated with eventTestId "subject-naming-test"
    When the producer receives ProduceEvent command for OrderCreated
    Then the event should be serialized with subject <expectedSubject>
    And the subject should NOT contain "-key" or "-value" suffix
    And the subject pattern should match TopicRecordNameStrategy format

    Examples:
      | testId                               | topic            | expectedSubject         |
      | "test-subject-naming-001"            | "orders"         | "orders-OrderCreated"   |
      | "test-subject-naming-002"            | "domain-events"  | "domain-events-OrderCreated" |

  @Edge @OneOf @CloudEventKey
  Scenario Outline: CloudEvent keys work consistently across all polymorphic event types
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a CloudEvent key is created with eventTestId <eventTestId>
    And an OrderCreated payload is created
    And a CloudEvent is created for OrderCreated with eventTestId <eventTestId>
    When the producer receives ProduceEvent command for OrderCreated
    Then the CloudEvent key should be serialized with subject <keySubject>
    And the CloudEvent key should contain eventTestId <eventTestId>
    And the key subject should be consistent for all event types on topic <topic>

    Examples:
      | testId                               | topic                  | eventTestId       | keySubject                  |
      | "test-cloudevent-key-001"            | "cloudevent-key-test"  | "event-key-001"   | "cloudevent-key-test-CloudEvent"  |

  @Performance @OneOf
  Scenario Outline: Produce multiple polymorphic events with minimal latency overhead
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And <eventCount> OrderCreated events are created
    And <eventCount> PaymentProcessed events are created
    When the producer receives ProduceEvent for all <totalCount> events
    Then all events should be produced successfully
    And the total serialization time should be less than <maxLatencyMs> milliseconds
    And the Schema Registry cache hit ratio should be greater than <cacheHitRatio> percent

    Examples:
      | testId                               | topic            | eventCount | totalCount | maxLatencyMs | cacheHitRatio |
      | "test-performance-001"               | "domain-events"  | 100        | 200        | 2000         | 99            |
