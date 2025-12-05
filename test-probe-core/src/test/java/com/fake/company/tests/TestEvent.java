package com.fake.company.tests;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;

@JsonSchemaTitle("TestEvent")
@JsonTypeName("TestEvent")
public class TestEvent {

    private String eventType;
    private String eventVersion;
    private String eventTestId;
    private long timestamp;
    private String key;
    private String payload;

    public TestEvent() {}

    public TestEvent(String eventType, String eventVersion, String eventTestId,
                     long timestamp, String key, String payload) {
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.eventTestId = eventTestId;
        this.timestamp = timestamp;
        this.key = key;
        this.payload = payload;
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

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "TestEvent{" +
                "eventType='" + eventType + '\'' +
                ", eventVersion='" + eventVersion + '\'' +
                ", eventTestId='" + eventTestId + '\'' +
                ", timestamp=" + timestamp +
                ", key='" + key + '\'' +
                ", payload='" + payload + '\'' +
                '}';
    }
}
