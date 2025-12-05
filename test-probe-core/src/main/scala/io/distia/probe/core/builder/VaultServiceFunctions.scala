package io.distia.probe
package core
package builder

import io.distia.probe.common
import builder.modules.ProbeVaultService
import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Curried vault service functions for actor consumption
 *
 * This function bundle decouples actors from vault service module implementations
 * (LocalVaultService, AwsVaultService, AzureVaultService, GcpVaultService).
 *
 * Pattern: Function Extraction via Factory
 * 1. Service module implements ProbeVaultService trait with business methods
 * 2. DefaultActorSystem calls VaultServiceFunctions.fromService() to extract functions
 * 3. Functions are bundled into ServiceFunctionsContext
 * 4. Context passed to GuardianActor → QueueActor → TestExecutionActor → VaultActor
 * 5. VaultActor receives curried functions in its constructor
 *
 * Design:
 * - Pure Functions: No service module dependencies in actor code
 * - Testability: Actors can be tested with mock functions
 * - Flexibility: Different vault implementations can be swapped at runtime
 *
 * @param fetchSecurityDirectives Curried function to fetch Kafka security directives from vault
 * @see ProbeVaultService for the service trait
 * @see StorageServiceFunctions for the storage equivalent
 */
case class VaultServiceFunctions(
  fetchSecurityDirectives: BlockStorageDirective => ExecutionContext ?=> Future[List[KafkaSecurityDirective]]
)

object VaultServiceFunctions {
  /**
   * Extract curried functions from ProbeVaultService module
   *
   * Called by DefaultActorSystem.initialize() to extract service functions
   * from the initialized ProbeVaultService instance. These functions are
   * then bundled into ServiceFunctionsContext and passed to actors.
   *
   * Pattern:
   * 1. Service module implements ProbeVaultService trait with business methods
   * 2. DefaultActorSystem calls fromService() to extract curried functions
   * 3. Functions are bundled into ServiceFunctionsContext
   * 4. Context is passed to GuardianActor → QueueActor → TestExecutionActor → VaultActor
   * 5. VaultActor receives the curried functions in its constructor
   *
   * @param service The initialized ProbeVaultService instance (LocalVaultService, AwsVaultService, etc.)
   * @return Function bundle for actor consumption
   */
  def fromService(service: ProbeVaultService): VaultServiceFunctions =
    VaultServiceFunctions(
      fetchSecurityDirectives = service.fetchSecurityDirectives
    )
}
