package io.distia.probe
package core
package services
package cucumber

import java.nio.file.Path
import java.util.UUID

/**
 * ThreadLocal registry for test context in Cucumber execution
 *
 * Stores test ID and jimfs paths for access by Cucumber glue steps and custom plugins.
 * Follows ThreadLocal pattern for thread isolation during concurrent test execution.
 *
 * Flow:
 * 1. CucumberExecutor calls registerTestId(testId) and registerEvidencePath(path)
 * 2. Cucumber glue steps and plugins access via getTestId/getEvidencePath
 * 3. CucumberExecutor calls clear() in finally block for cleanup
 *
 * Thread Safety:
 * ThreadLocal ensures each execution thread has isolated context.
 * Supports concurrent test execution on different threads.
 *
 * Added jimfs paths (2025-11-01):
 * Custom plugins need jimfs evidence path to write JSON/HTML reports to in-memory filesystem.
 */
private[cucumber] object CucumberContext:

  private val testIdThreadLocal: ThreadLocal[UUID] = new ThreadLocal[UUID]()

  private val evidencePathThreadLocal: ThreadLocal[Path] = new ThreadLocal[Path]()
  
  def registerTestId(testId: UUID): Unit =
    testIdThreadLocal.set(testId)
  
  def getTestId: UUID = Option(testIdThreadLocal.get()) match
    case Some(testId) => testId
    case None => throw new IllegalStateException("No test ID registered in CucumberContext - ensure CucumberExecutor calls registerTestId()")
  
  def registerEvidencePath(path: Path): Unit = evidencePathThreadLocal.set(path)
  
  def getEvidencePath: Path = Option(evidencePathThreadLocal.get()) match
    case Some(path) => path
    case None => throw new IllegalStateException("No evidence path registered in CucumberContext - ensure CucumberExecutor calls registerEvidencePath()")
  
  def clear(): Unit =
    testIdThreadLocal.remove()
    evidencePathThreadLocal.remove()
