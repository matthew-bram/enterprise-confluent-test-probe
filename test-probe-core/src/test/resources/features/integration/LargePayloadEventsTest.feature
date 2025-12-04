@IntegrationTest
Feature: Large Payload Events Integration Test
  As a Test-Probe developer
  I want to verify the system can handle large event payloads with nested structures
  So that I can ensure the framework handles realistic enterprise event sizes across all serdes types

  Background: Integration Test Environment
    Given the integration test environment is initialized
    And I want to run feature file "produce-consume-event.feature"
    And Testcontainers are running with Kafka and Schema Registry
    # JSON topic: test-events-json (CloudEvent JSON key + TestEvent JSON value)
    And CloudEvent key schema is registered for topic "test-events-json"
    And TestEvent value schema is registered for topic "test-events-json"
    # Avro topic: order-events-avro (CloudEvent Avro key + OrderEvent Avro value)
    And CloudEvent Avro key schema is registered for topic "order-events-avro"
    And OrderEvent Avro value schema is registered for topic "order-events-avro"
    # Protobuf topic: payment-events-proto (CloudEvent Protobuf key + PaymentEvent Protobuf value)
    And CloudEvent Protobuf key schema is registered for topic "payment-events-proto"
    And PaymentEvent Protobuf value schema is registered for topic "payment-events-proto"
    And the ActorSystem is running with GuardianActor and QueueActor
    And ServiceInterfaceFunctions are available

  Scenario: Process 20 large JSON events with ~10KB payloads and nested structures
    Given I have initialized a test with bucket "test-bucket" and prefix "large-json"
    When I have started the test
    And the test completes successfully
    Then the test should transition to "Completed" state within 60 seconds
    And the test should have success status "true"
    And the test should have no errors
    And evidence files should exist in JIMFS
    And the JSON report should show 3 scenario passed
    And the JSON report should show 0 scenarios failed

  Scenario: Process 20 large Avro events with binary compression and nested arrays
    Given I have initialized a test with bucket "test-bucket" and prefix "large-avro"
    When I have started the test
    And the test completes successfully
    Then the test should transition to "Completed" state within 60 seconds
    And the test should have success status "true"
    And the test should have no errors
    And evidence files should exist in JIMFS
    And the JSON report should show 3 scenario passed
    And the JSON report should show 0 scenarios failed

  Scenario: Process 20 large Protobuf events with repeated fields and message nesting
    Given I have initialized a test with bucket "test-bucket" and prefix "large-proto"
    When I have started the test
    And the test completes successfully
    Then the test should transition to "Completed" state within 60 seconds
    And the test should have success status "true"
    And the test should have no errors
    And evidence files should exist in JIMFS
    And the JSON report should show 3 scenario passed
    And the JSON report should show 0 scenarios failed
