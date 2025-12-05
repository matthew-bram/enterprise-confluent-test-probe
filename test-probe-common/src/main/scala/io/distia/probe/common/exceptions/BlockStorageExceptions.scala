package io.distia.probe.common.exceptions

/**
 * BlockStorageException - Base exception for block storage operations
 *
 * Root exception for all errors related to block storage operations including
 * S3, Azure Blob Storage, GCS, and JIMFS (in-memory filesystem) operations.
 *
 * This exception hierarchy covers:
 * - Feature file and directory validation errors
 * - Topic directive file parsing and validation errors
 * - Bucket URI parsing errors
 * - Streaming operation errors
 * - Kafka configuration validation errors
 *
 * @param message Human-readable error message describing the failure
 * @param cause Underlying exception that caused this error (if any)
 *
 * @see io.distia.probe.core.actors.BlockStorageActor for usage in block storage operations
 */
class BlockStorageException(message: String, cause: Throwable = null)
  extends RuntimeException(message, cause)

/**
 * MissingFeaturesDirectoryException - Features directory not found in block storage
 *
 * Thrown when the required "features/" directory is missing from the block storage
 * bucket during test initialization. The features directory must contain at least
 * one .feature file for Cucumber test execution.
 *
 * **Common Causes**:
 * - Incorrect bucket path in test submission
 * - Features directory not uploaded to block storage
 * - Typo in directory name (must be exactly "features/")
 *
 * @param message Error message including bucket and expected directory path
 * @param cause Underlying storage SDK exception (if any)
 */
class MissingFeaturesDirectoryException(message: String, cause: Throwable = null)
  extends BlockStorageException(message, cause)

/**
 * EmptyFeaturesDirectoryException - Features directory contains no .feature files
 *
 * Thrown when the features directory exists but contains no .feature files.
 * At least one .feature file is required for Cucumber test execution.
 *
 * **Common Causes**:
 * - .feature files not uploaded to block storage
 * - Wrong file extension (must be ".feature")
 * - Files uploaded to wrong subdirectory
 *
 * @param message Error message including directory path and file count
 * @param cause Underlying storage SDK exception (if any)
 */
class EmptyFeaturesDirectoryException(message: String, cause: Throwable = null)
  extends BlockStorageException(message, cause)

/**
 * MissingTopicDirectiveFileException - Required topic directive file not found
 *
 * Thrown when a topic directive JSON file (topic-name.json) referenced in
 * BlockStorageDirective is missing from the block storage bucket.
 *
 * **Topic Directive File Format**: Each Kafka topic requires a JSON file named
 * "{topic-name}.json" containing TopicDirective configuration (role, clientPrincipal,
 * eventFilters, etc.).
 *
 * **Common Causes**:
 * - Topic directive file not uploaded to block storage
 * - Incorrect filename (must match topic name exactly)
 * - File in wrong directory (must be in bucket root or configured path)
 *
 * @param message Error message including topic name and expected file path
 * @param cause Underlying storage SDK exception (if any)
 *
 * @see io.distia.probe.common.models.TopicDirective for topic directive structure
 */
class MissingTopicDirectiveFileException(message: String, cause: Throwable = null)
  extends BlockStorageException(message, cause)

/**
 * InvalidTopicDirectiveFormatException - Topic directive JSON parsing failed
 *
 * Thrown when a topic directive JSON file exists but cannot be parsed into a
 * valid TopicDirective object. This indicates malformed JSON, missing required
 * fields, or incorrect field types.
 *
 * **Required TopicDirective Fields**:
 * - topic (String): Kafka topic name
 * - role (String): "producer" or "consumer"
 * - clientPrincipal (String): OAuth client principal for vault lookup
 * - eventFilters (Array): List of {eventType, payloadVersion} objects
 *
 * **Common Causes**:
 * - Invalid JSON syntax
 * - Missing required fields
 * - Incorrect field types (e.g., string instead of array)
 * - Extra/unexpected fields
 *
 * @param message Error message including file path and parsing error details
 * @param cause Underlying JSON parsing exception
 *
 * @see io.distia.probe.common.models.TopicDirective for required format
 */
class InvalidTopicDirectiveFormatException(message: String, cause: Throwable = null)
  extends BlockStorageException(message, cause)

/**
 * BucketUriParseException - Block storage bucket URI parsing failed
 *
 * Thrown when a block storage bucket URI cannot be parsed to extract the
 * cloud provider, bucket name, and optional path prefix.
 *
 * **Expected URI Formats**:
 * - S3: "s3://bucket-name/optional/prefix"
 * - Azure: "azure://container-name/optional/prefix"
 * - GCS: "gs://bucket-name/optional/prefix"
 *
 * **Common Causes**:
 * - Missing or incorrect URI scheme (must be s3://, azure://, or gs://)
 * - Invalid bucket name (empty or contains invalid characters)
 * - Malformed URI structure
 *
 * @param message Error message including invalid URI and expected format
 * @param cause Underlying URI parsing exception (if any)
 */
class BucketUriParseException(message: String, cause: Throwable = null)
  extends BlockStorageException(message, cause)

/**
 * StreamingException - Pekko Streams materialization or execution failure
 *
 * Thrown when a Pekko Streams flow (block storage download/upload) fails during
 * materialization or execution. This wraps underlying stream failures for
 * consistent error handling.
 *
 * **Common Causes**:
 * - Network failures during download/upload
 * - Insufficient memory for large files
 * - Stream backpressure violations
 * - Storage SDK errors during streaming
 *
 * @param message Error message including stream operation and failure details
 * @param cause Underlying stream or storage SDK exception (required)
 *
 * @see io.distia.probe.core.actors.BlockStorageActor for streaming operations
 */
class StreamingException(message: String, cause: Throwable)
  extends BlockStorageException(message, cause)

/**
 * DuplicateTopicException - Duplicate topic + role combination detected
 *
 * Thrown by TopicDirectiveValidator when multiple TopicDirective instances
 * have the same topic name and role combination. Each topic + role pair must
 * be unique within a BlockStorageDirective.
 *
 * **Uniqueness Rule**: The combination of (topic, role) must be unique. You can have:
 * - Same topic with different roles: ("orders.events", "producer") and ("orders.events", "consumer") - OK
 * - Different topics with same role: ("orders.events", "producer") and ("payments.events", "producer") - OK
 * - Same topic + role twice: ("orders.events", "producer") and ("orders.events", "producer") - ERROR
 *
 * **Common Causes**:
 * - Duplicate topic directive files in block storage
 * - Copy/paste errors in topic directive configuration
 * - Incorrect merge of multiple test configurations
 *
 * @param message Error message including duplicate topic, role, and file paths
 * @param cause Underlying validation exception (if any)
 *
 * @see io.distia.probe.common.validation.TopicDirectiveValidator for validation rules
 */
class DuplicateTopicException(message: String, cause: Throwable = null)
  extends BlockStorageException(message, cause)

/**
 * InvalidBootstrapServersException - Bootstrap servers format validation failed
 *
 * Thrown by TopicDirectiveValidator when the optional bootstrapServers field
 * in a TopicDirective contains an invalid format.
 *
 * **Valid Format**: "host1:port1,host2:port2,host3:port3"
 * - Comma-separated list of "host:port" pairs
 * - Each host must be a valid hostname or IP address
 * - Each port must be a valid integer (1-65535)
 * - No spaces or special characters
 *
 * **Invalid Examples**:
 * - "broker:9092;" (semicolon instead of comma)
 * - "broker:9092, broker2:9092" (space after comma)
 * - "broker" (missing port)
 * - "broker:abc" (non-numeric port)
 *
 * **Common Causes**:
 * - Copy/paste errors from configuration files
 * - Incorrect delimiter (semicolon, space instead of comma)
 * - Missing or invalid port numbers
 * - Extra whitespace
 *
 * @param message Error message including invalid bootstrap servers and expected format
 * @param cause Underlying validation exception (if any)
 *
 * @see io.distia.probe.common.validation.TopicDirectiveValidator for validation rules
 * @see io.distia.probe.common.models.TopicDirective for bootstrapServers field usage
 */
class InvalidBootstrapServersException(message: String, cause: Throwable = null)
  extends BlockStorageException(message, cause)
