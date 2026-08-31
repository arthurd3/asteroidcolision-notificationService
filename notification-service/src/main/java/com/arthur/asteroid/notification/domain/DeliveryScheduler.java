package com.arthur.asteroid.notification.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically drains the delivery queue.
 *
 * <p>{@code fixedDelay}, not {@code fixedRate}: the interval is measured from the
 * end of the previous run, so a slow SMTP round cannot overlap the next tick.
 */
@Slf4j
@Component
public class DeliveryScheduler {

    private final DeliveryDispatchService dispatchService;

    public DeliveryScheduler(DeliveryDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(
            fixedDelayString = "${notification.dispatch-interval:PT30S}",
            initialDelayString = "${notification.dispatch-initial-delay:PT10S}")
    public void dispatch() {
        try {
            dispatchService.dispatchPending();
        } catch (RuntimeException ex) {
            // never let an exception escape a scheduled method: Spring's scheduler
            // silently cancels the task's future schedule if one does
            log.error("Delivery dispatch run failed", ex);
        }
    }
}
