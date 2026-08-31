package com.arthur.asteroid.notification.domain;

import com.arthur.asteroid.notification.config.NotificationProperties;
import com.arthur.asteroid.notification.email.AlertEmailSender;
import com.arthur.asteroid.notification.persistence.DeliveryStatus;
import com.arthur.asteroid.notification.persistence.Notification;
import com.arthur.asteroid.notification.persistence.NotificationDelivery;
import com.arthur.asteroid.notification.persistence.NotificationDeliveryRepository;
import com.arthur.asteroid.notification.persistence.Subscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.mail.MailSendException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Guards the regression that mattered most: the previous implementation flagged
 * alerts as sent <em>before</em> calling the mail server, so an SMTP failure
 * marked them delivered and they were silently never sent.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryDispatchServiceTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-03-01T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    private NotificationDeliveryRepository deliveryRepository;
    @Mock
    private AlertEmailSender emailSender;

    private DeliveryDispatchService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryDispatchService(
                deliveryRepository, emailSender,
                new NotificationProperties("asteroid-alert", "alerts@test.local", 50, 3),
                FIXED);
    }

    @Test
    @DisplayName("marks SENT only after the mail server accepts the message")
    void marksSentAfterSuccessfulSend() {
        final NotificationDelivery delivery = delivery("a@test.local");
        given(deliveryRepository.claimPending(any(Limit.class))).willReturn(List.of(delivery));
        doNothing().when(emailSender).send(eq("a@test.local"), any(), any());

        assertThat(service.dispatchPending()).isEqualTo(1);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(delivery.getSentAt()).isEqualTo(Instant.parse("2026-03-01T09:00:00Z"));
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getLastError()).isNull();
    }

    @Test
    @DisplayName("a failed send leaves the delivery claimable instead of marking it sent")
    void failedSendDoesNotMarkSent() {
        final NotificationDelivery delivery = delivery("bounces@test.local");
        given(deliveryRepository.claimPending(any(Limit.class))).willReturn(List.of(delivery));
        willThrow(new MailSendException("mailbox unavailable"))
                .given(emailSender).send(any(), any(), any());

        assertThat(service.dispatchPending()).isZero();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getSentAt()).isNull();
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getLastError()).contains("mailbox unavailable");
    }

    @Test
    @DisplayName("one bad recipient does not starve the rest of the batch")
    void oneFailureDoesNotAbortTheBatch() {
        final NotificationDelivery bad = delivery("bounces@test.local");
        final NotificationDelivery good = delivery("fine@test.local");
        given(deliveryRepository.claimPending(any(Limit.class))).willReturn(List.of(bad, good));
        willThrow(new MailSendException("nope"))
                .given(emailSender).send(eq("bounces@test.local"), any(), any());

        assertThat(service.dispatchPending()).isEqualTo(1);

        assertThat(bad.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(good.getStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    @DisplayName("parks a delivery as FAILED once attempts are exhausted")
    void parksAfterMaxAttempts() {
        final NotificationDelivery delivery = delivery("bounces@test.local");
        delivery.markFailed("first", 3);
        delivery.markFailed("second", 3);
        given(deliveryRepository.claimPending(any(Limit.class))).willReturn(List.of(delivery));
        willThrow(new MailSendException("third")).given(emailSender).send(any(), any(), any());

        service.dispatchPending();

        assertThat(delivery.getAttempts()).isEqualTo(3);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    @DisplayName("does not touch the mail server when nothing is pending")
    void noopWhenQueueEmpty() {
        given(deliveryRepository.claimPending(any(Limit.class))).willReturn(List.of());

        assertThat(service.dispatchPending()).isZero();
        verify(emailSender, never()).send(any(), any(), any());
    }

    private static NotificationDelivery delivery(String email) {
        final Notification notification = new Notification();
        notification.setEventId("event-" + email);
        notification.setAsteroidId("2000433");
        notification.setAsteroidName("433 Eros");
        notification.setCloseApproachDate(LocalDate.of(2026, 3, 4));
        notification.setMissDistanceKilometers(new BigDecimal("54321.5"));
        notification.setEstimatedDiameterAvgMeters(200.0);

        final Subscriber subscriber = new Subscriber();
        subscriber.setEmail(email);
        subscriber.setFullName("Test Subscriber");
        subscriber.setNotificationEnabled(true);

        return NotificationDelivery.pending(notification, subscriber, Instant.EPOCH);
    }
}
