package io.distia.probe.core.testutil;

import io.distia.probe.core.fixtures.CloudEventFixture$;
import io.distia.probe.core.pubsub.models.CloudEvent;

import java.util.UUID;

/**
 * Java-friendly factory for CloudEvent creation.
 *
 * Wraps Scala CloudEventFixtures for use in Java step definitions.
 * Follows the test architecture contract: use builders/fixtures, never hardcode.
 *
 * Usage:
 * <pre>{@code
 *   CloudEvent key = CloudEventFactory.createForEventTestId("evt-001", "TestEvent", "v1");
 * }</pre>
 */
public final class CloudEventFactory {

    private CloudEventFactory() {}

    /**
     * Create CloudEvent for an eventTestId.
     *
     * Generates deterministic correlationId from eventTestId using UUID.nameUUIDFromBytes().
     * This matches the ubiquitous language: eventTestId (human readable) -> correlationId (UUID).
     *
     * @param eventTestId Human-readable test identifier (e.g., "evt-001")
     * @param eventType Event type (e.g., "TestEvent")
     * @param payloadVersion Payload schema version (e.g., "v1")
     * @return CloudEvent with correlationId derived from eventTestId
     */
    public static CloudEvent createForEventTestId(
            String eventTestId,
            String eventType,
            String payloadVersion
    ) {
        // Generate deterministic UUID from eventTestId (same as ubiquitous language doc)
        UUID correlationId = UUID.nameUUIDFromBytes(
            eventTestId.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        return CloudEventFixture$.MODULE$.createCloudEventWithTypeAndVersion(
            eventType,
            payloadVersion,
            correlationId.toString()
        );
    }

    /**
     * Create CloudEvent for a specific topic.
     *
     * @param topic Kafka topic name
     * @param eventType Event type (e.g., "TestEvent")
     * @param payloadVersion Payload schema version (e.g., "v1")
     * @return CloudEvent with topic-based source
     */
    public static CloudEvent createForTopic(
            String topic,
            String eventType,
            String payloadVersion
    ) {
        UUID correlationId = UUID.randomUUID();

        return CloudEventFixture$.MODULE$.createCloudEventWithTopic(
            topic,
            eventType,
            correlationId.toString(),
            payloadVersion
        );
    }

    /**
     * Create CloudEvent with specific correlationId.
     *
     * @param correlationId Correlation ID (UUID string)
     * @param eventType Event type
     * @param payloadVersion Payload schema version
     * @return CloudEvent with the specified correlationId
     */
    public static CloudEvent createWithCorrelationId(
            String correlationId,
            String eventType,
            String payloadVersion
    ) {
        return CloudEventFixture$.MODULE$.createCloudEventWithTypeAndVersion(
            eventType,
            payloadVersion,
            correlationId
        );
    }

    /**
     * Get the correlationId that would be generated for an eventTestId.
     *
     * Useful for lookup operations where you need the correlationId
     * but don't need a full CloudEvent.
     *
     * @param eventTestId Human-readable test identifier
     * @return Correlation ID as String (deterministic UUID)
     */
    public static String getCorrelationIdForEventTestId(String eventTestId) {
        return UUID.nameUUIDFromBytes(
            eventTestId.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        ).toString();
    }
}
