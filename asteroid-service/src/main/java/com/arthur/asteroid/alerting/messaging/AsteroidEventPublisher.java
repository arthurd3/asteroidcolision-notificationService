package com.arthur.asteroid.alerting.messaging;

import com.arthur.asteroid.alerting.config.AlertingProperties;
import com.arthur.asteroid.contracts.v1.AsteroidCollisionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes collision events to the alert topic.
 *
 * <p>Two things the previous version got wrong are fixed here. It discarded the
 * future returned by {@code send()}, so broker-side failures were swallowed
 * entirely; and it logged success <em>before</em> the send was acknowledged, so
 * the log claimed delivery that might never have happened.
 *
 * <p>Messages are keyed by asteroid id. Sending with a null key round-robins
 * across partitions, which gives up ordering between two approaches for the
 * same asteroid.
 */
@Slf4j
@Component
public class AsteroidEventPublisher {

    private final KafkaTemplate<String, AsteroidCollisionEvent> kafkaTemplate;
    private final AlertingProperties properties;

    public AsteroidEventPublisher(KafkaTemplate<String, AsteroidCollisionEvent> kafkaTemplate,
                                  AlertingProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    /**
     * Publishes every event and waits for the broker to acknowledge them all.
     *
     * @return how many were acknowledged successfully
     */
    public int publishAll(final List<AsteroidCollisionEvent> events) {
        final List<CompletableFuture<Boolean>> results = events.stream()
                .map(this::publish)
                .toList();

        return (int) results.stream()
                .map(CompletableFuture::join)
                .filter(Boolean::booleanValue)
                .count();
    }

    private CompletableFuture<Boolean> publish(final AsteroidCollisionEvent event) {
        return kafkaTemplate.send(properties.topic(), event.asteroidId(), event)
                .handle((result, failure) -> {
                    if (failure != null) {
                        log.error("Failed to publish event {} for asteroid {}",
                                event.eventId(), event.asteroidName(), failure);
                        return false;
                    }
                    log.debug("Published event {} to {}-{}@{}", event.eventId(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    return true;
                });
    }
}
