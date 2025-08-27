package com.arthur.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    @KafkaListener(topics = "asteroid-alert" , groupId = "notification-service")
    public void alertEvent(AsteroidCollisionEvent notification){

    }
}
