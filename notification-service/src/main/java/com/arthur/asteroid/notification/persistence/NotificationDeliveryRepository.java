package com.arthur.asteroid.notification.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

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
}
