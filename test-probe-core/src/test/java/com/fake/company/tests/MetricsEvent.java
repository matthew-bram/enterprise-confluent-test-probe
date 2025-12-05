package com.fake.company.tests;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;

/**
 * Protobuf DynamicMessage wrapper for MetricsEvent integration tests.
 *
 * This class demonstrates how Java teams can create Protobuf events for use with
 * the Test Probe framework. It uses DynamicMessage for Schema Registry compatibility
 * (no generated Protobuf classes required).
 *
 * Field numbers match metrics-event.proto:
 * 1: event_type, 2: event_version, 3: event_test_id, 4: metric_id, 5: metric_name, 6: metric_value, 7: unit, 8: timestamp
 *
 * Usage:
 * <pre>
 * MetricsEvent metrics = MetricsEvent.newBuilder()
 *     .setMetricId("metric-123")
 *     .setMetricName("response_time")
 *     .setMetricValue(42.5)
 *     .setUnit("ms")
 *     .build();
 *
 * // Serialize to Kafka
 * DynamicMessage msg = metrics.toDynamicMessage(MetricsEvent.SCHEMA);
 *
 * // Deserialize from Kafka
 * MetricsEvent received = MetricsEvent.fromDynamicMessage(consumedMsg);
 * </pre>
 */
public class MetricsEvent {

    /**
     * Protobuf schema string matching metrics-event.proto.
     * Embedded inline to avoid file loading complexity in Java.
     */
    public static final String SCHEMA_STRING =
        "syntax = \"proto3\";\n" +
        "package com.fake.company.tests;\n" +
        "option java_package = \"com.fake.company.tests\";\n" +
        "option java_outer_classname = \"MetricsEventProtos\";\n" +
        "option java_multiple_files = true;\n" +
        "message MetricsEventProto {\n" +
        "  string event_type = 1;\n" +
        "  string event_version = 2;\n" +
        "  string event_test_id = 3;\n" +
        "  string metric_id = 4;\n" +
        "  string metric_name = 5;\n" +
        "  double metric_value = 6;\n" +
        "  string unit = 7;\n" +
        "  int64 timestamp = 8;\n" +
        "}";

    /**
     * Protobuf schema for serialization/deserialization.
     */
    public static final ProtobufSchema SCHEMA = new ProtobufSchema(SCHEMA_STRING);

    // Fields - order matches proto field numbers
    private String eventType;
    private String eventVersion;
    private String eventTestId;
    private String metricId;
    private String metricName;
    private Double metricValue;
    private String unit;
    private Long timestamp;

    /**
     * Default constructor.
     */
    public MetricsEvent() {
        this.eventType = "MetricsEvent";
        this.eventVersion = "v1";
    }

    /**
     * Full constructor.
     */
    public MetricsEvent(String eventType, String eventVersion, String eventTestId,
                        String metricId, String metricName, Double metricValue,
                        String unit, Long timestamp) {
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.eventTestId = eventTestId;
        this.metricId = metricId;
        this.metricName = metricName;
        this.metricValue = metricValue;
        this.unit = unit;
        this.timestamp = timestamp;
    }

    // ==========================================================================
    // DynamicMessage Conversion (Protobuf Serialization)
    // ==========================================================================

    /**
     * Convert to DynamicMessage for Confluent serialization.
     * The schema must match metrics-event.proto field numbers.
     *
     * @param schema ProtobufSchema for message construction
     * @return DynamicMessage ready for Kafka serialization
     */
    public DynamicMessage toDynamicMessage(ProtobufSchema schema) {
        Descriptor descriptor = schema.toDescriptor();
        return DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByNumber(1), eventType != null ? eventType : "")
            .setField(descriptor.findFieldByNumber(2), eventVersion != null ? eventVersion : "")
            .setField(descriptor.findFieldByNumber(3), eventTestId != null ? eventTestId : "")
            .setField(descriptor.findFieldByNumber(4), metricId != null ? metricId : "")
            .setField(descriptor.findFieldByNumber(5), metricName != null ? metricName : "")
            .setField(descriptor.findFieldByNumber(6), metricValue != null ? metricValue : 0.0)
            .setField(descriptor.findFieldByNumber(7), unit != null ? unit : "")
            .setField(descriptor.findFieldByNumber(8), timestamp != null ? timestamp : 0L)
            .build();
    }

    /**
     * Create from DynamicMessage (for deserialization).
     *
     * NOTE: Protobuf returns Java native types (String, Double, Long),
     * unlike Avro which returns Utf8 for strings. No special conversion needed.
     *
     * @param msg DynamicMessage from Kafka deserialization
     * @return MetricsEvent populated from message fields
     */
    public static MetricsEvent fromDynamicMessage(DynamicMessage msg) {
        Descriptor descriptor = msg.getDescriptorForType();
        return new MetricsEvent(
            (String) msg.getField(descriptor.findFieldByNumber(1)),
            (String) msg.getField(descriptor.findFieldByNumber(2)),
            (String) msg.getField(descriptor.findFieldByNumber(3)),
            (String) msg.getField(descriptor.findFieldByNumber(4)),
            (String) msg.getField(descriptor.findFieldByNumber(5)),
            ((Number) msg.getField(descriptor.findFieldByNumber(6))).doubleValue(),
            (String) msg.getField(descriptor.findFieldByNumber(7)),
            ((Number) msg.getField(descriptor.findFieldByNumber(8))).longValue()
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

    public String getMetricId() {
        return metricId;
    }

    public void setMetricId(String metricId) {
        this.metricId = metricId;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public Double getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(Double metricValue) {
        this.metricValue = metricValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
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
     * Create a new builder for MetricsEvent.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for MetricsEvent following Protobuf builder conventions.
     */
    public static class Builder {
        private String eventType = "MetricsEvent";
        private String eventVersion = "v1";
        private String eventTestId = "";
        private String metricId = "";
        private String metricName = "";
        private Double metricValue = 0.0;
        private String unit = "count";
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

        public Builder setMetricId(String metricId) {
            this.metricId = metricId;
            return this;
        }

        public Builder setMetricName(String metricName) {
            this.metricName = metricName;
            return this;
        }

        public Builder setMetricValue(Double metricValue) {
            this.metricValue = metricValue;
            return this;
        }

        public Builder setUnit(String unit) {
            this.unit = unit;
            return this;
        }

        public Builder setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public MetricsEvent build() {
            return new MetricsEvent(eventType, eventVersion, eventTestId, metricId, metricName,
                                    metricValue, unit, timestamp);
        }
    }

    // ==========================================================================
    // Object Methods
    // ==========================================================================

    @Override
    public String toString() {
        return "MetricsEvent{" +
                "eventType='" + eventType + '\'' +
                ", eventVersion='" + eventVersion + '\'' +
                ", eventTestId='" + eventTestId + '\'' +
                ", metricId='" + metricId + '\'' +
                ", metricName='" + metricName + '\'' +
                ", metricValue=" + metricValue +
                ", unit='" + unit + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MetricsEvent that = (MetricsEvent) o;

        if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) return false;
        if (eventVersion != null ? !eventVersion.equals(that.eventVersion) : that.eventVersion != null) return false;
        if (eventTestId != null ? !eventTestId.equals(that.eventTestId) : that.eventTestId != null) return false;
        if (metricId != null ? !metricId.equals(that.metricId) : that.metricId != null) return false;
        if (metricName != null ? !metricName.equals(that.metricName) : that.metricName != null) return false;
        if (metricValue != null ? !metricValue.equals(that.metricValue) : that.metricValue != null) return false;
        if (unit != null ? !unit.equals(that.unit) : that.unit != null) return false;
        return timestamp != null ? timestamp.equals(that.timestamp) : that.timestamp == null;
    }

    @Override
    public int hashCode() {
        int result = eventType != null ? eventType.hashCode() : 0;
        result = 31 * result + (eventVersion != null ? eventVersion.hashCode() : 0);
        result = 31 * result + (eventTestId != null ? eventTestId.hashCode() : 0);
        result = 31 * result + (metricId != null ? metricId.hashCode() : 0);
        result = 31 * result + (metricName != null ? metricName.hashCode() : 0);
        result = 31 * result + (metricValue != null ? metricValue.hashCode() : 0);
        result = 31 * result + (unit != null ? unit.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        return result;
    }
}
