package io.distia.probe
package core
package services
package cucumber

import io.cucumber.core.feature.FeatureParser
import io.cucumber.core.gherkin.{Feature, FeatureParserException}
import io.cucumber.core.runtime.FeatureSupplier
import java.nio.file.{Files, Path}
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.jdk.StreamConverters.*
import scala.util.{Failure, Success, Try}

/**
 * Custom FeatureSupplier that loads features from jimfs
 *
 * Replaces default FeaturePathFeatureSupplier (which uses PathScanner).
 * Discovers .feature files in jimfs directories and parses them into Features.
 *
 * Discovery Strategy:
 * 1. For each feature path (from CucumberConfiguration):
 *    - If directory: Walk tree recursively to find .feature files
 *    - If file: Use directly if ends with .feature
 * 2. Wrap each Path as JimfsResource
 * 3. Parse using Cucumber's FeatureParser
 * 4. Return java.util.List[Feature] to Cucumber runtime
 *
 * Integration:
 * Injected into Runtime.Builder via withFeatureSupplier() to bypass PathScanner.
 *
 * Thread Safety:
 * Stateless and thread-safe. All data passed in constructor is immutable.
 *
 * @param featurePaths jimfs Paths to feature files or directories containing .feature files
 */
private[cucumber] class JimfsFeatureSupplier(
  featurePaths: Seq[Path]
) extends FeatureSupplier:

  override def get(): java.util.List[Feature] =
    val parser: FeatureParser = new FeatureParser(() => UUID.randomUUID())
    val allFeatures: List[Feature] = featurePaths.flatMap { path =>
      discoverFeatureFiles(path).flatMap { featurePath =>
        parseFeatureFile(featurePath, parser)
      }
    }.toList
    allFeatures.asJava

  def discoverFeatureFiles(path: Path): List[Path] =
    if Files.isDirectory(path) then Files
      .walk(path)
      .toScala(List)
      .filter(p => Files.isRegularFile(p))
      .filter(p => p.toString.endsWith(".feature"))
    else if Files.isRegularFile(path) && path.toString.endsWith(".feature") then List(path)
    else
      Nil

  def parseFeatureFile(featurePath: Path, parser: FeatureParser): Option[Feature] = Try {
    val resource: JimfsResource = new JimfsResource(featurePath)
    parser.parseResource(resource).toScala
  } match {
    case Success(feature) => feature
    case Failure(_: FeatureParserException) => None
    case Failure(ex) => throw ex
  }
