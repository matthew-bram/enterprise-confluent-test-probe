package io.distia.probe
package services
package factories

import com.google.cloud.storage.Storage

/**
 * Factory trait for creating Google Cloud Storage clients
 *
 * This factory enables dependency injection for GCP Cloud Storage services.
 * Default implementation: DefaultStorageClientFactory
 *
 * Authentication Methods:
 * - Service account key file (explicit path provided)
 * - Application Default Credentials (ADC - when no key path provided)
 *
 * Testing:
 * - Mock this factory in unit tests to avoid GCP dependencies
 * - Use GCS emulator (fake-gcs-server) for integration tests
 */
trait StorageClientFactory:

  /**
   * Create a configured GCP Cloud Storage client
   *
   * @param projectId GCP project ID
   * @param serviceAccountKeyPath Optional path to service account JSON key file
   * @return Configured Storage client
   */
  def createClient(projectId: String, serviceAccountKeyPath: Option[String]): Storage
