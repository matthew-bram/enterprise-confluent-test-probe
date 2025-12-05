package io.distia.probe
package core
package pubsub
package models

import java.time.Instant
import scala.reflect.ClassTag
import org.apache.kafka.common.header.internals.RecordHeader

import java.time.format.DateTimeFormatter
import com.fasterxml.jackson.annotation.JsonTypeName
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle

sealed trait ProduceResult

private[core] case object ProducedAck extends ProduceResult

private[core] case class ProducedNack(ex: Throwable) extends ProduceResult

final case class ProducingSuccess() extends ProduceResult

sealed trait ConsumedResult

private[core] case class ConsumedAck(key: Array[Byte], value: Array[Byte], headers: List[RecordHeader]) extends ConsumedResult

private[core] case class ConsumedNack(status: Int = 0) extends ConsumedResult

final case class ConsumedSuccess[T: ClassTag](key: CloudEvent, value: T, headers: Map[String, String]) extends ConsumedResult

@JsonSchemaTitle("CloudEvent")
@JsonTypeName("CloudEvent")
final case class CloudEvent(
  id: String,
  source: String,
  specversion: String = "1.0",
  `type`: String,
  time: String = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
  subject: String,
  datacontenttype: String = "application/octet-stream",
  correlationid: String,
  payloadversion: String,
  time_epoch_micro_source: Long = 0L
)
