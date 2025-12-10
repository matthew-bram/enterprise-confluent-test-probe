package io.distia.probe.external.rest

import io.distia.probe.external.fixtures.RestClientFixtures
import io.distia.probe.external.fixtures.RestClientFixtures.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.reflect.ClassTag

class RestClientObjectMapperSpec extends AnyWordSpec with Matchers:

  "RestClientObjectMapper.mapper" should:

    "be lazily initialized" in:
      // Accessing mapper should not throw
      val mapper = RestClientObjectMapper.mapper
      mapper should not be null

    "serialize case class to JSON" in:
      val order = OrderRequest("prod-123", 5)
      val json = RestClientObjectMapper.mapper.writeValueAsString(order)

      json should include("prod-123")
      json should include("5")

    "deserialize JSON to case class" in:
      val json = """{"productId":"prod-456","quantity":10}"""
      val order = RestClientObjectMapper.mapper.readValue(json, classOf[OrderRequest])

      order.productId shouldBe "prod-456"
      order.quantity shouldBe 10

    "deserialize JSON with unknown properties without failing" in:
      val json = """{"productId":"prod-789","quantity":3,"unknownField":"ignored"}"""
      val order = RestClientObjectMapper.mapper.readValue(json, classOf[OrderRequest])

      order.productId shouldBe "prod-789"
      order.quantity shouldBe 3

    "serialize dates as timestamps" in:
      import java.util.Date
      case class WithDate(timestamp: Date)

      val date = new Date(1700000000000L)
      val obj = WithDate(date)
      val json = RestClientObjectMapper.mapper.writeValueAsString(obj)

      json should include("1700000000000")

    "deserialize response objects" in:
      val json = """{"orderId":"order-1","status":"COMPLETE","total":150.50}"""

      val response = RestClientObjectMapper.mapper.readValue(json, classOf[OrderResponse])

      response.orderId shouldBe "order-1"
      response.status shouldBe "COMPLETE"
      response.total shouldBe 150.50

    "serialize response objects" in:
      val response = OrderResponse("order-1", "CREATED", 99.99)
      val json = RestClientObjectMapper.mapper.writeValueAsString(response)

      json should include("order-1")
      json should include("CREATED")
      json should include("99.99")

    "round-trip OrderRequest" in:
      val original = OrderRequest("product-xyz", 42)
      val json = RestClientObjectMapper.mapper.writeValueAsString(original)
      val parsed = RestClientObjectMapper.mapper.readValue(json, classOf[OrderRequest])

      parsed shouldBe original

    "round-trip OrderResponse" in:
      val original = OrderResponse("ord-123", "SHIPPED", 250.00)
      val json = RestClientObjectMapper.mapper.writeValueAsString(original)
      val parsed = RestClientObjectMapper.mapper.readValue(json, classOf[OrderResponse])

      parsed shouldBe original

    "round-trip ErrorResponse" in:
      val original = ErrorResponse("ERR_001", "Something went wrong")
      val json = RestClientObjectMapper.mapper.writeValueAsString(original)
      val parsed = RestClientObjectMapper.mapper.readValue(json, classOf[ErrorResponse])

      parsed shouldBe original
