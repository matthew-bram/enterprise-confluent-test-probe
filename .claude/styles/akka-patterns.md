# Akka Typed Patterns

## Actor Use
- Use typed actors for proper compilation checks

## Actor Naming
- Actor classes: `NameActor` (e.g., `QueueActor`, `TestExecutionActor`)
- Actor companion objects: Same as class name for factory methods
- Actor references: `nameActor` or `nameActorRef`

## Message Protocol Design
- Sealed trait for all commands: `sealed trait Command`
- Case classes for specific commands: `case class SubmitTest(...) extends Command`
- Include `replyTo: ActorRef[ResponseType]` for request-response patterns
- Private internal commands: `private case class InternalCommand() extends Command`

## Actor Structure Pattern
```scala
object NameActor {
  sealed trait Command
  case class PublicCommand(data: String, replyTo: ActorRef[Response]) extends Command
  private case class InternalCommand() extends Command

  def apply(dependencies: Deps): Behavior[Command] = {
    Behaviors.setup { context =>
      new NameActor(dependencies, context).init()
    }
  }
}

class NameActor(
  dependencies: Deps,
  context: ActorContext[NameActor.Command]
) {
  import NameActor._

  def init(): Behavior[Command] = {
    // Initial behavior
  }
}
```

## State Management
- Use `Behaviors.withStash` for buffering messages during state transitions
only when asynchronous operations are happening on Command received. Stash is
unnecessary with synchronous operations.
- Finite State Machine pattern for complex state management
- Immutable state objects passed between behaviors

## Prefer Actor -> Actor Tell Communications Over "ASK"
- Prefer message passing between actors with `tell` or `!`
- Piping and asks should be limited and should be discussed within the team
- Bias on creating new actor adapter classes with tell commands over using 
scala futures

## Error Handling in Actors
- Use supervision strategies appropriately
- Log errors and telemetry with actor context
- Return to known good state on exceptions
- Use `Behaviors.stopped` to terminate gracefully

## Testing Patterns
- Use `ActorTestKit` for testing actors
- Create TestProbe for mocking actor dependencies
- Test message protocols separately from business logic
- Use synchronous testing with proper timeouts
- Protect class and object access at the class or object level, not
at the member level. eg `private[actors] class MyActor` and not
`private def...`

## Configuration
- Use Akka configuration for timeouts and limits
- Make actor behavior configurable through constructor parameters
- Use typed configuration objects instead of raw config

## Performance Considerations
- Avoid blocking operations in actor message handling
- Use `pipeTo` for async operations to adapters 
(eg, making a REST client call) that need to send results back
- For external blocking operations, create thread pools with fixed sizes
according to industry best practices
- Consider actor hierarchy for supervision and organization
- Use routers for horizontal scaling when appropriate

## Styling
- Single purpose actors
- Actors should generally be short-lived, meaning only used until their purpose
is fulfilled, then poison pill them after any external connections have 
been terminated.
- Commands should always be neatly organized in a single file and maintained for
good hygiene

## Complex Patterns
- In FSM logic, we should be using the Deferred self-message/Deferred continuation/Self-scheduling 
transition hook patterns to trigger actions off the back of state changes.
Here is an example:

```scala
private def behavior1(target: ActorRef[String]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case CommandX =>
          context.log.info("Received CommandX, transitioning to Behavior2")

          // Schedule post-transition hook
          context.self ! AfterTransition

          // Switch behavior
          behavior2(target)

        case _ =>
          context.log.info(s"Ignoring $message in Behavior1")
          Behaviors.same
      }
    }

  private def behavior2(target: ActorRef[String]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case AfterTransition =>
          // This will run *immediately after* Behavior2 becomes active
          context.log.info("Behavior2 post-transition hook running")
          target ! "Hello from post-transition"

        case DoSomethingElse =>
          context.log.info("Normal message in Behavior2")
      }
      Behaviors.same
    }
```
Those with a deep understanding of Akka single-threaded execution will see that we can 
leverage the fifo aspects of the mailbox to eliminate the need for non-trivial async 
operations within the actor system.
- Prefer passing actor refs in case classes over using pipeTo
