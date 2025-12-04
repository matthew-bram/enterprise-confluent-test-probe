@ComponentTest
Feature: TopicDirectiveValidator - Topic Directive Validation Rules
  As a TopicDirectiveValidator
  I need to validate topic directives before they are processed
  So that invalid configurations are rejected with clear error messages

  The validator enforces two rules:
  1. Topic uniqueness - each topic name must be unique within a directive list
  2. Bootstrap server format - must be valid host:port or comma-separated list

  Background:
    Given the TopicDirectiveValidator is available

  @Critical @Uniqueness @HappyPath
  Scenario Outline: Validate unique topics passes validation
    Given a list of TopicDirectives with topics <topics>
    When the validator checks uniqueness
    Then the validation should succeed
    And the result should be Right(())

    Examples:
      | topics                                  |
      | "order-events"                          |
      | "order-events,payment-events"           |
      | "orders,payments,shipments,returns"     |

  @Critical @Uniqueness @DuplicateDetection
  Scenario Outline: Detect single duplicate topic
    Given a list of TopicDirectives with topics <topics>
    When the validator checks uniqueness
    Then the validation should fail
    And the error list should contain <count> error(s)
    And the validation error message should contain "Topic '<duplicate>' appears <occurrences> times"

    Examples:
      | topics                         | count | duplicate      | occurrences |
      | "orders,orders"                | 1     | orders         | 2           |
      | "orders,payments,orders"       | 1     | orders         | 2           |
      | "events,events,events"         | 1     | events         | 3           |

  @Critical @Uniqueness @MultipleDuplicates
  Scenario Outline: Detect multiple duplicate topics - lists ALL duplicates
    Given a list of TopicDirectives with topics <topics>
    When the validator checks uniqueness
    Then the validation should fail
    And the error list should contain <count> error(s)
    And the validation error message should contain "Topic 'orders'"
    And the validation error message should contain "Topic 'payments'"

    Examples:
      | topics                                  | count |
      | "orders,orders,payments,payments"       | 2     |
      | "orders,payments,orders,payments,other" | 2     |

  @Edge @Uniqueness @EmptyList
  Scenario: Handle empty topic list
    Given an empty list of TopicDirectives
    When the validator checks uniqueness
    Then the validation should succeed
    And the result should be Right(())

  @Edge @Uniqueness @SingleTopic
  Scenario: Handle single topic list
    Given a list of TopicDirectives with topics "single-topic"
    When the validator checks uniqueness
    Then the validation should succeed
    And the result should be Right(())

  @Critical @BootstrapFormat @HappyPath
  Scenario Outline: Validate bootstrap server format - valid single host:port
    Given a bootstrap servers string <bootstrapServers>
    When the validator checks bootstrap server format
    Then the validation should succeed
    And the result should be Right(())

    Examples:
      | bootstrapServers              |
      | "localhost:9092"              |
      | "kafka.company.com:9092"      |
      | "kafka-broker-1:29092"        |
      | "192.168.1.100:9092"          |
      | "kafka.region1.company.com:9094" |

  @Critical @BootstrapFormat @MultipleHosts
  Scenario Outline: Validate bootstrap server format - valid multiple hosts
    Given a bootstrap servers string <bootstrapServers>
    When the validator checks bootstrap server format
    Then the validation should succeed
    And the result should be Right(())

    Examples:
      | bootstrapServers                                    |
      | "kafka1:9092,kafka2:9092"                           |
      | "kafka1:9092,kafka2:9092,kafka3:9092"               |
      | "region1.kafka:9092,region2.kafka:9092"             |

  @Critical @BootstrapFormat @NoneValue
  Scenario: Handle None bootstrap server - valid
    Given no bootstrap servers specified
    When the validator checks bootstrap server format
    Then the validation should succeed
    And the result should be Right(())

  @Edge @BootstrapFormat @MissingPort
  Scenario Outline: Reject invalid bootstrap server format - missing port
    Given a bootstrap servers string <bootstrapServers>
    When the validator checks bootstrap server format
    Then the validation should fail
    And the validation error message should contain "Invalid bootstrap server format"
    And the validation error message should contain "Expected format: host:port"

    Examples:
      | bootstrapServers           |
      | "localhost"                |
      | "kafka.company.com"        |
      | "kafka1:9092,kafka2"       |

  @Edge @BootstrapFormat @InvalidPort
  Scenario Outline: Reject invalid bootstrap server format - invalid port number
    Given a bootstrap servers string <bootstrapServers>
    When the validator checks bootstrap server format
    Then the validation should fail
    And the validation error message should contain "Invalid bootstrap server format"

    Examples:
      | bootstrapServers           |
      | "localhost:0"              |
      | "localhost:99999"          |
      | "localhost:-1"             |
      | "localhost:abc"            |

  @Edge @BootstrapFormat @EmptyString
  Scenario: Reject empty bootstrap servers string
    Given a bootstrap servers string ""
    When the validator checks bootstrap server format
    Then the validation should fail
    And the validation error message should contain "Bootstrap servers cannot be empty"

  @Edge @BootstrapFormat @WhitespaceHandling
  Scenario Outline: Handle whitespace in bootstrap servers
    Given a bootstrap servers string <bootstrapServers>
    When the validator checks bootstrap server format
    Then the validation should succeed

    Examples:
      | bootstrapServers                   |
      | "kafka1:9092, kafka2:9092"         |
      | "kafka1:9092 , kafka2:9092"        |

  @Edge @BootstrapFormat @InvalidHostname
  Scenario Outline: Reject invalid hostname format
    Given a bootstrap servers string <bootstrapServers>
    When the validator checks bootstrap server format
    Then the validation should fail
    And the validation error message should contain "Invalid bootstrap server format"

    Examples:
      | bootstrapServers           |
      | ":9092"                    |
      | "-kafka:9092"              |
      | "kafka-:9092"              |

  @Validation @PortRange @ValidBoundary
  Scenario Outline: Validate valid port range boundaries
    Given a bootstrap servers string <bootstrapServers>
    When the validator checks bootstrap server format
    Then the validation should succeed

    Examples:
      | bootstrapServers           |
      | "localhost:1"              |
      | "localhost:65535"          |

  @Validation @PortRange @InvalidBoundary
  Scenario: Reject port exceeding maximum
    Given a bootstrap servers string "localhost:65536"
    When the validator checks bootstrap server format
    Then the validation should fail
