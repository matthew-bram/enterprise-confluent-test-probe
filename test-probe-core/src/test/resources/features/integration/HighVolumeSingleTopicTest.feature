@IntegrationTest
Feature: High Volume Single Topic Integration Test
  As a Test-Probe developer
  I want to verify the system can handle 20 events on a single topic
  So that I can ensure the framework scales with increased load

  Background: Integration Test Environment
    Given the integration test environment is initialized
    And Testcontainers are running with Kafka and Schema Registry
    And CloudEvent Avro key schema is registered for topic "order-events-avro"
    And OrderEvent Avro value schema is registered for topic "order-events-avro"
    And CloudEvent Protobuf key schema is registered for topic "payment-events-proto"
    And PaymentEvent Protobuf value schema is registered for topic "payment-events-proto"
    And the ActorSystem is running with GuardianActor and QueueActor
    And ServiceInterfaceFunctions are available

  Scenario: Process 20 events with OrderEvent and PaymentEvent
    Given I have a bucket "test-bucket" and prefix "high-volume-single"
    And I want to run feature file "high-volume-single-topic.feature"
    When I have initialized a test with bucket "test-bucket" and prefix "high-volume-single"
    And I have started the test
    And the test completes successfully
    Then the test should transition to "Completed" state within 60 seconds
    And the test should have success status "true"
    And the test should have no errors
    And evidence files should exist in JIMFS
