package com.arthur.asteroid.webui.backend;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * A backend could not answer.
 *
 * <p>Carries enough for the error page to say something a person can act on: which
 * service, what status, and - when the backend spoke RFC 9457 - the title and detail
 * it supplied. That last part is where the {@code problem+json} handlers in both
 * backends finally pay off: "The NASA feed is failing repeatedly and calls are being
 * shed" is a sentence a user can do something with, and it was written once, in the
 * service that knows it.
 *
 * @param status  null when the connection never opened at all, which is the ordinary
 *                case of "you forgot to start that service"
 * @param problem null when the body was not problem+json
 */
public class BackendUnavailableException extends RuntimeException {

    private final transient String service;
    private final transient HttpStatusCode status;
    private final transient ProblemDetail problem;

    public BackendUnavailableException(final String service,
                                       final HttpStatusCode status,
                                       final ProblemDetail problem,
                                       final String message,
                                       final Throwable cause) {
        super(message, cause);
        this.service = service;
        this.status = status;
        this.problem = problem;
    }

    public String service() {
        return service;
    }

    public HttpStatusCode status() {
        return status;
    }

    public ProblemDetail problem() {
        return problem;
    }

    /** True when nothing was listening, as opposed to answering with an error. */
    public boolean unreachable() {
        return status == null;
    }
}
