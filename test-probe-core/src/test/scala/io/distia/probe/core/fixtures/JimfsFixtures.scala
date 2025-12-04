package io.distia.probe.core.fixtures

import com.google.common.jimfs.{Configuration, Jimfs}
import java.nio.file.FileSystem

/**
 * JIMFS (in-memory filesystem) test fixtures.
 *
 * Provides:
 * - Jimfs FileSystem creation (Unix, Windows, macOS configs)
 * - Auto-closing utilities (withJimfs)
 * - BlockStorageDirective creation with Jimfs paths
 *
 * Usage:
 * {{{
 *   class MySpec extends AnyWordSpec with JimfsFixtures {
 *     "test" should {
 *       "use jimfs" in withJimfs { jimfs =>
 *         // jimfs auto-closed after block
 *       }
 *     }
 *   }
 * }}}
 *
 * Thread Safety: Each FileSystem instance is independent - thread-safe.
 */
trait JimfsFixtures:

  /**
   * Create in-memory filesystem (JIMFS) with Unix configuration.
   *
   * JIMFS is a fast, in-memory file system that behaves like a real file system.
   * Perfect for testing file operations without touching disk.
   *
   * Important: Always close the filesystem after use to avoid memory leaks:
   * {{{
   *   val fs = createJimfs()
   *   try {
   *     // ... use fs
   *   } finally {
   *     fs.close()
   *   }
   * }}}
   *
   * @return FileSystem instance (JIMFS)
   */
  def createJimfs(): FileSystem =
    Jimfs.newFileSystem(Configuration.unix())

  /**
   * Create JIMFS with custom configuration.
   *
   * @param config JIMFS configuration
   * @return FileSystem instance
   */
  def createJimfs(config: Configuration): FileSystem =
    Jimfs.newFileSystem(config)

  /**
   * Create JIMFS with Windows-style configuration.
   *
   * Useful for testing Windows path handling.
   *
   * @return FileSystem instance (Windows-style)
   */
  def createJimfsWindows(): FileSystem =
    Jimfs.newFileSystem(Configuration.windows())

  /**
   * Create JIMFS with macOS-style configuration.
   *
   * @return FileSystem instance (macOS-style)
   */
  def createJimfsMacOs(): FileSystem =
    Jimfs.newFileSystem(Configuration.osX())

  /**
   * Execute code with JIMFS and auto-close.
   *
   * Automatically creates and closes JIMFS filesystem.
   *
   * @param block Code to execute with filesystem
   * @tparam T Return type
   * @return Result from block
   */
  def withJimfs[T](block: FileSystem => T): T =
    val fs = createJimfs()
    try
      block(fs)
    finally
      fs.close()

  /**
   * Execute code with custom JIMFS config and auto-close.
   *
   * @param config JIMFS configuration
   * @param block Code to execute with filesystem
   * @tparam T Return type
   * @return Result from block
   */
  def withJimfs[T](config: Configuration)(block: FileSystem => T): T =
    val fs = createJimfs(config)
    try
      block(fs)
    finally
      fs.close()

  /**
   * Create BlockStorageDirective with Jimfs-based paths and feature file.
   *
   * Copies feature file from classpath to Jimfs, creates evidence directory,
   * and returns BlockStorageDirective with Jimfs paths.
   *
   * Usage in Cucumber steps with Before/After hooks:
   * {{{
   *   var jimfs: Option[FileSystem] = None
   *
   *   Before {
   *     jimfs = Some(createJimfs())
   *   }
   *
   *   After {
   *     jimfs.foreach(_.close())
   *   }
   *
   *   // In step:
   *   val directive = jimfs.flatMap { fs =>
   *     createJimfsBlockStorageDirective(
   *       fs,
   *       "/stubs/bdd-framework-minimal-valid-test-stub.feature",
   *       "/features/test.feature",
   *       s"/evidence-${world.testId}"
   *     )
   *   }
   * }}}
   *
   * @param jimfs Jimfs filesystem instance
   * @param featureResource Classpath resource (e.g., "/stubs/test.feature")
   * @param featurePath Target path in Jimfs (e.g., "/features/test.feature")
   * @param evidencePath Evidence directory in Jimfs (e.g., "/evidence-123")
   * @param topicDirectives Topic directives (default: empty)
   * @return BlockStorageDirective with Jimfs paths, or None if resource not found
   */
  def createJimfsBlockStorageDirective(
    jimfs: FileSystem,
    featureResource: String,
    featurePath: String,
    evidencePath: String,
    topicDirectives: List[io.distia.probe.common.models.TopicDirective] = List.empty
  ): Option[io.distia.probe.common.models.BlockStorageDirective] =
    import scala.io.Source
    import java.nio.file.{Files, Path}

    // Copy feature file from classpath to Jimfs
    val resourcePath = if featureResource.startsWith("/") then featureResource.tail else featureResource
    try
      val featureContent = Source.fromResource(resourcePath)
      val jimfsFeaturePath: Path = jimfs.getPath(featurePath)
      Files.createDirectories(jimfsFeaturePath.getParent)
      Files.write(jimfsFeaturePath, featureContent.mkString.getBytes)
      featureContent.close()

      // Create evidence directory
      val jimfsEvidencePath: Path = jimfs.getPath(evidencePath)
      Files.createDirectories(jimfsEvidencePath)

      // jimfsLocation needs to point to the features directory (not evidence),
      // and must be a URI for Cucumber to load feature files
      // evidenceDir must also be a URI for CucumberExecutor
      val featuresDir: String = jimfsFeaturePath.getParent.toString
      val featuresURI: String = jimfsFeaturePath.getParent.toUri.toString
      val evidenceURI: String = jimfsEvidencePath.toUri.toString

      Some(io.distia.probe.common.models.BlockStorageDirective(
        jimfsLocation = featuresURI,  // URI to features directory (e.g., "jimfs://name/features")
        evidenceDir = evidenceURI,     // URI to evidence directory (e.g., "jimfs://name/evidence-UUID")
        topicDirectives = topicDirectives,
        bucket = "test-bucket"
      ))
    catch
      case ex: Exception => None
