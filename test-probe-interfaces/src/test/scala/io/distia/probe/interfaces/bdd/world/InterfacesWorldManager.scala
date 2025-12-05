package io.distia.probe.interfaces.bdd.world

/**
 * Thread-safe singleton manager for InterfacesWorld instances
 *
 * Provides per-thread InterfacesWorld for scenario isolation in parallel test execution.
 *
 * Pattern: EXACT copy of WorldManager pattern from test-probe-core.
 *
 * Usage in step definitions:
 * {{{
 * class MySteps extends ScalaDsl with EN with Matchers {
 *   private def world = InterfacesWorldManager.world
 *
 *   Given("...") { () =>
 *     world.someField = someValue
 *   }
 * }
 * }}}
 *
 * Thread Safety:
 * - ThreadLocal ensures one InterfacesWorld per thread
 * - Each scenario runs in isolation
 * - Safe for parallel test execution
 */
object InterfacesWorldManager {

  // ThreadLocal storage for InterfacesWorld (one per thread/scenario)
  private val worldPerThread = new ThreadLocal[InterfacesWorld] {
    override def initialValue(): InterfacesWorld = new InterfacesWorld
  }

  /**
   * Get current thread's InterfacesWorld
   * Creates new instance on first access (lazy initialization)
   */
  def world: InterfacesWorld = worldPerThread.get()

  /**
   * Shutdown current thread's InterfacesWorld
   * Called by ComponentTestHooks in After hook
   */
  def shutdownWorld(): Unit = {
    val w = worldPerThread.get()
    if (w != null) {
      w.shutdown()
      worldPerThread.remove()
    }
  }
}
