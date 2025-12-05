package io.distia.probe.javaapi;

import io.distia.probe.core.pubsub.models.CloudEvent;
import io.distia.probe.core.pubsub.models.ConsumedResult;
import io.distia.probe.core.pubsub.models.ProduceResult;
import io.distia.probe.core.pubsub.ProbeScalaDsl;
import io.distia.probe.core.pubsub.ProbeScalaDsl$;
import org.apache.pekko.actor.typed.ActorSystem;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import scala.util.Try;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static scala.jdk.CollectionConverters.MapHasAsScala;

/**
 * Java DSL for Test-Probe event production and consumption.
 *
 * <p>Provides CompletionStage-based methods for Java interoperability.
 * All methods delegate to ProbeScalaDsl with automatic type conversions:
 * <ul>
 *   <li>Scala Future → CompletionStage</li>
 *   <li>Java Map → Scala Map</li>
 *   <li>Class&lt;T&gt; → ClassTag[T]</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * // Initialize (once at application startup)
 * ActorSystem&lt;?&gt; system = ActorSystem.create(...);
 * ProbeJavaDsl.registerSystem(system);
 *
 * // Produce event
 * CloudEvent key = new CloudEvent(...);
 * MyEvent value = new MyEvent(...);
 * Map&lt;String, String&gt; headers = Map.of("trace-id", "123");
 *
 * CompletionStage&lt;ProduceResult&gt; future = ProbeJavaDsl.produceEvent(
 *     testId, "my-topic", key, value, headers, MyEvent.class
 * );
 *
 * // Consume event
 * CompletionStage&lt;ConsumedResult&gt; consumed = ProbeJavaDsl.fetchConsumedEvent(
 *     testId, "my-topic", "correlation-id", MyEvent.class
 * );
 *
 * // Blocking variants (use with caution)
 * ProduceResult result = ProbeJavaDsl.produceEventBlocking(
 *     testId, "my-topic", key, value, headers, MyEvent.class
 * );
 * </pre>
 */
public final class ProbeJavaDsl {

    private static final ProbeScalaDsl$ scalaDsl = ProbeScalaDsl$.MODULE$;
    private static volatile ActorSystem<?> actorSystem;

    private ProbeJavaDsl() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Register the actor system with the DSL.
     * Must be called once before using any DSL methods.
     *
     * @param system Pekko ActorSystem
     */
    public static void registerSystem(ActorSystem<?> system) {
        actorSystem = system;
        scalaDsl.registerSystem(system);
    }

    /**
     * Produce event to Kafka topic (async).
     *
     * @param testId UUID of the test
     * @param topic Kafka topic name
     * @param key CloudEvent key (contains type, version, correlation ID)
     * @param value Event payload (will be serialized based on schema registry)
     * @param headers Additional Kafka headers
     * @param valueClass Class of value type for serialization
     * @param <T> Value type
     * @return CompletionStage of ProduceResult
     * @throws io.distia.probe.core.pubsub.models.DslNotInitializedException if system not registered
     * @throws io.distia.probe.core.pubsub.models.ActorNotRegisteredException if no producer for test/topic
     */
    public static <T> CompletionStage<ProduceResult> produceEvent(
            UUID testId,
            String topic,
            CloudEvent key,
            T value,
            Map<String, String> headers,
            Class<T> valueClass) {

        scala.collection.immutable.Map<String, String> scalaHeaders =
            scala.collection.immutable.Map$.MODULE$.from(MapHasAsScala(headers).asScala());
        ClassTag<T> classTag = ClassTag$.MODULE$.apply(valueClass);
        Future<ProduceResult> scalaFuture =
            scalaDsl.produceEvent(testId, topic, key, value, scalaHeaders, classTag);

        return toCompletableFuture(scalaFuture, actorSystem.executionContext());
    }

    /**
     * Produce event to Kafka topic (blocking).
     * Blocks current thread until production completes (5 second timeout).
     *
     * @param testId UUID of the test
     * @param topic Kafka topic name
     * @param key CloudEvent key
     * @param value Event payload
     * @param headers Additional Kafka headers
     * @param valueClass Class of value type for serialization
     * @param <T> Value type
     * @return ProduceResult
     * @throws io.distia.probe.core.pubsub.models.DslNotInitializedException if system not registered
     * @throws io.distia.probe.core.pubsub.models.ActorNotRegisteredException if no producer for test/topic
     */
    public static <T> ProduceResult produceEventBlocking(
            UUID testId,
            String topic,
            CloudEvent key,
            T value,
            Map<String, String> headers,
            Class<T> valueClass) {

        scala.collection.immutable.Map<String, String> scalaHeaders =
            scala.collection.immutable.Map$.MODULE$.from(MapHasAsScala(headers).asScala());
        ClassTag<T> classTag = ClassTag$.MODULE$.apply(valueClass);
        return scalaDsl.produceEventBlocking(testId, topic, key, value, scalaHeaders, classTag);
    }

    /**
     * Fetch consumed event from Kafka topic (async).
     *
     * @param testId UUID of the test
     * @param topic Kafka topic name
     * @param correlationId CloudEvent correlation ID to match
     * @param valueClass Class of value type for deserialization
     * @param <T> Value type
     * @return CompletionStage of ConsumedResult
     * @throws io.distia.probe.core.pubsub.models.DslNotInitializedException if system not registered
     * @throws io.distia.probe.core.pubsub.models.ActorNotRegisteredException if no consumer for test/topic
     */
    public static <T> CompletionStage<ConsumedResult> fetchConsumedEvent(
            UUID testId,
            String topic,
            String correlationId,
            Class<T> valueClass) {

        ClassTag<T> classTag = ClassTag$.MODULE$.apply(valueClass);
        Future<ConsumedResult> scalaFuture =
            scalaDsl.fetchConsumedEvent(testId, topic, correlationId, classTag);

        return toCompletableFuture(scalaFuture, actorSystem.executionContext());
    }

    /**
     * Fetch consumed event from Kafka topic (blocking).
     * Blocks current thread until fetch completes (5 second timeout).
     *
     * @param testId UUID of the test
     * @param topic Kafka topic name
     * @param correlationId CloudEvent correlation ID to match
     * @param valueClass Class of value type for deserialization
     * @param <T> Value type
     * @return ConsumedResult
     * @throws io.distia.probe.core.pubsub.models.DslNotInitializedException if system not registered
     * @throws io.distia.probe.core.pubsub.models.ActorNotRegisteredException if no consumer for test/topic
     */
    public static <T> ConsumedResult fetchConsumedEventBlocking(
            UUID testId,
            String topic,
            String correlationId,
            Class<T> valueClass) {

        ClassTag<T> classTag = ClassTag$.MODULE$.apply(valueClass);
        return scalaDsl.fetchConsumedEventBlocking(testId, topic, correlationId, classTag);
    }

    private static <T> CompletionStage<T> toCompletableFuture(Future<T> scalaFuture, ExecutionContext ec) {
        CompletableFuture<T> javaFuture = new CompletableFuture<>();
        scalaFuture.onComplete(new AbstractFunction1<Try<T>, BoxedUnit>() {
            @Override
            public BoxedUnit apply(Try<T> result) {
                if (result.isSuccess()) {
                    javaFuture.complete(result.get());
                } else {
                    javaFuture.completeExceptionally(result.failed().get());
                }
                return BoxedUnit.UNIT;
            }
        }, ec);
        return javaFuture;
    }
}
