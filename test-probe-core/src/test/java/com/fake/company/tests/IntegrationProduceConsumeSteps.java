package com.fake.company.tests;

import io.distia.probe.core.pubsub.models.CloudEvent;
import io.distia.probe.core.pubsub.models.ConsumedResult;
import io.distia.probe.core.pubsub.models.ConsumedSuccess;
import io.distia.probe.core.pubsub.models.ConsumerNotAvailableException;
import io.distia.probe.core.pubsub.models.ProduceResult;
import io.distia.probe.core.pubsub.models.ProducingSuccess;
import io.distia.probe.core.services.cucumber.CucumberContext;
import io.distia.probe.core.testutil.CloudEventFactory;
import io.distia.probe.core.testutil.IntegrationTestDsl;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Inner test step definitions for produce/consume integration tests.
 *
 * IMPORTANT - Duplicate Step Resolution:
 * These step definitions use "inner test" vocabulary to avoid conflicts with component test steps.
 * Component tests (PolymorphicEventSteps) use steps like "a CloudEvent key is created..."
 * Integration tests use "the inner test creates a CloudEvent key..." to maintain uniqueness.
 *
 * Why the prefix?
 * When CucumberExecutor runs the inner test, it loads both framework glue packages
 * (io.distia.probe.core.glue) and user glue packages (com.fake.company.tests).
 * Without unique step wording, Cucumber throws DuplicateStepDefinitionException.
 *
 * These steps run INSIDE the TestExecutionActor when Cucumber executes the inner test.
 * They use the ProbeScalaDsl (via IntegrationTestDsl wrapper) to produce and consume events.
 *
 * Key Architecture:
 * - CloudEvent is the Kafka message KEY (contains correlationId for lookup)
 * - Payload is the Kafka message VALUE (TestEvent for JSON, OrderEvent for Avro)
 * - eventTestId is the human-readable test identifier (from feature file)
 * - correlationId is the UUID derived from eventTestId (for DSL fetch)
 *
 * Supported Event Types:
 * - TestEvent (JSON) - Simple JSON POJO for JSON schema validation
 * - OrderEvent (Avro) - SpecificRecord implementation for Avro binary validation
 *
 * Follows Test Architecture Contract:
 * - Uses CloudEventFactory (fixture) - never hardcodes CloudEvent construction
 * - Uses IntegrationTestDsl (abstraction) - never calls ProbeScalaDsl directly
 */
public class IntegrationProduceConsumeSteps {

    // Test context
    private UUID testId;
    private CloudEvent cloudEventKey;
    private Object currentPayload;  // Generic - can be TestEvent or OrderEvent
    private String currentEventType;  // Track event type for type-specific handling
    private String currentTopic;
    private String currentEventTestId;
    private Map<String, String> eventCorrelationIds = new HashMap<>();

    // Consumed event for verification
    private ConsumedSuccess consumedResult;

    // ==========================================================================
    // Background Steps
    // ==========================================================================

    @Given("schemas are registered in Schema Registry")
    public void schemasAreRegisteredInSchemaRegistry() {
        // Schema registration happens in the OUTER test (IntegrationTestSteps)
        // The inner test just verifies schemas are available
        System.out.println("[IntegrationProduceConsumeSteps] Schemas should already be registered by outer test");
    }

    @Given("I have a test ID from the Cucumber context")
    public void iHaveATestIdFromTheCucumberContext() {
        this.testId = CucumberContext.getTestId();
        assertNotNull("Test ID should be available from CucumberContext", testId);
        System.out.println("[IntegrationProduceConsumeSteps] Test ID from context: " + testId);
    }

    // ==========================================================================
    // CloudEvent Key Creation (using CloudEventFactory fixture)
    // ==========================================================================

    @Given("the inner test creates a CloudEvent key with eventTestId {string}")
    public void theInnerTestCreatesACloudEventKeyWithEventTestId(String eventTestId) {
        this.currentEventTestId = eventTestId;

        // Get correlationId from factory (deterministic UUID from eventTestId)
        String correlationId = CloudEventFactory.getCorrelationIdForEventTestId(eventTestId);
        eventCorrelationIds.put(eventTestId, correlationId);

        // Create CloudEvent using fixture factory (default type/version, will be updated)
        this.cloudEventKey = CloudEventFactory.createForEventTestId(
            eventTestId,
            "TestEvent",  // Default, will be updated in payload step
            "v1"          // Default, will be updated in payload step
        );

        System.out.println("[IntegrationProduceConsumeSteps] Inner test created CloudEvent key with eventTestId: " + eventTestId);
        System.out.println("[IntegrationProduceConsumeSteps] correlationId: " + correlationId);
    }

    // ==========================================================================
    // Payload Creation
    // ==========================================================================

    @And("the inner test creates a {word} payload with version {string}")
    public void theInnerTestCreatesAPayloadWithVersion(String eventType, String version) {
        // Update CloudEvent with correct type and version using factory
        String correlationId = eventCorrelationIds.get(currentEventTestId);
        this.cloudEventKey = CloudEventFactory.createWithCorrelationId(
            correlationId,
            eventType,
            version
        );
        this.currentEventType = eventType;

        // Create payload based on event type
        switch (eventType) {
            case "TestEvent":
                // JSON payload
                TestEvent testEvent = new TestEvent(
                    eventType,
                    version,
                    currentEventTestId,
                    System.currentTimeMillis(),
                    "test-key-" + currentEventTestId,
                    "Test payload for " + eventType + " v" + version
                );
                this.currentPayload = testEvent;
                System.out.println("[IntegrationProduceConsumeSteps] Created TestEvent (JSON): " + testEvent);
                break;

            case "UserEvent":
                // JSON payload
                UserEvent userEvent = new UserEvent(
                    eventType,
                    version,
                    currentEventTestId,
                    "user-" + currentEventTestId,
                    "testuser-" + currentEventTestId,
                    "testuser-" + currentEventTestId + "@example.com",
                    System.currentTimeMillis()
                );
                this.currentPayload = userEvent;
                System.out.println("[IntegrationProduceConsumeSteps] Created UserEvent (JSON): " + userEvent);
                break;

            case "ProductEvent":
                // JSON payload
                ProductEvent productEvent = new ProductEvent(
                    eventType,
                    version,
                    currentEventTestId,
                    "prod-" + currentEventTestId,
                    "Test Product " + currentEventTestId,
                    99.99,
                    "Electronics",
                    System.currentTimeMillis()
                );
                this.currentPayload = productEvent;
                System.out.println("[IntegrationProduceConsumeSteps] Created ProductEvent (JSON): " + productEvent);
                break;

            case "OrderEvent":
                // Avro payload (SpecificRecord)
                OrderEvent orderEvent = OrderEvent.newBuilder()
                    .setEventType(eventType)
                    .setOrderId("order-" + currentEventTestId)
                    .setCustomerId("customer-" + currentEventTestId)
                    .setAmount(99.99)
                    .setCurrency("USD")
                    .setTimestamp(System.currentTimeMillis())
                    .build();
                this.currentPayload = orderEvent;
                System.out.println("[IntegrationProduceConsumeSteps] Created OrderEvent (Avro): " + orderEvent);
                break;

            case "InventoryEvent":
                // Avro payload (SpecificRecord)
                InventoryEvent inventoryEvent = InventoryEvent.newBuilder()
                    .setEventType(eventType)
                    .setEventVersion(version)
                    .setEventTestId(currentEventTestId)
                    .setInventoryId("inv-" + currentEventTestId)
                    .setProductId("prod-" + currentEventTestId)
                    .setQuantity(100)
                    .setWarehouse("warehouse-" + currentEventTestId)
                    .setTimestamp(System.currentTimeMillis())
                    .build();
                this.currentPayload = inventoryEvent;
                System.out.println("[IntegrationProduceConsumeSteps] Created InventoryEvent (Avro): " + inventoryEvent);
                break;

            case "NotificationEvent":
                // JSON payload
                NotificationEvent notificationEvent = new NotificationEvent(
                    eventType,
                    version,
                    currentEventTestId,
                    "notif-" + currentEventTestId,
                    "user-" + currentEventTestId,
                    "Test notification for " + eventType + " v" + version,
                    "email",
                    System.currentTimeMillis()
                );
                this.currentPayload = notificationEvent;
                System.out.println("[IntegrationProduceConsumeSteps] Created NotificationEvent (JSON): " + notificationEvent);
                break;

            case "PaymentEvent":
                // Protobuf payload (DynamicMessage wrapper)
                PaymentEvent paymentEvent = PaymentEvent.newBuilder()
                    .setEventType(eventType)
                    .setPaymentId("pay-" + currentEventTestId)
                    .setOrderId("order-" + currentEventTestId)
                    .setAmount(149.99)
                    .setCurrency("USD")
                    .setPaymentMethod("CREDIT_CARD")
                    .setTimestamp(System.currentTimeMillis())
                    .build();
                this.currentPayload = paymentEvent;
                System.out.println("[IntegrationProduceConsumeSteps] Created PaymentEvent (Protobuf): " + paymentEvent);
                break;

            case "ShipmentEvent":
                // Avro payload (SpecificRecord)
                ShipmentEvent shipmentEvent = ShipmentEvent.newBuilder()
                    .setEventType(eventType)
                    .setEventVersion(version)
                    .setEventTestId(currentEventTestId)
                    .setShipmentId("ship-" + currentEventTestId)
                    .setOrderId("order-" + currentEventTestId)
                    .setCarrier("UPS")
                    .setTrackingNumber("1Z999AA1" + currentEventTestId.hashCode())
                    .setStatus("shipped")
                    .setTimestamp(System.currentTimeMillis())
                    .build();
                this.currentPayload = shipmentEvent;
                System.out.println("[IntegrationProduceConsumeSteps] Created ShipmentEvent (Avro): " + shipmentEvent);
                break;

            case "MetricsEvent":
                // Protobuf payload (DynamicMessage wrapper)
                MetricsEvent metricsEvent = MetricsEvent.newBuilder()
                    .setEventType(eventType)
                    .setEventVersion(version)
                    .setEventTestId(currentEventTestId)
                    .setMetricId("metric-" + currentEventTestId)
                    .setMetricName("response_time")
                    .setMetricValue(42.5)
                    .setUnit("ms")
                    .setTimestamp(System.currentTimeMillis())
                    .build();
                this.currentPayload = metricsEvent;
                System.out.println("[IntegrationProduceConsumeSteps] Created MetricsEvent (Protobuf): " + metricsEvent);
                break;

            default:
                throw new IllegalArgumentException("Unknown event type: " + eventType +
                    ". Supported types: TestEvent, UserEvent, ProductEvent (JSON), OrderEvent, ShipmentEvent (Avro), PaymentEvent, MetricsEvent (Protobuf)");
        }

        System.out.println("[IntegrationProduceConsumeSteps] Inner test created " + eventType + " payload v" + version);
    }

    // ==========================================================================
    // Produce Event
    // ==========================================================================

    @When("the inner test produces the event to topic {string}")
    public void theInnerTestProducesTheEventToTopic(String topic) {
        this.currentTopic = topic;

        System.out.println("[IntegrationProduceConsumeSteps] Inner test producing to topic: " + topic);
        System.out.println("[IntegrationProduceConsumeSteps] Key (CloudEvent): correlationId=" + cloudEventKey.correlationid());
        System.out.println("[IntegrationProduceConsumeSteps] Value (" + currentEventType + "): " + currentPayload);

        // Produce event using IntegrationTestDsl (abstraction over ProbeScalaDsl)
        System.out.println("[IntegrationProduceConsumeSteps] DEBUG: About to call produceEventBlocking with testId=" + testId + ", topic=" + topic + ", eventType=" + currentEventType);

        ProduceResult result = null;
        try {
            // Must call type-specific produce to satisfy Java generics
            result = produceEventByType(topic);
            System.out.println("[IntegrationProduceConsumeSteps] DEBUG: produceEventBlocking returned: " + result);
            System.out.println("[IntegrationProduceConsumeSteps] DEBUG: result class: " + (result != null ? result.getClass().getName() : "null"));
        } catch (Exception e) {
            System.err.println("[IntegrationProduceConsumeSteps] ERROR: Exception during produce: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // Verify production succeeded
        assertTrue("Event production should succeed, got: " + result,
            result instanceof ProducingSuccess);

        System.out.println("[IntegrationProduceConsumeSteps] ✅ Inner test produced " + currentEventType + " successfully!");
    }

    /**
     * Produce event with proper type safety.
     * Java generics require explicit type handling - we can't use Object + Class<?>.
     *
     * For Protobuf (PaymentEvent), we convert the wrapper to DynamicMessage
     * since SerdesFactory expects Message types for Protobuf serialization.
     */
    private ProduceResult produceEventByType(String topic) {
        switch (currentEventType) {
            case "TestEvent":
                return IntegrationTestDsl.produceEventBlocking(
                    testId, topic, cloudEventKey, (TestEvent) currentPayload, TestEvent.class
                );
            case "UserEvent":
                return IntegrationTestDsl.produceEventBlocking(
                    testId, topic, cloudEventKey, (UserEvent) currentPayload, UserEvent.class
                );
            case "ProductEvent":
                return IntegrationTestDsl.produceEventBlocking(
                    testId, topic, cloudEventKey, (ProductEvent) currentPayload, ProductEvent.class
                );
            case "OrderEvent":
                System.out.println("[HOOK-PRODUCE-AVRO] testId=" + testId + ", topic=" + topic + ", correlationId=" + cloudEventKey.correlationid());
                return IntegrationTestDsl.produceEventBlocking(
                    testId, topic, cloudEventKey, (OrderEvent) currentPayload, OrderEvent.class
                );
            case "InventoryEvent":
                System.out.println("[HOOK-PRODUCE-AVRO] testId=" + testId + ", topic=" + topic + ", correlationId=" + cloudEventKey.correlationid());
                return IntegrationTestDsl.produceEventBlocking(
                    testId, topic, cloudEventKey, (InventoryEvent) currentPayload, InventoryEvent.class
                );
            case "NotificationEvent":
                return IntegrationTestDsl.produceEventBlocking(
                    testId, topic, cloudEventKey, (NotificationEvent) currentPayload, NotificationEvent.class
                );
            case "PaymentEvent":
                System.out.println("[HOOK-PRODUCE-PROTO] testId=" + testId + ", topic=" + topic + ", correlationId=" + cloudEventKey.correlationid());
                // Convert PaymentEvent wrapper to DynamicMessage for Kafka serialization
                PaymentEvent payment = (PaymentEvent) currentPayload;
                DynamicMessage dynamicMessage = payment.toDynamicMessage(PaymentEvent.SCHEMA);
                return IntegrationTestDsl.produceEventBlocking(
                    testId, topic, cloudEventKey, dynamicMessage, DynamicMessage.class
                );
            case "ShipmentEvent":
                System.out.println("[HOOK-PRODUCE-AVRO] testId=" + testId + ", topic=" + topic + ", correlationId=" + cloudEventKey.correlationid());
                return IntegrationTestDsl.produceEventBlocking(
                    testId, topic, cloudEventKey, (ShipmentEvent) currentPayload, ShipmentEvent.class
                );
            case "MetricsEvent":
                System.out.println("[HOOK-PRODUCE-PROTO] testId=" + testId + ", topic=" + topic + ", correlationId=" + cloudEventKey.correlationid());
                // Convert MetricsEvent wrapper to DynamicMessage for Kafka serialization
                MetricsEvent metrics = (MetricsEvent) currentPayload;
                DynamicMessage metricsDynamicMessage = metrics.toDynamicMessage(MetricsEvent.SCHEMA);
                return IntegrationTestDsl.produceEventBlocking(
                    testId, topic, cloudEventKey, metricsDynamicMessage, DynamicMessage.class
                );
            default:
                throw new IllegalArgumentException("Unknown event type for produce: " + currentEventType);
        }
    }

    // ==========================================================================
    // Consume Event
    // ==========================================================================

    @Then("the inner test fetches the consumed event using eventTestId {string}")
    public void theInnerTestFetchesTheConsumedEventUsingEventTestId(String eventTestId) {
        System.out.println("[IntegrationProduceConsumeSteps] Inner test fetching consumed " + currentEventType + " for eventTestId: " + eventTestId);

        // Get correlationId from mapping
        String correlationId = eventCorrelationIds.get(eventTestId);
        assertNotNull("correlationId should be mapped for eventTestId: " + eventTestId, correlationId);

        // Retry loop for eventual consistency
        // Avro topics may need more time for consumer to connect and receive messages
        int maxAttempts = 10;
        int attemptDelay = 1000;
        ConsumedResult result = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            System.out.println("[IntegrationProduceConsumeSteps] Fetch attempt " + attempt + "/" + maxAttempts + " for " + currentEventType);

            try {
                // Must call type-specific fetch to satisfy Java generics
                result = fetchConsumedEventByType(correlationId);

                // If we get here, consumption succeeded
                System.out.println("[IntegrationProduceConsumeSteps] ✅ " + currentEventType + " fetched on attempt " + attempt);
                break;

            } catch (ConsumerNotAvailableException e) {
                // Debug: Print the actual cause for troubleshooting
                System.out.println("[DEBUG] ConsumerNotAvailableException: " + e.getMessage());
                if (e.getCause() != null) {
                    System.out.println("[DEBUG] Cause: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
                    e.getCause().printStackTrace(System.out);
                }
                // Event not ready yet
                if (attempt < maxAttempts) {
                    System.out.println("[IntegrationProduceConsumeSteps] Event not ready, waiting " + attemptDelay + "ms...");
                    try {
                        Thread.sleep(attemptDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        fail("Interrupted while waiting for event");
                    }
                } else {
                    // Max attempts exceeded - fail with the original exception
                    fail("Event consumption failed after " + maxAttempts + " attempts: " + e.getMessage());
                }
            }
        }

        // Verify consumption succeeded
        assertNotNull("Event consumption should succeed after " + maxAttempts + " attempts", result);
        assertTrue("Expected ConsumedSuccess but got: " + result, result instanceof ConsumedSuccess);

        this.consumedResult = (ConsumedSuccess) result;
        System.out.println("[IntegrationProduceConsumeSteps] ✅ Inner test consumed " + currentEventType + " verified!");
    }

    /**
     * Fetch consumed event with proper type safety.
     * Java generics require explicit type handling - we can't use Class<?>.
     *
     * For Protobuf (PaymentEvent), we fetch as DynamicMessage and convert
     * to PaymentEvent wrapper for verification.
     */
    private ConsumedResult fetchConsumedEventByType(String correlationId) {
        switch (currentEventType) {
            case "TestEvent":
                return IntegrationTestDsl.fetchConsumedEventBlocking(
                    testId, currentTopic, correlationId, TestEvent.class
                );
            case "UserEvent":
                return IntegrationTestDsl.fetchConsumedEventBlocking(
                    testId, currentTopic, correlationId, UserEvent.class
                );
            case "ProductEvent":
                return IntegrationTestDsl.fetchConsumedEventBlocking(
                    testId, currentTopic, correlationId, ProductEvent.class
                );
            case "OrderEvent":
                System.out.println("[HOOK-CONSUME-AVRO] testId=" + testId + ", topic=" + currentTopic + ", correlationId=" + correlationId);
                return IntegrationTestDsl.fetchConsumedEventBlocking(
                    testId, currentTopic, correlationId, OrderEvent.class
                );
            case "InventoryEvent":
                System.out.println("[HOOK-CONSUME-AVRO] testId=" + testId + ", topic=" + currentTopic + ", correlationId=" + correlationId);
                return IntegrationTestDsl.fetchConsumedEventBlocking(
                    testId, currentTopic, correlationId, InventoryEvent.class
                );
            case "NotificationEvent":
                return IntegrationTestDsl.fetchConsumedEventBlocking(
                    testId, currentTopic, correlationId, NotificationEvent.class
                );
            case "PaymentEvent":
                System.out.println("[HOOK-CONSUME-PROTO] testId=" + testId + ", topic=" + currentTopic + ", correlationId=" + correlationId);
                // Fetch as DynamicMessage (what SerdesFactory returns for Protobuf)
                return IntegrationTestDsl.fetchConsumedEventBlocking(
                    testId, currentTopic, correlationId, DynamicMessage.class
                );
            case "ShipmentEvent":
                System.out.println("[HOOK-CONSUME-AVRO] testId=" + testId + ", topic=" + currentTopic + ", correlationId=" + correlationId);
                return IntegrationTestDsl.fetchConsumedEventBlocking(
                    testId, currentTopic, correlationId, ShipmentEvent.class
                );
            case "MetricsEvent":
                System.out.println("[HOOK-CONSUME-PROTO] testId=" + testId + ", topic=" + currentTopic + ", correlationId=" + correlationId);
                // Fetch as DynamicMessage (what SerdesFactory returns for Protobuf)
                return IntegrationTestDsl.fetchConsumedEventBlocking(
                    testId, currentTopic, correlationId, DynamicMessage.class
                );
            default:
                throw new IllegalArgumentException("Unknown event type for fetch: " + currentEventType);
        }
    }

    // ==========================================================================
    // Payload Verification
    // ==========================================================================

    @And("the inner test verifies the consumed payload matches")
    public void theInnerTestVerifiesTheConsumedPayloadMatches() {
        System.out.println("[IntegrationProduceConsumeSteps] Inner test verifying consumed " + currentEventType + " matches produced event");

        assertNotNull("Consumed result should not be null", consumedResult);

        // Get key from consumed result
        CloudEvent consumedKey = (CloudEvent) consumedResult.key();

        // Verify key fields (common for all event types)
        assertEquals("Event type should match",
            cloudEventKey.type(), consumedKey.type());
        assertEquals("Payload version should match",
            cloudEventKey.payloadversion(), consumedKey.payloadversion());
        assertEquals("Correlation ID should match",
            cloudEventKey.correlationid(), consumedKey.correlationid());

        // Verify value fields based on event type
        switch (currentEventType) {
            case "TestEvent":
                verifyTestEventPayload();
                break;

            case "UserEvent":
                verifyUserEventPayload();
                break;

            case "ProductEvent":
                verifyProductEventPayload();
                break;

            case "OrderEvent":
                verifyOrderEventPayload();
                break;

            case "InventoryEvent":
                verifyInventoryEventPayload();
                break;

            case "NotificationEvent":
                verifyNotificationEventPayload();
                break;

            case "PaymentEvent":
                verifyPaymentEventPayload();
                break;

            case "ShipmentEvent":
                verifyShipmentEventPayload();
                break;

            case "MetricsEvent":
                verifyMetricsEventPayload();
                break;

            default:
                fail("Unknown event type for verification: " + currentEventType);
        }

        System.out.println("[IntegrationProduceConsumeSteps] ✅ Inner test verified consumed " + currentEventType + " matches!");
    }

    /**
     * Verify TestEvent (JSON) payload fields match.
     */
    private void verifyTestEventPayload() {
        TestEvent produced = (TestEvent) currentPayload;
        TestEvent consumed = (TestEvent) consumedResult.value();

        assertEquals("EventType should match",
            produced.getEventType(), consumed.getEventType());
        assertEquals("EventVersion should match",
            produced.getEventVersion(), consumed.getEventVersion());
        assertEquals("EventTestId should match",
            produced.getEventTestId(), consumed.getEventTestId());
        assertEquals("Payload should match",
            produced.getPayload(), consumed.getPayload());

        System.out.println("[IntegrationProduceConsumeSteps] TestEvent fields verified: eventType, eventVersion, eventTestId, payload");
    }

    /**
     * Verify UserEvent (JSON) payload fields match.
     */
    private void verifyUserEventPayload() {
        UserEvent produced = (UserEvent) currentPayload;
        UserEvent consumed = (UserEvent) consumedResult.value();

        assertEquals("EventType should match",
            produced.getEventType(), consumed.getEventType());
        assertEquals("EventVersion should match",
            produced.getEventVersion(), consumed.getEventVersion());
        assertEquals("EventTestId should match",
            produced.getEventTestId(), consumed.getEventTestId());
        assertEquals("UserId should match",
            produced.getUserId(), consumed.getUserId());
        assertEquals("Username should match",
            produced.getUsername(), consumed.getUsername());
        assertEquals("Email should match",
            produced.getEmail(), consumed.getEmail());

        System.out.println("[IntegrationProduceConsumeSteps] UserEvent fields verified: eventType, eventVersion, eventTestId, userId, username, email");
    }

    /**
     * Verify ProductEvent (JSON) payload fields match.
     */
    private void verifyProductEventPayload() {
        ProductEvent produced = (ProductEvent) currentPayload;
        ProductEvent consumed = (ProductEvent) consumedResult.value();

        assertEquals("EventType should match",
            produced.getEventType(), consumed.getEventType());
        assertEquals("EventVersion should match",
            produced.getEventVersion(), consumed.getEventVersion());
        assertEquals("EventTestId should match",
            produced.getEventTestId(), consumed.getEventTestId());
        assertEquals("ProductId should match",
            produced.getProductId(), consumed.getProductId());
        assertEquals("ProductName should match",
            produced.getProductName(), consumed.getProductName());
        assertEquals("Price should match",
            produced.getPrice(), consumed.getPrice(), 0.01);
        assertEquals("Category should match",
            produced.getCategory(), consumed.getCategory());

        System.out.println("[IntegrationProduceConsumeSteps] ProductEvent fields verified: eventType, eventVersion, eventTestId, productId, productName, price, category");
    }

    /**
     * Verify OrderEvent (Avro) payload fields match.
     */
    private void verifyOrderEventPayload() {
        OrderEvent produced = (OrderEvent) currentPayload;
        OrderEvent consumed = (OrderEvent) consumedResult.value();

        assertEquals("EventType should match",
            produced.getEventType(), consumed.getEventType());
        assertEquals("OrderId should match",
            produced.getOrderId(), consumed.getOrderId());
        assertEquals("CustomerId should match",
            produced.getCustomerId(), consumed.getCustomerId());
        assertEquals("Amount should match",
            produced.getAmount(), consumed.getAmount());
        assertEquals("Currency should match",
            produced.getCurrency(), consumed.getCurrency());

        System.out.println("[IntegrationProduceConsumeSteps] OrderEvent fields verified: eventType, orderId, customerId, amount, currency");
    }

    /**
     * Verify InventoryEvent (Avro) payload fields match.
     */
    private void verifyInventoryEventPayload() {
        InventoryEvent produced = (InventoryEvent) currentPayload;
        InventoryEvent consumed = (InventoryEvent) consumedResult.value();

        assertEquals("EventType should match",
            produced.getEventType(), consumed.getEventType());
        assertEquals("EventVersion should match",
            produced.getEventVersion(), consumed.getEventVersion());
        assertEquals("EventTestId should match",
            produced.getEventTestId(), consumed.getEventTestId());
        assertEquals("InventoryId should match",
            produced.getInventoryId(), consumed.getInventoryId());
        assertEquals("ProductId should match",
            produced.getProductId(), consumed.getProductId());
        assertEquals("Quantity should match",
            produced.getQuantity(), consumed.getQuantity());
        assertEquals("Warehouse should match",
            produced.getWarehouse(), consumed.getWarehouse());

        System.out.println("[IntegrationProduceConsumeSteps] InventoryEvent fields verified: eventType, eventVersion, eventTestId, inventoryId, productId, quantity, warehouse");
    }

    /**
     * Verify NotificationEvent (JSON) payload fields match.
     */
    private void verifyNotificationEventPayload() {
        NotificationEvent produced = (NotificationEvent) currentPayload;
        NotificationEvent consumed = (NotificationEvent) consumedResult.value();

        assertEquals("EventType should match",
            produced.getEventType(), consumed.getEventType());
        assertEquals("EventVersion should match",
            produced.getEventVersion(), consumed.getEventVersion());
        assertEquals("EventTestId should match",
            produced.getEventTestId(), consumed.getEventTestId());
        assertEquals("NotificationId should match",
            produced.getNotificationId(), consumed.getNotificationId());
        assertEquals("UserId should match",
            produced.getUserId(), consumed.getUserId());
        assertEquals("Message should match",
            produced.getMessage(), consumed.getMessage());
        assertEquals("Channel should match",
            produced.getChannel(), consumed.getChannel());

        System.out.println("[IntegrationProduceConsumeSteps] NotificationEvent fields verified: eventType, eventVersion, eventTestId, notificationId, userId, message, channel");
    }

    /**
     * Verify PaymentEvent (Protobuf) payload fields match.
     *
     * The consumed value is a DynamicMessage, so we convert it back to PaymentEvent
     * using the fromDynamicMessage() factory method.
     */
    private void verifyPaymentEventPayload() {
        PaymentEvent produced = (PaymentEvent) currentPayload;

        // Convert DynamicMessage back to PaymentEvent for comparison
        DynamicMessage consumedMsg = (DynamicMessage) consumedResult.value();
        PaymentEvent consumed = PaymentEvent.fromDynamicMessage(consumedMsg);

        assertEquals("EventType should match",
            produced.getEventType(), consumed.getEventType());
        assertEquals("PaymentId should match",
            produced.getPaymentId(), consumed.getPaymentId());
        assertEquals("OrderId should match",
            produced.getOrderId(), consumed.getOrderId());
        assertEquals("Amount should match",
            produced.getAmount(), consumed.getAmount());
        assertEquals("Currency should match",
            produced.getCurrency(), consumed.getCurrency());
        assertEquals("PaymentMethod should match",
            produced.getPaymentMethod(), consumed.getPaymentMethod());

        System.out.println("[IntegrationProduceConsumeSteps] PaymentEvent fields verified: eventType, paymentId, orderId, amount, currency, paymentMethod");
    }

    /**
     * Verify ShipmentEvent (Avro) payload fields match.
     */
    private void verifyShipmentEventPayload() {
        ShipmentEvent produced = (ShipmentEvent) currentPayload;
        ShipmentEvent consumed = (ShipmentEvent) consumedResult.value();

        assertEquals("EventType should match",
            produced.getEventType(), consumed.getEventType());
        assertEquals("EventVersion should match",
            produced.getEventVersion(), consumed.getEventVersion());
        assertEquals("EventTestId should match",
            produced.getEventTestId(), consumed.getEventTestId());
        assertEquals("ShipmentId should match",
            produced.getShipmentId(), consumed.getShipmentId());
        assertEquals("OrderId should match",
            produced.getOrderId(), consumed.getOrderId());
        assertEquals("Carrier should match",
            produced.getCarrier(), consumed.getCarrier());
        assertEquals("TrackingNumber should match",
            produced.getTrackingNumber(), consumed.getTrackingNumber());
        assertEquals("Status should match",
            produced.getStatus(), consumed.getStatus());

        System.out.println("[IntegrationProduceConsumeSteps] ShipmentEvent fields verified: eventType, eventVersion, eventTestId, shipmentId, orderId, carrier, trackingNumber, status");
    }

    /**
     * Verify MetricsEvent (Protobuf) payload fields match.
     *
     * The consumed value is a DynamicMessage, so we convert it back to MetricsEvent
     * using the fromDynamicMessage() factory method.
     */
    private void verifyMetricsEventPayload() {
        MetricsEvent produced = (MetricsEvent) currentPayload;

        // Convert DynamicMessage back to MetricsEvent for comparison
        DynamicMessage consumedMsg = (DynamicMessage) consumedResult.value();
        MetricsEvent consumed = MetricsEvent.fromDynamicMessage(consumedMsg);

        assertEquals("EventType should match",
            produced.getEventType(), consumed.getEventType());
        assertEquals("EventVersion should match",
            produced.getEventVersion(), consumed.getEventVersion());
        assertEquals("EventTestId should match",
            produced.getEventTestId(), consumed.getEventTestId());
        assertEquals("MetricId should match",
            produced.getMetricId(), consumed.getMetricId());
        assertEquals("MetricName should match",
            produced.getMetricName(), consumed.getMetricName());
        assertEquals("MetricValue should match",
            produced.getMetricValue(), consumed.getMetricValue());
        assertEquals("Unit should match",
            produced.getUnit(), consumed.getUnit());

        System.out.println("[IntegrationProduceConsumeSteps] MetricsEvent fields verified: eventType, eventVersion, eventTestId, metricId, metricName, metricValue, unit");
    }
}
