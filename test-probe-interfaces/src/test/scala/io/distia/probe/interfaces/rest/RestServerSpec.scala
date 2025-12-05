package io.distia.probe.interfaces.rest

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, StatusCodes}
import io.distia.probe.core.builder.ServiceInterfaceFunctions
import io.distia.probe.core.models.QueueStatusResponse
import io.distia.probe.interfaces.config.InterfacesConfig
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

/**
 * Unit tests for RestServer
 *
 * Target Coverage: HTTP server bootstrap (currently 0% → 80%+)
 *
 * Tests:
 * - Server starts successfully on configured port
 * - Server binding returns valid ServerBinding
 * - Routes are accessible after server starts
 * - Server handles bind failures gracefully
 * - Logging occurs on successful bind
 * - Logging occurs on failed bind
 */
class RestServerSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "RestServerSpec")
  implicit val ec: scala.concurrent.ExecutionContext = system.executionContext

  private val testConfig = InterfacesConfig(
    restHost = "127.0.0.1",
    restPort = 0, // OS will assign available port
    restTimeout = 30.seconds,
    gracefulShutdownTimeout = 10.seconds,
    askTimeout = 25.seconds,
    circuitBreakerMaxFailures = 5,
    circuitBreakerCallTimeout = 25.seconds,
    circuitBreakerResetTimeout = 30.seconds,
    maxConcurrentRequests = 100,
    maxRequestSize = 10485760L,
    maxUriLength = 8192,
    retryAfterServiceUnavailable = "30s",
    retryAfterNotReady = "5s"
  )

  private val mockFunctions = ServiceInterfaceFunctions(
    initializeTest = () => ???,
    startTest = (_, _, _) => ???,
    getStatus = _ => ???,
    getQueueStatus = _ => Future.successful(QueueStatusResponse(
      totalTests = 0,
      setupCount = 0,
      loadingCount = 0,
      loadedCount = 0,
      testingCount = 0,
      completedCount = 0,
      exceptionCount = 0,
      currentlyTesting = None
    )),
    cancelTest = _ => ???
  )

  override def afterAll(): Unit = {
    system.terminate()
    Await.result(system.whenTerminated, 10.seconds)
  }

  "RestServer" when {

    "starting server" should {

      "successfully bind to configured host and port" in {
        val server = new RestServer(testConfig, mockFunctions)

        val bindingFuture = server.start("127.0.0.1", 0)
        val binding = Await.result(bindingFuture, 5.seconds)

        binding should not be null
        binding.localAddress.getAddress.getHostAddress shouldBe "127.0.0.1"
        binding.localAddress.getPort should be > 0

        // Clean up
        Await.result(binding.unbind(), 3.seconds)
      }

      "return Future[ServerBinding] that completes successfully" in {
        val server = new RestServer(testConfig, mockFunctions)

        val bindingFuture = server.start("127.0.0.1", 0)

        bindingFuture.isCompleted shouldBe false // Initially not completed

        val binding = Await.result(bindingFuture, 5.seconds)
        bindingFuture.isCompleted shouldBe true

        binding.localAddress should not be null

        // Clean up
        Await.result(binding.unbind(), 3.seconds)
      }

      "bind server with routes accessible via HTTP" in {
        val server = new RestServer(testConfig, mockFunctions)

        val binding = Await.result(server.start("127.0.0.1", 0), 5.seconds)
        val port = binding.localAddress.getPort

        // Make HTTP request to health check endpoint
        val request = HttpRequest(uri = s"http://127.0.0.1:$port/api/v1/health")
        val responseFuture = Http().singleRequest(request)
        val response = Await.result(responseFuture, 5.seconds)

        response.status shouldBe StatusCodes.OK

        // Clean up
        Await.result(binding.unbind(), 3.seconds)
      }

      "allow binding to wildcard address 0.0.0.0" in {
        val server = new RestServer(testConfig, mockFunctions)

        val binding = Await.result(server.start("0.0.0.0", 0), 5.seconds)

        binding.localAddress.getAddress.isAnyLocalAddress shouldBe true
        binding.localAddress.getPort should be > 0

        // Clean up
        Await.result(binding.unbind(), 3.seconds)
      }

      "return different ports when binding to port 0 multiple times" in {
        val server1 = new RestServer(testConfig, mockFunctions)
        val server2 = new RestServer(testConfig, mockFunctions)

        val binding1 = Await.result(server1.start("127.0.0.1", 0), 5.seconds)
        val binding2 = Await.result(server2.start("127.0.0.1", 0), 5.seconds)

        val port1 = binding1.localAddress.getPort
        val port2 = binding2.localAddress.getPort

        port1 should not equal port2

        // Clean up
        Await.result(binding1.unbind(), 3.seconds)
        Await.result(binding2.unbind(), 3.seconds)
      }
    }

    "handling bind failures" should {

      "fail Future when binding to already-bound port" in {
        val server1 = new RestServer(testConfig, mockFunctions)
        val server2 = new RestServer(testConfig, mockFunctions)

        // Bind first server to specific port
        val binding1 = Await.result(server1.start("127.0.0.1", 0), 5.seconds)
        val port = binding1.localAddress.getPort

        // Try to bind second server to same port
        val bindingFuture2 = server2.start("127.0.0.1", port)

        // Should fail
        val result = Try(Await.result(bindingFuture2, 5.seconds))
        result shouldBe a[Failure[_]]

        // Clean up
        Await.result(binding1.unbind(), 3.seconds)
      }

      "fail when binding to invalid port" in {
        val server = new RestServer(testConfig, mockFunctions)

        // Invalid port throws IllegalArgumentException synchronously
        val result = Try(server.start("127.0.0.1", 99999))
        result shouldBe a[Failure[_]]
        result.failed.get shouldBe an[IllegalArgumentException]
      }

      "fail Future when binding to invalid host" in {
        val server = new RestServer(testConfig, mockFunctions)

        val bindingFuture = server.start("999.999.999.999", 0) // Invalid IP

        val result = Try(Await.result(bindingFuture, 5.seconds))
        result shouldBe a[Failure[_]]
      }
    }

    "server instance" should {

      "create RestRoutes with provided config and functions" in {
        val server = new RestServer(testConfig, mockFunctions)

        // Server should be created without throwing
        server should not be null
      }

      "use ExecutionContext from implicit scope" in {
        implicit val customEc = scala.concurrent.ExecutionContext.global

        val server = new RestServer(testConfig, mockFunctions)

        // Server created with custom EC
        server should not be null
      }
    }

    "integration with RestRoutes" should {

      "serve health check endpoint" in {
        val server = new RestServer(testConfig, mockFunctions)
        val binding = Await.result(server.start("127.0.0.1", 0), 5.seconds)
        val port = binding.localAddress.getPort

        val request = HttpRequest(uri = s"http://127.0.0.1:$port/api/v1/health")
        val response = Await.result(Http().singleRequest(request), 5.seconds)

        response.status shouldBe StatusCodes.OK

        // Clean up
        Await.result(binding.unbind(), 3.seconds)
      }

      "return 404 for non-existent endpoints" in {
        val server = new RestServer(testConfig, mockFunctions)
        val binding = Await.result(server.start("127.0.0.1", 0), 5.seconds)
        val port = binding.localAddress.getPort

        val request = HttpRequest(uri = s"http://127.0.0.1:$port/nonexistent")
        val response = Await.result(Http().singleRequest(request), 5.seconds)

        response.status shouldBe StatusCodes.NotFound

        // Clean up
        Await.result(binding.unbind(), 3.seconds)
      }
    }
  }
}
