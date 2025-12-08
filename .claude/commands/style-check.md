Analyze the current Scala file against our project style guidelines. Check for:

1. **Formatting compliance**:
   - 2-space indentation (no tabs)
   - Line length under 120 characters
   - Proper brace placement
   - Apply in all companion objects

2. **Naming conventions**:
   - PascalCase for classes/objects/traits
   - camelCase for methods/variables
   - UPPER_SNAKE_CASE for constants

3. **Import organization**:
   - Standard library imports first
   - Third-party imports second
   - Project imports last
   - Alphabetical within groups

4. **Akka patterns** (if applicable):
   - Sealed trait for Command types
   - Proper actor companion object structure
   - replyTo patterns for request-response

5. **Code structure**:
   - One public class per file
   - Proper package object usage
   - Method return type annotations for public methods

Provide specific feedback on any violations found and suggest corrections. If the file follows all conventions, confirm compliance.

File to analyze: $ARGUMENTS