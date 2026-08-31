package com.arthur.asteroid.alerting.nasa.dto.neo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One page of NASA's full near-Earth-object catalogue.
 *
 * <p>Unlike the feed, this is genuinely large - upwards of 62,000 objects, so more
 * than three thousand pages at the maximum page size of 20. Anything rendering it
 * has to page rather than fetch and filter.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NeoBrowsePage(

        @JsonProperty("near_earth_objects") List<Asteroid> nearEarthObjects,
        PageInfo page
) {

    /** @param number zero-based, matching NASA's own {@code page} query parameter */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageInfo(

            int size,
            @JsonProperty("total_elements") long totalElements,
            @JsonProperty("total_pages") int totalPages,
            int number
    ) {
    }

    /** Never null, so callers can stream without a guard. */
    public List<Asteroid> nearEarthObjects() {
        return nearEarthObjects == null ? List.of() : nearEarthObjects;
    }
}
