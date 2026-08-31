package com.arthur.asteroid.webui.web;

import com.arthur.asteroid.webui.backend.AsteroidServiceClient;
import com.arthur.asteroid.webui.config.BackendProperties;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/** The three views over the near-Earth-object catalogue. */
@Controller
public class NeoController {

    /** NASA's own cap on browse page size. */
    private static final int MAX_PAGE_SIZE = 20;

    private final AsteroidServiceClient asteroidService;
    private final BackendProperties properties;
    private final Formats formats;

    public NeoController(AsteroidServiceClient asteroidService,
                         BackendProperties properties,
                         Formats formats) {
        this.asteroidService = asteroidService;
        this.properties = properties;
        this.formats = formats;
    }

    /** Objects approaching in a window of at most seven days. */
    @GetMapping("/neo")
    public String feed(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            final Model model) {

        model.addAttribute("pageTitle", "Near-Earth objects");
        model.addAttribute("fmt", formats);
        model.addAttribute("feed", asteroidService.neoFeed(from, to));
        model.addAttribute("requestedFrom", from);
        model.addAttribute("requestedTo", to);
        return "neo-feed";
    }

    /**
     * The full catalogue, a page at a time.
     *
     * <p>Genuinely paginated: upwards of 62,000 objects, so more than three thousand
     * pages at NASA's maximum page size of 20. There is no "fetch it all and filter".
     */
    @GetMapping("/neo/browse")
    public String browse(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(required = false) Integer size,
                         final Model model) {

        final int pageSize = Math.clamp(
                size == null ? properties.pageSize() : size, 1, MAX_PAGE_SIZE);

        model.addAttribute("pageTitle", "Catalogue");
        model.addAttribute("fmt", formats);
        model.addAttribute("browse", asteroidService.neoBrowse(Math.max(page, 0), pageSize));
        model.addAttribute("size", pageSize);
        return "neo-browse";
    }

    /**
     * One object, including its orbit.
     *
     * <p>The id is constrained to digits in the mapping, matching what asteroid-service
     * accepts, so a malformed id is a 404 here rather than a round trip that ends in
     * one.
     */
    @GetMapping("/neo/{id:\\d{4,10}}")
    public String detail(@PathVariable String id, final Model model) {
        model.addAttribute("pageTitle", "Object " + id);
        model.addAttribute("fmt", formats);
        model.addAttribute("asteroid", asteroidService.neoLookup(id));
        return "neo-detail";
    }
}
