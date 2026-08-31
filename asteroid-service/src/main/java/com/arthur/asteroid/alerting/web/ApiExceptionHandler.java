package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.nasa.NasaUnavailableException;
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
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String PROBLEM_BASE = "https://asteroid.arthur.com/problems/";

    @ExceptionHandler(InvalidScanWindowException.class)
    ProblemDetail onInvalidScanWindow(InvalidScanWindowException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid scan window", ex.getMessage(), "invalid-scan-window");
    }

    @ExceptionHandler(CallNotPermittedException.class)
    ProblemDetail onCircuitOpen(CallNotPermittedException ex) {
        log.warn("Rejecting scan: circuit breaker '{}' is open", ex.getCausingCircuitBreakerName());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Upstream temporarily unavailable",
                "The NASA feed is failing repeatedly and calls are being shed. Retry shortly.",
                "upstream-unavailable");
    }

    @ExceptionHandler(NasaUnavailableException.class)
    ProblemDetail onNasaUnavailable(NasaUnavailableException ex) {
        log.warn("NASA feed call failed: {}", ex.getMessage());
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
