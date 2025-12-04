package io.distia.probe
package core
package config

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

private[core] class CoreConfigValidatorSpec extends AnyWordSpec with Matchers {

  private def createMinimalValidConfig(): Config = {
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

  private def configWithField(fieldPath: String, value: String): Config = {
    val baseConfig: Config = createMinimalValidConfig()
    val configOverride: Config = ConfigFactory.parseString(s"""
      $fieldPath = $value
    """)
    configOverride.withFallback(baseConfig).resolve()
  }

  "CoreConfigValidator.validate" should {

    "return ValidationSuccess for valid complete configuration" in {
      val config: Config = createMinimalValidConfig()
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result shouldBe a[ValidationSuccess]
      result.isValid shouldBe true
      result.errors shouldBe empty
      result.warnings shouldBe empty
    }

    "return ValidationFailure when root path is missing" in {
      val config: Config = ConfigFactory.parseString("""
        some-other-config {
          value = "test"
        }
      """)

      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      result.errors should have size 1
      result.errors.head.path shouldBe "test-probe.core"
      result.errors.head.message should include("Root configuration path not found")
      result.errors.head.severity shouldBe Error
    }

    "accumulate multiple validation errors" in {
      val config: Config = ConfigFactory.parseString("""
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

      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      result.errors.size should be > 3
    }

    "convert ValidationSuccess to Right(warnings)" in {
      val result: ValidationSuccess = ValidationSuccess(List.empty)
      result.toEither shouldBe Right(List.empty)
    }

    "convert ValidationFailure to Left(errors)" in {
      val errors: List[ValidationIssue] = List(ValidationIssue(Error, "test", "message", "suggestion"))
      val result: ValidationFailure = ValidationFailure(errors, List.empty)
      result.toEither shouldBe Left(errors)
    }

    "collect warnings without failing validation" in {
      val config: Config = configWithField("test-probe.core.supervision.max-restarts", "200")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe true
      result.errors shouldBe empty
      result.warnings should not be empty
      result.warnings.head.severity shouldBe Warning
      result.warnings.head.path should include("max-restarts")
    }
  }

  "CoreConfigValidator actor-system section validation" should {

    "accept valid name" in {
      val config: Config = configWithField("test-probe.core.actor-system.name", "\"my-system\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject empty name" in {
      val config: Config = configWithField("test-probe.core.actor-system.name", "\"\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("actor-system.name"))
      error shouldBe defined
      error.get.message should include("cannot be empty")
      error.get.severity shouldBe Error
    }

    "accept valid timeout" in {
      val config: Config = configWithField("test-probe.core.actor-system.timeout", "30s")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject timeout below minimum (1s)" in {
      val config: Config = configWithField("test-probe.core.actor-system.timeout", "500ms")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("actor-system.timeout"))
      error shouldBe defined
      error.get.message should include("at least")
      error.get.severity shouldBe Error
    }

    "warn when timeout exceeds recommended maximum (300s)" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            timeout = 500s
            max-execution-time = 600s
          }
        }
      """).withFallback(createMinimalValidConfig())
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe true
      result.errors shouldBe empty
      val warning: Option[ValidationIssue] = result.warnings.find(_.path.contains("actor-system.timeout"))
      warning shouldBe defined
      warning.get.message should include("recommended maximum")
      warning.get.severity shouldBe Warning
    }

    "accept valid pool-size" in {
      val config: Config = configWithField("test-probe.core.actor-system.pool-size", "10")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject pool-size below minimum (1)" in {
      val config: Config = configWithField("test-probe.core.actor-system.pool-size", "0")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("pool-size"))
      error shouldBe defined
      error.get.message should include("at least")
      error.get.severity shouldBe Error
    }

    "warn when pool-size exceeds recommended maximum (50)" in {
      val config: Config = configWithField("test-probe.core.actor-system.pool-size", "100")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe true
      result.errors shouldBe empty
      val warning: Option[ValidationIssue] = result.warnings.find(_.path.contains("pool-size"))
      warning shouldBe defined
      warning.get.message should include("recommended maximum")
      warning.get.severity shouldBe Warning
    }

    "warn when max-execution-time exceeds recommended maximum (3600s)" in {
      val config: Config = configWithField("test-probe.core.actor-system.max-execution-time", "4000s")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe true
      result.errors shouldBe empty
      val warning: Option[ValidationIssue] = result.warnings.find(_.path.contains("max-execution-time"))
      warning shouldBe defined
      warning.get.severity shouldBe Warning
    }
  }

  "CoreConfigValidator supervision section validation" should {

    "accept valid max-restarts" in {
      val config: Config = configWithField("test-probe.core.supervision.max-restarts", "10")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject max-restarts below minimum (1)" in {
      val config: Config = configWithField("test-probe.core.supervision.max-restarts", "0")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("max-restarts"))
      error shouldBe defined
      error.get.severity shouldBe Error
    }

    "warn when max-restarts exceeds recommended maximum (100)" in {
      val config: Config = configWithField("test-probe.core.supervision.max-restarts", "200")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe true
      result.errors shouldBe empty
      val warning: Option[ValidationIssue] = result.warnings.find(_.path.contains("max-restarts"))
      warning shouldBe defined
      warning.get.message should include("recommended maximum")
      warning.get.severity shouldBe Warning
    }

    "accept valid restart-time-range" in {
      val config: Config = configWithField("test-probe.core.supervision.restart-time-range", "60s")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject restart-time-range below minimum (1s)" in {
      val config: Config = configWithField("test-probe.core.supervision.restart-time-range", "500ms")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("restart-time-range"))
      error shouldBe defined
      error.get.severity shouldBe Error
    }

    "warn when restart-time-range exceeds recommended maximum (3600s)" in {
      val config: Config = configWithField("test-probe.core.supervision.restart-time-range", "4000s")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe true
      result.errors shouldBe empty
      val warning: Option[ValidationIssue] = result.warnings.find(_.path.contains("restart-time-range"))
      warning shouldBe defined
      warning.get.severity shouldBe Warning
    }
  }

  "CoreConfigValidator test-execution section validation" should {

    "accept valid max-retries" in {
      val config: Config = configWithField("test-probe.core.test-execution.max-retries", "3")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "accept max-retries of 0 (no retries)" in {
      val config: Config = configWithField("test-probe.core.test-execution.max-retries", "0")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "warn when max-retries exceeds recommended maximum (10)" in {
      val config: Config = configWithField("test-probe.core.test-execution.max-retries", "20")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe true
      result.errors shouldBe empty
      val warning: Option[ValidationIssue] = result.warnings.find(_.path.contains("max-retries"))
      warning shouldBe defined
      warning.get.severity shouldBe Warning
    }

    "accept valid cleanup-delay" in {
      val config: Config = configWithField("test-probe.core.test-execution.cleanup-delay", "60s")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "accept cleanup-delay of 0s (immediate cleanup)" in {
      val config: Config = configWithField("test-probe.core.test-execution.cleanup-delay", "0s")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "warn when cleanup-delay exceeds recommended maximum (600s)" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            max-execution-time = 900s
          }
          test-execution {
            cleanup-delay = 800s
          }
        }
      """).withFallback(createMinimalValidConfig())
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe true
      result.errors shouldBe empty
      val warning: Option[ValidationIssue] = result.warnings.find(_.path.contains("cleanup-delay"))
      warning shouldBe defined
      warning.get.severity shouldBe Warning
    }

    "accept valid stash-buffer-size" in {
      val config: Config = configWithField("test-probe.core.test-execution.stash-buffer-size", "100")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject stash-buffer-size below minimum (1)" in {
      val config: Config = configWithField("test-probe.core.test-execution.stash-buffer-size", "0")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("stash-buffer-size"))
      error shouldBe defined
      error.get.severity shouldBe Error
    }

    "reject stash-buffer-size above hard maximum (10000) for memory safety" in {
      val config: Config = configWithField("test-probe.core.test-execution.stash-buffer-size", "20000")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("stash-buffer-size"))
      error shouldBe defined
      error.get.message should include("memory safety")
      error.get.severity shouldBe Error
    }
  }

  "CoreConfigValidator cucumber section validation" should {

    "accept valid glue-packages string" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          cucumber {
            glue-packages = "io.distia.probe.core.glue"
          }
        }
      """).withFallback(createMinimalValidConfig())

      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject empty glue-packages string" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          cucumber {
            glue-packages = ""
          }
        }
      """).withFallback(createMinimalValidConfig())

      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("glue-packages"))
      error shouldBe defined
      error.get.message should include("cannot be empty")
      error.get.severity shouldBe Error
    }

    "reject missing glue-packages" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            name = "test"
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
          cucumber {
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
              client-scope = "kafka.read"
            }
          }
        }
      """)

      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("glue-packages"))
      error shouldBe defined
      error.get.severity shouldBe Error
    }
  }

  "CoreConfigValidator services section validation" should {

    "accept valid services timeout" in {
      val config: Config = configWithField("test-probe.core.services.timeout", "30s")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject services timeout below minimum (1s)" in {
      val config: Config = configWithField("test-probe.core.services.timeout", "500ms")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("services.timeout"))
      error shouldBe defined
      error.get.severity shouldBe Error
    }

    "warn when services timeout exceeds recommended maximum (300s)" in {
      val config: Config = configWithField("test-probe.core.services.timeout", "500s")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe true
      result.errors shouldBe empty
    }
  }

  "CoreConfigValidator kafka section validation" should {

    "accept valid kafka configuration" in {
      val config: Config = createMinimalValidConfig()
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "accept valid bootstrap-servers" in {
      val config: Config = configWithField("test-probe.core.kafka.bootstrap-servers", "\"kafka1:9092,kafka2:9092\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject empty bootstrap-servers" in {
      val config: Config = configWithField("test-probe.core.kafka.bootstrap-servers", "\"\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("bootstrap-servers"))
      error shouldBe defined
      error.get.message should include("cannot be empty")
      error.get.severity shouldBe Error
    }

    "accept valid schema-registry-url with http" in {
      val config: Config = configWithField("test-probe.core.kafka.schema-registry-url", "\"http://localhost:8081\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "accept valid schema-registry-url with https" in {
      val config: Config = configWithField("test-probe.core.kafka.schema-registry-url", "\"https://schema-registry.example.com\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject schema-registry-url without http/https" in {
      val config: Config = configWithField("test-probe.core.kafka.schema-registry-url", "\"localhost:8081\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("schema-registry-url"))
      error shouldBe defined
      error.get.message should include("URL")
      error.get.severity shouldBe Error
    }

    "reject empty schema-registry-url" in {
      val config: Config = configWithField("test-probe.core.kafka.schema-registry-url", "\"\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("schema-registry-url"))
      error shouldBe defined
      error.get.severity shouldBe Error
    }

    "accept valid oauth token-endpoint" in {
      val config: Config = configWithField("test-probe.core.kafka.oauth.token-endpoint", "\"https://auth.example.com/token\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject empty oauth token-endpoint" in {
      val config: Config = configWithField("test-probe.core.kafka.oauth.token-endpoint", "\"\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("token-endpoint"))
      error shouldBe defined
      error.get.severity shouldBe Error
    }

    "accept valid oauth client-scope" in {
      val config: Config = configWithField("test-probe.core.kafka.oauth.client-scope", "\"kafka.read kafka.write\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject empty oauth client-scope" in {
      val config: Config = configWithField("test-probe.core.kafka.oauth.client-scope", "\"\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("client-scope"))
      error shouldBe defined
      error.get.severity shouldBe Error
    }
  }

  "CoreConfigValidator dsl section validation" should {

    "accept valid dsl ask-timeout" in {
      val config: Config = configWithField("test-probe.core.dsl.ask-timeout", "3s")
      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject dsl ask-timeout below minimum (100ms)" in {
      val config: Config = configWithField("test-probe.core.dsl.ask-timeout", "50ms")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("dsl.ask-timeout"))
      error shouldBe defined
      error.get.message should include("at least")
      error.get.severity shouldBe Error
    }

    "warn when dsl ask-timeout exceeds recommended maximum (30s)" in {
      val config: Config = configWithField("test-probe.core.dsl.ask-timeout", "45s")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe true
      result.errors shouldBe empty
      val warning: Option[ValidationIssue] = result.warnings.find(_.path.contains("dsl.ask-timeout"))
      warning shouldBe defined
      warning.get.message should include("recommended maximum")
      warning.get.severity shouldBe Warning
    }
  }

  "CoreConfigValidator cross-field validation" should {

    "accept when max-execution-time > actor-system.timeout" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            timeout = 30s
            max-execution-time = 300s
          }
        }
      """).withFallback(createMinimalValidConfig())

      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject when max-execution-time <= actor-system.timeout" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            timeout = 30s
            max-execution-time = 25s
          }
        }
      """).withFallback(createMinimalValidConfig())

      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("max-execution-time"))
      error shouldBe defined
      error.get.message should include("greater than")
      error.get.severity shouldBe Error
    }

    "accept when cleanup-delay < max-execution-time" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            max-execution-time = 300s
          }
          test-execution {
            cleanup-delay = 60s
          }
        }
      """).withFallback(createMinimalValidConfig())

      val result: ValidationResult = CoreConfigValidator.validate(config)
      result.isValid shouldBe true
    }

    "reject when cleanup-delay >= max-execution-time" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {
          actor-system {
            max-execution-time = 60s
          }
          test-execution {
            cleanup-delay = 65s
          }
        }
      """).withFallback(createMinimalValidConfig())

      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("cleanup-delay"))
      error shouldBe defined
      error.get.message should include("less than")
      error.get.severity shouldBe Error
    }
  }

  "ValidationSuccess" should {

    "have isValid set to true" in {
      val success = ValidationSuccess()
      success.isValid shouldBe true
    }

    "have empty errors list" in {
      val success = ValidationSuccess()
      success.errors shouldBe empty
    }

    "return empty warnings by default" in {
      val success = ValidationSuccess()
      success.warnings shouldBe empty
    }

    "return provided warnings list" in {
      val warnings = List(
        ValidationIssue(Warning, "test.path", "warning message", "suggestion")
      )
      val success = ValidationSuccess(warnings)
      success.warnings shouldBe warnings
    }

    "convert to Right(warnings) via toEither" in {
      val warnings = List(
        ValidationIssue(Warning, "test.path", "warning message", "suggestion")
      )
      val success = ValidationSuccess(warnings)
      success.toEither shouldBe Right(warnings)
    }
  }

  "ValidationFailure" should {

    "have isValid set to false" in {
      val errors = List(ValidationIssue(Error, "test.path", "error message", "suggestion"))
      val failure = ValidationFailure(errors)
      failure.isValid shouldBe false
    }

    "return provided errors list" in {
      val errors = List(ValidationIssue(Error, "test.path", "error message", "suggestion"))
      val failure = ValidationFailure(errors)
      failure.errors shouldBe errors
    }

    "return empty warnings by default" in {
      val errors = List(ValidationIssue(Error, "test.path", "error message", "suggestion"))
      val failure = ValidationFailure(errors)
      failure.warnings shouldBe empty
    }

    "return provided warnings list" in {
      val errors = List(ValidationIssue(Error, "error.path", "error message", "suggestion"))
      val warnings = List(ValidationIssue(Warning, "warning.path", "warning message", "suggestion"))
      val failure = ValidationFailure(errors, warnings)
      failure.warnings shouldBe warnings
    }

    "convert to Left(errors) via toEither" in {
      val errors = List(ValidationIssue(Error, "test.path", "error message", "suggestion"))
      val warnings = List(ValidationIssue(Warning, "warn.path", "warning message", "suggestion"))
      val failure = ValidationFailure(errors, warnings)
      failure.toEither shouldBe Left(errors)
    }
  }

  "ValidationIssue" should {

    "format error message without cause" in {
      val error: ValidationIssue = ValidationIssue(
        severity = Error,
        path = "test.path",
        message = "Something went wrong",
        suggestion = "Fix it this way"
      )

      val formatted: String = error.format
      formatted should include("ERROR")
      formatted should include("test.path")
      formatted should include("Something went wrong")
      formatted should include("Fix it this way")
      formatted should not include "Cause:"
    }

    "format warning message without cause" in {
      val warning: ValidationIssue = ValidationIssue(
        severity = Warning,
        path = "test.path",
        message = "Something might be wrong",
        suggestion = "Consider fixing it this way"
      )

      val formatted: String = warning.format
      formatted should include("WARNING")
      formatted should include("test.path")
      formatted should include("Something might be wrong")
      formatted should include("Consider fixing it this way")
      formatted should not include "Cause:"
    }

    "format error message with cause" in {
      val exception: RuntimeException = new RuntimeException("Root cause")
      val error: ValidationIssue = ValidationIssue(
        severity = Error,
        path = "test.path",
        message = "Something went wrong",
        suggestion = "Fix it this way",
        cause = Some(exception)
      )

      val formatted: String = error.format
      formatted should include("ERROR")
      formatted should include("test.path")
      formatted should include("Something went wrong")
      formatted should include("Fix it this way")
      formatted should include("Cause:")
      formatted should include("Root cause")
    }
  }

  "CoreConfigValidator edge cases" should {

    "handle configuration with all missing sections gracefully" in {
      val config: Config = ConfigFactory.parseString("""
        test-probe.core {}
      """)

      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      result.errors.size should be > 10
    }

    "handle malformed duration values" in {
      val config: Config = configWithField("test-probe.core.actor-system.timeout", "\"not-a-duration\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("timeout"))
      error shouldBe defined
      error.get.severity shouldBe Error
    }

    "handle malformed integer values" in {
      val config: Config = configWithField("test-probe.core.actor-system.pool-size", "\"not-an-int\"")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("pool-size"))
      error shouldBe defined
      error.get.severity shouldBe Error
    }

    "handle null values gracefully" in {
      val config: Config = configWithField("test-probe.core.actor-system.name", "null")
      val result: ValidationResult = CoreConfigValidator.validate(config)

      result.isValid shouldBe false
      val error: Option[ValidationIssue] = result.errors.find(_.path.contains("name"))
      error shouldBe defined
      error.get.severity shouldBe Error
    }
  }
}