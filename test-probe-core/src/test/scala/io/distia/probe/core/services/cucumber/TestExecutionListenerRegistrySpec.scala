package io.distia.probe
package core
package services
package cucumber

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/**
 * Tests TestExecutionListenerRegistry ThreadLocal pattern.
 *
 * Verifies:
 * - registerTestId (ThreadLocal storage)
 * - getTestId (retrieval)
 * - unregister (cleanup)
 * - Thread isolation (ThreadLocal semantics)
 * - Error handling when not registered
 * - Concurrent access patterns
 *
 * Test Strategy: Unit tests (ThreadLocal verification)
 */
class TestExecutionListenerRegistrySpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  private val testId1: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
  private val testId2: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")

  override def afterEach(): Unit = {
    // Clean up registry after each test
    try {
      TestExecutionListenerRegistry.unregister(testId1)
    } catch {
      case _: Exception => // Ignore if not registered
    }
    try {
      TestExecutionListenerRegistry.unregister(testId2)
    } catch {
      case _: Exception => // Ignore if not registered
    }
  }

  "TestExecutionListenerRegistry.registerTestId" should {

    "store testId in ThreadLocal for current thread" in {
      TestExecutionListenerRegistry.registerTestId(testId1)

      val retrievedId = TestExecutionListenerRegistry.getTestId
      retrievedId shouldBe testId1
    }

    "allow overwriting existing testId for same thread" in {
      TestExecutionListenerRegistry.registerTestId(testId1)
      TestExecutionListenerRegistry.registerTestId(testId2)

      val retrievedId = TestExecutionListenerRegistry.getTestId
      retrievedId shouldBe testId2 // Latest value wins
    }
  }

  "TestExecutionListenerRegistry.getTestId" should {

    "throw IllegalStateException if no testId registered" in {
      val exception = intercept[IllegalStateException] {
        TestExecutionListenerRegistry.getTestId
      }

      exception.getMessage should include("No test ID registered for current thread")
    }

    "return registered testId when present" in {
      TestExecutionListenerRegistry.registerTestId(testId1)

      val retrievedId = TestExecutionListenerRegistry.getTestId
      retrievedId shouldBe testId1
    }
  }

  "TestExecutionListenerRegistry.registerListener" should {

    "store listener instance for retrieval" in {
      TestExecutionListenerRegistry.registerTestId(testId1)
      val listener = new TestExecutionEventListener()

      // Listener registers itself in constructor
      val retrievedListener = TestExecutionListenerRegistry.getListener(testId1)
      retrievedListener shouldBe listener
    }

    "allow multiple listeners with different testIds" in {
      TestExecutionListenerRegistry.registerTestId(testId1)
      val listener1 = new TestExecutionEventListener()

      // Save reference to listener1 BEFORE unregister
      val savedListener1 = TestExecutionListenerRegistry.getListener(testId1)

      // Clean up thread local and register new testId
      TestExecutionListenerRegistry.unregister(testId1)
      TestExecutionListenerRegistry.registerTestId(testId2)
      val listener2 = new TestExecutionEventListener()

      // listener1 is gone from registry after unregister, but listener2 should be present
      val exception = intercept[IllegalStateException] {
        TestExecutionListenerRegistry.getListener(testId1)
      }
      exception.getMessage should include("No listener registered")

      // listener2 should be retrievable
      TestExecutionListenerRegistry.getListener(testId2) shouldBe listener2

      // Verify we did have listener1 before unregister
      savedListener1 shouldBe listener1
    }
  }

  "TestExecutionListenerRegistry.getListener" should {

    "throw IllegalStateException if no listener registered for testId" in {
      val exception = intercept[IllegalStateException] {
        TestExecutionListenerRegistry.getListener(testId1)
      }

      exception.getMessage should include("No listener registered for test")
      exception.getMessage should include(testId1.toString)
    }

    "return registered listener when present" in {
      TestExecutionListenerRegistry.registerTestId(testId1)
      val listener = new TestExecutionEventListener()

      val retrievedListener = TestExecutionListenerRegistry.getListener(testId1)
      retrievedListener shouldBe listener
    }
  }

  "TestExecutionListenerRegistry.unregister" should {

    "clear ThreadLocal for current thread" in {
      TestExecutionListenerRegistry.registerTestId(testId1)
      TestExecutionListenerRegistry.unregister(testId1)

      val exception = intercept[IllegalStateException] {
        TestExecutionListenerRegistry.getTestId
      }

      exception.getMessage should include("No test ID registered for current thread")
    }

    "remove listener from map" in {
      TestExecutionListenerRegistry.registerTestId(testId1)
      val listener = new TestExecutionEventListener()

      TestExecutionListenerRegistry.unregister(testId1)

      val exception = intercept[IllegalStateException] {
        TestExecutionListenerRegistry.getListener(testId1)
      }

      exception.getMessage should include("No listener registered for test")
    }

    "be idempotent (safe to call multiple times)" in {
      TestExecutionListenerRegistry.registerTestId(testId1)
      new TestExecutionEventListener()

      noException should be thrownBy {
        TestExecutionListenerRegistry.unregister(testId1)
        TestExecutionListenerRegistry.unregister(testId1)
        TestExecutionListenerRegistry.unregister(testId1)
      }
    }
  }

  "TestExecutionListenerRegistry thread safety" should {

    "isolate testIds per thread using ThreadLocal" in {
      @volatile var thread1Id: UUID = null
      @volatile var thread2Id: UUID = null

      val thread1 = new Thread(new Runnable {
        override def run(): Unit = {
          TestExecutionListenerRegistry.registerTestId(testId1)
          thread1Id = TestExecutionListenerRegistry.getTestId
          TestExecutionListenerRegistry.unregister(testId1)
        }
      })

      val thread2 = new Thread(new Runnable {
        override def run(): Unit = {
          TestExecutionListenerRegistry.registerTestId(testId2)
          thread2Id = TestExecutionListenerRegistry.getTestId
          TestExecutionListenerRegistry.unregister(testId2)
        }
      })

      thread1.start()
      thread2.start()
      thread1.join()
      thread2.join()

      thread1Id shouldBe testId1
      thread2Id shouldBe testId2
    }

    "allow concurrent listener registration from different threads" in {
      @volatile var listener1: TestExecutionEventListener = null
      @volatile var listener2: TestExecutionEventListener = null

      val thread1 = new Thread(new Runnable {
        override def run(): Unit = {
          TestExecutionListenerRegistry.registerTestId(testId1)
          listener1 = new TestExecutionEventListener()
          TestExecutionListenerRegistry.unregister(testId1)
        }
      })

      val thread2 = new Thread(new Runnable {
        override def run(): Unit = {
          TestExecutionListenerRegistry.registerTestId(testId2)
          listener2 = new TestExecutionEventListener()
          TestExecutionListenerRegistry.unregister(testId2)
        }
      })

      thread1.start()
      thread2.start()
      thread1.join()
      thread2.join()

      listener1 should not be null
      listener2 should not be null
      listener1 should not be listener2
    }
  }
}
