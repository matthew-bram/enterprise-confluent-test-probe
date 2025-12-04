package io.distia.probe.services
package factories

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.lambda.LambdaClient

/**
 * Default production implementation of LambdaClientFactory.
 *
 * Creates standard AWS Lambda clients with no endpoint override.
 */
class DefaultLambdaClientFactory extends LambdaClientFactory {

  override def createClient(region: String): LambdaClient = {
    LambdaClient.builder()
      .region(Region.of(region))
      .build()
  }
}
