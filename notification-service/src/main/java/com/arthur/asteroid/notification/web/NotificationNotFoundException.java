package com.arthur.asteroid.notification.web;

/** No stored notification carries the requested event id. */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(final String eventId) {
        super("No notification with eventId '" + eventId + "'");
    }
}
