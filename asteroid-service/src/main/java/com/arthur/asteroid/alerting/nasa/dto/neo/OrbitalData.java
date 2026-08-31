package com.arthur.asteroid.alerting.nasa.dto.neo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * The object's orbit, as solved by JPL. Present on lookup and browse, absent on feed.
 *
 * <p>Every orbital element stays a {@link String}. NASA sends them as decimal
 * strings, this service displays them and never computes with them, and parsing
 * twenty fields to {@code BigDecimal} in order to render them back as text would buy
 * nothing while turning one malformed field into a failure of the whole response.
 * The same reasoning as {@link MissDistance}, applied twenty times over.
 *
 * @param orbitUncertainty        0 (well determined) to 9 (poorly determined)
 * @param minimumOrbitIntersection MOID in AU: how close the two orbits come to each
 *                                 other, regardless of where the bodies are. This is
 *                                 the number that decides "potentially hazardous"
 * @param orbitClass              the family of orbit, e.g. Apollo or Aten
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrbitalData(

        @JsonProperty("orbit_id") String orbitId,
        @JsonProperty("orbit_determination_date") String orbitDeterminationDate,
        @JsonProperty("first_observation_date") LocalDate firstObservationDate,
        @JsonProperty("last_observation_date") LocalDate lastObservationDate,
        @JsonProperty("data_arc_in_days") Integer dataArcInDays,
        @JsonProperty("observations_used") Integer observationsUsed,
        @JsonProperty("orbit_uncertainty") String orbitUncertainty,
        @JsonProperty("minimum_orbit_intersection") String minimumOrbitIntersection,
        @JsonProperty("jupiter_tisserand_invariant") String jupiterTisserandInvariant,
        @JsonProperty("epoch_osculation") String epochOsculation,
        String eccentricity,
        @JsonProperty("semi_major_axis") String semiMajorAxis,
        String inclination,
        @JsonProperty("ascending_node_longitude") String ascendingNodeLongitude,
        @JsonProperty("orbital_period") String orbitalPeriod,
        @JsonProperty("perihelion_distance") String perihelionDistance,
        @JsonProperty("perihelion_argument") String perihelionArgument,
        @JsonProperty("aphelion_distance") String aphelionDistance,
        @JsonProperty("perihelion_time") String perihelionTime,
        @JsonProperty("mean_anomaly") String meanAnomaly,
        @JsonProperty("mean_motion") String meanMotion,
        String equinox,
        @JsonProperty("orbit_class") OrbitClass orbitClass
) {
}
