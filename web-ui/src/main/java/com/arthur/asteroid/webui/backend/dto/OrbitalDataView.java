package com.arthur.asteroid.webui.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/** The orbit, as solved by JPL. Every element stays a String, as NASA sends it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrbitalDataView(

        @JsonProperty("orbit_id") String orbitId,
        @JsonProperty("first_observation_date") LocalDate firstObservationDate,
        @JsonProperty("last_observation_date") LocalDate lastObservationDate,
        @JsonProperty("observations_used") Integer observationsUsed,
        @JsonProperty("orbit_uncertainty") String orbitUncertainty,
        @JsonProperty("minimum_orbit_intersection") String minimumOrbitIntersection,
        String eccentricity,
        @JsonProperty("semi_major_axis") String semiMajorAxis,
        String inclination,
        @JsonProperty("orbital_period") String orbitalPeriod,
        @JsonProperty("perihelion_distance") String perihelionDistance,
        @JsonProperty("aphelion_distance") String aphelionDistance,
        @JsonProperty("orbit_class") OrbitClassView orbitClass
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrbitClassView(
            @JsonProperty("orbit_class_type") String type,
            @JsonProperty("orbit_class_description") String description,
            @JsonProperty("orbit_class_range") String range) {
    }
}
