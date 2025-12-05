@ComponentTest
Feature: KafkaConsumerActor - Kafka Message Consumption (Scaffolding)
  As a KafkaConsumerActor
  I need to setup Kafka consumer connections
  So that Cucumber tests can consume events from Kafka topics

  ⚠️  SECURITY: This actor handles sensitive KafkaSecurityDirective data
  All scenarios must verify that credentials are NEVER logged

  NOTE: StartTest is a stub (no-op) in scaffolding phase
  Full Kafka integration will be implemented in future phase

  @Critical @Initialization @Security
  Scenario: Initialize with directives and send ChildGoodToGo
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    And a KafkaConsumerActor is spawned for test "test-001"
    And a BlockStorageDirective is available
    And a list of KafkaSecurityDirective is available
    When the KafkaConsumerActor parent sends "Initialize" with blockStorageDirective and securityDirectives
    Then the KafkaConsumerActor should send "ChildGoodToGo" to parent

  @Critical @Scaffolding
  Scenario: StartTest is a no-op stub (does not throw exception)
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    And a KafkaConsumerActor is spawned for test "test-002"
    And the KafkaConsumerActor has been initialized
    When the KafkaConsumerActor parent sends "StartTest"

  @Critical @Lifecycle
  Scenario: Stop command triggers clean shutdown
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    And a KafkaConsumerActor is spawned for test "test-003"
    And the KafkaConsumerActor has been initialized
    When the KafkaConsumerActor parent sends "Stop"
    Then the KafkaConsumerActor should stop cleanly
    And the KafkaConsumerActor should log "Stopping KafkaConsumerActor" message

  @Sequential @HappyPath
  Scenario: Complete lifecycle - Initialize → StartTest → Stop
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    And a KafkaConsumerActor is spawned for test "test-004"
    And a BlockStorageDirective is available
    And a list of KafkaSecurityDirective is available
    When the KafkaConsumerActor parent sends "Initialize" with blockStorageDirective and securityDirectives
    Then the KafkaConsumerActor should send "ChildGoodToGo" to parent
    When the KafkaConsumerActor parent sends "StartTest"
    When the KafkaConsumerActor parent sends "Stop"
    Then the KafkaConsumerActor should stop cleanly

  @Edge @Idempotency
  Scenario: Initialize called twice - idempotent behavior
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    And a KafkaConsumerActor is spawned for test "test-005"
    And the KafkaConsumerActor has been initialized
    When the KafkaConsumerActor parent sends "Initialize" with directives again
    Then the KafkaConsumerActor should send "ChildGoodToGo" to parent

  @Edge @EarlyShutdown
  Scenario: Stop before Initialize - clean shutdown
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    And a KafkaConsumerActor is spawned for test "test-006"
    When the KafkaConsumerActor parent sends "Stop" before initialization
    Then the KafkaConsumerActor should stop cleanly

  @Exception @InitializationFailure
  Scenario: Initialize throws exception and bubbles to parent
    Given the following config properties are set:
      | key                                           | value     |
      | test-probe.core.kafka.bootstrap-servers       | null      |
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    And a KafkaConsumerActor is spawned for test "kafkaconsumer-test-007"
    And the Kafka service is configured to throw exception on initialize
    And a BlockStorageDirective is available
    And a list of KafkaSecurityDirective is available
    When the KafkaConsumerActor parent sends "Initialize" with blockStorageDirective and securityDirectives
    Then the KafkaConsumerActor should throw ProbeException
    And the exception should bubble to the parent TestExecutionActor

  @Security @LoggingCompliance
  Scenario: Credentials are redacted in all log messages
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    And a KafkaConsumerActor is spawned for test "test-008"
    And logging is monitored
    And a BlockStorageDirective is available
    And a list of KafkaSecurityDirective is available
    When the KafkaConsumerActor parent sends "Initialize" with blockStorageDirective and securityDirectives
