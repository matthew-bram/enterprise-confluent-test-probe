package io.distia.probe
package core
package builder


/**
 * Service functions context for actor consumption
 *
 * This bundle aggregates all service functions (vault and storage) that actors need
 * to perform business logic without depending on service module implementations.
 *
 * Pattern: Dependency Injection via Function Bundle
 * - Service modules implement business logic (ProbeVaultService, ProbeStorageService)
 * - DefaultActorSystem extracts functions via fromService() factory methods
 * - Functions bundled into ServiceFunctionsContext
 * - Context passed down actor hierarchy: GuardianActor → QueueActor → TestExecutionActor → child actors
 * - Child actors (VaultActor, BlockStorageActor) receive curried functions
 *
 * Design:
 * - Decoupling: Actors don't depend on service modules, only function signatures
 * - Testability: Actors can be tested with mock function implementations
 * - Flexibility: Service implementations can be swapped at runtime
 *
 * Lifecycle:
 * 1. DefaultActorSystem.initialize() extracts functions from service modules
 * 2. Functions bundled into ServiceFunctionsContext via apply()
 * 3. Context stored in BuilderContext.serviceFunctionsContext
 * 4. Context passed to GuardianActor during spawn
 * 5. GuardianActor passes to QueueActor, which passes to TestExecutionActor
 * 6. TestExecutionActor distributes specific functions to child actors
 *
 * @param vault Vault service curried functions (security credentials)
 * @param storage Storage service curried functions (S3/jimfs operations)
 * @see VaultServiceFunctions for vault function details
 * @see StorageServiceFunctions for storage function details
 */
case class ServiceFunctionsContext(
  vault: VaultServiceFunctions,
  storage: StorageServiceFunctions
)

object ServiceFunctionsContext {
  /**
   * Create ServiceFunctionsContext from individual service functions
   *
   * Called by DefaultActorSystem.initialize() after extracting functions
   * from service modules via fromService() factory methods.
   *
   * @param vaultFunctions Vault service functions extracted from ProbeVaultService
   * @param storageFunctions Storage service functions extracted from ProbeStorageService
   * @return ServiceFunctionsContext bundle for actor consumption
   */
  def apply(
    vaultFunctions: VaultServiceFunctions,
    storageFunctions: StorageServiceFunctions
  ): ServiceFunctionsContext = new ServiceFunctionsContext(vaultFunctions, storageFunctions)
}
