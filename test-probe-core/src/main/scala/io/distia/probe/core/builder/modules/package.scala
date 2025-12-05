package io.distia.probe
package core
package builder

import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective, ProbeExternalActorCommand}
import io.distia.probe.core.models.TestExecutionResult
import config.CoreConfig

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

package object modules {
  
  trait ProbeConfig extends Feature with BuilderModule {
    def validate(implicit ec: ExecutionContext): Future[CoreConfig]
  }
  
  trait ProbeActorSystem extends Feature with BuilderModule
  
  trait ProbeActorBehavior extends BuilderModule
  
  trait ProbeStorageService extends Feature with BuilderModule {
    def fetchFromBlockStorage(testId: UUID, bucket: String)(implicit ec: ExecutionContext): Future[BlockStorageDirective]
    def loadToBlockStorage(testId: UUID, bucket: String, evidence: String)(implicit ec: ExecutionContext): Future[Unit]
  }
  
  trait ProbeVaultService extends Feature with BuilderModule {
    def fetchSecurityDirectives(
      directive: BlockStorageDirective
    )(implicit ec: ExecutionContext): Future[List[KafkaSecurityDirective]]
    def shutdown()(implicit ec: ExecutionContext): Future[Unit]
  }

  trait ProbeInterface extends Feature with BuilderModule {
    def setCurriedFunctions(functions: ServiceInterfaceFunctions): Unit
    def shutdown()(implicit ec: ExecutionContext): Future[Unit]
  }
}
