package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.nasa.dto.neo.Asteroid;
import com.arthur.asteroid.alerting.nasa.dto.neo.NeoBrowsePage;

import java.time.LocalDate;
import java.util.List;

/**
 * Reads NASA's Near-Earth-Object Web Service.
 *
 * <p>Three endpoints over one resource: the feed answers "what is passing between
 * these dates", lookup answers "tell me everything about this one object", and
 * browse walks the whole catalogue. Only the feed drives the alerting pipeline; the
 * other two exist to be read.
 */
public interface NasaNeoClient {

    /**
     * Objects with a close approach in the window, flattened in date order.
     *
     * <p>The window may not exceed seven days - NASA's own limit, not a choice made
     * here. Objects returned by the feed have no {@code orbitalData}.
     */
    List<Asteroid> findAsteroids(LocalDate from, LocalDate to);

    /**
     * One object by its NEO reference id, including its orbit.
     *
     * @param neoReferenceId NASA's numeric id, e.g. "2000433" for 433 Eros
     */
    Asteroid lookup(String neoReferenceId);

    /**
     * One page of the full catalogue.
     *
     * @param page zero-based
     * @param size NASA caps this at 20
     */
    NeoBrowsePage browse(int page, int size);
}
