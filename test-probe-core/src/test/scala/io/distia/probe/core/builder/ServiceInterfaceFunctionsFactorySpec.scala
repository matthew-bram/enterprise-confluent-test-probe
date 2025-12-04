package io.distia.probe
package core
package builder

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.util.Timeout
import org.apache.pekko.pattern.CircuitBreakerOpenException
import org.apache.pekko.pattern.AskTimeoutException
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import core.models.QueueCommands.{QueueCommand, InitializeTestRequest, StartTestRequest, TestStatusRequest, QueueStatusRequest, CancelRequest}
import core.models.{ServiceResponse, InitializeTestResponse, StartTestResponse, QueueStatusResponse, TestCancelledResponse, ServiceTimeoutException}
import core.models.{TestStatusResponse as ServiceTestStatusResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

/**
 * Comprehensive test suite for ServiceInterfaceFunctionsFactory
 *
 * Tests:
 * - Factory creates all 5 curried functions
 * - Each function successfully communicates with QueueActor
 * - Circuit breaker integration (open circuit → ServiceUnavailableException)
 * - Ask timeout handling (timeout → ServiceTimeoutException)
 * - Circuit breaker configuration parameters
 *
 * Coverage target: 100% of ServiceInterfaceFunctionsFactory (32 statements)
 */
private[core] class ServiceInterfaceFunctionsFactorySpec
  extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val timeout: Timeout = Timeout(3.seconds)

  // Extended patience for circuit breaker tests
  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(100, Millis))

  // Test actor system
  private var testSystem: ActorSystem[Nothing] = _

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(Behaviors.empty, "ServiceInterfaceFunctionsFactorySpec")
  }

  override def afterAll(): Unit = {
    testSystem.terminate()
    scala.concurrent.Await.result(testSystem.whenTerminated, 10.seconds)
  }

  // ========== TEST HELPERS ==========

  /**
   * Create a mock QueueActor that responds successfully to all commands
   */
  private def createSuccessfulQueueActor(): ActorRef[QueueCommand] = {
    testSystem.systemActorOf(
      Behaviors.receive[QueueCommand] { (context, message) =>
        message match {
          case InitializeTestRequest(replyTo) =>
            val testId = UUID.randomUUID()
            replyTo ! InitializeTestResponse(testId)
            Behaviors.same

          case StartTestRequest(testId, bucket, testType, replyTo) =>
            replyTo ! StartTestResponse(testId, accepted = true, testType, "Test started")
            Behaviors.same

          case TestStatusRequest(testId, replyTo) =>
            replyTo ! ServiceTestStatusResponse(testId, state = "Running", bucket = None, testType = None,
              startTime = None, endTime = None, success = None, error = None)
            Behaviors.same

          case QueueStatusRequest(testId, replyTo) =>
            replyTo ! QueueStatusResponse(totalTests = 1, setupCount = 0, loadingCount = 0, loadedCount = 0,
              testingCount = 1, completedCount = 0, exceptionCount = 0, currentlyTesting = testId)
            Behaviors.same

          case CancelRequest(testId, replyTo) =>
            replyTo ! TestCancelledResponse(testId, cancelled = true, Some("Test cancelled"))
            Behaviors.same
        }
      },
      "successful-queue-actor-" + UUID.randomUUID().toString.take(8)
    )
  }

  /**
   * Create a mock QueueActor that never responds (triggers timeout)
   */
  private def createTimeoutQueueActor(): ActorRef[QueueCommand] = {
    testSystem.systemActorOf(
      Behaviors.receive[QueueCommand] { (context, message) =>
        // Never respond - will trigger AskTimeoutException
        Behaviors.same
      },
      "timeout-queue-actor-" + UUID.randomUUID().toString.take(8)
    )
  }

  // ========== FACTORY CREATION TESTS ==========

  "ServiceInterfaceFunctionsFactory.apply()" should {

    "create ServiceInterfaceFunctions bundle with all 5 curried functions" in {
      val queueActor = createSuccessfulQueueActor()

      val functions = ServiceInterfaceFunctionsFactory(queueActor, testSystem)

      // Verify all functions are non-null
      functions.initializeTest should not be null
      functions.startTest should not be null
      functions.getStatus should not be null
      functions.getQueueStatus should not be null
      functions.cancelTest should not be null
    }

    "use default circuit breaker parameters when not specified" in {
      val queueActor = createSuccessfulQueueActor()

      // Should not throw - default parameters should work
      val functions = ServiceInterfaceFunctionsFactory(queueActor, testSystem)

      whenReady(functions.initializeTest()) { response =>
        response shouldBe a [InitializeTestResponse]
      }
    }

    "accept custom circuit breaker maxFailures parameter" in {
      val queueActor = createSuccessfulQueueActor()

      val functions = ServiceInterfaceFunctionsFactory(
        queueActor,
        testSystem,
        circuitBreakerMaxFailures = 10  // Custom value
      )

      whenReady(functions.initializeTest()) { response =>
        response shouldBe a [InitializeTestResponse]
      }
    }

    "accept custom circuit breaker callTimeout parameter" in {
      val queueActor = createSuccessfulQueueActor()

      val functions = ServiceInterfaceFunctionsFactory(
        queueActor,
        testSystem,
        circuitBreakerCallTimeout = 10.seconds  // Custom value
      )

      whenReady(functions.initializeTest()) { response =>
        response shouldBe a [InitializeTestResponse]
      }
    }

    "accept custom circuit breaker resetTimeout parameter" in {
      val queueActor = createSuccessfulQueueActor()

      val functions = ServiceInterfaceFunctionsFactory(
        queueActor,
        testSystem,
        circuitBreakerResetTimeout = 60.seconds  // Custom value
      )

      whenReady(functions.initializeTest()) { response =>
        response shouldBe a [InitializeTestResponse]
      }
    }

    "accept all custom circuit breaker parameters together" in {
      val queueActor = createSuccessfulQueueActor()

      val functions = ServiceInterfaceFunctionsFactory(
        queueActor,
        testSystem,
        circuitBreakerMaxFailures = 10,
        circuitBreakerCallTimeout = 15.seconds,
        circuitBreakerResetTimeout = 45.seconds
      )

      whenReady(functions.initializeTest()) { response =>
        response shouldBe a [InitializeTestResponse]
      }
    }
  }

  // ========== INITIALIZE TEST FUNCTION TESTS ==========

  "ServiceInterfaceFunctions.initializeTest()" should {

    "successfully ask QueueActor and return InitializeTestResponse" in {
      val queueActor = createSuccessfulQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(queueActor, testSystem)

      whenReady(functions.initializeTest()) { response =>
        response shouldBe a [InitializeTestResponse]
        response.testId should not be null
      }
    }

    "map AskTimeoutException to ServiceTimeoutException" in {
      val queueActor = createTimeoutQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(
        queueActor,
        testSystem,
        circuitBreakerCallTimeout = 500.millis  // Short timeout
      )(Timeout(500.millis), ec)

      whenReady(functions.initializeTest().failed) { exception =>
        exception shouldBe a [ServiceTimeoutException]
        exception.getMessage should include("Actor did not respond in time")
      }
    }
  }

  // ========== START TEST FUNCTION TESTS ==========

  "ServiceInterfaceFunctions.startTest()" should {

    "successfully ask QueueActor and return StartTestResponse" in {
      val queueActor = createSuccessfulQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(queueActor, testSystem)

      val testId = UUID.randomUUID()
      whenReady(functions.startTest(testId, "test-bucket", Some("functional"))) { response =>
        response shouldBe a [StartTestResponse]
        response.testId shouldBe testId
        response.accepted shouldBe true
        response.testType shouldBe Some("functional")
      }
    }

    "successfully call with None testType" in {
      val queueActor = createSuccessfulQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(queueActor, testSystem)

      val testId = UUID.randomUUID()
      whenReady(functions.startTest(testId, "test-bucket", None)) { response =>
        response shouldBe a [StartTestResponse]
        response.testId shouldBe testId
      }
    }

    "map AskTimeoutException to ServiceTimeoutException" in {
      val queueActor = createTimeoutQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(
        queueActor,
        testSystem,
        circuitBreakerCallTimeout = 500.millis
      )(Timeout(500.millis), ec)

      val testId = UUID.randomUUID()
      whenReady(functions.startTest(testId, "test-bucket", Some("functional")).failed) { exception =>
        exception shouldBe a [ServiceTimeoutException]
        exception.getMessage should include("Actor did not respond in time")
      }
    }
  }

  // ========== GET STATUS FUNCTION TESTS ==========

  "ServiceInterfaceFunctions.getStatus()" should {

    "successfully ask QueueActor and return TestStatusResponse" in {
      val queueActor = createSuccessfulQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(queueActor, testSystem)

      val testId = UUID.randomUUID()
      whenReady(functions.getStatus(testId)) { response =>
        response shouldBe a [ServiceTestStatusResponse]
        response.testId shouldBe testId
        response.state shouldBe "Running"
      }
    }

    "map AskTimeoutException to ServiceTimeoutException" in {
      val queueActor = createTimeoutQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(
        queueActor,
        testSystem,
        circuitBreakerCallTimeout = 500.millis
      )(Timeout(500.millis), ec)

      val testId = UUID.randomUUID()
      whenReady(functions.getStatus(testId).failed) { exception =>
        exception shouldBe a [ServiceTimeoutException]
        exception.getMessage should include("Actor did not respond in time")
      }
    }
  }

  // ========== GET QUEUE STATUS FUNCTION TESTS ==========

  "ServiceInterfaceFunctions.getQueueStatus()" should {

    "successfully ask QueueActor with Some(testId)" in {
      val queueActor = createSuccessfulQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(queueActor, testSystem)

      val testId = UUID.randomUUID()
      whenReady(functions.getQueueStatus(Some(testId))) { response =>
        response shouldBe a [QueueStatusResponse]
        response.setupCount shouldBe 0
        response.testingCount shouldBe 1
      }
    }

    "successfully ask QueueActor with None" in {
      val queueActor = createSuccessfulQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(queueActor, testSystem)

      whenReady(functions.getQueueStatus(None)) { response =>
        response shouldBe a [QueueStatusResponse]
        response.setupCount shouldBe 0
        response.testingCount shouldBe 1
      }
    }

    "map AskTimeoutException to ServiceTimeoutException" in {
      val queueActor = createTimeoutQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(
        queueActor,
        testSystem,
        circuitBreakerCallTimeout = 500.millis
      )(Timeout(500.millis), ec)

      whenReady(functions.getQueueStatus(None).failed) { exception =>
        exception shouldBe a [ServiceTimeoutException]
        exception.getMessage should include("Actor did not respond in time")
      }
    }
  }

  // ========== CANCEL TEST FUNCTION TESTS ==========

  "ServiceInterfaceFunctions.cancelTest()" should {

    "successfully ask QueueActor and return TestCancelledResponse" in {
      val queueActor = createSuccessfulQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(queueActor, testSystem)

      val testId = UUID.randomUUID()
      whenReady(functions.cancelTest(testId)) { response =>
        response shouldBe a [TestCancelledResponse]
        response.testId shouldBe testId
        response.cancelled shouldBe true
        response.message.get should include("cancelled")
      }
    }

    "map AskTimeoutException to ServiceTimeoutException" in {
      val queueActor = createTimeoutQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(
        queueActor,
        testSystem,
        circuitBreakerCallTimeout = 500.millis
      )(Timeout(500.millis), ec)

      val testId = UUID.randomUUID()
      whenReady(functions.cancelTest(testId).failed) { exception =>
        exception shouldBe a [ServiceTimeoutException]
        exception.getMessage should include("Actor did not respond in time")
      }
    }
  }

  // ========== CIRCUIT BREAKER INTEGRATION TESTS ==========

  "ServiceInterfaceFunctions circuit breaker integration" should {

    "wrap all ask operations with circuit breaker" in {
      val queueActor = createSuccessfulQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(queueActor, testSystem)

      // All functions should complete successfully (circuit breaker wrapping doesn't break them)
      whenReady(functions.initializeTest()) { _ => succeed }
      whenReady(functions.startTest(UUID.randomUUID(), "bucket", None)) { _ => succeed }
      whenReady(functions.getStatus(UUID.randomUUID())) { _ => succeed }
      whenReady(functions.getQueueStatus(None)) { _ => succeed }
      whenReady(functions.cancelTest(UUID.randomUUID())) { _ => succeed }
    }

    "handle errors gracefully without breaking circuit" in {
      val queueActor = createTimeoutQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(
        queueActor,
        testSystem,
        circuitBreakerMaxFailures = 10,  // High threshold
        circuitBreakerCallTimeout = 500.millis
      )(Timeout(500.millis), ec)

      // Single failure should not open circuit (maxFailures = 10)
      whenReady(functions.initializeTest().failed) { exception =>
        exception shouldBe a [ServiceTimeoutException]
      }

      // Circuit should still be closed after one failure
      whenReady(functions.initializeTest().failed) { exception =>
        exception shouldBe a [ServiceTimeoutException]
      }
    }
  }

  // ========== ERROR MAPPING TESTS ==========

  "ServiceInterfaceFunctions error mapping" should {

    "preserve original exception message in ServiceTimeoutException" in {
      val queueActor = createTimeoutQueueActor()
      val functions = ServiceInterfaceFunctionsFactory(
        queueActor,
        testSystem,
        circuitBreakerCallTimeout = 500.millis
      )(Timeout(500.millis), ec)

      whenReady(functions.initializeTest().failed) { exception =>
        exception shouldBe a [ServiceTimeoutException]
        exception.getMessage shouldBe "Actor did not respond in time"
        // Cause can be either AskTimeoutException or TimeoutException depending on which timeout fires first
        exception.getCause should (be (a [AskTimeoutException]) or be (a [java.util.concurrent.TimeoutException]))
      }
    }
  }
}
