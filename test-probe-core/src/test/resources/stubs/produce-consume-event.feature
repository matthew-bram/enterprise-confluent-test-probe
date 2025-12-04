Feature: Produce and Consume Events
  This feature validates end-to-end Kafka event flow with schema serialization.
  The inner Cucumber test that runs inside TestExecutionActor.

  Background:
    Given schemas are registered in Schema Registry
    And I have a test ID from the Cucumber context

  Scenario Outline: Produce and consume event with <schemaType>
    Given the inner test creates a CloudEvent key with eventTestId "<eventTestId>"
    And the inner test creates a <eventType> payload with version "<version>"
    When the inner test produces the event to topic "<topic>"
    Then the inner test fetches the consumed event using eventTestId "<eventTestId>"
    And the inner test verifies the consumed payload matches

    Examples:
      | eventType    | version | schemaType | topic               | eventTestId |
      | TestEvent    | v1      | JSON       | test-events-json    | evt-001     |
      | OrderEvent   | v1      | AVRO       | order-events-avro   | evt-002     |
      | PaymentEvent | v1      | PROTOBUF   | payment-events-proto| evt-003     |
