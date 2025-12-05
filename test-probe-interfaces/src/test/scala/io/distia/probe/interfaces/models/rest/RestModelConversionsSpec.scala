package io.distia.probe.interfaces.models.rest

import io.distia.probe.interfaces.fixtures.{CoreModelsFixtures, RestApiModelsFixtures}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for RestModelConversions anti-corruption layer
 *
 * Target Coverage: 85%+ (pure functions, easier to test)
 *
 * Tests:
 * - toCore() conversions (REST → Core parameters)
 * - toRest() conversions (Core → REST responses)
 * - Field name mapping (kebab-case ↔ camelCase)
 * - UUID conversions
 * - Option handling (Some/None)
 */
class RestModelConversionsSpec extends AnyWordSpec with Matchers {

  // Use qualified imports to avoid ambiguity between fixtures
  import RestApiModelsFixtures.{testId1 => restTestId1, testId2 => restTestId2, testId3 => restTestId3, *}
  import CoreModelsFixtures.{
    testId1 => coreTestId1,
    testId2 => coreTestId2,
    testId3 => coreTestId3,
    emptyQueueStatusResponse => coreEmptyQueueStatusResponse,
    *
  }

  "RestModelConversions" when {

    // ========================================================================
    // Request Conversions (REST → Core)
    // ========================================================================

    "converting REST requests to Core parameters" should {

      "convert RestStartTestRequest to core parameters tuple" in {
        val (testId, bucket, testType) = RestModelConversions.toCore(validRestStartRequest)

        testId shouldBe restTestId1
        bucket shouldBe "s3://bucket/path/to/test"
        testType shouldBe Some("cucumber")
      }

      "handle RestStartTestRequest with no test type" in {
        val (testId, bucket, testType) = RestModelConversions.toCore(restStartRequestNoTestType)

        testId shouldBe restTestId2
        bucket shouldBe "s3://bucket/another/path"
        testType shouldBe None
      }

      "convert RestTestStatusRequest to UUID" in {
        val testId = RestModelConversions.toCore(validRestStatusRequest)

        testId shouldBe restTestId1
      }

      "convert RestQueueStatusRequest with test ID to Option[UUID]" in {
        val testIdOpt = RestModelConversions.toCore(validRestQueueStatusRequest)

        testIdOpt shouldBe Some(restTestId1)
      }

      "convert RestQueueStatusRequest without test ID to None" in {
        val testIdOpt = RestModelConversions.toCore(restQueueStatusRequestNoTestId)

        testIdOpt shouldBe None
      }

      "convert RestCancelRequest to UUID" in {
        val testId = RestModelConversions.toCore(validRestCancelRequest)

        testId shouldBe restTestId1
      }
    }

    // ========================================================================
    // Response Conversions (Core → REST)
    // ========================================================================

    "converting Core responses to REST responses" should {

      "convert InitializeTestResponse to RestInitializeTestResponse" in {
        val restResponse = RestModelConversions.toRest(validInitializeResponse)

        restResponse.`test-id` shouldBe coreTestId1
        restResponse.message shouldBe "Test initialized successfully"
      }

      "convert StartTestResponse to RestStartTestResponse with test type" in {
        val restResponse = RestModelConversions.toRest(validStartResponse)

        restResponse.`test-id` shouldBe coreTestId1
        restResponse.accepted shouldBe true
        restResponse.`test-type` shouldBe Some("cucumber")
        restResponse.message shouldBe "Test accepted and queued"
      }

      "convert rejected StartTestResponse to RestStartTestResponse" in {
        val restResponse = RestModelConversions.toRest(startResponseRejected)

        restResponse.`test-id` shouldBe coreTestId2
        restResponse.accepted shouldBe false
        restResponse.`test-type` shouldBe None
        restResponse.message shouldBe "Test rejected - invalid configuration"
      }

      "convert TestStatusResponse to RestTestStatusResponse (in-progress test)" in {
        val restResponse = RestModelConversions.toRest(validTestStatusResponse)

        restResponse.`test-id` shouldBe coreTestId1
        restResponse.state shouldBe "Testing"
        restResponse.bucket shouldBe Some("s3://bucket/path")
        restResponse.`test-type` shouldBe Some("cucumber")
        restResponse.`start-time` shouldBe Some("2024-01-15T10:30:00Z")
        restResponse.`end-time` shouldBe None
        restResponse.success shouldBe None
        restResponse.error shouldBe None
      }

      "convert completed TestStatusResponse to RestTestStatusResponse" in {
        val restResponse = RestModelConversions.toRest(completedTestStatusResponse)

        restResponse.`test-id` shouldBe coreTestId2
        restResponse.state shouldBe "Completed"
        restResponse.bucket shouldBe Some("s3://bucket/path")
        restResponse.`test-type` shouldBe Some("cucumber")
        restResponse.`start-time` shouldBe Some("2024-01-15T10:30:00Z")
        restResponse.`end-time` shouldBe Some("2024-01-15T10:45:00Z")
        restResponse.success shouldBe Some(true)
        restResponse.error shouldBe None
      }

      "convert failed TestStatusResponse to RestTestStatusResponse" in {
        val restResponse = RestModelConversions.toRest(failedTestStatusResponse)

        restResponse.`test-id` shouldBe coreTestId3
        restResponse.state shouldBe "Exception"
        restResponse.bucket shouldBe Some("s3://bucket/path")
        restResponse.`test-type` shouldBe Some("cucumber")
        restResponse.`start-time` shouldBe Some("2024-01-15T10:30:00Z")
        restResponse.`end-time` shouldBe Some("2024-01-15T10:35:00Z")
        restResponse.success shouldBe Some(false)
        restResponse.error shouldBe Some("Test execution failed: timeout")
      }

      "convert QueueStatusResponse to RestQueueStatusResponse" in {
        val restResponse = RestModelConversions.toRest(validQueueStatusResponse)

        restResponse.`total-tests` shouldBe 10
        restResponse.`setup-count` shouldBe 2
        restResponse.`loading-count` shouldBe 1
        restResponse.`loaded-count` shouldBe 3
        restResponse.`testing-count` shouldBe 1
        restResponse.`completed-count` shouldBe 2
        restResponse.`exception-count` shouldBe 1
        restResponse.`currently-testing` shouldBe Some(coreTestId1)
      }

      "convert empty QueueStatusResponse to RestQueueStatusResponse" in {
        val restResponse = RestModelConversions.toRest(coreEmptyQueueStatusResponse)

        restResponse.`total-tests` shouldBe 0
        restResponse.`setup-count` shouldBe 0
        restResponse.`loading-count` shouldBe 0
        restResponse.`loaded-count` shouldBe 0
        restResponse.`testing-count` shouldBe 0
        restResponse.`completed-count` shouldBe 0
        restResponse.`exception-count` shouldBe 0
        restResponse.`currently-testing` shouldBe None
      }

      "convert TestCancelledResponse to RestTestCancelledResponse (success)" in {
        val restResponse = RestModelConversions.toRest(validTestCancelledResponse)

        restResponse.`test-id` shouldBe coreTestId1
        restResponse.cancelled shouldBe true
        restResponse.message shouldBe Some("Test cancelled successfully")
      }

      "convert TestCancelledResponse to RestTestCancelledResponse (failed)" in {
        val restResponse = RestModelConversions.toRest(failedTestCancelledResponse)

        restResponse.`test-id` shouldBe coreTestId2
        restResponse.cancelled shouldBe false
        restResponse.message shouldBe Some("Test not found or already completed")
      }
    }

    // ========================================================================
    // Round-trip Conversions (validation)
    // ========================================================================

    "performing round-trip conversions" should {

      "preserve data through toCore → function → toRest cycle for start request" in {
        // Simulate: REST request → Core params → Core response → REST response
        val (testId, bucket, testType) = RestModelConversions.toCore(validRestStartRequest)

        // Verify REST → Core conversion extracted correct values
        testId shouldBe restTestId1
        bucket shouldBe "s3://bucket/path/to/test"
        testType shouldBe Some("cucumber")

        // Verify Core → REST conversion preserves values
        val coreResponse = validStartResponse
        val restResponse = RestModelConversions.toRest(coreResponse)

        restResponse.`test-id` shouldBe coreTestId1
        restResponse.`test-type` shouldBe Some("cucumber")
      }

      "preserve UUID through toCore → toRest cycle for status request" in {
        val testId = RestModelConversions.toCore(validRestStatusRequest)
        testId shouldBe restTestId1

        val coreResponse = validTestStatusResponse
        val restResponse = RestModelConversions.toRest(coreResponse)
        restResponse.`test-id` shouldBe coreTestId1
      }

      "preserve Option[UUID] through toCore → toRest cycle for queue status" in {
        val testIdOpt = RestModelConversions.toCore(validRestQueueStatusRequest)
        testIdOpt shouldBe Some(restTestId1)

        val coreResponse = validQueueStatusResponse
        val restResponse = RestModelConversions.toRest(coreResponse)
        restResponse.`currently-testing` shouldBe Some(coreTestId1)
      }
    }
  }
}
