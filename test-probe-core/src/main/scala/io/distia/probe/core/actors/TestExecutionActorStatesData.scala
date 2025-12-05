package io.distia.probe
package core
package actors

import java.time.Instant
import java.util.UUID

import org.apache.pekko.actor.typed.ActorRef

import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}

import models.{BlockStorageCommands, CucumberExecutionCommands, KafkaConsumerCommands, KafkaProducerCommands, ProbeExceptions, ServiceResponse, TestExecutionResult, VaultCommands}

/**
 * FSM States for TestExecutionActor
 *
 * TestExecutionActor implements a 7-state FSM to manage the complete lifecycle
 * of test execution from initialization through cleanup.
 *
 * State Machine Diagram:
 * {{{
 *                                    ┌─────────────┐
 *                                    │   Setup     │ Initial state
 *                                    └──────┬──────┘
 *                                           │ ExecuteTest
 *                                           ▼
 *                                    ┌─────────────┐
 *                                    │   Loading   │ Load test bundle
 *                                    └──────┬──────┘
 *                                           │ TestLoadSuccessful
 *                                           ▼
 *                                    ┌─────────────┐
 *           ┌────────────────────────┤   Loaded    │ Test ready
 *           │ StartTest              └──────┬──────┘
 *           ▼                               │
 *    ┌─────────────┐                        │
 *    │   Testing   │ Executing test         │
 *    └──────┬──────┘                        │
 *           │ TestComplete/TestFailed       │
 *           ▼                               ▼
 *    ┌─────────────┐                 ┌─────────────┐
 *    │  Completed  │────────────────▶│ ShuttingDown│ Cleanup
 *    └─────────────┘ CleanupTimeout  └──────┬──────┘
 *                                           │
 *                                           ▼
 *                                    ┌─────────────┐
 *                      ┌────────────▶│  Exception  │ Error state
 *                      │ (any error) └─────────────┘
 * }}}
 *
 * State Transitions:
 * - Setup → Loading: ExecuteTest received
 * - Loading → Loaded: TestLoadSuccessful received
 * - Loaded → Testing: StartTest received
 * - Testing → Completed: TestComplete received
 * - Testing → Exception: TestFailed received
 * - Completed → ShuttingDown: CleanupTimeout triggered
 * - Any → Exception: Fatal errors at any stage
 * - Exception → ShuttingDown: CleanupTimeout triggered
 *
 * State Timeouts:
 * - Setup: setupStateTimeout (default: 5 minutes)
 * - Loading: loadingStateTimeout (default: 2 minutes)
 * - Completed: completedStateTimeout (default: 30 seconds)
 * - Exception: exceptionStateTimeout (default: 30 seconds)
 *
 * @see TestExecutionActor for FSM implementation
 * @see working/TestExecutionActorImplementationPlan.md for detailed design
 */
sealed trait TestExecutionState extends Product with Serializable

object TestExecutionState {
  /**
   * Initial state - awaiting test execution command
   */
  case object Setup extends TestExecutionState

  /**
   * Loading test bundle from block storage
   */
  case object Loading extends TestExecutionState

  /**
   * Test loaded and ready for execution
   */
  case object Loaded extends TestExecutionState

  /**
   * Test currently executing
   */
  case object Testing extends TestExecutionState

  /**
   * Test completed successfully
   */
  case object Completed extends TestExecutionState

  /**
   * Test failed with exception
   */
  case object Exception extends TestExecutionState

  /**
   * Shutting down and cleaning up resources
   */
  case object ShuttingDown extends TestExecutionState
}

/**
 * FSM Data for TestExecutionActor
 *
 * Data accumulates through state transitions, starting with Uninitialized
 * and evolving into TestData as child actors are spawned and test execution
 * progresses.
 *
 * Data Evolution:
 * {{{
 *   Uninitialized
 *        ↓ ExecuteTest
 *   TestData(testId, testType, replyTo)
 *        ↓ Child actors spawned
 *   TestData(... + executor, producer, consumer, vault, blockStorage)
 *        ↓ Test loads
 *   TestData(... + blockStorageDirective, securityDirectives)
 *        ↓ Test starts
 *   TestData(... + startTime)
 *        ↓ Test completes
 *   TestData(... + endTime, success, testResult)
 *        ↓ Test fails
 *   TestData(... + endTime, error)
 * }}}
 *
 * @see TestExecutionState for state machine
 */
sealed trait TestExecutionData extends Product with Serializable

object TestExecutionData {

  /**
   * Initial data state before ExecuteTest command
   */
  case object Uninitialized extends TestExecutionData

  /**
   * Test execution data accumulated during FSM transitions
   *
   * This data structure holds all information required to execute a test,
   * manage child actors, track execution progress, and report results.
   *
   * Lifecycle:
   * 1. Created in Setup state with minimal fields (testId, testType, replyTo)
   * 2. Child actor refs populated during Loading state
   * 3. Directives loaded during Loaded state
   * 4. Timing and results captured during Testing/Completed/Exception states
   *
   * @param replyTo Actor reference to send final result (success or failure)
   * @param testId Unique identifier for this test execution
   * @param bucket Optional block storage bucket name (e.g., "s3://my-bucket")
   * @param testType Test type identifier (e.g., "cucumber", "junit")
   * @param executor Optional reference to CucumberExecutionActor for test execution
   * @param producer Optional reference to KafkaProducerStreamingActor for publishing events
   * @param consumer Optional reference to KafkaConsumerStreamingActor for consuming events
   * @param blockStorage Optional reference to BlockStorageActor for file operations
   * @param vault Optional reference to VaultActor for secret management
   * @param blockStorageDirective Optional directives for block storage configuration
   * @param securityDirectives List of Kafka security configurations (credentials, OAuth)
   * @param testResult Accumulated test execution results (scenarios, steps, evidence)
   * @param startTime Optional test start timestamp
   * @param endTime Optional test end timestamp
   * @param success Optional test success flag (None=running, Some(true)=passed, Some(false)=failed)
   * @param error Optional exception if test failed
   */
  case class TestData(
    replyTo: ActorRef[ServiceResponse],
    testId: UUID,
    bucket: Option[String] = None,
    testType: String,
    executor: Option[ActorRef[CucumberExecutionCommands.CucumberExecutionCommand]] = None,
    producer: Option[ActorRef[KafkaProducerCommands.KafkaProducerCommand]] = None,
    consumer: Option[ActorRef[KafkaConsumerCommands.KafkaConsumerCommand]] = None,
    blockStorage: Option[ActorRef[BlockStorageCommands.BlockStorageCommand]] = None,
    vault: Option[ActorRef[VaultCommands.VaultCommand]] = None,
    blockStorageDirective: Option[BlockStorageDirective] = None,
    securityDirectives: List[KafkaSecurityDirective],
    testResult: TestExecutionResult,
    startTime: Option[Instant] = None,
    endTime: Option[Instant] = None,
    success: Option[Boolean] = None,
    error: Option[ProbeExceptions] = None
  ) extends TestExecutionData
}
