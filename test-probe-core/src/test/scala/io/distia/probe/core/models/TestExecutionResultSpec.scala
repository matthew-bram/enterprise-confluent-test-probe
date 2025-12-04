package io.distia.probe
package core
package models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/**
 * Unit tests for TestExecutionResult model.
 *
 * Tests Cucumber test execution result calculations, including
 * success rate computation, summary formatting, and edge case handling.
 *
 * Coverage Focus:
 * - Success rate calculation (passed / total scenarios)
 * - Summary string formatting
 * - Edge cases: 0 scenarios, partial failures, all passed, all failed
 * - Decimal precision in percentage calculations
 *
 * Test Strategy:
 * - Tests all success rate ranges (0%, 50%, 100%)
 * - Validates summary format consistency
 * - Verifies proper handling of edge cases
 */
class TestExecutionResultSpec extends AnyWordSpec with Matchers {

  private val testId: UUID = UUID.fromString("12345678-1234-1234-1234-123456789012")

  "TestExecutionResult.successRate" should {

    "return 0.0 when scenarioCount is 0" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = true,
        scenarioCount = 0,
        scenariosPassed = 0,
        scenariosFailed = 0,
        scenariosSkipped = 0,
        stepCount = 0,
        stepsPassed = 0,
        stepsFailed = 0,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 1000L
      )

      result.successRate shouldBe 0.0
    }

    "return 100.0 when all scenarios passed" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = true,
        scenarioCount = 10,
        scenariosPassed = 10,
        scenariosFailed = 0,
        scenariosSkipped = 0,
        stepCount = 50,
        stepsPassed = 50,
        stepsFailed = 0,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 5000L
      )

      result.successRate shouldBe 100.0
    }

    "return 50.0 when half scenarios passed" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 10,
        scenariosPassed = 5,
        scenariosFailed = 5,
        scenariosSkipped = 0,
        stepCount = 50,
        stepsPassed = 25,
        stepsFailed = 25,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 5000L
      )

      result.successRate shouldBe 50.0
    }

    "return 0.0 when all scenarios failed" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 5,
        scenariosPassed = 0,
        scenariosFailed = 5,
        scenariosSkipped = 0,
        stepCount = 25,
        stepsPassed = 0,
        stepsFailed = 25,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 3000L
      )

      result.successRate shouldBe 0.0
    }

    "return 75.0 when 3 out of 4 scenarios passed" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 4,
        scenariosPassed = 3,
        scenariosFailed = 1,
        scenariosSkipped = 0,
        stepCount = 20,
        stepsPassed = 15,
        stepsFailed = 5,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 2000L
      )

      result.successRate shouldBe 75.0
    }

    "calculate correctly with skipped scenarios" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 10,
        scenariosPassed = 6,
        scenariosFailed = 2,
        scenariosSkipped = 2,
        stepCount = 50,
        stepsPassed = 30,
        stepsFailed = 10,
        stepsSkipped = 10,
        stepsUndefined = 0,
        durationMillis = 4000L
      )

      result.successRate shouldBe 60.0
    }
  }

  "TestExecutionResult.summary" should {

    "format complete passing result correctly" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = true,
        scenarioCount = 5,
        scenariosPassed = 5,
        scenariosFailed = 0,
        scenariosSkipped = 0,
        stepCount = 25,
        stepsPassed = 25,
        stepsFailed = 0,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 3500L
      )

      val summary: String = result.summary

      summary should include("Test Execution PASSED")
      summary should include("Test ID: 12345678-1234-1234-1234-123456789012")
      summary should include("Duration: 3500ms")
      summary should include("Scenarios: 5 total, 5 passed, 0 failed, 0 skipped")
      summary should include("Steps: 25 total, 25 passed, 0 failed, 0 skipped, 0 undefined")
      summary should include("Success Rate: 100.0%")
    }

    "format failing result correctly" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 10,
        scenariosPassed = 6,
        scenariosFailed = 4,
        scenariosSkipped = 0,
        stepCount = 50,
        stepsPassed = 30,
        stepsFailed = 20,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 7200L
      )

      val summary: String = result.summary

      summary should include("Test Execution FAILED")
      summary should include("Test ID: 12345678-1234-1234-1234-123456789012")
      summary should include("Duration: 7200ms")
      summary should include("Scenarios: 10 total, 6 passed, 4 failed, 0 skipped")
      summary should include("Steps: 50 total, 30 passed, 20 failed, 0 skipped, 0 undefined")
      summary should include("Success Rate: 60.0%")
    }

    "include failed scenarios when present" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 3,
        scenariosPassed = 1,
        scenariosFailed = 2,
        scenariosSkipped = 0,
        stepCount = 15,
        stepsPassed = 5,
        stepsFailed = 10,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 2000L,
        failedScenarios = Seq("Login with invalid credentials", "Password reset fails")
      )

      val summary: String = result.summary

      summary should include("Failed Scenarios:")
      summary should include("- Login with invalid credentials")
      summary should include("- Password reset fails")
    }

    "include error message when present" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 2,
        scenariosPassed = 0,
        scenariosFailed = 2,
        scenariosSkipped = 0,
        stepCount = 10,
        stepsPassed = 0,
        stepsFailed = 10,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 1500L,
        errorMessage = Some("Test execution interrupted: Connection timeout")
      )

      val summary: String = result.summary

      summary should include("Error: Test execution interrupted: Connection timeout")
    }

    "handle skipped scenarios and steps" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 8,
        scenariosPassed = 5,
        scenariosFailed = 1,
        scenariosSkipped = 2,
        stepCount = 40,
        stepsPassed = 25,
        stepsFailed = 5,
        stepsSkipped = 10,
        stepsUndefined = 0,
        durationMillis = 4500L
      )

      val summary: String = result.summary

      summary should include("Scenarios: 8 total, 5 passed, 1 failed, 2 skipped")
      summary should include("Steps: 40 total, 25 passed, 5 failed, 10 skipped, 0 undefined")
    }

    "handle undefined steps" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 5,
        scenariosPassed = 2,
        scenariosFailed = 3,
        scenariosSkipped = 0,
        stepCount = 25,
        stepsPassed = 10,
        stepsFailed = 10,
        stepsSkipped = 0,
        stepsUndefined = 5,
        durationMillis = 3000L
      )

      val summary: String = result.summary

      summary should include("Steps: 25 total, 10 passed, 10 failed, 0 skipped, 5 undefined")
    }

    "format success rate with one decimal place" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 3,
        scenariosPassed = 2,
        scenariosFailed = 1,
        scenariosSkipped = 0,
        stepCount = 15,
        stepsPassed = 10,
        stepsFailed = 5,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 2000L
      )

      val summary: String = result.summary

      summary should include("Success Rate: 66.7%")
    }

    "include both failed scenarios and error message when both present" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 4,
        scenariosPassed = 2,
        scenariosFailed = 2,
        scenariosSkipped = 0,
        stepCount = 20,
        stepsPassed = 10,
        stepsFailed = 10,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 3000L,
        failedScenarios = Seq("Authentication fails", "Session timeout"),
        errorMessage = Some("Database connection lost")
      )

      val summary: String = result.summary

      summary should include("Failed Scenarios:")
      summary should include("- Authentication fails")
      summary should include("- Session timeout")
      summary should include("Error: Database connection lost")
    }

    "handle empty failed scenarios list" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 5,
        scenariosPassed = 3,
        scenariosFailed = 2,
        scenariosSkipped = 0,
        stepCount = 25,
        stepsPassed = 15,
        stepsFailed = 10,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 4000L,
        failedScenarios = Seq.empty
      )

      val summary: String = result.summary

      summary should not include "Failed Scenarios:"
    }

    "handle no error message (None)" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = true,
        scenarioCount = 3,
        scenariosPassed = 3,
        scenariosFailed = 0,
        scenariosSkipped = 0,
        stepCount = 15,
        stepsPassed = 15,
        stepsFailed = 0,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 2000L,
        errorMessage = None
      )

      val summary: String = result.summary

      summary should not include "Error:"
    }
  }

  "TestExecutionResult construction" should {

    "construct with all required fields" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = true,
        scenarioCount = 5,
        scenariosPassed = 5,
        scenariosFailed = 0,
        scenariosSkipped = 0,
        stepCount = 25,
        stepsPassed = 25,
        stepsFailed = 0,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 3000L
      )

      result.testId shouldBe testId
      result.passed shouldBe true
      result.scenarioCount shouldBe 5
      result.scenariosPassed shouldBe 5
      result.durationMillis shouldBe 3000L
      result.errorMessage shouldBe None
      result.failedScenarios shouldBe Seq.empty
    }

    "construct with optional fields provided" in {
      val result = TestExecutionResult(
        testId = testId,
        passed = false,
        scenarioCount = 3,
        scenariosPassed = 1,
        scenariosFailed = 2,
        scenariosSkipped = 0,
        stepCount = 15,
        stepsPassed = 5,
        stepsFailed = 10,
        stepsSkipped = 0,
        stepsUndefined = 0,
        durationMillis = 2000L,
        errorMessage = Some("Test failed"),
        failedScenarios = Seq("Scenario 1", "Scenario 2")
      )

      result.errorMessage shouldBe Some("Test failed")
      result.failedScenarios shouldBe Seq("Scenario 1", "Scenario 2")
    }
  }
}
