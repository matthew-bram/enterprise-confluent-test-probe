package io.distia.probe

/**
 * External services module for REST client integration.
 *
 * This package provides a non-blocking REST client backed by Pekko HTTP
 * for making HTTP calls from test step definitions.
 *
 * Quick Start:
 * {{{
 * import io.distia.probe.external._
 *
 * // Create request envelope
 * val request = RestClientEnvelopeReq(
 *   payload = OrderRequest(productId = "prod-1", quantity = 2),
 *   uri = URI.create("https://api.example.com/orders"),
 *   method = HttpMethod.POST,
 *   headers = Map("Authorization" -> "Bearer token")
 * )
 *
 * // Send via actor
 * restClientActor ! ExecuteRequest(request, classOf[OrderResponse], probe.ref)
 * }}}
 *
 * For Java teams:
 * {{{
 * RestClientEnvelopeReq<OrderRequest> request = RestClientEnvelopeReq.create(
 *   new OrderRequest("prod-1", 2),
 *   URI.create("https://api.example.com/orders"),
 *   HttpMethod.POST,
 *   OrderRequest.class
 * );
 * }}}
 */
package object external {

  // HTTP Method enum
  type HttpMethod = rest.HttpMethod
  val HttpMethod: rest.HttpMethod.type = rest.HttpMethod

  // Request/Response envelopes
  type RestClientEnvelopeReq[T] = rest.RestClientEnvelopeReq[T]
  val RestClientEnvelopeReq: rest.RestClientEnvelopeReq.type = rest.RestClientEnvelopeReq

  type RestClientEnvelopeRes[T] = rest.RestClientEnvelopeRes[T]
  val RestClientEnvelopeRes: rest.RestClientEnvelopeRes.type = rest.RestClientEnvelopeRes

  // Actor commands and results
  type RestClientCommand = rest.RestClientCommands.RestClientCommand
  type ExecuteRequest[Req, Res] = rest.RestClientCommands.ExecuteRequest[Req, Res]
  val ExecuteRequest: rest.RestClientCommands.ExecuteRequest.type = rest.RestClientCommands.ExecuteRequest

  type RestClientResult[T] = rest.RestClientCommands.RestClientResult[T]
  type RestClientSuccess[T] = rest.RestClientCommands.RestClientSuccess[T]
  val RestClientSuccess: rest.RestClientCommands.RestClientSuccess.type = rest.RestClientCommands.RestClientSuccess

  type RestClientFailure = rest.RestClientCommands.RestClientFailure
  val RestClientFailure: rest.RestClientCommands.RestClientFailure.type = rest.RestClientCommands.RestClientFailure

  // Exception types
  type RestClientException = rest.RestClientException
  type RestHttpError = rest.RestHttpError
  val RestHttpError: rest.RestHttpError.type = rest.RestHttpError

  type RestConnectionError = rest.RestConnectionError
  val RestConnectionError: rest.RestConnectionError.type = rest.RestConnectionError

  type RestTimeoutError = rest.RestTimeoutError
  val RestTimeoutError: rest.RestTimeoutError.type = rest.RestTimeoutError

  type RestSerializationError = rest.RestSerializationError
  val RestSerializationError: rest.RestSerializationError.type = rest.RestSerializationError

  // Builder module
  type DefaultRestClient = builder.modules.DefaultRestClient

  // Factory
  type RestClientFactory = factories.RestClientFactory
  val RestClientFactory: factories.RestClientFactory.type = factories.RestClientFactory
}
