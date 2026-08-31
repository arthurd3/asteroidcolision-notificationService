package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.domain.AlertSummary;
import com.arthur.asteroid.alerting.domain.AsteroidAlertingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/asteroid-alerting")
public class AsteroidAlertingController {

    private final AsteroidAlertingService alertingService;
    private final NasaProperties nasaProperties;

    public AsteroidAlertingController(AsteroidAlertingService alertingService,
                                      NasaProperties nasaProperties) {
        this.alertingService = alertingService;
        this.nasaProperties = nasaProperties;
    }

    /**
     * Scans a window of the NASA feed and publishes any hazardous approaches.
     *
     * <p>Both parameters are optional; omitting them scans today through
     * {@code asteroid.nasa.lookahead-days} ahead.
     *
     * <p>Returns 200 with a summary. The previous version returned 202 Accepted
     * and an empty body while doing all the work synchronously, so the status code
     * promised background processing that was not happening and the caller learned
     * nothing about the outcome.
     */
    @PostMapping("/alert")
    public ResponseEntity<AlertSummary> alert(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (ScanWindow.isAbsent(from, to)) {
            return ResponseEntity.ok(alertingService.alert());
        }
        // the same rules NeoCatalogController#feed applies, defined once
        ScanWindow.validate(from, to, ScanWindow.MAX_FEED_DAYS);
        return ResponseEntity.ok(alertingService.alert(from, to));
    }

    /** Exposed so the handler can report the configured default in error detail. */
    int defaultLookaheadDays() {
        return nasaProperties.neo().lookaheadDays();
    }
}
