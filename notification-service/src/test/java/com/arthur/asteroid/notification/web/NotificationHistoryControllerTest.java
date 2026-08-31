package com.arthur.asteroid.notification.web;

import com.arthur.asteroid.notification.domain.NotificationQueryService;
import com.arthur.asteroid.notification.persistence.DeliveryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationHistoryController.class)
@Import(ApiExceptionHandler.class)
class NotificationHistoryControllerTest {

    private static final String BASE = "/api/v1/notifications";
    private static final String EVENT_ID = "1e2d3c4b-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationQueryService queryService;

    @Test
    @DisplayName("returns a page envelope this service controls, not Spring Data's")
    void returnsPageEnvelope() throws Exception {
        given(queryService.page(0, 20)).willReturn(
                new PageResponse<>(List.of(summary()), 0, 20, 1L, 1));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value(EVENT_ID))
                .andExpect(jsonPath("$.content[0].asteroidName").value("433 Eros"))
                .andExpect(jsonPath("$.content[0].sent").value(2))
                .andExpect(jsonPath("$.content[0].failed").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                // Spring Data's PageImpl would have serialised these instead
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.numberOfElements").doesNotExist());
    }

    @Test
    @DisplayName("passes explicit paging through")
    void passesPagingThrough() throws Exception {
        given(queryService.page(anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(), 3, 5, 0L, 0));

        mockMvc.perform(get(BASE).param("page", "3").param("size", "5"))
                .andExpect(status().isOk());

        verify(queryService).page(3, 5);
    }

    @Test
    @DisplayName("rejects a negative page")
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get(BASE).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid page"));

        verify(queryService, never()).page(anyInt(), anyInt());
    }

    @ParameterizedTest(name = "rejects size={0}")
    @ValueSource(strings = {"0", "101", "-5"})
    @DisplayName("rejects a page size that would dump the table")
    void rejectsBadPageSize(final String size) throws Exception {
        mockMvc.perform(get(BASE).param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid page size"));

        verify(queryService, never()).page(anyInt(), anyInt());
    }

    @Test
    @DisplayName("returns one alert with a row per recipient")
    void returnsDetail() throws Exception {
        given(queryService.detail(EVENT_ID)).willReturn(new NotificationDetail(
                EVENT_ID, "2000433", "433 Eros", LocalDate.of(2026, 3, 4),
                new BigDecimal("54321.5000"), 200.0,
                Instant.parse("2026-03-01T09:00:00Z"), Instant.parse("2026-03-01T09:00:01Z"),
                List.of(new DeliveryView("dev@asteroid.local", "Asteroid Watch (dev)",
                                DeliveryStatus.SENT, 1, Instant.parse("2026-03-01T09:00:30Z"),
                                null, Instant.parse("2026-03-01T09:00:01Z")),
                        new DeliveryView("broken@asteroid.local", "Broken Mailbox",
                                DeliveryStatus.FAILED, 3, null,
                                "550 mailbox unavailable", Instant.parse("2026-03-01T09:00:01Z")))));

        mockMvc.perform(get(BASE + "/" + EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asteroidName").value("433 Eros"))
                .andExpect(jsonPath("$.deliveries.length()").value(2))
                .andExpect(jsonPath("$.deliveries[0].status").value("SENT"))
                .andExpect(jsonPath("$.deliveries[1].status").value("FAILED"))
                .andExpect(jsonPath("$.deliveries[1].lastError").value("550 mailbox unavailable"))
                .andExpect(jsonPath("$.deliveries[1].attempts").value(3));
    }

    @Test
    @DisplayName("an unknown event id is 404 problem+json, not an empty 200")
    void unknownEventIdIsNotFound() throws Exception {
        given(queryService.detail(anyString()))
                .willThrow(new NotificationNotFoundException("nope"));

        mockMvc.perform(get(BASE + "/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Notification not found"))
                .andExpect(jsonPath("$.type")
                        .value("https://asteroid.arthur.com/problems/notification-not-found"));
    }

    @Test
    @DisplayName("returns pipeline totals")
    void returnsStats() throws Exception {
        given(queryService.stats()).willReturn(new DeliveryStats(
                12L, 3L, 1L, 34L, 2L, Instant.parse("2026-03-01T09:00:01Z")));

        mockMvc.perform(get(BASE + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").value(12))
                .andExpect(jsonPath("$.enabledSubscribers").value(3))
                .andExpect(jsonPath("$.sent").value(34))
                .andExpect(jsonPath("$.lastIngestedAt").value("2026-03-01T09:00:01Z"));
    }

    @Test
    @DisplayName("a fresh database reports a null last-ingest rather than failing")
    void statsOnEmptyDatabase() throws Exception {
        given(queryService.stats()).willReturn(new DeliveryStats(0L, 1L, 0L, 0L, 0L, null));

        mockMvc.perform(get(BASE + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").value(0))
                .andExpect(jsonPath("$.lastIngestedAt").doesNotExist());
    }

    private static NotificationSummary summary() {
        return new NotificationSummary(EVENT_ID, "2000433", "433 Eros",
                LocalDate.of(2026, 3, 4), new BigDecimal("54321.5000"), 200.0,
                Instant.parse("2026-03-01T09:00:00Z"), Instant.parse("2026-03-01T09:00:01Z"),
                0L, 2L, 1L);
    }
}
