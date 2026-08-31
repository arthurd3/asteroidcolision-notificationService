[← HTTP clients and resilience](03-http-clients-and-resilience.md) · **English** · [Português (Brasil)](../pt-BR/04-event-driven-kafka.md)

# Event-driven with Kafka

Two ideas carry this chapter: **idempotency**, which makes re-running safe, and
**dead-lettering**, which stops one bad message from halting everything behind it.

## The contract

One record, in its own module, depended on by both services:

```java
public record AsteroidCollisionEvent(
        @NotBlank String eventId,
        @NotNull Instant occurredAt,
        @NotBlank String asteroidId,
        @NotBlank String asteroidName,
        @NotNull LocalDate closeApproachDate,
        @NotNull @Positive BigDecimal missDistanceKilometers,
        @Positive double estimatedDiameterAvgMeters
) {}
```

Because both sides compile against it, the schema cannot drift. Change a component and
both stop compiling — which is the point.

`@JsonIgnoreProperties(ignoreUnknown = true)` is what lets a v1 consumer keep working
against a producer that has started sending an extra field. **Adding** a field is
backward compatible. **Removing or retyping** one is not, and needs a `v2` record
*beside* v1 rather than an edit to it.

## The alias in the header

Both services configure the same type mapping:

```yaml
spring.json.type.mapping: >-
  asteroid-collision.v1:com.arthur.asteroid.contracts.v1.AsteroidCollisionEvent
```

Without this, Spring's JSON serializer writes the producer's **fully-qualified class
name** into the `__TypeId__` header, and the consumer looks up that exact class. The
two services are then coupled through their package structure: rename a package on the
producer and every message already sitting in the topic becomes unreadable.

With it, the alias `asteroid-collision.v1` travels instead. Either side can move its
packages freely. The consumer additionally restricts `spring.json.trusted.packages` to
`com.arthur.asteroid.contracts.v1` — widening that to accept a producer FQCN would
reintroduce exactly the coupling the alias removes.

`AsteroidAlertConsumeIT` builds its producer with these settings on purpose, so the
alias contract is covered rather than assumed.

## Idempotency: why re-running is safe

This is the project's central claim, and it needs two mechanisms because there are two
independent sources of duplication.

**Duplication one: overlapping scans.** Each scan covers a rolling window — today
through seven days ahead. Run it twice in a day and the same approach is legitimately
reported twice. That is correct behaviour by the producer, not a bug.

**Duplication two: Kafka is at-least-once.** A consumer that processes a message and
dies before committing its offset will see that message again.

The answer to both is the same: make the event's identity a function of *what it
describes* rather than *when it was produced*.

```java
private static String eventId(final String asteroidId, final LocalDate closeApproachDate) {
    final String businessKey = asteroidId + ':' + closeApproachDate;
    return UUID.nameUUIDFromBytes(businessKey.getBytes(StandardCharsets.UTF_8)).toString();
}
```

A random UUID would make every re-scan look like a new event, and the consumer would
store and email it again. Deriving the id from asteroid + approach date means the
second scan produces a byte-identical event.

The consumer then has two layers:

- `NotificationIngestService.ingest()` short-circuits on `existsByEventId(...)`;
- the database has `uk_notification_event_id`, so even a race between two instances
  ends in a constraint violation rather than a duplicate row.

The application check is the fast path; the constraint is the one that is actually
true. A second constraint, `uk_delivery_notification_subscriber`, prevents double
fan-out to the same recipient.

The message key is the asteroid id, so all approaches for one object land on the same
partition and keep their relative order.

## Failure handling: the dead-letter topic

A message that cannot be deserialised will never succeed, however many times it is
retried. Without somewhere to put it, the consumer retries forever and **every message
behind it on that partition stops being processed** — a silent, total halt of one
third of the pipeline.

`KafkaErrorHandlingConfig` combines:

- `ExponentialBackOffWithMaxRetries(4)`, 500 ms initial, ×2, capped at 10 s — for
  transient failures like a database blip;
- a `DeadLetterPublishingRecoverer` routing to `record.topic() + ".DLT"`, preserving
  the original partition;
- `DeserializationException` and `MessageConversionException` registered as **not
  retryable**, because retrying a malformed payload is pure waste.

### Two subtleties that cost real time

**The DLT producer needs `DelegatingByTypeSerializer`.** A dead-lettered record carries
one of two very different payloads: a message that failed *deserialization* is
forwarded as the original raw `byte[]`, while one that deserialized fine but blew up in
the listener is forwarded as the object. With a plain `StringSerializer` the first case
fails with `Can't convert value of class [B`, the recoverer throws, the offset is never
committed, and the container re-reads the same poison message forever — precisely the
failure the DLT exists to prevent.

**The DLT producer must use `KafkaConnectionDetails`, not `KafkaProperties` alone.**
This was a real bug in this repository, found by writing `AsteroidAlertConsumeIT`.
Boot's own Kafka auto-configuration calls `buildProducerProperties()` **and then**
applies `KafkaConnectionDetails` on top. Connection details are how a broker address
arrives from somewhere other than the YAML — a Testcontainers `@ServiceConnection`,
Docker Compose support, a cloud binding. Building a factory from the properties alone
keeps whatever `spring.kafka.bootstrap-servers` happens to say.

The symptom is nasty: the consumer connects and reads perfectly, the error handler
correctly identifies the poison message, and then dead-lettering fails with
`Topic asteroid-alert.DLT not present in metadata after 60000 ms` — after which the
unrecovered record blocks its partition and everything behind it silently stops.

**The `.DLT` topic must be declared.** The broker runs with
`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`, so without the `NewTopic` bean in
`KafkaTopicConfig` the recoverer would fail on `UNKNOWN_TOPIC_OR_PARTITION`. The
consumer declares its own DLT — the producer has no reason to know a consumer failed.

## Producer settings

```yaml
acks: all
retries: 10
properties:
  enable.idempotence: true
  max.in.flight.requests.per.connection: 5
  delivery.timeout.ms: 120000
```

`acks: all` waits for every in-sync replica. `enable.idempotence: true` is what makes
`retries: 10` safe — without it a retried send can produce a duplicate on the broker,
so retrying trades one problem for another.

## Try it yourself

```bash
docker compose up -d

# publish the same scan twice and watch nothing change the second time
curl -X POST localhost:8080/api/v1/asteroid-alerting/alert
curl -s localhost:8081/api/v1/notifications | python3 -c "import json,sys; print('alerts:', json.load(sys.stdin)['totalElements'])"

curl -X POST localhost:8080/api/v1/asteroid-alerting/alert
curl -s localhost:8081/api/v1/notifications | python3 -c "import json,sys; print('alerts:', json.load(sys.stdin)['totalElements'])"
```

The two counts are identical. Then watch the messages in Kafka UI at
<http://localhost:8084> — the topic has twice as many records as the database has rows,
which is exactly the point.

To see dead-lettering, publish something malformed:

```bash
docker exec -it asteroid-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic asteroid-alert
> not json at all
```

It appears on `asteroid-alert.DLT` within a few seconds, and the next valid message
still gets processed. `AsteroidAlertConsumeIT` asserts all three behaviours without
needing you to type any of this.
