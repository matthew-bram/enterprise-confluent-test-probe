package io.distia.probe
package core
package glue
package steps

import io.distia.probe.common.models.TopicDirective
import io.distia.probe.common.validation.TopicDirectiveValidator
import io.distia.probe.core.fixtures.TopicDirectiveFixtures
import io.distia.probe.core.glue.world.ActorWorld
import io.cucumber.scala.{EN, ScalaDsl}
import org.scalatest.matchers.should.Matchers

/**
 * Step definitions for TopicDirectiveValidator component tests.
 *
 * Responsibilities:
 * - Setting up TopicDirective lists (with/without duplicates)
 * - Calling validation methods (uniqueness, bootstrap server format)
 * - Verifying validation results (Either[List[String], Unit])
 * - Bootstrap server format validation
 *
 * Fixtures Used:
 * - TopicDirectiveFixtures (TopicDirective factories)
 * - ActorWorld (state management)
 *
 * Feature File: component/validation/topic-directive-validator.feature
 *
 * Architecture Notes:
 * - Pure validation logic (no actors or Kafka required)
 * - Uses Either[List[String], Unit] for validation results
 * - No Thread.sleep needed (synchronous validation)
 *
 * Thread Safety: Cucumber runs scenarios sequentially, not thread-safe.
 */
private[core] class TopicDirectiveValidatorSteps(world: ActorWorld)
  extends ScalaDsl
  with EN
  with Matchers
  with TopicDirectiveFixtures:

  // ==========================================================================
  // State Management
  // ==========================================================================

  private var topicDirectives: List[TopicDirective] = List.empty
  private var bootstrapServersInput: Option[String] = None
  private var uniquenessResult: Option[Either[List[String], Unit]] = None
  private var bootstrapFormatResult: Option[Either[String, Unit]] = None

  // ==========================================================================
  // GIVEN STEPS (Setup)
  // ==========================================================================

  Given("""the TopicDirectiveValidator is available""") { () =>
    topicDirectives = List.empty
    bootstrapServersInput = None
    uniquenessResult = None
    bootstrapFormatResult = None
  }

  Given("""a list of TopicDirectives with topics {string}""") { (topicsStr: String) =>
    val topics: List[String] = topicsStr.split(",").map(_.trim).toList
    topicDirectives = topics.map { topic =>
      createProducerDirective(topic = topic)
    }
  }

  Given("""an empty list of TopicDirectives""") { () =>
    topicDirectives = List.empty
  }

  Given("""a bootstrap servers string {string}""") { (servers: String) =>
    bootstrapServersInput = Some(servers)
  }

  Given("""no bootstrap servers specified""") { () =>
    bootstrapServersInput = None
  }

  // ==========================================================================
  // WHEN STEPS (Actions)
  // ==========================================================================

  When("""the validator checks uniqueness""") { () =>
    uniquenessResult = Some(TopicDirectiveValidator.validateUniqueness(topicDirectives))
  }

  When("""the validator checks bootstrap server format""") { () =>
    bootstrapFormatResult = Some(TopicDirectiveValidator.validateBootstrapServersFormat(bootstrapServersInput))
  }

  // ==========================================================================
  // THEN STEPS (Verification)
  // ==========================================================================

  Then("""the validation should succeed""") { () =>
    (uniquenessResult, bootstrapFormatResult) match {
      case (Some(Right(())), _) =>
      case (_, Some(Right(()))) =>
      case (Some(Left(errors)), _) =>
        fail(s"Uniqueness validation failed unexpectedly: ${errors.mkString(", ")}")
      case (_, Some(Left(error))) =>
        fail(s"Bootstrap format validation failed unexpectedly: $error")
      case (None, None) =>
        fail("No validation was performed - call a When step first")
    }
  }

  Then("""the validation should fail""") { () =>
    (uniquenessResult, bootstrapFormatResult) match {
      case (Some(Left(_)), _) =>
      case (_, Some(Left(_))) =>
      case (Some(Right(())), _) =>
        fail("Uniqueness validation succeeded unexpectedly - expected failure")
      case (_, Some(Right(()))) =>
        fail("Bootstrap format validation succeeded unexpectedly - expected failure")
      case (None, None) =>
        fail("No validation was performed - call a When step first")
    }
  }

  Then("""the result should be Right\(\(\)\)""") { () =>
    (uniquenessResult, bootstrapFormatResult) match {
      case (Some(Right(())), _) =>
      case (_, Some(Right(()))) =>
      case (Some(Left(errors)), _) =>
        fail(s"Expected Right(()), got Left(${errors.mkString(", ")})")
      case (_, Some(Left(error))) =>
        fail(s"Expected Right(()), got Left($error)")
      case (None, None) =>
        fail("No validation result available")
    }
  }

  Then("""the error list should contain {int} error\(s\)""") { (expectedCount: Int) =>
    uniquenessResult match {
      case Some(Left(errors)) =>
        errors.size shouldBe expectedCount
      case Some(Right(())) =>
        fail("Expected validation errors, but validation succeeded")
      case None =>
        fail("No uniqueness validation result available")
    }
  }

  Then("""the validation error message should contain {string}""") { (expectedText: String) =>
    (uniquenessResult, bootstrapFormatResult) match {
      case (Some(Left(errors)), _) =>
        val allErrors: String = errors.mkString(" | ")
        withClue(s"Expected error containing '$expectedText' in: $allErrors") {
          allErrors should include(expectedText)
        }
      case (_, Some(Left(error))) =>
        withClue(s"Expected error containing '$expectedText' in: $error") {
          error should include(expectedText)
        }
      case (Some(Right(())), _) =>
        fail(s"Expected error containing '$expectedText', but uniqueness validation succeeded")
      case (_, Some(Right(()))) =>
        fail(s"Expected error containing '$expectedText', but bootstrap format validation succeeded")
      case (None, None) =>
        fail("No validation result available")
    }
  }
