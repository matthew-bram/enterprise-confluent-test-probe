# ADR-REST-003: Apache Pekko HTTP 1.1.0 Technology Choice

## Status
**Accepted** - Implemented in Phase 1 (2025-10-19)

## Context

The Test Probe REST API needed an HTTP framework to serve REST endpoints. The framework must:
1. Integrate with Pekko Typed Actors (our actor system)
2. Support Scala 3.3.6 LTS
3. Provide production-ready features (backpressure, graceful shutdown, etc.)
4. Have Apache 2.0 license (no BSL restrictions)
5. Be actively maintained

**Background:** Test Probe uses Apache Pekko (fork of Akka under Apache Software Foundation) after Akka moved to BSL license. We needed an HTTP framework compatible with Pekko.

## Decision

**Adopt Apache Pekko HTTP 1.1.0** as the HTTP framework.

### Dependency

```xml
<!-- test-probe-interfaces/pom.xml -->
<dependency>
  <groupId>org.apache.pekko</groupId>
  <artifactId>pekko-http_3</artifactId>
  <version>1.1.0</version>
</dependency>

<dependency>
  <groupId>org.apache.pekko</groupId>
  <artifactId>pekko-http-spray-json_3</artifactId>
  <version>1.1.0</version>
</dependency>
```

### Key Features Used

**Route DSL:**
```scala
val routes: Route =
  pathPrefix("api" / "v1") {
    concat(
      path("health") {
        get {
          complete(StatusCodes.OK, healthResponse)
        }
      },
      path("test" / "initialize") {
        post {
          onComplete(functions.initializeTest()) {
            case Success(response) => complete(StatusCodes.Created, response)
            case Failure(ex) => complete(StatusCodes.InternalServerError, errorResponse)
          }
        }
      }
    )
  }
```

**Custom Exception/Rejection Handlers:**
```scala
handleExceptions(exceptionHandler) {
  handleRejections(rejectionHandler) {
    // Routes
  }
}
```

**JSON Marshalling (Spray JSON):**
```scala
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

case class RestStartTestRequest(
  `test-id`: UUID,
  `block-storage-path`: String,
  `test-type`: Option[String]
)

object RestStartTestRequest {
  implicit val format: RootJsonFormat[RestStartTestRequest] =
    jsonFormat3(RestStartTestRequest.apply)
}
```

**Graceful Shutdown:**
```scala
binding.whenTerminated.onComplete { _ =>
  logger.info("HTTP server terminated")
  system.terminate()
}
```

## Alternatives Considered

### Alternative 1: http4s

**Example:**
```scala
import org.http4s._
import org.http4s.dsl.io._

val routes = HttpRoutes.of[IO] {
  case GET -> Root / "api" / "v1" / "health" =>
    Ok(healthResponse)
}
```

**Pros:**
- Pure functional (Cats Effect)
- Type-safe
- Active development

**Cons:**
- Not designed for Pekko (uses Cats Effect, not actor system)
- No built-in Pekko actor integration
- Different async model (IO monad vs Future)
- Learning curve for team (functional programming)
- Extra dependencies (Cats Effect, FS2)

**Rejected:** Mismatch with Pekko actor system, functional programming overhead.

### Alternative 2: Play Framework

**Example:**
```scala
import play.api.mvc._

class HealthController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  def health() = Action {
    Ok(Json.toJson(healthResponse))
  }
}
```

**Pros:**
- Full-stack framework (routing, templating, etc.)
- Active development
- Good Scala 3 support

**Cons:**
- Heavy (includes DB, ORM, templating - don't need)
- Opinionated (Play way or highway)
- Uses Play's actor system (not Pekko)
- Overkill for REST API (no UI, no DB layer needed)
- License uncertainty (Lightbend)

**Rejected:** Too heavy, doesn't integrate with Pekko actors.

### Alternative 3: Akka HTTP (Stay on Akka)

**Example:**
```scala
import akka.http.scaladsl.server.Directives._

val routes = path("health") {
  get {
    complete(StatusCodes.OK, healthResponse)
  }
}
```

**Pros:**
- Same as Pekko HTTP (it's the original)
- Mature, well-tested
- Excellent documentation

**Cons:**
- BSL license (Business Source License) since Akka 2.7+
- Not open source (Apache 2.0 license required)
- Lightbend commercial restrictions
- Akka maintenance uncertain under BSL

**Rejected:** License restrictions (BSL), prefer Apache 2.0.

### Alternative 4: Vert.x

**Example:**
```java
vertx.createHttpServer()
  .requestHandler(request -> {
    if (request.path().equals("/health")) {
      request.response()
        .putHeader("content-type", "application/json")
        .end(healthResponseJson);
    }
  })
  .listen(8080);
```

**Pros:**
- High performance
- Event-driven
- Polyglot (Java, Scala, Kotlin)

**Cons:**
- Not idiomatic Scala (feels like Java)
- No integration with Pekko actors
- Callback hell (not Future-based)
- Less type-safe

**Rejected:** Not Scala-idiomatic, no Pekko integration.

### Alternative 5: Spring Boot (Kotlin/Java)

**Rejected immediately:** Not Scala, can't use Pekko Typed Actors.

## Consequences

### Positive

1. **Apache 2.0 License:** No commercial restrictions
   - Can use in any project (open source, commercial)
   - No BSL restrictions

2. **Pekko Integration:** Seamless actor system integration
   ```scala
   implicit val system: ActorSystem[_] = // Pekko actor system
   val routes = new RestRoutes(serviceFunctions).routes
   Http().newServerAt("0.0.0.0", 8080).bind(routes)
   ```

3. **Production-Ready:** Battle-tested features
   - Backpressure (limits concurrent requests)
   - Graceful shutdown (waits for in-flight requests)
   - HTTP/2 support
   - WebSocket support (future use)

4. **Scala 3 Support:** Official Scala 3.3.6 artifacts
   ```xml
   <artifactId>pekko-http_3</artifactId>  <!-- _3 = Scala 3 -->
   ```

5. **Route DSL:** Expressive, type-safe routing
   ```scala
   path("test" / JavaUUID / "status") { testId =>
     get {
       // testId is UUID (type-safe)
     }
   }
   ```

6. **Active Development:** Apache Software Foundation maintains Pekko
   - Regular releases
   - Security updates
   - Community support

### Negative

1. **Smaller Community:** Pekko community smaller than Akka (newer fork)
   - Mitigation: Akka HTTP documentation still applies (same API)

2. **Spray JSON:** Less powerful than Circe
   - Mitigation: Spray JSON sufficient for REST DTOs, Circe used for actor messages

3. **Learning Curve:** Route DSL takes time to learn
   - Mitigation: Excellent documentation, similar to Akka HTTP

### Neutral

1. **Fork of Akka HTTP:** Same API, different package name
   - Pro: Akka HTTP documentation applies
   - Con: Search results often show Akka HTTP (need to translate packages)

2. **Requires Actor System:** Pekko HTTP needs actor system
   - Pro: We already have one (Pekko Typed Actors)
   - Con: Can't use without actor system

## Migration Path

**If we need to migrate:**

**To Akka HTTP (if BSL acceptable):**
```scala
// Change dependencies
-org.apache.pekko:pekko-http
+com.typesafe.akka:akka-http

// Change imports
-import org.apache.pekko.http.scaladsl._
+import akka.http.scaladsl._
```

**To http4s (if functional programming required):**
- Significant rewrite (Route DSL → http4s DSL)
- Change async model (Future → IO monad)
- Replace Spray JSON with Circe

**To Play (if full-stack needed):**
- Significant rewrite (routes → controllers)
- Change project structure (Play conventions)

## Implementation

### Files

**HTTP Server Bootstrap:** `/test-probe-interfaces/src/main/scala/io/distia/probe/interfaces/bootstrap/InterfacesBootstrap.scala`

**Routes:** `/test-probe-interfaces/src/main/scala/io/distia/probe/interfaces/rest/RestRoutes.scala` (256 lines)

**Configuration:** `/test-probe-interfaces/src/main/resources/reference.conf` (Pekko HTTP settings)

### Dependencies

```xml
<!-- Pekko HTTP -->
<dependency>
  <groupId>org.apache.pekko</groupId>
  <artifactId>pekko-http_3</artifactId>
  <version>1.1.0</version>
</dependency>

<!-- Spray JSON (marshalling) -->
<dependency>
  <groupId>org.apache.pekko</groupId>
  <artifactId>pekko-http-spray-json_3</artifactId>
  <version>1.1.0</version>
</dependency>

<!-- Pekko Actor (required by Pekko HTTP) -->
<dependency>
  <groupId>org.apache.pekko</groupId>
  <artifactId>pekko-actor-typed_3</artifactId>
  <version>1.1.2</version>
</dependency>

<!-- Test kit -->
<dependency>
  <groupId>org.apache.pekko</groupId>
  <artifactId>pekko-http-testkit_3</artifactId>
  <version>1.1.0</version>
  <scope>test</scope>
</dependency>
```

## Related Decisions

- [ADR-001: FSM Pattern](001-fsm-pattern-for-test-execution.md) - Pekko Typed Actors used for FSM
- [ADR-004: Factory Injection](004-factory-injection-for-child-actors.md) - Pekko actor factory pattern
- [Pekko HTTP Migration Decision](https://github.com/apache/pekko-http/blob/main/docs/src/main/paradox/migration-guide.md)

## References

- [Apache Pekko HTTP](https://pekko.apache.org/docs/pekko-http/current/)
- [Pekko HTTP vs Akka HTTP](https://pekko.apache.org/docs/pekko-http/current/migration-guide/migration-from-akka.html)
- [Apache Software Foundation](https://www.apache.org/)
- [BSL License Analysis](https://www.lightbend.com/akka/license-faq)

---

**Date:** 2025-10-19
**Deciders:** Test Probe Architecture Team
**Status:** Implemented (Phase 1)
**Version:** Pekko HTTP 1.1.0, Scala 3.3.6
