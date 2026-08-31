# Asteroid Collision Alerting

<img width="961" height="449" alt="architecture" src="https://github.com/user-attachments/assets/b270fcca-e7a3-4562-ab6e-4ea640e19d20" />

An event-driven pipeline over NASA's open APIs. **asteroid-service** reads five NASA
APIs and publishes potentially hazardous close approaches to Kafka;
**notification-service** consumes those events, stores them, and emails subscribers;
**web-ui** renders all of it, including what the pipeline itself did.

Java 21 · Spring Boot 4.1 · Kafka · MySQL · Flyway · JSP · Testcontainers

📚 **[Teaching documentation](docs/README.md)** — twelve chapters, in
[English](docs/en/00-overview.md) and [Português](docs/pt-BR/00-overview.md).

---

## Layout

```
.
├── contracts/              shared Kafka event contract (plain jar, no Spring)
├── asteroid-service/       NASA APIs -> Kafka        (port 8080)
├── notification-service/   Kafka -> MySQL -> email   (port 8081)
├── web-ui/                 JSP front end (war)       (port 8082)
├── docs/                   bilingual teaching material
└── docker-compose.yml      MySQL, Kafka (KRaft), Kafka UI
```

A Maven multi-module build. `contracts` holds the one definition of
`AsteroidCollisionEvent` that both services depend on, so the event schema cannot drift
between producer and consumer. `web-ui` deliberately does *not* depend on it — see
[Architecture](docs/en/01-architecture.md).

## Running it

**1. Configure.**

```bash
cp .env.example .env
```

| Variable | Notes |
|---|---|
| `NASA_API_KEY` | Free key from [api.nasa.gov](https://api.nasa.gov). **Do not leave it as `DEMO_KEY`** — that is capped at 30 requests/hour across *every* endpoint, and one page load spends four |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD` | Any values; compose and the app read the same ones |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP credentials — [Mailtrap](https://mailtrap.io) sandbox by default |

`.env` is git-ignored. Do not commit it.

**2. Start the infrastructure.**

```bash
docker compose up -d
docker compose ps          # wait for mysql and kafka to report healthy
```

**3. Build and run all three services.**

```bash
./mvnw clean install

set -a; . ./.env; set +a          # export the variables into your shell
./mvnw -pl asteroid-service      spring-boot:run     # terminal 1
./mvnw -pl notification-service  spring-boot:run     # terminal 2
./mvnw -pl web-ui                spring-boot:run     # terminal 3
```

**4. Open <http://localhost:8082>.**

Press **Run a scan**, then look at **Alert history** — the alerts are there with
per-recipient delivery status. Watch the raw messages in Kafka UI at
<http://localhost:8084>, and the emails in your Mailtrap inbox. The delivery worker
picks up pending emails every 30 seconds.

The services start in any order, and `web-ui` runs fine with the other two stopped —
it greys out the panels it cannot fill.

Or drive it from the command line:

```bash
curl -X POST localhost:8080/api/v1/asteroid-alerting/alert
# {"from":"2026-08-31","to":"2026-09-07","scanned":46,"hazardous":4,"published":4}

curl -s localhost:8081/api/v1/notifications/stats
```

## How it works

**Idempotency.** Each scan covers a rolling window, so consecutive scans legitimately
re-report the same approach, and Kafka is at-least-once on top of that. The producer
derives `eventId` from `asteroidId + closeApproachDate` rather than randomly, and the
consumer has a unique constraint on it — so re-scanning adds no rows and sends no
duplicate emails.

**Event contract.** The `__TypeId__` header carries the alias `asteroid-collision.v1`,
not a fully-qualified class name, via `spring.json.type.mapping` on both sides. Either
service can move its packages without stranding messages already in the topic.

**Per-API timeouts.** The five NASA APIs are not comparable: a DONKI space-weather
query routinely takes 60–90 seconds while the NEO feed answers in under two. Each gets
its own `RestClient`, timeout budget and Resilience4j instance, and DONKI additionally
gets a bulkhead so slow calls cannot occupy every request thread.

**The API key never leaves asteroid-service.** It travels as a query parameter, so the
URI is deliberately kept out of every log line and exception message. EPIC images —
whose archive requires the key — are proxied rather than linked.

**Delivery.** A notification fans out into one `notification_delivery` row per enabled
subscriber. The worker claims a batch with `SELECT ... FOR UPDATE SKIP LOCKED`, so
multiple instances share the queue rather than double-sending, and marks a row `SENT`
only *after* the mail server accepts the message.

**Failure handling.** Retries and circuit breakers on the NASA side. On the consumer
side, a message that cannot be deserialized is retried with exponential backoff and
then parked on `asteroid-alert.DLT` instead of blocking the partition.

**Schema.** Flyway owns it (`notification-service/src/main/resources/db/migration`),
with `ddl-auto=validate` so a mapping that disagrees with the schema fails at startup.

## Testing

```bash
./mvnw test      # unit and slice tests
./mvnw verify    # adds integration tests (needs a running Docker daemon)
```

Integration tests spin up real MySQL and Kafka containers via Testcontainers — no
in-memory substitutes, so constraints and `SKIP LOCKED` behave as they will in
production. `WebUiPagesIT` is the only test that actually compiles the JSPs;
`@WebMvcTest` cannot, because a slice has no servlet container.

## Endpoints

| | asteroid-service | notification-service | web-ui |
|---|---|---|---|
| Port | 8080 | 8081 | 8082 |
| Health | `/actuator/health` | `/actuator/health` | `/actuator/health` |
| Metrics | `/actuator/prometheus` | `/actuator/prometheus` | `/actuator/prometheus` |

**asteroid-service**

| | |
|---|---|
| `POST /api/v1/asteroid-alerting/alert?from=&to=` | scan the feed and publish events |
| `GET /api/v1/nasa/apod?date=` | astronomy picture of the day |
| `GET /api/v1/nasa/neo/feed?from=&to=` | close approaches in a window (max 7 days) |
| `GET /api/v1/nasa/neo/{id}` | one object, with its orbit |
| `GET /api/v1/nasa/neo/browse?page=&size=` | the full catalogue, paginated |
| `GET /api/v1/nasa/donki/{cme,gst,flr}?from=&to=` | space weather — **slow, up to 2 minutes** |
| `GET /api/v1/nasa/epic/natural?date=` | Earth imagery metadata |
| `GET /api/v1/nasa/epic/image/{collection}/{y}/{m}/{d}/{image}` | proxied PNG, no API key |

**notification-service**

| | |
|---|---|
| `GET /api/v1/notifications?page=&size=` | stored alerts, newest first |
| `GET /api/v1/notifications/{eventId}` | one alert with per-recipient delivery status |
| `GET /api/v1/notifications/stats` | pipeline totals |

## Troubleshooting

**Everything NASA-related returns 503, or the UI is all error pages.** Almost always
the rate limit — `DEMO_KEY` is 30 requests/hour across *all* endpoints. Get your own
key. See [Running locally](docs/en/10-running-locally.md).

**Space weather takes forever.** It is meant to. If you get a 429 instead, the bulkhead
is working — only two of those run concurrently.

**`notification-service` health is DOWN.** Usually the mail indicator with unset or
wrong SMTP credentials. `/actuator/health/readiness` stays UP — mail is reported
honestly without taking the service out of rotation.

**No emails arrive.** Open <http://localhost:8082/history>, which shows `status` and
`last_error` per recipient.

More, including what to do when a JSP change does not appear, in
[Running locally](docs/en/10-running-locally.md).
