package io.distia.probe
package services
package config

import io.distia.probe.services.fixtures.VaultConfigFixtures
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.*

class VaultConfigSpec extends AnyWordSpec with Matchers {

  "VaultConfig.fromConfig" should {

    "load local vault configuration successfully" in {
      val config: Config = VaultConfigFixtures.defaultLocalVaultConfig
      val vaultConfig: VaultConfig = VaultConfig.fromConfig(config)

      vaultConfig.provider shouldBe "local"
      vaultConfig.local.requiredFields should contain theSameElementsAs List("clientId", "clientSecret")
    }

    "load AWS vault configuration successfully" in {
      val config: Config = VaultConfigFixtures.defaultAwsVaultConfig
      val vaultConfig: VaultConfig = VaultConfig.fromConfig(config)

      vaultConfig.provider shouldBe "aws"
      vaultConfig.aws.lambdaArn shouldBe "arn:aws:lambda:us-east-1:123456789012:function:vault-invoker"
      vaultConfig.aws.region shouldBe "us-east-1"
      vaultConfig.aws.timeout shouldBe 30.seconds
      vaultConfig.aws.retryAttempts shouldBe 3
      vaultConfig.aws.requiredFields should contain theSameElementsAs List("clientId", "clientSecret")
    }

    "load Azure vault configuration successfully" in {
      val config: Config = VaultConfigFixtures.defaultAzureVaultConfig
      val vaultConfig: VaultConfig = VaultConfig.fromConfig(config)

      vaultConfig.provider shouldBe "azure"
      vaultConfig.azure.functionUrl shouldBe "https://vault-function.azurewebsites.net/api/vault"
      vaultConfig.azure.functionKey shouldBe "azure-function-key-123"
      vaultConfig.azure.timeout shouldBe 30.seconds
      vaultConfig.azure.retryAttempts shouldBe 3
      vaultConfig.azure.requiredFields should contain theSameElementsAs List("clientId", "clientSecret")
    }

    "load GCP vault configuration successfully with service account key" in {
      val config: Config = VaultConfigFixtures.defaultGcpVaultConfig
      val vaultConfig: VaultConfig = VaultConfig.fromConfig(config)

      vaultConfig.provider shouldBe "gcp"
      vaultConfig.gcp.functionUrl shouldBe "https://us-central1-project-id.cloudfunctions.net/vault-invoker"
      vaultConfig.gcp.serviceAccountKey shouldBe Some("/path/to/service-account-key.json")
      vaultConfig.gcp.timeout shouldBe 30.seconds
      vaultConfig.gcp.retryAttempts shouldBe 3
      vaultConfig.gcp.requiredFields should contain theSameElementsAs List("clientId", "clientSecret")
    }

    "load GCP vault configuration successfully without service account key" in {
      val config: Config = VaultConfigFixtures.gcpVaultConfigWithoutServiceAccountKey
      val vaultConfig: VaultConfig = VaultConfig.fromConfig(config)

      vaultConfig.provider shouldBe "gcp"
      vaultConfig.gcp.functionUrl shouldBe "https://us-central1-project-id.cloudfunctions.net/vault-invoker"
      vaultConfig.gcp.serviceAccountKey shouldBe None
    }

    "load OAuth configuration successfully" in {
      val config: Config = VaultConfigFixtures.defaultAwsVaultConfig
      val vaultConfig: VaultConfig = VaultConfig.fromConfig(config)

      vaultConfig.oauth.tokenEndpoint shouldBe "https://oauth.example.com/token"
      vaultConfig.oauth.scope shouldBe "kafka:read kafka:write"
    }

    "load rosetta mapping path when present" in {
      val config: Config = VaultConfigFixtures.defaultAwsVaultConfig
      val vaultConfig: VaultConfig = VaultConfig.fromConfig(config)

      vaultConfig.rosettaMappingPath shouldBe Some("classpath:rosetta/aws-vault-mapping.yaml")
    }

    "return None for rosetta mapping path when not present" in {
      val config: Config = VaultConfigFixtures.defaultLocalVaultConfig
      val vaultConfig: VaultConfig = VaultConfig.fromConfig(config)

      vaultConfig.rosettaMappingPath shouldBe None
    }
  }

  "VaultConfig validation" should {

    "reject invalid provider" in {
      val config: Config = VaultConfigFixtures.invalidProviderConfig

      val exception = intercept[IllegalArgumentException] {
        VaultConfig.fromConfig(config)
      }
      exception.getMessage should include("Invalid vault provider")
      exception.getMessage should include("invalid-provider")
    }

    "reject AWS configuration with empty lambda ARN when provider is aws" in {
      val config: Config = VaultConfigFixtures.awsVaultConfigWithMissingLambdaArn

      val exception = intercept[IllegalArgumentException] {
        VaultConfig.fromConfig(config)
      }
      exception.getMessage should include("AWS Lambda ARN cannot be empty")
    }

    "reject AWS configuration with missing region when provider is aws" in {
      val config: Config = ConfigFactory.parseString(
        """
          |test-probe {
          |  services {
          |    vault {
          |      provider = "aws"
          |      local {
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      aws {
          |        lambda-arn = "arn:aws:lambda:us-east-1:123456789012:function:vault-invoker"
          |        region = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      azure {
          |        function-url = ""
          |        function-key = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      gcp {
          |        function-url = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      oauth {
          |        token-endpoint = "https://oauth.example.com/token"
          |        scope = "kafka:read kafka:write"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      )

      val exception = intercept[IllegalArgumentException] {
        VaultConfig.fromConfig(config)
      }
      exception.getMessage should include("AWS region cannot be empty")
    }

    "reject Azure configuration with empty function URL when provider is azure" in {
      val config: Config = ConfigFactory.parseString(
        """
          |test-probe {
          |  services {
          |    vault {
          |      provider = "azure"
          |      local {
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      aws {
          |        lambda-arn = ""
          |        region = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      azure {
          |        function-url = ""
          |        function-key = "azure-function-key-123"
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      gcp {
          |        function-url = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      oauth {
          |        token-endpoint = "https://oauth.example.com/token"
          |        scope = "kafka:read kafka:write"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      )

      val exception = intercept[IllegalArgumentException] {
        VaultConfig.fromConfig(config)
      }
      exception.getMessage should include("Azure function URL cannot be empty")
    }

    "reject Azure configuration with empty function key when provider is azure" in {
      val config: Config = ConfigFactory.parseString(
        """
          |test-probe {
          |  services {
          |    vault {
          |      provider = "azure"
          |      local {
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      aws {
          |        lambda-arn = ""
          |        region = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      azure {
          |        function-url = "https://vault-function.azurewebsites.net/api/vault"
          |        function-key = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      gcp {
          |        function-url = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      oauth {
          |        token-endpoint = "https://oauth.example.com/token"
          |        scope = "kafka:read kafka:write"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      )

      val exception = intercept[IllegalArgumentException] {
        VaultConfig.fromConfig(config)
      }
      exception.getMessage should include("Azure function key cannot be empty")
    }

    "reject GCP configuration with empty function URL when provider is gcp" in {
      val config: Config = ConfigFactory.parseString(
        """
          |test-probe {
          |  services {
          |    vault {
          |      provider = "gcp"
          |      local {
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      aws {
          |        lambda-arn = ""
          |        region = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      azure {
          |        function-url = ""
          |        function-key = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      gcp {
          |        function-url = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      oauth {
          |        token-endpoint = "https://oauth.example.com/token"
          |        scope = "kafka:read kafka:write"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      )

      val exception = intercept[IllegalArgumentException] {
        VaultConfig.fromConfig(config)
      }
      exception.getMessage should include("GCP function URL cannot be empty")
    }

    "reject AWS configuration with zero retry attempts" in {
      val config: Config = VaultConfigFixtures.awsVaultConfigWithZeroRetries

      val exception = intercept[IllegalArgumentException] {
        VaultConfig.fromConfig(config)
      }
      exception.getMessage should include("AWS retry attempts must be positive")
    }

    "reject Azure configuration with negative retry attempts" in {
      val config: Config = ConfigFactory.parseString(
        """
          |test-probe {
          |  services {
          |    vault {
          |      provider = "azure"
          |      local {
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      aws {
          |        lambda-arn = ""
          |        region = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      azure {
          |        function-url = "https://vault-function.azurewebsites.net/api/vault"
          |        function-key = "azure-function-key-123"
          |        timeout = 30s
          |        retry-attempts = -1
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      gcp {
          |        function-url = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      oauth {
          |        token-endpoint = "https://oauth.example.com/token"
          |        scope = "kafka:read kafka:write"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      )

      val exception = intercept[IllegalArgumentException] {
        VaultConfig.fromConfig(config)
      }
      exception.getMessage should include("Azure retry attempts must be positive")
    }

    "reject GCP configuration with zero retry attempts" in {
      val config: Config = ConfigFactory.parseString(
        """
          |test-probe {
          |  services {
          |    vault {
          |      provider = "gcp"
          |      local {
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      aws {
          |        lambda-arn = ""
          |        region = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      azure {
          |        function-url = ""
          |        function-key = ""
          |        timeout = 30s
          |        retry-attempts = 3
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      gcp {
          |        function-url = "https://us-central1-project-id.cloudfunctions.net/vault-invoker"
          |        timeout = 30s
          |        retry-attempts = 0
          |        required-fields = ["clientId", "clientSecret"]
          |      }
          |      oauth {
          |        token-endpoint = "https://oauth.example.com/token"
          |        scope = "kafka:read kafka:write"
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      )

      val exception = intercept[IllegalArgumentException] {
        VaultConfig.fromConfig(config)
      }
      exception.getMessage should include("GCP retry attempts must be positive")
    }

    "accept local provider with no provider-specific validation" in {
      val config: Config = VaultConfigFixtures.defaultLocalVaultConfig
      val vaultConfig: VaultConfig = VaultConfig.fromConfig(config)

      vaultConfig.provider shouldBe "local"
    }
  }
}
