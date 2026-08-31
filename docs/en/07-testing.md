[← The JSP front end](06-jsp-frontend.md) · **English** · [Português (Brasil)](../pt-BR/07-testing.md)

# Testing

Five techniques, each used where it is the cheapest thing that can actually prove the
claim. The interesting question is never "did we test it" but "what would have caught
this".

```bash
./mvnw test      # unit and slice tests, no Docker needed
./mvnw verify    # adds the integration tests, needs a Docker daemon
```

## Hand-rolled stubs, where an interface is small

`AsteroidAlertingServiceTest` uses no mocking framework at all. The publisher is a
subclass that records what it was given; the clock is `Clock.fixed(...)`.

Until recently `NasaNeoClient` had one method and could be a lambda:

```java
final NasaNeoClient client = (from, to) -> feed;
```

Adding `lookup` and `browse` ended that — three methods is not a functional interface —
so it became a small named class whose two unused methods **throw**:

```java
@Override
public Asteroid lookup(String neoReferenceId) {
    throw new UnsupportedOperationException("alerting never looks an object up");
}
```

Returning `null` or an empty list there would be a lie: it would let a future change
start calling `lookup` from the alerting path and have the test still pass.

## Mockito, where the collaborator is not small

`DeliveryDispatchServiceTest` and `NotificationQueryServiceTest` use Mockito, because
`NotificationDeliveryRepository` has a dozen inherited methods and hand-writing a stub
for it would be noise.

The rule this project follows: **hand-roll when the interface is one or two methods you
control; mock when it is somebody else's fat interface.**

## WireMock, where you need a real socket

Every NASA client test runs against a real HTTP server on a real port. That is not
ceremony — a mocked `RestClient` cannot demonstrate the things that actually go wrong:

- a 429 with a JSON error body,
- a truncated or malformed response,
- a connection refused outright,
- an empty body with a 200 status.

`MockRestServiceServer` cannot express those either; it asserts on requests, not on
transport behaviour.

Note the dependency:

```xml
<dependency>
  <groupId>org.wiremock</groupId>
  <artifactId>wiremock-standalone</artifactId>
  <version>3.13.2</version>
  <scope>test</scope>
</dependency>
```

**`wiremock-standalone`, the shaded jar, on purpose.** Plain `org.wiremock:wiremock`
needs Jetty 11, while Boot 4 manages Jetty 12, and the mismatch fails at startup with
"Jetty 11 is not present". The shaded jar carries its own server and sidesteps the
clash entirely.

### Fixtures, and which ones are real

`RestNasaDonkiClientTest` and `RestNasaEpicClientTest` parse recorded responses from
`src/test/resources/nasa/` rather than hand-written JSON. Writing JSON from
documentation is how a record ends up mapping fields that do not exist.

All four fixtures are live captures. `README-fixtures.md` in that directory records
that, and explains why provenance is worth writing down at all: `ignoreUnknown` makes an
*extra* field harmless but does nothing about a *missing* one, so a component whose name
does not match what NASA sends deserialises to `null` silently — and a test parsing a
hand-written fixture passes anyway. Two of these were hand-written for a while, for
exactly that reason, and the file says so. It also gives the commands to re-record them.

## `@WebMvcTest`, for the HTTP contract

Every controller has a slice test. They cover the shape of the response, the status
codes, and — importantly — the `problem+json` body:

```java
.andExpect(status().isBadRequest())
.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
.andExpect(jsonPath("$.title").value("Invalid scan window"))
.andExpect(jsonPath("$.type").value("https://asteroid.arthur.com/problems/invalid-scan-window"))
```

A slice does not run `@ConfigurationPropertiesScan` or `ClockConfig`, so tests supply
those by hand in a nested `@TestConfiguration`.

**In `web-ui`, a slice test cannot assert HTML** — there is no servlet container, so
MockMvc only reports the forward. Assert the view name and the model; leave rendering
to `WebUiPagesIT`.

## Testcontainers, for anything the database or broker decides

Three integration tests, run by Failsafe under `./mvnw verify`.

**`NotificationPersistenceIT`** — real MySQL 8.4. It covers the things an in-memory
database would not reproduce faithfully: the unique constraints that make ingest
idempotent, `DECIMAL(20,4)` keeping its fractional kilometres, `SKIP LOCKED` respecting
a batch limit, and the projection query surviving `entityManager.clear()` — which is
what proves `open-in-view: false` is handled rather than accidentally working.

**`AsteroidAlertConsumeIT`** — real Kafka *and* real MySQL. The pipeline's most
important behaviours had no test at all before this: the README's central claim about
idempotency, and the retry-then-dead-letter route. Writing it found a real production
bug in `DeadLetterProducerConfig`, described in [document 04](04-event-driven-kafka.md).

**`NasaCacheIT`** — an integration test rather than a unit test because `@Cacheable` is
proxy-based: calling a client directly bypasses the advice, and a unit test would pass
whether or not caching worked. Its most valuable case is
`donkiEndpointsDoNotShareACache`, which guards against a silent bug —
`SimpleKeyGenerator` keys on arguments only, and DONKI's three endpoints share a
signature.

**`WebUiPagesIT`** — the only test that compiles the JSPs. Parameterised over every
page; asserts 200, `text/html`, and a per-page marker. Plus three specific claims: that
`<c:out>` really escapes, that the error page is reached rather than Boot's whitelabel,
and that no page contains `api_key`.

A note on Testcontainers 2.0, which trips people up:

```java
// the self-typed generic was dropped, so MySQLContainer<?> no longer compiles
static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
```

…and module artifact ids are now prefixed: `testcontainers-mysql`,
`testcontainers-kafka`. There are also three Kafka container classes; the KRaft-native
`org.testcontainers.kafka.KafkaContainer` is the one that matches the broker in
`docker-compose.yml`.

## The test that guards a string

`NasaConfigurationTest` is unusual and worth copying. Resilience4j's
`retry-exceptions` names a class by **fully-qualified string** in YAML. Move or rename
that class and retry silently stops working — no error, no warning.

So the test loads the real YAML, pulls the registries, and asserts the predicate still
matches. It turns an invisible coupling into a failing build.

Every project has a few couplings the compiler cannot see. Finding them and pinning
them with one test each is disproportionately valuable.

## What Failsafe needed

The parent POM activates it by hand:

```xml
<plugin>
  <artifactId>maven-failsafe-plugin</artifactId>
  <executions><execution><goals>
    <goal>integration-test</goal><goal>verify</goal>
  </goals></execution></executions>
</plugin>
```

`spring-boot-starter-parent` only *manages* Failsafe's version; it does not bind its
goals. Without this every `*IT` class is silently skipped and the build still reports
success — which is worse than having no integration tests at all, because it looks like
you have them.

## Try it yourself

```bash
./mvnw test                                   # fast, no Docker
./mvnw verify                                 # everything, needs Docker

# a single integration test
./mvnw -pl notification-service verify -Dtest='!*' -Dit.test=AsteroidAlertConsumeIT

# prove the FQCN guard works: change retry-exceptions in
# asteroid-service/src/main/resources/application.yaml to a class that does not
# exist, then run
./mvnw -pl asteroid-service test -Dtest=NasaConfigurationTest
```

The last one fails, which is the entire point of it existing.
