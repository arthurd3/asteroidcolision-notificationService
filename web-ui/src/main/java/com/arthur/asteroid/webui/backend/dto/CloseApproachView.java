package com.arthur.asteroid.webui.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/** One close approach, with the units a reader can picture. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CloseApproachView(

        @JsonProperty("close_approach_date") LocalDate closeApproachDate,
        @JsonProperty("close_approach_date_full") String closeApproachDateFull,
        @JsonProperty("relative_velocity") RelativeVelocityView relativeVelocity,
        @JsonProperty("miss_distance") MissDistanceView missDistance,
        @JsonProperty("orbiting_body") String orbitingBody
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelativeVelocityView(
            @JsonProperty("kilometers_per_second") String kilometersPerSecond,
            @JsonProperty("kilometers_per_hour") String kilometersPerHour) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MissDistanceView(String astronomical, String lunar,
                                   String kilometers, String miles) {
    }
}
