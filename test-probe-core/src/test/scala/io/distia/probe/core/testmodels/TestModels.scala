package io.distia.probe
package core
package testmodels

import java.util.UUID
import com.fasterxml.jackson.annotation.JsonTypeName
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle


/**
 * Test event payload case class (used for JSON schema serialization).
 * Simple structure for component tests.
 *
 * Corresponds to JSON schema:
 * {
 *   "type": "object",
 *   "properties": {
 *     "orderId": {"type": "string"},
 *     "amount": {"type": "number"},
 *     "currency": {"type": "string"}
 *   },
 *   "required": ["orderId", "amount"]
 * }
 */
@JsonSchemaTitle("TestEventPayload")
@JsonTypeName("TestEventPayload")
case class TestEventPayload(
  orderId: String = UUID.randomUUID().toString,
  amount: Double,
  currency: String = "USD"
)

/**
 * Order created event for polymorphic event testing.
 * Used to test oneOf JSON Schema support with multiple event types on single topic.
 */
@JsonSchemaTitle("OrderCreated")
@JsonTypeName("OrderCreated")
case class OrderCreated(
  eventType: String = "OrderCreated",
  orderId: String = UUID.randomUUID().toString,
  customerId: String,
  amount: Double,
  currency: String = "USD",
  timestamp: Long = System.currentTimeMillis()
)

/**
 * Payment processed event for polymorphic event testing.
 * Used to test oneOf JSON Schema support with multiple event types on single topic.
 */
@JsonSchemaTitle("PaymentProcessed")
@JsonTypeName("PaymentProcessed")
case class PaymentProcessed(
  eventType: String = "PaymentProcessed",
  paymentId: String = UUID.randomUUID().toString,
  orderId: String,
  amount: Double,
  currency: String = "USD",
  paymentMethod: String = "credit_card",
  timestamp: Long = System.currentTimeMillis()
)