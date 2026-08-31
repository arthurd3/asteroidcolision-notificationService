package com.arthur.asteroid.alerting.nasa.dto.donki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A solar flare: a sudden brightening on the Sun, classified by X-ray intensity.
 *
 * <p><strong>Unverified shape.</strong> As with {@link GeomagneticStorm}, these
 * component names come from NASA's DONKI documentation rather than from a captured
 * response, because DEMO_KEY's 30-per-hour budget was spent verifying the other
 * APIs. A mismatched name deserialises to null silently rather than failing. See
 * {@link GeomagneticStorm} for how to settle it.
 *
 * @param classType A, B, C, M or X followed by a magnitude, e.g. "M1.2". The scale
 *                  is logarithmic: an X flare is ten times a M and a hundred times a C
 * @param endTime   null while a flare is still in progress at the time DONKI is asked
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SolarFlare(

        @JsonProperty("flrID") String flrId,
        List<DonkiInstrument> instruments,
        @JsonProperty("beginTime") OffsetDateTime beginTime,
        @JsonProperty("peakTime") OffsetDateTime peakTime,
        @JsonProperty("endTime") OffsetDateTime endTime,
        @JsonProperty("classType") String classType,
        String sourceLocation,
        @JsonProperty("activeRegionNum") Integer activeRegionNum,
        String note,
        @JsonProperty("linkedEvents") List<LinkedEvent> linkedEvents,
        String link
) {

    /** Never null, so a view can iterate without a guard. */
    public List<DonkiInstrument> instruments() {
        return instruments == null ? List.of() : instruments;
    }

    /** Never null, so a view can iterate without a guard. */
    public List<LinkedEvent> linkedEvents() {
        return linkedEvents == null ? List.of() : linkedEvents;
    }

    /** The letter of the classification, which is the part worth colouring a badge by. */
    public String classLetter() {
        return classType == null || classType.isBlank() ? "?" : classType.substring(0, 1);
    }
}
