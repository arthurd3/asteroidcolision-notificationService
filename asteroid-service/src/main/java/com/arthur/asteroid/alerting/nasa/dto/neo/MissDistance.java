package com.arthur.asteroid.alerting.nasa.dto.neo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * How close the object came, in four units.
 *
 * <p>NASA sends every one as a decimal string, e.g. "4024665.4189", and they stay
 * strings here. Only {@code kilometers} is parsed, and only because it crosses the
 * Kafka boundary as a {@link BigDecimal} in the event contract - parsing it at this
 * edge means a malformed value is a skipped object rather than a failure inside the
 * consumer's listener. The other three are displayed and never computed with, so
 * parsing them would add three more ways for one bad field to fail a whole response.
 *
 * @param lunar distance in LD, where 1 is the average Earth-Moon distance. The unit
 *              people actually have intuition for
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MissDistance(

        String astronomical,
        String lunar,
        String kilometers,
        String miles
) {

    public Optional<BigDecimal> kilometersValue() {
        if (kilometers == null || kilometers.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(kilometers));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
