package com.arthur.asteroid.webui.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formats values for display, in Java rather than in the page.
 *
 * <p>This exists because JSTL's {@code <fmt:formatDate>} predates {@code java.time}
 * and still only accepts {@code java.util.Date} and {@code Calendar}. Handed a
 * {@code LocalDate} it fails at render time, inside the JSP, with a message about
 * types that says nothing about which page or which field. Converting in the page
 * would mean scriptlets; converting here means the view models carry strings that
 * are already right.
 *
 * <p>Everything is UTC and explicit about it. The data is astronomical - a close
 * approach happens at an instant, not in a timezone - and a page that renders times
 * in whatever zone the server happens to be in is quietly wrong for most readers.
 */
@Component
public class Formats {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MINUTE =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    /** e.g. "04 Mar 2026". Empty rather than "null" when absent. */
    public String date(final LocalDate date) {
        return date == null ? "" : DAY.format(date);
    }

    /** e.g. "29 Aug 2026 00:41 UTC". */
    public String dateTime(final LocalDateTime dateTime) {
        return dateTime == null ? "" : MINUTE.format(dateTime) + " UTC";
    }

    /** e.g. "31 Aug 2026 09:00 UTC". */
    public String instant(final Instant instant) {
        return instant == null ? "" : MINUTE.format(instant.atZone(ZoneOffset.UTC)) + " UTC";
    }

    /** e.g. "02 Aug 2026 10:45 UTC". */
    public String offsetDateTime(final OffsetDateTime dateTime) {
        return dateTime == null ? ""
                : MINUTE.format(dateTime.withOffsetSameInstant(ZoneOffset.UTC)) + " UTC";
    }

    /**
     * A distance in kilometres, grouped and without meaningless precision.
     *
     * <p>NASA sends these as many-digit decimal strings. "54321.5 km" is useful;
     * "54321.50000000001 km" is noise, and "5.43215E+04" is worse.
     */
    public String kilometers(final String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            return grouped(new BigDecimal(raw).setScale(0, RoundingMode.HALF_UP)) + " km";
        } catch (NumberFormatException ex) {
            // a value that will not parse is shown as sent rather than hidden
            return raw;
        }
    }

    public String kilometers(final BigDecimal value) {
        return value == null ? "" : grouped(value.setScale(0, RoundingMode.HALF_UP)) + " km";
    }

    /** e.g. "469 m", or "1.2 km" once an object is large enough for metres to read oddly. */
    public String meters(final Double value) {
        if (value == null) {
            return "";
        }
        if (value >= 1000) {
            return String.format(Locale.ENGLISH, "%.1f km", value / 1000);
        }
        return String.format(Locale.ENGLISH, "%.0f m", value);
    }

    /** Lunar distances, the unit people actually have intuition for. */
    public String lunar(final String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            return String.format(Locale.ENGLISH, "%.2f LD", new BigDecimal(raw).doubleValue());
        } catch (NumberFormatException ex) {
            return raw;
        }
    }

    /** e.g. "30.9 km/s". */
    public String kilometersPerSecond(final String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            return String.format(Locale.ENGLISH, "%.1f km/s", new BigDecimal(raw).doubleValue());
        } catch (NumberFormatException ex) {
            return raw;
        }
    }

    /** Thousands separators, e.g. "62,193". */
    public String count(final long value) {
        return String.format(Locale.ENGLISH, "%,d", value);
    }

    private static String grouped(final BigDecimal value) {
        return String.format(Locale.ENGLISH, "%,d", value.toBigInteger());
    }
}
