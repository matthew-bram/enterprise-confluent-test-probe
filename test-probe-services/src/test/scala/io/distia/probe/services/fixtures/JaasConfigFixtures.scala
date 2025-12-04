package io.distia.probe
package services
package fixtures

private[services] object JaasConfigFixtures {

  val validClientId: String = "test-client-id"
  val validClientSecret: String = "test-client-secret-xyz123"
  val validTokenEndpoint: String = "https://oauth.example.com/token"
  val validScope: String = "kafka:read kafka:write"

  def validJaasConfig: String =
    """org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
      | required oauth.client.id="test-client-id" oauth.client.secret="test-client-secret-xyz123"
      |  oauth.token.endpoint.uri="https://oauth.example.com/token";""".stripMargin

  def validJaasConfigWithScope: String =
    """org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
      | required oauth.client.id="test-client-id" oauth.client.secret="test-client-secret-xyz123"
      |  oauth.token.endpoint.uri="https://oauth.example.com/token" oauth.scope="kafka:read kafka:write";""".stripMargin

  def jaasConfigWithBackslashes: String =
    """org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
      | required oauth.client.id="domain\\user" oauth.client.secret="pass\\word"
      |  oauth.token.endpoint.uri="https://oauth.example.com/token";""".stripMargin

  def jaasConfigWithQuotes: String =
    """org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
      | required oauth.client.id="client\"id" oauth.client.secret="secret\"value"
      |  oauth.token.endpoint.uri="https://oauth.example.com/token";""".stripMargin

  def jaasConfigWithNewlines: String =
    """org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
      | required oauth.client.id="client\nid" oauth.client.secret="secret\nvalue"
      |  oauth.token.endpoint.uri="https://oauth.example.com/token";""".stripMargin

  def jaasConfigWithMultipleSpecialChars: String =
    """org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
      | required oauth.client.id="domain\\user\"test\nvalue" oauth.client.secret="pass\\word\"123\rvalue"
      |  oauth.token.endpoint.uri="https://oauth.example.com/token";""".stripMargin

  def invalidJaasConfigMissingModule: String =
    """required oauth.client.id="test-client-id" oauth.client.secret="test-client-secret-xyz123"
      |  oauth.token.endpoint.uri="https://oauth.example.com/token";""".stripMargin

  def invalidJaasConfigMissingRequired: String =
    """org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
      | oauth.client.id="test-client-id" oauth.client.secret="test-client-secret-xyz123"
      |  oauth.token.endpoint.uri="https://oauth.example.com/token";""".stripMargin

  def invalidJaasConfigMissingSemicolon: String =
    """org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
      | required oauth.client.id="test-client-id" oauth.client.secret="test-client-secret-xyz123"
      |  oauth.token.endpoint.uri="https://oauth.example.com/token"""".stripMargin

  def invalidJaasConfigMissingClientId: String =
    """org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
      | required oauth.client.secret="test-client-secret-xyz123"
      |  oauth.token.endpoint.uri="https://oauth.example.com/token";""".stripMargin

  val clientIdWithBackslash: String = "domain\\user"
  val clientSecretWithQuotes: String = "secret\"value"
  val clientIdWithNewline: String = "client\nid"
  val clientSecretWithCarriageReturn: String = "secret\rvalue"
}
