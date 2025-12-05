package io.distia.probe
package core
package models

import org.apache.pekko.actor.typed.ActorRef

import java.util.UUID

sealed trait ServiceResponse

private[core] case object ActorSystemInitializationSuccess extends ServiceResponse
private[core] case class ActorSystemInitializationFailure(exception: Exception) extends ServiceResponse
private[core] case class QueueActorReference(queueActorRef: ActorRef[QueueCommands.QueueCommand]) extends ServiceResponse

private[probe] case class InitializeTestResponse(
  testId: UUID,
  message: String = "Test initialized - upload files to {bucket}/{testId}/"
) extends ServiceResponse

private[probe] case class StartTestResponse(
  testId: UUID,
  accepted: Boolean,
  testType: Option[String],
  message: String = "Test accepted and will be executed"
) extends ServiceResponse

private[probe] case class TestStatusResponse(
  testId: UUID,
  state: String,
  bucket: Option[String],
  testType: Option[String],
  startTime: Option[String],
  endTime: Option[String],
  success: Option[Boolean],
  error: Option[String]
) extends ServiceResponse

private[probe] case class QueueStatusResponse(
  totalTests: Int,
  setupCount: Int,
  loadingCount: Int,
  loadedCount: Int,
  testingCount: Int,
  completedCount: Int,
  exceptionCount: Int,
  currentlyTesting: Option[UUID]
) extends ServiceResponse

private[probe] case class TestCancelledResponse(
  testId: UUID,
  cancelled: Boolean,
  message: Option[String] = None
) extends ServiceResponse

private[probe] case class QueueActorInterfaceResponse(
  queue: ActorRef[QueueCommands.QueueCommand]
) extends ServiceResponse
