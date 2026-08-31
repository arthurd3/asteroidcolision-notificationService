package com.arthur.asteroid.alerting.web;

/**
 * The caller asked for something this service will refuse before calling NASA.
 *
 * <p>Carries its own RFC 9457 title and type slug, so adding an endpoint with a new
 * kind of bad input does not mean adding another exception class and another
 * {@code @ExceptionHandler} method beside it. There is one handler for the whole
 * family in {@link ApiExceptionHandler}.
 *
 * <p>Validating here rather than letting NASA answer 400 is not only about a better
 * message: DEMO_KEY allows 30 requests an hour across every api.nasa.gov endpoint, so
 * a request that is knowably invalid must not spend one of them.
 */
public class InvalidRequestException extends RuntimeException {

    /** Human-readable summary, e.g. "Invalid date". */
    private final transient String title;

    /** Last path segment of the problem type URI, e.g. "invalid-date". */
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
