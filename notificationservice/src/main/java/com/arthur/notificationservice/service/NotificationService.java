package com.arthur.notificationservice.service;

import com.arthur.asteroidcolision.event.AsteroidCollisionEvent;
import com.arthur.notificationservice.service.entity.Notification;
import com.arthur.notificationservice.service.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    public NotificationService(NotificationRepository notificationRepository, EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
    }

    @KafkaListener(topics = "asteroid-alert" , groupId = "notification-service")
    public void alertEvent(AsteroidCollisionEvent notificationEvent){
        log.info("Received Asteroid Collision Event {}", notificationEvent);

        final Notification notification = Notification.builder()
                .asteroidName(notificationEvent.getAsteroidName())
                .closeApproachDate(LocalDate.parse(notificationEvent.getCloseApproachDate()))
                .estimatedDiameterAvgMeters(notificationEvent.getEstimatedDiameterAvgMeters())
                .missDistanceKilometers(new BigDecimal(notificationEvent.getMissDistanceKilometers()))
                .emailSent(false)
                .build();

        final Notification savedNotification = notificationRepository.saveAndFlush(notification);
        log.info("Saved Notification {}", savedNotification);

    }

    @Scheduled(fixedRate = 100000)
    public void sendAlertEmail(){
        log.info("Send Alert Email");
        emailService.sendAsteroidAlertEmail();
    }
}
