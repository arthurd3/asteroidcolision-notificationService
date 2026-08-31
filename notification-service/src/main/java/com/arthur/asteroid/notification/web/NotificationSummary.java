package com.arthur.asteroid.notification.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One stored alert, with its delivery outcome rolled up.
 *
 * <p>The three counts are what makes a history page useful at a glance: an alert
 * with failures looks different from one that went out cleanly, without having to
 * open it.
 *
 * @param occurredAt when the producer observed the approach
 * @param createdAt  when this service stored it
 */
public record NotificationSummary(

        String eventId,
        String asteroidId,
        String asteroidName,
        LocalDate closeApproachDate,
        BigDecimal missDistanceKilometers,
        double estimatedDiameterAvgMeters,
        Instant occurredAt,
        Instant createdAt,
        long pending,
        long sent,
        long failed
) {
}
