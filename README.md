# Asteroid Collision Alerting

<img width="961" height="449" alt="architecture" src="https://github.com/user-attachments/assets/b270fcca-e7a3-4562-ab6e-4ea640e19d20" />

An event-driven pipeline over NASA's Near-Earth-Object feed. **asteroid-service**
scans the feed for potentially hazardous close approaches and publishes them to
Kafka; **notification-service** consumes those events, stores them, and emails
subscribers.

Java 21 · Spring Boot 4.1 · Kafka · MySQL · Flyway · Testcontainers

---

## Layout

```
.
├── contracts/              shared Kafka event contract (plain jar, no Spring)
├── asteroid-service/       NASA feed -> Kafka          (port 8080)
├── notification-service/   Kafka -> MySQL -> email     (port 8081)
└── docker-compose.yml      MySQL, Kafka (KRaft), Kafka UI
```

A Maven multi-module build. `contracts` holds the one definition of
`AsteroidCollisionEvent` that both services depend on, so the event schema cannot
drift between producer and consumer.

## Running it

**1. Configure.** Copy the example env file and fill it in:

```bash
cp .env.example .env
```

| Variable | Notes |
|---|---|
| `NASA_API_KEY` | Free key from [api.nasa.gov](https://api.nasa.gov). `DEMO_KEY` works but is capped at 30 requests/hour |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD` | Any values; compose and the app read the same ones |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP credentials — [Mailtrap](https://mailtrap.io) sandbox by default |

`.env` is git-ignored. Do not commit it.

**2. Start the infrastructure.**

```bash
docker compose up -d
docker compose ps          # wait for mysql and kafka to report healthy
```

**3. Build and run both services.**

```bash
./mvnw clean install

set -a; . ./.env; set +a          # export the variables into your shell
./mvnw -pl asteroid-service      spring-boot:run     # terminal 1
./mvnw -pl notification-service  spring-boot:run     # terminal 2
```

**4. Trigger a scan.**

```bash
curl -X POST localhost:8080/api/v1/asteroid-alerting/alert
# {"from":"2026-08-31","to":"2026-09-07","scanned":46,"hazardous":4,"published":4}
```

Optionally pass an explicit window (maximum 7 days, the feed's own limit):

```bash
curl -X POST "localhost:8080/api/v1/asteroid-alerting/alert?from=2026-09-01&to=2026-09-05"
```

Then watch the messages in Kafka UI at <http://localhost:8084>, and the rows land
in MySQL. The delivery worker picks up pending emails every 30 seconds.

## How it works

**Idempotency.** Each scan covers a rolling window, so consecutive scans
legitimately re-report the same approach, and Kafka is at-least-once on top of
that. The producer derives `eventId` from `asteroidId + closeApproachDate` rather
than randomly, and the consumer has a unique constraint on it — so re-scanning
adds no rows and sends no duplicate emails.

**Event contract.** The `__TypeId__` header carries the alias
`asteroid-collision.v1`, not a fully-qualified class name, via
`spring.json.type.mapping` on both sides. Either service can move its packages
without stranding messages already in the topic. Adding a field is backward
compatible; changing one means a `v2` record beside the `v1`.

**Delivery.** A notification fans out into one `notification_delivery` row per
enabled subscriber. The worker claims a batch with `SELECT ... FOR UPDATE SKIP
LOCKED`, so multiple instances share the queue rather than double-sending, and
marks a row `SENT` only *after* the mail server accepts the message. A failure
records the attempt and leaves the row claimable.

**Failure handling.** The NASA client has connect/read timeouts plus a
Resilience4j retry and circuit breaker. On the consumer side, a message that
cannot be deserialized is retried with exponential backoff and then parked on
`asteroid-alert.DLT` instead of blocking the partition.

**Schema.** Flyway owns it (`notification-service/src/main/resources/db/migration`),
with `ddl-auto=validate` so a mapping that disagrees with the schema fails at
startup rather than silently altering tables.

## Testing

```bash
./mvnw test      # unit and slice tests
./mvnw verify    # adds integration tests (needs a running Docker daemon)
```

Integration tests spin up real MySQL and Kafka containers via Testcontainers —
no in-memory substitutes, so constraints and `SKIP LOCKED` behave as they will in
production.

## Endpoints

| | asteroid-service | notification-service |
|---|---|---|
| Port | 8080 | 8081 |
| Health | `/actuator/health` | `/actuator/health` |
| Metrics | `/actuator/prometheus` | `/actuator/prometheus` |

## Troubleshooting

**`notification-service` health is DOWN.** Usually the mail indicator with
unset or wrong SMTP credentials. `/actuator/health/readiness` stays UP — mail is
reported honestly without taking the service out of rotation.

**Scans return 503.** The NASA feed is rate-limiting (`DEMO_KEY` allows 30
requests/hour) or unreachable, and the circuit breaker is shedding calls. Use
your own API key.

**No emails arrive.** Check `notification_delivery` — `status` and `last_error`
say exactly what happened for each recipient.
