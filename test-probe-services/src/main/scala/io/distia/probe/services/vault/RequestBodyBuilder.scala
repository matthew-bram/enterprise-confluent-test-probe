package io.distia.probe
package services
package vault

import io.distia.probe.common.models.{TopicDirective, DirectiveFieldRef}
import io.distia.probe.common.rosetta.RosettaConfig
import io.distia.probe.common.exceptions.VaultMappingException
import com.typesafe.config.Config
import io.circe.Json

import scala.util.{Try, Success, Failure}
import scala.util.matching.Regex

/**
 * RequestBodyBuilder - Builds HTTP request bodies for vault invocations
 *
 * This object performs template substitution on Rosetta JSON templates by replacing
 * placeholders with values from three sources:
 * 1. Application config paths: {{$^request-params.some.path}}
 * 2. TopicDirective metadata: {{'metadataKey'}}
 * 3. TopicDirective fields: {{topic}}, {{payload}}, etc.
 *
 * Template Substitution Rules:
 * - Config paths must start with "request-params." namespace for security
 * - Metadata keys reference the TopicDirective.metadata Map
 * - Directive fields use DirectiveFieldRef enumeration for extraction
 * - All substitutions maintain JSON structure (string values only)
 *
 * Error Handling:
 * - Missing config paths → VaultMappingException
 * - Missing metadata keys → VaultMappingException with available keys
 * - Invalid directive fields → VaultMappingException
 * - Multiple errors → Combined error message
 *
 * Example Template:
 * {{{
 *   {
 *     "configValue": "{{$^request-params.api.key}}",
 *     "metadata": "{{'region'}}",
 *     "topic": "{{topic}}"
 *   }
 * }}}
 *
 * Testing:
 * - Mock appConfig to avoid external dependencies
 * - Use sample TopicDirective with known metadata
 */
private[services] object RequestBodyBuilder {

  private val ConfigPathPattern: Regex = """\{\{\$\^([^}]+)\}\}""".r
  private val MetadataKeyPattern: Regex = """\{\{'([^']+)'\}\}""".r
  private val DirectiveFieldPattern: Regex = """\{\{([a-zA-Z]+)\}\}""".r

  private val RequiredConfigPrefix: String = "request-params."
  private val SafeConfigPathRegex: Regex = """^request-params\.[a-zA-Z0-9._-]+$""".r

  /**
   * Build a JSON request body from a Rosetta template
   *
   * @param topicDirective Source of metadata and directive field values
   * @param rosettaConfig Contains the request template
   * @param appConfig Application config for {{$^...}} substitutions
   * @return JSON string with all variables substituted, or error
   * @throws VaultMappingException if template is missing or substitution fails
   */
  def build(
    topicDirective: TopicDirective,
    rosettaConfig: RosettaConfig.RosettaConfig,
    appConfig: Config
  ): Either[VaultMappingException, String] = rosettaConfig.requestTemplate match {
      case None => Left(new VaultMappingException(s"Request template is required in Rosetta config for topic ${topicDirective.topic}"))
      case Some(template) => substituteVariables(template, topicDirective, appConfig).map(_.noSpaces)
    }

  /**
   * Recursively substitute variables in a JSON structure
   *
   * Traverses JSON and applies substitution to all string values while preserving
   * the overall JSON structure (objects, arrays, primitives).
   *
   * @param json JSON structure to process
   * @param topicDirective Source of metadata and directive values
   * @param appConfig Application config for path lookups
   * @return Transformed JSON with all substitutions applied, or first error
   */
  def substituteVariables(
    json: Json,
    topicDirective: TopicDirective,
    appConfig: Config
  ): Either[VaultMappingException, Json] = json
    .fold(
      jsonNull = Right(Json.Null),
      jsonBoolean = bool => Right(Json.fromBoolean(bool)),
      jsonNumber = num => Right(Json.fromJsonNumber(num)),
      jsonString = str => substituteStringValue(str, topicDirective, appConfig).map(Json.fromString),
      jsonArray = arr => {
        val results = arr.map(elem => substituteVariables(elem, topicDirective, appConfig))
        val errors = results.collect { case Left(err) => err }
        if errors.nonEmpty then Left(combineErrors(errors, topicDirective.topic))
        else Right(Json.fromValues(results.collect { case Right(v) => v }))
      },
      jsonObject = obj => {
        val results = obj.toMap.map { case (key, value) => substituteVariables(value, topicDirective, appConfig).map(newValue => (key, newValue)) }
        val errors = results.collect { case Left(err) => err }
        if errors.nonEmpty then Left(combineErrors(errors, topicDirective.topic))
        else Right(Json.fromFields(results.collect { case Right(kv) => kv }))
      }
    )

  def substituteStringValue(
    value: String,
    topicDirective: TopicDirective,
    appConfig: Config
  ): Either[VaultMappingException, String] = value match {
    case ConfigPathPattern(configPath) => resolveConfigPath(configPath, appConfig, topicDirective.topic)
    case MetadataKeyPattern(metadataKey) => resolveMetadataKey(metadataKey, topicDirective)
    case DirectiveFieldPattern(fieldName) => resolveDirectiveField(fieldName, topicDirective)
    case _ => Right(value)
  }

  def validateConfigPath(configPath: String): Either[VaultMappingException, Unit] = {
    if !configPath.startsWith(RequiredConfigPrefix) then
      return Left(new VaultMappingException(
        s"""Invalid config path: '$configPath'.
           | Config paths must start with 'request-params.' namespace.""".stripMargin
      ))

    if configPath == RequiredConfigPrefix then
      return Left(new VaultMappingException(
        s"""Invalid config path: '$configPath'.
           | Config paths must have at least one sub-path after 'request-params.'
           | Format: request-params.[subpath]""".stripMargin
      ))

    configPath match {
      case SafeConfigPathRegex() => Right(())
      case _ => Left(new VaultMappingException(
        s"""Invalid config path: '$configPath'.
           | Config paths must use alphanumeric characters, dots, underscores, and hyphens only.""".stripMargin
      ))
    }
  }

  def combineErrors(
    errors: Iterable[VaultMappingException],
    topic: String
  ): VaultMappingException = new VaultMappingException(
    s"Multiple template errors for topic $topic: ${errors.map(_.getMessage).mkString("; ")}"
  )

  def resolveConfigPath(
    configPath: String,
    appConfig: Config,
    topic: String
  ): Either[VaultMappingException, String] = validateConfigPath(configPath).flatMap { _ =>
    Try {
      if !appConfig.hasPath(configPath) then throw new VaultMappingException(s"Config path not found: $configPath for topic $topic.")
      appConfig.getString(configPath)
    } match {
      case Success(string) => Right(string)
      case Failure(ex) => ex match {
        case ex: VaultMappingException => Left(ex)
        case ex: Exception => Left(new VaultMappingException(s"Failed to read config path '$configPath' for topic $topic: ${ex.getMessage}", ex))
      }
    }
  }

  def resolveMetadataKey(
    metadataKey: String,
    topicDirective: TopicDirective
  ): Either[VaultMappingException, String] = topicDirective.metadata.get(metadataKey) match {
    case Some(value) => Right(value)
    case None => Left(new VaultMappingException(
      s"""Metadata key '$metadataKey' not found in TopicDirective for topic ${topicDirective.topic}.
         | Available keys: ${topicDirective.metadata.keys.mkString(", ")}""".stripMargin ))
  }

  def resolveDirectiveField(
    fieldName: String,
    topicDirective: TopicDirective
  ): Either[VaultMappingException, String] =
    DirectiveFieldRef.fromString(fieldName).map(_.extract(topicDirective))

  def validate(
    template: Json,
    topicDirective: TopicDirective,
    appConfig: Config
  ): Either[VaultMappingException, Unit] = substituteVariables(template, topicDirective, appConfig).map(_ => ())

}
