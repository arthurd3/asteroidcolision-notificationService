package com.arthur.asteroid.alerting.nasa.dto.donki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

/**
 * One Kp-index observation during a geomagnetic storm.
 *
 * <p>Kp runs 0 to 9. A storm is reported from 5 upwards, and 8 or 9 is the range
 * where power grids and satellites are affected, so the maximum reading across a
 * storm is the number worth surfacing.
 *
 * <p>Verified against a live capture: {@code observedTime}, {@code kpIndex} and
 * {@code source} (e.g. "NOAA") are what DONKI sends. A single storm can carry a
 * dozen or more readings as it develops.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KpIndexReading(

        OffsetDateTime observedTime,
        Double kpIndex,
        String source
) {
}
