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

import java.time.Instant;
import java.util.Objects;

/**
 * Someone who receives asteroid alerts.
 *
 * <p>Named {@code subscriber} rather than {@code user}: {@code user} is a reserved
 * word in MySQL 8 and fully reserved in PostgreSQL, so the old {@code @Table(name
 * = "user")} needed quoting to survive and would not have ported.
 */
@Entity
@Table(name = "subscriber")
@Getter
@Setter
@NoArgsConstructor
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Subscriber that && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email);
    }

    @Override
    public String toString() {
        return "Subscriber[email=%s, enabled=%s]".formatted(email, notificationEnabled);
    }
}
