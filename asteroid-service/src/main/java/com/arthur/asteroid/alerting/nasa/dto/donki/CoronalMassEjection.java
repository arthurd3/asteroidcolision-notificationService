package com.arthur.asteroid.alerting.nasa.dto.donki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A coronal mass ejection: a cloud of plasma thrown off the Sun.
 *
 * <p>Two things about DONKI's JSON are worth knowing. Its timestamps have no
 * seconds - "2026-08-02T10:45Z" - which parses only because
 * {@code ISO_OFFSET_DATE_TIME} treats seconds as optional; a hand-written
 * {@code yyyy-MM-dd'T'HH:mm:ss'Z'} pattern would fail on every record. And
 * {@code activeRegionNum} is genuinely null for a CME with no identified source
 * region, so it is boxed - an {@code int} would deserialise it to 0, which reads as
 * "region zero" rather than "not known".
 *
 * <p>{@code cmeAnalyses} is deliberately unmapped. It is a nested array of modelled
 * trajectories that nothing here displays, and {@code ignoreUnknown} means leaving
 * it out costs nothing.
 *
 * @param activityId     DONKI's own id, e.g. "2026-08-02T10:45:00-CME-001"
 * @param sourceLocation heliographic coordinates like "N12E45", or empty when the
 *                       source could not be located
 * @param link           DONKI's human-readable page for this event
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoronalMassEjection(

        @JsonProperty("activityID") String activityId,
        String catalog,
        @JsonProperty("startTime") OffsetDateTime startTime,
        List<DonkiInstrument> instruments,
        String sourceLocation,
        @JsonProperty("activeRegionNum") Integer activeRegionNum,
        String note,
        @JsonProperty("submissionTime") OffsetDateTime submissionTime,
        String link
) {

    /** Never null, so a view can iterate without a guard. */
    public List<DonkiInstrument> instruments() {
        return instruments == null ? List.of() : instruments;
    }
}
