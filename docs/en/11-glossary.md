[← Running locally](10-running-locally.md) · **English** · [Português (Brasil)](../pt-BR/11-glossary.md)

# Glossary

## Astronomy

**NEO — Near-Earth Object.** An asteroid or comet whose orbit brings it close to
Earth's. About 62,000 are catalogued.

**Potentially hazardous.** NASA's classification, not a prediction. It means the object
is large enough and its orbit passes close enough to Earth's to be worth tracking. This
project alerts on the flag; it does not compute it.

**Close approach.** A specific pass. One object has many, to several bodies — NeoWs
lookup returns approaches to Mercury, Venus and Mars as well as Earth.

**Miss distance.** How close it came, reported in four units. **Lunar distance (LD)** —
multiples of the average Earth–Moon distance — is the one people have intuition for.

**MOID — Minimum Orbit Intersection Distance.** How close the two *orbits* come,
regardless of where the bodies are. This is the number that decides "potentially
hazardous".

**Absolute magnitude (H).** Brightness at a standard distance, used as a proxy for
size. Lower is bigger.

**Orbit class.** The family an orbit belongs to — Apollo, Aten, Amor. NASA sends a
description with the code, which is why the record carries both.

**APOD.** Astronomy Picture of the Day. Published daily since 1995-06-16; about one day
a week it is a video.

**CME — Coronal Mass Ejection.** A cloud of plasma thrown off the Sun.

**Geomagnetic storm.** A disturbance of Earth's magnetic field, usually caused by an
arriving CME. Measured by the **Kp index**, 0–9; reported from 5, and 8–9 is where
power grids and satellites are affected.

**Solar flare.** A sudden brightening, classified A/B/C/M/X by X-ray intensity on a
**logarithmic** scale — an X flare is ten times an M.

**DSCOVR / EPIC.** A spacecraft at the Earth–Sun L1 point, about a million miles out,
and the camera on it that photographs the full disc of Earth a dozen times a day.

**DONKI.** Space Weather Database Of Notifications, Knowledge, Information.

## Distributed systems

**Event-driven.** Services communicate by publishing facts that already happened,
rather than by calling each other. The producer does not know who consumes.

**At-least-once delivery.** Kafka's guarantee: a message will be delivered, possibly
more than once. Consumers must be idempotent — which is why this project derives event
ids from business keys.

**Idempotent.** Doing it twice has the same effect as doing it once. Here: re-scanning
an overlapping window adds no rows and sends no duplicate emails.

**Topic / partition / offset.** A topic is a named log; it is split into partitions for
parallelism; an offset is a message's position in a partition. Messages with the same
key land on the same partition and keep their relative order.

**Consumer group.** A set of consumers sharing a topic's partitions. Each partition
goes to exactly one member.

**DLT — Dead-Letter Topic.** Where a message goes when it cannot be processed. Without
one, a poison message is retried forever and **blocks every message behind it on its
partition**.

**Poison message.** One that will never succeed however often it is retried — typically
malformed and undeserializable.

**Circuit breaker.** After enough failures it stops calling the upstream at all for a
while, failing fast instead of piling up doomed requests. Resilience4j calls the states
CLOSED, OPEN and HALF_OPEN.

**Bulkhead.** A cap on concurrent calls, so one slow dependency cannot occupy every
request thread. Named after ship compartments.

**Backpressure.** Refusing work you cannot do rather than queueing it indefinitely.
This project's bulkhead has `max-wait-duration: 0` for exactly this reason.

## Spring and Java

**Bean.** An object the Spring container creates and wires. Everything annotated
`@Component`, `@Service`, `@Controller`, `@Configuration` or returned from a `@Bean`
method.

**Auto-configuration.** Spring Boot configuring things based on what is on the
classpath. `@ConditionalOnMissingBean` is what lets you override it by simply declaring
your own.

**`@ConfigurationProperties`.** Binds YAML to a typed object, validated at startup — so
a missing setting fails the context with a readable message instead of a
`NullPointerException` on the first request.

**Constructor binding.** How records bind configuration. `@DefaultValue` supplies
defaults; nested records give properties structure.

**Slice test.** `@WebMvcTest`, `@DataJpaTest` and friends: a partial application
context with only the layer under test. Fast, but note **`@WebMvcTest` does not render
JSPs**.

**Testcontainers.** Real dependencies in Docker containers, started by the test. Used
here for MySQL and Kafka, because constraints and `SKIP LOCKED` do not behave the same
in an in-memory substitute.

**`@ServiceConnection`.** Points Spring at a Testcontainers container automatically —
and the mechanism behind the `KafkaConnectionDetails` bug described in
[document 04](04-event-driven-kafka.md).

**Open Session In View.** Keeping the Hibernate session open for the whole request.
Disabled here, which is why the read API returns records rather than entities.

**Projection.** Selecting into a DTO rather than loading an entity. A **constructor
expression** (`select new com.example.Dto(...)`) builds it inside the query.

**RFC 9457 / `ProblemDetail`.** The standard shape for HTTP error responses — `type`,
`title`, `status`, `detail`. Both backends emit it and `web-ui` decodes it to build its
error page.

**Anti-corruption layer.** A boundary that translates an external model into your own,
so an upstream change does not ripple through your code. `asteroid-service`'s NASA
package is one.

## JSP

**JSP / Jasper.** JavaServer Pages, and the Tomcat component that compiles a `.jsp`
into a servlet at first request.

**JSTL.** The standard tag library — `<c:out>`, `<c:forEach>`. In Jakarta EE the URIs
are `jakarta.tags.*`; the old `java.sun.com` ones no longer resolve.

**EL — Expression Language.** `${...}`. **It does not HTML-escape**, which is why every
dynamic value here goes through `<c:out>`.

**Static vs dynamic include.** `<%@ include %>` merges at translation time — one
compiled servlet. `<jsp:include>` dispatches at request time, every request.

**`.jspf`.** Convention for a JSP fragment: a file included into a page, not requested
on its own.

**WAR.** Web Application Archive. Required for JSP; Boot's executable JAR layout cannot
serve pages from a document root a JAR does not have.

## Try it yourself

Every term above appears in the code. Find them:

```bash
grep -rn "SKIP LOCKED\|idempoten\|bulkhead\|circuit" --include=*.java --include=*.yaml . | grep -v target | head -20
```
