package io.distia.probe.interfaces.fixtures

import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.duration.*

/**
 * Test fixtures for configuration testing
 *
 * Provides HOCON config strings and Config objects for testing.
 */
object ConfigFixtures {

  /**
   * Valid REST interface configuration with all fields
   */
  val validRestConfig: String =
    """
      |test-probe {
      |  core {
      |    actor-system {
      |      timeout = 25s
      |    }
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
      |""".stripMargin

  /**
   * REST interface disabled
   */
  val disabledRestConfig: String =
    """
      |test-probe {
      |  core {
      |    actor-system {
      |      timeout = 25s
      |    }
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
      |""".stripMargin

  /**
   * Custom host and port
   */
  val customHostPortConfig: String =
    """
      |test-probe {
      |  core {
      |    actor-system {
      |      timeout = 55s
      |    }
      |    circuit-breaker {
      |      max-failures = 10
      |      call-timeout = 55s
      |      reset-timeout = 60s
      |    }
      |  }
      |  interfaces {
      |    rest {
      |      enabled = true
      |      host = "127.0.0.1"
      |      port = 9090
      |      timeout = 60s
      |      graceful-shutdown-timeout = 15s
      |      ask-timeout = 55s
      |      circuit-breaker-max-failures = 10
      |      circuit-breaker-call-timeout = 55s
      |      circuit-breaker-reset-timeout = 60s
      |      max-concurrent-requests = 200
      |      max-request-size = 20971520
      |      max-uri-length = 16384
      |      retry-after-service-unavailable = "60s"
      |      retry-after-not-ready = "10s"
      |    }
      |  }
      |}
      |""".stripMargin

  /**
   * Invalid config - missing required fields
   */
  val missingFieldsConfig: String =
    """
      |test-probe {
      |  interfaces {
      |    rest {
      |      host = "0.0.0.0"
      |    }
      |  }
      |}
      |""".stripMargin

  /**
   * Invalid config - infinite duration (not allowed)
   */
  val infiniteDurationConfig: String =
    """
      |test-probe {
      |  interfaces {
      |    rest {
      |      host = "0.0.0.0"
      |      port = 8080
      |      timeout = Inf
      |      graceful-shutdown-timeout = 10s
      |    }
      |  }
      |}
      |""".stripMargin

  /**
   * Invalid config - negative duration
   */
  val negativeDurationConfig: String =
    """
      |test-probe {
      |  interfaces {
      |    rest {
      |      host = "0.0.0.0"
      |      port = 8080
      |      timeout = -5s
      |      graceful-shutdown-timeout = 10s
      |    }
      |  }
      |}
      |""".stripMargin

  // Parsed Config objects

  def validConfig: Config = ConfigFactory.parseString(validRestConfig)
  def disabledConfig: Config = ConfigFactory.parseString(disabledRestConfig)
  def customConfig: Config = ConfigFactory.parseString(customHostPortConfig)
  def missingFields: Config = ConfigFactory.parseString(missingFieldsConfig)
  def infiniteDuration: Config = ConfigFactory.parseString(infiniteDurationConfig)

  // Expected values for valid config
  val expectedHost = "0.0.0.0"
  val expectedPort = 8080
  val expectedTimeout: FiniteDuration = 30.seconds
  val expectedShutdownTimeout: FiniteDuration = 10.seconds
  val expectedAskTimeout: FiniteDuration = 25.seconds
  val expectedCircuitBreakerMaxFailures = 5
  val expectedCircuitBreakerCallTimeout: FiniteDuration = 25.seconds
  val expectedCircuitBreakerResetTimeout: FiniteDuration = 30.seconds
  val expectedMaxConcurrentRequests = 100
  val expectedMaxRequestSize: Long = 10485760L
  val expectedMaxUriLength = 8192
  val expectedRetryAfterServiceUnavailable = "30s"
  val expectedRetryAfterNotReady = "5s"
}
