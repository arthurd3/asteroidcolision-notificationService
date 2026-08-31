package com.arthur.asteroid.webui.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * What notification-service has stored and delivered.
 *
 * <p>The only part of this front end that shows the pipeline's own output rather
 * than NASA's data.
 */
public final class NotificationViews {

    private NotificationViews() {
    }

    /** Deliberately mirrors the explicit envelope notification-service returns. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public List<T> content() {
            return content == null ? List.of() : content;
        }

        public boolean hasPrevious() {
            return page > 0;
        }

        public boolean hasNext() {
            return page + 1 < totalPages;
        }

        /** One-based, because a pager that starts at "page 0 of 5" reads as a bug. */
        public int displayPage() {
            return page + 1;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotificationSummaryView(
            String eventId, String asteroidId, String asteroidName,
            LocalDate closeApproachDate, BigDecimal missDistanceKilometers,
            double estimatedDiameterAvgMeters, Instant occurredAt, Instant createdAt,
            long pending, long sent, long failed) {

        public boolean hasFailures() {
            return failed > 0;
        }
    }

    /**
     * @param lastError whatever the mail server said. Untrusted text from outside
     *                  the system: the JSP must render it through {@code <c:out>}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeliveryDetailView(
            String recipientEmail, String recipientName, String status,
            int attempts, Instant sentAt, String lastError, Instant createdAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotificationDetailView(
            String eventId, String asteroidId, String asteroidName,
            LocalDate closeApproachDate, BigDecimal missDistanceKilometers,
            double estimatedDiameterAvgMeters, Instant occurredAt, Instant createdAt,
            List<DeliveryDetailView> deliveries) {

        public List<DeliveryDetailView> deliveries() {
            return deliveries == null ? List.of() : deliveries;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeliveryStatsView(
            long notifications, long enabledSubscribers,
            long pending, long sent, long failed, Instant lastIngestedAt) {

        public boolean everIngested() {
            return lastIngestedAt != null;
        }
    }
}
