package io.distia.probe
package core
package config

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

private[core] class CoreConfigSpec extends AnyWordSpec with Matchers {

  private def createValidConfig(): Config = {
    ConfigFactory.parseString("""
      test-probe.core {
        actor-system {
          name = "test-probe"
          timeout = 25s
          shutdown-timeout = 45s
          initialization-timeout = 30s
          pool-size = 3
          max-execution-time = 300s
        }

        supervision {
          max-restarts = 10
          restart-time-range = 60s
        }

        test-execution {
          max-retries = 3
          cleanup-delay = 60s
          stash-buffer-size = 100
        }

        timers {
          setup-state = 30s
          loading-state = 60s
          completed-state = 10s
          exception-state = 10s
        }

        cucumber {
          glue-packages = "io.distia.probe.core.glue"
        }

        services.timeout = 30s

        dsl {
          ask-timeout = 3s
        }

        kafka {
          bootstrap-servers = "localhost:9092"
          schema-registry-url = "http://localhost:8081"
          oauth {
            token-endpoint = "http://localhost:8080/oauth/token"
            client-scope = "kafka.read kafka.write"
          }
        }
      }
    """)
  }

  private def createInvalidConfig(): Config = {
    ConfigFactory.parseString("""
      test-probe.core {
        actor-system {
          name = ""
          timeout = 500ms
          shutdown-timeout = 10ms
          initialization-timeout = 60s
          pool-size = 0
          max-execution-time = 5s
        }
      }
    """)
  }

  private def createConfigWithHighRestarts(): Config = {
    ConfigFactory.parseString("""
      test-probe.core {
        supervision {
          max-restarts = 200
        }
      }
    """).withFallback(createValidConfig())
  }

  "CoreConfig.fromConfig" should {

    "successfully load valid configuration" in {
      val config: Config = createValidConfig()
      val coreConfig: CoreConfig = CoreConfig.fromConfig(config)

      coreConfig.actorSystemName shouldBe "test-probe"
      coreConfig.actorSystemTimeout shouldBe 25.seconds
      coreConfig.shutdownTimeout shouldBe 45.seconds
      coreConfig.initializationTimeout shouldBe 30.seconds
      coreConfig.poolSize shouldBe 3
      coreConfig.maxExecutionTime shouldBe 300.seconds

      coreConfig.maxRestarts shouldBe 10
      coreConfig.restartTimeRange shouldBe 60.seconds

      coreConfig.maxRetries shouldBe 3
      coreConfig.cleanupDelay shouldBe 60.seconds
      coreConfig.stashBufferSize shouldBe 100

      coreConfig.setupStateTimeout shouldBe 30.seconds
      coreConfig.loadingStateTimeout shouldBe 60.seconds
      coreConfig.completedStateTimeout shouldBe 10.seconds
      coreConfig.exceptionStateTimeout shouldBe 10.seconds

      coreConfig.gluePackage shouldBe "io.distia.probe.core.glue"
      coreConfig.storageTimeout shouldBe 30.seconds
      coreConfig.dslAskTimeout shouldBe 3.seconds

      coreConfig.kafka.bootstrapServers shouldBe "localhost:9092"
      coreConfig.kafka.schemaRegistryUrl shouldBe "http://localhost:8081"
      coreConfig.kafka.oauth.tokenEndpoint shouldBe "http://localhost:8080/oauth/token"
      coreConfig.kafka.oauth.clientScope shouldBe "kafka.read kafka.write"
    }

    "throw ConfigurationException for invalid config (empty name)" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            name = ""
            timeout = 25s
            shutdown-timeout = 45s
            initialization-timeout = 30s
            pool-size = 3
            max-execution-time = 300s
          }
        }
      """).withFallback(createValidConfig())

      val exception: ConfigurationException = intercept[ConfigurationException] {
        CoreConfig.fromConfig(config)
      }

      exception.getMessage should include("Configuration validation failed")
      exception.getMessage should include("actor-system.name")
    }

    "throw ConfigurationException for invalid config (pool-size = 0)" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            pool-size = 0
          }
        }
      """).withFallback(createValidConfig())

      val exception: ConfigurationException = intercept[ConfigurationException] {
        CoreConfig.fromConfig(config)
      }

      exception.getMessage should include("Configuration validation failed")
      exception.getMessage should include("pool-size")
    }

    "throw ConfigurationException for invalid config (timeout too short)" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            timeout = 500ms
          }
        }
      """).withFallback(createValidConfig())

      val exception: ConfigurationException = intercept[ConfigurationException] {
        CoreConfig.fromConfig(config)
      }

      exception.getMessage should include("Configuration validation failed")
    }

    "throw ConfigurationException for invalid config (stash-buffer-size too large)" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          test-execution {
            stash-buffer-size = 20000
          }
        }
      """).withFallback(createValidConfig())

      val exception: ConfigurationException = intercept[ConfigurationException] {
        CoreConfig.fromConfig(config)
      }

      exception.getMessage should include("Configuration validation failed")
      exception.getMessage should include("stash-buffer-size")
    }

    "throw ConfigurationException for missing required field" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            timeout = 25s
            shutdown-timeout = 45s
            initialization-timeout = 30s
            pool-size = 3
            max-execution-time = 300s
          }
        }
      """)

      val exception: ConfigurationException = intercept[ConfigurationException] {
        CoreConfig.fromConfig(config)
      }

      exception.getMessage should include("Configuration validation failed")
    }

    "succeed but log warnings for values exceeding recommended limits" in {
      val config: Config = createConfigWithHighRestarts()

      noException should be thrownBy {
        val coreConfig: CoreConfig = CoreConfig.fromConfig(config)
        coreConfig.maxRestarts shouldBe 200
      }
    }

    "throw ConfigurationException for logically impossible config (max-execution-time <= timeout)" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            timeout = 30s
            max-execution-time = 25s
          }
        }
      """).withFallback(createValidConfig())

      val exception: ConfigurationException = intercept[ConfigurationException] {
        CoreConfig.fromConfig(config)
      }

      exception.getMessage should include("Configuration validation failed")
      exception.getMessage should include("max-execution-time")
    }

    "throw ConfigurationException for logically impossible config (cleanup-delay >= max-execution-time)" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            max-execution-time = 60s
          }
          test-execution {
            cleanup-delay = 65s
          }
        }
      """).withFallback(createValidConfig())

      val exception: ConfigurationException = intercept[ConfigurationException] {
        CoreConfig.fromConfig(config)
      }

      exception.getMessage should include("Configuration validation failed")
      exception.getMessage should include("cleanup-delay")
    }

    "call CoreConfigValidator.validate (development guard)" in {
      val config: Config = createValidConfig()

      noException should be thrownBy {
        CoreConfig.fromConfig(config)
      }

      val invalidConfig: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            name = ""
          }
        }
      """).withFallback(createValidConfig())

      intercept[ConfigurationException] {
        CoreConfig.fromConfig(invalidConfig)
      }
    }

    "parse all actor system configuration correctly" in {
      val config: Config = createValidConfig()
      val coreConfig: CoreConfig = CoreConfig.fromConfig(config)

      coreConfig.actorSystemName shouldBe "test-probe"
      coreConfig.actorSystemTimeout.toSeconds shouldBe 25
      coreConfig.shutdownTimeout.toSeconds shouldBe 45
      coreConfig.initializationTimeout.toSeconds shouldBe 30
      coreConfig.poolSize shouldBe 3
      coreConfig.maxExecutionTime.toSeconds shouldBe 300
    }

    "parse all supervision configuration correctly" in {
      val config: Config = createValidConfig()
      val coreConfig: CoreConfig = CoreConfig.fromConfig(config)

      coreConfig.maxRestarts shouldBe 10
      coreConfig.restartTimeRange.toSeconds shouldBe 60
    }

    "parse all test execution configuration correctly" in {
      val config: Config = createValidConfig()
      val coreConfig: CoreConfig = CoreConfig.fromConfig(config)

      coreConfig.maxRetries shouldBe 3
      coreConfig.cleanupDelay.toSeconds shouldBe 60
      coreConfig.stashBufferSize shouldBe 100
    }

    "parse all state timer configuration correctly" in {
      val config: Config = createValidConfig()
      val coreConfig: CoreConfig = CoreConfig.fromConfig(config)

      coreConfig.setupStateTimeout.toSeconds shouldBe 30
      coreConfig.loadingStateTimeout.toSeconds shouldBe 60
      coreConfig.completedStateTimeout.toSeconds shouldBe 10
      coreConfig.exceptionStateTimeout.toSeconds shouldBe 10
    }

    "parse cucumber configuration correctly" in {
      val config: Config = createValidConfig()
      val coreConfig: CoreConfig = CoreConfig.fromConfig(config)

      coreConfig.gluePackage shouldBe "io.distia.probe.core.glue"
    }

    "parse storage configuration correctly" in {
      val config: Config = createValidConfig()
      val coreConfig: CoreConfig = CoreConfig.fromConfig(config)

      coreConfig.storageTimeout.toSeconds shouldBe 30
    }

    "parse kafka configuration correctly" in {
      val config: Config = createValidConfig()
      val coreConfig: CoreConfig = CoreConfig.fromConfig(config)

      coreConfig.kafka.bootstrapServers shouldBe "localhost:9092"
      coreConfig.kafka.schemaRegistryUrl shouldBe "http://localhost:8081"
      coreConfig.kafka.oauth.tokenEndpoint shouldBe "http://localhost:8080/oauth/token"
      coreConfig.kafka.oauth.clientScope shouldBe "kafka.read kafka.write"
    }

    "parse DSL configuration correctly" in {
      val config: Config = createValidConfig()
      val coreConfig: CoreConfig = CoreConfig.fromConfig(config)

      coreConfig.dslAskTimeout.toSeconds shouldBe 3
    }
  }

  "ConfigurationException" should {

    "be a RuntimeException" in {
      val ex: ConfigurationException = new ConfigurationException("test message")
      ex shouldBe a[RuntimeException]
    }

    "accept message and cause" in {
      val cause: Throwable = new RuntimeException("root cause")
      val ex: ConfigurationException = new ConfigurationException("test message", cause)

      ex.getMessage shouldBe "test message"
      ex.getCause shouldBe cause
    }

    "accept message only" in {
      val ex: ConfigurationException = new ConfigurationException("test message")

      ex.getMessage shouldBe "test message"
      ex.getCause shouldBe null
    }
  }
}