Hi Claude, Good Afternoon! We are going to create a new feature today!

This feature is a part of a larger epic that enables teams to make non-blocking
REST client calls from their step definitions. This feature will focus on:

1. Creating a new maven module called external services
2. Create a new builder module that implements the ProbeActorBehavior trait
3. The new builder module will be called DefaultRestClient
4. This will set the pattern for all external services teams will build
5. The basic concept for external services is that they present a set of typed actor
behaviors, that are passed to the DefaultActorSystem, and loaded behind an actor router.
We will not build the actor implementation in this feature. This includes registration to the ProbeScalaDsl.
6. Since 99% of REST client calls use JSON as the serialization mechanism for data, we
will want a smart way to perform marshalling and unmarshalling of requests and responses
into a type T.
7. T types will be Java POJOs (and Scala Case Classes) much like we are doing in the ProbeScala/JavaDsl(s).
8. The Behavior of the actor should receive a single type that acts as a strongly typed envelope
that contains the T, along with other properties such as the URI, headers, and other REST
client concerns. The envelope should be defined in the maven module. The ProbeScalaDsl should only deal
with "T" as the envelope. Again, ProbeScalaDsl is out of scope for this feature.
9. We should be using the Pekko Non-Blocking http client for our implementation.

We will take our time, not rush to start coding, making sure our plan is solid as this is a
major feature within a major epic. Please use ~/projects/temp-working/ for any documentation,
implementation plans, etc. you might need. 