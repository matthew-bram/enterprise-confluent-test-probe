package io.distia.probe.services
package fixtures

import io.distia.probe.services.factories.LambdaClientFactory
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.lambda.LambdaClient
import java.net.URI

/**
 * Test factory for creating Lambda clients configured to use LocalStack or WireMock.
 *
 * @param endpoint URI endpoint override (e.g., from LocalStack.getEndpointOverride)
 * @param region AWS region for the client
 * @param accessKey AWS access key for LocalStack
 * @param secretKey AWS secret key for LocalStack
 */
class LocalStackLambdaClientFactory(
  endpoint: URI,
  region: String,
  accessKey: String,
  secretKey: String
) extends LambdaClientFactory {

  override def createClient(region: String): LambdaClient = {
    // Ignore region parameter, use constructor fields configured for LocalStack
    LambdaClient.builder()
      .endpointOverride(endpoint)
      .region(Region.of(this.region))
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(accessKey, secretKey)
        )
      )
      .build()
  }
}
