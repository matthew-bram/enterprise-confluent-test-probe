package io.distia.probe
package core
package builder
package modules

import config.CoreConfig
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

object DefaultConfig {
  def apply()(implicit ec: ExecutionContext): DefaultConfig = new DefaultConfig(None)

  def withAltConfigLocation(path: String)(implicit ec: ExecutionContext): DefaultConfig =
    new DefaultConfig(Some(path))
}

class DefaultConfig(alternateConfigLocation: Option[String] = None) extends ProbeConfig {

  private val logger = LoggerFactory.getLogger(classOf[DefaultConfig])

  val config: Config = {
    val loadedConfig: Config = alternateConfigLocation match {
      case Some(location) =>
        val file: File = new File(location)
        if file.exists then
          logger.info(s"Loading configuration from file: $location")
          ConfigFactory.parseFile(file)
        else
          throw new RuntimeException(s"Could not parse config file: $location")
      case None => ConfigFactory.empty()
    }

    val merged = loadedConfig
      .withFallback(ConfigFactory.load())  // Auto-merges all reference.conf files from classpath
      .resolve()

    logConfigSources(merged)
    merged
  }

  private def logConfigSources(config: Config): Unit =
    if config.hasPath("test-probe.core") then
      logger.info("✓ Core module configuration loaded (test-probe.core.*)")
    if config.hasPath("test-probe.interfaces") then
      logger.info("✓ Interfaces module configuration loaded (test-probe.interfaces.*)")
    if !config.hasPath("test-probe.core") && !config.hasPath("test-probe.interfaces") then
      logger.warn("⚠ No test-probe configuration found - using defaults")

  override def validate(implicit ec: ExecutionContext): Future[CoreConfig] = Future(CoreConfig.fromConfig(config))

  override def preFlight(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
    validate.map(_ => ctx)

  override def initialize(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] =
    validate.map { sc => ctx.withConfig(config, sc) }

  override def finalCheck(ctx: BuilderContext)(implicit ec: ExecutionContext): Future[BuilderContext] = Future {
    require(ctx.config.isDefined && ctx.coreConfig.isDefined,
      "Config not initialized in BuilderContext")
    ctx
  }
}
