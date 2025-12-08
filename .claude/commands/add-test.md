Generate a test file following our ScalaTest standards for the specified component:

1. **Test Structure**:
   - Use AnyWordSpec with Matchers
   - "ComponentName should" describe blocks
   - "behavior description" in test cases
   - Proper test file naming: ComponentNameSpec.scala

2. **Actor Testing** (if applicable):
   - Use ActorTestKit for actor tests
   - Create TestProbe instances for mocking
   - Test message protocols thoroughly
   - Include proper cleanup with afterAll()

3. **Test Coverage**:
   - Happy path scenarios
   - Error conditions and edge cases
   - State transitions (for actors)
   - Async behavior validation

4. **Test Data**:
   - Descriptive test data that clarifies intent
   - Factory methods for complex objects
   - Named constants instead of magic values

5. **Assertions**:
   - Use ScalaTest matchers for readability
   - shouldBe, should contain, shouldBe defined
   - ScalaFutures for async testing

Create a comprehensive test file that achieves good coverage while being maintainable and readable.

Component to test: $ARGUMENTS