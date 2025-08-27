package com.arthur.notificationservice.service.repository;

import com.arthur.notificationservice.service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
