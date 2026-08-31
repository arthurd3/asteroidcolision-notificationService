package com.arthur.asteroid.alerting.nasa;

import com.arthur.asteroid.alerting.nasa.dto.epic.EpicImage;

import java.time.LocalDate;
import java.util.List;

/**
 * Reads NASA's EPIC imagery: full-disc photographs of Earth from the DSCOVR
 * spacecraft, roughly a dozen a day.
 */
public interface NasaEpicClient {

    /** Metadata for one day's frames, or the most recent day when {@code date} is null. */
    List<EpicImage> naturalImages(LocalDate date);

    /** Every date the natural-colour archive has frames for. */
    List<LocalDate> availableNaturalDates();

    /**
     * The PNG itself.
     *
     * @param archivePath a path produced by {@link EpicImage#archivePath}, or one
     *                    reassembled from <em>fully validated</em> request segments.
     *                    It is concatenated onto the API root with the key attached,
     *                    so an unvalidated value turns this into an open proxy
     */
    byte[] naturalImagePng(String archivePath);
}
