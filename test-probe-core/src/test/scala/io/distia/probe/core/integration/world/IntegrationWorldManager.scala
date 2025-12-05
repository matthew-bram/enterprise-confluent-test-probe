package io.distia.probe
package core
package integration
package world

/**
 * Singleton manager for IntegrationWorld instances.
 *
 * Provides thread-local access to IntegrationWorld for step definitions.
 * Each scenario gets its own IntegrationWorld instance.
 *
 * Usage from step definitions:
 * {{{
 *   private def world: IntegrationWorld = IntegrationWorldManager.getWorld()
 * }}}
 *
 * Lifecycle:
 * 1. Before scenario: setWorld(new IntegrationWorld)
 * 2. During scenario: getWorld() from step definitions
 * 3. After scenario: clearWorld()
 */
object IntegrationWorldManager:

  private val threadLocalWorld: ThreadLocal[IntegrationWorld] = new ThreadLocal[IntegrationWorld]()

  /**
   * Get the current IntegrationWorld instance.
   *
   * @throws IllegalStateException if no world is set for current thread
   */
  def getWorld(): IntegrationWorld =
    val world = threadLocalWorld.get()
    if world == null then
      throw IllegalStateException(
        "IntegrationWorld not initialized. Ensure BeforeAll hook has run."
      )
    world

  /**
   * Set the IntegrationWorld for the current thread.
   * Called in BeforeAll hook.
   */
  def setWorld(world: IntegrationWorld): Unit =
    threadLocalWorld.set(world)

  /**
   * Clear the IntegrationWorld for the current thread.
   * Called in AfterAll hook after cleanup.
   */
  def clearWorld(): Unit =
    threadLocalWorld.remove()

  /**
   * Check if a world is set for the current thread.
   */
  def hasWorld: Boolean =
    threadLocalWorld.get() != null
