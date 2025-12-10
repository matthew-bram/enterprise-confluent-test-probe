package io.distia.probe.external.rest

/**
 * HTTP methods supported by the REST client.
 *
 * Scala 3 enum for type-safe HTTP method specification.
 */
enum HttpMethod:
  case GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
