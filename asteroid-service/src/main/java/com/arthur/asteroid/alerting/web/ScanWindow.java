package com.arthur.asteroid.alerting.web;

import java.time.LocalDate;

/**
 * The from/to rules, in one place because two controllers enforce them.
 *
 * <p>They were inline in {@link AsteroidAlertingController}. Once the read-only feed
 * endpoint needed the identical four checks, leaving them there would have meant two
 * copies of a rule that comes from NASA rather than from us - and the copies would
 * have drifted the first time one of the messages was reworded.
 */
final class ScanWindow {

    /** The NEO feed rejects windows longer than this. */
    static final int MAX_FEED_DAYS = 7;

    private ScanWindow() {
    }

    /**
     * Validates a caller-supplied window.
     *
     * @param maxDays largest span this endpoint accepts
     * @throws InvalidScanWindowException if the pair is incomplete, reversed or too wide
     */
    static void validate(final LocalDate from, final LocalDate to, final int maxDays) {
        if (from == null || to == null) {
            throw new InvalidScanWindowException("'from' and 'to' must be supplied together");
        }
        if (to.isBefore(from)) {
            throw new InvalidScanWindowException("'to' must not be before 'from'");
        }
        if (from.plusDays(maxDays).isBefore(to)) {
            throw new InvalidScanWindowException(
                    "the NASA feed accepts a window of at most " + maxDays + " days");
        }
    }

    /** True when neither bound was supplied, i.e. the caller wants the default window. */
    static boolean isAbsent(final LocalDate from, final LocalDate to) {
        return from == null && to == null;
    }
}
