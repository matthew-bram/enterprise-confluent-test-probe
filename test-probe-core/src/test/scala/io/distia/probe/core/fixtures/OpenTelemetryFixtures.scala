package io.distia.probe.core.fixtures

/**
 * OpenTelemetry metrics verification utilities for testing.
 *
 * Purpose:
 * - Verify metrics counters increment correctly
 * - Verify histogram values
 * - Verify gauge values
 * - Reset metrics between tests for isolation
 *
 * Current Status: STUB IMPLEMENTATION
 * - Tests will pass but metrics are not actually verified
 * - Warnings printed to console to indicate stub usage
 *
 * TODO: Implement actual OpenTelemetry verification
 * Requirements:
 * - OpenTelemetry SDK test instrumentation
 * - InMemoryMetricExporter for test assertions
 * - Metric reader configuration
 *
 * See: ADR-TESTING-001-observability-verification-strategy.md
 *
 * Example (future implementation):
 * {{{
 *   Then("""counter should increment for errors""") { () =>
 *     // Trigger error condition
 *     produceInvalidMessage()
 *
 *     // Verify counter incremented
 *     verifyCounterIncremented(
 *       counterName = "kafka.producer.errors",
 *       expectedIncrement = 1,
 *       tags = Map("topic" -> "orders")
 *     )
 *   }
 * }}}
 */
trait OpenTelemetryFixtures:

  /**
   * Verify counter was incremented.
   *
   * Stub Implementation: Prints warning, does not verify.
   *
   * @param counterName Metric counter name (e.g., "kafka.consumer.decode.errors")
   * @param expectedIncrement Expected increment amount (default: 1)
   * @param tags Optional tags to filter by (e.g., Map("topic" -> "orders"))
   *
   * Future Implementation:
   * - Query InMemoryMetricExporter
   * - Filter by counter name and tags
   * - Assert increment matches expected
   */
  def verifyCounterIncremented(
    counterName: String,
    expectedIncrement: Long = 1,
    tags: Map[String, String] = Map.empty
  ): Unit =
    // TODO: Implement actual OpenTelemetry metrics verification
    // Requires: OpenTelemetry SDK test instrumentation
    println(s"⚠️  OpenTelemetry stub: Would verify counter '$counterName' incremented by $expectedIncrement")
    if tags.nonEmpty then
      println(s"⚠️  Tags filter: ${tags.map { case (k, v) => s"$k=$v" }.mkString(", ")}")

  /**
   * Verify histogram recorded value.
   *
   * Stub Implementation: Prints warning, does not verify.
   *
   * @param histogramName Metric histogram name
   * @param expectedValue Expected recorded value (or range)
   * @param tags Optional tags to filter by
   */
  def verifyHistogramRecorded(
    histogramName: String,
    expectedValue: Double,
    tags: Map[String, String] = Map.empty
  ): Unit =
    println(s"⚠️  OpenTelemetry stub: Would verify histogram '$histogramName' recorded value $expectedValue")
    if tags.nonEmpty then
      println(s"⚠️  Tags filter: ${tags.map { case (k, v) => s"$k=$v" }.mkString(", ")}")

  /**
   * Reset all metrics (for test isolation).
   *
   * Stub Implementation: Prints warning, does not reset.
   *
   * Call this in test setup/teardown to ensure metric isolation.
   */
  def resetMetrics(): Unit =
    println(s"⚠️  OpenTelemetry stub: Would reset all metrics for test isolation")

/**
 * Companion object for direct usage.
 */
object OpenTelemetryFixtures extends OpenTelemetryFixtures
