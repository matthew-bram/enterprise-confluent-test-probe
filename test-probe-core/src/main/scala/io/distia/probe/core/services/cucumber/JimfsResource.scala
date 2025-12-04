package io.distia.probe
package core
package services
package cucumber

import io.cucumber.core.resource.Resource
import java.io.InputStream
import java.net.URI
import java.nio.file.{Files, Path}

/**
 * Cucumber Resource implementation backed by jimfs Path
 *
 * Wraps a jimfs Path to implement Cucumber's Resource interface,
 * allowing Cucumber to load feature files from in-memory filesystem.
 *
 * URI Format: jimfs:/absolute/path/to/file.feature
 * Content: Streamed from jimfs via Files.newInputStream(path)
 *
 * Integration:
 * Used by JimfsFeatureSupplier to wrap discovered .feature files as Resources
 * for Cucumber's FeatureParser.
 *
 * Thread Safety:
 * Stateless and thread-safe. Path is immutable.
 *
 * @param path jimfs Path to feature file (must exist and be readable)
 */
private[cucumber] class JimfsResource(path: Path) extends Resource:
  
  override def getUri(): URI = URI.create(s"jimfs:${path.toAbsolutePath}")
  
  override def getInputStream(): InputStream = Files.newInputStream(path)
