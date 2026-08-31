package com.arthur.asteroid.webui.web;

import com.arthur.asteroid.webui.backend.AsteroidServiceClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/** Astronomy Picture of the Day, with prev/next navigation. */
@Controller
public class ApodController {

    /** APOD's first entry; there is nothing before this. */
    static final LocalDate FIRST_ENTRY = LocalDate.of(1995, 6, 16);

    private final AsteroidServiceClient asteroidService;
    private final Formats formats;

    public ApodController(AsteroidServiceClient asteroidService, Formats formats) {
        this.asteroidService = asteroidService;
        this.formats = formats;
    }

    @GetMapping("/apod")
    public String apod(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            final Model model) {

        final var entry = asteroidService.pictureOfTheDay(date);

        model.addAttribute("pageTitle", "Picture of the day");
        model.addAttribute("fmt", formats);
        model.addAttribute("apod", entry);

        // Built from the entry's own date rather than the requested one, so "previous"
        // still works when the request had no date at all.
        final LocalDate shown = entry.date();
        model.addAttribute("previousDate",
                shown != null && shown.isAfter(FIRST_ENTRY) ? shown.minusDays(1) : null);
        model.addAttribute("nextDate",
                shown != null && shown.isBefore(LocalDate.now()) ? shown.plusDays(1) : null);
        return "apod";
    }
}
