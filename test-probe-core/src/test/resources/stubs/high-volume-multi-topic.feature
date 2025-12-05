Feature: High Volume Multi-Topic - Inner Test
  Produces and consumes 30 events across 3 topics
  Tests 3 event types with different schema formats across multiple topics

  Background:
    Given schemas are registered in Schema Registry
    And I have a test ID from the Cucumber context

  Scenario Outline: Process <eventType> event on <topic>
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
      | ord-007     | OrderEvent   | v1      | order-events-avro     |
      | ord-008     | OrderEvent   | v1      | order-events-avro     |
      | ord-009     | OrderEvent   | v1      | order-events-avro     |
      | ord-010     | OrderEvent   | v1      | order-events-avro     |
      | pay-001     | PaymentEvent | v1      | payment-events-proto  |
      | pay-002     | PaymentEvent | v1      | payment-events-proto  |
      | pay-003     | PaymentEvent | v1      | payment-events-proto  |
      | pay-004     | PaymentEvent | v1      | payment-events-proto  |
      | pay-005     | PaymentEvent | v1      | payment-events-proto  |
      | pay-006     | PaymentEvent | v1      | payment-events-proto  |
      | pay-007     | PaymentEvent | v1      | payment-events-proto  |
      | pay-008     | PaymentEvent | v1      | payment-events-proto  |
      | pay-009     | PaymentEvent | v1      | payment-events-proto  |
      | pay-010     | PaymentEvent | v1      | payment-events-proto  |
      | usr-001     | UserEvent    | v1      | user-events-json      |
      | usr-002     | UserEvent    | v1      | user-events-json      |
      | usr-003     | UserEvent    | v1      | user-events-json      |
      | usr-004     | UserEvent    | v1      | user-events-json      |
      | usr-005     | UserEvent    | v1      | user-events-json      |
      | usr-006     | UserEvent    | v1      | user-events-json      |
      | usr-007     | UserEvent    | v1      | user-events-json      |
      | usr-008     | UserEvent    | v1      | user-events-json      |
      | usr-009     | UserEvent    | v1      | user-events-json      |
      | usr-010     | UserEvent    | v1      | user-events-json      |
