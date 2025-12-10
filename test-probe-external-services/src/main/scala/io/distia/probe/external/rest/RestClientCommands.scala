package io.distia.probe.external.rest

import org.apache.pekko.actor.typed.ActorRef
import io.distia.probe.common.models.ProbeExternalActorCommand
import scala.reflect.ClassTag

/**
 * Actor commands for REST client.
 *
 * Follows the Pekko typed actor pattern with replyTo for response routing.
 * Extends ProbeExternalActorCommand to integrate with the framework's
 * external behavior routing system.
 */
private[external] object RestClientCommands:

  /**
   * Base trait for REST client commands.
   * Extends ProbeExternalActorCommand for framework integration.
   */
  sealed trait RestClientCommand extends ProbeExternalActorCommand

  /**
   * Execute a REST request and reply with the response.
   *
   * Single command type following the request-response pattern.
   * Uses ClassTag for type-safe serialization/deserialization.
   *
   * @tparam Req Request payload type
   * @tparam Res Response body type
   * @param request The request envelope containing payload, URI, headers, etc.
   * @param responseClass Runtime class for response deserialization
   * @param replyTo Actor reference to receive the result
   */
  case class ExecuteRequest[Req: ClassTag, Res: ClassTag](
    request: RestClientEnvelopeReq[Req],
    responseClass: Class[Res],
    replyTo: ActorRef[RestClientResult[Res]]
  ) extends RestClientCommand

  /**
   * Result wrapper for actor responses.
   *
   * ADT pattern enabling pattern matching on success/failure.
   */
  sealed trait RestClientResult[+T]

  /**
   * Successful REST response.
   *
   * @param response The response envelope with deserialized body
   */
  case class RestClientSuccess[T](response: RestClientEnvelopeRes[T]) extends RestClientResult[T]

  /**
   * Failed REST response.
   *
   * @param exception The exception describing the failure
   */
  case class RestClientFailure(exception: RestClientException) extends RestClientResult[Nothing]
