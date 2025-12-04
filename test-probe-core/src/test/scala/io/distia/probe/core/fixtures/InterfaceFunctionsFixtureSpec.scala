package io.distia.probe.core.fixtures

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import java.util.UUID

/**
 * Tests InterfaceFunctionsFixture for function stub factories.
 *
 * Verifies:
 * - Successful block storage fetch function (getSuccessfulBlockStorageFetchFunction)
 * - Failed block storage fetch function (getFailedBlockStorageFetchFunction)
 * - Successful block storage load function (getSuccessfulBlockStorageLoadFunction)
 * - Failed block storage load function (getFailedBlockStorageLoadFunction)
 * - Successful security directives fetch function (getSuccessfulSecurityDirectivesFetchFunction)
 * - Failed security directives fetch function (getFailedSecurityDirectivesFetchFunction)
 * - Function signatures and return types
 * - Future completion states
 * - Error handling behavior
 *
 * Test Strategy: Unit tests (no external dependencies)
 *
 * Dogfooding: Tests InterfaceFunctionsFixture using InterfaceFunctionsFixture itself
 */
class InterfaceFunctionsFixtureSpec extends AnyWordSpec
  with Matchers
  with InterfaceFunctionsFixture
  with BlockStorageDirectiveFixtures
  with KafkaSecurityDirectiveFixtures {

  given ExecutionContext = ExecutionContext.global

  "InterfaceFunctionsFixture" should {

    "provide getSuccessfulBlockStorageFetchFunction" in {
      val directive = createBlockStorageDirective()
      val function = getSuccessfulBlockStorageFetchFunction(directive)
      function should not be null
    }

    "provide getFailedBlockStorageFetchFunction" in {
      val function = getFailedBlockStorageFetchFunction
      function should not be null
    }

    "provide getSuccessfulBlockStorageLoadFunction" in {
      val function = getSuccessfulBlockStorageLoadFunction
      function should not be null
    }

    "provide getFailedBlockStorageLoadFunction" in {
      val function = getFailedBlockStorageLoadFunction
      function should not be null
    }

    "provide getSuccessfulSecurityDirectivesFetchFunction" in {
      val directives = List(createProducerSecurity())
      val function = getSuccessfulSecurityDirectivesFetchFunction(directives)
      function should not be null
    }

    "provide getFailedSecurityDirectivesFetchFunction" in {
      val function = getFailedSecurityDirectivesFetchFunction
      function should not be null
    }
  }

  "getSuccessfulBlockStorageFetchFunction" should {

    "return successful Future with provided BlockStorageDirective" in {
      val directive = createBlockStorageDirective(bucket = "test-bucket")
      val function = getSuccessfulBlockStorageFetchFunction(directive)

      val testId = UUID.randomUUID()
      val future = function(testId, "bucket")

      Await.ready(future, 1.second)
      future.value should matchPattern { case Some(Success(_)) => }
      future.value.get.get shouldBe directive
    }

    "accept testId and bucket parameters" in {
      val directive = createBlockStorageDirective()
      val function = getSuccessfulBlockStorageFetchFunction(directive)

      val testId = UUID.randomUUID()
      val bucket = "custom-bucket"

      noException should be thrownBy {
        function(testId, bucket)
      }
    }

    "return same directive regardless of parameters" in {
      val directive = createBlockStorageDirective(bucket = "fixed-bucket")
      val function = getSuccessfulBlockStorageFetchFunction(directive)

      val result1 = Await.result(function(UUID.randomUUID(), "bucket-1"), 1.second)
      val result2 = Await.result(function(UUID.randomUUID(), "bucket-2"), 1.second)

      result1 shouldBe directive
      result2 shouldBe directive
    }
  }

  "getFailedBlockStorageFetchFunction" should {

    "return failed Future" in {
      val function = getFailedBlockStorageFetchFunction

      val testId = UUID.randomUUID()
      val future = function(testId, "bucket")

      Await.ready(future, 1.second)
      future.value should matchPattern { case Some(Failure(_)) => }
    }

    "fail with Throwable" in {
      val function = getFailedBlockStorageFetchFunction

      val testId = UUID.randomUUID()
      val future = function(testId, "bucket")

      Await.ready(future, 1.second)
      future.value.get.failed.get shouldBe a [Throwable]
    }

    "accept testId and bucket parameters" in {
      val function = getFailedBlockStorageFetchFunction

      val testId = UUID.randomUUID()
      val bucket = "test-bucket"

      noException should be thrownBy {
        function(testId, bucket)
      }
    }

    "always fail regardless of parameters" in {
      val function = getFailedBlockStorageFetchFunction

      val future1 = function(UUID.randomUUID(), "bucket-1")
      val future2 = function(UUID.randomUUID(), "bucket-2")

      Await.ready(future1, 1.second)
      Await.ready(future2, 1.second)

      future1.value should matchPattern { case Some(Failure(_)) => }
      future2.value should matchPattern { case Some(Failure(_)) => }
    }
  }

  "getSuccessfulBlockStorageLoadFunction" should {

    "return successful Future with Unit" in {
      val function = getSuccessfulBlockStorageLoadFunction

      val testId = UUID.randomUUID()
      val future = function(testId, "bucket", "evidence")

      Await.ready(future, 1.second)
      future.value should matchPattern { case Some(Success(())) => }
    }

    "accept testId, bucket, and evidence parameters" in {
      val function = getSuccessfulBlockStorageLoadFunction

      val testId = UUID.randomUUID()
      val bucket = "test-bucket"
      val evidence = "/path/to/evidence"

      noException should be thrownBy {
        function(testId, bucket, evidence)
      }
    }

    "succeed regardless of parameters" in {
      val function = getSuccessfulBlockStorageLoadFunction

      val result1 = Await.result(function(UUID.randomUUID(), "bucket-1", "evidence-1"), 1.second)
      val result2 = Await.result(function(UUID.randomUUID(), "bucket-2", "evidence-2"), 1.second)

      result1 shouldBe (())
      result2 shouldBe (())
    }
  }

  "getFailedBlockStorageLoadFunction" should {

    "return failed Future" in {
      val function = getFailedBlockStorageLoadFunction

      val testId = UUID.randomUUID()
      val future = function(testId, "bucket", "evidence")

      Await.ready(future, 1.second)
      future.value should matchPattern { case Some(Failure(_)) => }
    }

    "fail with Throwable" in {
      val function = getFailedBlockStorageLoadFunction

      val testId = UUID.randomUUID()
      val future = function(testId, "bucket", "evidence")

      Await.ready(future, 1.second)
      future.value.get.failed.get shouldBe a [Throwable]
    }

    "accept testId, bucket, and evidence parameters" in {
      val function = getFailedBlockStorageLoadFunction

      val testId = UUID.randomUUID()
      val bucket = "test-bucket"
      val evidence = "/path/to/evidence"

      noException should be thrownBy {
        function(testId, bucket, evidence)
      }
    }

    "always fail regardless of parameters" in {
      val function = getFailedBlockStorageLoadFunction

      val future1 = function(UUID.randomUUID(), "bucket-1", "evidence-1")
      val future2 = function(UUID.randomUUID(), "bucket-2", "evidence-2")

      Await.ready(future1, 1.second)
      Await.ready(future2, 1.second)

      future1.value should matchPattern { case Some(Failure(_)) => }
      future2.value should matchPattern { case Some(Failure(_)) => }
    }
  }

  "getSuccessfulSecurityDirectivesFetchFunction" should {

    "return successful Future with provided security directives" in {
      val directives = List(
        createProducerSecurity(topic = "topic-1"),
        createConsumerSecurity(topic = "topic-2")
      )
      val function = getSuccessfulSecurityDirectivesFetchFunction(directives)

      val blockStorageDirective = createBlockStorageDirective()
      val future = function(blockStorageDirective)

      Await.ready(future, 1.second)
      future.value should matchPattern { case Some(Success(_)) => }
      future.value.get.get shouldBe directives
    }

    "accept BlockStorageDirective parameter" in {
      val directives = List(createProducerSecurity())
      val function = getSuccessfulSecurityDirectivesFetchFunction(directives)

      val blockStorageDirective = createBlockStorageDirective()

      noException should be thrownBy {
        function(blockStorageDirective)
      }
    }

    "return same directives regardless of parameter" in {
      val directives = List(createProducerSecurity(), createConsumerSecurity())
      val function = getSuccessfulSecurityDirectivesFetchFunction(directives)

      val result1 = Await.result(function(createBlockStorageDirective(bucket = "bucket-1")), 1.second)
      val result2 = Await.result(function(createBlockStorageDirective(bucket = "bucket-2")), 1.second)

      result1 shouldBe directives
      result2 shouldBe directives
    }

    "handle empty directive list" in {
      val function = getSuccessfulSecurityDirectivesFetchFunction(List.empty)

      val blockStorageDirective = createBlockStorageDirective()
      val result = Await.result(function(blockStorageDirective), 1.second)

      result shouldBe empty
    }

    "handle single directive" in {
      val directive = createProducerSecurity(topic = "single-topic")
      val function = getSuccessfulSecurityDirectivesFetchFunction(List(directive))

      val blockStorageDirective = createBlockStorageDirective()
      val result = Await.result(function(blockStorageDirective), 1.second)

      result should have size 1
      result.head shouldBe directive
    }

    "handle multiple directives" in {
      val directives = List(
        createProducerSecurity(topic = "topic-1"),
        createProducerSecurity(topic = "topic-2"),
        createConsumerSecurity(topic = "topic-3")
      )
      val function = getSuccessfulSecurityDirectivesFetchFunction(directives)

      val blockStorageDirective = createBlockStorageDirective()
      val result = Await.result(function(blockStorageDirective), 1.second)

      result should have size 3
      result should contain theSameElementsAs directives
    }
  }

  "getFailedSecurityDirectivesFetchFunction" should {

    "return failed Future" in {
      val function = getFailedSecurityDirectivesFetchFunction

      val blockStorageDirective = createBlockStorageDirective()
      val future = function(blockStorageDirective)

      Await.ready(future, 1.second)
      future.value should matchPattern { case Some(Failure(_)) => }
    }

    "fail with Throwable" in {
      val function = getFailedSecurityDirectivesFetchFunction

      val blockStorageDirective = createBlockStorageDirective()
      val future = function(blockStorageDirective)

      Await.ready(future, 1.second)
      future.value.get.failed.get shouldBe a [Throwable]
    }

    "accept BlockStorageDirective parameter" in {
      val function = getFailedSecurityDirectivesFetchFunction

      val blockStorageDirective = createBlockStorageDirective()

      noException should be thrownBy {
        function(blockStorageDirective)
      }
    }

    "always fail regardless of parameter" in {
      val function = getFailedSecurityDirectivesFetchFunction

      val future1 = function(createBlockStorageDirective(bucket = "bucket-1"))
      val future2 = function(createBlockStorageDirective(bucket = "bucket-2"))

      Await.ready(future1, 1.second)
      Await.ready(future2, 1.second)

      future1.value should matchPattern { case Some(Failure(_)) => }
      future2.value should matchPattern { case Some(Failure(_)) => }
    }
  }

  "Function stub behaviors" should {

    "allow testing success scenarios" in {
      val directive = createBlockStorageDirective(bucket = "success-test")
      val function = getSuccessfulBlockStorageFetchFunction(directive)

      val result = Await.result(function(UUID.randomUUID(), "bucket"), 1.second)
      result.bucket shouldBe "success-test"
    }

    "allow testing failure scenarios" in {
      val function = getFailedBlockStorageFetchFunction

      intercept[Throwable] {
        Await.result(function(UUID.randomUUID(), "bucket"), 1.second)
      }
    }

    "support composing successful functions" in {
      val blockStorageDirective = createBlockStorageDirective()
      val securityDirectives = List(createProducerSecurity())

      val fetchBlockStorage = getSuccessfulBlockStorageFetchFunction(blockStorageDirective)
      val fetchSecurity = getSuccessfulSecurityDirectivesFetchFunction(securityDirectives)

      val blockResult = Await.result(fetchBlockStorage(UUID.randomUUID(), "bucket"), 1.second)
      val securityResult = Await.result(fetchSecurity(blockResult), 1.second)

      securityResult shouldBe securityDirectives
    }
  }
}
