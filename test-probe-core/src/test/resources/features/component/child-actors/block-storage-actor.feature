@ComponentTest
Feature: BlockStorageActor - Test Evidence Storage Management
  As a BlockStorageActor
  I need to manage test evidence between S3 and local jimfs file system
  So that test data can be fetched and test results can be uploaded

  Background:
    Given a running actor system
    And a CoreConfig is available
    And a TestExecutionActor is spawned as parent

  @Critical @Initialization
  Scenario Outline: Initialize with bucket and send BlockStorageFetched
    Given a BlockStorageActor is spawned for test <testId>
    When the parent sends "Initialize" with bucket <bucket>
    Then the BlockStorageActor should send "BlockStorageFetched" to parent
    And the BlockStorageActor should send "ChildGoodToGo" to parent

    Examples:
      | testId     | bucket            |
      | "test-001" | "my-test-bucket"  |

  @Edge @NoBucket
  Scenario: Initialize with None bucket throws BlockStorageException
    Given a BlockStorageActor is spawned for test "blockstorageactor-test-002"
    When the parent sends "Initialize" with bucket "None"
    Then the exception should bubble to the parent TestExecutionActor

  @Critical @Upload
  Scenario: LoadToBlockStorage uploads evidence and sends completion
    Given a BlockStorageActor is spawned for test "test-003"
    When the BlockStorageActor has been initialized with bucket "evidence-bucket"
    Then the BlockStorageActor should send "BlockStorageFetched" to parent
    And the BlockStorageActor should send "ChildGoodToGo" to parent
    When the parent sends "LoadToBlockStorage" with testExecutionResult
    Then the BlockStorageActor should send "BlockStorageUploadComplete" to parent
    And the BlockStorageActor should log "Evidence uploaded" message

  @Critical @Lifecycle
  Scenario: Stop command triggers clean shutdown
    Given a BlockStorageActor is spawned for test "test-004"
    And the BlockStorageActor has been initialized
    When the BlockStorageActor parent sends "Stop"
    Then the BlockStorageActor should stop cleanly
    And the BlockStorageActor should log "Stopping BlockStorageActor" message

  @Sequential @HappyPath
  Scenario: Complete lifecycle - Initialize → LoadToBlockStorage → Stop
    Given a BlockStorageActor is spawned for test "test-005"
    When the parent sends "Initialize" with bucket "full-lifecycle-bucket"
    Then the BlockStorageActor should send "BlockStorageFetched" to parent
    And the BlockStorageActor should send "ChildGoodToGo" to parent
    When the parent sends "LoadToBlockStorage" with testExecutionResult
    Then the BlockStorageActor should send "BlockStorageUploadComplete" to parent
    When the BlockStorageActor parent sends "Stop"
    Then the BlockStorageActor should stop cleanly

  @Edge @Idempotency
  Scenario: Initialize called twice - idempotent behavior
    Given a BlockStorageActor is spawned for test "test-006"
    And the BlockStorageActor has been initialized with bucket "idempotent-bucket"
    When the parent sends "Initialize" with bucket "idempotent-bucket" again
    Then the BlockStorageActor should send "BlockStorageFetched" to parent
    And the BlockStorageActor should send "ChildGoodToGo" to parent

  @Edge @OrderViolation
  Scenario: LoadToBlockStorage before Initialize throws exception
    Given a BlockStorageActor is spawned for test "test-007"
    When the parent sends "LoadToBlockStorage" with testExecutionResult before initialization
    Then the BlockStorageActor should throw IllegalStateException
    And the BlockStorageActor exception message should contain "not initialized"

  @Edge @EarlyShutdown
  Scenario: Stop before Initialize - clean shutdown
    Given a BlockStorageActor is spawned for test "test-008"
    When the BlockStorageActor parent sends "Stop" before initialization
    Then the BlockStorageActor should stop cleanly

  @Edge @PartialLifecycle
  Scenario: Stop after Initialize, before LoadToBlockStorage
    Given a BlockStorageActor is spawned for test "test-009"
    And the BlockStorageActor has been initialized with bucket "partial-bucket"
    When the BlockStorageActor parent sends "Stop" before LoadToBlockStorage
    Then the BlockStorageActor should stop cleanly

  @Exception @InitializationFailure
  Scenario: Initialize throws exception and bubbles to parent
    Given the following storage behavior:
      | operation | behavior |
      | fetch     | fail     |
    And a BlockStorageActor is spawned for test "blockstorageactor-test-010"
    When the parent sends "Initialize" with bucket "failing-bucket"
    Then the exception should bubble to the parent TestExecutionActor

  @Exception @UploadFailure
  Scenario: LoadToBlockStorage throws exception and bubbles to parent
    Given the following storage behavior:
      | operation | behavior |
      | load      | fail     |
    And a BlockStorageActor is spawned for test "blockstorageactor-test-011"
    And the BlockStorageActor has been initialized with bucket "upload-fail-bucket"
    Then the BlockStorageActor should send "BlockStorageFetched" to parent
    And the BlockStorageActor should send "ChildGoodToGo" to parent
    When the parent sends "LoadToBlockStorage" with testExecutionResult
    Then the exception should bubble to the parent TestExecutionActor

  @Performance @ResponseTime
  Scenario: Initialize responds within expected timeout
    Given a BlockStorageActor is spawned for test "test-012"
    When the parent sends "Initialize" with bucket "performance-bucket"
    And the response should include "BlockStorageFetched"

  @Performance @ResponseTime
  Scenario: LoadToBlockStorage responds within expected timeout
    Given a BlockStorageActor is spawned for test "test-013"
    When the BlockStorageActor has been initialized with bucket "performance-upload-bucket"
    Then the BlockStorageActor should send "BlockStorageFetched" to parent
    And the BlockStorageActor should send "ChildGoodToGo" to parent
    When the parent sends "LoadToBlockStorage" with testExecutionResult
    Then the BlockStorageActor should send "BlockStorageUploadComplete" to parent

  @Validation @MessageContent
  Scenario: BlockStorageFetched contains all required fields
    Given a BlockStorageActor is spawned for test "test-014"
    When the parent sends "Initialize" with bucket "validation-bucket"
    Then the BlockStorageActor should send "BlockStorageFetched" to parent
    And the BlockStorageFetched message should have field "jimfsLocation" not null
    And the BlockStorageFetched message should have field "topicDirectives" not null
    And the jimfsLocation should match pattern "/jimfs/test-.*"
