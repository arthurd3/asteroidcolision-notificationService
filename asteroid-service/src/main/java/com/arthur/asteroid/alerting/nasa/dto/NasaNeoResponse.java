package com.arthur.asteroid.alerting.nasa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Top level of the NASA NEO feed response.
 *
 * <p>The feed groups objects by approach date, hence the map. It also returns a
 * {@code links} object that this record does not map — the previous version of
 * this class was the only one missing {@code ignoreUnknown}, and only parsed at
 * all because it was deserialized by a hand-built RestTemplate that happened to
 * pick up lenient Jackson defaults rather than the application's configured mapper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NasaNeoResponse(

        @JsonProperty("element_count") Integer elementCount,
        @JsonProperty("near_earth_objects") Map<String, List<Asteroid>> nearEarthObjects
) {

    /** Never null, so callers can stream without a null guard. */
    public Map<String, List<Asteroid>> nearEarthObjects() {
        return nearEarthObjects == null ? Map.of() : nearEarthObjects;
    }
}
