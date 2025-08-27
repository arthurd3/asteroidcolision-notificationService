package com.arthur.asteroidcolision.service;

import com.arthur.asteroidcolision.client.NasaClient;
import com.arthur.asteroidcolision.dto.Asteroid;
import com.arthur.asteroidcolision.event.AsteroidCollisionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
public class AsteroidAlertingService {

    private final NasaClient nasaClient;
    private final KafkaTemplate<String, AsteroidCollisionEvent> kafkaTemplate;

    public AsteroidAlertingService(NasaClient nasaClient, KafkaTemplate<String, AsteroidCollisionEvent> kafkaTemplate) {
        this.nasaClient = nasaClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void alert(){
        log.info("Alert of Asteroid Alerting");

        final LocalDate fromDate = LocalDate.now();
        final LocalDate toDate = LocalDate.now().plusDays(7);

        log.info("Getting asteroid alerts from {} to {}", fromDate, toDate);
        final List<Asteroid> asteroidList = nasaClient.getNeoAsteroids(fromDate, toDate);
        log.info("Found {} asteroid alerts", asteroidList.size());

        final List<Asteroid> dangerousAsteroid = asteroidList.stream()
                .filter(Asteroid::isPotentiallyHazardous)
                .toList();
        log.info("Found {} asteroid alerts", dangerousAsteroid.size());

        final List<AsteroidCollisionEvent> asteroidCollisionEvents =
                createEventListOfDangerousAsteroids(dangerousAsteroid);

        log.info("Sending {} asteroid collision events to Kafka", asteroidCollisionEvents.size());
        asteroidCollisionEvents.forEach( event -> {
            kafkaTemplate.send("asteroid-alerts", event);
            log.info("Asteroid alert sent to Kafka Topic {}",event);
        });
    }

    private List<AsteroidCollisionEvent> createEventListOfDangerousAsteroids(final List<Asteroid> dangerousAsteroid) {
        return  dangerousAsteroid.stream()
                .map( asteroid -> {
                    if (asteroid.isPotentiallyHazardous()) {
                        return AsteroidCollisionEvent.builder()
                                .asteroidName(asteroid.getName())
                                .closeApproachDate(asteroid.getCloseApproachDataList().getFirst().getMissDistance().getKilometers())
                                .missDistanceKilometers(asteroid.getCloseApproachDataList().getFirst().getMissDistance().getKilometers())
                                .estimatedDiameterAvgMeters((asteroid.getEstimatedDiameter().getMeters().getMinDiameter() +
                                        asteroid.getEstimatedDiameter().getMeters().getMaxDiameter()) / 2)
                                .build();
                    }
                    return null;
                })
                .toList();

    }
}
