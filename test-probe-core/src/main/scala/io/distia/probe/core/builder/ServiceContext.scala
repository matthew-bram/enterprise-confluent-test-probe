package io.distia.probe
package core
package builder

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.http.scaladsl.Http.ServerBinding

import config.CoreConfig
import models.{GuardianCommands, InitializeTestResponse, QueueCommands, QueueStatusResponse, StartTestResponse, TestCancelledResponse, TestStatusResponse}

import scala.concurrent.Future

/**
 * ServiceContext - Resolved Runtime Context for Test-Probe Application
 *
 * The ServiceContext contains fully resolved, non-optional references to all runtime
 * dependencies needed by the Test-Probe application. It is produced by BuilderContext.toServiceContext
 * after all required modules have been added to the ServiceDsl builder.
 *
 * Design Principles:
 * - Non-Optional Fields: All fields are required and validated during conversion from BuilderContext
 * - Runtime Ready: This context is ready for immediate use by interface modules and actors
 * - Immutable: Once created, the context cannot be modified
 * - Type-Safe: All references are fully typed and resolved
 *
 * Usage:
 * Interface modules (REST, CLI, gRPC) receive this context from ServiceDsl.build() and use it to:
 * - Access the actor system for lifecycle management
 * - Read configuration values
 * - Call business logic via curriedFunctions
 * - Send messages to QueueActor
 *
 * Lifecycle:
 * 1. ServiceDsl builds BuilderContext with optional fields
 * 2. BuilderContext.toServiceContext validates and resolves all fields
 * 3. ServiceContext is returned to interface modules
 * 4. Interface modules use context for runtime operations
 *
 * @param actorSystem Root actor system with GuardianActor behavior
 * @param config Typesafe Config instance for application configuration
 * @param coreConfig Parsed CoreConfig from application.conf
 * @param queueActorRef Reference to QueueActor for test queue management
 * @param curriedFunctions Pre-curried business logic functions for interface layer
 * @see BuilderContext for the builder that produces this context
 * @see ServiceDsl for the high-level builder API
 */
case class ServiceContext(
  actorSystem: ActorSystem[GuardianCommands.GuardianCommand],
  config: com.typesafe.config.Config,
  coreConfig: CoreConfig,
  queueActorRef: ActorRef[QueueCommands.QueueCommand],
  curriedFunctions: ServiceInterfaceFunctions
)