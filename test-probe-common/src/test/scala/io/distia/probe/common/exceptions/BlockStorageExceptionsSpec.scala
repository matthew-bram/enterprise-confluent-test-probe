package io.distia.probe
package common
package exceptions

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for BlockStorageExceptions
 * Tests exception hierarchy, construction, and message formatting
 */
private[common] class BlockStorageExceptionsSpec extends AnyWordSpec with Matchers {

  // ========== BLOCKSTORAGEEXCEPTION TESTS ==========

  "BlockStorageException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: BlockStorageException = new BlockStorageException("Test error message")

        exception.getMessage shouldBe "Test error message"
      }

      "have null cause" in {
        val exception: BlockStorageException = new BlockStorageException("Test error message")

        exception.getCause shouldBe null
      }

      "be throwable" in {
        intercept[BlockStorageException] {
          throw new BlockStorageException("Test error")
        }
      }
    }

    "constructed with message and cause" should {

      "store message correctly" in {
        val cause: Throwable = new RuntimeException("Underlying cause")
        val exception: BlockStorageException = new BlockStorageException("Test error", cause)

        exception.getMessage shouldBe "Test error"
      }

      "store cause correctly" in {
        val cause: Throwable = new RuntimeException("Underlying cause")
        val exception: BlockStorageException = new BlockStorageException("Test error", cause)

        exception.getCause shouldBe cause
        exception.getCause.getMessage shouldBe "Underlying cause"
      }

      "preserve exception chain" in {
        val rootCause: Throwable = new IllegalArgumentException("Root cause")
        val intermediateCause: Throwable = new RuntimeException("Intermediate", rootCause)
        val exception: BlockStorageException = new BlockStorageException("Top level", intermediateCause)

        exception.getCause.getCause shouldBe rootCause
      }
    }

    "caught as RuntimeException" should {

      "be catchable as RuntimeException" in {
        try {
          throw new BlockStorageException("Test")
        } catch {
          case _: RuntimeException => succeed
          case _: Throwable => fail("Should be catchable as RuntimeException")
        }
      }
    }
  }

  // ========== MISSINGFEATURESDIRECTORYEXCEPTION TESTS ==========

  "MissingFeaturesDirectoryException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: MissingFeaturesDirectoryException =
          new MissingFeaturesDirectoryException("Features directory not found")

        exception.getMessage shouldBe "Features directory not found"
      }

      "extend BlockStorageException" in {
        val exception: MissingFeaturesDirectoryException =
          new MissingFeaturesDirectoryException("Test")

        exception shouldBe a[BlockStorageException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new java.nio.file.NoSuchFileException("/path/to/features")
        val exception: MissingFeaturesDirectoryException =
          new MissingFeaturesDirectoryException("Features directory missing", cause)

        exception.getMessage shouldBe "Features directory missing"
        exception.getCause shouldBe cause
      }
    }

    "caught as BlockStorageException" should {

      "be catchable as parent type" in {
        try {
          throw new MissingFeaturesDirectoryException("Test")
        } catch {
          case _: BlockStorageException => succeed
          case _: Throwable => fail("Should be catchable as BlockStorageException")
        }
      }
    }
  }

  // ========== EMPTYFEATURESDIRECTORYEXCEPTION TESTS ==========

  "EmptyFeaturesDirectoryException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: EmptyFeaturesDirectoryException =
          new EmptyFeaturesDirectoryException("Features directory is empty")

        exception.getMessage shouldBe "Features directory is empty"
      }

      "extend BlockStorageException" in {
        val exception: EmptyFeaturesDirectoryException =
          new EmptyFeaturesDirectoryException("Test")

        exception shouldBe a[BlockStorageException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new IllegalStateException("No feature files found")
        val exception: EmptyFeaturesDirectoryException =
          new EmptyFeaturesDirectoryException("Empty features directory", cause)

        exception.getMessage shouldBe "Empty features directory"
        exception.getCause shouldBe cause
      }
    }
  }

  // ========== MISSINGTOPICDIRECTIVEFILEEXCEPTION TESTS ==========

  "MissingTopicDirectiveFileException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: MissingTopicDirectiveFileException =
          new MissingTopicDirectiveFileException("Topic directive file not found")

        exception.getMessage shouldBe "Topic directive file not found"
      }

      "extend BlockStorageException" in {
        val exception: MissingTopicDirectiveFileException =
          new MissingTopicDirectiveFileException("Test")

        exception shouldBe a[BlockStorageException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new java.nio.file.NoSuchFileException("/path/to/topic-directive.yaml")
        val exception: MissingTopicDirectiveFileException =
          new MissingTopicDirectiveFileException("Missing topic directive", cause)

        exception.getMessage shouldBe "Missing topic directive"
        exception.getCause shouldBe cause
      }
    }
  }

  // ========== INVALIDTOPICDIRECTIVEFORMATEXCEPTION TESTS ==========

  "InvalidTopicDirectiveFormatException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: InvalidTopicDirectiveFormatException =
          new InvalidTopicDirectiveFormatException("Invalid YAML format")

        exception.getMessage shouldBe "Invalid YAML format"
      }

      "extend BlockStorageException" in {
        val exception: InvalidTopicDirectiveFormatException =
          new InvalidTopicDirectiveFormatException("Test")

        exception shouldBe a[BlockStorageException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new RuntimeException("YAML parsing error")
        val exception: InvalidTopicDirectiveFormatException =
          new InvalidTopicDirectiveFormatException("Invalid format", cause)

        exception.getMessage shouldBe "Invalid format"
        exception.getCause shouldBe cause
      }

      "wrap Circe decoding failures" in {
        val circeError: Throwable = new RuntimeException("Decoding failure")
        val exception: InvalidTopicDirectiveFormatException =
          new InvalidTopicDirectiveFormatException("Failed to decode", circeError)

        exception.getCause shouldBe circeError
      }
    }
  }

  // ========== BUCKETURIPARSEEXCEPTION TESTS ==========

  "BucketUriParseException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: BucketUriParseException =
          new BucketUriParseException("Invalid bucket URI format")

        exception.getMessage shouldBe "Invalid bucket URI format"
      }

      "extend BlockStorageException" in {
        val exception: BucketUriParseException =
          new BucketUriParseException("Test")

        exception shouldBe a[BlockStorageException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new java.net.URISyntaxException("invalid", "Bad URI syntax")
        val exception: BucketUriParseException =
          new BucketUriParseException("URI parse error", cause)

        exception.getMessage shouldBe "URI parse error"
        exception.getCause shouldBe cause
      }
    }

    "used for various URI parsing errors" should {

      "handle S3 URI errors" in {
        val exception: BucketUriParseException =
          new BucketUriParseException("Invalid S3 URI: missing bucket name")

        exception.getMessage should include("S3 URI")
      }

      "handle Azure URI errors" in {
        val exception: BucketUriParseException =
          new BucketUriParseException("Invalid Azure Blob URI: missing container")

        exception.getMessage should include("Azure Blob URI")
      }

      "handle local path errors" in {
        val exception: BucketUriParseException =
          new BucketUriParseException("Local path must be absolute")

        exception.getMessage should include("Local path")
      }
    }
  }

  // ========== STREAMINGEXCEPTION TESTS ==========

  "StreamingException" when {

    "constructed with message and cause" should {

      "require both message and cause" in {
        val cause: Throwable = new RuntimeException("Streaming failed")
        val exception: StreamingException =
          new StreamingException("Stream processing error", cause)

        exception.getMessage shouldBe "Stream processing error"
        exception.getCause shouldBe cause
      }

      "extend BlockStorageException" in {
        val cause: Throwable = new RuntimeException("Test")
        val exception: StreamingException =
          new StreamingException("Test", cause)

        exception shouldBe a[BlockStorageException]
      }
    }

    "wrapping streaming errors" should {

      "wrap S3 transfer errors" in {
        val cause: Throwable = new RuntimeException("S3 transfer failed")
        val exception: StreamingException =
          new StreamingException("Failed to upload to S3", cause)

        exception.getCause.getMessage shouldBe "S3 transfer failed"
      }

      "wrap Azure blob errors" in {
        val cause: Throwable = new RuntimeException("Azure blob operation failed")
        val exception: StreamingException =
          new StreamingException("Failed to download from Azure", cause)

        exception.getCause.getMessage shouldBe "Azure blob operation failed"
      }
    }
  }

  // ========== DUPLICATETOPICEXCEPTION TESTS ==========

  "DuplicateTopicException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: DuplicateTopicException =
          new DuplicateTopicException("Topic 'orders' appears 2 times - topic names must be unique")

        exception.getMessage shouldBe "Topic 'orders' appears 2 times - topic names must be unique"
      }

      "have null cause" in {
        val exception: DuplicateTopicException =
          new DuplicateTopicException("Duplicate topic detected")

        exception.getCause shouldBe null
      }

      "extend BlockStorageException" in {
        val exception: DuplicateTopicException =
          new DuplicateTopicException("Test")

        exception shouldBe a[BlockStorageException]
      }

      "be throwable" in {
        intercept[DuplicateTopicException] {
          throw new DuplicateTopicException("Duplicate topic error")
        }
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new IllegalArgumentException("Validation failed")
        val exception: DuplicateTopicException =
          new DuplicateTopicException("Duplicate topic in directive", cause)

        exception.getMessage shouldBe "Duplicate topic in directive"
        exception.getCause shouldBe cause
      }

      "preserve exception chain" in {
        val rootCause: Throwable = new RuntimeException("Root validation error")
        val exception: DuplicateTopicException =
          new DuplicateTopicException("Duplicate topics detected", rootCause)

        exception.getCause shouldBe rootCause
        exception.getCause.getMessage shouldBe "Root validation error"
      }
    }

    "caught as BlockStorageException" should {

      "be catchable as parent type" in {
        try {
          throw new DuplicateTopicException("Test")
        } catch {
          case _: BlockStorageException => succeed
          case _: Throwable => fail("Should be catchable as BlockStorageException")
        }
      }
    }

    "used for duplicate topic validation" should {

      "handle single duplicate topic message" in {
        val exception: DuplicateTopicException =
          new DuplicateTopicException("Topic 'payment-events' appears 2 times - topic names must be unique")

        exception.getMessage should include("payment-events")
        exception.getMessage should include("2 times")
      }

      "handle multiple duplicate topics message" in {
        val topics: List[String] = List("orders", "payments")
        val exception: DuplicateTopicException =
          new DuplicateTopicException(s"Multiple duplicate topics found: ${topics.mkString(", ")}")

        exception.getMessage should include("orders")
        exception.getMessage should include("payments")
      }
    }
  }

  // ========== INVALIDBOOTSTRAPSERVERSEXCEPTION TESTS ==========

  "InvalidBootstrapServersException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: InvalidBootstrapServersException =
          new InvalidBootstrapServersException("Invalid bootstrap server format: localhost")

        exception.getMessage shouldBe "Invalid bootstrap server format: localhost"
      }

      "have null cause" in {
        val exception: InvalidBootstrapServersException =
          new InvalidBootstrapServersException("Invalid format")

        exception.getCause shouldBe null
      }

      "extend BlockStorageException" in {
        val exception: InvalidBootstrapServersException =
          new InvalidBootstrapServersException("Test")

        exception shouldBe a[BlockStorageException]
      }

      "be throwable" in {
        intercept[InvalidBootstrapServersException] {
          throw new InvalidBootstrapServersException("Invalid bootstrap servers")
        }
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new IllegalArgumentException("Parse error")
        val exception: InvalidBootstrapServersException =
          new InvalidBootstrapServersException("Bootstrap server validation failed", cause)

        exception.getMessage shouldBe "Bootstrap server validation failed"
        exception.getCause shouldBe cause
      }

      "preserve exception chain" in {
        val rootCause: Throwable = new RuntimeException("Root parse error")
        val exception: InvalidBootstrapServersException =
          new InvalidBootstrapServersException("Invalid servers format", rootCause)

        exception.getCause shouldBe rootCause
        exception.getCause.getMessage shouldBe "Root parse error"
      }
    }

    "caught as BlockStorageException" should {

      "be catchable as parent type" in {
        try {
          throw new InvalidBootstrapServersException("Test")
        } catch {
          case _: BlockStorageException => succeed
          case _: Throwable => fail("Should be catchable as BlockStorageException")
        }
      }
    }

    "used for bootstrap server validation" should {

      "handle missing port error" in {
        val exception: InvalidBootstrapServersException =
          new InvalidBootstrapServersException("Invalid bootstrap server format: localhost. Expected format: host:port")

        exception.getMessage should include("localhost")
        exception.getMessage should include("Expected format")
      }

      "handle invalid port error" in {
        val exception: InvalidBootstrapServersException =
          new InvalidBootstrapServersException("Invalid bootstrap server format: localhost:abc. Port must be numeric.")

        exception.getMessage should include("localhost:abc")
        exception.getMessage should include("Port")
      }

      "handle empty servers error" in {
        val exception: InvalidBootstrapServersException =
          new InvalidBootstrapServersException("Bootstrap servers cannot be empty when specified")

        exception.getMessage should include("cannot be empty")
      }

      "handle multiple invalid servers error" in {
        val exception: InvalidBootstrapServersException =
          new InvalidBootstrapServersException("Invalid bootstrap server format: badhost, anotherBad. Expected format: host:port or host1:port1,host2:port2")

        exception.getMessage should include("badhost")
        exception.getMessage should include("anotherBad")
      }
    }
  }

  // ========== EXCEPTION HIERARCHY TESTS ==========

  "BlockStorageExceptions hierarchy" when {

    "catching exceptions polymorphically" should {

      "catch all subclasses as BlockStorageException" in {
        val exceptions: List[BlockStorageException] = List(
          new MissingFeaturesDirectoryException("Test"),
          new EmptyFeaturesDirectoryException("Test"),
          new MissingTopicDirectiveFileException("Test"),
          new InvalidTopicDirectiveFormatException("Test"),
          new BucketUriParseException("Test"),
          new StreamingException("Test", new RuntimeException()),
          new DuplicateTopicException("Test"),
          new InvalidBootstrapServersException("Test")
        )

        exceptions.foreach { ex =>
          ex shouldBe a[BlockStorageException]
          ex shouldBe a[RuntimeException]
        }
      }

      "distinguish between specific exception types" in {
        try {
          throw new MissingFeaturesDirectoryException("Test")
        } catch {
          case _: MissingFeaturesDirectoryException => succeed
          case _: EmptyFeaturesDirectoryException => fail("Wrong exception type")
          case _: BlockStorageException => fail("Should match specific type first")
        }
      }
    }

    "exception handling patterns" should {

      "support specific catch before general catch" in {
        def throwSpecificException(): Unit = {
          throw new BucketUriParseException("Invalid URI")
        }

        var caughtSpecific: Boolean = false
        var caughtGeneral: Boolean = false

        try {
          throwSpecificException()
        } catch {
          case _: BucketUriParseException => caughtSpecific = true
          case _: BlockStorageException => caughtGeneral = true
        }

        caughtSpecific shouldBe true
        caughtGeneral shouldBe false
      }

      "allow recovery with pattern matching" in {
        def riskyOperation(): Either[BlockStorageException, String] = {
          try {
            throw new MissingFeaturesDirectoryException("Features missing")
          } catch {
            case ex: BlockStorageException => Left(ex)
          }
        }

        riskyOperation() match {
          case Left(_: MissingFeaturesDirectoryException) => succeed
          case Left(_) => fail("Should match specific exception type")
          case Right(_) => fail("Should return Left")
        }
      }
    }
  }
}
