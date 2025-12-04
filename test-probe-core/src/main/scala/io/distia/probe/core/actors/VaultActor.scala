/*
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║ ⚠️  SECURITY WARNING - SENSITIVE DATA HANDLING                       ║
 * ╠══════════════════════════════════════════════════════════════════════╣
 * ║ This actor handles KafkaSecurityDirective containing:                     ║
 * ║ - OAuth client IDs and secrets                                       ║
 * ║ - Vault credentials                                                  ║
 * ║ - Kafka authentication tokens                                        ║
 * ║                                                                      ║
 * ║ ⚠️  DO NOT LOG KafkaSecurityDirective OR ANY DERIVED CREDENTIALS          ║
 * ║                                                                      ║
 * ║ All logging must:                                                    ║
 * ║ 1. Exclude KafkaSecurityDirective objects from log statements            ║
 * ║ 2. Redact credentials in error messages                             ║
 * ║ 3. Use testId for correlation, never credentials                    ║
 * ║                                                                      ║
 * ║ Future: Security agent will enforce compliance                      ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
package io.distia.probe
package core
package actors

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior, DispatcherSelector}

import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}
import io.distia.probe.common.security.SecretRedactor

import builder.VaultServiceFunctions
import models.*
import models.TestExecutionCommands.*
import models.VaultCommands.*

/**
 * VaultActor - Security Credentials Management
 *
 * Responsibilities:
 * 1. Initialize: Fetch client ID and client secret from Vault (stub)
 * 2. Stop: Clean shutdown
 *
 * Message Protocol:
 * - Receives: Initialize(blockStorageDirective), Stop
 * - Sends to parent TEA: SecurityFetched, ChildGoodToGo
 *
 * State: Implicit (Created → Fetching → Ready → Stopped)
 *
 * SECURITY: This actor handles sensitive KafkaSecurityDirective data
 * All credentials must be redacted in logs
 *
 * NOTE: This is scaffolding only - service integration stubs marked with TODO
 */
private[core] object VaultActor {

  
  def apply(
    testId: UUID,
    parentTea: ActorRef[TestExecutionCommand],
    vaultFunctions: VaultServiceFunctions
  ): Behavior[VaultCommand] = {
    Behaviors.setup { context =>
      
      val servicesEc: ExecutionContext = context.system.dispatchers.lookup(
        DispatcherSelector.fromConfig("pekko.actor.services-dispatcher")
      )

      context.log.info(s"VaultActor starting for test $testId")
      activeBehavior(testId, initialized = false, parentTea, vaultFunctions, servicesEc, context)
    }
  }

  
  def activeBehavior(
    testId: UUID,
    initialized: Boolean,
    parentTea: ActorRef[TestExecutionCommand],
    vaultFunctions: VaultServiceFunctions,
    servicesEc: ExecutionContext,
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[VaultCommand]
  ): Behavior[VaultCommand] = {
    Behaviors.receiveMessage {
      case Initialize(blockStorageDirective) =>
        handleInitialize(testId, blockStorageDirective, initialized, parentTea, vaultFunctions, servicesEc, context)

      case Stop =>
        handleStop(testId, context)

      case results: FetchResults =>
        handleFetchResults(testId, results, parentTea, vaultFunctions, servicesEc, context)
    }
  }
  
  def handleFetchResults(
    testId: UUID,
    results: FetchResults,
    parentTea: ActorRef[TestExecutionCommand],
    vaultFunctions: VaultServiceFunctions,
    servicesEc: ExecutionContext,
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[VaultCommand]
  ): Behavior[VaultCommand] = {
    results.results match {
      case Right(directives) =>
        // ⚠️  SECURITY: Use SecretRedactor for any logging that might contain credentials
        val safeMessage = SecretRedactor.redact(s"KafkaSecurityDirective fetched for test $testId with ${directives.size} directives")
        context.log.info(safeMessage)
        parentTea ! SecurityFetched(testId, directives)
        parentTea ! ChildGoodToGo(testId, context.self)

        activeBehavior(testId, initialized = true, parentTea, vaultFunctions, servicesEc, context)

      case Left(ex) =>
        val safeMessage = SecretRedactor.redact(s"Vault fetch failed for test $testId: ${ex.getMessage}")
        context.log.error(safeMessage, ex)
        throw VaultConsumerException(
          message = s"Failed to fetch secrets for test $testId: ${ex.getMessage}",
          cause = Some(ex)
        )
    }
  }
  
  def handleInitialize(
    testId: UUID,
    blockStorageDirective: BlockStorageDirective,
    initialized: Boolean,
    parentTea: ActorRef[TestExecutionCommand],
    vaultFunctions: VaultServiceFunctions,
    servicesEc: ExecutionContext,
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[VaultCommand]
  ): Behavior[VaultCommand] = {
    val securityDirectives: Future[List[KafkaSecurityDirective]] = vaultFunctions
      .fetchSecurityDirectives(blockStorageDirective)(using servicesEc)

    context.pipeToSelf(securityDirectives) {
      case Success(securityDirectives) => FetchResults(Right(securityDirectives))
      case Failure(ex) => FetchResults(Left(ex))
    }

    activeBehavior(testId, initialized = true, parentTea, vaultFunctions, servicesEc, context)
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
    context: org.apache.pekko.actor.typed.scaladsl.ActorContext[VaultCommand]
  ): Behavior[VaultCommand] = {
    context.log.info(s"Stopping VaultActor for test $testId")
    Behaviors.stopped
  }
}
