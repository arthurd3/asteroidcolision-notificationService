package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.nasa.NasaApodClient;
import com.arthur.asteroid.alerting.nasa.dto.apod.ApodEntry;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Read-only access to NASA's Astronomy Picture of the Day.
 *
 * <p>Read-only and therefore {@code GET}: unlike
 * {@link AsteroidAlertingController#alert}, nothing here publishes an event or
 * changes any state, so the response is cacheable and the request is repeatable.
 */
@RestController
@RequestMapping("/api/v1/nasa/apod")
public class ApodController {

    /** APOD's first entry. There is nothing before this and NASA answers 400. */
    static final LocalDate FIRST_ENTRY = LocalDate.of(1995, 6, 16);

    private final NasaApodClient apodClient;
    private final Clock clock;

    public ApodController(NasaApodClient apodClient, Clock clock) {
        this.apodClient = apodClient;
        this.clock = clock;
    }

    /**
     * @param date optional; omitting it asks for today's picture
     */
    @GetMapping
    public ResponseEntity<ApodEntry> pictureOfTheDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (date != null) {
            // Checked here rather than left to NASA, because DEMO_KEY allows 30 calls
            // an hour across every endpoint and a knowably invalid request must not
            // spend one of them.
            if (date.isBefore(FIRST_ENTRY)) {
                throw new InvalidRequestException("Invalid date", "invalid-date",
                        "APOD begins on " + FIRST_ENTRY + "; there is nothing earlier");
            }
            if (date.isAfter(LocalDate.now(clock))) {
                throw new InvalidRequestException("Invalid date", "invalid-date",
                        "'" + date + "' is in the future");
            }
        }
        return ResponseEntity.ok(apodClient.pictureOfTheDay(date));
    }
}
