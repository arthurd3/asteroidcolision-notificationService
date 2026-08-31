package com.arthur.asteroid.webui.web;

import com.arthur.asteroid.webui.backend.AsteroidServiceClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * Triggers a scan: the one write in this entire front end.
 *
 * <p>Everything else here is a {@code GET} over data that already exists. This posts
 * to asteroid-service, which reads the NASA feed and publishes an event per hazardous
 * approach - so a person clicking this button puts a message through Kafka, into
 * MySQL, and eventually into an inbox. It is the page that makes the pipeline
 * visible as a pipeline rather than as three services.
 *
 * <p>There is no CSRF token on the form. There is no Spring Security in this project
 * and no session, so there is currently nothing to forge into - but that stops being
 * true the moment anyone adds authentication, and it is called out in
 * the security document under docs/ rather than left to be discovered.
 */
@Controller
public class ScanController {

    private final AsteroidServiceClient asteroidService;
    private final Formats formats;

    public ScanController(AsteroidServiceClient asteroidService, Formats formats) {
        this.asteroidService = asteroidService;
        this.formats = formats;
    }

    @GetMapping("/scan")
    public String form(final Model model) {
        model.addAttribute("pageTitle", "Run a scan");
        model.addAttribute("fmt", formats);
        return "scan";
    }

    @PostMapping("/scan")
    public String scan(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            final Model model) {

        model.addAttribute("pageTitle", "Run a scan");
        model.addAttribute("fmt", formats);
        model.addAttribute("summary", asteroidService.triggerScan(from, to));
        return "scan";
    }
}
