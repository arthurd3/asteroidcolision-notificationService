package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.config.NasaProperties;
import com.arthur.asteroid.alerting.nasa.NasaNeoClient;
import com.arthur.asteroid.alerting.nasa.dto.neo.Asteroid;
import com.arthur.asteroid.alerting.nasa.dto.neo.NeoBrowsePage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only access to the near-Earth-object catalogue.
 *
 * <p>The counterpart to {@link AsteroidAlertingController}, which scans the same feed
 * in order to publish events. Nothing here publishes anything: these are {@code GET}s
 * that exist so the data can be looked at.
 */
@RestController
@RequestMapping("/api/v1/nasa/neo")
public class NeoCatalogController {

    /** NASA's own cap on the browse page size. */
    static final int MAX_PAGE_SIZE = 20;

    private final NasaNeoClient neoClient;
    private final NasaProperties properties;
    private final Clock clock;

    public NeoCatalogController(NasaNeoClient neoClient, NasaProperties properties, Clock clock) {
        this.neoClient = neoClient;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Objects approaching between two dates, at most seven days apart.
     *
     * <p>Both parameters are optional; omitting them scans today through
     * {@code asteroid.nasa.neo.lookahead-days} ahead.
     */
    @GetMapping("/feed")
    public ResponseEntity<FeedView> feed(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        final LocalDate start;
        final LocalDate end;
        if (ScanWindow.isAbsent(from, to)) {
            start = LocalDate.now(clock);
            end = start.plusDays(properties.neo().lookaheadDays());
        } else {
            ScanWindow.validate(from, to, ScanWindow.MAX_FEED_DAYS);
            start = from;
            end = to;
        }

        final List<Asteroid> objects = neoClient.findAsteroids(start, end);
        return ResponseEntity.ok(new FeedView(start, end, objects.size(), objects));
    }

    /**
     * One page of the full catalogue.
     *
     * <p>Declared before {@code /{id}} for readability; the ordering is not load
     * bearing, because the {@code id} mapping constrains itself to digits and
     * Spring's pattern matcher prefers a literal segment over a variable anyway.
     */
    @GetMapping("/browse")
    public ResponseEntity<NeoBrowsePage> browse(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {

        if (page < 0) {
            throw new InvalidRequestException("Invalid page", "invalid-page",
                    "'page' is zero-based and cannot be negative");
        }
        final int pageSize = size == null ? properties.neo().browsePageSize() : size;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("Invalid page size", "invalid-page-size",
                    "'size' must be between 1 and " + MAX_PAGE_SIZE);
        }
        return ResponseEntity.ok(neoClient.browse(page, pageSize));
    }

    /**
     * One object, including its orbit.
     *
     * <p>The id is constrained to digits in the mapping itself. That is not
     * cosmetic: the value is concatenated onto the upstream request path, so
     * anything other than a plain id could reshape the URL that gets sent to
     * api.nasa.gov with the API key attached.
     */
    @GetMapping("/{id:\\d{4,10}}")
    public ResponseEntity<Asteroid> lookup(@PathVariable String id) {
        return ResponseEntity.ok(neoClient.lookup(id));
    }

    /**
     * The feed's own shape is a map keyed by date, which is awkward to consume and
     * already flattened by the client. This is what that flattening looks like from
     * outside.
     */
    public record FeedView(LocalDate from, LocalDate to, int elementCount, List<Asteroid> objects) {
    }
}
