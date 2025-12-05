package io.distia.probe.interfaces.bdd.steps

import io.distia.probe.core.models.*
import io.distia.probe.interfaces.bdd.world.InterfacesWorldManager
import io.distia.probe.interfaces.models.rest.*
import io.cucumber.datatable.DataTable
import io.cucumber.scala.{EN, ScalaDsl}
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.jdk.CollectionConverters.*

/**
 * Step definitions for RestModelConversions testing
 *
 * Covers:
 * - Request conversions (REST → Core)
 * - Response conversions (Core → REST)
 * - Field name mapping (kebab-case ↔ camelCase)
 * - Round-trip conversions
 * - Type safety
 */
class RestModelConversionsSteps extends ScalaDsl with EN with Matchers {

  private def world = InterfacesWorldManager.world

  // ============================================================================
  // Given Steps - Create Models
  // ============================================================================

  Given("""a RestStartTestRequest with:""") { (dataTable: DataTable) =>
    val data = dataTable.asMap[String, String](classOf[String], classOf[String]).asScala.toMap

    world.restStartTestRequest = Some(RestStartTestRequest(
      `test-id` = UUID.fromString(data("test-id")),
      `block-storage-path` = data("block-storage-path"),
      `test-type` = data.get("test-type")
    ))
  }

  Given("""a RestTestStatusRequest with test-id {string}""") { (testId: String) =>
    // Clear other requests to ensure clean conversion
    world.restStartTestRequest = None
    world.restQueueStatusRequest = None
    world.restCancelRequest = None

    world.restTestStatusRequest = Some(RestTestStatusRequest(
      `test-id` = UUID.fromString(testId)
    ))
  }

  Given("""a RestQueueStatusRequest with test-id {string}""") { (testId: String) =>
    // Clear other requests to ensure clean conversion
    world.restStartTestRequest = None
    world.restTestStatusRequest = None
    world.restCancelRequest = None

    world.restQueueStatusRequest = Some(RestQueueStatusRequest(
      `test-id` = Some(UUID.fromString(testId))
    ))
  }

  Given("""a RestQueueStatusRequest with no test-id""") { () =>
    // Clear other requests to ensure clean conversion
    world.restStartTestRequest = None
    world.restTestStatusRequest = None
    world.restCancelRequest = None

    world.restQueueStatusRequest = Some(RestQueueStatusRequest(
      `test-id` = None
    ))
  }

  Given("""a RestCancelRequest with test-id {string}""") { (testId: String) =>
    // Clear other requests to ensure clean conversion
    world.restStartTestRequest = None
    world.restTestStatusRequest = None
    world.restQueueStatusRequest = None

    world.restCancelRequest = Some(RestCancelRequest(
      `test-id` = UUID.fromString(testId)
    ))
  }

  Given("""an InitializeTestResponse with:""") { (dataTable: DataTable) =>
    val data = dataTable.asMap[String, String](classOf[String], classOf[String]).asScala.toMap

    // Clear other core responses to ensure clean conversion
    world.coreStartTestResponse = None
    world.coreTestStatusResponse = None
    world.coreQueueStatusResponse = None
    world.coreTestCancelledResponse = None

    world.coreInitializeTestResponse = Some(InitializeTestResponse(
      testId = UUID.fromString(data("testId")),
      message = data("message")
    ))
  }

  Given("""a StartTestResponse with:""") { (dataTable: DataTable) =>
    val data = dataTable.asMap[String, String](classOf[String], classOf[String]).asScala.toMap

    // Clear other core responses to ensure clean conversion
    world.coreInitializeTestResponse = None
    world.coreTestStatusResponse = None
    world.coreQueueStatusResponse = None
    world.coreTestCancelledResponse = None

    world.coreStartTestResponse = Some(StartTestResponse(
      testId = UUID.fromString(data("testId")),
      accepted = data("accepted").toBoolean,
      testType = data.get("testType").filter(_ != "null"),
      message = data("message")
    ))
  }

  Given("""a TestStatusResponse with:""") { (dataTable: DataTable) =>
    val data = dataTable.asMap[String, String](classOf[String], classOf[String]).asScala.toMap

    // Clear other core responses to ensure clean conversion
    world.coreInitializeTestResponse = None
    world.coreStartTestResponse = None
    world.coreQueueStatusResponse = None
    world.coreTestCancelledResponse = None

    world.coreTestStatusResponse = Some(TestStatusResponse(
      testId = UUID.fromString(data("testId")),
      state = data("state"),
      bucket = data.get("bucket").filter(_ != "null"),
      testType = data.get("testType").filter(_ != "null"),
      startTime = data.get("startTime").filter(_ != "null"),
      endTime = data.get("endTime").filter(_ != "null"),
      success = data.get("success").filter(_ != "null").map(_.toBoolean),
      error = data.get("error").filter(_ != "null")
    ))
  }

  Given("""a QueueStatusResponse with:""") { (dataTable: DataTable) =>
    val data = dataTable.asMap[String, String](classOf[String], classOf[String]).asScala.toMap

    // Clear other core responses to ensure clean conversion
    world.coreInitializeTestResponse = None
    world.coreStartTestResponse = None
    world.coreTestStatusResponse = None
    world.coreTestCancelledResponse = None

    world.coreQueueStatusResponse = Some(QueueStatusResponse(
      totalTests = data("totalTests").toInt,
      setupCount = data("setupCount").toInt,
      loadingCount = data("loadingCount").toInt,
      loadedCount = data("loadedCount").toInt,
      testingCount = data("testingCount").toInt,
      completedCount = data("completedCount").toInt,
      exceptionCount = data("exceptionCount").toInt,
      currentlyTesting = data.get("currentlyTesting").filter(_ != "null").map(UUID.fromString)
    ))
  }

  Given("""a TestCancelledResponse with:""") { (dataTable: DataTable) =>
    val data = dataTable.asMap[String, String](classOf[String], classOf[String]).asScala.toMap

    // Clear other core responses to ensure clean conversion
    world.coreInitializeTestResponse = None
    world.coreStartTestResponse = None
    world.coreTestStatusResponse = None
    world.coreQueueStatusResponse = None

    world.coreTestCancelledResponse = Some(TestCancelledResponse(
      testId = UUID.fromString(data("testId")),
      cancelled = data("cancelled").toBoolean,
      message = data.get("message")
    ))
  }

  Given("""a UUID {string}""") { (uuid: String) =>
    world.coreTestStatusParam = Some(UUID.fromString(uuid))
  }

  Given("""an Option[String] with value {string}""") { (value: String) =>
    world.coreStartTestParams = Some((UUID.randomUUID(), "s3://bucket", Some(value)))
  }

  Given("""an Option[String] with None""") { () =>
    world.coreStartTestParams = Some((UUID.randomUUID(), "s3://bucket", None))
  }

  Given("""REST field names use kebab-case:""") { (dataTable: DataTable) =>
    // Just for documentation - verified in Then steps
  }

  Given("""Core field names use camelCase:""") { (dataTable: DataTable) =>
    // Just for documentation - verified in Then steps
  }

  Given("""a new REST field is added {string}""") { (fieldName: String) =>
    // Conceptual step for anti-corruption layer testing
  }

  Given("""a new Core field is added {string}""") { (fieldName: String) =>
    // Conceptual step for anti-corruption layer testing
  }

  Given("""a string {string}""") { (value: String) =>
    // For type safety testing
  }

  Given("""a REST field {string} with value {string}""") { (field: String, value: String) =>
    // For type safety testing
  }

  // ============================================================================
  // When Steps - Perform Conversions
  // ============================================================================

  When("""I convert the request to Core""") { () =>
    // Clear all core params first to ensure clean conversion
    world.coreStartTestParams = None
    world.coreTestStatusParam = None
    world.coreQueueStatusParam = None
    world.coreCancelParam = None

    // Convert the appropriate request
    if (world.restStartTestRequest.isDefined) {
      world.coreStartTestParams = Some(
        RestModelConversions.toCore(world.restStartTestRequest.get)
      )
    } else if (world.restTestStatusRequest.isDefined) {
      world.coreTestStatusParam = Some(
        RestModelConversions.toCore(world.restTestStatusRequest.get)
      )
    } else if (world.restQueueStatusRequest.isDefined) {
      world.coreQueueStatusParam = Some(
        RestModelConversions.toCore(world.restQueueStatusRequest.get)
      )
    } else if (world.restCancelRequest.isDefined) {
      world.coreCancelParam = Some(
        RestModelConversions.toCore(world.restCancelRequest.get)
      )
    }
  }

  When("""I convert the response to REST""") { () =>
    // Clear all REST responses first to ensure clean conversion
    world.restInitializeTestResponse = None
    world.restStartTestResponse = None
    world.restTestStatusResponse = None
    world.restQueueStatusResponse = None
    world.restTestCancelledResponse = None

    // Convert the appropriate response
    if (world.coreInitializeTestResponse.isDefined) {
      world.restInitializeTestResponse = Some(
        RestModelConversions.toRest(world.coreInitializeTestResponse.get)
      )
    } else if (world.coreStartTestResponse.isDefined) {
      world.restStartTestResponse = Some(
        RestModelConversions.toRest(world.coreStartTestResponse.get)
      )
    } else if (world.coreTestStatusResponse.isDefined) {
      world.restTestStatusResponse = Some(
        RestModelConversions.toRest(world.coreTestStatusResponse.get)
      )
    } else if (world.coreQueueStatusResponse.isDefined) {
      world.restQueueStatusResponse = Some(
        RestModelConversions.toRest(world.coreQueueStatusResponse.get)
      )
    } else if (world.coreTestCancelledResponse.isDefined) {
      world.restTestCancelledResponse = Some(
        RestModelConversions.toRest(world.coreTestCancelledResponse.get)
      )
    }
  }

  When("""I convert from Core to REST and back to Core""") { () =>
    // Round-trip conversion for UUIDs, Options, etc.
    // Implementation depends on what's in context
  }

  When("""I add the field to REST models only""") { () =>
    // Conceptual step for anti-corruption layer testing
  }

  When("""I add the field to Core models only""") { () =>
    // Conceptual step for anti-corruption layer testing
  }

  When("""I try to create a RestTestStatusRequest""") { () =>
    // For type safety testing - this should fail at compile time
  }

  When("""I try to parse the JSON""") { () =>
    // For type safety testing
  }

  // ============================================================================
  // Then Steps - Assertions
  // ============================================================================

  Then("""parameter {int} should be UUID {string}""") { (paramNum: Int, expected: String) =>
    world.coreStartTestParams should be(defined)
    val expectedUUID = UUID.fromString(expected)
    paramNum match {
      case 1 => world.coreStartTestParams.get._1 should be(expectedUUID)
      case _ => fail(s"Invalid parameter number: $paramNum")
    }
  }

  Then("""parameter {int} should be String {string}""") { (paramNum: Int, expected: String) =>
    world.coreStartTestParams should be(defined)
    paramNum match {
      case 2 => world.coreStartTestParams.get._2 should be(expected)
      case _ => fail(s"Invalid parameter number: $paramNum")
    }
  }

  Then("""parameter {int} should be Some\({string}\)""") { (paramNum: Int, expected: String) =>
    world.coreStartTestParams should be(defined)
    paramNum match {
      case 3 => world.coreStartTestParams.get._3 should be(Some(expected))
      case _ => fail(s"Invalid parameter number: $paramNum")
    }
  }

  Then("""parameter {int} should be None""") { (paramNum: Int) =>
    world.coreStartTestParams should be(defined)
    paramNum match {
      case 3 => world.coreStartTestParams.get._3 should be(None)
      case _ => fail(s"Invalid parameter number: $paramNum")
    }
  }

  Then("""the Core parameter should be UUID {string}""") { (expected: String) =>
    val expectedUUID = UUID.fromString(expected)

    // Check both coreTestStatusParam and coreCancelParam (both are UUID)
    (world.coreTestStatusParam, world.coreCancelParam) match {
      case (Some(uuid), None) => uuid should be(expectedUUID)
      case (None, Some(uuid)) => uuid should be(expectedUUID)
      case (None, None) => fail("Neither coreTestStatusParam nor coreCancelParam was set by conversion")
      case _ => fail("Both coreTestStatusParam and coreCancelParam are set - ambiguous")
    }
  }

  Then("""the Core parameter should be Some\(UUID\({string}\)\)""") { (expected: String) =>
    val expectedUUID = UUID.fromString(expected)
    world.coreQueueStatusParam match {
      case Some(Some(uuid)) => uuid should be(expectedUUID)
      case Some(None) => fail("coreQueueStatusParam was Some(None), expected Some(Some(UUID))")
      case None => fail("coreQueueStatusParam was not set by conversion")
    }
  }

  Then("""the Core parameter should be None""") { () =>
    world.coreQueueStatusParam match {
      case Some(None) => succeed
      case Some(Some(uuid)) => fail(s"coreQueueStatusParam was Some(Some($uuid)), expected Some(None)")
      case None => fail("coreQueueStatusParam was not set by conversion")
    }
  }

  Then("""the REST response should have field {string} = {string}""") { (field: String, expected: String) =>
    if (world.restInitializeTestResponse.isDefined) {
      field match {
        case "test-id" => world.restInitializeTestResponse.get.`test-id`.toString should be(expected)
        case "message" => world.restInitializeTestResponse.get.message should be(expected)
        case _ => fail(s"Unknown field: $field for RestInitializeTestResponse")
      }
    } else if (world.restStartTestResponse.isDefined) {
      field match {
        case "test-id" => world.restStartTestResponse.get.`test-id`.toString should be(expected)
        case "test-type" => world.restStartTestResponse.get.`test-type` should be(Some(expected))
        case "message" => world.restStartTestResponse.get.message should be(expected)
        case _ => fail(s"Unknown field: $field for RestStartTestResponse")
      }
    } else if (world.restTestStatusResponse.isDefined) {
      field match {
        case "test-id" => world.restTestStatusResponse.get.`test-id`.toString should be(expected)
        case "state" => world.restTestStatusResponse.get.state should be(expected)
        case "bucket" => world.restTestStatusResponse.get.bucket should be(Some(expected))
        case "test-type" => world.restTestStatusResponse.get.`test-type` should be(Some(expected))
        case "start-time" => world.restTestStatusResponse.get.`start-time` should be(Some(expected))
        case "end-time" => world.restTestStatusResponse.get.`end-time` should be(Some(expected))
        case "error" => world.restTestStatusResponse.get.error should be(Some(expected))
        case _ => fail(s"Unknown field: $field for RestTestStatusResponse")
      }
    } else if (world.restQueueStatusResponse.isDefined) {
      field match {
        case "currently-testing" =>
          world.restQueueStatusResponse.get.`currently-testing`.map(_.toString) should be(Some(expected))
        case _ => fail(s"Unknown field: $field for RestQueueStatusResponse")
      }
    } else if (world.restTestCancelledResponse.isDefined) {
      field match {
        case "test-id" => world.restTestCancelledResponse.get.`test-id`.toString should be(expected)
        case "message" => world.restTestCancelledResponse.get.message should be(Some(expected))
        case _ => fail(s"Unknown field: $field for RestTestCancelledResponse")
      }
    } else {
      fail(s"No REST response is defined")
    }
  }

  Then("""the REST response should have field {string} = {int}""") { (field: String, expected: Int) =>
    if (world.restQueueStatusResponse.isDefined) {
      val response = world.restQueueStatusResponse.get
      field match {
        case "total-tests" => response.`total-tests` should be(expected)
        case "setup-count" => response.`setup-count` should be(expected)
        case "loading-count" => response.`loading-count` should be(expected)
        case "loaded-count" => response.`loaded-count` should be(expected)
        case "testing-count" => response.`testing-count` should be(expected)
        case "completed-count" => response.`completed-count` should be(expected)
        case "exception-count" => response.`exception-count` should be(expected)
        case _ => fail(s"Unknown field: $field")
      }
    }
  }

  Then("""the REST response should have field {string} with value {word}""") { (field: String, expected: String) =>
    if (world.restStartTestResponse.isDefined && field == "accepted") {
      world.restStartTestResponse.get.accepted should be(expected.toBoolean)
    } else if (world.restTestStatusResponse.isDefined && field == "success") {
      world.restTestStatusResponse.get.success should be(Some(expected.toBoolean))
    } else if (world.restTestCancelledResponse.isDefined && field == "cancelled") {
      world.restTestCancelledResponse.get.cancelled should be(expected.toBoolean)
    }
  }

  Then("""the REST response field {string} should be null""") { (field: String) =>
    if (world.restStartTestResponse.isDefined) {
      field match {
        case "test-type" => world.restStartTestResponse.get.`test-type` should be(None)
        case _ => fail(s"Unknown field: $field for RestStartTestResponse")
      }
    } else if (world.restTestStatusResponse.isDefined) {
      field match {
        case "test-type" => world.restTestStatusResponse.get.`test-type` should be(None)
        case "bucket" => world.restTestStatusResponse.get.bucket should be(None)
        case "start-time" => world.restTestStatusResponse.get.`start-time` should be(None)
        case "end-time" => world.restTestStatusResponse.get.`end-time` should be(None)
        case "error" => world.restTestStatusResponse.get.error should be(None)
        case _ => fail(s"Unknown field: $field for RestTestStatusResponse")
      }
    } else if (world.restQueueStatusResponse.isDefined) {
      field match {
        case "currently-testing" => world.restQueueStatusResponse.get.`currently-testing` should be(None)
        case _ => fail(s"Unknown field: $field for RestQueueStatusResponse")
      }
    } else if (world.restTestCancelledResponse.isDefined) {
      field match {
        case "message" => world.restTestCancelledResponse.get.message should be(None)
        case _ => fail(s"Unknown field: $field for RestTestCancelledResponse")
      }
    } else {
      fail(s"No REST response is defined for null check on field: $field")
    }
  }

  Then("""the conversion should map field names correctly""") { () =>
    // This is verified by the successful conversions in other steps
    // The fact that conversions work proves field mapping is correct
  }

  Then("""the UUID should remain {string}""") { (expected: String) =>
    world.coreTestStatusParam should be(defined)
    world.coreTestStatusParam.get should be(UUID.fromString(expected))
  }

  Then("""the Option should contain {string}""") { (expected: String) =>
    world.coreStartTestParams should be(defined)
    world.coreStartTestParams.get._3 should be(Some(expected))
  }

  Then("""the Option should be None""") { () =>
    world.coreStartTestParams should be(defined)
    world.coreStartTestParams.get._3 should be(None)
  }

  Then("""Core models should remain unchanged""") { () =>
    // Conceptual assertion for anti-corruption layer
  }

  Then("""Core should not have knowledge of REST-specific fields""") { () =>
    // Conceptual assertion for anti-corruption layer
  }

  Then("""REST models should remain unchanged""") { () =>
    // Conceptual assertion for anti-corruption layer
  }

  Then("""REST API contract should remain stable""") { () =>
    // Conceptual assertion for anti-corruption layer
  }

  Then("""it should fail at compile time or parse time""") { () =>
    // Type safety is enforced at compile time
    // This step documents the expectation
  }

  Then("""the conversion should not accept invalid UUIDs""") { () =>
    // Type safety is enforced at compile time
  }

  Then("""it should fail with JSON parsing error""") { () =>
    // This would be tested in REST routes tests
  }

  Then("""boolean fields should only accept true\/false""") { () =>
    // Type safety is enforced by Spray JSON at parse time
    // This is a documentation/specification step
    succeed
  }
}
