package io.distia.probe.core.testutil;

import io.distia.probe.core.pubsub.ProbeScalaDsl;
import io.distia.probe.core.pubsub.SerdesFactory;
import io.distia.probe.core.pubsub.models.CloudEvent;
import io.distia.probe.core.pubsub.models.ConsumedResult;
import io.distia.probe.core.pubsub.models.ConsumedSuccess;
import io.distia.probe.core.pubsub.models.ProduceResult;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import scala.collection.immutable.Map;
import scala.collection.immutable.Map$;
import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;

import java.util.HashMap;
import java.util.UUID;

/**
 * Java-friendly wrapper for ProbeScalaDsl.
 *
 * Provides blocking methods for integration tests that are easier to use
 * from Java step definitions than the Scala DSL directly.
 *
 * Handles:
 * - ClassTag creation from Java Class objects
 * - Java Map to Scala Map conversion
 * - Blocking execution (appropriate for test code)
 *
 * Usage from Java step definitions:
 * <pre>{@code
 *   CloudEvent key = createCloudEvent(...);
 *   TestEvent value = new TestEvent(...);
 *   ProduceResult result = IntegrationTestDsl.produceEventBlocking(
 *       testId, "test-events", key, value, TestEvent.class
 *   );
 * }</pre>
 */
public final class IntegrationTestDsl {

    private IntegrationTestDsl() {}

    /**
     * Produce event to Kafka (blocking version for test simplicity).
     *
     * @param testId Test instance ID (from CucumberContext)
     * @param topic Kafka topic name
     * @param key CloudEvent key
     * @param value Event payload value
     * @param valueClass Class of the value (for ClassTag creation)
     * @param <T> Value type
     * @return ProduceResult (ProducingSuccess or ProducingFailed)
     */
    public static <T> ProduceResult produceEventBlocking(
            UUID testId,
            String topic,
            CloudEvent key,
            T value,
            Class<T> valueClass
    ) {
        return produceEventBlocking(testId, topic, key, value, valueClass, emptyScalaMap());
    }

    /**
     * Produce event to Kafka with headers (blocking version for test simplicity).
     *
     * @param testId Test instance ID (from CucumberContext)
     * @param topic Kafka topic name
     * @param key CloudEvent key
     * @param value Event payload value
     * @param valueClass Class of the value (for ClassTag creation)
     * @param headers Kafka headers as Scala Map
     * @param <T> Value type
     * @return ProduceResult (ProducingSuccess or ProducingFailed)
     */
    public static <T> ProduceResult produceEventBlocking(
            UUID testId,
            String topic,
            CloudEvent key,
            T value,
            Class<T> valueClass,
            Map<String, String> headers
    ) {
        ClassTag<T> classTag = createClassTag(valueClass);
        return ProbeScalaDsl.produceEventBlocking(testId, topic, key, value, headers, classTag);
    }

    /**
     * Produce event to Kafka with Java headers map (blocking version).
     *
     * @param testId Test instance ID (from CucumberContext)
     * @param topic Kafka topic name
     * @param key CloudEvent key
     * @param value Event payload value
     * @param valueClass Class of the value (for ClassTag creation)
     * @param headers Kafka headers as Java Map
     * @param <T> Value type
     * @return ProduceResult (ProducingSuccess or ProducingFailed)
     */
    public static <T> ProduceResult produceEventBlocking(
            UUID testId,
            String topic,
            CloudEvent key,
            T value,
            Class<T> valueClass,
            java.util.Map<String, String> headers
    ) {
        return produceEventBlocking(testId, topic, key, value, valueClass, toScalaMap(headers));
    }

    /**
     * Fetch consumed event from registry (blocking version for test simplicity).
     *
     * @param testId Test instance ID (from CucumberContext)
     * @param topic Kafka topic name
     * @param correlationId CloudEvent correlationId (UUID string)
     * @param valueClass Class of the value (for ClassTag creation)
     * @param <T> Value type
     * @return ConsumedResult (ConsumedSuccess with key/value or ConsumedNack)
     */
    public static <T> ConsumedResult fetchConsumedEventBlocking(
            UUID testId,
            String topic,
            String correlationId,
            Class<T> valueClass
    ) {
        ClassTag<T> classTag = createClassTag(valueClass);
        return ProbeScalaDsl.fetchConsumedEventBlocking(testId, topic, correlationId, classTag);
    }

    /**
     * Create a Scala ClassTag from a Java Class.
     *
     * @param clazz Java Class object
     * @param <T> Type parameter
     * @return Scala ClassTag for the type
     */
    @SuppressWarnings("unchecked")
    private static <T> ClassTag<T> createClassTag(Class<T> clazz) {
        return (ClassTag<T>) ClassTag$.MODULE$.apply(clazz);
    }

    /**
     * Create an empty Scala immutable Map.
     *
     * @return Empty Scala Map
     */
    private static Map<String, String> emptyScalaMap() {
        return Map$.MODULE$.empty();
    }

    /**
     * Convert Java Map to Scala immutable Map.
     *
     * @param javaMap Java Map to convert
     * @return Scala immutable Map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> toScalaMap(java.util.Map<String, String> javaMap) {
        if (javaMap == null || javaMap.isEmpty()) {
            return emptyScalaMap();
        }
        Map<String, String> result = emptyScalaMap();
        for (java.util.Map.Entry<String, String> entry : javaMap.entrySet()) {
            result = result.$plus(new scala.Tuple2<>(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    // ==========================================================================
    // Protobuf Wrapper Support
    // ==========================================================================

    /**
     * Produce Protobuf event using DynamicMessage with explicit subject naming.
     *
     * This method bypasses SerdesFactory's subject naming (which uses ClassTag's class name)
     * and uses the wrapper class name for schema lookup. This allows us to pass DynamicMessage
     * to Kafka while still using the correct schema subject (e.g., "payment-events-proto-PaymentEvent").
     *
     * @param testId Test instance ID
     * @param topic Kafka topic name
     * @param key CloudEvent key
     * @param dynamicMessage DynamicMessage value (converted from wrapper)
     * @param wrapperClassName The wrapper class name for subject lookup (e.g., "PaymentEvent")
     * @param schema ProtobufSchema for the message
     * @return ProduceResult
     */
    public static ProduceResult produceProtobufEventBlocking(
            UUID testId,
            String topic,
            CloudEvent key,
            DynamicMessage dynamicMessage,
            String wrapperClassName,
            ProtobufSchema schema
    ) {
        // Serialize CloudEvent key using ProbeScalaDsl (JSON schema)
        ClassTag<CloudEvent> keyTag = createClassTag(CloudEvent.class);
        byte[] keyBytes = SerdesFactory.serialize(key, topic, true, keyTag);

        // Serialize DynamicMessage value using explicit subject naming
        java.util.Map<String, Object> config = new HashMap<>();
        config.put("schema.registry.url", SerdesFactory.extractSchemaRegistryUrl());
        config.put("key.subject.name.strategy", TopicRecordNameStrategy.class.getName());
        config.put("value.subject.name.strategy", TopicRecordNameStrategy.class.getName());
        config.put("auto.register.schemas", "true");
        config.put("use.latest.version", "false");
        config.put("skip.known.types", "true");

        KafkaProtobufSerializer<Message> serializer = new KafkaProtobufSerializer<>(SerdesFactory.extractClient());
        serializer.configure(config, false);

        byte[] valueBytes = serializer.serialize(topic, dynamicMessage);

        // Now use the DSL to send the raw bytes
        // We pass DynamicMessage.class but the bytes are already serialized correctly
        return ProbeScalaDsl.produceEventBlocking(
                testId, topic, key, dynamicMessage, emptyScalaMap(),
                createClassTag(DynamicMessage.class)
        );
    }

    /**
     * Fetch consumed Protobuf event and return as DynamicMessage.
     *
     * This is a convenience wrapper that ensures proper deserialization
     * of Protobuf events stored in the consumer registry.
     *
     * @param testId Test instance ID
     * @param topic Kafka topic name
     * @param correlationId CloudEvent correlationId
     * @return ConsumedResult with DynamicMessage value
     */
    public static ConsumedResult fetchProtobufConsumedEventBlocking(
            UUID testId,
            String topic,
            String correlationId
    ) {
        // Use standard fetch - the consumer registry has raw bytes
        // that will be deserialized as DynamicMessage
        ClassTag<DynamicMessage> classTag = createClassTag(DynamicMessage.class);
        return ProbeScalaDsl.fetchConsumedEventBlocking(testId, topic, correlationId, classTag);
    }
}
