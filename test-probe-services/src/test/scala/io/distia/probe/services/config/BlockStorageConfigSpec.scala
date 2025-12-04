package io.distia.probe
package services
package config

import fixtures.BlockStorageConfigFixtures
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*

/**
 * Unit tests for BlockStorageConfig
 * Tests HOCON configuration loading, validation, and provider-specific requirements
 */
private[services] class BlockStorageConfigSpec extends AnyWordSpec with Matchers {

  // ========== VALID CONFIGURATION TESTS ==========

  "BlockStorageConfig.fromConfig" when {

    "loading valid local provider config" should {

      "parse all fields correctly" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.validLocalConfig
        )

        config.provider shouldBe "local"
        config.topicDirectiveFileName shouldBe "topic-directive.yaml"
      }

      "create LocalBlockStorageConfig instance" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.validLocalConfig
        )

        config.local shouldBe LocalBlockStorageConfig()
      }

      "pass validation" in {
        noException should be thrownBy {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.validLocalConfig)
        }
      }
    }

    "loading valid AWS provider config" should {

      "parse all fields correctly" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.validAwsConfig
        )

        config.provider shouldBe "aws"
        config.topicDirectiveFileName shouldBe "topic-directive.yaml"
      }

      "create AwsBlockStorageConfig with correct values" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.validAwsConfig
        )

        config.aws.region shouldBe "us-west-2"
        config.aws.timeout shouldBe 60.seconds
        config.aws.retryAttempts shouldBe 5
      }

      "pass validation with non-empty region" in {
        noException should be thrownBy {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.validAwsConfig)
        }
      }
    }

    "loading valid Azure provider config" should {

      "parse all fields correctly" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.validAzureConfig
        )

        config.provider shouldBe "azure"
        config.topicDirectiveFileName shouldBe "topic-directive.yaml"
      }

      "create AzureBlockStorageConfig with correct values" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.validAzureConfig
        )

        config.azure.storageAccountName shouldBe "prodstorageaccount"
        config.azure.storageAccountKey shouldBe "realkey456"
        config.azure.timeout shouldBe 45.seconds
        config.azure.retryAttempts shouldBe 4
      }

      "pass validation with non-empty account name and key" in {
        noException should be thrownBy {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.validAzureConfig)
        }
      }
    }

    "loading valid GCP provider config" should {

      "parse all fields correctly" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.validGcpConfig
        )

        config.provider shouldBe "gcp"
        config.topicDirectiveFileName shouldBe "topic-directive.yaml"
      }

      "create GcpBlockStorageConfig with correct values" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.validGcpConfig
        )

        config.gcp.projectId shouldBe "my-gcp-project"
        config.gcp.serviceAccountKey shouldBe Some("/path/to/key.json")
        config.gcp.timeout shouldBe 40.seconds
        config.gcp.retryAttempts shouldBe 2
      }

      "pass validation with non-empty project ID" in {
        noException should be thrownBy {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.validGcpConfig)
        }
      }

      "handle optional service account key" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.gcpConfigWithoutServiceAccountKey
        )

        config.gcp.serviceAccountKey shouldBe None
      }
    }
  }

  // ========== PROVIDER VALIDATION TESTS ==========

  "BlockStorageConfig validation" when {

    "provider is invalid" should {

      "throw IllegalArgumentException with descriptive message" in {
        val exception = intercept[IllegalArgumentException] {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.invalidProviderConfig)
        }

        exception.getMessage should include("Invalid block storage provider: s3")
        exception.getMessage should include("Must be one of: local, aws, azure, gcp")
      }
    }

    "topic directive file name is empty" should {

      "throw IllegalArgumentException" in {
        val exception = intercept[IllegalArgumentException] {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.emptyTopicDirectiveFileNameConfig)
        }

        exception.getMessage should include("Topic directive file name cannot be empty")
      }
    }
  }

  // ========== PROVIDER-SPECIFIC VALIDATION TESTS ==========

  "BlockStorageConfig provider-specific validation" when {

    "provider is AWS" should {

      "require non-empty region" in {
        val exception = intercept[IllegalArgumentException] {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.missingAwsRegionConfig)
        }

        exception.getMessage should include("AWS region cannot be empty when provider is 'aws'")
      }
    }

    "provider is Azure" should {

      "require non-empty storage account name" in {
        val exception = intercept[IllegalArgumentException] {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.missingAzureAccountNameConfig)
        }

        exception.getMessage should include("Azure storage account name cannot be empty when provider is 'azure'")
      }

      "require non-empty storage account key" in {
        val exception = intercept[IllegalArgumentException] {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.missingAzureAccountKeyConfig)
        }

        exception.getMessage should include("Azure storage account key cannot be empty when provider is 'azure'")
      }
    }

    "provider is GCP" should {

      "require non-empty project ID" in {
        val exception = intercept[IllegalArgumentException] {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.missingGcpProjectIdConfig)
        }

        exception.getMessage should include("GCP project ID cannot be empty when provider is 'gcp'")
      }
    }

    "provider is local" should {

      "not validate AWS/Azure/GCP fields" in {
        // Local provider should not validate other providers' required fields
        noException should be thrownBy {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.validLocalConfig)
        }
      }
    }
  }

  // ========== RETRY ATTEMPTS VALIDATION TESTS ==========

  "BlockStorageConfig retry attempts validation" when {

    "AWS retry attempts is zero" should {

      "throw IllegalArgumentException" in {
        val exception = intercept[IllegalArgumentException] {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.zeroRetryAttemptsConfig)
        }

        exception.getMessage should include("AWS retry attempts must be positive")
        exception.getMessage should include("got: 0")
      }
    }

    "Azure retry attempts is negative" should {

      "throw IllegalArgumentException" in {
        val exception = intercept[IllegalArgumentException] {
          BlockStorageConfig.fromConfig(BlockStorageConfigFixtures.negativeRetryAttemptsConfig)
        }

        exception.getMessage should include("Azure retry attempts must be positive")
        exception.getMessage should include("got: -1")
      }
    }

    "all retry attempts are positive" should {

      "pass validation" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.validLocalConfig
        )

        config.aws.retryAttempts should be > 0
        config.azure.retryAttempts should be > 0
        config.gcp.retryAttempts should be > 0
      }
    }
  }

  // ========== DURATION PARSING TESTS ==========

  "BlockStorageConfig duration parsing" when {

    "parsing AWS timeout" should {

      "convert Java duration to Scala FiniteDuration" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.validAwsConfig
        )

        config.aws.timeout shouldBe a[FiniteDuration]
        config.aws.timeout shouldBe 60.seconds
      }
    }

    "parsing Azure timeout" should {

      "convert Java duration to Scala FiniteDuration" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.validAzureConfig
        )

        config.azure.timeout shouldBe a[FiniteDuration]
        config.azure.timeout shouldBe 45.seconds
      }
    }

    "parsing GCP timeout" should {

      "convert Java duration to Scala FiniteDuration" in {
        val config: BlockStorageConfig = BlockStorageConfig.fromConfig(
          BlockStorageConfigFixtures.validGcpConfig
        )

        config.gcp.timeout shouldBe a[FiniteDuration]
        config.gcp.timeout shouldBe 40.seconds
      }
    }
  }

  // ========== CASE CLASS STRUCTURE TESTS ==========

  "BlockStorageConfig case classes" when {

    "LocalBlockStorageConfig" should {

      "be instantiable with no parameters" in {
        val localConfig: LocalBlockStorageConfig = LocalBlockStorageConfig()
        localConfig should not be null
      }
    }

    "AwsBlockStorageConfig" should {

      "require all parameters" in {
        val awsConfig: AwsBlockStorageConfig = AwsBlockStorageConfig(
          region = "us-east-1",
          timeout = 30.seconds,
          retryAttempts = 3
        )

        awsConfig.region shouldBe "us-east-1"
        awsConfig.timeout shouldBe 30.seconds
        awsConfig.retryAttempts shouldBe 3
      }
    }

    "AzureBlockStorageConfig" should {

      "require all parameters" in {
        val azureConfig: AzureBlockStorageConfig = AzureBlockStorageConfig(
          storageAccountName = "test",
          storageAccountKey = "key",
          timeout = 30.seconds,
          retryAttempts = 3
        )

        azureConfig.storageAccountName shouldBe "test"
        azureConfig.storageAccountKey shouldBe "key"
        azureConfig.timeout shouldBe 30.seconds
        azureConfig.retryAttempts shouldBe 3
      }
    }

    "GcpBlockStorageConfig" should {

      "require all parameters" in {
        val gcpConfig: GcpBlockStorageConfig = GcpBlockStorageConfig(
          projectId = "test-project",
          serviceAccountKey = Some("/path/to/key.json"),
          timeout = 30.seconds,
          retryAttempts = 3
        )

        gcpConfig.projectId shouldBe "test-project"
        gcpConfig.serviceAccountKey shouldBe Some("/path/to/key.json")
        gcpConfig.timeout shouldBe 30.seconds
        gcpConfig.retryAttempts shouldBe 3
      }

      "support None for optional service account key" in {
        val gcpConfig: GcpBlockStorageConfig = GcpBlockStorageConfig(
          projectId = "test-project",
          serviceAccountKey = None,
          timeout = 30.seconds,
          retryAttempts = 3
        )

        gcpConfig.serviceAccountKey shouldBe None
      }
    }
  }
}
