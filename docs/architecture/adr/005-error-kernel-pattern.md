# ADR 005: Error Kernel Pattern for Exception Handling

**Date:** 2025-10-13
**Status:** Accepted
**Deciders:** Engineering Team
**Component:** TestExecutionActor, QueueActor

## Context

The TestExecutionActor orchestrates 5 child actors that interact with external systems (S3, Vault, Kafka, Cucumber). These external interactions are error-prone:

**Common Failure Scenarios:**

1. **Network failures:** S3 timeout, Vault connection refused, Kafka broker down
2. **Authentication failures:** Invalid credentials, expired tokens, missing permissions
3. **Resource failures:** File not found, bucket doesn't exist, out of disk space
4. **Configuration failures:** Invalid test type, malformed feature file, bad Gherkin syntax
5. **System failures:** Out of memory, actor crash, supervisor restart

**Error Handling Challenges:**

1. **Where to catch errors?** Child actors, parent actor, or both?
2. **How to report errors?** Return failure messages, throw exceptions, or supervision?
3. **How to prevent cascading failures?** Child exception shouldn't crash parent
4. **How to make errors testable?** Hard to verify exception handling with traditional try/catch
5. **How to maintain FSM purity?** Exceptions disrupt state machine flow

**Requirements:**

1. **Fault isolation:** Child failures must not crash TestExecutionActor
2. **Clear error boundaries:** Know exactly where errors are handled
3. **Deterministic error handling:** Same exception always causes same behavior
4. **Testability:** Can inject exceptions and verify FSM response
5. **Visibility:** All errors logged and reported to QueueActor

## Decision

We will implement the **Error Kernel Pattern** from Erlang/OTP:

**Core Principle:** "Let it crash" (controlled) - child actors throw exceptions, parent catches via supervision and handles gracefully.

**Architecture:**

```
TestExecutionActor (Error Kernel / Supervisor)
├── Supervision: Catches child exceptions, translates to TrnException
├── FSM: Transitions to Exception state on TrnException
└── Cleanup: Notifies QueueActor, shuts down children
    │
    ├── BlockStorageActor (Worker)
    │   └── Throws: BlockStorageException
    ├── VaultActor (Worker)
    │   └── Throws: VaultException
    ├── CucumberExecutionActor (Worker)
    │   └── Throws: CucumberExecutionException
    ├── KafkaProducerActor (Worker)
    │   └── Throws: KafkaProducerException
    └── KafkaConsumerActor (Worker)
        └── Throws: KafkaConsumerException
```

**Implementation:**

1. **Child actors throw exceptions directly:**
   ```scala
   // BlockStorageActor
   case Initialize(bucket) =>
     try {
       val files = s3Client.fetchFiles(bucket)
       context.parent ! BlockStorageFetched(testId, files)
     } catch {
       case e: AmazonS3Exception =>
         throw BlockStorageException(s"Failed to fetch from S3: ${e.getMessage}", e)
     }
   ```

2. **Child supervision restarts on failure:**
   ```scala
   private def defaultBlockStorageFactory(testId: UUID): BlockStorageFactory = { ctx =>
     ctx.spawn(
       Behaviors.supervise(BlockStorageActor(testId))
         .onFailure[Exception](SupervisorStrategy.restart),
       s"block-storage-$testId"
     )
   }
   ```

3. **Parent supervision catches and translates:**
   ```scala
   Behaviors.supervise {
     // All FSM behaviors
   }.onFailure[Throwable](SupervisorStrategy.resume)
   ```

   When child throws → child restarts → exception propagates → parent catches → parent sends `TrnException` to self → FSM transitions to Exception state

4. **Exception state handles cleanup:**
   ```scala
   private def exceptionBehavior(
     testId: UUID,
     queueActor: ActorRef[QueueCommand],
     timers: TimerScheduler[TestExecutionCommand],
     data: TestData,
     exception: ProbeExceptions,
     serviceConfig: ServiceConfig,
     context: ActorContext[TestExecutionCommand]
   ): Behavior[TestExecutionCommand] = {
     Behaviors.receiveMessage {
       case TrnException(receivedException) =>
         context.log.error(s"Processing TrnException: ${receivedException.getMessage}")
         timers.cancelAll()
         timers.startSingleTimer("poison-pill", TrnPoisonPill, serviceConfig.exceptionStateTimeout)

         val updatedData = data.copy(error = Some(receivedException))
         queueActor ! QueueCommands.TestException(testId, receivedException)

         Behaviors.same
       // ... other handlers
     }
   }
   ```

## Consequences

### Positive

1. **Clear error boundaries:** Errors caught at supervision layer, not scattered through code
2. **Fault isolation:** Child crashes don't crash parent
3. **Error as domain event:** Exceptions become `TrnException` messages (first-class)
4. **Deterministic handling:** Exception always transitions to Exception state
5. **Testability:** Can send `TrnException` directly to test exception handling
6. **FSM purity:** State machine handles exceptions like any other message
7. **Visibility:** All exceptions logged with full context
8. **QueueActor aware:** Parent notified of all child failures
9. **Clean supervision tree:** Follows Akka best practices

### Negative

1. **Child state lost on restart:** Child actors restart with fresh state
   - **Mitigation:** TestExecutionActor transitions to Exception state, no retry
2. **Exception propagation delay:** Exception must bubble up through supervision
   - **Mitigation:** Happens within milliseconds, acceptable latency
3. **Complex supervision setup:** Requires understanding of Akka supervision
   - **Mitigation:** Pattern is well-documented, consistent across actors

### Risks

1. **Child doesn't throw:** Child actor swallows exception and sends success message
   - **Mitigation:** Code review enforces "throw on error" pattern
   - **Mitigation:** Unit tests verify exceptions are thrown

2. **Wrong exception type:** Child throws exception that doesn't match sealed trait
   - **Mitigation:** All domain exceptions extend `ProbeExceptions` sealed trait
   - **Mitigation:** Supervision catches `Throwable` (all exceptions)

3. **Supervision strategy wrong:** Restart instead of resume, or vice versa
   - **Mitigation:** Child: restart (give it another chance), Parent: resume (handle exception)
   - **Mitigation:** BDD tests verify exception handling works

4. **Exception not translated:** Parent catches exception but doesn't send `TrnException`
   - **Mitigation:** `SupervisorStrategy.resume` always translates to `TrnException`
   - **Mitigation:** Tests verify `TrnException` received

## Alternatives Considered

### 1. Return Failure Messages (No Exceptions)

```scala
// Child sends failure message instead of throwing
case Initialize(bucket) =>
  try {
    val files = s3Client.fetchFiles(bucket)
    context.parent ! BlockStorageFetched(testId, files)
  } catch {
    case e: AmazonS3Exception =>
      context.parent ! BlockStorageError(testId, e.getMessage)
  }
```

**Pros:**
- Explicit error handling
- No exception propagation

**Cons:**
- Child must handle all exceptions (error-prone)
- Parent must handle both success and failure messages
- Easy to forget error cases
- No supervision benefits
- Harder to test (must mock both paths)

**Verdict:** Rejected due to scattered error handling

### 2. Try/Catch in Parent

```scala
// Parent catches exceptions from child operations
case TrnLoading =>
  try {
    spawnChildren()
    initializeChildren()
  } catch {
    case e: Exception =>
      context.self ! TrnException(e)
  }
```

**Pros:**
- Explicit error handling
- Centralized catch blocks

**Cons:**
- Doesn't catch child actor exceptions (they're async)
- Mixes sync error handling with async actor messaging
- Doesn't leverage supervision

**Verdict:** Rejected because it doesn't work with async actors

### 3. Supervision with Stop Strategy

```scala
Behaviors.supervise(BlockStorageActor(testId))
  .onFailure[Exception](SupervisorStrategy.stop)
```

**Pros:**
- Clean failure model (stop failed actor)

**Cons:**
- Parent doesn't receive exception details
- No chance for transient failure recovery
- Harder to report specific error to user
- FSM doesn't know why child stopped

**Verdict:** Rejected due to lack of error information

### 4. No Supervision (Let Parent Crash)

```scala
// Don't catch exceptions at all
// Let entire actor system crash on error
```

**Pros:**
- Simple: no error handling code

**Cons:**
- Unacceptable: single test failure crashes entire system
- Loses all running tests
- Poor user experience
- Production system would be unstable

**Verdict:** Rejected as unacceptable

### 5. Circuit Breaker Pattern

```scala
val breaker = CircuitBreaker(
  maxFailures = 3,
  callTimeout = 10.seconds,
  resetTimeout = 1.minute
)

breaker.withCircuitBreaker {
  blockStorageActor ! Initialize(bucket)
}
```

**Pros:**
- Prevents repeated failures
- Fail-fast when system is unhealthy

**Cons:**
- Adds complexity
- Each child operation needs circuit breaker
- Doesn't replace supervision (needs both)
- TestExecutionActor is single-use (no repeated calls)

**Verdict:** Rejected as overkill for single-use actor; may be useful for QueueActor

## Implementation Notes

**Exception Hierarchy:**

```scala
sealed trait ProbeExceptions extends Exception

case class BlockStorageException(message: String, cause: Throwable = null)
  extends Exception(message, cause) with ProbeExceptions

case class VaultException(message: String, cause: Throwable = null)
  extends Exception(message, cause) with ProbeExceptions

case class CucumberExecutionException(message: String, cause: Throwable = null)
  extends Exception(message, cause) with ProbeExceptions

case class KafkaProducerException(message: String, cause: Throwable = null)
  extends Exception(message, cause) with ProbeExceptions

case class KafkaConsumerException(message: String, cause: Throwable = null)
  extends Exception(message, cause) with ProbeExceptions

case class ConfigurationException(message: String, cause: Throwable = null)
  extends Exception(message, cause) with ProbeExceptions
```

**Child Actor Pattern:**

```scala
object BlockStorageActor {
  def apply(testId: UUID): Behavior[BlockStorageCommand] = {
    Behaviors.receive { (context, message) =>
      message match {
        case Initialize(bucket) =>
          try {
            // Perform operation
            val result = fetchFromS3(bucket)
            context.parent ! BlockStorageFetched(testId, result)
            context.parent ! ChildGoodToGo(testId, context.self)
            Behaviors.same
          } catch {
            case e: AmazonS3Exception =>
              // Wrap and throw domain exception
              throw BlockStorageException(s"S3 fetch failed: ${e.getMessage}", e)
          }
      }
    }
  }
}
```

**Testing Pattern:**

```scala
// Test exception handling
When("""the TestExecutionActor receives "TrnException"""") { () =>
  val exception = BlockStorageException("Test exception")
  world.sendMessage(TestExecutionCommands.TrnException(exception))
}

And("""the TestExecutionActor processes "TrnException"""") { () =>
  Thread.sleep(100)  // Allow processing
}

Then("""the QueueActor should receive "TestException" with testId {word}""") { testIdStr =>
  val message = world.expectQueueMessage[QueueCommands.TestException]()
  message.testId shouldBe UUID.fromString(testIdStr)
  message.exception should not be null
}
```

**Error Reporting:**

```scala
// To QueueActor
queueActor ! QueueCommands.TestException(testId, exception)

// To Service Layer (HTTP API)
case GetStatus(testId, replyTo) =>
  replyTo ! TestStatusResponse(
    testId = testId,
    state = "Exception",
    error = Some(exception.getMessage)
  )
```

## Related Decisions

- ADR 001: FSM Pattern for Test Execution (parent decision)
- ADR 002: Self-Message Continuation (`TrnException` is a self-message)
- ADR 004: Factory Injection (enables testing exception handling)

## References

- Erlang Supervision Trees: https://www.erlang.org/doc/design_principles/des_princ.html#supervision-trees
- Akka Supervision: https://doc.akka.io/docs/akka/current/typed/fault-tolerance.html
- Error Kernel: https://ferd.ca/the-zen-of-erlang.html
- Blueprint: [05.2-test-execution-actor-error-handling.md](../blueprint/05%20State%20Machine/05.2-test-execution-actor-error-handling.md)
- Requirements: `working/TestExecutionActorFSMRequirements.md` (Section 7: Error Handling)
- Error Flow Diagram: `working/TestExecutionActor-ErrorPropogationRecoveryFlow.mermaid`

---

**Document History:**
- 2025-10-13: Initial ADR created
