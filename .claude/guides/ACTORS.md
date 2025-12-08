# Akka Actors Guide

This guide covers Akka Typed Actor patterns and best practices for the Test-Probe project.

---

## Message Protocol Design

All commands organized in sealed traits:

```scala
object QueueCommands {
  sealed trait QueueCommand
  case class SubmitTest(testId: UUID, replyTo: ActorRef[Response]) extends Command
  private case class InternalCommand() extends Command  // Private for internal use
}
```

### Naming Convention

- Actor classes: `NameActor` (e.g., `QueueActor`, `TestExecutionActor`)
- Actor refs: `nameActor` or `nameActorRef`
- Actor commands: `nameCommand` (e.g., `QueueCommand`, `TestExecutionCommand`)
- Actor states: `nameState` (e.g., `QueueState`, `TestExecutionState`)
- Actor data: `nameData` (e.g., `QueueData`, `TestExecutionData`)

---

## Prefer Tell Over Ask

- **Tell (`!`)** is preferred for actor-to-actor communication
- **Ask/Pipe**: Limited use, requires team discussion
- **Reasoning**: Maintains actor model purity, avoids blocking

---

## FSM with Deferred Self-Messages

Use the deferred continuation pattern for post-transition actions:

```scala
private def loadingBehavior(): Behavior[Command] =
  Behaviors.receive { (context, message) =>
    message match {
      case StartLoading =>
        context.log.info("Loading complete, transitioning to Loaded")

        // Schedule post-transition hook
        context.self ! TrnLoaded

        // Switch behavior
        loadedBehavior()

      case _ =>
        Behaviors.same
    }
  }

private def loadedBehavior(): Behavior[Command] =
  Behaviors.receive { (context, message) =>
    message match {
      case TrnLoaded =>
        // Post-transition hook runs immediately after behavior switch
        context.log.info("Loaded behavior active, notifying queue")
        queueActor ! TestLoaded(testId)
        Behaviors.same

      case StartTest =>
        // Normal message handling
        context.log.info("Starting test execution")
        Behaviors.same
    }
  }
```

**Why**: Leverages single-threaded actor execution and FIFO mailbox ordering to eliminate complex async operations.

---

## Actor Lifecycle

- **Short-lived actors**: Use actors until purpose fulfilled, then poison pill
- **Supervision**: Resume on validation errors, Restart on transient failures, Stop on fatal errors
- **Cleanup**: Terminate external connections before stopping actor

---

## Visibility Pattern for Testability

**REQUIRED - Peer Review Rejection Point**

Keep methods public (no `private` modifier) and apply visibility restriction at class/object level:

```scala
// CORRECT: Methods public, object scoped to module
private[core] object TestExecutionActor {
  def setupBehavior(...): Behavior[Command] = { ... }      // ✅ Can unit test
  def identifyChildActor(...): String = { ... }            // ✅ Can unit test
  def createStatusResponse(...): Response = { ... }        // ✅ Can unit test
}

// INCORRECT: Methods marked private
object TestExecutionActor {
  private def setupBehavior(...): Behavior[Command] = { ... }  // ❌ Cannot unit test
}
```

**Why This Matters**:
- `private` methods **CANNOT** be directly unit tested in Scala
- Forces testing through integration/behavior tests only
- **Reduces code coverage by 20-40%** (proven in GuardianActor, TestExecutionActor)
- Eliminates regression protection for helper methods
- Makes debugging harder due to lack of isolated tests

See `.claude/styles/scala-conventions.md` and `.claude/styles/testing-standards.md` for complete details.

---

## Adding a New Actor

1. Create actor in `test-probe-core/src/main/scala/com/company/probe/core/actors/`
2. Define message protocol in `test-probe-core/src/main/scala/com/company/probe/core/models/ActorCommands.scala`
3. Add supervision in `GuardianActor` or parent actor
4. Create `ActorSpec` in `test-probe-core/src/test/scala/.../component/actors/`
5. Add to `BuilderContext` if needed globally

---

## Related Guides

- **Full Akka Patterns**: See `.claude/styles/akka-patterns.md` for comprehensive patterns
- **Testing Actors**: See `.claude/guides/TESTING.md` for actor testing patterns
- **Architecture**: See `.claude/guides/ARCHITECTURE.md` for actor system hierarchy
- **Scala Conventions**: See `.claude/styles/scala-conventions.md` for code style