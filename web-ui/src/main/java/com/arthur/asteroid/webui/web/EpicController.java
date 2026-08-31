package com.arthur.asteroid.webui.web;

import com.arthur.asteroid.webui.backend.AsteroidServiceClient;
import com.arthur.asteroid.webui.backend.BackendUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/** Full-disc photographs of Earth from the DSCOVR spacecraft. */
@Slf4j
@Controller
public class EpicController {

    private final AsteroidServiceClient asteroidService;
    private final Formats formats;

    public EpicController(AsteroidServiceClient asteroidService, Formats formats) {
        this.asteroidService = asteroidService;
        this.formats = formats;
    }

    @GetMapping("/epic")
    public String epic(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            final Model model) {

        model.addAttribute("pageTitle", "Earth imagery");
        model.addAttribute("fmt", formats);
        model.addAttribute("frames", asteroidService.epicNatural(date));
        model.addAttribute("selectedDate", date);

        // The date picker is a convenience, not the page. If the archive index is
        // unavailable the frames themselves may still have loaded, and losing the
        // dropdown is better than losing the photographs.
        model.addAttribute("availableDates", availableDatesOrEmpty());
        return "epic";
    }

    private List<LocalDate> availableDatesOrEmpty() {
        try {
            return asteroidService.epicAvailableDates();
        } catch (BackendUnavailableException ex) {
            log.warn("EPIC date index unavailable: {}", ex.getMessage());
            return List.of();
        }
    }
}
