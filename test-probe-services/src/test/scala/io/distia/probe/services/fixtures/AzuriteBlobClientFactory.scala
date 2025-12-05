package io.distia.probe
package services
package fixtures

import com.azure.storage.blob.{BlobServiceAsyncClient, BlobServiceClientBuilder}
import io.distia.probe.services.factories.BlobClientFactory

class AzuriteBlobClientFactory(endpoint: String) extends BlobClientFactory:

  private val azuriteConnectionString: String =
    s"DefaultEndpointsProtocol=http;" +
    s"AccountName=devstoreaccount1;" +
    s"AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;" +
    s"BlobEndpoint=$endpoint;"

  override def createAsyncClient(connectionString: String): BlobServiceAsyncClient =
    BlobServiceClientBuilder()
      .connectionString(azuriteConnectionString)
      .buildAsyncClient()