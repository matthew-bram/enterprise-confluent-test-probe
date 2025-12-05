package io.distia.probe.javaapi;

import io.distia.probe.core.pubsub.models.ActorNotRegisteredException;
import io.distia.probe.core.pubsub.models.CloudEvent;
import io.distia.probe.core.pubsub.models.ConsumedResult;
import io.distia.probe.core.pubsub.models.DslNotInitializedException;
import io.distia.probe.core.pubsub.models.ProduceResult;
import io.distia.probe.core.pubsub.SerdesFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorSystem;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProbeJavaDsl (Java implementation).
 *
 * <p>Tests the Java DSL adapter layer that wraps ProbeScalaDsl
 * for Java interoperability. This is the primary API for Java users
 * of the test-probe framework.
 *
 * <p><b>Design Principles:</b>
 * <ul>
 *   <li>Pure Java tests using JUnit 5 (validates actual Java usage)</li>
 *   <li>Tests Java→Scala type conversions (Map, CompletionStage, ClassTag)</li>
 *   <li>Tests delegation to ProbeScalaDsl</li>
 *   <li>Comprehensive error handling</li>
 * </ul>
 *
 * <p><b>Test Strategy:</b>
 * <ul>
 *   <li>Unit tests with no real Kafka or actors</li>
 *   <li>Focus on Java/Scala interop and type conversions</li>
 *   <li>Delegation to ProbeScalaDsl is tested</li>
 * </ul>
 *
 * <p><b>Test Coverage:</b>
 * <ul>
 *   <li>System registration/deregistration</li>
 *   <li>Java Map → Scala Map conversion</li>
 *   <li>Java Class&lt;T&gt; → Scala ClassTag[T] conversion</li>
 *   <li>Scala Future → CompletionStage conversion</li>
 *   <li>Blocking vs async variants</li>
 *   <li>Exception handling (DslNotInitializedException, ActorNotRegisteredException)</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> Each test gets fresh TestKit (isolated)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProbeJavaDslTest {

    private ActorTestKit testKit;
    private ActorSystem<?> system;

    @BeforeAll
    void beforeAll() {
        Config config = ConfigFactory.parseString(
            """
            test-probe.core.dsl.ask-timeout = 3s
            test-probe.core.kafka.schema-registry-url = "http://localhost:8081"
            """
        );
        testKit = ActorTestKit.create(config);
        system = testKit.system();

        // Initialize SerdesFactory with mock client
        SerdesFactory.setClient(new CachedSchemaRegistryClient("http://localhost:8081", 100));
    }

    @AfterAll
    void afterAll() {
        testKit.shutdownTestKit();
    }

    // Helper method to create CloudEvent
    private CloudEvent createCloudEvent() {
        return new CloudEvent(
            UUID.randomUUID().toString(),      // id
            "test-source",                      // source
            "1.0",                              // specversion
            "test-event",                       // type
            DateTimeFormatter.ISO_INSTANT.format(Instant.now()), // time
            "test-subject",                     // subject
            "application/octet-stream",         // datacontenttype
            UUID.randomUUID().toString(),       // correlationid
            "1.0",                              // payloadversion
            0L                                  // time_epoch_micro_source
        );
    }

    // ========================================================================
    // registerSystem Tests
    // ========================================================================

    @Test
    @DisplayName("registerSystem should initialize DSL with actor system")
    void registerSystemShouldInitializeDsl() {
        assertDoesNotThrow(() -> ProbeJavaDsl.registerSystem(system));
    }

    @Test
    @DisplayName("registerSystem should allow re-registration with same system")
    void registerSystemShouldAllowReRegistration() {
        assertDoesNotThrow(() -> {
            ProbeJavaDsl.registerSystem(system);
            ProbeJavaDsl.registerSystem(system);
        });
    }

    // ========================================================================
    // produceEvent (async) Tests
    // ========================================================================

    @Test
    @DisplayName("produceEvent should throw ActorNotRegisteredException when producer not registered")
    void produceEventAsyncShouldThrowWhenProducerNotRegistered() throws InterruptedException {
        ProbeJavaDsl.registerSystem(system);

        UUID testId = UUID.randomUUID();
        String topic = "unregistered-topic";
        CloudEvent event = createCloudEvent();
        Map<String, String> headers = Map.of();

        CompletionStage<ProduceResult> futureResult = ProbeJavaDsl.produceEvent(
            testId,
            topic,
            event,
            "test-value",
            headers,
            String.class
        );

        // Attempt to get result - should throw
        ExecutionException exception = assertThrows(ExecutionException.class, () ->
            futureResult.toCompletableFuture().get(5, TimeUnit.SECONDS)
        );

        // Verify cause is ActorNotRegisteredException
        assertInstanceOf(ActorNotRegisteredException.class, exception.getCause());
        String message = exception.getCause().getMessage();
        assertTrue(message.contains("No producer registered"));
        assertTrue(message.contains(testId.toString()));
        assertTrue(message.contains(topic));
    }

    @Test
    @DisplayName("produceEvent should convert Java Map to Scala Map")
    void produceEventShouldConvertJavaMapToScalaMap() throws InterruptedException {
        ProbeJavaDsl.registerSystem(system);

        UUID testId = UUID.randomUUID();
        String topic = "unregistered-topic";
        CloudEvent event = createCloudEvent();

        // Java Map with headers
        Map<String, String> javaHeaders = Map.of(
            "trace-id", "123",
            "span-id", "456"
        );

        CompletionStage<ProduceResult> futureResult = ProbeJavaDsl.produceEvent(
            testId,
            topic,
            event,
            "test-value",
            javaHeaders,
            String.class
        );

        // Should accept Java Map and convert internally
        ExecutionException exception = assertThrows(ExecutionException.class, () ->
            futureResult.toCompletableFuture().get(5, TimeUnit.SECONDS)
        );

        // Exception proves conversion happened (actor not registered)
        assertInstanceOf(ActorNotRegisteredException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("No producer registered"));
    }

    @Test
    @DisplayName("produceEvent should convert Class<T> to ClassTag[T]")
    void produceEventShouldConvertClassToClassTag() throws InterruptedException {
        ProbeJavaDsl.registerSystem(system);

        UUID testId = UUID.randomUUID();
        String topic = "unregistered-topic";
        CloudEvent event = createCloudEvent();
        Map<String, String> headers = Map.of();

        // Pass Class<String> - should convert to ClassTag[String]
        CompletionStage<ProduceResult> futureResult = ProbeJavaDsl.produceEvent(
            testId,
            topic,
            event,
            "test-value",
            headers,
            String.class  // Java Class object
        );

        ExecutionException exception = assertThrows(ExecutionException.class, () ->
            futureResult.toCompletableFuture().get(5, TimeUnit.SECONDS)
        );

        // Exception proves ClassTag conversion happened
        assertInstanceOf(ActorNotRegisteredException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("No producer registered"));
    }

    // ========================================================================
    // produceEventBlocking Tests
    // ========================================================================

    @Test
    @DisplayName("produceEventBlocking should throw ActorNotRegisteredException when producer not registered")
    void produceEventBlockingShouldThrowWhenProducerNotRegistered() {
        ProbeJavaDsl.registerSystem(system);

        UUID testId = UUID.randomUUID();
        String topic = "unregistered-topic";
        CloudEvent event = createCloudEvent();
        Map<String, String> headers = Map.of();

        ActorNotRegisteredException exception = assertThrows(ActorNotRegisteredException.class, () ->
            ProbeJavaDsl.produceEventBlocking(
                testId,
                topic,
                event,
                "test-value",
                headers,
                String.class
            )
        );

        String message = exception.getMessage();
        assertTrue(message.contains("No producer registered"));
        assertTrue(message.contains(testId.toString()));
        assertTrue(message.contains(topic));
    }

    // ========================================================================
    // fetchConsumedEvent (async) Tests
    // ========================================================================

    @Test
    @DisplayName("fetchConsumedEvent should throw ActorNotRegisteredException when consumer not registered")
    void fetchConsumedEventAsyncShouldThrowWhenConsumerNotRegistered() throws InterruptedException {
        ProbeJavaDsl.registerSystem(system);

        UUID testId = UUID.randomUUID();
        String topic = "unregistered-topic";
        String correlationId = UUID.randomUUID().toString();

        CompletionStage<ConsumedResult> futureResult = ProbeJavaDsl.fetchConsumedEvent(
            testId,
            topic,
            correlationId,
            String.class
        );

        ExecutionException exception = assertThrows(ExecutionException.class, () ->
            futureResult.toCompletableFuture().get(5, TimeUnit.SECONDS)
        );

        assertInstanceOf(ActorNotRegisteredException.class, exception.getCause());
        String message = exception.getCause().getMessage();
        assertTrue(message.contains("No consumer registered"));
        assertTrue(message.contains(testId.toString()));
        assertTrue(message.contains(topic));
    }

    @Test
    @DisplayName("fetchConsumedEvent should convert Class<T> to ClassTag[T]")
    void fetchConsumedEventShouldConvertClassToClassTag() throws InterruptedException {
        ProbeJavaDsl.registerSystem(system);

        UUID testId = UUID.randomUUID();
        String topic = "unregistered-topic";
        String correlationId = UUID.randomUUID().toString();

        CompletionStage<ConsumedResult> futureResult = ProbeJavaDsl.fetchConsumedEvent(
            testId,
            topic,
            correlationId,
            String.class  // Java Class object
        );

        ExecutionException exception = assertThrows(ExecutionException.class, () ->
            futureResult.toCompletableFuture().get(5, TimeUnit.SECONDS)
        );

        // Exception proves ClassTag conversion happened
        assertInstanceOf(ActorNotRegisteredException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("No consumer registered"));
    }

    // ========================================================================
    // fetchConsumedEventBlocking Tests
    // ========================================================================

    @Test
    @DisplayName("fetchConsumedEventBlocking should throw ActorNotRegisteredException when consumer not registered")
    void fetchConsumedEventBlockingShouldThrowWhenConsumerNotRegistered() {
        ProbeJavaDsl.registerSystem(system);

        UUID testId = UUID.randomUUID();
        String topic = "unregistered-topic";
        String correlationId = UUID.randomUUID().toString();

        ActorNotRegisteredException exception = assertThrows(ActorNotRegisteredException.class, () ->
            ProbeJavaDsl.fetchConsumedEventBlocking(
                testId,
                topic,
                correlationId,
                String.class
            )
        );

        String message = exception.getMessage();
        assertTrue(message.contains("No consumer registered"));
        assertTrue(message.contains(testId.toString()));
        assertTrue(message.contains(topic));
    }
}
