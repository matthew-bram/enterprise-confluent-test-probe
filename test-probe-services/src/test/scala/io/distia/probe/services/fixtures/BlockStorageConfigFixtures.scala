package io.distia.probe
package services
package fixtures

import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.duration.*

/**
 * Test fixtures for BlockStorageConfig testing
 * Provides factory methods for creating various config scenarios
 */
private[services] object BlockStorageConfigFixtures {

  /**
   * Valid local provider configuration
   */
  def validLocalConfig: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "local"
      topic-directive-file-name = "topic-directive.yaml"

      aws {
        region = "us-east-1"
        timeout = 30s
        retry-attempts = 3
      }

      azure {
        storage-account-name = "teststorage"
        storage-account-key = "dummykey123"
        timeout = 30s
        retry-attempts = 3
      }

      gcp {
        project-id = "test-project"
        service-account-key = ""
        timeout = 30s
        retry-attempts = 3
      }
    }
  """)

  /**
   * Valid AWS provider configuration
   */
  def validAwsConfig: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "aws"
      topic-directive-file-name = "topic-directive.yaml"

      aws {
        region = "us-west-2"
        timeout = 60s
        retry-attempts = 5
      }

      azure {
        storage-account-name = "teststorage"
        storage-account-key = "dummykey123"
        timeout = 30s
        retry-attempts = 3
      }

      gcp {
        project-id = "test-project"
        service-account-key = ""
        timeout = 30s
        retry-attempts = 3
      }
    }
  """)

  /**
   * Valid Azure provider configuration
   */
  def validAzureConfig: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "azure"
      topic-directive-file-name = "topic-directive.yaml"

      aws {
        region = "us-east-1"
        timeout = 30s
        retry-attempts = 3
      }

      azure {
        storage-account-name = "prodstorageaccount"
        storage-account-key = "realkey456"
        timeout = 45s
        retry-attempts = 4
      }

      gcp {
        project-id = "test-project"
        service-account-key = ""
        timeout = 30s
        retry-attempts = 3
      }
    }
  """)

  /**
   * Valid GCP provider configuration
   */
  def validGcpConfig: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "gcp"
      topic-directive-file-name = "topic-directive.yaml"

      aws {
        region = "us-east-1"
        timeout = 30s
        retry-attempts = 3
      }

      azure {
        storage-account-name = "teststorage"
        storage-account-key = "dummykey123"
        timeout = 30s
        retry-attempts = 3
      }

      gcp {
        project-id = "my-gcp-project"
        service-account-key = "/path/to/key.json"
        timeout = 40s
        retry-attempts = 2
      }
    }
  """)

  /**
   * Invalid provider (not one of: local, aws, azure, gcp)
   */
  def invalidProviderConfig: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "s3"
      topic-directive-file-name = "topic-directive.yaml"

      aws {
        region = "us-east-1"
        timeout = 30s
        retry-attempts = 3
      }

      azure {
        storage-account-name = "teststorage"
        storage-account-key = "dummykey123"
        timeout = 30s
        retry-attempts = 3
      }

      gcp {
        project-id = "test-project"
        service-account-key = ""
        timeout = 30s
        retry-attempts = 3
      }
    }
  """)

  /**
   * Empty topic directive file name
   */
  def emptyTopicDirectiveFileNameConfig: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "local"
      topic-directive-file-name = ""

      aws {
        region = "us-east-1"
        timeout = 30s
        retry-attempts = 3
      }

      azure {
        storage-account-name = "teststorage"
        storage-account-key = "dummykey123"
        timeout = 30s
        retry-attempts = 3
      }

      gcp {
        project-id = "test-project"
        service-account-key = ""
        timeout = 30s
        retry-attempts = 3
      }
    }
  """)

  /**
   * Missing AWS region when provider is AWS
   */
  def missingAwsRegionConfig: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "aws"
      topic-directive-file-name = "topic-directive.yaml"

      aws {
        region = ""
        timeout = 30s
        retry-attempts = 3
      }

      azure {
        storage-account-name = "teststorage"
        storage-account-key = "dummykey123"
        timeout = 30s
        retry-attempts = 3
      }

      gcp {
        project-id = "test-project"
        service-account-key = ""
        timeout = 30s
        retry-attempts = 3
      }
    }
  """)

  /**
   * Missing Azure storage account name when provider is Azure
   */
  def missingAzureAccountNameConfig: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "azure"
      topic-directive-file-name = "topic-directive.yaml"

      aws {
        region = "us-east-1"
        timeout = 30s
        retry-attempts = 3
      }

      azure {
        storage-account-name = ""
        storage-account-key = "dummykey123"
        timeout = 30s
        retry-attempts = 3
      }

      gcp {
        project-id = "test-project"
        service-account-key = ""
        timeout = 30s
        retry-attempts = 3
      }
    }
  """)

  /**
   * Missing Azure storage account key when provider is Azure
   */
  def missingAzureAccountKeyConfig: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "azure"
      topic-directive-file-name = "topic-directive.yaml"

      aws {
        region = "us-east-1"
        timeout = 30s
        retry-attempts = 3
      }

      azure {
        storage-account-name = "teststorage"
        storage-account-key = ""
        timeout = 30s
        retry-attempts = 3
      }

      gcp {
        project-id = "test-project"
        service-account-key = ""
        timeout = 30s
        retry-attempts = 3
      }
    }
  """)

  /**
   * Missing GCP project ID when provider is GCP
   */
  def missingGcpProjectIdConfig: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "gcp"
      topic-directive-file-name = "topic-directive.yaml"

      aws {
        region = "us-east-1"
        timeout = 30s
        retry-attempts = 3
      }

      azure {
        storage-account-name = "teststorage"
        storage-account-key = "dummykey123"
        timeout = 30s
        retry-attempts = 3
      }

      gcp {
        project-id = ""
        service-account-key = ""
        timeout = 30s
        retry-attempts = 3
      }
    }
  """)

  /**
   * Invalid retry attempts (zero)
   */
  def zeroRetryAttemptsConfig: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "local"
      topic-directive-file-name = "topic-directive.yaml"

      aws {
        region = "us-east-1"
        timeout = 30s
        retry-attempts = 0
      }

      azure {
        storage-account-name = "teststorage"
        storage-account-key = "dummykey123"
        timeout = 30s
        retry-attempts = 3
      }

      gcp {
        project-id = "test-project"
        service-account-key = ""
        timeout = 30s
        retry-attempts = 3
      }
    }
  """)

  /**
   * Invalid retry attempts (negative)
   */
  def negativeRetryAttemptsConfig: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "local"
      topic-directive-file-name = "topic-directive.yaml"

      aws {
        region = "us-east-1"
        timeout = 30s
        retry-attempts = 3
      }

      azure {
        storage-account-name = "teststorage"
        storage-account-key = "dummykey123"
        timeout = 30s
        retry-attempts = -1
      }

      gcp {
        project-id = "test-project"
        service-account-key = ""
        timeout = 30s
        retry-attempts = 3
      }
    }
  """)

  /**
   * GCP config without service account key (optional field)
   */
  def gcpConfigWithoutServiceAccountKey: Config = ConfigFactory.parseString("""
    test-probe.services.block-storage {
      provider = "gcp"
      topic-directive-file-name = "topic-directive.yaml"

      aws {
        region = "us-east-1"
        timeout = 30s
        retry-attempts = 3
      }

      azure {
        storage-account-name = "teststorage"
        storage-account-key = "dummykey123"
        timeout = 30s
        retry-attempts = 3
      }

      gcp {
        project-id = "my-gcp-project"
        timeout = 40s
        retry-attempts = 2
      }
    }
  """)
}
