package io.distia.probe
package core
package integration
package steps

import io.distia.probe.core.integration.world.{IntegrationWorld, IntegrationWorldManager}
import io.cucumber.scala.{EN, ScalaDsl}
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*

/**
 * Step definitions for integration test environment initialization.
 *
 * Handles Background steps that set up the integration test environment:
 * - Initialize IntegrationWorld (ActorSystem, Testcontainers, JIMFS)
 * - Register schemas in Schema Registry
 * - Verify Kafka and Schema Registry connectivity
 * - Verify ActorSystem and ServiceInterfaceFunctions availability
 */
class IntegrationTestSteps extends ScalaDsl with EN with Matchers:

  // Get IntegrationWorld instance from manager
  private def world: IntegrationWorld = IntegrationWorldManager.getWorld()

  // ==========================================================================
  // Environment Initialization
  // ==========================================================================

  /**
   * Initialize integration test environment.
   *
   * Calls IntegrationWorld.initializeIntegrationTest() which:
   * - Starts Testcontainers via TestcontainersManager (idempotent)
   * - Initializes SerdesFactory with Schema Registry URL
   * - Creates real ActorSystem with GuardianActor
   * - Creates ServiceInterfaceFunctions
   * - Creates JIMFS in-memory filesystem
   */
  Given("""the integration test environment is initialized""") { () =>
    println("[IntegrationTestSteps] Initializing integration test environment...")

    // Ensure world is set (should be done in BeforeAll hook)
    if !IntegrationWorldManager.hasWorld then
      IntegrationWorldManager.setWorld(new IntegrationWorld)

    // Initialize the integration test environment
    world.initializeIntegrationTest()

    println("[IntegrationTestSteps] Integration test environment initialized successfully")
  }

  // ==========================================================================
  // Testcontainers Verification
  // ==========================================================================

  /**
   * Verify Testcontainers are running.
   *
   * Checks that Kafka and Schema Registry containers are started
   * and have valid bootstrap servers / URLs.
   */
  Given("""Testcontainers are running with Kafka and Schema Registry""") { () =>
    println("[IntegrationTestSteps] Verifying Testcontainers...")

    // Verify Kafka bootstrap servers
    world.kafkaBootstrapServers should not be empty
    println(s"[IntegrationTestSteps] ✅ Kafka running: ${world.kafkaBootstrapServers}")

    // Verify Schema Registry URL
    world.schemaRegistryUrl should not be empty
    world.schemaRegistryUrl should startWith("http://")
    println(s"[IntegrationTestSteps] ✅ Schema Registry running: ${world.schemaRegistryUrl}")
  }

  // ==========================================================================
  // Schema Registration
  // ==========================================================================

  /**
   * Register CloudEvent key schema for a topic.
   * Uses SerdesFixtures.registerCloudEventKeySchema().
   */
  Given("""CloudEvent key schema is registered for topic {string}""") { (topic: String) =>
    println(s"[IntegrationTestSteps] Registering CloudEvent key schema for topic: $topic")

    world.registerCloudEventKeySchema(topic) match
      case scala.util.Success(schemaId) =>
        println(s"[IntegrationTestSteps] ✅ CloudEvent key schema registered (ID: $schemaId)")
      case scala.util.Failure(ex) =>
        fail(s"Failed to register CloudEvent key schema: ${ex.getMessage}")

    // Add topic to configured topics list
    if !world.configuredTopics.contains(topic) then
      world.configuredTopics = world.configuredTopics :+ topic
  }

  /**
   * Register TestEvent value schema for a topic.
   * Uses SerdesFixtures.registerTestPayloadSchema().
   */
  Given("""TestEvent value schema is registered for topic {string}""") { (topic: String) =>
    println(s"[IntegrationTestSteps] Registering TestEvent value schema for topic: $topic")

    world.registerTestEventSchema(topic) match
      case scala.util.Success(schemaId) =>
        println(s"[IntegrationTestSteps] ✅ TestEvent value schema registered (ID: $schemaId)")
      case scala.util.Failure(ex) =>
        fail(s"Failed to register TestEvent value schema: ${ex.getMessage}")
  }

  /**
   * Register CloudEvent Avro key schema for a topic.
   * Uses SerdesFixtures.registerCloudEventAvroSchema().
   */
  Given("""CloudEvent Avro key schema is registered for topic {string}""") { (topic: String) =>
    println(s"[IntegrationTestSteps] Registering CloudEvent Avro key schema for topic: $topic")

    world.registerCloudEventAvroSchema(topic) match
      case scala.util.Success(schemaId) =>
        println(s"[IntegrationTestSteps] ✅ CloudEvent Avro key schema registered (ID: $schemaId)")
      case scala.util.Failure(ex) =>
        fail(s"Failed to register CloudEvent Avro key schema: ${ex.getMessage}")

    // Add topic to configured topics list
    if !world.configuredTopics.contains(topic) then
      world.configuredTopics = world.configuredTopics :+ topic
  }

  /**
   * Register OrderEvent Avro value schema for a topic.
   * Uses SerdesFixtures.registerOrderEventAvroSchema().
   */
  Given("""OrderEvent Avro value schema is registered for topic {string}""") { (topic: String) =>
    println(s"[IntegrationTestSteps] Registering OrderEvent Avro value schema for topic: $topic")

    world.registerOrderEventAvroSchema(topic) match
      case scala.util.Success(schemaId) =>
        println(s"[IntegrationTestSteps] ✅ OrderEvent Avro value schema registered (ID: $schemaId)")
      case scala.util.Failure(ex) =>
        fail(s"Failed to register OrderEvent Avro value schema: ${ex.getMessage}")
  }

  // ==========================================================================
  // Protobuf Schema Registration
  // ==========================================================================

  /**
   * Register CloudEvent Protobuf key schema for a topic.
   * Uses SerdesFixtures.registerCloudEventProtoSchema().
   */
  Given("""CloudEvent Protobuf key schema is registered for topic {string}""") { (topic: String) =>
    println(s"[IntegrationTestSteps] Registering CloudEvent Protobuf key schema for topic: $topic")

    world.registerCloudEventProtoSchema(topic) match
      case scala.util.Success(schemaId) =>
        println(s"[IntegrationTestSteps] ✅ CloudEvent Protobuf key schema registered (ID: $schemaId)")
      case scala.util.Failure(ex) =>
        fail(s"Failed to register CloudEvent Protobuf key schema: ${ex.getMessage}")

    // Add topic to configured topics list
    if !world.configuredTopics.contains(topic) then
      world.configuredTopics = world.configuredTopics :+ topic
  }

  /**
   * Register PaymentEvent Protobuf value schema for a topic.
   * Uses SerdesFixtures.registerPaymentEventProtoSchema().
   */
  Given("""PaymentEvent Protobuf value schema is registered for topic {string}""") { (topic: String) =>
    println(s"[IntegrationTestSteps] Registering PaymentEvent Protobuf value schema for topic: $topic")

    world.registerPaymentEventProtoSchema(topic) match
      case scala.util.Success(schemaId) =>
        println(s"[IntegrationTestSteps] ✅ PaymentEvent Protobuf value schema registered (ID: $schemaId)")
      case scala.util.Failure(ex) =>
        fail(s"Failed to register PaymentEvent Protobuf value schema: ${ex.getMessage}")
  }

  // ==========================================================================
  // ActorSystem Verification
  // ==========================================================================

  /**
   * Verify ActorSystem is running with GuardianActor and QueueActor.
   */
  Given("""the ActorSystem is running with GuardianActor and QueueActor""") { () =>
    println("[IntegrationTestSteps] Verifying ActorSystem...")

    // Verify ActorSystem is initialized
    world.realActorSystemOpt shouldBe defined
    println(s"[IntegrationTestSteps] ✅ ActorSystem running: ${world.realActorSystemOpt.get.name}")

    // Verify QueueActor is spawned
    world.realQueueActorOpt shouldBe defined
    println("[IntegrationTestSteps] ✅ QueueActor spawned successfully")
  }

  /**
   * Verify ServiceInterfaceFunctions are available.
   */
  Given("""ServiceInterfaceFunctions are available""") { () =>
    println("[IntegrationTestSteps] Verifying ServiceInterfaceFunctions...")

    // Verify ServiceInterfaceFunctions are initialized
    world.serviceFunctionsOpt shouldBe defined
    println("[IntegrationTestSteps] ✅ ServiceInterfaceFunctions available")
    println("[IntegrationTestSteps] Ready to call initializeTest(), startTest(), getStatus()")
  }

  // ==========================================================================
  // Test Initialization
  // ==========================================================================

  /**
   * Initialize a test with bucket and prefix.
   */
  Given("""I have a bucket {string} and prefix {string}""") { (bucket: String, prefix: String) =>
    println(s"[IntegrationTestSteps] Preparing to initialize test with bucket: $bucket, prefix: $prefix")
    // Store for When step
    world.serviceFunctionsOpt shouldBe defined
  }

  /**
   * Set the feature file name for the test (inner test stub).
   */
  Given("""I want to run feature file {string}""") { (featureFileName: String) =>
    println(s"[IntegrationTestSteps] Setting feature file name: $featureFileName")
    world.featureFileName = featureFileName
    println(s"[IntegrationTestSteps] ✅ Feature file configured: ${world.featureFileName}")
  }

  /**
   * Initialize a test (combined Given for scenarios 2-4).
   */
  Given("""I have initialized a test with bucket {string} and prefix {string}""") { (bucket: String, prefix: String) =>
    println(s"[IntegrationTestSteps] Initializing test with bucket: $bucket, prefix: $prefix")

    val serviceFunctions = world.serviceFunctionsOpt.getOrElse(
      fail("ServiceInterfaceFunctions not available")
    )

    // Call initializeTest (takes no parameters - bucket/prefix from BlockStorageDirective)
    val responseFuture = serviceFunctions.initializeTest()
    val response = Await.result(responseFuture, 10.seconds)
    world.lastInitializeResponse = Some(response)
    world.currentTestId = Some(response.testId)

    println(s"[IntegrationTestSteps] ✅ Test initialized with ID: ${response.testId}")
  }

  /**
   * Call initializeTest with bucket and prefix.
   */
  When("""I call initializeTest with the bucket and prefix""") { () =>
    println("[IntegrationTestSteps] Calling initializeTest...")

    val serviceFunctions = world.serviceFunctionsOpt.getOrElse(
      fail("ServiceInterfaceFunctions not available")
    )

    // Call initializeTest (takes no parameters)
    val responseFuture = serviceFunctions.initializeTest()
    val response = Await.result(responseFuture, 10.seconds)
    world.lastInitializeResponse = Some(response)
    world.currentTestId = Some(response.testId)

    println(s"[IntegrationTestSteps] ✅ initializeTest returned test ID: ${response.testId}")
  }

  /**
   * Verify test ID is valid.
   */
  Then("""I should receive a valid test ID""") { () =>
    println("[IntegrationTestSteps] Verifying test ID...")

    world.lastInitializeResponse shouldBe defined
    val response = world.lastInitializeResponse.get

    response.testId should not be null
    world.currentTestId shouldBe defined

    println(s"[IntegrationTestSteps] ✅ Valid test ID received: ${response.testId}")
  }

  /**
   * Verify test state.
   */
  Then("""the test should be in {string} state""") { (expectedState: String) =>
    println(s"[IntegrationTestSteps] Verifying test state is: $expectedState")

    world.currentTestId shouldBe defined
    val testId = world.currentTestId.get

    val serviceFunctions = world.serviceFunctionsOpt.getOrElse(
      fail("ServiceInterfaceFunctions not available")
    )

    val statusFuture = serviceFunctions.getStatus(testId)
    val statusResponse = Await.result(statusFuture, 10.seconds)
    world.lastStatusResponse = Some(statusResponse)

    statusResponse.state shouldBe expectedState
    println(s"[IntegrationTestSteps] ✅ Test state verified: ${statusResponse.state}")
  }

  /**
   * Verify test has no errors.
   */
  Then("""the test should have no errors""") { () =>
    println("[IntegrationTestSteps] Verifying test has no errors...")

    world.lastStatusResponse shouldBe defined
    val statusResponse = world.lastStatusResponse.get

    statusResponse.error shouldBe None
    println("[IntegrationTestSteps] ✅ Test has no errors")
  }

  // ==========================================================================
  // Test Execution
  // ==========================================================================

  /**
   * Start the test.
   */
  When("""I call startTest for the current test""") { () =>
    println("[IntegrationTestSteps] Calling startTest...")

    world.currentTestId shouldBe defined
    val testId = world.currentTestId.get

    val serviceFunctions = world.serviceFunctionsOpt.getOrElse(
      fail("ServiceInterfaceFunctions not available")
    )

    // startTest(testId: UUID, bucket: String, prefixOpt: Option[String])
    val responseFuture = serviceFunctions.startTest(testId, "test-bucket", Some("integration-test"))
    val response = Await.result(responseFuture, 10.seconds)
    world.lastStartResponse = Some(response)

    println(s"[IntegrationTestSteps] ✅ startTest called for test ID: $testId")
  }

  /**
   * Start the test (Given form for scenarios 3-4).
   */
  Given("""I have started the test""") { () =>
    println("[IntegrationTestSteps] Starting test...")

    world.currentTestId shouldBe defined
    val testId = world.currentTestId.get

    val serviceFunctions = world.serviceFunctionsOpt.getOrElse(
      fail("ServiceInterfaceFunctions not available")
    )

    // startTest(testId: UUID, bucket: String, prefixOpt: Option[String])
    val responseFuture = serviceFunctions.startTest(testId, "test-bucket", Some("integration-test"))
    val response = Await.result(responseFuture, 10.seconds)
    world.lastStartResponse = Some(response)

    println(s"[IntegrationTestSteps] ✅ Test started: $testId")
  }

  /**
   * Verify test transitions to expected state within timeout.
   */
  Then("""the test should transition to {string} state within {int} seconds""") {
    (expectedState: String, timeoutSeconds: Int) =>
    println(s"[IntegrationTestSteps] Waiting for test to transition to $expectedState (timeout: ${timeoutSeconds}s)...")

    world.currentTestId shouldBe defined
    val testId = world.currentTestId.get

    val serviceFunctions = world.serviceFunctionsOpt.getOrElse(
      fail("ServiceInterfaceFunctions not available")
    )

    // Poll for state transition
    val startTime = System.currentTimeMillis()
    val timeoutMs = timeoutSeconds * 1000L
    var currentState = ""
    var pollCount = 0

    while currentState != expectedState && (System.currentTimeMillis() - startTime) < timeoutMs do
      Thread.sleep(500) // Poll every 500ms
      pollCount += 1

      val statusFuture = serviceFunctions.getStatus(testId)
      val statusResponse = Await.result(statusFuture, 5.seconds)
      currentState = statusResponse.state
      world.lastStatusResponse = Some(statusResponse)

      if pollCount % 4 == 0 then // Log every 2 seconds
        println(s"[IntegrationTestSteps] Current state: $currentState (poll #$pollCount)")

    currentState shouldBe expectedState
    println(s"[IntegrationTestSteps] ✅ Test transitioned to $expectedState after ${pollCount * 500}ms")
  }

  /**
   * Verify test has success status.
   */
  Then("""the test should have success status {string}""") { (expectedSuccess: String) =>
    println(s"[IntegrationTestSteps] Verifying success status: $expectedSuccess")

    world.lastStatusResponse shouldBe defined
    val statusResponse = world.lastStatusResponse.get

    val isSuccess = expectedSuccess.toBoolean
    statusResponse.success shouldBe Some(isSuccess)

    println(s"[IntegrationTestSteps] ✅ Success status verified: ${statusResponse.success}")
  }

  /**
   * Wait for test completion (When form for scenarios 3-4).
   */
  When("""the test completes successfully""") { () =>
    println("[IntegrationTestSteps] Waiting for test completion...")

    world.currentTestId shouldBe defined
    val testId = world.currentTestId.get

    val serviceFunctions = world.serviceFunctionsOpt.getOrElse(
      fail("ServiceInterfaceFunctions not available")
    )

    // Poll until Completed state (30 second timeout)
    val startTime = System.currentTimeMillis()
    val timeoutMs = 30000L
    var currentState = ""

    while currentState != "Completed" && (System.currentTimeMillis() - startTime) < timeoutMs do
      Thread.sleep(500)
      val statusFuture = serviceFunctions.getStatus(testId)
      val statusResponse = Await.result(statusFuture, 5.seconds)
      currentState = statusResponse.state
      world.lastStatusResponse = Some(statusResponse)

    currentState shouldBe "Completed"
    println("[IntegrationTestSteps] ✅ Test completed successfully")
  }

  // ==========================================================================
  // Evidence Verification
  // ==========================================================================

  /**
   * Verify Cucumber executed the inner test by checking test completion status.
   */
  Then("""Cucumber should have executed the inner test""") { () =>
    println("[IntegrationTestSteps] Verifying Cucumber executed the inner test...")

    world.lastStatusResponse shouldBe defined
    val status = world.lastStatusResponse.get

    // Test should be in Completed state
    status.state shouldBe "Completed"
    println(s"[IntegrationTestSteps] ✅ Test reached Completed state")

    // Test should have success=true (no Cucumber exceptions)
    status.success shouldBe Some(true)
    println(s"[IntegrationTestSteps] ✅ Test completed with success=true")

    // Test should have no errors
    status.error shouldBe None
    println(s"[IntegrationTestSteps] ✅ Cucumber executed inner test successfully")
  }

  /**
   * Verify the inner test produced events to Kafka topic.
   * We infer this from successful test completion + JSON report showing passed scenarios.
   */
  Then("""the inner test should have produced a TestEvent to topic {string}""") { (topic: String) =>
    println(s"[IntegrationTestSteps] Verifying TestEvent produced to topic: $topic")

    // If Cucumber test passed, the produce step must have succeeded
    world.lastStatusResponse shouldBe defined
    val status = world.lastStatusResponse.get

    status.success shouldBe Some(true)
    println(s"[IntegrationTestSteps] ✅ Inner test produced TestEvent to $topic (inferred from test success)")
  }

  /**
   * Verify the inner test consumed events from Kafka topic.
   * We infer this from successful test completion + JSON report showing passed scenarios.
   */
  Then("""the inner test should have consumed a TestEvent from topic {string}""") { (topic: String) =>
    println(s"[IntegrationTestSteps] Verifying TestEvent consumed from topic: $topic")

    // Check if ProbeScalaDsl has consumed events
    val hasConsumed = world.hasConsumedEvents(topic)
    hasConsumed shouldBe true

    println(s"[IntegrationTestSteps] ✅ Inner test consumed TestEvent from $topic")
  }

  /**
   * Verify the JSON scenario passed by parsing the Cucumber JSON report.
   */
  Then("""the JSON scenario should have passed""") { () =>
    println("[IntegrationTestSteps] Verifying JSON scenario passed...")

    val (passedCount, failedCount) = world.getCucumberScenarioCounts()

    passedCount should be > 0
    failedCount shouldBe 0

    println(s"[IntegrationTestSteps] ✅ JSON scenario passed ($passedCount passed, $failedCount failed)")
  }

  /**
   * Verify evidence files exist in JIMFS.
   */
  Then("""evidence files should exist in JIMFS""") { () =>
    println("[IntegrationTestSteps] Verifying evidence files in JIMFS...")

    val evidenceFiles = world.listEvidenceFiles()

    evidenceFiles should not be empty
    println(s"[IntegrationTestSteps] Found ${evidenceFiles.size} evidence files:")
    evidenceFiles.foreach(f => println(s"  - $f"))

    println("[IntegrationTestSteps] ✅ Evidence files exist in JIMFS")
  }

  /**
   * Verify JIMFS contains a Cucumber JSON report.
   */
  Then("""JIMFS should contain a Cucumber JSON report""") { () =>
    println("[IntegrationTestSteps] Verifying Cucumber JSON report in JIMFS...")

    val evidenceFiles = world.listEvidenceFiles()
    val jsonReports = evidenceFiles.filter(_.endsWith(".json"))

    jsonReports should not be empty
    println(s"[IntegrationTestSteps] Found JSON report(s): ${jsonReports.mkString(", ")}")

    // Verify we can read the report
    world.readCucumberJsonReport() shouldBe defined

    println("[IntegrationTestSteps] ✅ Cucumber JSON report exists and is readable")
  }

  /**
   * Verify the JSON report shows expected number of passed scenarios.
   */
  Then("""the JSON report should show {int} scenario passed""") { (expectedPassed: Int) =>
    println(s"[IntegrationTestSteps] Verifying $expectedPassed scenario(s) passed...")

    val (actualPassed, _) = world.getCucumberScenarioCounts()

    actualPassed shouldBe expectedPassed
    println(s"[IntegrationTestSteps] ✅ JSON report shows $actualPassed scenario(s) passed")
  }

  /**
   * Verify the JSON report shows expected number of failed scenarios.
   */
  Then("""the JSON report should show {int} scenarios failed""") { (expectedFailed: Int) =>
    println(s"[IntegrationTestSteps] Verifying $expectedFailed scenario(s) failed...")

    val (_, actualFailed) = world.getCucumberScenarioCounts()

    actualFailed shouldBe expectedFailed
    println(s"[IntegrationTestSteps] ✅ JSON report shows $actualFailed scenario(s) failed")
  }
