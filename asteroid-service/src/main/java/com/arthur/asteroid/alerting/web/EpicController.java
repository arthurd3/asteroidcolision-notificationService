package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.nasa.NasaEpicClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only access to EPIC imagery, including the image proxy.
 *
 * <p>The proxy is the reason this controller is more than a passthrough. NASA's EPIC
 * archive requires the API key as a query parameter, so the bytes have to be fetched
 * server-side; a browser can never be given the real URL. See
 * {@link EpicImageAssembler} for the validation that keeps that from turning into an
 * open proxy.
 */
@RestController
@RequestMapping("/api/v1/nasa/epic")
public class EpicController {

    private final NasaEpicClient epicClient;
    private final EpicImageAssembler assembler;
    private final NasaProperties properties;

    public EpicController(NasaEpicClient epicClient,
                          EpicImageAssembler assembler,
                          NasaProperties properties) {
        this.epicClient = epicClient;
        this.assembler = assembler;
        this.properties = properties;
    }

    /**
     * Metadata for one day's frames, or the most recent set when no date is given.
     *
     * <p>Returns {@link EpicImageView}, not the raw {@code EpicImage}: the raw record
     * has everything needed to build a key-bearing archive URL, and the view does not.
     */
    @GetMapping("/natural")
    public ResponseEntity<List<EpicImageView>> naturalImages(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        final List<EpicImageView> views = epicClient.naturalImages(date).stream()
                .map(image -> assembler.toView(image, "natural"))
                .toList();
        return ResponseEntity.ok(views);
    }

    /** Every date the natural-colour archive has frames for, for a date picker. */
    @GetMapping("/natural/dates")
    public ResponseEntity<List<LocalDate>> availableDates() {
        return ResponseEntity.ok(epicClient.availableNaturalDates());
    }

    /**
     * Serves one archive PNG through this service, so the API key stays server-side.
     *
     * <p>Every path segment is validated by {@link EpicImageAssembler#archivePath}
     * before it becomes part of an upstream URL. The response is cacheable for a long
     * time because the archive is immutable once published: a frame from a given
     * second never changes.
     */
    @GetMapping(value = "/image/{collection}/{year}/{month}/{day}/{image}",
            produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String collection,
                                        @PathVariable int year,
                                        @PathVariable int month,
                                        @PathVariable int day,
                                        @PathVariable String image) {

        final String archivePath = assembler.archivePath(collection, year, month, day, image);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(properties.epic().imageCacheTtl()).cachePublic().immutable())
                .body(epicClient.naturalImagePng(archivePath));
    }
}
