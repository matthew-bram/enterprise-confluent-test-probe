package io.distia.probe.core.glue.world.fixtures.actors

import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.common.models.KafkaSecurityDirective
import io.distia.probe.core.models.VaultCommands.VaultCommand
import org.apache.pekko.actor.typed.ActorRef


trait VaultActorFixture {
  this: ActorWorld =>
  
  var vaultActor: Option[ActorRef[VaultCommand]] = None
  var shouldFailOnVaultFetch: Boolean = false
  var lastKafkaSecurityDirectives: List[KafkaSecurityDirective] = List.empty
  

}
