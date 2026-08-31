package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.nasa.dto.epic.EpicImage;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns EPIC metadata into something safe to render, and validates the way back.
 *
 * <p>One class owns both directions of the key-safety rule, so the rule has one test
 * rather than being asserted in two places and enforced in neither:
 *
 * <ul>
 *   <li>{@link #toView} builds a key-free path on this service, never a NASA URL;</li>
 *   <li>{@link #archivePath} rebuilds the upstream path from request segments, and
 *       refuses anything that does not look exactly like an EPIC frame.</li>
 * </ul>
 *
 * <p>That second method is the security-critical one. Its output is concatenated onto
 * {@code https://api.nasa.gov} <em>with the API key attached</em>, so without the
 * checks below a crafted {@code image} segment would turn this service into an open
 * proxy for any api.nasa.gov endpoint, signed with our credential. That is strictly
 * worse than the key leak the proxy exists to prevent.
 */
@Component
public class EpicImageAssembler {

    /** The two collections EPIC publishes. Anything else is not a collection. */
    static final List<String> COLLECTIONS = List.of("natural", "enhanced");

    /**
     * An EPIC frame name: {@code epic_1b_20260829004554}. Anchored, so a value
     * containing a slash, a dot or a percent-encoded traversal cannot match.
     */
    static final Pattern IMAGE_NAME = Pattern.compile("^epic_[A-Za-z0-9]{1,8}_\\d{14,20}$");

    /** Path prefix of the proxy endpoint that serves these images. */
    static final String PROXY_PREFIX = "/api/v1/nasa/epic/image/";

    /**
     * The public view of a frame, with a path on this service in place of the
     * key-bearing NASA archive URL.
     */
    public EpicImageView toView(final EpicImage image, final String collection) {
        return new EpicImageView(
                image.identifier(),
                image.caption(),
                image.image(),
                image.date(),
                image.centroidCoordinates(),
                proxyPath(image, collection));
    }

    private String proxyPath(final EpicImage image, final String collection) {
        return String.format(Locale.ROOT, "%s%s/%04d/%02d/%02d/%s",
                PROXY_PREFIX, collection,
                image.date().getYear(), image.date().getMonthValue(), image.date().getDayOfMonth(),
                image.image());
    }

    /**
     * Rebuilds the upstream archive path from validated request segments.
     *
     * <p>The {@code .png} suffix is appended here and never taken from the caller,
     * so the extension cannot be used to reach a different kind of resource.
     *
     * @throws InvalidRequestException if any segment is not exactly what EPIC uses
     */
    public String archivePath(final String collection,
                              final int year, final int month, final int day,
                              final String image) {

        if (!COLLECTIONS.contains(collection)) {
            throw new InvalidRequestException("Unknown EPIC collection", "unknown-epic-image",
                    "collection must be one of " + COLLECTIONS);
        }
        if (image == null || !IMAGE_NAME.matcher(image).matches()) {
            throw new InvalidRequestException("Unknown EPIC image", "unknown-epic-image",
                    "'" + image + "' is not an EPIC frame name");
        }
        final LocalDate date;
        try {
            // rejects 2026-13-40 and similar before it becomes part of a URL
            date = LocalDate.of(year, month, day);
        } catch (java.time.DateTimeException ex) {
            throw new InvalidRequestException("Unknown EPIC image", "unknown-epic-image",
                    "not a real date: " + year + "-" + month + "-" + day);
        }
        return String.format(Locale.ROOT, "/EPIC/archive/%s/%04d/%02d/%02d/png/%s.png",
                collection, date.getYear(), date.getMonthValue(), date.getDayOfMonth(), image);
    }
}
