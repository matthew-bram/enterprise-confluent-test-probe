package io.distia.probe
package core
package actors

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import io.distia.probe.common.models.BlockStorageDirective

import builder.StorageServiceFunctions
import models.*
import models.BlockStorageCommands.*
import models.TestExecutionCommands.*

/**
 * BlockStorageActor - Test Evidence Storage Management
 *
 * Responsibilities:
 * 1. Fetch: Fetch test data from block storage (S3/Azure/GCS) to jimfs local filesystem
 * 2. Load: Upload test evidence from jimfs to block storage
 * 3. Stop: Clean shutdown
 *
 * Message Protocol:
 * - Receives: Initialize(bucket), FetchResults, LoadToBlockStorage, LoadResults, Stop
 * - Sends to parent TEA: BlockStorageFetched, ChildGoodToGo, BlockStorageUploadComplete
 *
 * State Management:
 * - Stores BlockStorageDirective after fetch (needed for load phase)
 * - initialized flag tracks if Initialize has been called
 *
 * Pattern: Follows VaultActor pattern with pipeToSelf for async operations
 * - Fetch flow: Initialize → pipeToSelf(fetchFromBlockStorage) → FetchResults → BlockStorageFetched + ChildGoodToGo
 * - Load flow: LoadToBlockStorage → pipeToSelf(loadToBlockStorage) → LoadResults → BlockStorageUploadComplete
 *
 * Service Functions:
 * - fetchFromBlockStorage: (UUID, String) => Future[BlockStorageDirective]
 * - loadToBlockStorage: (String, TestExecutionResult) => Future[Unit]
 */
private[core] object BlockStorageActor {


  def apply(
    testId: UUID,
    parentTea: ActorRef[TestExecutionCommand],
    storageFunctions: StorageServiceFunctions,
  ): Behavior[BlockStorageCommand] = {
    Behaviors.setup { context =>
      val servicesEc: ExecutionContext = context.system.dispatchers.lookup(
        org.apache.pekko.actor.typed.DispatcherSelector.fromConfig("pekko.actor.services-dispatcher")
      )
      context.log.info(s"BlockStorageActor starting for test $testId")
      activeBehavior(
        testId,
        initialized = false,
        blockStorageDirective = None,
        parentTea,
        storageFunctions,
        servicesEc,
        context
      )
    }
  }

  def activeBehavior(
    testId: UUID,
    initialized: Boolean,
    blockStorageDirective: Option[BlockStorageDirective],
    parentTea: ActorRef[TestExecutionCommand],
    storageFunctions: StorageServiceFunctions,
    servicesEc: ExecutionContext,
    context: ActorContext[BlockStorageCommand]
  ): Behavior[BlockStorageCommand] = {
    Behaviors.receiveMessage {
      case Initialize(bucket) =>
        val resolvedBucket: String = bucket.getOrElse(throw BlockStorageException(
          message = s"A valid bucket is required to initialize this test: $testId",
          cause = None
        ))
        handleInitialize(testId, resolvedBucket, initialized, parentTea, storageFunctions, servicesEc, context)

      case fetchResults: FetchResults =>
        handleFetchResults(testId, fetchResults, parentTea, storageFunctions, servicesEc, context)

      case LoadToBlockStorage(testExecutionResult) =>
        handleLoadToBlockStorage(testId, blockStorageDirective, testExecutionResult, initialized, parentTea, storageFunctions, servicesEc, context)

      case loadResults: LoadResults =>
        handleLoadResults(testId, loadResults, blockStorageDirective, parentTea, storageFunctions, servicesEc, context)

      case Stop =>
        handleStop(testId, context)
    }
  }


  def handleInitialize(
    testId: UUID,
    bucket: String,
    initialized: Boolean,
    parentTea: ActorRef[TestExecutionCommand],
    storageFunctions: StorageServiceFunctions,
    servicesEc: ExecutionContext,
    context: ActorContext[BlockStorageCommand]
  ): Behavior[BlockStorageCommand] = {

    val fetchFuture: Future[BlockStorageDirective] = storageFunctions.fetchFromBlockStorage(testId, bucket)(using servicesEc)
    
    context.pipeToSelf(fetchFuture) {
      case Success(directive) => FetchResults(Right(directive))
      case Failure(ex) => FetchResults(Left(ex))
    }
    activeBehavior(testId, initialized = true, None, parentTea, storageFunctions, servicesEc, context)
  }

  def handleFetchResults(
    testId: UUID,
    results: FetchResults,
    parentTea: ActorRef[TestExecutionCommand],
    storageFunctions: StorageServiceFunctions,
    servicesEc: ExecutionContext,
    context: ActorContext[BlockStorageCommand]
  ): Behavior[BlockStorageCommand] = {
    results.results match {
      case Right(directive) =>
        context.log.info(s"BlockStorageDirective fetched for test $testId at ${directive.jimfsLocation} (${directive.topicDirectives.size} topics)")
        
        parentTea ! BlockStorageFetched(testId, directive)
        parentTea ! ChildGoodToGo(testId, context.self)
        
        activeBehavior(testId, initialized = true, Some(directive), parentTea, storageFunctions, servicesEc, context)

      case Left(ex) =>
        context.log.error(s"BlockStorage fetch failed for test $testId", ex)
        throw BlockStorageException(
          message = s"Failed to fetch block storage for test $testId: ${ex.getMessage}",
          cause = Some(ex)
        )
    }
  }


  def handleLoadToBlockStorage(
    testId: UUID,
    blockStorageDirective: Option[BlockStorageDirective],
    testExecutionResult: TestExecutionResult,
    initialized: Boolean,
    parentTea: ActorRef[TestExecutionCommand],
    storageFunctions: StorageServiceFunctions,
    servicesEc: ExecutionContext,
    context: ActorContext[BlockStorageCommand]
  ): Behavior[BlockStorageCommand] = {
    if !initialized then throw new IllegalStateException(
      s"""BlockStorageActor not initialized for test $testId
         | - call Initialize before LoadToBlockStorage""".stripMargin)

    val directive = blockStorageDirective match {
      case Some(directive) => directive
      case None =>
        val ex = new IllegalStateException(
          s"""BlockStorageDirective not available for test $testId
             | - fetch must complete before load""".stripMargin)
        context.log.error("LoadToBlockStorage called before fetch completed", ex)
        throw ex
    }
    val loadFuture: Future[Unit] = storageFunctions.loadToBlockStorage(testId, directive.bucket, directive.evidenceDir)(using servicesEc)
    context.pipeToSelf(loadFuture) {
      case Success(_) => LoadResults(Right(()))
      case Failure(ex) => LoadResults(Left(ex))
    }
    Behaviors.same
  }


  def handleLoadResults(
    testId: UUID,
    results: LoadResults,
    blockStorageDirective: Option[BlockStorageDirective],
    parentTea: ActorRef[TestExecutionCommand],
    storageFunctions: StorageServiceFunctions,
    servicesEc: ExecutionContext,
    context: ActorContext[BlockStorageCommand]
  ): Behavior[BlockStorageCommand] = {
    results.results match {
      case Right(_) =>
        context.log.info(s"Evidence uploaded successfully for test $testId")
        
        parentTea ! BlockStorageUploadComplete(testId)
        
        activeBehavior(testId, initialized = true, blockStorageDirective, parentTea, storageFunctions, servicesEc, context)

      case Left(ex) =>
        context.log.error(s"Evidence upload failed for test $testId", ex)
        throw BlockStorageException(
          message = s"Failed to upload evidence for test $testId: ${ex.getMessage}",
          cause = Some(ex)
        )
    }
  }

  /**
   * Handle Stop command: Clean shutdown
   *
   * @param testId UUID of the test
   * @param context Actor context
   * @return Stopped behavior
   */
  def handleStop(
    testId: UUID,
    context: ActorContext[BlockStorageCommand]
  ): Behavior[BlockStorageCommand] = {
    context.log.info(s"Stopping BlockStorageActor for test $testId")
    Behaviors.stopped
  }
}
