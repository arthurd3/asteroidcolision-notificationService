[← Security and secrets](09-security-and-secrets.md) · **English** · [Português (Brasil)](../pt-BR/10-running-locally.md)

# Running locally

## What you need

| | |
|---|---|
| **JDK 21** | `java -version` should say 21 |
| **Docker** | for MySQL, Kafka, and the integration tests |
| **A NASA API key** | free, instant, from <https://api.nasa.gov> |

Maven is not required — the wrapper (`./mvnw`) fetches it.

> **Get a real API key before anything else.** `DEMO_KEY` allows 30 requests per hour
> across *every* `api.nasa.gov` endpoint, and one load of the home page spends four.
> With `DEMO_KEY` the front end will mostly render error pages, and it will look like
> this project is broken when it is not.

## 1. Configure

```bash
cp .env.example .env
```

| Variable | Notes |
|---|---|
| `NASA_API_KEY` | from <https://api.nasa.gov>. **Do not leave this as `DEMO_KEY`.** |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD` | any values; compose and the app read the same ones |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP credentials — [Mailtrap](https://mailtrap.io) sandbox by default |

`.env` is git-ignored. Never commit it — see [document 09](09-security-and-secrets.md).

## 2. Start the infrastructure

```bash
docker compose up -d
docker compose ps          # wait for mysql and kafka to report healthy
```

Three containers: MySQL 8.4, a single-node Kafka 4 KRaft broker, and Kafka UI.

## 3. Build

```bash
./mvnw clean install
```

Add `-DskipTests` to skip the Testcontainers integration tests, which need Docker.

## 4. Run the three services

Each in its own terminal, with the environment loaded:

```bash
set -a; . ./.env; set +a

./mvnw -pl asteroid-service      spring-boot:run    # :8080
./mvnw -pl notification-service  spring-boot:run    # :8081
./mvnw -pl web-ui                spring-boot:run    # :8082
```

The `set -a` line exports everything in `.env` into the shell. Spring Boot does **not**
read `.env` files itself; the variables have to be in the environment.

They start in any order. `web-ui` runs fine with the other two stopped — it greys out
the panels it cannot fill.

## 5. Open it

<http://localhost:8082>

Then:

1. Go to **Run a scan** and press the button. That reads the NASA feed and publishes an
   event for every hazardous approach.
2. Go to **Alert history**. The alerts are there, each with per-recipient delivery
   status.
3. Watch the raw messages in Kafka UI at <http://localhost:8084>.
4. Check the emails in your Mailtrap inbox. The delivery worker runs every 30 seconds.

## The API, without the front end

```bash
# NASA data
curl -s localhost:8080/api/v1/nasa/apod | python3 -m json.tool
curl -s "localhost:8080/api/v1/nasa/neo/feed?from=2026-09-01&to=2026-09-05" | head -c 400
curl -s "localhost:8080/api/v1/nasa/neo/browse?page=0&size=5" | python3 -m json.tool | head -20
curl -s localhost:8080/api/v1/nasa/neo/2000433 | python3 -m json.tool | head -30
curl -s localhost:8080/api/v1/nasa/donki/cme | head -c 400        # slow: allow 90s
curl -s localhost:8080/api/v1/nasa/epic/natural | python3 -m json.tool | head -20

# trigger a scan
curl -X POST localhost:8080/api/v1/asteroid-alerting/alert

# what the pipeline stored
curl -s localhost:8081/api/v1/notifications | python3 -m json.tool
curl -s localhost:8081/api/v1/notifications/stats | python3 -m json.tool
```

## Ports

| | |
|---|---|
| 8080 | `asteroid-service` |
| 8081 | `notification-service` |
| 8082 | `web-ui` |
| 8084 | Kafka UI |
| 3306 | MySQL |
| 9092 | Kafka |

## Troubleshooting

**Everything NASA-related returns 503, or the UI is all error pages.**
Almost always the rate limit. `DEMO_KEY` is 30 requests/hour across all endpoints, and
once exhausted the circuit breaker sheds calls on top. Get your own key. Confirm with:

```bash
curl -s "https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY" | head -c 200
```

If that says `OVER_RATE_LIMIT`, that is the answer.

**Space weather takes forever.** It is meant to. DONKI genuinely takes 60–90 seconds;
`asteroid-service` gives it a 120-second budget. If you get a 429 instead, the bulkhead
is doing its job — only two of those run concurrently.

**`notification-service` health is DOWN.** Usually the mail indicator with unset or
wrong SMTP credentials. `/actuator/health/readiness` stays UP: mail is reported
honestly without taking the service out of rotation.

**No emails arrive.** Check `notification_delivery` — or just open
<http://localhost:8082/history>, which now shows exactly that per recipient. `status`
and `last_error` say what happened.

**`./mvnw verify` fails with Docker errors.** The integration tests need a running
daemon. `docker info` should succeed. Use `./mvnw test` to skip them.

**The service will not start after I changed an entity.** That is `ddl-auto: validate`
working. Add a Flyway migration in
`notification-service/src/main/resources/db/migration` — the next one is `V4__*.sql`.

**A JSP change is not showing.** Restart `web-ui`. Jasper caches compiled pages, and a
change to an included `.jspf` is not always detected.

## Try it yourself

The whole loop in one go, once everything is running:

```bash
curl -X POST localhost:8080/api/v1/asteroid-alerting/alert
sleep 5
curl -s localhost:8081/api/v1/notifications/stats | python3 -m json.tool
```

`notifications` should be greater than zero, and `pending` should be non-zero for about
thirty seconds before it becomes `sent`. That gap is the delivery worker's interval,
and watching it move is the clearest demonstration of the pipeline being asynchronous.
