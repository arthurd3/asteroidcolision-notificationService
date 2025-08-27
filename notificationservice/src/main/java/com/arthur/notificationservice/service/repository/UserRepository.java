package com.arthur.notificationservice.service.repository;

import com.arthur.notificationservice.service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u.email FROM User u WHERE u.notificationEnabled = true")
    List<String> findAllEmailsAndNotificationEnabled();
}
