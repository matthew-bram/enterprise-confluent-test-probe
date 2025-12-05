package io.distia.probe
package core
package builder

import core.models.*

import java.util.UUID
import scala.concurrent.Future

/**
 * Bundle of curried functions for interface layer to call business logic
 *
 * This case class contains pre-curried functions where the QueueActor reference
 * is already bound. Interface layers (REST, CLI, gRPC) receive this bundle and
 * call functions without needing actor knowledge.
 *
 * Pattern: Hexagonal Architecture Ports
 * - This represents the "port" that interface adapters connect to
 * - Core creates implementations via ServiceInterfaceFunctionsFactory
 * - Interfaces consume this contract without knowing about actors
 *
 * Lifecycle:
 * 1. DefaultActorSystem gets QueueActor reference
 * 2. ServiceInterfaceFunctionsFactory creates this bundle (QueueActor curried in)
 * 3. Bundle stored in BuilderContext.curriedFunctions
 * 4. Interface modules receive bundle via setCurriedFunctions()
 * 5. Interface routes/handlers call these functions
 *
 * @param initializeTest Create new test and return test ID
 * @param startTest Accept test for execution (after files uploaded)
 * @param getStatus Get current status of a test
 * @param getQueueStatus Get queue statistics
 * @param cancelTest Cancel a running or queued test
 */
case class ServiceInterfaceFunctions(
  initializeTest: () => Future[InitializeTestResponse],
  startTest: (UUID, String, Option[String]) => Future[StartTestResponse],
  getStatus: UUID => Future[TestStatusResponse],
  getQueueStatus: Option[UUID] => Future[QueueStatusResponse],
  cancelTest: UUID => Future[TestCancelledResponse]
)
