package io.distia.probe
package core
package builder

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext.Implicits.global

private[core] class BuilderContextSpec extends AnyWordSpec with Matchers {

  // ========== IMMUTABILITY TESTS ==========

  "BuilderContext immutability" should {

    "create new instance when decorating with config" in {
      val ctx1 = BuilderContext()
      val ctx2 = ctx1.withConfig(null, null)

      ctx1 should not be theSameInstanceAs(ctx2)
      ctx1.config shouldBe None
      ctx2.config shouldBe defined
    }

    "create new instance when decorating with actor system" in {
      val ctx1 = BuilderContext()
      val ctx2 = ctx1.withActorSystem(null)

      ctx1 should not be theSameInstanceAs(ctx2)
      ctx1.actorSystem shouldBe None
      ctx2.actorSystem shouldBe defined
    }

    "create new instance when decorating with queue actor ref" in {
      val ctx1 = BuilderContext()
      val ctx2 = ctx1.withQueueActorRef(null)

      ctx1 should not be theSameInstanceAs(ctx2)
      ctx1.queueActorRef shouldBe None
      ctx2.queueActorRef shouldBe defined
    }

    "create new instance when decorating with curried functions" in {
      val ctx1 = BuilderContext()
      val ctx2 = ctx1.withCurriedFunctions(null)

      ctx1 should not be theSameInstanceAs(ctx2)
      ctx1.curriedFunctions shouldBe None
      ctx2.curriedFunctions shouldBe defined
    }

    "create new instance when decorating with actor behaviors context" in {
      val ctx1 = BuilderContext()
      val ctx2 = ctx1.withActorBehaviorsContext(ActorBehaviorsContext())

      ctx1 should not be theSameInstanceAs(ctx2)
      ctx1.actorBehaviorsContext shouldBe None
      ctx2.actorBehaviorsContext shouldBe defined
    }
  }

  // ========== FIELD ACCUMULATION TESTS ==========

  "BuilderContext field accumulation" should {

    "start with all fields None" in {
      val ctx = BuilderContext()

      ctx.config shouldBe None
      ctx.coreConfig shouldBe None
      ctx.actorSystem shouldBe None
      ctx.queueActorRef shouldBe None
      ctx.curriedFunctions shouldBe None
      ctx.actorBehaviorsContext shouldBe None
    }

    "accumulate config fields correctly" in {
      val ctx = BuilderContext()
        .withConfig(null, null)

      ctx.config shouldBe defined
      ctx.coreConfig shouldBe defined
      ctx.actorSystem shouldBe None
      ctx.queueActorRef shouldBe None
      ctx.curriedFunctions shouldBe None
    }

    "accumulate actor system fields correctly" in {
      val ctx = BuilderContext()
        .withConfig(null, null)
        .withActorSystem(null)

      ctx.config shouldBe defined
      ctx.coreConfig shouldBe defined
      ctx.actorSystem shouldBe defined
      ctx.queueActorRef shouldBe None
      ctx.curriedFunctions shouldBe None
    }

    "accumulate queue actor ref correctly" in {
      val ctx = BuilderContext()
        .withConfig(null, null)
        .withActorSystem(null)
        .withQueueActorRef(null)

      ctx.config shouldBe defined
      ctx.coreConfig shouldBe defined
      ctx.actorSystem shouldBe defined
      ctx.queueActorRef shouldBe defined
      ctx.curriedFunctions shouldBe None
    }

    "accumulate all fields through full lifecycle" in {
      val ctx = BuilderContext()
        .withConfig(null, null)
        .withActorSystem(null)
        .withQueueActorRef(null)
        .withCurriedFunctions(null)
        .withActorBehaviorsContext(ActorBehaviorsContext())

      ctx.config shouldBe defined
      ctx.coreConfig shouldBe defined
      ctx.actorSystem shouldBe defined
      ctx.queueActorRef shouldBe defined
      ctx.curriedFunctions shouldBe defined
      ctx.actorBehaviorsContext shouldBe defined
    }
  }

  // ========== toServiceContext() SAFETY TESTS ==========

  "BuilderContext.toServiceContext()" should {

    "throw IllegalStateException when actorSystem missing" in {
      val ctx = BuilderContext()
        .withConfig(null, null)
        .withQueueActorRef(null)
        .withCurriedFunctions(null)

      val exception = intercept[IllegalStateException] {
        ctx.toServiceContext
      }

      exception.getMessage should include("ActorSystem not initialized")
    }

    "throw IllegalStateException when config missing" in {
      val ctx = BuilderContext()
        .withActorSystem(null)
        .withQueueActorRef(null)
        .withCurriedFunctions(null)

      val exception = intercept[IllegalStateException] {
        ctx.toServiceContext
      }

      exception.getMessage should include("Config not initialized")
    }

    "throw IllegalStateException when coreConfig missing" in {
      val ctx = BuilderContext(
        config = Some(null),
        coreConfig = None,
        actorSystem = Some(null),
        queueActorRef = Some(null),
        curriedFunctions = Some(null)
      )

      val exception = intercept[IllegalStateException] {
        ctx.toServiceContext
      }

      exception.getMessage should include("CoreConfig not initialized")
    }

    "throw IllegalStateException when queueActorRef missing" in {
      val ctx = BuilderContext()
        .withConfig(null, null)
        .withActorSystem(null)
        .withCurriedFunctions(null)

      val exception = intercept[IllegalStateException] {
        ctx.toServiceContext
      }

      exception.getMessage should include("QueueActorRef not initialized")
    }

    "throw IllegalStateException when curriedFunctions missing" in {
      val ctx = BuilderContext()
        .withConfig(null, null)
        .withActorSystem(null)
        .withQueueActorRef(null)

      val exception = intercept[IllegalStateException] {
        ctx.toServiceContext
      }

      exception.getMessage should include("CurriedFunctions not initialized")
    }

    "succeed when all required fields populated" in {
      val ctx = BuilderContext()
        .withConfig(null, null)
        .withActorSystem(null)
        .withQueueActorRef(null)
        .withCurriedFunctions(null)

      noException should be thrownBy {
        ctx.toServiceContext
      }
    }

    "create ServiceContext with correct field mappings" in {
      val ctx = BuilderContext()
        .withConfig(null, null)
        .withActorSystem(null)
        .withQueueActorRef(null)
        .withCurriedFunctions(null)

      val serviceContext = ctx.toServiceContext

      serviceContext shouldBe a[ServiceContext]
      serviceContext.actorSystem shouldBe ctx.actorSystem.get
      serviceContext.config shouldBe ctx.config.get
      serviceContext.coreConfig shouldBe ctx.coreConfig.get
      serviceContext.queueActorRef shouldBe ctx.queueActorRef.get
      serviceContext.curriedFunctions shouldBe ctx.curriedFunctions.get
    }
  }

  // ========== DECORATOR METHOD CHAINING TESTS ==========

  "BuilderContext decorator method chaining" should {

    "support fluent chaining in any order" in {
      val ctx1 = BuilderContext()
        .withConfig(null, null)
        .withActorSystem(null)
        .withQueueActorRef(null)
        .withCurriedFunctions(null)

      val ctx2 = BuilderContext()
        .withCurriedFunctions(null)
        .withQueueActorRef(null)
        .withActorSystem(null)
        .withConfig(null, null)

      // Both should have all fields populated
      ctx1.config shouldBe defined
      ctx1.actorSystem shouldBe defined
      ctx1.queueActorRef shouldBe defined
      ctx1.curriedFunctions shouldBe defined

      ctx2.config shouldBe defined
      ctx2.actorSystem shouldBe defined
      ctx2.queueActorRef shouldBe defined
      ctx2.curriedFunctions shouldBe defined
    }

    "preserve previous decorations when adding new fields" in {
      val ctx = BuilderContext()
        .withConfig(null, null)

      ctx.config shouldBe defined
      ctx.coreConfig shouldBe defined

      val ctx2 = ctx.withActorSystem(null)

      // Previous fields should still be present
      ctx2.config shouldBe defined
      ctx2.coreConfig shouldBe defined
      // New field should be added
      ctx2.actorSystem shouldBe defined
    }

    "allow overwriting fields via copy" in {
      val ctx1 = BuilderContext()
        .withConfig(null, null)

      ctx1.config shouldBe defined

      // Manually overwrite via copy (not a builder method, but tests case class immutability)
      val ctx2 = ctx1.copy(config = None)

      ctx2.config shouldBe None
      ctx1.config shouldBe defined // Original unchanged
    }
  }

  // ========== EDGE CASES ==========

  "BuilderContext edge cases" should {

    "allow actorBehaviorsContext to remain None" in {
      val ctx = BuilderContext()
        .withConfig(null, null)
        .withActorSystem(null)
        .withQueueActorRef(null)
        .withCurriedFunctions(null)

      ctx.actorBehaviorsContext shouldBe None

      // Should still be able to create ServiceContext
      noException should be thrownBy {
        ctx.toServiceContext
      }
    }

    "support adding actorBehaviorsContext" in {
      val behaviors = ActorBehaviorsContext()
      val ctx = BuilderContext()
        .withActorBehaviorsContext(behaviors)

      ctx.actorBehaviorsContext shouldBe Some(behaviors)
    }
  }
}
