package io.distia.probe
package core
package builder

/**
 * Type-safe interface config without concrete dependency
 *
 * This trait allows BuilderContext to reference interface configurations
 * without creating a circular dependency on the interfaces module.
 *
 * Interface modules extend this trait with their concrete configs.
 *
 * Design:
 * - Core module defines this trait (no dependency on interfaces)
 * - Interfaces module implements trait with concrete config
 * - BuilderContext uses trait type (type-safe, no circular dependency)
 */
trait InterfaceConfig {
  /**
   * Type of interface (e.g., "REST", "CLI", "gRPC")
   */
  def interfaceType: String

  /**
   * Whether this interface is enabled
   */
  def isEnabled: Boolean
}
