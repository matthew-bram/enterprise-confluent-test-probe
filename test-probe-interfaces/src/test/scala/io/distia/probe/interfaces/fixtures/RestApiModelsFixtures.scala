package io.distia.probe.interfaces.fixtures

import io.distia.probe.interfaces.models.rest.*
import java.util.UUID

/**
 * Test fixtures for REST API models
 *
 * Provides reusable test data to minimize duplication across test suites.
 * Follows the "do more with less code" principle.
 */
object RestApiModelsFixtures {

  val testId1: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
  val testId2: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
  val testId3: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")

  // Request Fixtures

  val validRestInitializeRequest: RestInitializeTestRequest =
    RestInitializeTestRequest()

  val validRestStartRequest: RestStartTestRequest =
    RestStartTestRequest(
      `test-id` = testId1,
      `block-storage-path` = "s3://bucket/path/to/test",
      `test-type` = Some("cucumber")
    )

  val restStartRequestNoTestType: RestStartTestRequest =
    RestStartTestRequest(
      `test-id` = testId2,
      `block-storage-path` = "s3://bucket/another/path",
      `test-type` = None
    )

  val validRestStatusRequest: RestTestStatusRequest =
    RestTestStatusRequest(`test-id` = testId1)

  val validRestQueueStatusRequest: RestQueueStatusRequest =
    RestQueueStatusRequest(`test-id` = Some(testId1))

  val restQueueStatusRequestNoTestId: RestQueueStatusRequest =
    RestQueueStatusRequest(`test-id` = None)

  val validRestCancelRequest: RestCancelRequest =
    RestCancelRequest(`test-id` = testId1)

  // Response Fixtures

  val validRestInitializeResponse: RestInitializeTestResponse =
    RestInitializeTestResponse(
      `test-id` = testId1,
      message = "Test initialized successfully"
    )

  val validRestStartResponse: RestStartTestResponse =
    RestStartTestResponse(
      `test-id` = testId1,
      accepted = true,
      `test-type` = Some("cucumber"),
      message = "Test accepted and queued"
    )

  val restStartResponseRejected: RestStartTestResponse =
    RestStartTestResponse(
      `test-id` = testId2,
      accepted = false,
      `test-type` = None,
      message = "Test rejected - invalid configuration"
    )

  val validRestTestStatusResponse: RestTestStatusResponse =
    RestTestStatusResponse(
      `test-id` = testId1,
      state = "Testing",
      bucket = Some("s3://bucket/path"),
      `test-type` = Some("cucumber"),
      `start-time` = Some("2024-01-15T10:30:00Z"),
      `end-time` = None,
      success = None,
      error = None
    )

  val completedRestTestStatusResponse: RestTestStatusResponse =
    RestTestStatusResponse(
      `test-id` = testId2,
      state = "Completed",
      bucket = Some("s3://bucket/path"),
      `test-type` = Some("cucumber"),
      `start-time` = Some("2024-01-15T10:30:00Z"),
      `end-time` = Some("2024-01-15T10:45:00Z"),
      success = Some(true),
      error = None
    )

  val failedRestTestStatusResponse: RestTestStatusResponse =
    RestTestStatusResponse(
      `test-id` = testId3,
      state = "Exception",
      bucket = Some("s3://bucket/path"),
      `test-type` = Some("cucumber"),
      `start-time` = Some("2024-01-15T10:30:00Z"),
      `end-time` = Some("2024-01-15T10:35:00Z"),
      success = Some(false),
      error = Some("Test execution failed: timeout")
    )

  val validRestQueueStatusResponse: RestQueueStatusResponse =
    RestQueueStatusResponse(
      `total-tests` = 10,
      `setup-count` = 2,
      `loading-count` = 1,
      `loaded-count` = 3,
      `testing-count` = 1,
      `completed-count` = 2,
      `exception-count` = 1,
      `currently-testing` = Some(testId1)
    )

  val emptyQueueStatusResponse: RestQueueStatusResponse =
    RestQueueStatusResponse(
      `total-tests` = 0,
      `setup-count` = 0,
      `loading-count` = 0,
      `loaded-count` = 0,
      `testing-count` = 0,
      `completed-count` = 0,
      `exception-count` = 0,
      `currently-testing` = None
    )

  val validRestCancelResponse: RestTestCancelledResponse =
    RestTestCancelledResponse(
      `test-id` = testId1,
      cancelled = true,
      message = Some("Test cancelled successfully")
    )

  val failedRestCancelResponse: RestTestCancelledResponse =
    RestTestCancelledResponse(
      `test-id` = testId2,
      cancelled = false,
      message = Some("Test not found or already completed")
    )
}
