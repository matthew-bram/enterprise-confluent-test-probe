package com.fake.company.tests;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;

@JsonSchemaTitle("NotificationEvent")
@JsonTypeName("NotificationEvent")
public class NotificationEvent {

    private String eventType;
    private String eventVersion;
    private String eventTestId;
    private String notificationId;
    private String userId;
    private String message;
    private String channel;
    private long timestamp;

    public NotificationEvent() {}

    public NotificationEvent(String eventType, String eventVersion, String eventTestId,
                             String notificationId, String userId, String message,
                             String channel, long timestamp) {
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.eventTestId = eventTestId;
        this.notificationId = notificationId;
        this.userId = userId;
        this.message = message;
        this.channel = channel;
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

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "NotificationEvent{" +
                "eventType='" + eventType + '\'' +
                ", eventVersion='" + eventVersion + '\'' +
                ", eventTestId='" + eventTestId + '\'' +
                ", notificationId='" + notificationId + '\'' +
                ", userId='" + userId + '\'' +
                ", message='" + message + '\'' +
                ", channel='" + channel + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
