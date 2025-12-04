package io.distia.probe.interfaces.fixtures

import io.distia.probe.core.models.*

import java.util.UUID

/**
 * Test fixtures for Core domain models
 *
 * Provides reusable test data for core response models used in conversion tests.
 */
object CoreModelsFixtures {

  val testId1: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
  val testId2: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
  val testId3: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")

  val validInitializeResponse: InitializeTestResponse =
    InitializeTestResponse(
      testId = testId1,
      message = "Test initialized successfully"
    )

  val validStartResponse: StartTestResponse =
    StartTestResponse(
      testId = testId1,
      accepted = true,
      testType = Some("cucumber"),
      message = "Test accepted and queued"
    )

  val startResponseRejected: StartTestResponse =
    StartTestResponse(
      testId = testId2,
      accepted = false,
      testType = None,
      message = "Test rejected - invalid configuration"
    )

  val validTestStatusResponse: TestStatusResponse =
    TestStatusResponse(
      testId = testId1,
      state = "Testing",
      bucket = Some("s3://bucket/path"),
      testType = Some("cucumber"),
      startTime = Some("2024-01-15T10:30:00Z"),
      endTime = None,
      success = None,
      error = None
    )

  val completedTestStatusResponse: TestStatusResponse =
    TestStatusResponse(
      testId = testId2,
      state = "Completed",
      bucket = Some("s3://bucket/path"),
      testType = Some("cucumber"),
      startTime = Some("2024-01-15T10:30:00Z"),
      endTime = Some("2024-01-15T10:45:00Z"),
      success = Some(true),
      error = None
    )

  val failedTestStatusResponse: TestStatusResponse =
    TestStatusResponse(
      testId = testId3,
      state = "Exception",
      bucket = Some("s3://bucket/path"),
      testType = Some("cucumber"),
      startTime = Some("2024-01-15T10:30:00Z"),
      endTime = Some("2024-01-15T10:35:00Z"),
      success = Some(false),
      error = Some("Test execution failed: timeout")
    )

  val validQueueStatusResponse: QueueStatusResponse =
    QueueStatusResponse(
      totalTests = 10,
      setupCount = 2,
      loadingCount = 1,
      loadedCount = 3,
      testingCount = 1,
      completedCount = 2,
      exceptionCount = 1,
      currentlyTesting = Some(testId1)
    )

  val emptyQueueStatusResponse: QueueStatusResponse =
    QueueStatusResponse(
      totalTests = 0,
      setupCount = 0,
      loadingCount = 0,
      loadedCount = 0,
      testingCount = 0,
      completedCount = 0,
      exceptionCount = 0,
      currentlyTesting = None
    )

  val validTestCancelledResponse: TestCancelledResponse =
    TestCancelledResponse(
      testId = testId1,
      cancelled = true,
      message = Some("Test cancelled successfully")
    )

  val failedTestCancelledResponse: TestCancelledResponse =
    TestCancelledResponse(
      testId = testId2,
      cancelled = false,
      message = Some("Test not found or already completed")
    )
}
