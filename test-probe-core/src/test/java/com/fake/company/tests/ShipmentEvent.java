package com.fake.company.tests;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;

/**
 * Avro SpecificRecord implementation for ShipmentEvent integration tests.
 *
 * This class demonstrates how Java teams can create Avro events for use with
 * the Test Probe framework. It implements SpecificRecord to enable real Avro
 * binary serialization through SerdesFactory and Confluent Schema Registry.
 *
 * Field order matches shipment-event.avsc:
 * 0: eventType, 1: eventVersion, 2: eventTestId, 3: shipmentId, 4: orderId, 5: carrier, 6: trackingNumber, 7: status, 8: timestamp
 *
 * Usage:
 * <pre>
 * ShipmentEvent shipment = new ShipmentEvent("ShipmentEvent", "v1", "shipment-001", "ship-123", "order-456", "UPS", "1Z999AA10123456784", "shipped", System.currentTimeMillis());
 * // Or use builder:
 * ShipmentEvent shipment = ShipmentEvent.newBuilder()
 *     .setShipmentId("ship-123")
 *     .setOrderId("order-456")
 *     .setCarrier("UPS")
 *     .setTrackingNumber("1Z999AA10123456784")
 *     .setStatus("shipped")
 *     .build();
 * </pre>
 */
public class ShipmentEvent implements SpecificRecord {

    /**
     * Avro schema for ShipmentEvent.
     * Must match shipment-event.avsc in test/resources/schemas/
     */
    public static final Schema SCHEMA = new Schema.Parser().parse(
        "{" +
        "  \"type\": \"record\"," +
        "  \"name\": \"ShipmentEvent\"," +
        "  \"namespace\": \"com.fake.company.tests\"," +
        "  \"doc\": \"Shipment event for Avro integration testing\"," +
        "  \"fields\": [" +
        "    {\"name\": \"eventType\", \"type\": \"string\", \"default\": \"ShipmentEvent\"}," +
        "    {\"name\": \"eventVersion\", \"type\": \"string\", \"default\": \"v1\"}," +
        "    {\"name\": \"eventTestId\", \"type\": \"string\"}," +
        "    {\"name\": \"shipmentId\", \"type\": \"string\"}," +
        "    {\"name\": \"orderId\", \"type\": \"string\"}," +
        "    {\"name\": \"carrier\", \"type\": \"string\"}," +
        "    {\"name\": \"trackingNumber\", \"type\": \"string\"}," +
        "    {\"name\": \"status\", \"type\": \"string\"}," +
        "    {\"name\": \"timestamp\", \"type\": \"long\"}" +
        "  ]" +
        "}"
    );

    // Fields - order matches schema field indices
    private String eventType;
    private String eventVersion;
    private String eventTestId;
    private String shipmentId;
    private String orderId;
    private String carrier;
    private String trackingNumber;
    private String status;
    private Long timestamp;

    /**
     * Default constructor for Avro deserialization.
     */
    public ShipmentEvent() {
        this.eventType = "ShipmentEvent";
        this.eventVersion = "v1";
    }

    /**
     * Full constructor for creating ShipmentEvent instances.
     */
    public ShipmentEvent(String eventType, String eventVersion, String eventTestId,
                         String shipmentId, String orderId, String carrier,
                         String trackingNumber, String status, Long timestamp) {
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.eventTestId = eventTestId;
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.status = status;
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
            case 1: return eventVersion;
            case 2: return eventTestId;
            case 3: return shipmentId;
            case 4: return orderId;
            case 5: return carrier;
            case 6: return trackingNumber;
            case 7: return status;
            case 8: return timestamp;
            default: throw new IndexOutOfBoundsException("Invalid field index: " + field);
        }
    }

    @Override
    public void put(int field, Object value) {
        switch (field) {
            // Avro deserializes strings as Utf8 objects - must convert to String
            case 0: eventType = value != null ? value.toString() : null; break;
            case 1: eventVersion = value != null ? value.toString() : null; break;
            case 2: eventTestId = value != null ? value.toString() : null; break;
            case 3: shipmentId = value != null ? value.toString() : null; break;
            case 4: orderId = value != null ? value.toString() : null; break;
            case 5: carrier = value != null ? value.toString() : null; break;
            case 6: trackingNumber = value != null ? value.toString() : null; break;
            case 7: status = value != null ? value.toString() : null; break;
            case 8: timestamp = (Long) value; break;
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

    public String getEventVersion() {
        return eventVersion;
    }

    public void setEventVersion(String eventVersion) {
        this.eventVersion = eventVersion;
    }

    public String getEventTestId() {
        return eventTestId;
    }

    public void setEventTestId(String eventTestId) {
        this.eventTestId = eventTestId;
    }

    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
     * Create a new builder for ShipmentEvent.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for ShipmentEvent following Avro builder conventions.
     */
    public static class Builder {
        private String eventType = "ShipmentEvent";
        private String eventVersion = "v1";
        private String eventTestId = "";
        private String shipmentId = "";
        private String orderId = "";
        private String carrier = "";
        private String trackingNumber = "";
        private String status = "shipped";
        private Long timestamp = System.currentTimeMillis();

        public Builder setEventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder setEventVersion(String eventVersion) {
            this.eventVersion = eventVersion;
            return this;
        }

        public Builder setEventTestId(String eventTestId) {
            this.eventTestId = eventTestId;
            return this;
        }

        public Builder setShipmentId(String shipmentId) {
            this.shipmentId = shipmentId;
            return this;
        }

        public Builder setOrderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder setCarrier(String carrier) {
            this.carrier = carrier;
            return this;
        }

        public Builder setTrackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
            return this;
        }

        public Builder setStatus(String status) {
            this.status = status;
            return this;
        }

        public Builder setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ShipmentEvent build() {
            return new ShipmentEvent(eventType, eventVersion, eventTestId, shipmentId, orderId,
                                     carrier, trackingNumber, status, timestamp);
        }
    }

    // ==========================================================================
    // Object Methods
    // ==========================================================================

    @Override
    public String toString() {
        return "ShipmentEvent{" +
                "eventType='" + eventType + '\'' +
                ", eventVersion='" + eventVersion + '\'' +
                ", eventTestId='" + eventTestId + '\'' +
                ", shipmentId='" + shipmentId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", carrier='" + carrier + '\'' +
                ", trackingNumber='" + trackingNumber + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ShipmentEvent that = (ShipmentEvent) o;

        if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) return false;
        if (eventVersion != null ? !eventVersion.equals(that.eventVersion) : that.eventVersion != null) return false;
        if (eventTestId != null ? !eventTestId.equals(that.eventTestId) : that.eventTestId != null) return false;
        if (shipmentId != null ? !shipmentId.equals(that.shipmentId) : that.shipmentId != null) return false;
        if (orderId != null ? !orderId.equals(that.orderId) : that.orderId != null) return false;
        if (carrier != null ? !carrier.equals(that.carrier) : that.carrier != null) return false;
        if (trackingNumber != null ? !trackingNumber.equals(that.trackingNumber) : that.trackingNumber != null) return false;
        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        return timestamp != null ? timestamp.equals(that.timestamp) : that.timestamp == null;
    }

    @Override
    public int hashCode() {
        int result = eventType != null ? eventType.hashCode() : 0;
        result = 31 * result + (eventVersion != null ? eventVersion.hashCode() : 0);
        result = 31 * result + (eventTestId != null ? eventTestId.hashCode() : 0);
        result = 31 * result + (shipmentId != null ? shipmentId.hashCode() : 0);
        result = 31 * result + (orderId != null ? orderId.hashCode() : 0);
        result = 31 * result + (carrier != null ? carrier.hashCode() : 0);
        result = 31 * result + (trackingNumber != null ? trackingNumber.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        return result;
    }
}
