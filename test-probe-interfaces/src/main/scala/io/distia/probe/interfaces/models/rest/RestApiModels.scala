package io.distia.probe.interfaces.models.rest

import java.util.UUID

/**
 * REST-specific API models
 *
 * These models use kebab-case field names for REST convention.
 * Anti-corruption layer converts between these and core models.
 *
 * Visibility: private[interfaces] maintains module encapsulation.
 * All methods remain public for comprehensive unit testing.
 */

// Request Models

private[interfaces] case class RestInitializeTestRequest()

private[interfaces] case class RestStartTestRequest(
  `test-id`: UUID,
  `block-storage-path`: String,
  `test-type`: Option[String]
)

private[interfaces] case class RestTestStatusRequest(`test-id`: UUID)

private[interfaces] case class RestQueueStatusRequest(`test-id`: Option[UUID])

private[interfaces] case class RestCancelRequest(`test-id`: UUID)

// Response Models

private[interfaces] case class RestInitializeTestResponse(
  `test-id`: UUID,
  message: String
)

private[interfaces] case class RestStartTestResponse(
  `test-id`: UUID,
  accepted: Boolean,
  `test-type`: Option[String],
  message: String
)

private[interfaces] case class RestTestStatusResponse(
  `test-id`: UUID,
  state: String,
  bucket: Option[String],
  `test-type`: Option[String],
  `start-time`: Option[String],
  `end-time`: Option[String],
  success: Option[Boolean],
  error: Option[String]
)

private[interfaces] case class RestQueueStatusResponse(
  `total-tests`: Int,
  `setup-count`: Int,
  `loading-count`: Int,
  `loaded-count`: Int,
  `testing-count`: Int,
  `completed-count`: Int,
  `exception-count`: Int,
  `currently-testing`: Option[UUID]
)

private[interfaces] case class RestTestCancelledResponse(
  `test-id`: UUID,
  cancelled: Boolean,
  message: Option[String]
)
