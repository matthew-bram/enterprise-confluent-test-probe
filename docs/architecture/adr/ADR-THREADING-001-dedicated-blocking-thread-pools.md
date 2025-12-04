# ADR-THREADING-001: Dedicated Blocking Thread Pools for I/O Operations

**Status**: Accepted
**Date**: 2025-10-23
**Component**: VaultActor, BlockStorageActor, Service Layer
**Related ADRs**: ADR-VAULT-001

---

## Context

The test-probe framework integrates with external blocking I/O services:
- **Vault Services**: AWS Lambda SDK, Azure Functions HTTP, GCP Cloud Functions
- **Block Storage Services**: AWS S3 SDK, Azure Blob Storage HTTP, GCP Storage

These services use **synchronous/blocking Java SDKs** that block threads while waiting for I/O:

```scala
// AWS Lambda SDK (synchronous/blocking)
val response: InvokeResponse = lambdaClient.invoke(request)  // Blocks thread 1-5 seconds

// AWS S3 SDK (synchronous/blocking)
val s3Object: S3Object = s3Client.getObject(request)  // Blocks thread 1-5 seconds
```

### The Threading Challenge

**Problem**: Mixing blocking I/O with Pekko's default ForkJoinPool ExecutionContext causes:
- **Thread starvation**: All threads blocked on I/O, no threads for message processing
- **Deadlocks**: Actor asks blocked waiting for Future that needs a thread to complete
- **Poor throughput**: Limited parallelism (ForkJoin optimized for CPU work, not I/O waits)

**Naive Approach** (❌ Anti-pattern):
```scala
implicit val ec: ExecutionContext = actorSystem.executionContext  // ForkJoinPool

def invokeVault(...): Future[Response] = Future {
  blocking {  // Tells pool "I'm blocking", pool spawns compensating threads
    lambdaClient.invoke(request)  // Blocks thread
  }
}
```

**Problems with `blocking { }`**:
- Spawns compensating threads on-demand (overhead)
- No upper bound on thread creation (can exhaust resources)
- Not ideal for sustained high-throughput I/O
- Couples threading strategy to Scala's BlockContext mechanism

---

## Decision

**We use dedicated blocking thread pools** for all external I/O operations (Vault, Block Storage).

### Architecture

**VaultActor and BlockStorageActor create dedicated ExecutionContexts**:

```scala
private[core] object VaultActor {

  val servicesEc: ExecutionContext = context.system.dispatchers.lookup(
    DispatcherSelector.fromConfig("pekko.actor.services-dispatcher")
  )

  // Blocking CSP I/O operations run on services-dispatcher thread pool
  val securityDirectives: Future[List[KafkaSecurityDirective]] = vaultFunctions
    .fetchSecurityDirectives(blockStorageDirective)(using servicesEc)
}
```

**Service layer accepts implicit ExecutionContext**:

```scala
class AwsLambdaClient(region: String)(implicit ec: ExecutionContext) {

  // No blocking { } needed - caller provides appropriate thread pool
  def invoke(arn: String, payload: String): Future[(Int, Option[String])] = Future {
    val response = lambdaClient.invoke(request)  // Blocks, but on dedicated I/O pool
    (response.statusCode(), extractBody(response))
  }
}
```

**Call flow**:
```
VaultActor (implicit ec = blockingDispatcher)
   ↓
AwsVaultService.invokeVault()(implicit ec)
   ↓
AwsLambdaClient.invoke()(implicit ec)  // Future runs on blockingDispatcher
   ↓
lambdaClient.invoke()  // Blocks thread from CachedThreadPool, not ForkJoinPool
```

---

## Rationale

### Why Dedicated Thread Pools?

**1. Predictable Resource Allocation**
- CachedThreadPool grows/shrinks based on I/O demand
- Isolated from actor message processing threads
- No accidental starvation of critical actor work

**2. Performance**
- CachedThreadPool ideal for I/O waits (creates threads on-demand, reuses idle threads)
- ForkJoinPool ideal for CPU work (work-stealing, optimized for compute)
- Each pool optimized for its workload

**3. Clarity**
- Explicit threading model visible in code
- No hidden `blocking { }` magic
- Easier to monitor and tune (named thread pools)

**4. Flexibility**
- Can tune pool independently (thread count, keep-alive, etc.)
- Can switch to different pool types (FixedThreadPool, custom) without code changes

### Why NOT `blocking { }`?

**`blocking { }` is appropriate when**:
- You have occasional blocking calls in otherwise async code
- You're using a library you don't control
- Thread pool spawning compensation is acceptable overhead

**We have**:
- Sustained high-volume blocking I/O (vault/storage on every test)
- Full control over actor ExecutionContext
- Need for explicit resource management

Therefore, dedicated thread pools are the better architectural choice.

---

## Consequences

### Positive

✅ **Clear separation of concerns**: I/O threads isolated from actor processing
✅ **Better performance**: CachedThreadPool scales naturally with I/O demand
✅ **Monitoring**: Named threads (`vault-io-1`, `storage-io-2`) visible in thread dumps
✅ **Testability**: Can inject mock ExecutionContext for deterministic tests
✅ **No `blocking { }` clutter**: Service layer code is cleaner

### Negative

⚠️ **More verbose actor setup**: Must create and manage thread pools
⚠️ **Resource management**: Must ensure pools are shut down properly
⚠️ **Risk of misconfiguration**: Could use wrong EC if not careful

### Migration Path

If we later need to switch from dedicated pools back to `blocking { }`:

1. Remove dedicated ExecutionContext from actors
2. Add `blocking { }` wrapper in service layer methods:
   ```scala
   def invoke(...): Future[(Int, Option[String])] = Future {
     blocking {  // Add this
       val response = lambdaClient.invoke(request)
       (response.statusCode(), body)
     }
   }
   ```

This is a **localized change** (only service layer), actors remain unchanged.

---

## Implementation

### Actors with Blocking I/O

- **VaultActor**: `vault-io-*` thread pool for AWS/Azure/GCP vault calls
- **BlockStorageActor**: `storage-io-*` thread pool for S3/Azure/GCP storage calls

### Service Layer

All service layer classes accept `implicit ec: ExecutionContext`:

```scala
class AwsLambdaClient(region: String)(implicit ec: ExecutionContext)
class AwsS3Client(region: String)(implicit ec: ExecutionContext)
class ServicesHttpClient()(implicit actorSystem: ActorSystem)  // Uses system dispatcher
```

**Rule**: Service layer **never** creates threads or ExecutionContexts - always uses implicit.

---

## Alternatives Considered

### Alternative 1: Use `blocking { }` everywhere

**Rejected**:
- Thread spawning overhead on every I/O call
- Less explicit resource management
- Harder to monitor (unnamed threads)

### Alternative 2: Use Pekko Dispatcher Configuration

```hocon
pekko.actor.deployment {
  /vault-actor {
    dispatcher = "blocking-io-dispatcher"
  }
}

blocking-io-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor {
    fixed-pool-size = 32
  }
}
```

**Rejected**:
- Less flexible (requires config changes, not code)
- Harder to test (need full actor system config)
- Not as clear in code what's happening

### Alternative 3: Use Virtual Threads (Java 21+)

```scala
implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(
  Executors.newVirtualThreadPerTaskExecutor()
)
```

**Future consideration**:
- Virtual threads would be ideal for this use case
- Would eliminate need for dedicated pools entirely
- Consider when moving to Java 21+ LTS

---

## References

- **Pekko Dispatchers**: https://pekko.apache.org/docs/pekko/1.0/typed/dispatchers.html
- **Scala BlockContext**: https://www.scala-lang.org/api/current/scala/concurrent/BlockContext.html
- **Java Virtual Threads**: https://openjdk.org/jeps/444

---

## Document History

| Date | Change | Author |
|------|--------|--------|
| 2025-10-23 | Initial decision: Dedicated thread pools for Vault/Storage I/O | Claude + Matt |

