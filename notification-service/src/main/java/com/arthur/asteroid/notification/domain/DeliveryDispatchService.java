package com.arthur.asteroid.notification.domain;

import com.arthur.asteroid.notification.config.NotificationProperties;
import com.arthur.asteroid.notification.email.AlertEmailSender;
import com.arthur.asteroid.notification.persistence.NotificationDelivery;
import com.arthur.asteroid.notification.persistence.NotificationDeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Drains the pending-delivery queue. */
@Slf4j
@Service
public class DeliveryDispatchService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final AlertEmailSender emailSender;
    private final NotificationProperties properties;
    private final Clock clock;

    public DeliveryDispatchService(NotificationDeliveryRepository deliveryRepository,
                                   AlertEmailSender emailSender,
                                   NotificationProperties properties,
                                   Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Claims a batch of pending deliveries and attempts each one.
     *
     * <p>The ordering here is the important part. The previous implementation set
     * {@code emailSent = true} and saved <em>before</em> calling the mail sender, so
     * any SMTP failure permanently marked those alerts delivered and they were never
     * sent to anyone. Here a delivery is only marked SENT after the mail server has
     * accepted it; a failure increments the attempt count and leaves it claimable.
     *
     * <p>Each attempt is wrapped individually, so one bad address no longer aborts
     * the loop and starves every remaining recipient.
     *
     * <p>The claim holds row locks for the length of the batch. That is deliberate:
     * it keeps the state machine to two states instead of needing an IN_PROGRESS
     * status plus a reaper for rows orphaned by a crash. Batch size bounds how long
     * the locks are held.
     *
     * @return how many were delivered
     */
    @Transactional
    public int dispatchPending() {
        final List<NotificationDelivery> batch =
                deliveryRepository.claimPending(Limit.of(properties.batchSize()));

        if (batch.isEmpty()) {
            return 0;
        }

        int sent = 0;
        for (final NotificationDelivery delivery : batch) {
            try {
                emailSender.send(
                        delivery.getSubscriber().getEmail(),
                        delivery.getSubscriber().getFullName(),
                        delivery.getNotification());
                delivery.markSent(Instant.now(clock));
                sent++;
            } catch (RuntimeException ex) {
                delivery.markFailed(ex.getMessage(), properties.maxAttempts());
                log.warn("Delivery {} to {} failed (attempt {}/{}): {}",
                        delivery.getId(), delivery.getSubscriber().getEmail(),
                        delivery.getAttempts(), properties.maxAttempts(), ex.getMessage());
            }
        }

        log.info("Dispatched {}/{} pending deliveries", sent, batch.size());
        return sent;
    }
}
