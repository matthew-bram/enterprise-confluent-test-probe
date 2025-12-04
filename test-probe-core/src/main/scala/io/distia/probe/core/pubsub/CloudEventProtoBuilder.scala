package io.distia.probe.core
package pubsub

import pubsub.models.CloudEvent
import com.google.protobuf.{Descriptors, DynamicMessage}
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema

/**
 * CloudEventProto wrapper for Protobuf serialization.
 * Uses DynamicMessage internally for Schema Registry compatibility.
 */
private[pubsub] class CloudEventProtoWrapper(
  val id: String,
  val source: String,
  val specversion: String,
  val `type`: String,
  val time: String,
  val subject: String,
  val datacontenttype: String,
  val correlationid: String,
  val payloadversion: String,
  val timeEpochMicroSource: Long
):
  /**
   * Convert to DynamicMessage for Confluent serialization.
   * The schema must match cloud-event.proto field numbers.
   */
  def toDynamicMessage(schema: ProtobufSchema): DynamicMessage =
    val descriptor = schema.toDescriptor
    DynamicMessage.newBuilder(descriptor)
      .setField(descriptor.findFieldByNumber(1), id)
      .setField(descriptor.findFieldByNumber(2), source)
      .setField(descriptor.findFieldByNumber(3), specversion)
      .setField(descriptor.findFieldByNumber(4), `type`)
      .setField(descriptor.findFieldByNumber(5), time)
      .setField(descriptor.findFieldByNumber(6), subject)
      .setField(descriptor.findFieldByNumber(7), datacontenttype)
      .setField(descriptor.findFieldByNumber(8), correlationid)
      .setField(descriptor.findFieldByNumber(9), payloadversion)
      .setField(descriptor.findFieldByNumber(10), timeEpochMicroSource)
      .build()

  override def toString: String =
    s"CloudEventProtoWrapper(id=$id, source=$source, type=${`type`}, subject=$subject)"

/**
 * Companion object with schema definition and builder factory.
 */
private[pubsub] object CloudEventProtoWrapper:
  /**
   * Protobuf schema string matching cloud-event.proto.
   */
  val SCHEMA_STRING: String =
    """syntax = "proto3";
      |package io.distia.probe.core.pubsub.models;
      |option java_package = "io.distia.probe.core.pubsub.models";
      |option java_outer_classname = "CloudEventProtos";
      |option java_multiple_files = true;
      |message CloudEvent {
      |  string id = 1;
      |  string source = 2;
      |  string specversion = 3;
      |  string type = 4;
      |  string time = 5;
      |  string subject = 6;
      |  string datacontenttype = 7;
      |  string correlationid = 8;
      |  string payloadversion = 9;
      |  int64 time_epoch_micro_source = 10;
      |}""".stripMargin

  val SCHEMA: ProtobufSchema = new ProtobufSchema(SCHEMA_STRING)

  def newBuilder(): CloudEventProtoBuilder = new CloudEventProtoBuilder()

  /**
   * Create from DynamicMessage (for deserialization).
   */
  def fromDynamicMessage(msg: DynamicMessage): CloudEventProtoWrapper =
    val descriptor = msg.getDescriptorForType
    new CloudEventProtoWrapper(
      id = msg.getField(descriptor.findFieldByNumber(1)).asInstanceOf[String],
      source = msg.getField(descriptor.findFieldByNumber(2)).asInstanceOf[String],
      specversion = msg.getField(descriptor.findFieldByNumber(3)).asInstanceOf[String],
      `type` = msg.getField(descriptor.findFieldByNumber(4)).asInstanceOf[String],
      time = msg.getField(descriptor.findFieldByNumber(5)).asInstanceOf[String],
      subject = msg.getField(descriptor.findFieldByNumber(6)).asInstanceOf[String],
      datacontenttype = msg.getField(descriptor.findFieldByNumber(7)).asInstanceOf[String],
      correlationid = msg.getField(descriptor.findFieldByNumber(8)).asInstanceOf[String],
      payloadversion = msg.getField(descriptor.findFieldByNumber(9)).asInstanceOf[String],
      timeEpochMicroSource = msg.getField(descriptor.findFieldByNumber(10)).asInstanceOf[java.lang.Long].longValue()
    )

/**
 * Builder for CloudEventProtoWrapper following Protobuf builder pattern.
 */
class CloudEventProtoBuilder:
  private var id: String = ""
  private var source: String = ""
  private var specversion: String = "1.0"
  private var `type`: String = ""
  private var time: String = ""
  private var subject: String = ""
  private var datacontenttype: String = "application/octet-stream"
  private var correlationid: String = ""
  private var payloadversion: String = ""
  private var timeEpochMicroSource: Long = 0L

  def setId(v: String): CloudEventProtoBuilder = { id = v; this }
  def setSource(v: String): CloudEventProtoBuilder = { source = v; this }
  def setSpecversion(v: String): CloudEventProtoBuilder = { specversion = v; this }
  def setType(v: String): CloudEventProtoBuilder = { `type` = v; this }
  def setTime(v: String): CloudEventProtoBuilder = { time = v; this }
  def setSubject(v: String): CloudEventProtoBuilder = { subject = v; this }
  def setDatacontenttype(v: String): CloudEventProtoBuilder = { datacontenttype = v; this }
  def setCorrelationid(v: String): CloudEventProtoBuilder = { correlationid = v; this }
  def setPayloadversion(v: String): CloudEventProtoBuilder = { payloadversion = v; this }
  def setTimeEpochMicroSource(v: Long): CloudEventProtoBuilder = { timeEpochMicroSource = v; this }

  def build(): CloudEventProtoWrapper = new CloudEventProtoWrapper(
    id, source, specversion, `type`, time, subject,
    datacontenttype, correlationid, payloadversion, timeEpochMicroSource
  )

/**
 * Converter between CloudEvent (Scala case class) and CloudEventProtoWrapper.
 * Used internally by SerdesFactory for CloudEvent key encapsulation.
 */
private[pubsub] object CloudEventProtoConverter:

  def toProto(ce: CloudEvent): CloudEventProtoWrapper =
    CloudEventProtoWrapper.newBuilder()
      .setId(ce.id)
      .setSource(ce.source)
      .setSpecversion(ce.specversion)
      .setType(ce.`type`)
      .setTime(ce.time)
      .setSubject(ce.subject)
      .setDatacontenttype(ce.datacontenttype)
      .setCorrelationid(ce.correlationid)
      .setPayloadversion(ce.payloadversion)
      .setTimeEpochMicroSource(ce.time_epoch_micro_source)
      .build()

  def toCloudEvent(wrapper: CloudEventProtoWrapper): CloudEvent =
    CloudEvent(
      id = wrapper.id,
      source = wrapper.source,
      specversion = wrapper.specversion,
      `type` = wrapper.`type`,
      time = wrapper.time,
      subject = wrapper.subject,
      datacontenttype = wrapper.datacontenttype,
      correlationid = wrapper.correlationid,
      payloadversion = wrapper.payloadversion,
      time_epoch_micro_source = wrapper.timeEpochMicroSource
    )

  /**
   * Convert DynamicMessage to CloudEvent (for deserialization).
   */
  def fromDynamicMessage(msg: DynamicMessage): CloudEvent =
    toCloudEvent(CloudEventProtoWrapper.fromDynamicMessage(msg))
