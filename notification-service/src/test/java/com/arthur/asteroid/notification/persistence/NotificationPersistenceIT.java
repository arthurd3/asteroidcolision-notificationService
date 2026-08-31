package com.arthur.asteroid.notification.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
