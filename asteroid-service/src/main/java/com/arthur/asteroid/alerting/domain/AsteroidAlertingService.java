package com.arthur.asteroid.alerting.domain;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.messaging.AsteroidEventPublisher;
import com.arthur.asteroid.alerting.nasa.NasaNeoClient;
import com.arthur.asteroid.alerting.nasa.dto.neo.Asteroid;
import com.arthur.asteroid.contracts.v1.AsteroidCollisionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Scans the NASA feed for hazardous close approaches and publishes them. */
@Slf4j
@Service
public class AsteroidAlertingService {

    private final NasaNeoClient nasaNeoClient;
    private final AsteroidEventPublisher publisher;
    private final NasaProperties nasaProperties;
    private final Clock clock;

    public AsteroidAlertingService(NasaNeoClient nasaNeoClient,
                                   AsteroidEventPublisher publisher,
                                   NasaProperties nasaProperties,
                                   Clock clock) {
        this.nasaNeoClient = nasaNeoClient;
        this.publisher = publisher;
        this.nasaProperties = nasaProperties;
        this.clock = clock;
    }

    /** Scans the default window: today through {@code lookaheadDays} ahead. */
    public AlertSummary alert() {
        final LocalDate today = LocalDate.now(clock);
        return alert(today, today.plusDays(nasaProperties.neo().lookaheadDays()));
    }

    public AlertSummary alert(final LocalDate from, final LocalDate to) {
        final List<Asteroid> asteroids = nasaNeoClient.findAsteroids(from, to);

        final List<Asteroid> hazardous = asteroids.stream()
                .filter(Asteroid::potentiallyHazardous)
                .toList();

        final List<AsteroidCollisionEvent> events = hazardous.stream()
                .map(this::toEvent)
                .flatMap(Optional::stream)
                .toList();

        if (events.size() < hazardous.size()) {
            log.warn("{} of {} hazardous asteroids lacked usable approach data and were skipped",
                    hazardous.size() - events.size(), hazardous.size());
        }

        final int published = publisher.publishAll(events);
        log.info("Scanned {} .. {}: {} objects, {} hazardous, {} published",
                from, to, asteroids.size(), hazardous.size(), published);

        return new AlertSummary(from, to, asteroids.size(), hazardous.size(), published);
    }

    /**
     * Builds an event, or empty if the feed did not supply the approach data the
     * contract requires. Skipping one object beats failing the whole scan.
     */
    private Optional<AsteroidCollisionEvent> toEvent(final Asteroid asteroid) {
        return asteroid.firstApproach().flatMap(approach ->
                Optional.ofNullable(approach.missDistance())
                        .flatMap(distance -> distance.kilometersValue())
                        .flatMap(kilometers -> asteroid.averageDiameterMeters()
                                .filter(diameter -> diameter > 0)
                                .map(diameter -> new AsteroidCollisionEvent(
                                        eventId(asteroid.id(), approach.closeApproachDate()),
                                        Instant.now(clock),
                                        asteroid.id(),
                                        asteroid.name(),
                                        approach.closeApproachDate(),
                                        kilometers,
                                        diameter))));
    }

    /**
     * Derives the event id from the business key rather than randomly.
     *
     * <p>Each scan covers a rolling window, so consecutive scans legitimately
     * re-report the same approach. A random id would make every one of those look
     * like a new event and the consumer would store and email it again. Deriving
     * the id from asteroid plus approach date makes re-scanning a no-op downstream.
     */
    private static String eventId(final String asteroidId, final LocalDate closeApproachDate) {
        final String businessKey = asteroidId + ':' + closeApproachDate;
        return UUID.nameUUIDFromBytes(businessKey.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
