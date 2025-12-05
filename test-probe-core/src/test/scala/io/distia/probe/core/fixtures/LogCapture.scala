package io.distia.probe
package core
package fixtures

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.*

private[core] object LogCapture {

  case class LogEvent(
    level: Level,
    message: String,
    loggerName: String,
    throwable: Option[Throwable] = None
  )

  case class CapturedLogs(events: List[LogEvent]) {

    def filterByLevel(level: Level): List[LogEvent] = events.filter(_.level == level)

    def filterByLevelOrHigher(level: Level): List[LogEvent] = events.filter(_.level.isGreaterOrEqual(level))

    def filterByLogger(loggerName: String): List[LogEvent] = events.filter(_.loggerName == loggerName)

    def filterByMessage(pattern: String): List[LogEvent] = events.filter(_.message.contains(pattern))

    def filterByMessageRegex(regex: String): List[LogEvent] = {
      val compiledPattern = regex.r
      events.filter(event => compiledPattern.findFirstIn(event.message).isDefined)
    }

    def contains(level: Level, messagePattern: String): Boolean =
      events.exists(event => event.level == level && event.message.contains(messagePattern))

    def containsExact(level: Level, message: String): Boolean =
      events.exists(event => event.level == level && event.message == message)

    def count: Int = events.size

    def countByLevel(level: Level): Int = filterByLevel(level).size

    def isEmpty: Boolean = events.isEmpty

    def nonEmpty: Boolean = events.nonEmpty

    def infoLogs: List[LogEvent] = filterByLevel(Level.INFO)

    def warnLogs: List[LogEvent] = filterByLevel(Level.WARN)

    def errorLogs: List[LogEvent] = filterByLevel(Level.ERROR)

    def debugLogs: List[LogEvent] = filterByLevel(Level.DEBUG)
  }

  def captureLogsFor[T](loggerName: String)(block: => T): (T, CapturedLogs) = {
    val logger: Logger = LoggerFactory.getLogger(loggerName).asInstanceOf[Logger]
    val listAppender: ListAppender[ILoggingEvent] = new ListAppender[ILoggingEvent]()
    val loggerContext: LoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

    listAppender.setContext(loggerContext)
    listAppender.start()

    val originalLevel: Level = logger.getLevel
    logger.addAppender(listAppender)

    try
      val result: T = block
      val capturedEvents: List[LogEvent] = listAppender.list.asScala.toList.map { event =>
        LogEvent(
          level = event.getLevel,
          message = event.getFormattedMessage,
          loggerName = event.getLoggerName,
          throwable = Option(event.getThrowableProxy).map(_ => new Exception(event.getThrowableProxy.getMessage))
        )
      }
      (result, CapturedLogs(capturedEvents))
    finally
      logger.detachAppender(listAppender)
      listAppender.stop()
      if originalLevel != null then logger.setLevel(originalLevel)
  }

  def captureLogsForMultiple[T](loggerNames: List[String])(block: => T): (T, CapturedLogs) = {
    val loggers: List[Logger] = loggerNames.map(name => LoggerFactory.getLogger(name).asInstanceOf[Logger])
    val listAppender: ListAppender[ILoggingEvent] = new ListAppender[ILoggingEvent]()
    val loggerContext: LoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

    listAppender.setContext(loggerContext)
    listAppender.start()

    val originalLevels: Map[Logger, Level] = loggers.map(logger => logger -> logger.getLevel).toMap
    loggers.foreach(_.addAppender(listAppender))

    try
      val result: T = block
      val capturedEvents: List[LogEvent] = listAppender.list.asScala.toList.map { event =>
        LogEvent(
          level = event.getLevel,
          message = event.getFormattedMessage,
          loggerName = event.getLoggerName,
          throwable = Option(event.getThrowableProxy).map(_ => new Exception(event.getThrowableProxy.getMessage))
        )
      }
      (result, CapturedLogs(capturedEvents))
    finally
      loggers.foreach(_.detachAppender(listAppender))
      listAppender.stop()
      loggers.foreach { logger =>
        val originalLevel: Level = originalLevels.getOrElse(logger, null)
        if originalLevel != null then logger.setLevel(originalLevel)
      }
  }

  def captureRootLogs[T](block: => T): (T, CapturedLogs) = {
    val rootLogger: Logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    captureLogsFor(org.slf4j.Logger.ROOT_LOGGER_NAME)(block)
  }

  def withLogLevel[T](loggerName: String, level: Level)(block: => T): T = {
    val logger: Logger = LoggerFactory.getLogger(loggerName).asInstanceOf[Logger]
    val originalLevel: Level = logger.getLevel

    logger.setLevel(level)
    try block
    finally if originalLevel != null then logger.setLevel(originalLevel)
  }
}

trait LogCapture {

  import LogCapture.*

  def captureLogsFor[T](loggerName: String)(block: => T): (T, CapturedLogs) =
    LogCapture.captureLogsFor(loggerName)(block)

  def captureLogsForMultiple[T](loggerNames: List[String])(block: => T): (T, CapturedLogs) =
    LogCapture.captureLogsForMultiple(loggerNames)(block)

  def captureRootLogs[T](block: => T): (T, CapturedLogs) =
    LogCapture.captureRootLogs(block)

  def withLogLevel[T](loggerName: String, level: Level)(block: => T): T =
    LogCapture.withLogLevel(loggerName, level)(block)

  def expectLog(logs: CapturedLogs, level: Level, messagePattern: String): Unit =
    assert(logs.contains(level, messagePattern),
      s"Expected log at level $level with message containing '$messagePattern'. Actual logs:\n${formatLogs(logs)}")

  def expectExactLog(logs: CapturedLogs, level: Level, message: String): Unit =
    assert(logs.containsExact(level, message),
      s"Expected log at level $level with exact message '$message'. Actual logs:\n${formatLogs(logs)}")

  def expectNoLogs(logs: CapturedLogs): Unit =
    assert(logs.isEmpty, s"Expected no logs but found ${logs.count} log events:\n${formatLogs(logs)}")

  def expectLogCount(logs: CapturedLogs, expectedCount: Int): Unit =
    assert(logs.count == expectedCount,
      s"Expected $expectedCount log events but found ${logs.count}:\n${formatLogs(logs)}")

  def expectLogCountAtLevel(logs: CapturedLogs, level: Level, expectedCount: Int): Unit = {
    val actualCount: Int = logs.countByLevel(level)
    assert(actualCount == expectedCount,
      s"Expected $expectedCount $level log events but found $actualCount:\n${formatLogs(CapturedLogs(logs.filterByLevel(level)))}")
  }

  private def formatLogs(logs: CapturedLogs): String =
    if logs.isEmpty then "  (no logs captured)"
    else logs.events.map(e => s"  [${e.level}] ${e.loggerName}: ${e.message}").mkString("\n")
}
