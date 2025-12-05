package io.distia.probe.interfaces.rest

import io.distia.probe.interfaces.models.rest._

/**
 * REST protocol-level validation
 *
 * Validates HTTP/REST concerns ONLY, not business logic:
 * - Non-empty required fields
 * - String length limits (security: prevent DoS)
 * - Basic character validation (security: prevent injection)
 *
 * Does NOT validate:
 * - Business logic (storage paths, domain rules) - belongs in services layer
 * - Provider-specific formats (S3, Azure) - belongs in services layer
 * - Domain constraints (testId exists) - belongs in services layer
 *
 * Returns Either[String, Unit]:
 * - Left(errorMessage) for validation failures
 * - Right(()) for valid input
 *
 * Visibility: private[interfaces] maintains module encapsulation.
 * All methods remain public for comprehensive unit testing.
 */
private[interfaces] object RestValidation {

  // Security: Prevent DoS via huge strings
  private val MaxFieldLength = 2048  // 2KB per field (test-type, etc.)
  private val MaxPathLength = 4096   // 4KB for paths (URIs can be long)

  /**
   * Validate RestStartTestRequest for REST protocol concerns
   *
   * Checks:
   * - block-storage-path is non-empty (required field)
   * - block-storage-path length is reasonable (security: prevent DoS)
   * - block-storage-path has no null bytes (security: prevent injection)
   * - test-type is non-empty if provided (no empty strings)
   * - test-type length is reasonable (security: prevent DoS)
   *
   * Does NOT check:
   * - Storage path format (S3, Azure, etc.) - that's services layer concern
   * - Business rules - that's domain layer concern
   *
   * @param req The start test request to validate
   * @return Left(error message) if validation fails, Right(()) if valid
   */
  def validateStartRequest(req: RestStartTestRequest): Either[String, Unit] = {
    val path = req.`block-storage-path`.trim

    // Check required field
    if path.isEmpty then
      Left("block-storage-path cannot be empty")

    // Check length limit (security: prevent DoS)
    else if path.length > MaxPathLength then
      Left(s"block-storage-path exceeds maximum length ($MaxPathLength characters)")

    // Check for null bytes (security: prevent injection attacks)
    else if path.contains('\u0000') then
      Left("block-storage-path contains invalid null character")

    else
      // Validate test-type if provided
      req.`test-type` match
        case Some(testType) if testType.trim.isEmpty =>
          Left("test-type cannot be empty string (omit field or provide valid value)")
        case Some(testType) if testType.length > MaxFieldLength =>
          Left(s"test-type exceeds maximum length ($MaxFieldLength characters)")
        case Some(testType) if testType.contains('\u0000') =>
          Left("test-type contains invalid null character")
        case _ =>
          Right(())
  }

  /**
   * Validate non-empty trimmed string with security checks
   *
   * Generic validator for required string fields.
   * Includes REST protocol validation (length limits, null bytes).
   *
   * @param fieldName Name of the field (for error message)
   * @param value The value to validate
   * @param maxLength Maximum allowed length (default: 2048)
   * @return Left(error message) if invalid, Right(()) if valid
   */
  def validateNonEmpty(
    fieldName: String,
    value: String,
    maxLength: Int = MaxFieldLength
  ): Either[String, Unit] =
    if value.trim.isEmpty then
      Left(s"$fieldName cannot be empty")
    else if value.length > maxLength then
      Left(s"$fieldName exceeds maximum length ($maxLength characters)")
    else if value.contains('\u0000') then
      Left(s"$fieldName contains invalid null character")
    else
      Right(())

  /**
   * Validate string length (security check)
   *
   * Use this for optional fields that need length validation but not required checks.
   *
   * @param fieldName Name of the field (for error message)
   * @param value The value to validate
   * @param maxLength Maximum allowed length
   * @return Left(error message) if too long, Right(()) if valid
   */
  def validateLength(
    fieldName: String,
    value: String,
    maxLength: Int
  ): Either[String, Unit] =
    if value.length > maxLength then
      Left(s"$fieldName exceeds maximum length ($maxLength characters)")
    else
      Right(())
}
