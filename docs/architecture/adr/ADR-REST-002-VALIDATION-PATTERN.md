# ADR-REST-002: Early Validation Before Actor Communication

## Status
**Accepted** - Implemented in Phase 1 (2025-10-19)

## Context

The Test Probe REST API delegates business logic to actors via `ServiceInterfaceFunctions`. Before sending requests to actors, we needed to decide: validate requests in RestRoutes or let actors handle validation?

**Problem:** Actor communication is expensive (ask pattern, timeout overhead). If we send invalid requests to actors:
- Wastes actor resources (mailbox processing)
- Slower failure (25s timeout vs <1ms validation)
- Harder to provide field-specific error messages
- Actors must handle both business logic AND input validation

**Requirements:**
- Fail fast for invalid input (don't waste 25s timeout)
- Reduce actor load (no asks for bad requests)
- Provide clear, field-specific error messages
- Maintain clean separation (validation in REST layer, business logic in actors)

## Decision

**Validate requests BEFORE actor communication** using dedicated `RestValidation` object.

### Pattern

```scala
// File: RestValidation.scala (91 lines)

object RestValidation {
  def validateStartRequest(req: RestStartTestRequest): Either[String, Unit] = {
    val path = req.`block-storage-path`.trim

    if (path.isEmpty) {
      Left("block-storage-path cannot be empty")
    } else if (!path.startsWith("s3://")) {
      Left("block-storage-path must be a valid S3 path (s3://bucket/...)")
    } else {
      req.`test-type` match {
        case Some(testType) if testType.trim.isEmpty =>
          Left("test-type cannot be empty string (omit field or provide valid value)")
        case _ =>
          Right(())
      }
    }
  }
}
```

### Usage in RestRoutes

```scala
// POST /api/v1/test/start
RestValidation.validateStartRequest(request) match {
  case Left(errorMessage) =>
    // Validation failed - return 400 Bad Request immediately
    complete(
      StatusCodes.BadRequest,
      RestErrorResponse.validationError("Request validation failed", errorMessage)
    )

  case Right(_) =>
    // Validation passed - proceed with actor communication
    onComplete(functions.startTest(testId, bucket, testType)) {
      // Handle actor response
    }
}
```

### Validation Rules

**What to Validate in REST Layer:**
1. **Format Validation:** S3 paths must start with `s3://`
2. **Non-Empty Validation:** Required fields must not be empty
3. **Type Validation:** UUIDs must be valid (handled by Pekko HTTP path matcher)
4. **Schema Validation:** JSON must match schema (handled by Spray JSON)

**What NOT to Validate in REST Layer:**
1. **Business Rules:** Test ID exists, test not already running (actor's responsibility)
2. **External Resources:** S3 bucket exists, Vault secrets exist (actor's responsibility)
3. **State Transitions:** Test in correct state to start (actor FSM's responsibility)

## Alternatives Considered

### Alternative 1: Validate in Actors

**Pattern:**
```scala
// RestRoutes - No validation
onComplete(functions.startTest(testId, bucket, testType)) {
  case Failure(ex: IllegalArgumentException) =>
    // Actor threw validation exception
    complete(StatusCodes.BadRequest, ...)
}

// Actor - Validates input
def handleStart(testId: UUID, bucket: String, testType: Option[String]): Behavior[Command] = {
  if (bucket.trim.isEmpty) {
    throw new IllegalArgumentException("block-storage-path cannot be empty")
  }
  // Process request
}
```

**Pros:**
- Single validation location (DRY)
- Actors own all validation logic

**Cons:**
- Wastes actor ask timeout (25s to discover empty field)
- Actor mailbox fills with invalid requests
- Harder to provide field-specific errors (exception messages less structured)
- Actors must handle both validation AND business logic

**Rejected:** Too slow, wastes actor resources.

### Alternative 2: Validation in Both Layers

**Pattern:**
```scala
// RestRoutes - Format validation
if (!bucket.startsWith("s3://")) {
  return 400 Bad Request
}

// Actor - Business rule validation
if (!testExists(testId)) {
  throw IllegalArgumentException("Test not found")
}
```

**Pros:**
- Fast failure for format errors
- Actors validate business rules

**Cons:**
- Duplicate validation logic (which layer validates what?)
- Confusion about responsibility boundaries

**Considered but refined:** We adopted this, but with clear boundaries (format in REST, business in actors).

### Alternative 3: JSON Schema Validation

**Pattern:**
```scala
// Use JSON Schema library to validate request body
val schema = JsonSchema.parse(...)
if (!schema.validate(requestJson)) {
  return 400 Bad Request with schema errors
}
```

**Pros:**
- Declarative validation (schema file)
- Comprehensive validation (all fields at once)

**Cons:**
- Extra dependency (JSON Schema library)
- Less control over error messages
- Schema maintenance overhead
- Overkill for simple validations

**Rejected:** Too heavy for our needs, custom validation simpler.

## Consequences

### Positive

1. **Fail Fast:** Invalid requests fail in <1ms (not 25s timeout)
   ```
   Empty S3 path → 400 Bad Request in <1ms
   vs
   Empty S3 path → Actor ask → 400 Bad Request in 25s (wasted timeout)
   ```

2. **Reduced Actor Load:** Actors never see invalid requests
   - Actor mailbox only contains valid requests
   - More capacity for legitimate work

3. **Better Error Messages:** Field-specific validation errors
   ```json
   {
     "error": "validation_error",
     "message": "Request validation failed",
     "details": "block-storage-path must be a valid S3 path (s3://bucket/...)"
   }
   ```

4. **Composable Validators:** `Either[String, Unit]` enables composition
   ```scala
   for {
     _ <- validateNonEmpty("field1", value1)
     _ <- validateS3Path(value2)
     _ <- validateNonEmpty("field3", value3)
   } yield ()
   ```

5. **Testable in Isolation:** Pure functions, easy to test
   ```scala
   RestValidation.validateStartRequest(invalidRequest) shouldBe Left("...")
   ```

### Negative

1. **Duplication Risk:** Format validation in REST, similar checks might exist in actors
   - Mitigation: Clear separation (format in REST, business in actors)

2. **Extra Code:** RestValidation object + tests (91 lines implementation, 247 lines tests)
   - Mitigation: Worth it for fail-fast behavior

3. **Validation Logic Scattered:** Some in REST (format), some in actors (business)
   - Mitigation: Clear boundaries, documented in ADR

### Neutral

1. **Either[String, Unit] Return Type:** Not throwing exceptions
   - Pro: Functional, composable
   - Con: More verbose than exceptions

## Implementation

### Files

**Validation Logic:** `/test-probe-interfaces/src/main/scala/com/company/probe/interfaces/rest/RestValidation.scala` (91 lines)

**Tests:** `/test-probe-interfaces/src/test/scala/com/company/probe/interfaces/rest/RestValidationSpec.scala` (247 lines, 19 tests)

**Usage:** `/test-probe-interfaces/src/main/scala/com/company/probe/interfaces/rest/RestRoutes.scala` (lines 148-155)

### Test Coverage

**All validation rules tested:**
- ✅ Empty block-storage-path → error
- ✅ Non-S3 path (http://, file://) → error
- ✅ Just "s3://" (no bucket) → error
- ✅ Valid S3 path → success
- ✅ Empty test-type string → error
- ✅ Valid test-type → success
- ✅ Omitted test-type → success

## Boundary Definition

**REST Layer Validates:**
- Format constraints (S3 path format, non-empty strings)
- Schema compliance (JSON structure, field types)
- HTTP constraints (Content-Type, Accept headers)

**Actor Layer Validates:**
- Business rules (test exists, test in correct state)
- External resources (S3 bucket accessible, Vault secrets exist)
- State transitions (FSM state machine validation)

**Clear Guideline:** If validation can be done without actor state or external systems, do it in REST layer.

## Related Decisions

- [ADR-REST-001: Error Handling Strategy](ADR-REST-001-ERROR-HANDLING-STRATEGY.md) - RestErrorResponse.validationError usage
- [ADR-005: Error Kernel Pattern](005-error-kernel-pattern.md) - Actors handle business logic errors

## References

- [REST Validation Implementation](../../test-probe-interfaces/src/main/scala/com/company/probe/interfaces/rest/RestValidation.scala)
- [REST Error Handling Documentation](../blueprint/03 APIs/03.1 REST API/03.1-rest-error-handling.md)
- [Functional Error Handling with Either](https://typelevel.org/cats/datatypes/either.html)

---

**Date:** 2025-10-19
**Deciders:** Test Probe Architecture Team
**Status:** Implemented (Phase 1)
