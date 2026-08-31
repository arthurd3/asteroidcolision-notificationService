package com.arthur.asteroid.notification.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    /**
     * Newest first, which is the only order a history page wants.
     *
     * <p>Ordered by {@code created_at} rather than by id: they agree today, but
     * ingestion order is the thing being described, and V3 adds the index that keeps
     * this from being a filesort over the whole table on every page view.
     */
    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Empty on a fresh database, which is a real state rather than an error. */
    @Query("select max(n.createdAt) from Notification n")
    Optional<Instant> findLastCreatedAt();
}
