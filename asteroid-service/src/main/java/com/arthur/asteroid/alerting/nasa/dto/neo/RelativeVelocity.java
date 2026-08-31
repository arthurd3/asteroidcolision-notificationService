package com.arthur.asteroid.alerting.nasa.dto.neo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How fast the object was moving relative to the body it passed.
 *
 * <p>All three are decimal strings, which is how NASA sends them. See
 * {@link MissDistance} for why they are not parsed here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RelativeVelocity(

        @JsonProperty("kilometers_per_second") String kilometersPerSecond,
        @JsonProperty("kilometers_per_hour") String kilometersPerHour,
        @JsonProperty("miles_per_hour") String milesPerHour
) {
}
