package io.distia.probe.core.fixtures

/**
 * Universal test harness utilities and model fixtures.
 *
 * Provides:
 * - Universal test model fixtures (via composition)
 *   - CloudEvent builders
 *   - TopicDirective factories
 *   - KafkaSecurityDirective factories
 *   - BlockStorageDirective factories
 *   - StreamingTestConstants
 * - Validation helpers
 *   - Schema Registry format validation
 *   - CloudEvent comprehensive validation
 *   - Payload structure validation
 *   - Kafka message consumption helpers
 *   - FIFO ordering verification
 * - Observability helpers (stub implementations)
 *   - OpenTelemetry metrics verification (TODO: implement)
 *   - Dispatcher verification (TODO: implement)
 *   - Log capture (TODO: move from archive)
 *
 * Design Philosophy:
 * - Universal fixtures aggregated here via trait composition
 * - Specialized fixtures (ActorTestingFixtures) remain separate
 * - Single source of truth for common test data
 * - Each domain fixture in its own file (no god trait)
 *
 * Usage:
 * {{{
 *   class MySpec extends AnyWordSpec
 *     with TestHarnessFixtures {
 *
 *     "feature" should {
 *       "work with fixtures" in withJimfs { jimfs =>
 *         val event = CloudEvent.aTestEvent().build()
 *         val directive = TopicDirective.createProducerDirective()
 *         val storage = BlockStorageDirective.createBlockStorageDirective()
 *
 *         // Validation helpers now available
 *         validateCloudEvent(event, "correlation-123")
 *         validateSchemaRegistryFormat(serializedBytes)
 *         // ...
 *       }
 *     }
 *   }
 * }}}
 *
 * Architecture:
 * - Specs mix in TestHarnessFixtures (ONE trait)
 * - TestHarnessFixtures composes all universal fixture traits
 * - Changes to domain fixtures stay in their own files
 *
 * Thread Safety: Each FileSystem instance is independent - thread-safe.
 *
 * Test Strategy: Tested via integration (no standalone tests)
 */
trait TestHarnessFixtures
  extends CloudEventFixtures
  with TopicDirectiveFixtures
  with KafkaSecurityDirectiveFixtures
  with BlockStorageDirectiveFixtures
  with StreamingTestConstantsFixtures
  with InterfaceFunctionsFixture
  with JimfsFixtures
  with SerdesFixtures
  with ValidationFixtures
  with OpenTelemetryFixtures
  with DispatcherVerification
