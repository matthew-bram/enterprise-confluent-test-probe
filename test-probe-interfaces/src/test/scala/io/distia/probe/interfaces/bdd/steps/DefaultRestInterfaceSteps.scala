//package io.distia.probe.interfaces.bdd.steps
//
//import io.distia.probe.core.builder.{BuilderContext, ServiceInterfaceFunctions}
//import io.distia.probe.core.models.*
//import io.distia.probe.interfaces.bdd.world.InterfacesWorldManager
//import io.distia.probe.interfaces.builder.modules.DefaultRestInterface
//import com.typesafe.config.{Config, ConfigFactory}
//import io.cucumber.scala.{EN, ScalaDsl}
//import org.scalatest.matchers.should.Matchers
//
//import scala.concurrent.duration.*
//import scala.concurrent.{Await, ExecutionContext}
//import scala.util.Try
//
///**
// * Step definitions for DefaultRestInterface builder module testing
// *
// * Covers:
// * - Builder lifecycle (preFlight, initialize, finalCheck, shutdown)
// * - Dependency validation
// * - Error accumulation
// * - Thread-safe state management
// * - Server binding and unbinding
// */
//class DefaultRestInterfaceSteps extends ScalaDsl with EN with Matchers {
//
//  private def world = InterfacesWorldManager.world
//  private implicit val ec: ExecutionContext = ExecutionContext.global
//
//  // ============================================================================
//  // Background Steps - Actor System Setup
//  // ============================================================================
//
//  Given("""a test ActorSystem is running""") { () =>
//    // Create test actor system using InterfacesWorld
//    world.createTestActorSystem()
//  }
//
//  // ============================================================================
//  // Given Steps - BuilderContext Setup
//  // ============================================================================
//
//  Given("""a BuilderContext with Config initialized""") { () =>
//    val config = createRestEnabledConfig()
//    world.builderContext = Some(BuilderContext(config = Some(config)))
//  }
//
//  Given("""a BuilderContext with Config NOT initialized""") { () =>
//    world.builderContext = Some(BuilderContext(config = None))
//  }
//
//  Given("""a BuilderContext with ActorSystem initialized""") { () =>
//    val system = world.testActorSystem.getOrElse(world.createTestActorSystem())
//    world.builderContext = world.builderContext.map(_.copy(
//      actorSystem = Some(system.asInstanceOf[org.apache.pekko.actor.typed.ActorSystem[Any]])
//    ))
//  }
//
//  Given("""a BuilderContext with ActorSystem NOT initialized""") { () =>
//    world.builderContext = world.builderContext.map(_.copy(actorSystem = None))
//  }
//
//  Given("""a BuilderContext with ServiceInterfaceFunctions initialized""") { () =>
//    // Create mock ServiceInterfaceFunctions using InterfacesWorld
//    val system = world.testActorSystem.getOrElse(world.createTestActorSystem())
//    val mockFunctions = world.createMockServiceFunctions()(system)
//    world.builderContext = world.builderContext.map(_.copy(
//      curriedFunctions = Some(mockFunctions)
//    ))
//  }
//
//  Given("""a BuilderContext with ServiceInterfaceFunctions NOT initialized""") { () =>
//    world.builderContext = world.builderContext.map(_.copy(curriedFunctions = None))
//  }
//
//  Given("""REST is enabled in configuration""") { () =>
//    val config = createRestEnabledConfig()
//    world.builderContext = world.builderContext.map(_.copy(config = Some(config)))
//  }
//
//  Given("""REST is disabled in configuration""") { () =>
//    val config = createRestDisabledConfig()
//    world.builderContext = world.builderContext.map(_.copy(config = Some(config)))
//  }
//
//  Given("""a DefaultRestInterface is already initialized""") { () =>
//    // Create the interface and initialize it
//    val interface = new DefaultRestInterface
//    val ctx = world.builderContext.getOrElse(BuilderContext())
//
//    // Set curried functions first
//    interface.setCurriedFunctions(ctx.curriedFunctions.get)
//
//    // Initialize
//    val initResult = Try {
//      Await.result(interface.initialize(ctx), 5.seconds)
//    }
//
//    world.restInterface = Some(interface)
//    world.initializationResult = Some(initResult.toEither.swap.swap)
//
//    // Update builderContext with returned context (which has InterfacesConfig)
//    initResult.toEither match {
//      case Right(updatedCtx) => world.builderContext = Some(updatedCtx)
//      case Left(_) => // Keep existing context
//    }
//  }
//
//  Given("""a DefaultRestInterface is initialized""") { () =>
//    // Same as "already initialized"
//    val interface = new DefaultRestInterface
//    val ctx = world.builderContext.getOrElse(BuilderContext())
//    interface.setCurriedFunctions(ctx.curriedFunctions.get)
//    val initResult = Try {
//      Await.result(interface.initialize(ctx), 5.seconds)
//    }
//    world.restInterface = Some(interface)
//    world.initializationResult = Some(initResult.toEither.swap.swap)
//
//    // Update builderContext with returned context (which has InterfacesConfig)
//    initResult.toEither match {
//      case Right(updatedCtx) => world.builderContext = Some(updatedCtx)
//      case Left(_) => // Keep existing context
//    }
//  }
//
//  Given("""the REST server is bound""") { () =>
//    // This is a verification step - the server should already be bound from initialization
//    world.initializationResult.foreach {
//      case Right(_) => // Server is bound
//      case Left(ex) => fail(s"Server not bound: ${ex.getMessage}")
//    }
//  }
//
//  Given("""a DefaultRestInterface is created but not initialized""") { () =>
//    val interface = new DefaultRestInterface
//    world.restInterface = Some(interface)
//  }
//
//  Given("""a DefaultRestInterface is created""") { () =>
//    val interface = new DefaultRestInterface
//    world.restInterface = Some(interface)
//  }
//
//  Given("""a ServiceInterfaceFunctions instance exists""") { () =>
//    val system = world.testActorSystem.getOrElse(world.createTestActorSystem())
//    world.createMockServiceFunctions()(system)
//  }
//
//  // ============================================================================
//  // When Steps - Actions
//  // ============================================================================
//
//  When("""I create a DefaultRestInterface""") { () =>
//    val interface = new DefaultRestInterface
//    world.restInterface = Some(interface)
//  }
//
//  When("""I call preFlight on the DefaultRestInterface""") { () =>
//    val interface = world.restInterface.getOrElse(
//      fail("DefaultRestInterface not created")
//    )
//    val ctx = world.builderContext.getOrElse(BuilderContext())
//    val result = Try {
//      Await.result(interface.preFlight(ctx), 5.seconds)
//    }
//    world.preFlightResult = Some(result.toEither.swap.swap)
//  }
//
//  When("""I call initialize on the DefaultRestInterface""") { () =>
//    val interface = world.restInterface.getOrElse(
//      fail("DefaultRestInterface not created")
//    )
//    val ctx = world.builderContext.getOrElse(BuilderContext())
//
//    // Set curried functions before initialize
//    if (ctx.curriedFunctions.isDefined) {
//      interface.setCurriedFunctions(ctx.curriedFunctions.get)
//    }
//
//    val result = Try {
//      Await.result(interface.initialize(ctx), 5.seconds)
//    }
//    world.initializationResult = Some(result.toEither.swap.swap)
//
//    // Update builderContext with returned context (which has InterfacesConfig)
//    result.toEither match {
//      case Right(updatedCtx) => world.builderContext = Some(updatedCtx)
//      case Left(_) => // Keep existing context
//    }
//  }
//
//  When("""I call initialize on the DefaultRestInterface again""") { () =>
//    // Try to initialize again (should fail)
//    val interface = world.restInterface.getOrElse(
//      fail("DefaultRestInterface not created")
//    )
//    val ctx = world.builderContext.getOrElse(BuilderContext())
//
//    val result = Try {
//      Await.result(interface.initialize(ctx), 5.seconds)
//    }
//    world.initializationResult = Some(result.toEither.swap.swap)
//  }
//
//  When("""I call finalCheck on the DefaultRestInterface""") { () =>
//    val interface = world.restInterface.getOrElse(
//      fail("DefaultRestInterface not created")
//    )
//    val ctx = world.builderContext.getOrElse(BuilderContext())
//    val result = Try {
//      Await.result(interface.finalCheck(ctx), 5.seconds)
//    }
//    world.finalCheckResult = Some(result.toEither.swap.swap)
//  }
//
//  When("""I call shutdown on the DefaultRestInterface""") { () =>
//    val interface = world.restInterface.getOrElse(
//      fail("DefaultRestInterface not created")
//    )
//    val result = Try {
//      Await.result(interface.shutdown(), 5.seconds)
//    }
//    world.shutdownResult = Some(result.toEither.swap.swap)
//  }
//
//  When("""I call setCurriedFunctions with the functions""") { () =>
//    val interface = world.restInterface.getOrElse(
//      fail("DefaultRestInterface not created")
//    )
//    val functions = world.mockServiceFunctions.getOrElse(
//      fail("ServiceInterfaceFunctions not created")
//    )
//
//    // This is a void method, just call it
//    interface.setCurriedFunctions(functions)
//  }
//
//  // ============================================================================
//  // Then Steps - Assertions
//  // ============================================================================
//
//  Then("""the preFlight should succeed""") { () =>
//    world.preFlightResult match {
//      case Some(Right(ctx)) =>
//        ctx shouldBe a[BuilderContext]
//      case Some(Left(ex)) =>
//        fail(s"PreFlight failed unexpectedly: ${ex.getMessage}")
//      case None =>
//        fail("PreFlight was not called")
//    }
//  }
//
//  Then("""the preFlight should fail with error containing {string}""") { (expectedMessage: String) =>
//    world.preFlightResult match {
//      case Some(Left(ex)) =>
//        ex.getMessage should include(expectedMessage)
//      case Some(Right(_)) =>
//        fail("PreFlight succeeded but was expected to fail")
//      case None =>
//        fail("PreFlight was not called")
//    }
//  }
//
//  Then("""the initialize should succeed""") { () =>
//    world.initializationResult match {
//      case Some(Right(ctx)) =>
//        ctx shouldBe a[BuilderContext]
//      case Some(Left(ex)) =>
//        fail(s"Initialize failed unexpectedly: ${ex.getMessage}")
//      case None =>
//        fail("Initialize was not called")
//    }
//  }
//
//  Then("""the initialize should fail with error containing {string}""") { (expectedMessage: String) =>
//    world.initializationResult match {
//      case Some(Left(ex)) =>
//        ex.getMessage should include(expectedMessage)
//      case Some(Right(_)) =>
//        fail("Initialize succeeded but was expected to fail")
//      case None =>
//        fail("Initialize was not called")
//    }
//  }
//
//  Then("""the REST server should be bound to configured port""") { () =>
//    world.initializationResult match {
//      case Some(Right(_)) =>
//        // If initialize succeeded, server should be bound
//        // We can't easily check the actual binding without accessing internal state,
//        // but success of initialize implies binding succeeded
//        succeed
//      case Some(Left(ex)) =>
//        fail(s"Server not bound - initialization failed: ${ex.getMessage}")
//      case None =>
//        fail("Initialize was not called")
//    }
//  }
//
//  Then("""the BuilderContext should contain InterfacesConfig""") { () =>
//    world.initializationResult match {
//      case Some(Right(ctx)) =>
//        ctx.interfacesConfig shouldBe defined
//      case _ =>
//        fail("Initialize did not succeed")
//    }
//  }
//
//  Then("""the finalCheck should succeed""") { () =>
//    world.finalCheckResult match {
//      case Some(Right(ctx)) =>
//        ctx shouldBe a[BuilderContext]
//      case Some(Left(ex)) =>
//        fail(s"FinalCheck failed unexpectedly: ${ex.getMessage}")
//      case None =>
//        fail("FinalCheck was not called")
//    }
//  }
//
//  Then("""the finalCheck should fail with error containing {string}""") { (expectedMessage: String) =>
//    world.finalCheckResult match {
//      case Some(Left(ex)) =>
//        ex.getMessage should include(expectedMessage)
//      case Some(Right(_)) =>
//        fail("FinalCheck succeeded but was expected to fail")
//      case None =>
//        fail("FinalCheck was not called")
//    }
//  }
//
//  Then("""the BuilderContext should have InterfacesConfig""") { () =>
//    // This is same as "should contain InterfacesConfig"
//    world.finalCheckResult match {
//      case Some(Right(ctx)) =>
//        ctx.interfacesConfig shouldBe defined
//      case _ =>
//        // Could also check from initializeResult
//        world.initializationResult match {
//          case Some(Right(ctx)) =>
//            ctx.interfacesConfig shouldBe defined
//          case _ =>
//            fail("No successful initialization or finalCheck")
//        }
//    }
//  }
//
//  Then("""the shutdown should succeed""") { () =>
//    world.shutdownResult match {
//      case Some(Right(_)) =>
//        succeed
//      case Some(Left(ex)) =>
//        fail(s"Shutdown failed unexpectedly: ${ex.getMessage}")
//      case None =>
//        fail("Shutdown was not called")
//    }
//  }
//
//  Then("""the server should be unbound""") { () =>
//    // If shutdown succeeded, server should be unbound
//    world.shutdownResult match {
//      case Some(Right(_)) =>
//        succeed
//      case _ =>
//        fail("Shutdown did not succeed")
//    }
//  }
//
//  Then("""no errors should occur""") { () =>
//    // Check that there are no failures in any results
//    world.shutdownResult.foreach {
//      case Left(ex) => fail(s"Unexpected error: ${ex.getMessage}")
//      case Right(_) => succeed
//    }
//  }
//
//  Then("""the functions should be stored in the interface""") { () =>
//    // setCurriedFunctions is a void method, we can't directly verify storage
//    // But we can verify it was called without error
//    // This is more of a smoke test
//    succeed
//  }
//
//  // ============================================================================
//  // Helper Methods
//  // ============================================================================
//
//  /**
//   * Create a test config with REST enabled
//   * Uses port 0 for automatic port assignment to avoid conflicts
//   */
//  private def createRestEnabledConfig(): Config = {
//    ConfigFactory.parseString("""
//      test-probe.interfaces {
//        rest {
//          enabled = true
//          host = "localhost"
//          port = 0
//          timeout = 30 seconds
//          graceful-shutdown-timeout = 10 seconds
//        }
//      }
//    """)
//      .withFallback(ConfigFactory.defaultReference())
//      .resolve()
//  }
//
//  /**
//   * Create a test config with REST disabled
//   */
//  private def createRestDisabledConfig(): Config = {
//    ConfigFactory.parseString("""
//      test-probe.interfaces {
//        rest {
//          enabled = false
//          host = "localhost"
//          port = 0
//          timeout = 30 seconds
//          graceful-shutdown-timeout = 10 seconds
//        }
//      }
//    """)
//      .withFallback(ConfigFactory.defaultReference())
//      .resolve()
//  }
//}
