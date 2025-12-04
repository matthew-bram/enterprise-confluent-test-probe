@ComponentTest
Feature: CucumberExecutionActor - Test Execution Management
  As a CucumberExecutionActor
  I need to execute Cucumber test scenarios
  So that event-driven tests can be run and results can be collected

  ⚠️  SECURITY: This actor handles sensitive KafkaSecurityDirective data
  All scenarios must verify that credentials are NEVER logged

  Background:
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent

  @Critical @Initialization @Security
  Scenario: Initialize with directives and send ChildGoodToGo
    Given a CucumberExecutionActor is spawned for test "test-001"
    And a BlockStorageDirective with stub feature file is available
    And a list of KafkaSecurityDirective is available
    When the CucumberExecutionActor parent sends "Initialize" with blockStorageDirective and securityDirectives
    Then the CucumberExecutionActor should send "ChildGoodToGo" to parent

  @Critical @TestExecution
  Scenario: StartTest executes scenarios and sends TestComplete
    Given a CucumberExecutionActor is spawned for test "test-002"
    When the CucumberExecutionActor has been initialized
    Then the CucumberExecutionActor should send "ChildGoodToGo" to parent
    When the CucumberExecutionActor parent sends "StartTest"
    Then the CucumberExecutionActor should send "TestComplete" to parent with TestExecutionResult
    And the TestExecutionResult should have field "testId" not null
    And the TestExecutionResult should have field "passed" not null
    And the TestExecutionResult should have field "scenarioCount" greater than 0

  @Critical @Lifecycle
  Scenario: Stop command triggers clean shutdown
    Given a CucumberExecutionActor is spawned for test "test-003"
    And the CucumberExecutionActor has been initialized
    When the CucumberExecutionActor parent sends "Stop"
    Then the CucumberExecutionActor should stop cleanly
    And the CucumberExecutionActor should log "Stopping CucumberExecutionActor" message

  @Sequential @HappyPath
  Scenario: Complete lifecycle - Initialize → StartTest → TestComplete → Stop
    Given a CucumberExecutionActor is spawned for test "test-004"
    And a BlockStorageDirective with stub feature file is available
    And a list of KafkaSecurityDirective is available
    When the CucumberExecutionActor parent sends "Initialize" with blockStorageDirective and securityDirectives
    Then the CucumberExecutionActor should send "ChildGoodToGo" to parent
    When the CucumberExecutionActor parent sends "StartTest"
    Then the CucumberExecutionActor should send "TestComplete" to parent
    When the CucumberExecutionActor parent sends "Stop"
    Then the CucumberExecutionActor should stop cleanly

  @Edge @Idempotency
  Scenario: Initialize called twice - idempotent behavior
    Given a CucumberExecutionActor is spawned for test "test-005"
    And the CucumberExecutionActor has been initialized
    When the CucumberExecutionActor parent sends "Initialize" with directives again
    Then the CucumberExecutionActor should send "ChildGoodToGo" to parent

  @Edge @OrderViolation @Ignore
  Scenario: StartTest before Initialize throws exception
    Given a CucumberExecutionActor is spawned for test "test-006"
    When the CucumberExecutionActor parent sends "StartTest" before initialization
    Then the CucumberExecutionActor should throw IllegalStateException
    And the exception message should contain "not initialized"
    # TODO: Implement exception monitoring infrastructure for actor supervision failures
    # Requires DeathWatch or supervision event monitoring to catch exceptions thrown in actors

  @Edge @EarlyShutdown
  Scenario: Stop before Initialize - clean shutdown
    Given a CucumberExecutionActor is spawned for test "test-007"
    When the CucumberExecutionActor parent sends "Stop" before initialization
    Then the CucumberExecutionActor should stop cleanly

  @Edge @PartialLifecycle
  Scenario: Stop after Initialize, before StartTest
    Given a CucumberExecutionActor is spawned for test "test-008"
    And the CucumberExecutionActor has been initialized
    When the CucumberExecutionActor parent sends "Stop" before StartTest
    Then the CucumberExecutionActor should stop cleanly

  @Exception @InitializationFailure
  Scenario: Initialize throws exception and bubbles to parent
    Given a CucumberExecutionActor is spawned for test "cucumber-test-009"
    And the Cucumber service is configured to throw exception on initialize
    And a BlockStorageDirective with stub feature file is available
    And a list of KafkaSecurityDirective is available
    When the CucumberExecutionActor parent sends "Initialize" with blockStorageDirective and securityDirectives
    Then the CucumberExecutionActor should throw ProbeException
    And the exception should bubble to the parent TestExecutionActor

  @Exception @ExecutionFailure
  Scenario: StartTest throws exception and bubbles to parent
    Given a CucumberExecutionActor is spawned for test "cucumber-test-010"
    And the CucumberExecutionActor has been initialized
    And the Cucumber service is configured to throw exception on execution
    When the CucumberExecutionActor parent sends "StartTest"
    Then the CucumberExecutionActor should throw ProbeException
    And the exception should bubble to the parent TestExecutionActor

  @Security @LoggingCompliance
  Scenario: Credentials are redacted in all log messages
    Given a CucumberExecutionActor is spawned for test "test-011"
    And logging is monitored
    And a BlockStorageDirective with stub feature file is available
    And a list of KafkaSecurityDirective is available
    When the CucumberExecutionActor parent sends "Initialize" with blockStorageDirective and securityDirectives

  @Performance @ResponseTime @Ignore
  Scenario: StartTest may run long without timeout errors
    Given a CucumberExecutionActor is spawned for test "test-012"
    When the CucumberExecutionActor has been initialized
    Then the CucumberExecutionActor should send "ChildGoodToGo" to parent
    When the CucumberExecutionActor parent sends "StartTest"
    And the response should be "TestComplete"
