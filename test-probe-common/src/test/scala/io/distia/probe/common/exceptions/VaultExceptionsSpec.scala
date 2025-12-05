package io.distia.probe
package common
package exceptions

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Unit tests for VaultExceptions
 * Tests exception hierarchy, construction, classification, and HTTP status mapping
 */
private[common] class VaultExceptionsSpec extends AnyWordSpec with Matchers {

  // ========== VAULTEXCEPTIONTYPE TESTS ==========

  "VaultExceptionType" when {

    "Transient case object" should {

      "be a VaultExceptionType" in {
        Transient shouldBe a[VaultExceptionType]
      }
    }

    "NonTransient case object" should {

      "be a VaultExceptionType" in {
        NonTransient shouldBe a[VaultExceptionType]
      }
    }
  }

  // ========== VAULTEXCEPTION TESTS ==========

  "VaultException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: VaultException = new VaultException("Test vault error")

        exception.getMessage shouldBe "Test vault error"
      }

      "have null cause" in {
        val exception: VaultException = new VaultException("Test vault error")

        exception.getCause shouldBe null
      }

      "default to NonTransient type" in {
        val exception: VaultException = new VaultException("Test vault error")

        exception.exceptionType shouldBe NonTransient
      }

      "be throwable" in {
        intercept[VaultException] {
          throw new VaultException("Test error")
        }
      }
    }

    "constructed with message and cause" should {

      "store message correctly" in {
        val cause: Throwable = new RuntimeException("Underlying cause")
        val exception: VaultException = new VaultException("Test error", cause)

        exception.getMessage shouldBe "Test error"
      }

      "store cause correctly" in {
        val cause: Throwable = new RuntimeException("Underlying cause")
        val exception: VaultException = new VaultException("Test error", cause)

        exception.getCause shouldBe cause
        exception.getCause.getMessage shouldBe "Underlying cause"
      }

      "preserve exception chain" in {
        val rootCause: Throwable = new IllegalArgumentException("Root cause")
        val intermediateCause: Throwable = new RuntimeException("Intermediate", rootCause)
        val exception: VaultException = new VaultException("Top level", intermediateCause)

        exception.getCause.getCause shouldBe rootCause
      }
    }

    "constructed with all parameters" should {

      "allow Transient exception type" in {
        val exception: VaultException = new VaultException("Test", null, Transient)

        exception.exceptionType shouldBe Transient
      }

      "allow NonTransient exception type" in {
        val exception: VaultException = new VaultException("Test", null, NonTransient)

        exception.exceptionType shouldBe NonTransient
      }
    }

    "caught as RuntimeException" should {

      "be catchable as RuntimeException" in {
        try {
          throw new VaultException("Test")
        } catch {
          case _: RuntimeException => succeed
          case _: Throwable => fail("Should be catchable as RuntimeException")
        }
      }
    }
  }

  // ========== TRANSIENTVAULTEXCEPTION TESTS ==========

  "TransientVaultException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: TransientVaultException = new TransientVaultException("Transient error")

        exception.getMessage shouldBe "Transient error"
      }

      "have Transient exception type" in {
        val exception: TransientVaultException = new TransientVaultException("Transient error")

        exception.exceptionType shouldBe Transient
      }

      "extend VaultException" in {
        val exception: TransientVaultException = new TransientVaultException("Test")

        exception shouldBe a[VaultException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new java.net.SocketTimeoutException("Connection timeout")
        val exception: TransientVaultException = new TransientVaultException("Network issue", cause)

        exception.getMessage shouldBe "Network issue"
        exception.getCause shouldBe cause
      }
    }

    "caught as VaultException" should {

      "be catchable as parent type" in {
        try {
          throw new TransientVaultException("Test")
        } catch {
          case _: VaultException => succeed
          case _: Throwable => fail("Should be catchable as VaultException")
        }
      }
    }
  }

  // ========== VAULTTIMEOUTEXCEPTION TESTS ==========

  "VaultTimeoutException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: VaultTimeoutException = new VaultTimeoutException("Timeout occurred")

        exception.getMessage shouldBe "Timeout occurred"
      }

      "have Transient exception type" in {
        val exception: VaultTimeoutException = new VaultTimeoutException("Timeout")

        exception.exceptionType shouldBe Transient
      }

      "extend TransientVaultException" in {
        val exception: VaultTimeoutException = new VaultTimeoutException("Test")

        exception shouldBe a[TransientVaultException]
      }

      "extend VaultException" in {
        val exception: VaultTimeoutException = new VaultTimeoutException("Test")

        exception shouldBe a[VaultException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new java.net.SocketTimeoutException("Socket timeout")
        val exception: VaultTimeoutException = new VaultTimeoutException("Vault timeout", cause)

        exception.getMessage shouldBe "Vault timeout"
        exception.getCause shouldBe cause
      }
    }
  }

  // ========== VAULTRATELIMITEXCEPTION TESTS ==========

  "VaultRateLimitException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: VaultRateLimitException = new VaultRateLimitException("Rate limit exceeded")

        exception.getMessage shouldBe "Rate limit exceeded"
      }

      "have Transient exception type" in {
        val exception: VaultRateLimitException = new VaultRateLimitException("Rate limited")

        exception.exceptionType shouldBe Transient
      }

      "extend TransientVaultException" in {
        val exception: VaultRateLimitException = new VaultRateLimitException("Test")

        exception shouldBe a[TransientVaultException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new RuntimeException("AWS throttling")
        val exception: VaultRateLimitException = new VaultRateLimitException("Rate limit", cause)

        exception.getMessage shouldBe "Rate limit"
        exception.getCause shouldBe cause
      }
    }
  }

  // ========== VAULTSERVICEUNAVAILABLEEXCEPTION TESTS ==========

  "VaultServiceUnavailableException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: VaultServiceUnavailableException =
          new VaultServiceUnavailableException("Service unavailable")

        exception.getMessage shouldBe "Service unavailable"
      }

      "have Transient exception type" in {
        val exception: VaultServiceUnavailableException =
          new VaultServiceUnavailableException("Unavailable")

        exception.exceptionType shouldBe Transient
      }

      "extend TransientVaultException" in {
        val exception: VaultServiceUnavailableException =
          new VaultServiceUnavailableException("Test")

        exception shouldBe a[TransientVaultException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new java.net.ConnectException("Connection refused")
        val exception: VaultServiceUnavailableException =
          new VaultServiceUnavailableException("Vault unavailable", cause)

        exception.getMessage shouldBe "Vault unavailable"
        exception.getCause shouldBe cause
      }
    }
  }

  // ========== NONTRANSIENTVAULTEXCEPTION TESTS ==========

  "NonTransientVaultException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: NonTransientVaultException =
          new NonTransientVaultException("Non-transient error")

        exception.getMessage shouldBe "Non-transient error"
      }

      "have NonTransient exception type" in {
        val exception: NonTransientVaultException =
          new NonTransientVaultException("Error")

        exception.exceptionType shouldBe NonTransient
      }

      "extend VaultException" in {
        val exception: NonTransientVaultException =
          new NonTransientVaultException("Test")

        exception shouldBe a[VaultException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new IllegalArgumentException("Bad argument")
        val exception: NonTransientVaultException =
          new NonTransientVaultException("Invalid request", cause)

        exception.getMessage shouldBe "Invalid request"
        exception.getCause shouldBe cause
      }
    }
  }

  // ========== VAULTAUTHEXCEPTION TESTS ==========

  "VaultAuthException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: VaultAuthException = new VaultAuthException("Authentication failed")

        exception.getMessage shouldBe "Authentication failed"
      }

      "have NonTransient exception type" in {
        val exception: VaultAuthException = new VaultAuthException("Auth error")

        exception.exceptionType shouldBe NonTransient
      }

      "extend NonTransientVaultException" in {
        val exception: VaultAuthException = new VaultAuthException("Test")

        exception shouldBe a[NonTransientVaultException]
      }

      "extend VaultException" in {
        val exception: VaultAuthException = new VaultAuthException("Test")

        exception shouldBe a[VaultException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new RuntimeException("Token expired")
        val exception: VaultAuthException = new VaultAuthException("Auth failed", cause)

        exception.getMessage shouldBe "Auth failed"
        exception.getCause shouldBe cause
      }
    }
  }

  // ========== VAULTMAPPINGEXCEPTION TESTS ==========

  "VaultMappingException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: VaultMappingException = new VaultMappingException("Mapping failed")

        exception.getMessage shouldBe "Mapping failed"
      }

      "have NonTransient exception type" in {
        val exception: VaultMappingException = new VaultMappingException("Mapping error")

        exception.exceptionType shouldBe NonTransient
      }

      "extend NonTransientVaultException" in {
        val exception: VaultMappingException = new VaultMappingException("Test")

        exception shouldBe a[NonTransientVaultException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new io.circe.ParsingFailure("Invalid JSON", null)
        val exception: VaultMappingException = new VaultMappingException("Parse error", cause)

        exception.getMessage shouldBe "Parse error"
        exception.getCause shouldBe cause
      }
    }
  }

  // ========== VAULTNOTFOUNDEXCEPTION TESTS ==========

  "VaultNotFoundException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: VaultNotFoundException = new VaultNotFoundException("Not found")

        exception.getMessage shouldBe "Not found"
      }

      "have NonTransient exception type" in {
        val exception: VaultNotFoundException = new VaultNotFoundException("404")

        exception.exceptionType shouldBe NonTransient
      }

      "extend NonTransientVaultException" in {
        val exception: VaultNotFoundException = new VaultNotFoundException("Test")

        exception shouldBe a[NonTransientVaultException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new RuntimeException("Function deleted")
        val exception: VaultNotFoundException = new VaultNotFoundException("Function missing", cause)

        exception.getMessage shouldBe "Function missing"
        exception.getCause shouldBe cause
      }
    }
  }

  // ========== VAULTCONFIGURATIONEXCEPTION TESTS ==========

  "VaultConfigurationException" when {

    "constructed with message only" should {

      "store message correctly" in {
        val exception: VaultConfigurationException =
          new VaultConfigurationException("Invalid config")

        exception.getMessage shouldBe "Invalid config"
      }

      "have NonTransient exception type" in {
        val exception: VaultConfigurationException =
          new VaultConfigurationException("Config error")

        exception.exceptionType shouldBe NonTransient
      }

      "extend NonTransientVaultException" in {
        val exception: VaultConfigurationException =
          new VaultConfigurationException("Test")

        exception shouldBe a[NonTransientVaultException]
      }
    }

    "constructed with message and cause" should {

      "store both message and cause" in {
        val cause: Throwable = new IllegalArgumentException("Missing required field")
        val exception: VaultConfigurationException =
          new VaultConfigurationException("Config validation failed", cause)

        exception.getMessage shouldBe "Config validation failed"
        exception.getCause shouldBe cause
      }
    }
  }

  // ========== VAULTEXCEPTION COMPANION OBJECT TESTS ==========

  "VaultException companion object" when {

    "isTransient" should {

      "return true for TransientVaultException" in {
        val exception = new TransientVaultException("Transient")

        VaultException.isTransient(exception) shouldBe true
      }

      "return true for VaultTimeoutException" in {
        val exception = new VaultTimeoutException("Timeout")

        VaultException.isTransient(exception) shouldBe true
      }

      "return true for VaultRateLimitException" in {
        val exception = new VaultRateLimitException("Rate limit")

        VaultException.isTransient(exception) shouldBe true
      }

      "return true for VaultServiceUnavailableException" in {
        val exception = new VaultServiceUnavailableException("Unavailable")

        VaultException.isTransient(exception) shouldBe true
      }

      "return true for java.net.SocketTimeoutException" in {
        val exception = new java.net.SocketTimeoutException("Socket timeout")

        VaultException.isTransient(exception) shouldBe true
      }

      "return true for java.net.ConnectException" in {
        val exception = new java.net.ConnectException("Connection refused")

        VaultException.isTransient(exception) shouldBe true
      }

      "return true for IOException with timeout in message" in {
        val exception = new java.io.IOException("Connection timeout occurred")

        VaultException.isTransient(exception) shouldBe true
      }

      "return false for IOException without timeout in message" in {
        val exception = new java.io.IOException("File not found")

        VaultException.isTransient(exception) shouldBe false
      }

      "return false for IOException with null message" in {
        val exception = new java.io.IOException(null: String)

        VaultException.isTransient(exception) shouldBe false
      }

      "return false for NonTransientVaultException" in {
        val exception = new NonTransientVaultException("Non-transient")

        VaultException.isTransient(exception) shouldBe false
      }

      "return false for VaultAuthException" in {
        val exception = new VaultAuthException("Auth error")

        VaultException.isTransient(exception) shouldBe false
      }

      "return false for VaultMappingException" in {
        val exception = new VaultMappingException("Mapping error")

        VaultException.isTransient(exception) shouldBe false
      }

      "return false for generic RuntimeException" in {
        val exception = new RuntimeException("Generic error")

        VaultException.isTransient(exception) shouldBe false
      }
    }

    "isNonTransient" should {

      "return true for NonTransientVaultException" in {
        val exception = new NonTransientVaultException("Non-transient")

        VaultException.isNonTransient(exception) shouldBe true
      }

      "return true for VaultAuthException" in {
        val exception = new VaultAuthException("Auth error")

        VaultException.isNonTransient(exception) shouldBe true
      }

      "return true for VaultMappingException" in {
        val exception = new VaultMappingException("Mapping error")

        VaultException.isNonTransient(exception) shouldBe true
      }

      "return true for VaultNotFoundException" in {
        val exception = new VaultNotFoundException("Not found")

        VaultException.isNonTransient(exception) shouldBe true
      }

      "return true for VaultConfigurationException" in {
        val exception = new VaultConfigurationException("Config error")

        VaultException.isNonTransient(exception) shouldBe true
      }

      "return false for TransientVaultException" in {
        val exception = new TransientVaultException("Transient")

        VaultException.isNonTransient(exception) shouldBe false
      }

      "return false for VaultTimeoutException" in {
        val exception = new VaultTimeoutException("Timeout")

        VaultException.isNonTransient(exception) shouldBe false
      }

      "return false for generic RuntimeException" in {
        val exception = new RuntimeException("Generic error")

        VaultException.isNonTransient(exception) shouldBe false
      }
    }

    "fromHttpStatus" should {

      "return VaultAuthException for 401" in {
        val exception = VaultException.fromHttpStatus(401, "Unauthorized")

        exception shouldBe a[VaultAuthException]
        exception.getMessage should include("401")
        exception.getMessage should include("Unauthorized")
      }

      "return VaultAuthException for 403" in {
        val exception = VaultException.fromHttpStatus(403, "Forbidden")

        exception shouldBe a[VaultAuthException]
        exception.getMessage should include("403")
      }

      "return VaultNotFoundException for 404" in {
        val exception = VaultException.fromHttpStatus(404, "Not found")

        exception shouldBe a[VaultNotFoundException]
        exception.getMessage should include("404")
      }

      "return VaultRateLimitException for 429" in {
        val exception = VaultException.fromHttpStatus(429, "Too many requests")

        exception shouldBe a[VaultRateLimitException]
        exception.getMessage should include("429")
      }

      "return NonTransientVaultException for 500" in {
        val exception = VaultException.fromHttpStatus(500, "Internal error")

        exception shouldBe a[NonTransientVaultException]
        exception.getMessage should include("500")
      }

      "return VaultServiceUnavailableException for 503" in {
        val exception = VaultException.fromHttpStatus(503, "Service unavailable")

        exception shouldBe a[VaultServiceUnavailableException]
        exception.getMessage should include("503")
      }

      "return NonTransientVaultException for 4xx client errors" in {
        val exception400 = VaultException.fromHttpStatus(400, "Bad request")
        val exception422 = VaultException.fromHttpStatus(422, "Unprocessable entity")

        exception400 shouldBe a[NonTransientVaultException]
        exception422 shouldBe a[NonTransientVaultException]
        exception400.getMessage should include("400")
        exception422.getMessage should include("422")
      }

      "return VaultServiceUnavailableException for 5xx server errors" in {
        val exception502 = VaultException.fromHttpStatus(502, "Bad gateway")
        val exception504 = VaultException.fromHttpStatus(504, "Gateway timeout")

        exception502 shouldBe a[VaultServiceUnavailableException]
        exception504 shouldBe a[VaultServiceUnavailableException]
      }

      "return VaultException for unexpected status codes" in {
        val exception200 = VaultException.fromHttpStatus(200, "OK")
        val exception301 = VaultException.fromHttpStatus(301, "Redirect")

        exception200 shouldBe a[VaultException]
        exception301 shouldBe a[VaultException]
        exception200.getMessage should include("200")
      }

      "include message in exception message" in {
        val exception = VaultException.fromHttpStatus(401, "Token expired")

        exception.getMessage should include("Token expired")
      }

      "handle empty responseBody parameter" in {
        val exception = VaultException.fromHttpStatus(500, "Error", "")

        exception shouldBe a[NonTransientVaultException]
      }

      "handle non-empty responseBody parameter" in {
        val exception = VaultException.fromHttpStatus(500, "Error", "{\"detail\": \"Server error\"}")

        exception shouldBe a[NonTransientVaultException]
      }
    }
  }

  // ========== EXCEPTION HIERARCHY TESTS ==========

  "Exception hierarchy" when {

    "checking inheritance" should {

      "ensure all exceptions extend RuntimeException" in {
        new VaultException("test") shouldBe a[RuntimeException]
        new TransientVaultException("test") shouldBe a[RuntimeException]
        new NonTransientVaultException("test") shouldBe a[RuntimeException]
        new VaultTimeoutException("test") shouldBe a[RuntimeException]
        new VaultRateLimitException("test") shouldBe a[RuntimeException]
        new VaultServiceUnavailableException("test") shouldBe a[RuntimeException]
        new VaultAuthException("test") shouldBe a[RuntimeException]
        new VaultMappingException("test") shouldBe a[RuntimeException]
        new VaultNotFoundException("test") shouldBe a[RuntimeException]
        new VaultConfigurationException("test") shouldBe a[RuntimeException]
      }

      "ensure transient exceptions are correctly classified" in {
        VaultException.isTransient(new TransientVaultException("test")) shouldBe true
        VaultException.isTransient(new VaultTimeoutException("test")) shouldBe true
        VaultException.isTransient(new VaultRateLimitException("test")) shouldBe true
        VaultException.isTransient(new VaultServiceUnavailableException("test")) shouldBe true
      }

      "ensure non-transient exceptions are correctly classified" in {
        VaultException.isNonTransient(new NonTransientVaultException("test")) shouldBe true
        VaultException.isNonTransient(new VaultAuthException("test")) shouldBe true
        VaultException.isNonTransient(new VaultMappingException("test")) shouldBe true
        VaultException.isNonTransient(new VaultNotFoundException("test")) shouldBe true
        VaultException.isNonTransient(new VaultConfigurationException("test")) shouldBe true
      }
    }
  }
}
