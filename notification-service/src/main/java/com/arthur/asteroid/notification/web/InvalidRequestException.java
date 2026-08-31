package com.arthur.asteroid.notification.web;

/**
 * The caller asked for something this service will refuse.
 *
 * <p>Carries its own RFC 9457 title and type slug, so a new validation rule does not
 * mean a new exception class and a new handler method beside it. Mirrors the type of
 * the same name in asteroid-service; the two services deliberately do not share a
 * module for this, because {@code contracts} is the Kafka wire contract and nothing
 * else - putting HTTP plumbing in it would couple the event schema to the web layer.
 */
public class InvalidRequestException extends RuntimeException {

    private final transient String title;
    private final transient String slug;

    public InvalidRequestException(final String title, final String slug, final String message) {
        super(message);
        this.title = title;
        this.slug = slug;
    }

    public String title() {
        return title;
    }

    public String slug() {
        return slug;
    }
}
