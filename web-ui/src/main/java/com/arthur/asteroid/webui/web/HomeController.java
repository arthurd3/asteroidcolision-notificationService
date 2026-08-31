package com.arthur.asteroid.webui.web;

import com.arthur.asteroid.webui.backend.AsteroidServiceClient;
import com.arthur.asteroid.webui.backend.BackendUnavailableException;
import com.arthur.asteroid.webui.backend.NotificationServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.function.Supplier;

/**
 * The dashboard.
 *
 * <p>Every panel is fetched independently and each one is allowed to fail on its
 * own. That is the one design decision on this page worth stating: the home page
 * fans out to three or four calls across two services, and if a single failure took
 * the whole page to the error view, stopping notification-service would make the
 * NASA half of the site disappear too. Instead a dead backend greys out its own card
 * and the rest of the page still renders.
 *
 * <p>Note this is the only controller that swallows {@link BackendUnavailableException}.
 * Everywhere else the page IS the backend's data, so failing to the error view is
 * the honest outcome.
 */
@Slf4j
@Controller
public class HomeController {

    private final AsteroidServiceClient asteroidService;
    private final NotificationServiceClient notificationService;
    private final Formats formats;

    public HomeController(AsteroidServiceClient asteroidService,
                          NotificationServiceClient notificationService,
                          Formats formats) {
        this.asteroidService = asteroidService;
        this.notificationService = notificationService;
        this.formats = formats;
    }

    @GetMapping("/")
    public String home(final Model model) {
        model.addAttribute("pageTitle", "Overview");
        model.addAttribute("fmt", formats);

        model.addAttribute("apod", optional("picture of the day",
                () -> asteroidService.pictureOfTheDay(null)));
        model.addAttribute("feed", optional("near-earth feed",
                () -> asteroidService.neoFeed(null, null)));
        model.addAttribute("epic", optional("earth imagery",
                () -> asteroidService.epicNatural(null)));
        model.addAttribute("stats", optional("delivery statistics",
                notificationService::stats));

        model.addAttribute("asteroidServiceName", asteroidService.displayName());
        model.addAttribute("notificationServiceName", notificationService.displayName());
        return "home";
    }

    /**
     * Fetches one panel, returning null instead of propagating.
     *
     * <p>The JSP tests each attribute for null and renders an "unavailable" card in
     * its place, so the page degrades a card at a time.
     */
    private <T> T optional(final String what, final Supplier<T> call) {
        try {
            return call.get();
        } catch (BackendUnavailableException ex) {
            log.warn("Dashboard panel '{}' unavailable: {}", what, ex.getMessage());
            return null;
        }
    }
}
