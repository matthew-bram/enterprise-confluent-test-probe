package io.distia.probe.interfaces.bdd.world

import io.distia.probe.core.builder.{BuilderContext, ServiceInterfaceFunctions}
import io.distia.probe.core.models.*
import io.distia.probe.interfaces.builder.modules.DefaultRestInterface
import io.distia.probe.interfaces.config.InterfacesConfig
import io.distia.probe.interfaces.models.rest.*
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http.ServerBinding

import java.util.UUID
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * InterfacesWorld Pattern for test-probe-interfaces component tests
 *
 * Provides:
 * - Builder module state (BuilderContext, DefaultRestInterface)
 * - Configuration state (test configs, parsed InterfacesConfig)
 * - Model conversion state (REST/Core request and response conversions)
 * - State tracking for test assertions
 *
 * Thread Safety:
 * - One instance per thread via InterfacesWorldManager
 * - Mutable vars for state tracking (thread-isolated)
 *
 * Pattern: EXACT copy of ActorWorld pattern from test-probe-core
 */
class InterfacesWorld {

  // ========== Builder Module State ==========

  var builderContext: Option[BuilderContext] = None
  var restInterface: Option[DefaultRestInterface] = None
  var serverBinding: Option[ServerBinding] = None
  var initializationResult: Option[Either[Throwable, BuilderContext]] = None
  var preFlightResult: Option[Either[Throwable, BuilderContext]] = None
  var finalCheckResult: Option[Either[Throwable, BuilderContext]] = None
  var shutdownResult: Option[Either[Throwable, Unit]] = None

  // ========== Configuration State ==========

  var testConfig: Option[Config] = None
  var parsedInterfacesConfig: Option[InterfacesConfig] = None
  var configParseResult: Option[Either[Throwable, InterfacesConfig]] = None
  var configFieldOverrides: Map[String, Any] = Map.empty
  var environmentVariables: Map[String, String] = Map.empty
  var systemProperties: Map[String, String] = Map.empty

  // ========== Model Conversion State (REST ↔ Core) ==========

  // REST Request models
  var restStartTestRequest: Option[RestStartTestRequest] = None
  var restTestStatusRequest: Option[RestTestStatusRequest] = None
  var restQueueStatusRequest: Option[RestQueueStatusRequest] = None
  var restCancelRequest: Option[RestCancelRequest] = None

  // Core Request parameters (converted from REST)
  var coreStartTestParams: Option[(UUID, String, Option[String])] = None
  var coreTestStatusParam: Option[UUID] = None
  var coreQueueStatusParam: Option[Option[UUID]] = None
  var coreCancelParam: Option[UUID] = None

  // Core Response models
  var coreInitializeTestResponse: Option[InitializeTestResponse] = None
  var coreStartTestResponse: Option[StartTestResponse] = None
  var coreTestStatusResponse: Option[TestStatusResponse] = None
  var coreQueueStatusResponse: Option[QueueStatusResponse] = None
  var coreTestCancelledResponse: Option[TestCancelledResponse] = None

  // REST Response models (converted from Core)
  var restInitializeTestResponse: Option[RestInitializeTestResponse] = None
  var restStartTestResponse: Option[RestStartTestResponse] = None
  var restTestStatusResponse: Option[RestTestStatusResponse] = None
  var restQueueStatusResponse: Option[RestQueueStatusResponse] = None
  var restTestCancelledResponse: Option[RestTestCancelledResponse] = None

  // ========== Test Infrastructure ==========

  // Test ActorSystem for DefaultRestInterface tests
  var testActorSystem: Option[ActorSystem[Nothing]] = None

  // Mock ServiceInterfaceFunctions for builder module tests
  var mockServiceFunctions: Option[ServiceInterfaceFunctions] = None

  // ========== Helper Methods ==========

  /**
   * Create a test ActorSystem for builder module tests
   */
  def createTestActorSystem(): ActorSystem[Nothing] = {
    val system = ActorSystem[Nothing](Behaviors.empty, "test-interfaces-system")
    testActorSystem = Some(system)
    system
  }

  /**
   * Create mock ServiceInterfaceFunctions for builder module tests
   * Returns Future.successful responses for all functions
   */
  def createMockServiceFunctions()(implicit system: ActorSystem[Nothing]): ServiceInterfaceFunctions = {
    implicit val ec: ExecutionContext = system.executionContext

    val initializeTestFunc: () => Future[InitializeTestResponse] =
      () => Future.successful(InitializeTestResponse(UUID.randomUUID(), "Test initialized"))

    val startTestFunc: (UUID, String, Option[String]) => Future[StartTestResponse] =
      (testId, bucket, testType) =>
        Future.successful(StartTestResponse(testId, accepted = true, testType, "Test started"))

    val getStatusFunc: UUID => Future[TestStatusResponse] =
      testId =>
        Future.successful(TestStatusResponse(testId, "Setup", None, None, None, None, None, None))

    val getQueueStatusFunc: Option[UUID] => Future[QueueStatusResponse] =
      _ =>
        Future.successful(QueueStatusResponse(0, 0, 0, 0, 0, 0, 0, None))

    val cancelTestFunc: UUID => Future[TestCancelledResponse] =
      testId =>
        Future.successful(TestCancelledResponse(testId, cancelled = true, Some("Test cancelled")))

    val functions = ServiceInterfaceFunctions(
      initializeTest = initializeTestFunc,
      startTest = startTestFunc,
      getStatus = getStatusFunc,
      getQueueStatus = getQueueStatusFunc,
      cancelTest = cancelTestFunc
    )

    mockServiceFunctions = Some(functions)
    functions
  }

  /**
   * Create a test Config from HOCON string
   *
   * Note: We invalidate ConfigFactory caches before parsing to ensure test isolation.
   * This prevents configs from previous scenarios from leaking through.
   */
  def createConfigFromHocon(hocon: String): Config = {
    // Clear ConfigFactory caches to prevent state leakage between scenarios
    ConfigFactory.invalidateCaches()

    val config = ConfigFactory.parseString(hocon)
      .withFallback(ConfigFactory.defaultReference())
      .resolve()
    testConfig = Some(config)
    config
  }

  /**
   * Create a test Config with field overrides
   */
  def createConfigWithOverrides(overrides: Map[String, Any]): Config = {
    val hoconOverrides = overrides.map {
      case (key, value: String) => s"$key = \"$value\""
      case (key, value: Boolean) => s"$key = $value"
      case (key, value: Int) => s"$key = $value"
      case (key, value) => s"$key = $value"
    }.mkString("\n")

    createConfigFromHocon(hoconOverrides)
  }

  /**
   * Reset world state
   */
  def reset(): Unit = {
    // Shutdown test ActorSystem if running
    testActorSystem.foreach { system =>
      implicit val ec: ExecutionContext = system.executionContext
      Await.ready(system.whenTerminated, 5.seconds)
    }

    // Clear all state
    builderContext = None
    restInterface = None
    serverBinding = None
    initializationResult = None
    preFlightResult = None
    finalCheckResult = None
    shutdownResult = None

    testConfig = None
    parsedInterfacesConfig = None
    configParseResult = None
    configFieldOverrides = Map.empty
    environmentVariables = Map.empty
    systemProperties = Map.empty

    restStartTestRequest = None
    restTestStatusRequest = None
    restQueueStatusRequest = None
    restCancelRequest = None

    coreStartTestParams = None
    coreTestStatusParam = None
    coreQueueStatusParam = None
    coreCancelParam = None

    coreInitializeTestResponse = None
    coreStartTestResponse = None
    coreTestStatusResponse = None
    coreQueueStatusResponse = None
    coreTestCancelledResponse = None

    restInitializeTestResponse = None
    restStartTestResponse = None
    restTestStatusResponse = None
    restQueueStatusResponse = None
    restTestCancelledResponse = None

    testActorSystem = None
    mockServiceFunctions = None
  }

  /**
   * Cleanup and shutdown
   */
  def shutdown(): Unit = {
    // Shutdown REST interface if running
    restInterface.foreach { interface =>
      try {
        Await.result(interface.shutdown()(scala.concurrent.ExecutionContext.global), 5.seconds)
      } catch {
        case _: Exception => // Ignore shutdown errors in tests
      }
    }

    // Shutdown test ActorSystem
    testActorSystem.foreach { system =>
      try {
        implicit val ec: ExecutionContext = system.executionContext
        Await.ready(system.whenTerminated, 5.seconds)
      } catch {
        case _: Exception => // Ignore termination errors in tests
      }
    }
  }
}
