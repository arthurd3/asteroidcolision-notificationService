package com.arthur.notificationservice.service;

import com.arthur.notificationservice.asteroidalert.event.AsteroidCollisionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    @KafkaListener(topics = "asteroid-alert" , groupId = "notification-service")
    public void alertEvent(AsteroidCollisionEvent notificationEvent){
        log.info("Received Asteroid Collision Event {}", notificationEvent);



    }
}
