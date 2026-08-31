package com.arthur.asteroid.notification.web;

import com.arthur.asteroid.notification.persistence.DeliveryStatus;

import java.time.Instant;

/**
 * What happened when this notification was emailed to one recipient.
 *
 * <p>Built by a JPQL constructor expression rather than mapped from an entity - see
 * {@code NotificationDeliveryRepository#findViewsByEventId} for why that matters
 * with {@code open-in-view: false}.
 *
 * @param lastError whatever the mail server said, truncated to 1000 characters.
 *                  Untrusted text from outside this system: anything rendering it
 *                  into HTML must escape it
 */
public record DeliveryView(

        String recipientEmail,
        String recipientName,
        DeliveryStatus status,
        int attempts,
        Instant sentAt,
        String lastError,
        Instant createdAt
) {
}
