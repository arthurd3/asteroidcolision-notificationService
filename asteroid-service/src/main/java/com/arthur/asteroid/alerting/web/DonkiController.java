package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.nasa.NasaDonkiClient;
import com.arthur.asteroid.alerting.nasa.dto.donki.CoronalMassEjection;
import com.arthur.asteroid.alerting.nasa.dto.donki.GeomagneticStorm;
import com.arthur.asteroid.alerting.nasa.dto.donki.SolarFlare;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only access to NASA's space-weather database.
 *
 * <p><strong>These endpoints are slow.</strong> A query over a month of events
 * routinely takes 60 to 90 seconds upstream, so a caller needs a read timeout well
 * over the usual ten seconds, and the front end warns before making the request.
 * That latency is why {@code asteroid.nasa.donki} has its own timeout budget, its
 * own circuit breaker and a bulkhead.
 */
@RestController
@RequestMapping("/api/v1/nasa/donki")
public class DonkiController {

    private final NasaDonkiClient donkiClient;
    private final NasaProperties properties;
    private final Clock clock;

    public DonkiController(NasaDonkiClient donkiClient, NasaProperties properties, Clock clock) {
        this.donkiClient = donkiClient;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Coronal mass ejections in the window.
     *
     * <p>Both parameters are optional; omitting them queries the last
     * {@code asteroid.nasa.donki.default-window-days}.
     */
    @GetMapping("/cme")
    public ResponseEntity<List<CoronalMassEjection>> coronalMassEjections(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        final Window window = window(from, to);
        return ResponseEntity.ok(donkiClient.coronalMassEjections(window.from(), window.to()));
    }

    /**
     * Geomagnetic storms in the window.
     *
     * <p>The response shape is unverified - see {@link GeomagneticStorm}.
     */
    @GetMapping("/gst")
    public ResponseEntity<List<GeomagneticStorm>> geomagneticStorms(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        final Window window = window(from, to);
        return ResponseEntity.ok(donkiClient.geomagneticStorms(window.from(), window.to()));
    }

    /**
     * Solar flares in the window.
     *
     * <p>The response shape is unverified - see {@link SolarFlare}.
     */
    @GetMapping("/flr")
    public ResponseEntity<List<SolarFlare>> solarFlares(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        final Window window = window(from, to);
        return ResponseEntity.ok(donkiClient.solarFlares(window.from(), window.to()));
    }

    /**
     * Resolves and validates the window.
     *
     * <p>The cap is not NASA's - DONKI will happily accept a year and spend minutes
     * answering. It is this service's, because the response grows without bound and
     * so does the time a request thread is held.
     */
    private Window window(final LocalDate from, final LocalDate to) {
        if (ScanWindow.isAbsent(from, to)) {
            final LocalDate today = LocalDate.now(clock);
            return new Window(today.minusDays(properties.donki().defaultWindowDays()), today);
        }
        ScanWindow.validate(from, to, properties.donki().maxWindowDays());
        return new Window(from, to);
    }

    private record Window(LocalDate from, LocalDate to) {
    }
}
