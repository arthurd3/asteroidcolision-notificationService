package com.arthur.asteroid.alerting.domain;

import java.time.LocalDate;

/**
 * Outcome of one scan, returned to the caller so a request reports what it did
 * rather than an empty body.
 *
 * @param scanned   objects returned by the feed for the window
 * @param hazardous how many of those were flagged potentially hazardous
 * @param published how many events the broker acknowledged. Lower than
 *                  {@code hazardous} means some sends failed, or some objects
 *                  lacked the approach data needed to build an event
 */
public record AlertSummary(
        LocalDate from,
        LocalDate to,
        int scanned,
        int hazardous,
        int published
) {
}
