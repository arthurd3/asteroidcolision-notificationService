package com.arthur.asteroid.notification.persistence;

public enum DeliveryStatus {

    /** Created, not yet attempted. */
    PENDING,

    /** The mail server accepted the message. */
    SENT,

    /** Every attempt failed; see {@code lastError}. */
    FAILED
}
