package com.arthur.asteroid.notification.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}
