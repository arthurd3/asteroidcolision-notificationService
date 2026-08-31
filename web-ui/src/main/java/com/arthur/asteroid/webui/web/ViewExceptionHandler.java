package com.arthur.asteroid.webui.web;

import com.arthur.asteroid.webui.backend.BackendUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

/**
 * Renders failures as a page rather than as JSON.
 *
 * <p>{@code @ControllerAdvice}, not {@code @RestControllerAdvice} - that distinction
 * is the whole reason this front end is a separate module. Both backends answer with
 * {@code application/problem+json}, which is right for an API and useless in a
 * browser; here the same failure has to become HTML. Had the JSPs been added to
 * asteroid-service instead, its existing {@code @RestControllerAdvice} would have
 * returned a problem document for view requests too.
 *
 * <p>Two Boot behaviours have to line up for the {@code error} view to be reached at
 * all, and both fail silently:
 * <ul>
 *   <li>{@code spring.web.error.whitelabel.enabled} must be false, because the
 *       whitelabel page is a {@code View} bean named {@code error} and
 *       {@code BeanNameViewResolver} resolves it ahead of the JSP resolver;</li>
 *   <li>the property is {@code spring.web.error.*} - {@code server.error.*} has been
 *       deprecated at level {@code error} since Boot 4.0.0 and binds to nothing.</li>
 * </ul>
 */
@Slf4j
@ControllerAdvice
public class ViewExceptionHandler {

    /**
     * A backend did not answer.
     *
     * <p>503 rather than 500: this service is fine, something it depends on is not,
     * and the distinction matters to whatever is watching the health endpoint.
     */
    @ExceptionHandler(BackendUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ModelAndView onBackendUnavailable(final BackendUnavailableException ex) {
        log.warn("{} failed: {}", ex.service(), ex.getMessage());

        final ModelAndView view = new ModelAndView("error");
        view.addObject("pageTitle", "Service unavailable");
        view.addObject("service", ex.service());
        view.addObject("unreachable", ex.unreachable());
        view.addObject("status", ex.status() == null ? null : ex.status().value());

        // The backend's own RFC 9457 title and detail, when it sent them. This is
        // where problem+json stops being ceremony: "The NASA feed is failing
        // repeatedly and calls are being shed. Retry shortly." was written once, in
        // the service that knows it, and arrives here ready to show a person.
        if (ex.problem() != null) {
            view.addObject("problemTitle", ex.problem().getTitle());
            view.addObject("problemDetail", ex.problem().getDetail());
        }

        // An unreachable backend is nearly always a service that was not started, so
        // the page says how to start it rather than only that it is missing.
        view.addObject("startCommand", startCommandFor(ex.service()));
        return view;
    }

    /** Anything else, so a bug in this module does not leak a stack trace to a browser. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView onUnexpected(final Exception ex) {
        log.error("Unexpected failure rendering a page", ex);

        final ModelAndView view = new ModelAndView("error");
        view.addObject("pageTitle", "Something went wrong");
        view.addObject("problemTitle", "Unexpected error");
        view.addObject("problemDetail", "The details are in this service's log.");
        return view;
    }

    private static String startCommandFor(final String service) {
        if (service == null) {
            return null;
        }
        if (service.startsWith("asteroid-service")) {
            return "./mvnw -pl asteroid-service spring-boot:run";
        }
        if (service.startsWith("notification-service")) {
            return "./mvnw -pl notification-service spring-boot:run";
        }
        return null;
    }
}
