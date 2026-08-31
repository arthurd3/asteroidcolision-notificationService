package com.arthur.asteroid.alerting.nasa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Asteroid(

        String id,
        String name,
        @JsonProperty("estimated_diameter") EstimatedDiameter estimatedDiameter,
        @JsonProperty("is_potentially_hazardous_asteroid") boolean potentiallyHazardous,
        @JsonProperty("close_approach_data") List<CloseApproachData> closeApproachData
) {

    /**
     * The soonest approach on record, if the feed supplied one.
     *
     * <p>The feed can return an object with an empty {@code close_approach_data}
     * array. The previous code called {@code getFirst()} on it twice with no
     * guard, so such an object threw {@link java.util.NoSuchElementException}
     * mid-batch and dropped every remaining asteroid in the response.
     */
    public Optional<CloseApproachData> firstApproach() {
        return closeApproachData == null || closeApproachData.isEmpty()
                ? Optional.empty()
                : Optional.of(closeApproachData.getFirst());
    }

    /** Mean of NASA's min/max diameter estimate, in metres. */
    public Optional<Double> averageDiameterMeters() {
        return Optional.ofNullable(estimatedDiameter)
                .map(EstimatedDiameter::meters)
                .map(DiameterRange::average);
    }
}
