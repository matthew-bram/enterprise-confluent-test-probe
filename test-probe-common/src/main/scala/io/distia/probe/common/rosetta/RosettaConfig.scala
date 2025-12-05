package io.distia
package probe
package common
package rosetta

import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import io.circe.parser.parse
import io.circe.yaml.parser as yamlParser

import java.io.InputStream
import scala.io.Source
import scala.util.{Try, Using}
import java.nio.file.{Files, Path, Paths}

/**
 * Rosetta mapping configuration models and parsers
 *
 * Provides configuration model for defining how vault JSON fields map to
 * target model fields (e.g., VaultCredentials).
 *
 * Supports both YAML and JSON configuration files with automatic format detection.
 *
 * Example YAML configuration:
 * ```yaml
 * mappings:
 *   - targetField: topic
 *     sourcePath: $.kafka.topics[0].name
 *
 *   - targetField: clientSecret
 *     sourcePath: $.oauth.secret
 *     transformations:
 *       - type: base64Decode
 *
 *   - targetField: role
 *     sourcePath: $.kafka.role
 *     defaultValue: "PRODUCER"
 *     transformations:
 *       - type: toUpper
 * ```
 */
private[probe] object RosettaConfig {

  /**
   * Configuration for mapping a single field
   *
   * @param targetField Name of the target field in the destination model (e.g., "topic", "clientId")
   * @param sourcePath JSONPath expression to extract value from vault JSON (e.g., "$.kafka.topics[0].name")
   * @param transformations Optional list of transformations to apply to extracted value
   * @param defaultValue Optional default value if source path is not found
   */
  case class RosettaFieldMapping(
    targetField: String,
    sourcePath: String,
    transformations: List[TransformationConfig] = Nil,
    defaultValue: Option[String] = None
  )

  /**
   * Transformation configuration from YAML/JSON
   *
   * @param transformType Type of transformation (e.g., "base64Decode", "toUpper", "prefix")
   * @param value Optional value parameter for transformations that need it (e.g., prefix value)
   * @param separator Optional separator for concat transformation
   */
  case class TransformationConfig(
    transformType: String,
    value: Option[String] = None,
    separator: Option[String] = None
  ) {
    /**
     * Convert configuration to actual Transform instance
     *
     * @return Right(Transform) or Left(error message)
     */
    def toTransform: Either[String, RosettaTransformations.Transform] =
      transformType.toLowerCase match {
        case "base64decode" => Right(RosettaTransformations.Base64Decode)
        case "base64encode" => Right(RosettaTransformations.Base64Encode)
        case "toupper" => Right(RosettaTransformations.ToUpper)
        case "tolower" => Right(RosettaTransformations.ToLower)
        case "prefix" =>
          value match {
            case Some(v) => Right(RosettaTransformations.Prefix(v))
            case None => Left(s"Transformation 'prefix' requires 'value' parameter")
          }
        case "suffix" =>
          value match {
            case Some(v) => Right(RosettaTransformations.Suffix(v))
            case None => Left(s"Transformation 'suffix' requires 'value' parameter")
          }
        case "concat" =>
          Right(RosettaTransformations.Concat(separator.getOrElse("")))
        case "default" =>
          value match {
            case Some(v) => Right(RosettaTransformations.Default(v))
            case None => Left(s"Transformation 'default' requires 'value' parameter")
          }
        case unknown =>
          Left(s"Unknown transformation type: $unknown")
      }
  }

  /**
   * Complete Rosetta mapping configuration
   *
   * @param mappings List of field mappings
   * @param requestTemplate Optional template for building request body to vault
   */
  case class RosettaConfig(
    mappings: List[RosettaFieldMapping],
    requestTemplate: Option[Json] = None
  ) {
    /**
     * Get mapping for a specific target field
     *
     * @param fieldName Target field name
     * @return Some(mapping) or None if not found
     */
    def getMapping(fieldName: String): Option[RosettaFieldMapping] =
      mappings.find(_.targetField == fieldName)

    /**
     * Validate that all required fields have mappings
     *
     * @param requiredFields List of required field names
     * @return Right(()) if all fields present, Left(missing fields) otherwise
     */
    def validateRequiredFields(requiredFields: List[String]): Either[List[String], Unit] = {
      val mappedFields = mappings.map(_.targetField).toSet
      val missing = requiredFields.filterNot(mappedFields.contains)
      if (missing.isEmpty) Right(()) else Left(missing)
    }
  }

  // Circe decoders
  given Decoder[TransformationConfig] = (c: HCursor) => {
    for {
      transformType <- c.downField("type").as[String]
      value <- c.downField("value").as[Option[String]]
      separator <- c.downField("separator").as[Option[String]]
    } yield TransformationConfig(transformType, value, separator)
  }

  given Decoder[RosettaFieldMapping] = (c: HCursor) => {
    for {
      targetField <- c.downField("targetField").as[String]
      sourcePath <- c.downField("sourcePath").as[String]
      transformations <- c.downField("transformations").as[Option[List[TransformationConfig]]]
      defaultValue <- c.downField("defaultValue").as[Option[String]]
    } yield RosettaFieldMapping(
      targetField,
      sourcePath,
      transformations.getOrElse(Nil),
      defaultValue
    )
  }

  given Decoder[RosettaConfig] = (c: HCursor) => {
    for {
      mappings <- c.downField("mappings").as[List[RosettaFieldMapping]]
      requestTemplate <- c.downField("request-template").as[Option[Json]]
    } yield RosettaConfig(mappings, requestTemplate)
  }

  /**
   * Load Rosetta configuration from a file path
   *
   * Supports:
   * - Classpath resources: "classpath:rosetta/mapping.yaml"
   * - Filesystem paths: "/absolute/path/to/mapping.yaml" or "relative/path/to/mapping.yaml"
   * - Auto-detection of YAML vs JSON by file extension
   *
   * @param path File path (classpath or filesystem)
   * @return Right(RosettaConfig) or Left(Exception)
   */
  def load(path: String): Either[Throwable, RosettaConfig] = {
    // Load file content
    loadFileContent(path).flatMap { content => // Determine format from file extension
      val isYaml = path.endsWith(".yaml") || path.endsWith(".yml")
      if isYaml then parseYaml(content) else parseJson(content)
    }
  }

  def loadFileContent(path: String): Either[Throwable, String] = {
    if path.startsWith("classpath:") then
      val resourcePath: String = path.stripPrefix("classpath:")
      Try {
        Option(getClass.getClassLoader.getResourceAsStream(resourcePath)) match {
          case Some(stream) => Using.resource(Source.fromInputStream(stream))(_.mkString)
          case None => throw new IllegalArgumentException(s"Classpath resource not found: $resourcePath")
        }
      }.toEither.left.map(ex => new IllegalStateException(s"Failed to load classpath resource '$resourcePath': ${ex.getMessage}", ex))
    else
      Try {
        val filePath: Path = Paths.get(path)
        if !Files.exists(filePath) then
          throw new IllegalArgumentException(s"File not found: $path")
        Files.readString(filePath)
      }.toEither.left.map(ex => new IllegalStateException(s"Failed to load file '$path': ${ex.getMessage}", ex))
  }

  /**
   * Parse YAML content into RosettaConfig
   *
   * @param yaml YAML string content
   * @return Right(RosettaConfig) or Left(error)
   */
  private def parseYaml(yaml: String): Either[Throwable, RosettaConfig] = {
    yamlParser.parse(yaml)
      .flatMap(_.as[RosettaConfig])
      .left.map(ex => new IllegalArgumentException(s"Failed to parse YAML configuration: ${ex.getMessage}", ex))
  }

  
  private def parseJson(json: String): Either[Throwable, RosettaConfig] = {
    parse(json)
      .flatMap(_.as[RosettaConfig])
      .left.map(ex => new IllegalArgumentException(s"Failed to parse JSON configuration: ${ex.getMessage}", ex))
  }
  
  def fromJson(json: Json): Either[Throwable, RosettaConfig] = {
    json.as[RosettaConfig]
      .left.map(ex => new IllegalArgumentException(s"Failed to decode RosettaConfig from JSON: ${ex.getMessage}", ex))
  }
}
