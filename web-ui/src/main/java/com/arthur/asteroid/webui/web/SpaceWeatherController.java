package com.arthur.asteroid.webui.web;

import com.arthur.asteroid.webui.backend.AsteroidServiceClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * DONKI space weather: coronal mass ejections, geomagnetic storms and solar flares.
 *
 * <p>One type at a time, chosen by a query parameter, rather than all three at once.
 * Each is a separate upstream call that can take ninety seconds, so a page that
 * fetched all three would routinely take four minutes and hold three request threads
 * on two services while doing it.
 */
@Controller
public class SpaceWeatherController {

    private static final List<String> TYPES = List.of("cme", "gst", "flr");

    private final AsteroidServiceClient asteroidService;
    private final Formats formats;

    public SpaceWeatherController(AsteroidServiceClient asteroidService, Formats formats) {
        this.asteroidService = asteroidService;
        this.formats = formats;
    }

    @GetMapping("/space-weather")
    public String spaceWeather(
            @RequestParam(defaultValue = "cme") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            final Model model) {

        final String selected = TYPES.contains(type) ? type : "cme";

        model.addAttribute("pageTitle", "Space weather");
        model.addAttribute("fmt", formats);
        model.addAttribute("type", selected);
        model.addAttribute("requestedFrom", from);
        model.addAttribute("requestedTo", to);

        switch (selected) {
            case "gst" -> model.addAttribute("storms", asteroidService.geomagneticStorms(from, to));
            case "flr" -> model.addAttribute("flares", asteroidService.solarFlares(from, to));
            default -> model.addAttribute("ejections", asteroidService.coronalMassEjections(from, to));
        }
        return "space-weather";
    }
}
