Refactor the specified Scala actor to follow our Akka Typed best practices:

1. **Message Protocol**:
   - Ensure sealed trait Command hierarchy
   - Add replyTo parameters for request-response patterns
   - Use case classes for all messages
   - Include private internal commands where appropriate

2. **Actor Structure**:
   - Companion object with apply method
   - Constructor dependency injection
   - Setup behavior pattern with context

3. **State Management**:
   - Immutable state objects
   - Proper behavior transitions
   - Use of stash for buffering when needed

4. **Error Handling**:
   - Graceful error recovery
   - Appropriate logging with context
   - Return to known state on exceptions

5. **Performance**:
   - Avoid blocking operations
   - Use pipeTo for async operations
   - Consider message batching where appropriate

Apply these patterns while preserving the existing functionality. Ensure the refactored code compiles and maintains the same external interface.

Actor to refactor: $ARGUMENTS