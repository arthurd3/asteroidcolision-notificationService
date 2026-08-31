package com.arthur.asteroid.alerting.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the NASA Near-Earth-Object feed.
 *
 * <p>Bound and validated at startup, so a missing API key fails the context
 * immediately with a readable message instead of surfacing as a 403 on the
 * first request.
 *
 * @param baseUrl       feed endpoint, without query parameters
 * @param apiKey        NASA API key. DEMO_KEY works but is capped at 30 requests
 *                      per hour, which is low enough to trip the circuit breaker
 *                      in normal use
 * @param lookaheadDays how far ahead to scan. The feed itself rejects spans
 *                      longer than 7 days
 */
@Validated
@ConfigurationProperties(prefix = "asteroid.nasa")
public record NasaProperties(

        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @Min(1) @Max(7) int lookaheadDays
) {
}
