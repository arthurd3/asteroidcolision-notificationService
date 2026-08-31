package com.arthur.asteroid.notification.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Translates failures into RFC 9457 problem responses.
 *
 * <p>The same problem-type base as asteroid-service, on purpose: a client - web-ui
 * in particular - decodes {@code application/problem+json} the same way whichever
 * backend answered, and the {@code type} URI identifies the problem rather than the
 * service that produced it.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final String PROBLEM_BASE = "https://asteroid.arthur.com/problems/";

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail onInvalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.title(), ex.getMessage(), ex.slug());
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    ProblemDetail onNotFound(NotificationNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Notification not found",
                ex.getMessage(), "notification-not-found");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String slug) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        // left null, Spring defaults it to "about:blank", which tells a client nothing
        problem.setType(URI.create(PROBLEM_BASE + slug));
        return problem;
    }
}
