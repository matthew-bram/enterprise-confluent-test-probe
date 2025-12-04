@IntegrationTest
Feature: High Volume Multi-Topic Integration Test
  As a Test-Probe developer
  I want to verify the system can handle 30 events across 3 topics
  So that I can ensure the framework scales with distributed event processing

  Background: Integration Test Environment
    Given the integration test environment is initialized
    And Testcontainers are running with Kafka and Schema Registry
    And CloudEvent Avro key schema is registered for topic "order-events-avro"
    And OrderEvent Avro value schema is registered for topic "order-events-avro"
    And CloudEvent Protobuf key schema is registered for topic "payment-events-proto"
    And PaymentEvent Protobuf value schema is registered for topic "payment-events-proto"
    And CloudEvent key schema is registered for topic "test-events-json"
    And TestEvent value schema is registered for topic "test-events-json"
    And the ActorSystem is running with GuardianActor and QueueActor
    And ServiceInterfaceFunctions are available

  Scenario: Process 30 events across order-events, payment-events, and user-events
    Given I have a bucket "test-bucket" and prefix "high-volume-multi"
    And I want to run feature file "high-volume-multi-topic.feature"
    When I have initialized a test with bucket "test-bucket" and prefix "high-volume-multi"
    And I have started the test
    And the test completes successfully
    Then the test should transition to "Completed" state within 60 seconds
    And the test should have success status "true"
    And the test should have no errors
    And evidence files should exist in JIMFS
