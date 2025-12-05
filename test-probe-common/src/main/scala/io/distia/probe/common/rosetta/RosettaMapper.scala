package io.distia.probe
package common
package rosetta

import io.circe.{ACursor, HCursor, Json}

/**
 * Core JSONPath resolver and transformation engine for Rosetta mapping
 *
 * Provides JSONPath-like query capabilities to extract values from nested JSON structures.
 * Supports transformation pipelines to modify extracted values.
 *
 * Supported JSONPath syntax:
 * - `$.field` - Direct field access
 * - `$.field.nested` - Nested field access (unlimited depth)
 * - `$.array[0]` - Array index access
 * - `$.array[*]` - Array wildcard (all elements)
 * - `$.array.length()` - Array length operation
 * - `$.field?` - Optional field (returns None if missing)
 *
 * Design principles:
 * - Pure functional design (no side effects)
 * - Error accumulation for better diagnostics
 * - Security: Never log JSON values (may contain credentials)
 *
 * Thread safety: Stateless object, safe for concurrent use
 */
private[probe] object RosettaMapper {

  /**
   * Resolve a JSONPath expression to extract a value from JSON
   *
   * Examples:
   * ```scala
   * // Simple field access
   * resolvePath(json, "$.user.name")  // Some(Json.fromString("John"))
   *
   * // Array index
   * resolvePath(json, "$.users[0].email")  // Some(Json.fromString("user@example.com"))
   *
   * // Array wildcard
   * resolvePath(json, "$.users[*].name")  // Some(Json.arr(names...))
   *
   * // Array length
   * resolvePath(json, "$.users.length()")  // Some(Json.fromInt(5))
   *
   * // Missing path
   * resolvePath(json, "$.nonexistent")  // None
   * ```
   *
   * @param json Root JSON object to query
   * @param path JSONPath expression (must start with $.)
   * @return Some(extracted JSON value) or None if path not found
   */
  def resolvePath(json: Json, path: String): Option[Json] = {
    // Remove leading "$." if present
    val cleanPath = if (path.startsWith("$.")) path.drop(2) else path

    // Handle empty path (return root)
    if (cleanPath.isEmpty) return Some(json)

    // Split path into segments
    val segments = parsePath(cleanPath)

    // Walk the path from root
    walk(json.hcursor, segments)
  }

  /**
   * Parse a JSONPath expression into segments
   *
   * Handles:
   * - Field access: "field" -> FieldSegment("field")
   * - Array index: "array[0]" -> FieldSegment("array"), IndexSegment(0)
   * - Array wildcard: "array[*]" -> FieldSegment("array"), WildcardSegment
   * - Array operation: "array.length()" -> FieldSegment("array"), LengthSegment
   * - Optional: "field?" -> OptionalFieldSegment("field")
   *
   * @param path Clean path without "$." prefix
   * @return List of path segments
   */
  private def parsePath(path: String): List[PathSegment] = {
    // Split on dots, but preserve array notation
    val parts = path.split("\\.").toList

    parts.flatMap { part =>
      // Check for array access patterns
      if (part.endsWith("()") && part.stripSuffix("()") == "length") {
        // Special case: length() operation
        List(LengthSegment)
      } else if (part.contains("[")) {
        // Array access: field[index] or field[*]
        val Array(fieldName, arrayPart) = part.split("\\[", 2)
        val arrayContent = arrayPart.stripSuffix("]")

        val fieldSegment = if (fieldName.endsWith("?")) {
          OptionalFieldSegment(fieldName.stripSuffix("?"))
        } else if (fieldName.nonEmpty) {
          FieldSegment(fieldName)
        } else {
          // No field, just array access on current object
          null
        }

        val arraySegment = arrayContent match {
          case "*" => WildcardSegment
          case idx => IndexSegment(idx.toInt)
        }

        List(fieldSegment, arraySegment).filter(_ != null)
      } else if (part.endsWith("?")) {
        // Optional field
        List(OptionalFieldSegment(part.stripSuffix("?")))
      } else {
        // Simple field access
        List(FieldSegment(part))
      }
    }
  }

  /**
   * Walk through JSON structure following path segments
   *
   * @param cursor Current position in JSON
   * @param segments Remaining path segments to traverse
   * @return Some(value at path) or None if path not found
   */
  private def walk(cursor: ACursor, segments: List[PathSegment]): Option[Json] =
    segments match {
      case Nil =>
        // No more segments, return current position
        cursor.focus

      case segment :: tail =>
        segment match {
          case FieldSegment(name) =>
            // Navigate to field and continue
            walk(cursor.downField(name), tail)

          case OptionalFieldSegment(name) =>
            // Navigate to field, but don't fail if missing
            val nextCursor = cursor.downField(name)
            if (nextCursor.succeeded) {
              walk(nextCursor, tail)
            } else {
              None
            }

          case IndexSegment(idx) =>
            // Navigate to array element
            cursor.focus.flatMap(_.asArray).flatMap { arr =>
              if (idx >= 0 && idx < arr.length) {
                walk(arr(idx).hcursor, tail)
              } else {
                None
              }
            }

          case WildcardSegment =>
            // Collect all array elements, apply remaining path to each
            cursor.focus.flatMap(_.asArray).map { arr =>
              val results = arr.flatMap(elem => walk(elem.hcursor, tail))
              Json.fromValues(results)
            }

          case LengthSegment =>
            // Return array length
            cursor.focus.flatMap(_.asArray).map { arr =>
              Json.fromInt(arr.length)
            }
        }
    }

  /**
   * Extract a value from JSON using a path and optional transformations
   *
   * This is the main entry point for field extraction with transformation support.
   *
   * Special handling for Default transformation:
   * - If path is not found AND first transformation is Default, use default value
   * - Apply remaining transformations to the default value
   * - This enables default values to work when fields are missing
   *
   * @param json Root JSON object
   * @param path JSONPath expression
   * @param transforms Optional list of transformations to apply
   * @return Right(extracted and transformed value) or Left(error message)
   */
  def extract(
    json: Json,
    path: String,
    transforms: List[RosettaTransformations.Transform] = Nil
  ): Either[String, Json] = {
    // Resolve the path
    resolvePath(json, path) match {
      case Some(value) =>
        // Apply transformations if any
        if (transforms.isEmpty) {
          Right(value)
        } else {
          RosettaTransformations.chain(value, transforms)
        }
      case None =>
        // Path not found - check if Default transformation exists
        transforms.headOption match {
          case Some(defaultTransform: RosettaTransformations.Default) =>
            // Use default value and apply remaining transformations
            val defaultValue = Json.fromString(defaultTransform.value)
            if (transforms.tail.isEmpty) {
              Right(defaultValue)
            } else {
              RosettaTransformations.chain(defaultValue, transforms.tail)
            }
          case _ =>
            // No default transformation, path is required
            Left(s"Path not found: $path")
        }
    }
  }

  /**
   * Extract multiple values for concat transformation
   *
   * Used when a field mapping uses concat with multiple source paths.
   *
   * @param json Root JSON object
   * @param paths List of JSONPath expressions to extract
   * @return Right(list of extracted values) or Left(error message)
   */
  def extractMultiple(json: Json, paths: List[String]): Either[String, List[Json]] = {
    val results = paths.map(path => resolvePath(json, path))

    // Check if all paths resolved successfully
    if (results.forall(_.isDefined)) {
      Right(results.flatten)
    } else {
      val missingPaths = paths.zip(results)
        .filter(_._2.isEmpty)
        .map(_._1)
      Left(s"Paths not found: ${missingPaths.mkString(", ")}")
    }
  }

  /**
   * Path segment ADT representing components of a JSONPath expression
   */
  private sealed trait PathSegment

  /**
   * Simple field access: `.field`
   */
  private case class FieldSegment(name: String) extends PathSegment

  /**
   * Optional field access: `.field?`
   * Returns None instead of failing if field doesn't exist
   */
  private case class OptionalFieldSegment(name: String) extends PathSegment

  /**
   * Array index access: `[0]`, `[5]`
   */
  private case class IndexSegment(index: Int) extends PathSegment

  /**
   * Array wildcard: `[*]`
   * Collects all elements
   */
  private case object WildcardSegment extends PathSegment

  /**
   * Array length operation: `.length()`
   */
  private case object LengthSegment extends PathSegment
}
