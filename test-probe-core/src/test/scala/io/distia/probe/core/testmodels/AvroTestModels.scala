package io.distia.probe.core.testmodels

import org.apache.avro.Schema
import org.apache.avro.specific.SpecificRecord

/**
 * Avro SpecificRecord implementations for polymorphic event testing.
 *
 * These classes implement the Avro SpecificRecord interface to enable real
 * Avro binary serialization through SerdesFactory and Confluent Schema Registry.
 *
 * Schema files: src/test/resources/schemas/order-created.avsc, payment-processed.avsc
 */

// =============================================================================
// OrderCreatedAvro - Avro SpecificRecord
// =============================================================================

/**
 * Trait defining accessors for OrderCreatedAvro fields.
 */
trait OrderCreatedAvro extends SpecificRecord:
  def getEventType: String
  def getOrderId: String
  def getCustomerId: String
  def getAmount: java.lang.Double
  def getCurrency: String
  def getTimestamp: java.lang.Long

/**
 * Avro SpecificRecord implementation for OrderCreated events.
 *
 * Field order matches order-created.avsc:
 * 0: eventType, 1: orderId, 2: customerId, 3: amount, 4: currency, 5: timestamp
 */
private[testmodels] class OrderCreatedAvroRecord(
  private var _eventType: String,
  private var _orderId: String,
  private var _customerId: String,
  private var _amount: java.lang.Double,
  private var _currency: String,
  private var _timestamp: java.lang.Long
) extends OrderCreatedAvro:

  // Trait accessors
  override def getEventType: String = _eventType
  override def getOrderId: String = _orderId
  override def getCustomerId: String = _customerId
  override def getAmount: java.lang.Double = _amount
  override def getCurrency: String = _currency
  override def getTimestamp: java.lang.Long = _timestamp

  // SpecificRecord implementation
  override def getSchema: Schema = OrderCreatedAvroRecord.SCHEMA

  override def get(field: Int): AnyRef = field match
    case 0 => _eventType
    case 1 => _orderId
    case 2 => _customerId
    case 3 => _amount
    case 4 => _currency
    case 5 => _timestamp
    case _ => throw new IndexOutOfBoundsException(s"Invalid field index: $field")

  override def put(field: Int, value: Any): Unit = field match
    case 0 => _eventType = value.asInstanceOf[String]
    case 1 => _orderId = value.asInstanceOf[String]
    case 2 => _customerId = value.asInstanceOf[String]
    case 3 => _amount = value.asInstanceOf[java.lang.Double]
    case 4 => _currency = value.asInstanceOf[String]
    case 5 => _timestamp = value.asInstanceOf[java.lang.Long]
    case _ => throw new IndexOutOfBoundsException(s"Invalid field index: $field")

  override def toString: String =
    s"OrderCreatedAvroRecord(eventType=$_eventType, orderId=$_orderId, customerId=$_customerId, amount=$_amount)"

/**
 * Companion object with schema and builder factory.
 */
object OrderCreatedAvroRecord:
  val SCHEMA: Schema = new Schema.Parser().parse(
    """{
      |  "type": "record",
      |  "name": "OrderCreatedAvro",
      |  "namespace": "io.distia.probe.core.testmodels",
      |  "doc": "OrderCreated event schema for Avro serialization",
      |  "fields": [
      |    {"name": "eventType", "type": "string", "default": "OrderCreated"},
      |    {"name": "orderId", "type": "string"},
      |    {"name": "customerId", "type": "string"},
      |    {"name": "amount", "type": "double"},
      |    {"name": "currency", "type": ["null", "string"], "default": null},
      |    {"name": "timestamp", "type": ["null", "long"], "default": null}
      |  ]
      |}""".stripMargin
  )

  def newBuilder(): OrderCreatedAvroBuilder = new OrderCreatedAvroBuilder()

  def apply(
    orderId: String,
    customerId: String,
    amount: Double,
    eventType: String = "OrderCreated",
    currency: String = null,
    timestamp: java.lang.Long = null
  ): OrderCreatedAvro = new OrderCreatedAvroRecord(
    eventType, orderId, customerId, amount, currency, timestamp
  )

/**
 * Builder for OrderCreatedAvroRecord following Avro builder pattern.
 */
class OrderCreatedAvroBuilder:
  private var eventType: String = "OrderCreated"
  private var orderId: String = ""
  private var customerId: String = ""
  private var amount: java.lang.Double = 0.0
  private var currency: String = null
  private var timestamp: java.lang.Long = null

  def setEventType(v: String): OrderCreatedAvroBuilder = { eventType = v; this }
  def setOrderId(v: String): OrderCreatedAvroBuilder = { orderId = v; this }
  def setCustomerId(v: String): OrderCreatedAvroBuilder = { customerId = v; this }
  def setAmount(v: Double): OrderCreatedAvroBuilder = { amount = v; this }
  def setCurrency(v: String): OrderCreatedAvroBuilder = { currency = v; this }
  def setTimestamp(v: Long): OrderCreatedAvroBuilder = { timestamp = v; this }

  def build(): OrderCreatedAvro = new OrderCreatedAvroRecord(
    eventType, orderId, customerId, amount, currency, timestamp
  )

// =============================================================================
// PaymentProcessedAvro - Avro SpecificRecord
// =============================================================================

/**
 * Trait defining accessors for PaymentProcessedAvro fields.
 */
trait PaymentProcessedAvro extends SpecificRecord:
  def getEventType: String
  def getPaymentId: String
  def getOrderId: String
  def getAmount: java.lang.Double
  def getCurrency: String
  def getPaymentMethod: String
  def getTimestamp: java.lang.Long

/**
 * Avro SpecificRecord implementation for PaymentProcessed events.
 *
 * Field order matches payment-processed.avsc:
 * 0: eventType, 1: paymentId, 2: orderId, 3: amount, 4: currency, 5: paymentMethod, 6: timestamp
 */
private[testmodels] class PaymentProcessedAvroRecord(
  private var _eventType: String,
  private var _paymentId: String,
  private var _orderId: String,
  private var _amount: java.lang.Double,
  private var _currency: String,
  private var _paymentMethod: String,
  private var _timestamp: java.lang.Long
) extends PaymentProcessedAvro:

  // Trait accessors
  override def getEventType: String = _eventType
  override def getPaymentId: String = _paymentId
  override def getOrderId: String = _orderId
  override def getAmount: java.lang.Double = _amount
  override def getCurrency: String = _currency
  override def getPaymentMethod: String = _paymentMethod
  override def getTimestamp: java.lang.Long = _timestamp

  // SpecificRecord implementation
  override def getSchema: Schema = PaymentProcessedAvroRecord.SCHEMA

  override def get(field: Int): AnyRef = field match
    case 0 => _eventType
    case 1 => _paymentId
    case 2 => _orderId
    case 3 => _amount
    case 4 => _currency
    case 5 => _paymentMethod
    case 6 => _timestamp
    case _ => throw new IndexOutOfBoundsException(s"Invalid field index: $field")

  override def put(field: Int, value: Any): Unit = field match
    case 0 => _eventType = value.asInstanceOf[String]
    case 1 => _paymentId = value.asInstanceOf[String]
    case 2 => _orderId = value.asInstanceOf[String]
    case 3 => _amount = value.asInstanceOf[java.lang.Double]
    case 4 => _currency = value.asInstanceOf[String]
    case 5 => _paymentMethod = value.asInstanceOf[String]
    case 6 => _timestamp = value.asInstanceOf[java.lang.Long]
    case _ => throw new IndexOutOfBoundsException(s"Invalid field index: $field")

  override def toString: String =
    s"PaymentProcessedAvroRecord(eventType=$_eventType, paymentId=$_paymentId, orderId=$_orderId, amount=$_amount)"

/**
 * Companion object with schema and builder factory.
 */
object PaymentProcessedAvroRecord:
  val SCHEMA: Schema = new Schema.Parser().parse(
    """{
      |  "type": "record",
      |  "name": "PaymentProcessedAvro",
      |  "namespace": "io.distia.probe.core.testmodels",
      |  "doc": "PaymentProcessed event schema for Avro serialization",
      |  "fields": [
      |    {"name": "eventType", "type": "string", "default": "PaymentProcessed"},
      |    {"name": "paymentId", "type": "string"},
      |    {"name": "orderId", "type": "string"},
      |    {"name": "amount", "type": "double"},
      |    {"name": "currency", "type": ["null", "string"], "default": null},
      |    {"name": "paymentMethod", "type": ["null", "string"], "default": null},
      |    {"name": "timestamp", "type": ["null", "long"], "default": null}
      |  ]
      |}""".stripMargin
  )

  def newBuilder(): PaymentProcessedAvroBuilder = new PaymentProcessedAvroBuilder()

  def apply(
    paymentId: String,
    orderId: String,
    amount: Double,
    eventType: String = "PaymentProcessed",
    currency: String = null,
    paymentMethod: String = null,
    timestamp: java.lang.Long = null
  ): PaymentProcessedAvro = new PaymentProcessedAvroRecord(
    eventType, paymentId, orderId, amount, currency, paymentMethod, timestamp
  )

/**
 * Builder for PaymentProcessedAvroRecord following Avro builder pattern.
 */
class PaymentProcessedAvroBuilder:
  private var eventType: String = "PaymentProcessed"
  private var paymentId: String = ""
  private var orderId: String = ""
  private var amount: java.lang.Double = 0.0
  private var currency: String = null
  private var paymentMethod: String = null
  private var timestamp: java.lang.Long = null

  def setEventType(v: String): PaymentProcessedAvroBuilder = { eventType = v; this }
  def setPaymentId(v: String): PaymentProcessedAvroBuilder = { paymentId = v; this }
  def setOrderId(v: String): PaymentProcessedAvroBuilder = { orderId = v; this }
  def setAmount(v: Double): PaymentProcessedAvroBuilder = { amount = v; this }
  def setCurrency(v: String): PaymentProcessedAvroBuilder = { currency = v; this }
  def setPaymentMethod(v: String): PaymentProcessedAvroBuilder = { paymentMethod = v; this }
  def setTimestamp(v: Long): PaymentProcessedAvroBuilder = { timestamp = v; this }

  def build(): PaymentProcessedAvro = new PaymentProcessedAvroRecord(
    eventType, paymentId, orderId, amount, currency, paymentMethod, timestamp
  )
