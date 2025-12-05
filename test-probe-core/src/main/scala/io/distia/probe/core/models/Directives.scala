package io.distia.probe
package core
package models

import java.util.UUID

case class TestExecutionResult(
  testId: UUID,
  passed: Boolean,
  scenarioCount: Int,
  scenariosPassed: Int,
  scenariosFailed: Int,
  scenariosSkipped: Int,
  stepCount: Int,
  stepsPassed: Int,
  stepsFailed: Int,
  stepsSkipped: Int,
  stepsUndefined: Int,
  durationMillis: Long,
  errorMessage: Option[String] = None,
  failedScenarios: Seq[String] = Seq.empty
) {

  /**
   * Get success rate percentage
   *
   * @return Success rate as percentage (0-100)
   */
  def successRate: Double =
    if scenarioCount == 0 then 0.0
    else (scenariosPassed.toDouble / scenarioCount) * 100.0

  /**
   * Format as human-readable summary
   *
   * @return Multi-line summary string
   */
  def summary: String =
    val status: String = if passed then "PASSED" else "FAILED"
    val baseLines: Seq[String] = Seq(
      s"Test Execution $status",
      s"Test ID: $testId",
      s"Duration: ${durationMillis}ms",
      "",
      s"Scenarios: $scenarioCount total, $scenariosPassed passed, $scenariosFailed failed, $scenariosSkipped skipped",
      s"Steps: $stepCount total, $stepsPassed passed, $stepsFailed failed, $stepsSkipped skipped, $stepsUndefined undefined",
      s"Success Rate: ${"%.1f".format(successRate)}%"
    )

    val failedScenarioLines: Seq[String] =
      if failedScenarios.nonEmpty then
        Seq("", "Failed Scenarios:") ++ failedScenarios.map(name => s"  - $name")
      else Seq.empty

    val errorLines: Seq[String] =
      errorMessage.map(msg => Seq("", s"Error: $msg")).getOrElse(Seq.empty)

    (baseLines ++ failedScenarioLines ++ errorLines).mkString("\n")
}