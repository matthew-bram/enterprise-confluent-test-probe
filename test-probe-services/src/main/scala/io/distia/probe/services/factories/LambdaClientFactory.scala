package io.distia.probe.services
package factories

import software.amazon.awssdk.services.lambda.LambdaClient

/**
 * Factory for creating AWS Lambda clients with configurable endpoints.
 *
 * This trait enables dependency injection for testing, allowing LocalStack
 * or other mock implementations to override Lambda endpoints.
 */
trait LambdaClientFactory {

  /**
   * Creates a Lambda client configured for the specified region.
   *
   * @param region AWS region (e.g., "us-east-1")
   * @return Configured LambdaClient instance
   */
  def createClient(region: String): LambdaClient
}
