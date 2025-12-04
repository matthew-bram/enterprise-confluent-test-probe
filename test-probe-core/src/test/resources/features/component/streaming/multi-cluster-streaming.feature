@ComponentTest
Feature: Multi-Cluster Streaming - Multiple Bootstrap Servers Support
  As a Kafka streaming actor
  I need to use custom bootstrap servers from TopicDirective when specified
  So that tests can connect to different Kafka clusters per topic

  This feature enables multi-stretch cluster testing where:
  - Some topics connect to Region1 Kafka cluster
  - Other topics connect to Region2 Kafka cluster
  - Topics without explicit bootstrap servers use the default from config

  Background:
    Given testcontainers infrastructure is started
    And a running actor system
    And a CoreConfig is available

  @Critical @Consumer @CustomBootstrap
  Scenario Outline: Consumer uses custom bootstrap server from TopicDirective
    Given a TopicDirective is configured for topic <topic> with custom bootstrap server <customBootstrap>
    And a KafkaSecurityDirective is configured for role consumer
    And the default bootstrap server is <defaultBootstrap>
    When a KafkaConsumerStreamingActor would be spawned for test <testId> and topic <topic>
    Then the consumer should connect to bootstrap server <expectedBootstrap>
    And the consumer should NOT connect to bootstrap server <defaultBootstrap>

    Examples:
      | testId                           | topic            | customBootstrap              | defaultBootstrap  | expectedBootstrap            |
      | "test-consumer-custom-001"       | "region2-events" | "kafka-region2.company:9092" | "localhost:9092"  | "kafka-region2.company:9092" |
      | "test-consumer-custom-002"       | "region3-events" | "kafka-region3.company:9094" | "localhost:9092"  | "kafka-region3.company:9094" |

  @Critical @Consumer @DefaultFallback
  Scenario Outline: Consumer falls back to default bootstrap server when None specified
    Given a TopicDirective is configured for topic <topic> with no custom bootstrap server
    And a KafkaSecurityDirective is configured for role consumer
    And the default bootstrap server is <defaultBootstrap>
    When a KafkaConsumerStreamingActor would be spawned for test <testId> and topic <topic>
    Then the consumer should connect to bootstrap server <defaultBootstrap>

    Examples:
      | testId                           | topic            | defaultBootstrap          |
      | "test-consumer-default-001"      | "order-events"   | "localhost:9092"          |
      | "test-consumer-default-002"      | "payment-events" | "kafka.default.local:9092"|

  @Critical @Producer @CustomBootstrap
  Scenario Outline: Producer uses custom bootstrap server from TopicDirective
    Given a TopicDirective is configured for topic <topic> with custom bootstrap server <customBootstrap>
    And a KafkaSecurityDirective is configured for role producer
    And the default bootstrap server is <defaultBootstrap>
    When a KafkaProducerStreamingActor would be spawned for test <testId> and topic <topic>
    Then the producer should connect to bootstrap server <expectedBootstrap>
    And the producer should NOT connect to bootstrap server <defaultBootstrap>

    Examples:
      | testId                           | topic            | customBootstrap              | defaultBootstrap  | expectedBootstrap            |
      | "test-producer-custom-001"       | "region2-events" | "kafka-region2.company:9092" | "localhost:9092"  | "kafka-region2.company:9092" |
      | "test-producer-custom-002"       | "region3-events" | "kafka-region3.company:9094" | "localhost:9092"  | "kafka-region3.company:9094" |

  @Critical @Producer @DefaultFallback
  Scenario Outline: Producer falls back to default bootstrap server when None specified
    Given a TopicDirective is configured for topic <topic> with no custom bootstrap server
    And a KafkaSecurityDirective is configured for role producer
    And the default bootstrap server is <defaultBootstrap>
    When a KafkaProducerStreamingActor would be spawned for test <testId> and topic <topic>
    Then the producer should connect to bootstrap server <defaultBootstrap>

    Examples:
      | testId                           | topic            | defaultBootstrap          |
      | "test-producer-default-001"      | "order-events"   | "localhost:9092"          |
      | "test-producer-default-002"      | "payment-events" | "kafka.default.local:9092"|

  @Critical @MultiCluster @MixedConfiguration
  Scenario: Multiple consumers with different bootstrap servers in same test
    Given the following TopicDirectives are configured:
      | topic            | bootstrapServers             |
      | region1-events   | None                         |
      | region2-events   | kafka-region2.company:9092   |
      | region3-events   | kafka-region3.company:9094   |
    And KafkaSecurityDirectives are available for all consumers
    And the default bootstrap server is "localhost:9092"
    When KafkaConsumerStreamingActors would be spawned for test "test-multi-cluster-001"
    Then consumer for "region1-events" should connect to "localhost:9092"
    And consumer for "region2-events" should connect to "kafka-region2.company:9092"
    And consumer for "region3-events" should connect to "kafka-region3.company:9094"

  @Critical @MultiCluster @ProducerConsumerMix
  Scenario: Producers and consumers with mixed bootstrap server configurations
    Given the following TopicDirectives are configured:
      | topic            | role     | bootstrapServers           |
      | input-events     | consumer | kafka-region1:9092         |
      | output-events    | producer | kafka-region2:9092         |
      | default-events   | consumer | None                       |
    And KafkaSecurityDirectives are available
    And the default bootstrap server is "localhost:9092"
    When streaming actors would be spawned for test "test-mixed-cluster-001"
    Then consumer for "input-events" should connect to "kafka-region1:9092"
    And producer for "output-events" should connect to "kafka-region2:9092"
    And consumer for "default-events" should connect to "localhost:9092"

  @Edge @Consumer @EmptyBootstrap
  Scenario Outline: Consumer treats empty bootstrap server as invalid
    Given a TopicDirective is configured for topic <topic> with empty bootstrap server
    And a KafkaSecurityDirective is configured for role consumer
    When validation is performed on the TopicDirective
    Then the validation should fail with "Bootstrap servers cannot be empty"

    Examples:
      | topic           |
      | "empty-events"  |

  @Edge @Producer @EmptyBootstrap
  Scenario Outline: Producer treats empty bootstrap server as invalid
    Given a TopicDirective is configured for topic <topic> with empty bootstrap server
    And a KafkaSecurityDirective is configured for role producer
    When validation is performed on the TopicDirective
    Then the validation should fail with "Bootstrap servers cannot be empty"

    Examples:
      | topic           |
      | "empty-events"  |

  @Edge @MultipleBootstrapHosts
  Scenario Outline: Consumer handles comma-separated bootstrap servers
    Given a TopicDirective is configured for topic <topic> with custom bootstrap server <bootstrapServers>
    And a KafkaSecurityDirective is configured for role consumer
    When a KafkaConsumerStreamingActor would be spawned for test <testId> and topic <topic>
    Then the consumer should use all bootstrap servers from <bootstrapServers>

    Examples:
      | testId                           | topic           | bootstrapServers                        |
      | "test-multi-host-001"            | "ha-events"     | "kafka1:9092,kafka2:9092"               |
      | "test-multi-host-002"            | "cluster-events"| "kafka1:9092,kafka2:9092,kafka3:9092"   |

  @TestInfrastructure @MultiCluster
  Scenario Outline: TestcontainersManager supports named clusters
    Given a named cluster <clusterName> is requested
    When TestcontainersManager creates the cluster
    Then the cluster should be accessible
    And the cluster bootstrap servers should be available
    And the cluster schema registry should be available

    Examples:
      | clusterName |
      | "default"   |
      | "region2"   |
      | "region3"   |

  @TestInfrastructure @ClusterCleanup
  Scenario: TestcontainersManager cleans up all clusters on shutdown
    Given the following clusters are started:
      | clusterName |
      | default     |
      | region2     |
    When TestcontainersManager cleanup is triggered
    Then all clusters should be stopped
    And no cluster resources should remain

  @Validation @EffectiveBootstrapServers
  Scenario Outline: Verify effective bootstrap server calculation
    Given a TopicDirective with bootstrapServers <directiveBootstrap>
    And the default bootstrap server from config is <configBootstrap>
    When the effective bootstrap server is calculated
    Then the result should be <effectiveBootstrap>

    Examples:
      | directiveBootstrap               | configBootstrap       | effectiveBootstrap               |
      | "kafka-custom:9092"              | "localhost:9092"      | "kafka-custom:9092"              |
      | "None"                           | "localhost:9092"      | "localhost:9092"                 |
      | "kafka1:9092,kafka2:9092"        | "localhost:9092"      | "kafka1:9092,kafka2:9092"        |
      | "None"                           | "default.kafka:9092"  | "default.kafka:9092"             |
