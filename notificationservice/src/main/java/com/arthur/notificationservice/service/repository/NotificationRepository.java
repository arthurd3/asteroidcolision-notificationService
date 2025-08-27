package com.arthur.notificationservice.service.repository;


import com.arthur.notificationservice.service.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByEmailSent(boolean b);
}
