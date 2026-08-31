package com.arthur.asteroid.notification.domain;

import com.arthur.asteroid.notification.persistence.DeliveryStatus;
import com.arthur.asteroid.notification.persistence.Notification;
import com.arthur.asteroid.notification.persistence.NotificationDeliveryRepository;
import com.arthur.asteroid.notification.persistence.NotificationRepository;
import com.arthur.asteroid.notification.persistence.SubscriberRepository;
import com.arthur.asteroid.notification.web.DeliveryStats;
import com.arthur.asteroid.notification.web.DeliveryView;
import com.arthur.asteroid.notification.web.NotificationDetail;
import com.arthur.asteroid.notification.web.NotificationNotFoundException;
import com.arthur.asteroid.notification.web.NotificationSummary;
import com.arthur.asteroid.notification.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read side of the notification store.
 *
 * <p>Every method here is read-only, transactional, and returns records - never
 * entities. That is not ceremony; it is what makes {@code open-in-view: false} safe.
 * With the session closed at the end of these methods, handing a
 * {@code Notification} or a {@code NotificationDelivery} to a controller hands it a
 * detached object, and the first lazy association read fails during serialisation,
 * inside the message converter, where the stack trace explains nothing about the
 * cause. Assembling DTOs while the session is open is the fix, and doing it here
 * means there is one place to check.
 */
@Service
public class NotificationQueryService {

    /** A page size beyond this is a client bug or an attempt to dump the table. */
    public static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final SubscriberRepository subscribers;

    public NotificationQueryService(NotificationRepository notifications,
                                    NotificationDeliveryRepository deliveries,
                                    SubscriberRepository subscribers) {
        this.notifications = notifications;
        this.deliveries = deliveries;
        this.subscribers = subscribers;
    }

    /** Stored alerts, newest first, with their delivery outcomes rolled up. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationSummary> page(final int page, final int size) {
        final var found = notifications.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));

        // one grouped query for the whole page, rather than one per row
        final Map<Long, Map<DeliveryStatus, Long>> counts = countsFor(found.getContent());

        return PageResponse.of(found.map(notification ->
                summary(notification, counts.getOrDefault(notification.getId(), Map.of()))));
    }

    /** One alert with a row per recipient. */
    @Transactional(readOnly = true)
    public NotificationDetail detail(final String eventId) {
        final Notification notification = notifications.findByEventId(eventId)
                .orElseThrow(() -> new NotificationNotFoundException(eventId));

        final List<DeliveryView> views = deliveries.findViewsByEventId(eventId);

        return new NotificationDetail(
                notification.getEventId(), notification.getAsteroidId(), notification.getAsteroidName(),
                notification.getCloseApproachDate(), notification.getMissDistanceKilometers(),
                notification.getEstimatedDiameterAvgMeters(), notification.getOccurredAt(),
                notification.getCreatedAt(), views);
    }

    /** The whole pipeline in six numbers. */
    @Transactional(readOnly = true)
    public DeliveryStats stats() {
        return new DeliveryStats(
                notifications.count(),
                subscribers.countByNotificationEnabledTrue(),
                deliveries.countByStatus(DeliveryStatus.PENDING),
                deliveries.countByStatus(DeliveryStatus.SENT),
                deliveries.countByStatus(DeliveryStatus.FAILED),
                notifications.findLastCreatedAt().orElse(null));
    }

    private Map<Long, Map<DeliveryStatus, Long>> countsFor(final List<Notification> page) {
        if (page.isEmpty()) {
            // an `in ()` with no values is a syntax error in some databases and an
            // always-false predicate in others; not asking is unambiguous
            return Map.of();
        }
        final List<Long> ids = page.stream().map(Notification::getId).toList();

        final Map<Long, Map<DeliveryStatus, Long>> byNotification = new HashMap<>();
        for (final Object[] row : deliveries.countByNotificationAndStatus(ids)) {
            byNotification
                    .computeIfAbsent((Long) row[0], key -> new EnumMap<>(DeliveryStatus.class))
                    .put((DeliveryStatus) row[1], (Long) row[2]);
        }
        return byNotification;
    }

    private static NotificationSummary summary(final Notification notification,
                                               final Map<DeliveryStatus, Long> counts) {
        return new NotificationSummary(
                notification.getEventId(), notification.getAsteroidId(), notification.getAsteroidName(),
                notification.getCloseApproachDate(), notification.getMissDistanceKilometers(),
                notification.getEstimatedDiameterAvgMeters(), notification.getOccurredAt(),
                notification.getCreatedAt(),
                counts.getOrDefault(DeliveryStatus.PENDING, 0L),
                counts.getOrDefault(DeliveryStatus.SENT, 0L),
                counts.getOrDefault(DeliveryStatus.FAILED, 0L));
    }
}
