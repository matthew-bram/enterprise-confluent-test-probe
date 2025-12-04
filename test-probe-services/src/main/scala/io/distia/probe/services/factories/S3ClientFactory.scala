package io.distia.probe
package services
package factories

import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.transfer.s3.S3TransferManager

/**
 * Factory trait for creating AWS S3 clients.
 *
 * This abstraction enables dependency injection for testing purposes,
 * allowing tests to provide LocalStack-configured clients while
 * production code uses standard AWS endpoints.
 */
trait S3ClientFactory {

  /**
   * Creates an S3 async client configured for the specified region.
   *
   * @param region AWS region (e.g., "us-east-1")
   * @return Configured S3AsyncClient instance
   */
  def createAsyncClient(region: String): S3AsyncClient

  /**
   * Creates an S3 transfer manager wrapping the provided async client.
   *
   * @param asyncClient The S3AsyncClient to wrap
   * @return Configured S3TransferManager instance
   */
  def createTransferManager(asyncClient: S3AsyncClient): S3TransferManager
}
