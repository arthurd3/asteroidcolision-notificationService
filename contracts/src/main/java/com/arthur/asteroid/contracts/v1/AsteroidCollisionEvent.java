package com.arthur.asteroid.contracts.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A hazardous close approach detected in the NASA NEO feed, published to the
 * {@code asteroid-alert} topic.
 *
 * <p>This is the wire contract between asteroid-service and notification-service.
 * It is deliberately dependency-light: Jackson annotations and Bean Validation
 * annotations only, no Spring and no Lombok, so neither service inherits the
 * other's build choices.
 *
 * <p>{@code ignoreUnknown} is what lets a v1 consumer keep working against a
 * producer that has started sending an extra field. Removing or retyping an
 * existing component is a breaking change and needs a {@code v2} record
 * alongside this one, not an edit here.
 *
 * @param eventId                    stable idempotency key; also used as the Kafka
 *                                   message key so approaches for one asteroid keep
 *                                   partition affinity and relative order
 * @param occurredAt                 when the producer observed the approach, not when
 *                                   the approach happens
 * @param asteroidId                 NASA NEO reference id
 * @param closeApproachDate          date of closest approach
 * @param missDistanceKilometers     exact, because NASA reports it as a
 *                                   many-digit decimal string
 * @param estimatedDiameterAvgMeters mean of NASA's min/max estimate, so a double
 *                                   is honest about the precision available
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsteroidCollisionEvent(

        @NotBlank String eventId,
        @NotNull Instant occurredAt,
        @NotBlank String asteroidId,
        @NotBlank String asteroidName,
        @NotNull LocalDate closeApproachDate,
        @NotNull @Positive BigDecimal missDistanceKilometers,
        @Positive double estimatedDiameterAvgMeters
) {
}
