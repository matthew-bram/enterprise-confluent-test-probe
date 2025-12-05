@ComponentTest
Feature: Queue Actor - FIFO Test Management
  As a Queue Actor
  I need to manage test lifecycle and ensure single-threaded FIFO execution
  So that tests are executed fairly in order with proper resource management

  Background:
    Given a running actor system
    And a CoreConfig is available
    And a QueueActor is spawned

  @Critical @TestInitialization
  Scenario Outline: Generate UUID and spawn TestExecutionActor on InitializeTestRequest
    When a service sends "InitializeTestRequest" to QueueActor
    Then the QueueActor should generate a new UUID for the test
    And the QueueActor should spawn a TestExecutionActor with name "test-execution-<testId>"
    And the TestExecutionActor should be added to the test registry with state "Setup"
    And the QueueActor should forward "InInitializeTestRequest" to the TestExecutionActor with replyTo
    Examples:
      | scenario |
      | test1    |
      | test2    |

  @Critical @MessageRouting
  Scenario Outline: Forward StartTestRequest to TestExecutionActor with replyTo
    Given a test with id "<testId>" exists in the registry
    When a service sends "StartTestRequest" with testId "<testId>", bucket "<bucket>", testType "<testType>" to QueueActor
    Then the QueueActor should update the test entry with bucket "<bucket>"
    And the QueueActor should update the test entry with testType "<testType>"
    And the QueueActor should update the test entry with startRequestTime
    And the test should be added to the pending queue
    And the QueueActor should forward "InStartTestRequest" to the TestExecutionActor with replyTo
    Examples:
      | testId                               | bucket        | testType    |
      | 11111111-1111-1111-1111-111111111111 | test-bucket-1 | functional  |
      | 22222222-2222-2222-2222-222222222222 | test-bucket-2 | performance |
      | 33333333-3333-3333-3333-333333333333 | test-bucket-3 | regression  |

  @Critical @MessageRouting
  Scenario Outline: Forward TestStatusRequest to TestExecutionActor with replyTo
    Given a test with id "<testId>" exists in the registry
    When a service sends "TestStatusRequest" with testId "<testId>" to QueueActor
    Then the QueueActor should forward "GetStatus" to the TestExecutionActor with replyTo
    Examples:
      | testId                               |
      | 44444444-4444-4444-4444-444444444444 |
      | 55555555-5555-5555-5555-555555555555 |

  @Critical @MessageRouting
  Scenario Outline: Forward CancelRequest to TestExecutionActor with replyTo
    Given a test with id "<testId>" exists in the registry
    When a service sends "CancelRequest" with testId "<testId>" to QueueActor
    Then the QueueActor should forward "InCancelRequest" to the TestExecutionActor with replyTo
    Examples:
      | testId                               |
      | 66666666-6666-6666-6666-666666666666 |
      | 77777777-7777-7777-7777-777777777777 |

  @Critical @QueueStatus
  Scenario Outline: Handle QueueStatusRequest directly
    Given the queue has <totalTests> tests
    And <setupCount> tests are in Setup state
    And <loadingCount> tests are in Loading state
    And <loadedCount> tests are in Loaded state
    And <testingCount> tests are in Testing state
    And <completedCount> tests are in Completed state
    And <exceptionCount> tests are in Exception state
    When a service sends "QueueStatusRequest" to QueueActor
    Then the service should receive "QueueStatusResponse" with totalTests <totalTests>, setupCount <setupCount>, loadingCount <loadingCount>, loadedCount <loadedCount>, testingCount <testingCount>, completedCount <completedCount>, exceptionCount <exceptionCount>
    Examples:
      | totalTests | setupCount | loadingCount | loadedCount | testingCount | completedCount | exceptionCount |
      | 0          | 0          | 0            | 0           | 0            | 0              | 0              |
      | 5          | 1          | 1            | 2           | 1            | 0              | 0              |
      | 10         | 2          | 2            | 3           | 1            | 1              | 1              |

  @Critical @FSMCommunication
  Scenario Outline: Update test state on TestInitialized
    Given a test with id "<testId>" exists in the registry
    When the TestExecutionActor sends "TestInitialized" with testId "<testId>"
    Then the QueueActor should update the test state to "Setup"
    Examples:
      | testId                               |
      | 88888888-8888-8888-8888-888888888888 |
      | 99999999-9999-9999-9999-999999999999 |

  @Critical @FSMCommunication
  Scenario Outline: Update test state on TestLoading
    Given a test with id "<testId>" exists in the registry
    When the TestExecutionActor sends "TestLoading" with testId "<testId>"
    Then the QueueActor should update the test state to "Loading"
    Examples:
      | testId                               |
      | aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa |
      | bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb |

  @Critical @FSMCommunication @FIFOOrdering
  Scenario Outline: Add test to loadedTests on TestLoaded and attempt FIFO processing
    Given a test with id "<testId>" exists in the registry with state "Loading"
    And the test is in the pending queue
    And there is no currently executing test
    When the TestExecutionActor sends "TestLoaded" with testId "<testId>"
    Then the QueueActor should update the test state to "Loaded"
    And the test should be added to loadedTests
    And the QueueActor should process the queue
    And the QueueActor should send "StartTesting" to the TestExecutionActor for testId "<testId>"
    And the test should be removed from the pending queue
    And the test should be removed from loadedTests
    And the test should be set as currentTest
    Examples:
      | testId                               |
      | cccccccc-cccc-cccc-cccc-cccccccccccc |
      | dddddddd-dddd-dddd-dddd-dddddddddddd |

  @Critical @FSMCommunication
  Scenario Outline: Update test state on TestStarted
    Given a test with id "<testId>" exists in the registry
    When the TestExecutionActor sends "TestStarted" with testId "<testId>"
    Then the QueueActor should update the test state to "Testing"
    Examples:
      | testId                               |
      | eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee |
      | ffffffff-ffff-ffff-ffff-ffffffffffff |

  @Critical @FSMCommunication @QueueProcessing
  Scenario Outline: Clear currentTest on TestCompleted and start next test
    Given a test with id "<currentTestId>" is currently executing
    And a test with id "<nextTestId>" is in Loaded state in the pending queue
    When the TestExecutionActor sends "TestCompleted" with testId "<currentTestId>"
    Then the QueueActor should update the test state to "Completed"
    And the currentTest should be cleared
    And the QueueActor should process the queue
    And the QueueActor should send "StartTesting" to the TestExecutionActor for testId "<nextTestId>"
    Examples:
      | currentTestId                        | nextTestId                           |
      | 11111111-1111-1111-1111-111111111111 | 22222222-2222-2222-2222-222222222222 |
      | 33333333-3333-3333-3333-333333333333 | 44444444-4444-4444-4444-444444444444 |

  @Critical @FSMCommunication @QueueProcessing
  Scenario Outline: Clear currentTest on TestException and start next test
    Given a test with id "<currentTestId>" is currently executing
    And a test with id "<nextTestId>" is in Loaded state in the pending queue
    When the TestExecutionActor sends "TestException" with testId "<currentTestId>" and exception "<exception>"
    Then the QueueActor should update the test state to "Exception"
    And the currentTest should be cleared
    And the QueueActor should process the queue
    And the QueueActor should send "StartTesting" to the TestExecutionActor for testId "<nextTestId>"
    Examples:
      | currentTestId                        | nextTestId                           | exception                  |
      | 55555555-5555-5555-5555-555555555555 | 66666666-6666-6666-6666-666666666666 | BlockStorageException      |
      | 77777777-7777-7777-7777-777777777777 | 88888888-8888-8888-8888-888888888888 | CucumberException          |

  @Critical @Cleanup @QueueProcessing
  Scenario Outline: Remove test from all structures on TestStopping and start next test
    Given a test with id "<stoppingTestId>" is currently executing
    And a test with id "<nextTestId>" is in Loaded state in the pending queue
    When the TestExecutionActor sends "TestStopping" with testId "<stoppingTestId>"
    Then the test should be removed from the test registry
    And the test should be removed from the pending queue
    And the test should be removed from loadedTests
    And the currentTest should be cleared
    And the test should be added to stoppedTests
    And the QueueActor should process the queue
    And the QueueActor should send "StartTesting" to the TestExecutionActor for testId "<nextTestId>"
    Examples:
      | stoppingTestId                       | nextTestId                           |
      | 99999999-9999-9999-9999-999999999999 | aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa |
      | bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb | cccccccc-cccc-cccc-cccc-cccccccccccc |
#
#  @Critical @FIFOOrdering @Pending
#  Scenario: Enforce FIFO ordering for test execution
#    Given 3 tests exist with ids:
#      | testId                               | startRequestTime |
#      | 11111111-1111-1111-1111-111111111111 | 2025-10-13T10:00:00Z |
#      | 22222222-2222-2222-2222-222222222222 | 2025-10-13T10:00:01Z |
#      | 33333333-3333-3333-3333-333333333333 | 2025-10-13T10:00:02Z |
#    And all 3 tests are in Loaded state in the pending queue
#    And there is no currently executing test
#    When the QueueActor processes the queue
#    Then the QueueActor should send "StartTesting" to test "11111111-1111-1111-1111-111111111111" (oldest)
#    And test "11111111-1111-1111-1111-111111111111" should be set as currentTest
#    When test "11111111-1111-1111-1111-111111111111" completes
#    And the QueueActor processes the queue
#    Then the QueueActor should send "StartTesting" to test "22222222-2222-2222-2222-222222222222" (second oldest)
#    When test "22222222-2222-2222-2222-222222222222" completes
#    And the QueueActor processes the queue
#    Then the QueueActor should send "StartTesting" to test "33333333-3333-3333-3333-333333333333" (youngest)
#
#  @Critical @SingleThreadedExecution @Pending
#  Scenario: Enforce single test execution (max 1 in Testing state)
#    Given a test with id "44444444-4444-4444-4444-444444444444" is currently executing
#    And a test with id "55555555-5555-5555-5555-555555555555" is in Loaded state in the pending queue
#    When the QueueActor processes the queue
#    Then the QueueActor should not send "StartTesting" to test "55555555-5555-5555-5555-555555555555"
#    And the currentTest should remain "44444444-4444-4444-4444-444444444444"
#    And test "55555555-5555-5555-5555-555555555555" should remain in loadedTests
#
  @Edge @ErrorHandling
  Scenario Outline: Handle StartTestRequest for unknown test ID
    When a service sends "StartTestRequest" with unknown testId "<testId>", bucket "<bucket>", testType "<testType>" to QueueActor
    Then the QueueActor should log a warning
    And no message should be forwarded to any TestExecutionActor
    Examples:
      | testId                               | bucket      | testType   |
      | 00000000-0000-0000-0000-000000000000 | test-bucket | functional |
      | ffffffff-ffff-ffff-ffff-ffffffffffff | test-bucket | regression |

  @Edge @ErrorHandling
  Scenario Outline: Handle TestStatusRequest for unknown test ID
    When a service sends "TestStatusRequest" with unknown testId "<testId>" to QueueActor
    Then the QueueActor should log a warning
    And no message should be forwarded to any TestExecutionActor
    Examples:
      | testId                               |
      | 00000000-0000-0000-0000-000000000000 |
      | ffffffff-ffff-ffff-ffff-ffffffffffff |

  @Edge @ErrorHandling
  Scenario Outline: Handle CancelRequest for unknown test ID
    When a service sends "CancelRequest" with unknown testId "<testId>" to QueueActor
    Then the QueueActor should log a warning
    And no message should be forwarded to any TestExecutionActor
    Examples:
      | testId                               |
      | 00000000-0000-0000-0000-000000000000 |
      | ffffffff-ffff-ffff-ffff-ffffffffffff |
#
  @Edge @ErrorHandling
  Scenario Outline: Handle TestCompleted for non-current test
    Given a test with id "66666666-6666-6666-6666-666666666666" is currently executing
    And a test with id "<nonCurrentTestId>" exists in the registry with state "Testing"
    When the TestExecutionActor sends "TestCompleted" with testId "<nonCurrentTestId>"
    Then the QueueActor should update the test state to "Completed"
    But the currentTest should remain "66666666-6666-6666-6666-666666666666"
    And the QueueActor should not process the queue
    Examples:
      | nonCurrentTestId                     |
      | 77777777-7777-7777-7777-777777777777 |
      | 88888888-8888-8888-8888-888888888888 |
#
#  Pending Further Review
#  @Edge @ActorTermination @Pending
#  Scenario Outline: Handle unexpected TestExecutionActor termination
#    Given a test with id "<testId>" is currently executing
#    And a test with id "<nextTestId>" is in Loaded state in the pending queue
#    When the TestExecutionActor for test "<testId>" terminates unexpectedly
#    Then the QueueActor should log an error
#    And the test state should be updated to "Exception"
#    And the currentTest should be cleared
#    And the QueueActor should process the queue
#    And the QueueActor should send "StartTesting" to the TestExecutionActor for testId "<nextTestId>"
#    Examples:
#      | testId                               | nextTestId                           |
#      | 99999999-9999-9999-9999-999999999999 | aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa |
#
#  @Edge @MessageIgnoring @Pending
#  Scenario Outline: Ignore TestStatusResponse (should go directly to service)
#    When the QueueActor receives "TestStatusResponse" with testId "<testId>"
#    Then the QueueActor should log a warning
#    And no state changes should occur
#    Examples:
#      | testId                               |
#      | bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb |
#      | cccccccc-cccc-cccc-cccc-cccccccccccc |
#
  @Edge @EmptyQueue
  Scenario: Process empty queue (no tests ready)
    Given the queue has no tests in Loaded state
    And there is no currently executing test
    When the QueueActor processes the queue
    Then no "StartTesting" message should be sent
    And the currentTest should remain None

  @Performance @HelperMethods
  Scenario: Count tests by state efficiently
    Given the queue has 10 tests with the following states:
      | state     | count |
      | Setup     | 2     |
      | Loading   | 2     |
      | Loaded    | 3     |
      | Testing   | 1     |
      | Completed | 1     |
      | Exception | 1     |
    When countByState is called for each state
    Then the counts should match exactly:
      | state     | expectedCount |
      | Setup     | 2             |
      | Loading   | 2             |
      | Loaded    | 3             |
      | Testing   | 1             |
      | Completed | 1             |
      | Exception | 1             |
