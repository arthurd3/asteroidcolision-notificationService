package com.arthur.asteroid.webui.web;

import com.arthur.asteroid.webui.backend.NotificationServiceClient;
import com.arthur.asteroid.webui.config.BackendProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * What the pipeline actually did: stored alerts and per-recipient delivery status.
 *
 * <p>The only pages here that show this project's own data rather than NASA's, and
 * the reason notification-service grew a read API. Before it existed, the answer to
 * "did that alert reach anyone" was a MySQL query the README told you to type.
 */
@Controller
public class HistoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationServiceClient notificationService;
    private final BackendProperties properties;
    private final Formats formats;

    public HistoryController(NotificationServiceClient notificationService,
                             BackendProperties properties,
                             Formats formats) {
        this.notificationService = notificationService;
        this.properties = properties;
        this.formats = formats;
    }

    @GetMapping("/history")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) Integer size,
                       final Model model) {

        final int pageSize = Math.clamp(
                size == null ? properties.pageSize() : size, 1, MAX_PAGE_SIZE);

        model.addAttribute("pageTitle", "Alert history");
        model.addAttribute("fmt", formats);
        model.addAttribute("history", notificationService.history(Math.max(page, 0), pageSize));
        model.addAttribute("size", pageSize);
        return "history";
    }

    @GetMapping("/history/{eventId}")
    public String detail(@PathVariable String eventId, final Model model) {
        model.addAttribute("pageTitle", "Alert detail");
        model.addAttribute("fmt", formats);
        model.addAttribute("notification", notificationService.detail(eventId));
        return "history-detail";
    }
}
