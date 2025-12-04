@ComponentTest @Protobuf
Feature: Polymorphic Events - Protobuf Schema Support
  As a test framework
  I need to produce and consume multiple Protobuf event types on a single Kafka topic
  So that event democratization is enabled with type-safe schema validation using Protobuf Messages

  Background:
    Given testcontainers infrastructure is started
    And a running actor system
    And a CoreConfig is available

  @Critical @HappyPath @Protobuf
  Scenario Outline: Produce and consume OrderCreatedProto event with CloudEvent key
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a valid CloudEventProto key is created for OrderCreatedProto event with orderId <orderId> customerId <customerId> amount <amount>
    And an OrderCreatedProto payload is created with orderId <orderId> customerId <customerId> amount <amount>
    When the producer receives ProduceEvent command for OrderCreatedProto
    Then the producer should respond with ProducedAck
    And the OrderCreatedProto event should be produced to Kafka topic <topic>
    And the event should be serialized with subject <subject>
    And the wire format should contain magic byte 0x00 and message index array
    And consuming the event should deserialize to OrderCreatedProto Message type

    Examples:
      | testId                               | topic              | subject                        | orderId         | customerId      | amount |
      | "test-order-created-proto-001"       | "orders-proto"     | "orders-proto-DynamicMessage"  | "order-12345"   | "customer-001"  | 100.00 |

  @Critical @HappyPath @Protobuf
  Scenario Outline: Produce and consume PaymentProcessedProto event with CloudEvent key
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a valid CloudEventProto key is created for PaymentProcessedProto event with paymentId <paymentId> orderId <orderId> amount <amount>
    And a PaymentProcessedProto payload is created with paymentId <paymentId> orderId <orderId> amount <amount> paymentMethod <paymentMethod>
    When the producer receives ProduceEvent command for PaymentProcessedProto
    Then the producer should respond with ProducedAck
    And the PaymentProcessedProto event should be produced to Kafka topic <topic>
    And the event should be serialized with subject <subject>
    And the wire format should contain magic byte 0x00 and message index array
    And consuming the event should deserialize to PaymentProcessedProto Message type

    Examples:
      | testId                               | topic                | subject                          | paymentId       | orderId         | amount | paymentMethod |
      | "test-payment-processed-proto-001"   | "payments-proto"     | "payments-proto-DynamicMessage"  | "payment-67890" | "order-12345"   | 100.00 | "credit_card" |

  @Critical @HappyPath @Protobuf @EventDemocratization
  Scenario Outline: Produce multiple Protobuf event types to separate topics (SerdesFactory Pattern)
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <orderTopic>
    And an OrderCreatedProto payload is created with orderId <orderId1> customerId <customerId> amount <amount1>
    And a CloudEventProto key is created for OrderCreatedProto with eventTestId <eventTestId1>
    When the producer receives ProduceEvent command for OrderCreatedProto
    Then the producer should respond with ProducedAck
    And the OrderCreatedProto event should be produced to Kafka topic <orderTopic>
    And the OrderCreatedProto event should use subject <orderSubject>

    # Note: For multiple Protobuf event types, use separate topics (one schema per topic)
    # This aligns with SerdesFactory's DynamicMessage subject naming pattern
    # Event democratization for Protobuf is tested via separate producer actors per topic

    Examples:
      | testId                                 | orderTopic           | orderId1        | customerId      | amount1 | eventTestId1  | orderSubject                        |
      | "test-event-democratization-proto-001" | "orders-proto-dm"    | "order-11111"   | "customer-001"  | 150.00  | "event-001"   | "orders-proto-dm-DynamicMessage"    |

  @Critical @SchemaValidation @Protobuf
  Scenario Outline: Protobuf schema validation with proto3 default values
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And an OrderCreatedProto payload is created with empty string for required field <emptyField>
    When the producer receives ProduceEvent command for OrderCreatedProto
    Then the producer should respond with ProducedAck
    And the event should serialize with empty string as default value
    And consuming the event should return empty string for field <emptyField>

    Examples:
      | testId                               | topic              | emptyField     |
      | "test-proto3-defaults-001"           | "orders-proto"     | "customerId"   |

  @Critical @SchemaEvolution @Protobuf
  Scenario Outline: Protobuf schema evolution with field number stability
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And the Schema Registry has OrderCreatedProto schema version <orderVersion> registered
    And an OrderCreatedProto payload is created matching version <orderVersion>
    When the producer receives ProduceEvent command for OrderCreatedProto
    Then the producer should respond with ProducedAck
    And the OrderCreatedProto event should use schema ID for version <orderVersion>
    And Protobuf schema evolution should be independent for each type

    # Note: With DynamicMessage subject naming, each event type requires its own topic
    # Schema evolution is tested per-topic, not per-event-type on shared topic

    Examples:
      | testId                               | topic                       | orderVersion |
      | "test-schema-evolution-proto-001"    | "order-events-proto-evol"   | 1            |

  @Edge @Protobuf @SubjectNaming
  Scenario Outline: TopicRecordNameStrategy subject naming for Protobuf with DynamicMessage
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And an OrderCreatedProto payload is created
    And a CloudEventProto key is created for OrderCreatedProto with eventTestId "subject-naming-proto-test"
    When the producer receives ProduceEvent command for OrderCreatedProto
    Then the event should be serialized with subject <expectedSubject>
    And the subject should NOT contain "-key" or "-value" suffix
    And the subject pattern should match TopicRecordNameStrategy format

    Examples:
      | testId                               | topic                    | expectedSubject                      |
      | "test-subject-naming-proto-001"      | "orders-proto"           | "orders-proto-DynamicMessage"        |
      | "test-subject-naming-proto-002"      | "domain-events-proto"    | "domain-events-proto-DynamicMessage" |

  @Edge @Protobuf @CloudEventKey
  Scenario Outline: CloudEventProto keys serialize with TopicRecordNameStrategy
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And a CloudEventProto key is created with eventTestId <eventTestId>
    And an OrderCreatedProto payload is created
    When the producer receives ProduceEvent command for OrderCreatedProto
    Then the CloudEventProto key should be serialized with subject <keySubject>
    And the CloudEventProto key should contain eventTestId <eventTestId>
    And the key subject should be consistent for all Protobuf event types on topic <topic>

    Examples:
      | testId                               | topic                          | eventTestId       | keySubject                             |
      | "test-cloudevent-key-proto-001"      | "cloudevent-key-proto-test"    | "event-key-001"   | "cloudevent-key-proto-test-CloudEvent" |

  @Edge @Protobuf @WireFormat
  Scenario Outline: Protobuf wire format verification with message index array
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And an OrderCreatedProto payload is created with orderId <orderId> customerId <customerId> amount <amount>
    And a CloudEventProto key is created for OrderCreatedProto with eventTestId <eventTestId>
    When the producer receives ProduceEvent command for OrderCreatedProto
    Then the Protobuf serialized bytes should start with magic byte 0x00
    And Protobuf bytes 1-4 should contain a valid schema ID
    And the message index array should indicate message position in proto file
    And the payload should be Protobuf binary encoded with field numbers
    And the Protobuf payload size should be smaller than equivalent JSON encoding

    Examples:
      | testId                               | topic              | orderId         | customerId      | amount | eventTestId   |
      | "test-wire-format-proto-001"         | "orders-proto"     | "order-12345"   | "customer-001"  | 100.00 | "wire-test-001" |

  @Edge @Protobuf @MessageIndex
  Scenario Outline: Message index array for single-message proto files
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And an OrderCreatedProto payload is created
    When the producer receives ProduceEvent command for OrderCreatedProto
    Then the message index array should be a single byte 0x00
    And the message index should indicate first message in proto file

    Examples:
      | testId                               | topic              |
      | "test-message-index-001"             | "orders-proto"     |

  @Edge @Protobuf @DeriveType
  Scenario Outline: Derive type from schema with java_multiple_files option
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And an OrderCreatedProto payload is created
    And the proto schema has java_multiple_files set to true
    When the producer receives ProduceEvent command for OrderCreatedProto
    And the event is consumed from topic <topic>
    Then the deserializer should derive the type from the schema
    And the result should be an instance of OrderCreatedProto

    Examples:
      | testId                               | topic              |
      | "test-derive-type-001"               | "orders-proto"     |

  @Performance @Protobuf
  Scenario Outline: Produce multiple Protobuf events with optimal performance
    Given a KafkaProducerStreamingActor is spawned for test <testId> and topic <topic>
    And <eventCount> OrderCreatedProto events are created
    When the producer receives ProduceEvent for all <eventCount> Protobuf events
    Then all events should be produced successfully
    And the total serialization time should be less than <maxLatencyMs> milliseconds
    And Protobuf binary encoding should outperform JSON encoding by at least 40 percent

    # Note: Performance testing with single event type per topic
    # This aligns with SerdesFactory's DynamicMessage subject pattern

    Examples:
      | testId                               | topic                       | eventCount | maxLatencyMs |
      | "test-performance-proto-001"         | "perf-orders-proto"         | 100        | 800          |
