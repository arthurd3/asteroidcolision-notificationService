[← Event-driven with Kafka](04-event-driven-kafka.md) · **English** · [Português (Brasil)](../pt-BR/05-persistence-and-flyway.md)

# Persistence and Flyway

Three tables in MySQL, owned by `notification-service`. `asteroid-service` and
`web-ui` have no database at all.

## Flyway owns the schema, Hibernate only checks it

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

This project previously used `ddl-auto: update`. That is convenient and wrong for
anything that will outlive a demo:

- it cannot be reviewed — the schema change is a diff of Java, not of SQL;
- it cannot be rolled back;
- it never drops or safely alters anything, so it accumulates dead columns silently;
- it does something different depending on which version of your entities happens to
  start first.

`validate` means Hibernate compares the mapping to the actual schema at startup and
**fails the context** if they disagree. A mapping that has drifted from the migration
is a startup error rather than a runtime surprise on the one query that touches the
changed column.

Migrations live in `notification-service/src/main/resources/db/migration`. The next one
is `V4__*.sql`.

## The tables

**`notification`** — one row per alert received.

The load-bearing part is one constraint:

```sql
CONSTRAINT uk_notification_event_id UNIQUE (event_id)
```

That is what makes Kafka re-delivery and overlapping producer scans a no-op rather
than a duplicate row and a duplicate email. See [document 04](04-event-driven-kafka.md).

Also note `miss_distance_kilometers DECIMAL(20,4)`. Hibernate's default for a
`BigDecimal` is `decimal(38,2)`, which silently truncates NASA's four-decimal
distances. The precision is explicit on the column *and* on the `@Column` annotation,
and `NotificationPersistenceIT` asserts that `50661467.0317` survives a round trip.

**`subscriber`** — one row per email recipient.

Named `subscriber`, not `user`, because `user` is reserved in MySQL 8 and every query
touching it would need quoting.

**`notification_delivery`** — the join: one row per notification per subscriber.

This table is why the model changed. Delivery state used to be a single `emailSent`
boolean on the notification itself, with no link to a recipient — so there was no way
to know *who* had received *what*, and a partially successful batch was
indistinguishable from a complete one. Now each row carries its own `status`,
`attempts`, `sent_at` and `last_error`.

## Claiming work: `SKIP LOCKED`

The dispatch worker claims a batch:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
@Query("select d from NotificationDelivery d where d.status = 'PENDING' order by d.id")
List<NotificationDelivery> claimPending(Limit limit);
```

`PESSIMISTIC_WRITE` issues `SELECT ... FOR UPDATE`. The `-2` is Hibernate's encoding of
**`SKIP LOCKED`** — rows another instance is already working are skipped rather than
waited on.

Without it, two instances of the service would serialise: the second blocks on the
first's locks and does no useful work. With it, they share the queue and no subscriber
gets the same alert twice.

A row is marked `SENT` only **after** the mail server has accepted the message. A
failure records the attempt and leaves the row claimable until `max-attempts`, at which
point it parks as `FAILED` with the reason in `last_error`. Nothing is lost and nothing
is retried forever.

## `open-in-view: false`, and what it forces

```yaml
spring:
  jpa:
    open-in-view: false
```

Open Session In View keeps the Hibernate session open for the whole HTTP request, so
lazy associations still load while a response is being serialised. It is on by default
in Spring Boot and it hides a real problem: the persistence context outlives the
transaction, queries fire from the view layer, and you get N+1 selects with no
transaction boundary around them.

Turning it off is right, and it has a consequence you must design for. By the time a
controller serialises its result the session is closed, so returning an entity hands
the serialiser a detached object and the first lazy read fails — **inside the message
converter**, where the stack trace explains nothing about the cause.

The read API therefore never returns entities. `NotificationQueryService` is
`@Transactional(readOnly = true)` and returns records, and the per-recipient rows come
from a **JPQL constructor expression**:

```java
@Query("""
        select new com.arthur.asteroid.notification.web.DeliveryView(
            s.email, s.fullName, d.status, d.attempts, d.sentAt, d.lastError, d.createdAt)
        from NotificationDelivery d
        join d.subscriber s
        where d.notification.eventId = :eventId
        order by s.email
        """)
List<DeliveryView> findViewsByEventId(String eventId);
```

Building the DTO *inside the query* makes the mistake structurally impossible rather
than something a reviewer has to notice. `NotificationPersistenceIT` calls
`entityManager.clear()` before reading the projection, which reproduces the closed
session — so if that ever regresses to an entity fetch, the test fails rather than
production.

### The count query, and the clever version that is not here

Delivery counts for a page come from one grouped query, not one per row:

```java
@Query("""
        select d.notification.id, d.status, count(d)
        from NotificationDelivery d
        where d.notification.id in :notificationIds
        group by d.notification.id, d.status
        """)
List<Object[]> countByNotificationAndStatus(Collection<Long> notificationIds);
```

`Object[]`, not a record, and that is deliberate. A constructor expression over
`count(d)` receives a `Long`, which will not match a `long` record component — and that
failure appears when the query is compiled at *startup*, not at compile time. The
assembly is ordinary Java in `NotificationQueryService` instead, where it is readable
and cannot fail late.

The single-query version — one `select new NotificationSummary(... sum(case when ...))
... group by` — also needs a hand-written `countQuery` to paginate. Two simple queries
avoid both traps.

## Paging

`GET /api/v1/notifications` returns an explicit `PageResponse` record, never Spring
Data's `Page`. Serialising `PageImpl` directly is unsupported: it logs a warning, and
its JSON structure is not part of Spring Data's public contract, so an upgrade can
rename fields under a client that depends on them. `web-ui` is exactly such a client.

## Indexes, including one deliberately absent

`V3__notification_history_read_index.sql` adds one index and explains why it does not
add a second:

```sql
CREATE INDEX ix_notification_created_at ON notification (created_at);
```

The history page orders by `created_at desc` and pages through it; without this that is
a filesort over the whole table on every page view, and the table only grows.

No index is added on `notification_delivery(notification_id)` for the grouped count
query, because `uk_delivery_notification_subscriber` is already
`(notification_id, subscriber_id)` and MySQL uses its **leftmost prefix** for a
`where notification_id in (...)` lookup. A second index would be another copy of the
same B-tree to maintain on every insert.

## Why the entities are not `@Data`

Lombok's `@Data` generates `equals`/`hashCode` over every field including the generated
id. An entity's hash therefore *changes* the moment `IDENTITY` assigns that id after
persist, which breaks `HashSet` membership and violates the JPA identity contract.

`Notification` uses its natural key, `eventId`, which is stable from construction.
`NotificationDelivery` — which has no natural key — uses the id when present and a
constant `hashCode`, which is the standard safe pattern.

## Try it yourself

```bash
docker compose up -d
./mvnw -pl notification-service verify   # runs the Testcontainers ITs

# look at the real schema Flyway produced
docker exec -it asteroid-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" asteroidalerting \
  -e "show create table notification_delivery\G"

# prove SKIP LOCKED is really in the plan
docker exec -it asteroid-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" asteroidalerting \
  -e "select id, status, attempts, last_error from notification_delivery limit 5;"
```

Then break something on purpose: add a field to `Notification` without a migration and
start the service. It fails at startup with a schema validation error, which is exactly
what `ddl-auto: validate` is for.
