package io.distia.probe.external.rest

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}

/**
 * Shared ObjectMapper configuration for REST client serialization.
 *
 * Mirrors the proven pattern from test-probe-core pubsub package.
 * Uses Jackson with DefaultScalaModule for seamless Scala case class support.
 *
 * Configuration:
 * - NON_NULL: Excludes null fields from JSON output
 * - WRITE_DATES_AS_TIMESTAMPS: Serializes dates as numeric timestamps
 * - FAIL_ON_UNKNOWN_PROPERTIES=false: Ignores unknown fields during deserialization
 */
private[external] object RestClientObjectMapper:

  /**
   * Lazily initialized ObjectMapper with Scala module.
   * Uses :: syntax to add ClassTagExtensions for type-safe deserialization.
   */
  lazy val mapper: ObjectMapper =
    val m = JsonMapper.builder()
      .addModule(DefaultScalaModule)
      .build() :: ClassTagExtensions
    m.setSerializationInclusion(JsonInclude.Include.NON_NULL)
    m.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
    m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    m
