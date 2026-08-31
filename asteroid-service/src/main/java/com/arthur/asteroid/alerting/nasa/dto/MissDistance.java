package com.arthur.asteroid.alerting.nasa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MissDistance(

        /* NASA sends this as a decimal string, e.g. "4024665.4189". Parsed to
           BigDecimal here rather than being carried as a String across the Kafka
           boundary and parsed on the far side, where a bad value would fail
           inside the consumer's listener instead of at this edge. */
        @JsonProperty("kilometers") String kilometers
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
