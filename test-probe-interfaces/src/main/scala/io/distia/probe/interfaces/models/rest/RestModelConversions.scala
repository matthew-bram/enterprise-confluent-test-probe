package io.distia.probe.interfaces.models.rest

import io.distia.probe.core.models.*

import java.util.UUID

/**
 * Anti-corruption layer for converting between Core and REST models
 *
 * Hexagonal Architecture Pattern:
 * - Core models are protocol-agnostic (camelCase, business-focused)
 * - REST models are REST-specific (kebab-case, HTTP-focused)
 * - This layer translates between the two without polluting either
 *
 * Conversion Direction:
 * - toCore: REST → Core (inbound requests)
 * - toRest: Core → REST (outbound responses)
 *
 * Visibility: private[interfaces] maintains module encapsulation.
 * All methods remain public for comprehensive unit testing.
 */
private[interfaces] object RestModelConversions {

  // ============================================================================
  // Request Conversions (REST → Core domain)
  // ============================================================================

  /**
   * Convert REST start request to core function parameters
   * REST uses kebab-case, core uses camelCase
   */
  def toCore(req: RestStartTestRequest): (UUID, String, Option[String]) =
    (req.`test-id`, req.`block-storage-path`, req.`test-type`)

  def toCore(req: RestTestStatusRequest): UUID =
    req.`test-id`

  def toCore(req: RestQueueStatusRequest): Option[UUID] =
    req.`test-id`

  def toCore(req: RestCancelRequest): UUID =
    req.`test-id`

  // ============================================================================
  // Response Conversions (Core domain → REST)
  // ============================================================================

  def toRest(resp: InitializeTestResponse): RestInitializeTestResponse =
    RestInitializeTestResponse(resp.testId, resp.message)

  def toRest(resp: StartTestResponse): RestStartTestResponse =
    RestStartTestResponse(resp.testId, resp.accepted, resp.testType, resp.message)

  def toRest(resp: TestStatusResponse): RestTestStatusResponse =
    RestTestStatusResponse(
      resp.testId,
      resp.state,
      resp.bucket,
      resp.testType,
      resp.startTime,
      resp.endTime,
      resp.success,
      resp.error
    )

  def toRest(resp: QueueStatusResponse): RestQueueStatusResponse =
    RestQueueStatusResponse(
      resp.totalTests,
      resp.setupCount,
      resp.loadingCount,
      resp.loadedCount,
      resp.testingCount,
      resp.completedCount,
      resp.exceptionCount,
      resp.currentlyTesting
    )

  def toRest(resp: TestCancelledResponse): RestTestCancelledResponse =
    RestTestCancelledResponse(resp.testId, resp.cancelled, resp.message)
}
