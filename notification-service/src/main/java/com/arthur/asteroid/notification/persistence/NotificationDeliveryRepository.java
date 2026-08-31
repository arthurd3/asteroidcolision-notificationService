package com.arthur.asteroid.notification.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import com.arthur.asteroid.notification.web.DeliveryView;

import java.util.Collection;
import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    /**
     * Claims a batch of pending deliveries for this worker only.
     *
     * <p>{@code PESSIMISTIC_WRITE} issues {@code SELECT ... FOR UPDATE}, and the
     * {@code jakarta.persistence.lock.timeout} hint of {@code -2} is Hibernate's
     * encoding of {@code SKIP LOCKED} (MySQL 8 supports it). Rows another instance
     * is already working are skipped rather than waited on, so several instances
     * can share the queue instead of serialising — and no subscriber gets the same
     * alert twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select d from NotificationDelivery d where d.status = 'PENDING' order by d.id")
    List<NotificationDelivery> claimPending(Limit limit);

    long countByStatus(DeliveryStatus status);

    /**
     * Per-recipient rows for one notification, projected inside the query.
     *
     * <p>A constructor expression, not an entity fetch, and that is structural
     * rather than stylistic. {@code spring.jpa.open-in-view} is false, so the
     * persistence context is closed by the time a controller serialises its result.
     * Returning {@code NotificationDelivery} entities would therefore throw
     * {@code LazyInitializationException} on the first {@code getSubscriber()} - not
     * in the service where it would be obvious, but inside the message converter,
     * where the stack trace explains nothing. Building the DTO in the query makes
     * that mistake impossible instead of something a reviewer has to notice.
     */
    @Query("""
            select new com.arthur.asteroid.notification.web.DeliveryView(
                s.email, s.fullName, d.status, d.attempts, d.sentAt, d.lastError, d.createdAt)
            from NotificationDelivery d
            join d.subscriber s
            where d.notification.eventId = :eventId
            order by s.email
            """)
    List<DeliveryView> findViewsByEventId(String eventId);

    /**
     * (notificationId, status, count) for a page of notifications - one query rather
     * than one per row.
     *
     * <p>Returns {@code Object[]} rather than a record, because a constructor
     * expression over {@code count(d)} hands back a {@code Long} that will not match
     * a {@code long} component, and that failure appears when the query is compiled
     * at startup rather than at compile time. The assembly is done in
     * {@code NotificationQueryService} instead, where it is ordinary Java.
     *
     * <p>No index is needed for the {@code in} lookup:
     * {@code uk_delivery_notification_subscriber} is
     * {@code (notification_id, subscriber_id)} and MySQL uses its leftmost prefix.
     */
    @Query("""
            select d.notification.id, d.status, count(d)
            from NotificationDelivery d
            where d.notification.id in :notificationIds
            group by d.notification.id, d.status
            """)
    List<Object[]> countByNotificationAndStatus(Collection<Long> notificationIds);
}
