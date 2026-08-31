package com.arthur.asteroid.notification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

/**
 * One notification's delivery to one subscriber.
 *
 * <p>This table is the reason the model changed. Previously {@code emailSent} was
 * a single boolean on the notification itself, with no link to the recipient — so
 * there was no way to know who had actually received what, and a partially
 * successful batch was indistinguishable from a complete one.
 *
 * <p>Both associations are LAZY: the dispatch worker claims rows in batches and
 * only needs the subscriber's address, so eager loading would pull notifications
 * it does not read.
 */
@Entity
@Table(
        name = "notification_delivery",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_delivery_notification_subscriber",
                columnNames = {"notification_id", "subscriber_id"}))
@Getter
@Setter
@NoArgsConstructor
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private Subscriber subscriber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static NotificationDelivery pending(Notification notification, Subscriber subscriber, Instant now) {
        final NotificationDelivery delivery = new NotificationDelivery();
        delivery.notification = notification;
        delivery.subscriber = subscriber;
        delivery.status = DeliveryStatus.PENDING;
        delivery.createdAt = now;
        return delivery;
    }

    /** Called only after the mail server has accepted the message. */
    public void markSent(Instant now) {
        this.status = DeliveryStatus.SENT;
        this.sentAt = now;
        this.attempts++;
        this.lastError = null;
    }

    /**
     * Records a failed attempt, giving up once {@code maxAttempts} is reached.
     * Staying PENDING is what lets the next scheduler tick retry.
     */
    public void markFailed(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = error == null ? null
                : error.substring(0, Math.min(error.length(), 1000));
        if (this.attempts >= maxAttempts) {
            this.status = DeliveryStatus.FAILED;
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof NotificationDelivery that && id != null && Objects.equals(id, that.id));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
