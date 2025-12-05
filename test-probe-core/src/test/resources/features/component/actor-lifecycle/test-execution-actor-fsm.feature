@ComponentTest
Feature: Test Execution Actor FSM Complete Implementation
  As a Test Execution Actor
  I need to manage test lifecycle through FSM states
  So that tests progress through Setup → Loading → Loaded → Testing → Completed/Exception → ShuttingDown

  Background:
    Given a running actor system
    And Docker is available
    And Kafka is running
    And a QueueActor is spawned as parent

  @Critical @InitializeRequest
  Scenario Outline: Handle InInitializeTestRequest in Setup state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor
    Then the TestExecutionActor should store the replyTo reference
    And the TestExecutionActor should send "TrnSetup" to itself
    And the TestExecutionActor should transition to Setup state with TestData
    When the TestExecutionActor processes "TrnSetup"
    Then the TestExecutionActor should schedule poison pill timer for <setupTimeout> seconds
    And the service should receive "InitializeTestResponse" with testId <testId> and success <success>
    And the QueueActor should receive "TestInitialized" with testId <testId>
    Examples:
      | testId                               | setupTimeout | success |
      | 11111111-1111-1111-1111-111111111111 | 60          | true    |
      | 22222222-2222-2222-2222-222222222222 | 60          | true    |

  @Critical @StartRequest
  Scenario Outline: Handle InStartTestRequest in Setup state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    And the QueueActor should receive "TestInitialized" with testId <testId>
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the QueueActor should receive "TestLoading" with testId <testId>
    Examples:
      | testId                               | bucket        | testType    |
      | dddddddd-dddd-dddd-dddd-dddddddddddd | test-bucket-1 | functional  |
      | eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee | test-bucket-2 | performance |
      | ffffffff-ffff-ffff-ffff-ffffffffffff | test-bucket-3 | regression  |

  @Critical @ExceptionHandling @Loading
  Scenario Outline: Handle TrnException in Loading state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    And the QueueActor should receive "TestInitialized" with testId <testId>
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the QueueActor should receive "TestLoading" with testId <testId>
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    When the TestExecutionActor receives "TrnException"
    And the TestExecutionActor processes "TrnException"
    Then the QueueActor should receive "TestException" with testId <testId>
    Examples:
      | testId                               | bucket      | testType   |
      | 11111111-1111-1111-1111-111111111111 | test-bucket | functional |
      | 22222222-2222-2222-2222-222222222222 | test-bucket | regression |

  @Critical @ExceptionHandling @Testing
  Scenario Outline: Handle TrnException in Testing state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    And the QueueActor should receive "TestInitialized" with testId <testId>
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the QueueActor should receive "TestLoading" with testId <testId>
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaConsumerActor sends "ChildGoodToGo" for testId <testId>
    And the TestExecutionActor processes "TrnLoaded"
    And the QueueActor should receive "TestLoaded" with testId <testId>
    When the QueueActor sends "StartTesting" for testId <testId>
    And the TestExecutionActor processes "TrnTesting"
    Then the CucumberExecutionActor should receive "StartTest"
    And the QueueActor should receive "TestStarted" with testId <testId>
    When the TestExecutionActor receives "TrnException"
    And the TestExecutionActor processes "TrnException"
    Then the QueueActor should receive "TestException" with testId <testId>
    Examples:
      | testId                               | bucket      | testType   |
      | 33333333-3333-3333-3333-333333333333 | test-bucket | functional |
      | 44444444-4444-4444-4444-444444444444 | test-bucket | regression |

  @Critical @CancelRequests @Setup
  Scenario Outline: Handle InCancelRequest in Setup state (cancellation allowed)
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "InCancelRequest" for testId <testId>
    Then the service should receive "TestCancelledResponse" with testId <testId> and cancelled <cancelled>
    Examples:
      | testId                               | cancelled |
      | 11111111-1111-1111-1111-111111111111 | true      |
      | 22222222-2222-2222-2222-222222222222 | true      |

  @Critical @CancelRequests @Loading
  Scenario Outline: Handle InCancelRequest in Loading state (cancellation allowed)
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    When a service sends "InCancelRequest" for testId <testId>
    Then the service should receive "TestCancelledResponse" with testId <testId> and cancelled <cancelled>
    Examples:
      | testId                               | bucket      | testType   | cancelled |
      | 33333333-3333-3333-3333-333333333333 | test-bucket | functional | true      |
      | 44444444-4444-4444-4444-444444444444 | test-bucket | regression | true      |

  @Critical @CancelRequests @Loaded
  Scenario Outline: Handle InCancelRequest in Loaded state (cancellation allowed)
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaConsumerActor sends "ChildGoodToGo" for testId <testId>
    And the TestExecutionActor processes "TrnLoaded"
    When a service sends "InCancelRequest" for testId <testId>
    Then the service should receive "TestCancelledResponse" with testId <testId> and cancelled <cancelled>
    Examples:
      | testId                               | bucket      | testType    | cancelled |
      | 55555555-5555-5555-5555-555555555555 | test-bucket | functional  | true      |
      | 66666666-6666-6666-6666-666666666666 | test-bucket | performance | true      |

  @Critical @CancelRequests @Testing
  Scenario Outline: Handle InCancelRequest in Testing state (cancellation NOT allowed)
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaConsumerActor sends "ChildGoodToGo" for testId <testId>
    And the TestExecutionActor processes "TrnLoaded"
    When the QueueActor sends "StartTesting" for testId <testId>
    And the TestExecutionActor processes "TrnTesting"
    Then the CucumberExecutionActor should receive "StartTest"
    When a service sends "InCancelRequest" for testId <testId>
    Then the service should receive "TestCancelledResponse" with testId <testId> and cancelled <cancelled>
    Examples:
      | testId                               | bucket      | testType   | cancelled |
      | 77777777-7777-7777-7777-777777777777 | test-bucket | functional | false     |
      | 88888888-8888-8888-8888-888888888888 | test-bucket | regression | false     |

  @Critical @CancelRequests @Completed
  Scenario Outline: Handle InCancelRequest in Completed state (cancellation NOT allowed)
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaConsumerActor sends "ChildGoodToGo" for testId <testId>
    And the TestExecutionActor processes "TrnLoaded"
    When the QueueActor sends "StartTesting" for testId <testId>
    And the TestExecutionActor processes "TrnTesting"
    Then the CucumberExecutionActor should receive "StartTest"
    When the CucumberExecutionActor sends "TestComplete" for testId <testId> with result "<result>"
    Then the BlockStorageActor should receive "LoadToBlockStorage" with result
    When the BlockStorageActor sends "BlockStorageUploadComplete" for testId <testId> after upload
    And the TestExecutionActor processes "TrnComplete"
    When a service sends "InCancelRequest" for testId <testId>
    Then the service should receive "TestCancelledResponse" with testId <testId> and cancelled <cancelled>
    Examples:
      | testId                               | bucket      | testType   | result | cancelled |
      | 99999999-9999-9999-9999-999999999999 | test-bucket | functional | passed | false     |
      | aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa | test-bucket | regression | passed | false     |

  @Critical @CancelRequests @Exception
  Scenario Outline: Handle InCancelRequest in Exception state (cancellation NOT allowed)
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    When the TestExecutionActor receives "TrnException"
    And the TestExecutionActor processes "TrnException"
    When a service sends "InCancelRequest" for testId <testId>
    Then the service should receive "TestCancelledResponse" with testId <testId> and cancelled <cancelled>
    Examples:
      | testId                               | bucket      | testType    | cancelled |
      | bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb | test-bucket | functional  | false     |
      | cccccccc-cccc-cccc-cccc-cccccccccccc | test-bucket | performance | false     |

  @Critical @GetStatus @Setup
  Scenario Outline: Handle GetStatus in Setup state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "GetStatus" for testId <testId>
    Then the service should receive "TestStatusResponse" with testId "<testId>", state "<state>", bucket "<bucket>", testType "<testType>", startTime "<startTime>", endTime "<endTime>", success "<success>", and error "<error>"
    Examples:
      | testId                               | state | bucket    | testType | startTime | endTime   | success   | error     |
      | 11111111-1111-1111-1111-111111111111 | Setup | undefined |          | undefined | undefined | undefined | undefined |
      | 22222222-2222-2222-2222-222222222222 | Setup | undefined |          | undefined | undefined | undefined | undefined |

  @Critical @GetStatus @Loading
  Scenario Outline: Handle GetStatus in Loading state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    When a service sends "GetStatus" for testId <testId>
    Then the service should receive "TestStatusResponse" with testId "<testId>", state "<state>", bucket "<bucket>", testType "<testType>", startTime "<startTime>", endTime "<endTime>", success "<success>", and error "<error>"
    Examples:
      | testId                               | state   | bucket      | testType   | startTime | endTime   | success   | error     |
      | 33333333-3333-3333-3333-333333333333 | Loading | test-bucket | functional | undefined | undefined | undefined | undefined |
      | 44444444-4444-4444-4444-444444444444 | Loading | test-bucket | regression | undefined | undefined | undefined | undefined |

  @Critical @GetStatus @Loaded
  Scenario Outline: Handle GetStatus in Loaded state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaConsumerActor sends "ChildGoodToGo" for testId <testId>
    And the TestExecutionActor processes "TrnLoaded"
    When a service sends "GetStatus" for testId <testId>
    Then the service should receive "TestStatusResponse" with testId "<testId>", state "<state>", bucket "<bucket>", testType "<testType>", startTime "<startTime>", endTime "<endTime>", success "<success>", and error "<error>"
    Examples:
      | testId                               | state  | bucket      | testType    | startTime | endTime   | success   | error     |
      | 77777777-7777-7777-7777-777777777777 | Loaded | test-bucket | functional  | undefined | undefined | undefined | undefined |
      | 88888888-8888-8888-8888-888888888888 | Loaded | test-bucket | performance | undefined | undefined | undefined | undefined |

  @Critical @GetStatus @Testing
  Scenario Outline: Handle GetStatus in Testing state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaConsumerActor sends "ChildGoodToGo" for testId <testId>
    And the TestExecutionActor processes "TrnLoaded"
    When the QueueActor sends "StartTesting" for testId <testId>
    And the TestExecutionActor processes "TrnTesting"
    Then the CucumberExecutionActor should receive "StartTest"
    When a service sends "GetStatus" for testId <testId>
    Then the service should receive "TestStatusResponse" with testId "<testId>", state "<state>", bucket "<bucket>", testType "<testType>", startTime "<startTime>", endTime "<endTime>", success "<success>", and error "<error>"
    Examples:
      | testId                               | state   | bucket      | testType   | startTime | endTime   | success   | error     |
      | 99999999-9999-9999-9999-999999999999 | Testing | test-bucket | functional | defined   | undefined | undefined | undefined |
      | aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa | Testing | test-bucket | regression | defined   | undefined | undefined | undefined |

  @Critical @GetStatus @Completed
  Scenario Outline: Handle GetStatus in Completed state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaConsumerActor sends "ChildGoodToGo" for testId <testId>
    And the TestExecutionActor processes "TrnLoaded"
    When the QueueActor sends "StartTesting" for testId <testId>
    And the TestExecutionActor processes "TrnTesting"
    Then the CucumberExecutionActor should receive "StartTest"
    When the CucumberExecutionActor sends "TestComplete" for testId <testId> with result "<result>"
    Then the BlockStorageActor should receive "LoadToBlockStorage" with result
    When the BlockStorageActor sends "BlockStorageUploadComplete" for testId <testId> after upload
    And the TestExecutionActor processes "TrnComplete"
    When a service sends "GetStatus" for testId <testId>
    Then the service should receive "TestStatusResponse" with testId "<testId>", state "<state>", bucket "<bucket>", testType "<testType>", startTime "<startTime>", endTime "<endTime>", success "<success>", and error "<error>"
    Examples:
      | testId                               | state     | bucket      | testType   | result | startTime | endTime | success | error     |
      | bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb | Completed | test-bucket | functional | passed | defined   | defined | true    | undefined |
      | cccccccc-cccc-cccc-cccc-cccccccccccc | Completed | test-bucket | regression | passed | defined   | defined | true    | undefined |


  @Critical @GetStatus @Exception
  Scenario Outline: Handle GetStatus in Exception state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    And the QueueActor should receive "TestInitialized" with testId <testId>
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the QueueActor should receive "TestLoading" with testId <testId>
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    When the TestExecutionActor receives "TrnException"
    And the TestExecutionActor processes "TrnException"
    Then the QueueActor should receive "TestException" with testId <testId>
    When a service sends "GetStatus" for testId <testId>
    Then the service should receive "TestStatusResponse" with testId "<testId>", state "<state>", bucket "<bucket>", testType "<testType>", startTime "<startTime>", endTime "<endTime>", success "<success>", and error "<error>"
    Examples:
      | testId                               | state     | bucket      | testType    | startTime | endTime   | success   | error                     |
      | 55555555-5555-5555-5555-555555555555 | Exception | test-bucket | functional  | undefined | undefined | undefined | Manual test exception     |
      | 66666666-6666-6666-6666-666666666666 | Exception | test-bucket | performance | undefined | undefined | undefined | Manual test exception     |

  @Critical @TimerExpiry @Setup
  Scenario Outline: Handle poison pill timer expiry in Setup state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with short timers
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    And the QueueActor should receive "TestInitialized" with testId <testId>
    When the timer expires after 3 seconds
    Then the QueueActor should receive "TestStopping" with testId <testId>
    And the TestExecutionActor should have stopped
    Examples:
      | testId                               |
      | 11111111-1111-1111-1111-111111111111 |
      | 22222222-2222-2222-2222-222222222222 |

  @Critical @TimerExpiry @Loading
  Scenario Outline: Handle poison pill timer expiry in Loading state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks and short timers
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    And the QueueActor should receive "TestInitialized" with testId <testId>
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the QueueActor should receive "TestLoading" with testId <testId>
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    When the timer expires after 3 seconds
    Then the QueueActor should receive "TestStopping" with testId <testId>
    And the TestExecutionActor should have stopped
    Examples:
      | testId                               | bucket      | testType   |
      | 33333333-3333-3333-3333-333333333333 | test-bucket | functional |
      | 44444444-4444-4444-4444-444444444444 | test-bucket | regression |

  @Critical @TimerExpiry @Completed
  Scenario Outline: Handle poison pill timer expiry in Completed state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks and short timers
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    And the QueueActor should receive "TestInitialized" with testId <testId>
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the QueueActor should receive "TestLoading" with testId <testId>
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaConsumerActor sends "ChildGoodToGo" for testId <testId>
    And the TestExecutionActor processes "TrnLoaded"
    And the QueueActor should receive "TestLoaded" with testId <testId>
    When the QueueActor sends "StartTesting" for testId <testId>
    And the TestExecutionActor processes "TrnTesting"
    Then the CucumberExecutionActor should receive "StartTest"
    And the QueueActor should receive "TestStarted" with testId <testId>
    When the CucumberExecutionActor sends "TestComplete" for testId <testId> with result "passed"
    Then the BlockStorageActor should receive "LoadToBlockStorage" with result
    When the BlockStorageActor sends "BlockStorageUploadComplete" for testId <testId> after upload
    And the TestExecutionActor processes "TrnComplete"
    And the QueueActor should receive "TestCompleted" with testId <testId>
    When the timer expires after 3 seconds
    Then the QueueActor should receive "TestStopping" with testId <testId>
    And the TestExecutionActor should have stopped
    Examples:
      | testId                               | bucket      | testType   |
      | 55555555-5555-5555-5555-555555555555 | test-bucket | functional |
      | 66666666-6666-6666-6666-666666666666 | test-bucket | regression |

  @Critical @TimerExpiry @Exception
  Scenario Outline: Handle poison pill timer expiry in Exception state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks and short timers
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    And the QueueActor should receive "TestInitialized" with testId <testId>
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the QueueActor should receive "TestLoading" with testId <testId>
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    When the TestExecutionActor receives "TrnException"
    And the TestExecutionActor processes "TrnException"
    Then the QueueActor should receive "TestException" with testId <testId>
    When the timer expires after 3 seconds
    Then the QueueActor should receive "TestStopping" with testId <testId>
    And the TestExecutionActor should have stopped
    Examples:
      | testId                               | bucket      | testType   |
      | 77777777-7777-7777-7777-777777777777 | test-bucket | functional |
      | 88888888-8888-8888-8888-888888888888 | test-bucket | regression |


  @Edge @MessageIgnoring @Completed
  Scenario Outline: Ignore invalid messages in Completed state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaConsumerActor sends "ChildGoodToGo" for testId <testId>
    And the TestExecutionActor processes "TrnLoaded"
    When the QueueActor sends "StartTesting" for testId <testId>
    And the TestExecutionActor processes "TrnTesting"
    Then the CucumberExecutionActor should receive "StartTest"
    When the CucumberExecutionActor sends "TestComplete" for testId <testId> with result "passed"
    Then the BlockStorageActor should receive "LoadToBlockStorage" with result
    When the BlockStorageActor sends "BlockStorageUploadComplete" for testId <testId> after upload
    And the TestExecutionActor processes "TrnComplete"
    When the TestExecutionActor receives "<invalidMessage>"
    Then the TestExecutionActor should remain in Completed state
    And no response should be sent
    Examples:
      | testId                               | bucket      | testType   | invalidMessage |
      | 11111111-1111-1111-1111-111111111111 | test-bucket | functional | TrnLoading     |
      | 22222222-2222-2222-2222-222222222222 | test-bucket | regression | TrnTesting     |

  @Edge @MessageIgnoring @Exception
  Scenario Outline: Ignore invalid messages in Exception state
    When a service sends "InInitializeTestRequest" with testId <testId> to TestExecutionActor with mocks
    And the TestExecutionActor processes "TrnSetup"
    Then the service should receive "InitializeTestResponse" with testId <testId> and success true
    When a service sends "InStartTestRequest" with testId <testId>, bucket "<bucket>", testType "<testType>"
    And the TestExecutionActor processes "TrnLoading"
    Then the service should receive "StartTestResponse" with testId <testId> and accepted true
    And the BlockStorageActor should receive "Initialize"
    When the BlockStorageActor sends "ChildGoodToGo" for testId <testId>
    And the VaultActor sends "ChildGoodToGo" for testId <testId>
    And the CucumberExecutionActor sends "ChildGoodToGo" for testId <testId>
    And the KafkaProducerActor sends "ChildGoodToGo" for testId <testId>
    When the TestExecutionActor receives "TrnException"
    And the TestExecutionActor processes "TrnException"
    When the TestExecutionActor receives "<invalidMessage>"
    Then the TestExecutionActor should remain in Exception state
    And no response should be sent
    Examples:
      | testId                               | bucket      | testType    | invalidMessage |
      | 33333333-3333-3333-3333-333333333333 | test-bucket | functional  | TrnLoading     |
      | 44444444-4444-4444-4444-444444444444 | test-bucket | performance | TrnTesting     |