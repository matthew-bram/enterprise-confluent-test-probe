package io.distia.probe
package services
package fixtures

import com.typesafe.config.{Config, ConfigFactory}

private[services] object VaultConfigFixtures {

  def defaultLocalVaultConfig: Config = ConfigFactory.parseString(
    """
      |test-probe {
      |  services {
      |    vault {
      |      provider = "local"
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

  def defaultAwsVaultConfig: Config = ConfigFactory.parseString(
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
      |        region = "us-east-1"
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
      |      rosetta-mapping-path = "classpath:rosetta/aws-vault-mapping.yaml"
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def defaultAzureVaultConfig: Config = ConfigFactory.parseString(
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
      |      rosetta-mapping-path = "classpath:rosetta/azure-vault-mapping.yaml"
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def defaultGcpVaultConfig: Config = ConfigFactory.parseString(
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
      |        service-account-key = "/path/to/service-account-key.json"
      |        timeout = 30s
      |        retry-attempts = 3
      |        required-fields = ["clientId", "clientSecret"]
      |      }
      |      oauth {
      |        token-endpoint = "https://oauth.example.com/token"
      |        scope = "kafka:read kafka:write"
      |      }
      |      rosetta-mapping-path = "classpath:rosetta/gcp-vault-mapping.yaml"
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def gcpVaultConfigWithoutServiceAccountKey: Config = ConfigFactory.parseString(
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
      |        retry-attempts = 3
      |        required-fields = ["clientId", "clientSecret"]
      |      }
      |      oauth {
      |        token-endpoint = "https://oauth.example.com/token"
      |        scope = "kafka:read kafka:write"
      |      }
      |      rosetta-mapping-path = "classpath:rosetta/gcp-vault-mapping.yaml"
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def invalidProviderConfig: Config = ConfigFactory.parseString(
    """
      |test-probe {
      |  services {
      |    vault {
      |      provider = "invalid-provider"
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

  def awsVaultConfigWithMissingLambdaArn: Config = ConfigFactory.parseString(
    """
      |test-probe {
      |  services {
      |    vault {
      |      provider = "aws"
      |      local {
      |        required-fields = ["clientId", "clientSecret"]
      |      }
      |      aws {
      |        lambda-arn = ""
      |        region = "us-east-1"
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

  def awsVaultConfigWithZeroRetries: Config = ConfigFactory.parseString(
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
      |        region = "us-east-1"
      |        timeout = 30s
      |        retry-attempts = 0
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

  def awsVaultConfigWithMissingRegion: Config = ConfigFactory.parseString(
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
      |      rosetta-mapping-path = "classpath:rosetta/aws-vault-mapping.yaml"
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def azureVaultConfigWithMissingFunctionUrl: Config = ConfigFactory.parseString(
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
      |      rosetta-mapping-path = "classpath:rosetta/azure-vault-mapping.yaml"
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def azureVaultConfigWithMissingFunctionKey: Config = ConfigFactory.parseString(
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
      |      rosetta-mapping-path = "classpath:rosetta/azure-vault-mapping.yaml"
      |    }
      |  }
      |}
      |""".stripMargin
  )

  def gcpVaultConfigWithMissingFunctionUrl: Config = ConfigFactory.parseString(
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
      |      rosetta-mapping-path = "classpath:rosetta/gcp-vault-mapping.yaml"
      |    }
      |  }
      |}
      |""".stripMargin
  )
}
