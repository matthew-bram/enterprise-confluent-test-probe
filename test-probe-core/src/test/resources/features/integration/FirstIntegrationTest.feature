@IntegrationTest
Feature: First End-to-End Integration Test
  As a Test-Probe developer
  I want to verify the complete system flow from initialization to Kafka event testing
  So that I can ensure the framework works end-to-end with real infrastructure

  Background: Integration Test Environment
    Given the integration test environment is initialized
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

  Scenario: Initialize test and receive test ID
    Given I have a bucket "test-bucket" and prefix "integration-test"
    When I call initializeTest with the bucket and prefix
    Then I should receive a valid test ID
    And the test should be in "Setup" state
    And the test should have no errors

  Scenario: Start test and verify Cucumber execution
    Given I have initialized a test with bucket "test-bucket" and prefix "integration-test"
    When I call startTest for the current test
    Then the test should transition to "Completed" state within 30 seconds
    And the test should have success status "true"
    And the test should have no errors

  Scenario: Verify JSON event flow end-to-end
    Given I have initialized a test with bucket "test-bucket" and prefix "integration-test"
    And I have started the test
    When the test completes successfully
    Then Cucumber should have executed the inner test
    And the inner test should have produced a TestEvent to topic "test-events-json"
    And the inner test should have consumed a TestEvent from topic "test-events-json"
    And the JSON scenario should have passed
    And evidence files should exist in JIMFS

  Scenario: Verify test evidence generation
    Given I have initialized a test with bucket "test-bucket" and prefix "integration-test"
    And I have started the test
    When the test completes successfully
    Then JIMFS should contain a Cucumber JSON report
    And the JSON report should show 3 scenario passed
    And the JSON report should show 0 scenarios failed
