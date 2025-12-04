@ComponentTest
Feature: Guardian Actor - Root Supervisor & Error Kernel
  As a GuardianActor (root supervisor)
  I need to initialize the actor system and supervise QueueActor
  So that the system is protected from fatal exceptions

  Background:
    Given a running actor system
    And a CoreConfig is available

  @Critical @Initialization
  Scenario: Initialize GuardianActor successfully and spawn QueueActor
    When the boot service sends "Initialize" to GuardianActor
    Then GuardianActor should respond "ActorSystemInitializationSuccess"
    And GuardianActor should spawn a QueueActor child
    And GuardianActor state should be "Initialized"

  @Critical @GetQueueActor
  Scenario: Retrieve QueueActor reference after initialization
    Given GuardianActor is initialized
    When the boot service sends "GetQueueActor" to GuardianActor
    Then GuardianActor should respond "QueueActorReference" with valid queueActorRef

  @Critical @Idempotency
  Scenario: Initialize GuardianActor multiple times (idempotent)
    Given GuardianActor is initialized
    When the boot service sends "Initialize" to GuardianActor a second time
    Then GuardianActor should respond "ActorSystemInitializationSuccess"
    And GuardianActor should not spawn a duplicate QueueActor
    And GuardianActor should log a warning about duplicate initialization

  @Edge @ErrorHandling
  Scenario: GetQueueActor called before Initialize
    When the boot service sends "GetQueueActor" to GuardianActor before initialization
    Then GuardianActor should respond "ActorSystemInitializationFailure" with error message
    And the error message should contain "not initialized"

# DEFERRED SCENARIOS - Require advanced testing infrastructure
# These scenarios test supervision and lifecycle features that will be
# implemented when production hardening is required.

#  @Edge @ErrorHandling @Pending
#  Scenario: Initialization fails due to configuration error
#    Given a ServiceConfig with invalid supervision settings
#    When the boot service sends "Initialize" to GuardianActor
#    Then GuardianActor should respond "ActorSystemInitializationFailure" with exception
#    And GuardianActor should log the configuration error
#
#  @Supervision @QueueActorRestart @Pending
#  Scenario: QueueActor throws exception and is restarted by supervision
#    Given GuardianActor is initialized with QueueActor running
#    When QueueActor throws a ProbeException
#    Then GuardianActor should log the exception
#    And GuardianActor should restart QueueActor automatically
#    And the system should remain stable
#
#  @Supervision @RestartLimitExceeded @Pending
#  Scenario: QueueActor exceeds restart limit (10 restarts per minute)
#    Given GuardianActor is initialized with QueueActor running
#    When QueueActor throws exceptions 11 times within 1 minute
#    Then GuardianActor should restart QueueActor 10 times
#    And GuardianActor should stop QueueActor on the 11th failure
#    And GuardianActor should log "restart limit exceeded"
#    And GuardianActor should enter degraded mode
#
#  @Supervision @ResumeOnValidationError @Pending
#  Scenario: QueueActor throws ValidationException and is resumed (not restarted)
#    Given GuardianActor is initialized with QueueActor running
#    When QueueActor throws a ValidationException
#    Then GuardianActor should log the validation error
#    And GuardianActor should resume QueueActor without restarting
#    And QueueActor should maintain its current state
#
#  @Lifecycle @GracefulShutdown @Pending
#  Scenario: System shutdown coordinates graceful termination
#    Given GuardianActor is initialized with QueueActor running
#    When the actor system receives a shutdown signal
#    Then GuardianActor should coordinate graceful termination
#    And QueueActor should be stopped cleanly
#    And GuardianActor should be stopped cleanly

  @Performance @ResponseTime
  Scenario Outline: Commands respond within expected timeout
    Given <precondition>
    When the boot service sends <command> to GuardianActor
    Then GuardianActor should respond within <timeout>
    And the response should be <expectedResponse>

    Examples:
      | precondition                | command            | timeout            | expectedResponse                     |
      | a running actor system      | "Initialize"       | 1 second           | "ActorSystemInitializationSuccess"   |
      | GuardianActor is initialized| "GetQueueActor"    | 100 milliseconds   | "QueueActorReference"                |
