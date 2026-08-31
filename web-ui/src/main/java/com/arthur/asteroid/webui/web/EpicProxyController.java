package com.arthur.asteroid.webui.web;

import com.arthur.asteroid.webui.backend.AsteroidServiceClient;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Serves EPIC frames through this service, so the browser talks to one origin.
 *
 * <p>This is the second of two proxy hops and the only {@code @RestController} in the
 * module - it returns bytes, not a view. The first hop, in asteroid-service, exists
 * because NASA's EPIC archive requires the API key as a query parameter and the key
 * must not reach a page. This one exists so the rendered HTML contains no
 * cross-origin image requests at all.
 *
 * <p>It is worth being honest in the docs that {@code epic.gsfc.nasa.gov} serves the
 * same PNGs with no key whatsoever, which would make both hops unnecessary. Two
 * things are bought by proxying anyway: a single origin, and a cache this project
 * controls in front of a rate-limited upstream. What it costs is moving a couple of
 * megabytes through two JVMs for a photograph.
 *
 * <p>The segments are re-validated here rather than trusted. They arrive from a page
 * this service rendered, but "it came from our own HTML" is not a property the server
 * can check, and these values end up in a request path.
 */
@RestController
public class EpicProxyController {

    private static final List<String> COLLECTIONS = List.of("natural", "enhanced");
    private static final Pattern IMAGE_NAME = Pattern.compile("^epic_[A-Za-z0-9]{1,8}_\\d{14,20}$");

    private final AsteroidServiceClient asteroidService;

    public EpicProxyController(AsteroidServiceClient asteroidService) {
        this.asteroidService = asteroidService;
    }

    @GetMapping(value = "/epic/image/{collection}/{year}/{month}/{day}/{image}",
            produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String collection,
                                        @PathVariable int year,
                                        @PathVariable int month,
                                        @PathVariable int day,
                                        @PathVariable String image) {

        if (!COLLECTIONS.contains(collection) || !IMAGE_NAME.matcher(image).matches()) {
            return ResponseEntity.notFound().build();
        }
        final String backendPath = String.format(Locale.ROOT,
                "/api/v1/nasa/epic/image/%s/%04d/%02d/%02d/%s", collection, year, month, day, image);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                // the archive is immutable once published, so a browser can keep these
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable())
                .body(asteroidService.epicImage(backendPath));
    }
}
