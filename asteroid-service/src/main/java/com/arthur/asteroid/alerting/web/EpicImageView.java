package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.nasa.dto.epic.EpicCoordinates;

import java.time.LocalDateTime;

/**
 * An EPIC frame as this service exposes it.
 *
 * <p>The whole point of this record is what it does <em>not</em> have: a field
 * capable of holding a NASA URL. The archive that serves EPIC photographs requires
 * the API key as a query parameter, so any response carrying an archive URL would
 * publish the credential to every browser that rendered the page - and to every
 * proxy and history file in between.
 *
 * <p>{@code imagePath} is a path on this service instead. It has no query string and
 * no key, and it is safe to write straight into an {@code <img src>}. Because there
 * is nowhere on this record to put a real NASA URL, that property cannot regress by
 * accident - only by someone adding a field on purpose.
 *
 * @param imagePath e.g. {@code /api/v1/nasa/epic/image/natural/2026/08/29/epic_1b_20260829004554}
 */
public record EpicImageView(

        String identifier,
        String caption,
        String image,
        LocalDateTime date,
        EpicCoordinates centroidCoordinates,
        String imagePath
) {
}
