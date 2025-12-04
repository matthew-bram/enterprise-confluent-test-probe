Feature: High Volume Single Topic - Inner Test
  Produces and consumes 20 events on "order-events-avro" topic
  Tests 2 event types (OrderEvent, PaymentEvent) with different versions

  Background:
    Given schemas are registered in Schema Registry
    And I have a test ID from the Cucumber context

  Scenario Outline: Process event <eventTestId> with <eventType> <version>
    Given the inner test creates a CloudEvent key with eventTestId "<eventTestId>"
    And the inner test creates a <eventType> payload with version "<version>"
    When the inner test produces the event to topic "<topic>"
    Then the inner test fetches the consumed event using eventTestId "<eventTestId>"
    And the inner test verifies the consumed payload matches

    Examples:
      | eventTestId | eventType    | version | topic                 |
      | ord-001     | OrderEvent   | v1      | order-events-avro     |
      | ord-002     | OrderEvent   | v1      | order-events-avro     |
      | ord-003     | OrderEvent   | v1      | order-events-avro     |
      | ord-004     | OrderEvent   | v1      | order-events-avro     |
      | ord-005     | OrderEvent   | v1      | order-events-avro     |
      | ord-006     | OrderEvent   | v1      | order-events-avro     |
      | pay-001     | PaymentEvent | v1      | payment-events-proto  |
      | pay-002     | PaymentEvent | v1      | payment-events-proto  |
      | ord-007     | OrderEvent   | v1      | order-events-avro     |
      | ord-008     | OrderEvent   | v1      | order-events-avro     |
      | pay-003     | PaymentEvent | v1      | payment-events-proto  |
      | ord-009     | OrderEvent   | v1      | order-events-avro     |
      | ord-010     | OrderEvent   | v1      | order-events-avro     |
      | pay-004     | PaymentEvent | v1      | payment-events-proto  |
      | ord-011     | OrderEvent   | v1      | order-events-avro     |
      | ord-012     | OrderEvent   | v1      | order-events-avro     |
      | pay-005     | PaymentEvent | v1      | payment-events-proto  |
      | ord-013     | OrderEvent   | v1      | order-events-avro     |
      | pay-006     | PaymentEvent | v1      | payment-events-proto  |
      | ord-014     | OrderEvent   | v1      | order-events-avro     |
