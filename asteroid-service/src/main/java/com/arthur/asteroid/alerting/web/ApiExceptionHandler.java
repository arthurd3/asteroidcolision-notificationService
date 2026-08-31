package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.nasa.NasaUnavailableException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Translates failures into RFC 9457 problem responses.
 *
 * <p>Without this the service had no exception handling at all: a rate-limited or
 * unreachable NASA feed surfaced to the caller as a bare 500 with a stack trace.
 *
 * <p>It is also what lets web-ui render a useful error page rather than "something
 * went wrong": the front end decodes {@code application/problem+json} and shows the
 * title and detail from here.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String PROBLEM_BASE = "https://asteroid.arthur.com/problems/";

    /**
     * One handler for every kind of bad input, because each exception names its own
     * title and slug. A new endpoint with a new validation rule needs neither a new
     * exception class nor a new method here.
     */
    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail onInvalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.title(), ex.getMessage(), ex.slug());
    }

    @ExceptionHandler(CallNotPermittedException.class)
    ProblemDetail onCircuitOpen(CallNotPermittedException ex) {
        log.warn("Rejecting call: circuit breaker '{}' is open", ex.getCausingCircuitBreakerName());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Upstream temporarily unavailable",
                "The NASA API is failing repeatedly and calls are being shed. Retry shortly.",
                "upstream-unavailable");
    }

    /**
     * The DONKI bulkhead is full.
     *
     * <p>429 rather than 503: nothing is broken, the caller is simply one of too many
     * asking for a two-minute query at once, and retrying shortly will work.
     */
    @ExceptionHandler(BulkheadFullException.class)
    ProblemDetail onBulkheadFull(BulkheadFullException ex) {
        log.warn("Rejecting call: bulkhead '{}' is full", ex.getMessage());
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Too many concurrent requests",
                "This endpoint is slow enough that only a few callers are admitted at once. Retry shortly.",
                "too-many-concurrent-requests");
    }

    @ExceptionHandler(NasaUnavailableException.class)
    ProblemDetail onNasaUnavailable(NasaUnavailableException ex) {
        log.warn("NASA API call failed: {}", ex.getMessage());
        // getMessage() only, never the cause: the cause is a RestClientException whose
        // own message contains the request URI, and the URI carries the api_key.
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Upstream unavailable",
                ex.getMessage(), "upstream-unavailable");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String slug) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        // left null, Spring defaults it to "about:blank", which tells a client nothing
        problem.setType(URI.create(PROBLEM_BASE + slug));
        return problem;
    }
}
