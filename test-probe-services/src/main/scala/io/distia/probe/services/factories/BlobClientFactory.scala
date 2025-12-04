package io.distia.probe
package services
package factories

import com.azure.storage.blob.BlobServiceAsyncClient

/**
 * Factory trait for creating Azure Blob Storage clients
 *
 * This factory enables dependency injection for Azure Blob Storage services.
 * Default implementation: DefaultBlobClientFactory
 *
 * Testing:
 * - Mock this factory in unit tests to avoid Azure dependencies
 * - Use Azurite (Azure Storage Emulator) for integration tests
 */
trait BlobClientFactory:

  /**
   * Create a configured Azure Blob Storage async client
   *
   * @param connectionString Azure Storage connection string
   * @return Configured BlobServiceAsyncClient
   */
  def createAsyncClient(connectionString: String): BlobServiceAsyncClient