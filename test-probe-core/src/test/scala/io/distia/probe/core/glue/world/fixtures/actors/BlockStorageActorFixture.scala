package io.distia.probe.core.glue.world.fixtures.actors

import io.distia.probe.core.glue.world.ActorWorld
import io.distia.probe.common.models.BlockStorageDirective
import io.distia.probe.core.models.BlockStorageCommands.BlockStorageCommand
import org.apache.pekko.actor.typed.ActorRef


trait BlockStorageActorFixture {
  this: ActorWorld =>

  var blockStorageActor: Option[ActorRef[BlockStorageCommand]] = None
  var lastBlockStorageDirective: Option[BlockStorageDirective] = None
  var shouldFailOnBlockFetch: Boolean = false
  var shouldFailOnBlockLoad: Boolean = false
  
}
