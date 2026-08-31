**English** · [Português (Brasil)](../pt-BR/00-overview.md)

# Overview

## What this system does

NASA publishes a feed of asteroids that will pass near Earth. This project watches
that feed, and when something classified as *potentially hazardous* is coming, it
stores the fact and emails the people who asked to be told.

That is a small idea, and it is deliberately implemented the way a real system would
be rather than the smallest way that works:

```
NASA  ──►  asteroid-service  ──►  Kafka  ──►  notification-service  ──►  email
                  ▲                                    │
                  │                                    ▼
              web-ui  ◄──────────────────────────  MySQL
```

Three services, an event log between them, a database, and a front end that can see
all of it.

## Who this is for

Someone who can read Java and wants to see why a distributed system is built the way
it is — not just what the code does. Every document here answers "why is it like
this" at least as much as "what is it".

You do not need to know Kafka, Spring Boot or JSP in advance. You do need to be
comfortable reading code and running commands.

## The three services

**`asteroid-service`** (port 8080) is everything that talks to NASA. It reads five
APIs — near-Earth objects, the astronomy picture of the day, space weather, Earth
imagery — turns them into Java records, and publishes an event to Kafka for every
hazardous close approach it finds. It owns the API key, and no other process ever
sees it.

**`notification-service`** (port 8081) consumes those events. It stores each one,
fans it out into one delivery row per subscriber, and a worker sends the emails. It
also exposes a read-only API so you can ask what it stored and whether the mail
actually arrived.

**`web-ui`** (port 8082) renders all of it as HTML. It holds no NASA client and no
database; it reads the other two over HTTP. It is a WAR rather than a JAR, because it
serves JSP.

There is also **`contracts`**, which is not a service: one record, shared by the
producer and the consumer, defining the event that travels between them.

## How to read this

In order, if you have the time. Each document assumes the ones before it.

If you are short on time, three of them carry most of the load:

- [01 — Architecture](01-architecture.md), for why there are three services rather than one.
- [03 — HTTP clients and resilience](03-http-clients-and-resilience.md), for the most
  detailed reasoning in the project: timeouts, retries, circuit breakers and what
  happens when an upstream is slow rather than broken.
- [04 — Event-driven with Kafka](04-event-driven-kafka.md), for idempotency and dead
  letters, which are the two ideas that make the pipeline safe to re-run.

## This is a teaching repository

Two consequences worth knowing up front.

**The comments are part of the material.** The POM and YAML files are commented far
more heavily than production code normally is, because most of what they record are
Spring Boot 3 → 4 migration traps that cost real time to find. [Document 08](08-spring-boot-3-to-4.md)
collects them.

**So is the commit history.** Each message explains why, not what. `git log` is worth
reading.

**And the working notes are left in view.** Where a fixture is a real capture rather
than something written from documentation, it says so, and why that distinction changes
what a test proves — see `asteroid-service/src/test/resources/nasa/README-fixtures.md`.
Where a simpler alternative exists and was not taken, the reasoning is written down —
see the EPIC image proxy in [document 02](02-nasa-apis.md). A teaching repository that
hides its trade-offs teaches the wrong thing.

## Try it yourself

Before reading further, get the thing running — the rest makes more sense with a
browser open:

```bash
docker compose up -d
./mvnw clean install -DskipTests

set -a; . ./.env; set +a
./mvnw -pl asteroid-service      spring-boot:run   # terminal 1
./mvnw -pl notification-service  spring-boot:run   # terminal 2
./mvnw -pl web-ui                spring-boot:run   # terminal 3
```

Then open <http://localhost:8082>. Full instructions, including what to do when a
step fails, are in [document 10](10-running-locally.md).
