package com.arthur.asteroid.notification.web;

import java.time.Instant;

/**
 * The pipeline in six numbers, for a dashboard tile.
 *
 * @param lastIngestedAt null when nothing has ever been ingested, which is a
 *                       meaningful state on a fresh checkout rather than an error
 */
public record DeliveryStats(

        long notifications,
        long enabledSubscribers,
        long pending,
        long sent,
        long failed,
        Instant lastIngestedAt
) {
}
