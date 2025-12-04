package io.distia.probe
package services
package storage

import fixtures.JimfsTestFixtures
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.nio.file.{Files, Path}
import java.util.UUID

/**
 * Unit tests for JimfsManager
 * Tests in-memory filesystem management for test isolation
 */
private[services] class JimfsManagerSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  private var jimfsManager: JimfsManager = _

  override def beforeEach(): Unit = {
    jimfsManager = new JimfsManager()
  }

  override def afterEach(): Unit = {
    // Cleanup all test directories after each test
    jimfsManager.cleanupAll()
  }

  // ========== CREATE TEST DIRECTORY TESTS ==========

  "JimfsManager.createTestDirectory" when {

    "creating a new test directory" should {

      "create directory with test ID as name" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()

        val testDir: Path = jimfsManager.createTestDirectory(testId)

        testDir.toString shouldBe s"/$testId"
        Files.exists(testDir) shouldBe true
        Files.isDirectory(testDir) shouldBe true
      }

      "return path to created directory" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()

        val testDir: Path = jimfsManager.createTestDirectory(testId)

        testDir should not be null
        testDir.getFileName.toString shouldBe testId.toString
      }

      "create directory in JIMFS in-memory filesystem" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()

        val testDir: Path = jimfsManager.createTestDirectory(testId)

        // JIMFS paths have specific filesystem type
        testDir.getFileSystem.provider().getScheme shouldBe "jimfs"
      }
    }

    "directory already exists" should {

      "throw IllegalStateException" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()
        jimfsManager.createTestDirectory(testId)

        val exception = intercept[IllegalStateException] {
          jimfsManager.createTestDirectory(testId)
        }

        exception.getMessage should include("Test directory already exists")
        exception.getMessage should include(testId.toString)
      }
    }

    "creating multiple directories" should {

      "isolate each test directory" in {
        val testId1: UUID = JimfsTestFixtures.randomTestId()
        val testId2: UUID = JimfsTestFixtures.randomTestId()

        val testDir1: Path = jimfsManager.createTestDirectory(testId1)
        val testDir2: Path = jimfsManager.createTestDirectory(testId2)

        testDir1 should not be testDir2
        Files.exists(testDir1) shouldBe true
        Files.exists(testDir2) shouldBe true
      }

      "not interfere with each other" in {
        val testId1: UUID = JimfsTestFixtures.randomTestId()
        val testId2: UUID = JimfsTestFixtures.randomTestId()

        jimfsManager.createTestDirectory(testId1)
        jimfsManager.createTestDirectory(testId2)

        val file1: Path = jimfsManager.getTestDirectory(testId1).resolve("file1.txt")
        Files.writeString(file1, "content1")

        val file2: Path = jimfsManager.getTestDirectory(testId2).resolve("file2.txt")
        Files.writeString(file2, "content2")

        Files.exists(file1) shouldBe true
        Files.exists(file2) shouldBe true
        Files.readString(file1) shouldBe "content1"
        Files.readString(file2) shouldBe "content2"
      }
    }
  }

  // ========== GET TEST DIRECTORY TESTS ==========

  "JimfsManager.getTestDirectory" when {

    "directory exists" should {

      "return path to directory" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()
        jimfsManager.createTestDirectory(testId)

        val testDir: Path = jimfsManager.getTestDirectory(testId)

        testDir.toString shouldBe s"/$testId"
        Files.exists(testDir) shouldBe true
      }

      "return same path as createTestDirectory" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()
        val createdDir: Path = jimfsManager.createTestDirectory(testId)

        val retrievedDir: Path = jimfsManager.getTestDirectory(testId)

        retrievedDir shouldBe createdDir
      }
    }

    "directory does not exist" should {

      "return path without creating it" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()

        val testDir: Path = jimfsManager.getTestDirectory(testId)

        testDir.toString shouldBe s"/$testId"
        Files.exists(testDir) shouldBe false
      }
    }
  }

  // ========== DELETE TEST DIRECTORY TESTS ==========

  "JimfsManager.deleteTestDirectory" when {

    "directory exists and is empty" should {

      "delete directory successfully" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()
        jimfsManager.createTestDirectory(testId)

        jimfsManager.deleteTestDirectory(testId)

        Files.exists(jimfsManager.getTestDirectory(testId)) shouldBe false
      }
    }

    "directory exists with files" should {

      "recursively delete directory and contents" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()
        val testDir: Path = jimfsManager.createTestDirectory(testId)

        // Create files and subdirectories
        val file1: Path = testDir.resolve("file1.txt")
        Files.writeString(file1, "content")

        val subDir: Path = testDir.resolve("subdir")
        Files.createDirectories(subDir)
        val file2: Path = subDir.resolve("file2.txt")
        Files.writeString(file2, "nested content")

        jimfsManager.deleteTestDirectory(testId)

        Files.exists(testDir) shouldBe false
        Files.exists(file1) shouldBe false
        Files.exists(subDir) shouldBe false
        Files.exists(file2) shouldBe false
      }
    }

    "directory does not exist" should {

      "not throw exception" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()

        noException should be thrownBy {
          jimfsManager.deleteTestDirectory(testId)
        }
      }
    }

    "deleting one directory" should {

      "not affect other directories" in {
        val testId1: UUID = JimfsTestFixtures.randomTestId()
        val testId2: UUID = JimfsTestFixtures.randomTestId()

        jimfsManager.createTestDirectory(testId1)
        jimfsManager.createTestDirectory(testId2)

        jimfsManager.deleteTestDirectory(testId1)

        Files.exists(jimfsManager.getTestDirectory(testId1)) shouldBe false
        Files.exists(jimfsManager.getTestDirectory(testId2)) shouldBe true
      }
    }
  }

  // ========== TEST DIRECTORY EXISTS TESTS ==========

  "JimfsManager.testDirectoryExists" when {

    "directory exists" should {

      "return true" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()
        jimfsManager.createTestDirectory(testId)

        jimfsManager.testDirectoryExists(testId) shouldBe true
      }
    }

    "directory does not exist" should {

      "return false" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()

        jimfsManager.testDirectoryExists(testId) shouldBe false
      }
    }

    "directory was deleted" should {

      "return false" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()
        jimfsManager.createTestDirectory(testId)
        jimfsManager.deleteTestDirectory(testId)

        jimfsManager.testDirectoryExists(testId) shouldBe false
      }
    }
  }

  // ========== LIST TEST DIRECTORIES TESTS ==========

  "JimfsManager.listTestDirectories" when {

    "no directories exist" should {

      "return empty list" in {
        val directories: List[UUID] = jimfsManager.listTestDirectories()

        directories shouldBe empty
      }
    }

    "one directory exists" should {

      "return list with single UUID" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()
        jimfsManager.createTestDirectory(testId)

        val directories: List[UUID] = jimfsManager.listTestDirectories()

        directories should have size 1
        directories should contain(testId)
      }
    }

    "multiple directories exist" should {

      "return list with all UUIDs" in {
        val testId1: UUID = JimfsTestFixtures.randomTestId()
        val testId2: UUID = JimfsTestFixtures.randomTestId()
        val testId3: UUID = JimfsTestFixtures.randomTestId()

        jimfsManager.createTestDirectory(testId1)
        jimfsManager.createTestDirectory(testId2)
        jimfsManager.createTestDirectory(testId3)

        val directories: List[UUID] = jimfsManager.listTestDirectories()

        directories should have size 3
        directories should contain allOf (testId1, testId2, testId3)
      }
    }

    "non-UUID directories exist" should {

      "filter out invalid UUIDs" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()
        jimfsManager.createTestDirectory(testId)

        // Create a non-UUID directory directly in JIMFS
        val nonUuidDir: Path = jimfsManager.getTestDirectory(testId).getFileSystem.getPath("/not-a-uuid")
        Files.createDirectories(nonUuidDir)

        val directories: List[UUID] = jimfsManager.listTestDirectories()

        directories should have size 1
        directories should contain(testId)
        directories should not contain "not-a-uuid"
      }
    }
  }

  // ========== CLEANUP ALL TESTS ==========

  "JimfsManager.cleanupAll" when {

    "no directories exist" should {

      "not throw exception" in {
        noException should be thrownBy {
          jimfsManager.cleanupAll()
        }
      }
    }

    "multiple directories exist" should {

      "delete all directories" in {
        val testId1: UUID = JimfsTestFixtures.randomTestId()
        val testId2: UUID = JimfsTestFixtures.randomTestId()
        val testId3: UUID = JimfsTestFixtures.randomTestId()

        jimfsManager.createTestDirectory(testId1)
        jimfsManager.createTestDirectory(testId2)
        jimfsManager.createTestDirectory(testId3)

        jimfsManager.cleanupAll()

        jimfsManager.listTestDirectories() shouldBe empty
        jimfsManager.testDirectoryExists(testId1) shouldBe false
        jimfsManager.testDirectoryExists(testId2) shouldBe false
        jimfsManager.testDirectoryExists(testId3) shouldBe false
      }
    }

    "directories contain nested structures" should {

      "recursively delete all contents" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()
        val testDir: Path = jimfsManager.createTestDirectory(testId)

        // Create nested structure
        JimfsTestFixtures.createNestedDirectoryStructure(testDir)

        jimfsManager.cleanupAll()

        jimfsManager.listTestDirectories() shouldBe empty
      }
    }

    "called multiple times" should {

      "be idempotent" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()
        jimfsManager.createTestDirectory(testId)

        jimfsManager.cleanupAll()
        noException should be thrownBy {
          jimfsManager.cleanupAll()
        }

        jimfsManager.listTestDirectories() shouldBe empty
      }
    }
  }

  // ========== FILESYSTEM ISOLATION TESTS ==========

  "JimfsManager filesystem isolation" when {

    "using multiple JimfsManager instances" should {

      "isolate filesystems between instances" in {
        val manager1: JimfsManager = new JimfsManager()
        val manager2: JimfsManager = new JimfsManager()

        val testId: UUID = JimfsTestFixtures.randomTestId()

        val dir1: Path = manager1.createTestDirectory(testId)
        val dir2: Path = manager2.createTestDirectory(testId)

        // Same UUID but different filesystem instances
        dir1.getFileSystem should not be dir2.getFileSystem
        manager1.testDirectoryExists(testId) shouldBe true
        manager2.testDirectoryExists(testId) shouldBe true

        // Cleanup one should not affect the other
        manager1.cleanupAll()
        manager1.testDirectoryExists(testId) shouldBe false
        manager2.testDirectoryExists(testId) shouldBe true

        manager2.cleanupAll()
      }
    }

    "storing test data" should {

      "not interfere with real filesystem" in {
        val testId: UUID = JimfsTestFixtures.randomTestId()
        val testDir: Path = jimfsManager.createTestDirectory(testId)

        val jimfsFile: Path = testDir.resolve("test.txt")
        Files.writeString(jimfsFile, "jimfs content")

        // Verify file is in JIMFS, not real filesystem
        jimfsFile.getFileSystem.provider().getScheme shouldBe "jimfs"
        Files.exists(jimfsFile) shouldBe true

        // Cleanup should not affect real filesystem
        jimfsManager.cleanupAll()
      }
    }
  }
}
