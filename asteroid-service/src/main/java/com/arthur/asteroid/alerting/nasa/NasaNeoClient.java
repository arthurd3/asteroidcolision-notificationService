package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.nasa.dto.Asteroid;

import java.time.LocalDate;
import java.util.List;

/**
 * Reads near-Earth objects from the NASA feed.
 *
 * <p>An interface so the HTTP call can be stubbed in tests without standing up a
 * server or resorting to Mockito on a concrete class.
 */
public interface NasaNeoClient {

    /**
     * @throws NasaUnavailableException if the feed is unreachable or rejects the request
     */
    List<Asteroid> findAsteroids(LocalDate from, LocalDate to);
}
