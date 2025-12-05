package io.distia.probe
package core
package builder

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

private[core] class ActorBehaviorsContextSpec extends AnyWordSpec with Matchers {

  // ========== MOCK IMPLEMENTATIONS ==========

  /**
   * Mock behavior for testing - using null since we're only testing the context structure
   * The actual behavior type is covariant and we don't need real behaviors for these tests
   */
  private def mockBehavior = null

  // ========== EMPTY DEFAULT TESTS ==========

  "ActorBehaviorsContext default initialization" should {

    "start with empty behaviors list" in {
      val context = ActorBehaviorsContext()

      context.behaviors shouldBe empty
      context.behaviors should have size 0
    }

    "support Nil default" in {
      val context = ActorBehaviorsContext(Nil)

      context.behaviors shouldBe Nil
      context.behaviors should have size 0
    }
  }

  // ========== IMMUTABILITY TESTS ==========

  "ActorBehaviorsContext immutability" should {

    "create new instance when adding behavior" in {
      val ctx1 = ActorBehaviorsContext()
      val config = ExternalBehaviorConfig(
        routerPoolSize = 5,
        actorNamePrefix = "rest-client",
        externalAddress = "rest-api",
        behavior = mockBehavior
      )
      val ctx2 = ctx1.addBehavior(config)

      ctx1 should not be theSameInstanceAs(ctx2)
      ctx1.behaviors shouldBe empty
      ctx2.behaviors should have size 1
    }

    "preserve original when adding multiple behaviors" in {
      val ctx1 = ActorBehaviorsContext()
      val config1 = ExternalBehaviorConfig(5, "client-1", "addr-1", mockBehavior)
      val ctx2 = ctx1.addBehavior(config1)

      val config2 = ExternalBehaviorConfig(10, "client-2", "addr-2", mockBehavior)
      val ctx3 = ctx2.addBehavior(config2)

      // Original contexts should be unchanged
      ctx1.behaviors should have size 0
      ctx2.behaviors should have size 1
      ctx3.behaviors should have size 2
    }
  }

  // ========== BEHAVIOR ADDITION TESTS ==========

  "ActorBehaviorsContext.addBehavior()" should {

    "add single behavior correctly" in {
      val context = ActorBehaviorsContext()
      val config = ExternalBehaviorConfig(
        routerPoolSize = 5,
        actorNamePrefix = "rest-client",
        externalAddress = "rest-api",
        behavior = mockBehavior
      )
      val updated = context.addBehavior(config)

      updated.behaviors should have size 1
      updated.behaviors.head shouldBe config
    }

    "add multiple behaviors correctly" in {
      val context = ActorBehaviorsContext()
      val config1 = ExternalBehaviorConfig(5, "client-1", "addr-1", mockBehavior)
      val config2 = ExternalBehaviorConfig(10, "client-2", "addr-2", mockBehavior)
      val config3 = ExternalBehaviorConfig(15, "client-3", "addr-3", mockBehavior)

      val updated = context
        .addBehavior(config1)
        .addBehavior(config2)
        .addBehavior(config3)

      updated.behaviors should have size 3
      updated.behaviors should contain allOf (config1, config2, config3)
    }

    "preserve LIFO order (due to :: prepending)" in {
      val context = ActorBehaviorsContext()
      val config1 = ExternalBehaviorConfig(5, "client-1", "addr-1", mockBehavior)
      val config2 = ExternalBehaviorConfig(10, "client-2", "addr-2", mockBehavior)
      val config3 = ExternalBehaviorConfig(15, "client-3", "addr-3", mockBehavior)

      val updated = context
        .addBehavior(config1)
        .addBehavior(config2)
        .addBehavior(config3)

      // Due to :: prepending, last added is first in list
      updated.behaviors.head shouldBe config3
      updated.behaviors(1) shouldBe config2
      updated.behaviors(2) shouldBe config1
    }

    "allow duplicate behaviors (no uniqueness constraint)" in {
      val context = ActorBehaviorsContext()
      val config = ExternalBehaviorConfig(5, "client", "addr", mockBehavior)

      val updated = context
        .addBehavior(config)
        .addBehavior(config)
        .addBehavior(config)

      updated.behaviors should have size 3
      updated.behaviors.distinct should have size 1
    }
  }

  // ========== ExternalBehaviorConfig TESTS ==========

  "ExternalBehaviorConfig case class" should {

    "create with all required fields" in {
      val config = ExternalBehaviorConfig(
        routerPoolSize = 10,
        actorNamePrefix = "grpc-client",
        externalAddress = "grpc-api",
        behavior = mockBehavior
      )

      config.routerPoolSize shouldBe 10
      config.actorNamePrefix shouldBe "grpc-client"
      config.externalAddress shouldBe "grpc-api"
      config.behavior shouldBe mockBehavior
    }

    "support named parameter construction" in {
      val config = ExternalBehaviorConfig(
        behavior = mockBehavior,
        externalAddress = "db-adapter",
        actorNamePrefix = "db-client",
        routerPoolSize = 3
      )

      config.routerPoolSize shouldBe 3
      config.actorNamePrefix shouldBe "db-client"
      config.externalAddress shouldBe "db-adapter"
    }

    "support copy for modifications" in {
      val config1 = ExternalBehaviorConfig(5, "client", "addr", mockBehavior)
      val config2 = config1.copy(routerPoolSize = 20)

      config1.routerPoolSize shouldBe 5
      config2.routerPoolSize shouldBe 20
      config2.actorNamePrefix shouldBe "client"
      config2.externalAddress shouldBe "addr"
    }

    "support equality comparison for same parameters" in {
      val config1 = ExternalBehaviorConfig(5, "client", "addr", mockBehavior)
      val config2 = ExternalBehaviorConfig(5, "client", "addr", mockBehavior)

      // Note: Since mockBehavior is null for both, and other params are the same,
      // case class equality will consider them equal
      config1 shouldBe config2
    }

    "support inequality comparison for different parameters" in {
      val config1 = ExternalBehaviorConfig(5, "client-1", "addr", mockBehavior)
      val config2 = ExternalBehaviorConfig(10, "client-2", "addr", mockBehavior)

      config1 should not equal config2
    }

    "allow different pool sizes" in {
      val config1 = ExternalBehaviorConfig(1, "single", "addr", mockBehavior)
      val config2 = ExternalBehaviorConfig(100, "large-pool", "addr", mockBehavior)

      config1.routerPoolSize shouldBe 1
      config2.routerPoolSize shouldBe 100
    }

    "allow arbitrary string prefixes and addresses" in {
      val config = ExternalBehaviorConfig(
        routerPoolSize = 5,
        actorNamePrefix = "my-custom-actor-2024",
        externalAddress = "https://api.example.com/v1",
        behavior = mockBehavior
      )

      config.actorNamePrefix shouldBe "my-custom-actor-2024"
      config.externalAddress shouldBe "https://api.example.com/v1"
    }
  }

  // ========== FLUENT CHAINING TESTS ==========

  "ActorBehaviorsContext fluent chaining" should {

    "support multiple addBehavior calls in chain" in {
      val config1 = ExternalBehaviorConfig(5, "client-1", "addr-1", mockBehavior)
      val config2 = ExternalBehaviorConfig(10, "client-2", "addr-2", mockBehavior)

      val context = ActorBehaviorsContext()
        .addBehavior(config1)
        .addBehavior(config2)

      context.behaviors should have size 2
    }

    "allow starting from non-empty context" in {
      val config1 = ExternalBehaviorConfig(5, "client-1", "addr-1", mockBehavior)
      val config2 = ExternalBehaviorConfig(10, "client-2", "addr-2", mockBehavior)

      val initialContext = ActorBehaviorsContext(List(config1))
      val updated = initialContext.addBehavior(config2)

      updated.behaviors should have size 2
      updated.behaviors should contain(config1)
      updated.behaviors should contain(config2)
    }
  }

  // ========== EDGE CASES ==========

  "ActorBehaviorsContext edge cases" should {

    "handle zero behaviors" in {
      val context = ActorBehaviorsContext()

      context.behaviors shouldBe empty
      context.behaviors.isEmpty shouldBe true
      context.behaviors.size shouldBe 0
    }

    "handle single behavior" in {
      val config = ExternalBehaviorConfig(1, "single", "addr", mockBehavior)
      val context = ActorBehaviorsContext().addBehavior(config)

      context.behaviors should have size 1
      context.behaviors.head shouldBe config
    }

    "handle many behaviors (scalability test)" in {
      val configs = (1 to 50).map { i =>
        ExternalBehaviorConfig(i, s"client-$i", s"addr-$i", mockBehavior)
      }

      val context = configs.foldLeft(ActorBehaviorsContext()) { (ctx, config) =>
        ctx.addBehavior(config)
      }

      context.behaviors should have size 50
    }
  }
}
