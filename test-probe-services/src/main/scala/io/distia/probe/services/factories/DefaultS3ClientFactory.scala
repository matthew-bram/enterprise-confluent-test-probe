package io.distia.probe
package services
package factories

import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.transfer.s3.S3TransferManager

/**
 * Default production implementation of S3ClientFactory.
 *
 * Creates S3 clients using standard AWS SDK configuration with
 * default AWS endpoints. This is the implementation used in
 * production environments.
 */
class DefaultS3ClientFactory extends S3ClientFactory {

  override def createAsyncClient(region: String): S3AsyncClient = {
    S3AsyncClient.builder()
      .httpClientBuilder(NettyNioAsyncHttpClient.builder())
      .region(Region.of(region))
      .build()
  }

  override def createTransferManager(asyncClient: S3AsyncClient): S3TransferManager = {
    S3TransferManager.builder()
      .s3Client(asyncClient)
      .build()
  }
}
