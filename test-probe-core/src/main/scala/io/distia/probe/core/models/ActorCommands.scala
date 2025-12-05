package io.distia.probe
package core
package models

import java.time.Instant
import java.util.UUID

import org.apache.kafka.common.header.Headers
import org.apache.pekko.actor.typed.ActorRef

import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}

import actors.TestExecutionState
import pubsub.models.{ConsumedResult, ProduceResult}

private[probe] object GuardianCommands {
  sealed trait GuardianCommand
  case class Initialize(replyTo: ActorRef[ServiceResponse]) extends GuardianCommand
  case class GetQueueActor(replyTo: ActorRef[ServiceResponse]) extends GuardianCommand
}

private[core] object QueueCommands {
  sealed trait QueueCommand
  case class InitializeTestRequest(replyTo: ActorRef[ServiceResponse]) extends QueueCommand
  case class StartTestRequest(testId: UUID, bucket: String, testType: Option[String], replyTo: ActorRef[ServiceResponse]) extends QueueCommand
  case class TestStatusRequest(testId: UUID, replyTo: ActorRef[ServiceResponse]) extends QueueCommand
  case class QueueStatusRequest(testId: Option[UUID], replyTo: ActorRef[ServiceResponse]) extends QueueCommand
  case class CancelRequest(testId: UUID, replyTo: ActorRef[ServiceResponse]) extends QueueCommand
  case class TestInitialized(testId: UUID) extends QueueCommand
  case class TestLoading(testId: UUID) extends QueueCommand
  case class TestLoaded(testId: UUID) extends QueueCommand
  case class TestStarted(testId: UUID) extends QueueCommand
  case class TestCompleted(testId: UUID) extends QueueCommand
  case class TestException(testId: UUID, exception: ProbeExceptions) extends QueueCommand
  case class TestStopping(testId: UUID) extends QueueCommand
  case class TestStatusResponse(
    testId: UUID,
    state: TestExecutionState,
    bucket: Option[String],
    testType: Option[String],
    startTime: Option[Instant] = None,
    endTime: Option[Instant] = None,
    success: Option[Boolean] = None,
    error: Option[ProbeExceptions] = None
  ) extends QueueCommand
}

private[core] object TestExecutionCommands {
  sealed trait TestExecutionCommand

  case class InInitializeTestRequest(testId: UUID, replyTo: ActorRef[ServiceResponse]) extends TestExecutionCommand
  case class InStartTestRequest(
    testId: UUID,
    bucket: String,
    testType: Option[String],
    replyTo: ActorRef[ServiceResponse]) extends TestExecutionCommand
  case class InCancelRequest(testId: UUID, replyTo: ActorRef[ServiceResponse]) extends TestExecutionCommand
  case class GetStatus(testId: UUID, replyTo: ActorRef[ServiceResponse]) extends TestExecutionCommand
  case object TrnSetup extends TestExecutionCommand
  case object TrnLoading extends TestExecutionCommand
  case object TrnLoaded extends TestExecutionCommand
  case object TrnTesting extends TestExecutionCommand
  case object TrnComplete extends TestExecutionCommand
  case class TrnException(exception: ProbeExceptions) extends TestExecutionCommand
  case object TrnShutdown extends TestExecutionCommand
  case object TrnPoisonPill extends TestExecutionCommand
  case class StartTesting(testId: UUID) extends TestExecutionCommand

  case class ChildGoodToGo(testId: UUID, child: ActorRef[_]) extends TestExecutionCommand
  case class BlockStorageFetched(testId: UUID, blockStorageDirective: BlockStorageDirective) extends TestExecutionCommand
  case class SecurityFetched(testId: UUID, securityDirectives: List[KafkaSecurityDirective]) extends TestExecutionCommand
  case class TestComplete(testId: UUID, result: TestExecutionResult) extends TestExecutionCommand
  case class BlockStorageUploadComplete(testId: UUID) extends TestExecutionCommand
}

private[core] object CucumberExecutionCommands {
  sealed trait CucumberExecutionCommand

  case class Initialize(blockStorageDirective: BlockStorageDirective) extends CucumberExecutionCommand
  case object StartTest extends CucumberExecutionCommand
  case object Stop extends CucumberExecutionCommand

  case class TestExecutionComplete(result: Either[Throwable, TestExecutionResult]) extends CucumberExecutionCommand
}

private[core] object KafkaProducerCommands {
  sealed trait KafkaProducerCommand

  case class Initialize(blockStorageDirective: BlockStorageDirective, securityDirectives: List[KafkaSecurityDirective]) extends KafkaProducerCommand
  case object StartTest extends KafkaProducerCommand
  case object Stop extends KafkaProducerCommand
}

private[core] object KafkaConsumerCommands {
  sealed trait KafkaConsumerCommand
  case class Initialize(blockStorageDirective: BlockStorageDirective, securityDirectives: List[KafkaSecurityDirective]) extends KafkaConsumerCommand
  case object StartTest extends KafkaConsumerCommand
  case object Stop extends KafkaConsumerCommand
}

private[core] object BlockStorageCommands {
  sealed trait BlockStorageCommand

  case class Initialize(bucket: Option[String]) extends BlockStorageCommand
  case class FetchResults(results: Either[Throwable, BlockStorageDirective]) extends BlockStorageCommand
  case class LoadToBlockStorage(testExecutionResult: TestExecutionResult) extends BlockStorageCommand
  case class LoadResults(results: Either[Throwable, Unit]) extends BlockStorageCommand
  case object Stop extends BlockStorageCommand
}

private[core] object VaultCommands {
  sealed trait VaultCommand

  case class Initialize(blockStorageDirective: BlockStorageDirective) extends VaultCommand
  case class FetchResults(results: Either[Throwable, List[KafkaSecurityDirective]]) extends VaultCommand
  case object Stop extends VaultCommand
}

private[core] object KafkaConsumerStreamingCommands {
  sealed trait KafkaConsumerStreamingCommand
  case class FetchConsumedEvent(correlationId: String, replyTo: ActorRef[ConsumedResult]) extends KafkaConsumerStreamingCommand
  case class InternalAdd(correlationId: String, key: Array[Byte], value: Array[Byte], headers: Headers, replyTo: ActorRef[InternalAddConfirmed]) extends KafkaConsumerStreamingCommand
  case class InternalAddConfirmed(correlationId: String) extends KafkaConsumerStreamingCommand
}

private[core] object KafkaProducerStreamingCommands {
  sealed trait KafkaProducerStreamingCommand
  case class ProduceEvent(key: Array[Byte], value: Array[Byte], headers: Map[String, String], replyTo: ActorRef[ProduceResult]) extends KafkaProducerStreamingCommand
}