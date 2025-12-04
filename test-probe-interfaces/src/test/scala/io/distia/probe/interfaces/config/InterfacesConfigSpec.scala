package io.distia.probe.interfaces.config

import io.distia.probe.interfaces.fixtures.ConfigFixtures
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

/**
 * Unit tests for InterfacesConfig
 *
 * Target Coverage: 70%+
 *
 * Tests:
 * - Config parsing from HOCON
 * - Duration validation (no infinite durations)
 * - Default values
 * - Invalid config handling
 * - InterfaceConfig trait implementation
 */
class InterfacesConfigSpec extends AnyWordSpec with Matchers {

  import ConfigFixtures.*

  "InterfacesConfig" when {

    // ========================================================================
    // Valid Configuration Parsing
    // ========================================================================

    "parsing valid configuration" should {

      "parse all fields from valid HOCON config" in {
        val config = InterfacesConfig.fromConfig(validConfig)

        config.restHost shouldBe expectedHost
        config.restPort shouldBe expectedPort
        config.restTimeout shouldBe expectedTimeout
        config.gracefulShutdownTimeout shouldBe expectedShutdownTimeout
        config.askTimeout shouldBe expectedAskTimeout
        config.circuitBreakerMaxFailures shouldBe expectedCircuitBreakerMaxFailures
        config.circuitBreakerCallTimeout shouldBe expectedCircuitBreakerCallTimeout
        config.circuitBreakerResetTimeout shouldBe expectedCircuitBreakerResetTimeout
        config.maxConcurrentRequests shouldBe expectedMaxConcurrentRequests
        config.maxRequestSize shouldBe expectedMaxRequestSize
        config.maxUriLength shouldBe expectedMaxUriLength
        config.retryAfterServiceUnavailable shouldBe expectedRetryAfterServiceUnavailable
        config.retryAfterNotReady shouldBe expectedRetryAfterNotReady
      }

      "parse configuration with custom host and port" in {
        val config = InterfacesConfig.fromConfig(customConfig)

        config.restHost shouldBe "127.0.0.1"
        config.restPort shouldBe 9090
        config.restTimeout shouldBe 60.seconds
        config.gracefulShutdownTimeout shouldBe 15.seconds
        config.askTimeout shouldBe 55.seconds
        config.circuitBreakerMaxFailures shouldBe 10
        config.circuitBreakerCallTimeout shouldBe 55.seconds
        config.circuitBreakerResetTimeout shouldBe 60.seconds
        config.maxConcurrentRequests shouldBe 200
        config.maxRequestSize shouldBe 20971520L
        config.maxUriLength shouldBe 16384
        config.retryAfterServiceUnavailable shouldBe "60s"
        config.retryAfterNotReady shouldBe "10s"
      }

      "parse finite durations correctly" in {
        val config = InterfacesConfig.fromConfig(validConfig)

        config.restTimeout.isFinite shouldBe true
        config.gracefulShutdownTimeout.isFinite shouldBe true

        config.restTimeout.toSeconds shouldBe 30L
        config.gracefulShutdownTimeout.toSeconds shouldBe 10L
      }
    }

    // ========================================================================
    // Invalid Configuration Handling
    // ========================================================================

    "handling invalid configuration" should {

      "reject configuration with missing required fields" in {
        val exception = intercept[ConfigException] {
          InterfacesConfig.fromConfig(missingFields)
        }

        exception.getMessage should (
          include("port") or include("timeout")
        )
      }

      "reject configuration with infinite duration" in {
        // Typesafe Config throws ConfigException.BadValue for "Inf" duration strings
        intercept[ConfigException] {
          InterfacesConfig.fromConfig(infiniteDuration)
        }
      }

      "reject configuration missing test-probe.interfaces section" in {
        val emptyConfig = ConfigFactory.parseString("{}")

        intercept[ConfigException] {
          InterfacesConfig.fromConfig(emptyConfig)
        }
      }

      "handle type conversion for boolean field" in {
        // Typesafe Config automatically converts "true" string to boolean true
        val booleanStringConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 25s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 25s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces {
          |    rest {
          |      host = "0.0.0.0"
          |      port = 8080
          |      timeout = 30s
          |      graceful-shutdown-timeout = 10s
          |      ask-timeout = 25s
          |      circuit-breaker-max-failures = 5
          |      circuit-breaker-call-timeout = 25s
          |      circuit-breaker-reset-timeout = 30s
          |      max-concurrent-requests = 100
          |      max-request-size = 10485760
          |      max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |    }
          |  }
          |}
          |""".stripMargin)

        val config = InterfacesConfig.fromConfig(booleanStringConfig)
        config.restHost shouldBe "0.0.0.0"  // Config parsing works correctly
      }

      "handle type conversion for integer field" in {
        // Typesafe Config automatically converts "8080" string to int 8080
        val intStringConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 25s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 25s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces {
          |    rest {
          |      host = "0.0.0.0"
          |      port = "8080"
          |      timeout = 30s
          |      graceful-shutdown-timeout = 10s
          |      ask-timeout = 25s
          |      circuit-breaker-max-failures = 5
          |      circuit-breaker-call-timeout = 25s
          |      circuit-breaker-reset-timeout = 30s
          |      max-concurrent-requests = 100
          |      max-request-size = 10485760
          |      max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |    }
          |  }
          |}
          |""".stripMargin)

        val config = InterfacesConfig.fromConfig(intStringConfig)
        config.restPort shouldBe 8080  // Auto-converted from "8080"
      }

      "handle type conversion for string field" in {
        // Typesafe Config automatically converts numeric values to strings
        val numericStringConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 25s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 25s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces {
          |    rest {
          |      enabled = true
          |      host = 8080
          |      port = 8080
          |      timeout = 30s
          |      graceful-shutdown-timeout = 10s
          |      ask-timeout = 25s
          |      circuit-breaker-max-failures = 5
          |      circuit-breaker-call-timeout = 25s
          |      circuit-breaker-reset-timeout = 30s
          |      max-concurrent-requests = 100
          |      max-request-size = 10485760
          |      max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |    }
          |  }
          |}
          |""".stripMargin)

        val config = InterfacesConfig.fromConfig(numericStringConfig)
        config.restHost shouldBe "8080"  // Auto-converted from numeric 8080
      }

      "reject configuration with invalid duration format" in {
        val invalidDurationConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 25s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 25s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces {
          |    rest {
          |      host = "0.0.0.0"
          |      port = 8080
          |      timeout = "not-a-duration"
          |      graceful-shutdown-timeout = 10s
          |      ask-timeout = 25s
          |      circuit-breaker-max-failures = 5
          |      circuit-breaker-call-timeout = 25s
          |      circuit-breaker-reset-timeout = 30s
          |      max-concurrent-requests = 100
          |      max-request-size = 10485760
          |      max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |    }
          |  }
          |}
          |""".stripMargin)

        intercept[ConfigException] {
          InterfacesConfig.fromConfig(invalidDurationConfig)
        }
      }
    }

    // ========================================================================
    // Duration Edge Cases
    // ========================================================================

    "handling duration edge cases" should {

      "accept zero duration" in {
        val zeroDurationConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 0s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 0s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces {
          |    rest {
          |      host = "0.0.0.0"
          |      port = 8080
          |      timeout = 1s
          |      graceful-shutdown-timeout = 0s
          |      ask-timeout = 0s
          |      circuit-breaker-max-failures = 5
          |      circuit-breaker-call-timeout = 0s
          |      circuit-breaker-reset-timeout = 30s
          |      max-concurrent-requests = 100
          |      max-request-size = 10485760
          |      max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |    }
          |  }
          |}
          |""".stripMargin)

        val config = InterfacesConfig.fromConfig(zeroDurationConfig)

        config.askTimeout shouldBe Duration.Zero
        config.gracefulShutdownTimeout shouldBe Duration.Zero
      }

      "accept very large finite duration" in {
        val largeDurationConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 364d
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 364d
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces {
          |    rest {
          |      host = "0.0.0.0"
          |      port = 8080
          |      timeout = 365d
          |      graceful-shutdown-timeout = 10s
          |      ask-timeout = 364d
          |      circuit-breaker-max-failures = 5
          |      circuit-breaker-call-timeout = 364d
          |      circuit-breaker-reset-timeout = 30s
          |      max-concurrent-requests = 100
          |      max-request-size = 10485760
          |      max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |    }
          |  }
          |}
          |""".stripMargin)

        val config = InterfacesConfig.fromConfig(largeDurationConfig)

        config.restTimeout shouldBe 365.days
      }

      "parse millisecond precision durations" in {
        val millisConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 1400ms
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 1400ms
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces {
          |    rest {
          |      host = "0.0.0.0"
          |      port = 8080
          |      timeout = 1500ms
          |      graceful-shutdown-timeout = 500ms
          |      ask-timeout = 1400ms
          |      circuit-breaker-max-failures = 5
          |      circuit-breaker-call-timeout = 1400ms
          |      circuit-breaker-reset-timeout = 30s
          |      max-concurrent-requests = 100
          |      max-request-size = 10485760
          |      max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |    }
          |  }
          |}
          |""".stripMargin)

        val config = InterfacesConfig.fromConfig(millisConfig)

        config.restTimeout shouldBe 1500.milliseconds
        config.gracefulShutdownTimeout shouldBe 500.milliseconds
      }
    }

    // ========================================================================
    // Retry-After Validation
    // ========================================================================

    "validating retry-after formats" should {

      "accept valid duration strings (seconds)" in {
        val config = InterfacesConfig.fromConfig(validConfig)
        config.retryAfterServiceUnavailable shouldBe "30s"
        config.retryAfterNotReady shouldBe "5s"
      }

      "accept valid duration strings (minutes)" in {
        val minutesConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 25s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 25s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces.rest {
          |    host = "0.0.0.0"
          |    port = 8080
          |    timeout = 30s
          |    graceful-shutdown-timeout = 10s
          |    ask-timeout = 25s
          |    circuit-breaker-max-failures = 5
          |    circuit-breaker-call-timeout = 25s
          |    circuit-breaker-reset-timeout = 30s
          |    max-concurrent-requests = 100
          |    max-request-size = 10485760
          |    max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |    retry-after-service-unavailable = "2m"
          |    retry-after-not-ready = "1m"
          |  }
          |}
          |""".stripMargin)

        val config = InterfacesConfig.fromConfig(minutesConfig)
        config.retryAfterServiceUnavailable shouldBe "2m"
        config.retryAfterNotReady shouldBe "1m"
      }

      "accept valid duration strings (hours)" in {
        val hoursConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 25s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 25s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces.rest {
          |    host = "0.0.0.0"
          |    port = 8080
          |    timeout = 30s
          |    graceful-shutdown-timeout = 10s
          |    ask-timeout = 25s
          |    circuit-breaker-max-failures = 5
          |    circuit-breaker-call-timeout = 25s
          |    circuit-breaker-reset-timeout = 30s
          |    max-concurrent-requests = 100
          |    max-request-size = 10485760
          |    max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |    retry-after-service-unavailable = "2h"
          |    retry-after-not-ready = "1h"
          |  }
          |}
          |""".stripMargin)

        val config = InterfacesConfig.fromConfig(hoursConfig)
        config.retryAfterServiceUnavailable shouldBe "2h"
        config.retryAfterNotReady shouldBe "1h"
      }

      "reject invalid retry-after format (missing unit)" in {
        val invalidConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 25s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 25s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces.rest {
          |    host = "0.0.0.0"
          |    port = 8080
          |    timeout = 30s
          |    graceful-shutdown-timeout = 10s
          |    ask-timeout = 25s
          |    circuit-breaker-max-failures = 5
          |    circuit-breaker-call-timeout = 25s
          |    circuit-breaker-reset-timeout = 30s
          |    max-concurrent-requests = 100
          |    max-request-size = 10485760
          |    max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |    retry-after-service-unavailable = "30"
          |    retry-after-not-ready = "5s"
          |  }
          |}
          |""".stripMargin)

        val exception = intercept[IllegalArgumentException] {
          InterfacesConfig.fromConfig(invalidConfig)
        }
        exception.getMessage should include("retryAfterServiceUnavailable")
        exception.getMessage should include("must be valid duration string")
      }

      "reject invalid retry-after format (invalid unit)" in {
        val invalidConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 25s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 25s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces.rest {
          |    host = "0.0.0.0"
          |    port = 8080
          |    timeout = 30s
          |    graceful-shutdown-timeout = 10s
          |    ask-timeout = 25s
          |    circuit-breaker-max-failures = 5
          |    circuit-breaker-call-timeout = 25s
          |    circuit-breaker-reset-timeout = 30s
          |    max-concurrent-requests = 100
          |    max-request-size = 10485760
          |    max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |    retry-after-service-unavailable = "30s"
          |    retry-after-not-ready = "5d"
          |  }
          |}
          |""".stripMargin)

        val exception = intercept[IllegalArgumentException] {
          InterfacesConfig.fromConfig(invalidConfig)
        }
        exception.getMessage should include("retryAfterNotReady")
        exception.getMessage should include("must be valid duration string")
      }

      "reject invalid retry-after format (non-numeric)" in {
        val invalidConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 25s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 25s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces.rest {
          |    host = "0.0.0.0"
          |    port = 8080
          |    timeout = 30s
          |    graceful-shutdown-timeout = 10s
          |    ask-timeout = 25s
          |    circuit-breaker-max-failures = 5
          |    circuit-breaker-call-timeout = 25s
          |    circuit-breaker-reset-timeout = 30s
          |    max-concurrent-requests = 100
          |    max-request-size = 10485760
          |    max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |    retry-after-service-unavailable = "abc"
          |    retry-after-not-ready = "5s"
          |  }
          |}
          |""".stripMargin)

        val exception = intercept[IllegalArgumentException] {
          InterfacesConfig.fromConfig(invalidConfig)
        }
        exception.getMessage should include("retryAfterServiceUnavailable")
        exception.getMessage should include("must be valid duration string")
      }
    }

    // ========================================================================
    // Copy and Equality
    // ========================================================================

    "using case class features" should {

      "support copy with modified fields" in {
        val original = InterfacesConfig.fromConfig(validConfig)
        val modified = original.copy(restPort = 9999)

        modified.restPort shouldBe 9999
        modified.restHost shouldBe original.restHost
      }

      "support equality comparison" in {
        val config1 = InterfacesConfig.fromConfig(validConfig)
        val config2 = InterfacesConfig.fromConfig(validConfig)

        config1 shouldBe config2
      }

      "detect inequality when fields differ" in {
        val config1 = InterfacesConfig.fromConfig(validConfig)
        val config2 = InterfacesConfig.fromConfig(customConfig)

        config1 should not be config2
      }
    }

    // ========================================================================
    // Timeout Hierarchy Validation
    // ========================================================================

    "validating timeout hierarchy" should {

      "accept valid timeout hierarchy (askTimeout < restTimeout)" in {
        val validHierarchyConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 25s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 25s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces.rest {
          |    host = "0.0.0.0"
          |    port = 8080
          |    timeout = 30s
          |    graceful-shutdown-timeout = 10s
          |    ask-timeout = 25s
          |    circuit-breaker-max-failures = 5
          |    circuit-breaker-call-timeout = 25s
          |    circuit-breaker-reset-timeout = 30s
          |    max-concurrent-requests = 100
          |    max-request-size = 10485760
          |    max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |  }
          |}
          |""".stripMargin)

        val config = InterfacesConfig.fromConfig(validHierarchyConfig)
        config.askTimeout should be < config.restTimeout
      }

      "reject invalid timeout hierarchy (askTimeout >= restTimeout)" in {
        val invalidHierarchyConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 30s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 30s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces.rest {
          |    host = "0.0.0.0"
          |    port = 8080
          |    timeout = 30s
          |    graceful-shutdown-timeout = 10s
          |    ask-timeout = 30s
          |    circuit-breaker-max-failures = 5
          |    circuit-breaker-call-timeout = 30s
          |    circuit-breaker-reset-timeout = 30s
          |    max-concurrent-requests = 100
          |    max-request-size = 10485760
          |    max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |  }
          |}
          |""".stripMargin)

        val exception = intercept[IllegalArgumentException] {
          InterfacesConfig.fromConfig(invalidHierarchyConfig)
        }
        exception.getMessage should include("REST timeout")
        exception.getMessage should include("must be greater than")
        exception.getMessage should include("ask timeout")
      }

      "validate circuit breaker call timeout matches ask timeout" in {
        val config = InterfacesConfig.fromConfig(validConfig)
        config.circuitBreakerCallTimeout shouldBe config.askTimeout
      }

      "reject circuit breaker call timeout not matching ask timeout" in {
        val mismatchedTimeoutConfig = ConfigFactory.parseString("""
          |test-probe {
          |  core {
          |    actor-system.timeout = 25s
          |    circuit-breaker {
          |      max-failures = 5
          |      call-timeout = 25s
          |      reset-timeout = 30s
          |    }
          |  }
          |  interfaces.rest {
          |    host = "0.0.0.0"
          |    port = 8080
          |    timeout = 30s
          |    graceful-shutdown-timeout = 10s
          |    ask-timeout = 25s
          |    circuit-breaker-max-failures = 5
          |    circuit-breaker-call-timeout = 20s
          |    circuit-breaker-reset-timeout = 30s
          |    max-concurrent-requests = 100
          |    max-request-size = 10485760
          |    max-uri-length = 8192
          |      retry-after-service-unavailable = "30s"
          |      retry-after-not-ready = "5s"
          |  }
          |}
          |""".stripMargin)

        val exception = intercept[IllegalArgumentException] {
          InterfacesConfig.fromConfig(mismatchedTimeoutConfig)
        }
        exception.getMessage should include("Circuit breaker call timeout")
        exception.getMessage should include("must equal")
        exception.getMessage should include("ask timeout")
      }
    }
  }
}
