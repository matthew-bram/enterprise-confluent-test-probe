package io.distia.probe.external.rest

import io.distia.probe.external.fixtures.RestClientFixtures.*
import io.distia.probe.external.rest.RestClientCommands.*
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll

import java.net.URI
import scala.concurrent.duration.*
import scala.reflect.ClassTag

/**
 * Unit tests for RestClientActor.
 *
 * These tests verify actor creation and behavior setup.
 * For HTTP behavior testing, see RestClientActorIntegrationSpec with WireMock.
 */
class RestClientActorSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll:

  val testKit: ActorTestKit = ActorTestKit()

  override def afterAll(): Unit =
    testKit.shutdownTestKit()
    super.afterAll()

  "RestClientActor" should:

    "be created with default timeout" in:
      val behavior = RestClientActor()
      val actor = testKit.spawn(behavior, "rest-client-default")

      actor should not be null

    "be created with custom timeout" in:
      val behavior = RestClientActor(60.seconds)
      val actor = testKit.spawn(behavior, "rest-client-custom")

      actor should not be null

    "accept ExecuteRequest messages" in:
      given ClassTag[OrderRequest] = ClassTag(classOf[OrderRequest])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
      val actor = testKit.spawn(RestClientActor(), "rest-client-accept")

      val request = RestClientEnvelopeReq(
        payload = OrderRequest("prod-1", 1),
        uri = URI.create("http://127.0.0.1:9999/nonexistent"),
        method = HttpMethod.POST
      )

      // Verify actor accepts the message without throwing
      noException should be thrownBy:
        actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)

    "support all HTTP methods in request creation" in:
      given ClassTag[EmptyPayload] = ClassTag(classOf[EmptyPayload])
      given ClassTag[OrderResponse] = ClassTag(classOf[OrderResponse])

      val actor = testKit.spawn(RestClientActor(), "rest-client-methods")

      val methods = List(
        HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT,
        HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.HEAD, HttpMethod.OPTIONS
      )

      methods.foreach { method =>
        val probe = testKit.createTestProbe[RestClientResult[OrderResponse]]()
        val request = RestClientEnvelopeReq(
          payload = EmptyPayload(),
          uri = URI.create("http://127.0.0.1:9999/test"),
          method = method
        )

        // Verify actor accepts messages for all methods
        noException should be thrownBy:
          actor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)
      }
