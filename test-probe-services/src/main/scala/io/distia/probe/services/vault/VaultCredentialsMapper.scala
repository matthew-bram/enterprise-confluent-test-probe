package io.distia.probe
package services
package vault

import io.distia.probe.common.rosetta.{RosettaConfig, RosettaMapper, RosettaTransformations}
import io.distia.probe.common.exceptions.VaultMappingException
import io.circe.Json
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.*

/**
 * VaultCredentialsMapper - Rosetta-based Credential Field Extraction
 *
 * This utility extracts and validates credential fields from vault JSON responses using
 * the Rosetta mapping configuration. It provides flexible, configurable mapping from
 * cloud provider vault response structures to standardized credential fields.
 *
 * Key Features:
 * - Required field validation with fail-fast semantics
 * - Multiple field extraction with error accumulation
 * - Transformation support (default values, custom transforms)
 * - Type-safe credential access via VaultCredentials case class
 *
 * Error Handling:
 * - All validation errors are accumulated using Cats Validated
 * - Returns Either[NonEmptyList[Throwable], VaultCredentials]
 * - Guarantees at least one error if Left is returned
 *
 * Thread Safety: All methods are pure functions with no shared mutable state
 *
 * Security Note:
 * - Never log VaultCredentials contents directly - use SecretRedactor
 * - Credential fields typically include sensitive data (clientSecret, passwords)
 *
 * @see RosettaConfig for Rosetta mapping configuration
 * @see RosettaMapper for JSON path extraction logic
 * @see JaasConfigBuilder for consuming extracted credentials
 */
private[services] object VaultCredentialsMapper {

  /**
   * VaultCredentials - Type-safe container for extracted credential fields
   *
   * Wraps a Map[String, String] with safe accessor methods for credential fields.
   * All fields are stored as strings and should be treated as sensitive data.
   *
   * @param fields Map of credential field names to values
   */
  private[vault] case class VaultCredentials(
    fields: Map[String, String]
  ) {
    /** Safe accessor that returns Option if field exists */
    def get(key: String): Option[String] = fields.get(key)

    /** Direct accessor that throws if field is missing - use only when field is guaranteed to exist */
    def apply(key: String): String = fields(key)
  }

  /**
   * Map vault JSON response to VaultCredentials using Rosetta configuration
   *
   * Validates that all required fields are defined in the Rosetta mapping, then
   * extracts each field from the JSON response. All validation errors are accumulated
   * and returned as a NonEmptyList.
   *
   * Typical required fields: clientId, clientSecret
   *
   * @param vaultJson JSON response from vault (AWS Lambda, Azure Function, GCP Cloud Function)
   * @param config Rosetta configuration defining field mappings
   * @param requiredFields List of field names that must be present in the response
   * @return Right(VaultCredentials) if all fields extracted successfully,
   *         Left(NonEmptyList[Throwable]) if any validation or extraction failures
   * @throws VaultMappingException wrapped in Left for missing mappings or extraction errors
   */
  def mapToVaultCredentials(
    vaultJson: Json,
    config: RosettaConfig.RosettaConfig,
    requiredFields: List[String]
  ): Either[NonEmptyList[Throwable], VaultCredentials] = {

    config.validateRequiredFields(requiredFields) match {
      case Left(missingFields) =>
        val errors: NonEmptyList[Throwable] = NonEmptyList.of(
          new VaultMappingException(s"Missing required field mappings: ${missingFields.mkString(", ")}")
        )
        Left(errors)

      case Right(_) =>
        val results: List[ValidatedNel[Throwable, (String, String)]] =
          requiredFields.map { fieldName =>
            extractField(vaultJson, fieldName, config).map(value => (fieldName, value))
          }

        val validated: ValidatedNel[Throwable, VaultCredentials] =
          results.sequence.map { pairs =>
            VaultCredentials(fields = pairs.toMap)
          }

        validated.toEither
    }
  }

  /**
   * Extract a single field from vault JSON using Rosetta mapping
   *
   * Looks up the Rosetta mapping for the field, applies transformations (including
   * default values), and extracts the value from the JSON using the configured source path.
   *
   * Transformation Order:
   * 1. Apply default value transform (if configured)
   * 2. Apply additional transforms from mapping configuration
   * 3. Extract final string value
   *
   * @param json Vault JSON response
   * @param fieldName Name of the field to extract (e.g., "clientId", "clientSecret")
   * @param config Rosetta configuration
   * @return ValidatedNel[Throwable, String] with extracted value or accumulated errors
   * @throws VaultMappingException wrapped in Validated.invalid for:
   *                                - Missing field mapping
   *                                - Invalid transformations
   *                                - JSON extraction failures
   *                                - Non-string JSON values
   */
  def extractField(
    json: Json,
    fieldName: String,
    config: RosettaConfig.RosettaConfig
  ): ValidatedNel[Throwable, String] = config.getMapping(fieldName) match {
      case None => Validated
        .invalidNel(new VaultMappingException(s"No mapping found for field '$fieldName'"))
      case Some(mapping) =>
        val transformsResult: Either[String, List[RosettaTransformations.Transform]] = mapping.transformations
          .map(_.toTransform)
          .sequence
        transformsResult match {
          case Left(error) =>
            Validated.invalidNel(
              new VaultMappingException(s"Field '$fieldName': $error")
            )
          case Right(transforms) =>
            val allTransforms: List[RosettaTransformations.Transform] =
              mapping.defaultValue match {
                case Some(defaultVal) =>
                  RosettaTransformations.Default(defaultVal) :: transforms
                case None =>
                  transforms
              }
            RosettaMapper.extract(json, mapping.sourcePath, allTransforms) match {
              case Right(extractedJson) =>
                extractedJson.asString match {
                  case Some(str) =>
                    Validated.valid(str)
                  case None =>
                    Validated.invalidNel(
                      new VaultMappingException(
                        s"Field '$fieldName': Expected string value but got ${extractedJson.name}"
                      )
                    )
                }
              case Left(error) =>
                Validated.invalidNel(new VaultMappingException(s"Field '$fieldName': $error"))
            }
        }
    }

  /**
   * Map multiple vault JSON responses to VaultCredentials list
   *
   * Convenience method for batch processing multiple vault responses. Each JSON
   * response is mapped independently, and all errors are accumulated across all
   * responses.
   *
   * Use Case: Processing credentials for multiple topics/services in parallel
   *
   * @param vaultJsonList List of vault JSON responses to process
   * @param config Rosetta configuration defining field mappings
   * @param requiredFields List of field names that must be present in each response
   * @return Right(List[VaultCredentials]) if all responses processed successfully,
   *         Left(NonEmptyList[Throwable]) with accumulated errors from all failures
   */
  def mapMultiple(
    vaultJsonList: List[Json],
    config: RosettaConfig.RosettaConfig,
    requiredFields: List[String]
  ): Either[NonEmptyList[Throwable], List[VaultCredentials]] = {
    vaultJsonList.map(json => mapToVaultCredentials(json, config, requiredFields))
      .map(_.toValidated).sequence.toEither
  }
}
