package io.distia.probe
package core
package services
package cucumber

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for sharing test context with Cucumber event listeners
 *
 * Problem: Cucumber instantiates plugins via class name string using no-arg constructor.
 * We cannot pass testId parameter directly to TestExecutionEventListener.
 *
 * Solution: Use ThreadLocal registry pattern.
 * 1. CucumberExecutor registers testId for current thread
 * 2. Cucumber instantiates TestExecutionEventListener on the same thread
 * 3. Listener constructor reads testId from ThreadLocal
 * 4. CucumberExecutor unregisters testId after execution completes
 *
 * Thread Safety:
 * Uses ThreadLocal for thread-isolated storage.
 * Each Cucumber execution runs on a dedicated thread from cucumber-blocking-dispatcher.
 *
 */
private[cucumber] object TestExecutionListenerRegistry:

  private val testIdThreadLocal: ThreadLocal[UUID] = new ThreadLocal[UUID]()

  private val listeners = new ConcurrentHashMap[UUID, TestExecutionEventListener]()

  def registerTestId(testId: UUID): Unit = testIdThreadLocal.set(testId): Unit

  def getTestId: UUID = Option(testIdThreadLocal.get()) match
    case Some(testId) => testId
    case None => throw new IllegalStateException("No test ID registered for current thread. CucumberExecutor must call registerTestId() before execution.")

  def registerListener(testId: UUID, listener: TestExecutionEventListener): Unit = listeners.put(testId, listener): Unit

  def getListener(testId: UUID): TestExecutionEventListener = Option(listeners.get(testId)) match
    case Some(listener) => listener
    case None => throw new IllegalStateException(s"No listener registered for test $testId. Cucumber may have failed to instantiate the listener.")

  def unregister(testId: UUID): Unit =
    testIdThreadLocal.remove()
    listeners.remove(testId): Unit
