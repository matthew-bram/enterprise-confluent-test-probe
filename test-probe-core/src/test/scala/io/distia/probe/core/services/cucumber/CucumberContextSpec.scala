package io.distia.probe.core
package services
package cucumber

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}

import java.util.UUID
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

/**
 * Unit tests for CucumberContext ThreadLocal registry.
 *
 * Tests:
 * - Register testId
 * - Get testId
 * - Clear testId
 * - Thread isolation (ThreadLocal semantics)
 * - Error handling (getTestId when not registered)
 *
 * Coverage Target: 85%+ (production code)
 *
 * Test Strategy: Unit tests (ThreadLocal verification)
 */
class CucumberContextSpec extends AnyWordSpec with Matchers with GivenWhenThen with BeforeAndAfterEach {

  // Clean up ThreadLocal after each test to prevent test pollution
  override def afterEach(): Unit = {
    CucumberContext.clear()
    super.afterEach()
  }

  "CucumberContext" should {

  // ============================================================================
  // Happy Path: Register, Get, Clear
  // ============================================================================

    "register and retrieve testId successfully" in {
    Given("a test ID")
    val testId: UUID = UUID.randomUUID()

    When("I register the testId")
    CucumberContext.registerTestId(testId)

    Then("I should be able to retrieve it")
    val retrieved: UUID = CucumberContext.getTestId
    retrieved shouldBe testId
  }

    "allow multiple register/get cycles" in {
    Given("multiple test IDs")
    val testId1: UUID = UUID.randomUUID()
    val testId2: UUID = UUID.randomUUID()

    When("I register the first testId")
    CucumberContext.registerTestId(testId1)

    Then("I should retrieve the first testId")
    CucumberContext.getTestId shouldBe testId1

    When("I register the second testId (overwriting)")
    CucumberContext.registerTestId(testId2)

    Then("I should retrieve the second testId")
    CucumberContext.getTestId shouldBe testId2
  }

    "clear testId successfully" in {
    Given("a registered testId")
    val testId: UUID = UUID.randomUUID()
    CucumberContext.registerTestId(testId)

    When("I clear the testId")
    CucumberContext.clear()

    Then("getTestId should throw IllegalStateException")
    val exception: IllegalStateException = intercept[IllegalStateException] {
      CucumberContext.getTestId
    }

    exception.getMessage should include("No test ID registered in CucumberContext")
  }

  // ============================================================================
  // Error Handling
  // ============================================================================

    "throw IllegalStateException when getTestId called without registration" in {
    Given("no testId registered")
    // Clean state (afterEach ensures this)

    When("I call getTestId")
    Then("it should throw IllegalStateException with helpful message")
    val exception: IllegalStateException = intercept[IllegalStateException] {
      CucumberContext.getTestId
    }

    exception.getMessage should include("No test ID registered in CucumberContext")
  }

    "handle clear when nothing is registered (no-op, no exception)" in {
    Given("no testId registered")

    When("I call clear")
    Then("it should not throw exception")
    noException should be thrownBy {
      CucumberContext.clear()
    }
  }

  // ============================================================================
  // Thread Isolation (Critical for Cucumber parallel execution)
  // ============================================================================

    "isolate testIds between threads" in {
    Given("two different test IDs")
    val testId1: UUID = UUID.randomUUID()
    val testId2: UUID = UUID.randomUUID()

    When("I register testId1 on thread 1 and testId2 on thread 2")
    val future1: Future[UUID] = Future {
      CucumberContext.registerTestId(testId1)
      Thread.sleep(100)  // Simulate work
      CucumberContext.getTestId
    }

    val future2: Future[UUID] = Future {
      Thread.sleep(50)  // Offset start to interleave execution
      CucumberContext.registerTestId(testId2)
      Thread.sleep(100)
      CucumberContext.getTestId
    }

    Then("each thread should retrieve its own testId")
    val result1: UUID = Await.result(future1, 2.seconds)
    val result2: UUID = Await.result(future2, 2.seconds)

    result1 shouldBe testId1
    result2 shouldBe testId2
  }

    "not leak testId from one thread to another" in {
    Given("testId registered on thread 1")
    val testId: UUID = UUID.randomUUID()
    CucumberContext.registerTestId(testId)

    When("thread 2 tries to retrieve testId")
    // Use dedicated executor to ensure different thread
    import java.util.concurrent.Executors
    val executor = Executors.newSingleThreadExecutor()
    implicit val ec = scala.concurrent.ExecutionContext.fromExecutor(executor)

    try
      val future: Future[Option[UUID]] = Future {
        try
          Some(CucumberContext.getTestId)
        catch
          case _: IllegalStateException => None
      }

      Then("thread 2 should not see thread 1's testId")
      val result: Option[UUID] = Await.result(future, 2.seconds)
      result shouldBe None
    finally
      executor.shutdown()
  }

    "allow thread 2 to register its own testId independently" in {
    Given("testId registered on thread 1")
    val testId1: UUID = UUID.randomUUID()
    CucumberContext.registerTestId(testId1)

    When("thread 2 registers its own testId")
    val testId2: UUID = UUID.randomUUID()
    val future: Future[UUID] = Future {
      CucumberContext.registerTestId(testId2)
      CucumberContext.getTestId
    }

    Then("thread 2 should retrieve its own testId, not thread 1's")
    val result: UUID = Await.result(future, 2.seconds)
    result shouldBe testId2

    And("thread 1 should still have its original testId")
    CucumberContext.getTestId shouldBe testId1
  }

  // ============================================================================
  // Thread Pool Hygiene (Critical for preventing stale data)
  // ============================================================================

    "prevent stale data when thread is reused from pool" in {
    Given("a thread pool with a single thread")
    import java.util.concurrent.Executors
    val executor = Executors.newSingleThreadExecutor()
    implicit val ec = scala.concurrent.ExecutionContext.fromExecutor(executor)

    try
      When("thread 1 registers testId1 and clears it")
      val testId1: UUID = UUID.randomUUID()
      val future1: Future[Unit] = Future {
        CucumberContext.registerTestId(testId1)
        CucumberContext.clear()
      }
      Await.result(future1, 2.seconds)

      And("the SAME thread (from pool) is reused for thread 2")
      val future2: Future[Option[UUID]] = Future {
        try
          Some(CucumberContext.getTestId)
        catch
          case _: IllegalStateException => None
      }

      Then("thread 2 should not see stale testId1")
      val result: Option[UUID] = Await.result(future2, 2.seconds)
      result shouldBe None
    finally
      executor.shutdown()
  }

  // ============================================================================
  // Integration Pattern (How CucumberExecutor uses CucumberContext)
  // ============================================================================

    "work correctly in try/finally pattern (CucumberExecutor integration)" in {
    Given("a testId")
    val testId: UUID = UUID.randomUUID()

    When("I use the CucumberExecutor pattern (register in try, clear in finally)")
    var retrievedTestId: Option[UUID] = None

    try
      CucumberContext.registerTestId(testId)

      // Simulate Cucumber execution
      retrievedTestId = Some(CucumberContext.getTestId)
    finally
      CucumberContext.clear()

    Then("the testId should have been retrieved during execution")
    retrievedTestId shouldBe Some(testId)

    And("after finally block, testId should be cleared")
    val exception: IllegalStateException = intercept[IllegalStateException] {
      CucumberContext.getTestId
    }
    exception.getMessage should include("No test ID registered in CucumberContext")
  }

    "clear testId even if exception occurs during execution" in {
    Given("a testId")
    val testId: UUID = UUID.randomUUID()

    When("I use try/finally pattern and exception occurs")
    try
      CucumberContext.registerTestId(testId)

      // Simulate exception during Cucumber execution
      throw RuntimeException("Simulated Cucumber failure")
    catch
      case _: RuntimeException => // Expected
    finally
      CucumberContext.clear()

    Then("testId should still be cleared")
    val exception: IllegalStateException = intercept[IllegalStateException] {
      CucumberContext.getTestId
    }
    exception.getMessage should include("No test ID registered in CucumberContext")
  }

  // ============================================================================
  // Evidence Path Tests (jimfs integration)
  // ============================================================================

    "register and retrieve evidence path successfully" in {
    Given("an evidence path")
    import java.nio.file.{Path, Paths}
    val evidencePath: Path = Paths.get("/jimfs/test-123/evidence")

    When("I register the evidence path")
    CucumberContext.registerEvidencePath(evidencePath)

    Then("I should be able to retrieve it")
    val retrieved: Path = CucumberContext.getEvidencePath
    retrieved shouldBe evidencePath
  }

    "throw IllegalStateException when getEvidencePath called without registration" in {
    Given("no evidence path registered")

    When("I call getEvidencePath")
    Then("it should throw IllegalStateException with helpful message")
    val exception: IllegalStateException = intercept[IllegalStateException] {
      CucumberContext.getEvidencePath
    }

    exception.getMessage should include("No evidence path registered in CucumberContext")
  }

    "clear both testId and evidencePath together" in {
    Given("both testId and evidencePath registered")
    import java.nio.file.{Path, Paths}
    val testId: UUID = UUID.randomUUID()
    val evidencePath: Path = Paths.get("/jimfs/test-456/evidence")

    CucumberContext.registerTestId(testId)
    CucumberContext.registerEvidencePath(evidencePath)

    When("I clear the context")
    CucumberContext.clear()

    Then("both testId and evidencePath should be cleared")
    val testIdException: IllegalStateException = intercept[IllegalStateException] {
      CucumberContext.getTestId
    }
    testIdException.getMessage should include("No test ID registered")

    val pathException: IllegalStateException = intercept[IllegalStateException] {
      CucumberContext.getEvidencePath
    }
    pathException.getMessage should include("No evidence path registered")
  }

    "work correctly in CucumberExecutor pattern with both testId and evidence path" in {
    Given("testId and evidence path")
    import java.nio.file.{Path, Paths}
    val testId: UUID = UUID.randomUUID()
    val evidencePath: Path = Paths.get("/jimfs/test-789/evidence")

    When("I use the CucumberExecutor pattern")
    var retrievedTestId: Option[UUID] = None
    var retrievedPath: Option[Path] = None

    try
      CucumberContext.registerTestId(testId)
      CucumberContext.registerEvidencePath(evidencePath)

      retrievedTestId = Some(CucumberContext.getTestId)
      retrievedPath = Some(CucumberContext.getEvidencePath)
    finally
      CucumberContext.clear()

    Then("both should have been retrieved during execution")
    retrievedTestId shouldBe Some(testId)
    retrievedPath shouldBe Some(evidencePath)

    And("after finally block, both should be cleared")
    intercept[IllegalStateException] {
      CucumberContext.getTestId
    }
    intercept[IllegalStateException] {
      CucumberContext.getEvidencePath
    }
  }
  }
}