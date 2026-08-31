package com.arthur.asteroid.alerting.nasa.dto.neo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

/**
 * One near-Earth object, as returned by all three NeoWs endpoints.
 *
 * <p>One record covers the feed, lookup and browse deliberately. They return the
 * same resource; lookup and browse simply include {@code orbital_data}, which the
 * feed omits to keep its payload small. Forking this into {@code Asteroid} and a
 * separate {@code NeoDetail} would duplicate {@code estimated_diameter},
 * {@code close_approach_data} and both helper methods so that one nullable field
 * could be non-null in one of them.
 *
 * <p>The cost of that choice is that {@link #orbitalData()} is null on feed
 * responses. That is stated here rather than left for a caller to discover.
 *
 * <p>{@code potentiallyHazardous} is a primitive on purpose, and it is the only
 * required field here. A response missing it fails to parse rather than defaulting
 * to false - which is the behaviour you want, because this flag decides whether an
 * alert is published. Silently treating "NASA did not say" as "not hazardous" is the
 * one wrong answer. Every other field is boxed and may legitimately be absent.
 *
 * @param orbitalData     null on feed responses; populated by lookup and browse
 * @param sentryObject    whether NASA's Sentry system is tracking this object for
 *                        long-term impact risk. Boxed, because the feed omits it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Asteroid(

        String id,
        @JsonProperty("neo_reference_id") String neoReferenceId,
        String name,
        String designation,
        @JsonProperty("nasa_jpl_url") String nasaJplUrl,
        @JsonProperty("absolute_magnitude_h") Double absoluteMagnitudeH,
        @JsonProperty("estimated_diameter") EstimatedDiameter estimatedDiameter,
        @JsonProperty("is_potentially_hazardous_asteroid") boolean potentiallyHazardous,
        @JsonProperty("is_sentry_object") Boolean sentryObject,
        @JsonProperty("close_approach_data") List<CloseApproachData> closeApproachData,
        @JsonProperty("orbital_data") OrbitalData orbitalData
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

    /** Never null, so a view can iterate without a guard. */
    public List<CloseApproachData> closeApproachData() {
        return closeApproachData == null ? List.of() : closeApproachData;
    }
}
