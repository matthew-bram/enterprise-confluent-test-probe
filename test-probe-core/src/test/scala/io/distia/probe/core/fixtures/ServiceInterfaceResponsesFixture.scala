package io.distia.probe.core.fixtures

import io.distia.probe.core.models.*

import java.util.UUID

/**
 * Factory methods for service interface response models.
 *
 * Provides:
 * - InitializeTestResponse factory
 * - StartTestResponse factory
 * - TestStatusResponse factory
 * - TestCancelledResponse factory
 * - QueueStatusResponse factory
 *
 * Design Philosophy:
 * - Single source of truth for ServiceResponse model creation
 * - Consistent default values across tests
 * - Reduces duplication in test code
 * - Aligns with DRY and fixture composition patterns
 * - Only includes ServiceResponse subtypes (not domain models like TestExecutionResult)
 *
 * Usage:
 * {{{
 *   class MySpec extends AnyWordSpec
 *     with ServiceInterfaceResponsesFixture {
 *
 *     "feature" should {
 *       "work with responses" in {
 *         val initResp = initializeTestResponse(testId)
 *         val startResp = startTestResponse(testId, accepted = true)
 *         val statusResp = testStatusResponse(testId, TestExecutionState.Setup)
 *       }
 *     }
 *   }
 * }}}
 *
 * Thread Safety: Pure functions - thread-safe.
 *
 * Test Strategy: Tested via ServiceInterfaceResponsesFixtureSpec
 */
trait ServiceInterfaceResponsesFixture {

  /**
   * Create InitializeTestResponse.
   *
   * @param testId Test UUID
   * @param message Optional custom message (default: "Test initialized - upload files to bucket/{testId}/")
   * @return InitializeTestResponse
   */
  def initializeTestResponse(testId: UUID, message: Option[String] = None): InitializeTestResponse = {
    InitializeTestResponse(
      testId = testId,
      message = message.getOrElse(s"Test initialized - upload files to bucket/$testId/")
    )
  }

  /**
   * Create StartTestResponse.
   *
   * @param testId Test UUID
   * @param accepted Whether test was accepted
   * @param testType Optional test type
   * @param message Optional custom message (default: "Test accepted and will be executed")
   * @return StartTestResponse
   */
  def startTestResponse(
    testId: UUID,
    accepted: Boolean,
    testType: Option[String] = None,
    message: Option[String] = None
  ): StartTestResponse = {
    StartTestResponse(
      testId = testId,
      accepted = accepted,
      testType = testType,
      message = message.getOrElse("Test accepted and will be executed")
    )
  }

  /**
   * Create TestStatusResponse.
   *
   * @param testId Test UUID
   * @param state Current FSM state (as string)
   * @param bucket Optional S3 bucket name
   * @param testType Optional test type
   * @param startTime Optional start time
   * @param endTime Optional end time
   * @param success Optional success flag
   * @param error Optional error message
   * @return TestStatusResponse
   */
  def testStatusResponse(
    testId: UUID,
    state: String,
    bucket: Option[String] = None,
    testType: Option[String] = None,
    startTime: Option[String] = None,
    endTime: Option[String] = None,
    success: Option[Boolean] = None,
    error: Option[String] = None
  ): TestStatusResponse = {
    TestStatusResponse(
      testId = testId,
      state = state,
      bucket = bucket,
      testType = testType,
      startTime = startTime,
      endTime = endTime,
      success = success,
      error = error
    )
  }

  /**
   * Create TestCancelledResponse.
   *
   * @param testId Test UUID
   * @param cancelled Whether test was cancelled
   * @param message Optional cancellation message
   * @return TestCancelledResponse
   */
  def testCancelledResponse(
    testId: UUID,
    cancelled: Boolean,
    message: Option[String] = None
  ): TestCancelledResponse = {
    TestCancelledResponse(
      testId = testId,
      cancelled = cancelled,
      message = message
    )
  }

  /**
   * Create QueueStatusResponse.
   *
   * @param totalTests Total tests in queue
   * @param setupCount Tests in Setup state
   * @param loadingCount Tests in Loading state
   * @param loadedCount Tests in Loaded state
   * @param testingCount Tests in Testing state
   * @param completedCount Tests in Completed state
   * @param exceptionCount Tests in Exception state
   * @param currentlyTesting Optional currently testing test ID
   * @return QueueStatusResponse
   */
  def queueStatusResponse(
    totalTests: Int = 0,
    setupCount: Int = 0,
    loadingCount: Int = 0,
    loadedCount: Int = 0,
    testingCount: Int = 0,
    completedCount: Int = 0,
    exceptionCount: Int = 0,
    currentlyTesting: Option[UUID] = None
  ): QueueStatusResponse = {
    QueueStatusResponse(
      totalTests = totalTests,
      setupCount = setupCount,
      loadingCount = loadingCount,
      loadedCount = loadedCount,
      testingCount = testingCount,
      completedCount = completedCount,
      exceptionCount = exceptionCount,
      currentlyTesting = currentlyTesting
    )
  }

}
