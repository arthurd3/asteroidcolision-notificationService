package com.arthur.asteroid.alerting.nasa.dto.epic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * One full-disc photograph of Earth from the EPIC camera aboard DSCOVR.
 *
 * <p>Two things make EPIC the most interesting of these APIs to model.
 *
 * <p>First, {@code date} is {@code "2026-08-29 00:41:06"} - a space between the date
 * and the time, not the {@code T} that ISO-8601 requires. Jackson cannot parse it
 * without being told the pattern, so the {@link JsonFormat} below is load bearing
 * rather than decoration.
 *
 * <p>Second, the response contains no URL for the actual photograph. It has to be
 * assembled from the date and the {@code image} name, and the archive that serves it
 * requires the API key as a query parameter - so unlike APOD, an EPIC image URL can
 * never be handed to a browser. See {@code EpicImageAssembler}, which is the only
 * place allowed to build one.
 *
 * @param identifier a timestamp-shaped id. Deliberately NOT used to build the
 *                   archive path: it can differ from {@code date} by a few minutes,
 *                   and using it yields a 404 for frames that cross midnight UTC
 * @param image      the file name, without extension, e.g. "epic_1b_20260829004554"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EpicImage(

        String identifier,
        String caption,
        String image,
        String version,
        @JsonProperty("centroid_coordinates") EpicCoordinates centroidCoordinates,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime date
) {

    /**
     * The archive path for this frame, relative to the API root and without the key.
     *
     * <p>e.g. {@code /EPIC/archive/natural/2026/08/29/png/epic_1b_20260829004554.png}.
     * The date parts come from {@link #date}, which is what the archive is keyed by.
     */
    public String archivePath(final String collection) {
        return String.format(Locale.ROOT, "/EPIC/archive/%s/%04d/%02d/%02d/png/%s.png",
                collection, date.getYear(), date.getMonthValue(), date.getDayOfMonth(), image);
    }
}
