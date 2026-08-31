package com.arthur.asteroid.notification.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import com.arthur.asteroid.notification.web.DeliveryView;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real MySQL, because the things worth checking here are database
 * behaviour: the unique constraint that makes ingest idempotent, the Flyway
 * schema matching the entity mappings, and the SKIP LOCKED claim query, none of
 * which an in-memory database would reproduce faithfully.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DirtiesContext
class NotificationPersistenceIT {

    @Container
    @ServiceConnection
    // Testcontainers 2.0 dropped the self-typed generic: MySQLContainer<?> no longer compiles
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private SubscriberRepository subscriberRepository;
    @Autowired
    private NotificationDeliveryRepository deliveryRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Flyway applied the schema and the seed subscriber")
    void schemaAndSeedApplied() {
        assertThat(subscriberRepository.findAllByNotificationEnabledTrue())
                .extracting(Subscriber::getEmail)
                .contains("dev@asteroid.local");
    }

    @Test
    @DisplayName("event_id is unique, which is what makes re-delivery a no-op")
    void eventIdIsUnique() {
        notificationRepository.saveAndFlush(notification("duplicate-me"));

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(notification("duplicate-me")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the same subscriber cannot be queued twice for one notification")
    void deliveryPairIsUnique() {
        final Notification notification = notificationRepository.saveAndFlush(notification("pair-test"));
        final Subscriber subscriber = subscriberRepository.saveAndFlush(subscriber("pair@test.local"));

        deliveryRepository.saveAndFlush(
                NotificationDelivery.pending(notification, subscriber, Instant.now()));

        assertThatThrownBy(() -> deliveryRepository.saveAndFlush(
                NotificationDelivery.pending(notification, subscriber, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the claim query returns pending rows and respects the batch limit")
    void claimPendingRespectsLimit() {
        final Notification notification = notificationRepository.saveAndFlush(notification("claim-test"));
        for (int i = 0; i < 3; i++) {
            final Subscriber subscriber = subscriberRepository.saveAndFlush(subscriber("claim" + i + "@test.local"));
            deliveryRepository.saveAndFlush(
                    NotificationDelivery.pending(notification, subscriber, Instant.now()));
        }

        assertThat(deliveryRepository.claimPending(Limit.of(2))).hasSize(2);
        assertThat(deliveryRepository.countByStatus(DeliveryStatus.PENDING)).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("miss distance keeps its fractional kilometres")
    void missDistanceKeepsPrecision() {
        final Notification saved = notificationRepository.saveAndFlush(notification("precision-test"));

        assertThat(notificationRepository.findByEventId(saved.getEventId()))
                .get()
                .extracting(Notification::getMissDistanceKilometers)
                // decimal(20,4) - the old mapping defaulted to decimal(38,2) and truncated
                .isEqualTo(new BigDecimal("50661467.0317"));
    }

    @Test
    @DisplayName("the delivery projection survives a cleared persistence context")
    void deliveryProjectionNeedsNoOpenSession() {
        final Notification notification = notificationRepository.saveAndFlush(notification("projection-test"));
        final Subscriber sent = subscriberRepository.saveAndFlush(subscriber("a-sent@test.local"));
        final Subscriber failed = subscriberRepository.saveAndFlush(subscriber("b-failed@test.local"));

        final NotificationDelivery ok = NotificationDelivery.pending(notification, sent, Instant.now());
        ok.markSent(Instant.parse("2026-03-01T09:00:30Z"));
        deliveryRepository.saveAndFlush(ok);

        final NotificationDelivery bad = NotificationDelivery.pending(notification, failed, Instant.now());
        bad.markFailed("550 mailbox unavailable", 3);
        bad.markFailed("550 mailbox unavailable", 3);
        bad.markFailed("550 mailbox unavailable", 3);
        deliveryRepository.saveAndFlush(bad);

        // This is the point of the test. spring.jpa.open-in-view is false, so by the
        // time a controller serialises a result the session is gone. Clearing the
        // context here reproduces that: if findViewsByEventId returned entities
        // rather than projecting in the query, reading the subscriber's email below
        // would throw LazyInitializationException.
        entityManager.clear();

        final List<DeliveryView> views = deliveryRepository.findViewsByEventId("projection-test");

        assertThat(views).hasSize(2);
        assertThat(views.getFirst()).satisfies(view -> {
            assertThat(view.recipientEmail()).isEqualTo("a-sent@test.local");
            assertThat(view.status()).isEqualTo(DeliveryStatus.SENT);
            assertThat(view.attempts()).isEqualTo(1);
            assertThat(view.sentAt()).isEqualTo(Instant.parse("2026-03-01T09:00:30Z"));
            assertThat(view.lastError()).isNull();
        });
        assertThat(views.get(1)).satisfies(view -> {
            assertThat(view.recipientEmail()).isEqualTo("b-failed@test.local");
            assertThat(view.status()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(view.attempts()).isEqualTo(3);
            assertThat(view.lastError()).isEqualTo("550 mailbox unavailable");
        });
    }

    @Test
    @DisplayName("the grouped count query rolls up statuses per notification in one query")
    void groupedCountsPerNotification() {
        final Notification notification = notificationRepository.saveAndFlush(notification("counts-test"));
        for (int i = 0; i < 3; i++) {
            final Subscriber subscriber =
                    subscriberRepository.saveAndFlush(subscriber("counts" + i + "@test.local"));
            final NotificationDelivery delivery =
                    NotificationDelivery.pending(notification, subscriber, Instant.now());
            if (i < 2) {
                delivery.markSent(Instant.now());
            }
            deliveryRepository.saveAndFlush(delivery);
        }

        final Map<DeliveryStatus, Long> counts =
                deliveryRepository.countByNotificationAndStatus(List.of(notification.getId())).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                row -> (DeliveryStatus) row[1], row -> (Long) row[2]));

        assertThat(counts).containsEntry(DeliveryStatus.SENT, 2L)
                .containsEntry(DeliveryStatus.PENDING, 1L);
    }

    @Test
    @DisplayName("the history page orders newest first")
    void historyPageIsNewestFirst() {
        final Notification older = notification("order-older");
        older.setCreatedAt(Instant.parse("2026-03-01T09:00:00Z"));
        notificationRepository.saveAndFlush(older);

        final Notification newer = notification("order-newer");
        newer.setCreatedAt(Instant.parse("2026-03-02T09:00:00Z"));
        notificationRepository.saveAndFlush(newer);

        assertThat(notificationRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 2)))
                .extracting(Notification::getEventId)
                .containsExactly("order-newer", "order-older");
    }

    @Test
    @DisplayName("last-ingested is empty on a fresh table rather than throwing")
    void lastCreatedAtOnEmptyTable() {
        // max() over no rows is SQL NULL, which Spring Data maps to an empty Optional
        // only because the return type says Optional. A bare Instant would be null.
        assertThat(notificationRepository.findLastCreatedAt()).isEmpty();

        notificationRepository.saveAndFlush(notification("last-created"));

        assertThat(notificationRepository.findLastCreatedAt())
                .contains(Instant.parse("2026-03-01T09:00:00Z"));
    }

    private static Notification notification(String eventId) {
        final Notification notification = new Notification();
        notification.setEventId(eventId);
        notification.setAsteroidId("2000433");
        notification.setAsteroidName("433 Eros");
        notification.setCloseApproachDate(LocalDate.of(2026, 3, 4));
        notification.setMissDistanceKilometers(new BigDecimal("50661467.0317"));
        notification.setEstimatedDiameterAvgMeters(469.4);
        notification.setOccurredAt(Instant.parse("2026-03-01T09:00:00Z"));
        notification.setCreatedAt(Instant.parse("2026-03-01T09:00:00Z"));
        return notification;
    }

    private static Subscriber subscriber(String email) {
        final Subscriber subscriber = new Subscriber();
        subscriber.setEmail(email);
        subscriber.setFullName("Test");
        subscriber.setNotificationEnabled(true);
        subscriber.setCreatedAt(Instant.parse("2026-03-01T09:00:00Z"));
        return subscriber;
    }
}
