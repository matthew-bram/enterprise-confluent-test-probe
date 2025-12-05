package com.fake.company.tests;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;

/**
 * Protobuf DynamicMessage wrapper for PaymentEvent integration tests.
 *
 * This class demonstrates how Java teams can create Protobuf events for use with
 * the Test Probe framework. It uses DynamicMessage for Schema Registry compatibility
 * (no generated Protobuf classes required).
 *
 * Field numbers match payment-event.proto:
 * 1: event_type, 2: payment_id, 3: order_id, 4: amount, 5: currency, 6: payment_method, 7: timestamp
 *
 * Usage:
 * <pre>
 * PaymentEvent payment = PaymentEvent.newBuilder()
 *     .setPaymentId("pay-123")
 *     .setOrderId("order-456")
 *     .setAmount(99.99)
 *     .setPaymentMethod("CREDIT_CARD")
 *     .build();
 *
 * // Serialize to Kafka
 * DynamicMessage msg = payment.toDynamicMessage(PaymentEvent.SCHEMA);
 *
 * // Deserialize from Kafka
 * PaymentEvent received = PaymentEvent.fromDynamicMessage(consumedMsg);
 * </pre>
 */
public class PaymentEvent {

    /**
     * Protobuf schema string matching payment-event.proto.
     * Embedded inline to avoid file loading complexity in Java.
     */
    public static final String SCHEMA_STRING =
        "syntax = \"proto3\";\n" +
        "package com.fake.company.tests;\n" +
        "option java_package = \"com.fake.company.tests\";\n" +
        "option java_outer_classname = \"PaymentEventProtos\";\n" +
        "option java_multiple_files = true;\n" +
        "message PaymentEventProto {\n" +
        "  string event_type = 1;\n" +
        "  string payment_id = 2;\n" +
        "  string order_id = 3;\n" +
        "  double amount = 4;\n" +
        "  string currency = 5;\n" +
        "  string payment_method = 6;\n" +
        "  int64 timestamp = 7;\n" +
        "}";

    /**
     * Protobuf schema for serialization/deserialization.
     */
    public static final ProtobufSchema SCHEMA = new ProtobufSchema(SCHEMA_STRING);

    // Fields - order matches proto field numbers
    private String eventType;
    private String paymentId;
    private String orderId;
    private Double amount;
    private String currency;
    private String paymentMethod;
    private Long timestamp;

    /**
     * Default constructor.
     */
    public PaymentEvent() {
        this.eventType = "PaymentEvent";
    }

    /**
     * Full constructor.
     */
    public PaymentEvent(String eventType, String paymentId, String orderId,
                        Double amount, String currency, String paymentMethod, Long timestamp) {
        this.eventType = eventType;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.timestamp = timestamp;
    }

    // ==========================================================================
    // DynamicMessage Conversion (Protobuf Serialization)
    // ==========================================================================

    /**
     * Convert to DynamicMessage for Confluent serialization.
     * The schema must match payment-event.proto field numbers.
     *
     * @param schema ProtobufSchema for message construction
     * @return DynamicMessage ready for Kafka serialization
     */
    public DynamicMessage toDynamicMessage(ProtobufSchema schema) {
        Descriptor descriptor = schema.toDescriptor();
        return DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByNumber(1), eventType != null ? eventType : "")
            .setField(descriptor.findFieldByNumber(2), paymentId != null ? paymentId : "")
            .setField(descriptor.findFieldByNumber(3), orderId != null ? orderId : "")
            .setField(descriptor.findFieldByNumber(4), amount != null ? amount : 0.0)
            .setField(descriptor.findFieldByNumber(5), currency != null ? currency : "")
            .setField(descriptor.findFieldByNumber(6), paymentMethod != null ? paymentMethod : "")
            .setField(descriptor.findFieldByNumber(7), timestamp != null ? timestamp : 0L)
            .build();
    }

    /**
     * Create from DynamicMessage (for deserialization).
     *
     * NOTE: Protobuf returns Java native types (String, Double, Long),
     * unlike Avro which returns Utf8 for strings. No special conversion needed.
     *
     * @param msg DynamicMessage from Kafka deserialization
     * @return PaymentEvent populated from message fields
     */
    public static PaymentEvent fromDynamicMessage(DynamicMessage msg) {
        Descriptor descriptor = msg.getDescriptorForType();
        return new PaymentEvent(
            (String) msg.getField(descriptor.findFieldByNumber(1)),
            (String) msg.getField(descriptor.findFieldByNumber(2)),
            (String) msg.getField(descriptor.findFieldByNumber(3)),
            ((Number) msg.getField(descriptor.findFieldByNumber(4))).doubleValue(),
            (String) msg.getField(descriptor.findFieldByNumber(5)),
            (String) msg.getField(descriptor.findFieldByNumber(6)),
            ((Number) msg.getField(descriptor.findFieldByNumber(7))).longValue()
        );
    }

    // ==========================================================================
    // Getters and Setters
    // ==========================================================================

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    // ==========================================================================
    // Builder Pattern (Fluent API)
    // ==========================================================================

    /**
     * Create a new builder for PaymentEvent.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for PaymentEvent following Protobuf builder conventions.
     */
    public static class Builder {
        private String eventType = "PaymentEvent";
        private String paymentId = "";
        private String orderId = "";
        private Double amount = 0.0;
        private String currency = null;
        private String paymentMethod = null;
        private Long timestamp = null;

        public Builder setEventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder setPaymentId(String paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder setOrderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder setAmount(Double amount) {
            this.amount = amount;
            return this;
        }

        public Builder setCurrency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder setPaymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        public Builder setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public PaymentEvent build() {
            return new PaymentEvent(eventType, paymentId, orderId, amount, currency, paymentMethod, timestamp);
        }
    }

    // ==========================================================================
    // Object Methods
    // ==========================================================================

    @Override
    public String toString() {
        return "PaymentEvent{" +
                "eventType='" + eventType + '\'' +
                ", paymentId='" + paymentId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PaymentEvent that = (PaymentEvent) o;

        if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) return false;
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) return false;
        if (orderId != null ? !orderId.equals(that.orderId) : that.orderId != null) return false;
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) return false;
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) return false;
        if (paymentMethod != null ? !paymentMethod.equals(that.paymentMethod) : that.paymentMethod != null) return false;
        return timestamp != null ? timestamp.equals(that.timestamp) : that.timestamp == null;
    }

    @Override
    public int hashCode() {
        int result = eventType != null ? eventType.hashCode() : 0;
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (orderId != null ? orderId.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (paymentMethod != null ? paymentMethod.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        return result;
    }
}
