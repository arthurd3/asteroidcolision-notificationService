package com.arthur.asteroid.notification.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    List<Subscriber> findAllByNotificationEnabledTrue();
}
