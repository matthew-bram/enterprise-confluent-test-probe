package io.distia.probe.core.testmodels

import com.google.protobuf.DynamicMessage
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema

/**
 * Protobuf wrapper implementations for polymorphic event testing.
 *
 * These classes follow the DynamicMessage pattern used by CloudEventProtoWrapper
 * to enable real Protobuf binary serialization through SerdesFactory and
 * Confluent Schema Registry.
 *
 * Schema files: src/test/resources/schemas/order-created.proto, payment-processed.proto
 */

// =============================================================================
// OrderCreatedProtoWrapper - Protobuf DynamicMessage wrapper
// =============================================================================

/**
 * Protobuf wrapper for OrderCreated events.
 * Uses DynamicMessage internally for Schema Registry compatibility.
 *
 * Field numbers match order-created.proto:
 * 1: event_type, 2: order_id, 3: customer_id, 4: amount, 5: currency, 6: timestamp
 */
class OrderCreatedProtoWrapper(
  val eventType: String,
  val orderId: String,
  val customerId: String,
  val amount: Double,
  val currency: String,
  val timestamp: Long
):
  /**
   * Convert to DynamicMessage for Confluent serialization.
   * The schema must match order-created.proto field numbers.
   */
  def toDynamicMessage(schema: ProtobufSchema): DynamicMessage =
    val descriptor = schema.toDescriptor
    DynamicMessage.newBuilder(descriptor)
      .setField(descriptor.findFieldByNumber(1), eventType)
      .setField(descriptor.findFieldByNumber(2), orderId)
      .setField(descriptor.findFieldByNumber(3), customerId)
      .setField(descriptor.findFieldByNumber(4), amount)
      .setField(descriptor.findFieldByNumber(5), currency)
      .setField(descriptor.findFieldByNumber(6), timestamp)
      .build()

  override def toString: String =
    s"OrderCreatedProtoWrapper(eventType=$eventType, orderId=$orderId, customerId=$customerId, amount=$amount)"

/**
 * Companion object with schema definition and builder factory.
 */
object OrderCreatedProtoWrapper:
  /**
   * Protobuf schema string matching order-created.proto.
   */
  val SCHEMA_STRING: String =
    """syntax = "proto3";
      |package io.distia.probe.core.testmodels;
      |option java_package = "io.distia.probe.core.testmodels";
      |option java_outer_classname = "OrderCreatedProtos";
      |option java_multiple_files = true;
      |message OrderCreatedProto {
      |  string event_type = 1;
      |  string order_id = 2;
      |  string customer_id = 3;
      |  double amount = 4;
      |  string currency = 5;
      |  int64 timestamp = 6;
      |}""".stripMargin

  val SCHEMA: ProtobufSchema = new ProtobufSchema(SCHEMA_STRING)

  def newBuilder(): OrderCreatedProtoBuilder = new OrderCreatedProtoBuilder()

  /**
   * Create from DynamicMessage (for deserialization).
   */
  def fromDynamicMessage(msg: DynamicMessage): OrderCreatedProtoWrapper =
    val descriptor = msg.getDescriptorForType
    new OrderCreatedProtoWrapper(
      eventType = msg.getField(descriptor.findFieldByNumber(1)).asInstanceOf[String],
      orderId = msg.getField(descriptor.findFieldByNumber(2)).asInstanceOf[String],
      customerId = msg.getField(descriptor.findFieldByNumber(3)).asInstanceOf[String],
      amount = msg.getField(descriptor.findFieldByNumber(4)).asInstanceOf[java.lang.Double].doubleValue(),
      currency = msg.getField(descriptor.findFieldByNumber(5)).asInstanceOf[String],
      timestamp = msg.getField(descriptor.findFieldByNumber(6)).asInstanceOf[java.lang.Long].longValue()
    )

  def apply(
    orderId: String,
    customerId: String,
    amount: Double,
    eventType: String = "OrderCreated",
    currency: String = "",
    timestamp: Long = 0L
  ): OrderCreatedProtoWrapper = new OrderCreatedProtoWrapper(
    eventType, orderId, customerId, amount, currency, timestamp
  )

/**
 * Builder for OrderCreatedProtoWrapper following Protobuf builder pattern.
 */
class OrderCreatedProtoBuilder:
  private var eventType: String = "OrderCreated"
  private var orderId: String = ""
  private var customerId: String = ""
  private var amount: Double = 0.0
  private var currency: String = ""
  private var timestamp: Long = 0L

  def setEventType(v: String): OrderCreatedProtoBuilder = { eventType = v; this }
  def setOrderId(v: String): OrderCreatedProtoBuilder = { orderId = v; this }
  def setCustomerId(v: String): OrderCreatedProtoBuilder = { customerId = v; this }
  def setAmount(v: Double): OrderCreatedProtoBuilder = { amount = v; this }
  def setCurrency(v: String): OrderCreatedProtoBuilder = { currency = v; this }
  def setTimestamp(v: Long): OrderCreatedProtoBuilder = { timestamp = v; this }

  def build(): OrderCreatedProtoWrapper = new OrderCreatedProtoWrapper(
    eventType, orderId, customerId, amount, currency, timestamp
  )

// =============================================================================
// PaymentProcessedProtoWrapper - Protobuf DynamicMessage wrapper
// =============================================================================

/**
 * Protobuf wrapper for PaymentProcessed events.
 * Uses DynamicMessage internally for Schema Registry compatibility.
 *
 * Field numbers match payment-processed.proto:
 * 1: event_type, 2: payment_id, 3: order_id, 4: amount, 5: currency, 6: payment_method, 7: timestamp
 */
class PaymentProcessedProtoWrapper(
  val eventType: String,
  val paymentId: String,
  val orderId: String,
  val amount: Double,
  val currency: String,
  val paymentMethod: String,
  val timestamp: Long
):
  /**
   * Convert to DynamicMessage for Confluent serialization.
   * The schema must match payment-processed.proto field numbers.
   */
  def toDynamicMessage(schema: ProtobufSchema): DynamicMessage =
    val descriptor = schema.toDescriptor
    DynamicMessage.newBuilder(descriptor)
      .setField(descriptor.findFieldByNumber(1), eventType)
      .setField(descriptor.findFieldByNumber(2), paymentId)
      .setField(descriptor.findFieldByNumber(3), orderId)
      .setField(descriptor.findFieldByNumber(4), amount)
      .setField(descriptor.findFieldByNumber(5), currency)
      .setField(descriptor.findFieldByNumber(6), paymentMethod)
      .setField(descriptor.findFieldByNumber(7), timestamp)
      .build()

  override def toString: String =
    s"PaymentProcessedProtoWrapper(eventType=$eventType, paymentId=$paymentId, orderId=$orderId, amount=$amount)"

/**
 * Companion object with schema definition and builder factory.
 */
object PaymentProcessedProtoWrapper:
  /**
   * Protobuf schema string matching payment-processed.proto.
   */
  val SCHEMA_STRING: String =
    """syntax = "proto3";
      |package io.distia.probe.core.testmodels;
      |option java_package = "io.distia.probe.core.testmodels";
      |option java_outer_classname = "PaymentProcessedProtos";
      |option java_multiple_files = true;
      |message PaymentProcessedProto {
      |  string event_type = 1;
      |  string payment_id = 2;
      |  string order_id = 3;
      |  double amount = 4;
      |  string currency = 5;
      |  string payment_method = 6;
      |  int64 timestamp = 7;
      |}""".stripMargin

  val SCHEMA: ProtobufSchema = new ProtobufSchema(SCHEMA_STRING)

  def newBuilder(): PaymentProcessedProtoBuilder = new PaymentProcessedProtoBuilder()

  /**
   * Create from DynamicMessage (for deserialization).
   */
  def fromDynamicMessage(msg: DynamicMessage): PaymentProcessedProtoWrapper =
    val descriptor = msg.getDescriptorForType
    new PaymentProcessedProtoWrapper(
      eventType = msg.getField(descriptor.findFieldByNumber(1)).asInstanceOf[String],
      paymentId = msg.getField(descriptor.findFieldByNumber(2)).asInstanceOf[String],
      orderId = msg.getField(descriptor.findFieldByNumber(3)).asInstanceOf[String],
      amount = msg.getField(descriptor.findFieldByNumber(4)).asInstanceOf[java.lang.Double].doubleValue(),
      currency = msg.getField(descriptor.findFieldByNumber(5)).asInstanceOf[String],
      paymentMethod = msg.getField(descriptor.findFieldByNumber(6)).asInstanceOf[String],
      timestamp = msg.getField(descriptor.findFieldByNumber(7)).asInstanceOf[java.lang.Long].longValue()
    )

  def apply(
    paymentId: String,
    orderId: String,
    amount: Double,
    eventType: String = "PaymentProcessed",
    currency: String = "",
    paymentMethod: String = "",
    timestamp: Long = 0L
  ): PaymentProcessedProtoWrapper = new PaymentProcessedProtoWrapper(
    eventType, paymentId, orderId, amount, currency, paymentMethod, timestamp
  )

/**
 * Builder for PaymentProcessedProtoWrapper following Protobuf builder pattern.
 */
class PaymentProcessedProtoBuilder:
  private var eventType: String = "PaymentProcessed"
  private var paymentId: String = ""
  private var orderId: String = ""
  private var amount: Double = 0.0
  private var currency: String = ""
  private var paymentMethod: String = ""
  private var timestamp: Long = 0L

  def setEventType(v: String): PaymentProcessedProtoBuilder = { eventType = v; this }
  def setPaymentId(v: String): PaymentProcessedProtoBuilder = { paymentId = v; this }
  def setOrderId(v: String): PaymentProcessedProtoBuilder = { orderId = v; this }
  def setAmount(v: Double): PaymentProcessedProtoBuilder = { amount = v; this }
  def setCurrency(v: String): PaymentProcessedProtoBuilder = { currency = v; this }
  def setPaymentMethod(v: String): PaymentProcessedProtoBuilder = { paymentMethod = v; this }
  def setTimestamp(v: Long): PaymentProcessedProtoBuilder = { timestamp = v; this }

  def build(): PaymentProcessedProtoWrapper = new PaymentProcessedProtoWrapper(
    eventType, paymentId, orderId, amount, currency, paymentMethod, timestamp
  )
