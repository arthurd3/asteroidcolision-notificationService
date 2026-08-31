package com.arthur.asteroid.notification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A hazardous approach received from the alert topic.
 *
 * <p>Deliberately not a Lombok {@code @Data} class. {@code @Data} generates
 * {@code equals}/{@code hashCode} over every field including the generated id, so
 * an entity's hash changes the moment IDENTITY assigns that id after persist —
 * which breaks {@code HashSet} membership and violates the JPA identity contract.
 * Equality here is on {@code eventId}, the natural key, which is stable from
 * construction.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Producer-assigned idempotency key. Unique, which is what makes re-delivery a no-op. */
    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "asteroid_id", nullable = false, length = 64)
    private String asteroidId;

    @Column(name = "asteroid_name", nullable = false, length = 255)
    private String asteroidName;

    @Column(name = "close_approach_date", nullable = false)
    private LocalDate closeApproachDate;

    /** Explicit precision: Hibernate otherwise defaults to decimal(38,2) and truncates. */
    @Column(name = "miss_distance_kilometers", nullable = false, precision = 20, scale = 4)
    private BigDecimal missDistanceKilometers;

    @Column(name = "estimated_diameter_avg_meters", nullable = false)
    private double estimatedDiameterAvgMeters;

    /** When the producer observed the approach. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** When this service stored it. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Notification that && Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "Notification[eventId=%s, asteroid=%s, closeApproach=%s]"
                .formatted(eventId, asteroidName, closeApproachDate);
    }
}
