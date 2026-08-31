package com.arthur.asteroid.notification.web;

import com.arthur.asteroid.notification.domain.NotificationQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to what this service has stored and delivered.
 *
 * <p>The first HTTP surface this service has ever had beyond Actuator. Until now the
 * only way to see whether an alert had reached its recipients was to open MySQL and
 * query {@code notification_delivery} by hand, which is what the README told people
 * to do.
 *
 * <p>Everything here is a {@code GET}. The write side of this service is the Kafka
 * listener, and it stays that way: an HTTP endpoint that created notifications would
 * be a second, unversioned way into the same table.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationHistoryController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final NotificationQueryService queryService;

    public NotificationHistoryController(NotificationQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Stored alerts, newest first.
     *
     * <p>Paginated because this table only grows: one row per hazardous approach per
     * scan, forever, and {@code notification_delivery} grows that times the number of
     * subscribers.
     */
    @GetMapping
    public ResponseEntity<PageResponse<NotificationSummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

        if (page < 0) {
            throw new InvalidRequestException("Invalid page", "invalid-page",
                    "'page' is zero-based and cannot be negative");
        }
        if (size < 1 || size > NotificationQueryService.MAX_PAGE_SIZE) {
            throw new InvalidRequestException("Invalid page size", "invalid-page-size",
                    "'size' must be between 1 and " + NotificationQueryService.MAX_PAGE_SIZE);
        }
        return ResponseEntity.ok(queryService.page(page, size));
    }

    /** One alert, with a row per recipient showing status, attempts and last error. */
    @GetMapping("/{eventId}")
    public ResponseEntity<NotificationDetail> detail(@PathVariable String eventId) {
        return ResponseEntity.ok(queryService.detail(eventId));
    }

    /** Totals for a dashboard tile. */
    @GetMapping("/stats")
    public ResponseEntity<DeliveryStats> stats() {
        return ResponseEntity.ok(queryService.stats());
    }
}
