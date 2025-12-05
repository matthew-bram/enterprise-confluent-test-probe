package com.fake.company.tests;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;

/**
 * Avro SpecificRecord implementation for OrderEvent integration tests.
 *
 * This class demonstrates how Java teams can create Avro events for use with
 * the Test Probe framework. It implements SpecificRecord to enable real Avro
 * binary serialization through SerdesFactory and Confluent Schema Registry.
 *
 * Field order matches order-event.avsc:
 * 0: eventType, 1: orderId, 2: customerId, 3: amount, 4: currency, 5: timestamp
 *
 * Usage:
 * <pre>
 * OrderEvent order = new OrderEvent("OrderEvent", "order-123", "cust-456", 99.99, "USD", System.currentTimeMillis());
 * // Or use builder:
 * OrderEvent order = OrderEvent.newBuilder()
 *     .setOrderId("order-123")
 *     .setCustomerId("cust-456")
 *     .setAmount(99.99)
 *     .build();
 * </pre>
 */
public class OrderEvent implements SpecificRecord {

    /**
     * Avro schema for OrderEvent.
     * Must match order-event.avsc in test/resources/schemas/
     */
    public static final Schema SCHEMA = new Schema.Parser().parse(
        "{" +
        "  \"type\": \"record\"," +
        "  \"name\": \"OrderEvent\"," +
        "  \"namespace\": \"com.fake.company.tests\"," +
        "  \"doc\": \"Order event for Avro integration testing\"," +
        "  \"fields\": [" +
        "    {\"name\": \"eventType\", \"type\": \"string\", \"default\": \"OrderEvent\"}," +
        "    {\"name\": \"orderId\", \"type\": \"string\"}," +
        "    {\"name\": \"customerId\", \"type\": \"string\"}," +
        "    {\"name\": \"amount\", \"type\": \"double\"}," +
        "    {\"name\": \"currency\", \"type\": [\"null\", \"string\"], \"default\": null}," +
        "    {\"name\": \"timestamp\", \"type\": [\"null\", \"long\"], \"default\": null}" +
        "  ]" +
        "}"
    );

    // Fields - order matches schema field indices
    private String eventType;
    private String orderId;
    private String customerId;
    private Double amount;
    private String currency;
    private Long timestamp;

    /**
     * Default constructor for Avro deserialization.
     */
    public OrderEvent() {
        this.eventType = "OrderEvent";
    }

    /**
     * Full constructor for creating OrderEvent instances.
     */
    public OrderEvent(String eventType, String orderId, String customerId,
                      Double amount, String currency, Long timestamp) {
        this.eventType = eventType;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = timestamp;
    }

    // ==========================================================================
    // SpecificRecord Implementation
    // ==========================================================================

    @Override
    public Schema getSchema() {
        return SCHEMA;
    }

    @Override
    public Object get(int field) {
        switch (field) {
            case 0: return eventType;
            case 1: return orderId;
            case 2: return customerId;
            case 3: return amount;
            case 4: return currency;
            case 5: return timestamp;
            default: throw new IndexOutOfBoundsException("Invalid field index: " + field);
        }
    }

    @Override
    public void put(int field, Object value) {
        switch (field) {
            // Avro deserializes strings as Utf8 objects - must convert to String
            case 0: eventType = value != null ? value.toString() : null; break;
            case 1: orderId = value != null ? value.toString() : null; break;
            case 2: customerId = value != null ? value.toString() : null; break;
            case 3: amount = (Double) value; break;
            case 4: currency = value != null ? value.toString() : null; break;
            case 5: timestamp = (Long) value; break;
            default: throw new IndexOutOfBoundsException("Invalid field index: " + field);
        }
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

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
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
     * Create a new builder for OrderEvent.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for OrderEvent following Avro builder conventions.
     */
    public static class Builder {
        private String eventType = "OrderEvent";
        private String orderId = "";
        private String customerId = "";
        private Double amount = 0.0;
        private String currency = null;
        private Long timestamp = null;

        public Builder setEventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder setOrderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder setCustomerId(String customerId) {
            this.customerId = customerId;
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

        public Builder setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public OrderEvent build() {
            return new OrderEvent(eventType, orderId, customerId, amount, currency, timestamp);
        }
    }

    // ==========================================================================
    // Object Methods
    // ==========================================================================

    @Override
    public String toString() {
        return "OrderEvent{" +
                "eventType='" + eventType + '\'' +
                ", orderId='" + orderId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OrderEvent that = (OrderEvent) o;

        if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) return false;
        if (orderId != null ? !orderId.equals(that.orderId) : that.orderId != null) return false;
        if (customerId != null ? !customerId.equals(that.customerId) : that.customerId != null) return false;
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) return false;
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) return false;
        return timestamp != null ? timestamp.equals(that.timestamp) : that.timestamp == null;
    }

    @Override
    public int hashCode() {
        int result = eventType != null ? eventType.hashCode() : 0;
        result = 31 * result + (orderId != null ? orderId.hashCode() : 0);
        result = 31 * result + (customerId != null ? customerId.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        return result;
    }
}
