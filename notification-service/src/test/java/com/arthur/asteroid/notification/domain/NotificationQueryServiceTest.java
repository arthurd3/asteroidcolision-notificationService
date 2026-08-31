package com.arthur.asteroid.notification.domain;

import com.arthur.asteroid.notification.persistence.DeliveryStatus;
import com.arthur.asteroid.notification.persistence.Notification;
import com.arthur.asteroid.notification.persistence.NotificationDeliveryRepository;
import com.arthur.asteroid.notification.persistence.NotificationRepository;
import com.arthur.asteroid.notification.persistence.SubscriberRepository;
import com.arthur.asteroid.notification.web.NotificationSummary;
import com.arthur.asteroid.notification.web.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The count roll-up and the paging guards, without a database. */
@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationRepository notifications;
    @Mock
    private NotificationDeliveryRepository deliveries;
    @Mock
    private SubscriberRepository subscribers;

    @InjectMocks
    private NotificationQueryService service;

    @Test
    @DisplayName("rolls the grouped counts onto the right notifications")
    void rollsCountsOntoSummaries() {
        final Notification first = notification(1L, "event-1");
        final Notification second = notification(2L, "event-2");
        given(notifications.findAllByOrderByCreatedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 2));
        given(deliveries.countByNotificationAndStatus(anyCollection())).willReturn(List.of(
                new Object[]{1L, DeliveryStatus.SENT, 2L},
                new Object[]{1L, DeliveryStatus.FAILED, 1L},
                new Object[]{2L, DeliveryStatus.PENDING, 5L}));

        final PageResponse<NotificationSummary> page = service.page(0, 20);

        assertThat(page.content()).satisfiesExactly(
                one -> {
                    assertThat(one.eventId()).isEqualTo("event-1");
                    assertThat(one.sent()).isEqualTo(2L);
                    assertThat(one.failed()).isEqualTo(1L);
                    assertThat(one.pending()).isZero();
                },
                two -> {
                    assertThat(two.eventId()).isEqualTo("event-2");
                    assertThat(two.pending()).isEqualTo(5L);
                    assertThat(two.sent()).isZero();
                });
    }

    @Test
    @DisplayName("a notification with no deliveries reports zeroes, not nulls")
    void notificationWithNoDeliveries() {
        given(notifications.findAllByOrderByCreatedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(notification(1L, "lonely")), PageRequest.of(0, 20), 1));
        given(deliveries.countByNotificationAndStatus(anyCollection())).willReturn(List.of());

        assertThat(service.page(0, 20).content()).singleElement().satisfies(summary -> {
            assertThat(summary.pending()).isZero();
            assertThat(summary.sent()).isZero();
            assertThat(summary.failed()).isZero();
        });
    }

    @Test
    @DisplayName("an empty page does not run the count query at all")
    void emptyPageSkipsCountQuery() {
        // `in ()` with no values is a syntax error in some databases and an
        // always-false predicate in others; not asking is unambiguous
        given(notifications.findAllByOrderByCreatedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(service.page(0, 20).content()).isEmpty();

        verify(deliveries, never()).countByNotificationAndStatus(anyCollection());
    }

    @Test
    @DisplayName("clamps an oversized page size rather than dumping the table")
    void clampsPageSize() {
        given(notifications.findAllByOrderByCreatedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        service.page(0, 10_000);

        final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notifications).findAllByOrderByCreatedAtDesc(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(NotificationQueryService.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("clamps a nonsensical page size up to one, since PageRequest rejects zero")
    void clampsPageSizeUp() {
        given(notifications.findAllByOrderByCreatedAtDesc(any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        service.page(-3, 0);

        final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notifications).findAllByOrderByCreatedAtDesc(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    private static Notification notification(final Long id, final String eventId) {
        final Notification notification = new Notification();
        notification.setId(id);
        notification.setEventId(eventId);
        notification.setAsteroidId("2000433");
        notification.setAsteroidName("433 Eros");
        notification.setCloseApproachDate(LocalDate.of(2026, 3, 4));
        notification.setMissDistanceKilometers(new BigDecimal("54321.5000"));
        notification.setEstimatedDiameterAvgMeters(200.0);
        notification.setOccurredAt(Instant.parse("2026-03-01T09:00:00Z"));
        notification.setCreatedAt(Instant.parse("2026-03-01T09:00:01Z"));
        return notification;
    }
}
