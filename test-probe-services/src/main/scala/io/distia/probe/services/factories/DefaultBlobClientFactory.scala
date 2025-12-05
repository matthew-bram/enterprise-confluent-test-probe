package io.distia.probe
package services
package factories

import com.azure.storage.blob.{BlobServiceAsyncClient, BlobServiceClientBuilder}

/**
 * Default production implementation of BlobClientFactory
 *
 * Creates Azure Blob Storage clients using standard Azure SDK configuration.
 * This is the implementation used in production environments.
 */
class DefaultBlobClientFactory extends BlobClientFactory:

  override def createAsyncClient(connectionString: String): BlobServiceAsyncClient =
    BlobServiceClientBuilder()
      .connectionString(connectionString)
      .buildAsyncClient()