package io.distia.probe.core
package pubsub

import pubsub.models.CloudEvent
import org.apache.avro.specific.SpecificRecord
import org.apache.avro.{Schema, SchemaBuilder}
import org.apache.avro.generic.GenericData

/**
 * CloudEventAvro trait defining the accessor methods for CloudEvent Avro records.
 * Extends SpecificRecord for Confluent Schema Registry compatibility.
 */
trait CloudEventAvro extends SpecificRecord:
  def getId: String
  def getSource: String
  def getSpecversion: String
  def getType: String
  def getTime: String
  def getSubject: String
  def getDatacontenttype: String
  def getCorrelationid: String
  def getPayloadversion: String
  def getTimeEpochMicroSource: java.lang.Long

/**
 * Concrete implementation of CloudEventAvro that implements SpecificRecord.
 * Used for Avro serialization of CloudEvent keys with TopicRecordNameStrategy.
 */
private[pubsub] class CloudEventAvroRecord(
  private var _id: String,
  private var _source: String,
  private var _specversion: String,
  private var _type: String,
  private var _time: String,
  private var _subject: String,
  private var _datacontenttype: String,
  private var _correlationid: String,
  private var _payloadversion: String,
  private var _timeEpochMicroSource: java.lang.Long
) extends CloudEventAvro:

  // CloudEventAvro trait methods
  override def getId: String = _id
  override def getSource: String = _source
  override def getSpecversion: String = _specversion
  override def getType: String = _type
  override def getTime: String = _time
  override def getSubject: String = _subject
  override def getDatacontenttype: String = _datacontenttype
  override def getCorrelationid: String = _correlationid
  override def getPayloadversion: String = _payloadversion
  override def getTimeEpochMicroSource: java.lang.Long = _timeEpochMicroSource

  // SpecificRecord implementation
  override def getSchema: Schema = CloudEventAvroRecord.SCHEMA

  override def get(field: Int): AnyRef = field match
    case 0 => _id
    case 1 => _source
    case 2 => _specversion
    case 3 => _type
    case 4 => _time
    case 5 => _subject
    case 6 => _datacontenttype
    case 7 => _correlationid
    case 8 => _payloadversion
    case 9 => _timeEpochMicroSource
    case _ => throw new IndexOutOfBoundsException(s"Invalid field index: $field")

  override def put(field: Int, value: Any): Unit = field match
    case 0 => _id = value.asInstanceOf[String]
    case 1 => _source = value.asInstanceOf[String]
    case 2 => _specversion = value.asInstanceOf[String]
    case 3 => _type = value.asInstanceOf[String]
    case 4 => _time = value.asInstanceOf[String]
    case 5 => _subject = value.asInstanceOf[String]
    case 6 => _datacontenttype = value.asInstanceOf[String]
    case 7 => _correlationid = value.asInstanceOf[String]
    case 8 => _payloadversion = value.asInstanceOf[String]
    case 9 => _timeEpochMicroSource = value.asInstanceOf[java.lang.Long]
    case _ => throw new IndexOutOfBoundsException(s"Invalid field index: $field")

  override def toString: String =
    s"CloudEventAvroRecord(id=$_id, source=$_source, type=$_type, subject=$_subject)"

/**
 * Companion object with schema definition and builder factory.
 */
private[pubsub] object CloudEventAvroRecord:
  /**
   * Avro schema matching cloud-event.avsc.
   * Field order must match get/put indices.
   */
  val SCHEMA: Schema = new Schema.Parser().parse(
    """{
      |  "type": "record",
      |  "name": "CloudEvent",
      |  "namespace": "io.distia.probe.core.pubsub.models",
      |  "doc": "CloudEvents 1.0 specification for Kafka message keys",
      |  "fields": [
      |    {"name": "id", "type": "string", "doc": "Unique event identifier (UUID)"},
      |    {"name": "source", "type": "string", "doc": "Event source"},
      |    {"name": "specversion", "type": "string", "default": "1.0", "doc": "CloudEvents spec version"},
      |    {"name": "type", "type": "string", "doc": "Event type"},
      |    {"name": "time", "type": "string", "doc": "Event timestamp in ISO 8601 format"},
      |    {"name": "subject", "type": "string", "doc": "Event subject"},
      |    {"name": "datacontenttype", "type": "string", "default": "application/octet-stream", "doc": "Payload MIME type"},
      |    {"name": "correlationid", "type": "string", "doc": "Correlation ID"},
      |    {"name": "payloadversion", "type": "string", "doc": "Payload schema version"},
      |    {"name": "time_epoch_micro_source", "type": ["null", "long"], "default": null, "doc": "Optional source timestamp"}
      |  ]
      |}""".stripMargin
  )

  def newBuilder(): CloudEventAvroBuilder = new CloudEventAvroBuilder()

/**
 * Builder for CloudEventAvroRecord following Avro builder pattern.
 */
class CloudEventAvroBuilder:
  private var id: String = ""
  private var source: String = ""
  private var specversion: String = "1.0"
  private var `type`: String = ""
  private var time: String = ""
  private var subject: String = ""
  private var datacontenttype: String = "application/octet-stream"
  private var correlationid: String = ""
  private var payloadversion: String = ""
  private var timeEpochMicroSource: java.lang.Long = null

  def setId(v: String): CloudEventAvroBuilder = { id = v; this }
  def setSource(v: String): CloudEventAvroBuilder = { source = v; this }
  def setSpecversion(v: String): CloudEventAvroBuilder = { specversion = v; this }
  def setType(v: String): CloudEventAvroBuilder = { `type` = v; this }
  def setTime(v: String): CloudEventAvroBuilder = { time = v; this }
  def setSubject(v: String): CloudEventAvroBuilder = { subject = v; this }
  def setDatacontenttype(v: String): CloudEventAvroBuilder = { datacontenttype = v; this }
  def setCorrelationid(v: String): CloudEventAvroBuilder = { correlationid = v; this }
  def setPayloadversion(v: String): CloudEventAvroBuilder = { payloadversion = v; this }
  def setTimeEpochMicroSource(v: Long): CloudEventAvroBuilder = { timeEpochMicroSource = v; this }

  def build(): CloudEventAvro = new CloudEventAvroRecord(
    id, source, specversion, `type`, time, subject,
    datacontenttype, correlationid, payloadversion, timeEpochMicroSource
  )

/**
 * Converter between CloudEvent (Scala case class) and CloudEventAvro (SpecificRecord).
 * Used internally by SerdesFactory for CloudEvent key encapsulation.
 */
private[pubsub] object CloudEventAvroConverter:

  def toAvro(ce: CloudEvent): CloudEventAvro = CloudEventAvroRecord.newBuilder()
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

  def toCloudEvent(avro: CloudEventAvro): CloudEvent = CloudEvent(
    id = avro.getId,
    source = avro.getSource,
    specversion = avro.getSpecversion,
    `type` = avro.getType,
    time = avro.getTime,
    subject = avro.getSubject,
    datacontenttype = avro.getDatacontenttype,
    correlationid = avro.getCorrelationid,
    payloadversion = avro.getPayloadversion,
    time_epoch_micro_source = Option(avro.getTimeEpochMicroSource).map(_.longValue()).getOrElse(0L)
  )

  /**
   * Convert GenericRecord (from Avro deserializer in generic mode) to CloudEvent.
   * Used when deserializing CloudEvent keys since our CloudEventAvroRecord class
   * isn't discoverable by namespace in the Schema Registry specific reader.
   */
  def fromGenericRecord(record: org.apache.avro.generic.GenericRecord): CloudEvent =
    CloudEvent(
      id = Option(record.get("id")).map(_.toString).getOrElse(""),
      source = Option(record.get("source")).map(_.toString).getOrElse(""),
      specversion = Option(record.get("specversion")).map(_.toString).getOrElse("1.0"),
      `type` = Option(record.get("type")).map(_.toString).getOrElse(""),
      time = Option(record.get("time")).map(_.toString).getOrElse(""),
      subject = Option(record.get("subject")).map(_.toString).getOrElse(""),
      datacontenttype = Option(record.get("datacontenttype")).map(_.toString).getOrElse("application/octet-stream"),
      correlationid = Option(record.get("correlationid")).map(_.toString).getOrElse(""),
      payloadversion = Option(record.get("payloadversion")).map(_.toString).getOrElse(""),
      time_epoch_micro_source = Option(record.get("time_epoch_micro_source")).map {
        case l: java.lang.Long => l.longValue()
        case _ => 0L
      }.getOrElse(0L)
    )
