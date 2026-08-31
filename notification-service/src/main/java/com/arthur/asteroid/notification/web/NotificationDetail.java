package com.arthur.asteroid.notification.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One stored alert with a row per recipient, which is what the README used to say to query by hand. */
public record NotificationDetail(

        String eventId,
        String asteroidId,
        String asteroidName,
        LocalDate closeApproachDate,
        BigDecimal missDistanceKilometers,
        double estimatedDiameterAvgMeters,
        Instant occurredAt,
        Instant createdAt,
        List<DeliveryView> deliveries
) {
}
