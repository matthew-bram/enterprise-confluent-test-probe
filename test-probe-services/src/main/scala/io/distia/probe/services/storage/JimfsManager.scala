package io.distia.probe
package services
package storage

import com.google.common.jimfs.{Configuration, Jimfs}
import java.nio.file.{FileSystem, Files, Path}
import java.util.UUID
import scala.jdk.StreamConverters._

/**
 * JimfsManager - In-memory filesystem manager for test artifact staging
 *
 * Provides lifecycle management for test-specific directories using JIMFS (Java In-Memory FileSystem).
 * Each test execution gets an isolated directory identified by UUID, where test artifacts (features,
 * topic directives, evidence) are staged during execution. This approach enables:
 * - Fast I/O operations (all in-memory)
 * - Clean isolation between concurrent tests
 * - No filesystem pollution (automatic cleanup)
 * - Consistent behavior across platforms (Unix-style paths)
 *
 * The in-memory filesystem uses Unix configuration for consistent path handling regardless of
 * the underlying OS. Test directories are created at the root level with UUIDs as names (e.g., /test-id).
 *
 * Lifecycle:
 * 1. createTestDirectory(testId) - Creates isolated directory for test
 * 2. Test execution uses the directory for staging artifacts
 * 3. deleteTestDirectory(testId) - Cleans up after test completion
 *
 * Thread Safety: This class is thread-safe. The underlying JIMFS FileSystem is thread-safe,
 * and all operations use atomic file system operations. Multiple tests can safely create and
 * delete their directories concurrently.
 *
 * @see Configuration for JIMFS configuration options
 * @see FileSystem for the underlying in-memory filesystem
 */
private[services] class JimfsManager {

  private val fileSystem: FileSystem = Jimfs.newFileSystem(Configuration.unix())

  /**
   * Creates an isolated test directory for the given test ID.
   *
   * @param testId unique identifier for the test execution
   * @return Path to the created test directory
   * @throws IllegalStateException if a directory for this testId already exists
   */
  def createTestDirectory(testId: UUID): Path = {
    val testDir: Path = fileSystem.getPath(s"/$testId")
    if Files.exists(testDir) then
      throw new IllegalStateException(s"Test directory already exists: $testDir")
    Files.createDirectories(testDir)
    testDir
  }

  /**
   * Gets the path to an existing test directory.
   *
   * @param testId unique identifier for the test execution
   * @return Path to the test directory (may not exist)
   */
  def getTestDirectory(testId: UUID): Path =
    fileSystem.getPath(s"/$testId")

  /**
   * Recursively deletes a test directory and all its contents.
   *
   * @param testId unique identifier for the test execution
   */
  def deleteTestDirectory(testId: UUID): Unit = {
    val testDir: Path = fileSystem.getPath(s"/$testId")
    if Files.exists(testDir) then deleteRecursively(testDir)
  }

  /**
   * Checks if a test directory exists.
   *
   * @param testId unique identifier for the test execution
   * @return true if directory exists, false otherwise
   */
  def testDirectoryExists(testId: UUID): Boolean =
    Files.exists(fileSystem.getPath(s"/$testId"))

  /**
   * Lists all test directories currently in JIMFS.
   *
   * @return List of test IDs (UUIDs) for existing test directories
   */
  def listTestDirectories(): List[UUID] = Files
    .list(fileSystem.getPath("/"))
    .toScala(List)
    .flatMap { path =>
      try Some(UUID.fromString(path.getFileName.toString))
      catch case _: IllegalArgumentException => None
    }

  /**
   * Cleans up all test directories in JIMFS.
   *
   * Useful for cleanup in test teardown or service shutdown.
   */
  def cleanupAll(): Unit = Files
    .list(fileSystem.getPath("/"))
    .toScala(List)
    .foreach(deleteRecursively)

  /** Recursively deletes a directory and all its contents. */
  private def deleteRecursively(path: Path): Unit = {
    if Files.isDirectory(path) then
      Files.list(path).toScala(List).foreach(deleteRecursively)
    Files.delete(path)
  }
}
