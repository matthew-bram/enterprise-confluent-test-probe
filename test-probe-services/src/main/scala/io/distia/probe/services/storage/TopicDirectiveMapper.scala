package io.distia.probe
package services
package storage

import common.models.{EventFilter, TopicDirective}
import common.exceptions.{DuplicateTopicException, InvalidBootstrapServersException, InvalidTopicDirectiveFormatException}
import common.validation.TopicDirectiveValidator

import io.circe.yaml.parser
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.semiauto._

/**
 * TopicDirectiveMapper - YAML parser for Kafka topic directives
 *
 * Parses and validates topic directive YAML files that define Kafka topics and event filters
 * for test execution. Topic directives specify which Kafka topics to produce to/consume from,
 * optional bootstrap servers per topic, and event filters for consuming specific event types.
 *
 * YAML Format:
 * {{{
 * topics:
 *   - topic: "my-topic"
 *     bootstrapServers: "localhost:9092"  # optional
 *     eventFilters:                        # optional
 *       - key: "OrderCreated"
 *         value: "v1.0"
 * }}}
 *
 * Validation:
 * - Topic uniqueness (no duplicate topic names)
 * - Bootstrap servers format (comma-separated host:port pairs)
 *
 * Thread Safety: This class is thread-safe. All operations are stateless and use immutable
 * data structures. Multiple threads can safely parse different YAML documents concurrently.
 *
 * @see TopicDirective for the model class
 * @see TopicDirectiveValidator for validation rules
 * @see EventFilter for event filtering configuration
 */
private[services] class TopicDirectiveMapper {

  given eventFilterDecoder: Decoder[EventFilter] = (c: HCursor) =>
    for {
      eventType <- c.downField("key").as[String]
      payloadVersion <- c.downField("value").as[String]
    } yield EventFilter(eventType, payloadVersion)

  given eventFilterEncoder: Encoder[EventFilter] = (filter: EventFilter) =>
    Json.obj(
      "key" -> Json.fromString(filter.eventType),
      "value" -> Json.fromString(filter.payloadVersion)
    )

  implicit val topicDirectiveDecoder: Decoder[TopicDirective] = deriveDecoder[TopicDirective]

  case class TopicsWrapper(topics: List[TopicDirective])
  implicit val topicsWrapperDecoder: Decoder[TopicsWrapper] = deriveDecoder[TopicsWrapper]

  /**
   * Parses and validates YAML content into topic directives.
   *
   * @param yamlContent the YAML string to parse
   * @return List of validated TopicDirective objects
   * @throws InvalidTopicDirectiveFormatException if YAML parsing or decoding fails
   * @throws DuplicateTopicException if duplicate topic names are found
   * @throws InvalidBootstrapServersException if bootstrap servers format is invalid
   */
  def parse(yamlContent: String): List[TopicDirective] = parser.parse(yamlContent) match {
    case Right(json) => json.as[TopicsWrapper] match {
      case Right(wrapper) =>
        val directives: List[TopicDirective] = wrapper.topics
        validateDirectives(directives)
        directives
      case Left(error) =>
        throw new InvalidTopicDirectiveFormatException(
          s"Failed to decode topic directives: ${error.getMessage}",
          error
        )
    }
    case Left(error) =>
      throw new InvalidTopicDirectiveFormatException(
        s"Failed to parse YAML: ${error.getMessage}",
        error
      )
  }

  /**
   * Validates topic directives for uniqueness and format.
   *
   * @param directives the list of topic directives to validate
   * @throws DuplicateTopicException if duplicate topics are found
   * @throws InvalidBootstrapServersException if bootstrap servers format is invalid
   */
  def validateDirectives(directives: List[TopicDirective]): Unit = {
    TopicDirectiveValidator.validateUniqueness(directives) match {
      case Left(errors) =>
        throw new DuplicateTopicException(s"Duplicate topics found: ${errors.mkString("; ")}")
      case Right(_) => ()
    }

    directives.foreach { directive =>
      TopicDirectiveValidator.validateBootstrapServersFormat(directive.bootstrapServers) match {
        case Left(error) =>
          throw new InvalidBootstrapServersException(s"Invalid bootstrap servers for topic '${directive.topic}': $error")
        case Right(_) => ()
      }
    }
  }
}
