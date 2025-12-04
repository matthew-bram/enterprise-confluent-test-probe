package io.distia.probe
package core
package fixtures

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Unit tests for SerdesFixtures wire format validation helpers.
 *
 * Tests the utility methods that validate and extract components from
 * Confluent Schema Registry wire format:
 * - Magic byte validation (0x00)
 * - Schema ID extraction (4-byte big-endian)
 * - Protobuf message index array extraction
 * - Payload extraction
 *
 * Wire Format Reference:
 * - JSON/Avro: [magic byte (1)][schema ID (4)][payload (N)]
 * - Protobuf: [magic byte (1)][schema ID (4)][message index (N)][payload (M)]
 *
 * Test Strategy:
 * - Unit tests (no Testcontainers)
 * - Test valid wire format parsing
 * - Test error handling for invalid input
 * - Test edge cases (min/max schema IDs, empty payloads)
 */
class WireFormatValidationSpec extends AnyWordSpec with Matchers with SerdesFixtures {

  "MagicByte constant" should {

    "be 0x00" in {
      MagicByte shouldBe 0x00.toByte
    }
  }

  "hasMagicByte" should {

    "return true for valid wire format with magic byte 0x00" in {
      val bytes = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x01, 0x01, 0x02, 0x03)

      hasMagicByte(bytes) shouldBe true
    }

    "return false for invalid magic byte" in {
      val bytes = Array[Byte](0x01, 0x00, 0x00, 0x00, 0x01)

      hasMagicByte(bytes) shouldBe false
    }

    "return false for empty array" in {
      val bytes = Array.empty[Byte]

      hasMagicByte(bytes) shouldBe false
    }

    "return true for array with only magic byte" in {
      val bytes = Array[Byte](0x00)

      hasMagicByte(bytes) shouldBe true
    }
  }

  "extractSchemaId" should {

    "extract schema ID 1 from wire format" in {
      // Schema ID 1 = 0x00 0x00 0x00 0x01
      val bytes = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x01, 0x01, 0x02, 0x03)

      extractSchemaId(bytes) shouldBe 1
    }

    "extract schema ID 256 from wire format" in {
      // Schema ID 256 = 0x00 0x00 0x01 0x00
      val bytes = Array[Byte](0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x02, 0x03)

      extractSchemaId(bytes) shouldBe 256
    }

    "extract schema ID 65536 from wire format" in {
      // Schema ID 65536 = 0x00 0x01 0x00 0x00
      val bytes = Array[Byte](0x00, 0x00, 0x01, 0x00, 0x00, 0x01, 0x02, 0x03)

      extractSchemaId(bytes) shouldBe 65536
    }

    "extract max positive schema ID from wire format" in {
      // Schema ID 2147483647 = 0x7F 0xFF 0xFF 0xFF (max positive int)
      val bytes = Array[Byte](0x00, 0x7F, 0xFF.toByte, 0xFF.toByte, 0xFF.toByte, 0x01)

      extractSchemaId(bytes) shouldBe 2147483647
    }

    "extract schema ID from minimum valid length (5 bytes)" in {
      val bytes = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x42)

      extractSchemaId(bytes) shouldBe 66  // 0x42 = 66
    }

    "throw IllegalArgumentException for bytes too short" in {
      val bytes = Array[Byte](0x00, 0x00, 0x00, 0x01)  // Only 4 bytes

      val ex = intercept[IllegalArgumentException] {
        extractSchemaId(bytes)
      }

      ex.getMessage should include("Bytes too short")
      ex.getMessage should include("need at least 5")
    }

    "throw IllegalArgumentException for empty array" in {
      val bytes = Array.empty[Byte]

      val ex = intercept[IllegalArgumentException] {
        extractSchemaId(bytes)
      }

      ex.getMessage should include("Bytes too short")
    }

    "throw IllegalArgumentException for invalid magic byte" in {
      val bytes = Array[Byte](0x01, 0x00, 0x00, 0x00, 0x01)

      val ex = intercept[IllegalArgumentException] {
        extractSchemaId(bytes)
      }

      ex.getMessage should include("Invalid magic byte")
      ex.getMessage should include("expected 0x00")
    }
  }

  "extractProtobufMessageIndex" should {

    "extract single byte message index (0x00) for single-message schemas" in {
      // Protobuf wire format: [magic][schema ID][message index][payload]
      val bytes = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x02, 0x03)

      val messageIndex = extractProtobufMessageIndex(bytes)

      messageIndex.length shouldBe 1
      messageIndex(0) shouldBe 0x00.toByte
    }

    "extract non-zero message index" in {
      val bytes = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x01, 0x05, 0x01, 0x02, 0x03)

      val messageIndex = extractProtobufMessageIndex(bytes)

      messageIndex(0) shouldBe 0x05.toByte
    }

    "throw IllegalArgumentException for bytes too short" in {
      val bytes = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x01)  // Only 5 bytes (no message index)

      val ex = intercept[IllegalArgumentException] {
        extractProtobufMessageIndex(bytes)
      }

      ex.getMessage should include("Bytes too short for Protobuf")
    }
  }

  "extractPayload" should {

    "extract payload starting at default offset (5) for JSON/Avro" in {
      val bytes = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x01, 0x48, 0x65, 0x6C, 0x6C, 0x6F)

      val payload = extractPayload(bytes)

      payload.length shouldBe 5
      new String(payload) shouldBe "Hello"
    }

    "extract payload starting at custom offset (6) for Protobuf" in {
      // Protobuf: [magic][schema ID][msg index][payload]
      val bytes = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x48, 0x65, 0x6C, 0x6C, 0x6F)

      val payload = extractPayload(bytes, payloadStartOffset = 6)

      payload.length shouldBe 5
      new String(payload) shouldBe "Hello"
    }

    "return empty array when no payload" in {
      val bytes = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x01)

      val payload = extractPayload(bytes)

      payload.length shouldBe 0
    }

    "return empty array when offset equals length" in {
      val bytes = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x01)

      val payload = extractPayload(bytes, payloadStartOffset = 5)

      payload.length shouldBe 0
    }

    "handle large payloads" in {
      val header = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x01)
      val largePayload = Array.fill[Byte](10000)(0x42)
      val bytes = header ++ largePayload

      val payload = extractPayload(bytes)

      payload.length shouldBe 10000
      payload.forall(_ == 0x42) shouldBe true
    }
  }

  "Wire format size comparison helpers" should {

    "calculateSizeReduction returns correct percentage" in {
      val jsonBytes = Array.fill[Byte](100)(0x00)
      val binaryBytes = Array.fill[Byte](60)(0x00)

      val reduction = calculateSizeReduction(jsonBytes, binaryBytes)

      reduction shouldBe 40.0 +- 0.01
    }

    "calculateSizeReduction returns 0 for empty JSON bytes" in {
      val jsonBytes = Array.empty[Byte]
      val binaryBytes = Array.fill[Byte](10)(0x00)

      calculateSizeReduction(jsonBytes, binaryBytes) shouldBe 0.0
    }

    "calculateSizeReduction handles equal sizes" in {
      val jsonBytes = Array.fill[Byte](50)(0x00)
      val binaryBytes = Array.fill[Byte](50)(0x00)

      calculateSizeReduction(jsonBytes, binaryBytes) shouldBe 0.0
    }

    "calculateSizeReduction handles binary larger than JSON (negative reduction)" in {
      val jsonBytes = Array.fill[Byte](50)(0x00)
      val binaryBytes = Array.fill[Byte](75)(0x00)

      calculateSizeReduction(jsonBytes, binaryBytes) shouldBe -50.0 +- 0.01
    }

    "isBinarySmallerThanJson returns true when reduction meets threshold" in {
      val jsonBytes = Array.fill[Byte](100)(0x00)
      val binaryBytes = Array.fill[Byte](60)(0x00)  // 40% reduction

      isBinarySmallerThanJson(jsonBytes, binaryBytes, expectedReductionPercent = 30.0) shouldBe true
    }

    "isBinarySmallerThanJson returns false when reduction below threshold" in {
      val jsonBytes = Array.fill[Byte](100)(0x00)
      val binaryBytes = Array.fill[Byte](80)(0x00)  // 20% reduction

      isBinarySmallerThanJson(jsonBytes, binaryBytes, expectedReductionPercent = 30.0) shouldBe false
    }

    "isBinarySmallerThanJson uses default 30% threshold" in {
      val jsonBytes = Array.fill[Byte](100)(0x00)
      val binaryBytes = Array.fill[Byte](69)(0x00)  // 31% reduction

      isBinarySmallerThanJson(jsonBytes, binaryBytes) shouldBe true
    }
  }
}
