package io.distia.probe.common.models

/**
 * Block Storage Directive - Test Data Location
 *
 * Encapsulates all information needed to locate and access test data
 * in block storage (S3, Azure Blob, GCS, etc.) and jimfs (in-memory filesystem).
 *
 * @param jimfsLocation String path to jimfs directory where test data is downloaded (e.g., "/testId/features/")
 * @param evidenceDir String path to jimfs directory for test evidence output (e.g., "/testId/evidence/")
 * @param topicDirectives List of Kafka topic configuration files (topic-name.json)
 * @param bucket Block storage bucket name (S3 bucket, Azure container, etc.)
 * @param userGluePackages Optional list of user-provided Cucumber glue packages for custom step definitions
 */
case class BlockStorageDirective(
  jimfsLocation: String,
  evidenceDir: String,
  topicDirectives: List[TopicDirective],
  bucket: String,
  userGluePackages: List[String] = List.empty,
  tags: List[String] = List.empty
)
