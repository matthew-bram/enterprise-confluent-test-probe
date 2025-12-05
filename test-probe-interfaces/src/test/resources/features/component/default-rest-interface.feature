#Feature: DefaultRestInterface Builder Module
#  As a builder
#  I want to initialize the REST interface module
#  So that I can serve HTTP requests for test management
#
#  Background:
#    Given a test ActorSystem is running
#
#  Scenario: Successful REST interface initialization with all dependencies
#    Given a BuilderContext with Config initialized
#    And a BuilderContext with ActorSystem initialized
#    And a BuilderContext with ServiceInterfaceFunctions initialized
#    And REST is enabled in configuration
#    When I create a DefaultRestInterface
#    And I call preFlight on the DefaultRestInterface
#    And I call initialize on the DefaultRestInterface
#    Then the preFlight should succeed
#    And the initialize should succeed
#    And the REST server should be bound to configured port
#    And the BuilderContext should contain InterfacesConfig
#
#  Scenario: PreFlight fails when Config is missing
#    Given a BuilderContext with Config NOT initialized
#    And a BuilderContext with ActorSystem initialized
#    And a BuilderContext with ServiceInterfaceFunctions initialized
#    When I create a DefaultRestInterface
#    And I call preFlight on the DefaultRestInterface
#    Then the preFlight should fail with error containing "Config must be initialized"
#
#  Scenario: PreFlight fails when ActorSystem is missing
#    Given a BuilderContext with Config initialized
#    And a BuilderContext with ActorSystem NOT initialized
#    And a BuilderContext with ServiceInterfaceFunctions initialized
#    When I create a DefaultRestInterface
#    And I call preFlight on the DefaultRestInterface
#    Then the preFlight should fail with error containing "ActorSystem must be initialized"
#
#  Scenario: PreFlight fails when ServiceInterfaceFunctions is missing
#    Given a BuilderContext with Config initialized
#    And a BuilderContext with ActorSystem initialized
#    And a BuilderContext with ServiceInterfaceFunctions NOT initialized
#    When I create a DefaultRestInterface
#    And I call preFlight on the DefaultRestInterface
#    Then the preFlight should fail with error containing "ServiceInterfaceFunctions must be initialized"
#
#  Scenario: PreFlight accumulates all missing dependencies
#    Given a BuilderContext with Config NOT initialized
#    And a BuilderContext with ActorSystem NOT initialized
#    And a BuilderContext with ServiceInterfaceFunctions NOT initialized
#    When I create a DefaultRestInterface
#    And I call preFlight on the DefaultRestInterface
#    Then the preFlight should fail with error containing "Config must be initialized"
#    And the preFlight should fail with error containing "ActorSystem must be initialized"
#    And the preFlight should fail with error containing "ServiceInterfaceFunctions must be initialized"
#
#  Scenario: PreFlight fails when REST interface is disabled in configuration
#    Given a BuilderContext with Config initialized
#    And a BuilderContext with ActorSystem initialized
#    And a BuilderContext with ServiceInterfaceFunctions initialized
#    And REST is disabled in configuration
#    When I create a DefaultRestInterface
#    And I call preFlight on the DefaultRestInterface
#    Then the preFlight should fail with error containing "REST interface is disabled"
#
#  Scenario: Initialize fails when already initialized
#    Given a BuilderContext with Config initialized
#    And a BuilderContext with ActorSystem initialized
#    And a BuilderContext with ServiceInterfaceFunctions initialized
#    And REST is enabled in configuration
#    And a DefaultRestInterface is already initialized
#    When I call initialize on the DefaultRestInterface again
#    Then the initialize should fail with error containing "already initialized"
#
#  Scenario: FinalCheck succeeds when server is running
#    Given a BuilderContext with Config initialized
#    And a BuilderContext with ActorSystem initialized
#    And a BuilderContext with ServiceInterfaceFunctions initialized
#    And a DefaultRestInterface is initialized
#    When I call finalCheck on the DefaultRestInterface
#    Then the finalCheck should succeed
#    And the BuilderContext should have InterfacesConfig
#
#  Scenario: FinalCheck fails when server not started
#    Given a DefaultRestInterface is created but not initialized
#    When I call finalCheck on the DefaultRestInterface
#    Then the finalCheck should fail with error containing "not started"
#
#  Scenario: Graceful shutdown unbinds server
#    Given a DefaultRestInterface is initialized
#    And the REST server is bound
#    When I call shutdown on the DefaultRestInterface
#    Then the shutdown should succeed
#    And the server should be unbound
#
#  Scenario: Shutdown is idempotent when not initialized
#    Given a DefaultRestInterface is created but not initialized
#    When I call shutdown on the DefaultRestInterface
#    Then the shutdown should succeed
#    And no errors should occur
#
#  Scenario: SetCurriedFunctions updates functions reference
#    Given a DefaultRestInterface is created
#    And a ServiceInterfaceFunctions instance exists
#    When I call setCurriedFunctions with the functions
#    Then the functions should be stored in the interface
