package io.distia.probe
package common
package validation

import scala.util.matching.Regex

import models.TopicDirective

/**
 * TopicDirectiveValidator - Validation utilities for TopicDirective instances
 *
 * Provides validation functions for ensuring TopicDirective instances meet
 * business rules and format requirements before being processed by the framework.
 *
 * **Validation Rules**:
 * 1. **Uniqueness**: Each topic name must be unique within a BlockStorageDirective
 * 2. **Bootstrap Servers Format**: Optional bootstrapServers must follow "host:port,host:port" format
 *
 * **Design Pattern**: Pure functions returning Either[Error, Success] for composable validation
 *
 * **Usage Example**:
 * {{{
 * val directives: List[TopicDirective] = ...
 *
 * // Validate uniqueness
 * TopicDirectiveValidator.validateUniqueness(directives) match {
 *   case Right(()) => // All topics unique, proceed
 *   case Left(errors) => // Handle duplicate topics
 * }
 *
 * // Validate bootstrap servers format
 * val directive = TopicDirective(
 *   topic = "orders.events",
 *   role = "producer",
 *   clientPrincipal = "kafka-producer",
 *   eventFilters = List.empty,
 *   bootstrapServers = Some("broker1:9092,broker2:9092")
 * )
 *
 * TopicDirectiveValidator.validateBootstrapServersFormat(directive.bootstrapServers) match {
 *   case Right(()) => // Format valid, proceed
 *   case Left(error) => // Handle format error
 * }
 * }}}
 *
 * @see io.distia.probe.common.models.TopicDirective for validated model
 * @see io.distia.probe.common.exceptions.DuplicateTopicException for uniqueness violations
 * @see io.distia.probe.common.exceptions.InvalidBootstrapServersException for format violations
 */
object TopicDirectiveValidator {

  /**
   * Regular expression pattern for validating "host:port" format
   *
   * **Pattern Rules**:
   * - Host: alphanumeric + hyphens + dots, must start/end with alphanumeric
   * - Port: 1-5 digit number (validated separately for 1-65535 range)
   *
   * **Valid Examples**: "broker:9092", "kafka-1.example.com:9092", "10.0.1.5:9092"
   * **Invalid Examples**: "-broker:9092", "broker.:9092", "broker"
   */
  val HOST_PORT_PATTERN: Regex = "^[a-zA-Z0-9]([a-zA-Z0-9\\-\\.]*[a-zA-Z0-9])?:\\d{1,5}$".r

  /**
   * Validate that all topic names are unique within a list of TopicDirective instances
   *
   * **Business Rule**: Each topic name must appear exactly once. Duplicate topic names
   * are not allowed because they would cause ambiguity in Kafka producer/consumer setup.
   *
   * @param directives List of TopicDirective instances to validate
   * @return Right(()) if all topics unique, Left(error messages) if duplicates found
   */
  def validateUniqueness(directives: List[TopicDirective]): Either[List[String], Unit] = {
    val duplicates: Map[String, Int] = directives
      .map(_.topic)
      .groupBy(identity)
      .view
      .mapValues(_.size)
      .filter(_._2 > 1)
      .toMap

    if duplicates.isEmpty then Right(())
    else Left(duplicates.map { case (topic, count) =>
      s"Topic '$topic' appears $count times - topic names must be unique"
    }.toList.sorted)
  }

  /**
   * Validate bootstrap servers format for optional override
   *
   * **Format Requirements**:
   * - Comma-separated list: "host1:port1,host2:port2,host3:port3"
   * - Each entry must match HOST_PORT_PATTERN
   * - Port must be in valid range (1-65535)
   * - No spaces, semicolons, or other delimiters
   *
   * **Valid Examples**:
   * - None (no override, uses default bootstrap servers)
   * - Some("broker:9092")
   * - Some("broker1:9092,broker2:9092,broker3:9092")
   *
   * **Invalid Examples**:
   * - Some("") (empty string not allowed)
   * - Some("broker:9092; broker2:9092") (semicolon delimiter)
   * - Some("broker") (missing port)
   * - Some("broker:99999") (port out of range)
   *
   * @param bootstrapServers Optional bootstrap servers string to validate
   * @return Right(()) if format valid or None, Left(error message) if invalid
   */
  def validateBootstrapServersFormat(bootstrapServers: Option[String]): Either[String, Unit] = bootstrapServers match {
    case None => Right(())
    case Some(servers) if servers.trim.isEmpty =>
      Left("Bootstrap servers cannot be empty when specified")
    case Some(servers) =>
      val entries: Array[String] = servers.split(",").map(_.trim)
      val invalidEntries: Array[String] = entries.filterNot(isValidHostPort)
      if invalidEntries.isEmpty then Right(())
      else Left(s"Invalid bootstrap server format: ${invalidEntries.mkString(", ")}. Expected format: host:port or host1:port1,host2:port2")
  }

  /**
   * Validate a single "host:port" entry
   *
   * **Validation Steps**:
   * 1. Check against HOST_PORT_PATTERN regex (alphanumeric host, numeric port)
   * 2. Validate port is in valid range (1-65535)
   *
   * @param entry Single "host:port" string to validate
   * @return true if valid, false otherwise
   */
  def isValidHostPort(entry: String): Boolean =
    HOST_PORT_PATTERN.matches(entry) && {
      val port: Int = entry.split(":").last.toInt
      port > 0 && port <= 65535
    }
}
