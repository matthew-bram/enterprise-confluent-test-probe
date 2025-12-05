package com.fake.company.tests;

import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;

public class InventoryEvent implements SpecificRecord {

    public static final Schema SCHEMA = new Schema.Parser().parse(
        "{" +
        "  \"type\": \"record\"," +
        "  \"name\": \"InventoryEvent\"," +
        "  \"namespace\": \"com.fake.company.tests\"," +
        "  \"doc\": \"Inventory event for Avro integration testing\"," +
        "  \"fields\": [" +
        "    {\"name\": \"eventType\", \"type\": \"string\", \"default\": \"InventoryEvent\"}," +
        "    {\"name\": \"eventVersion\", \"type\": \"string\", \"default\": \"v1\"}," +
        "    {\"name\": \"eventTestId\", \"type\": \"string\"}," +
        "    {\"name\": \"inventoryId\", \"type\": \"string\"}," +
        "    {\"name\": \"productId\", \"type\": \"string\"}," +
        "    {\"name\": \"quantity\", \"type\": \"int\"}," +
        "    {\"name\": \"warehouse\", \"type\": \"string\"}," +
        "    {\"name\": \"timestamp\", \"type\": \"long\"}" +
        "  ]" +
        "}"
    );

    private String eventType;
    private String eventVersion;
    private String eventTestId;
    private String inventoryId;
    private String productId;
    private int quantity;
    private String warehouse;
    private long timestamp;

    public InventoryEvent() {
        this.eventType = "InventoryEvent";
        this.eventVersion = "v1";
    }

    public InventoryEvent(String eventType, String eventVersion, String eventTestId,
                          String inventoryId, String productId, int quantity,
                          String warehouse, long timestamp) {
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.eventTestId = eventTestId;
        this.inventoryId = inventoryId;
        this.productId = productId;
        this.quantity = quantity;
        this.warehouse = warehouse;
        this.timestamp = timestamp;
    }

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
            case 3: return inventoryId;
            case 4: return productId;
            case 5: return quantity;
            case 6: return warehouse;
            case 7: return timestamp;
            default: throw new IndexOutOfBoundsException("Invalid field index: " + field);
        }
    }

    @Override
    public void put(int field, Object value) {
        switch (field) {
            case 0: eventType = value != null ? value.toString() : null; break;
            case 1: eventVersion = value != null ? value.toString() : null; break;
            case 2: eventTestId = value != null ? value.toString() : null; break;
            case 3: inventoryId = value != null ? value.toString() : null; break;
            case 4: productId = value != null ? value.toString() : null; break;
            case 5: quantity = (Integer) value; break;
            case 6: warehouse = value != null ? value.toString() : null; break;
            case 7: timestamp = (Long) value; break;
            default: throw new IndexOutOfBoundsException("Invalid field index: " + field);
        }
    }

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

    public String getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(String inventoryId) {
        this.inventoryId = inventoryId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(String warehouse) {
        this.warehouse = warehouse;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private String eventType = "InventoryEvent";
        private String eventVersion = "v1";
        private String eventTestId = "";
        private String inventoryId = "";
        private String productId = "";
        private int quantity = 0;
        private String warehouse = "";
        private long timestamp = 0L;

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

        public Builder setInventoryId(String inventoryId) {
            this.inventoryId = inventoryId;
            return this;
        }

        public Builder setProductId(String productId) {
            this.productId = productId;
            return this;
        }

        public Builder setQuantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder setWarehouse(String warehouse) {
            this.warehouse = warehouse;
            return this;
        }

        public Builder setTimestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public InventoryEvent build() {
            return new InventoryEvent(eventType, eventVersion, eventTestId,
                inventoryId, productId, quantity, warehouse, timestamp);
        }
    }

    @Override
    public String toString() {
        return "InventoryEvent{" +
                "eventType='" + eventType + '\'' +
                ", eventVersion='" + eventVersion + '\'' +
                ", eventTestId='" + eventTestId + '\'' +
                ", inventoryId='" + inventoryId + '\'' +
                ", productId='" + productId + '\'' +
                ", quantity=" + quantity +
                ", warehouse='" + warehouse + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InventoryEvent that = (InventoryEvent) o;

        if (quantity != that.quantity) return false;
        if (timestamp != that.timestamp) return false;
        if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) return false;
        if (eventVersion != null ? !eventVersion.equals(that.eventVersion) : that.eventVersion != null) return false;
        if (eventTestId != null ? !eventTestId.equals(that.eventTestId) : that.eventTestId != null) return false;
        if (inventoryId != null ? !inventoryId.equals(that.inventoryId) : that.inventoryId != null) return false;
        if (productId != null ? !productId.equals(that.productId) : that.productId != null) return false;
        return warehouse != null ? warehouse.equals(that.warehouse) : that.warehouse == null;
    }

    @Override
    public int hashCode() {
        int result = eventType != null ? eventType.hashCode() : 0;
        result = 31 * result + (eventVersion != null ? eventVersion.hashCode() : 0);
        result = 31 * result + (eventTestId != null ? eventTestId.hashCode() : 0);
        result = 31 * result + (inventoryId != null ? inventoryId.hashCode() : 0);
        result = 31 * result + (productId != null ? productId.hashCode() : 0);
        result = 31 * result + quantity;
        result = 31 * result + (warehouse != null ? warehouse.hashCode() : 0);
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        return result;
    }
}
