package io.distia.probe
package common
package rosetta

import io.circe.Json

import java.util.Base64
import scala.util.{Try, Success, Failure}

/**
 * Transformation functions for Rosetta vault mapping
 *
 * Provides a set of transformation operations that can be applied to JSON values
 * during the mapping process from vault JSON to VaultCredentials.
 *
 * All transformations follow the pattern: Json => Either[String, Json]
 * - Right(json) indicates successful transformation
 * - Left(error) indicates transformation failure with descriptive message
 *
 * Transformations can be chained to build complex mapping pipelines.
 *
 * Security Note: Transformations operate on JSON values, not raw strings.
 * Credential values are never logged or exposed in error messages.
 */
private[probe] object RosettaTransformations {

  /**
   * Transformation ADT representing all available transformation operations
   *
   * Each transformation case class contains the parameters needed for that operation.
   * Transformations are serialized from mapping configuration files (YAML/JSON).
   */
  sealed trait Transform {
    /**
     * Apply this transformation to a JSON value
     *
     * @param json Input JSON value to transform
     * @return Right(transformed JSON) or Left(error message)
     */
    def apply(json: Json): Either[String, Json]
  }

  /**
   * Base64 decode transformation
   *
   * Decodes a base64-encoded string value. Commonly used for secrets stored
   * in base64 format in vault systems.
   *
   * Input: Json.fromString("SGVsbG8gV29ybGQ=")
   * Output: Right(Json.fromString("Hello World"))
   *
   * Error cases:
   * - Input is not a string: "Expected string value for base64Decode"
   * - Invalid base64 format: "Invalid base64 encoding: {error}"
   */
  case object Base64Decode extends Transform {
    def apply(json: Json): Either[String, Json] =
      json.asString match {
        case Some(str) =>
          Try(Base64.getDecoder.decode(str)) match {
            case Success(bytes) =>
              Right(Json.fromString(new String(bytes, "UTF-8")))
            case Failure(ex) =>
              Left(s"Invalid base64 encoding: ${ex.getMessage}")
          }
        case None =>
          Left("Expected string value for base64Decode transformation")
      }
  }

  /**
   * Base64 encode transformation
   *
   * Encodes a string value to base64 format.
   *
   * Input: Json.fromString("Hello World")
   * Output: Right(Json.fromString("SGVsbG8gV29ybGQ="))
   *
   * Error case:
   * - Input is not a string: "Expected string value for base64Encode"
   */
  case object Base64Encode extends Transform {
    def apply(json: Json): Either[String, Json] =
      json.asString match {
        case Some(str) =>
          val encoded = Base64.getEncoder.encodeToString(str.getBytes("UTF-8"))
          Right(Json.fromString(encoded))
        case None =>
          Left("Expected string value for base64Encode transformation")
      }
  }

  /**
   * Convert string to uppercase
   *
   * Input: Json.fromString("producer")
   * Output: Right(Json.fromString("PRODUCER"))
   *
   * Error case:
   * - Input is not a string: "Expected string value for toUpper"
   */
  case object ToUpper extends Transform {
    def apply(json: Json): Either[String, Json] =
      json.asString match {
        case Some(str) =>
          Right(Json.fromString(str.toUpperCase))
        case None =>
          Left("Expected string value for toUpper transformation")
      }
  }

  /**
   * Convert string to lowercase
   *
   * Input: Json.fromString("PRODUCER")
   * Output: Right(Json.fromString("producer"))
   *
   * Error case:
   * - Input is not a string: "Expected string value for toLower"
   */
  case object ToLower extends Transform {
    def apply(json: Json): Either[String, Json] =
      json.asString match {
        case Some(str) =>
          Right(Json.fromString(str.toLowerCase))
        case None =>
          Left("Expected string value for toLower transformation")
      }
  }

  /**
   * Add prefix to string value
   *
   * Input: Json.fromString("123"), Prefix("kafka-client-")
   * Output: Right(Json.fromString("kafka-client-123"))
   *
   * Error case:
   * - Input is not a string: "Expected string value for prefix"
   */
  case class Prefix(value: String) extends Transform {
    def apply(json: Json): Either[String, Json] =
      json.asString match {
        case Some(str) =>
          Right(Json.fromString(s"$value$str"))
        case None =>
          Left("Expected string value for prefix transformation")
      }
  }

  /**
   * Add suffix to string value
   *
   * Input: Json.fromString("client"), Suffix("-prod")
   * Output: Right(Json.fromString("client-prod"))
   *
   * Error case:
   * - Input is not a string: "Expected string value for suffix"
   */
  case class Suffix(value: String) extends Transform {
    def apply(json: Json): Either[String, Json] =
      json.asString match {
        case Some(str) =>
          Right(Json.fromString(s"$str$value"))
        case None =>
          Left("Expected string value for suffix transformation")
      }
  }

  /**
   * Concatenate multiple string values
   *
   * This transformation is different from others - it operates on multiple JSON values,
   * not just the current value. It's applied by RosettaMapper when processing
   * concat transformation in the configuration.
   *
   * Input: List(Json.fromString("Hello"), Json.fromString(" "), Json.fromString("World")), separator = ""
   * Output: Right(Json.fromString("Hello World"))
   *
   * Error case:
   * - Any input is not a string: "Expected all values to be strings for concat"
   * - Empty list: "Concat requires at least one value"
   */
  case class Concat(separator: String = "") extends Transform {
    /**
     * Concatenate multiple JSON string values
     *
     * @param values List of JSON values to concatenate
     * @return Right(concatenated string as JSON) or Left(error)
     */
    def applyToList(values: List[Json]): Either[String, Json] =
      if (values.isEmpty) {
        Left("Concat transformation requires at least one value")
      } else {
        val strings = values.map(_.asString)
        if (strings.forall(_.isDefined)) {
          Right(Json.fromString(strings.flatten.mkString(separator)))
        } else {
          Left("Expected all values to be strings for concat transformation")
        }
      }

    // Single value concat just returns the value (for API consistency)
    def apply(json: Json): Either[String, Json] =
      applyToList(List(json))
  }

  /**
   * Provide default value if field is missing or null
   *
   * This transformation is typically applied before other transformations
   * to handle optional fields in vault JSON.
   *
   * Input: Json.Null, Default("default-value")
   * Output: Right(Json.fromString("default-value"))
   *
   * Input: Json.fromString("actual-value"), Default("default-value")
   * Output: Right(Json.fromString("actual-value"))
   */
  case class Default(value: String) extends Transform {
    def apply(json: Json): Either[String, Json] =
      if (json.isNull) {
        Right(Json.fromString(value))
      } else {
        Right(json)
      }
  }

  /**
   * Chain multiple transformations together
   *
   * Transformations are applied left-to-right. If any transformation fails,
   * the chain stops and returns the error.
   *
   * Example:
   * ```scala
   * val transforms = List(Base64Decode, ToUpper)
   * chain(Json.fromString("aGVsbG8="), transforms)
   * // Result: Right(Json.fromString("HELLO"))
   * ```
   *
   * @param json Initial JSON value
   * @param transforms List of transformations to apply in order
   * @return Right(final transformed value) or Left(first error encountered)
   */
  def chain(json: Json, transforms: List[Transform]): Either[String, Json] =
    transforms.foldLeft[Either[String, Json]](Right(json)) {
      case (Right(currentJson), transform) =>
        transform.apply(currentJson)
      case (left @ Left(_), _) =>
        left
    }
}
