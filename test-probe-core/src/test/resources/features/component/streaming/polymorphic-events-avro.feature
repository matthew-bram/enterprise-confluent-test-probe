@ComponentTest @Avro
Feature: Polymorphic Events - Avro Schema Support
  As a test framework
  I need to produce and consume multiple Avro event types on a single Kafka topic
  So that event democratization is enabled with type-safe schema validation using Avro SpecificRecords

  Background:
    Given testcontainers infrastructure is started
    And a running actor system
    And a CoreConfig is available

  @Critical @HappyPath @Avro
  Scenario Outline: Produce and consume OrderCreatedAvro event with CloudEvent key
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a valid CloudEventAvro key is created for OrderCreatedAvro event with orderId <orderId> customerId <customerId> amount <amount>
    And an OrderCreatedAvro payload is created with orderId <orderId> customerId <customerId> amount <amount>
    When the producer receives ProduceEvent command for OrderCreatedAvro
    Then the producer should respond with ProducedAck
    And the OrderCreatedAvro event should be produced to Kafka topic <topic>
    And the event should be serialized with subject <subject>
    And the wire format should contain magic byte 0x00 and schema ID
    And consuming the event should deserialize to OrderCreatedAvro SpecificRecord type

    Examples:
      | testId                               | topic            | subject                    | orderId         | customerId      | amount |
      | "test-order-created-avro-001"        | "orders-avro"    | "orders-avro-OrderCreatedAvro" | "order-12345"   | "customer-001"  | 100.00 |

  @Critical @HappyPath @Avro
  Scenario Outline: Produce and consume PaymentProcessedAvro event with CloudEvent key
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a valid CloudEventAvro key is created for PaymentProcessedAvro event with paymentId <paymentId> orderId <orderId> amount <amount>
    And a PaymentProcessedAvro payload is created with paymentId <paymentId> orderId <orderId> amount <amount> paymentMethod <paymentMethod>
    When the producer receives ProduceEvent command for PaymentProcessedAvro
    Then the producer should respond with ProducedAck
    And the PaymentProcessedAvro event should be produced to Kafka topic <topic>
    And the event should be serialized with subject <subject>
    And the wire format should contain magic byte 0x00 and schema ID
    And consuming the event should deserialize to PaymentProcessedAvro SpecificRecord type

    Examples:
      | testId                               | topic              | subject                          | paymentId       | orderId         | amount | paymentMethod |
      | "test-payment-processed-avro-001"    | "payments-avro"    | "payments-avro-PaymentProcessedAvro" | "payment-67890" | "order-12345"   | 100.00 | "credit_card" |

  @Critical @HappyPath @Avro @EventDemocratization
  Scenario Outline: Produce multiple Avro event types to same topic (Event Democratization)
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And an OrderCreatedAvro payload is created with orderId <orderId1> customerId <customerId> amount <amount1>
    And a CloudEventAvro key is created for OrderCreatedAvro with eventTestId <eventTestId1>
    And a PaymentProcessedAvro payload is created with paymentId <paymentId> orderId <orderId2> amount <amount2> paymentMethod <paymentMethod>
    And a CloudEventAvro key is created for PaymentProcessedAvro with eventTestId <eventTestId2>
    When the producer receives ProduceEvent command for OrderCreatedAvro
    And the producer receives ProduceEvent command for PaymentProcessedAvro
    Then the producer should respond with ProducedAck for both Avro events
    And both Avro events should be produced to the same Kafka topic <topic>
    And the OrderCreatedAvro event should use subject <orderSubject>
    And the PaymentProcessedAvro event should use subject <paymentSubject>
    And consuming both events should deserialize to their respective SpecificRecord types

    Examples:
      | testId                               | topic                  | orderId1        | customerId      | amount1 | paymentId       | orderId2        | amount2 | paymentMethod | eventTestId1  | eventTestId2  | orderSubject                          | paymentSubject                             |
      | "test-event-democratization-avro-001" | "domain-events-avro"   | "order-11111"   | "customer-001"  | 150.00  | "payment-22222" | "order-11111"   | 150.00  | "credit_card" | "event-001"   | "event-002"   | "domain-events-avro-OrderCreatedAvro" | "domain-events-avro-PaymentProcessedAvro" |

  @Critical @SchemaValidation @Avro
  Scenario Outline: Avro schema validation enforces required fields
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And an OrderCreatedAvro payload is created with missing required field <missingField>
    When the producer receives ProduceEvent command for OrderCreatedAvro
    Then the producer should respond with ProducedNack
    And the error should indicate Avro schema validation failure
    And the error message should mention the missing field <missingField>

    Examples:
      | testId                               | topic            | missingField   |
      | "test-schema-validation-avro-001"    | "orders-avro"    | "customerId"   |

  @Critical @SchemaEvolution @Avro
  Scenario Outline: Independent Avro schema evolution with backward compatibility
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And the Schema Registry has OrderCreatedAvro schema version <orderVersion> registered
    And the Schema Registry has PaymentProcessedAvro schema version <paymentVersion> registered
    And an OrderCreatedAvro payload is created matching version <orderVersion>
    And a PaymentProcessedAvro payload is created matching version <paymentVersion>
    When the producer receives ProduceEvent for both Avro event types
    Then both Avro events should serialize with their respective schema versions
    And the OrderCreatedAvro event should use schema ID for version <orderVersion>
    And the PaymentProcessedAvro event should use schema ID for version <paymentVersion>
    And Avro schema evolution should be independent for each type

    Examples:
      | testId                               | topic                  | orderVersion | paymentVersion |
      | "test-schema-evolution-avro-001"     | "domain-events-avro"   | 1            | 1              |

  @Edge @Avro @SubjectNaming
  Scenario Outline: TopicRecordNameStrategy subject naming for Avro without key-value suffix
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And an OrderCreatedAvro payload is created
    And a CloudEventAvro key is created for OrderCreatedAvro with eventTestId "subject-naming-avro-test"
    When the producer receives ProduceEvent command for OrderCreatedAvro
    Then the event should be serialized with subject <expectedSubject>
    And the subject should NOT contain "-key" or "-value" suffix
    And the subject pattern should match TopicRecordNameStrategy format

    Examples:
      | testId                               | topic                  | expectedSubject                       |
      | "test-subject-naming-avro-001"       | "orders-avro"          | "orders-avro-OrderCreatedAvro"        |
      | "test-subject-naming-avro-002"       | "domain-events-avro"   | "domain-events-avro-OrderCreatedAvro" |

  @Edge @Avro @CloudEventKey
  Scenario Outline: CloudEventAvro keys serialize with TopicRecordNameStrategy
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a CloudEventAvro key is created with eventTestId <eventTestId>
    And an OrderCreatedAvro payload is created
    When the producer receives ProduceEvent command for OrderCreatedAvro
    Then the CloudEventAvro key should be serialized with subject <keySubject>
    And the CloudEventAvro key should contain eventTestId <eventTestId>
    And the key subject should be consistent for all Avro event types on topic <topic>

    Examples:
      | testId                               | topic                        | eventTestId       | keySubject                           |
      | "test-cloudevent-key-avro-001"       | "cloudevent-key-avro-test"   | "event-key-001"   | "cloudevent-key-avro-test-CloudEvent" |

  @Edge @Avro @WireFormat
  Scenario Outline: Avro wire format verification with binary payload
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And an OrderCreatedAvro payload is created with orderId <orderId> customerId <customerId> amount <amount>
    And a CloudEventAvro key is created for OrderCreatedAvro with eventTestId <eventTestId>
    When the producer receives ProduceEvent command for OrderCreatedAvro
    Then the Avro serialized bytes should start with magic byte 0x00
    And Avro bytes 1-4 should contain a valid schema ID
    And the payload should be Avro binary encoded without field names
    And the Avro payload size should be smaller than equivalent JSON encoding

    Examples:
      | testId                               | topic            | orderId         | customerId      | amount | eventTestId   |
      | "test-wire-format-avro-001"          | "orders-avro"    | "order-12345"   | "customer-001"  | 100.00 | "wire-test-001" |

  @Performance @Avro
  Scenario Outline: Produce multiple Avro polymorphic events with optimal performance
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And <eventCount> OrderCreatedAvro events are created
    And <eventCount> PaymentProcessedAvro events are created
    When the producer receives ProduceEvent for all <totalCount> Avro events
    Then all events should be produced successfully
    And the total serialization time should be less than <maxLatencyMs> milliseconds
    And Avro binary encoding should outperform JSON encoding by at least 30 percent

    Examples:
      | testId                               | topic                  | eventCount | totalCount | maxLatencyMs |
      | "test-performance-avro-001"          | "domain-events-avro"   | 100        | 200        | 1500         |
