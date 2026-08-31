package com.arthur.asteroid.notification.domain;

import com.arthur.asteroid.contracts.v1.AsteroidCollisionEvent;
import com.arthur.asteroid.notification.persistence.Notification;
import com.arthur.asteroid.notification.persistence.NotificationDelivery;
import com.arthur.asteroid.notification.persistence.NotificationDeliveryRepository;
import com.arthur.asteroid.notification.persistence.NotificationRepository;
import com.arthur.asteroid.notification.persistence.Subscriber;
import com.arthur.asteroid.notification.persistence.SubscriberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Stores an incoming alert and queues a delivery per enabled subscriber. */
@Slf4j
@Service
public class NotificationIngestService {

    private final NotificationRepository notificationRepository;
    private final SubscriberRepository subscriberRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final Clock clock;

    public NotificationIngestService(NotificationRepository notificationRepository,
                                     SubscriberRepository subscriberRepository,
                                     NotificationDeliveryRepository deliveryRepository,
                                     Clock clock) {
        this.notificationRepository = notificationRepository;
        this.subscriberRepository = subscriberRepository;
        this.deliveryRepository = deliveryRepository;
        this.clock = clock;
    }

    /**
     * Ingests one event. Re-delivery of an event already stored is a no-op.
     *
     * <p>Kafka is at-least-once, and the producer re-scans an overlapping window
     * on every run, so the same approach legitimately arrives more than once. The
     * previous listener called {@code saveAndFlush} unconditionally, so each
     * arrival became another row and another email. The unique constraint on
     * {@code event_id} plus this check makes repeats free.
     *
     * <p>Runs in one transaction: either the notification and all of its pending
     * deliveries land, or none of them do. There was no transaction boundary at all
     * before, so a crash mid-way could leave a notification with a partial fan-out.
     *
     * @return true if this was a new event
     */
    @Transactional
    public boolean ingest(final AsteroidCollisionEvent event) {
        if (notificationRepository.existsByEventId(event.eventId())) {
            log.debug("Event {} already ingested, skipping", event.eventId());
            return false;
        }

        final Instant now = Instant.now(clock);

        final Notification notification = new Notification();
        notification.setEventId(event.eventId());
        notification.setAsteroidId(event.asteroidId());
        notification.setAsteroidName(event.asteroidName());
        notification.setCloseApproachDate(event.closeApproachDate());
        notification.setMissDistanceKilometers(event.missDistanceKilometers());
        notification.setEstimatedDiameterAvgMeters(event.estimatedDiameterAvgMeters());
        notification.setOccurredAt(event.occurredAt());
        notification.setCreatedAt(now);
        notificationRepository.save(notification);

        final List<Subscriber> recipients = subscriberRepository.findAllByNotificationEnabledTrue();
        if (recipients.isEmpty()) {
            log.warn("Stored notification {} but no subscribers have notifications enabled",
                    event.eventId());
            return true;
        }

        deliveryRepository.saveAll(recipients.stream()
                .map(subscriber -> NotificationDelivery.pending(notification, subscriber, now))
                .toList());

        log.info("Ingested {} ({}), queued {} deliveries",
                event.asteroidName(), event.eventId(), recipients.size());
        return true;
    }
}
