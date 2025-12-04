package io.distia.probe
package services
package vault

/**
 * JaasConfigBuilder - JAAS Configuration String Generator for OAuth/OIDC Authentication
 *
 * This utility builds properly formatted JAAS (Java Authentication and Authorization Service)
 * configuration strings for Kafka OAuth Bearer authentication. The generated configuration
 * is used with `sasl.jaas.config` Kafka client property.
 *
 * Security Considerations:
 * - All credential values are escaped to prevent injection attacks
 * - Client secrets should never be logged - use SecretRedactor for logging
 * - Token endpoints must be valid HTTPS URLs in production
 *
 * Generated Format:
 * {{{
 *   org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required
 *     oauth.client.id="<escaped-id>"
 *     oauth.client.secret="<escaped-secret>"
 *     oauth.token.endpoint.uri="<escaped-endpoint>"
 *     [oauth.scope="<escaped-scope>"];
 * }}}
 *
 * Thread Safety: All methods are pure functions with no shared mutable state.
 *
 * @see VaultCredentialsMapper for credential extraction from vault responses
 * @see SecretRedactor for safe logging of JAAS configurations
 */
private[services] object JaasConfigBuilder {

  /**
   * Build a JAAS configuration string for OAuth Bearer authentication
   *
   * Constructs a properly formatted and escaped JAAS configuration string
   * suitable for use with Kafka's `sasl.jaas.config` property.
   *
   * @param clientId OAuth client identifier (required, non-empty)
   * @param clientSecret OAuth client secret (required, non-empty)
   * @param tokenEndpoint OAuth token endpoint URL (required, must be HTTP/HTTPS)
   * @param scope Optional OAuth scope(s) - space-separated if multiple
   * @return Formatted JAAS configuration string
   * @throws IllegalArgumentException if clientId, clientSecret, or tokenEndpoint is empty,
   *                                  or if tokenEndpoint is not a valid HTTP/HTTPS URL
   */
  def build(
    clientId: String,
    clientSecret: String,
    tokenEndpoint: String,
    scope: Option[String] = None
  ): String = {
    require(clientId.nonEmpty, "clientId cannot be empty")
    require(clientSecret.nonEmpty, "clientSecret cannot be empty")
    require(tokenEndpoint.nonEmpty, "tokenEndpoint cannot be empty")
    require(
      tokenEndpoint.startsWith("http://") || tokenEndpoint.startsWith("https://"),
      s"tokenEndpoint must be a valid HTTP/HTTPS URL: $tokenEndpoint"
    )

    val escapedClientId = escapeJaasValue(clientId)
    val escapedClientSecret = escapeJaasValue(clientSecret)
    val escapedTokenEndpoint = escapeJaasValue(tokenEndpoint)

    val scopeParam = scope match {
      case Some(s) if s.nonEmpty =>
        val escapedScope = escapeJaasValue(s)
        s""" oauth.scope="$escapedScope""""
      case _ =>
        ""
    }

    s"""org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
       | required oauth.client.id="$escapedClientId" oauth.client.secret="$escapedClientSecret"
       |  oauth.token.endpoint.uri="$escapedTokenEndpoint"$scopeParam;""".stripMargin
  }

  /**
   * Build a JAAS configuration string with a required scope parameter
   *
   * Convenience method that enforces scope is provided. Use this when the OAuth
   * provider requires a scope parameter for token requests.
   *
   * @param clientId OAuth client identifier (required, non-empty)
   * @param clientSecret OAuth client secret (required, non-empty)
   * @param tokenEndpoint OAuth token endpoint URL (required, must be HTTP/HTTPS)
   * @param scope OAuth scope(s) (required, non-empty) - space-separated if multiple
   * @return Formatted JAAS configuration string with scope included
   * @throws IllegalArgumentException if any parameter is empty or invalid
   */
  def buildWithScope(
    clientId: String,
    clientSecret: String,
    tokenEndpoint: String,
    scope: String
  ): String = {
    require(scope.nonEmpty, "scope cannot be empty when using buildWithScope")
    build(clientId, clientSecret, tokenEndpoint, Some(scope))
  }

  /** Escape special characters in JAAS configuration values to prevent parsing issues */
  private def escapeJaasValue(value: String): String = {
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
  }

  /**
   * Validate a JAAS configuration string for OAuth Bearer authentication
   *
   * Checks that the configuration contains all required elements:
   * - OAuthBearerLoginModule class reference
   * - 'required' control flag
   * - Semicolon terminator
   * - Required OAuth parameters (client.id, client.secret, token.endpoint.uri)
   *
   * @param jaasConfig JAAS configuration string to validate
   * @return Right(()) if valid, Left(errorMessage) if invalid
   */
  def validate(jaasConfig: String): Either[String, Unit] = {
    if (!jaasConfig.contains("OAuthBearerLoginModule")) {
      return Left("JAAS config must contain OAuthBearerLoginModule")
    }

    if (!jaasConfig.contains("required")) {
      return Left("JAAS config must specify 'required' flag")
    }

    if (!jaasConfig.trim.endsWith(";")) {
      return Left("JAAS config must end with semicolon")
    }

    val requiredParams = List(
      "oauth.client.id",
      "oauth.client.secret",
      "oauth.token.endpoint.uri"
    )

    val missingParams = requiredParams.filterNot(param => jaasConfig.contains(param))
    if (missingParams.nonEmpty) {
      return Left(s"JAAS config missing required parameters: ${missingParams.mkString(", ")}")
    }

    Right(())
  }

  /**
   * Extract the OAuth client ID from a JAAS configuration string
   *
   * @param jaasConfig JAAS configuration string to parse
   * @return Some(clientId) if found, None if not present or malformed
   */
  def extractClientId(jaasConfig: String): Option[String] = {
    extractParameter(jaasConfig, "oauth.client.id")
  }

  /** Extract a parameter value from the JAAS configuration using regex pattern matching */
  private def extractParameter(jaasConfig: String, paramName: String): Option[String] = {
    val pattern = s"""$paramName="([^"]+)"""".r
    pattern.findFirstMatchIn(jaasConfig).map(_.group(1))
  }
}
