# Testing Standards

## Test Framework
- Use ScalaTest with `AnyWordSpec` style for readability
- Use `Matchers` trait for assertion DSL
- Use `ActorTestKit` for actor testing

## Test Structure
```scala
class ComponentSpec extends AnyWordSpec with Matchers {
  "ComponentName" should {
    "behavior description" in {
      // Test implementation
    }

    "handle edge cases" in {
      // Edge case tests
    }
  }
}
```

## Test Naming
- Test files: `ComponentNameSpec.scala`
- Test classes: `ComponentNameSpec`
- Describe blocks: Use the component/class name being tested
- Test descriptions: Use "should [expected behavior]" format

## Akka Actor Testing
```scala
class ActorSpec extends AnyWordSpec with Matchers {
  implicit val testKit: ActorTestKit = ActorTestKit()

  "ActorName" should {
    "respond to messages correctly" in {
      val probe = testKit.createTestProbe[ResponseType]()
      val actor = testKit.spawn(ActorName(dependencies))

      actor ! Message(data, probe.ref)
      probe.expectMessage(ExpectedResponse)
    }
  }

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
  }
}
```

## Test Data
- Use descriptive test data that makes test intent clear
- Create factory methods for complex test objects
- Use realistic but minimal data sets
- Avoid magic numbers and strings - use named constants

## Assertions
- Use ScalaTest matchers for readability:
  - `result shouldBe expected`
  - `list should contain (element)`
  - `option shouldBe defined`
  - `future.futureValue should startWith("prefix")`

## Async Testing
- Use `ScalaFutures` trait for testing Future-based code
- Set appropriate timeouts for async operations
- Use `eventually` for testing eventual consistency

## Test Organization
- One test class per production class
- Group related tests in nested describe blocks
- Use `beforeEach`/`afterEach` for test setup/cleanup
- Keep tests independent and isolated

## Mock/Stub Guidelines
- Use TestProbe for mocking actors
- Create test doubles for external services (virtualization)
- Prefer dependency injection for testability
- Test real behavior when possible - Caveat: Use mocking for unit and
some component tests, and virtualization only for component tests

## Testability Pattern (REQUIRED)

**All production code must follow the visibility pattern for comprehensive testing.**

### Visibility Pattern
- **Methods**: Keep public (no `private` modifier)
- **Objects/Classes**: Apply visibility at this level (`private[core]`, `private[common]`, etc.)

**Example** (TestExecutionActor pattern):
```scala
// Production code
private[core] object TestExecutionActor {

  // All methods public - can be unit tested directly
  def setupBehavior(...): Behavior[Command] = { ... }

  def identifyChildActor(child: ActorRef[_], data: TestData): String = { ... }

  def createStatusResponse(testId: UUID, state: String, data: TestData): Response = { ... }
}
```

```scala
// Test code - direct method testing
"TestExecutionActor helper methods" should {
  "identifyChildActor should correctly identify BlockStorage actor" in {
    val testData: TestData = createTestData()
    val result: String = TestExecutionActor.identifyChildActor(blockStorageProbe.ref, testData)
    result shouldBe "BlockStorage"
  }

  "createStatusResponse should include all fields from TestData" in {
    val testData: TestData = createCompleteTestData()
    val response: TestStatusResponse = TestExecutionActor.createStatusResponse(testId, "Testing", testData)
    response.bucket shouldBe Some("test-bucket")
    response.testType shouldBe Some("functional")
  }
}
```

**Benefits**:
- Direct unit testing of all helper methods
- 85%+ coverage achievable without hacks
- Helper methods have regression protection
- Module boundaries still enforced

**This is a peer review rejection point** - see `.claude/styles/scala-conventions.md` for full details.

## Coverage Expectations
- Aim for 70%+ line coverage minimum (enforced by Maven profile)
- Target 85%+ for actors and critical business logic
- Focus on critical business logic paths
- Test error conditions and edge cases
- Test all helper methods directly (enabled by visibility pattern)
- Don't test trivial getters/setters unless they have logic

## Helper Method Testing
With the visibility pattern, all helper methods should have direct unit tests:

```scala
"Helper methods" should {
  "test pure helper logic directly" in {
    val input: Input = createTestInput()
    val result: Output = MyObject.helperMethod(input)
    result shouldBe expectedOutput
  }
}
```

**Do NOT** test helper methods only indirectly through integration tests. Direct unit tests provide:
- Faster feedback
- Clearer failure messages
- Better edge case coverage
- Regression protection