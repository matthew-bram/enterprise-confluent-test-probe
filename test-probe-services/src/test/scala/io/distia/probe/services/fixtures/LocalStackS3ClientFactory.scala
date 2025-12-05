package io.distia.probe
package services
package fixtures

import io.distia.probe.services.factories.S3ClientFactory
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.transfer.s3.S3TransferManager

import java.net.URI

/**
 * LocalStack implementation of S3ClientFactory for integration testing.
 *
 * Creates S3 clients configured to connect to a LocalStack container
 * with endpoint override and test credentials.
 *
 * @param endpoint LocalStack S3 endpoint URI (e.g., http://localhost:4566)
 * @param region AWS region to use (should match LocalStack configuration)
 * @param accessKey LocalStack access key
 * @param secretKey LocalStack secret key
 */
class LocalStackS3ClientFactory(
  endpoint: URI,
  region: String,
  accessKey: String,
  secretKey: String
) extends S3ClientFactory {

  override def createAsyncClient(region: String): S3AsyncClient = {
    S3AsyncClient.builder()
      .endpointOverride(endpoint)
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(accessKey, secretKey)
        )
      )
      .region(Region.of(this.region))  // Use factory's region, not parameter
      .httpClientBuilder(NettyNioAsyncHttpClient.builder())
      .build()
  }

  override def createTransferManager(asyncClient: S3AsyncClient): S3TransferManager = {
    S3TransferManager.builder()
      .s3Client(asyncClient)
      .build()
  }
}
