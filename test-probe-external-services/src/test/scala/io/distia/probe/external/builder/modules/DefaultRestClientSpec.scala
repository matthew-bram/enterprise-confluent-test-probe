package io.distia.probe.external.builder.modules

import io.distia.probe.core.builder.{ActorBehaviorsContext, BuilderContext, ExternalBehaviorConfig}
import io.distia.probe.core.builder.modules.ProbeActorBehavior
import io.distia.probe.external.rest.RestClientCommands.RestClientCommand
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

class DefaultRestClientSpec extends AnyWordSpec with Matchers with ScalaFutures:

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))

  "DefaultRestClient" should:

    "implement ProbeActorBehavior trait" in:
      val client = new DefaultRestClient()
      client shouldBe a[ProbeActorBehavior]

    "be created with default parameters" in:
      val client = new DefaultRestClient()
      client should not be null

    "be created with custom parameters" in:
      val client = new DefaultRestClient(
        routerPoolSize = 10,
        defaultTimeout = 60.seconds,
        externalAddress = "custom-rest"
      )
      client should not be null


  "DefaultRestClient.preFlight" should:

    "pass with empty context" in:
      val client = new DefaultRestClient()
      val ctx = BuilderContext()

      val result = client.preFlight(ctx).futureValue

      result shouldBe ctx

    "pass when ObjectMapper initializes successfully" in:
      val client = new DefaultRestClient()
      val ctx = BuilderContext()

      // Should not throw
      noException should be thrownBy:
        client.preFlight(ctx).futureValue

    "return the same context (no decoration)" in:
      val client = new DefaultRestClient()
      val ctx = BuilderContext()

      val result = client.preFlight(ctx).futureValue

      result shouldBe theSameInstanceAs(ctx)


  "DefaultRestClient.initialize" should:

    "add ExternalBehaviorConfig to empty context" in:
      val client = new DefaultRestClient()
      val ctx = BuilderContext()

      val result = client.initialize(ctx).futureValue

      result.actorBehaviorsContext shouldBe defined
      result.actorBehaviorsContext.get.behaviors should have size 1

    "configure behavior with correct router pool size" in:
      val client = new DefaultRestClient(routerPoolSize = 7)
      val ctx = BuilderContext()

      val result = client.initialize(ctx).futureValue

      val config = result.actorBehaviorsContext.get.behaviors.head
      config.routerPoolSize shouldBe 7

    "configure behavior with correct actor name prefix" in:
      val client = new DefaultRestClient()
      val ctx = BuilderContext()

      val result = client.initialize(ctx).futureValue

      val config = result.actorBehaviorsContext.get.behaviors.head
      config.actorNamePrefix shouldBe "rest-client"

    "configure behavior with correct external address" in:
      val client = new DefaultRestClient(externalAddress = "my-rest-api")
      val ctx = BuilderContext()

      val result = client.initialize(ctx).futureValue

      val config = result.actorBehaviorsContext.get.behaviors.head
      config.externalAddress shouldBe "my-rest-api"

    "configure behavior with RestClientActor behavior" in:
      val client = new DefaultRestClient()
      val ctx = BuilderContext()

      val result = client.initialize(ctx).futureValue

      val config = result.actorBehaviorsContext.get.behaviors.head
      config.behavior should not be null

    "add to existing behaviors context" in:
      val existingConfig = ExternalBehaviorConfig(
        routerPoolSize = 3,
        actorNamePrefix = "existing",
        externalAddress = "existing-svc",
        behavior = null.asInstanceOf[org.apache.pekko.actor.typed.Behavior[RestClientCommand]]
      )
      val existingBehaviors = ActorBehaviorsContext().addBehavior(existingConfig)
      val ctx = BuilderContext().withActorBehaviorsContext(existingBehaviors)

      val client = new DefaultRestClient()
      val result = client.initialize(ctx).futureValue

      result.actorBehaviorsContext.get.behaviors should have size 2


  "DefaultRestClient.finalCheck" should:

    "pass when behavior is registered" in:
      val client = new DefaultRestClient()
      val ctx = BuilderContext()

      val initialized = client.initialize(ctx).futureValue
      val result = client.finalCheck(initialized).futureValue

      result shouldBe initialized

    "fail when no behaviors registered" in:
      val client = new DefaultRestClient()
      val ctx = BuilderContext()

      val ex = client.finalCheck(ctx).failed.futureValue
      ex shouldBe a[IllegalArgumentException]
      ex.getMessage should include("not registered")

    "fail when actorBehaviorsContext is None" in:
      val client = new DefaultRestClient()
      val ctx = BuilderContext()

      val ex = client.finalCheck(ctx).failed.futureValue
      ex shouldBe a[IllegalArgumentException]

    "fail when actorBehaviorsContext has empty behaviors list" in:
      val client = new DefaultRestClient()
      val ctx = BuilderContext().withActorBehaviorsContext(ActorBehaviorsContext())

      val ex = client.finalCheck(ctx).failed.futureValue
      ex shouldBe a[IllegalArgumentException]

    "return the same context (no decoration)" in:
      val client = new DefaultRestClient()
      val ctx = client.initialize(BuilderContext()).futureValue

      val result = client.finalCheck(ctx).futureValue

      result shouldBe theSameInstanceAs(ctx)


  "DefaultRestClient validation" should:

    "fail finalCheck with invalid router pool size" in:
      val client = new DefaultRestClient(routerPoolSize = 0)
      val ctx = client.initialize(BuilderContext()).futureValue

      val ex = client.finalCheck(ctx).failed.futureValue
      ex shouldBe a[IllegalArgumentException]
      ex.getMessage should include("positive")

    "fail finalCheck with negative router pool size" in:
      val client = new DefaultRestClient(routerPoolSize = -1)
      val ctx = client.initialize(BuilderContext()).futureValue

      val ex = client.finalCheck(ctx).failed.futureValue
      ex shouldBe a[IllegalArgumentException]


  "DefaultRestClient lifecycle" should:

    "complete full preFlight -> initialize -> finalCheck cycle" in:
      val client = new DefaultRestClient()
      val emptyCtx = BuilderContext()

      // Phase 1: preFlight (validation)
      val ctx1 = client.preFlight(emptyCtx).futureValue
      ctx1 shouldBe emptyCtx

      // Phase 2: initialize (decoration)
      val ctx2 = client.initialize(ctx1).futureValue
      ctx2.actorBehaviorsContext shouldBe defined

      // Phase 3: finalCheck (validation)
      val ctx3 = client.finalCheck(ctx2).futureValue
      ctx3 shouldBe ctx2

    "work with different configurations" in:
      val client = new DefaultRestClient(
        routerPoolSize = 3,
        defaultTimeout = 15.seconds,
        externalAddress = "api-client"
      )

      val result = (for
        ctx1 <- client.preFlight(BuilderContext())
        ctx2 <- client.initialize(ctx1)
        ctx3 <- client.finalCheck(ctx2)
      yield ctx3).futureValue

      val config = result.actorBehaviorsContext.get.behaviors.head
      config.routerPoolSize shouldBe 3
      config.externalAddress shouldBe "api-client"
