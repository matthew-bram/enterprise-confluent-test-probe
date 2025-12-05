package io.distia.probe
package services
package vault

import io.distia.probe.services.fixtures.JaasConfigFixtures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class JaasConfigBuilderSpec extends AnyWordSpec with Matchers {

  "JaasConfigBuilder.build" should {

    "build valid JAAS config without scope" in {
      val jaasConfig: String = JaasConfigBuilder.build(
        clientId = JaasConfigFixtures.validClientId,
        clientSecret = JaasConfigFixtures.validClientSecret,
        tokenEndpoint = JaasConfigFixtures.validTokenEndpoint,
        scope = None
      )

      jaasConfig should include("OAuthBearerLoginModule")
      jaasConfig should include("required")
      jaasConfig should include(s"""oauth.client.id="${JaasConfigFixtures.validClientId}"""")
      jaasConfig should include(s"""oauth.client.secret="${JaasConfigFixtures.validClientSecret}"""")
      jaasConfig should include(s"""oauth.token.endpoint.uri="${JaasConfigFixtures.validTokenEndpoint}"""")
      jaasConfig should not include "oauth.scope"
      jaasConfig should endWith(";")
    }

    "build valid JAAS config with scope" in {
      val jaasConfig: String = JaasConfigBuilder.build(
        clientId = JaasConfigFixtures.validClientId,
        clientSecret = JaasConfigFixtures.validClientSecret,
        tokenEndpoint = JaasConfigFixtures.validTokenEndpoint,
        scope = Some(JaasConfigFixtures.validScope)
      )

      jaasConfig should include("OAuthBearerLoginModule")
      jaasConfig should include("required")
      jaasConfig should include(s"""oauth.client.id="${JaasConfigFixtures.validClientId}"""")
      jaasConfig should include(s"""oauth.client.secret="${JaasConfigFixtures.validClientSecret}"""")
      jaasConfig should include(s"""oauth.token.endpoint.uri="${JaasConfigFixtures.validTokenEndpoint}"""")
      jaasConfig should include(s"""oauth.scope="${JaasConfigFixtures.validScope}"""")
      jaasConfig should endWith(";")
    }

    "build valid JAAS config with empty scope string treated as None" in {
      val jaasConfig: String = JaasConfigBuilder.build(
        clientId = JaasConfigFixtures.validClientId,
        clientSecret = JaasConfigFixtures.validClientSecret,
        tokenEndpoint = JaasConfigFixtures.validTokenEndpoint,
        scope = Some("")
      )

      jaasConfig should not include "oauth.scope"
    }

    "escape backslashes in client ID and client secret" in {
      val jaasConfig: String = JaasConfigBuilder.build(
        clientId = JaasConfigFixtures.clientIdWithBackslash,
        clientSecret = "password\\with\\backslashes",
        tokenEndpoint = JaasConfigFixtures.validTokenEndpoint
      )

      jaasConfig should include("""oauth.client.id="domain\\user"""")
      jaasConfig should include("""oauth.client.secret="password\\with\\backslashes"""")
    }

    "escape double quotes in client ID and client secret" in {
      val jaasConfig: String = JaasConfigBuilder.build(
        clientId = "client\"id",
        clientSecret = JaasConfigFixtures.clientSecretWithQuotes,
        tokenEndpoint = JaasConfigFixtures.validTokenEndpoint
      )

      jaasConfig should include("""oauth.client.id="client\"id"""")
      jaasConfig should include("""oauth.client.secret="secret\"value"""")
    }

    "escape newlines in client ID and client secret" in {
      val jaasConfig: String = JaasConfigBuilder.build(
        clientId = JaasConfigFixtures.clientIdWithNewline,
        clientSecret = "secret\nvalue",
        tokenEndpoint = JaasConfigFixtures.validTokenEndpoint
      )

      jaasConfig should include("""oauth.client.id="client\nid"""")
      jaasConfig should include("""oauth.client.secret="secret\nvalue"""")
    }

    "escape carriage returns in client secret" in {
      val jaasConfig: String = JaasConfigBuilder.build(
        clientId = JaasConfigFixtures.validClientId,
        clientSecret = JaasConfigFixtures.clientSecretWithCarriageReturn,
        tokenEndpoint = JaasConfigFixtures.validTokenEndpoint
      )

      jaasConfig should include("""oauth.client.secret="secret\rvalue"""")
    }

    "escape multiple special characters in same value" in {
      val jaasConfig: String = JaasConfigBuilder.build(
        clientId = "domain\\user\"test\nvalue",
        clientSecret = "pass\\word\"123\rvalue",
        tokenEndpoint = JaasConfigFixtures.validTokenEndpoint
      )

      jaasConfig should include("""oauth.client.id="domain\\user\"test\nvalue"""")
      jaasConfig should include("""oauth.client.secret="pass\\word\"123\rvalue"""")
    }

    "reject empty clientId" in {
      val exception = intercept[IllegalArgumentException] {
        JaasConfigBuilder.build("", "secret", JaasConfigFixtures.validTokenEndpoint)
      }
      exception.getMessage should include("clientId cannot be empty")
    }

    "reject empty clientSecret" in {
      val exception = intercept[IllegalArgumentException] {
        JaasConfigBuilder.build("clientId", "", JaasConfigFixtures.validTokenEndpoint)
      }
      exception.getMessage should include("clientSecret cannot be empty")
    }

    "reject empty tokenEndpoint" in {
      val exception = intercept[IllegalArgumentException] {
        JaasConfigBuilder.build("clientId", "secret", "")
      }
      exception.getMessage should include("tokenEndpoint cannot be empty")
    }

    "reject invalid tokenEndpoint without http/https scheme" in {
      val exception = intercept[IllegalArgumentException] {
        JaasConfigBuilder.build("clientId", "secret", "ftp://invalid.com/token")
      }
      exception.getMessage should include("tokenEndpoint must be a valid HTTP/HTTPS URL")
    }
  }

  "JaasConfigBuilder.buildWithScope" should {

    "build JAAS config with scope" in {
      val jaasConfig: String = JaasConfigBuilder.buildWithScope(
        clientId = JaasConfigFixtures.validClientId,
        clientSecret = JaasConfigFixtures.validClientSecret,
        tokenEndpoint = JaasConfigFixtures.validTokenEndpoint,
        scope = JaasConfigFixtures.validScope
      )

      jaasConfig should include(s"""oauth.scope="${JaasConfigFixtures.validScope}"""")
    }

    "reject empty scope" in {
      val exception = intercept[IllegalArgumentException] {
        JaasConfigBuilder.buildWithScope(
          JaasConfigFixtures.validClientId,
          JaasConfigFixtures.validClientSecret,
          JaasConfigFixtures.validTokenEndpoint,
          ""
        )
      }
      exception.getMessage should include("scope cannot be empty when using buildWithScope")
    }
  }

  "JaasConfigBuilder.validate" should {

    "validate correct JAAS config without scope" in {
      val result: Either[String, Unit] = JaasConfigBuilder.validate(JaasConfigFixtures.validJaasConfig)

      result shouldBe Right(())
    }

    "validate correct JAAS config with scope" in {
      val result: Either[String, Unit] = JaasConfigBuilder.validate(JaasConfigFixtures.validJaasConfigWithScope)

      result shouldBe Right(())
    }

    "reject JAAS config missing OAuthBearerLoginModule" in {
      val result: Either[String, Unit] = JaasConfigBuilder.validate(JaasConfigFixtures.invalidJaasConfigMissingModule)

      result shouldBe a[Left[_, _]]
      result.left.getOrElse(fail("Expected Left")) should include("OAuthBearerLoginModule")
    }

    "reject JAAS config missing required flag" in {
      val result: Either[String, Unit] = JaasConfigBuilder.validate(JaasConfigFixtures.invalidJaasConfigMissingRequired)

      result shouldBe a[Left[_, _]]
      result.left.getOrElse(fail("Expected Left")) should include("required")
    }

    "reject JAAS config missing semicolon" in {
      val result: Either[String, Unit] = JaasConfigBuilder.validate(JaasConfigFixtures.invalidJaasConfigMissingSemicolon)

      result shouldBe a[Left[_, _]]
      result.left.getOrElse(fail("Expected Left")) should include("semicolon")
    }

    "reject JAAS config missing oauth.client.id parameter" in {
      val result: Either[String, Unit] = JaasConfigBuilder.validate(JaasConfigFixtures.invalidJaasConfigMissingClientId)

      result shouldBe a[Left[_, _]]
      result.left.getOrElse(fail("Expected Left")) should include("oauth.client.id")
    }

    "reject JAAS config missing oauth.client.secret parameter" in {
      val invalidConfig: String = """org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
        | required oauth.client.id="test-client-id"
        |  oauth.token.endpoint.uri="https://oauth.example.com/token";""".stripMargin

      val result: Either[String, Unit] = JaasConfigBuilder.validate(invalidConfig)

      result shouldBe a[Left[_, _]]
      result.left.getOrElse(fail("Expected Left")) should include("oauth.client.secret")
    }

    "reject JAAS config missing oauth.token.endpoint.uri parameter" in {
      val invalidConfig: String = """org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule
        | required oauth.client.id="test-client-id" oauth.client.secret="test-secret";""".stripMargin

      val result: Either[String, Unit] = JaasConfigBuilder.validate(invalidConfig)

      result shouldBe a[Left[_, _]]
      result.left.getOrElse(fail("Expected Left")) should include("oauth.token.endpoint.uri")
    }
  }

  "JaasConfigBuilder.extractClientId" should {

    "extract clientId from valid JAAS config" in {
      val result: Option[String] = JaasConfigBuilder.extractClientId(JaasConfigFixtures.validJaasConfig)

      result shouldBe Some(JaasConfigFixtures.validClientId)
    }

    "extract clientId with special characters" in {
      val result: Option[String] = JaasConfigBuilder.extractClientId(JaasConfigFixtures.jaasConfigWithBackslashes)

      result.map(_.replaceAll("\\\\+", "/")) shouldBe Some("domain/user")
    }

    "return None when clientId not found in JAAS config" in {
      val result: Option[String] = JaasConfigBuilder.extractClientId(JaasConfigFixtures.invalidJaasConfigMissingClientId)

      result shouldBe None
    }
  }
}
