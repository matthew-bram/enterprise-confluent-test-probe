package io.distia.probe.external.rest

import org.apache.pekko.actor.typed.{ActorSystem, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.model.{HttpMethods => PekkoHttpMethods}
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import io.distia.probe.external.rest.RestClientCommands.*

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

/**
 * REST client actor for executing HTTP requests.
 *
 * Uses Pekko HTTP's non-blocking client with the typed actor replyTo pattern.
 * Supports all standard HTTP methods with JSON serialization via Jackson.
 *
 * Design decisions:
 * - Single message type (ExecuteRequest) following request-response pattern
 * - pipeToSelf for async HTTP operations
 * - Internal message type for routing responses back to caller
 * - Configurable default timeout with per-request override capability
 */
private[external] object RestClientActor:

  /**
   * Internal protocol for actor coordination.
   * Separate from RestClientCommand to allow sealed hierarchy in Commands file.
   */
  private sealed trait InternalMessage
  private case class InternalResult[T](
    replyTo: org.apache.pekko.actor.typed.ActorRef[RestClientResult[T]],
    result: RestClientResult[T]
  ) extends InternalMessage

  /** Combined message type for the actor - handles both external commands and internal messages */
  private type ActorMessage = RestClientCommand | InternalMessage

  /**
   * Create REST client actor behavior.
   *
   * @param defaultTimeout Default timeout for requests (can be overridden per-request)
   * @return Behavior for handling RestClientCommand messages
   */
  def apply(defaultTimeout: FiniteDuration = 30.seconds): Behavior[RestClientCommand] =
    Behaviors.setup[ActorMessage] { context =>
      given ActorSystem[?] = context.system
      given ExecutionContext = context.executionContext
      val http = Http()

      Behaviors.receiveMessage[ActorMessage] {
        case cmd: ExecuteRequest[?, ?] =>
          handleExecuteRequest(cmd, http, defaultTimeout, context)
          Behaviors.same

        case internal: InternalResult[t] =>
          // Route result to original caller (type parameter t is captured by pattern)
          internal.replyTo.asInstanceOf[org.apache.pekko.actor.typed.ActorRef[RestClientResult[t]]] ! internal.result
          Behaviors.same
      }
    }.narrow[RestClientCommand]

  /**
   * Handle ExecuteRequest command by making HTTP call and piping result.
   */
  private def handleExecuteRequest[Req, Res](
    cmd: ExecuteRequest[Req, Res],
    http: org.apache.pekko.http.scaladsl.HttpExt,
    defaultTimeout: FiniteDuration,
    context: ActorContext[ActorMessage]
  )(using system: ActorSystem[?], ec: ExecutionContext): Unit =

    val startTime = System.nanoTime()
    val timeout = cmd.request.timeout.getOrElse(defaultTimeout)

    val httpRequest = buildHttpRequest(cmd.request)

    val responseFuture: Future[RestClientResult[Res]] =
      http.singleRequest(httpRequest)
        .flatMap { response =>
          response.entity.toStrict(timeout).map { entity =>
            val responseTime = (System.nanoTime() - startTime).nanos
            val bodyString = entity.data.utf8String
            val headers = response.headers.map(h => h.name -> h.value).toMap

            if response.status.isSuccess then
              given ClassTag[Res] = ClassTag(cmd.responseClass)
              val envelope = RestClientEnvelopeRes.fromJson[Res](
                response.status.intValue,
                bodyString,
                headers,
                responseTime
              )
              RestClientSuccess(envelope)
            else
              RestClientFailure(
                RestHttpError(response.status.intValue, bodyString)
              )
          }
        }
        .recover {
          case _: java.util.concurrent.TimeoutException =>
            RestClientFailure(
              RestTimeoutError(cmd.request.uri.toString, timeout)
            )
          case ex: com.fasterxml.jackson.core.JsonProcessingException =>
            RestClientFailure(
              RestSerializationError("deserialize", ex)
            )
          case ex: Throwable =>
            RestClientFailure(
              RestConnectionError(cmd.request.uri.toString, ex)
            )
        }

    // Pipe result to self, then route to original caller
    context.pipeToSelf(responseFuture) {
      case Success(result) =>
        InternalResult(cmd.replyTo, result)
      case Failure(ex) =>
        InternalResult(
          cmd.replyTo,
          RestClientFailure(RestConnectionError(cmd.request.uri.toString, ex))
        )
    }

  /**
   * Build Pekko HttpRequest from RestClientEnvelopeReq.
   */
  private def buildHttpRequest[T](req: RestClientEnvelopeReq[T]): HttpRequest =
    val method = req.method match
      case HttpMethod.GET     => PekkoHttpMethods.GET
      case HttpMethod.POST    => PekkoHttpMethods.POST
      case HttpMethod.PUT     => PekkoHttpMethods.PUT
      case HttpMethod.DELETE  => PekkoHttpMethods.DELETE
      case HttpMethod.PATCH   => PekkoHttpMethods.PATCH
      case HttpMethod.HEAD    => PekkoHttpMethods.HEAD
      case HttpMethod.OPTIONS => PekkoHttpMethods.OPTIONS

    val headers = req.headers.map { case (k, v) => RawHeader(k, v) }.toList

    val entity = method match
      case PekkoHttpMethods.GET | PekkoHttpMethods.HEAD | PekkoHttpMethods.OPTIONS =>
        HttpEntity.Empty
      case _ =>
        HttpEntity(ContentTypes.`application/json`, req.toJson)

    HttpRequest(
      method = method,
      uri = req.uri.toString,
      headers = headers,
      entity = entity
    )
