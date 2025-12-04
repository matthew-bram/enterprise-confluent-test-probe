# ADR-REST-001: RFC 7807-Inspired Error Response Format

## Status
**Accepted** - Implemented in Phase 1 (2025-10-19)

## Context

The Test Probe REST API needed a consistent error response format that would:
1. Provide machine-readable error codes for client error handling logic
2. Include human-readable messages for user display and debugging
3. Support structured error details (field validation errors, additional context)
4. Indicate retry-ability for 503 Service Unavailable responses
5. Enable error correlation with server-side logs via timestamps

**Problem:** Without a standard error format, errors would be inconsistent:
- Some endpoints return plain strings
- Others return different JSON structures
- No machine-readable error codes (clients can't programmatically handle errors)
- No retry guidance for temporary failures
- Hard to correlate client errors with server logs

**Requirements:**
- **Consistency:** All endpoints return same error structure
- **Machine-Readable:** Error codes for client logic (if error == "validation_error" then...)
- **Human-Readable:** Messages for user display
- **Structured Details:** Field-level validation errors, stack trace references
- **Retry Guidance:** Indicate when to retry (retryAfter for 503 responses)
- **Debuggable:** Timestamps for log correlation

## Decision

**Adopt RFC 7807-inspired error response format** with the following structure:

```scala
case class RestErrorResponse(
  error: String,                          // Machine-readable error code
  message: String,                        // Human-readable description
  details: Option[String] = None,         // Optional structured details
  retryAfter: Option[String] = None,      // Optional retry delay (503 responses)
  timestamp: Long = System.currentTimeMillis()  // When error occurred (epoch ms)
)
```

**JSON Example:**
```json
{
  "error": "validation_error",
  "message": "Request validation failed",
  "details": "block-storage-path must be a valid S3 path (s3://bucket/...)",
  "retryAfter": null,
  "timestamp": 1634567890123
}
```

### Error Codes (Machine-Readable)

**4xx Client Errors:**
- `bad_request` - Invalid input (400)
- `validation_error` - Field validation failed (400)
- `not_found` - Resource not found (404)
- `method_not_allowed` - Wrong HTTP method (405)
- `unsupported_media_type` - Wrong Content-Type (415)

**5xx Server Errors:**
- `internal_server_error` - Unexpected server error (500)
- `service_unavailable` - Circuit breaker open (503)
- `not_ready` - System initializing (503)
- `timeout` - Request timeout (504)
- `actor_timeout` - Actor ask timeout (504)

### Factory Methods

**All errors created via factory methods (type-safe, consistent):**

```scala
object RestErrorResponse {
  def badRequest(message: String, details: Option[String] = None): RestErrorResponse
  def validationError(message: String, details: String): RestErrorResponse
  def notFound(message: String): RestErrorResponse
  def unsupportedMediaType(message: String): RestErrorResponse
  def methodNotAllowed(message: String): RestErrorResponse
  def internalServerError(message: String): RestErrorResponse
  def serviceUnavailable(message: String, retryAfter: String = "30s"): RestErrorResponse
  def notReady(message: String): RestErrorResponse
  def timeout(message: String): RestErrorResponse
  def actorTimeout(message: String, details: Option[String] = None): RestErrorResponse
}
```

### Why RFC 7807-Inspired (Not Strict RFC 7807)?

**RFC 7807 (Problem Details for HTTP APIs):**
```json
{
  "type": "https://example.com/probs/validation-error",
  "title": "Request validation failed",
  "status": 400,
  "detail": "block-storage-path must be a valid S3 path",
  "instance": "/api/v1/test/start"
}
```

**Our Simplification:**
```json
{
  "error": "validation_error",
  "message": "Request validation failed",
  "details": "block-storage-path must be a valid S3 path",
  "timestamp": 1634567890123
}
```

**Rationale for Simplification:**
1. **No URIs:** Don't need URLs for error types (clients care about error code, not URL)
2. **No Status Field:** Status code already in HTTP response (redundant in body)
3. **No Instance Field:** Request path logged server-side, not needed in response
4. **Add Timestamp:** Better log correlation than instance path
5. **Add retryAfter:** Explicit retry guidance for 503 responses

**RFC 7807 Compliance Benefits Kept:**
- Consistent structure across all errors
- Machine-readable error identifier
- Human-readable message
- Optional structured details

## Alternatives Considered

### Alternative 1: Plain String Errors

**Example:**
```json
"block-storage-path must be a valid S3 path (s3://bucket/...)"
```

**Pros:**
- Simple
- Easy to implement

**Cons:**
- Not machine-readable (can't programmatically handle different error types)
- No structured details
- No retry guidance
- No timestamp (hard to correlate with logs)

**Rejected:** Too simplistic, clients can't handle errors programmatically.

### Alternative 2: Strict RFC 7807

**Example:**
```json
{
  "type": "https://test-probe.company.com/errors/validation-error",
  "title": "Request validation failed",
  "status": 400,
  "detail": "block-storage-path must be a valid S3 path",
  "instance": "/api/v1/test/start"
}
```

**Pros:**
- Standards-compliant
- Well-documented pattern
- Tool support (OpenAPI, etc.)

**Cons:**
- Verbose (extra fields: type URL, status, instance)
- Type URLs require maintenance (need to host error documentation at URLs)
- Status field redundant (already in HTTP response)
- Instance field redundant (logged server-side)
- No timestamp (worse log correlation)
- No retry guidance (need extension)

**Rejected:** Too heavy for our needs, extra fields don't add value.

### Alternative 3: Separate Error Structures per Endpoint

**Example:**
```scala
case class ValidationErrorResponse(fieldErrors: Map[String, String])
case class TimeoutErrorResponse(timeoutMs: Long, operation: String)
case class NotFoundErrorResponse(resourceType: String, resourceId: String)
```

**Pros:**
- Type-specific error details

**Cons:**
- Inconsistent (different structure per error type)
- Clients need to handle multiple structures
- Hard to add new error types (need new case class)
- No machine-readable error code (clients check type instead)

**Rejected:** Inconsistency makes client code complex.

## Consequences

### Positive

1. **Consistency:** All endpoints return same error structure
   - Clients write one error handler, works for all endpoints

2. **Machine-Readable:** Error codes enable programmatic error handling
   ```typescript
   if (error.error === "validation_error") {
     // Show field-level errors to user
   } else if (error.error === "service_unavailable") {
     // Retry after retryAfter delay
   }
   ```

3. **Debuggable:** Timestamps enable log correlation
   ```
   Client error at 1634567890123
   Server log grep 1634567890123 → Find exact request
   ```

4. **Retry Guidance:** Explicit retryAfter for 503 responses
   ```json
   {
     "error": "service_unavailable",
     "message": "Circuit breaker open",
     "retryAfter": "30s"
   }
   ```

5. **Type-Safe Construction:** Factory methods prevent typos
   ```scala
   // ✅ Type-safe
   RestErrorResponse.validationError("...", "...")

   // ❌ Would be typo-prone
   RestErrorResponse("validaton_error", ...)  // Typo!
   ```

6. **Testable:** Easy to verify error responses
   ```scala
   val error = responseAs[RestErrorResponse]
   error.error shouldBe "validation_error"
   error.details should contain("block-storage-path")
   ```

### Negative

1. **Not Strictly RFC 7807:** Not fully compliant
   - Mitigation: Keep spirit of RFC 7807 (consistent structure, machine + human readable)

2. **Fixed Structure:** Can't add error-specific fields
   - Mitigation: Use `details` field for structured data (JSON string if needed)

3. **Error Code Enum Not Enforced:** `error` is String, not enum
   - Mitigation: Factory methods ensure consistency, OpenAPI spec documents valid codes

### Neutral

1. **Error Codes in camelCase:** `validation_error` not `VALIDATION_ERROR`
   - Follows JSON convention (snake_case)
   - Consistent with REST API naming (kebab-case URLs, snake_case JSON)

2. **Timestamp as Epoch Milliseconds:** Not ISO 8601 string
   - Easier to parse (no timezone issues)
   - Smaller JSON payload

## Implementation

### Files

**Model:** `/test-probe-interfaces/src/main/scala/com/company/probe/interfaces/rest/RestErrorResponse.scala` (97 lines)

**Exception Handler:** `/test-probe-interfaces/src/main/scala/com/company/probe/interfaces/rest/RestExceptionHandler.scala` (130 lines)

**Rejection Handler:** `/test-probe-interfaces/src/main/scala/com/company/probe/interfaces/rest/RestRejectionHandler.scala` (99 lines)

**Tests:** `/test-probe-interfaces/src/test/scala/com/company/probe/interfaces/rest/RestErrorResponseSpec.scala` (360 lines, 18 tests)

### Example Usage

**In RestRoutes:**
```scala
case Failure(ex: ServiceTimeoutException) =>
  logger.error(s"Timeout starting test testId=$testId", ex)
  complete(
    StatusCodes.GatewayTimeout,
    RestErrorResponse.actorTimeout("Actor system did not respond in time")
  )

case Failure(ex: ServiceUnavailableException) =>
  logger.warn(s"Service unavailable: ${ex.getMessage}")
  complete(
    StatusCodes.ServiceUnavailable,
    RestErrorResponse.serviceUnavailable(ex.getMessage, "30s")
  )
```

**In Validation:**
```scala
RestValidation.validateStartRequest(request) match {
  case Left(errorMessage) =>
    complete(
      StatusCodes.BadRequest,
      RestErrorResponse.validationError("Request validation failed", errorMessage)
    )
  case Right(_) =>
    // Proceed with request
}
```

## Related Decisions

- [ADR-REST-002: Validation Pattern](ADR-REST-002-VALIDATION-PATTERN.md) - Early validation using RestErrorResponse
- [ADR-005: Error Kernel Pattern](005-error-kernel-pattern.md) - Actor error handling (core module)

## References

- [RFC 7807: Problem Details for HTTP APIs](https://datatracker.ietf.org/doc/html/rfc7807)
- [REST API Error Handling](../blueprint/03 APIs/03.1 REST API/03.1-rest-error-handling.md) - Complete error handling documentation
- [OpenAPI Specification](../../test-probe-interfaces/src/main/resources/openapi.yaml) - Error response schema

---

**Date:** 2025-10-19
**Deciders:** Test Probe Architecture Team
**Status:** Implemented (Phase 1)
