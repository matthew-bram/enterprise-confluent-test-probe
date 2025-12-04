package io.distia.probe
package core
package builder

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Scheduler}
import org.apache.pekko.actor.typed.scaladsl.AskPattern.Askable
import org.apache.pekko.pattern.{AskTimeoutException, CircuitBreaker, CircuitBreakerOpenException}
import org.apache.pekko.util.Timeout
import core.models.QueueCommands.{QueueCommand, InitializeTestRequest, StartTestRequest, TestStatusRequest, QueueStatusRequest, CancelRequest}
import core.models.{InitializeTestResponse, QueueStatusResponse, ServiceResponse, ServiceTimeoutException, StartTestResponse, TestCancelledResponse, TestStatusResponse}
import core.models.ServiceUnavailableException

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import java.util.UUID

/**
 * Factory for creating ServiceInterfaceFunctions with QueueActor reference curried in
 *
 * This factory accepts a QueueActor reference as a parameter and returns a bundle
 * of curried functions. Each function has the QueueActor reference bound, so
 * interface layers can call business logic without knowing about actors.
 *
 * Pattern: Dependency Injection via currying
 * - Factory injects QueueActor reference into functions
 * - Interface layer receives pure functions (no actor knowledge needed)
 * - Testability: Can create mock function bundles for testing
 *
 * Circuit Breaker Integration:
 * - All actor asks wrapped with Pekko Circuit Breaker
 * - Fail-fast when circuit open (too many failures)
 * - Configuration: maxFailures=5, callTimeout=25s, resetTimeout=30s
 * - CircuitBreakerOpenException → ServiceUnavailableException
 * - AskTimeoutException → ServiceTimeoutException
 *
 * Created during DefaultActorSystem.initialize() after QueueActor is spawned.
 *
 * Example Usage:
 * {{{
 *   // In DefaultActorSystem.initialize():
 *   val queueActorRef: ActorRef[QueueCommand] = ... // from GuardianActor
 *   val curriedFunctions = ServiceInterfaceFunctionsFactory(queueActorRef, system)
 *
 *   // Later in interface layer:
 *   interface.setCurriedFunctions(curriedFunctions)
 *
 *   // Interface can now call:
 *   val response: Future[InitializeTestResponse] = curriedFunctions.initializeTest()
 * }}}
 *
 * @see ServiceInterfaceFunctions in core.builder package
 */
private[core] object ServiceInterfaceFunctionsFactory {

  /**
   * Create a ServiceInterfaceFunctions bundle with QueueActor reference curried in
   *
   * All actor ask operations are wrapped with a Circuit Breaker for fail-fast behavior
   * and resilience against cascading failures.
   *
   * Circuit Breaker Configuration:
   * - maxFailures: Number of consecutive failures before opening circuit (default: 5)
   * - callTimeout: Timeout for individual calls, should match ask timeout (default: 25s)
   * - resetTimeout: Time to wait before attempting recovery (default: 30s)
   *
   * @param queueActor Reference to the QueueActor (accepts QueueCommand messages)
   * @param system ActorSystem for scheduler and execution context
   * @param circuitBreakerMaxFailures Max consecutive failures before opening circuit
   * @param circuitBreakerCallTimeout Circuit breaker call timeout (should match ask timeout)
   * @param circuitBreakerResetTimeout Time to wait before attempting recovery
   * @param timeout Timeout for ask pattern operations
   * @param ec Execution context for Future composition
   * @return ServiceInterfaceFunctions bundle with all functions curried
   */
  def apply(
    queueActor: ActorRef[QueueCommand],
    system: ActorSystem[_],
    circuitBreakerMaxFailures: Int = 5,
    circuitBreakerCallTimeout: FiniteDuration = 25.seconds,
    circuitBreakerResetTimeout: FiniteDuration = 30.seconds
  )(implicit timeout: Timeout, ec: ExecutionContext): ServiceInterfaceFunctions = {

    implicit val scheduler: Scheduler = system.scheduler

    val breaker: CircuitBreaker = new CircuitBreaker(
      scheduler = system.classicSystem.scheduler,
      maxFailures = circuitBreakerMaxFailures,
      callTimeout = circuitBreakerCallTimeout,
      resetTimeout = circuitBreakerResetTimeout
    )(system.executionContext)

    def withCircuitBreaker[T](ask: => Future[T]): Future[T] =
      breaker.withCircuitBreaker(ask).recoverWith {
        case _: CircuitBreakerOpenException =>
          Future.failed(ServiceUnavailableException(
            "Service temporarily unavailable - circuit breaker open (too many failures)"
          ))
        case ex: AskTimeoutException =>
          Future.failed(ServiceTimeoutException(
            "Actor did not respond in time",
            ex
          ))
        case ex: java.util.concurrent.TimeoutException =>
          Future.failed(ServiceTimeoutException(
            "Actor did not respond in time",
            ex
          ))
      }

    ServiceInterfaceFunctions(

      initializeTest = () => {
        withCircuitBreaker(
          queueActor.ask[ServiceResponse](replyTo => InitializeTestRequest(replyTo))
            .collect { case r: InitializeTestResponse => r }
        )
      },

      startTest = (testId: UUID, bucket: String, testType: Option[String]) => {
        withCircuitBreaker(
          queueActor.ask[ServiceResponse](replyTo =>
            StartTestRequest(testId, bucket, testType, replyTo))
            .collect { case r: StartTestResponse => r }
        )
      },

      getStatus = (testId: UUID) => {
        withCircuitBreaker(
          queueActor.ask[ServiceResponse](replyTo => TestStatusRequest(testId, replyTo))
            .collect { case r: TestStatusResponse => r }
        )
      },

      getQueueStatus = (testId: Option[UUID]) => {
        withCircuitBreaker(
          queueActor.ask[ServiceResponse](replyTo => QueueStatusRequest(testId, replyTo))
            .collect { case r: QueueStatusResponse => r }
        )
      },

      cancelTest = (testId: UUID) => {
        withCircuitBreaker(
          queueActor.ask[ServiceResponse](replyTo => CancelRequest(testId, replyTo))
            .collect { case r: TestCancelledResponse => r }
        )
      }
    )
  }
}
