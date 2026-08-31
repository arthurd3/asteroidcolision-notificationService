package com.arthur.asteroid.alerting.nasa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CloseApproachData(

        @JsonProperty("close_approach_date") LocalDate closeApproachDate,
        @JsonProperty("miss_distance") MissDistance missDistance
) {
}
