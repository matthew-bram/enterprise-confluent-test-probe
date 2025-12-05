package io.distia.probe
package core
package fixtures

import ch.qos.logback.classic.Level
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory

/**
 * Tests LogCapture fixture for SLF4J/Logback log capturing during tests.
 *
 * Verifies:
 * - captureLogsFor (single logger capture)
 * - captureLogsForMultiple (multiple logger capture)
 * - CapturedLogs filtering (by level, message, regex)
 * - Helper assertion methods (expectLog, expectExactLog, expectNoLogs, expectLogCount)
 * - withLogLevel (temporary log level changes)
 * - Logger state restoration after capture
 *
 * Test Strategy: Unit tests (no external dependencies)
 */
class LogCaptureSpec extends AnyWordSpec with Matchers with LogCapture {

  private val testLogger = LoggerFactory.getLogger("io.distia.probe.test.LogCaptureSpec")

  "LogCapture.captureLogsFor" should {

    "capture INFO level logs" in {
      val (result, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Test info message")
        "test-result"
      }

      result shouldBe "test-result"
      logs.nonEmpty shouldBe true
      logs.count shouldBe 1
      logs.infoLogs should have size 1
      logs.infoLogs.head.message shouldBe "Test info message"
      logs.infoLogs.head.level shouldBe Level.INFO
    }

    "capture ERROR level logs" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.error("Test error message")
      }

      logs.errorLogs should have size 1
      logs.errorLogs.head.message shouldBe "Test error message"
      logs.errorLogs.head.level shouldBe Level.ERROR
    }

    "capture WARN level logs" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.warn("Test warning message")
      }

      logs.warnLogs should have size 1
      logs.warnLogs.head.message shouldBe "Test warning message"
      logs.warnLogs.head.level shouldBe Level.WARN
    }

    "capture DEBUG level logs when logger level is DEBUG" in {
      val (_, logs) = withLogLevel("io.distia.probe.test.LogCaptureSpec", Level.DEBUG) {
        captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
          testLogger.debug("Test debug message")
        }
      }

      logs.debugLogs should have size 1
      logs.debugLogs.head.message shouldBe "Test debug message"
      logs.debugLogs.head.level shouldBe Level.DEBUG
    }

    "capture multiple log events" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("First message")
        testLogger.warn("Second message")
        testLogger.error("Third message")
      }

      logs.count shouldBe 3
      logs.infoLogs should have size 1
      logs.warnLogs should have size 1
      logs.errorLogs should have size 1
    }

    "return empty logs when nothing is logged" in {
      val (result, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        42
      }

      result shouldBe 42
      logs.isEmpty shouldBe true
      logs.count shouldBe 0
    }

    "capture logs with formatted messages" in {
      val testId = "test-123"
      val bucket = "my-bucket"

      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info(s"Processing test $testId in bucket $bucket")
      }

      logs.infoLogs should have size 1
      logs.infoLogs.head.message shouldBe "Processing test test-123 in bucket my-bucket"
    }

    "properly restore logger state after capture" in {
      val originalLevel = LoggerFactory.getLogger("io.distia.probe.test.LogCaptureSpec")
        .asInstanceOf[ch.qos.logback.classic.Logger]
        .getLevel

      captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Capture test")
      }

      val afterCaptureLevel = LoggerFactory.getLogger("io.distia.probe.test.LogCaptureSpec")
        .asInstanceOf[ch.qos.logback.classic.Logger]
        .getLevel

      afterCaptureLevel shouldBe originalLevel
    }
  }

  "LogCapture.captureLogsForMultiple" should {

    "capture logs from multiple loggers" in {
      val logger1 = LoggerFactory.getLogger("io.distia.probe.test.Logger1")
      val logger2 = LoggerFactory.getLogger("io.distia.probe.test.Logger2")

      val (_, logs) = captureLogsForMultiple(List(
        "io.distia.probe.test.Logger1",
        "io.distia.probe.test.Logger2"
      )) {
        logger1.info("Message from logger1")
        logger2.warn("Message from logger2")
      }

      logs.count shouldBe 2
      logs.filterByLogger("io.distia.probe.test.Logger1") should have size 1
      logs.filterByLogger("io.distia.probe.test.Logger2") should have size 1
    }
  }

  "CapturedLogs" should {

    "filter by level" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Info message")
        testLogger.warn("Warn message")
        testLogger.error("Error message")
      }

      logs.filterByLevel(Level.INFO) should have size 1
      logs.filterByLevel(Level.WARN) should have size 1
      logs.filterByLevel(Level.ERROR) should have size 1
    }

    "filter by level or higher" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Info message")
        testLogger.warn("Warn message")
        testLogger.error("Error message")
      }

      logs.filterByLevelOrHigher(Level.WARN) should have size 2
      logs.filterByLevelOrHigher(Level.ERROR) should have size 1
      logs.filterByLevelOrHigher(Level.INFO) should have size 3
    }

    "filter by message pattern" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Test message one")
        testLogger.info("Test message two")
        testLogger.info("Different message")
      }

      logs.filterByMessage("Test message") should have size 2
      logs.filterByMessage("Different") should have size 1
      logs.filterByMessage("nonexistent") should have size 0
    }

    "filter by message regex" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Test ID: 12345")
        testLogger.info("Test ID: 67890")
        testLogger.info("No ID here")
      }

      logs.filterByMessageRegex("Test ID: \\d+") should have size 2
      logs.filterByMessageRegex("No ID") should have size 1
    }

    "check if contains log with pattern" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Expected message")
        testLogger.error("Error occurred")
      }

      logs.contains(Level.INFO, "Expected") shouldBe true
      logs.contains(Level.ERROR, "Error") shouldBe true
      logs.contains(Level.WARN, "Expected") shouldBe false
      logs.contains(Level.INFO, "Nonexistent") shouldBe false
    }

    "check if contains exact log message" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Exact message")
      }

      logs.containsExact(Level.INFO, "Exact message") shouldBe true
      logs.containsExact(Level.INFO, "Exact") shouldBe false
      logs.containsExact(Level.WARN, "Exact message") shouldBe false
    }

    "count logs by level" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Info 1")
        testLogger.info("Info 2")
        testLogger.warn("Warn 1")
        testLogger.error("Error 1")
        testLogger.error("Error 2")
        testLogger.error("Error 3")
      }

      logs.countByLevel(Level.INFO) shouldBe 2
      logs.countByLevel(Level.WARN) shouldBe 1
      logs.countByLevel(Level.ERROR) shouldBe 3
    }
  }

  "LogCapture trait helper methods" should {

    "expectLog should succeed when log exists" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Expected log message")
      }

      expectLog(logs, Level.INFO, "Expected log")
    }

    "expectLog should fail when log does not exist" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Different message")
      }

      assertThrows[AssertionError] {
        expectLog(logs, Level.INFO, "Expected log")
      }
    }

    "expectExactLog should succeed for exact match" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Exact message")
      }

      expectExactLog(logs, Level.INFO, "Exact message")
    }

    "expectExactLog should fail for partial match" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Exact message here")
      }

      assertThrows[AssertionError] {
        expectExactLog(logs, Level.INFO, "Exact message")
      }
    }

    "expectNoLogs should succeed when no logs captured" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
      }

      expectNoLogs(logs)
    }

    "expectNoLogs should fail when logs exist" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Unexpected log")
      }

      assertThrows[AssertionError] {
        expectNoLogs(logs)
      }
    }

    "expectLogCount should verify total log count" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.info("Log 1")
        testLogger.warn("Log 2")
        testLogger.error("Log 3")
      }

      expectLogCount(logs, 3)
    }

    "expectLogCountAtLevel should verify count for specific level" in {
      val (_, logs) = captureLogsFor("io.distia.probe.test.LogCaptureSpec") {
        testLogger.error("Error 1")
        testLogger.error("Error 2")
        testLogger.info("Info 1")
      }

      expectLogCountAtLevel(logs, Level.ERROR, 2)
      expectLogCountAtLevel(logs, Level.INFO, 1)
    }
  }

  "withLogLevel" should {

    "restore original level after block completes" in {
      val logger = LoggerFactory.getLogger("io.distia.probe.test.RestoreLogger").asInstanceOf[ch.qos.logback.classic.Logger]
      logger.setLevel(Level.WARN)

      withLogLevel("io.distia.probe.test.RestoreLogger", Level.DEBUG) {
      }

      logger.getLevel shouldBe Level.WARN
    }
  }
}
