package com.arthur.asteroid.alerting.nasa.dto.neo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The family an orbit belongs to, with NASA's own plain-English description.
 *
 * <p>Worth carrying rather than just the type code: "APO" means nothing to a reader,
 * while the description that comes with it explains the whole classification.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrbitClass(

        @JsonProperty("orbit_class_type") String type,
        @JsonProperty("orbit_class_description") String description,
        @JsonProperty("orbit_class_range") String range
) {
}
