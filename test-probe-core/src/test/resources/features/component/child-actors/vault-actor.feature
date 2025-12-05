@ComponentTest
Feature: VaultActor - Security Credentials Management
  As a VaultActor
  I need to fetch security credentials from Vault
  So that Kafka producers/consumers and Cucumber tests can authenticate

  ⚠️  SECURITY: This actor handles sensitive KafkaSecurityDirective data
  All scenarios must verify that credentials are NEVER logged

  Background:
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent

  @Critical @Initialization @Security
  Scenario: Initialize fetches KafkaSecurityDirective and sends to parent
    Given a VaultActor is spawned for test "test-001"
    And a BlockStorageDirective is available with bucket "security-test-bucket"
    When the parent sends "Initialize" with blockStorageDirective
    Then the VaultActor should send "SecurityFetched" to parent with list of KafkaSecurityDirective
    And the VaultActor should send "ChildGoodToGo" to parent

  @Critical @Security @Validation
  Scenario: SecurityFetched contains valid KafkaSecurityDirective list
    Given a VaultActor is spawned for test "test-002"
    And a BlockStorageDirective is available
    When the parent sends "Initialize" with blockStorageDirective
    Then the VaultActor should send "SecurityFetched" to parent
    And the KafkaSecurityDirective list should not be empty
    And each KafkaSecurityDirective should have field "topic" not null
    And each KafkaSecurityDirective should have field "role" not null
    And each KafkaSecurityDirective should have jaasConfig populated

  @Critical @Lifecycle
  Scenario: Stop command triggers clean shutdown
    Given a VaultActor is spawned for test "test-003"
    And the VaultActor has been initialized
    When the VaultActor parent sends "Stop"
    Then the VaultActor should stop cleanly
    And the VaultActor should log "Stopping VaultActor" message

  @Sequential @HappyPath
  Scenario: Complete lifecycle - Initialize → Stop
    Given a VaultActor is spawned for test "test-004"
    And a BlockStorageDirective is available with bucket "lifecycle-bucket"
    When the parent sends "Initialize" with blockStorageDirective
    Then the VaultActor should send "SecurityFetched" to parent
    And the VaultActor should send "ChildGoodToGo" to parent
    When the VaultActor parent sends "Stop"
    Then the VaultActor should stop cleanly

  @Edge @Idempotency
  Scenario: Initialize called twice - idempotent behavior
    Given a VaultActor is spawned for test "test-005"
    And the VaultActor has been initialized
    When the parent sends "Initialize" with blockStorageDirective again
    Then the VaultActor should send "SecurityFetched" to parent
    And the VaultActor should send "ChildGoodToGo" to parent

  @Edge @EarlyShutdown
  Scenario: Stop before Initialize - clean shutdown
    Given a VaultActor is spawned for test "test-006"
    When the VaultActor parent sends "Stop" before initialization
    Then the VaultActor should stop cleanly

  @Exception @VaultFailure
  Scenario: Initialize throws exception and bubbles to parent
    Given the Vault service is configured to throw exception on fetch
    And a VaultActor is spawned for test "vaultactor-test-007"
    And a BlockStorageDirective is available
    When the parent sends "Initialize" with blockStorageDirective
    Then the VaultActor should throw ProbeException
    And the exception should bubble to the parent TestExecutionActor

  @Security @LoggingCompliance
  Scenario: Credentials are redacted in all log messages
    Given a VaultActor is spawned for test "test-008"
    And logging is monitored
    And a BlockStorageDirective is available
    When the parent sends "Initialize" with blockStorageDirective

  @Security @ErrorHandling
  Scenario: Exception messages do not leak credentials
    Given a VaultActor is spawned for test "test-009"
    And the Vault service is configured to throw exception with sensitive data
    And a BlockStorageDirective is available
    When the parent sends "Initialize" with blockStorageDirective
    Then the exception message should be redacted
    And the exception message should contain testId for debugging

  @Performance @ResponseTime
  Scenario: Initialize responds within expected timeout
    Given a VaultActor is spawned for test "test-010"
    And a BlockStorageDirective is available
    When the parent sends "Initialize" with blockStorageDirective
    And the VaultActor response should include "SecurityFetched"
