#Feature: REST Model Conversions (Anti-Corruption Layer)
#  As a hexagonal architecture
#  I want to convert between REST models (kebab-case) and Core models (camelCase)
#  So that I can maintain protocol-agnostic core domain and REST-specific interfaces
#
#  # Request Conversions (REST → Core)
#
#  Scenario: Convert RestStartTestRequest to Core parameters
#    Given a RestStartTestRequest with:
#      | field               | value                                  |
#      | test-id             | 550e8400-e29b-41d4-a716-446655440000   |
#      | block-storage-path  | s3://bucket/test-files                 |
#      | test-type           | integration                            |
#    When I convert the request to Core
#    Then parameter 1 should be UUID "550e8400-e29b-41d4-a716-446655440000"
#    And parameter 2 should be String "s3://bucket/test-files"
#    And parameter 3 should be Some("integration")
#
#  Scenario: Convert RestStartTestRequest without optional test-type
#    Given a RestStartTestRequest with:
#      | field               | value                                  |
#      | test-id             | 550e8400-e29b-41d4-a716-446655440000   |
#      | block-storage-path  | s3://bucket/test-files                 |
#    When I convert the request to Core
#    Then parameter 3 should be None
#
#  Scenario: Convert RestTestStatusRequest to Core parameter
#    Given a RestTestStatusRequest with test-id "550e8400-e29b-41d4-a716-446655440000"
#    When I convert the request to Core
#    Then the Core parameter should be UUID "550e8400-e29b-41d4-a716-446655440000"
#
#  Scenario: Convert RestQueueStatusRequest with testId to Core parameter
#    Given a RestQueueStatusRequest with test-id "550e8400-e29b-41d4-a716-446655440000"
#    When I convert the request to Core
#    Then the Core parameter should be Some(UUID("550e8400-e29b-41d4-a716-446655440000"))
#
#  Scenario: Convert RestQueueStatusRequest without testId to Core parameter
#    Given a RestQueueStatusRequest with no test-id
#    When I convert the request to Core
#    Then the Core parameter should be None
#
#  Scenario: Convert RestCancelRequest to Core parameter
#    Given a RestCancelRequest with test-id "550e8400-e29b-41d4-a716-446655440000"
#    When I convert the request to Core
#    Then the Core parameter should be UUID "550e8400-e29b-41d4-a716-446655440000"
#
#  # Response Conversions (Core → REST)
#
#  Scenario: Convert InitializeTestResponse to REST response
#    Given an InitializeTestResponse with:
#      | field   | value                                  |
#      | testId  | 550e8400-e29b-41d4-a716-446655440000   |
#      | message | Test initialized successfully          |
#    When I convert the response to REST
#    Then the REST response should have field "test-id" = "550e8400-e29b-41d4-a716-446655440000"
#    And the REST response should have field "message" = "Test initialized successfully"
#
#  Scenario: Convert StartTestResponse to REST response with all fields
#    Given a StartTestResponse with:
#      | field    | value                                  |
#      | testId   | 550e8400-e29b-41d4-a716-446655440000   |
#      | accepted | true                                   |
#      | testType | integration                            |
#      | message  | Test accepted for execution            |
#    When I convert the response to REST
#    Then the REST response should have field "test-id" = "550e8400-e29b-41d4-a716-446655440000"
#    And the REST response should have field "accepted" with value true
#    And the REST response should have field "test-type" = "integration"
#    And the REST response should have field "message" = "Test accepted for execution"
#
#  Scenario: Convert StartTestResponse without optional testType
#    Given a StartTestResponse with:
#      | field    | value                                  |
#      | testId   | 550e8400-e29b-41d4-a716-446655440000   |
#      | accepted | true                                   |
#      | testType | null                                   |
#      | message  | Test accepted                          |
#    When I convert the response to REST
#    Then the REST response field "test-type" should be null
#
#  Scenario: Convert TestStatusResponse with all optional fields
#    Given a TestStatusResponse with:
#      | field     | value                                  |
#      | testId    | 550e8400-e29b-41d4-a716-446655440000   |
#      | state     | Completed                              |
#      | bucket    | s3://bucket/test-files                 |
#      | testType  | integration                            |
#      | startTime | 2025-10-19T10:00:00Z                   |
#      | endTime   | 2025-10-19T10:05:00Z                   |
#      | success   | true                                   |
#      | error     | null                                   |
#    When I convert the response to REST
#    Then the REST response should have field "test-id" = "550e8400-e29b-41d4-a716-446655440000"
#    And the REST response should have field "state" = "Completed"
#    And the REST response should have field "bucket" = "s3://bucket/test-files"
#    And the REST response should have field "test-type" = "integration"
#    And the REST response should have field "start-time" = "2025-10-19T10:00:00Z"
#    And the REST response should have field "end-time" = "2025-10-19T10:05:00Z"
#    And the REST response should have field "success" with value true
#    And the REST response field "error" should be null
#
#  Scenario: Convert TestStatusResponse for failed test with error
#    Given a TestStatusResponse with:
#      | field     | value                                  |
#      | testId    | 550e8400-e29b-41d4-a716-446655440000   |
#      | state     | Exception                              |
#      | bucket    | s3://bucket/test-files                 |
#      | testType  | integration                            |
#      | startTime | 2025-10-19T10:00:00Z                   |
#      | endTime   | 2025-10-19T10:03:00Z                   |
#      | success   | false                                  |
#      | error     | Test execution timeout                 |
#    When I convert the response to REST
#    Then the REST response should have field "success" with value false
#    And the REST response should have field "error" = "Test execution timeout"
#
#  Scenario: Convert TestStatusResponse with minimal fields (test not started)
#    Given a TestStatusResponse with:
#      | field     | value                                  |
#      | testId    | 550e8400-e29b-41d4-a716-446655440000   |
#      | state     | Setup                                  |
#      | bucket    | null                                   |
#      | testType  | null                                   |
#      | startTime | null                                   |
#      | endTime   | null                                   |
#      | success   | null                                   |
#      | error     | null                                   |
#    When I convert the response to REST
#    Then the REST response should have field "state" = "Setup"
#    And the REST response field "bucket" should be null
#    And the REST response field "test-type" should be null
#    And the REST response field "start-time" should be null
#
#  Scenario: Convert QueueStatusResponse with all counts
#    Given a QueueStatusResponse with:
#      | field             | value                                  |
#      | totalTests        | 10                                     |
#      | setupCount        | 2                                      |
#      | loadingCount      | 1                                      |
#      | loadedCount       | 3                                      |
#      | testingCount      | 1                                      |
#      | completedCount    | 2                                      |
#      | exceptionCount    | 1                                      |
#      | currentlyTesting  | 550e8400-e29b-41d4-a716-446655440000   |
#    When I convert the response to REST
#    Then the REST response should have field "total-tests" = 10
#    And the REST response should have field "setup-count" = 2
#    And the REST response should have field "loading-count" = 1
#    And the REST response should have field "loaded-count" = 3
#    And the REST response should have field "testing-count" = 1
#    And the REST response should have field "completed-count" = 2
#    And the REST response should have field "exception-count" = 1
#    And the REST response should have field "currently-testing" = "550e8400-e29b-41d4-a716-446655440000"
#
#  Scenario: Convert QueueStatusResponse with empty queue
#    Given a QueueStatusResponse with:
#      | field             | value  |
#      | totalTests        | 0      |
#      | setupCount        | 0      |
#      | loadingCount      | 0      |
#      | loadedCount       | 0      |
#      | testingCount      | 0      |
#      | completedCount    | 0      |
#      | exceptionCount    | 0      |
#      | currentlyTesting  | null   |
#    When I convert the response to REST
#    Then the REST response should have field "total-tests" = 0
#    And the REST response field "currently-testing" should be null
#
#  Scenario: Convert TestCancelledResponse for successful cancellation
#    Given a TestCancelledResponse with:
#      | field     | value                                  |
#      | testId    | 550e8400-e29b-41d4-a716-446655440000   |
#      | cancelled | true                                   |
#      | message   | Test cancelled successfully            |
#    When I convert the response to REST
#    Then the REST response should have field "test-id" = "550e8400-e29b-41d4-a716-446655440000"
#    And the REST response should have field "cancelled" with value true
#    And the REST response should have field "message" = "Test cancelled successfully"
#
#  Scenario: Convert TestCancelledResponse for already completed test
#    Given a TestCancelledResponse with:
#      | field     | value                                  |
#      | testId    | 550e8400-e29b-41d4-a716-446655440000   |
#      | cancelled | false                                  |
#      | message   | Test already completed                 |
#    When I convert the response to REST
#    Then the REST response should have field "cancelled" with value false
#    And the REST response should have field "message" = "Test already completed"
#
#  # Field Name Mapping (kebab-case ↔ camelCase)
#
#  Scenario: Verify kebab-case to camelCase mapping in requests
#    Given REST field names use kebab-case:
#      | REST field name     | Core field name    |
#      | test-id             | testId             |
#      | block-storage-path  | bucket             |
#      | test-type           | testType           |
#    Then the conversion should map field names correctly
#
#  Scenario: Verify camelCase to kebab-case mapping in responses
#    Given Core field names use camelCase:
#      | Core field name     | REST field name         |
#      | testId              | test-id                 |
#      | testType            | test-type               |
#      | startTime           | start-time              |
#      | endTime             | end-time                |
#      | totalTests          | total-tests             |
#      | setupCount          | setup-count             |
#      | loadingCount        | loading-count           |
#      | loadedCount         | loaded-count            |
#      | testingCount        | testing-count           |
#      | completedCount      | completed-count         |
#      | exceptionCount      | exception-count         |
#      | currentlyTesting    | currently-testing       |
#    Then the conversion should map field names correctly
#
#  # Round-trip Conversions
#
#  Scenario: Round-trip conversion preserves UUID values
#    Given a UUID "550e8400-e29b-41d4-a716-446655440000"
#    When I convert from Core to REST and back to Core
#    Then the UUID should remain "550e8400-e29b-41d4-a716-446655440000"
#
#  Scenario: Round-trip conversion preserves Option[String] values
#    Given an Option[String] with value "integration"
#    When I convert from Core to REST and back to Core
#    Then the Option should contain "integration"
#
#  Scenario: Round-trip conversion preserves None values
#    Given an Option[String] with None
#    When I convert from Core to REST and back to Core
#    Then the Option should be None
#
#  # Anti-Corruption Layer Isolation
#
#  Scenario: REST model changes do not affect Core models
#    Given a new REST field is added "api-version"
#    When I add the field to REST models only
#    Then Core models should remain unchanged
#    And Core should not have knowledge of REST-specific fields
#
#  Scenario: Core model changes do not affect REST API contract
#    Given a new Core field is added "internalMetadata"
#    When I add the field to Core models only
#    Then REST models should remain unchanged
#    And REST API contract should remain stable
#
#  # Type Safety
#
#  Scenario: Conversion maintains type safety for UUIDs
#    Given a string "invalid-uuid"
#    When I try to create a RestTestStatusRequest
#    Then it should fail at compile time or parse time
#    And the conversion should not accept invalid UUIDs
#
#  Scenario: Conversion maintains type safety for booleans
#    Given a REST field "accepted" with value "yes"
#    When I try to parse the JSON
#    Then it should fail with JSON parsing error
#    And boolean fields should only accept true/false
