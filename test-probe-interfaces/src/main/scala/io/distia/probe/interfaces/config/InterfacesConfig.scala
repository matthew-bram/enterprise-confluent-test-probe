package io.distia.probe.interfaces.config

import com.typesafe.config.Config
import scala.concurrent.duration.{Duration, FiniteDuration}

/**
 * Type-safe configuration for interfaces module
 *
 * Loaded from reference.conf (test-probe.interfaces.*)
 * Each interface type (REST, CLI, gRPC) has its own section.
 *
 * Timeout Hierarchy (validated at construction):
 * - askTimeout (25s) < restTimeout (30s)
 * - circuitBreakerCallTimeout must equal askTimeout
 *
 * Visibility: private[interfaces] maintains module encapsulation.
 * All methods remain public for comprehensive unit testing.
 *
 * @param restHost Host to bind REST server (0.0.0.0 for all interfaces)
 * @param restPort Port for REST server
 * @param restTimeout Request timeout for REST operations (must be > askTimeout)
 * @param gracefulShutdownTimeout Time to wait for in-flight requests during shutdown
 * @param askTimeout Timeout for actor ask operations (must be < restTimeout)
 * @param circuitBreakerMaxFailures Max consecutive failures before circuit opens
 * @param circuitBreakerCallTimeout Circuit breaker call timeout (must equal askTimeout)
 * @param circuitBreakerResetTimeout Time to wait before attempting recovery
 * @param maxConcurrentRequests Maximum concurrent HTTP requests
 * @param maxRequestSize Maximum HTTP request body size in bytes
 * @param maxUriLength Maximum HTTP URI length in characters
 * @param retryAfterServiceUnavailable Retry-After value for 503 circuit breaker open responses
 * @param retryAfterNotReady Retry-After value for 503 system initializing responses
 */
private[interfaces] case class InterfacesConfig(
  restHost: String,
  restPort: Int,
  restTimeout: FiniteDuration,
  gracefulShutdownTimeout: FiniteDuration,
  askTimeout: FiniteDuration,
  circuitBreakerMaxFailures: Int,
  circuitBreakerCallTimeout: FiniteDuration,
  circuitBreakerResetTimeout: FiniteDuration,
  maxConcurrentRequests: Int,
  maxRequestSize: Long,
  maxUriLength: Int,
  retryAfterServiceUnavailable: String,
  retryAfterNotReady: String
) {

  // Validate timeout hierarchy: ask timeout must trigger before HTTP timeout
  require(
    restTimeout > askTimeout,
    s"""REST timeout ($restTimeout) must be greater than ask timeout ($askTimeout).
       |This ensures actor ask timeout triggers before HTTP client timeout.""".stripMargin
  )

  // Validate circuit breaker call timeout matches ask timeout
  require(
    circuitBreakerCallTimeout == askTimeout,
    s"""Circuit breaker call timeout ($circuitBreakerCallTimeout) must equal ask timeout ($askTimeout).
       |This ensures consistent timeout behavior across all actor asks.""".stripMargin
  )

  // Validate positive values
  require(maxConcurrentRequests > 0, s"maxConcurrentRequests must be positive, got: $maxConcurrentRequests")
  require(maxRequestSize > 0, s"maxRequestSize must be positive, got: $maxRequestSize")
  require(maxUriLength > 0, s"maxUriLength must be positive, got: $maxUriLength")
  require(circuitBreakerMaxFailures > 0, s"circuitBreakerMaxFailures must be positive, got: $circuitBreakerMaxFailures")

  // Validate retry-after format (simple duration string: number + unit)
  require(
    retryAfterServiceUnavailable.matches("\\d+[smh]"),
    s"retryAfterServiceUnavailable must be valid duration string (e.g., '30s', '1m'), got: $retryAfterServiceUnavailable"
  )
  require(
    retryAfterNotReady.matches("\\d+[smh]"),
    s"retryAfterNotReady must be valid duration string (e.g., '5s', '1m'), got: $retryAfterNotReady"
  )
}

private[interfaces] object InterfacesConfig {

  /**
   * Parse finite duration from config with type-safe pattern matching
   *
   * @param config Config object to read from
   * @param path Configuration path
   * @return Validated FiniteDuration
   * @throws IllegalArgumentException if duration is infinite
   */
  def parseFiniteDuration(config: Config, path: String): FiniteDuration = 
    val duration = Duration.fromNanos(config.getDuration(path).toNanos)
    duration match {
      case finite: FiniteDuration => finite
      case _ =>
        throw new IllegalArgumentException(
          s"Configuration '$path' must be a finite duration, got: $duration"
        )
    }

  /**
   * Parse InterfacesConfig from Typesafe Config
   *
   * Validates timeout hierarchy:
   * - askTimeout < restTimeout (ensures ask timeout triggers first)
   * - circuitBreakerCallTimeout == askTimeout (consistent timeout behavior)
   *
   * @param config Root config object (must contain test-probe.interfaces.*)
   * @return Validated InterfacesConfig
   * @throws com.typesafe.config.ConfigException if config is invalid
   * @throws IllegalArgumentException if duration configs are infinite or validation fails
   */
  def fromConfig(config: Config): InterfacesConfig =
    val interfacesConfig = config.getConfig("test-probe.interfaces")

    InterfacesConfig(
      restHost = interfacesConfig.getString("rest.host"),
      restPort = interfacesConfig.getInt("rest.port"),
      restTimeout = parseFiniteDuration(interfacesConfig, "rest.timeout"),
      gracefulShutdownTimeout = parseFiniteDuration(interfacesConfig, "rest.graceful-shutdown-timeout"),
      askTimeout = parseFiniteDuration(interfacesConfig, "rest.ask-timeout"),
      circuitBreakerMaxFailures = interfacesConfig.getInt("rest.circuit-breaker-max-failures"),
      circuitBreakerCallTimeout = parseFiniteDuration(interfacesConfig, "rest.circuit-breaker-call-timeout"),
      circuitBreakerResetTimeout = parseFiniteDuration(interfacesConfig, "rest.circuit-breaker-reset-timeout"),
      maxConcurrentRequests = interfacesConfig.getInt("rest.max-concurrent-requests"),
      maxRequestSize = interfacesConfig.getLong("rest.max-request-size"),
      maxUriLength = interfacesConfig.getInt("rest.max-uri-length"),
      retryAfterServiceUnavailable = interfacesConfig.getString("rest.retry-after-service-unavailable"),
      retryAfterNotReady = interfacesConfig.getString("rest.retry-after-not-ready")
    )
  
}
