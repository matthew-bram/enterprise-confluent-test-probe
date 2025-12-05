@ComponentTest
Feature: KafkaProducerActor - Kafka Message Production (Scaffolding)
  As a KafkaProducerActor
  I need to setup Kafka producer connections
  So that Cucumber tests can produce events to Kafka topics

  ⚠️  SECURITY: This actor handles sensitive KafkaSecurityDirective data
  All scenarios must verify that credentials are NEVER logged

  NOTE: StartTest is a stub (no-op) in scaffolding phase
  Full Kafka integration will be implemented in future phase

  @Critical @Initialization @Security
  Scenario: Initialize with directives and send ChildGoodToGo
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    Given a KafkaProducerActor is spawned for test "test-001"
    And a BlockStorageDirective is available
    And a list of KafkaSecurityDirective is available
    When the KafkaProducerActor parent sends "Initialize" with blockStorageDirective and securityDirectives
    Then the KafkaProducerActor should send "ChildGoodToGo" to parent

  @Critical @Scaffolding
  Scenario: StartTest is a no-op stub (does not throw exception)
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    Given a KafkaProducerActor is spawned for test "test-002"
    And the KafkaProducerActor has been initialized
    When the KafkaProducerActor parent sends "StartTest"

  @Critical @Lifecycle
  Scenario: Stop command triggers clean shutdown
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    Given a KafkaProducerActor is spawned for test "test-003"
    And the KafkaProducerActor has been initialized
    When the KafkaProducerActor parent sends "Stop"
    Then the KafkaProducerActor should stop cleanly
    And the KafkaProducerActor should log "Stopping KafkaProducerActor" message

  @Sequential @HappyPath
  Scenario: Complete lifecycle - Initialize → StartTest → Stop
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    Given a KafkaProducerActor is spawned for test "test-004"
    And a BlockStorageDirective is available
    And a list of KafkaSecurityDirective is available
    When the KafkaProducerActor parent sends "Initialize" with blockStorageDirective and securityDirectives
    Then the KafkaProducerActor should send "ChildGoodToGo" to parent
    When the KafkaProducerActor parent sends "StartTest"
    When the KafkaProducerActor parent sends "Stop"
    Then the KafkaProducerActor should stop cleanly

  @Edge @Idempotency
  Scenario: Initialize called twice - idempotent behavior
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    Given a KafkaProducerActor is spawned for test "test-005"
    And the KafkaProducerActor has been initialized
    When the KafkaProducerActor parent sends "Initialize" with directives again
    Then the KafkaProducerActor should send "ChildGoodToGo" to parent

  @Edge @EarlyShutdown
  Scenario: Stop before Initialize - clean shutdown
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    Given a KafkaProducerActor is spawned for test "test-006"
    When the KafkaProducerActor parent sends "Stop" before initialization
    Then the KafkaProducerActor should stop cleanly

  @Exception @InitializationFailure
  Scenario: Initialize throws exception and bubbles to parent
    Given the following config properties are set:
      | key                                           | value     |
      | test-probe.core.kafka.bootstrap-servers       | null      |
    And a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    Given a KafkaProducerActor is spawned for test "kafkaproducer-test-007"
    And the Kafka service is configured to throw exception on initialize
    And a BlockStorageDirective is available
    And a list of KafkaSecurityDirective is available
    When the KafkaProducerActor parent sends "Initialize" with blockStorageDirective and securityDirectives
    Then the KafkaProducerActor should throw ProbeException
    And the exception should bubble to the parent TestExecutionActor

  @Security @LoggingCompliance
  Scenario: Credentials are redacted in all log messages
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent
    Given a KafkaProducerActor is spawned for test "test-008"
    And logging is monitored
    And a BlockStorageDirective is available
    And a list of KafkaSecurityDirective is available
    When the KafkaProducerActor parent sends "Initialize" with blockStorageDirective and securityDirectives
