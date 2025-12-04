package io.distia.probe
package core
package builder

import builder.modules.ProbeStorageService
import io.distia.probe.common.models.BlockStorageDirective

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

/**
 * Curried storage service functions for actor consumption
 *
 * Pattern: Same as VaultServiceFunctions - pure function bundle that
 * decouples actors from service module implementations.
 *
 * These functions are extracted from ProbeStorageService implementations
 * (LocalBlockStorageService, AwsBlockStorageService, etc.) by DefaultActorSystem
 * and passed down the actor hierarchy via ServiceFunctionsContext.
 *
 * @param fetchFromBlockStorage Curried function to fetch test data from block storage to jimfs
 * @param loadToBlockStorage Curried function to upload test evidence from jimfs to block storage
 */
case class StorageServiceFunctions(
  fetchFromBlockStorage: (UUID, String) => ExecutionContext ?=> Future[BlockStorageDirective],
  loadToBlockStorage: (UUID, String, String) => ExecutionContext ?=> Future[Unit]
)

object StorageServiceFunctions {
  /**
   * Extract curried functions from ProbeStorageService module
   *
   * Called by DefaultActorSystem.initialize() to extract service functions
   * from the initialized ProbeStorageService instance. These functions are
   * then bundled into ServiceFunctionsContext and passed to actors.
   *
   * Pattern:
   * 1. Service module implements ProbeStorageService trait with business methods
   * 2. DefaultActorSystem calls fromService() to extract curried functions
   * 3. Functions are bundled into ServiceFunctionsContext
   * 4. Context is passed to GuardianActor → QueueActor → TestExecutionActor → BlockStorageActor
   * 5. BlockStorageActor receives the curried functions in its constructor
   *
   * @param service The initialized ProbeStorageService instance (LocalBlockStorageService, AwsBlockStorageService, etc.)
   * @return Function bundle for actor consumption
   */
  def fromService(service: ProbeStorageService): StorageServiceFunctions =
    StorageServiceFunctions(
      fetchFromBlockStorage = service.fetchFromBlockStorage,
      loadToBlockStorage = service.loadToBlockStorage
    )
}
