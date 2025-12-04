package io.distia.probe.interfaces.rest

import io.distia.probe.interfaces.models.rest.RestStartTestRequest
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

/**
 * Unit tests for RestValidation object
 *
 * Target Coverage: All REST protocol validation methods
 * - validateStartRequest (REST protocol concerns)
 * - validateNonEmpty (with security checks)
 * - validateLength (security checks)
 *
 * Testing Strategy:
 * - Test REST protocol rules (non-empty, length limits, security)
 * - Test boundary conditions (empty, max length, null bytes)
 * - Verify Left/Right Either semantics
 * - Ensure error messages are descriptive
 *
 * Does NOT test business logic (S3 paths, etc.) - that's services layer
 */
class RestValidationSpec extends AnyWordSpec with Matchers {

  private val testId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

  // ========================================
  // validateStartRequest
  // ========================================

  "validateStartRequest" when {

    "block-storage-path is non-empty and test-type is valid" should {
      "return Right(())" in {
        val request = RestStartTestRequest(
          `test-id` = testId,
          `block-storage-path` = "s3://bucket/path/to/files",
          `test-type` = Some("integration")
        )

        val result = RestValidation.validateStartRequest(request)

        result shouldBe Right(())
      }
    }

    "block-storage-path is non-empty and test-type is omitted" should {
      "return Right(())" in {
        val request = RestStartTestRequest(
          `test-id` = testId,
          `block-storage-path` = "azure://container/path",
          `test-type` = None
        )

        val result = RestValidation.validateStartRequest(request)

        result shouldBe Right(())
      }
    }

    "block-storage-path is file:// path (any URI scheme accepted)" should {
      "return Right(())" in {
        val request = RestStartTestRequest(
          `test-id` = testId,
          `block-storage-path` = "file:///local/path/to/files",
          `test-type` = Some("unit")
        )

        val result = RestValidation.validateStartRequest(request)

        result shouldBe Right(())
      }
    }

    "block-storage-path is empty" should {
      "return Left with error message" in {
        val request = RestStartTestRequest(
          `test-id` = testId,
          `block-storage-path` = "",
          `test-type` = Some("integration")
        )

        val result = RestValidation.validateStartRequest(request)

        result shouldBe Left("block-storage-path cannot be empty")
      }
    }

    "block-storage-path is whitespace-only" should {
      "return Left with error message" in {
        val request = RestStartTestRequest(
          `test-id` = testId,
          `block-storage-path` = "   ",
          `test-type` = Some("integration")
        )

        val result = RestValidation.validateStartRequest(request)

        result shouldBe Left("block-storage-path cannot be empty")
      }
    }


    "test-type is empty string" should {
      "return Left with test-type validation error" in {
        val request = RestStartTestRequest(
          `test-id` = testId,
          `block-storage-path` = "s3://bucket/path",
          `test-type` = Some("")
        )

        val result = RestValidation.validateStartRequest(request)

        result shouldBe Left("test-type cannot be empty string (omit field or provide valid value)")
      }
    }

    "test-type is whitespace-only" should {
      "return Left with test-type validation error" in {
        val request = RestStartTestRequest(
          `test-id` = testId,
          `block-storage-path` = "s3://bucket/path",
          `test-type` = Some("   ")
        )

        val result = RestValidation.validateStartRequest(request)

        result shouldBe Left("test-type cannot be empty string (omit field or provide valid value)")
      }
    }

    // Security Tests - Length Limits

    "block-storage-path exceeds max length (4096)" should {
      "return Left with length limit error" in {
        val longPath = "s3://bucket/" + ("a" * 5000)  // > 4096 chars
        val request = RestStartTestRequest(
          `test-id` = testId,
          `block-storage-path` = longPath,
          `test-type` = None
        )

        val result = RestValidation.validateStartRequest(request)

        result.isLeft shouldBe true
        val Left(msg) = result: @unchecked
        msg should include("exceeds maximum length")
        msg should include("4096")
      }
    }

    "test-type exceeds max length (2048)" should {
      "return Left with length limit error" in {
        val longType = "a" * 3000  // > 2048 chars
        val request = RestStartTestRequest(
          `test-id` = testId,
          `block-storage-path` = "s3://bucket/path",
          `test-type` = Some(longType)
        )

        val result = RestValidation.validateStartRequest(request)

        result.isLeft shouldBe true
        val Left(msg) = result: @unchecked
        msg should include("exceeds maximum length")
        msg should include("2048")
      }
    }

    // Security Tests - Null Byte Detection

    "block-storage-path contains null byte" should {
      "return Left with security error" in {
        val request = RestStartTestRequest(
          `test-id` = testId,
          `block-storage-path` = "s3://bucket\u0000/path",
          `test-type` = None
        )

        val result = RestValidation.validateStartRequest(request)

        result shouldBe Left("block-storage-path contains invalid null character")
      }
    }

    "test-type contains null byte" should {
      "return Left with security error" in {
        val request = RestStartTestRequest(
          `test-id` = testId,
          `block-storage-path` = "s3://bucket/path",
          `test-type` = Some("integration\u0000test")
        )

        val result = RestValidation.validateStartRequest(request)

        result shouldBe Left("test-type contains invalid null character")
      }
    }
  }

  // ========================================
  // validateNonEmpty
  // ========================================

  "validateNonEmpty" when {

    "value is non-empty" should {
      "return Right(())" in {
        val result = RestValidation.validateNonEmpty("fieldName", "valid-value")

        result shouldBe Right(())
      }
    }

    "value is empty string" should {
      "return Left with field name in error message" in {
        val result = RestValidation.validateNonEmpty("myField", "")

        result shouldBe Left("myField cannot be empty")
      }
    }

    "value is whitespace-only" should {
      "return Left with field name in error message" in {
        val result = RestValidation.validateNonEmpty("myField", "   ")

        result shouldBe Left("myField cannot be empty")
      }
    }

    "value has leading and trailing whitespace but content in middle" should {
      "return Right(()) - trimmed value is non-empty" in {
        val result = RestValidation.validateNonEmpty("myField", "  content  ")

        result shouldBe Right(())
      }
    }

    // Security Tests - Length Limits

    "value exceeds default max length (2048)" should {
      "return Left with length limit error" in {
        val longValue = "a" * 3000  // > 2048 chars

        val result = RestValidation.validateNonEmpty("myField", longValue)

        result.isLeft shouldBe true
        val Left(msg) = result: @unchecked
        msg should include("myField")
        msg should include("exceeds maximum length")
      }
    }

    "value exceeds custom max length" should {
      "return Left with length limit error" in {
        val value = "a" * 150

        val result = RestValidation.validateNonEmpty("myField", value, maxLength = 100)

        result.isLeft shouldBe true
        val Left(msg) = result: @unchecked
        msg should include("myField")
        msg should include("exceeds maximum length")
        msg should include("100")
      }
    }

    // Security Tests - Null Byte Detection

    "value contains null byte" should {
      "return Left with security error" in {
        val result = RestValidation.validateNonEmpty("myField", "value\u0000here")

        result shouldBe Left("myField contains invalid null character")
      }
    }
  }

  // ========================================
  // validateLength
  // ========================================

  "validateLength" when {

    "value is within limit" should {
      "return Right(())" in {
        val result = RestValidation.validateLength("myField", "short value", 100)

        result shouldBe Right(())
      }
    }

    "value is exactly at limit" should {
      "return Right(())" in {
        val value = "a" * 100
        val result = RestValidation.validateLength("myField", value, 100)

        result shouldBe Right(())
      }
    }

    "value exceeds limit by 1" should {
      "return Left with length error" in {
        val value = "a" * 101
        val result = RestValidation.validateLength("myField", value, 100)

        result.isLeft shouldBe true
        val Left(msg) = result: @unchecked
        msg should include("myField")
        msg should include("exceeds maximum length")
        msg should include("100")
      }
    }

    "value significantly exceeds limit" should {
      "return Left with length error" in {
        val value = "a" * 5000
        val result = RestValidation.validateLength("myField", value, 1000)

        result.isLeft shouldBe true
        val Left(msg) = result: @unchecked
        msg should include("exceeds maximum length")
      }
    }
  }

}
