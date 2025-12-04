package io.distia.probe
package core
package config

import com.typesafe.config.{Config, ConfigException}

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
 * Validation severity level
 *
 * - Error: Blocks configuration loading, system cannot start
 * - Warning: Logs warning, system starts with potentially suboptimal config
 */
sealed trait ValidationSeverity

/**
 * Error severity - blocks system startup
 */
case object Error extends ValidationSeverity

/**
 * Warning severity - logs warning but allows startup
 */
case object Warning extends ValidationSeverity

/**
 * Configuration validation issue
 *
 * Represents a single validation problem with diagnostic information
 * and actionable suggestions for resolution.
 *
 * @param severity Error (blocks startup) or Warning (logs only)
 * @param path Configuration path (e.g., "test-probe.core.actor-system.timeout")
 * @param message Human-readable description of the issue
 * @param suggestion Actionable guidance for fixing the issue
 * @param cause Optional underlying exception that triggered the issue
 */
case class ValidationIssue(
  severity: ValidationSeverity,
  path: String,
  message: String,
  suggestion: String,
  cause: Option[Throwable] = None
) {
  def format: String = {
    val severityLabel: String = severity match {
      case Error => "ERROR"
      case Warning => "WARNING"
    }
    val baseMsg: String = s"[$severityLabel] Configuration validation issue at '$path': $message\n  Suggestion: $suggestion"
    cause match {
      case Some(ex) => s"$baseMsg\n  Cause: ${ex.getMessage}"
      case None => baseMsg
    }
  }
}

/**
 * Configuration validation result
 *
 * ADT representing the outcome of configuration validation.
 * Contains errors (blocking) and warnings (non-blocking).
 */
sealed trait ValidationResult {
  def isValid: Boolean
  def errors: List[ValidationIssue]
  def warnings: List[ValidationIssue]

  /**
   * Convert to Either for functional composition
   *
   * @return Right(warnings) if valid, Left(errors) if invalid
   */
  def toEither: Either[List[ValidationIssue], List[ValidationIssue]] = this match {
    case ValidationSuccess(warns) => Right(warns)
    case ValidationFailure(errs, warns) => Left(errs)
  }
}

/**
 * Validation succeeded with optional warnings
 *
 * @param warnings Non-blocking issues that should be reviewed but do not prevent startup
 */
case class ValidationSuccess(warnings: List[ValidationIssue] = List.empty) extends ValidationResult {
  val isValid: Boolean = true
  val errors: List[ValidationIssue] = List.empty
}

/**
 * Validation failed with errors
 *
 * @param errors Blocking issues that prevent system startup
 * @param warnings Non-blocking issues in addition to errors
 */
case class ValidationFailure(
  errors: List[ValidationIssue],
  warnings: List[ValidationIssue] = List.empty
) extends ValidationResult {
  val isValid: Boolean = false
}

/**
 * CoreConfig validator
 *
 * Validates all configuration values from test-probe.core.* namespace.
 * Performs comprehensive validation including:
 * - Required field presence
 * - Type validation (durations, integers, strings)
 * - Range validation (min/max bounds)
 * - Format validation (URLs, package names)
 * - Dependency validation (relative timeout constraints)
 *
 * Validation Strategy:
 * - Errors: Block system startup (missing required fields, invalid types, safety limits)
 * - Warnings: Log suboptimal values (timeouts exceeding recommended limits)
 *
 * @see CoreConfig for configuration schema
 */
private[config] object CoreConfigValidator {

  private val ROOT_PATH: String = "test-probe.core"

  /**
   * Validate entire CoreConfig configuration
   *
   * Validates all sections: actor-system, test-execution, supervision,
   * cucumber, services, kafka, dsl, and cross-section dependencies.
   *
   * @param config Typesafe Config instance to validate
   * @return ValidationSuccess with warnings, or ValidationFailure with errors
   */
  def validate(config: Config): ValidationResult =
    validateRootPath(config) match {
      case Left(err) =>
        ValidationFailure(List(err), Nil)
      case Right(_) =>
        val errors = List.newBuilder[ValidationIssue]
        val warnings = List.newBuilder[ValidationIssue]

        val actorSystemIssues: (List[ValidationIssue], List[ValidationIssue]) = validateActorSystemSection(config)
        errors ++= actorSystemIssues._1
        warnings ++= actorSystemIssues._2

        val testExecutionIssues: (List[ValidationIssue], List[ValidationIssue]) = validateTestExecutionSection(config)
        errors ++= testExecutionIssues._1
        warnings ++= testExecutionIssues._2

        val supervisionIssues: (List[ValidationIssue], List[ValidationIssue]) = validateSupervisionSection(config)
        errors ++= supervisionIssues._1
        warnings ++= supervisionIssues._2

        errors ++= validateCucumberSection(config)
        errors ++= validateServicesSection(config)

        val kafkaIssues: (List[ValidationIssue], List[ValidationIssue]) = validateKafkaSection(config)
        errors ++= kafkaIssues._1
        warnings ++= kafkaIssues._2

        val dslIssues: (List[ValidationIssue], List[ValidationIssue]) = validateDslSection(config)
        errors ++= dslIssues._1
        warnings ++= dslIssues._2

        errors ++= validateDependencies(config)

        val errorsList: List[ValidationIssue] = errors.result
        val warningsList: List[ValidationIssue] = warnings.result

        if errorsList.isEmpty then ValidationSuccess(warningsList)
        else ValidationFailure(errorsList, warningsList)
    }

  /**
   * Validate root configuration path exists
   *
   * @param config Typesafe Config instance
   * @return Right(()) if root path exists, Left(error) otherwise
   */
  def validateRootPath(config: Config): Either[ValidationIssue, Unit] =
    if !config.hasPath(ROOT_PATH) then
      Left(ValidationIssue(
        severity = Error,
        path = ROOT_PATH,
        message = "Root configuration path not found",
        suggestion = s"Ensure reference.conf or application.conf contains '$ROOT_PATH { ... }' section"
      ))
    else
      Right(())

  def validateActorSystemSection(config: Config): (List[ValidationIssue], List[ValidationIssue]) = {
    val errors = List.newBuilder[ValidationIssue]
    val warnings = List.newBuilder[ValidationIssue]

    validateNonEmptyString(
      config = config,
      path = s"$ROOT_PATH.actor-system.name",
      description = "Actor system name"
    ).foreach(errors += _)

    val timeoutIssues: (Option[ValidationIssue], Option[ValidationIssue]) = validateDuration(
      config = config,
      path = s"$ROOT_PATH.actor-system.timeout",
      minMillis = 1000,
      recommendedMaxMillis = Some(300000),
      description = "Actor system timeout"
    )
    timeoutIssues._1.foreach(errors += _)
    timeoutIssues._2.foreach(warnings += _)

    val shutdownIssues: (Option[ValidationIssue], Option[ValidationIssue]) = validateDuration(
      config = config,
      path = s"$ROOT_PATH.actor-system.shutdown-timeout",
      minMillis = 1000,
      recommendedMaxMillis = Some(300000),
      description = "Shutdown timeout"
    )
    shutdownIssues._1.foreach(errors += _)
    shutdownIssues._2.foreach(warnings += _)

    val initIssues: (Option[ValidationIssue], Option[ValidationIssue]) = validateDuration(
      config = config,
      path = s"$ROOT_PATH.actor-system.initialization-timeout",
      minMillis = 1000,
      recommendedMaxMillis = Some(300000),
      description = "Initialization timeout"
    )
    initIssues._1.foreach(errors += _)
    initIssues._2.foreach(warnings += _)

    val poolSizeIssues: (Option[ValidationIssue], Option[ValidationIssue]) = validateInt(
      config = config,
      path = s"$ROOT_PATH.actor-system.pool-size",
      min = 1,
      recommendedMax = Some(50),
      description = "Pool size"
    )
    poolSizeIssues._1.foreach(errors += _)
    poolSizeIssues._2.foreach(warnings += _)

    val maxExecIssues: (Option[ValidationIssue], Option[ValidationIssue]) = validateDuration(
      config = config,
      path = s"$ROOT_PATH.actor-system.max-execution-time",
      minMillis = 10000,
      recommendedMaxMillis = Some(3600000),
      description = "Max execution time"
    )
    maxExecIssues._1.foreach(errors += _)
    maxExecIssues._2.foreach(warnings += _)

    (errors.result, warnings.result)
  }

  def validateTestExecutionSection(config: Config): (List[ValidationIssue], List[ValidationIssue]) = {
    val errors = List.newBuilder[ValidationIssue]
    val warnings = List.newBuilder[ValidationIssue]

    val retriesIssues: (Option[ValidationIssue], Option[ValidationIssue]) = validateInt(
      config = config,
      path = s"$ROOT_PATH.test-execution.max-retries",
      min = 0,
      recommendedMax = Some(10),
      description = "Max retries"
    )
    retriesIssues._1.foreach(errors += _)
    retriesIssues._2.foreach(warnings += _)

    val cleanupIssues: (Option[ValidationIssue], Option[ValidationIssue]) = validateDuration(
      config = config,
      path = s"$ROOT_PATH.test-execution.cleanup-delay",
      minMillis = 0,
      recommendedMaxMillis = Some(600000),
      description = "Cleanup delay"
    )
    cleanupIssues._1.foreach(errors += _)
    cleanupIssues._2.foreach(warnings += _)

    val stashIssues: (Option[ValidationIssue], Option[ValidationIssue]) = validateInt(
      config = config,
      path = s"$ROOT_PATH.test-execution.stash-buffer-size",
      min = 1,
      max = Some(10000),
      recommendedMax = None,
      description = "Stash buffer size"
    )
    stashIssues._1.foreach(errors += _)
    stashIssues._2.foreach(warnings += _)

    (errors.result, warnings.result)
  }

  def validateSupervisionSection(config: Config): (List[ValidationIssue], List[ValidationIssue]) = {
    val errors = List.newBuilder[ValidationIssue]
    val warnings = List.newBuilder[ValidationIssue]

    val restartsIssues: (Option[ValidationIssue], Option[ValidationIssue]) = validateInt(
      config = config,
      path = s"$ROOT_PATH.supervision.max-restarts",
      min = 1,
      recommendedMax = Some(100),
      description = "Max restarts"
    )
    restartsIssues._1.foreach(errors += _)
    restartsIssues._2.foreach(warnings += _)

    val timeRangeIssues: (Option[ValidationIssue], Option[ValidationIssue]) = validateDuration(
      config = config,
      path = s"$ROOT_PATH.supervision.restart-time-range",
      minMillis = 1000,
      recommendedMaxMillis = Some(3600000),
      description = "Restart time range"
    )
    timeRangeIssues._1.foreach(errors += _)
    timeRangeIssues._2.foreach(warnings += _)

    (errors.result, warnings.result)
  }

  def validateCucumberSection(config: Config): List[ValidationIssue] = {
    val errors = List.newBuilder[ValidationIssue]

    validateNonEmptyString(
      config = config,
      path = s"$ROOT_PATH.cucumber.glue-packages",
      description = "Cucumber glue package"
    ).foreach(errors += _)

    errors.result
  }

  def validateServicesSection(config: Config): List[ValidationIssue] = {
    val errors = List.newBuilder[ValidationIssue]

    val timeoutIssue: (Option[ValidationIssue], Option[ValidationIssue]) = validateDuration(
      config = config,
      path = s"$ROOT_PATH.services.timeout",
      minMillis = 1000,
      recommendedMaxMillis = Some(300000),
      description = "Services timeout"
    )
    timeoutIssue._1.foreach(errors += _)

    errors.result
  }

  def validateKafkaSection(config: Config): (List[ValidationIssue], List[ValidationIssue]) = {
    val errors = List.newBuilder[ValidationIssue]
    val warnings = List.newBuilder[ValidationIssue]

    validateNonEmptyString(
      config = config,
      path = s"$ROOT_PATH.kafka.bootstrap-servers",
      description = "Kafka bootstrap servers"
    ).foreach(errors += _)

    validateNonEmptyString(
      config = config,
      path = s"$ROOT_PATH.kafka.schema-registry-url",
      description = "Schema Registry URL"
    ).foreach(errors += _)

    Try(config.getString(s"$ROOT_PATH.kafka.schema-registry-url")).toOption match {
      case Some(url) if url.nonEmpty && !url.startsWith("http://") && !url.startsWith("https://") =>
        errors += ValidationIssue(
          severity = Error,
          path = s"$ROOT_PATH.kafka.schema-registry-url",
          message = s"Invalid Schema Registry URL format: $url",
          suggestion = "URL should start with http:// or https://"
        )
      case _ =>
    }

    validateNonEmptyString(
      config = config,
      path = s"$ROOT_PATH.kafka.oauth.token-endpoint",
      description = "OAuth token endpoint"
    ).foreach(errors += _)

    validateNonEmptyString(
      config = config,
      path = s"$ROOT_PATH.kafka.oauth.client-scope",
      description = "OAuth client scope"
    ).foreach(errors += _)

    (errors.result, warnings.result)
  }

  def validateDslSection(config: Config): (List[ValidationIssue], List[ValidationIssue]) = {
    val errors = List.newBuilder[ValidationIssue]
    val warnings = List.newBuilder[ValidationIssue]

    val askTimeoutIssues: (Option[ValidationIssue], Option[ValidationIssue]) = validateDuration(
      config = config,
      path = s"$ROOT_PATH.dsl.ask-timeout",
      minMillis = 100,
      recommendedMaxMillis = Some(30000),
      description = "DSL ask timeout"
    )
    askTimeoutIssues._1.foreach(errors += _)
    askTimeoutIssues._2.foreach(warnings += _)

    (errors.result, warnings.result)
  }

  def validateDependencies(config: Config): List[ValidationIssue] = {
    val errors = List.newBuilder[ValidationIssue]

    for {
      maxExecTime <- getMillis(config, s"$ROOT_PATH.actor-system.max-execution-time")
      timeout <- getMillis(config, s"$ROOT_PATH.actor-system.timeout")
    } {
      if maxExecTime <= timeout then
        errors += ValidationIssue(
          severity = Error,
          path = s"$ROOT_PATH.actor-system.max-execution-time",
          message = s"Max execution time ($maxExecTime ms) must be greater than actor timeout ($timeout ms)",
          suggestion = "Increase max-execution-time or decrease actor-system.timeout"
        )
    }

    for {
      cleanupDelay <- getMillis(config, s"$ROOT_PATH.test-execution.cleanup-delay")
      maxExecTime <- getMillis(config, s"$ROOT_PATH.actor-system.max-execution-time")
    } {
      if cleanupDelay >= maxExecTime then
        errors += ValidationIssue(
          severity = Error,
          path = s"$ROOT_PATH.test-execution.cleanup-delay",
          message = s"Cleanup delay ($cleanupDelay ms) should be less than max execution time ($maxExecTime ms)",
          suggestion = "Reduce cleanup-delay or increase max-execution-time"
        )
    }

    errors.result
  }

  /**
   * Validate duration configuration value
   *
   * Validates that a duration exists, is parseable, and falls within
   * specified bounds (min/max for errors, recommended max for warnings).
   *
   * @param config Typesafe Config instance
   * @param path Configuration path to validate
   * @param minMillis Minimum allowed duration in milliseconds (error if below)
   * @param maxMillis Optional hard maximum (error if exceeded, for memory safety)
   * @param recommendedMaxMillis Optional soft maximum (warning if exceeded)
   * @param description Human-readable field description for error messages
   * @return (Option[error], Option[warning]) - both None if valid
   */
  def validateDuration(
    config: Config,
    path: String,
    minMillis: Long,
    maxMillis: Option[Long] = None,
    recommendedMaxMillis: Option[Long] = None,
    description: String
  ): (Option[ValidationIssue], Option[ValidationIssue]) = Try(config.getDuration(path).toMillis) match {
    case Success(millis) =>
      val error: Option[ValidationIssue] = if millis < minMillis then
        Some(ValidationIssue(
          severity = Error,
          path = path,
          message = s"$description must be at least ${minMillis}ms, got ${millis}ms",
          suggestion = s"Set $path to a value of at least ${formatDuration(minMillis)}"
        ))
      else maxMillis match {
        case Some(max) if millis > max =>
          Some(ValidationIssue(
            severity = Error,
            path = path,
            message = s"$description must not exceed ${max}ms, got ${millis}ms (memory safety limit)",
            suggestion = s"Set $path to a value of at most ${formatDuration(max)}"
          ))
        case _ => None
      }

      val warning: Option[ValidationIssue] = if error.isEmpty then
        recommendedMaxMillis match {
          case Some(recommended) if millis > recommended =>
            Some(ValidationIssue(
              severity = Warning,
              path = path,
              message = s"$description is ${millis}ms (recommended maximum: ${recommended}ms)",
              suggestion = s"Consider reducing $path to ${formatDuration(recommended)} or less for typical use cases"
            ))
          case _ => None
        }
      else
        None

      (error, warning)

    case Failure(ex: ConfigException.Missing) =>
      (Some(ValidationIssue(
        severity = Error,
        path = path,
        message = s"Required field '$description' is missing",
        suggestion = s"Add '$path = <duration>' to your configuration (e.g., '30 seconds')",
        cause = Some(ex)
      )), None)

    case Failure(ex) =>
      (Some(ValidationIssue(
        severity = Error,
        path = path,
        message = s"Invalid duration format for $description",
        suggestion = "Use format like '30 seconds', '5 minutes', or '100 milliseconds'",
        cause = Some(ex)
      )), None)
  }

  /**
   * Validate integer configuration value
   *
   * Validates that an integer exists, is parseable, and falls within
   * specified bounds (min/max for errors, recommended max for warnings).
   *
   * @param config Typesafe Config instance
   * @param path Configuration path to validate
   * @param min Minimum allowed value (error if below)
   * @param max Optional hard maximum (error if exceeded, for memory safety)
   * @param recommendedMax Optional soft maximum (warning if exceeded)
   * @param description Human-readable field description for error messages
   * @return (Option[error], Option[warning]) - both None if valid
   */
  def validateInt(
    config: Config,
    path: String,
    min: Int,
    max: Option[Int] = None,
    recommendedMax: Option[Int] = None,
    description: String
  ): (Option[ValidationIssue], Option[ValidationIssue]) = Try(config.getInt(path)) match {
    case Success(value) =>
      val error: Option[ValidationIssue] = if value < min then
        Some(ValidationIssue(
          severity = Error,
          path = path,
          message = s"$description must be at least $min, got $value",
          suggestion = s"Set $path to a value of at least $min"
        ))
      else max match {
        case Some(maximum) if value > maximum =>
          Some(ValidationIssue(
            severity = Error,
            path = path,
            message = s"$description must not exceed $maximum, got $value (memory safety limit)",
            suggestion = s"Set $path to a value of at most $maximum"
          ))
        case _ => None
      }

      val warning: Option[ValidationIssue] = if error.isEmpty then
        recommendedMax match {
          case Some(recommended) if value > recommended =>
            Some(ValidationIssue(
              severity = Warning,
              path = path,
              message = s"$description is $value (recommended maximum: $recommended)",
              suggestion = s"Consider reducing $path to $recommended or less for typical use cases"
            ))
          case _ => None
        }
      else
        None

      (error, warning)

    case Failure(ex: ConfigException.Missing) =>
      (Some(ValidationIssue(
        severity = Error,
        path = path,
        message = s"Required field '$description' is missing",
        suggestion = s"Add '$path = <integer>' to your configuration",
        cause = Some(ex)
      )), None)

    case Failure(ex) =>
      (Some(ValidationIssue(
        severity = Error,
        path = path,
        message = s"Invalid integer format for $description",
        suggestion = "Use a valid integer value",
        cause = Some(ex)
      )), None)
  }

  /**
   * Validate non-empty string configuration value
   *
   * Validates that a string exists, is parseable, and is not empty.
   *
   * @param config Typesafe Config instance
   * @param path Configuration path to validate
   * @param description Human-readable field description for error messages
   * @return Some(error) if invalid, None if valid
   */
  def validateNonEmptyString(
    config: Config,
    path: String,
    description: String
  ): Option[ValidationIssue] = Try(config.getString(path)) match {
    case Success(value) if value.isEmpty =>
      Some(ValidationIssue(
        severity = Error,
        path = path,
        message = s"$description cannot be empty",
        suggestion = s"Set $path to a non-empty string value"
      ))
    case Success(_) => None
    case Failure(ex: ConfigException.Missing) =>
      Some(ValidationIssue(
        severity = Error,
        path = path,
        message = s"Required field '$description' is missing",
        suggestion = s"""Add '$path = "<value>"' to your configuration or set environment variable"""
      ))
    case Failure(ex) =>
      Some(ValidationIssue(
        severity = Error,
        path = path,
        message = s"Invalid string format for $description",
        suggestion = "Use a valid string value",
        cause = Some(ex)
      ))
  }

  def getMillis(config: Config, path: String): Option[Long] = Try(config.getDuration(path).toMillis).toOption

  def formatDuration(millis: Long): String =
    if millis < 1000 then s"${millis}ms"
    else if millis < 60000 then s"${millis / 1000}s"
    else s"${millis / 60000}m"
}