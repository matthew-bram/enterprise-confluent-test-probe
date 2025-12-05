package com.fake.company.tests;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;

@JsonSchemaTitle("UserEvent")
@JsonTypeName("UserEvent")
public class UserEvent {

    private String eventType;
    private String eventVersion;
    private String eventTestId;
    private String userId;
    private String username;
    private String email;
    private long timestamp;

    public UserEvent() {}

    public UserEvent(String eventType, String eventVersion, String eventTestId,
                     String userId, String username, String email, long timestamp) {
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.eventTestId = eventTestId;
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.timestamp = timestamp;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "UserEvent{" +
                "eventType='" + eventType + '\'' +
                ", eventVersion='" + eventVersion + '\'' +
                ", eventTestId='" + eventTestId + '\'' +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
