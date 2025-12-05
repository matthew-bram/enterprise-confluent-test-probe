package com.example.quickstart;

/**
 * Test-Probe Java Quickstart - Minimal Example
 *
 * This is a minimal main class demonstrating the Test-Probe dependency.
 *
 * NOTE: This is a minimal example that verifies the test-probe dependency
 * resolves correctly. For full integration testing examples with Kafka
 * produce/consume workflows, see the integration tests in test-probe-core.
 *
 * Usage:
 *   mvn compile exec:java
 */
public class QuickstartApplication {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Test-Probe Java Quickstart");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Test-Probe dependency loaded successfully!");
        System.out.println();
        System.out.println("Available APIs:");
        System.out.println("  - ProbeJavaDsl: Java DSL for produce/consume");
        System.out.println("  - CloudEvent: Kafka message key model");
        System.out.println("  - ProduceResult/ConsumedResult: Operation results");
        System.out.println();

        // Verify key classes are available
        verifyClass("io.distia.probe.javaapi.ProbeJavaDsl", "ProbeJavaDsl");
        verifyClass("io.distia.probe.core.pubsub.models.CloudEvent", "CloudEvent");
        verifyClass("io.distia.probe.core.pubsub.models.ProduceResult", "ProduceResult");
        verifyClass("io.distia.probe.core.pubsub.models.ConsumedResult", "ConsumedResult");
        verifyClass("org.apache.pekko.actor.typed.ActorSystem", "Pekko ActorSystem");
        verifyClass("io.cucumber.java.en.Given", "Cucumber");

        System.out.println();
        System.out.println("See test-probe-core integration tests for full examples.");
        System.out.println("========================================");
    }

    private static void verifyClass(String className, String displayName) {
        try {
            Class.forName(className);
            System.out.println("[OK] " + displayName + " available");
        } catch (ClassNotFoundException e) {
            System.out.println("[ERROR] " + displayName + " not found: " + className);
        }
    }
}
