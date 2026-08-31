package com.arthur.asteroid.webui.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/** One near-Earth object. {@code orbitalData} is null on feed responses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsteroidView(

        String id,
        String name,
        String designation,
        @JsonProperty("nasa_jpl_url") String nasaJplUrl,
        @JsonProperty("absolute_magnitude_h") Double absoluteMagnitudeH,
        @JsonProperty("estimated_diameter") EstimatedDiameterView estimatedDiameter,
        @JsonProperty("is_potentially_hazardous_asteroid") boolean potentiallyHazardous,
        @JsonProperty("is_sentry_object") Boolean sentryObject,
        @JsonProperty("close_approach_data") List<CloseApproachView> closeApproachData,
        @JsonProperty("orbital_data") OrbitalDataView orbitalData
) {

    public List<CloseApproachView> closeApproachData() {
        return closeApproachData == null ? List.of() : closeApproachData;
    }

    public Optional<CloseApproachView> firstApproach() {
        return closeApproachData().isEmpty() ? Optional.empty()
                : Optional.of(closeApproachData().getFirst());
    }

    public Optional<Double> averageDiameterMeters() {
        return Optional.ofNullable(estimatedDiameter)
                .map(EstimatedDiameterView::meters)
                .map(range -> (range.min() + range.max()) / 2);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EstimatedDiameterView(DiameterRangeView meters) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiameterRangeView(
            @JsonProperty("estimated_diameter_min") double min,
            @JsonProperty("estimated_diameter_max") double max) {
    }
}
