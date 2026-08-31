package com.arthur.asteroid.alerting.web;

import com.arthur.asteroid.alerting.nasa.NasaApodClient;
import com.arthur.asteroid.alerting.nasa.NasaUnavailableException;
import com.arthur.asteroid.alerting.nasa.dto.apod.ApodEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApodController.class)
@Import({ApiExceptionHandler.class, ApodControllerTest.FixedClock.class})
class ApodControllerTest {

    private static final String URL = "/api/v1/nasa/apod";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NasaApodClient apodClient;

    /** A slice does not get the application's ClockConfig, so the test supplies one. */
    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-31T09:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    @DisplayName("returns today's picture when no date is given")
    void returnsToday() throws Exception {
        given(apodClient.pictureOfTheDay(null)).willReturn(entry("image"));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("NGC 4565"))
                .andExpect(jsonPath("$.media_type").value("image"));
    }

    @Test
    @DisplayName("passes an explicit date straight through")
    void returnsRequestedDate() throws Exception {
        given(apodClient.pictureOfTheDay(LocalDate.of(2026, 8, 30))).willReturn(entry("image"));

        mockMvc.perform(get(URL).param("date", "2026-08-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("NGC 4565"));
    }

    @Test
    @DisplayName("refuses a date before APOD existed, without calling NASA")
    void refusesDateBeforeFirstEntry() throws Exception {
        mockMvc.perform(get(URL).param("date", "1995-06-15"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid date"))
                .andExpect(jsonPath("$.type").value("https://asteroid.arthur.com/problems/invalid-date"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1995-06-16")));
    }

    @Test
    @DisplayName("accepts APOD's very first entry")
    void acceptsFirstEntry() throws Exception {
        given(apodClient.pictureOfTheDay(ApodController.FIRST_ENTRY)).willReturn(entry("image"));

        mockMvc.perform(get(URL).param("date", "1995-06-16")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("refuses a future date")
    void refusesFutureDate() throws Exception {
        mockMvc.perform(get(URL).param("date", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid date"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("future")));
    }

    @Test
    @DisplayName("today is not in the future")
    void acceptsToday() throws Exception {
        given(apodClient.pictureOfTheDay(LocalDate.of(2026, 8, 31))).willReturn(entry("image"));

        mockMvc.perform(get(URL).param("date", "2026-08-31")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an unreachable NASA becomes 503 problem+json, not a stack trace")
    void translatesUpstreamFailure() throws Exception {
        given(apodClient.pictureOfTheDay(any()))
                .willThrow(new NasaUnavailableException("NASA APOD returned 429 TOO_MANY_REQUESTS"));

        mockMvc.perform(get(URL))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Upstream unavailable"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("429")));
    }

    @Test
    @DisplayName("exposes exactly the record components, in NASA's own field names")
    void exposesStableShape() throws Exception {
        // Pins this service's own contract, which web-ui consumes. Two things are
        // easy to break by accident: media_type / service_version keep NASA's snake
        // case, and the derived helpers - image(), video(), displayUrl() - are NOT
        // serialised, because Jackson maps a record's components and nothing else.
        // A front end therefore has to branch on media_type itself.
        given(apodClient.pictureOfTheDay(null)).willReturn(entry("image"));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media_type").exists())
                .andExpect(jsonPath("$.service_version").exists())
                .andExpect(jsonPath("$.hdurl").exists())
                .andExpect(jsonPath("$.displayUrl").doesNotExist())
                .andExpect(jsonPath("$.image").doesNotExist())
                .andExpect(jsonPath("$.video").doesNotExist());
    }

    @Test
    @DisplayName("an unparseable date is a 400, not a 500")
    void refusesUnparseableDate() throws Exception {
        mockMvc.perform(get(URL).param("date", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    private static ApodEntry entry(final String mediaType) {
        return new ApodEntry(LocalDate.of(2026, 8, 30), "NGC 4565", "A galaxy.",
                "https://apod.nasa.gov/apod/image/2608/g.jpg",
                "https://apod.nasa.gov/apod/image/2608/g_hd.jpg",
                null, mediaType, "v1");
    }
}
