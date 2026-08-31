package com.arthur.asteroid.alerting.nasa.dto.neo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * One close approach of one object to one body.
 *
 * @param closeApproachDateFull    minute precision, e.g. "1900-Jun-01 16:40". A
 *                                 String, because that format is NASA's own and
 *                                 parsing it only to format it again buys nothing
 * @param epochDateCloseApproach   the same instant as epoch milliseconds
 * @param orbitingBody             what the object passed close to. Usually "Earth",
 *                                 but lookup returns approaches to Mercury, Venus,
 *                                 Mars and Jupiter as well, which is worth showing
 *                                 rather than filtering away
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CloseApproachData(

        @JsonProperty("close_approach_date") LocalDate closeApproachDate,
        @JsonProperty("close_approach_date_full") String closeApproachDateFull,
        @JsonProperty("epoch_date_close_approach") Long epochDateCloseApproach,
        @JsonProperty("relative_velocity") RelativeVelocity relativeVelocity,
        @JsonProperty("miss_distance") MissDistance missDistance,
        @JsonProperty("orbiting_body") String orbitingBody
) {
}
