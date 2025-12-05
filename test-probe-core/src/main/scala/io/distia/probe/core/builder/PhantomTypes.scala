package io.distia.probe
package core
package builder

/**
 * Phantom type machinery for compile-time builder validation
 *
 * This file implements a type-level set that tracks which builder modules
 * have been applied to the ServiceDSL. The compiler enforces that build()
 * can only be called when all required modules are present.
 *
 * Key features:
 * - Order-independent: .withConfig().withActorSystem() === .withActorSystem().withConfig()
 * - Duplicate prevention: Adding the same module twice has no effect on the type
 * - Zero runtime cost: All validation happens at compile time
 *
 * The pattern uses three core abstractions:
 * 1. Feature markers (ProbeConfig, ProbeActorSystem, ProbeContext)
 * 2. FeatureSet type-level list (Nil, Cons)
 * 3. Typeclasses (Contains, AddIfAbsent) for compile-time proofs
 */

// ============================================================================
// Feature Markers
// ============================================================================

/**
 * Base trait for all builder module feature markers
 * Non-sealed to allow extension from modules package
 */
trait Feature

// ============================================================================
// Type-Level Set (FeatureSet)
// ============================================================================

/**
 * Type-level list representing a set of features
 * Used to track which modules have been added to the builder
 */
trait FeatureSet

/**
 * Empty feature set (no modules added yet)
 */
sealed trait Nil extends FeatureSet

/**
 * Non-empty feature set (cons cell: head feature + tail set)
 *
 * @tparam H Feature at the head of the list
 * @tparam T Remaining features (tail of the list)
 */
final case class Cons[H <: Feature, T <: FeatureSet]() extends FeatureSet

// ============================================================================
// Contains Typeclass
// ============================================================================

/**
 * Typeclass proving that a feature F exists in feature set S
 *
 * The compiler uses given resolution to prove that a feature is present.
 * If no given can be found, compilation fails with a clear error message.
 *
 * Example:
 * {{{
 * def build()(using hasConfig: Contains[Features, ProbeConfig]) = ???
 * // Compiler will only find hasConfig if ProbeConfig is in Features
 * }}}
 */
trait Contains[S <: FeatureSet, F <: Feature]

object Contains {

  /**
   * Base case: Feature F is at the head of the list
   *
   * If we're looking for H and the list starts with H, we found it.
   */
  given headContains[H <: Feature, T <: FeatureSet]: Contains[Cons[H, T], H] =
  new Contains[Cons[H, T], H] {}

  /**
   * Recursive case: Feature F is somewhere in the tail
   *
   * If we're looking for F and the head is H (not F), recursively search the tail.
   * This given requires that Contains[T, F] exists (proven recursively).
   */
  given tailContains[H <: Feature, T <: FeatureSet, F <: Feature]
  (using ev: Contains[T, F]): Contains[Cons[H, T], F] =
  new Contains[Cons[H, T], F] {}
}

// ============================================================================
// AddIfAbsent Typeclass
// ============================================================================

/**
 * Typeclass for adding a feature to a set only if it's not already present
 *
 * This enables duplicate prevention: calling .withConfig() twice doesn't
 * add ProbeConfig to the type-level set twice.
 *
 * The Out type member represents the resulting feature set after the add operation.
 *
 * Example:
 * {{{
 * AddIfAbsent[Nil, ProbeConfig]#Out                     = Cons[ProbeConfig, Nil]
 * AddIfAbsent[Cons[ProbeConfig, Nil], ProbeConfig]#Out  = Cons[ProbeConfig, Nil] (unchanged)
 * AddIfAbsent[Cons[ProbeConfig, Nil], ProbeActorSystem]#Out = Cons[ProbeActorSystem, Cons[ProbeConfig, Nil]]
 * }}}
 */
trait AddIfAbsent[S <: FeatureSet, F <: Feature]:
  type Out <: FeatureSet

object AddIfAbsent {

  /**
   * Type alias for AddIfAbsent with explicit Out type parameter
   * Makes it easier to use in given parameter lists
   */
  type Aux[S <: FeatureSet, F <: Feature, Out0 <: FeatureSet] =
    AddIfAbsent[S, F] { type Out = Out0 }

  /**
   * Case 1: Feature already at head - don't add duplicate
   *
   * If we're trying to add H to a list that starts with H, return the list unchanged.
   */
  given alreadyPresent[H <: Feature, T <: FeatureSet]: Aux[Cons[H, T], H, Cons[H, T]] =
    new AddIfAbsent[Cons[H, T], H] { type Out = Cons[H, T] }

  /**
   * Case 2: Recurse down the list
   *
   * If the head is not the feature we're adding, recursively try to add to the tail.
   * The resulting set is Cons[H, OutT] where OutT is the result of adding F to T.
   */
  given recurse[H <: Feature, T <: FeatureSet, F <: Feature, OutT <: FeatureSet](
    using tail: Aux[T, F, OutT]
  ): Aux[Cons[H, T], F, Cons[H, OutT]] =
  new AddIfAbsent[Cons[H, T], F] { type Out = Cons[H, OutT] }

  /**
   * Case 3: Base case - add feature to empty list
   *
   * If we're adding F to an empty list, return Cons[F, Nil].
   */
  given addToEmpty[F <: Feature]: Aux[Nil, F, Cons[F, Nil]] = new AddIfAbsent[Nil, F] { type Out = Cons[F, Nil] }
}