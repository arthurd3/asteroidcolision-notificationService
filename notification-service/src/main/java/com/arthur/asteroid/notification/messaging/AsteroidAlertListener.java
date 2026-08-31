package com.arthur.asteroid.notification.messaging;

import com.arthur.asteroid.contracts.v1.AsteroidCollisionEvent;
import com.arthur.asteroid.notification.domain.NotificationIngestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AsteroidAlertListener {

    private final NotificationIngestService ingestService;

    public AsteroidAlertListener(NotificationIngestService ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * Consumes one alert.
     *
     * <p>The topic comes from configuration rather than a literal on the
     * annotation, and the group id is set here only — the old
     * {@code spring.kafka.consumer.group-id} property was dead config that the
     * annotation overrode, and confusingly it was named after the topic.
     *
     * <p>Anything thrown here reaches the container's error handler, which retries
     * with backoff and then routes to the dead-letter topic.
     */
    @KafkaListener(
            topics = "${notification.topic}",
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onAsteroidAlert(@Payload AsteroidCollisionEvent event,
                                @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        log.debug("Received alert for asteroid {} (key={})", event.asteroidName(), key);
        ingestService.ingest(event);
    }
}
