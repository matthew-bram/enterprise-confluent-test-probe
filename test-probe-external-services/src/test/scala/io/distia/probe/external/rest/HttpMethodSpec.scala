package io.distia.probe.external.rest

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HttpMethodSpec extends AnyWordSpec with Matchers:

  "HttpMethod" should:

    "have all standard HTTP methods" in:
      HttpMethod.values should contain allOf (
        HttpMethod.GET,
        HttpMethod.POST,
        HttpMethod.PUT,
        HttpMethod.DELETE,
        HttpMethod.PATCH,
        HttpMethod.HEAD,
        HttpMethod.OPTIONS
      )

    "have exactly 7 methods" in:
      HttpMethod.values.length shouldBe 7

    "support valueOf for GET" in:
      HttpMethod.valueOf("GET") shouldBe HttpMethod.GET

    "support valueOf for POST" in:
      HttpMethod.valueOf("POST") shouldBe HttpMethod.POST

    "support valueOf for PUT" in:
      HttpMethod.valueOf("PUT") shouldBe HttpMethod.PUT

    "support valueOf for DELETE" in:
      HttpMethod.valueOf("DELETE") shouldBe HttpMethod.DELETE

    "support valueOf for PATCH" in:
      HttpMethod.valueOf("PATCH") shouldBe HttpMethod.PATCH

    "support valueOf for HEAD" in:
      HttpMethod.valueOf("HEAD") shouldBe HttpMethod.HEAD

    "support valueOf for OPTIONS" in:
      HttpMethod.valueOf("OPTIONS") shouldBe HttpMethod.OPTIONS

    "throw for invalid method name" in:
      an[IllegalArgumentException] should be thrownBy:
        HttpMethod.valueOf("INVALID")

    "have correct ordinal values" in:
      HttpMethod.GET.ordinal shouldBe 0
      HttpMethod.POST.ordinal shouldBe 1
      HttpMethod.PUT.ordinal shouldBe 2
      HttpMethod.DELETE.ordinal shouldBe 3
      HttpMethod.PATCH.ordinal shouldBe 4
      HttpMethod.HEAD.ordinal shouldBe 5
      HttpMethod.OPTIONS.ordinal shouldBe 6

    "support pattern matching" in:
      val method: HttpMethod = HttpMethod.POST
      val result = method match
        case HttpMethod.GET => "get"
        case HttpMethod.POST => "post"
        case HttpMethod.PUT => "put"
        case HttpMethod.DELETE => "delete"
        case HttpMethod.PATCH => "patch"
        case HttpMethod.HEAD => "head"
        case HttpMethod.OPTIONS => "options"

      result shouldBe "post"
